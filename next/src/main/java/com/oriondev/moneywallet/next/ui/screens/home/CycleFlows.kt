package com.oriondev.moneywallet.next.ui.screens.home

import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.BudgetType
import com.oriondev.moneywallet.next.data.db.CurrencySum
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.analytics.Aggregates
import com.oriondev.moneywallet.next.domain.analytics.Comparison
import com.oriondev.moneywallet.next.domain.analytics.Total
import com.oriondev.moneywallet.next.domain.analytics.from
import com.oriondev.moneywallet.next.domain.analytics.to
import com.oriondev.moneywallet.next.domain.analytics.total
import com.oriondev.moneywallet.next.domain.pacing.OverallBudget
import com.oriondev.moneywallet.next.domain.pacing.Pacing
import com.oriondev.moneywallet.next.domain.pacing.PacingEngine
import com.oriondev.moneywallet.next.domain.pacing.PacingInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Raw sums behind the payday-cycle card and the widgets: this cycle so far, the same days of the last one, and all of it. */
data class CycleSums(
    val period: Period.Month,
    val today: LocalDate,
    val soFar: List<CurrencySum>,
    val previousSoFar: List<CurrencySum>,
    val previousTotal: List<CurrencySum>,
)

fun cycleSums(db: WalletDatabase, walletId: Long?, startDay: Int, today: LocalDate): Flow<CycleSums> {
    val period = Period.monthOf(today, startDay)
    val w = Comparison.windows(period, today)
    val prev = period.previous().range
    val tx = db.transactions()
    return combine(
        tx.observeTotals(walletId, w.current.from, w.current.to),
        tx.observeTotals(walletId, w.previous.from, w.previous.to),
        tx.observeTotals(walletId, prev.from, prev.to),
    ) { a, b, c -> CycleSums(period, today, a, b, c) }
}

/** The payday card's content. [change] is like-for-like (same elapsed days); [pacing] is null when a rate is missing. */
data class CycleUi(
    val period: Period.Month,
    val today: LocalDate,
    val spent: Total,
    val previousSpent: Total,
    val change: Int?,
    val pacing: Pacing?,
    val earned: Total = Total.EMPTY,
) {
    val start: LocalDate get() = Dates.toLocalDate(period.range.from)
    val endExclusive: LocalDate get() = Dates.toLocalDate(period.range.to)
    val lastDay: LocalDate get() = endExclusive.minusDays(1)
    /** Days until the next cycle starts, today included (so 1 = payday is tomorrow). */
    val daysLeft: Int get() = Dates.daysBetween(today, endExclusive).toInt().coerceAtLeast(1)
    val rangeLabel: String get() = Dates.monthLabel(period.month, period.startDay)
    /** "4 days to payday" for a payday cycle, "4 days left" for a calendar month. */
    val daysLeftLabel: String get() = when {
        period.startDay == 1 -> if (daysLeft == 1) "Last day" else "$daysLeft days left"
        daysLeft == 1 -> "Payday tomorrow"
        else -> "$daysLeft days to payday"
    }
    val lastDayLabel: String get() = lastDay.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
}

/**
 * Converts [CycleSums] to base-currency figures and runs [PacingEngine]. [budgets] are the active
 * budgets (soonest ending first): the first overall expense budget drives safe-to-spend.
 */
fun CycleSums.toUi(ctx: CurrencyContext, budgets: List<BudgetUi>, balance: Total): CycleUi {
    val spent = ctx.total(Aggregates.bag(soFar, Direction.EXPENSE))
    val income = ctx.total(Aggregates.bag(soFar, Direction.INCOME))
    val prevSoFar = ctx.total(Aggregates.bag(previousSoFar, Direction.EXPENSE))
    val prevTotal = ctx.total(Aggregates.bag(previousTotal, Direction.EXPENSE))
    val overall = budgets.firstOrNull { it.budget.type == BudgetType.EXPENSES }
    val budget = overall?.let { b ->
        val amount = ctx.convert(b.budget.money, b.budget.currency, ctx.baseIso)
        val used = ctx.convert(b.spent, b.budget.currency, ctx.baseIso)
        if (amount == null || used == null) null else OverallBudget(amount, used, Dates.toLocalDate(b.budget.endDate))
    }
    val range = period.range
    // a budget we can't convert must not silently fall back to income-based advice
    val pacing = if (spent.base == null || income.base == null || (overall != null && budget == null)) null else PacingEngine.compute(
        PacingInput(
            start = Dates.toLocalDate(range.from), endExclusive = Dates.toLocalDate(range.to), today = today,
            spent = spent.base, income = income.base,
            previousSpentSoFar = prevSoFar.base, previousSpentTotal = prevTotal.base.takeIf { !prevTotal.isEmpty },
            budget = budget, balance = balance.base,
        ),
    )
    return CycleUi(period, today, spent, prevSoFar, Aggregates.percentChange(spent.base, prevSoFar.base), pacing, earned = income)
}
