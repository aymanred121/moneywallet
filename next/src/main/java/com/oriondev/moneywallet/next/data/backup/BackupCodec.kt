package com.oriondev.moneywallet.next.data.backup

import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.BudgetEntity
import com.oriondev.moneywallet.next.data.db.BudgetWalletEntity
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.CurrencyEntity
import com.oriondev.moneywallet.next.data.db.GoalEntity
import com.oriondev.moneywallet.next.data.db.TransactionEntity
import com.oriondev.moneywallet.next.data.db.TransactionType
import com.oriondev.moneywallet.next.data.db.TransferEntity
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import com.oriondev.moneywallet.next.data.db.WalletEntity
import com.oriondev.moneywallet.next.data.prefs.UserPreferences
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes the legacy `.mwbx` backup (a zip holding databases/database.json, format
 * versions 1-3). Debts, events, places, people, recurrences and models are skipped on import and
 * written as empty arrays on export so the legacy app keeps accepting our files. Legacy savings
 * become manual goals; our goals travel in an extra "next_goals" array.
 *
 * The legacy importer is a streaming reader that expects every top-level key in one fixed order
 * ([LEGACY_ORDER]) and fails on anything else; it stops reading after the last one, so extra keys
 * are only safe at the very end. The export therefore writes keys in that order by hand instead of
 * relying on a JSONObject's iteration order.
 */
class BackupCodec(private val db: WalletDatabase, private val prefs: UserPreferences? = null) {

    /**
     * [skipped] names legacy sections this app does not import; [problems] lists rows that were
     * dropped because they could not be read (for example a transaction whose wallet is missing).
     */
    data class ImportReport(
        val wallets: Int, val categories: Int, val transactions: Int, val transfers: Int, val budgets: Int,
        val skipped: List<String>, val problems: List<String> = emptyList(), val goals: Int = 0,
    )

    suspend fun import(input: InputStream, replace: Boolean = true): ImportReport {
        val json = readDatabaseJson(input) ?: error("The file does not contain databases/database.json")
        val root = JSONObject(json)
        val version = root.optJSONObject("header")?.optInt("version_code", 1) ?: 1
        if (version > 3) error("Backup version $version is newer than this app supports")
        val report = db.withTransaction { importInTransaction(root, replace) }
        // ids are regenerated on restore: a remembered wallet filter would point at nothing
        if (replace) prefs?.setSelectedWallet(null)
        return report
    }

    private suspend fun importInTransaction(root: JSONObject, replace: Boolean): ImportReport {
        val problems = ProblemCounter()
        run {
            if (replace) {
                db.goals().deleteAll(); db.transactions().deleteAll(); db.transfers().deleteAll(); db.budgets().deleteAllWallets(); db.budgets().deleteAll()
                db.categories().deleteAll(); db.wallets().deleteAll()
            }
            // currencies (merge, never delete)
            root.optJSONArray("currencies")?.let { arr ->
                // keep favourites already set on this device: legacy backups rarely carry them
                val existing = db.currencies().all().associateBy { it.iso }
                val items = arr.objects().filter { !it.optBoolean("deleted") }.map {
                    val iso = it.getString("iso")
                    CurrencyEntity(iso, it.optString("name", iso), com.oriondev.moneywallet.next.core.CurrencySymbols.preferred(iso, it.optString("symbol", iso)), it.optInt("decimals", 2), it.optBoolean("favourite") || (existing[iso]?.favourite ?: false))
                }
                db.currencies().upsertAll(items)
            }
            val walletIds = HashMap<String, Long>()
            val walletCurrency = HashMap<String, String>()
            var walletCount = 0
            root.optJSONArray("wallets")?.objects()?.filter { !it.optBoolean("deleted") }?.forEach { o ->
                val key = o.optStringOrNull("id")
                val id = db.wallets().insert(
                    WalletEntity(
                        uuid = key ?: UUID.randomUUID().toString(), name = o.optString("name"), icon = o.optString("icon"),
                        currency = o.optString("currency", "USD"), startMoney = o.optLong("start_money"), countInTotal = o.optBoolean("count_in_total", true),
                        archived = o.optBoolean("archived"), note = o.optStringOrNull("note"), sortIndex = o.optInt("index"), lastEdit = o.optLong("last_edit", System.currentTimeMillis()),
                    )
                )
                // a wallet without an id cannot be referenced by any row, and must not collide with others under ""
                if (key != null) { walletIds[key] = id; walletCurrency[key] = o.optString("currency", "USD") }
                walletCount++
            }
            val categoryIds = HashMap<String, Long>()
            val categories = root.optJSONArray("categories")?.objects()?.filter { !it.optBoolean("deleted") } ?: emptyList()
            // parents first, then children
            val (parents, children) = categories.partition { it.optStringOrNull("parent") == null }
            (parents + children).forEach { o ->
                val parentId = o.optStringOrNull("parent")?.let { categoryIds[it] }
                val id = db.categories().insert(
                    CategoryEntity(
                        uuid = o.optString("id", UUID.randomUUID().toString()), name = o.optString("name"), icon = o.optString("icon"),
                        type = o.optInt("type", CategoryType.EXPENSE), parentId = parentId, showReport = o.optBoolean("show_report", true),
                        sortIndex = o.optInt("index"), tag = o.optStringOrNull("tag"), lastEdit = o.optLong("last_edit", System.currentTimeMillis()),
                    )
                )
                categoryIds[o.optString("id")] = id
            }
            val txIds = HashMap<String, Long>()
            var txCount = 0
            root.optJSONArray("transactions")?.objects()?.filter { !it.optBoolean("deleted") }?.forEach { o ->
                val walletId = walletIds[o.optString("wallet")] ?: return@forEach problems.add("transactions whose wallet is missing")
                // never move an undated transaction into "today": it would distort this month's totals
                val date = Dates.parseSqlDateTime(o.optString("date")) ?: return@forEach problems.add("transactions with an unreadable date")
                val id = db.transactions().insert(
                    TransactionEntity(
                        uuid = o.optString("id", UUID.randomUUID().toString()), money = o.optLong("money"),
                        currency = o.optStringOrNull("currency"),
                        originalMoney = if (o.has("original_money")) o.optLong("original_money") else null,
                        exchangeRate = if (o.has("exchange_rate")) o.optDouble("exchange_rate") else null,
                        date = date,
                        description = o.optString("description"), categoryId = categoryIds[o.optString("category")],
                        direction = o.optInt("direction"), type = o.optInt("type", TransactionType.STANDARD), walletId = walletId,
                        note = o.optStringOrNull("note"), confirmed = o.optBoolean("confirmed", true), countInTotal = o.optBoolean("count_in_total", true),
                        tag = o.optStringOrNull("tag"), lastEdit = o.optLong("last_edit", System.currentTimeMillis()),
                    )
                )
                txIds[o.optString("id")] = id
                txCount++
            }
            var transferCount = 0
            root.optJSONArray("transfers")?.objects()?.filter { !it.optBoolean("deleted") }?.forEach { o ->
                val from = txIds[o.optString("from")] ?: return@forEach problems.add("transfers with a missing side")
                val to = txIds[o.optString("to")] ?: return@forEach problems.add("transfers with a missing side")
                val tax = o.optStringOrNull("tax")?.let { txIds[it] }
                val id = db.transfers().insert(
                    TransferEntity(
                        uuid = o.optString("id", UUID.randomUUID().toString()), description = o.optString("description"),
                        date = Dates.parseSqlDateTime(o.optString("date")) ?: db.transactions().byId(from)!!.date, fromTransactionId = from, toTransactionId = to,
                        taxTransactionId = tax, note = o.optStringOrNull("note"), confirmed = o.optBoolean("confirmed", true), countInTotal = o.optBoolean("count_in_total", true),
                    )
                )
                listOfNotNull(from, to, tax).forEach { txId ->
                    db.transactions().byId(txId)?.let { db.transactions().update(it.copy(transferId = id, type = TransactionType.TRANSFER)) }
                }
                transferCount++
            }
            val budgetIds = HashMap<String, Long>()
            root.optJSONArray("budgets")?.objects()?.filter { !it.optBoolean("deleted") }?.forEach { o ->
                val start = Dates.parseSqlDate(o.optString("start_date")) ?: return@forEach problems.add("budgets with unreadable dates")
                val end = Dates.parseSqlDate(o.optString("end_date")) ?: return@forEach problems.add("budgets with unreadable dates")
                val id = db.budgets().insert(
                    BudgetEntity(
                        uuid = o.optString("id", UUID.randomUUID().toString()), type = o.optInt("type"), categoryId = o.optStringOrNull("category")?.let { categoryIds[it] },
                        startDate = start,
                        endDate = end,
                        money = o.optLong("money"), currency = o.optString("currency", "USD"), lastEdit = o.optLong("last_edit", System.currentTimeMillis()),
                    )
                )
                budgetIds[o.optString("id")] = id
            }
            val links = root.optJSONArray("budget_wallets")?.objects()?.filter { !it.optBoolean("deleted") }?.mapNotNull { o ->
                val b = budgetIds[o.optString("budget")] ?: return@mapNotNull null
                val w = walletIds[o.optString("wallet")] ?: return@mapNotNull null
                BudgetWalletEntity(b, w)
            } ?: emptyList()
            if (links.isNotEmpty()) db.budgets().insertWallets(links)
            val goals = ArrayList<GoalEntity>()
            val now = System.currentTimeMillis()
            // legacy savings become manual goals; their deposits are ordinary transactions already imported above
            root.optJSONArray("savings")?.objects()?.filter { !it.optBoolean("deleted") }?.forEachIndexed { i, o ->
                val currency = walletCurrency[o.optString("wallet")] ?: return@forEachIndexed problems.add("savings whose wallet is missing")
                goals += GoalEntity(
                    uuid = o.optStringOrNull("id") ?: UUID.randomUUID().toString(), name = o.optString("description"), icon = o.optString("icon"),
                    targetMoney = o.optLong("end_money"), currency = currency, walletId = null, savedMoney = o.optLong("start_money"),
                    deadline = o.optStringOrNull("end_date")?.let { Dates.parseSqlDate(it) ?: Dates.parseSqlDateTime(it) },
                    archived = o.optBoolean("complete"), sortIndex = i, lastEdit = o.optLong("last_edit", now),
                )
            }
            root.optJSONArray("next_goals")?.objects()?.forEach { o ->
                val currency = o.optStringOrNull("currency") ?: return@forEach problems.add("goals without a currency")
                goals += GoalEntity(
                    uuid = o.optStringOrNull("id") ?: UUID.randomUUID().toString(), name = o.optString("name"), icon = o.optString("icon"),
                    targetMoney = o.optLong("target_money"), currency = currency, walletId = o.optStringOrNull("wallet")?.let { walletIds[it] },
                    savedMoney = o.optLong("saved_money"), deadline = o.optStringOrNull("deadline")?.let(Dates::parseSqlDate),
                    archived = o.optBoolean("archived"), sortIndex = o.optInt("index"), lastEdit = o.optLong("last_edit", now),
                )
            }
            val knownGoals = if (replace) emptySet() else db.goals().all().map { it.uuid }.toSet()
            val newGoals = goals.filter { it.uuid !in knownGoals }.distinctBy { it.uuid }
            if (newGoals.isNotEmpty()) db.goals().insertAll(newGoals)
            val skipped = listOf("debts", "events", "places", "people", "recurrent_transactions", "recurrent_transfers", "transaction_models", "transfer_models", "attachments")
                .filter { (root.optJSONArray(it)?.length() ?: 0) > 0 }
            return ImportReport(walletCount, categoryIds.size, txCount, transferCount, budgetIds.size, skipped, problems.lines(), newGoals.size)
        }
    }

    private class ProblemCounter {
        private val counts = LinkedHashMap<String, Int>()
        fun add(what: String) { counts[what] = (counts[what] ?: 0) + 1 }
        fun lines(): List<String> = counts.map { (what, n) -> "$n $what" }
    }

    /** Reads everything inside one transaction so a concurrent write cannot produce a torn snapshot. */
    suspend fun export(output: OutputStream) {
        val sections = db.withTransaction { buildExport() }
        val json = buildString {
            append('{')
            (LEGACY_ORDER + GOALS_KEY).forEachIndexed { i, key ->
                if (i > 0) append(',')
                append(JSONObject.quote(key)).append(':').append(sections[key] ?: JSONArray())
            }
            append('}')
        }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("databases/database.json"))
            zip.write(json.toByteArray())
            zip.closeEntry()
        }
    }

    /** Sections by key; sections the legacy app expects but we do not keep are written as empty arrays by [export]. */
    private suspend fun buildExport(): Map<String, Any> {
        val root = HashMap<String, Any>()
        root.put("header", JSONObject().put("version_code", 3))
        root.put("currencies", JSONArray().apply {
            db.currencies().all().forEach { c ->
                put(JSONObject().put("iso", c.iso).put("name", c.name).put("symbol", c.symbol).put("decimals", c.decimals).put("favourite", c.favourite).put("last_edit", c.lastEdit).put("deleted", false))
            }
        })
        val wallets = db.wallets().all()
        val walletUuid = wallets.associate { it.id to it.uuid }
        root.put("wallets", JSONArray().apply {
            wallets.forEach { w ->
                put(JSONObject().put("name", w.name).put("icon", w.icon).put("currency", w.currency).put("start_money", w.startMoney).put("count_in_total", w.countInTotal)
                    .put("archived", w.archived).putOpt("note", w.note).put("id", w.uuid).put("index", w.sortIndex).put("last_edit", w.lastEdit).put("deleted", false))
            }
        })
        val categories = db.categories().all()
        val categoryUuid = categories.associate { it.id to it.uuid }
        root.put("categories", JSONArray().apply {
            categories.forEach { c ->
                put(JSONObject().put("name", c.name).put("icon", c.icon).put("type", c.type).putOpt("parent", c.parentId?.let { categoryUuid[it] }).putOpt("tag", c.tag)
                    .put("show_report", c.showReport).put("index", c.sortIndex).put("id", c.uuid).put("last_edit", c.lastEdit).put("deleted", false))
            }
        })
        val budgets = db.budgets().all()
        val budgetUuid = budgets.associate { it.id to it.uuid }
        root.put("budgets", JSONArray().apply {
            budgets.forEach { b ->
                put(JSONObject().put("type", b.type).putOpt("category", b.categoryId?.let { categoryUuid[it] }).put("start_date", Dates.formatSqlDate(b.startDate))
                    .put("end_date", Dates.formatSqlDate(b.endDate)).put("money", b.money).put("currency", b.currency).put("id", b.uuid).put("last_edit", b.lastEdit).put("deleted", false))
            }
        })
        root.put("budget_wallets", JSONArray().apply {
            db.budgets().allBudgetWallets().forEach { l ->
                val b = budgetUuid[l.budgetId] ?: return@forEach
                val w = walletUuid[l.walletId] ?: return@forEach
                put(JSONObject().put("budget", b).put("wallet", w).put("id", UUID.nameUUIDFromBytes("$b:$w".toByteArray()).toString()).put("last_edit", System.currentTimeMillis()).put("deleted", false))
            }
        })
        val transactions = db.transactions().all()
        val txUuid = transactions.associate { it.id to it.uuid }
        root.put("transactions", JSONArray().apply {
            transactions.forEach { t ->
                val o = JSONObject().put("money", t.money).put("date", Dates.formatSqlDateTime(t.date)).put("description", t.description)
                    .putOpt("category", t.categoryId?.let { categoryUuid[it] }).put("direction", t.direction).put("type", t.type)
                    .put("wallet", walletUuid[t.walletId]).putOpt("note", t.note).put("confirmed", t.confirmed).put("count_in_total", t.countInTotal)
                    .putOpt("tag", t.tag).put("id", t.uuid).put("last_edit", t.lastEdit).put("deleted", false)
                if (t.currency != null) o.put("currency", t.currency)
                if (t.originalMoney != null) o.put("original_money", t.originalMoney)
                if (t.exchangeRate != null) o.put("exchange_rate", t.exchangeRate)
                put(o)
            }
        })
        root.put("transfers", JSONArray().apply {
            db.transfers().all().forEach { tr ->
                put(JSONObject().put("description", tr.description).put("date", Dates.formatSqlDateTime(tr.date)).put("from", txUuid[tr.fromTransactionId]).put("to", txUuid[tr.toTransactionId])
                    .putOpt("tax", tr.taxTransactionId?.let { txUuid[it] }).putOpt("note", tr.note).put("confirmed", tr.confirmed).put("count_in_total", tr.countInTotal)
                    .put("id", tr.uuid).put("last_edit", tr.lastEdit).put("deleted", false))
            }
        })
        root.put(GOALS_KEY, JSONArray().apply {
            db.goals().all().forEach { g ->
                put(JSONObject().put("id", g.uuid).put("name", g.name).put("icon", g.icon).put("target_money", g.targetMoney).put("currency", g.currency)
                    .putOpt("wallet", g.walletId?.let { walletUuid[it] }).put("saved_money", g.savedMoney).putOpt("deadline", g.deadline?.let(Dates::formatSqlDate))
                    .put("archived", g.archived).put("index", g.sortIndex).put("last_edit", g.lastEdit))
            }
        })
        return root
    }

    companion object {
        /** Top-level keys in the exact order the legacy JSONDatabaseImporter reads them. */
        val LEGACY_ORDER = listOf(
            "header", "currencies", "wallets", "categories", "events", "places", "people", "event_people", "debts", "debt_people", "budgets", "budget_wallets",
            "savings", "recurrent_transactions", "recurrent_transfers", "transactions", "transaction_people", "transaction_models", "transfers", "transfer_people",
            "transfer_models", "attachments", "transaction_attachment", "transfer_attachment",
        )
        /** Written after [LEGACY_ORDER], where the legacy reader has already stopped. */
        const val GOALS_KEY = "next_goals"
    }

    private fun readDatabaseJson(input: InputStream): String? {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith("database.json")) return zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
}
