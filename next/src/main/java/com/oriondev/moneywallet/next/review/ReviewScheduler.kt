package com.oriondev.moneywallet.next.review

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.oriondev.moneywallet.next.WalletApp
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.prefs.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Schedules the weekly (first day of the week, 09:00) and monthly (first day of the payday cycle,
 * 09:00: the morning after the cycle ends) review notifications with inexact, doze-friendly alarms.
 * Alarms are one-shot; the receiver re-arms the next occurrence after firing. Call [ensureScheduled]
 * from `WalletApp.onCreate` and whenever settings change (start days move the alarms).
 */
object ReviewScheduler {
    const val ACTION_WEEKLY = "com.oriondev.moneywallet.next.review.WEEKLY"
    const val ACTION_MONTHLY = "com.oriondev.moneywallet.next.review.MONTHLY"
    private const val REQ_WEEKLY = 4101
    private const val REQ_MONTHLY = 4102
    private val fireTime: LocalTime = LocalTime.of(9, 0)

    /** Reads the preferences and arms or cancels both alarms accordingly. Safe to call repeatedly. */
    fun ensureScheduled(context: Context) {
        val container = WalletApp.container(context)
        container.scope.launch {
            val settings = try { container.prefs.settings.first() } catch (e: Exception) { Settings() }
            if (settings.reviewNotifications) scheduleAll(context, settings) else cancelAll(context)
        }
    }

    fun scheduleAll(context: Context, settings: Settings) {
        rearm(context, ACTION_WEEKLY, settings)
        rearm(context, ACTION_MONTHLY, settings)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, ACTION_WEEKLY, REQ_WEEKLY))
        am.cancel(pendingIntent(context, ACTION_MONTHLY, REQ_MONTHLY))
    }

    /** Arm the next occurrence of one alarm. */
    fun rearm(context: Context, action: String, settings: Settings) {
        when (action) {
            ACTION_WEEKLY -> schedule(context, ACTION_WEEKLY, REQ_WEEKLY, nextWeekly(firstDayOfWeek = dayOfWeek(settings)))
            ACTION_MONTHLY -> schedule(context, ACTION_MONTHLY, REQ_MONTHLY, nextMonthly(startDay = settings.firstDayOfMonth))
        }
    }

    fun dayOfWeek(settings: Settings): DayOfWeek = DayOfWeek.of(settings.firstDayOfWeek.coerceIn(1, 7))

    private fun schedule(context: Context, action: String, requestCode: Int, at: LocalDateTime) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMillis(at), pendingIntent(context, action, requestCode))
    }

    /** Wall-clock time to epoch millis: 09:00 stays 09:00 across DST changes. */
    internal fun fireMillis(at: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long = at.atZone(zone).toInstant().toEpochMilli()

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReviewNotificationReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Next [firstDayOfWeek] at 09:00 strictly after [now]. */
    internal fun nextWeekly(now: LocalDateTime = LocalDateTime.now(), firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): LocalDateTime {
        var candidate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(firstDayOfWeek)).atTime(fireTime)
        if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1)
        return candidate
    }

    /** Next cycle start (day [startDay], clamped to short months) at 09:00 strictly after [now]. */
    internal fun nextMonthly(now: LocalDateTime = LocalDateTime.now(), startDay: Int = Dates.monthStartDay): LocalDateTime {
        val month = YearMonth.from(now)
        val candidate = Dates.monthStart(month, startDay).atTime(fireTime)
        return if (candidate.isAfter(now)) candidate else Dates.monthStart(month.plusMonths(1), startDay).atTime(fireTime)
    }
}
