package com.oriondev.moneywallet.next.domain

import com.oriondev.moneywallet.next.data.repo.GoalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalMathTest {
    private val today = LocalDate.of(2026, 9, 29)

    @Test fun payCyclesCountTheCurrentOneAndTheDeadlineOne() {
        // payday on the 27th: Sep 27, Oct 27, Nov 27 and Dec 27 paychecks are all before 31 Dec
        assertEquals(4, GoalMath.monthsLeft(today, LocalDate.of(2026, 12, 31), startDay = 27))
        // 20 Dec belongs to the cycle that started on 27 Nov
        assertEquals(3, GoalMath.monthsLeft(today, LocalDate.of(2026, 12, 20), startDay = 27))
        // calendar months: Sep..Dec
        assertEquals(4, GoalMath.monthsLeft(today, LocalDate.of(2026, 12, 31)))
        // a deadline inside the current cycle still leaves one month
        assertEquals(1, GoalMath.monthsLeft(today, LocalDate.of(2026, 10, 10), startDay = 27))
    }

    @Test fun perMonthRoundsUp() {
        val p = GoalMath.progress(saved = 1_000_00, target = 10_000_01, deadline = LocalDate.of(2026, 12, 31), today = today, startDay = 27)
        assertEquals(9_000_01L, p.remaining)
        assertEquals(4, p.monthsLeft)
        assertEquals(2_250_01L, p.perMonth) // 9,000.01 / 4 = 2,250.0025 -> never short
        assertEquals(0.1f, p.fraction, 0.001f)
        assertFalse(p.complete)
    }

    @Test fun completeOverdueAndUndated() {
        val done = GoalMath.progress(12_000, 10_000, LocalDate.of(2026, 1, 1), today)
        assertTrue(done.complete); assertFalse(done.overdue); assertNull(done.perMonth); assertEquals(1f, done.fraction)
        val late = GoalMath.progress(5_000, 10_000, LocalDate.of(2026, 9, 1), today)
        assertTrue(late.overdue); assertNull(late.perMonth); assertEquals(5_000L, late.remaining)
        val open = GoalMath.progress(-500, 10_000, null, today)
        assertEquals(0f, open.fraction); assertNull(open.monthsLeft); assertEquals(10_500L, open.remaining)
        assertFalse(GoalMath.progress(0, 0, null, today).complete)
    }
}
