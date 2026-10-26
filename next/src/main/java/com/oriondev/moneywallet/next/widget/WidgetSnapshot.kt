package com.oriondev.moneywallet.next.widget

import android.content.Context
import android.content.Intent
import com.oriondev.moneywallet.next.MainActivity
import com.oriondev.moneywallet.next.WalletApp
import com.oriondev.moneywallet.next.core.CurrencySymbols
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.domain.analytics.total
import com.oriondev.moneywallet.next.domain.pacing.SafeSource
import com.oriondev.moneywallet.next.ui.screens.home.CycleUi
import com.oriondev.moneywallet.next.ui.screens.home.activeBudgets
import com.oriondev.moneywallet.next.ui.screens.home.cycleSums
import com.oriondev.moneywallet.next.ui.screens.home.toUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * What the widgets show, already formatted as plain text (widgets can't load the app's riyal font,
 * so symbols go through [CurrencySymbols.plainText]; hide balances masks every amount).
 */
data class WidgetSnapshot(
    val balanceLines: List<String>,
    val spentLine: String,
    /** "Spent 27 Sep – 26 Oct" (payday cycle) or "Spent in Sep" (calendar month). */
    val spentLabel: String,
    val rateHint: Boolean,
    /** "SAR 120/day", or null when there's no budget or income to base it on (or a rate is missing). */
    val safePerDay: String?,
    /** "Over budget" style message replacing [safePerDay] when the pool is used up. */
    val overLine: String?,
    /** "4 days to payday". */
    val daysLeftLabel: String,
) {
    companion object {
        val EMPTY = WidgetSnapshot(listOf("—"), "—", "Spent this cycle", false, null, null, "")

        /** Reads the same figures as Home (same wallet filter, payday cycle and pacing). */
        suspend fun load(context: Context): WidgetSnapshot {
            val container = WalletApp.container(context)
            val ctx = container.repo.currencyContext.first()
            val settings = container.prefs.settings.first()
            val wallets = container.repo.wallets().first()
            val hidden = settings.hideBalances
            val selected = settings.selectedWalletId
            val bag = MoneyBag()
            wallets.filter { !it.archived && (if (selected == null) it.countInTotal else it.id == selected) }.forEach { bag.add(it.currency, it.balance) }
            val balance = ctx.total(bag)
            val budgets = activeBudgets(container.db, container.repo.currencyContext, flowOf(System.currentTimeMillis()), Int.MAX_VALUE, container.repo.categories).first()
            val cycle: CycleUi = cycleSums(container.db, selected, settings.firstDayOfMonth.coerceIn(1, 28), LocalDate.now()).first().toUi(ctx, budgets, balance)
            val spent = cycle.spent
            val plain = CurrencySymbols::plainText
            val balanceLines = if (hidden) listOf(MoneyFormat.MASK) else balance.base?.let { listOf(MoneyFormat.format(it, ctx.base, hidden = false)) }
                ?: bag.entries.filterValues { it != 0L }.map { MoneyFormat.format(it.value, ctx.currency(it.key), hidden = false) }.ifEmpty { listOf(MoneyFormat.format(0, ctx.base, hidden = false)) }
            val spentLine = spent.base?.let { MoneyFormat.format(it, ctx.base, compact = true, hidden = hidden) }
                ?: spent.bag.entries.entries.joinToString(" + ") { MoneyFormat.format(it.value, ctx.currency(it.key), compact = true, hidden = hidden) }
            val p = cycle.pacing
            val overLine = p?.takeIf { it.over }?.let { if (it.source == SafeSource.INCOME) "Spent more than earned" else "Over budget" }
            val safe = p?.safePerDay?.takeIf { !p.over }?.let { "${MoneyFormat.format(it, ctx.base, compact = true, hidden = hidden)}/day" }
            val spentLabel = if (cycle.period.startDay == 1) "Spent this month" else "Spent ${cycle.rangeLabel}"
            return WidgetSnapshot(
                balanceLines.map(plain), plain(spentLine), spentLabel, balance.hasRateGap || spent.hasRateGap,
                safe?.let(plain), overLine, cycle.daysLeftLabel,
            )
        }
    }
}

/** Opens the app on [route] (must be allow-listed in [MainActivity.isAllowedExternalRoute]). */
internal fun openRoute(context: Context, route: String): Intent =
    Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK; putExtra(MainActivity.EXTRA_ROUTE, route) }
