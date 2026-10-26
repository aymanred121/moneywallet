package com.oriondev.moneywallet.next.ui.screens.manage

import com.oriondev.moneywallet.next.ui.theme.Motion
import com.oriondev.moneywallet.next.ui.screens.home.budgetDirection
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.BudgetEntity
import com.oriondev.moneywallet.next.data.db.BudgetType
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.EmptyState
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.theme.money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class BudgetsViewModel(container: AppContainer) : ViewModel() {
    val budgets = container.db.budgets().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = container.repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Direction the budget tracks: incomes for INCOMES budgets and income categories, expenses otherwise. */

@Composable
fun BudgetsScreen(navController: NavHostController) {
    val vm = containerViewModel { BudgetsViewModel(it) }
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val ctx by rememberCurrencyContext()
    val today = Dates.startOfDay(LocalDate.now())
    val active = budgets.filter { it.endDate >= today }
    val past = budgets.filter { it.endDate < today }
    val catById = categories.associateBy { it.id }

    ManageScaffold(
        title = "Budgets",
        onBack = { navController.popBackStack() },
        fab = { ExtendedFloatingActionButton(onClick = { navController.navigate(Routes.editBudget(0)) }, icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("New budget") }) },
    ) { padding ->
        if (budgets.isEmpty()) {
            EmptyState(Icons.Outlined.Savings, "No budgets", "Set a spending limit for a period and follow your progress here.", Modifier.padding(padding), "Create budget") { navController.navigate(Routes.editBudget(0)) }
            return@ManageScaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (active.isNotEmpty()) item(key = "h-active") { GroupLabel("Active") }
            items(active, key = { it.id }) { b -> BudgetCard(b, catById[b.categoryId ?: -1], ctx, Modifier.animateItem()) { navController.navigate(Routes.editBudget(b.id)) } }
            if (past.isNotEmpty()) item(key = "h-past") { GroupLabel("Past") }
            items(past, key = { it.id }) { b -> BudgetCard(b, catById[b.categoryId ?: -1], ctx, Modifier.animateItem(), past = true) { navController.navigate(Routes.editBudget(b.id)) } }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun BudgetCard(b: BudgetEntity, category: CategoryEntity?, ctx: com.oriondev.moneywallet.next.data.repo.CurrencyContext, modifier: Modifier = Modifier, past: Boolean = false, onClick: () -> Unit) {
    val container = LocalAppContainer.current
    val direction = budgetDirection(b, category)
    val spentFlow = remember(b, category) {
        com.oriondev.moneywallet.next.ui.screens.home.budgetProgress(container.db, b, category, container.repo.currencyContext)
    }
    val spentRaw by spentFlow.collectAsStateWithLifecycle(initialValue = null)
    val spent = spentRaw ?: 0L
    val currency = ctx.currency(b.currency)
    val fraction = if (b.money > 0) (spent.toFloat() / b.money).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(fraction, Motion.data(), label = "budget")
    val left = b.money - spent
    val daysLeft = Dates.daysBetween(LocalDate.now(), Dates.toLocalDate(b.endDate)).coerceAtLeast(0)
    val over = left < 0
    val colors = MaterialTheme.money
    val bar = when {
        over -> MaterialTheme.colorScheme.error
        direction == Direction.INCOME -> colors.income
        fraction > 0.85f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val title = when (b.type) {
        BudgetType.INCOMES -> "All income"
        BudgetType.CATEGORY -> category?.name ?: "Category"
        else -> "All expenses"
    }

    Card(modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = if (past) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (category != null) SpecIcon(category.icon, 40.dp) else Icon(Icons.Outlined.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${Dates.dayLabelWithYear(b.startDate)} – ${Dates.dayLabelWithYear(b.endDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AmountText(b.money, currency, style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(progress = { progress }, color = bar, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AmountText(kotlin.math.abs(left), currency, style = MaterialTheme.typography.bodyMedium, modifier = Modifier)
                    Text(if (over) (if (direction == Direction.INCOME) "above target" else "over budget") else if (direction == Direction.INCOME) "to go" else "left", style = MaterialTheme.typography.bodyMedium, color = if (over && direction != Direction.INCOME) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (past) "Ended" else if (daysLeft == 0L) "Last day" else "$daysLeft days left", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
