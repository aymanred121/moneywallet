package com.oriondev.moneywallet.next.domain

import com.oriondev.moneywallet.next.core.Dates
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A reporting window; [range] is [start, end) in epoch millis. */
sealed class Period {
    abstract val range: LongRange
    abstract val label: String
    abstract fun previous(): Period
    abstract fun next(): Period
    /** The same-length window right before this one, for comparisons. */
    open fun comparable(): Period = previous()

    /** [month] names the calendar month the period starts in; [startDay] is captured so a settings change yields a new key. */
    data class Month(val month: YearMonth, val startDay: Int = Dates.monthStartDay) : Period() {
        override val range get() = Dates.monthRange(month, startDay)
        override val label get() = Dates.monthLabel(month, startDay)
        override fun previous() = Month(month.minusMonths(1), startDay)
        override fun next() = Month(month.plusMonths(1), startDay)
    }

    data class Week(val start: LocalDate) : Period() {
        override val range get() = Dates.startOfDay(start) until Dates.startOfDay(start.plusWeeks(1))
        override val label: String get() {
            val f = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            return "${start.format(f)} – ${start.plusDays(6).format(f)}"
        }
        override fun previous() = Week(start.minusWeeks(1))
        override fun next() = Week(start.plusWeeks(1))
    }

    data class Year(val year: Int) : Period() {
        override val range get() = Dates.yearRange(year)
        override val label get() = year.toString()
        override fun previous() = Year(year - 1)
        override fun next() = Year(year + 1)
    }

    data class Custom(val from: LocalDate, val to: LocalDate) : Period() {
        override val range get() = Dates.startOfDay(from) until Dates.startOfDay(to.plusDays(1))
        override val label: String get() {
            val f = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
            return "${from.format(f)} – ${to.format(f)}"
        }
        private val days get() = Dates.daysBetween(from, to) + 1
        override fun previous() = Custom(from.minusDays(days), to.minusDays(days))
        override fun next() = Custom(from.plusDays(days), to.plusDays(days))
    }

    companion object {
        fun currentMonth(startDay: Int = Dates.monthStartDay) = Month(Dates.currentMonthAnchor(startDay), startDay)
        /** The month period containing [day]. */
        fun monthOf(day: LocalDate, startDay: Int = Dates.monthStartDay) = Month(Dates.monthAnchor(day, startDay), startDay)
        fun currentWeek(firstDay: DayOfWeek = DayOfWeek.MONDAY) = Week(LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDay)))
        fun currentYear() = Year(LocalDate.now().year)
        fun encode(p: Period): String = when (p) {
            is Month -> "m:${p.month}"
            is Week -> "w:${p.start}"
            is Year -> "y:${p.year}"
            is Custom -> "c:${p.from}:${p.to}"
        }
        fun decode(s: String): Period = try {
            val parts = s.split(":")
            when (parts[0]) {
                "m" -> Month(YearMonth.parse(parts[1]))
                "w" -> Week(LocalDate.parse(parts[1]))
                "y" -> Year(parts[1].toInt())
                "c" -> Custom(LocalDate.parse(parts[1]), LocalDate.parse(parts[2]))
                else -> currentMonth()
            }
        } catch (e: Exception) { currentMonth() }
    }
}
