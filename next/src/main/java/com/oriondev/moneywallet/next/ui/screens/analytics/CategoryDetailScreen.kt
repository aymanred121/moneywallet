package com.oriondev.moneywallet.next.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.TransactionRow
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.analytics.Aggregates
import com.oriondev.moneywallet.next.domain.analytics.Total
import com.oriondev.moneywallet.next.domain.analytics.Trend
import com.oriondev.moneywallet.next.domain.analytics.from
import com.oriondev.moneywallet.next.domain.analytics.to
import com.oriondev.moneywallet.next.domain.analytics.total
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.screens.home.RateHint
import com.oriondev.moneywallet.next.ui.screens.home.Staggered
import com.oriondev.moneywallet.next.ui.screens.home.TotalText
import com.oriondev.moneywallet.next.ui.screens.home.TransactionRowItem
import com.oriondev.moneywallet.next.ui.theme.money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.oriondev.moneywallet.next.ui.components.MoneyText

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailViewModel(container: AppContainer, private val categoryId: Long, private val from: Long, private val to: Long) : ViewModel() {
    private val db = container.db
    private val repo = container.repo

    /** The period the caller was looking at, reconstructed from the window length, plus the cycles before it. */
    private val periods: List<Period> = run {
        val start = Dates.toLocalDate(from)
        val days = Dates.daysBetween(start, Dates.toLocalDate(to))
        val current: Period = when {
            days <= 7 -> Period.Week(start)
            days <= 31 -> Period.monthOf(start, Dates.monthStartDay)
            days <= 366 -> Period.Year(start.year)
            else -> Period.Custom(start, Dates.toLocalDate(to).minusDays(1))
        }
        generateSequence(current) { it.previous() }.take(if (current is Period.Year || current is Period.Custom) 5 else 12).toList().reversed()
    }
    val trendTitle: String = when (periods.last()) {
        is Period.Week -> "Last ${periods.size} weeks"
        is Period.Month -> "Last ${periods.size} months"
        is Period.Year -> "Last ${periods.size} years"
        is Period.Custom -> "Last ${periods.size} periods"
    }

    data class UiState(
        val loading: Boolean = true,
        val ctx: CurrencyContext? = null,
        val category: CategoryEntity? = null,
        val total: Total = Total.EMPTY,
        val trend: List<TrendBar> = emptyList(),
        val rows: List<TransactionRow> = emptyList(),
        val direction: Int = Direction.EXPENSE,
        val periodLabel: String = "",
        /** Average of the finished periods since the category was first used; null without history. */
        val average: Long? = null,
    )

    private val walletId = container.prefs.settings.map { it.selectedWalletId }.distinctUntilChanged()

    val state: StateFlow<UiState> = walletId.flatMapLatest { w ->
        val tx = db.transactions()
        combine(
            repo.currencyContext, repo.categories,
            tx.observeCategoryDailySums(w, periods.first().range.from, to, categoryId),
            tx.observeRows(w, from, to, categoryId, ""),
        ) { ctx, cats, daily, rows ->
            val cat = cats.firstOrNull { it.id == categoryId }
            val direction = if (cat?.type == CategoryType.INCOME) Direction.INCOME else Direction.EXPENSE
            val dailyBags = Aggregates.dailyBags(daily, direction)
            val trend = periods.map { p ->
                val bag = Trend.sumBetween(dailyBags, Dates.toLocalDate(p.range.from), Dates.toLocalDate(p.range.to))
                TrendBar(shortLabel(p), ctx.total(bag), p == periods.last())
            }
            val today = java.time.LocalDate.now()
            val finished = trend.zip(periods).filter { (_, p) -> Dates.toLocalDate(p.range.to) <= today }.map { it.first.total.base }
            val used = finished.dropWhile { (it ?: 0L) == 0L }
            val average = if (used.isEmpty() || used.any { it == null }) null else used.sumOf { it!! } / used.size
            UiState(
                loading = false, ctx = ctx, category = cat, average = average,
                total = ctx.total(Trend.sumBetween(dailyBags, Dates.toLocalDate(from), Dates.toLocalDate(to))),
                trend = trend, rows = rows, direction = direction, periodLabel = periods.last().label,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun shortLabel(p: Period): String = when (p) {
        is Period.Month -> Dates.shortMonthLabel(p.month)
        is Period.Week -> p.start.dayOfMonth.toString() + "/" + p.start.monthValue
        is Period.Year -> p.year.toString().takeLast(2)
        is Period.Custom -> p.from.dayOfMonth.toString() + "/" + p.from.monthValue
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(categoryId: Long, from: Long, to: Long, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val vm = containerViewModel(key = "category-$categoryId-$from-$to") { CategoryDetailViewModel(it, categoryId, from, to) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.category?.name ?: "Category") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        val ctx = state.ctx ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                Staggered(0) {
                    Card(
                        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            SpecIcon(state.category?.icon, size = 56.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(state.periodLabel, style = MaterialTheme.typography.labelLarge)
                                TotalText(state.total, ctx, style = MaterialTheme.typography.headlineMedium, animated = true)
                                Text("${state.rows.size} ${if (state.rows.size == 1) "transaction" else "transactions"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item(key = "trend") {
                Staggered(1) {
                    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(16.dp)) {
                            Text(vm.trendTitle, style = MaterialTheme.typography.titleMedium)
                            state.average?.let { avg ->
                                MoneyText("Average ${MoneyFormat.format(avg, ctx.base, compact = true)} (dashed line)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(12.dp))
                            BarChart(
                                bars = state.trend.map { BarValue(it.label, it.total.base ?: 0L, it.isCurrent) },
                                modifier = Modifier.fillMaxWidth().height(160.dp), average = state.average, maxLabels = 6,
                                formatValue = { com.oriondev.moneywallet.next.core.CurrencySymbols.plainText(MoneyFormat.format(it, ctx.base, compact = true)) },
                                barColor = if (state.direction == Direction.EXPENSE) MaterialTheme.money.expense else MaterialTheme.money.income,
                                highlightColor = MaterialTheme.colorScheme.primary,
                            )
                            if (state.trend.any { it.total.hasRateGap }) RateHint(Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            item(key = "list-header") { Text("Transactions", style = MaterialTheme.typography.titleMedium) }
            if (state.rows.isEmpty() && !state.loading) {
                item { Text("No transactions in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.rows.size, key = { state.rows[it].id }) { i ->
                val row = state.rows[i]
                ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    TransactionRowItem(row, ctx, onClick = { onNavigate(Routes.transactionDetail(row.id)) })
                }
            }
        }
    }
}
