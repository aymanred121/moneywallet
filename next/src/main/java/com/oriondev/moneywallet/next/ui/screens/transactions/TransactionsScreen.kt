@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.oriondev.moneywallet.next.ui.screens.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.TransactionRow
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.ui.components.AppTopBar
import com.oriondev.moneywallet.next.ui.components.EmptyState
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.appBarScrollBehavior
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.components.transactions.MonthPickerDialog
import com.oriondev.moneywallet.next.ui.theme.Motion
import com.oriondev.moneywallet.next.ui.components.fabVisibility

@Composable
fun TransactionsScreen(onAdd: (Long?) -> Unit, onOpen: (Long) -> Unit, onTransfer: () -> Unit) {
    val vm = containerViewModel { TransactionsViewModel(it) }
    val ctx by vm.ctx.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val selectedWalletId by vm.selectedWalletId.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val pendingDelete by vm.pendingDelete.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var showMonthPicker by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(true) }
    var fabVisible by remember { mutableStateOf(true) }
    val scrollBehavior = appBarScrollBehavior()

    // the ViewModel owns the delete; the snackbar only decides between undo and committing early
    LaunchedEffect(pendingDelete) {
        val id = pendingDelete ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Transaction deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete(id) else vm.commitPending(id)
    }
    DisposableEffect(vm) { onDispose { vm.commitPending() } }
    val delete: (TransactionRow) -> Unit = { vm.delete(it.id) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                AppTopBar("Transactions", scrollBehavior) {
                    IconButton(onClick = { vm.setSearching(true) }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                    IconButton(onClick = onTransfer) { Icon(Icons.Default.SwapHoriz, contentDescription = "New transfer") }
                }
            },
            floatingActionButton = {
                androidx.compose.animation.AnimatedVisibility(
                    visible = fabVisible,
                    enter = androidx.compose.animation.scaleIn(Motion.spatialFast()) + androidx.compose.animation.fadeIn(Motion.effects()),
                    exit = androidx.compose.animation.scaleOut(Motion.spatialFast()) + androidx.compose.animation.fadeOut(Motion.effects()),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { onAdd(selectedWalletId) },
                        expanded = fabExpanded,
                        icon = { Icon(Icons.Default.Add, contentDescription = "Add transaction") },
                        text = { Text("Add transaction") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                MonthSwitcher(period, onPrevious = vm::previousMonth, onNext = vm::nextMonth, onPick = { showMonthPicker = true })
                // a lone wallet needs no filter; keep the row while a filter is set so it can be cleared
                if (wallets.size > 1 || selectedWalletId != null) WalletChipRow(wallets = wallets, selectedId = selectedWalletId, onSelect = vm::selectWallet)
                AnimatedContent(targetState = period, transitionSpec = { monthTransition() }, label = "month", modifier = Modifier.fillMaxSize()) { p ->
                    val ui by vm.monthUi(p).collectAsStateWithLifecycle()
                    val listState = rememberLazyListState()
                    if (p == period) {
                        LaunchedEffect(listState) {
                            snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24 }.collect { fabExpanded = it }
                        }
                        LaunchedEffect(listState) { listState.fabVisibility().collect { fabVisible = it } }
                    }
                    MonthPage(ui, ctx, listState, onOpen, delete, onAdd = { onAdd(selectedWalletId) })
                }
            }
        }

        AnimatedVisibility(visible = searching, enter = fadeIn(Motion.effects()), exit = fadeOut(Motion.effects())) {
            SearchOverlay(query, { vm.query.value = it }, { vm.setSearching(false) }, searchResults, ctx, onOpen, delete)
        }
    }

    if (showMonthPicker) MonthPickerDialog(initial = period.month, onDismiss = { showMonthPicker = false }, onPick = vm::setMonth)
}

/** Later months come in from the end side, earlier ones from the start (mirrored in RTL). */
private fun AnimatedContentTransitionScope<Period.Month>.monthTransition(): ContentTransform {
    val cmp = targetState.month.compareTo(initialState.month)
    if (cmp == 0) return fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects())
    val towards = if (cmp > 0) SlideDirection.Start else SlideDirection.End
    return (slideIntoContainer(towards, Motion.slide) { it / 4 } + fadeIn(Motion.effects())) togetherWith
        (slideOutOfContainer(towards, Motion.slide) { it / 4 } + fadeOut(Motion.effects()))
}

@Composable
private fun MonthSwitcher(period: Period.Month, onPrevious: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
        TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Text(Dates.monthLabel(period.month, period.startDay), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month") }
    }
}

@Composable
private fun WalletChipRow(wallets: List<WalletWithBalance>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        item("all") { FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("All wallets") }) }
        items(wallets, key = { it.id }) { w ->
            FilterChip(
                selected = selectedId == w.id,
                onClick = { onSelect(if (selectedId == w.id) null else w.id) },
                label = { Text(w.name) },
                leadingIcon = { SpecIcon(w.icon, size = 18.dp) },
            )
        }
    }
}

@Composable
private fun MonthPage(
    ui: MonthUi,
    ctx: CurrencyContext,
    listState: LazyListState,
    onOpen: (Long) -> Unit,
    onDelete: (TransactionRow) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item("summary") { MonthSummaryCard(ui, ctx, Modifier.animateItem()) }
        if (ui.loaded && ui.sections.isEmpty()) {
            item("empty") {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong, title = "No transactions",
                    message = "Nothing recorded this month yet. Add your first expense or income.",
                    action = "Add transaction", onAction = onAdd, modifier = Modifier.animateItem(),
                )
            }
        }
        ui.sections.forEach { section -> daySection(section, ctx, onOpen, onDelete) }
    }
}

fun androidx.compose.foundation.lazy.LazyListScope.daySection(section: DaySection, ctx: CurrencyContext, onOpen: (Long) -> Unit, onDelete: (TransactionRow) -> Unit) {
    stickyHeader(key = "day-${section.dayStart}") { DayHeader(section, ctx, Modifier.animateItem()) }
    items(section.rows, key = { it.id }) { row ->
        DismissibleTransactionRow(row, ctx, onClick = { onOpen(row.id) }, onDelete = { onDelete(row) }, modifier = Modifier.animateItem())
    }
}

@Composable
private fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    results: List<DaySection>,
    ctx: CurrencyContext,
    onOpen: (Long) -> Unit,
    onDelete: (TransactionRow) -> Unit,
) {
    BackHandler(onBack = onClose)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query, onQueryChange = onQueryChange, onSearch = {}, expanded = true, onExpandedChange = { if (!it) onClose() },
                placeholder = { Text("Search description, category or note") },
                leadingIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") } },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, contentDescription = "Clear") } },
                modifier = Modifier.focusRequester(focus),
            )
        },
        expanded = true,
        onExpandedChange = { if (!it) onClose() },
        modifier = Modifier.fillMaxSize(),
    ) {
        if (query.isBlank()) {
            Text("Type to search all transactions", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (results.isEmpty()) {
            EmptyState(icon = Icons.Default.Search, title = "No matches", message = "Nothing matches \"$query\".")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                results.forEach { section -> daySection(section, ctx, onOpen, onDelete) }
            }
        }
    }
}
