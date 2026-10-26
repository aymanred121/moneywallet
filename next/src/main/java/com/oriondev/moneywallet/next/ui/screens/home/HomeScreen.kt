package com.oriondev.moneywallet.next.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.ui.Routes
import androidx.compose.runtime.setValue
import com.oriondev.moneywallet.next.ui.components.fabVisibility
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.remember
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.screens.capture.CaptureInboxBanner
import com.oriondev.moneywallet.next.ui.screens.goals.GoalsHomeCard
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.theme.Motion
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val vm = containerViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var fabVisible by remember { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(listState) { listState.fabVisibility().collect { fabVisible = it } }
    // optional cards from other features: only take a slot (and its spacing) when they have content
    val container = LocalAppContainer.current
    val pendingCaptures by remember(container) { container.db.captures().observePendingCount() }.collectAsStateWithLifecycle(initialValue = 0)
    val hasGoals by remember(container) { container.db.goals().observeAll().map { list -> list.any { !it.archived } } }.collectAsStateWithLifecycle(initialValue = false)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(greeting(), style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            androidx.compose.animation.AnimatedVisibility(
                visible = fabVisible,
                enter = androidx.compose.animation.scaleIn(Motion.spatialFast()) + androidx.compose.animation.fadeIn(Motion.effects()),
                exit = androidx.compose.animation.scaleOut(Motion.spatialFast()) + androidx.compose.animation.fadeOut(Motion.effects()),
            ) {
                FloatingActionButton(onClick = { onNavigate(Routes.addTransaction(state.selectedWalletId, Direction.EXPENSE)) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        },
    ) { padding ->
        val ctx = state.ctx
        if (ctx == null) {
            LoadingPlaceholder(Modifier.padding(padding))
            return@Scaffold
        }
        // four calm blocks: balance, payday cycle, budgets (only if any), recent
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero") {
                HeroBalanceCard(state.balance, ctx, state.wallets, state.selectedWalletId, onSelectWallet = vm::selectWallet, hidden = state.hideBalances, onToggleHidden = vm::toggleHidden)
            }
            if (pendingCaptures > 0) item(key = "captures") { CaptureInboxBanner(onOpen = { onNavigate(Routes.CAPTURES) }, calmItem()) }
            state.cycle?.let { cycle ->
                item(key = "cycle") { PaydayCard(cycle, ctx, onClick = { onNavigate(Routes.TRANSACTIONS) }, calmItem()) }
            }
            if (state.budgets.isNotEmpty()) {
                item(key = "budgets") { BudgetsCard(state.budgets, ctx, onClick = { onNavigate(Routes.BUDGETS) }, calmItem()) }
            }
            if (hasGoals) item(key = "goals") {
                GoalsHomeCard(onOpen = { onNavigate(Routes.GOALS) }, onOpenGoal = { onNavigate(Routes.editGoal(it)) }, calmItem())
            }
            item(key = "recent") {
                RecentCard(state.recent, ctx, onRow = { onNavigate(Routes.transactionDetail(it)) }, onSeeAll = { onNavigate(Routes.TRANSACTIONS) }, calmItem())
            }
        }
    }
}

/** Quick fade + placement for dashboard items, so a card appearing or leaving doesn't make the list jump. */
fun LazyItemScope.calmItem(): Modifier = Modifier.animateItem(fadeInSpec = Motion.effects(), placementSpec = Motion.slide, fadeOutSpec = Motion.effects())

/** Static card-shaped blocks while the first data loads, so the layout doesn't jump when it arrives. */
@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlaceholderBlock(152.dp, MaterialTheme.shapes.extraLarge)
        PlaceholderBlock(168.dp, MaterialTheme.shapes.large)
        PlaceholderBlock(240.dp, MaterialTheme.shapes.large)
    }
}

@Composable
private fun PlaceholderBlock(height: Dp, shape: Shape) {
    Box(Modifier.fillMaxWidth().height(height).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Hello"
}
