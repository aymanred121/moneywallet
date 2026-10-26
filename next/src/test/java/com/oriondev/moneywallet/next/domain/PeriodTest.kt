package com.oriondev.moneywallet.next.domain

import com.oriondev.moneywallet.next.core.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodTest {
    @Test fun paydayMonthStartsOnThe27th() {
        val p = Period.Month(YearMonth.of(2026, 9), startDay = 27)
        assertEquals(Dates.startOfDay(LocalDate.of(2026, 9, 27)), p.range.first)
        assertEquals(Dates.startOfDay(LocalDate.of(2026, 10, 27)), p.range.last + 1)
    }

    @Test fun anchorBeforeStartDayBelongsToPreviousMonth() {
        assertEquals(YearMonth.of(2026, 9), Dates.monthAnchor(LocalDate.of(2026, 10, 26), 27))
        assertEquals(YearMonth.of(2026, 10), Dates.monthAnchor(LocalDate.of(2026, 10, 27), 27))
        assertEquals(YearMonth.of(2026, 10), Dates.monthAnchor(LocalDate.of(2026, 10, 1), 1))
    }

    @Test fun consecutiveMonthsAreContiguous() {
        var p = Period.Month(YearMonth.of(2026, 1), startDay = 27)
        repeat(24) {
            val next = p.next()
            assertEquals(p.range.last + 1, next.range.first)
            p = next
        }
    }

    @Test fun startDayBeyondMonthLengthClampsToLastDay() {
        // day 31 in February starts on the 28th, and the previous period still ends right before it
        assertEquals(LocalDate.of(2026, 2, 28), Dates.monthStart(YearMonth.of(2026, 2), 31))
        assertEquals(Dates.startOfDay(LocalDate.of(2026, 2, 28)), Period.Month(YearMonth.of(2026, 1), 31).range.last + 1)
    }

    @Test fun startDayIsPartOfThePeriodKey() {
        // a settings change must produce a different key so cached ranges are recomputed
        assertNotEquals(Period.Month(YearMonth.of(2026, 9), 1), Period.Month(YearMonth.of(2026, 9), 27))
        assertEquals(27, Period.Month(YearMonth.of(2026, 9), 27).previous().let { (it as Period.Month).startDay })
    }

    @Test fun monthOfPicksThePaydayCycle() {
        assertEquals(Period.Month(YearMonth.of(2026, 9), 27), Period.monthOf(LocalDate.of(2026, 10, 5), 27))
    }

    @Test fun labelShowsTheCycleRange() {
        val label = Period.Month(YearMonth.of(2026, 9), 27).label
        assert(label.contains("27") && label.contains("26")) { label }
    }
}
