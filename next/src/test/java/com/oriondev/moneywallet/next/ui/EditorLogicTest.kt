package com.oriondev.moneywallet.next.ui

import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.CategoryUse
import com.oriondev.moneywallet.next.ui.components.lerpMinor
import com.oriondev.moneywallet.next.ui.components.transactions.AmountExpression
import com.oriondev.moneywallet.next.ui.screens.transactions.editor.QuickCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/** Pure logic behind the transaction editor: quick-category ranking, the keypad calculator, amount animation. */
class EditorLogicTest {
    private val sar = Currency("SAR", "Saudi Riyal", "SAR", 2)
    private val jpy = Currency("JPY", "Yen", "¥", 0)
    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun cat(id: Long, type: Int = CategoryType.EXPENSE, parent: Long? = null, tag: String? = null) =
        CategoryEntity(id = id, uuid = "c$id", name = "C$id", icon = "", type = type, parentId = parent, tag = tag)
    private fun use(id: Long, daysAgo: Int) = CategoryUse(id, now - daysAgo * day)

    // ---- QuickCategories ----

    @Test fun rankingWeighsRecencyOverRawCount() {
        val cats = listOf(cat(1), cat(2), cat(3))
        // 3 uses ~7 weeks ago vs 2 uses this week
        val uses = listOf(use(1, 50), use(1, 51), use(1, 52), use(2, 1), use(2, 2))
        assertEquals(listOf(2L, 1L, 3L), QuickCategories.rank(uses, cats, CategoryType.EXPENSE, now).map { it.id })
    }

    @Test fun rankingPrefersFrequencyAtSimilarAge() {
        val cats = listOf(cat(1), cat(2))
        val uses = listOf(use(1, 3), use(2, 3), use(2, 4))
        assertEquals(listOf(2L, 1L), QuickCategories.rank(uses, cats, CategoryType.EXPENSE, now).map { it.id })
    }

    @Test fun noHistoryFallsBackToFirstTopLevelCategoriesOfType() {
        val cats = listOf(cat(1), cat(2, parent = 1), cat(3, type = CategoryType.INCOME), cat(4), cat(5, tag = "system::transfer"), cat(6), cat(7), cat(8), cat(9))
        assertEquals(listOf(1L, 4L, 6L, 7L, 8L), QuickCategories.rank(emptyList(), cats, CategoryType.EXPENSE, now).map { it.id })
    }

    @Test fun rankingIgnoresOtherTypesDeletedAndSystemCategories() {
        val cats = listOf(cat(1), cat(2, type = CategoryType.INCOME), cat(5, tag = "system::transfer"), cat(6, parent = 1))
        val uses = listOf(use(2, 1), use(5, 1), use(99, 1), use(6, 1))
        assertEquals(listOf(6L, 1L), QuickCategories.rank(uses, cats, CategoryType.EXPENSE, now).map { it.id })
    }

    @Test fun selectedOutsideRankedMovesToFront() {
        val ranked = (1L..5L).map { cat(it) }
        assertEquals(listOf(9L, 1L, 2L, 3L, 4L), QuickCategories.withSelected(ranked, cat(9)).map { it.id })
        assertEquals(ranked, QuickCategories.withSelected(ranked, cat(3)))
        assertEquals(ranked, QuickCategories.withSelected(ranked, null))
    }

    // ---- AmountExpression ----

    private fun type(keys: String, decimals: Int = 2) = keys.fold("") { e, k -> AmountExpression.append(e, k.toString(), decimals) }

    @Test fun leadingZeroIsReplacedAndDecimalsAreCapped() {
        assertEquals("5", type("05"))
        assertEquals("0.", type("."))
        assertEquals("1.25", type("1.259"))
        assertEquals("1.2", type("1.2."))
        assertEquals("12", type("1.2", decimals = 0))
    }

    @Test fun operatorsReplaceEachOtherAndCannotLead() {
        assertEquals("", type("+"))
        assertEquals("5×", type("5+×"))
        assertEquals("5+", type("5.+"))
    }

    @Test fun digitsAreCappedAtTwelve() {
        assertEquals("123456789012", type("1234567890123"))
    }

    @Test fun evaluateHonoursPrecedenceAndTrailingOperator() {
        assertEquals(0, BigDecimal("14").compareTo(AmountExpression.evaluate("2+3×4")))
        assertEquals(0, BigDecimal("5").compareTo(AmountExpression.evaluate("5+")))
        assertEquals(0, BigDecimal("-1").compareTo(AmountExpression.evaluate("2−3")))
        assertNull(AmountExpression.evaluate("5÷0"))
        assertNull(AmountExpression.evaluate(""))
    }

    @Test fun toMinorRoundsHalfUpAndHandlesZeroDecimals() {
        assertEquals(333L, AmountExpression.toMinor("10÷3", sar))
        assertEquals(67L, AmountExpression.toMinor("2÷3", sar))
        assertEquals(1235L, AmountExpression.toMinor("1234.5", jpy))
        assertEquals(99_999_999_999_99L, AmountExpression.toMinor("99999999999.99", sar))
        assertNull(AmountExpression.toMinor("", sar))
    }

    @Test fun simplifyAndFromMinorRoundTrip() {
        assertEquals("3.33", AmountExpression.simplify("10÷3", 2))
        assertEquals("1500", AmountExpression.simplify("1000+500", 2))
        assertEquals("", AmountExpression.simplify("", 2))
        assertEquals("12.5", AmountExpression.fromMinor(1250, sar))
        assertEquals("", AmountExpression.fromMinor(0, sar))
        assertEquals(1250L, AmountExpression.toMinor(AmountExpression.fromMinor(1250, sar), sar))
    }

    // ---- lerpMinor ----

    @Test fun lerpIsExactAtTheEndsForLargeAmounts() {
        val from = 16_777_217L // first integer a Float cannot hold
        val to = 987_654_321_987L
        assertEquals(from, lerpMinor(from, to, 0f))
        assertEquals(to, lerpMinor(from, to, 1f))
        assertEquals(to, lerpMinor(from, to, 1.2f))
        assertEquals(to, lerpMinor(to, to, 0.37f))
    }

    @Test fun lerpInterpolatesBothWays() {
        assertEquals(150L, lerpMinor(100, 200, 0.5f))
        assertEquals(150L, lerpMinor(200, 100, 0.5f))
        assertEquals(-50L, lerpMinor(-100, 0, 0.5f))
    }
}
