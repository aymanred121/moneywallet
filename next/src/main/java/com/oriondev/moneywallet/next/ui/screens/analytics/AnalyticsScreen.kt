package com.oriondev.moneywallet.next.ui.screens.analytics

import androidx.compose.foundation.background
import com.oriondev.moneywallet.next.ui.components.MoneyText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.oriondev.moneywallet.next.core.CurrencySymbols
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.domain.analytics.total
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.analytics.CategoryShare
import com.oriondev.moneywallet.next.domain.analytics.Total
import com.oriondev.moneywallet.next.domain.analytics.from
import com.oriondev.moneywallet.next.domain.analytics.to
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.AmountTint
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.components.parseColor
import com.oriondev.moneywallet.next.ui.screens.home.RateHint
import com.oriondev.moneywallet.next.ui.screens.home.Staggered
import com.oriondev.moneywallet.next.ui.screens.home.TotalText
import com.oriondev.moneywallet.next.ui.theme.money
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(onNavigate: (String) -> Unit) {
    val vm = containerViewModel { AnalyticsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val netWorth by vm.netWorth.collectAsStateWithLifecycle()
    var showCustom by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Analytics") }) }) { padding ->
        val ctx = state.ctx
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "period") {
                Column {
                    PeriodSelector(
                        period = state.period,
                        onKind = { kind -> if (kind == PeriodKind.CUSTOM) showCustom = true else vm.setKind(kind) },
                        onPrevious = vm::previous, onNext = vm::next,
                    )
                    if (state.period is Period.Year) {
                        FilledTonalButton(onClick = { onNavigate(Routes.recap(Period.encode(state.period))) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Year in review")
                        }
                    }
                }
            }
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.selectedWalletId == null, onClick = { vm.selectWallet(null) }, label = { Text("All wallets") })
                        state.wallets.forEach { w ->
                            FilterChip(selected = state.selectedWalletId == w.id, onClick = { vm.selectWallet(w.id) }, label = { Text(w.name) }, leadingIcon = { SpecIcon(w.icon, size = 18.dp) })
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = state.direction == Direction.EXPENSE, onClick = { vm.setDirection(Direction.EXPENSE) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Expenses") }
                        SegmentedButton(selected = state.direction == Direction.INCOME, onClick = { vm.setDirection(Direction.INCOME) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Income") }
                    }
                }
            }
            if (ctx != null) {
                item(key = "donut") {
                    Staggered(0) {
                        DonutCard(state.shares, state.total, state.direction, ctx, animationKey = state.period) { id ->
                            if (id != null) onNavigate(Routes.categoryDetail(id, state.period.range.from, state.period.range.to))
                        }
                    }
                }
                item(key = "trend") { Staggered(1) { TrendCard(state.bars, state.period, state.direction, ctx) } }
                item(key = "compare") { Staggered(2) { CompareCard(state.spent, state.earned, state.previousSpent, state.previousEarned, ctx, state.partial) } }
                netWorth?.takeIf { nw -> nw.values.any { it != null && it != 0L } }?.let { nw -> item(key = "net-worth") { NetWorthCard(nw, ctx) } }
                subscriptions?.takeIf { it.charges.isNotEmpty() }?.let { subs -> item(key = "subscriptions") { SubscriptionsCard(subs, ctx) } }
                if (state.direction == Direction.EXPENSE) item(key = "merchants") { Staggered(3) { MerchantsCard(state.merchants, ctx) } }
                item(key = "averages") { Staggered(4) { AveragesCard(state, ctx) } }
            }
        }
    }

    if (showCustom) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showCustom = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    onClick = {
                        val s = pickerState.selectedStartDateMillis; val e = pickerState.selectedEndDateMillis
                        if (s != null && e != null) vm.setCustom(utcDate(s), utcDate(e))
                        showCustom = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(480.dp), title = { Text("Custom range", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

private fun utcDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelector(period: Period, onKind: (PeriodKind) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val kinds = PeriodKind.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            kinds.forEachIndexed { i, k ->
                SegmentedButton(
                    selected = period.kind() == k, onClick = { onKind(k) }, shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                    // no check mark: four labels must fit at large font scales, the fill shows the selection
                    icon = {},
                ) {
                    val style = LocalTextStyle.current
                    BasicText(
                        k.name.lowercase().replaceFirstChar { it.uppercase() }, maxLines = 1,
                        style = style.copy(color = LocalContentColor.current),
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = if (style.fontSize.isSpecified) style.fontSize else 14.sp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period") }
            Text(period.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period") }
        }
    }
}

@Composable
fun DonutCard(shares: List<CategoryShare>, total: Total, direction: Int, ctx: CurrencyContext, animationKey: Any? = Unit, onCategory: (Long?) -> Unit) {
    var selected by remember { mutableStateOf<Long?>(null) }
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text(if (direction == Direction.EXPENSE) "Where it went" else "Where it came from", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            if (shares.isEmpty()) {
                Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DonutChart(
                        slices = shares.map { DonutSlice(it.categoryId, it.fraction, parseColor(it.colorHex), it.name) },
                        modifier = Modifier.size(220.dp), strokeWidth = 26.dp, selectedKey = selected, animationKey = animationKey,
                        onSliceTap = { key -> selected = if (selected == key) null else key as Long? },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val sel = shares.firstOrNull { it.categoryId == selected }
                            Text(sel?.name ?: "Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            TotalText(sel?.total ?: total, ctx, style = MaterialTheme.typography.titleLarge, compact = true, animated = sel == null)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                shares.forEachIndexed { i, s -> LegendRow(s, ctx, selected == s.categoryId, onClick = { onCategory(s.categoryId) }) }
                if (shares.any { it.total.hasRateGap }) RateHint(Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun LegendRow(s: CategoryShare, ctx: CurrencyContext, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)
            .clickable(enabled = s.categoryId != null, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(parseColor(s.colorHex)))
        Spacer(Modifier.width(12.dp))
        SpecIcon(s.icon, size = 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${(s.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        TotalText(s.total, ctx, style = MaterialTheme.typography.bodyMedium, compact = true)
    }
}

@Composable
fun TrendCard(bars: List<TrendBar>, period: Period, direction: Int, ctx: CurrencyContext) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text(if (period is Period.Year || (period is Period.Custom && bars.size <= 12 && bars.isNotEmpty() && bars.first().label.length == 1)) "Monthly trend" else "Daily trend", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val values = bars.map { BarValue(it.label, it.total.base ?: 0L, it.isCurrent) }
            if (values.all { it.value == 0L }) {
                Text("No data for this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                BarChart(
                    bars = values, modifier = Modifier.fillMaxWidth().height(180.dp), animationKey = period,
                    formatValue = { com.oriondev.moneywallet.next.core.CurrencySymbols.plainText(MoneyFormat.format(it, ctx.base, compact = true)) },
                    barColor = if (direction == Direction.EXPENSE) MaterialTheme.money.expense else MaterialTheme.money.income,
                    highlightColor = MaterialTheme.colorScheme.primary,
                )
            }
            if (bars.any { it.total.hasRateGap }) RateHint(Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun CompareCard(spent: Total, earned: Total, previousSpent: Total, previousEarned: Total, ctx: CurrencyContext, partial: Boolean = false) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text(if (partial) "So far vs the same days before" else "This vs previous period", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            ComparePair("Expenses", spent, previousSpent, ctx, MaterialTheme.money.expense, AmountTint.EXPENSE)
            Spacer(Modifier.height(16.dp))
            ComparePair("Income", earned, previousEarned, ctx, MaterialTheme.money.income, AmountTint.INCOME)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant)); Spacer(Modifier.width(6.dp))
                Text("This period", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))); Spacer(Modifier.width(6.dp))
                Text("Previous", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ComparePair(label: String, current: Total, previous: Total, ctx: CurrencyContext, color: androidx.compose.ui.graphics.Color, tint: AmountTint) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TotalText(current, ctx, style = MaterialTheme.typography.titleMedium, tint = tint, compact = true)
        }
        Spacer(Modifier.height(8.dp))
        if (current.base != null && previous.base != null) {
            CompareBars(current.base, previous.base, Modifier.fillMaxWidth().height(28.dp), color)
            val change = com.oriondev.moneywallet.next.domain.analytics.Aggregates.percentChange(current.base, previous.base)
            MoneyText(
                when {
                    change == null -> "Previous: ${MoneyFormat.format(previous.base, ctx.base, compact = true)}"
                    abs(change) < 3 -> "About the same as before (${MoneyFormat.format(previous.base, ctx.base, compact = true)})"
                    change < 0 -> "${abs(change)}% less than before (${MoneyFormat.format(previous.base, ctx.base, compact = true)})"
                    else -> "$change% more than before (${MoneyFormat.format(previous.base, ctx.base, compact = true)})"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
        } else RateHint()
    }
}

@Composable
fun MerchantsCard(merchants: List<Merchant>, ctx: CurrencyContext) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text("Top merchants", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (merchants.isEmpty()) Text("Add descriptions to your expenses to see where you shop most.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            merchants.forEachIndexed { i, m ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${m.count} ${if (m.count == 1) "purchase" else "purchases"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TotalText(m.total, ctx, style = MaterialTheme.typography.bodyMedium, compact = true)
                }
            }
        }
    }
}

@Composable
fun AveragesCard(state: AnalyticsViewModel.UiState, ctx: CurrencyContext) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(16.dp)) {
            StatCell("Per day", state.perDay, ctx, Modifier.weight(1f))
            StatCell("Per transaction", state.perTransaction, ctx, Modifier.weight(1f))
            Column(Modifier.weight(1f)) {
                Text("Transactions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.transactionCount}", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: Long?, ctx: CurrencyContext, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value != null) AmountText(value, ctx.base, style = MaterialTheme.typography.titleLarge, compact = true) else Text("—", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun NetWorthCard(nw: NetWorthUi, ctx: CurrencyContext) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text("Net worth", style = MaterialTheme.typography.titleMedium)
            val last = nw.values.lastOrNull()
            val first = nw.values.firstOrNull { it != null }
            if (last != null) {
                AmountText(last, ctx.base, style = MaterialTheme.typography.headlineSmall)
                if (first != null && nw.values.size > 1) {
                    MoneyText(
                        "${MoneyFormat.format(last - first, ctx.base, signed = true, compact = true)} over ${nw.values.size} months",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (nw.values.any { it == null }) RateHint()
            else AreaChart(
                points = nw.labels.zip(nw.values).map { (l, v) -> BarValue(l, v ?: 0L) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                formatValue = { CurrencySymbols.plainText(MoneyFormat.format(it, ctx.base, compact = true)) },
                description = "Net worth by month: " + nw.labels.zip(nw.values).joinToString { (l, v) -> "$l ${CurrencySymbols.plainText(MoneyFormat.format(v ?: 0L, ctx.base, compact = true))}" },
            )
            Text(
                if (nw.multiCurrency) "Balance of counted wallets at the end of each payday month, converted at today's rates."
                else "Balance of counted wallets at the end of each payday month.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun SubscriptionsCard(subs: Subscriptions, ctx: CurrencyContext) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text("Subscriptions & bills", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                TotalText(subs.monthly, ctx, style = MaterialTheme.typography.headlineSmall)
                Text(" a month", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            subs.charges.take(6).forEach { c ->
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c.cadence.label} · next ${Dates.dayLabel(Dates.startOfDay(c.nextDate))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TotalText(ctx.total(c.typicalAmount, c.currency), ctx, style = MaterialTheme.typography.bodyMedium, compact = true)
                }
            }
            if (subs.charges.size > 6) Text("and ${subs.charges.size - 6} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Text("Detected from repeating expenses with a steady amount.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
