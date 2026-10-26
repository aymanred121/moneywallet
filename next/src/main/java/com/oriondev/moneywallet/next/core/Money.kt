package com.oriondev.moneywallet.next.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/** A currency as the app knows it (mirrors the currencies table). */
data class Currency(val iso: String, val name: String, val symbol: String, val decimals: Int = 2) {
    val factor: Double get() = 10.0.pow(decimals)
}

/** Amount in minor units of a given currency. */
data class Amount(val minor: Long, val currency: String) {
    operator fun plus(other: Amount): Amount {
        require(other.currency == currency) { "currency mismatch" }
        return Amount(minor + other.minor, currency)
    }
    operator fun unaryMinus() = Amount(-minor, currency)
}

/** Multi-currency bag of amounts, never collapses on its own. */
class MoneyBag(initial: Map<String, Long> = emptyMap()) {
    private val map = LinkedHashMap<String, Long>(initial)
    val entries: Map<String, Long> get() = map
    val isEmpty get() = map.isEmpty()
    val size get() = map.size
    fun add(currency: String, minor: Long) { map[currency] = (map[currency] ?: 0L) + minor }
    fun add(amount: Amount) = add(amount.currency, amount.minor)
    fun single(): Amount? = map.entries.singleOrNull()?.let { Amount(it.value, it.key) }
}

/** Formatting helpers; every UI amount goes through here so decimals and symbols stay consistent. */
object MoneyFormat {
    /** When false, amounts are displayed without decimals (user setting). */
    @Volatile var showCents: Boolean = true
    /** Privacy mode: every formatted amount becomes dots (user setting). */
    @Volatile var masked: Boolean = false
    const val MASK = "••••"

    fun format(minor: Long, currency: Currency, showSymbol: Boolean = true, signed: Boolean = false, compact: Boolean = false, hidden: Boolean = masked): String {
        if (hidden) return MASK
        val value = minor / currency.factor
        val number = if (compact && abs(value) >= 10_000) compactNumber(value) else {
            val f = NumberFormat.getNumberInstance(Locale.getDefault()) as DecimalFormat
            val digits = if (showCents) currency.decimals else 0
            f.minimumFractionDigits = digits
            f.maximumFractionDigits = digits
            f.format(abs(value))
        }
        val sign = when {
            signed && minor < 0 -> "−"
            signed && minor > 0 -> "+"
            !signed && minor < 0 -> "−"
            else -> ""
        }
        return if (showSymbol) "$sign${currency.symbol} $number" else "$sign$number"
    }

    private fun compactNumber(value: Double): String {
        val v = abs(value)
        return when {
            v >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.1fB", v / 1_000_000_000)
            v >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", v / 1_000_000)
            else -> String.format(Locale.getDefault(), "%.1fK", v / 1_000)
        }
    }

    /**
     * Parse user input into minor units. Accepts "12.5", "1,250.75", "12,50", "1.250,75" and
     * Arabic-Indic digits ("١٢٫٥"). With both separators present the last one is the decimal
     * point; a lone comma is decimal only when 1..[Currency.decimals] digits follow it once.
     */
    fun parse(text: String, currency: Currency): Long? {
        val ascii = buildString {
            for (ch in text.trim()) append(
                when (ch) {
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    '\u066B' -> '.' // Arabic decimal separator
                    '\u066C', '\u00A0', '\u202F', ' ', '\'' -> continue // grouping
                    '−' -> '-'
                    else -> ch
                },
            )
        }
        val lastDot = ascii.lastIndexOf('.')
        val lastComma = ascii.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastComma > lastDot) ascii.replace(".", "").replace(',', '.') else ascii.replace(",", "")
            lastComma >= 0 -> {
                val tail = ascii.length - lastComma - 1
                if (ascii.count { it == ',' } == 1 && tail in 1..currency.decimals.coerceAtLeast(1) && tail != 3) ascii.replace(',', '.') else ascii.replace(",", "")
            }
            else -> ascii
        }
        val bd = normalized.toBigDecimalOrNull() ?: return null
        return bd.movePointRight(currency.decimals).setScale(0, RoundingMode.HALF_EVEN).toLong()
    }

    fun toDecimal(minor: Long, currency: Currency): BigDecimal = BigDecimal(minor).movePointLeft(currency.decimals)
}

/** Symbols the bundled list is behind on. */
object CurrencySymbols {
    /** New Saudi Riyal sign (Unicode 16). Shown only when the device font can draw it. */
    private const val RIYAL = "\u20C1"
    const val RIYAL_SIGN = RIYAL
    /** The app bundles res/font/riyal_symbol.ttf, so the sign is always safe to use in Compose text. */
    fun preferred(iso: String, symbol: String): String = when (iso) {
        "SAR" -> RIYAL
        else -> symbol
    }
    /** For surfaces that cannot load app fonts (widgets, notifications, share sheets). */
    fun plainText(text: String): String = text.replace(RIYAL, "SAR")
}

object Convert {
    /** Convert minor units between currencies given 1 from = rate to. */
    fun minor(from: Currency, to: Currency, minor: Long, rate: Double): Long {
        if (from.iso == to.iso) return minor
        require(rate.isFinite() && rate > 0) { "invalid rate $rate" }
        // BigDecimal + banker's rounding: no drift on large amounts, no bias on halves
        return BigDecimal(minor).movePointLeft(from.decimals)
            .multiply(BigDecimal.valueOf(rate))
            .movePointRight(to.decimals)
            .setScale(0, RoundingMode.HALF_EVEN)
            .toLong()
    }

    /** Cross rate from a USD-based table: 1 from = ? to. */
    fun crossRate(usdRates: Map<String, Double>, from: String, to: String): Double? {
        if (from == to) return 1.0
        val rf = usdRates[from] ?: return null
        val rt = usdRates[to] ?: return null
        return (1.0 / rf) * rt
    }
}
