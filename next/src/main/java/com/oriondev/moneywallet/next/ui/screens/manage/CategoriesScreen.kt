package com.oriondev.moneywallet.next.ui.screens.manage

import com.oriondev.moneywallet.next.ui.theme.Motion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.EmptyState
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class CategoriesViewModel(container: AppContainer) : ViewModel() {
    val categories = container.repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(navController: NavHostController) {
    val vm = containerViewModel { CategoriesViewModel(it) }
    val all by vm.categories.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    val type = if (tab == 0) CategoryType.EXPENSE else CategoryType.INCOME
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    var showSystem by remember { mutableStateOf(false) }

    val ofType = all.filter { it.type == type }
    val parents = ofType.filter { it.parentId == null }
    val childrenOf = ofType.filter { it.parentId != null }.groupBy { it.parentId!! }
    val system = all.filter { it.type == CategoryType.SYSTEM }

    ManageScaffold(
        title = "Categories",
        onBack = { navController.popBackStack() },
        fab = {
            ExtendedFloatingActionButton(onClick = { navController.navigate(Routes.editCategory(0, type)) }, icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("New category") })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Expenses") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Income") })
            }
            if (parents.isEmpty()) {
                EmptyState(Icons.Outlined.Category, "No categories", "Add categories to organise your ${if (type == CategoryType.EXPENSE) "expenses" else "income"}.", action = "Add category") { navController.navigate(Routes.editCategory(0, type)) }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                parents.forEach { parent ->
                    val kids = childrenOf[parent.id].orEmpty()
                    val isOpen = expanded[parent.id] ?: true
                    item(key = "c${parent.id}") {
                        CategoryRow(
                            parent, indent = false, modifier = Modifier.animateItem(),
                            trailing = if (kids.isNotEmpty()) {
                                {
                                    val rot by animateFloatAsState(if (isOpen) 180f else 0f, Motion.spatialFast(), label = "chevron")
                                    IconButton(onClick = { expanded[parent.id] = !isOpen }) { Icon(Icons.Filled.ExpandMore, contentDescription = if (isOpen) "Collapse" else "Expand", modifier = Modifier.rotate(rot)) }
                                }
                            } else null,
                            onClick = { navController.navigate(Routes.editCategory(parent.id, type)) },
                        )
                    }
                    items(kids, key = { "c${it.id}" }) { child ->
                        AnimatedVisibility(isOpen, enter = expandVertically(Motion.spatial()), exit = shrinkVertically(Motion.spatial()), modifier = Modifier.animateItem()) {
                            CategoryRow(child, indent = true, onClick = { navController.navigate(Routes.editCategory(child.id, type)) })
                        }
                    }
                }
                if (system.isNotEmpty()) {
                    item(key = "system-header") {
                        ListItem(
                            headlineContent = { Text("System categories (${system.size})", style = MaterialTheme.typography.titleSmall) },
                            supportingContent = { Text("Used for transfers; read-only") },
                            leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = {
                                val rot by animateFloatAsState(if (showSystem) 180f else 0f, Motion.spatialFast(), label = "sys")
                                Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rot))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.animateItem().clickable { showSystem = !showSystem },
                        )
                    }
                    items(system, key = { "c${it.id}" }) { c ->
                        AnimatedVisibility(showSystem, enter = expandVertically(Motion.spatial()), exit = shrinkVertically(Motion.spatial()), modifier = Modifier.animateItem()) {
                            CategoryRow(c, indent = true, onClick = null)
                        }
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

@Composable
private fun CategoryRow(c: CategoryEntity, indent: Boolean, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(c.name, fontWeight = if (indent) FontWeight.Normal else FontWeight.Medium) },
        supportingContent = if (!c.showReport) ({ Text("Hidden from reports", style = MaterialTheme.typography.bodySmall) }) else null,
        leadingContent = { SpecIcon(c.icon, if (indent) 36.dp else 44.dp) },
        trailingContent = trailing ?: if (!c.showReport) ({ Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth().animateContentSize(Motion.spatial()).padding(start = if (indent) 24.dp else 0.dp).let { m -> if (onClick != null) m.clickable(onClick = onClick) else m },
    )
}
