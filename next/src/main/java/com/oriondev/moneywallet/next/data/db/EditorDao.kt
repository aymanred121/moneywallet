package com.oriondev.moneywallet.next.data.db

import androidx.room.Dao
import androidx.room.Query

/** One categorised transaction, for ranking the editor's quick-category chips. */
data class CategoryUse(val categoryId: Long, val date: Long)

/** Read-only lookups for the transaction editor (quick add and auto-categorise). */
@Dao
interface EditorDao {
    /** Categorised, non-transfer transactions of [direction] since [since], newest first. */
    @Query(
        "SELECT categoryId, date FROM transactions WHERE direction = :direction AND categoryId IS NOT NULL " +
            "AND transferId IS NULL AND date >= :since ORDER BY date DESC LIMIT 500",
    )
    suspend fun categoryUses(direction: Int, since: Long): List<CategoryUse>

    /** Category of the most recent [direction] transaction whose description equals [description], ignoring case. */
    @Query(
        "SELECT categoryId FROM transactions WHERE direction = :direction AND categoryId IS NOT NULL AND transferId IS NULL " +
            "AND description = :description COLLATE NOCASE ORDER BY date DESC, id DESC LIMIT 1",
    )
    suspend fun lastCategoryFor(description: String, direction: Int): Long?
}
