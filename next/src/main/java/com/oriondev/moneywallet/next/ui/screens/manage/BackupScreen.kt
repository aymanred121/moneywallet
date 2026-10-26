package com.oriondev.moneywallet.next.ui.screens.manage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.data.backup.BackupCodec
import com.oriondev.moneywallet.next.data.csv.Csv
import com.oriondev.moneywallet.next.data.csv.CsvTransactions
import com.oriondev.moneywallet.next.data.csv.TransactionCsv
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed class BackupState {
    data object Idle : BackupState()
    data class Working(val label: String) : BackupState()
    data class Exported(val name: String) : BackupState()
    data class Imported(val report: BackupCodec.ImportReport) : BackupState()
    data class CsvImported(val report: CsvTransactions.Report) : BackupState()
    data class Failed(val message: String) : BackupState()
}

/** A spreadsheet read from disk and waiting for the user to confirm columns and wallet. */
data class CsvDraft(val table: List<List<String>>, val own: Boolean, val mapping: TransactionCsv.Mapping, val hasHeader: Boolean = true, val walletId: Long? = null) {
    fun parse(): TransactionCsv.Result = TransactionCsv.parse(table, if (own) null else mapping, hasHeader)
}

class BackupViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow<BackupState>(BackupState.Idle)
    val csvDraft = MutableStateFlow<CsvDraft?>(null)
    val wallets = container.repo.wallets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val csv = CsvTransactions(container.db)

    private fun task(label: String, fallback: String, block: suspend () -> BackupState) = viewModelScope.launch {
        state.value = BackupState.Working(label)
        state.value = try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { BackupState.Failed(e.message ?: fallback) }
    }

    fun export(context: android.content.Context, uri: Uri, name: String) = task("Exporting…", "Export failed") {
        context.contentResolver.openOutputStream(uri, "wt")?.use { container.backup.export(it) } ?: error("Could not open the destination")
        BackupState.Exported(name)
    }

    fun import(context: android.content.Context, uri: Uri) = task("Restoring…", "Restore failed") {
        BackupState.Imported(context.contentResolver.openInputStream(uri)?.use { container.backup.import(it, replace = true) } ?: error("Could not open the file"))
    }

    fun exportCsv(context: android.content.Context, uri: Uri, name: String) = task("Exporting…", "Export failed") {
        context.contentResolver.openOutputStream(uri, "wt")?.use { csv.export(it) } ?: error("Could not open the destination")
        BackupState.Exported(name)
    }

    fun openCsv(context: android.content.Context, uri: Uri) = task("Reading…", "Could not read the file") {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not open the file")
        val table = Csv.parse(Csv.decode(bytes))
        if (table.isEmpty()) error("The file is empty")
        val header = table.first()
        csvDraft.value = CsvDraft(table, TransactionCsv.isOwnFormat(header), TransactionCsv.guessMapping(header), walletId = wallets.value.firstOrNull { !it.archived }?.id)
        BackupState.Idle
    }

    fun updateDraft(f: (CsvDraft) -> CsvDraft) { csvDraft.value = csvDraft.value?.let(f) }

    fun importCsv() {
        val draft = csvDraft.value ?: return
        val walletId = draft.walletId ?: return
        csvDraft.value = null
        task("Importing…", "Import failed") {
            val parsed = draft.parse()
            BackupState.CsvImported(csv.import(parsed.rows, walletId, parsed.problems))
        }
    }

    fun reset() { state.value = BackupState.Idle }
}

fun backupFileName(): String = "backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".mwbx"
fun csvFileName(): String = "moneywallet-transactions-${LocalDate.now()}.csv"

@Composable
fun BackupScreen(navController: NavHostController) {
    val vm = containerViewModel { BackupViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val draft by vm.csvDraft.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val ctx by rememberCurrencyContext()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingName by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) vm.export(context, uri, pendingName) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestore = uri }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) vm.exportCsv(context, uri, pendingName) }
    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.openCsv(context, uri) }

    LaunchedEffect(state) {
        when (val s = state) {
            is BackupState.Failed -> { snackbar.showSnackbar(s.message); vm.reset() }
            is BackupState.Exported -> snackbar.showSnackbar("Saved ${s.name}")
            else -> Unit
        }
    }

    val working = state is BackupState.Working

    ManageScaffold(title = "Backup & restore", onBack = { navController.popBackStack() }, snackbarHostState = snackbar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BackupCard(Icons.Outlined.CloudUpload, "Export", "Saves wallets, categories, transactions, transfers, budgets and savings goals to a .mwbx file. The file uses the original MoneyWallet backup format, so the legacy app can open it as well.") {
                Button(onClick = { pendingName = backupFileName(); exportLauncher.launch(pendingName) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Export backup") }
            }
            BackupCard(Icons.Outlined.CloudDownload, "Restore", "Reads a .mwbx file created by this app or by the original MoneyWallet. Restoring replaces all current data. Legacy savings become goals; debts, events, places, people, recurring items and models are skipped.") {
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/*", "*/*")) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Restore from file") }
            }
            BackupCard(Icons.Outlined.TableChart, "Spreadsheet (CSV)", "Export every transaction for Excel or Google Sheets, or add transactions from a bank or spreadsheet file. Importing never deletes anything and skips rows you already have.") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { pendingName = csvFileName(); csvExportLauncher.launch(pendingName) }, enabled = !working, modifier = Modifier.weight(1f)) { Text("Export CSV") }
                    OutlinedButton(
                        onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) },
                        enabled = !working && wallets.isNotEmpty(), modifier = Modifier.weight(1f),
                    ) { Text("Import CSV") }
                }
            }
            AnimatedVisibility(working, enter = fadeIn(Motion.effects()) + expandVertically(Motion.spatial()), exit = fadeOut(Motion.effects()) + shrinkVertically(Motion.spatial())) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text((state as? BackupState.Working)?.label ?: "Working…", style = MaterialTheme.typography.bodyLarge)
                }
            }
            val done = state as? BackupState.Imported
            AnimatedVisibility(done != null, enter = fadeIn(Motion.effects()) + expandVertically(Motion.spatial()), exit = fadeOut(Motion.effects()) + shrinkVertically(Motion.spatial())) {
                val r = done?.report
                if (r != null) ResultCard("Restore complete", onDismiss = { vm.reset() }) {
                    Text("${r.wallets} wallets · ${r.categories} categories · ${r.transactions} transactions · ${r.transfers} transfers · ${r.budgets} budgets · ${r.goals} goals", style = MaterialTheme.typography.bodyMedium)
                    if (r.skipped.isNotEmpty()) WarningLine("Skipped legacy sections: ${r.skipped.joinToString(", ") { it.replace('_', ' ') }}")
                    if (r.problems.isNotEmpty()) WarningLine("Not restored: ${r.problems.joinToString(", ")}")
                }
            }
            val csvDone = state as? BackupState.CsvImported
            AnimatedVisibility(csvDone != null, enter = fadeIn(Motion.effects()) + expandVertically(Motion.spatial()), exit = fadeOut(Motion.effects()) + shrinkVertically(Motion.spatial())) {
                val r = csvDone?.report
                if (r != null) ResultCard("Import complete", onDismiss = { vm.reset() }) {
                    Text(
                        "${r.imported} imported · ${r.duplicates} already there" + if (r.categoriesCreated > 0) " · ${r.categoriesCreated} new categories" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (r.problems.isNotEmpty()) WarningLine("Skipped: ${r.problems.joinToString(", ")}")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    draft?.let { d ->
        CsvImportSheet(d, wallets, ctx, onChange = vm::updateDraft, onImport = vm::importCsv, onDismiss = { vm.csvDraft.value = null })
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
            title = { Text("Replace all current data?") },
            text = { Text("Restoring a backup deletes every wallet, category, transaction, budget and goal currently in the app and replaces them with the file contents. Export a backup first if you want to keep them.") },
            confirmButton = { TextButton(onClick = { pendingRestore = null; vm.import(context, uri) }) { Text("Restore", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BackupCard(icon: ImageVector, title: String, body: String, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}

@Composable
private fun ResultCard(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
