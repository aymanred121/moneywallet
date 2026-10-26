package com.oriondev.moneywallet.next.ui.screens.manage

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.ui.components.AppTopBar
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.components.appBarScrollBehavior

/** Common scaffold for every management screen: [AppTopBar] with optional back arrow, FAB and snackbars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = appBarScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { AppTopBar(title, scroll, onBack, actions) },
        floatingActionButton = fab,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
    ) { padding ->
        // shrink the viewport to end at the keyboard: scrollable content then keeps the focused
        // field in view (e.g. the capture sample box at the end of a list). Not reduced by the
        // nav-bar padding: screens put that at the end of their scrolling content, not at the edge.
        Box(Modifier.fillMaxSize().imePadding()) { content(padding) }
    }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    )
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null, icon: ImageVector? = null, enabled: Boolean = true) {
    SettingRow(
        title = title, subtitle = subtitle, icon = icon,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
    )
}

@Composable
fun GroupLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Live currency context (currencies + rates + base) shared by the management screens. */
@Composable
fun rememberCurrencyContext(): State<CurrencyContext> {
    val container = LocalAppContainer.current
    val flow = remember(container) { container.repo.currencyContext }
    return flow.collectAsStateWithLifecycle(initialValue = CurrencyContext(emptyMap(), emptyMap(), "USD"))
}

fun CurrencyContext.currencyOrFallback(iso: String?): Currency = currency(iso ?: baseIso)

