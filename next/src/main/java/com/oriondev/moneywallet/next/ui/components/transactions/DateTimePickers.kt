@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.oriondev.moneywallet.next.ui.components.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.oriondev.moneywallet.next.core.Dates
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

private const val DAY_MS = 86_400_000L

/** M3 date picker dialog working in local dates (the picker itself uses UTC midnight millis). */
@Composable
fun WalletDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * DAY_MS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / DAY_MS)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

/** Dialog wrapper around the M3 [TimePicker]. */
@Composable
fun WalletTimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Select time", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") }
                }
            }
        }
    }
}

/** Year stepper + 12 month chips. */
@Composable
fun MonthPickerDialog(initial: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    var year by remember { mutableIntStateOf(initial.year) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text("Select month", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { year-- }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year") }
                    Text(year.toString(), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { year++ }) { Icon(Icons.Default.ChevronRight, contentDescription = "Next year") }
                }
                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 4) {
                    for (m in 1..12) {
                        val ym = YearMonth.of(year, m)
                        FilterChip(
                            selected = ym == initial,
                            onClick = { onPick(ym); onDismiss() },
                            label = { Text(Dates.shortMonthLabel(ym)) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onPick(YearMonth.now()); onDismiss() }) { Text("This month") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}
