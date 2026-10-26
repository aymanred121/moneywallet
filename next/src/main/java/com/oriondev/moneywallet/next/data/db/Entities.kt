package com.oriondev.moneywallet.next.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Legacy-compatible enum values (see the original Schema.java). */
object CategoryType { const val INCOME = 0; const val EXPENSE = 1; const val SYSTEM = 2 }
object Direction { const val EXPENSE = 0; const val INCOME = 1 }
object TransactionType { const val STANDARD = 0; const val TRANSFER = 1; const val DEBT = 2; const val SAVING = 3; const val MODEL = 4 }
object BudgetType { const val EXPENSES = 0; const val INCOMES = 1; const val CATEGORY = 2 }

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val iso: String,
    val name: String,
    val symbol: String,
    val decimals: Int = 2,
    val favourite: Boolean = false,
    val lastEdit: Long = System.currentTimeMillis(),
)

@Entity(tableName = "wallets", indices = [Index("uuid", unique = true)])
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val icon: String,
    val currency: String,
    val startMoney: Long = 0,
    val countInTotal: Boolean = true,
    val archived: Boolean = false,
    val note: String? = null,
    val sortIndex: Int = 0,
    val lastEdit: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "categories",
    indices = [Index("uuid", unique = true), Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val icon: String,
    val type: Int,
    val parentId: Long? = null,
    val showReport: Boolean = true,
    val sortIndex: Int = 0,
    val tag: String? = null,
    val lastEdit: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "transactions",
    indices = [Index("uuid", unique = true), Index("walletId"), Index("categoryId"), Index("date"), Index("transferId"), Index("walletId", "date")],
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    /** Amount in the wallet currency, minor units, always positive. */
    val money: Long,
    /** Currency the user typed the amount in (null = wallet currency). */
    val currency: String? = null,
    val originalMoney: Long? = null,
    val exchangeRate: Double? = null,
    /** Local date-time in epoch millis. */
    val date: Long,
    val description: String = "",
    val categoryId: Long? = null,
    val direction: Int,
    val type: Int = TransactionType.STANDARD,
    val walletId: Long,
    val note: String? = null,
    val confirmed: Boolean = true,
    val countInTotal: Boolean = true,
    val transferId: Long? = null,
    val tag: String? = null,
    val lastEdit: Long = System.currentTimeMillis(),
)

@Entity(tableName = "transfers", indices = [Index("uuid", unique = true)])
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val description: String = "",
    val date: Long,
    val fromTransactionId: Long,
    val toTransactionId: Long,
    val taxTransactionId: Long? = null,
    val note: String? = null,
    val confirmed: Boolean = true,
    val countInTotal: Boolean = true,
    val lastEdit: Long = System.currentTimeMillis(),
)

@Entity(tableName = "budgets", indices = [Index("uuid", unique = true)])
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val type: Int,
    val categoryId: Long? = null,
    /** Inclusive local dates as epoch millis at start of day. */
    val startDate: Long,
    val endDate: Long,
    val money: Long,
    val currency: String,
    val lastEdit: Long = System.currentTimeMillis(),
)

@Entity(tableName = "budget_wallets", primaryKeys = ["budgetId", "walletId"])
data class BudgetWalletEntity(val budgetId: Long, val walletId: Long)

@Entity(tableName = "exchange_rates")
data class ExchangeRateEntity(
    @PrimaryKey val iso: String,
    /** Value of 1 USD expressed in this currency. */
    val rate: Double,
    val timestamp: Long,
)

/** A savings goal. Progress is the linked wallet's balance, or [savedMoney] when no wallet is linked. */
@Entity(tableName = "goals", indices = [Index("uuid", unique = true)])
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val icon: String,
    /** Target in [currency], minor units. */
    val targetMoney: Long,
    val currency: String,
    val walletId: Long? = null,
    /** Manually tracked progress in [currency] (used when [walletId] is null). */
    val savedMoney: Long = 0,
    /** Optional target date, epoch millis at start of day. */
    val deadline: Long? = null,
    val archived: Boolean = false,
    val sortIndex: Int = 0,
    val lastEdit: Long = System.currentTimeMillis(),
)

object CaptureStatus { const val PENDING = 0; const val ADDED = 1; const val DISMISSED = 2 }

/** A payment notification read from another app (Google Wallet, a bank...), waiting to be confirmed. */
@Entity(tableName = "captures", indices = [Index("dedupeKey", unique = true), Index("status")])
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable hash of package + text so re-posted notifications are not captured twice. */
    val dedupeKey: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** Parsed amount in minor units of [currency]; null when the text could not be parsed. */
    val amount: Long? = null,
    val currency: String? = null,
    val merchant: String? = null,
    val direction: Int = Direction.EXPENSE,
    val status: Int = CaptureStatus.PENDING,
    val transactionId: Long? = null,
)
