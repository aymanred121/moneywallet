package com.oriondev.moneywallet.next.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object Dates {
    /**
     * Day of month a "month" period starts on (1..28). Loaded synchronously in [WalletApp.onCreate]
     * so receivers, workers and widgets started in a fresh process see the user's value, then kept
     * in sync with settings. Periods capture it at creation ([Period.Month.startDay]).
     */
    @Volatile var monthStartDay: Int = 1

    /** The [YearMonth] whose custom-start period contains [day]. */
    fun monthAnchor(day: LocalDate, startDay: Int = monthStartDay): YearMonth =
        if (day.dayOfMonth >= monthStart(YearMonth.from(day), startDay).dayOfMonth) YearMonth.from(day) else YearMonth.from(day.minusMonths(1))
    fun currentMonthAnchor(startDay: Int = monthStartDay): YearMonth = monthAnchor(LocalDate.now(), startDay)
    fun monthStart(month: YearMonth, startDay: Int = monthStartDay): LocalDate = month.atDay(startDay.coerceIn(1, month.lengthOfMonth()))
    fun monthEndExclusive(month: YearMonth, startDay: Int = monthStartDay): LocalDate = monthStart(month.plusMonths(1), startDay)
    private val sql: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val sqlDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun now(): Long = System.currentTimeMillis()
    fun toLocalDateTime(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    fun toLocalDate(millis: Long): LocalDate = toLocalDateTime(millis).toLocalDate()
    fun toMillis(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()
    fun startOfDay(d: LocalDate): Long = toMillis(d.atStartOfDay())
    /** First millisecond of tomorrow: everything dated today (any time) is before it. */
    fun startOfTomorrow(): Long = startOfDay(LocalDate.now().plusDays(1))

    fun monthRange(month: YearMonth, startDay: Int = monthStartDay): LongRange =
        startOfDay(monthStart(month, startDay)) until startOfDay(monthEndExclusive(month, startDay))
    fun weekRange(anyDay: LocalDate, firstDayOfWeek: java.time.DayOfWeek): LongRange {
        val start = anyDay.with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return startOfDay(start) until startOfDay(start.plusWeeks(1))
    }
    fun yearRange(year: Int): LongRange = startOfDay(LocalDate.of(year, 1, 1)) until startOfDay(LocalDate.of(year + 1, 1, 1))
    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

    // legacy backup format helpers
    fun parseSqlDateTime(text: String?): Long? = try {
        text?.let { toMillis(LocalDateTime.parse(it.trim(), sql)) }
    } catch (e: Exception) {
        try { text?.let { startOfDay(LocalDate.parse(it.trim(), sqlDate)) } } catch (e2: Exception) { null }
    }
    fun parseSqlDate(text: String?): Long? = try { text?.let { startOfDay(LocalDate.parse(it.trim(), sqlDate)) } } catch (e: Exception) { null }
    fun formatSqlDateTime(millis: Long): String = toLocalDateTime(millis).format(sql)
    fun formatSqlDate(millis: Long): String = toLocalDate(millis).format(sqlDate)

    // display
    fun dayLabel(millis: Long): String = toLocalDate(millis).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    fun dayLabelWithYear(millis: Long): String = toLocalDate(millis).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    fun timeLabel(millis: Long): String = toLocalDateTime(millis).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    fun monthLabel(month: YearMonth, startDay: Int = monthStartDay): String {
        if (startDay == 1) return month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        val f = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        val start = monthStart(month, startDay)
        val end = monthEndExclusive(month, startDay).minusDays(1)
        return "${start.format(f)} – ${end.format(f)}" + if (end.year != LocalDate.now().year) " ${end.year}" else ""
    }
    fun shortMonthLabel(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
    fun relativeDayLabel(millis: Long): String {
        val d = toLocalDate(millis)
        val today = LocalDate.now()
        return when {
            d == today -> "Today"
            d == today.minusDays(1) -> "Yesterday"
            d.year == today.year -> d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
            else -> d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
        }
    }
}
