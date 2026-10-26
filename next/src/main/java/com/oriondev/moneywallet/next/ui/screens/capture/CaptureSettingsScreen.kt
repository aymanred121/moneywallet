package com.oriondev.moneywallet.next.ui.screens.capture

import com.oriondev.moneywallet.next.ui.Routes
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import com.oriondev.moneywallet.next.capture.PaymentNotificationListener
import androidx.compose.material3.ListItem
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Delete
import com.oriondev.moneywallet.next.ui.theme.money
import com.oriondev.moneywallet.next.capture.HeaderRule
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.capture.CaptureApps
import com.oriondev.moneywallet.next.capture.ParseResult
import com.oriondev.moneywallet.next.capture.PaymentParser
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.components.MoneyText
import com.oriondev.moneywallet.next.ui.screens.manage.GroupLabel
import com.oriondev.moneywallet.next.ui.screens.manage.ManageScaffold
import com.oriondev.moneywallet.next.ui.screens.manage.SettingRow
import com.oriondev.moneywallet.next.ui.screens.manage.SwitchRow
import com.oriondev.moneywallet.next.ui.screens.manage.rememberCurrencyContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SAMPLE = "شراء عبر نقاط البيع\nمبلغ: 45.00 ريال\nلدى: STARBUCKS"

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureSettingsScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by remember(container) { container.prefs.settings }.collectAsStateWithLifecycle(initialValue = Settings())
    val wallets by remember(container) { container.repo.wallets() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val currencies by rememberCurrencyContext()

    // notification access is granted outside the app: re-check whenever we come back
    var access by remember { mutableStateOf(CaptureApps.hasAccess(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { access = CaptureApps.hasAccess(context); PaymentNotificationListener.ensureBound(context) }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var installed by remember { mutableStateOf<List<CaptureApps.App>?>(null) }
    LaunchedEffect(Unit) { installed = withContext(Dispatchers.IO) { runCatching { CaptureApps.launchable(context) }.getOrDefault(emptyList()) } }
    var showAllApps by rememberSaveable { mutableStateOf(false) }
    var pickWallet by remember { mutableStateOf(false) }
    var sample by rememberSaveable { mutableStateOf(SAMPLE) }
    var sampleResult by remember { mutableStateOf<ParseResult?>(null) }
    var lastStrict by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    val selection = CaptureApps.allowed(settings.captureApps)
    fun toggleApp(pkg: String) {
        val next = if (pkg in selection) selection - pkg else selection + pkg
        if (next.isEmpty()) {
            scope.launch { snackbar.showSnackbar("Keep at least one app, or turn capture off") }
            return
        }
        // storing "empty" keeps following the built-in list if it grows in an update
        scope.launch { container.prefs.setCaptureApps(if (next == CaptureApps.defaults.keys) emptySet() else next) }
    }

    ManageScaffold(title = "Payment capture", onBack = { navController.popBackStack() }, snackbarHostState = snackbar) { padding ->
        val apps = installed.orEmpty()
        val (primary, others) = apps.partition { it.packageName in CaptureApps.defaults || it.packageName in selection }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Private by design", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Google Wallet doesn't share your purchases with other apps, so MoneyWallet reads the payment notifications " +
                                    "it and your bank apps show you. Only notifications from the apps you tick below are read; everything else is ignored. " +
                                    "Nothing is uploaded: the text is parsed and kept on this device, and nothing is recorded until you tap Add.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
            item {
                SwitchRow(
                    "Capture payments",
                    checked = settings.captureEnabled && access,
                    onCheckedChange = { on ->
                        scope.launch { container.prefs.setCaptureEnabled(on) }
                        if (on && !access) CaptureApps.openAccessSettings(context)
                    },
                    subtitle = when {
                        settings.captureEnabled && !access -> "Waiting for notification access"
                        settings.captureEnabled -> "New payments appear in the inbox"
                        else -> "Off"
                    },
                    icon = Icons.Outlined.Contactless,
                )
                if (settings.captureEnabled && access) {
                    val listening by PaymentNotificationListener.connected.collectAsStateWithLifecycle()
                    if (listening) {
                        SettingRow("Listening for payments", subtitle = "New bank SMS and wallet notifications are read as they arrive", icon = Icons.Outlined.CheckCircle)
                    } else {
                        SettingRow(
                            "Capture is not running",
                            subtitle = if (CaptureApps.isXiaomi) "Xiaomi blocks it in the background. Tap, turn on Autostart for MoneyWallet, then come back."
                            else "Android stopped the capture service. Tap to restart it.",
                            icon = Icons.Outlined.Warning,
                        ) {
                            PaymentNotificationListener.ensureBound(context)
                            if (CaptureApps.isXiaomi) CaptureApps.openAutostartSettings(context)
                        }
                    }
                }
                SettingRow(
                    "Notification access",
                    subtitle = if (access) "Granted. You can revoke it at any time in system settings." else "Not granted. Tap to allow MoneyWallet to read notifications.",
                    icon = Icons.Outlined.NotificationsActive,
                ) { CaptureApps.openAccessSettings(context) }
                val wallet = wallets.firstOrNull { it.id == settings.captureWalletId }
                SettingRow("Add payments to", subtitle = wallet?.name ?: "The editor's default wallet", icon = Icons.Outlined.AccountBalanceWallet) { pickWallet = true }
                SwitchRow(
                    "Bank SMS",
                    checked = settings.captureSms,
                    onCheckedChange = { on -> scope.launch { container.prefs.setCaptureSms(on) } },
                    subtitle = "Read bank alerts from your SMS app. Only texts with payment wording and a currency are captured, never chats.",
                    icon = Icons.Outlined.Sms,
                )
                SettingRow(
                    "Import recent bank SMS",
                    subtitle = "Add bank payments from messages that arrived before capture was on",
                    icon = Icons.Outlined.MoveToInbox,
                ) { showImport = true }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                GroupLabel("Apps to read")
            }
            if (installed != null && primary.isEmpty()) item {
                Text(
                    "None of the supported wallet or bank apps is installed. Pick your bank's app from all apps below.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(primary, key = { "p:" + it.packageName }) { AppRow(it, it.packageName in selection) { toggleApp(it.packageName) } }
            item {
                TextButton(onClick = { showAllApps = !showAllApps }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showAllApps) "Hide other apps" else "Show all apps (${others.size})")
                }
            }
            if (showAllApps) items(others, key = { "o:" + it.packageName }) { AppRow(it, it.packageName in selection) { toggleApp(it.packageName) } }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                GroupLabel("Test with a sample")
                // keep the whole box and its Test buttons above the keyboard, not just the cursor line
                val sampleArea = remember { BringIntoViewRequester() }
                var sampleFocused by remember { mutableStateOf(false) }
                val imeVisible = WindowInsets.isImeVisible
                LaunchedEffect(sampleFocused, imeVisible, sample.length) { if (sampleFocused && imeVisible) { kotlinx.coroutines.delay(320); sampleArea.bringIntoView() } } // after the IME settles
                Column(Modifier.padding(horizontal = 16.dp).bringIntoViewRequester(sampleArea), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste a payment notification (first line = title) to see what would be captured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = sample, onValueChange = { sample = it; sampleResult = null }, minLines = 3, maxLines = 8,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { sampleFocused = it.isFocused },
                    )
                    val rules = remember(settings.captureRules) { settings.captureRules.mapNotNull(HeaderRule::decode) }
                    fun test(strict: Boolean) {
                        lastStrict = strict
                        val lines = sample.trim().lines()
                        sampleResult = PaymentParser.parse(lines.first(), lines.drop(1).joinToString("\n"), settings.baseCurrency ?: "SAR", strict = strict, rules = rules)
                    }
                    // re-run after a rule is learned or removed so the result reflects it
                    LaunchedEffect(rules) { if (sampleResult != null) test(lastStrict) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { test(strict = false) }) { Text("Test") }
                        OutlinedButton(onClick = { test(strict = true) }) { Text("Test as SMS") }
                    }
                    when (val r = sampleResult) {
                        is ParseResult.Payment -> Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val currency = currencies.currency(r.currency)
                                val income = r.direction == Direction.INCOME
                                MoneyText(
                                    "${if (income) "Credit · income" else "Debit · expense"} · ${MoneyFormat.format(r.amountMinor, currency, hidden = false)}",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    color = if (income) MaterialTheme.money.income else MaterialTheme.money.expense,
                                )
                                Text("${if (income) "From" else "At / to"}: ${r.merchant ?: "not found"}", style = MaterialTheme.typography.bodyMedium)
                                r.matchedType?.let { Text("Recognised as \"$it\"${if (r.learned) " (learned)" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text("Currency: ${r.currency} · confidence ${(r.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is ParseResult.Ignored -> Text("Not captured: ${r.reason.label}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        null -> Unit
                    }
                    if (sampleResult != null) {
                        // teach the header of this message: every future one like it gets that direction
                        val header = sample.trim().lines().first().trim()
                        Text("Wrong? Teach this message type (\"${header.take(40)}\"):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Direction.INCOME to "It's income", Direction.EXPENSE to "It's expense").forEach { (dir, label) ->
                                OutlinedButton(onClick = {
                                    val phrase = HeaderRule.normalizePhrase(header)
                                    if (phrase.length < 3) return@OutlinedButton
                                    val next = settings.captureRules.filterNot { HeaderRule.decode(it)?.phrase == phrase }.toSet() + HeaderRule(phrase, dir).encode()
                                    scope.launch {
                                        container.prefs.setCaptureRules(next)
                                        snackbar.showSnackbar("Learned: \"$phrase\" is ${if (dir == Direction.INCOME) "income" else "an expense"}")
                                    }
                                }) { Text(label) }
                            }
                        }
                    }
                }
            }
            val learned = settings.captureRules.mapNotNull(HeaderRule::decode).sortedBy { it.phrase }
            if (learned.isNotEmpty()) item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                GroupLabel("Learned message types")
                learned.forEach { rule ->
                    ListItem(
                        headlineContent = { Text(rule.phrase, maxLines = 2) },
                        supportingContent = { Text(if (rule.direction == Direction.INCOME) "Credit · income" else "Debit · expense") },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { container.prefs.setCaptureRules(settings.captureRules.filterNot { HeaderRule.decode(it) == rule }.toSet()) } }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Forget ${rule.phrase}")
                            }
                        },
                    )
                }
            }
        }
    }

    if (showImport) SmsImportDialog(onDismiss = { showImport = false }, onReview = { navController.navigate(Routes.CAPTURES) })

    if (pickWallet) {
        AlertDialog(
            onDismissRequest = { pickWallet = false },
            title = { Text("Add payments to") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val options = listOf<Pair<Long?, String>>(null to "The editor's default wallet") + wallets.filter { !it.archived }.map { it.id to "${it.name} (${it.currency})" }
                    options.forEach { (id, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch { container.prefs.setCaptureWallet(id) }
                                pickWallet = false
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.captureWalletId == id, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickWallet = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun AppRow(app: CaptureApps.App, checked: Boolean, onToggle: () -> Unit) {
    SettingRow(app.label, subtitle = app.packageName, trailing = { Checkbox(checked = checked, onCheckedChange = { onToggle() }) }, onClick = onToggle)
}
