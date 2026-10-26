package com.oriondev.moneywallet.next.ui.screens.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.csv.TransactionCsv
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.AmountTint
import com.oriondev.moneywallet.next.ui.components.SpecIcon

/** Column mapping (for foreign files), target wallet and a preview of the first rows before a CSV import. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CsvImportSheet(
    draft: CsvDraft,
    wallets: List<WalletWithBalance>,
    ctx: CurrencyContext,
    onChange: ((CsvDraft) -> CsvDraft) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val parsed = remember(draft.table, draft.own, draft.mapping, draft.hasHeader) { draft.parse() }
    val wallet = wallets.firstOrNull { it.id == draft.walletId }
    val width = draft.table.maxOf { it.size }
    val columns = (0 until width).map { i ->
        val head = draft.table.first().getOrNull(i)?.trim().orEmpty()
        if (draft.hasHeader && head.isNotEmpty()) head else "Column ${i + 1}" + (draft.table.first().getOrNull(i)?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Import spreadsheet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                if (draft.own) "MoneyWallet export detected: columns are matched automatically." else "Tell us which column holds what. Negative amounts count as expenses unless a direction column says otherwise.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (!draft.own) {
                SwitchRow("First row is a header", checked = draft.hasHeader, onCheckedChange = { v -> onChange { it.copy(hasHeader = v) } })
                val m = draft.mapping
                ColumnChoice("Date", columns, m.date, optional = false) { i -> onChange { it.copy(mapping = it.mapping.copy(date = i ?: 0)) } }
                ColumnChoice("Amount", columns, m.amount, optional = false) { i -> onChange { it.copy(mapping = it.mapping.copy(amount = i ?: 0)) } }
                ColumnChoice("Description", columns, m.description, optional = true) { i -> onChange { it.copy(mapping = it.mapping.copy(description = i)) } }
                ColumnChoice("Category", columns, m.category, optional = true) { i -> onChange { it.copy(mapping = it.mapping.copy(category = i)) } }
                ColumnChoice("Direction (expense / income)", columns, m.direction, optional = true) { i -> onChange { it.copy(mapping = it.mapping.copy(direction = i)) } }
            }
            GroupLabel(if (draft.own) "Wallet for rows whose wallet is not found" else "Into wallet")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                wallets.filter { !it.archived }.forEach { w ->
                    FilterChip(selected = w.id == draft.walletId, onClick = { onChange { it.copy(walletId = w.id) } }, label = { Text(w.name) }, leadingIcon = { SpecIcon(w.icon, 20.dp) })
                }
            }
            GroupLabel("Preview")
            if (parsed.rows.isEmpty()) Text("No readable rows with this setup.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp))
            parsed.rows.take(5).forEach { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(r.description.ifEmpty { "No description" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Dates.dayLabelWithYear(Dates.toMillis(r.date)) + (r.category?.let { " · $it" } ?: "") + if (r.transfer) " · transfer (skipped)" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val cur = ctx.currency(r.currency ?: wallet?.currency ?: ctx.baseIso)
                    val minor = TransactionCsv.toMinor(r.amount.abs(), cur.decimals)
                    AmountText(if (r.expense) -minor else minor, cur, tint = AmountTint.AUTO, signed = true)
                }
            }
            if (parsed.rows.size > 5) Text("…and ${parsed.rows.size - 5} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            if (parsed.problems.isNotEmpty()) Text("Will skip ${parsed.problems.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Button(onClick = onImport, enabled = parsed.rows.isNotEmpty() && wallet != null, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp)) {
                Text("Import ${parsed.rows.size} ${if (parsed.rows.size == 1) "row" else "rows"}")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColumnChoice(label: String, columns: List<String>, selected: Int?, optional: Boolean, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingRow(label, subtitle = selected?.let { columns.getOrNull(it) } ?: "None", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (optional) DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); open = false })
            columns.forEachIndexed { i, c -> DropdownMenuItem(text = { Text(c, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onSelect(i); open = false }) }
        }
    }
}
