package com.oriondev.moneywallet.next.ui.screens.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.data.repo.GoalRepository
import com.oriondev.moneywallet.next.data.repo.GoalUi
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.EmptyState
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.screens.manage.GroupLabel
import com.oriondev.moneywallet.next.ui.screens.manage.ManageScaffold
import com.oriondev.moneywallet.next.ui.screens.manage.rememberCurrencyContext
import com.oriondev.moneywallet.next.ui.theme.Motion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Savings goals: list and editor. Registered once in WalletNavHost. */
fun NavGraphBuilder.goalsGraph(navController: NavHostController) {
    composable(Routes.GOALS) { GoalsScreen(navController) }
    composable(Routes.EDIT_GOAL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
        EditGoalScreen(navController, entry.arguments?.getLong("id") ?: 0L)
    }
}

class GoalsViewModel(container: AppContainer) : ViewModel() {
    private val goalsRepo = GoalRepository(container.db, container.repo)
    val goals = goalsRepo.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun adjust(id: Long, delta: Long) = viewModelScope.launch { goalsRepo.adjust(id, delta) }
}

@Composable
fun GoalsScreen(navController: NavHostController) {
    val vm = containerViewModel { GoalsViewModel(it) }
    val goals by vm.goals.collectAsStateWithLifecycle()
    val ctx by rememberCurrencyContext()
    var addTo by remember { mutableStateOf<GoalUi?>(null) }
    val open = { id: Long -> navController.navigate(Routes.editGoal(id)) }

    ManageScaffold(
        title = "Savings goals",
        onBack = { navController.popBackStack() },
        fab = { ExtendedFloatingActionButton(onClick = { open(0L) }, icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("New goal") }) },
    ) { padding ->
        val all = goals ?: return@ManageScaffold
        if (all.isEmpty()) {
            EmptyState(
                Icons.Outlined.Flag, "No goals yet", "Set a target and a date, and see how much to put aside each month.",
                Modifier.padding(padding), action = "New goal", onAction = { open(0L) },
            )
            return@ManageScaffold
        }
        val (archived, active) = all.partition { it.goal.archived }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(active, key = { it.goal.id }) { ui ->
                GoalCard(
                    ui, ctx, onClick = { open(ui.goal.id) },
                    modifier = Modifier.animateItem(fadeInSpec = Motion.effects(), placementSpec = Motion.slide, fadeOutSpec = Motion.effects()),
                    onAdd = if (ui.goal.walletId == null && !ui.complete) ({ addTo = ui }) else null,
                )
            }
            if (archived.isNotEmpty()) {
                item(key = "archived") { GroupLabel("Archived") }
                items(archived, key = { it.goal.id }) { ui ->
                    GoalCard(ui, ctx, onClick = { open(ui.goal.id) }, modifier = Modifier.alpha(0.72f).animateItem(fadeInSpec = Motion.effects(), placementSpec = Motion.slide, fadeOutSpec = Motion.effects()))
                }
            }
            item(key = "fab-space") { Spacer(Modifier.height(72.dp)) }
        }
    }

    addTo?.let { ui ->
        GoalAmountDialog("Add to ${ui.goal.name}", "Add", ctx.currency(ui.goal.currency), onDismiss = { addTo = null }) { vm.adjust(ui.goal.id, it) }
    }
}
