package com.oriondev.moneywallet.next.ui.screens.manage

import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.BuildConfig
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.currency.RatesWorker
import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.data.prefs.ThemeMode
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.screens.manage.pickers.CurrencyPickerSheet
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember(container) { container.prefs.settings }.collectAsStateWithLifecycle(initialValue = Settings())
    val ctx by rememberCurrencyContext()
    var showCurrency by remember { mutableStateOf(false) }
    var showFirstDay by remember { mutableStateOf(false) }
    var showMonthStart by remember { mutableStateOf(false) }
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    ManageScaffold(title = "Settings", onBack = { navController.popBackStack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            GroupLabel("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        ThemeMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(selected = settings.themeMode == mode, onClick = { scope.launch { container.prefs.setThemeMode(mode) } }, shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) {
                                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                },
            )
            SwitchRow("Dynamic colour", settings.dynamicColor, { v -> scope.launch { container.prefs.setDynamicColor(v) } }, subtitle = "Follow the wallpaper palette (Android 12+)")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            GroupLabel("Money")
            SettingRow("Global currency", subtitle = "${ctx.base.name} (${ctx.base.iso}) - used for totals across wallets", icon = Icons.Outlined.Payments) { showCurrency = true }
            SwitchRow("Show cents", settings.showCents, { v -> scope.launch { container.prefs.setShowCents(v) } }, subtitle = "Display decimal digits in amounts")
            SettingRow("First day of week", subtitle = DayOfWeek.of(settings.firstDayOfWeek).getDisplayName(TextStyle.FULL, Locale.getDefault()), icon = Icons.Outlined.CalendarMonth) { showFirstDay = true }
            SettingRow("Month starts on", subtitle = if (settings.firstDayOfMonth == 1) "1st (calendar month)" else "Day ${settings.firstDayOfMonth} of each month", icon = Icons.Outlined.CalendarMonth) { showMonthStart = true }
            SwitchRow("Hide balances", settings.hideBalances, { v -> scope.launch { container.prefs.setHideBalances(v) } }, subtitle = "Mask wallet balances on the home screen, wallet list and widget")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            GroupLabel("Notifications")
            val notifications = com.oriondev.moneywallet.next.ui.components.rememberNotificationPermission()
            SwitchRow("Weekly and monthly review", settings.reviewNotifications, { v -> scope.launch { container.prefs.setReviewNotifications(v) }; if (v) notifications.request() }, subtitle = "A summary when each week and payday month ends", icon = Icons.Outlined.Notifications)
            SwitchRow("Budget alerts", settings.budgetAlerts, { v -> scope.launch { container.prefs.setBudgetAlerts(v) }; if (v) notifications.request() }, subtitle = "When a budget reaches 80% and 100%")
            if ((settings.reviewNotifications || settings.budgetAlerts) && !notifications.granted) {
                SettingRow("Notifications are blocked", subtitle = "Open settings to allow them", icon = Icons.Outlined.Info) {
                    com.oriondev.moneywallet.next.ui.components.openNotificationSettings(context)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            GroupLabel("Security")
            SwitchRow(
                "Biometric lock", settings.biometricLock && biometricAvailable, { v -> scope.launch { container.prefs.setBiometricLock(v) } },
                subtitle = if (biometricAvailable) "Require fingerprint, face or device PIN to open the app" else "No biometric or device credential is set up on this device",
                icon = Icons.Outlined.Fingerprint, enabled = biometricAvailable,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            GroupLabel("Data")
            SettingRow("Backup & restore", subtitle = "Export or import a .mwbx file", icon = Icons.Outlined.SettingsBackupRestore) { navController.navigate(Routes.BACKUP) }
            SettingRow(
                "Exchange rates",
                subtitle = if (settings.ratesLastUpdate > 0) "Updated ${Dates.dayLabelWithYear(settings.ratesLastUpdate)} ${Dates.timeLabel(settings.ratesLastUpdate)}" else "Never updated",
                icon = Icons.Outlined.CurrencyExchange,
                trailing = { IconButton(onClick = { RatesWorker.refreshNow(context) }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh rates") } },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            GroupLabel("About")
            SettingRow("MoneyWallet", subtitle = "Version ${BuildConfig.VERSION_NAME}. Backups stay compatible with the original MoneyWallet app.", icon = Icons.Outlined.Info)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCurrency) CurrencyPickerSheet(selected = ctx.baseIso, onDismiss = { showCurrency = false }) { iso -> scope.launch { container.prefs.setBaseCurrency(iso) } }
    if (showMonthStart) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMonthStart = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showMonthStart = false }) { Text("Done") } },
            title = { Text("Month starts on") },
            text = {
                Column {
                    Text("Reports, budgets and the home screen use months that start on this day.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..28).forEach { day ->
                            androidx.compose.material3.FilterChip(
                                selected = settings.firstDayOfMonth == day,
                                onClick = { scope.launch { container.prefs.setFirstDayOfMonth(day) } },
                                label = { Text(day.toString()) },
                            )
                        }
                    }
                }
            },
        )
    }
    if (showFirstDay) {
        ModalBottomSheet(onDismissRequest = { showFirstDay = false }) {
            Text("First day of week", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { day ->
                ListItem(
                    headlineContent = { Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                    trailingContent = { if (settings.firstDayOfWeek == day.value) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { scope.launch { container.prefs.setFirstDayOfWeek(day.value) }; showFirstDay = false },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
