package com.oriondev.moneywallet.next.domain.analytics

import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategorySum
import com.oriondev.moneywallet.next.data.db.CurrencySum
import com.oriondev.moneywallet.next.data.db.DailySum
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** DAO windows are [from, to); [Period.range] is built with `until`, so `to` is `last + 1`. */
val LongRange.from: Long get() = first
val LongRange.to: Long get() = last + 1

/** A multi-currency total plus its base-currency collapse (null when a rate is missing). */
data class Total(val bag: MoneyBag, val base: Long?) {
    val isEmpty: Boolean get() = bag.isEmpty
    val hasRateGap: Boolean get() = base == null

    companion object {
        val EMPTY = Total(MoneyBag(), 0L)
    }
}

fun CurrencyContext.total(bag: MoneyBag): Total = Total(bag, if (bag.isEmpty) 0L else toBase(bag))

fun CurrencyContext.total(minor: Long, currency: String): Total = total(MoneyBag(mapOf(currency to minor)))

/** Share of one top-level category in a period. */
data class CategoryShare(
    val categoryId: Long?,
    val name: String,
    val icon: String?,
    val total: Total,
    /** 0..1 of the convertible total; 0 when the rate is missing. */
    val fraction: Float,
    val colorHex: String,
)

object Aggregates {
    fun bag(sums: List<CurrencySum>, direction: Int): MoneyBag {
        val bag = MoneyBag()
        sums.filter { it.direction == direction }.forEach { bag.add(it.currency, it.total) }
        return bag
    }

    /** Per category bag, preserving the DAO's descending order of first appearance. */
    fun categoryBags(sums: List<CategorySum>): Map<Long?, MoneyBag> {
        val out = LinkedHashMap<Long?, MoneyBag>()
        for (s in sums) out.getOrPut(s.categoryId) { MoneyBag() }.add(s.currency, s.total)
        return out
    }

    fun dailyBags(sums: List<DailySum>, direction: Int): Map<LocalDate, MoneyBag> {
        val out = LinkedHashMap<LocalDate, MoneyBag>()
        for (s in sums) {
            if (s.direction != direction) continue
            val day = try { LocalDate.parse(s.day) } catch (e: Exception) { continue }
            out.getOrPut(day) { MoneyBag() }.add(s.currency, s.total)
        }
        return out
    }

    /** Whole percent change, null when there is nothing to compare against. */
    fun percentChange(current: Long?, previous: Long?): Int? {
        if (current == null || previous == null || previous == 0L) return null
        return (((current - previous).toDouble() / previous) * 100).toInt()
    }

    fun shares(sums: List<CategorySum>, categories: List<CategoryEntity>, ctx: CurrencyContext, limit: Int = Int.MAX_VALUE): List<CategoryShare> {
        val byId = categories.associateBy { it.id }
        val bags = categoryBags(sums)
        val totals = bags.map { (id, bag) -> id to ctx.total(bag) }
        val convertible = totals.sumOf { it.second.base ?: 0L }.coerceAtLeast(1L)
        return totals
            .sortedByDescending { it.second.base ?: Long.MIN_VALUE }
            .take(limit)
            .mapIndexed { index, (id, total) ->
                val cat = id?.let { byId[it] }
                CategoryShare(
                    categoryId = id,
                    name = cat?.name ?: "Uncategorised",
                    icon = cat?.icon,
                    total = total,
                    fraction = ((total.base ?: 0L).toFloat() / convertible).coerceIn(0f, 1f),
                    colorHex = categoryColorHex(cat?.icon, index),
                )
            }
    }

    /** Stable colour for a category: its letter-icon colour, otherwise a palette entry by position. */
    fun categoryColorHex(icon: String?, index: Int): String = when (val spec = IconSpec.parse(icon)) {
        is IconSpec.Letter -> if (icon.isNullOrBlank()) IconSpec.palette[index % IconSpec.palette.size] else spec.color
        is IconSpec.Symbol -> spec.color
        is IconSpec.Resource -> IconSpec.palette[index % IconSpec.palette.size]
    }
}

/** One bar of a trend chart. */
data class Bucket(val label: String, val start: LocalDate, val endExclusive: LocalDate, val bag: MoneyBag, val isCurrent: Boolean) {
    fun contains(day: LocalDate) = !day.isBefore(start) && day.isBefore(endExclusive)
}

object Trend {
    enum class Granularity { DAY, MONTH }

    fun granularity(period: Period): Granularity = when (period) {
        is Period.Year -> Granularity.MONTH
        is Period.Custom -> if (Dates.daysBetween(period.from, period.to) > 62) Granularity.MONTH else Granularity.DAY
        else -> Granularity.DAY
    }

    /** Empty buckets spanning the period, ready to be filled. */
    fun emptyBuckets(period: Period): List<Bucket> {
        val today = LocalDate.now()
        val start = Dates.toLocalDate(period.range.from)
        val end = Dates.toLocalDate(period.range.to)
        val out = ArrayList<Bucket>()
        when (granularity(period)) {
            Granularity.DAY -> {
                var d = start
                val weekly = period is Period.Week
                while (d.isBefore(end)) {
                    val label = if (weekly) d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) else d.dayOfMonth.toString()
                    out += Bucket(label, d, d.plusDays(1), MoneyBag(), d == today)
                    d = d.plusDays(1)
                }
            }
            Granularity.MONTH -> {
                var m = YearMonth.from(start)
                val last = YearMonth.from(end.minusDays(1))
                while (!m.isAfter(last)) {
                    val s = maxOf(m.atDay(1), start)
                    val e = minOf(m.plusMonths(1).atDay(1), end)
                    out += Bucket(Dates.shortMonthLabel(m).take(1), s, e, MoneyBag(), m == YearMonth.from(today))
                    m = m.plusMonths(1)
                }
            }
        }
        return out
    }

    fun buckets(period: Period, sums: List<DailySum>, direction: Int): List<Bucket> {
        val buckets = emptyBuckets(period)
        if (buckets.isEmpty()) return buckets
        val daily = Aggregates.dailyBags(sums, direction)
        for ((day, bag) in daily) {
            val b = buckets.firstOrNull { it.contains(day) } ?: continue
            bag.entries.forEach { (iso, minor) -> b.bag.add(iso, minor) }
        }
        return buckets
    }

    /** Sum of a daily map inside [start, endExclusive). */
    fun sumBetween(daily: Map<LocalDate, MoneyBag>, start: LocalDate, endExclusive: LocalDate): MoneyBag {
        val out = MoneyBag()
        for ((day, bag) in daily) {
            if (day.isBefore(start) || !day.isBefore(endExclusive)) continue
            bag.entries.forEach { (iso, minor) -> out.add(iso, minor) }
        }
        return out
    }
}
