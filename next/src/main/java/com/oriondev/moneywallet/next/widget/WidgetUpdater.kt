package com.oriondev.moneywallet.next.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Refreshes every placed home-screen widget ([widgets]). Call [requestUpdate] after any write that
 * changes what they show (transactions, wallets, budgets, rates, relevant settings); calls are
 * debounced so bursts of writes cost one render. Add new widget classes to [widgets].
 */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    private fun widgets(): List<GlanceAppWidget> = listOf(BalanceWidget(), SafeToSpendWidget(), QuickAddWidget())

    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            pending?.cancel()
            pending = scope.launch {
                delay(400)
                widgets().forEach { w ->
                    try { w.updateAll(app) } catch (e: Exception) { /* none placed or host unavailable */ }
                }
            }
        }
    }
}
