package com.oriondev.moneywallet.next.review

import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.domain.Period
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class ReviewSchedulerTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    @Test fun monthlyFiresOnTheCycleStartDay() {
        assertEquals(at(2026, 9, 27, 9), ReviewScheduler.nextMonthly(at(2026, 9, 26), startDay = 27))
        assertEquals(at(2026, 9, 27, 9), ReviewScheduler.nextMonthly(at(2026, 9, 27, 8, 59), startDay = 27))
        // at or after 09:00 on the start day: next cycle
        assertEquals(at(2026, 10, 27, 9), ReviewScheduler.nextMonthly(at(2026, 9, 27, 9), startDay = 27))
        assertEquals(at(2026, 10, 1, 9), ReviewScheduler.nextMonthly(at(2026, 9, 1, 10), startDay = 1))
    }

    @Test fun monthlyClampsToShortMonths() {
        assertEquals(at(2027, 2, 28, 9), ReviewScheduler.nextMonthly(at(2027, 2, 1), startDay = 31))
        assertEquals(at(2027, 3, 31, 9), ReviewScheduler.nextMonthly(at(2027, 2, 28, 10), startDay = 31))
        assertEquals(at(2028, 2, 29, 9), ReviewScheduler.nextMonthly(at(2028, 2, 10), startDay = 30)) // leap year
    }

    @Test fun weeklyFollowsFirstDayOfWeek() {
        // 2026-10-03 is a Saturday
        assertEquals(at(2026, 10, 3, 9), ReviewScheduler.nextWeekly(at(2026, 10, 3, 8), DayOfWeek.SATURDAY))
        assertEquals(at(2026, 10, 10, 9), ReviewScheduler.nextWeekly(at(2026, 10, 3, 9), DayOfWeek.SATURDAY))
        assertEquals(at(2026, 10, 5, 9), ReviewScheduler.nextWeekly(at(2026, 10, 3), DayOfWeek.MONDAY))
    }

    @Test fun dstDayStillFiresAtNineWallClock() {
        val ny = ZoneId.of("America/New_York") // clocks go forward on Sunday 2026-03-08
        val next = ReviewScheduler.nextWeekly(at(2026, 3, 7, 10), DayOfWeek.SUNDAY)
        assertEquals(at(2026, 3, 8, 9), next)
        val gap = ReviewScheduler.fireMillis(next, ny) - ReviewScheduler.fireMillis(at(2026, 3, 7, 9), ny)
        assertEquals(23L * 60 * 60 * 1000, gap)
        assertEquals(9, java.time.Instant.ofEpochMilli(ReviewScheduler.fireMillis(next, ny)).atZone(ny).hour)
    }

    @Test fun receiverSummarisesTheCycleThatJustEnded() {
        val s = Settings(firstDayOfMonth = 27, firstDayOfWeek = 6)
        assertEquals(Period.Month(YearMonth.of(2026, 8), 27), ReviewNotificationReceiver.justEnded(ReviewScheduler.ACTION_MONTHLY, s, LocalDate.of(2026, 9, 27)))
        assertEquals(Period.Week(LocalDate.of(2026, 9, 26)), ReviewNotificationReceiver.justEnded(ReviewScheduler.ACTION_WEEKLY, s, LocalDate.of(2026, 10, 3)))
    }
}
