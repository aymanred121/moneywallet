package com.oriondev.moneywallet.next.domain.insights

import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.core.MoneyFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class CategoryDelta(val name: String, val current: Long, val previous: Long?)
data class BiggestExpense(val amount: Long, val description: String, val categoryName: String?)
data class BudgetStatus(val name: String, val spent: Long, val limit: Long) {
    val ratio: Float get() = if (limit <= 0) 0f else spent.toFloat() / limit
}

/**
 * Everything the engine needs, already aggregated and converted to [currency]. Nulls mean "rate
 * unavailable". Previous-period figures must be like-for-like (see `Comparison.windows`); set
 * [partial] when the period is still running so sentences say "at this point last month".
 */
data class InsightInput(
    /** "week", "month" or "year". */
    val periodNoun: String,
    val currency: Currency,
    val spent: Long?,
    val earned: Long?,
    val previousSpent: Long?,
    val categories: List<CategoryDelta>,
    /** Days tracked in the period (days before the first transaction excluded). */
    val daysElapsed: Int,
    val daysWithSpending: Int,
    val topDay: Pair<LocalDate, Long>?,
    val longestStreakWithoutSpending: Int,
    val biggest: BiggestExpense?,
    val budgets: List<BudgetStatus>,
    val transactionCount: Int,
    val partial: Boolean = false,
    val unusual: UnusualExpense? = null,
)

enum class InsightKind { POSITIVE, NEGATIVE, NEUTRAL }
data class Insight(val text: String, val kind: InsightKind)

/** Pure rules that turn aggregates into plain-language sentences; no I/O, deterministic. Only notable facts make it. */
object InsightEngine {
    /** A category must weigh this much of the spending (now or before) for its move to matter. */
    const val MOVER_MIN_SHARE = 0.05
    /** ...and move by at least this share of the spending, so 5 → 50 on a 5 000 month stays quiet. */
    const val MOVER_MIN_CHANGE = 0.05
    const val MOVER_MIN_PERCENT = 15

    fun generate(input: InsightInput, max: Int = 5): List<Insight> {
        val out = ArrayList<Pair<Int, Insight>>() // priority, insight (lower is more important)
        val money = { minor: Long -> MoneyFormat.format(minor, input.currency) }
        val prevNoun = if (input.partial) "at this point last ${input.periodNoun}" else "last ${input.periodNoun}"

        // 1. Spending vs previous period (like-for-like)
        val spent = input.spent
        val prev = input.previousSpent
        if (spent != null && prev != null && prev > 0) {
            val pct = ((spent - prev) * 100.0 / prev).roundToInt()
            when {
                abs(pct) < 3 -> out += 20 to Insight("You spent about the same as $prevNoun.", InsightKind.NEUTRAL)
                pct < 0 -> out += 5 to Insight("You spent ${abs(pct)}% less than $prevNoun.", InsightKind.POSITIVE)
                else -> out += 5 to Insight("You spent $pct% more than $prevNoun.", InsightKind.NEGATIVE)
            }
        } else if (spent != null && spent == 0L) {
            out += 5 to Insight("No spending recorded this ${input.periodNoun} so far.", InsightKind.NEUTRAL)
        }

        // 2. Category moving the most, when it matters
        mover(input)?.let { (c, pct) ->
            val delta = c.current - c.previous!!
            val size = if (pct > 300) "from ${money(c.previous)} to ${money(c.current)}" else "${abs(pct)}% (${if (delta > 0) "+" else "−"}${money(abs(delta))})"
            out += 8 to if (delta > 0) Insight("${c.name} is up $size vs $prevNoun.", InsightKind.NEGATIVE)
            else Insight("${c.name} is down $size vs $prevNoun.", InsightKind.POSITIVE)
        }

        // 3. An expense far above its category's usual
        input.unusual?.let { u ->
            val what = u.description.takeIf { it.isNotBlank() }?.let { "'$it'" } ?: "One expense"
            val usual = u.categoryName?.let { " for $it" } ?: ""
            out += 7 to Insight("$what (${money(u.amount)}) was about ${u.multiple.roundToInt()}× your usual$usual.", InsightKind.NEUTRAL)
        }

        // 4. Dominant category
        if (spent != null && spent > 0) {
            input.categories.maxByOrNull { it.current }?.let { top ->
                val share = (top.current * 100 / spent).toInt()
                if (share >= 25) out += 12 to Insight("${top.name} made up $share% of your spending.", InsightKind.NEUTRAL)
            }
        }

        // 5. Biggest single expense, when it weighs on the period
        input.biggest?.let { b ->
            if (spent != null && spent > 0 && b.amount * 5 >= spent) {
                val where = b.description.takeIf { it.isNotBlank() }?.let { " at '$it'" } ?: b.categoryName?.let { " on $it" } ?: ""
                out += 10 to Insight("Your biggest expense was ${money(b.amount)}$where, ${(b.amount * 100 / spent).toInt()}% of the ${input.periodNoun}.", InsightKind.NEUTRAL)
            }
        }

        // 6. Income coverage
        val earned = input.earned
        if (spent != null && earned != null && spent > 0 && earned > 0) {
            val coverage = (earned * 100 / spent).toInt()
            out += 9 to if (coverage >= 100) Insight("Your income covered all your spending, with ${money(earned - spent)} to spare.", InsightKind.POSITIVE)
            else Insight("Income covered $coverage% of your spending.", InsightKind.NEGATIVE)
        }

        // 7. Few spending days (only worth saying when it is unusual)
        if (input.daysElapsed >= 7 && input.transactionCount > 0 && input.daysWithSpending * 10 <= input.daysElapsed * 4) {
            out += 15 to Insight("You spent on only ${input.daysWithSpending} of ${input.daysElapsed} days.", InsightKind.POSITIVE)
        }

        // 8. A day that stands out
        input.topDay?.let { (day, amount) ->
            if (spent != null && spent > 0 && amount * 10 >= spent * 3 && input.daysWithSpending > 2) {
                val name = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                out += 16 to Insight("$name ${day.dayOfMonth} took ${(amount * 100 / spent).toInt()}% of your spending (${money(amount)}).", InsightKind.NEUTRAL)
            }
        }

        // 9. Streak
        if (input.longestStreakWithoutSpending >= 3) {
            out += 14 to Insight("Your longest no-spend streak was ${input.longestStreakWithoutSpending} days.", InsightKind.POSITIVE)
        }

        // 10. Budgets
        if (input.budgets.isNotEmpty()) {
            val over = input.budgets.filter { it.ratio > 1f }
            val close = input.budgets.filter { it.ratio in 0.85f..1f }
            when {
                over.isNotEmpty() -> out += 6 to Insight("You're over budget on ${over.joinToString { it.name }}.", InsightKind.NEGATIVE)
                close.isNotEmpty() -> out += 7 to Insight("${close.first().name} budget is almost used up (${(close.first().ratio * 100).toInt()}%).", InsightKind.NEGATIVE)
                else -> out += 13 to Insight("All your budgets are on track.", InsightKind.POSITIVE)
            }
        }

        return out.sortedBy { it.first }.map { it.second }.take(max)
    }

    /** The category whose change matters most, with its whole-percent change; null when nothing moved enough. */
    fun mover(input: InsightInput): Pair<CategoryDelta, Int>? {
        val scale = maxOf(input.spent ?: 0L, input.previousSpent ?: 0L)
        if (scale <= 0) return null
        return input.categories
            .filter { c -> c.previous != null && c.previous > 0 && maxOf(c.current, c.previous) >= scale * MOVER_MIN_SHARE }
            .filter { c -> abs(c.current - c.previous!!) >= scale * MOVER_MIN_CHANGE }
            .map { c -> c to ((c.current - c.previous!!) * 100.0 / c.previous).roundToInt() }
            .filter { abs(it.second) >= MOVER_MIN_PERCENT }
            .maxByOrNull { abs(it.first.current - it.first.previous!!) }
    }
}
