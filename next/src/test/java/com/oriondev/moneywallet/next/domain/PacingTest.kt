package com.oriondev.moneywallet.next.domain

import com.oriondev.moneywallet.next.domain.pacing.OverallBudget
import com.oriondev.moneywallet.next.domain.pacing.PacingEngine
import com.oriondev.moneywallet.next.domain.pacing.PacingInput
import com.oriondev.moneywallet.next.domain.pacing.SafeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PacingTest {
    // payday cycle 27 Sep – 26 Oct: 30 days
    private val start = LocalDate.of(2026, 9, 27)
    private val end = LocalDate.of(2026, 10, 27)
    private fun input(
        today: LocalDate, spent: Long, income: Long = 0, prevSoFar: Long? = null, prevTotal: Long? = null,
        budget: OverallBudget? = null, balance: Long? = null,
    ) = PacingInput(start, end, today, spent, income, prevSoFar, prevTotal, budget, balance)

    @Test fun daysCountTodayIncluded() {
        val first = PacingEngine.compute(input(start, 0))
        assertEquals(30, first.totalDays)
        assertEquals(1, first.elapsedDays)
        assertEquals(30, first.daysLeft)
        val last = PacingEngine.compute(input(LocalDate.of(2026, 10, 26), 0))
        assertEquals(30, last.elapsedDays)
        assertEquals(1, last.daysLeft)
    }

    @Test fun projectionIsRunRateWithoutHistory() {
        val p = PacingEngine.compute(input(LocalDate.of(2026, 10, 1), spent = 5_000)) // day 5
        assertEquals(30_000, p.projectedSpend)
        assertNull(p.projectedChange)
    }

    @Test fun projectionBlendsLastCycleShape() {
        // day 3 of 30: run rate 30_000, shape 3_000 + (20_000 - 2_000) = 21_000; weight 0.1
        val p = PacingEngine.compute(input(LocalDate.of(2026, 9, 29), spent = 3_000, prevSoFar = 2_000, prevTotal = 20_000))
        assertEquals(21_900, p.projectedSpend)
        assertEquals(10, p.projectedChange) // 21_900 vs 20_000 → +9.5% → 10
    }

    @Test fun projectionNeverBelowSpent() {
        val p = PacingEngine.compute(input(LocalDate.of(2026, 9, 29), spent = 9_000, prevSoFar = 30_000, prevTotal = 30_000))
        assertTrue(p.projectedSpend >= 9_000)
    }

    @Test fun safeToSpendFromBudget() {
        // 28 days left from 29 Sep; 30_000 - 2_000 left
        val p = PacingEngine.compute(input(LocalDate.of(2026, 9, 29), spent = 2_000, income = 100_000, budget = OverallBudget(30_000, 2_000, LocalDate.of(2026, 10, 26))))
        assertEquals(SafeSource.BUDGET, p.source)
        assertEquals(1_000L, p.safePerDay)
        assertFalse(p.over)
    }

    @Test fun budgetEndingBeforePaydaySpreadsOverItsOwnDays() {
        val p = PacingEngine.compute(input(LocalDate.of(2026, 9, 29), spent = 0, budget = OverallBudget(10_000, 0, LocalDate.of(2026, 10, 8))))
        assertEquals(1_000L, p.safePerDay) // 29 Sep – 8 Oct = 10 days
    }

    @Test fun safeToSpendFromIncomeWhenNoBudget() {
        val p = PacingEngine.compute(input(LocalDate.of(2026, 10, 17), spent = 50_000, income = 80_000)) // 10 days left
        assertEquals(SafeSource.INCOME, p.source)
        assertEquals(3_000L, p.safePerDay)
    }

    @Test fun noBudgetNoIncomeGivesNoNumber() {
        val p = PacingEngine.compute(input(LocalDate.of(2026, 10, 17), spent = 50_000))
        assertNull(p.safePerDay)
        assertNull(p.source)
        assertFalse(p.over)
    }

    @Test fun overBudgetIsNeverNegative() {
        val p = PacingEngine.compute(input(LocalDate.of(2026, 10, 17), spent = 12_000, budget = OverallBudget(10_000, 12_000, LocalDate.of(2026, 10, 26))))
        assertTrue(p.over)
        assertEquals(0L, p.safePerDay)
        assertEquals(2_000, p.overBy)
    }

    @Test fun balanceCapsSafeToSpend() {
        val p = PacingEngine.compute(input(LocalDate.of(2026, 10, 17), spent = 0, income = 100_000, balance = 5_000))
        assertEquals(500L, p.safePerDay)
        val broke = PacingEngine.compute(input(LocalDate.of(2026, 10, 17), spent = 0, income = 100_000, balance = -5_000))
        assertEquals(0L, broke.safePerDay)
        assertFalse(broke.over)
    }

    @Test fun todayOutsideCycleIsClamped() {
        val p = PacingEngine.compute(input(LocalDate.of(2026, 11, 5), spent = 30_000))
        assertEquals(1, p.daysLeft)
        assertEquals(30, p.elapsedDays)
        assertEquals(30_000, p.projectedSpend)
    }
}
