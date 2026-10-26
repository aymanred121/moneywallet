package com.oriondev.moneywallet.next.data.repo

import com.oriondev.moneywallet.next.core.Convert
import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.TransactionEntity
import com.oriondev.moneywallet.next.data.db.TransactionType
import com.oriondev.moneywallet.next.data.db.TransferEntity
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import com.oriondev.moneywallet.next.data.db.WalletEntity
import com.oriondev.moneywallet.next.data.prefs.UserPreferences
import androidx.room.withTransaction
import com.oriondev.moneywallet.next.core.Clock
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Currency table + USD rates + base currency, everything needed to convert and format. */
data class CurrencyContext(
    val currencies: Map<String, Currency>,
    val usdRates: Map<String, Double>,
    val baseIso: String,
) {
    val base: Currency get() = currencies[baseIso] ?: Currency(baseIso, baseIso, baseIso)
    fun currency(iso: String): Currency = currencies[iso] ?: Currency(iso, iso, iso)
    fun rate(from: String, to: String): Double? = Convert.crossRate(usdRates, from, to)
    fun convert(minor: Long, from: String, to: String): Long? {
        if (from == to) return minor
        val r = rate(from, to) ?: return null
        return Convert.minor(currency(from), currency(to), minor, r)
    }
    /** Collapse a bag into the base currency; null when a rate is missing. */
    fun toBase(bag: MoneyBag): Long? {
        var total = 0L
        for ((iso, minor) in bag.entries) total += convert(minor, iso, baseIso) ?: return null
        return total
    }
    fun toBaseOrNull(minor: Long, from: String): Long? = convert(minor, from, baseIso)
}

class WalletRepository(val db: WalletDatabase, val prefs: UserPreferences) {

    val currencyContext: Flow<CurrencyContext> = combine(
        db.currencies().observeAll(), db.rates().observeAll(), prefs.settings,
    ) { currencies, rates, settings ->
        val map = currencies.associate { it.iso to Currency(it.iso, it.name, it.symbol, it.decimals) }
        val base = settings.baseCurrency ?: java.util.Currency.getInstance(java.util.Locale.getDefault()).currencyCode.takeIf { map.containsKey(it) } ?: "USD"
        CurrencyContext(map, rates.associate { it.iso to it.rate }, base)
    }

    /**
     * Wallets with their balance as of today: every transaction dated today counts (whatever its
     * time), future-dated ones do not. The cut-off moves forward at midnight.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun wallets(): Flow<List<WalletWithBalance>> =
        Clock.today.flatMapLatest { db.wallets().observeWithBalance(Dates.startOfTomorrow() - 1) }
    val categories: Flow<List<CategoryEntity>> = db.categories().observeAll()

    // ---------------- transactions ----------------

    data class TransactionDraft(
        val id: Long? = null,
        val walletId: Long,
        val walletCurrency: String,
        val direction: Int,
        val categoryId: Long?,
        /** Amount as typed, in [inputCurrency]. */
        val inputMinor: Long,
        val inputCurrency: String,
        val rate: Double?, // 1 inputCurrency = rate walletCurrency, null when same currency
        val date: Long,
        val description: String,
        val note: String?,
        val confirmed: Boolean = true,
        val countInTotal: Boolean = true,
    )

    suspend fun saveTransaction(draft: TransactionDraft, ctx: CurrencyContext): Long {
        val foreign = draft.inputCurrency != draft.walletCurrency
        val rate = if (foreign) (draft.rate ?: ctx.rate(draft.inputCurrency, draft.walletCurrency) ?: error("missing rate")) else null
        val money = if (foreign) Convert.minor(ctx.currency(draft.inputCurrency), ctx.currency(draft.walletCurrency), draft.inputMinor, rate!!) else draft.inputMinor
        val existing = draft.id?.let { db.transactions().byId(it) }
        val entity = TransactionEntity(
            id = existing?.id ?: 0, uuid = existing?.uuid ?: UUID.randomUUID().toString(), money = money,
            currency = if (foreign) draft.inputCurrency else null, originalMoney = if (foreign) draft.inputMinor else null, exchangeRate = rate,
            date = draft.date, description = draft.description.trim(), categoryId = draft.categoryId, direction = draft.direction,
            type = existing?.type ?: TransactionType.STANDARD, walletId = draft.walletId, note = draft.note?.trim()?.ifEmpty { null },
            confirmed = draft.confirmed, countInTotal = draft.countInTotal, transferId = existing?.transferId, lastEdit = System.currentTimeMillis(),
        )
        return if (existing == null) db.transactions().insert(entity) else { db.transactions().update(entity); entity.id }
    }

    suspend fun deleteTransaction(id: Long) {
        val t = db.transactions().byId(id) ?: return
        if (t.transferId != null) deleteTransfer(t.transferId) else db.transactions().deleteById(id)
    }

    data class TransferDraft(
        val fromWalletId: Long, val toWalletId: Long, val fromMinor: Long, val toMinor: Long, val taxMinor: Long = 0,
        val date: Long, val description: String, val note: String?,
    )

    suspend fun saveTransfer(draft: TransferDraft): Long = db.withTransaction {
        val transferCat = db.categories().byTag("system::transfer")
        val taxCat = db.categories().byTag("system::transfer_tax")
        val now = System.currentTimeMillis()
        val from = db.transactions().insert(TransactionEntity(uuid = UUID.randomUUID().toString(), money = draft.fromMinor, date = draft.date, description = draft.description,
            categoryId = transferCat?.id, direction = Direction.EXPENSE, type = TransactionType.TRANSFER, walletId = draft.fromWalletId, note = draft.note, lastEdit = now))
        val to = db.transactions().insert(TransactionEntity(uuid = UUID.randomUUID().toString(), money = draft.toMinor, date = draft.date, description = draft.description,
            categoryId = transferCat?.id, direction = Direction.INCOME, type = TransactionType.TRANSFER, walletId = draft.toWalletId, note = draft.note, lastEdit = now))
        val tax = if (draft.taxMinor > 0) db.transactions().insert(TransactionEntity(uuid = UUID.randomUUID().toString(), money = draft.taxMinor, date = draft.date, description = draft.description,
            categoryId = taxCat?.id, direction = Direction.EXPENSE, type = TransactionType.TRANSFER, walletId = draft.fromWalletId, note = draft.note, lastEdit = now)) else null
        val id = db.transfers().insert(TransferEntity(uuid = UUID.randomUUID().toString(), description = draft.description, date = draft.date, fromTransactionId = from, toTransactionId = to, taxTransactionId = tax, note = draft.note, lastEdit = now))
        listOfNotNull(from, to, tax).forEach { txId -> db.transactions().byId(txId)?.let { db.transactions().update(it.copy(transferId = id)) } }
        id
    }

    suspend fun deleteTransfer(id: Long) = db.withTransaction {
        db.transactions().deleteByTransfer(id)
        db.transfers().deleteById(id)
    }

    // ---------------- wallets / categories ----------------

    suspend fun saveWallet(w: WalletEntity): Long = if (w.id == 0L) db.wallets().insert(w) else { db.wallets().update(w); w.id }
    /**
     * Deletes the wallet and everything that would otherwise dangle: both legs (and the fee) of
     * every transfer touching it, its budget links and goal links. Its own transactions go with
     * the wallet through the foreign key cascade.
     */
    suspend fun deleteWallet(w: WalletEntity) = db.withTransaction {
        val transferIds = db.transactions().transferIdsForWallet(w.id)
        transferIds.forEach { db.transactions().deleteByTransfer(it); db.transfers().deleteById(it) }
        db.budgets().deleteWalletLinks(w.id)
        db.goals().unlinkWallet(w.id)
        db.wallets().delete(w)
    }

    /** A wallet's currency can only change while it has no transactions (amounts are stored in it). */
    suspend fun walletHasTransactions(walletId: Long): Boolean = db.transactions().countForWallet(walletId) > 0
    suspend fun saveCategory(c: CategoryEntity): Long = if (c.id == 0L) db.categories().insert(c) else { db.categories().update(c); c.id }
    suspend fun deleteCategory(c: CategoryEntity) = db.withTransaction { db.categories().detachChildren(c.id); db.categories().delete(c) }

    /** System + a sensible starter set, only when the database has no categories yet. */
    suspend fun ensureDefaultCategories() {
        if (db.categories().all().isNotEmpty()) return
        val now = System.currentTimeMillis()
        val system = listOf(
            Triple("Transfer", "system::transfer", "#E5A319"), Triple("Transfer tax", "system::transfer_tax", "#CCC618"),
        ).map { (name, tag, color) -> CategoryEntity(uuid = "system-uuid-$tag", name = name, icon = IconSpec.letterFor(name, color).encode(), type = CategoryType.SYSTEM, tag = tag, lastEdit = now) }
        val expenses = listOf(
            "Food and drinks" to "ic_icon_cocktail", "Groceries" to "ic_icon_shopping_cart", "Transport" to "ic_icon_car", "Housing" to "ic_icon_home",
            "Bills" to "ic_icon_receipt", "Health" to "ic_icon_medical", "Shopping" to "ic_icon_shopping_bag", "Entertainment" to "ic_icon_gamepad",
            "Travel" to "ic_icon_airplane", "Education" to "ic_icon_book", "Gifts" to "ic_icon_gift", "Other" to "ic_icon_wallet_2",
        )
        val incomes = listOf("Salary" to "ic_icon_wallet", "Business" to "ic_icon_briefcase", "Gifts received" to "ic_icon_gift", "Other income" to "ic_icon_money")
        val available = AVAILABLE_ICONS
        fun icon(name: String, res: String, idx: Int): String {
            val color = IconSpec.palette[idx % IconSpec.palette.size]
            val symbol = com.oriondev.moneywallet.next.ui.components.SymbolIcons.legacyNames[res]
            return when {
                res in available -> IconSpec.Resource(res)
                symbol != null -> IconSpec.Symbol(symbol, color, (IconSpec.letterFor(name, color) as IconSpec.Letter).text)
                else -> IconSpec.letterFor(name, color)
            }.encode()
        }
        val items = system +
            expenses.mapIndexed { i, (n, r) -> CategoryEntity(uuid = UUID.randomUUID().toString(), name = n, icon = icon(n, r, i), type = CategoryType.EXPENSE, sortIndex = i, lastEdit = now) } +
            incomes.mapIndexed { i, (n, r) -> CategoryEntity(uuid = UUID.randomUUID().toString(), name = n, icon = icon(n, r, i + 7), type = CategoryType.INCOME, sortIndex = i, lastEdit = now) }
        db.categories().insertAll(items)
    }

    companion object {
        /** Vector icons bundled in res/drawable (copied from the legacy free icon set). */
        val AVAILABLE_ICONS: Set<String> by lazy { IconCatalog.names }
    }
}

/** Filled in at build time from R.drawable via reflection so the catalog never goes stale. */
object IconCatalog {
    val names: Set<String> by lazy {
        com.oriondev.moneywallet.next.R.drawable::class.java.fields.map { it.name }.filter { it.startsWith("ic_icon_") }.toSet()
    }
    fun resId(name: String): Int = try { com.oriondev.moneywallet.next.R.drawable::class.java.getField(name).getInt(null) } catch (e: Exception) { 0 }
}
