package com.oriondev.moneywallet.next.data

import com.oriondev.moneywallet.next.data.csv.Csv
import com.oriondev.moneywallet.next.data.csv.TransactionCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class CsvTest {

    @Test fun quotingRoundTrips() {
        val rows = listOf(listOf("plain", "with, comma", "with \"quote\"", "two\nlines", " edge ", "قهوة", ""))
        val text = Csv.write(rows)
        assertEquals("plain,\"with, comma\",\"with \"\"quote\"\"\",\"two\nlines\",\" edge \",قهوة,\r\n", text)
        assertEquals(rows, Csv.parse(text))
    }

    @Test fun parserHandlesBomSemicolonsCrlfAndBlankLines() {
        val text = Csv.BOM + "date;amount\r\n2025-01-02;\"12,50\"\r\n\r\n2025-01-03;7\n"
        assertEquals(listOf(listOf("date", "amount"), listOf("2025-01-02", "12,50"), listOf("2025-01-03", "7")), Csv.parse(text))
        assertEquals('\t', Csv.detectDelimiter("a\tb\tc,d"))
        assertEquals(listOf(listOf("a", "")), Csv.parse("a,"))
    }

    @Test fun decodeFallsBackForNonUtf8() {
        assertEquals("abc", Csv.decode((Csv.BOM + "abc").toByteArray()))
        assertEquals("قهوة", Csv.decode("قهوة".toByteArray(charset("windows-1256"))))
    }

    @Test fun amounts() {
        fun a(s: String) = TransactionCsv.parseAmount(s)
        assertEquals(BigDecimal("1250.75"), a("1,250.75"))
        assertEquals(BigDecimal("1250.75"), a("1.250,75"))
        assertEquals(BigDecimal("12.5"), a("12,5"))
        assertEquals(BigDecimal("1250"), a("1,250"))
        assertEquals(BigDecimal("-12.00"), a("(12.00)"))
        assertEquals(BigDecimal("-12.00"), a("12.00-"))
        assertEquals(BigDecimal("-45.5"), a("−SAR 45.5"))
        assertEquals(BigDecimal("12.5"), a("١٢٫٥ ر.س"))
        assertNull(a("n/a"))
        assertEquals(1234L, TransactionCsv.toMinor(BigDecimal("12.345"), 2)) // banker's rounding
    }

    @Test fun ownExportIsDetectedAndReadBack() {
        val row = TransactionCsv.Row(
            LocalDateTime.of(2025, 1, 2, 10, 30), "Cash", "Coffee", "Food", "Flat white, large", null, TransactionCsv.Type.EXPENSE,
            BigDecimal("-18.50"), "SAR", BigDecimal("4.93"), "USD", 3.75,
        )
        val text = TransactionCsv.export(listOf(row))
        assertTrue(text.startsWith(Csv.BOM))
        val table = Csv.parse(text)
        assertTrue(TransactionCsv.isOwnFormat(table.first()))
        val p = TransactionCsv.parse(table, mapping = null).rows.single()
        assertEquals(LocalDateTime.of(2025, 1, 2, 10, 30), p.date)
        assertTrue(p.expense); assertFalse(p.transfer)
        assertEquals("Flat white, large", p.description)
        assertEquals("Food", p.parentCategory); assertEquals("Cash", p.wallet); assertEquals("USD", p.originalCurrency); assertEquals(3.75, p.rate!!, 0.0)
    }

    @Test fun foreignFileWithMappingAndDirection() {
        val table = Csv.parse("Transaction Date,Details,Debit/Credit,Amount\n27/09/2026,Salary,Credit,\"15,000.00\"\n28/09/2026 14:05,Coffee,Debit,18\n12/25/2026,Gift,,-200\nbad,x,,1\n")
        val m = TransactionCsv.guessMapping(table.first())
        assertEquals(TransactionCsv.Mapping(date = 0, amount = 3, description = 1, category = null, direction = 2), m)
        val r = TransactionCsv.parse(table, m)
        assertEquals(3, r.rows.size)
        assertEquals(listOf(false, true, true), r.rows.map { it.expense })
        assertEquals(LocalDateTime.of(2026, 9, 28, 14, 5), r.rows[1].date)
        assertEquals(LocalDateTime.of(2026, 12, 25, 0, 0), r.rows[2].date) // month-first only when day-first cannot fit
        assertEquals(listOf("1 rows with an unreadable date"), r.problems)
    }
}
