package com.oriondev.moneywallet.next.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.CurrencyEntity
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.ExchangeRateEntity
import com.oriondev.moneywallet.next.data.db.TransactionEntity
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import com.oriondev.moneywallet.next.data.db.WalletEntity
import com.oriondev.moneywallet.next.data.prefs.UserPreferences
import com.oriondev.moneywallet.next.data.repo.WalletRepository
import java.time.LocalDate
import java.util.UUID

/** In-memory database with a small, known data set shared by the data-layer tests. */
class TestData {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: WalletDatabase = Room.inMemoryDatabaseBuilder(context, WalletDatabase::class.java).allowMainThreadQueries().build()
    val repo = WalletRepository(db, UserPreferences(context))

    var cash = 0L; var savings = 0L; var offBook = 0L; var food = 0L; var salary = 0L

    /** A day inside the current payday month, so "this month" queries see it. */
    val day: LocalDate = LocalDate.now()
    fun at(hour: Int) = Dates.toMillis(day.atTime(hour, 0))

    suspend fun seed() {
        db.currencies().upsertAll(listOf(CurrencyEntity("SAR", "Saudi Riyal", "SAR", 2), CurrencyEntity("USD", "US Dollar", "$", 2)))
        db.rates().upsertAll(listOf(ExchangeRateEntity("USD", 1.0, 0), ExchangeRateEntity("SAR", 3.75, 0)))
        repo.ensureDefaultCategories()
        cash = db.wallets().insert(WalletEntity(uuid = uuid(), name = "Cash", icon = "", currency = "SAR"))
        savings = db.wallets().insert(WalletEntity(uuid = uuid(), name = "Savings", icon = "", currency = "SAR"))
        offBook = db.wallets().insert(WalletEntity(uuid = uuid(), name = "Off book", icon = "", currency = "USD", countInTotal = false))
        food = db.categories().insert(CategoryEntity(uuid = uuid(), name = "Food", icon = "", type = CategoryType.EXPENSE))
        salary = db.categories().insert(CategoryEntity(uuid = uuid(), name = "Pay", icon = "", type = CategoryType.INCOME))
        tx(cash, 100_00, Direction.EXPENSE, food, at(9))
        tx(cash, 1000_00, Direction.INCOME, salary, at(8))
        tx(offBook, 50_00, Direction.EXPENSE, food, at(10))
        repo.saveTransfer(WalletRepository.TransferDraft(cash, savings, 500_00, 500_00, taxMinor = 5_00, date = at(11), description = "to savings", note = null))
    }

    suspend fun tx(wallet: Long, money: Long, direction: Int, category: Long?, date: Long) =
        db.transactions().insert(TransactionEntity(uuid = uuid(), money = money, date = date, categoryId = category, direction = direction, walletId = wallet))

    private fun uuid() = UUID.randomUUID().toString()
}
