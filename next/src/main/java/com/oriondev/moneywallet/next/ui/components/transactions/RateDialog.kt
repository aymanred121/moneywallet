package com.oriondev.moneywallet.next.ui.components.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

fun formatRate(rate: Double): String = when {
    rate == 0.0 -> "0"
    rate >= 100 -> String.format(Locale.US, "%.2f", rate)
    rate >= 1 -> String.format(Locale.US, "%.4f", rate)
    else -> String.format(Locale.US, "%.6f", rate)
}.trimEnd('0').trimEnd('.')

/** Edit an exchange rate "1 [fromIso] = x [toIso]" with an optional reset to the live value. */
@Composable
fun RateDialog(
    fromIso: String,
    toIso: String,
    current: Double?,
    live: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onReset: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current?.let { formatRate(it) } ?: "") }
    val parsed = text.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exchange rate") },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    prefix = { Text("1 $fromIso = ") }, suffix = { Text(toIso) },
                    isError = text.isNotEmpty() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (live != null) "Live rate: 1 $fromIso = ${formatRate(live)} $toIso" else "No live rate available for this pair.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let(onSave); onDismiss() }) { Text("Save") } },
        dismissButton = {
            if (live != null) TextButton(onClick = { onReset(); onDismiss() }) { Text("Reset to live rate") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
