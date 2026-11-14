package com.oriondev.moneywallet.next.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every released schema must migrate to the current one without losing rows. When bumping the
 * database version, add a step here that seeds the previous version and validates the next.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WalletDatabase::class.java)

    @Test fun v1ToV2KeepsData() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO wallets (id, uuid, name, icon, currency, startMoney, countInTotal, archived, sortIndex, lastEdit) VALUES (1, 'w', 'Cash', '', 'SAR', 0, 1, 0, 0, 0)")
            execSQL("INSERT INTO transactions (id, uuid, money, date, description, direction, type, walletId, confirmed, countInTotal, lastEdit) VALUES (1, 't', 1500, 0, 'coffee', 0, 0, 1, 1, 1, 0)")
            close()
        }
        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT money FROM transactions WHERE uuid = 't'").use { c -> c.moveToFirst(); assertEquals(1500L, c.getLong(0)) }
            db.query("SELECT COUNT(*) FROM goals").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }

    @Test fun v2ToV3AddsBudgetExclusionsAndKeepsBudgets() {
        helper.createDatabase(DB, 2).apply {
            execSQL("INSERT INTO budgets (id, uuid, type, startDate, endDate, money, currency, lastEdit) VALUES (1, 'b', 0, 0, 1, 50000, 'SAR', 0)")
            execSQL("INSERT INTO categories (id, uuid, name, icon, type, showReport, sortIndex, lastEdit) VALUES (1, 'c', 'Rent', '', 1, 1, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT money FROM budgets WHERE uuid = 'b'").use { c -> c.moveToFirst(); assertEquals(50000L, c.getLong(0)) }
            db.execSQL("INSERT INTO budget_excluded_categories (budgetId, categoryId) VALUES (1, 1)")
            db.query("SELECT COUNT(*) FROM budget_excluded_categories").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        }
    }

    private companion object { const val DB = "migration-test.db" }
}
