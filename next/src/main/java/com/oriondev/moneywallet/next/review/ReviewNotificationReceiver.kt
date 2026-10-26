package com.oriondev.moneywallet.next.review

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oriondev.moneywallet.next.MainActivity
import com.oriondev.moneywallet.next.WalletApp
import com.oriondev.moneywallet.next.core.CurrencySymbols
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.analytics.Aggregates
import com.oriondev.moneywallet.next.domain.analytics.Comparison
import com.oriondev.moneywallet.next.domain.analytics.from
import com.oriondev.moneywallet.next.domain.analytics.to
import com.oriondev.moneywallet.next.domain.analytics.total
import com.oriondev.moneywallet.next.ui.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * Fires for [ReviewScheduler.ACTION_WEEKLY] / [ReviewScheduler.ACTION_MONTHLY] (re-armed after boot by [BootReceiver]),
 * posts a summary of the period that just ended and always re-arms itself, even when the summary fails.
 * Tapping opens MainActivity with extra "route" = Routes.review(encodedPeriod).
 */
class ReviewNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ReviewScheduler.ACTION_WEEKLY && action != ReviewScheduler.ACTION_MONTHLY) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            var settings: Settings? = null
            try {
                val container = WalletApp.container(context)
                settings = container.prefs.settings.first()
                if (settings.reviewNotifications) {
                    val period = justEnded(action, settings, LocalDate.now())
                    val (title, body) = summarise(context, period)
                    post(context, period, title, body, if (action == ReviewScheduler.ACTION_WEEKLY) ID_WEEKLY else ID_MONTHLY)
                }
            } catch (e: Exception) {
                // never crash a broadcast
            } finally {
                try {
                    val s = settings ?: Settings()
                    if (s.reviewNotifications) ReviewScheduler.rearm(context, action, s)
                } catch (e: Exception) { /* next app start re-arms */ }
                pending.finish()
            }
        }
    }

    private suspend fun summarise(context: Context, period: Period): Pair<String, String> {
        val container = WalletApp.container(context)
        val tx = container.db.transactions()
        val ctx = container.repo.currencyContext.first()
        val noun = if (period is Period.Week) "week" else "month"
        val w = Comparison.windows(period, LocalDate.now())
        val cur = ctx.total(Aggregates.bag(tx.observeTotals(null, w.current.from, w.current.to).first(), Direction.EXPENSE))
        val prev = ctx.total(Aggregates.bag(tx.observeTotals(null, w.previous.from, w.previous.to).first(), Direction.EXPENSE))
        val title = "Your $noun in review"
        val change = Aggregates.percentChange(cur.base, prev.base)
        // privacy mode: never put amounts on the lock screen
        val spent = if (MoneyFormat.masked) "You spent" else "You spent " + (cur.base?.let { MoneyFormat.format(it, ctx.base) }
            ?: cur.bag.entries.entries.joinToString(" + ") { MoneyFormat.format(it.value, ctx.currency(it.key)) })
        val body = when {
            cur.isEmpty -> "No spending recorded last $noun. Tap to see the summary."
            change == null -> "$spent last $noun. Tap to see where it went."
            abs(change) < 3 -> "$spent last $noun, about the same as before."
            change < 0 -> "$spent last $noun, ${abs(change)}% less than the $noun before."
            else -> "$spent last $noun, $change% more than the $noun before."
        }
        return title to CurrencySymbols.plainText(body)
    }

    private fun post(context: Context, period: Period, title: String, body: String, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, Routes.review(Period.encode(period)))
        }
        val tap = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, WalletApp.CHANNEL_REVIEW)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        try { NotificationManagerCompat.from(context).notify(id, notification) } catch (e: SecurityException) { /* permission revoked */ }
    }

    companion object {
        /** Intent extra carrying a navigation route for MainActivity. */
        const val EXTRA_ROUTE = "route"
        private const val ID_WEEKLY = 7101
        private const val ID_MONTHLY = 7102

        /** The period that ended just before [today], built with the settings' start days explicitly. */
        internal fun justEnded(action: String, settings: Settings, today: LocalDate): Period =
            if (action == ReviewScheduler.ACTION_WEEKLY) {
                Period.Week(today.with(TemporalAdjusters.previousOrSame(ReviewScheduler.dayOfWeek(settings)))).previous()
            } else Period.monthOf(today, settings.firstDayOfMonth.coerceIn(1, 28)).previous()
    }
}
