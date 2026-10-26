package com.oriondev.moneywallet.next.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY archived ASC, sortIndex ASC, name ASC")
    fun observeAll(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goals") suspend fun all(): List<GoalEntity>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun byId(id: Long): GoalEntity?
    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM goals") suspend fun maxSortIndex(): Int
    /** Manual progress change; never drops below zero. */
    @Query("UPDATE goals SET savedMoney = MAX(0, savedMoney + :delta), lastEdit = :now WHERE id = :id")
    suspend fun adjustSaved(id: Long, delta: Long, now: Long = System.currentTimeMillis())
    @Query("UPDATE goals SET archived = :archived, lastEdit = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long = System.currentTimeMillis())
    @Insert suspend fun insert(g: GoalEntity): Long
    @Insert suspend fun insertAll(g: List<GoalEntity>)
    @Update suspend fun update(g: GoalEntity)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("UPDATE goals SET walletId = NULL WHERE walletId = :walletId") suspend fun unlinkWallet(walletId: Long)
    @Query("DELETE FROM goals") suspend fun deleteAll()
}

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures WHERE status = 0 ORDER BY postedAt DESC")
    fun observePending(): Flow<List<CaptureEntity>>
    @Query("SELECT COUNT(*) FROM captures WHERE status = 0")
    fun observePendingCount(): Flow<Int>
    @Query("SELECT * FROM captures WHERE id = :id") suspend fun byId(id: Long): CaptureEntity?
    /** Returns -1 when the same notification was already captured. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(c: CaptureEntity): Long
    @Query("UPDATE captures SET status = :status, transactionId = :transactionId WHERE id = :id")
    suspend fun setStatus(id: Long, status: Int, transactionId: Long? = null)
    /** Keep the table small: resolved captures older than [before] are dropped. */
    @Query("DELETE FROM captures WHERE status != 0 AND postedAt < :before") suspend fun pruneResolved(before: Long)
    /** Same amount and merchant from the same app since [since]: a bank and its wallet echoing one payment. */
    @Query("SELECT COUNT(*) FROM captures WHERE packageName = :packageName AND amount = :amount AND IFNULL(merchant, '') = :merchant AND direction = :direction AND postedAt >= :since")
    suspend fun countSimilarSince(packageName: String, amount: Long, merchant: String, direction: Int, since: Long): Int
    /** Category of the latest transaction with this description (case-insensitive), to pre-fill the editor. */
    @Query(
        """
        SELECT t.categoryId FROM transactions t JOIN categories c ON c.id = t.categoryId
        WHERE t.description = :description COLLATE NOCASE ORDER BY t.date DESC LIMIT 1
        """
    )
    suspend fun lastCategoryFor(description: String): Long?

    @Query("UPDATE captures SET status = 2 WHERE status = 0") suspend fun dismissAllPending(): Int

    /**
     * Transactions that already record this payment: same direction and amount (typed in
     * [currency], or stored in a wallet of that currency) inside [from, to). Used to skip bank
     * SMS the user entered by hand before importing.
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions t JOIN wallets w ON w.id = t.walletId
        WHERE t.date >= :from AND t.date < :to AND t.direction = :direction
          AND ((t.currency = :currency AND t.originalMoney = :amount)
            OR (IFNULL(t.currency, w.currency) = w.currency AND w.currency = :currency AND t.money = :amount))
        """
    )
    suspend fun countMatchingTransactions(amount: Long, currency: String, direction: Int, from: Long, to: Long): Int
}
