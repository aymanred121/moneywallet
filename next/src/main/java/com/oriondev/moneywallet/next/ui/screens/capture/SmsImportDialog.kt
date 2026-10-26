package com.oriondev.moneywallet.next.ui.screens.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.oriondev.moneywallet.next.capture.SmsImporter
import kotlinx.coroutines.launch

private sealed interface ImportState {
    data object Choose : ImportState
    data object Running : ImportState
    data class Done(val report: SmsImporter.Report) : ImportState
    data class Failed(val message: String) : ImportState
}

/**
 * "Import recent bank SMS": pick a window, grant READ_SMS (asked only here), scan, report.
 * [onReview] opens the capture inbox when something was queued.
 */
@Composable
fun SmsImportDialog(onDismiss: () -> Unit, onReview: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(30) }
    var state by remember { mutableStateOf<ImportState>(ImportState.Choose) }

    fun run() {
        state = ImportState.Running
        scope.launch {
            state = try { ImportState.Done(SmsImporter.import(context, days)) } catch (e: Exception) { ImportState.Failed(e.message ?: "Could not read your messages") }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) run() else state = ImportState.Failed("MoneyWallet needs permission to read SMS to import bank messages. Nothing was read.")
    }
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) run()
        else permission.launch(Manifest.permission.READ_SMS)
    }

    AlertDialog(
        onDismissRequest = { if (state != ImportState.Running) onDismiss() },
        title = { Text("Import recent bank SMS") },
        text = {
            when (val s = state) {
                ImportState.Choose -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Reads your SMS inbox once and adds bank payments to the review list. Other messages are skipped and never saved; " +
                            "payments you already recorded are left out.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(7 to "Last 7 days", 30 to "Last 30 days", 90 to "Last 90 days").forEach { (d, label) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = days == d, onClick = { days = d }, role = Role.RadioButton).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = days == d, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                ImportState.Running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Reading messages…")
                }
                is ImportState.Done -> {
                    val r = s.report
                    Text(
                        buildString {
                            append(
                                when (r.added) {
                                    0 -> "No new bank payments found"
                                    1 -> "1 payment is ready to review"
                                    else -> "${r.added} payments are ready to review"
                                },
                            )
                            append(".\n\n${r.payments} of ${r.scanned} messages were bank payments.")
                            if (r.alreadyRecorded > 0) append("\n${r.alreadyRecorded} already in your transactions.")
                            if (r.alreadyCaptured > 0) append("\n${r.alreadyCaptured} already captured before.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is ImportState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            when (val s = state) {
                ImportState.Choose -> TextButton(onClick = ::start) { Text("Import") }
                ImportState.Running -> Unit
                is ImportState.Done -> if (s.report.added > 0) TextButton(onClick = { onDismiss(); onReview() }) { Text("Review now") } else TextButton(onClick = onDismiss) { Text("Done") }
                is ImportState.Failed -> TextButton(onClick = { state = ImportState.Choose }) { Text("Try again") }
            }
        },
        dismissButton = {
            if (state == ImportState.Choose || state is ImportState.Failed) TextButton(onClick = onDismiss) { Text("Cancel") }
            else if (state is ImportState.Done && (state as ImportState.Done).report.added > 0) TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
}
