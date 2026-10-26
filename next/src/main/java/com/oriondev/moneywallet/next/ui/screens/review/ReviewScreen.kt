package com.oriondev.moneywallet.next.ui.screens.review

import android.content.Intent
import com.oriondev.moneywallet.next.ui.components.MoneyText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.insights.Insight
import com.oriondev.moneywallet.next.domain.insights.InsightKind
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.AmountTint
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.components.parseColor
import com.oriondev.moneywallet.next.ui.screens.home.BudgetRow
import com.oriondev.moneywallet.next.ui.screens.home.Staggered
import com.oriondev.moneywallet.next.ui.screens.home.TotalText
import com.oriondev.moneywallet.next.ui.theme.MoneyDisplay
import com.oriondev.moneywallet.next.ui.theme.Motion
import com.oriondev.moneywallet.next.domain.analytics.total
import com.oriondev.moneywallet.next.ui.theme.money
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(initialPeriod: Period, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val vm = containerViewModel(key = "review-${Period.encode(initialPeriod)}") { ReviewViewModel(it, initialPeriod) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { onNavigate(Routes.recap(Period.encode(state.period))) }) { Icon(Icons.Outlined.AutoAwesome, contentDescription = "Recap") }
                    IconButton(onClick = {
                        val text = com.oriondev.moneywallet.next.core.CurrencySymbols.plainText(vm.shareText(state))
                        if (text.isNotBlank()) {
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, "My ${state.noun} in review") }
                            context.startActivity(Intent.createChooser(send, "Share review"))
                        }
                    }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "switch") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = state.period is Period.Week, onClick = { vm.showWeek() }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Week") }
                        SegmentedButton(selected = state.period !is Period.Week, onClick = { vm.showMonth() }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Month") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = vm::previous) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous") }
                        Text(state.period.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        IconButton(onClick = vm::next) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next") }
                    }
                }
            }
            val ctx = state.ctx
            if (ctx != null) {
                item(key = "headline") { Staggered(0) { HeadlineCard(state, ctx) } }
                item(key = "categories") { Staggered(1) { TopCategoriesCard(state, ctx, onNavigate) } }
                item(key = "highlights") { Staggered(2) { HighlightsCard(state, ctx) } }
                if (state.comingUp.isNotEmpty()) item(key = "coming-up") { ComingUpCard(state, ctx) }
                item(key = "days") { Staggered(3) { DaysCard(state, ctx) } }
                if (state.budgets.isNotEmpty()) item(key = "budgets") { Staggered(4) { BudgetsStatusCard(state, ctx) } }
                item(key = "insights-title") { Staggered(5) { Text("Insights", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) } }
                if (state.insights.isEmpty()) item { Text("Add a few transactions and the insights will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(state.insights.size, key = { "insight-$it" }) { i -> Staggered(6 + i) { InsightCard(state.insights[i]) } }
            }
        }
    }
}

@Composable
private fun HeadlineCard(state: ReviewViewModel.UiState, ctx: CurrencyContext) {
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("You spent this ${state.noun}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            TotalText(state.spent, ctx, style = MoneyDisplay, animated = true)
            Spacer(Modifier.height(12.dp))
            val change = state.change
            if (change != null) {
                val rotation by animateFloatAsState(if (change <= 0) 180f else 0f, Motion.spatial(), label = "arrow")
                val pct by animateFloatAsState(abs(change).toFloat(), Motion.data(), label = "pct")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp).rotate(rotation))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (abs(change) < 3) "About the same as ${state.previousNoun}" else "${pct.toInt()}% ${if (change < 0) "less" else "more"} than ${state.previousNoun}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else if (state.spent.hasRateGap || state.previousSpent.hasRateGap) {
                Text("Comparison needs an exchange rate", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Nothing to compare with yet", style = MaterialTheme.typography.bodyMedium)
            }
            if (!state.earned.isEmpty) {
                Spacer(Modifier.height(12.dp))
                Row {
                    Text("Earned ", style = MaterialTheme.typography.bodyMedium)
                    TotalText(state.earned, ctx, style = MaterialTheme.typography.bodyMedium, tint = AmountTint.NEUTRAL)
                }
            }
        }
    }
}

@Composable
private fun TopCategoriesCard(state: ReviewViewModel.UiState, ctx: CurrencyContext, onNavigate: (String) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text("Top categories", style = MaterialTheme.typography.titleMedium)
            if (state.topCategories.isEmpty()) Text("No expenses in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            state.topCategories.forEach { s ->
                val fraction by animateFloatAsState(s.fraction, Motion.spatial(), label = "cat")
                Column(Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpecIcon(s.icon, size = 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(s.fraction * 100).toInt()}%  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TotalText(s.total, ctx, style = MaterialTheme.typography.bodyMedium, compact = true)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = parseColor(s.colorHex), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            if (state.topCategories.isNotEmpty()) {
                androidx.compose.material3.TextButton(onClick = { onNavigate(Routes.ANALYTICS) }, modifier = Modifier.padding(top = 4.dp)) { Text("Full breakdown") }
            }
        }
    }
}

@Composable
private fun HighlightsCard(state: ReviewViewModel.UiState, ctx: CurrencyContext) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text("Biggest expense", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val biggest = state.biggest
            if (biggest == null) {
                Text("No single expense stands out yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val (row, total) = biggest
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpecIcon(row.categoryIcon, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.description.ifBlank { row.categoryName ?: "Expense" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(row.categoryName, Dates.dayLabel(row.date)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TotalText(total, ctx, style = MaterialTheme.typography.titleMedium, tint = AmountTint.EXPENSE)
                }
            }
        }
    }
}

/** Subscriptions and bills expected in the next days, detected from history. */
@Composable
private fun ComingUpCard(state: ReviewViewModel.UiState, ctx: CurrencyContext) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text("Coming up", style = MaterialTheme.typography.titleMedium)
            state.comingUp.forEach { c ->
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EventRepeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c.cadence.label} · ${Dates.relativeDayLabel(Dates.startOfDay(c.nextDate))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TotalText(ctx.total(c.typicalAmount, c.currency), ctx, style = MaterialTheme.typography.bodyMedium, compact = true)
                }
            }
        }
    }
}

@Composable
private fun DaysCard(state: ReviewViewModel.UiState, ctx: CurrencyContext) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text("Days", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row {
                Stat("${state.daysWithSpending}/${state.daysElapsed}", "days with spending", Modifier.weight(1f))
                Stat("${state.longestStreak}", "longest no-spend streak", Modifier.weight(1f))
                Stat("${state.transactionCount}", "expenses", Modifier.weight(1f))
            }
            if (state.topDays.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Most expensive days", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val max = state.topDays.maxOf { it.total.base ?: 0L }.coerceAtLeast(1L)
                state.topDays.forEach { d ->
                    val fraction by animateFloatAsState(((d.total.base ?: 0L).toFloat() / max), Motion.spatial(), label = "day")
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${d.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.day.dayOfMonth}",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp),
                        )
                        LinearProgressIndicator(
                            progress = { fraction }, modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
                            color = MaterialTheme.money.expense, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        Spacer(Modifier.width(12.dp))
                        TotalText(d.total, ctx, style = MaterialTheme.typography.bodyMedium, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        MoneyText(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BudgetsStatusCard(state: ReviewViewModel.UiState, ctx: CurrencyContext) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text("Budgets", style = MaterialTheme.typography.titleMedium)
            state.budgets.forEach { BudgetRow(it, ctx) }
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val (container, content, icon) = when (insight.kind) {
        InsightKind.POSITIVE -> Triple(MaterialTheme.money.incomeContainer, MaterialTheme.money.income, Icons.Filled.ThumbUp)
        InsightKind.NEGATIVE -> Triple(MaterialTheme.money.expenseContainer, MaterialTheme.money.expense, Icons.Filled.Warning)
        InsightKind.NEUTRAL -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.primary, Icons.Filled.Lightbulb)
    }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content)
            Spacer(Modifier.width(16.dp))
            MoneyText(insight.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Suppress("unused")
private fun money(minor: Long, ctx: CurrencyContext) = MoneyFormat.format(minor, ctx.base)
