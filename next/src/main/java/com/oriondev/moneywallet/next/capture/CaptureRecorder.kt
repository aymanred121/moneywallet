package com.oriondev.moneywallet.next.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oriondev.moneywallet.next.MainActivity
import com.oriondev.moneywallet.next.R
import com.oriondev.moneywallet.next.WalletApp
import com.oriondev.moneywallet.next.core.CurrencySymbols
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.CaptureEntity
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.ui.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Turns a read notification into a PENDING capture and tells the user about it quietly. */
object CaptureRecorder {
    const val CHANNEL = "captures"
    private const val TAG = "capture"
    private const val ECHO_WINDOW = 10 * 60_000L
    private const val KEEP_RESOLVED = 30L * 24 * 60 * 60_000L

    /** Serialises the "similar capture?" check and the insert so two echoes cannot both pass. */
    private val lock = Mutex()

    /** Parses and stores one notification; returns the new capture id, or null when skipped. */
    suspend fun record(context: Context, packageName: String, title: String, text: String, postedAt: Long, whenMillis: Long, strict: Boolean = false, notify: Boolean = true): Long? {
        val container = WalletApp.container(context)
        val settings = container.prefs.settings.first()
        val payment = PaymentParser.parse(title, text, settings.baseCurrency ?: "SAR", strict = strict, rules = settings.captureRules.mapNotNull(HeaderRule::decode)) as? ParseResult.Payment ?: return null
        val dao = container.db.captures()
        val id = lock.withLock {
            // an app echoing the same payment twice; a credit and a debit of the same amount are two payments
            if (dao.countSimilarSince(packageName, payment.amountMinor, payment.merchant.orEmpty(), payment.direction, postedAt - ECHO_WINDOW) > 0) return null
            dao.insert(
                CaptureEntity(
                    dedupeKey = PaymentParser.dedupeKey(packageName, title, text, whenMillis),
                    packageName = packageName, appName = CaptureApps.label(context, packageName),
                    title = title.take(200), text = text.take(1000), postedAt = postedAt,
                    amount = payment.amountMinor, currency = payment.currency, merchant = payment.merchant, direction = payment.direction,
                ),
            )
        }
        if (id < 0) return null
        dao.pruneResolved(System.currentTimeMillis() - KEEP_RESOLVED)
        if (notify) dao.byId(id)?.let { notify(context, it) }
        return id
    }

    /** Editor route for a capture: amount, currency, merchant, date, the category last used with that merchant and the capture wallet. */
    suspend fun addRoute(context: Context, capture: CaptureEntity): String {
        val container = WalletApp.container(context)
        val categoryId = capture.merchant?.let { container.db.captures().lastCategoryFor(it) }
        return Routes.addTransaction(
            walletId = container.prefs.settings.first().captureWalletId, direction = capture.direction,
            amountMinor = capture.amount, currency = capture.currency, description = capture.merchant,
            date = capture.postedAt, categoryId = categoryId, captureId = capture.id,
        )
    }

    private suspend fun notify(context: Context, capture: CaptureEntity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val ctx = WalletApp.container(context).repo.currencyContext.first()
        val amount = capture.amount?.let { MoneyFormat.format(it, ctx.currency(capture.currency ?: ctx.baseIso)) } ?: return
        val verb = if (capture.direction == Direction.INCOME) "Received" else "Captured"
        val body = CurrencySymbols.plainText(capture.merchant?.let { "$verb $amount ${if (capture.direction == Direction.INCOME) "from" else "at"} $it" } ?: "$verb $amount")
        val code = capture.id.toInt()
        fun open(route: String, request: Int) = PendingIntent.getActivity(
            context, request,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_ROUTE, route)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, code,
            Intent(context, CaptureActionReceiver::class.java).setAction(CaptureActionReceiver.ACTION_DISMISS).putExtra(CaptureActionReceiver.EXTRA_ID, capture.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setContentTitle(body)
            .setContentText("From ${capture.appName}. Tap to review.")
            .setContentIntent(open(Routes.CAPTURES, code * 2))
            .addAction(0, "Add", open(addRoute(context, capture), code * 2 + 1))
            .addAction(0, "Dismiss", dismiss)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setWhen(capture.postedAt)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try { NotificationManagerCompat.from(context).notify(TAG, code, notification) } catch (e: SecurityException) { /* permission revoked */ }
    }

    fun cancel(context: Context, captureId: Long) = NotificationManagerCompat.from(context).cancel(TAG, captureId.toInt())

    /** Removes our capture notifications whose capture is no longer pending (added in the editor, dismissed in the inbox). */
    fun cancelResolved(context: Context, pendingIds: Set<Long>) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.activeNotifications.filter { it.tag == TAG && it.id.toLong() !in pendingIds }.forEach { nm.cancel(TAG, it.id) }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.capture_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.capture_channel_description) },
        )
    }
}
