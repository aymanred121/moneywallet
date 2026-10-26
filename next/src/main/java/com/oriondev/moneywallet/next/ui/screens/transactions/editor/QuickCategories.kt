package com.oriondev.moneywallet.next.ui.screens.transactions.editor

import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryUse
import kotlin.math.pow

/** Ranking for the editor's quick-category chips: frequent and recent categories first. */
object QuickCategories {
    const val WINDOW_DAYS = 60
    const val HALF_LIFE_DAYS = 14.0
    const val LIMIT = 5
    private const val DAY_MS = 86_400_000L

    fun since(now: Long) = now - WINDOW_DAYS * DAY_MS

    /**
     * Scores each use by 0.5^(age / [HALF_LIFE_DAYS]) and sums per category, so a category used
     * twice this week beats one used three times last month. Only existing, non-system categories
     * of [type] count; the rest of [limit] is filled with the first top-level categories of [type].
     */
    fun rank(uses: List<CategoryUse>, categories: List<CategoryEntity>, type: Int, now: Long, limit: Int = LIMIT): List<CategoryEntity> {
        val eligible = categories.filter { it.type == type && it.tag?.startsWith("system::") != true }
        val byId = eligible.associateBy { it.id }
        val scores = HashMap<Long, Double>()
        for (u in uses) {
            if (u.categoryId !in byId) continue
            val ageDays = (now - u.date).coerceAtLeast(0L).toDouble() / DAY_MS
            scores[u.categoryId] = (scores[u.categoryId] ?: 0.0) + 0.5.pow(ageDays / HALF_LIFE_DAYS)
        }
        val ranked = scores.entries.sortedByDescending { it.value }.mapNotNull { byId[it.key] }
        val fallback = eligible.filter { it.parentId == null && it.id !in scores }
        return (ranked + fallback).take(limit)
    }

    /** Chips to show: the ranked ones, with a [selected] category outside them moved to the front. */
    fun withSelected(ranked: List<CategoryEntity>, selected: CategoryEntity?, limit: Int = LIMIT): List<CategoryEntity> =
        if (selected == null || ranked.any { it.id == selected.id }) ranked else (listOf(selected) + ranked).take(limit)
}
