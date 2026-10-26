@file:OptIn(ExperimentalMaterial3Api::class)

package com.oriondev.moneywallet.next.ui.screens.transactions

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.TransactionRow
import com.oriondev.moneywallet.next.data.db.TransactionType
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.MoneyText
import com.oriondev.moneywallet.next.ui.components.AmountTint
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.theme.Motion

/** Amount that may span several currencies: single value, base-converted (≈) or one line per currency. */
@Composable
fun MultiCurrencyAmount(
    values: Map<String, Long>,
    ctx: CurrencyContext,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    tint: AmountTint = AmountTint.AUTO,
    signed: Boolean = true,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
) {
    val nonZero = values.filterValues { it != 0L }
    when {
        nonZero.isEmpty() -> AmountText(0, ctx.base, style = style, tint = AmountTint.NEUTRAL)
        nonZero.size == 1 -> {
            val (iso, minor) = nonZero.entries.first()
            AmountText(minor, ctx.currency(iso), style = style, tint = tint, signed = signed)
        }
        else -> {
            val base = ctx.toBase(MoneyBag(nonZero))
            if (base != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("≈ ", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(base, ctx.base, style = style, tint = tint, signed = signed)
                }
            } else {
                Column(horizontalAlignment = horizontalAlignment) {
                    nonZero.forEach { (iso, minor) -> AmountText(minor, ctx.currency(iso), style = MaterialTheme.typography.bodyMedium, tint = tint, signed = signed) }
                }
            }
        }
    }
}

@Composable
fun DayHeader(section: DaySection, ctx: CurrencyContext, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(section.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        MultiCurrencyAmount(section.net, ctx, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TransactionRowItem(row: TransactionRow, ctx: CurrencyContext, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isTransfer = row.type == TransactionType.TRANSFER
    val title = when {
        isTransfer -> if (row.direction == Direction.INCOME) "Transfer in" else "Transfer out"
        !row.categoryName.isNullOrBlank() -> row.categoryName
        row.description.isNotBlank() -> row.description
        else -> "Uncategorised"
    }
    val subtitle = listOfNotNull(
        row.description.takeIf { it.isNotBlank() && it != title },
        row.walletName,
        row.note?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    val walletCurrency = ctx.currency(row.walletCurrency)
    val tint = if (row.direction == Direction.INCOME) AmountTint.INCOME else AmountTint.EXPENSE
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = {
            if (isTransfer) Box(Modifier.padding(8.dp)) { Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) }
            else SpecIcon(row.categoryIcon, size = 40.dp)
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (subtitle.isNotBlank()) ({ Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    if (row.direction == Direction.INCOME) row.money else -row.money, walletCurrency,
                    style = MaterialTheme.typography.titleMedium, tint = tint, signed = true,
                )
                if (row.currency != null && row.currency != row.walletCurrency && row.originalMoney != null) {
                    MoneyText(
                        MoneyFormat.format(row.originalMoney, ctx.currency(row.currency)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!row.confirmed) Text("Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        },
    )
}

/** Swipe end→start (or the TalkBack "Delete" action) to delete; the caller hides the row and offers Undo. */
@Composable
fun DismissibleTransactionRow(row: TransactionRow, ctx: CurrencyContext, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value -> if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false },
        positionalThreshold = { it * 0.4f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics { customActions = listOf(CustomAccessibilityAction("Delete") { onDelete(); true }) },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val active = state.targetValue == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier.fillMaxSize().background(if (active) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Default.Delete, contentDescription = null, tint = if (active) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    ) { TransactionRowItem(row, ctx, onClick) }
}

/** Income / expenses / net for the visible month. */
@Composable
fun MonthSummaryCard(ui: MonthUi, ctx: CurrencyContext, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize(Motion.spatial()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryColumn("Income", ui.income, ctx, AmountTint.INCOME, Alignment.Start)
            SummaryColumn("Expenses", ui.expense.mapValues { -it.value }, ctx, AmountTint.EXPENSE, Alignment.CenterHorizontally)
            SummaryColumn("Net", ui.net, ctx, AmountTint.AUTO, Alignment.End)
        }
    }
}

@Composable
private fun SummaryColumn(label: String, values: Map<String, Long>, ctx: CurrencyContext, tint: AmountTint, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MultiCurrencyAmount(values, ctx, style = MaterialTheme.typography.titleMedium, tint = tint, signed = tint == AmountTint.AUTO, horizontalAlignment = align)
    }
}
