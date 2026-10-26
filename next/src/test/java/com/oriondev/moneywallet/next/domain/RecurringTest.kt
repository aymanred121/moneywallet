package com.oriondev.moneywallet.next.domain

import com.oriondev.moneywallet.next.domain.insights.Cadence
import com.oriondev.moneywallet.next.domain.insights.Charge
import com.oriondev.moneywallet.next.domain.insights.Recurring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringTest {
    private val today = LocalDate.of(2026, 6, 20)
    private fun c(date: String, desc: String, amount: Long, currency: String = "SAR") = Charge(LocalDate.parse(date), desc, currency, amount)

    @Test fun monthlySubscriptionAcrossSpellings() {
        val found = Recurring.detect(
            listOf(c("2026-02-05", "Netflix #1021", 45_00), c("2026-03-05", "NETFLIX", 45_00), c("2026-04-06", "netflix", 45_00),
                c("2026-05-05", "Netflix", 47_00), c("2026-06-05", "Netflix", 45_00), c("2026-06-07", "Coffee", 12_00)),
            today,
        )
        assertEquals(1, found.size)
        val n = found.single()
        assertEquals(Cadence.MONTHLY, n.cadence)
        assertEquals(45_00L, n.typicalAmount)
        assertEquals(LocalDate.of(2026, 7, 5), n.nextDate)
        assertEquals(45_00L, n.monthlyCost)
        assertEquals(5, n.occurrences)
    }

    @Test fun weeklyAndYearly() {
        val gym = (0..4).map { c(LocalDate.of(2026, 5, 18).plusWeeks(it.toLong()).toString(), "Gym", 10_00) }
        val domain = listOf(c("2025-06-01", "Domain renewal", 60_00), c("2026-06-02", "Domain renewal", 60_00))
        val found = Recurring.detect(gym + domain, today).associateBy { it.cadence }
        assertEquals(4333L, found[Cadence.WEEKLY]!!.monthlyCost) // 10.00 × 52 / 12
        assertEquals(500L, found[Cadence.YEARLY]!!.monthlyCost)
    }

    @Test fun rejectsIrregularUnstableShortOrStopped() {
        val unstable = listOf(c("2026-03-01", "Electricity", 100_00), c("2026-04-01", "Electricity", 200_00), c("2026-05-01", "Electricity", 100_00))
        val twice = listOf(c("2026-05-01", "Spotify", 20_00), c("2026-06-01", "Spotify", 20_00))
        val stopped = listOf(c("2025-12-01", "Old gym", 99_00), c("2026-01-01", "Old gym", 99_00), c("2026-02-01", "Old gym", 99_00))
        val irregular = listOf(c("2026-03-01", "Taxi", 30_00), c("2026-03-19", "Taxi", 30_00), c("2026-05-02", "Taxi", 30_00), c("2026-06-10", "Taxi", 30_00))
        assertTrue(Recurring.detect(unstable + twice + stopped + irregular, today).isEmpty())
    }

    @Test fun currenciesAreNotMerged() {
        val sar = (1..3).map { c("2026-0${it + 2}-10", "Cloud", 10_00, "SAR") }
        val usd = (1..3).map { c("2026-0${it + 2}-12", "Cloud", 3_00, "USD") }
        assertEquals(setOf("SAR", "USD"), Recurring.detect(sar + usd, today).map { it.currency }.toSet())
    }
}
