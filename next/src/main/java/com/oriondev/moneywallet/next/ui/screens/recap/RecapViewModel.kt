package com.oriondev.moneywallet.next.ui.screens.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.analytics.Aggregates
import com.oriondev.moneywallet.next.domain.analytics.CategoryShare
import com.oriondev.moneywallet.next.domain.analytics.Comparison
import com.oriondev.moneywallet.next.domain.analytics.PeriodSelection
import com.oriondev.moneywallet.next.domain.analytics.Total
import com.oriondev.moneywallet.next.domain.analytics.from
import com.oriondev.moneywallet.next.domain.analytics.to
import com.oriondev.moneywallet.next.domain.analytics.total
import com.oriondev.moneywallet.next.domain.insights.DayStats
import com.oriondev.moneywallet.next.domain.insights.RecurringCharge
import com.oriondev.moneywallet.next.ui.screens.review.DayTotal
import com.oriondev.moneywallet.next.ui.screens.review.monthlyTotal
import com.oriondev.moneywallet.next.ui.screens.review.recurringCharges
import com.oriondev.moneywallet.next.ui.screens.review.resolvedPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Big numbers for a cycle or a year; an open period is summarised "so far". */
@OptIn(ExperimentalCoroutinesApi::class)
class RecapViewModel(container: AppContainer, period: Period) : ViewModel() {
    private val db = container.db
    private val repo = container.repo
    private val resolved = resolvedPeriod(MutableStateFlow(PeriodSelection.Fixed(period)), container.prefs.settings)
    private val walletId = container.prefs.settings.map { it.selectedWalletId }.distinctUntilChanged()

    data class UiState(
        val loading: Boolean = true,
        val ctx: CurrencyContext? = null,
        val period: Period = Period.currentMonth(),
        val partial: Boolean = false,
        val spent: Total = Total.EMPTY,
        val earned: Total = Total.EMPTY,
        val change: Int? = null,
        val topCategory: CategoryShare? = null,
        val biggestDay: DayTotal? = null,
        /** Share of income kept, whole percent; null without income or rates. */
        val savingsRate: Int? = null,
        val noSpendDays: Int = 0,
        val trackedDays: Int = 0,
        /** Subscriptions and bills over the period (monthly cost × months covered). */
        val subscriptions: Total = Total.EMPTY,
        val subscriptionCount: Int = 0,
    ) {
        val isYear: Boolean get() = period is Period.Year
        val noun: String get() = when (period) { is Period.Week -> "week"; is Period.Year -> "year"; is Period.Month -> "month"; else -> "period" }
        val title: String get() = if (isYear) "${period.label} in review" else "Recap · ${period.label}"
    }

    private data class Inputs(val period: Period, val today: LocalDate, val walletId: Long?)

    val state: StateFlow<UiState> = combine(resolved, walletId) { (p, today), w -> Inputs(p, today, w) }.flatMapLatest { i ->
        val tx = db.transactions()
        val win = Comparison.windows(i.period, i.today)
        val cur = win.current
        // subscriptions as they stood at the end of the period (or today while it runs)
        val anchor = minOf(i.today, Dates.toLocalDate(i.period.range.to).minusDays(1))
        combine(
            combine(tx.observeTotals(i.walletId, cur.from, cur.to), tx.observeTotals(i.walletId, win.previous.from, win.previous.to)) { a, b -> a to b },
            tx.observeCategorySums(i.walletId, cur.from, cur.to, Direction.EXPENSE),
            tx.observeDailySums(i.walletId, cur.from, cur.to),
            combine(db.history().observeFirstDate(i.walletId), recurringCharges(db, i.walletId, anchor)) { f, r -> f to r },
            combine(repo.currencyContext, repo.categories) { c, cats -> c to cats },
        ) { (totals, prevTotals), cats, daily, (first, charges), (ctx, categories) ->
            val spent = ctx.total(Aggregates.bag(totals, Direction.EXPENSE))
            val earned = ctx.total(Aggregates.bag(totals, Direction.INCOME))
            val prevSpent = ctx.total(Aggregates.bag(prevTotals, Direction.EXPENSE))
            val days = Aggregates.dailyBags(daily, Direction.EXPENSE).mapValues { ctx.total(it.value) }
            val start = Dates.toLocalDate(cur.from); val end = Dates.toLocalDate(cur.to)
            val firstDay = first?.let(Dates::toLocalDate)
            val spendingDays = days.filter { !it.value.isEmpty }.keys
            val savings = if (earned.base != null && spent.base != null && earned.base > 0) (((earned.base - spent.base) * 100.0) / earned.base).roundToInt() else null
            UiState(
                loading = false, ctx = ctx, period = i.period, partial = win.partial,
                spent = spent, earned = earned, change = Aggregates.percentChange(spent.base, prevSpent.base),
                topCategory = Aggregates.shares(cats, categories, ctx, limit = 1).firstOrNull(),
                biggestDay = days.entries.filter { it.value.base != null }.maxByOrNull { it.value.base!! }?.let { DayTotal(it.key, it.value) },
                savingsRate = savings,
                noSpendDays = DayStats.noSpendDays(start, end, spendingDays, firstDay),
                trackedDays = DayStats.trackedDays(start, end, firstDay),
                subscriptions = subscriptionsOver(charges, ctx, Dates.daysBetween(start, end)),
                subscriptionCount = charges.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(period = period))

    private fun subscriptionsOver(charges: List<RecurringCharge>, ctx: CurrencyContext, days: Long): Total {
        val monthly = monthlyTotal(charges, ctx)
        val months = days / (365.25 / 12)
        return Total(MoneyBag(monthly.bag.entries.mapValues { (it.value * months).roundToLong() }), monthly.base?.let { (it * months).roundToLong() })
    }
}
