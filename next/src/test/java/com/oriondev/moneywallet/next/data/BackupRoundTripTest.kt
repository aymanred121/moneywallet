package com.oriondev.moneywallet.next.data

import androidx.room.Room
import com.oriondev.moneywallet.next.data.backup.BackupCodec
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.GoalEntity
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.util.zip.ZipInputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class BackupRoundTripTest {

    @Test fun exportThenImportKeepsEverything() = runBlocking {
        val source = TestData().apply { seed() }
        val bytes = ByteArrayOutputStream().also { BackupCodec(source.db).export(it) }.toByteArray()

        val target = Room.inMemoryDatabaseBuilder(source.context, WalletDatabase::class.java).allowMainThreadQueries().build()
        val report = BackupCodec(target).import(ByteArrayInputStream(bytes))
        assertEquals(3, report.wallets)
        assertEquals(source.db.transactions().all().size, report.transactions)
        assertEquals(1, report.transfers)
        assertTrue(report.problems.isEmpty())

        val before = source.db.transactions().all().map { Triple(it.uuid, it.money, it.date) }.toSet()
        val after = target.db().transactions().all().map { Triple(it.uuid, it.money, it.date) }.toSet()
        assertEquals(before, after)
        // transfer legs are re-linked to the imported transfer
        assertEquals(3, target.transactions().all().count { it.transferId != null })
        source.db.close(); target.close()
    }

    @Test fun unreadableRowsAreReportedNotMovedToToday() = runBlocking {
        val json = """
            {"header":{"version_code":3},
             "wallets":[{"id":"w1","name":"Cash","icon":"","currency":"SAR"},{"name":"No id","icon":"","currency":"SAR"},{"name":"No id 2","icon":"","currency":"SAR"}],
             "transactions":[
               {"id":"t1","wallet":"w1","money":100,"direction":0,"date":"2025-01-02 10:00:00"},
               {"id":"t2","wallet":"w1","money":100,"direction":0,"date":"not a date"},
               {"id":"t3","wallet":"missing","money":100,"direction":0,"date":"2025-01-02 10:00:00"}
             ]}
        """.trimIndent()
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("databases/database.json")); it.write(json.toByteArray()); it.closeEntry() }
        }.toByteArray()
        val t = TestData()
        val report = BackupCodec(t.db).import(ByteArrayInputStream(zip))
        assertEquals(3, report.wallets) // wallets without an id no longer collide
        assertEquals(1, report.transactions)
        assertEquals(listOf("1 transactions with an unreadable date", "1 transactions whose wallet is missing"), report.problems)
        t.db.close()
    }

    @Test fun goalsRoundTripAndRestoreReplacesExistingGoals() = runBlocking {
        val source = TestData().apply { seed() }
        val deadline = Dates.startOfDay(LocalDate.of(2027, 6, 1))
        source.db.goals().insert(GoalEntity(uuid = "g-linked", name = "Car", icon = "", targetMoney = 50_000_00, currency = "SAR", walletId = source.savings, deadline = deadline))
        source.db.goals().insert(GoalEntity(uuid = "g-manual", name = "Trip", icon = "", targetMoney = 2_000_00, currency = "USD", savedMoney = 150_00, archived = true, sortIndex = 1))
        val bytes = ByteArrayOutputStream().also { BackupCodec(source.db).export(it) }.toByteArray()

        val target = TestData().apply { seed() }
        target.db.goals().insert(GoalEntity(uuid = "stale", name = "Old", icon = "", targetMoney = 1, currency = "SAR"))
        val report = BackupCodec(target.db).import(ByteArrayInputStream(bytes))
        assertEquals(2, report.goals)
        assertFalse("savings" in report.skipped)
        val goals = target.db.goals().all().associateBy { it.uuid }
        assertEquals(setOf("g-linked", "g-manual"), goals.keys)
        val linked = goals.getValue("g-linked")
        // the wallet link survives through the wallet uuid, not the (regenerated) row id
        assertEquals(source.db.wallets().byId(source.savings)!!.uuid, target.db.wallets().byId(linked.walletId!!)!!.uuid)
        assertEquals(deadline, linked.deadline)
        with(goals.getValue("g-manual")) { assertNull(walletId); assertEquals(150_00L, savedMoney); assertTrue(archived); assertEquals("USD", currency); assertNull(this.deadline) }
        source.db.close(); target.db.close()
    }

    @Test fun exportKeepsTheLegacyKeyOrderWithGoalsLast() = runBlocking {
        val t = TestData().apply { seed() }
        val json = unzip(ByteArrayOutputStream().also { BackupCodec(t.db).export(it) }.toByteArray())
        // the legacy importer reads top-level keys in one fixed order and stops after transfer_attachment
        val positions = (BackupCodec.LEGACY_ORDER + BackupCodec.GOALS_KEY).map { json.indexOf("\"$it\":") }
        assertTrue(positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
        t.db.close()
    }

    @Test fun legacySavingsBecomeManualGoals() = runBlocking {
        val json = """
            {"header":{"version_code":3},
             "wallets":[{"id":"w1","name":"Cash","icon":"","currency":"SAR"}],
             "savings":[
               {"id":"s1","description":"New phone","icon":"{\"type\":\"resource\",\"resource\":\"ic_icon_phone\"}","start_money":50000,"end_money":400000,"wallet":"w1","end_date":"2026-12-31","complete":false},
               {"id":"s2","description":"Done","icon":"","start_money":100,"end_money":100,"wallet":"w1","complete":true},
               {"id":"s3","description":"Deleted","wallet":"w1","deleted":true},
               {"id":"s4","description":"Orphan","wallet":"nope","end_money":5}
             ]}
        """.trimIndent()
        val t = TestData()
        val report = BackupCodec(t.db).import(ByteArrayInputStream(zip(json)))
        assertEquals(2, report.goals)
        assertEquals(listOf("1 savings whose wallet is missing"), report.problems)
        assertTrue(report.skipped.isEmpty())
        val goals = t.db.goals().all().associateBy { it.uuid }
        with(goals.getValue("s1")) {
            assertEquals("New phone", name); assertEquals("SAR", currency); assertEquals(400000L, targetMoney); assertEquals(50000L, savedMoney)
            assertNull(walletId); assertFalse(archived); assertEquals(Dates.startOfDay(LocalDate.of(2026, 12, 31)), deadline); assertTrue(icon.contains("ic_icon_phone"))
        }
        assertTrue(goals.getValue("s2").archived)
        t.db.close()
    }

    private fun zip(json: String): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("databases/database.json")); it.write(json.toByteArray()); it.closeEntry() }
    }.toByteArray()

    private fun unzip(bytes: ByteArray): String = ZipInputStream(ByteArrayInputStream(bytes)).use { it.nextEntry; it.readBytes().toString(Charsets.UTF_8) }

    private fun WalletDatabase.db() = this
}
