package com.oriondev.moneywallet.next.data.csv

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * The transactions spreadsheet: our own export layout ([HEADER]) plus a lenient reader for bank
 * and spreadsheet exports through a [Mapping]. Pure: no Android, no database.
 */
object TransactionCsv {
    val HEADER = listOf("date", "wallet", "category", "parent category", "description", "note", "type", "amount", "currency", "original amount", "original currency", "rate")
    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    enum class Type(val label: String) { EXPENSE("expense"), INCOME("income"), TRANSFER("transfer") }

    /** One exported line; [amount] is signed (expenses and outgoing transfer legs negative) so spreadsheet sums work. */
    data class Row(
        val date: LocalDateTime, val wallet: String, val category: String?, val parentCategory: String?, val description: String, val note: String?,
        val type: Type, val amount: BigDecimal, val currency: String, val originalAmount: BigDecimal? = null, val originalCurrency: String? = null, val rate: Double? = null,
    )

    /** UTF-8 text with a BOM so Excel shows Arabic correctly. */
    fun export(rows: List<Row>): String = Csv.BOM + Csv.write(listOf(HEADER) + rows.map { r ->
        listOf(
            r.date.format(ISO), r.wallet, r.category.orEmpty(), r.parentCategory.orEmpty(), r.description, r.note.orEmpty(), r.type.label,
            r.amount.toPlainString(), r.currency, r.originalAmount?.toPlainString().orEmpty(), r.originalCurrency.orEmpty(), r.rate?.toString().orEmpty(),
        )
    })

    fun isOwnFormat(header: List<String>?): Boolean = header != null && header.size >= HEADER.size && header.take(HEADER.size).map { it.trim().lowercase() } == HEADER

    /** Column indices for a foreign file; optional columns are null. */
    data class Mapping(val date: Int, val amount: Int, val description: Int? = null, val category: Int? = null, val direction: Int? = null)

    /** Best guess from header names (English and common Arabic headings); falls back to the first two columns. */
    fun guessMapping(header: List<String>): Mapping {
        val h = header.map { it.trim().lowercase() }
        fun find(vararg keys: String): Int? = h.indexOfFirst { name -> keys.any { name.contains(it) } }.takeIf { it >= 0 }
        val date = find("date", "تاريخ", "time") ?: 0
        val amount = find("amount", "مبلغ", "المبلغ", "value", "sum", "total") ?: (if (date == 0) 1 else 0).coerceAtMost(h.lastIndex.coerceAtLeast(0))
        return Mapping(
            date = date, amount = amount,
            description = find("description", "details", "memo", "payee", "narration", "merchant", "الوصف", "البيان", "بيان", "التفاصيل")?.takeIf { it != date && it != amount },
            category = find("category", "الفئة", "التصنيف")?.takeIf { it != date && it != amount },
            direction = find("direction", "type", "debit/credit", "dr/cr", "النوع")?.takeIf { it != date && it != amount },
        )
    }

    /** A readable line. [amount] is signed as in the file; [expense] is already resolved from sign or direction. */
    data class Parsed(
        val line: Int, val date: LocalDateTime, val amount: BigDecimal, val expense: Boolean, val transfer: Boolean, val description: String,
        val category: String? = null, val parentCategory: String? = null, val note: String? = null, val wallet: String? = null, val currency: String? = null,
        val originalAmount: BigDecimal? = null, val originalCurrency: String? = null, val rate: Double? = null,
    )

    data class Result(val rows: List<Parsed>, val problems: List<String>)

    /** [mapping] null reads our own layout (header row required). Unreadable rows are counted in [Result.problems]. */
    fun parse(table: List<List<String>>, mapping: Mapping?, hasHeader: Boolean = true): Result {
        val problems = LinkedHashMap<String, Int>()
        fun problem(what: String) { problems[what] = (problems[what] ?: 0) + 1 }
        val dates = DateReader()
        val out = ArrayList<Parsed>()
        val start = if (mapping == null || hasHeader) 1 else 0
        for (i in start until table.size) {
            val r = table[i]
            fun cell(idx: Int?): String? = idx?.let { r.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
            val date = dates.read(cell(mapping?.date ?: 0)) ?: run { problem("rows with an unreadable date"); null } ?: continue
            val amount = parseAmount(cell(mapping?.amount ?: 7)) ?: run { problem("rows with an unreadable amount"); null } ?: continue
            if (amount.signum() == 0) { problem("rows with a zero amount"); continue }
            out += if (mapping == null) {
                val type = Type.entries.firstOrNull { it.label == cell(6)?.lowercase() }
                Parsed(
                    line = i + 1, date = date, amount = amount, expense = type == Type.EXPENSE || (type != Type.INCOME && amount.signum() < 0), transfer = type == Type.TRANSFER,
                    description = cell(4).orEmpty(), category = cell(2), parentCategory = cell(3), note = cell(5), wallet = cell(1), currency = cell(8)?.uppercase(),
                    originalAmount = parseAmount(cell(9))?.abs(), originalCurrency = cell(10)?.uppercase(), rate = cell(11)?.toDoubleOrNull(),
                )
            } else {
                val direction = cell(mapping.direction)?.let(::directionOf)
                Parsed(line = i + 1, date = date, amount = amount, expense = direction ?: (amount.signum() < 0), transfer = false, description = cell(mapping.description).orEmpty(), category = cell(mapping.category))
            }
        }
        return Result(out, problems.map { (what, n) -> "$n $what" })
    }

    /** True = expense, false = income, null = not a direction word. */
    fun directionOf(text: String): Boolean? = when (text.trim().lowercase()) {
        "expense", "expenses", "debit", "dr", "out", "withdrawal", "payment", "spent", "-", "مصروف", "مصروفات", "مدين", "سحب", "خصم" -> true
        "income", "credit", "cr", "in", "deposit", "received", "+", "دخل", "دائن", "إيداع", "ايداع" -> false
        else -> null
    }

    /**
     * Lenient decimal: currency codes/symbols and spaces are ignored, Arabic-Indic digits accepted,
     * "(12.50)" and "12.50-" are negative. With both separators the last one is the decimal point;
     * a lone separator followed by exactly three digits is grouping ("1,250"), otherwise decimal.
     */
    fun parseAmount(text: String?): BigDecimal? {
        if (text.isNullOrBlank()) return null
        var negative = false
        val t = normalizeDigits(text.trim()).let {
            if (it.startsWith("(") && it.endsWith(")")) { negative = true; it.substring(1, it.length - 1) } else it
        }
        val kept = StringBuilder()
        for (c in t) when (c) {
            in '0'..'9', '.', ',' -> kept.append(c)
            '٫' -> kept.append('.')
            '-', '−' -> negative = !negative
            else -> Unit
        }
        val s = kept.toString().trim('.', ',') // dots of abbreviations such as "ر.س"
        if (s.none { it.isDigit() }) return null
        val lastDot = s.lastIndexOf('.'); val lastComma = s.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastDot >= 0 || lastComma >= 0 -> {
                val sep = if (lastDot >= 0) '.' else ','
                val tail = s.length - s.lastIndexOf(sep) - 1
                if (s.count { it == sep } == 1 && tail != 3) s.replace(sep, '.') else s.replace(sep.toString(), "")
            }
            else -> s
        }
        val bd = normalized.toBigDecimalOrNull() ?: return null
        return if (negative) bd.negate() else bd
    }

    fun toMinor(amount: BigDecimal, decimals: Int): Long = amount.movePointRight(decimals).setScale(0, RoundingMode.HALF_EVEN).toLong()

    private fun normalizeDigits(s: String): String = buildString {
        for (ch in s) append(when (ch) { in '٠'..'٩' -> '0' + (ch - '٠'); in '۰'..'۹' -> '0' + (ch - '۰'); else -> ch })
    }

    /**
     * Tries ISO first, then day-first numeric dates (as used in most of the world), then
     * month-first when day-first cannot fit ("12/25/2025"). Remembers the last format that worked.
     */
    private class DateReader {
        private val datePatterns = listOf("yyyy-M-d", "yyyy/M/d", "d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "M/d/yyyy", "d/M/yy", "yyyyMMdd")
        private val formats: List<Pair<DateTimeFormatter, Boolean>> = datePatterns.flatMap { p ->
            listOf(DateTimeFormatter.ofPattern("$p'T'H:mm[:ss][.SSS]") to true, DateTimeFormatter.ofPattern("$p H:mm[:ss]") to true, DateTimeFormatter.ofPattern(p) to false)
        }
        private var hint = 0

        fun read(text: String?): LocalDateTime? {
            val t = text?.let(::normalizeDigits)?.trim() ?: return null
            tryFormat(hint, t)?.let { return it }
            for (i in formats.indices) if (i != hint) tryFormat(i, t)?.let { hint = i; return it }
            return try { OffsetDateTime.parse(t).toLocalDateTime() } catch (e: Exception) { null }
        }

        private fun tryFormat(i: Int, t: String): LocalDateTime? = try {
            val (f, withTime) = formats[i]
            if (withTime) LocalDateTime.parse(t, f) else LocalDate.parse(t, f).atStartOfDay()
        } catch (e: Exception) { null }
    }
}
