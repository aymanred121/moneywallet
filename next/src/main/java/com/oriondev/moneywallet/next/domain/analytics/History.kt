package com.oriondev.moneywallet.next.domain.analytics

import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.data.db.CurrencyTotal
import com.oriondev.moneywallet.next.data.db.DayFlow
import com.oriondev.moneywallet.next.domain.Period
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * What the user picked on a period selector, resolved against today and the settings. Keeping the
 * choice relative ("this month", "two weeks ago") lets screens roll over at midnight and follow a
 * month-start-day change without being told.
 */
sealed interface PeriodSelection {
    enum class Kind { WEEK, MONTH, YEAR }

    data class Relative(val kind: Kind, val offset: Int = 0) : PeriodSelection
    data class Fixed(val period: Period) : PeriodSelection

    fun resolve(today: LocalDate, monthStartDay: Int, firstDayOfWeek: DayOfWeek): Period = when (this) {
        is Relative -> when (kind) {
            Kind.WEEK -> Period.Week(today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).plusWeeks(offset.toLong()))
            Kind.MONTH -> Period.Month(Dates.monthAnchor(today, monthStartDay).plusMonths(offset.toLong()), monthStartDay)
            Kind.YEAR -> Period.Year(today.year + offset)
        }
        // a stored month keeps its calendar anchor but follows the current start day
        is Fixed -> (period as? Period.Month)?.copy(startDay = monthStartDay) ?: period
    }

    fun shift(by: Int): PeriodSelection = when (this) {
        is Relative -> copy(offset = offset + by)
        is Fixed -> Fixed(generateSequence(period) { if (by < 0) it.previous() else it.next() }.drop(abs(by)).first())
    }

    private fun abs(v: Int) = if (v < 0) -v else v
}

/** Total balance of the counted wallets at the end of each of the last payday cycles. */
object NetWorth {
    /** The [count] cycles ending with the one containing [today], oldest first. */
    fun cycles(today: LocalDate, startDay: Int, count: Int = 12): List<Period.Month> {
        val current = Period.monthOf(today, startDay)
        return generateSequence(current) { it.previous() }.take(count).toList().reversed()
    }

    /** Cut-off of a cycle: its end, or tomorrow for the one still running (everything dated today counts). */
    fun cutoff(cycle: Period.Month, today: LocalDate): LocalDate =
        minOf(Dates.toLocalDate(cycle.range.to), today.plusDays(1))

    /**
     * Balance bag per cycle: start money + everything before the first cycle + the daily flows up to
     * each cut-off. [before] must be the flow before the first cycle's start, [daily] from then on.
     */
    fun balances(cycles: List<Period.Month>, today: LocalDate, start: List<CurrencyTotal>, before: List<CurrencyTotal>, daily: List<DayFlow>): List<MoneyBag> {
        val running = HashMap<String, Long>()
        (start + before).forEach { running[it.currency] = (running[it.currency] ?: 0L) + it.total }
        val days = daily.mapNotNull { f -> runCatching { LocalDate.parse(f.day) }.getOrNull()?.let { it to f } }.sortedBy { it.first }
        var i = 0
        return cycles.map { cycle ->
            val cut = cutoff(cycle, today)
            while (i < days.size && days[i].first.isBefore(cut)) {
                val f = days[i].second
                running[f.currency] = (running[f.currency] ?: 0L) + f.total
                i++
            }
            MoneyBag(running.toMap())
        }
    }
}
