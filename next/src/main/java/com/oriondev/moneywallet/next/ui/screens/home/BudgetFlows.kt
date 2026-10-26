package com.oriondev.moneywallet.next.ui.screens.home

import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.BudgetEntity
import com.oriondev.moneywallet.next.data.db.BudgetType
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** A budget with its current progress (in the budget currency), ready for display. */
data class BudgetUi(val budget: BudgetEntity, val spent: Long, val name: String) {
    val ratio: Float get() = if (budget.money <= 0) 0f else spent.toFloat() / budget.money
    val over: Boolean get() = spent > budget.money
}

fun budgetName(b: BudgetEntity, categories: List<CategoryEntity>): String = when (b.type) {
    BudgetType.INCOMES -> "Income goal"
    BudgetType.CATEGORY -> categories.firstOrNull { it.id == b.categoryId }?.name ?: "Category budget"
    else -> "Expenses"
}

fun budgetDirection(b: BudgetEntity, category: CategoryEntity?): Int = when {
    b.type == BudgetType.INCOMES -> Direction.INCOME
    b.type == BudgetType.CATEGORY && category?.type == CategoryType.INCOME -> Direction.INCOME
    else -> Direction.EXPENSE
}

/** First millisecond after the budget's (inclusive) last day; DST-safe unlike `endDate + 24h`. */
fun budgetEndExclusive(b: BudgetEntity): Long = Dates.startOfDay(Dates.toLocalDate(b.endDate).plusDays(1))

fun BudgetEntity.isActiveAt(at: Long): Boolean = at >= startDate && at < budgetEndExclusive(this)

/**
 * Live progress of one budget in its own currency. Linked wallets may use other currencies, so
 * their per-currency sums are converted; null while a needed exchange rate is missing.
 */
fun budgetProgress(db: WalletDatabase, b: BudgetEntity, category: CategoryEntity?, ctx: Flow<CurrencyContext>): Flow<Long?> =
    combine(
        db.transactions().observeBudgetProgress(
            budgetId = b.id, from = b.startDate, to = budgetEndExclusive(b),
            direction = budgetDirection(b, category),
            categoryId = if (b.type == BudgetType.CATEGORY) b.categoryId else null,
        ),
        ctx,
    ) { sums, c ->
        var total = 0L
        for (s in sums) total += c.convert(s.total, s.currency, b.currency) ?: return@combine null
        total
    }

/**
 * Budgets active at the instant [at] emits (up to [limit]) with live progress. Shared by the
 * dashboard (today) and the review (the reviewed period's last day).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun activeBudgets(
    db: WalletDatabase,
    ctx: Flow<CurrencyContext>,
    at: Flow<Long>,
    limit: Int,
    categories: Flow<List<CategoryEntity>>,
): Flow<List<BudgetUi>> =
    combine(db.budgets().observeAll(), categories, at) { budgets, cats, now ->
        budgets.filter { it.isActiveAt(now) }.sortedBy { it.endDate }.take(limit) to cats
    }.flatMapLatest { (active, cats) ->
        if (active.isEmpty()) flowOf(emptyList()) else combine(
            active.map { b ->
                val category = cats.firstOrNull { it.id == b.categoryId }
                budgetProgress(db, b, category, ctx).map { spent -> BudgetUi(b, spent ?: 0L, budgetName(b, cats)) }
            },
        ) { it.toList() }
    }
