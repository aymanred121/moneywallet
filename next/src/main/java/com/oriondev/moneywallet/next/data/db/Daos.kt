package com.oriondev.moneywallet.next.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class WalletWithBalance(
    val id: Long,
    val uuid: String,
    val name: String,
    val icon: String,
    val currency: String,
    val startMoney: Long,
    val countInTotal: Boolean,
    val archived: Boolean,
    val note: String?,
    val sortIndex: Int,
    /** startMoney + confirmed, counted, non-future flows. */
    val balance: Long,
)

data class TransactionRow(
    val id: Long,
    val money: Long,
    val currency: String?,
    val originalMoney: Long?,
    val exchangeRate: Double?,
    val date: Long,
    val description: String,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIcon: String?,
    val direction: Int,
    val type: Int,
    val walletId: Long,
    val walletName: String,
    val walletCurrency: String,
    val note: String?,
    val confirmed: Boolean,
    val countInTotal: Boolean,
    val transferId: Long?,
)

data class CategorySum(val categoryId: Long?, val currency: String, val total: Long)
data class DailySum(val day: String, val currency: String, val direction: Int, val total: Long)
data class CurrencySum(val currency: String, val direction: Int, val total: Long)
data class DescriptionSum(val description: String, val currency: String, val total: Long, val count: Int)
data class CurrencyTotal(val currency: String, val total: Long)

private const val BALANCE_FILTER = "t.confirmed = 1 AND t.countInTotal = 1 AND t.date <= :now"

/**
 * Moving money between your own wallets is neither spending nor income: drop both legs of a
 * transfer from reports but keep its fee (the tax leg), which is real spending.
 */
private const val NOT_TRANSFER =
    "(t.transferId IS NULL OR EXISTS (SELECT 1 FROM transfers tr WHERE tr.id = t.transferId AND tr.taxTransactionId = t.id))"

/** Wallet scope for reports: one wallet, or every active wallet that counts in the total. */
private const val WALLET_SCOPE =
    "(:walletId IS NULL OR t.walletId = :walletId) AND (:walletId IS NOT NULL OR (w.archived = 0 AND w.countInTotal = 1))"

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies ORDER BY favourite DESC, name ASC")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies")
    suspend fun all(): List<CurrencyEntity>

    @Query("SELECT COUNT(*) FROM currencies")
    suspend fun count(): Int

    @Query("SELECT * FROM currencies WHERE iso = :iso")
    suspend fun byIso(iso: String): CurrencyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CurrencyEntity>)

    @Query("UPDATE currencies SET symbol = :symbol WHERE iso = :iso")
    suspend fun setSymbol(iso: String, symbol: String)

    @Query("UPDATE currencies SET favourite = :favourite WHERE iso = :iso")
    suspend fun setFavourite(iso: String, favourite: Boolean)
}

@Dao
interface WalletDao {
    @Query(
        """
        SELECT w.id, w.uuid, w.name, w.icon, w.currency, w.startMoney, w.countInTotal, w.archived, w.note, w.sortIndex,
               w.startMoney + IFNULL((SELECT SUM(CASE WHEN t.direction = 1 THEN t.money ELSE -t.money END)
                                      FROM transactions t WHERE t.walletId = w.id AND $BALANCE_FILTER), 0) AS balance
        FROM wallets w ORDER BY w.archived ASC, w.sortIndex ASC, w.name ASC
        """
    )
    fun observeWithBalance(now: Long): Flow<List<WalletWithBalance>>

    @Query("SELECT * FROM wallets ORDER BY sortIndex ASC, name ASC")
    suspend fun all(): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun byId(id: Long): WalletEntity?

    @Query("SELECT * FROM wallets WHERE id = :id")
    fun observeById(id: Long): Flow<WalletEntity?>

    @Query("SELECT COUNT(*) FROM wallets")
    fun observeCount(): Flow<Int>

    @Insert suspend fun insert(w: WalletEntity): Long
    @Update suspend fun update(w: WalletEntity)
    @Delete suspend fun delete(w: WalletEntity)
    @Query("DELETE FROM wallets") suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type ASC, sortIndex ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE tag = :tag LIMIT 1")
    suspend fun byTag(tag: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE type != 2")
    suspend fun userCount(): Int

    @Insert suspend fun insert(c: CategoryEntity): Long
    @Insert suspend fun insertAll(c: List<CategoryEntity>): List<Long>
    @Update suspend fun update(c: CategoryEntity)
    @Delete suspend fun delete(c: CategoryEntity)
    @Query("UPDATE categories SET parentId = NULL WHERE parentId = :parentId") suspend fun detachChildren(parentId: Long)
    @Query("DELETE FROM categories") suspend fun deleteAll()
}

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT t.id, t.money, t.currency, t.originalMoney, t.exchangeRate, t.date, t.description, t.categoryId,
               c.name AS categoryName, c.icon AS categoryIcon, t.direction, t.type, t.walletId,
               w.name AS walletName, w.currency AS walletCurrency, t.note, t.confirmed, t.countInTotal, t.transferId
        FROM transactions t
        JOIN wallets w ON w.id = t.walletId
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE (:walletId IS NULL OR t.walletId = :walletId)
          AND (:walletId IS NOT NULL OR w.archived = 0)
          AND t.date >= :from AND t.date < :to
          AND (:categoryId IS NULL OR t.categoryId = :categoryId OR c.parentId = :categoryId)
          AND (:query = '' OR t.description LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%' OR t.note LIKE '%' || :query || '%')
        ORDER BY t.date DESC, t.id DESC
        """
    )
    fun observeRows(walletId: Long?, from: Long, to: Long, categoryId: Long?, query: String): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT t.id, t.money, t.currency, t.originalMoney, t.exchangeRate, t.date, t.description, t.categoryId,
               c.name AS categoryName, c.icon AS categoryIcon, t.direction, t.type, t.walletId,
               w.name AS walletName, w.currency AS walletCurrency, t.note, t.confirmed, t.countInTotal, t.transferId
        FROM transactions t JOIN wallets w ON w.id = t.walletId LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.id = :id
        """
    )
    fun observeRow(id: Long): Flow<TransactionRow?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transferId = :transferId")
    suspend fun byTransfer(transferId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TransactionEntity>

    /** Per top-level category totals (children folded into their parent). */
    @Query(
        """
        SELECT IFNULL(c.parentId, t.categoryId) AS categoryId, w.currency AS currency, SUM(t.money) AS total
        FROM transactions t JOIN wallets w ON w.id = t.walletId LEFT JOIN categories c ON c.id = t.categoryId
        WHERE $WALLET_SCOPE
          AND t.direction = :direction AND t.date >= :from AND t.date < :to
          AND t.confirmed = 1 AND t.countInTotal = 1 AND $NOT_TRANSFER AND (c.showReport IS NULL OR c.showReport = 1)
        GROUP BY IFNULL(c.parentId, t.categoryId), w.currency
        ORDER BY total DESC
        """
    )
    fun observeCategorySums(walletId: Long?, from: Long, to: Long, direction: Int): Flow<List<CategorySum>>

    /** Daily buckets (local day string yyyy-MM-dd computed from epoch millis). */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', t.date / 1000, 'unixepoch', 'localtime') AS day, w.currency AS currency, t.direction AS direction, SUM(t.money) AS total
        FROM transactions t JOIN wallets w ON w.id = t.walletId LEFT JOIN categories c ON c.id = t.categoryId
        WHERE $WALLET_SCOPE
          AND t.date >= :from AND t.date < :to AND t.confirmed = 1 AND t.countInTotal = 1 AND $NOT_TRANSFER
          AND (c.showReport IS NULL OR c.showReport = 1)
        GROUP BY day, w.currency, t.direction ORDER BY day ASC
        """
    )
    fun observeDailySums(walletId: Long?, from: Long, to: Long): Flow<List<DailySum>>

    @Query(
        """
        SELECT w.currency AS currency, t.direction AS direction, SUM(t.money) AS total
        FROM transactions t JOIN wallets w ON w.id = t.walletId LEFT JOIN categories c ON c.id = t.categoryId
        WHERE $WALLET_SCOPE
          AND t.date >= :from AND t.date < :to AND t.confirmed = 1 AND t.countInTotal = 1 AND $NOT_TRANSFER
          AND (c.showReport IS NULL OR c.showReport = 1)
        GROUP BY w.currency, t.direction
        """
    )
    fun observeTotals(walletId: Long?, from: Long, to: Long): Flow<List<CurrencySum>>

    @Query(
        """
        SELECT t.description AS description, w.currency AS currency, SUM(t.money) AS total, COUNT(*) AS count
        FROM transactions t JOIN wallets w ON w.id = t.walletId
        WHERE $WALLET_SCOPE
          AND t.direction = 0 AND t.date >= :from AND t.date < :to AND t.confirmed = 1 AND t.countInTotal = 1 AND $NOT_TRANSFER
          AND t.description != ''
        GROUP BY LOWER(t.description), w.currency ORDER BY total DESC LIMIT :limit
        """
    )
    fun observeTopDescriptions(walletId: Long?, from: Long, to: Long, limit: Int): Flow<List<DescriptionSum>>

    @Query(
        """
        SELECT w.currency AS currency, SUM(t.money) AS total
        FROM transactions t JOIN budget_wallets bw ON bw.walletId = t.walletId JOIN wallets w ON w.id = t.walletId
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE bw.budgetId = :budgetId AND t.direction = :direction AND t.date >= :from AND t.date < :to
          AND t.confirmed = 1 AND t.countInTotal = 1 AND $NOT_TRANSFER
          AND (:categoryId IS NULL OR t.categoryId = :categoryId OR c.parentId = :categoryId)
        GROUP BY w.currency
        """
    )
    /** Per wallet-currency totals; the caller converts them into the budget currency. */
    fun observeBudgetProgress(budgetId: Long, from: Long, to: Long, direction: Int, categoryId: Long?): Flow<List<CurrencyTotal>>

    // ---- dashboard / analytics / review additions (read-only) ----

    /** Most recent rows for the dashboard (all wallets counted in total when walletId is null). */
    @Query(
        """
        SELECT t.id, t.money, t.currency, t.originalMoney, t.exchangeRate, t.date, t.description, t.categoryId,
               c.name AS categoryName, c.icon AS categoryIcon, t.direction, t.type, t.walletId,
               w.name AS walletName, w.currency AS walletCurrency, t.note, t.confirmed, t.countInTotal, t.transferId
        FROM transactions t JOIN wallets w ON w.id = t.walletId LEFT JOIN categories c ON c.id = t.categoryId
        WHERE (:walletId IS NULL OR t.walletId = :walletId) AND (:walletId IS NOT NULL OR w.archived = 0)
        ORDER BY t.date DESC, t.id DESC LIMIT :limit
        """
    )
    fun observeRecentRows(walletId: Long?, limit: Int): Flow<List<TransactionRow>>

    /** Largest expenses in a window (ordered by raw wallet-currency amount; the caller converts and re-ranks). */
    @Query(
        """
        SELECT t.id, t.money, t.currency, t.originalMoney, t.exchangeRate, t.date, t.description, t.categoryId,
               c.name AS categoryName, c.icon AS categoryIcon, t.direction, t.type, t.walletId,
               w.name AS walletName, w.currency AS walletCurrency, t.note, t.confirmed, t.countInTotal, t.transferId
        FROM transactions t JOIN wallets w ON w.id = t.walletId LEFT JOIN categories c ON c.id = t.categoryId
        WHERE $WALLET_SCOPE
          AND t.direction = 0 AND t.date >= :from AND t.date < :to AND t.confirmed = 1 AND t.countInTotal = 1
          AND t.transferId IS NULL AND (c.showReport IS NULL OR c.showReport = 1)
        ORDER BY t.money DESC LIMIT :limit
        """
    )
    fun observeLargestExpenses(walletId: Long?, from: Long, to: Long, limit: Int): Flow<List<TransactionRow>>

    /** Daily buckets restricted to one top-level category (children folded in). */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', t.date / 1000, 'unixepoch', 'localtime') AS day, w.currency AS currency, t.direction AS direction, SUM(t.money) AS total
        FROM transactions t JOIN wallets w ON w.id = t.walletId LEFT JOIN categories c ON c.id = t.categoryId
        WHERE $WALLET_SCOPE
          AND t.date >= :from AND t.date < :to AND t.confirmed = 1 AND t.countInTotal = 1 AND $NOT_TRANSFER
          AND (t.categoryId = :categoryId OR c.parentId = :categoryId)
        GROUP BY day, w.currency, t.direction ORDER BY day ASC
        """
    )
    fun observeCategoryDailySums(walletId: Long?, from: Long, to: Long, categoryId: Long): Flow<List<DailySum>>

    /** Number of confirmed, counted transactions of one direction in a window. */
    @Query(
        """
        SELECT COUNT(*) FROM transactions t JOIN wallets w ON w.id = t.walletId LEFT JOIN categories c ON c.id = t.categoryId
        WHERE $WALLET_SCOPE
          AND t.direction = :direction AND t.date >= :from AND t.date < :to AND t.confirmed = 1 AND t.countInTotal = 1 AND $NOT_TRANSFER
          AND (c.showReport IS NULL OR c.showReport = 1)
        """
    )
    fun observeTransactionCount(walletId: Long?, from: Long, to: Long, direction: Int): Flow<Int>

    @Query("SELECT DISTINCT t.description FROM transactions t WHERE t.description LIKE :prefix || '%' ORDER BY t.date DESC LIMIT 8")
    suspend fun suggestDescriptions(prefix: String): List<String>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE walletId = :walletId")
    suspend fun countForWallet(walletId: Long): Int

    @Query("SELECT DISTINCT transferId FROM transactions WHERE walletId = :walletId AND transferId IS NOT NULL")
    suspend fun transferIdsForWallet(walletId: Long): List<Long>

    @Insert suspend fun insert(t: TransactionEntity): Long
    @Insert suspend fun insertAll(t: List<TransactionEntity>)
    @Update suspend fun update(t: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("DELETE FROM transactions WHERE transferId = :transferId") suspend fun deleteByTransfer(transferId: Long)
    @Query("DELETE FROM transactions") suspend fun deleteAll()
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers WHERE id = :id") suspend fun byId(id: Long): TransferEntity?
    @Query("SELECT * FROM transfers") suspend fun all(): List<TransferEntity>
    @Insert suspend fun insert(t: TransferEntity): Long
    @Update suspend fun update(t: TransferEntity)
    @Query("DELETE FROM transfers WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("DELETE FROM transfers") suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY endDate DESC")
    fun observeAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets") suspend fun all(): List<BudgetEntity>
    @Query("SELECT * FROM budgets WHERE id = :id") suspend fun byId(id: Long): BudgetEntity?
    @Query("SELECT * FROM budgets WHERE id = :id") fun observeById(id: Long): Flow<BudgetEntity?>
    @Query("SELECT walletId FROM budget_wallets WHERE budgetId = :budgetId") suspend fun walletIds(budgetId: Long): List<Long>
    @Query("SELECT * FROM budget_wallets") suspend fun allBudgetWallets(): List<BudgetWalletEntity>
    @Insert suspend fun insert(b: BudgetEntity): Long
    @Update suspend fun update(b: BudgetEntity)
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteById(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWallets(links: List<BudgetWalletEntity>)
    @Query("DELETE FROM budget_wallets WHERE budgetId = :budgetId") suspend fun deleteWallets(budgetId: Long)
    @Query("DELETE FROM budget_wallets WHERE walletId = :walletId") suspend fun deleteWalletLinks(walletId: Long)
    @Query("DELETE FROM budgets") suspend fun deleteAll()
    @Query("DELETE FROM budget_wallets") suspend fun deleteAllWallets()

    @Transaction
    suspend fun save(b: BudgetEntity, walletIds: List<Long>): Long {
        val id = if (b.id == 0L) insert(b) else { update(b); b.id }
        deleteWallets(id)
        insertWallets(walletIds.map { BudgetWalletEntity(id, it) })
        return id
    }
}

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates") fun observeAll(): Flow<List<ExchangeRateEntity>>
    @Query("SELECT * FROM exchange_rates") suspend fun all(): List<ExchangeRateEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rates: List<ExchangeRateEntity>)
}
