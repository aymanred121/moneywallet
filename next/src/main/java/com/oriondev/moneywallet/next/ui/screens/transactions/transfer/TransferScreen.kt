@file:OptIn(ExperimentalMaterial3Api::class)

package com.oriondev.moneywallet.next.ui.screens.transactions.transfer

import androidx.compose.animation.AnimatedVisibility
import com.oriondev.moneywallet.next.ui.components.MoneyText
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Convert
import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.data.repo.WalletRepository
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.components.transactions.RateDialog
import com.oriondev.moneywallet.next.ui.components.transactions.WalletDatePickerDialog
import com.oriondev.moneywallet.next.ui.components.transactions.WalletPickerSheet
import com.oriondev.moneywallet.next.ui.components.transactions.WalletTimePickerDialog
import com.oriondev.moneywallet.next.ui.components.transactions.formatRate
import com.oriondev.moneywallet.next.ui.components.transactions.SaveButton
import com.oriondev.moneywallet.next.ui.components.transactions.rememberCloseOnce
import com.oriondev.moneywallet.next.ui.theme.Motion
import com.oriondev.moneywallet.next.ui.screens.transactions.EMPTY_CTX
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransferForm(
    val fromWalletId: Long? = null,
    val toWalletId: Long? = null,
    val amountText: String = "",
    /** Received amount typed by the user; null = derive from rate. */
    val toAmountText: String? = null,
    val manualRate: Double? = null,
    val feeText: String = "",
    val date: Long = Dates.now(),
    val description: String = "",
    val note: String = "",
)

data class TransferComputed(
    val from: WalletWithBalance?, val to: WalletWithBalance?,
    val fromCurrency: Currency, val toCurrency: Currency, val foreign: Boolean,
    val liveRate: Double?, val rate: Double?,
    val fromMinor: Long?, val toMinor: Long?, val feeMinor: Long,
    val toText: String,
)

sealed class TransferEvent { data object Saved : TransferEvent(); data class Error(val message: String) : TransferEvent() }

class TransferViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repo
    val ctx: StateFlow<CurrencyContext> = repo.currencyContext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EMPTY_CTX)
    val wallets: StateFlow<List<WalletWithBalance>> = repo.wallets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _form = MutableStateFlow(TransferForm())
    val form: StateFlow<TransferForm> = _form.asStateFlow()
    val events = MutableSharedFlow<TransferEvent>(extraBufferCapacity = 2)
    private val _saving = MutableStateFlow(false)
    /** True from a Save tap until it fails; repeat taps are ignored meanwhile. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    init {
        viewModelScope.launch {
            val selected = container.prefs.settings.first().selectedWalletId
            val list = wallets.first { it.isNotEmpty() }.filter { !it.archived }
            val from = list.firstOrNull { it.id == selected } ?: list.firstOrNull()
            _form.update { it.copy(fromWalletId = from?.id, toWalletId = list.firstOrNull { w -> w.id != from?.id }?.id) }
        }
    }

    fun compute(f: TransferForm, ctx: CurrencyContext, wallets: List<WalletWithBalance>): TransferComputed {
        val from = wallets.firstOrNull { it.id == f.fromWalletId }
        val to = wallets.firstOrNull { it.id == f.toWalletId }
        val fromCur = ctx.currency(from?.currency ?: ctx.baseIso)
        val toCur = ctx.currency(to?.currency ?: ctx.baseIso)
        val foreign = fromCur.iso != toCur.iso
        val liveRate = if (foreign) ctx.rate(fromCur.iso, toCur.iso) else null
        val rate = if (foreign) f.manualRate ?: liveRate else null
        val fromMinor = MoneyFormat.parse(f.amountText, fromCur)
        val feeMinor = MoneyFormat.parse(f.feeText, fromCur) ?: 0L
        val derivedTo = when {
            !foreign -> fromMinor
            fromMinor != null && rate != null -> Convert.minor(fromCur, toCur, fromMinor, rate)
            else -> null
        }
        val toMinor = if (f.toAmountText != null) MoneyFormat.parse(f.toAmountText, toCur) else derivedTo
        val toText = f.toAmountText ?: derivedTo?.let { MoneyFormat.toDecimal(it, toCur).toPlainString() } ?: ""
        return TransferComputed(from, to, fromCur, toCur, foreign, liveRate, rate, fromMinor, toMinor, feeMinor, toText)
    }

    fun setFrom(w: WalletWithBalance) = _form.update { it.copy(fromWalletId = w.id, toWalletId = if (it.toWalletId == w.id) null else it.toWalletId, toAmountText = null, manualRate = null) }
    fun setTo(w: WalletWithBalance) = _form.update { it.copy(toWalletId = w.id, fromWalletId = if (it.fromWalletId == w.id) null else it.fromWalletId, toAmountText = null, manualRate = null) }
    fun swap() = _form.update { it.copy(fromWalletId = it.toWalletId, toWalletId = it.fromWalletId, toAmountText = null, manualRate = null) }
    fun setAmount(t: String) = _form.update { it.copy(amountText = t, toAmountText = null) }
    fun setToAmount(t: String) = _form.update { it.copy(toAmountText = t) }
    fun setRate(r: Double?) = _form.update { it.copy(manualRate = r, toAmountText = null) }
    fun setFee(t: String) = _form.update { it.copy(feeText = t) }
    fun setDate(m: Long) = _form.update { it.copy(date = m) }
    fun setDescription(t: String) = _form.update { it.copy(description = t) }
    fun setNote(t: String) = _form.update { it.copy(note = t) }

    fun save() {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch { doSave() }
    }

    private suspend fun doSave() {
        val f = _form.value
        val c = compute(f, ctx.value, wallets.value)
        val error = when {
            c.from == null -> "Choose the source wallet"
            c.to == null -> "Choose the destination wallet"
            c.from.id == c.to.id -> "Source and destination must differ"
            c.fromMinor == null || c.fromMinor <= 0 -> "Enter an amount greater than zero"
            c.toMinor == null || c.toMinor <= 0 -> if (c.rate == null) "Enter an exchange rate or the received amount" else "Enter the received amount"
            c.feeMinor < 0 -> "Fee cannot be negative"
            else -> null
        }
        if (error != null) { _saving.value = false; events.emit(TransferEvent.Error(error)); return }
        try {
            repo.saveTransfer(WalletRepository.TransferDraft(
                fromWalletId = c.from!!.id, toWalletId = c.to!!.id, fromMinor = c.fromMinor!!, toMinor = c.toMinor!!, taxMinor = c.feeMinor,
                date = f.date, description = f.description.trim(), note = f.note.trim().ifEmpty { null },
            ))
            events.emit(TransferEvent.Saved) // stays "saving": the screen is closing
        } catch (e: Exception) { _saving.value = false; events.emit(TransferEvent.Error(e.message ?: "Could not save transfer")) }
    }
}

private enum class TransferSheet { From, To, Date, Time, Rate }

@Composable
fun TransferScreen(onClose: () -> Unit) {
    val vm = containerViewModel { TransferViewModel(it) }
    val form by vm.form.collectAsStateWithLifecycle()
    val ctx by vm.ctx.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val c = remember(form, ctx, wallets) { vm.compute(form, ctx, wallets) }
    val saving by vm.saving.collectAsStateWithLifecycle()
    val close = rememberCloseOnce(onClose)
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<TransferSheet?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { e -> when (e) { TransferEvent.Saved -> close(); is TransferEvent.Error -> snackbar.showSnackbar(e.message) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New transfer") },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.Default.Close, contentDescription = "Close") } },
            )
        },
        bottomBar = { SaveButton(saving = saving, onClick = vm::save, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            WalletRow("From", c.from, c.fromCurrency) { sheet = TransferSheet.From }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FilledTonalIconButton(onClick = vm::swap) { Icon(Icons.Default.SwapVert, contentDescription = "Swap wallets") }
            }
            WalletRow("To", c.to, c.toCurrency) { sheet = TransferSheet.To }

            OutlinedTextField(
                value = form.amountText, onValueChange = vm::setAmount, singleLine = true,
                label = { Text("Amount") }, suffix = { Text(c.fromCurrency.iso) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AnimatedVisibility(
                visible = c.foreign,
                enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
            ) {
                Column {
                    val rateText = if (c.rate != null) "1 ${c.fromCurrency.iso} = ${formatRate(c.rate)} ${c.toCurrency.symbol}" + (if (form.manualRate != null) " (custom)" else "") else "Rate unavailable — tap to enter one"
                    Row(
                        Modifier.fillMaxWidth().clickable { sheet = TransferSheet.Rate }.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = null, tint = if (c.rate == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(rateText, style = MaterialTheme.typography.bodyMedium, color = if (c.rate == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = c.toText, onValueChange = vm::setToAmount, singleLine = true,
                        label = { Text("Received amount") }, suffix = { Text(c.toCurrency.iso) },
                        supportingText = { Text(if (form.toAmountText == null) "Calculated from the rate; edit to override" else "Custom amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            OutlinedTextField(
                value = form.feeText, onValueChange = vm::setFee, singleLine = true,
                label = { Text("Fee (optional)") }, suffix = { Text(c.fromCurrency.iso) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.weight(1f).clickable { sheet = TransferSheet.Date },
                    leadingContent = { Icon(Icons.Default.Event, contentDescription = null) }, overlineContent = { Text("Date") },
                    headlineContent = { Text(Dates.dayLabelWithYear(form.date)) },
                )
                ListItem(
                    modifier = Modifier.weight(1f).clickable { sheet = TransferSheet.Time },
                    leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) }, overlineContent = { Text("Time") },
                    headlineContent = { Text(Dates.timeLabel(form.date)) },
                )
            }
            OutlinedTextField(value = form.description, onValueChange = vm::setDescription, singleLine = true, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            OutlinedTextField(value = form.note, onValueChange = vm::setNote, minLines = 2, label = { Text("Note") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            Spacer(Modifier.height(32.dp))
        }
    }

    // The wallet sheets animate away and then call onDismiss, so onPick must not clear `sheet`.
    when (sheet) {
        TransferSheet.From -> WalletPickerSheet(wallets, ctx, form.fromWalletId, title = "From wallet", onPick = vm::setFrom, onDismiss = { sheet = null })
        TransferSheet.To -> WalletPickerSheet(wallets, ctx, form.toWalletId, title = "To wallet", excludeId = form.fromWalletId, onPick = vm::setTo, onDismiss = { sheet = null })
        TransferSheet.Date -> WalletDatePickerDialog(Dates.toLocalDate(form.date), onDismiss = { sheet = null }, onPick = { d -> vm.setDate(Dates.toMillis(d.atTime(Dates.toLocalDateTime(form.date).toLocalTime()))) })
        TransferSheet.Time -> WalletTimePickerDialog(Dates.toLocalDateTime(form.date).toLocalTime(), onDismiss = { sheet = null }, onPick = { t -> vm.setDate(Dates.toMillis(Dates.toLocalDate(form.date).atTime(t))) })
        TransferSheet.Rate -> RateDialog(c.fromCurrency.iso, c.toCurrency.iso, current = c.rate, live = c.liveRate, onDismiss = { sheet = null }, onSave = { vm.setRate(it) }, onReset = { vm.setRate(null) })
        null -> Unit
    }
}

@Composable
private fun WalletRow(label: String, wallet: WalletWithBalance?, currency: Currency, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { if (wallet != null) SpecIcon(wallet.icon, size = 40.dp) else Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
        overlineContent = { Text(label) },
        headlineContent = { Text(wallet?.name ?: "Choose a wallet") },
        supportingContent = wallet?.let { w -> { AmountText(w.balance, currency, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
    )
}
