package com.oriondev.moneywallet.next.ui.screens.manage

import com.oriondev.moneywallet.next.ui.theme.Motion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.AmountTint
import com.oriondev.moneywallet.next.ui.components.EmptyState
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController

class WalletsViewModel(private val container: AppContainer) : ViewModel() {
    val wallets = container.repo.wallets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setArchived(w: WalletWithBalance, archived: Boolean) = viewModelScope.launch {
        container.db.wallets().byId(w.id)?.let { container.repo.saveWallet(it.copy(archived = archived, lastEdit = System.currentTimeMillis())) }
    }

    fun delete(w: WalletWithBalance) = viewModelScope.launch {
        container.db.wallets().byId(w.id)?.let { container.repo.deleteWallet(it) }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WalletsScreen(navController: NavHostController) {
    val vm = containerViewModel { WalletsViewModel(it) }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val ctx by rememberCurrencyContext()
    val settings by com.oriondev.moneywallet.next.ui.components.LocalAppContainer.current.prefs.settings.collectAsStateWithLifecycle(initialValue = com.oriondev.moneywallet.next.data.prefs.Settings())
    val hideBalances = settings.hideBalances
    var showArchived by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<WalletWithBalance?>(null) }
    var deleteFor by remember { mutableStateOf<WalletWithBalance?>(null) }

    val active = wallets.filter { !it.archived }
    val archived = wallets.filter { it.archived }
    val totalBag = MoneyBag().also { bag -> active.filter { it.countInTotal }.forEach { bag.add(it.currency, it.balance) } }
    val total = ctx.toBase(totalBag)

    ManageScaffold(
        title = "Wallets",
        onBack = { navController.popBackStack() },
        fab = {
            ExtendedFloatingActionButton(onClick = { navController.navigate(Routes.editWallet(0)) }, icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("New wallet") })
        },
    ) { padding ->
        if (wallets.isEmpty()) {
            EmptyState(Icons.Outlined.AccountBalanceWallet, "No wallets yet", "Create a wallet for each account you want to track.", Modifier.padding(padding), "Create wallet") { navController.navigate(Routes.editWallet(0)) }
            return@ManageScaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "total") {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Total balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        if (hideBalances) {
                            Text("••••••", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else if (total != null) {
                            AmountText(total, ctx.base, style = MaterialTheme.typography.headlineMedium, tint = AmountTint.NEUTRAL)
                        } else {
                            Text("Exchange rates unavailable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        if (totalBag.size > 1) {
                            Text("${totalBag.size} currencies, converted to ${ctx.baseIso}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }
            items(active, key = { it.id }) { w ->
                WalletRow(w, ctx, hideBalances, Modifier.animateItem(), onClick = { navController.navigate(Routes.editWallet(w.id)) }, onLongClick = { menuFor = w })
            }
            if (archived.isNotEmpty()) {
                item(key = "archived-header") {
                    ListItem(
                        headlineContent = { Text("Archived (${archived.size})", style = MaterialTheme.typography.titleSmall) },
                        trailingContent = { Icon(if (showArchived) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.animateItem().combinedClickable(onClick = { showArchived = !showArchived }),
                    )
                }
                items(archived, key = { it.id }) { w ->
                    AnimatedVisibility(showArchived, enter = expandVertically(Motion.spatial()), exit = shrinkVertically(Motion.spatial()), modifier = Modifier.animateItem()) {
                        WalletRow(w, ctx, hideBalances, onClick = { navController.navigate(Routes.editWallet(w.id)) }, onLongClick = { menuFor = w })
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    menuFor?.let { w ->
        ModalBottomSheet(onDismissRequest = { menuFor = null }) {
            ListItem(headlineContent = { Text(w.name, fontWeight = FontWeight.SemiBold) }, leadingContent = { SpecIcon(w.icon, 40.dp) })
            ListItem(headlineContent = { Text("Edit") }, leadingContent = { Icon(Icons.Outlined.Edit, null) }, modifier = Modifier.combinedClickable(onClick = { menuFor = null; navController.navigate(Routes.editWallet(w.id)) }))
            ListItem(
                headlineContent = { Text(if (w.archived) "Unarchive" else "Archive") },
                leadingContent = { Icon(if (w.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) },
                modifier = Modifier.combinedClickable(onClick = { vm.setArchived(w, !w.archived); menuFor = null }),
            )
            ListItem(
                headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.combinedClickable(onClick = { deleteFor = w; menuFor = null }),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    deleteFor?.let { w ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text("Delete \"${w.name}\"?") },
            text = { Text("Every transaction and transfer recorded in this wallet will be deleted as well. This cannot be undone. Consider archiving instead.") },
            confirmButton = { TextButton(onClick = { vm.delete(w); deleteFor = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WalletRow(w: WalletWithBalance, ctx: com.oriondev.moneywallet.next.data.repo.CurrencyContext, hideBalances: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val currency = ctx.currency(w.currency)
    ListItem(
        headlineContent = { Text(w.name, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(currency.iso, style = MaterialTheme.typography.bodySmall)
                if (!w.countInTotal) Text("· not in total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (w.archived) Text("· archived", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = { SpecIcon(w.icon, 44.dp) },
        trailingContent = { if (hideBalances) Text("••••", style = MaterialTheme.typography.titleMedium) else AmountText(w.balance, currency, style = MaterialTheme.typography.titleMedium, tint = AmountTint.AUTO) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}
