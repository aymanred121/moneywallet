package com.oriondev.moneywallet.next.ui.screens.capture

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.capture.CaptureRecorder
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.CaptureEntity
import com.oriondev.moneywallet.next.data.db.CaptureStatus
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.AmountTint
import com.oriondev.moneywallet.next.ui.components.EmptyState
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.screens.manage.ManageScaffold
import com.oriondev.moneywallet.next.ui.screens.manage.rememberCurrencyContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CaptureInboxViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.db.captures()
    /** null until the first read, so the empty state never flashes. */
    val pending = dao.observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setStatus(id: Long, status: Int) = viewModelScope.launch { dao.setStatus(id, status) }
    fun dismissAll() = viewModelScope.launch { dao.dismissAllPending() }
}

@Composable
fun CaptureInboxScreen(navController: NavHostController) {
    val vm = containerViewModel("captures") { CaptureInboxViewModel(it) }
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pending by vm.pending.collectAsStateWithLifecycle()
    val settings by remember(container) { container.prefs.settings }.collectAsStateWithLifecycle(initialValue = Settings())
    val currencies by rememberCurrencyContext()
    var showImport by remember { mutableStateOf(false) }
    var confirmDismissAll by remember { mutableStateOf(false) }
    if (showImport) SmsImportDialog(onDismiss = { showImport = false }, onReview = {})
    if (confirmDismissAll) AlertDialog(
        onDismissRequest = { confirmDismissAll = false },
        title = { Text("Dismiss all ${pending?.size ?: 0} payments?") },
        text = { Text("They leave the review list without being added. New payments keep arriving as usual.") },
        confirmButton = { TextButton(onClick = { vm.dismissAll(); confirmDismissAll = false }) { Text("Dismiss all") } },
        dismissButton = { TextButton(onClick = { confirmDismissAll = false }) { Text("Cancel") } },
    )

    ManageScaffold(
        title = "Payments to review",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { showImport = true }) { Icon(Icons.Outlined.MoveToInbox, contentDescription = "Import recent bank SMS") }
            if (!pending.isNullOrEmpty()) IconButton(onClick = { confirmDismissAll = true }) { Icon(Icons.Outlined.ClearAll, contentDescription = "Dismiss all") }
            IconButton(onClick = { navController.navigate(Routes.CAPTURE_SETTINGS) }) { Icon(Icons.Outlined.Settings, contentDescription = "Capture settings") }
        },
    ) { padding ->
        val list = pending ?: return@ManageScaffold
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Outlined.Contactless,
                    title = if (settings.captureEnabled) "All caught up" else "Capture payments automatically",
                    message = if (settings.captureEnabled) {
                        "New payments from Google Wallet and your bank apps will appear here, ready to add with one tap."
                    } else {
                        "MoneyWallet can read payment notifications from Google Wallet and your bank apps, so each purchase is one tap away from being recorded. Everything stays on this device."
                    },
                    action = if (settings.captureEnabled) "Import recent bank SMS" else "Set up",
                    onAction = { if (settings.captureEnabled) showImport = true else navController.navigate(Routes.CAPTURE_SETTINGS) },
                )
            }
            return@ManageScaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { capture ->
                CaptureCard(
                    capture = capture,
                    amount = { c -> c.amount?.let { AmountText(it, currencies.currency(c.currency ?: currencies.baseIso), style = MaterialTheme.typography.titleLarge, tint = if (c.direction == Direction.INCOME) AmountTint.INCOME else AmountTint.EXPENSE) } },
                    onAdd = { scope.launch { navController.navigate(CaptureRecorder.addRoute(context, capture)) } },
                    onDismiss = {
                        vm.setStatus(capture.id, CaptureStatus.DISMISSED)
                        CaptureRecorder.cancel(context, capture.id)
                        scope.launch {
                            if (snackbar.showSnackbar("Payment dismissed", actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                                vm.setStatus(capture.id, CaptureStatus.PENDING)
                            }
                        }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun CaptureCard(capture: CaptureEntity, amount: @Composable (CaptureEntity) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(capture.merchant ?: capture.title.ifBlank { "Payment" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${capture.appName} · ${Dates.relativeDayLabel(capture.postedAt)} ${Dates.timeLabel(capture.postedAt)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                amount(capture)
            }
            Text(
                capture.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onAdd) { Text("Add") }
            }
        }
    }
}
