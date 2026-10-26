package com.oriondev.moneywallet.next.alerts

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
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.MainActivity
import com.oriondev.moneywallet.next.R
import com.oriondev.moneywallet.next.core.Clock
import com.oriondev.moneywallet.next.core.CurrencySymbols
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.screens.home.BudgetUi
import com.oriondev.moneywallet.next.ui.screens.home.activeBudgets
import com.oriondev.moneywallet.next.ui.screens.home.budgetDirection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Posts one notification when an expense budget reaches 80% and one when it reaches 100%, once per
 * budget, threshold and budget period. Watches live progress while the process lives (anything
 * that changes spending happens in this process); fired keys persist in a small prefs file.
 */
object BudgetAlerts {
    const val CHANNEL = "budgets"
    private const val PREFS = "budget_alerts"
    private const val KEY_FIRED = "fired"
    private val THRESHOLDS = listOf(100, 80)

    /** Call once from `WalletApp.onCreate`. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(context: Context, container: AppContainer) {
        val app = context.applicationContext
        createChannel(app)
        container.scope.launch {
            container.prefs.settings.map { it.budgetAlerts }.distinctUntilChanged().flatMapLatest { enabled ->
                if (!enabled) emptyFlow()
                else combine(
                    activeBudgets(container.db, container.repo.currencyContext, Clock.today.map { Dates.now() }, Int.MAX_VALUE, container.repo.categories),
                    container.repo.categories, container.repo.currencyContext,
                ) { budgets, cats, ctx -> budgets.filter { b -> budgetDirection(b.budget, cats.firstOrNull { it.id == b.budget.categoryId }) == Direction.EXPENSE } to ctx }
            }.collect { (budgets, ctx) -> try { check(app, budgets, ctx) } catch (e: Exception) { /* keep watching */ } }
        }
    }

    /** Highest threshold [ratio] has reached, or null. */
    internal fun reached(spent: Long, limit: Long): Int? {
        if (limit <= 0) return null
        return THRESHOLDS.firstOrNull { spent * 100 >= limit * it }
    }

    internal fun key(b: BudgetUi, threshold: Int) = "${b.budget.id}:${b.budget.startDate}:${b.budget.endDate}:$threshold"

    private fun check(context: Context, budgets: List<BudgetUi>, ctx: CurrencyContext) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fired = prefs.getStringSet(KEY_FIRED, emptySet()).orEmpty().toMutableSet()
        val before = fired.toSet()
        for (b in budgets) {
            val threshold = reached(b.spent, b.budget.money) ?: continue
            if (key(b, threshold) in fired) continue
            // reaching 100% directly also settles the 80% alert
            THRESHOLDS.filter { it <= threshold }.forEach { fired += key(b, it) }
            post(context, b, threshold, ctx)
        }
        // forget periods that ended more than a day ago
        val now = Dates.now()
        fired.removeAll { k -> k.split(":").getOrNull(2)?.toLongOrNull()?.let { it + 2 * 86_400_000L < now } ?: true }
        if (fired != before) prefs.edit().putStringSet(KEY_FIRED, fired).apply()
    }

    private fun post(context: Context, b: BudgetUi, threshold: Int, ctx: CurrencyContext) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val currency = ctx.currency(b.budget.currency)
        val title = if (threshold >= 100) "${b.name} budget used up" else "${b.name} budget at $threshold%"
        val body = when {
            MoneyFormat.masked -> if (threshold >= 100) "You've spent all of this budget." else "You've used $threshold% of this budget."
            threshold >= 100 -> "Spent ${MoneyFormat.format(b.spent, currency)} of ${MoneyFormat.format(b.budget.money, currency)}."
            else -> "${MoneyFormat.format(b.budget.money - b.spent, currency)} left of ${MoneyFormat.format(b.budget.money, currency)}."
        }.let(CurrencySymbols::plainText)
        val id = (b.budget.id * 10 + threshold / 10).toInt()
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUTE, Routes.BUDGETS)
        }
        val tap = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try { NotificationManagerCompat.from(context).notify("budget", id, n) } catch (e: SecurityException) { /* permission revoked */ }
    }

    private fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.budget_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.budget_channel_description)
        nm.createNotificationChannel(channel)
    }
}
