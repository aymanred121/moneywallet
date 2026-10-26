@file:OptIn(ExperimentalMaterial3Api::class)

package com.oriondev.moneywallet.next.ui.screens.transactions.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.MoneyText
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModelWithState
import com.oriondev.moneywallet.next.ui.components.transactions.AmountExpression
import com.oriondev.moneywallet.next.ui.components.transactions.AmountKeypad
import com.oriondev.moneywallet.next.ui.components.transactions.CategoryPickerSheet
import com.oriondev.moneywallet.next.ui.components.transactions.CurrencyPickerSheet
import com.oriondev.moneywallet.next.ui.components.transactions.RateDialog
import com.oriondev.moneywallet.next.ui.components.transactions.SaveButton
import com.oriondev.moneywallet.next.ui.components.transactions.WalletDatePickerDialog
import com.oriondev.moneywallet.next.ui.components.transactions.WalletPickerSheet
import com.oriondev.moneywallet.next.ui.components.transactions.WalletTimePickerDialog
import com.oriondev.moneywallet.next.ui.components.transactions.formatRate
import com.oriondev.moneywallet.next.ui.components.transactions.rememberCloseOnce
import com.oriondev.moneywallet.next.ui.theme.MoneyDisplay
import com.oriondev.moneywallet.next.ui.theme.Motion
import com.oriondev.moneywallet.next.ui.theme.money

/**
 * Amount-first editor. For a new transaction the fast path is FAB → digits → category chip (which
 * saves); the keypad's primary key and the bottom Save button open the category sheet when none is
 * chosen yet. Rarely used fields live under "More options".
 */
@Composable
fun TransactionEditorScreen(
    id: Long?,
    presetWalletId: Long?,
    presetDirection: Int,
    onClose: () -> Unit,
    prefill: EditorPrefill? = null,
    onCreateWallet: () -> Unit = {},
) {
    val vm = containerViewModelWithState(key = "editor-${id ?: "new"}-${prefill?.captureId ?: ""}") { c, state ->
        TransactionEditorViewModel(c, id, presetWalletId, presetDirection, prefill, state)
    }
    val form by vm.form.collectAsStateWithLifecycle()
    val ctx by vm.ctx.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val currencies by vm.currencies.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val quick by vm.quickCategories.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val noWallet by vm.noWallet.collectAsStateWithLifecycle()
    val computed = remember(form, ctx, wallets, categories) { vm.compute(form, ctx, wallets, categories) }

    val close = rememberCloseOnce(onClose)
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<EditorSheet?>(null) }
    /** The category sheet was opened by Save/Done, so picking in it saves. */
    var saveAfterPick by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                EditorEvent.Saved, EditorEvent.Deleted -> close()
                is EditorEvent.Error -> snackbar.showSnackbar(e.message)
            }
        }
    }

    val needsCategory = computed.category == null && !form.isTransferLeg
    val primary: () -> Unit = {
        when {
            (computed.inputMinor ?: 0L) <= 0L -> vm.save() // surfaces "enter an amount", keypad stays
            needsCategory -> { vm.done(); saveAfterPick = true; sheet = EditorSheet.Category }
            else -> { vm.done(); vm.save() }
        }
    }
    val primaryLabel = if (needsCategory && (computed.inputMinor ?: 0L) > 0L) "Choose category" else "Save"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "New transaction" else "Edit transaction") },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.Default.Close, contentDescription = "Close") } },
                actions = {
                    if (id != null) IconButton(onClick = { confirmDelete = true }, enabled = !saving) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete") }
                },
            )
        },
        bottomBar = {
            // the keypad lives in the form, right under the amount; the bar only holds Save once it closes
            if (!noWallet) Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
                AnimatedVisibility(
                    visible = !form.keypadOpen,
                    enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                    exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
                ) {
                    SaveButton(saving = saving, enabled = form.loaded, onClick = primary, label = primaryLabel)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (noWallet) {
            NoWalletState(onCreateWallet, Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            AmountHero(form, computed, onToggleKeypad = { vm.setKeypadOpen(!form.keypadOpen) }, onCurrency = { sheet = EditorSheet.Currency }, onRate = { sheet = EditorSheet.Rate })

            if (!form.isTransferLeg) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    listOf(Direction.EXPENSE to "Expense", Direction.INCOME to "Income").forEachIndexed { i, (d, label) ->
                        SegmentedButton(
                            selected = form.direction == d, onClick = { vm.setDirection(d) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 2), label = { Text(label) },
                        )
                    }
                }
            }
            // digits straight under the amount they type into; category chips follow, in thumb reach
            AnimatedVisibility(
                visible = form.keypadOpen,
                enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
            ) {
                AmountKeypad(
                    onKey = vm::key, onBackspace = vm::backspace, onClear = vm::clearAmount, onDone = primary,
                    allowDecimal = computed.inputCurrency.decimals > 0, doneLabel = if (saving) "Saving…" else primaryLabel,
                )
            }
            if (!form.isTransferLeg) {
                QuickCategoryRow(
                    chips = QuickCategories.withSelected(quick, computed.category),
                    selectedId = form.categoryId,
                    hint = id == null && computed.category == null && (computed.inputMinor ?: 0L) > 0L,
                    onPick = vm::quickPick,
                    onAll = { vm.setKeypadOpen(false); saveAfterPick = false; sheet = EditorSheet.Category },
                )
            }

            Row(Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.weight(1.2f).clickable(enabled = !form.isTransferLeg, onClickLabel = "Change wallet") { vm.setKeypadOpen(false); sheet = EditorSheet.Wallet },
                    leadingContent = { if (computed.wallet != null) SpecIcon(computed.wallet.icon, size = 36.dp) else Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    overlineContent = { Text("Wallet") },
                    headlineContent = { Text(computed.wallet?.name ?: "Choose a wallet", maxLines = 1) },
                    supportingContent = computed.wallet?.let { w -> { AmountText(w.balance, computed.walletCurrency, style = MaterialTheme.typography.bodySmall) } },
                )
                ListItem(
                    modifier = Modifier.weight(1f).clickable(onClickLabel = "Change date") { sheet = EditorSheet.Date },
                    leadingContent = { Icon(Icons.Default.Event, contentDescription = null) },
                    overlineContent = { Text("Date") },
                    headlineContent = { Text(Dates.dayLabelWithYear(form.date), maxLines = 1) },
                )
            }

            var descriptionFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = form.description, onValueChange = vm::setDescription, singleLine = true,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .onFocusChanged { descriptionFocused = it.isFocused; if (it.isFocused) vm.setKeypadOpen(false) else vm.clearSuggestions() },
            )
            AnimatedVisibility(
                visible = descriptionFocused && suggestions.isNotEmpty(),
                enter = expandVertically(Motion.spatialFast()) + fadeIn(Motion.effects()),
                exit = shrinkVertically(Motion.spatialFast()) + fadeOut(Motion.effects()),
            ) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { s -> SuggestionChip(onClick = { vm.pickSuggestion(s) }, label = { Text(s) }) }
                }
            }

            MoreOptions(form, onToggle = { vm.setMoreOpen(!form.moreOpen) }) {
                ListItem(
                    modifier = Modifier.clickable(onClickLabel = "Change time") { sheet = EditorSheet.Time },
                    leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    overlineContent = { Text("Time") },
                    headlineContent = { Text(Dates.timeLabel(form.date)) },
                )
                OutlinedTextField(
                    value = form.note, onValueChange = vm::setNote, minLines = 2,
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).onFocusChanged { if (it.isFocused) vm.setKeypadOpen(false) },
                )
                ListItem(
                    headlineContent = { Text("Confirmed") },
                    supportingContent = { Text("Unconfirmed transactions are excluded from balances") },
                    trailingContent = { Switch(checked = form.confirmed, onCheckedChange = null) },
                    modifier = Modifier.clickable { vm.setConfirmed(!form.confirmed) },
                )
                ListItem(
                    headlineContent = { Text("Count in total") },
                    supportingContent = { Text("Include this transaction in wallet totals and reports") },
                    trailingContent = { Switch(checked = form.countInTotal, onCheckedChange = null) },
                    modifier = Modifier.clickable { vm.setCountInTotal(!form.countInTotal) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Picker sheets animate themselves away and then call onDismiss, so onPick must not clear `sheet`.
    when (sheet) {
        EditorSheet.Category -> CategoryPickerSheet(
            categories, categoryTypeFor(form.direction), form.categoryId,
            onPick = { vm.setCategory(it.id); if (saveAfterPick) vm.save(); saveAfterPick = false },
            onDismiss = { sheet = null; saveAfterPick = false },
        )
        EditorSheet.Wallet -> WalletPickerSheet(wallets, ctx, form.walletId, onPick = vm::setWallet, onDismiss = { sheet = null })
        EditorSheet.Currency -> CurrencyPickerSheet(currencies, computed.inputCurrency.iso, onPick = { vm.setInputCurrency(it.iso) }, onDismiss = { sheet = null })
        EditorSheet.Date -> WalletDatePickerDialog(
            initial = Dates.toLocalDate(form.date), onDismiss = { sheet = null },
            onPick = { d -> vm.setDate(Dates.toMillis(d.atTime(Dates.toLocalDateTime(form.date).toLocalTime()))) },
        )
        EditorSheet.Time -> WalletTimePickerDialog(
            initial = Dates.toLocalDateTime(form.date).toLocalTime(), onDismiss = { sheet = null },
            onPick = { t -> vm.setDate(Dates.toMillis(Dates.toLocalDate(form.date).atTime(t))) },
        )
        EditorSheet.Rate -> RateDialog(
            fromIso = computed.inputCurrency.iso, toIso = computed.walletCurrency.iso, current = computed.rate, live = computed.liveRate,
            onDismiss = { sheet = null }, onSave = { vm.setManualRate(it) }, onReset = { vm.setManualRate(null) },
        )
        null -> Unit
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = { Text(if (form.isTransferLeg) "This will delete the whole transfer." else "This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private enum class EditorSheet { Category, Wallet, Currency, Date, Time, Rate }

/** One-tap categories under the amount, plus an "All" chip for the full sheet. */
@Composable
private fun QuickCategoryRow(chips: List<CategoryEntity>, selectedId: Long?, hint: Boolean, onPick: (CategoryEntity) -> Unit, onAll: () -> Unit) {
    // the row first composes with only "All"; keyed items would keep it anchored there once the
    // ranked chips arrive, so jump back to the start whenever the ranking changes
    val listState = rememberLazyListState()
    LaunchedEffect(chips.map { it.id }) { listState.scrollToItem(0) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chips, key = { it.id }) { c ->
                FilterChip(
                    selected = c.id == selectedId, onClick = { onPick(c) },
                    label = { Text(c.name, maxLines = 1) },
                    leadingIcon = { SpecIcon(c.icon, size = 18.dp) },
                    modifier = Modifier.animateItem(fadeInSpec = Motion.effects(), placementSpec = Motion.spatialFast(), fadeOutSpec = Motion.effects()),
                )
            }
            item(key = "all") {
                AssistChip(onClick = onAll, label = { Text("All") }, leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null, Modifier.size(18.dp)) })
            }
        }
        AnimatedVisibility(visible = hint, enter = fadeIn(Motion.effects()), exit = fadeOut(Motion.effects())) {
            Text(
                "Tap a category to save", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/** Collapsible home for the rarely changed fields. */
@Composable
private fun MoreOptions(form: EditorForm, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val rotation by animateFloatAsState(if (form.moreOpen) 180f else 0f, Motion.spatialFast(), label = "chevron")
    ListItem(
        modifier = Modifier.clickable(onClickLabel = if (form.moreOpen) "Hide options" else "Show options", onClick = onToggle),
        headlineContent = { Text("More options") },
        supportingContent = if (form.moreOpen) null else ({ Text("Time, note, confirmed, count in total") }),
        trailingContent = { Icon(Icons.Default.ExpandMore, contentDescription = null, Modifier.rotate(rotation)) },
    )
    AnimatedVisibility(
        visible = form.moreOpen,
        enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
        exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
    ) { Column { content() } }
}

@Composable
private fun NoWalletState(onCreateWallet: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Create a wallet first", style = MaterialTheme.typography.titleLarge)
        Text(
            "Every transaction belongs to a wallet. Add one, then come back to log this.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Button(onClick = onCreateWallet, modifier = Modifier.height(48.dp)) { Text("Create wallet") }
    }
}

@Composable
private fun AmountHero(form: EditorForm, c: EditorComputed, onToggleKeypad: () -> Unit, onCurrency: () -> Unit, onRate: () -> Unit) {
    val tint = if (form.direction == Direction.INCOME) MaterialTheme.money.income else MaterialTheme.money.expense
    // amounts and expressions read left to right in every locale
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).animateContentSize(Motion.spatial()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (AmountExpression.hasOperator(form.expr)) {
                Text(form.expr, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyText(
                    MoneyFormat.format(c.inputMinor ?: 0L, c.inputCurrency, hidden = false),
                    style = MoneyDisplay, color = if (form.expr.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else tint,
                    modifier = Modifier.clickable(onClickLabel = if (form.keypadOpen) "Hide keypad" else "Edit amount", onClick = onToggleKeypad),
                )
                AssistChip(onClick = onCurrency, label = { Text(c.inputCurrency.iso) }, trailingIcon = { Icon(Icons.Default.UnfoldMore, contentDescription = "Change currency") })
            }
            AnimatedVisibility(
                visible = c.foreign,
                enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
            ) {
                val text = if (c.rate != null) {
                    "1 ${c.inputCurrency.iso} = ${formatRate(c.rate)} ${c.walletCurrency.symbol}  ≈ ${MoneyFormat.format(c.convertedMinor ?: 0L, c.walletCurrency, hidden = false)}" +
                        if (form.manualRate != null) "  (custom)" else ""
                } else "Rate unavailable — tap to enter one"
                Row(
                    Modifier.padding(top = 8.dp).clickable(onClickLabel = "Edit exchange rate", onClick = onRate).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, tint = if (c.rate == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(text, style = MaterialTheme.typography.bodyMedium, color = if (c.rate == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
