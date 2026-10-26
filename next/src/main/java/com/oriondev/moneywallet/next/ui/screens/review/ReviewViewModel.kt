package com.oriondev.moneywallet.next.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.CategorySum
import com.oriondev.moneywallet.next.data.db.CurrencySum
import com.oriondev.moneywallet.next.data.db.DailySum
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.ExpenseRow
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.analytics.Aggregates
import com.oriondev.moneywallet.next.domain.analytics.CategoryShare
import com.oriondev.moneywallet.next.domain.analytics.Comparison
import com.oriondev.moneywallet.next.domain.analytics.ComparisonWindows
import com.oriondev.moneywallet.next.domain.analytics.PeriodSelection
import com.oriondev.moneywallet.next.domain.analytics.Total
import com.oriondev.moneywallet.next.domain.analytics.from
import com.oriondev.moneywallet.next.domain.analytics.to
import com.oriondev.moneywallet.next.domain.analytics.total
import com.oriondev.moneywallet.next.domain.insights.BiggestExpense
import com.oriondev.moneywallet.next.domain.insights.BudgetStatus
import com.oriondev.moneywallet.next.domain.insights.CategoryDelta
import com.oriondev.moneywallet.next.domain.insights.DayStats
import com.oriondev.moneywallet.next.domain.insights.Insight
import com.oriondev.moneywallet.next.domain.insights.InsightEngine
import com.oriondev.moneywallet.next.domain.insights.InsightInput
import com.oriondev.moneywallet.next.domain.insights.RecurringCharge
import com.oriondev.moneywallet.next.ui.screens.home.BudgetUi
import com.oriondev.moneywallet.next.ui.screens.home.DAY_MILLIS
import com.oriondev.moneywallet.next.ui.screens.home.activeBudgets
import com.oriondev.moneywallet.next.ui.screens.home.describe
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
import java.time.LocalDate

data class DayTotal(val day: LocalDate, val total: Total)

/** How far ahead "Coming up" looks for subscriptions and bills. */
private const val COMING_UP_DAYS = 10L

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(container: AppContainer, initial: Period) : ViewModel() {
    private val db = container.db
    private val repo = container.repo
    private val prefs = container.prefs

    private val selection = MutableStateFlow<PeriodSelection>(PeriodSelection.Fixed(initial))
    private val resolved = resolvedPeriod(selection, prefs.settings)

    data class UiState(
        val loading: Boolean = true,
        val ctx: CurrencyContext? = null,
        val period: Period = Period.currentMonth(),
        val spent: Total = Total.EMPTY,
        val earned: Total = Total.EMPTY,
        /** Like-for-like: the same elapsed days of the previous period while this one is running. */
        val previousSpent: Total = Total.EMPTY,
        val partial: Boolean = false,
        val topCategories: List<CategoryShare> = emptyList(),
        val biggest: Pair<ExpenseRow, Total>? = null,
        val topDays: List<DayTotal> = emptyList(),
        /** Days of the period since tracking began (today included). */
        val daysElapsed: Int = 0,
        val daysWithSpending: Int = 0,
        val longestStreak: Int = 0,
        val budgets: List<BudgetUi> = emptyList(),
        val insights: List<Insight> = emptyList(),
        val transactionCount: Int = 0,
        val comingUp: List<RecurringCharge> = emptyList(),
    ) {
        val change: Int? get() = Aggregates.percentChange(spent.base, previousSpent.base)
        val noun: String get() = when (period) { is Period.Week -> "week"; is Period.Year -> "year"; else -> "month" }
        val previousNoun: String get() = if (partial) "at this point last $noun" else "last $noun"
    }

    private data class Raw(
        val period: Period, val windows: ComparisonWindows,
        val totals: List<CurrencySum>, val previousTotals: List<CurrencySum>, val categories: List<CategorySum>, val previousCategories: List<CategorySum>,
        val daily: List<DailySum>, val expenses: List<ExpenseRow>, val firstDate: Long?,
    )

    private val walletId = prefs.settings.map { it.selectedWalletId }.distinctUntilChanged()

    private val raw: Flow<Raw> = combine(resolved, walletId) { (p, today), w -> Triple(p, today, w) }.flatMapLatest { (p, today, w) ->
        val tx = db.transactions()
        val win = Comparison.windows(p, today)
        val cur = win.current; val prev = win.previous
        combine(
            tx.observeTotals(w, cur.from, cur.to),
            tx.observeTotals(w, prev.from, prev.to),
            tx.observeCategorySums(w, cur.from, cur.to, Direction.EXPENSE),
            tx.observeCategorySums(w, prev.from, prev.to, Direction.EXPENSE),
            combine(
                tx.observeDailySums(w, cur.from, cur.to),
                // the period plus enough history for "usual" amounts per category
                db.history().observeExpenses(w, minOf(cur.from, cur.to - 90 * DAY_MILLIS), cur.to),
                db.history().observeFirstDate(w),
            ) { d, e, f -> Triple(d, e, f) },
        ) { t, pt, c, pc, (d, e, f) -> Raw(p, win, t, pt, c, pc, d, e, f) }
    }

    private val comingUp: Flow<List<RecurringCharge>> = combine(resolved.map { it.second }.distinctUntilChanged(), walletId) { t, w -> t to w }
        .flatMapLatest { (today, w) ->
            recurringCharges(db, w, today).map { list -> list.filter { !it.nextDate.isBefore(today) && !it.nextDate.isAfter(today.plusDays(COMING_UP_DAYS)) } }
        }

    private val budgets = activeBudgets(db, repo.currencyContext, resolved.map { (p, _) -> minOf(p.range.last, Dates.now()) }, 5, repo.categories)

    val state: StateFlow<UiState> = combine(raw, repo.currencyContext, repo.categories, budgets, comingUp) { r, ctx, cats, budgets, upcoming ->
        val win = r.windows
        val spent = ctx.total(Aggregates.bag(r.totals, Direction.EXPENSE))
        val earned = ctx.total(Aggregates.bag(r.totals, Direction.INCOME))
        val prevSpent = ctx.total(Aggregates.bag(r.previousTotals, Direction.EXPENSE))
        val shares = Aggregates.shares(r.categories, cats, ctx)
        val prevBags = Aggregates.categoryBags(r.previousCategories)
        val daily = Aggregates.dailyBags(r.daily, Direction.EXPENSE).mapValues { ctx.total(it.value) }
        val start = Dates.toLocalDate(win.current.from)
        val endExclusive = Dates.toLocalDate(win.current.to)
        val first = r.firstDate?.let(Dates::toLocalDate)
        val spendingDays = daily.filter { (it.value.base ?: 0L) > 0L || !it.value.isEmpty }.keys
        val tracked = DayStats.trackedDays(start, endExclusive, first)
        val streak = DayStats.longestNoSpendStreak(start, endExclusive, spendingDays, first)
        val current = r.expenses.filter { it.date >= win.current.from }
        val biggest = biggestExpense(current, ctx)
        val topDays = daily.entries.map { DayTotal(it.key, it.value) }.sortedByDescending { it.total.base ?: Long.MIN_VALUE }.take(3)
        val unusual = unusualExpense(current, r.expenses, spent.base, ctx)

        val input = InsightInput(
            periodNoun = when (r.period) { is Period.Week -> "week"; is Period.Year -> "year"; else -> "month" },
            currency = ctx.base,
            spent = spent.base, earned = earned.base, previousSpent = prevSpent.base,
            categories = shares.mapNotNull { s -> s.total.base?.let { CategoryDelta(s.name, it, prevBags[s.categoryId]?.let(ctx::toBase)) } },
            daysElapsed = tracked, daysWithSpending = spendingDays.size,
            topDay = topDays.firstOrNull()?.let { t -> t.total.base?.let { t.day to it } },
            longestStreakWithoutSpending = streak,
            biggest = biggest?.let { (row, total) -> total.base?.let { BiggestExpense(it, row.description, row.categoryName) } },
            budgets = budgets.map { BudgetStatus(it.name, it.spent, it.budget.money) },
            transactionCount = current.size,
            partial = win.partial,
            unusual = unusual,
        )
        UiState(
            loading = false, ctx = ctx, period = r.period, spent = spent, earned = earned, previousSpent = prevSpent, partial = win.partial,
            topCategories = shares.take(3), biggest = biggest, topDays = topDays,
            daysElapsed = tracked, daysWithSpending = spendingDays.size, longestStreak = streak,
            budgets = budgets, insights = InsightEngine.generate(input), transactionCount = current.size, comingUp = upcoming,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(period = initial))

    fun showWeek() { selection.value = PeriodSelection.Relative(PeriodSelection.Kind.WEEK) }
    fun showMonth() { selection.value = PeriodSelection.Relative(PeriodSelection.Kind.MONTH) }
    fun previous() { selection.value = selection.value.shift(-1) }
    fun next() { selection.value = selection.value.shift(1) }

    /** Plain-text summary for the share sheet. */
    fun shareText(s: UiState): String {
        val ctx = s.ctx ?: return ""
        val sb = StringBuilder()
        sb.appendLine("My ${s.noun} in review · ${s.period.label}")
        sb.appendLine("Spent: ${s.spent.describe(ctx)}")
        if (!s.earned.isEmpty) sb.appendLine("Earned: ${s.earned.describe(ctx)}")
        s.change?.let { sb.appendLine(if (it <= 0) "${-it}% less than ${s.previousNoun}" else "$it% more than ${s.previousNoun}") }
        if (s.topCategories.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Top categories:")
            s.topCategories.forEach { sb.appendLine("• ${it.name}: ${it.total.describe(ctx)} (${(it.fraction * 100).toInt()}%)") }
        }
        s.biggest?.let { (row, total) -> sb.appendLine("Biggest expense: ${total.describe(ctx)}${row.description.takeIf { it.isNotBlank() }?.let { " at $it" } ?: ""}") }
        if (s.insights.isNotEmpty()) {
            sb.appendLine()
            s.insights.forEach { sb.appendLine("• ${it.text}") }
        }
        sb.appendLine()
        sb.append("Tracked with MoneyWallet")
        return sb.toString()
    }
}

internal fun fmt(minor: Long, ctx: CurrencyContext) = MoneyFormat.format(minor, ctx.base)
