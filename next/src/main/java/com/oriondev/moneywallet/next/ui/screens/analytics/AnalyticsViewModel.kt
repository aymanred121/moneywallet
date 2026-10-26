package com.oriondev.moneywallet.next.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Clock
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.data.db.CategorySum
import com.oriondev.moneywallet.next.data.db.CurrencySum
import com.oriondev.moneywallet.next.data.db.DailySum
import com.oriondev.moneywallet.next.data.db.DescriptionSum
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.analytics.Aggregates
import com.oriondev.moneywallet.next.domain.analytics.Bucket
import com.oriondev.moneywallet.next.domain.analytics.CategoryShare
import com.oriondev.moneywallet.next.domain.analytics.Comparison
import com.oriondev.moneywallet.next.domain.analytics.NetWorth
import com.oriondev.moneywallet.next.domain.analytics.PeriodSelection
import com.oriondev.moneywallet.next.domain.analytics.Total
import com.oriondev.moneywallet.next.domain.analytics.Trend
import com.oriondev.moneywallet.next.domain.analytics.from
import com.oriondev.moneywallet.next.domain.analytics.to
import com.oriondev.moneywallet.next.domain.analytics.total
import com.oriondev.moneywallet.next.domain.insights.RecurringCharge
import com.oriondev.moneywallet.next.ui.screens.review.monthlyTotal
import com.oriondev.moneywallet.next.ui.screens.review.recurringCharges
import com.oriondev.moneywallet.next.ui.screens.review.resolvedPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class PeriodKind { WEEK, MONTH, YEAR, CUSTOM }

fun Period.kind(): PeriodKind = when (this) {
    is Period.Week -> PeriodKind.WEEK
    is Period.Month -> PeriodKind.MONTH
    is Period.Year -> PeriodKind.YEAR
    is Period.Custom -> PeriodKind.CUSTOM
}

data class TrendBar(val label: String, val total: Total, val isCurrent: Boolean)
data class Merchant(val name: String, val total: Total, val count: Int)
data class Subscriptions(val charges: List<RecurringCharge>, val monthly: Total)
/** Balance at the end of each cycle, base currency (null where a rate is missing). */
data class NetWorthUi(val labels: List<String>, val values: List<Long?>, val multiCurrency: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(container: AppContainer) : ViewModel() {
    private val db = container.db
    private val repo = container.repo
    private val prefs = container.prefs

    private val selection = MutableStateFlow<PeriodSelection>(PeriodSelection.Relative(PeriodSelection.Kind.MONTH))
    /** Follows midnight and the month start day: "this month" stays this payday month. */
    private val resolved = resolvedPeriod(selection, prefs.settings)
    val direction = MutableStateFlow(Direction.EXPENSE)

    data class UiState(
        val loading: Boolean = true,
        val ctx: CurrencyContext? = null,
        val period: Period = Period.currentMonth(),
        val direction: Int = Direction.EXPENSE,
        val wallets: List<WalletWithBalance> = emptyList(),
        val selectedWalletId: Long? = null,
        val shares: List<CategoryShare> = emptyList(),
        /** Whole period, for the breakdown. */
        val total: Total = Total.EMPTY,
        /** Like-for-like pair (see [Comparison]): the elapsed part of the period vs the same days before. */
        val comparedTotal: Total = Total.EMPTY,
        val previousTotal: Total = Total.EMPTY,
        val bars: List<TrendBar> = emptyList(),
        val spent: Total = Total.EMPTY,
        val earned: Total = Total.EMPTY,
        val previousSpent: Total = Total.EMPTY,
        val previousEarned: Total = Total.EMPTY,
        val partial: Boolean = false,
        val merchants: List<Merchant> = emptyList(),
        val transactionCount: Int = 0,
        val daysElapsed: Int = 1,
    ) {
        val perDay: Long? get() = comparedTotal.base?.let { it / daysElapsed.coerceAtLeast(1) }
        val perTransaction: Long? get() = total.base?.let { if (transactionCount == 0) 0L else it / transactionCount }
        val change: Int? get() = Aggregates.percentChange(comparedTotal.base, previousTotal.base)
    }

    private data class Raw(
        val period: Period, val today: LocalDate, val direction: Int, val walletId: Long?, val partial: Boolean,
        val totals: List<CurrencySum>, val comparedTotals: List<CurrencySum>, val previousTotals: List<CurrencySum>,
        val categories: List<CategorySum>, val daily: List<DailySum>, val merchants: List<DescriptionSum>, val count: Int,
    )

    private val walletId = prefs.settings.map { it.selectedWalletId }.distinctUntilChanged()

    private val raw: Flow<Raw> = combine(resolved, direction, walletId) { (p, today), d, w -> listOf(p, today, d, w) }.flatMapLatest { args ->
        val p = args[0] as Period; val today = args[1] as LocalDate; val d = args[2] as Int; val w = args[3] as Long?
        val tx = db.transactions()
        val win = Comparison.windows(p, today)
        combine(
            tx.observeTotals(w, p.range.from, p.range.to),
            tx.observeTotals(w, win.current.from, win.current.to),
            tx.observeTotals(w, win.previous.from, win.previous.to),
            tx.observeCategorySums(w, p.range.from, p.range.to, d),
            combine(
                tx.observeDailySums(w, p.range.from, p.range.to),
                tx.observeTopDescriptions(w, p.range.from, p.range.to, 8),
                tx.observeTransactionCount(w, p.range.from, p.range.to, d),
            ) { daily, m, c -> Triple(daily, m, c) },
        ) { t, ct, pt, c, (daily, m, count) -> Raw(p, today, d, w, win.partial, t, ct, pt, c, daily, m, count) }
    }

    val state: StateFlow<UiState> = combine(raw, repo.currencyContext, repo.wallets(), repo.categories) { r, ctx, wallets, cats ->
        val d = r.direction
        val buckets: List<Bucket> = Trend.buckets(r.period, r.daily, d)
        val spent = ctx.total(Aggregates.bag(r.comparedTotals, Direction.EXPENSE))
        val earned = ctx.total(Aggregates.bag(r.comparedTotals, Direction.INCOME))
        val prevSpent = ctx.total(Aggregates.bag(r.previousTotals, Direction.EXPENSE))
        val prevEarned = ctx.total(Aggregates.bag(r.previousTotals, Direction.INCOME))
        val merchants = r.merchants.groupBy { it.description.lowercase() }.map { (_, rows) ->
            val bag = MoneyBag(); rows.forEach { bag.add(it.currency, it.total) }
            Merchant(rows.first().description, ctx.total(bag), rows.sumOf { it.count })
        }.sortedByDescending { it.total.base ?: Long.MIN_VALUE }.take(5)
        UiState(
            loading = false, ctx = ctx, period = r.period, direction = d,
            wallets = wallets.filter { !it.archived }, selectedWalletId = r.walletId,
            shares = Aggregates.shares(r.categories, cats, ctx),
            total = ctx.total(Aggregates.bag(r.totals, d)),
            comparedTotal = if (d == Direction.EXPENSE) spent else earned,
            previousTotal = if (d == Direction.EXPENSE) prevSpent else prevEarned,
            bars = buckets.map { TrendBar(it.label, ctx.total(it.bag), it.isCurrent) },
            spent = spent, earned = earned, previousSpent = prevSpent, previousEarned = prevEarned, partial = r.partial,
            merchants = if (d == Direction.EXPENSE) merchants else emptyList(),
            transactionCount = r.count,
            daysElapsed = daysElapsed(r.period, r.today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** Subscriptions and bills of the selected wallet scope, most expensive first. */
    val subscriptions: StateFlow<Subscriptions?> = combine(Clock.today, walletId) { t, w -> t to w }
        .flatMapLatest { (today, w) -> recurringCharges(db, w, today) }
        .combine(repo.currencyContext) { list, ctx ->
            Subscriptions(list.sortedByDescending { ctx.toBaseOrNull(it.monthlyCost, it.currency) ?: 0L }, monthlyTotal(list, ctx))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Counted wallets' total balance at each of the last 12 payday cycle ends, at today's rates. */
    val netWorth: StateFlow<NetWorthUi?> = combine(Clock.today, prefs.settings.map { it.firstDayOfMonth.coerceIn(1, 28) }.distinctUntilChanged()) { t, s -> t to s }
        .flatMapLatest { (today, startDay) ->
            val cycles = NetWorth.cycles(today, startDay)
            val from = cycles.first().range.from
            val h = db.history()
            combine(h.observeStartMoney(), h.observeFlowBefore(from), h.observeDailyFlow(from, Dates.startOfDay(today.plusDays(1))), repo.currencyContext) { start, before, daily, ctx ->
                val bags = NetWorth.balances(cycles, today, start, before, daily)
                NetWorthUi(
                    labels = cycles.map { Dates.shortMonthLabel(it.month) },
                    values = bags.map { ctx.toBase(it) },
                    multiCurrency = bags.any { bag -> bag.entries.keys.any { it != ctx.baseIso } },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previous() { selection.value = selection.value.shift(-1) }
    fun next() { selection.value = selection.value.shift(1) }

    fun setKind(kind: PeriodKind) {
        selection.value = when (kind) {
            PeriodKind.WEEK -> PeriodSelection.Relative(PeriodSelection.Kind.WEEK)
            PeriodKind.MONTH -> PeriodSelection.Relative(PeriodSelection.Kind.MONTH)
            PeriodKind.YEAR -> PeriodSelection.Relative(PeriodSelection.Kind.YEAR)
            PeriodKind.CUSTOM -> state.value.period.let { PeriodSelection.Fixed(Period.Custom(Dates.toLocalDate(it.range.from), Dates.toLocalDate(it.range.last))) }
        }
    }

    fun setCustom(from: LocalDate, to: LocalDate) { selection.value = PeriodSelection.Fixed(Period.Custom(minOf(from, to), maxOf(from, to))) }
    fun setDirection(d: Int) { direction.value = d }
    fun selectWallet(id: Long?) { viewModelScope.launch { prefs.setSelectedWallet(id) } }

    companion object {
        /** Days of the period that have already happened (at least 1). */
        fun daysElapsed(p: Period, today: LocalDate = LocalDate.now()): Int {
            val start = Dates.toLocalDate(p.range.from)
            val end = Dates.toLocalDate(p.range.to)
            val effectiveEnd = if (today.isBefore(end)) today.plusDays(1) else end
            return Dates.daysBetween(start, effectiveEnd).toInt().coerceAtLeast(1)
        }
    }
}
