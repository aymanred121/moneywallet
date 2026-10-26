package com.oriondev.moneywallet.next.data.currency

import android.content.Context
import com.oriondev.moneywallet.next.data.db.CurrencyDao
import com.oriondev.moneywallet.next.data.db.CurrencyEntity
import org.json.JSONArray

/**
 * Loads the bundled ISO-4217 list (same asset as the legacy app). Runs on every start but only
 * inserts codes the table does not have yet, so user edits (favourites, symbols) are kept and
 * currencies added to the asset in later versions still appear.
 */
object CurrencySeed {
    suspend fun ensureSeeded(context: Context, dao: CurrencyDao) {
        val existing = dao.all().mapTo(HashSet()) { it.iso }
        val firstRun = existing.isEmpty()
        val json = context.assets.open("currencies.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val favourites = setOf("USD", "EUR", "SAR", "GBP", "AED", "EGP")
        val items = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val iso = o.getString("code")
            CurrencyEntity(
                iso = iso,
                name = o.getString("name"),
                symbol = com.oriondev.moneywallet.next.core.CurrencySymbols.preferred(iso, o.optString("symbol", iso)),
                decimals = o.optInt("decimals", 2),
                favourite = firstRun && iso in favourites,
            )
        }.filter { it.iso !in existing }
        if (items.isNotEmpty()) dao.upsertAll(items)
    }
}
