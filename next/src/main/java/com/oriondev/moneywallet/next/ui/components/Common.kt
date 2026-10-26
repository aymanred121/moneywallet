package com.oriondev.moneywallet.next.ui.components

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.ui.theme.Motion

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Fades and settles in the first time it enters the composition. */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = shown, enter = fadeIn(Motion.effects()) + scaleIn(Motion.spatial(), initialScale = 0.94f)) {
        Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/**
 * The app's one app-bar pattern: a small [TopAppBar] that slides away while scrolling down and
 * comes back on any scroll up. Pair it with [appBarScrollBehavior] and put the behaviour's
 * `nestedScrollConnection` on the screen's Scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior?,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarScrollBehavior(): TopAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

/**
 * Whether a FAB over this list should show: always near the top, otherwise only after the user
 * last scrolled up. Reading down a list, the button gets out of the way of amounts it would cover.
 */
fun androidx.compose.foundation.lazy.LazyListState.fabVisibility(): kotlinx.coroutines.flow.Flow<Boolean> =
    androidx.compose.runtime.snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
        .runningFold(Triple(0, 0, true)) { (prevIndex, prevOffset, visible), (index, offset) ->
            val show = when {
                index == 0 && offset < 48 -> true
                index == prevIndex && kotlin.math.abs(offset - prevOffset) < 12 -> visible // ignore jitter
                else -> index < prevIndex || (index == prevIndex && offset < prevOffset)
            }
            Triple(index, offset, show)
        }
        .map { it.third }
        .distinctUntilChanged()
