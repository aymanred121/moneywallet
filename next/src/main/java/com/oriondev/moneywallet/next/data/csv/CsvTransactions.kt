package com.oriondev.moneywallet.next.data.csv

import androidx.room.withTransaction
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.TransactionEntity
import com.oriondev.moneywallet.next.data.db.TransactionType
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import java.io.OutputStream
import java.math.BigDecimal
import java.util.UUID

/** Moves transactions between the database and [TransactionCsv] spreadsheets. */
class CsvTransactions(private val db: WalletDatabase) {

    data class Report(val imported: Int, val duplicates: Int, val categoriesCreated: Int, val problems: List<String>)

    /** Every transaction, oldest first, read inside one transaction so the file is a consistent snapshot. */
    suspend fun export(output: OutputStream) {
        val rows = db.withTransaction {
            val wallets = db.wallets().all().associateBy { it.id }
            val categories = db.categories().all().associateBy { it.id }
            val decimals = db.currencies().all().associate { it.iso to it.decimals }
            db.transactions().all().sortedBy { it.date }.mapNotNull { t ->
                val w = wallets[t.walletId] ?: return@mapNotNull null
                val c = t.categoryId?.let(categories::get)
                val type = when {
                    t.transferId != null || t.type == TransactionType.TRANSFER -> TransactionCsv.Type.TRANSFER
                    t.direction == Direction.EXPENSE -> TransactionCsv.Type.EXPENSE
                    else -> TransactionCsv.Type.INCOME
                }
                val amount = BigDecimal(t.money).movePointLeft(decimals[w.currency] ?: 2)
                TransactionCsv.Row(
                    date = Dates.toLocalDateTime(t.date), wallet = w.name, category = c?.name, parentCategory = c?.parentId?.let(categories::get)?.name,
                    description = t.description, note = t.note, type = type, amount = if (t.direction == Direction.EXPENSE) amount.negate() else amount, currency = w.currency,
                    originalAmount = t.originalMoney?.let { BigDecimal(it).movePointLeft(decimals[t.currency] ?: 2) }, originalCurrency = t.currency, rate = t.exchangeRate,
                )
            }
        }
        output.write(TransactionCsv.export(rows).toByteArray(Charsets.UTF_8))
    }

    /**
     * Adds [rows] to [walletId] (our own exports go back to the wallet named in the row when one
     * matches). Categories are matched by name and created when missing. A row is a duplicate when
     * the wallet already holds a transaction on the same day with the same amount, direction and
     * description; rows repeated inside the file are kept (two coffees on one day are real).
     */
    suspend fun import(rows: List<TransactionCsv.Parsed>, walletId: Long, parseProblems: List<String> = emptyList()): Report = db.withTransaction {
        val fallback = db.wallets().byId(walletId) ?: error("The selected wallet no longer exists")
        val byName = db.wallets().all().associateBy { it.name.trim().lowercase() }
        val decimals = db.currencies().all().associate { it.iso to it.decimals }
        val categories = db.categories().all().filter { it.type != CategoryType.SYSTEM }.toMutableList()
        val existing = db.transactions().all().mapTo(HashSet()) { key(it.walletId, it.date, it.money, it.direction, it.description) }
        val problems = LinkedHashMap<String, Int>()
        fun problem(what: String) { problems[what] = (problems[what] ?: 0) + 1 }
        var created = 0
        suspend fun category(name: String?, parent: String?, type: Int): Long? {
            val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val parentId = parent?.let { category(it, null, type) }
            categories.firstOrNull { it.type == type && it.name.equals(n, ignoreCase = true) && (parent == null || it.parentId == parentId) }?.let { return it.id }
            val c = CategoryEntity(uuid = UUID.randomUUID().toString(), name = n, icon = IconSpec.letterFor(n, IconSpec.palette[categories.size % IconSpec.palette.size]).encode(), type = type, parentId = parentId, sortIndex = categories.size)
            return db.categories().insert(c).also { categories += c.copy(id = it); created++ }
        }
        var imported = 0; var duplicates = 0
        for (r in rows) {
            if (r.transfer) { problem("transfers (restore a .mwbx backup to keep them)"); continue }
            val wallet = r.wallet?.let { byName[it.trim().lowercase()] } ?: fallback
            if (r.currency != null && r.currency != wallet.currency) { problem("rows in a currency other than the wallet's"); continue }
            val money = TransactionCsv.toMinor(r.amount.abs(), decimals[wallet.currency] ?: 2)
            val direction = if (r.expense) Direction.EXPENSE else Direction.INCOME
            val date = Dates.toMillis(r.date)
            if (key(wallet.id, date, money, direction, r.description) in existing) { duplicates++; continue }
            val foreign = r.originalCurrency != null && r.originalAmount != null && r.originalCurrency != wallet.currency
            db.transactions().insert(
                TransactionEntity(
                    uuid = UUID.randomUUID().toString(), money = money, date = date, description = r.description.trim(),
                    categoryId = category(r.category, r.parentCategory, if (r.expense) CategoryType.EXPENSE else CategoryType.INCOME),
                    direction = direction, walletId = wallet.id, note = r.note?.trim()?.ifEmpty { null },
                    currency = if (foreign) r.originalCurrency else null, originalMoney = if (foreign) TransactionCsv.toMinor(r.originalAmount!!, decimals[r.originalCurrency] ?: 2) else null,
                    exchangeRate = if (foreign) r.rate else null,
                ),
            )
            imported++
        }
        Report(imported, duplicates, created, parseProblems + problems.map { (what, n) -> "$n $what" })
    }

    private fun key(walletId: Long, date: Long, money: Long, direction: Int, description: String) =
        "$walletId|${Dates.toLocalDate(date)}|$money|$direction|${description.trim().lowercase()}"
}
