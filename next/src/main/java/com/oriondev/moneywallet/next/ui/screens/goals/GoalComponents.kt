package com.oriondev.moneywallet.next.ui.screens.goals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.data.repo.GoalRepository
import com.oriondev.moneywallet.next.data.repo.GoalUi
import com.oriondev.moneywallet.next.data.repo.forHome
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.components.LocalHideAmounts
import com.oriondev.moneywallet.next.ui.components.MoneyText
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.screens.manage.rememberCurrencyContext
import com.oriondev.moneywallet.next.ui.theme.Motion

/** Formats through [MoneyFormat] and honours the hide-balances toggle. */
@Composable
internal fun money(minor: Long, currency: Currency): String = MoneyFormat.format(minor, currency, hidden = LocalHideAmounts.current || MoneyFormat.masked)

/** Progress ring around the goal icon; turns into a check (with a small overshoot) once reached. */
@Composable
fun GoalRing(fraction: Float, complete: Boolean, icon: String, size: Dp = 56.dp) {
    val animated by animateFloatAsState(fraction, Motion.data(), label = "goalProgress")
    val color by animateColorAsState(if (complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, Motion.effects(), label = "goalColor")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animated }, modifier = Modifier.fillMaxSize(), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 4.dp, strokeCap = StrokeCap.Round,
        )
        AnimatedContent(complete, transitionSpec = { (fadeIn(Motion.effects()) + scaleIn(Motion.expressive(), initialScale = 0.6f)) togetherWith fadeOut(Motion.effects()) }, label = "goalIcon") { done ->
            if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.45f))
            else SpecIcon(icon, size - 18.dp)
        }
    }
}

/** One-line status under a goal: the monthly pace, "reached", "date passed" or what is left. */
@Composable
fun goalStatus(ui: GoalUi, ctx: CurrencyContext): String {
    val g = ui.goal
    val cur = ctx.currency(g.currency)
    val p = ui.progress ?: return "No exchange rate to ${g.currency} yet"
    return when {
        p.complete -> "Goal reached"
        p.overdue -> "Target date passed · ${money(p.remaining, cur)} to go"
        p.perMonth != null && g.deadline != null -> "${money(p.perMonth, cur)} per month to reach it by ${Dates.dayLabelWithYear(g.deadline)}"
        else -> "${money(p.remaining, cur)} to go"
    } + (ui.walletName?.let { " · $it" } ?: "")
}

@Composable
fun GoalCard(ui: GoalUi, ctx: CurrencyContext, onClick: () -> Unit, modifier: Modifier = Modifier, onAdd: (() -> Unit)? = null) {
    val g = ui.goal
    val cur = ctx.currency(g.currency)
    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GoalRing(ui.progress?.fraction ?: 0f, ui.complete, g.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MoneyText("${ui.saved?.let { money(it, cur) } ?: "—"} of ${money(g.targetMoney, cur)}", style = MaterialTheme.typography.bodyMedium)
                MoneyText(
                    goalStatus(ui, ctx), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (ui.complete) MaterialTheme.colorScheme.tertiary else if (ui.progress?.overdue == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onAdd != null) FilledTonalIconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add money to ${g.name}") }
        }
    }
}

/** Small amount entry used by Add money / Withdraw. */
@Composable
fun GoalAmountDialog(title: String, confirm: String, currency: Currency, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    val minor = MoneyFormat.parse(text, currency)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text("Amount") }, singleLine = true, prefix = { MoneyText(currency.symbol + " ") },
                isError = text.isNotBlank() && minor == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(enabled = minor != null && minor > 0, onClick = { onConfirm(minor!!); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Home card with the top one or two active goals. Renders nothing until there is a goal, so it can
 * sit on Home unconditionally.
 */
@Composable
fun GoalsHomeCard(onOpen: () -> Unit, onOpenGoal: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val goals by remember(container) { GoalRepository(container.db, container.repo).goals }.collectAsStateWithLifecycle(initialValue = null)
    val ctx by rememberCurrencyContext()
    val top = goals?.forHome().orEmpty()
    AnimatedVisibility(top.isNotEmpty(), modifier = modifier, enter = fadeIn(Motion.effects()) + expandVertically(Motion.spatial()), exit = fadeOut(Motion.effects()) + shrinkVertically(Motion.spatial())) {
        ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Savings goals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("See all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                top.forEach { ui -> GoalMiniRow(ui, ctx) { onOpenGoal(ui.goal.id) } }
            }
        }
    }
}

@Composable
private fun GoalMiniRow(ui: GoalUi, ctx: CurrencyContext, onClick: () -> Unit) {
    val g = ui.goal
    val cur = ctx.currency(g.currency)
    val animated by animateFloatAsState(ui.progress?.fraction ?: 0f, Motion.data(), label = "goalMini")
    val percent = ((ui.progress?.fraction ?: 0f) * 100).toInt()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${g.name}, $percent percent" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpecIcon(g.icon, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                MoneyText(
                    if (ui.complete) "Reached" else "${ui.saved?.let { money(it, cur) } ?: "—"} / ${money(g.targetMoney, cur)}",
                    style = MaterialTheme.typography.labelMedium, color = if (ui.complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { animated }, modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round,
                color = if (ui.complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
