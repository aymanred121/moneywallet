package com.oriondev.moneywallet.next.data

import com.oriondev.moneywallet.next.data.csv.Csv
import com.oriondev.moneywallet.next.data.csv.CsvTransactions
import com.oriondev.moneywallet.next.data.csv.TransactionCsv
import com.oriondev.moneywallet.next.data.db.Direction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class CsvImportTest {

    @Test fun reimportingOurExportSkipsEverything() = runBlocking {
        val t = TestData().apply { seed() }
        val csv = CsvTransactions(t.db)
        val text = Csv.decode(ByteArrayOutputStream().also { csv.export(it) }.toByteArray())
        val table = Csv.parse(text)
        assertTrue(TransactionCsv.isOwnFormat(table.first()))
        assertEquals(t.db.transactions().all().size + 1, table.size)
        val parsed = TransactionCsv.parse(table, mapping = null)
        val before = t.db.transactions().all().size
        val report = csv.import(parsed.rows, t.cash, parsed.problems)
        assertEquals(0, report.imported)
        assertEquals(3, report.duplicates) // the three standard rows; the three transfer legs are refused
        assertEquals(listOf("3 transfers (restore a .mwbx backup to keep them)"), report.problems)
        assertEquals(before, t.db.transactions().all().size)
        t.db.close()
    }

    @Test fun mappedImportCreatesCategoriesAndSkipsDuplicates() = runBlocking {
        val t = TestData().apply { seed() }
        val table = Csv.parse("date,description,category,amount\n2026-09-28,Coffee,Cafés,-18.5\n2026-09-28,Coffee,Cafés,-18.5\n2026-09-29,Refund,Cafés,5\n")
        val parsed = TransactionCsv.parse(table, TransactionCsv.guessMapping(table.first()))
        val csv = CsvTransactions(t.db)
        val first = csv.import(parsed.rows, t.cash)
        assertEquals(3, first.imported) // repeated rows inside one file are kept
        assertEquals(2, first.categoriesCreated) // "Cafés" as an expense and as an income category
        val coffee = t.db.transactions().all().filter { it.description == "Coffee" }
        assertEquals(listOf(18_50L, 18_50L), coffee.map { it.money })
        assertTrue(coffee.all { it.direction == Direction.EXPENSE && it.walletId == t.cash })
        val again = csv.import(parsed.rows, t.cash)
        assertEquals(0, again.imported); assertEquals(3, again.duplicates)
        t.db.close()
    }
}
