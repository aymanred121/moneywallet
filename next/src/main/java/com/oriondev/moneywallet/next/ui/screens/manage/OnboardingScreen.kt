package com.oriondev.moneywallet.next.ui.screens.manage

import com.oriondev.moneywallet.next.ui.theme.Motion
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.WalletEntity
import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.screens.manage.pickers.CurrencyList
import com.oriondev.moneywallet.next.ui.screens.manage.pickers.CurrencyPickerSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val PAGES = 3

@Composable
fun OnboardingScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { PAGES }
    val snackbar = remember { SnackbarHostState() }
    val settings by remember(container) { container.prefs.settings }.collectAsStateWithLifecycle(initialValue = Settings())
    val ctx by rememberCurrencyContext()

    var walletName by remember { mutableStateOf("Cash") }
    var walletCurrency by remember { mutableStateOf<String?>(null) }
    var balance by remember { mutableStateOf("") }
    var showCurrency by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val baseIso = settings.baseCurrency ?: ctx.baseIso
    val currency = ctx.currency(walletCurrency ?: baseIso)

    fun finish() {
        scope.launch {
            container.prefs.setOnboardingDone(true) // WalletNavHost keeps its start destination, so this does not re-root the graph
            navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val report = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { container.backup.import(it, replace = true) } ?: error("Could not open the file")
                }
                snackbar.showSnackbar("Restored ${report.wallets} wallets and ${report.transactions} transactions")
                finish()
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Restore failed")
            } finally { busy = false }
        }
    }

    fun createWalletAndFinish() {
        scope.launch {
            busy = true
            val iso = walletCurrency ?: baseIso
            if (settings.baseCurrency == null) container.prefs.setBaseCurrency(baseIso)
            val minor = MoneyFormat.parse(balance.ifBlank { "0" }, ctx.currency(iso)) ?: 0L
            container.repo.saveWallet(
                WalletEntity(uuid = UUID.randomUUID().toString(), name = walletName.trim().ifEmpty { "Cash" }, icon = IconSpec.letterFor(walletName.ifBlank { "Cash" }, IconSpec.palette[1]).encode(), currency = iso, startMoney = minor),
            )
            busy = false
            finish()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = true) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> CurrencyPage(baseIso) { iso -> scope.launch { container.prefs.setBaseCurrency(iso) } }
                    else -> FirstWalletPage(
                        name = walletName, onName = { walletName = it }, currencyLabel = "${currency.name} (${currency.iso})", onPickCurrency = { showCurrency = true },
                        balance = balance, onBalance = { balance = it }, symbol = currency.symbol, busy = busy,
                        onRestore = { restoreLauncher.launch(arrayOf("application/*", "*/*")) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                PageIndicator(count = PAGES, current = pager.currentPage)
                AnimatedContent(targetState = pager.currentPage == PAGES - 1, transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) }, label = "cta") { last ->
                    if (!last) {
                        Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                            Text("Next"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    } else {
                        Button(onClick = { createWalletAndFinish() }, enabled = !busy && walletName.isNotBlank() && (balance.isBlank() || MoneyFormat.parse(balance, currency) != null)) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Get started")
                        }
                    }
                }
            }
        }
    }
    if (showCurrency) CurrencyPickerSheet(selected = walletCurrency ?: baseIso, onDismiss = { showCurrency = false }) { walletCurrency = it }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, Motion.spatialFast(), label = "dot")
            val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, Motion.effects(), label = "dotColor")
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun Illustration(content: @Composable () -> Unit) {
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.size(200.dp), shape = RoundedCornerShape(64.dp), color = MaterialTheme.colorScheme.primaryContainer) {}
        Surface(Modifier.size(120.dp).offset(x = 60.dp, y = (-60).dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)) {}
        content()
    }
}

@Composable
private fun WelcomePage() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Illustration {
            Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(56.dp).offset(y = 40.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Welcome to MoneyWallet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Track spending across every wallet and currency, set budgets and see where your money goes. Everything stays on your device.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CurrencyPage(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Pick your global currency", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text("Totals across wallets are converted to this currency. You can change it later in Settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        CurrencyList(selected = selected, onSelect = { onSelect(it.iso) }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun FirstWalletPage(
    name: String, onName: (String) -> Unit, currencyLabel: String, onPickCurrency: () -> Unit,
    balance: String, onBalance: (String) -> Unit, symbol: String, busy: Boolean, onRestore: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Illustration { SpecIcon(IconSpec.letterFor(name.ifBlank { "Cash" }, IconSpec.palette[1]), size = 96.dp) }
        Text("Create your first wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        OutlinedTextField(value = name, onValueChange = onName, label = { Text("Wallet name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SettingRow(title = "Currency", subtitle = currencyLabel, onClick = onPickCurrency)
        OutlinedTextField(value = balance, onValueChange = onBalance, label = { Text("Starting balance") }, prefix = { Text(symbol) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("Already using MoneyWallet?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRestore, enabled = !busy) {
            Icon(Icons.Outlined.Restore, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Restore a .mwbx backup")
        }
        Spacer(Modifier.height(48.dp))
    }
}
