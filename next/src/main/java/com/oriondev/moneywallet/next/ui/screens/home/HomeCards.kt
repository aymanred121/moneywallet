package com.oriondev.moneywallet.next.ui.screens.home

import com.oriondev.moneywallet.next.ui.theme.money
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.TransactionRow
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.analytics.Total
import com.oriondev.moneywallet.next.domain.pacing.SafeSource
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.AmountTint
import com.oriondev.moneywallet.next.ui.components.MoneyText
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.theme.MoneyDisplay
import com.oriondev.moneywallet.next.ui.theme.Motion
import kotlin.math.abs

/** Balance hero. Long-press anywhere on it (or the eye button) toggles hide balances. */
@Composable
fun HeroBalanceCard(
    balance: Total,
    ctx: CurrencyContext,
    wallets: List<WalletWithBalance>,
    selectedWalletId: Long?,
    onSelectWallet: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    onToggleHidden: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val toggle by rememberUpdatedState(onToggleHidden)
    val toggleLabel = if (hidden) "Show balances" else "Hide balances"
    Card(
        modifier.fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); toggle() }) }
            .semantics { customActions = listOf(CustomAccessibilityAction(toggleLabel) { toggle(); true }) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selectedWalletId == null) "Total balance" else wallets.firstOrNull { it.id == selectedWalletId }?.name ?: "Balance",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleHidden) { // default 48dp touch target
                    Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = toggleLabel, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.padding(end = 12.dp)) {
                if (hidden) Text("••••••", style = MoneyDisplay) else TotalText(balance, ctx, style = MoneyDisplay, animated = true)
                if (!hidden) CurrencyBreakdown(balance.bag, ctx, Modifier.padding(top = 8.dp))
            }
            if (wallets.size > 1) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedWalletId == null, onClick = { onSelectWallet(null) }, label = { Text("All") })
                    wallets.forEach { w ->
                        FilterChip(
                            selected = selectedWalletId == w.id,
                            onClick = { onSelectWallet(w.id) },
                            label = { Text(w.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { SpecIcon(w.icon, size = 18.dp) },
                        )
                    }
                }
            }
        }
    }
}

/** Payday cycle: range and days to payday, spent so far vs the same days last cycle, and pacing. */
@Composable
fun PaydayCard(cycle: CycleUi, ctx: CurrencyContext, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text("${cycle.rangeLabel} · ${cycle.daysLeftLabel}", style = MaterialTheme.typography.labelLarge, color = muted)
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Spent", style = MaterialTheme.typography.labelMedium, color = muted)
                    TotalText(cycle.spent, ctx, style = MaterialTheme.typography.headlineSmall, tint = AmountTint.EXPENSE, animated = true, compact = true)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Earned", style = MaterialTheme.typography.labelMedium, color = muted)
                    TotalText(cycle.earned, ctx, style = MaterialTheme.typography.headlineSmall, tint = AmountTint.INCOME, animated = true, compact = true)
                }
            }
            SpentVsEarnedBar(cycle.spent.base, cycle.earned.base, Modifier.padding(top = 10.dp))
            Text(comparisonText(cycle), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 6.dp))
            val p = cycle.pacing ?: return@Column
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val days = if (p.daysLeft == 1) "1 day left" else "${p.daysLeft} days left"
            when {
                p.over -> MoneyText(
                    when {
                        p.source == SafeSource.INCOME -> "Spent ${MoneyFormat.format(p.overBy, ctx.base)} more than you earned this cycle"
                        p.overBy > 0 -> "Over budget by ${MoneyFormat.format(p.overBy, ctx.base)} · $days"
                        else -> "Budget used up · $days"
                    },
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error,
                )
                p.safePerDay != null -> MoneyText("${MoneyFormat.format(p.safePerDay, ctx.base)}/day safe to spend · $days", style = MaterialTheme.typography.titleSmall)
            }
            val vs = p.projectedChange?.let { c -> " (${if (c > 0) "+" else if (c < 0) "−" else "±"}${abs(c)}% vs last cycle)" } ?: ""
            MoneyText(
                "On track to spend ${MoneyFormat.format(p.projectedSpend, ctx.base, compact = true)} by ${cycle.lastDayLabel}$vs",
                style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Expenses against income for the cycle: the track is what came in, the fill what went out.
 * It turns red and stays full once spending passes income.
 */
@Composable
private fun SpentVsEarnedBar(spent: Long?, earned: Long?, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val s = spent ?: 0L
    val e = earned ?: 0L
    val ratio = when {
        spent == null || earned == null -> 0f
        e <= 0L -> if (s > 0) 1f else 0f
        else -> (s.toFloat() / e).coerceIn(0f, 1f)
    }
    // with no income yet the bar reads "all spending, nothing in", but that is not an alarm
    val over = earned != null && spent != null && e > 0 && s > e
    val fill by animateFloatAsState(ratio, Motion.spatial(), label = "spentVsEarned")
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { fill },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).semantics {
                contentDescription = if (e > 0) "Spent ${(s * 100 / e)} percent of income" else "No income yet this cycle"
            },
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.money.expense.copy(alpha = if (e > 0) 1f else 0.5f),
            trackColor = if (e > 0) MaterialTheme.money.income.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            when {
                spent == null || earned == null -> "Needs an exchange rate"
                e <= 0L -> if (s > 0) "No income yet this cycle" else "Nothing in or out yet"
                over -> "Spent more than you earned"
                else -> "Spent ${(s * 100 / e).coerceAtLeast(if (s > 0) 1 else 0)}% of what you earned · ${100 - (s * 100 / e)}% left"
            },
            style = MaterialTheme.typography.labelSmall, color = if (over) MaterialTheme.colorScheme.error else muted, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun comparisonText(cycle: CycleUi): String {
    val change = cycle.change
    val cur = cycle.spent.base ?: 0L
    return when {
        cycle.spent.hasRateGap || cycle.previousSpent.hasRateGap -> "Comparison needs an exchange rate"
        change == null -> if (cur > 0) "Nothing spent by this point last cycle" else "Nothing spent yet"
        abs(change) < 3 -> "About the same as this point last cycle"
        change < 0 -> "${abs(change)}% less than this point last cycle"
        else -> "$change% more than this point last cycle"
    }
}

@Composable
fun BudgetsCard(budgets: List<BudgetUi>, ctx: CurrencyContext, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Budgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            budgets.forEach { b -> BudgetRow(b, ctx) }
        }
    }
}

@Composable
fun BudgetRow(b: BudgetUi, ctx: CurrencyContext, modifier: Modifier = Modifier) {
    val currency = ctx.currency(b.budget.currency)
    val animated by animateFloatAsState(b.ratio.coerceIn(0f, 1f), Motion.spatial(), label = "budget")
    val color = if (b.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier.padding(top = 12.dp)) {
        Row {
            Text(b.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            MoneyText(
                "${MoneyFormat.format(b.spent, currency, compact = true)} / ${MoneyFormat.format(b.budget.money, currency, compact = true)}",
                style = MaterialTheme.typography.labelMedium, color = if (b.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { animated }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = color, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        if (b.over) Text("${(b.ratio * 100).toInt()}% used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun RecentCard(rows: List<TransactionRow>, ctx: CurrencyContext, onRow: (Long) -> Unit, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
            if (rows.isEmpty()) {
                Text("No transactions yet. Tap + to add your first one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            rows.forEach { row -> TransactionRowItem(row, ctx, onClick = { onRow(row.id) }) }
        }
    }
}

@Composable
fun TransactionRowItem(row: TransactionRow, ctx: CurrencyContext, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpecIcon(row.categoryIcon, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.description.ifBlank { row.categoryName ?: "Transaction" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(row.categoryName?.takeIf { row.description.isNotBlank() }, Dates.relativeDayLabel(row.date), row.walletName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        val signedMinor = if (row.direction == Direction.INCOME) row.money else -row.money
        AmountText(signedMinor, ctx.currency(row.walletCurrency), style = MaterialTheme.typography.titleMedium, tint = AmountTint.AUTO, signed = true)
    }
}
