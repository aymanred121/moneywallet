package com.oriondev.moneywallet.next.domain

import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.domain.analytics.Comparison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ComparisonTest {
    private val sep = Period.Month(YearMonth.of(2026, 9), startDay = 27) // 27 Sep – 26 Oct

    @Test fun openPeriodComparesSameElapsedDays() {
        val w = Comparison.windows(sep, today = LocalDate.of(2026, 9, 29)) // day 3
        assertTrue(w.partial)
        assertEquals(3, w.elapsedDays)
        assertEquals(Dates.startOfDay(LocalDate.of(2026, 9, 30)), w.current.last + 1)
        assertEquals(Dates.startOfDay(LocalDate.of(2026, 8, 27)), w.previous.first)
        assertEquals(Dates.startOfDay(LocalDate.of(2026, 8, 30)), w.previous.last + 1)
    }

    @Test fun closedPeriodComparesWhole() {
        val w = Comparison.windows(sep, today = LocalDate.of(2026, 11, 3))
        assertFalse(w.partial)
        assertEquals(sep.range, w.current)
        assertEquals(sep.previous().range, w.previous)
    }

    @Test fun previousSliceNeverRunsPastThePreviousPeriod() {
        // a 31-day cycle compared with a shorter one: day 31 maps to the whole previous period
        val jan = Period.Month(YearMonth.of(2026, 1), 1)
        val mar = Period.Month(YearMonth.of(2026, 3), 1)
        val w = Comparison.windows(mar, today = LocalDate.of(2026, 3, 31))
        assertEquals(mar.previous().range.last + 1, w.previous.last + 1)
        assertEquals(31, Comparison.windows(jan, LocalDate.of(2026, 1, 31)).elapsedDays)
    }
}
