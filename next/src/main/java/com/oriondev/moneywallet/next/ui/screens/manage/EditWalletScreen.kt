package com.oriondev.moneywallet.next.ui.screens.manage

import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.WalletEntity
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.screens.manage.pickers.CurrencyPickerSheet
import com.oriondev.moneywallet.next.ui.screens.manage.pickers.IconPickerSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class WalletForm(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String = "",
    val icon: IconSpec? = null,
    val currency: String? = null,
    val startBalance: String = "",
    val countInTotal: Boolean = true,
    val archived: Boolean = false,
    val note: String = "",
    val sortIndex: Int = 0,
    val loaded: Boolean = false,
)

class EditWalletViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    val form = MutableStateFlow(WalletForm(id = id, loaded = id == 0L))
    val saved = MutableStateFlow(false)
    /** Amounts are stored in the wallet currency, so it is fixed once transactions exist. */
    val currencyLocked = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val ctx = container.repo.currencyContext.first()
            if (id != 0L) currencyLocked.value = container.repo.walletHasTransactions(id)
            if (id == 0L) {
                form.value = form.value.copy(currency = ctx.baseIso, loaded = true)
            } else {
                container.db.wallets().byId(id)?.let { w ->
                    form.value = WalletForm(
                        id = w.id, uuid = w.uuid, name = w.name, icon = IconSpec.parse(w.icon), currency = w.currency,
                        startBalance = MoneyFormat.toDecimal(w.startMoney, ctx.currency(w.currency)).toPlainString(),
                        countInTotal = w.countInTotal, archived = w.archived, note = w.note ?: "", sortIndex = w.sortIndex, loaded = true,
                    )
                }
            }
        }
    }

    fun update(f: (WalletForm) -> WalletForm) { form.value = f(form.value) }

    fun save() = viewModelScope.launch {
        val f = form.value
        val ctx = container.repo.currencyContext.first()
        val iso = f.currency ?: ctx.baseIso
        val minor = MoneyFormat.parse(f.startBalance.ifBlank { "0" }, ctx.currency(iso)) ?: 0L
        val icon = f.icon ?: IconSpec.letterFor(f.name, IconSpec.palette[(f.name.hashCode() and 0xffff) % IconSpec.palette.size])
        container.repo.saveWallet(
            WalletEntity(
                id = f.id, uuid = f.uuid, name = f.name.trim(), icon = icon.encode(), currency = iso, startMoney = minor,
                countInTotal = f.countInTotal, archived = f.archived, note = f.note.trim().ifEmpty { null }, sortIndex = f.sortIndex, lastEdit = System.currentTimeMillis(),
            ),
        )
        saved.value = true
    }

    fun delete() = viewModelScope.launch {
        container.db.wallets().byId(id)?.let { container.repo.deleteWallet(it) }
        saved.value = true
    }
}

@Composable
fun EditWalletScreen(navController: NavHostController, id: Long) {
    val vm = containerViewModel(key = "wallet-$id") { EditWalletViewModel(it, id) }
    val form by vm.form.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val currencyLocked by vm.currencyLocked.collectAsStateWithLifecycle()
    val ctx by rememberCurrencyContext()
    var showCurrency by remember { mutableStateOf(false) }
    var showIcon by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (saved) {
        androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
    }

    val previewIcon = form.icon ?: IconSpec.letterFor(form.name.ifBlank { "Wallet" }, IconSpec.palette[(form.name.hashCode() and 0xffff) % IconSpec.palette.size])
    val currency = ctx.currencyOrFallback(form.currency)
    val balanceValid = form.startBalance.isBlank() || MoneyFormat.parse(form.startBalance, currency) != null

    ManageScaffold(
        title = if (id == 0L) "New wallet" else "Edit wallet",
        onBack = { navController.popBackStack() },
        actions = {
            if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
        },
    ) { padding ->
        if (!form.loaded) {
            CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
            return@ManageScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SpecIcon(previewIcon, size = 64.dp, modifier = Modifier.clickable(onClickLabel = "Change icon", role = Role.Button) { showIcon = true })
                OutlinedTextField(
                    value = form.name, onValueChange = { v -> vm.update { it.copy(name = v) } }, label = { Text("Name") }, singleLine = true,
                    isError = form.name.isBlank(), modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = { showIcon = true }) { Text("Change icon") }
            SettingRow(
                title = "Currency",
                subtitle = "${currency.name} (${currency.iso})" + if (currencyLocked) "\nLocked because this wallet already has transactions" else "",
                trailing = if (currencyLocked) ({ Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
                onClick = if (currencyLocked) null else ({ showCurrency = true }),
            )
            OutlinedTextField(
                value = form.startBalance, onValueChange = { v -> vm.update { it.copy(startBalance = v) } },
                label = { Text("Starting balance") }, prefix = { Text(currency.symbol) }, singleLine = true, isError = !balanceValid,
                supportingText = { if (!balanceValid) Text("Enter a valid amount") else Text("Balance before your first recorded transaction") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow("Count in total", form.countInTotal, { v -> vm.update { it.copy(countInTotal = v) } }, subtitle = "Include this wallet in the overall balance")
            SwitchRow("Archived", form.archived, { v -> vm.update { it.copy(archived = v) } }, subtitle = "Hide the wallet from lists without deleting it")
            OutlinedTextField(value = form.note, onValueChange = { v -> vm.update { it.copy(note = v) } }, label = { Text("Note") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { vm.save() }, enabled = form.name.isNotBlank() && balanceValid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text(if (id == 0L) "Create wallet" else "Save changes")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showCurrency && !currencyLocked) CurrencyPickerSheet(selected = form.currency, onDismiss = { showCurrency = false }) { iso -> vm.update { it.copy(currency = iso) } }
    if (showIcon) IconPickerSheet(current = previewIcon, nameHint = form.name, onDismiss = { showIcon = false }) { spec -> vm.update { it.copy(icon = spec) } }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete wallet?") },
            text = { Text("All transactions in this wallet will be deleted too. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
