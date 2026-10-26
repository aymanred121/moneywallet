package com.oriondev.moneywallet.next.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CurrencyEntity::class, WalletEntity::class, CategoryEntity::class, TransactionEntity::class,
        TransferEntity::class, BudgetEntity::class, BudgetWalletEntity::class, ExchangeRateEntity::class,
        GoalEntity::class, CaptureEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // Every schema change needs a migration (auto or manual) and a MigrationTest: there is no
    // destructive fallback, so a missing migration fails loudly instead of wiping user data.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun currencies(): CurrencyDao
    abstract fun wallets(): WalletDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun transfers(): TransferDao
    abstract fun budgets(): BudgetDao
    abstract fun rates(): ExchangeRateDao
    abstract fun goals(): GoalDao
    abstract fun captures(): CaptureDao
    abstract fun editor(): EditorDao
    abstract fun history(): HistoryDao

    companion object {
        fun create(context: Context): WalletDatabase =
            Room.databaseBuilder(context, WalletDatabase::class.java, DB_NAME).build()

        const val DB_NAME = "moneywallet-next.db"
    }
}
