package com.oriondev.moneywallet.next.ui.screens.goals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.GoalEntity
import com.oriondev.moneywallet.next.data.repo.GoalMath
import com.oriondev.moneywallet.next.data.repo.GoalRepository
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.MoneyText
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.components.transactions.WalletDatePickerDialog
import com.oriondev.moneywallet.next.ui.components.transactions.WalletPickerSheet
import com.oriondev.moneywallet.next.ui.screens.manage.ManageScaffold
import com.oriondev.moneywallet.next.ui.screens.manage.SettingRow
import com.oriondev.moneywallet.next.ui.screens.manage.SwitchRow
import com.oriondev.moneywallet.next.ui.screens.manage.currencyOrFallback
import com.oriondev.moneywallet.next.ui.screens.manage.pickers.CurrencyPickerSheet
import com.oriondev.moneywallet.next.ui.screens.manage.pickers.IconPickerSheet
import com.oriondev.moneywallet.next.ui.screens.manage.rememberCurrencyContext
import com.oriondev.moneywallet.next.ui.theme.Motion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class GoalForm(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String = "",
    val icon: String? = null,
    val amount: String = "",
    val currency: String? = null,
    val linked: Boolean = false,
    val walletId: Long? = null,
    /** Manual progress in the goal currency. */
    val saved: Long = 0,
    val deadline: LocalDate? = null,
    val archived: Boolean = false,
    val sortIndex: Int = 0,
    val loaded: Boolean = false,
)

class EditGoalViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    private val goals = GoalRepository(container.db, container.repo)
    val form = MutableStateFlow(GoalForm(id = id))
    val done = MutableStateFlow(false)
    val wallets = container.repo.wallets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val ctx = container.repo.currencyContext.first()
            val g = if (id == 0L) null else goals.byId(id)
            form.value = if (g == null) GoalForm(currency = ctx.baseIso, loaded = true) else GoalForm(
                id = g.id, uuid = g.uuid, name = g.name, icon = g.icon, amount = MoneyFormat.toDecimal(g.targetMoney, ctx.currency(g.currency)).toPlainString(),
                currency = g.currency, linked = g.walletId != null, walletId = g.walletId, saved = g.savedMoney, deadline = g.deadline?.let(Dates::toLocalDate),
                archived = g.archived, sortIndex = g.sortIndex, loaded = true,
            )
        }
    }

    fun update(f: (GoalForm) -> GoalForm) { form.value = f(form.value) }

    /** Add money / Withdraw: applied at once for a saved goal so the list reflects it even without "Save". */
    fun adjust(delta: Long) {
        update { it.copy(saved = (it.saved + delta).coerceAtLeast(0)) }
        if (id != 0L) viewModelScope.launch { goals.adjust(id, delta) }
    }

    fun save() = viewModelScope.launch {
        val f = form.value
        val ctx = container.repo.currencyContext.first()
        val iso = f.currency ?: ctx.baseIso
        val target = MoneyFormat.parse(f.amount, ctx.currency(iso))?.takeIf { it > 0 } ?: return@launch
        val name = f.name.trim()
        goals.save(
            GoalEntity(
                id = f.id, uuid = f.uuid, name = name, icon = f.icon ?: IconSpec.letterFor(name, IconSpec.palette[(name.hashCode() and 0x7fffffff) % IconSpec.palette.size]).encode(),
                targetMoney = target, currency = iso, walletId = if (f.linked) f.walletId else null, savedMoney = f.saved,
                deadline = f.deadline?.let(Dates::startOfDay), archived = f.archived, sortIndex = f.sortIndex, lastEdit = System.currentTimeMillis(),
            ),
        )
        done.value = true
    }

    fun delete() = viewModelScope.launch { goals.delete(id); done.value = true }
}

@Composable
fun EditGoalScreen(navController: NavHostController, id: Long) {
    val vm = containerViewModel(key = "goal-$id") { EditGoalViewModel(it, id) }
    val form by vm.form.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val ctx by rememberCurrencyContext()
    var showIcon by remember { mutableStateOf(false) }
    var showCurrency by remember { mutableStateOf(false) }
    var showWallet by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var amountDialog by remember { mutableStateOf<Int?>(null) } // +1 add, -1 withdraw
    var confirmDelete by remember { mutableStateOf(false) }

    if (done) LaunchedEffect(Unit) { navController.popBackStack() }

    val currency = ctx.currencyOrFallback(form.currency)
    val target = MoneyFormat.parse(form.amount, currency)
    val wallet = wallets.firstOrNull { it.id == form.walletId }
    val saved: Long? = if (form.linked) wallet?.let { ctx.convert(it.balance, it.currency, currency.iso) } else form.saved
    val iconSpec = form.icon?.let(IconSpec::parse) ?: IconSpec.letterFor(form.name.ifBlank { "?" }, IconSpec.palette[0])
    val valid = form.name.isNotBlank() && target != null && target > 0 && (!form.linked || form.walletId != null)

    ManageScaffold(
        title = if (id == 0L) "New goal" else "Edit goal",
        onBack = { navController.popBackStack() },
        actions = { if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete goal") } },
    ) { padding ->
        if (!form.loaded) { CircularProgressIndicator(Modifier.padding(padding).padding(32.dp)); return@ManageScaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { showIcon = true }, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) { com.oriondev.moneywallet.next.ui.components.SpecIcon(iconSpec, 48.dp) }
                }
                OutlinedTextField(
                    value = form.name, onValueChange = { v -> vm.update { it.copy(name = v) } }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = form.amount, onValueChange = { v -> vm.update { it.copy(amount = v) } }, label = { Text("Target") }, singleLine = true,
                prefix = { MoneyText(currency.symbol + " ") }, isError = form.amount.isNotBlank() && target == null,
                trailingIcon = { TextButton(onClick = { showCurrency = true }) { Text(currency.iso) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            Text("Progress", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !form.linked, onClick = { vm.update { it.copy(linked = false) } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Track manually") }
                SegmentedButton(selected = form.linked, onClick = { vm.update { it.copy(linked = true) }; if (form.walletId == null) showWallet = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Link a wallet") }
            }
            AnimatedContent(form.linked, transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) }, label = "goalMode") { linked ->
                if (linked) {
                    Column {
                        SettingRow(
                            title = wallet?.name ?: "Choose a wallet", icon = Icons.Outlined.AccountBalanceWallet,
                            subtitle = if (wallet == null) "Progress follows the wallet's balance" else if (saved == null) "No exchange rate from ${wallet.currency} to ${currency.iso} yet" else "Balance ${money(saved, currency)}",
                            onClick = { showWallet = true },
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Saved so far", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            AmountText(form.saved, currency, style = MaterialTheme.typography.titleMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { amountDialog = -1 }, enabled = form.saved > 0, modifier = Modifier.weight(1f).height(48.dp)) {
                                Icon(Icons.Filled.Remove, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Withdraw")
                            }
                            FilledTonalButton(onClick = { amountDialog = 1 }, modifier = Modifier.weight(1f).height(48.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Add money")
                            }
                        }
                    }
                }
            }
            SettingRow(
                title = "Target date", icon = Icons.Outlined.Event,
                subtitle = form.deadline?.let { Dates.dayLabelWithYear(Dates.startOfDay(it)) } ?: "Optional",
                trailing = if (form.deadline != null) ({ IconButton(onClick = { vm.update { it.copy(deadline = null) } }) { Icon(Icons.Outlined.Close, contentDescription = "Clear target date") } }) else null,
                onClick = { showDate = true },
            )
            if (target != null && target > 0 && saved != null) {
                val p = GoalMath.progress(saved, target, form.deadline, LocalDate.now(), Dates.monthStartDay)
                val line = when {
                    p.complete -> "Already reached"
                    p.overdue -> "The target date has passed"
                    p.perMonth != null -> "${money(p.perMonth, currency)} per month for ${p.monthsLeft} ${if (p.monthsLeft == 1) "month" else "months"} reaches it on time"
                    else -> "${money(p.remaining, currency)} to go"
                }
                MoneyText(line, style = MaterialTheme.typography.bodyMedium, color = if (p.complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (id != 0L) SwitchRow("Archived", checked = form.archived, onCheckedChange = { v -> vm.update { it.copy(archived = v) } }, subtitle = "Hide from Home and move to the bottom of the list")
            Button(onClick = { vm.save() }, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.size(8.dp)); Text(if (id == 0L) "Create goal" else "Save changes")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showIcon) IconPickerSheet(current = iconSpec, nameHint = form.name, onDismiss = { showIcon = false }) { spec -> vm.update { it.copy(icon = spec.encode()) } }
    if (showCurrency) CurrencyPickerSheet(selected = form.currency, onDismiss = { showCurrency = false }) { iso -> vm.update { it.copy(currency = iso) } }
    if (showWallet) WalletPickerSheet(
        wallets = wallets.filter { !it.archived || it.id == form.walletId }, ctx = ctx, selectedId = form.walletId, title = "Saving into",
        onPick = { w -> vm.update { it.copy(walletId = w.id, linked = true) }; showWallet = false },
        onDismiss = { showWallet = false },
    )
    if (showDate) WalletDatePickerDialog(initial = form.deadline ?: LocalDate.now().plusMonths(6), onDismiss = { showDate = false }) { d -> vm.update { it.copy(deadline = d) } }
    amountDialog?.let { sign ->
        GoalAmountDialog(if (sign > 0) "Add money" else "Withdraw", if (sign > 0) "Add" else "Withdraw", currency, onDismiss = { amountDialog = null }) { vm.adjust(sign * it) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete goal?") },
            text = { Text("Only the goal is removed; wallets and transactions are untouched.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
