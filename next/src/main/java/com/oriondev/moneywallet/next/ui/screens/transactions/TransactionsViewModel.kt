@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.oriondev.moneywallet.next.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Clock
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.CurrencySum
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.TransactionRow
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** One calendar day of rows with its net per wallet currency. */
data class DaySection(val dayStart: Long, val label: String, val rows: List<TransactionRow>, val net: Map<String, Long>)

data class MonthUi(
    val loaded: Boolean = false,
    val sections: List<DaySection> = emptyList(),
    val income: Map<String, Long> = emptyMap(),
    val expense: Map<String, Long> = emptyMap(),
) {
    val net: Map<String, Long> get() = (income.keys + expense.keys).associateWith { (income[it] ?: 0L) - (expense[it] ?: 0L) }
}

val EMPTY_CTX = CurrencyContext(emptyMap(), emptyMap(), "USD")

class TransactionsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repo
    private val db = container.db
    private val prefs = container.prefs

    val ctx: StateFlow<CurrencyContext> = repo.currencyContext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EMPTY_CTX)
    val wallets: StateFlow<List<WalletWithBalance>> = repo.wallets().map { list -> list.filter { !it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedWalletId: StateFlow<Long?> = prefs.settings.map { it.selectedWalletId }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val startDay = prefs.settings.map { it.firstDayOfMonth }.distinctUntilChanged()
    private val today = Clock.today.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())
    /** Month the user browsed to; null follows the current (payday) month and rolls over with it. */
    private val chosen = MutableStateFlow<YearMonth?>(null)
    val period: StateFlow<Period.Month> = combine(startDay, today, chosen, ::periodFor)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Period.currentMonth())
    val query = MutableStateFlow("")
    val searching = MutableStateFlow(false)

    /** Rows swiped away: pending undo, or deleted (kept so a row can't flash back before Room re-emits). */
    private val hidden = MutableStateFlow<Set<Long>>(emptySet())
    private val _pendingDelete = MutableStateFlow<Long?>(null)
    /** Row waiting on its Undo snackbar. Committed when the snackbar ends, the screen leaves or the VM dies. */
    val pendingDelete: StateFlow<Long?> = _pendingDelete.asStateFlow()
    private val monthCache = HashMap<Period.Month, StateFlow<MonthUi>>()

    /** Keyed on the whole period, so a changed first day of month gets fresh ranges. */
    fun monthUi(p: Period.Month): StateFlow<MonthUi> = monthCache.getOrPut(p) {
        val range = p.range
        val from = range.first
        val to = range.last + 1
        val data = selectedWalletId.flatMapLatest { w ->
            combine(db.transactions().observeRows(w, from, to, null, ""), db.transactions().observeTotals(w, from, to)) { rows, totals -> rows to totals }
        }
        combine(data, hidden) { (rows, totals), hiddenIds ->
            MonthUi(
                loaded = true,
                sections = groupByDay(rows.filter { it.id !in hiddenIds }),
                income = totals.sumBy(Direction.INCOME),
                expense = totals.sumBy(Direction.EXPENSE),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthUi())
    }

    val searchResults: StateFlow<List<DaySection>> = combine(query.debounce(150), selectedWalletId) { q, w -> q.trim() to w }
        .flatMapLatest { (q, w) ->
            if (q.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
            else combine(db.transactions().observeRows(w, 0L, Long.MAX_VALUE, null, q), hidden) { rows, h -> groupByDay(rows.filter { it.id !in h }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMonth(m: YearMonth) { chosen.value = m.takeIf { it != Dates.monthAnchor(today.value, period.value.startDay) } }
    fun previousMonth() = setMonth(period.value.month.minusMonths(1))
    fun nextMonth() = setMonth(period.value.month.plusMonths(1))
    fun setSearching(on: Boolean) { searching.value = on; if (!on) query.value = "" }
    fun selectWallet(id: Long?) = viewModelScope.launch { prefs.setSelectedWallet(id) }

    /** Hides the row at once; the previous pending delete (if any) is committed. */
    fun delete(id: Long) {
        commitPending()
        hidden.update { it + id }
        _pendingDelete.value = id
    }

    fun undoDelete(id: Long) { if (_pendingDelete.compareAndSet(id, null)) hidden.update { it - id } }

    /** Runs on the app scope so it completes even while this ViewModel is being cleared. */
    fun commitPending(id: Long? = _pendingDelete.value) {
        if (id == null || !_pendingDelete.compareAndSet(id, null)) return
        container.scope.launch { repo.deleteTransaction(id) }
    }

    override fun onCleared() = commitPending()

    private fun List<CurrencySum>.sumBy(direction: Int): Map<String, Long> =
        filter { it.direction == direction }.groupBy { it.currency }.mapValues { (_, v) -> v.sumOf { it.total } }

    companion object {
        fun periodFor(startDay: Int, today: LocalDate, chosen: YearMonth?): Period.Month =
            Period.Month(chosen ?: Dates.monthAnchor(today, startDay), startDay)

        fun groupByDay(rows: List<TransactionRow>): List<DaySection> = rows
            .groupBy { Dates.startOfDay(Dates.toLocalDate(it.date)) }
            .entries.sortedByDescending { it.key }
            .map { (day, list) ->
                val net = HashMap<String, Long>()
                list.forEach { r -> if (r.confirmed && r.countInTotal) net[r.walletCurrency] = (net[r.walletCurrency] ?: 0L) + if (r.direction == Direction.INCOME) r.money else -r.money }
                DaySection(day, Dates.relativeDayLabel(day), list, net)
            }
    }
}
