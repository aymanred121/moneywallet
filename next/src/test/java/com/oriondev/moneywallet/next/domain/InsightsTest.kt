package com.oriondev.moneywallet.next.domain

import com.oriondev.moneywallet.next.alerts.BudgetAlerts
import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.data.db.CurrencyTotal
import com.oriondev.moneywallet.next.data.db.DayFlow
import com.oriondev.moneywallet.next.domain.analytics.NetWorth
import com.oriondev.moneywallet.next.domain.analytics.PeriodSelection
import com.oriondev.moneywallet.next.domain.insights.CategoryDelta
import com.oriondev.moneywallet.next.domain.insights.DayStats
import com.oriondev.moneywallet.next.domain.insights.InsightEngine
import com.oriondev.moneywallet.next.domain.insights.InsightInput
import com.oriondev.moneywallet.next.domain.insights.Unusual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class InsightsTest {
    private fun input(categories: List<CategoryDelta>, spent: Long = 5000_00, prev: Long = 5000_00) = InsightInput(
        periodNoun = "month", currency = Currency("SAR", "Saudi Riyal", "SAR"), spent = spent, earned = null, previousSpent = prev,
        categories = categories, daysElapsed = 30, daysWithSpending = 20, topDay = null, longestStreakWithoutSpending = 0,
        biggest = null, budgets = emptyList(), transactionCount = 40,
    )

    @Test fun tinyCategoriesDoNotMove() {
        // 5 → 50 is +900% but nothing on a 5 000 month
        assertNull(InsightEngine.mover(input(listOf(CategoryDelta("Snacks", 50_00, 5_00)))))
        val m = InsightEngine.mover(input(listOf(CategoryDelta("Snacks", 50_00, 5_00), CategoryDelta("Food", 1500_00, 1000_00))))
        assertEquals("Food", m!!.first.name)
        assertEquals(50, m.second)
    }

    @Test fun fillerInsightsAreGone() {
        val texts = InsightEngine.generate(input(emptyList())).map { it.text }
        assertTrue(texts.none { "on average" in it || "You spent on" in it })
    }

    @Test fun partialPeriodSaysLikeForLike() {
        val text = InsightEngine.generate(input(emptyList(), spent = 400_00, prev = 500_00).copy(partial = true)).first().text
        assertEquals("You spent 20% less than at this point last month.", text)
    }

    @Test fun unusualExpenseNeedsHistoryAndWeight() {
        val history = (1..5).map { Unusual.Item(it.toLong(), 7L, 100_00L + it, "Groceries", "Groceries") }
        val big = Unusual.Item(99, 7L, 500_00, "Big shop", "Groceries")
        val found = Unusual.find(listOf(big), history + big, periodSpend = 2000_00)
        assertNotNull(found)
        assertTrue(found!!.multiple > 4.9)
        assertNull(Unusual.find(listOf(big), history, periodSpend = 100_000_00)) // under 5% of the month
        assertNull(Unusual.find(listOf(big), history.take(2), periodSpend = 2000_00)) // not enough history
    }

    @Test fun streakStartsAtFirstTransaction() {
        val start = LocalDate.of(2026, 9, 1); val end = LocalDate.of(2026, 9, 30)
        val first = LocalDate.of(2026, 9, 20)
        val spending = setOf(LocalDate.of(2026, 9, 22))
        assertEquals(7, DayStats.longestNoSpendStreak(start, end, spending, first))
        assertEquals(10, DayStats.trackedDays(start, end, first))
        assertEquals(9, DayStats.noSpendDays(start, end, spending, first))
        assertEquals(0, DayStats.longestNoSpendStreak(start, end, emptySet(), null))
    }

    @Test fun selectionFollowsTodayAndStartDay() {
        val month = PeriodSelection.Relative(PeriodSelection.Kind.MONTH)
        assertEquals(Period.Month(YearMonth.of(2026, 9), 27), month.resolve(LocalDate.of(2026, 9, 29), 27, DayOfWeek.MONDAY))
        assertEquals(Period.Month(YearMonth.of(2026, 8), 27), month.resolve(LocalDate.of(2026, 9, 26), 27, DayOfWeek.MONDAY))
        assertEquals(Period.Month(YearMonth.of(2026, 7), 27), month.shift(-1).resolve(LocalDate.of(2026, 9, 26), 27, DayOfWeek.MONDAY))
        assertEquals(Period.Month(YearMonth.of(2026, 3), 27), PeriodSelection.Fixed(Period.Month(YearMonth.of(2026, 3), 1)).resolve(LocalDate.now(), 27, DayOfWeek.MONDAY))
    }

    @Test fun netWorthCumulatesFlowsPerCycle() {
        val today = LocalDate.of(2026, 9, 29)
        val cycles = NetWorth.cycles(today, 27, count = 3) // Jul, Aug, Sep cycles
        assertEquals(YearMonth.of(2026, 7), cycles.first().month)
        val bags = NetWorth.balances(
            cycles, today,
            start = listOf(CurrencyTotal("SAR", 1000_00)), before = listOf(CurrencyTotal("SAR", 200_00)),
            daily = listOf(DayFlow("2026-07-30", "SAR", -100_00), DayFlow("2026-08-27", "SAR", 500_00), DayFlow("2026-09-28", "USD", 10_00), DayFlow("2026-09-30", "SAR", 1_00)),
        )
        assertEquals(listOf(1100_00L, 1600_00L, 1600_00L), bags.map { it.entries["SAR"] })
        assertEquals(10_00L, bags.last().entries["USD"]) // 30 Sep is after today: not counted
    }

    @Test fun budgetThresholds() {
        assertNull(BudgetAlerts.reached(79_99, 100_00))
        assertEquals(80, BudgetAlerts.reached(80_00, 100_00))
        assertEquals(100, BudgetAlerts.reached(120_00, 100_00))
        assertNull(BudgetAlerts.reached(10, 0))
    }
}
