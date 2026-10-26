package com.oriondev.moneywallet.next.ui.screens.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.BudgetEntity
import com.oriondev.moneywallet.next.data.db.BudgetType
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.screens.manage.pickers.CurrencyPickerSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

data class BudgetForm(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val type: Int = BudgetType.EXPENSES,
    val categoryId: Long? = null,
    val amount: String = "",
    val currency: String? = null,
    val start: LocalDate = Dates.monthStart(Dates.currentMonthAnchor()),
    val end: LocalDate = Dates.monthEndExclusive(Dates.currentMonthAnchor()).minusDays(1),
    val walletIds: Set<Long> = emptySet(),
    val loaded: Boolean = false,
)

class EditBudgetViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    val form = MutableStateFlow(BudgetForm(id = id))
    val saved = MutableStateFlow(false)
    val wallets = container.repo.wallets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = container.repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val ctx = container.repo.currencyContext.first()
            if (id == 0L) {
                val all = container.db.wallets().all().filter { !it.archived }.map { it.id }.toSet()
                form.value = form.value.copy(currency = ctx.baseIso, walletIds = all, loaded = true)
            } else {
                val b = container.db.budgets().byId(id) ?: return@launch
                form.value = BudgetForm(
                    id = b.id, uuid = b.uuid, type = b.type, categoryId = b.categoryId,
                    amount = MoneyFormat.toDecimal(b.money, ctx.currency(b.currency)).toPlainString(), currency = b.currency,
                    start = Dates.toLocalDate(b.startDate), end = Dates.toLocalDate(b.endDate),
                    walletIds = container.db.budgets().walletIds(b.id).toSet(), loaded = true,
                )
            }
        }
    }

    fun update(f: (BudgetForm) -> BudgetForm) { form.value = f(form.value) }

    fun save() = viewModelScope.launch {
        val f = form.value
        val ctx = container.repo.currencyContext.first()
        val iso = f.currency ?: ctx.baseIso
        val minor = MoneyFormat.parse(f.amount, ctx.currency(iso)) ?: return@launch
        container.db.budgets().save(
            BudgetEntity(id = f.id, uuid = f.uuid, type = f.type, categoryId = if (f.type == BudgetType.CATEGORY) f.categoryId else null, startDate = Dates.startOfDay(f.start), endDate = Dates.startOfDay(f.end), money = minor, currency = iso, lastEdit = System.currentTimeMillis()),
            f.walletIds.toList(),
        )
        saved.value = true
    }

    fun delete() = viewModelScope.launch {
        container.db.budgets().deleteWallets(id)
        container.db.budgets().deleteById(id)
        saved.value = true
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditBudgetScreen(navController: NavHostController, id: Long) {
    val vm = containerViewModel(key = "budget-$id") { EditBudgetViewModel(it, id) }
    val form by vm.form.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val ctx by rememberCurrencyContext()
    var showCurrency by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var showRange by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (saved) LaunchedEffect(Unit) { navController.popBackStack() }

    val currency = ctx.currencyOrFallback(form.currency)
    val amountMinor = MoneyFormat.parse(form.amount, currency)
    val category = categories.firstOrNull { it.id == form.categoryId }
    val thisMonth = Dates.currentMonthAnchor()
    val nextMonth = thisMonth.plusMonths(1)
    val preset = when {
        form.start == Dates.monthStart(thisMonth) && form.end == Dates.monthEndExclusive(thisMonth).minusDays(1) -> 0
        form.start == Dates.monthStart(nextMonth) && form.end == Dates.monthEndExclusive(nextMonth).minusDays(1) -> 1
        else -> 2
    }
    val valid = amountMinor != null && amountMinor > 0 && form.walletIds.isNotEmpty() && !form.end.isBefore(form.start) && (form.type != BudgetType.CATEGORY || form.categoryId != null)

    ManageScaffold(
        title = if (id == 0L) "New budget" else "Edit budget",
        onBack = { navController.popBackStack() },
        actions = { if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") } },
    ) { padding ->
        if (!form.loaded) { CircularProgressIndicator(Modifier.padding(padding).padding(32.dp)); return@ManageScaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("Track", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(BudgetType.EXPENSES to "Expenses", BudgetType.INCOMES to "Incomes", BudgetType.CATEGORY to "Category").forEachIndexed { i, (t, label) ->
                    SegmentedButton(selected = form.type == t, onClick = { vm.update { it.copy(type = t) } }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                }
            }
            if (form.type == BudgetType.CATEGORY) {
                SettingRow(title = "Category", subtitle = category?.name ?: "Choose a category", onClick = { showCategory = true })
            }
            OutlinedTextField(
                value = form.amount, onValueChange = { v -> vm.update { it.copy(amount = v) } }, label = { Text("Amount") }, singleLine = true,
                prefix = { Text(currency.symbol) }, isError = form.amount.isNotBlank() && amountMinor == null,
                trailingIcon = { TextButton(onClick = { showCurrency = true }) { Text(currency.iso) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            Text("Period", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = preset == 0, onClick = { vm.update { it.copy(start = Dates.monthStart(thisMonth), end = Dates.monthEndExclusive(thisMonth).minusDays(1)) } }, shape = SegmentedButtonDefaults.itemShape(0, 3)) { Text("This month") }
                SegmentedButton(selected = preset == 1, onClick = { vm.update { it.copy(start = Dates.monthStart(nextMonth), end = Dates.monthEndExclusive(nextMonth).minusDays(1)) } }, shape = SegmentedButtonDefaults.itemShape(1, 3)) { Text("Next month") }
                SegmentedButton(selected = preset == 2, onClick = { showRange = true }, shape = SegmentedButtonDefaults.itemShape(2, 3)) { Text("Custom") }
            }
            SettingRow(title = "${Dates.dayLabelWithYear(Dates.startOfDay(form.start))} – ${Dates.dayLabelWithYear(Dates.startOfDay(form.end))}", subtitle = "${Dates.daysBetween(form.start, form.end) + 1} days", onClick = { showRange = true })
            Text("Wallets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                wallets.filter { !it.archived || it.id in form.walletIds }.forEach { w ->
                    val selected = w.id in form.walletIds
                    FilterChip(
                        selected = selected,
                        onClick = { vm.update { f -> f.copy(walletIds = if (selected) f.walletIds - w.id else f.walletIds + w.id) } },
                        label = { Text(w.name) },
                        leadingIcon = { SpecIcon(w.icon, 20.dp) },
                    )
                }
            }
            if (form.walletIds.isEmpty()) Text("Select at least one wallet", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.save() }, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text(if (id == 0L) "Create budget" else "Save changes")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showCurrency) CurrencyPickerSheet(selected = form.currency, onDismiss = { showCurrency = false }) { iso -> vm.update { it.copy(currency = iso) } }
    if (showCategory) {
        ModalBottomSheet(onDismissRequest = { showCategory = false }) {
            Text("Category", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            val options = categories.filter { it.type != CategoryType.SYSTEM }.sortedWith(compareBy({ it.type }, { it.parentId ?: it.id }, { it.parentId != null }, { it.name }))
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(options, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        supportingContent = { Text(if (c.type == CategoryType.INCOME) "Income" else "Expense") },
                        leadingContent = { SpecIcon(c.icon, 36.dp) },
                        modifier = Modifier.padding(start = if (c.parentId != null) 24.dp else 0.dp).clickable { vm.update { it.copy(categoryId = c.id) }; showCategory = false },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (showRange) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = form.start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            initialSelectedEndDateMillis = form.end.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                    onClick = {
                        val s = Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        val e = Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.update { it.copy(start = s, end = e) }
                        showRange = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.heightIn(max = 520.dp), showModeToggle = true)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete budget?") },
            text = { Text("Only the budget is removed; transactions are untouched.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
