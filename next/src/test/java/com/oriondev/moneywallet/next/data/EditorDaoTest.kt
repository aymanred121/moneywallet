package com.oriondev.moneywallet.next.data

import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.TransactionEntity
import com.oriondev.moneywallet.next.ui.screens.transactions.editor.EditorForm
import com.oriondev.moneywallet.next.ui.screens.transactions.editor.editorFormOf
import com.oriondev.moneywallet.next.ui.screens.transactions.editor.toBundle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class EditorDaoTest {
    private lateinit var t: TestData

    @Before fun setUp() = runBlocking { t = TestData(); t.seed() }
    @After fun tearDown() = t.db.close()

    private suspend fun described(description: String, category: Long, date: Long, direction: Int = Direction.EXPENSE) =
        t.db.transactions().insert(TransactionEntity(uuid = UUID.randomUUID().toString(), money = 1_00, date = date, description = description, categoryId = category, direction = direction, walletId = t.cash))

    @Test fun categoryUsesSkipTransfersAndOtherDirections() = runBlocking {
        val uses = t.db.editor().categoryUses(Direction.EXPENSE, since = 0)
        // seed: two food expenses (cash, off book); the transfer legs and fee are excluded
        assertEquals(listOf(t.food, t.food), uses.map { it.categoryId })
        assertTrue(t.db.editor().categoryUses(Direction.EXPENSE, since = Long.MAX_VALUE).isEmpty())
    }

    @Test fun lastCategoryForMatchesIgnoringCaseAndPicksNewest() = runBlocking {
        val coffee = t.db.categories().insert(CategoryEntity(uuid = UUID.randomUUID().toString(), name = "Coffee", icon = "", type = CategoryType.EXPENSE))
        described("Starbucks", t.food, t.at(7))
        described("starbucks", coffee, t.at(12))
        described("Starbucks", t.salary, t.at(13), Direction.INCOME)
        assertEquals(coffee, t.db.editor().lastCategoryFor("STARBUCKS", Direction.EXPENSE))
        assertEquals(t.salary, t.db.editor().lastCategoryFor("starbucks", Direction.INCOME))
        assertNull(t.db.editor().lastCategoryFor("Starbuck", Direction.EXPENSE))
    }

    @Test fun editorFormSurvivesBundleRoundTrip() {
        val form = EditorForm(
            id = 7, loaded = true, expr = "12+3.5", direction = Direction.INCOME, categoryId = 4, walletId = 2, inputCurrency = "USD",
            manualRate = 3.75, date = 123_456, description = "Lunch", note = "n", confirmed = false, countInTotal = false,
            keypadOpen = false, isTransferLeg = true, categoryPicked = true, moreOpen = true,
        )
        assertEquals(form, editorFormOf(form.toBundle()))
        val blank = EditorForm(loaded = true, date = 1)
        assertEquals(blank, editorFormOf(blank.toBundle()))
    }
}
