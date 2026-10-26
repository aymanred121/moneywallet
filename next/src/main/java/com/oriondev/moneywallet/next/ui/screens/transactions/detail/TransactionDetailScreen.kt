@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.oriondev.moneywallet.next.ui.screens.transactions.detail

import androidx.compose.animation.animateContentSize
import com.oriondev.moneywallet.next.ui.components.MoneyText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.TransactionRow
import com.oriondev.moneywallet.next.data.db.TransactionType
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.AppTopBar
import com.oriondev.moneywallet.next.ui.components.appBarScrollBehavior
import com.oriondev.moneywallet.next.ui.components.AmountTint
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.components.transactions.formatRate
import com.oriondev.moneywallet.next.ui.screens.transactions.EMPTY_CTX
import com.oriondev.moneywallet.next.ui.theme.MoneyDisplay
import com.oriondev.moneywallet.next.ui.theme.Motion
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed class DetailState {
    data object Loading : DetailState()
    data object Missing : DetailState()
    data class Loaded(val row: TransactionRow) : DetailState()
}

sealed class DetailEvent {
    data object Deleted : DetailEvent()
    data class Duplicated(val newId: Long) : DetailEvent()
}

class TransactionDetailViewModel(container: AppContainer, private val id: Long) : ViewModel() {
    private val repo = container.repo
    private val db = container.db
    val ctx: StateFlow<CurrencyContext> = repo.currencyContext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EMPTY_CTX)
    val state: StateFlow<DetailState> = db.transactions().observeRow(id).map { if (it == null) DetailState.Missing else DetailState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)
    val events = MutableSharedFlow<DetailEvent>(extraBufferCapacity = 2)

    fun delete() = viewModelScope.launch { repo.deleteTransaction(id); events.emit(DetailEvent.Deleted) }

    fun duplicate() = viewModelScope.launch {
        val t = db.transactions().byId(id) ?: return@launch
        val now = System.currentTimeMillis()
        val newId = db.transactions().insert(t.copy(id = 0, uuid = UUID.randomUUID().toString(), date = now, lastEdit = now, transferId = null, type = TransactionType.STANDARD))
        events.emit(DetailEvent.Duplicated(newId))
    }
}

@Composable
fun TransactionDetailScreen(id: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = containerViewModel(key = "detail-$id") { TransactionDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx by vm.ctx.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                DetailEvent.Deleted -> onBack()
                is DetailEvent.Duplicated -> onEdit(e.newId)
            }
        }
    }
    LaunchedEffect(state) { if (state is DetailState.Missing) onBack() }

    val row = (state as? DetailState.Loaded)?.row
    val isTransfer = row?.type == TransactionType.TRANSFER
    val scroll = appBarScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            // Edit and Duplicate live at the bottom of the content; the bar keeps only the destructive action
            AppTopBar(if (isTransfer) "Transfer" else "Transaction", scroll, onBack) {
                if (row != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete") }
            }
        },
    ) { padding ->
        if (row == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        } else {
            DetailContent(row, ctx, isTransfer, Modifier.padding(padding), onEdit = { onEdit(row.id) }, onDuplicate = vm::duplicate)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (isTransfer) "Delete transfer?" else "Delete transaction?") },
            text = { Text(if (isTransfer) "Both sides of the transfer will be removed." else "This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailContent(row: TransactionRow, ctx: CurrencyContext, isTransfer: Boolean, modifier: Modifier, onEdit: () -> Unit, onDuplicate: () -> Unit) {
    val walletCurrency = ctx.currency(row.walletCurrency)
    val tint = if (row.direction == Direction.INCOME) AmountTint.INCOME else AmountTint.EXPENSE
    val signedMoney = if (row.direction == Direction.INCOME) row.money else -row.money
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp).animateContentSize(Motion.spatial()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isTransfer) Icon(Icons.Default.SwapHoriz, contentDescription = null, Modifier.height(64.dp), tint = MaterialTheme.colorScheme.tertiary)
                else SpecIcon(row.categoryIcon, size = 64.dp)
                Text(
                    when {
                        isTransfer -> if (row.direction == Direction.INCOME) "Transfer in" else "Transfer out"
                        else -> row.categoryName ?: "Uncategorised"
                    },
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AmountText(signedMoney, walletCurrency, style = MoneyDisplay, tint = tint, signed = true)
                if (row.currency != null && row.currency != row.walletCurrency && row.originalMoney != null) {
                    val original = ctx.currency(row.currency)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(
                            "${MoneyFormat.format(row.originalMoney, original)} ${original.iso}" +
                                (row.exchangeRate?.let { "  ·  1 ${original.iso} = ${formatRate(it)} ${walletCurrency.symbol}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.description.isNotBlank()) Text(row.description, style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!row.confirmed) StatusTag("Pending")
                    if (!row.countInTotal) StatusTag("Not counted in total")
                }
            }
        }

        ListItem(leadingContent = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) }, overlineContent = { Text("Wallet") }, headlineContent = { Text(row.walletName) }, supportingContent = { Text(row.walletCurrency) })
        ListItem(leadingContent = { Icon(Icons.Default.Event, contentDescription = null) }, overlineContent = { Text("Date") }, headlineContent = { Text(Dates.dayLabelWithYear(row.date)) }, supportingContent = { Text(Dates.relativeDayLabel(row.date)) })
        ListItem(leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) }, overlineContent = { Text("Time") }, headlineContent = { Text(Dates.timeLabel(row.date)) })
        if (!row.note.isNullOrBlank()) ListItem(leadingContent = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) }, overlineContent = { Text("Note") }, headlineContent = { Text(row.note) })

        if (!isTransfer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text("Duplicate")
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text("Edit")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Read-only status label (not a chip: chips imply an action). */
@Composable
private fun StatusTag(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
