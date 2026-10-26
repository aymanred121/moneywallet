package com.oriondev.moneywallet.next.domain.insights

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/** How often a recurring charge comes back; [tolerance] is the accepted drift in days around [days]. */
enum class Cadence(val label: String, val days: Int, val tolerance: Int, val perMonth: Double, val minOccurrences: Int) {
    WEEKLY("Weekly", 7, 1, 52.0 / 12, 3),
    MONTHLY("Monthly", 30, 4, 1.0, 3),
    /** Two payments a year apart are enough: a year of history can't hold three. */
    YEARLY("Yearly", 365, 20, 1.0 / 12, 2);

    fun next(from: LocalDate): LocalDate = when (this) {
        WEEKLY -> from.plusWeeks(1)
        MONTHLY -> from.plusMonths(1)
        YEARLY -> from.plusYears(1)
    }
}

/** One expense as the detector sees it: [amount] in minor units of [currency]. */
data class Charge(val date: LocalDate, val description: String, val currency: String, val amount: Long)

data class RecurringCharge(
    val name: String,
    val currency: String,
    val cadence: Cadence,
    /** Median payment, minor units of [currency]. */
    val typicalAmount: Long,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
    val occurrences: Int,
) {
    /** What this charge costs per month on average, minor units of [currency]. */
    val monthlyCost: Long get() = (typicalAmount * cadence.perMonth).roundToLong()
}

/**
 * Subscriptions and bills from plain expense history: same normalised description and currency,
 * a regular weekly / monthly / yearly rhythm and a stable amount (every payment within ±15% of the
 * median). Charges that stopped (well past their next date) are dropped. Pure and deterministic.
 */
object Recurring {
    const val AMOUNT_TOLERANCE = 0.15

    /** "Netflix #1234", "NETFLIX  " and "netflix.com" all become "netflix com"/"netflix": digits and punctuation go. */
    fun normalize(description: String): String =
        description.lowercase().replace(Regex("[^\\p{L}\\s]"), " ").trim().replace(Regex("\\s+"), " ")

    fun detect(charges: List<Charge>, today: LocalDate): List<RecurringCharge> =
        charges.asSequence()
            .map { it to normalize(it.description) }
            .filter { it.second.isNotEmpty() }
            .groupBy({ it.second to it.first.currency }, { it.first })
            .values.mapNotNull { detectOne(it, today) }
            .sortedBy { it.nextDate }

    private fun detectOne(group: List<Charge>, today: LocalDate): RecurringCharge? {
        // several payments on one day (a split bill) are one occurrence
        val days = group.groupBy { it.date }.toSortedMap().map { (day, cs) -> Charge(day, cs.last().description, cs.first().currency, cs.sumOf { it.amount }) }
        if (days.size < 2) return null
        val gaps = days.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
        val typicalGap = median(gaps.map { it.toLong() }).toInt()
        val cadence = Cadence.entries.firstOrNull { abs(typicalGap - it.days) <= it.tolerance } ?: return null
        if (days.size < cadence.minOccurrences) return null
        // one skipped or late payment is tolerated once the rhythm is well established
        val irregular = gaps.count { abs(it - cadence.days) > cadence.tolerance }
        if (irregular > (if (days.size >= 5) 1 else 0)) return null
        val typical = median(days.map { it.amount })
        if (typical <= 0 || days.any { abs(it.amount - typical) > typical * AMOUNT_TOLERANCE }) return null
        val last = days.last()
        val next = cadence.next(last.date)
        if (today.isAfter(next.plusDays(cadence.tolerance * 2L + 2))) return null // cancelled
        return RecurringCharge(last.description.trim(), last.currency, cadence, typical, last.date, next, days.size)
    }

    private fun median(values: List<Long>): Long {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
