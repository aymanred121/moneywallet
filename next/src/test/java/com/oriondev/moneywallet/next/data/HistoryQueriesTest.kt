package com.oriondev.moneywallet.next.data

import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.analytics.from
import com.oriondev.moneywallet.next.domain.analytics.to
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class HistoryQueriesTest {
    private lateinit var t: TestData
    private val month get() = Period.monthOf(t.day, Dates.monthStartDay).range

    @Before fun setUp() = runBlocking { t = TestData(); t.seed() }
    @After fun tearDown() = t.db.close()

    @Test fun expensesKeepFeesAndDropTransferLegsAndUncountedWallets() = runBlocking {
        val rows = t.db.history().observeExpenses(null, month.from, month.to).first()
        // food 100 + transfer fee 5; the 500 leg and the off-book wallet are out
        assertEquals(listOf(100_00L, 5_00L), rows.map { it.money })
        assertNotNull(t.db.history().observeFirstDate(null).first())
    }

    @Test fun balanceFlowsMatchWalletBalances() = runBlocking {
        val tomorrow = Dates.startOfTomorrow()
        val before = t.db.history().observeFlowBefore(tomorrow).first().associate { it.currency to it.total }
        // counted SAR wallets: 1000 − 100 − 500 − 5 + 500
        assertEquals(895_00L, before["SAR"])
        val daily = t.db.history().observeDailyFlow(month.from, tomorrow).first()
        assertEquals(895_00L, daily.filter { it.currency == "SAR" }.sumOf { it.total })
    }
}
