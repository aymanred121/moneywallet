package com.oriondev.moneywallet.next.domain.analytics

import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.domain.Period
import java.time.LocalDate

/**
 * Like-for-like comparison windows. An open period (one containing [today]) is compared with the
 * same number of elapsed days at the start of the previous period, so on day 3 of a payday month
 * "vs last month" means "vs the first 3 days of last month", not "vs all of last month". A closed
 * period is compared whole with the whole previous one.
 */
data class ComparisonWindows(
    /** [from, to) of the part of the period compared (the whole period when closed). */
    val current: LongRange,
    /** [from, to) of the matching slice of the previous period. */
    val previous: LongRange,
    /** true when [current] stops at today (period still running). */
    val partial: Boolean,
    /** Days covered by [current], today included. */
    val elapsedDays: Int,
    val totalDays: Int,
)

object Comparison {
    fun windows(period: Period, today: LocalDate = LocalDate.now()): ComparisonWindows {
        val range = period.range
        val prev = period.comparable().range
        val start = Dates.toLocalDate(range.first)
        val endExclusive = Dates.toLocalDate(range.last + 1)
        val totalDays = Dates.daysBetween(start, endExclusive).toInt()
        val open = !today.isBefore(start) && today.isBefore(endExclusive)
        if (!open) return ComparisonWindows(range, prev, partial = false, elapsedDays = totalDays, totalDays = totalDays)
        val elapsed = Dates.daysBetween(start, today).toInt() + 1
        val currentEnd = Dates.startOfDay(today.plusDays(1))
        val prevStart = Dates.toLocalDate(prev.first)
        val prevEndDay = prevStart.plusDays(elapsed.toLong())
        val prevEnd = minOf(Dates.startOfDay(prevEndDay), prev.last + 1)
        return ComparisonWindows(range.first until currentEnd, prev.first until prevEnd, partial = true, elapsedDays = elapsed, totalDays = totalDays)
    }
}
