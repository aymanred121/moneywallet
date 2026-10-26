package com.oriondev.moneywallet.next.domain.pacing

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** An active overall expense budget, all amounts in the base currency; [lastDay] is inclusive. */
data class OverallBudget(val amount: Long, val spent: Long, val lastDay: LocalDate)

/** Everything pacing needs about one payday cycle; amounts are minor units of the base currency. */
data class PacingInput(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val today: LocalDate,
    val spent: Long,
    val income: Long,
    /** What the previous cycle had spent after the same number of days; null when unknown. */
    val previousSpentSoFar: Long?,
    /** The whole previous cycle's spend; null when unknown. */
    val previousSpentTotal: Long?,
    val budget: OverallBudget? = null,
    /** Current balance of the counted wallets; caps safe-to-spend (you can't spend money you don't have). */
    val balance: Long? = null,
)

/** Where [Pacing.safePerDay] comes from. */
enum class SafeSource { BUDGET, INCOME }

data class Pacing(
    val totalDays: Int,
    /** Days of the cycle that have started, today included. */
    val elapsedDays: Int,
    /** Days still to live on, today included (1 on the last day before payday). */
    val daysLeft: Int,
    val projectedSpend: Long,
    /** Whole percent of [projectedSpend] vs the previous cycle's total; null when not comparable. */
    val projectedChange: Int?,
    /** Base-currency minor units that can be spent per day, never negative; null when there's nothing to base it on. */
    val safePerDay: Long?,
    val source: SafeSource?,
    /** The budget (or this cycle's income) is already used up; [overBy] says by how much. */
    val over: Boolean,
    val overBy: Long,
)

/**
 * Payday-cycle pacing. Deliberately simple so the numbers can be explained in one sentence:
 *
 * - **Projection** blends two guesses, trusting the current pace more as the cycle goes on
 *   (weight = elapsed / total): the *run rate* (daily average so far × cycle length) and the
 *   *last-cycle shape* (spent so far + what last cycle spent in its remaining days). Early on a
 *   big payday-week bill makes the run rate wild, so last cycle's shape dominates; near the end the
 *   actual pace does. Without history it is the run rate alone. Never below what's already spent.
 * - **Safe to spend per day** = what's left of the overall expense budget (or, without one, of
 *   this cycle's income) ÷ days left, capped by the current balance. Null when there is neither a
 *   budget nor income: no invented numbers.
 */
object PacingEngine {
    fun compute(input: PacingInput): Pacing = with(input) {
        val total = ChronoUnit.DAYS.between(start, endExclusive).toInt().coerceAtLeast(1)
        val day = today.coerceIn(start, endExclusive.minusDays(1))
        val elapsed = (ChronoUnit.DAYS.between(start, day).toInt() + 1).coerceIn(1, total)
        val daysLeft = ChronoUnit.DAYS.between(day, endExclusive).toInt().coerceAtLeast(1)

        val runRate = spent.toDouble() / elapsed * total
        val shape = if (previousSpentSoFar != null && previousSpentTotal != null) spent + (previousSpentTotal - previousSpentSoFar).coerceAtLeast(0) else null
        val weight = elapsed.toDouble() / total
        val projected = (if (shape == null) runRate else weight * runRate + (1 - weight) * shape).roundToLong().coerceAtLeast(spent)
        val change = previousSpentTotal?.takeIf { it > 0 }?.let { ((projected - it) * 100.0 / it).roundToLong().toInt() }

        val (source, remaining, days) = when {
            budget != null -> {
                val budgetDays = ChronoUnit.DAYS.between(day, minOf(budget.lastDay.plusDays(1), endExclusive)).toInt().coerceAtLeast(1)
                Triple(SafeSource.BUDGET, budget.amount - budget.spent, budgetDays)
            }
            income > 0 -> Triple(SafeSource.INCOME, income - spent, daysLeft)
            else -> Triple(null, 0L, daysLeft)
        }
        val spendable = if (balance != null) minOf(remaining, balance) else remaining
        val safe = if (source == null) null else spendable.coerceAtLeast(0) / days
        Pacing(total, elapsed, daysLeft, projected, change, safe, source, over = source != null && remaining <= 0, overBy = (-remaining).coerceAtLeast(0))
    }
}
