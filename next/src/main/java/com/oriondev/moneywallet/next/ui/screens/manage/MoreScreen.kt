package com.oriondev.moneywallet.next.ui.screens.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.BuildConfig
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.currency.RatesWorker
import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer

@Composable
fun MoreScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by remember(container) { container.prefs.settings }.collectAsStateWithLifecycle(initialValue = Settings())

    var showAbout by remember { mutableStateOf(false) }

    ManageScaffold(title = "More", onBack = null) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            GroupLabel("Manage")
            SettingRow("Wallets", subtitle = "Accounts, cash and cards", icon = Icons.Outlined.AccountBalanceWallet) { navController.navigate(Routes.WALLETS) }
            SettingRow("Categories", subtitle = "Organise expenses and income", icon = Icons.Outlined.Category) { navController.navigate(Routes.CATEGORIES) }
            SettingRow("Budgets", subtitle = "Spending limits per period", icon = Icons.Outlined.Savings) { navController.navigate(Routes.BUDGETS) }
            SettingRow("Savings goals", subtitle = "Targets and a monthly pace to reach them", icon = Icons.Outlined.Flag) { navController.navigate(Routes.GOALS) }
            SettingRow("Review", subtitle = "This month at a glance", icon = Icons.Outlined.Summarize) { navController.navigate(Routes.review(Period.encode(Period.currentMonth()))) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            GroupLabel("Data")
            SettingRow(
                "Exchange rates",
                subtitle = if (settings.ratesLastUpdate > 0) "Updated ${Dates.dayLabelWithYear(settings.ratesLastUpdate)} ${Dates.timeLabel(settings.ratesLastUpdate)}" else "Never updated",
                icon = Icons.Outlined.CurrencyExchange,
                trailing = { IconButton(onClick = { RatesWorker.refreshNow(context) }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh rates") } },
            )
            SettingRow("Payment capture", subtitle = "Add payments from Google Wallet and bank notifications", icon = Icons.Outlined.Contactless) { navController.navigate(Routes.CAPTURES) }
            SettingRow("Backup & restore", subtitle = "Export or import a .mwbx file", icon = Icons.Outlined.SettingsBackupRestore) { navController.navigate(Routes.BACKUP) }
            SettingRow("Settings", subtitle = "Appearance, money, security", icon = Icons.Outlined.Settings) { navController.navigate(Routes.SETTINGS) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingRow("About", subtitle = "Version ${BuildConfig.VERSION_NAME}", icon = Icons.Outlined.Info) { showAbout = true }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        title = { Text("MoneyWallet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Your data stays on this device.")
                Text("Built on the original open-source MoneyWallet by AndreAle94.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
