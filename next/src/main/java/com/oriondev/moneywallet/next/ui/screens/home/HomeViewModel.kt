package com.oriondev.moneywallet.next.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Clock
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.data.db.TransactionRow
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.analytics.Total
import com.oriondev.moneywallet.next.domain.analytics.total
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {
    private val db = container.db
    private val repo = container.repo
    private val prefs = container.prefs

    /** [ctx] is null until the first emission (the screen shows a placeholder). */
    data class UiState(
        val ctx: CurrencyContext? = null,
        val wallets: List<WalletWithBalance> = emptyList(),
        val selectedWalletId: Long? = null,
        val balance: Total = Total.EMPTY,
        val cycle: CycleUi? = null,
        val budgets: List<BudgetUi> = emptyList(),
        val recent: List<TransactionRow> = emptyList(),
        val hideBalances: Boolean = false,
    )

    // re-keyed at midnight too, so the dashboard rolls into a new payday cycle on its own
    private val sums: Flow<Pair<CycleSums, List<TransactionRow>>> = combine(
        prefs.settings.map { it.selectedWalletId to it.firstDayOfMonth.coerceIn(1, 28) }.distinctUntilChanged(),
        Clock.today,
    ) { f, today -> Triple(f.first, f.second, today) }.flatMapLatest { (walletId, startDay, today) ->
        combine(cycleSums(db, walletId, startDay, today), db.transactions().observeRecentRows(walletId, 5)) { c, r -> c to r }
    }

    // all active budgets: the card shows the first few, pacing needs the overall expense one wherever it sorts
    private val budgets: Flow<List<BudgetUi>> = activeBudgets(db, repo.currencyContext, Clock.today.map { System.currentTimeMillis() }, Int.MAX_VALUE, repo.categories)

    val state: StateFlow<UiState> = combine(prefs.settings, repo.currencyContext, repo.wallets(), sums, budgets) { settings, ctx, wallets, (cycle, recent), budgets ->
        val selected = settings.selectedWalletId
        val counted = wallets.filter { !it.archived && (if (selected == null) it.countInTotal else it.id == selected) }
        val balance = ctx.total(MoneyBag().also { bag -> counted.forEach { bag.add(it.currency, it.balance) } })
        UiState(
            ctx = ctx,
            wallets = wallets.filter { !it.archived },
            selectedWalletId = selected,
            balance = balance,
            cycle = cycle.toUi(ctx, budgets, balance),
            budgets = budgets.take(3),
            recent = recent,
            hideBalances = settings.hideBalances,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun selectWallet(id: Long?) { viewModelScope.launch { prefs.setSelectedWallet(id) } }
    fun toggleHidden() { viewModelScope.launch { prefs.setHideBalances(!state.value.hideBalances) } }
}
