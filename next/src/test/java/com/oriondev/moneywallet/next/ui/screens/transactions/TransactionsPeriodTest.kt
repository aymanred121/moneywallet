package com.oriondev.moneywallet.next.ui.screens.transactions

import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.domain.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TransactionsPeriodTest {
    private val periodFor = TransactionsViewModel.Companion::periodFor

    @Test fun followsTodayAndRollsOverOnPayday() {
        val before = periodFor(27, LocalDate.of(2026, 10, 26), null)
        val after = periodFor(27, LocalDate.of(2026, 10, 27), null)
        assertEquals(Period.Month(YearMonth.of(2026, 9), 27), before)
        assertEquals(Period.Month(YearMonth.of(2026, 10), 27), after)
        assertEquals(Dates.startOfDay(LocalDate.of(2026, 10, 27)), after.range.first)
    }

    @Test fun chosenMonthStaysPutAcrossDays() {
        val chosen = YearMonth.of(2026, 3)
        assertEquals(periodFor(27, LocalDate.of(2026, 10, 26), chosen), periodFor(27, LocalDate.of(2026, 10, 27), chosen))
    }

    @Test fun startDayIsPartOfTheCacheKey() {
        val m = YearMonth.of(2026, 9)
        val a = periodFor(1, LocalDate.of(2026, 9, 15), m)
        val b = periodFor(27, LocalDate.of(2026, 9, 15), m)
        assertNotEquals(a, b)
        assertNotEquals(a.range, b.range)
    }
}
