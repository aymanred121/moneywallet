package com.oriondev.moneywallet.next.domain.insights

import java.time.LocalDate

/** Day-level statistics that must not count the days before the user started tracking. */
object DayStats {
    /** First day that counts: the later of [start] and the day of the first transaction (null = nothing tracked yet). */
    fun trackedFrom(start: LocalDate, firstActivity: LocalDate?): LocalDate? = firstActivity?.let { maxOf(start, it) }

    /** Days in [start, endExclusive) since tracking began. */
    fun trackedDays(start: LocalDate, endExclusive: LocalDate, firstActivity: LocalDate?): Int {
        val from = trackedFrom(start, firstActivity) ?: return 0
        return java.time.temporal.ChronoUnit.DAYS.between(from, endExclusive).toInt().coerceAtLeast(0)
    }

    fun noSpendDays(start: LocalDate, endExclusive: LocalDate, spendingDays: Set<LocalDate>, firstActivity: LocalDate?): Int {
        val from = trackedFrom(start, firstActivity) ?: return 0
        return (trackedDays(start, endExclusive, firstActivity) - spendingDays.count { !it.isBefore(from) && it.isBefore(endExclusive) }).coerceAtLeast(0)
    }

    /** Longest run of days without spending in [start, endExclusive), starting no earlier than [firstActivity]. */
    fun longestNoSpendStreak(start: LocalDate, endExclusive: LocalDate, spendingDays: Set<LocalDate>, firstActivity: LocalDate?): Int {
        var d = trackedFrom(start, firstActivity) ?: return 0
        var run = 0; var best = 0
        while (d.isBefore(endExclusive)) {
            if (d in spendingDays) run = 0 else { run++; if (run > best) best = run }
            d = d.plusDays(1)
        }
        return best
    }
}

data class UnusualExpense(val description: String, val categoryName: String?, val amount: Long, val multiple: Double)

/**
 * An expense far above what its category usually costs: more than [FACTOR]× the median of the
 * category's other expenses in the look-back window (with at least [MIN_SAMPLES] of them) and
 * large enough to matter (at least [MIN_SHARE] of the period's spending). All amounts in base currency.
 */
object Unusual {
    const val FACTOR = 3.0
    const val MIN_SAMPLES = 4
    const val MIN_SHARE = 0.05

    data class Item(val id: Long, val categoryId: Long?, val amount: Long, val description: String, val categoryName: String?)

    fun find(candidates: List<Item>, history: List<Item>, periodSpend: Long): UnusualExpense? {
        if (periodSpend <= 0) return null
        val byCategory = history.filter { it.categoryId != null }.groupBy { it.categoryId }
        return candidates.asSequence()
            .filter { it.categoryId != null && it.amount >= periodSpend * MIN_SHARE }
            .mapNotNull { c ->
                val others = byCategory[c.categoryId].orEmpty().filter { it.id != c.id }.map { it.amount }.sorted()
                if (others.size < MIN_SAMPLES) return@mapNotNull null
                val median = others[others.size / 2].takeIf { it > 0 } ?: return@mapNotNull null
                val multiple = c.amount.toDouble() / median
                if (multiple > FACTOR) UnusualExpense(c.description, c.categoryName, c.amount, multiple) else null
            }
            .maxByOrNull { it.multiple }
    }
}
