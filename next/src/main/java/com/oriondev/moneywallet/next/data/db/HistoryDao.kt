package com.oriondev.moneywallet.next.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One expense for insights: amount in its wallet currency, category folded into its top-level parent. */
data class ExpenseRow(
    val id: Long,
    val money: Long,
    val currency: String,
    val date: Long,
    val description: String,
    /** Top-level category (children folded in), for per-category statistics. */
    val categoryId: Long?,
    /** The transaction's own category, for display. */
    val categoryName: String?,
    val categoryIcon: String?,
)

data class DayFlow(val day: String, val currency: String, val total: Long)

// Same report filters as TransactionDao (private there): transfer legs out, their fee in.
private const val H_NOT_TRANSFER =
    "(t.transferId IS NULL OR EXISTS (SELECT 1 FROM transfers tr WHERE tr.id = t.transferId AND tr.taxTransactionId = t.id))"
private const val H_WALLET_SCOPE =
    "(:walletId IS NULL OR t.walletId = :walletId) AND (:walletId IS NOT NULL OR (w.archived = 0 AND w.countInTotal = 1))"
/** Balance semantics of WalletDao: counted wallets, confirmed and counted transactions. */
private const val H_BALANCE = "w.archived = 0 AND w.countInTotal = 1 AND t.confirmed = 1 AND t.countInTotal = 1"

/** Read-only history queries behind analytics, insights, the recap and budget alerts. */
@Dao
interface HistoryDao {
    @Query(
        """
        SELECT t.id, t.money, w.currency AS currency, t.date, t.description, IFNULL(c.parentId, t.categoryId) AS categoryId,
               c.name AS categoryName, c.icon AS categoryIcon
        FROM transactions t JOIN wallets w ON w.id = t.walletId LEFT JOIN categories c ON c.id = t.categoryId
        WHERE $H_WALLET_SCOPE
          AND t.direction = 0 AND t.date >= :from AND t.date < :to AND t.confirmed = 1 AND t.countInTotal = 1 AND $H_NOT_TRANSFER
          AND (c.showReport IS NULL OR c.showReport = 1)
        ORDER BY t.date ASC, t.id ASC
        """
    )
    fun observeExpenses(walletId: Long?, from: Long, to: Long): Flow<List<ExpenseRow>>

    /** Date of the first confirmed, counted transaction in scope; null when there is none. */
    @Query(
        """
        SELECT MIN(t.date) FROM transactions t JOIN wallets w ON w.id = t.walletId
        WHERE $H_WALLET_SCOPE AND t.confirmed = 1 AND t.countInTotal = 1
        """
    )
    fun observeFirstDate(walletId: Long?): Flow<Long?>

    /** Start money of the counted wallets, per currency. */
    @Query("SELECT currency, SUM(startMoney) AS total FROM wallets WHERE archived = 0 AND countInTotal = 1 GROUP BY currency")
    fun observeStartMoney(): Flow<List<CurrencyTotal>>

    /** Signed flow (income − expense) of counted wallets dated before [before], per wallet currency. */
    @Query(
        """
        SELECT w.currency AS currency, SUM(CASE WHEN t.direction = 1 THEN t.money ELSE -t.money END) AS total
        FROM transactions t JOIN wallets w ON w.id = t.walletId
        WHERE $H_BALANCE AND t.date < :before GROUP BY w.currency
        """
    )
    fun observeFlowBefore(before: Long): Flow<List<CurrencyTotal>>

    /** Signed daily flow of counted wallets in [from, to), per local day and wallet currency. */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', t.date / 1000, 'unixepoch', 'localtime') AS day, w.currency AS currency,
               SUM(CASE WHEN t.direction = 1 THEN t.money ELSE -t.money END) AS total
        FROM transactions t JOIN wallets w ON w.id = t.walletId
        WHERE $H_BALANCE AND t.date >= :from AND t.date < :to
        GROUP BY day, w.currency ORDER BY day ASC
        """
    )
    fun observeDailyFlow(from: Long, to: Long): Flow<List<DayFlow>>
}
