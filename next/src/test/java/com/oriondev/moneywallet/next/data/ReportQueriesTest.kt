package com.oriondev.moneywallet.next.data

import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.BudgetEntity
import com.oriondev.moneywallet.next.data.db.BudgetType
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.analytics.from
import com.oriondev.moneywallet.next.domain.analytics.to
import com.oriondev.moneywallet.next.ui.screens.home.budgetProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class ReportQueriesTest {
    private lateinit var t: TestData
    private val month get() = Period.monthOf(t.day, Dates.monthStartDay).range

    @Before fun setUp() = runBlocking { t = TestData(); t.seed() }
    @After fun tearDown() = t.db.close()

    private fun totals(walletId: Long?) = runBlocking {
        t.db.transactions().observeTotals(walletId, month.from, month.to).first().associate { (it.currency to it.direction) to it.total }
    }

    @Test fun transfersAreNotSpendingButTheirFeeIs() {
        val all = totals(null)
        // 100 food + 5 transfer fee; the 500 moved to savings is neither spent nor earned
        assertEquals(105_00L, all["SAR" to Direction.EXPENSE])
        assertEquals(1000_00L, all["SAR" to Direction.INCOME])
    }

    @Test fun walletsOutsideTheTotalAreLeftOutOfAllWallets() {
        assertEquals(null, totals(null)["USD" to Direction.EXPENSE])
        // but still reported when that wallet is selected on its own
        assertEquals(50_00L, totals(t.offBook)["USD" to Direction.EXPENSE])
    }

    @Test fun categoryAndDailySumsIgnoreTransferLegs() = runBlocking {
        val cats = t.db.transactions().observeCategorySums(null, month.from, month.to, Direction.EXPENSE).first()
        val transferCat = t.db.categories().byTag("system::transfer")!!.id
        assertTrue(cats.none { it.categoryId == transferCat })
        val daily = t.db.transactions().observeDailySums(null, month.from, month.to).first()
        assertEquals(1000_00L, daily.filter { it.direction == Direction.INCOME }.sumOf { it.total })
    }

    @Test fun budgetProgressConvertsWalletCurrencies() = runBlocking {
        val b = BudgetEntity(uuid = UUID.randomUUID().toString(), type = BudgetType.EXPENSES, startDate = month.from, endDate = month.to - 1, money = 100_00, currency = "USD")
        val id = t.db.budgets().save(b, listOf(t.cash))
        val spent = budgetProgress(t.db, b.copy(id = id), null, t.repo.currencyContext).first()
        // 105.00 SAR at 3.75 = 28.00 USD
        assertEquals(28_00L, spent)
    }

    @Test fun balanceCountsEverythingDatedToday() = runBlocking {
        t.tx(t.cash, 1_00, Direction.EXPENSE, t.food, Dates.toMillis(t.day.atTime(23, 59)))
        val cash = t.repo.wallets().first().first { it.id == t.cash }
        // 1000 - 100 - 500 - 5 - 1
        assertEquals(394_00L, cash.balance)
    }

    @Test fun deletingAWalletRemovesBothSidesOfItsTransfers() = runBlocking {
        val b = BudgetEntity(uuid = UUID.randomUUID().toString(), type = BudgetType.EXPENSES, startDate = month.from, endDate = month.to - 1, money = 1, currency = "SAR")
        t.db.budgets().save(b, listOf(t.cash, t.savings))
        t.repo.deleteWallet(t.db.wallets().byId(t.cash)!!)
        assertTrue(t.db.transfers().all().isEmpty())
        val savings = t.repo.wallets().first().first { it.id == t.savings }
        assertEquals(0L, savings.balance)
        assertEquals(listOf(t.savings), t.db.budgets().allBudgetWallets().map { it.walletId })
    }
}
