package com.oriondev.moneywallet.next.ui.screens.transactions.editor

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.Convert
import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.CaptureStatus
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.CurrencyEntity
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.data.repo.WalletRepository
import com.oriondev.moneywallet.next.ui.components.transactions.AmountExpression
import com.oriondev.moneywallet.next.ui.screens.transactions.EMPTY_CTX
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the user is editing; amounts stay as a keypad expression until save. */
data class EditorForm(
    val id: Long? = null,
    val loaded: Boolean = false,
    val expr: String = "",
    val direction: Int = Direction.EXPENSE,
    val categoryId: Long? = null,
    val walletId: Long? = null,
    /** null = use the wallet currency. */
    val inputCurrency: String? = null,
    val manualRate: Double? = null,
    val date: Long = Dates.now(),
    val description: String = "",
    val note: String = "",
    val confirmed: Boolean = true,
    val countInTotal: Boolean = true,
    val keypadOpen: Boolean = true,
    val isTransferLeg: Boolean = false,
    /** The category was chosen deliberately (by the user, an edit or a prefill): auto-categorise leaves it alone. */
    val categoryPicked: Boolean = false,
    /** "More options" (time, note, confirmed, count in total) expanded. */
    val moreOpen: Boolean = false,
)

/** Flattens the form for SavedStateHandle so a half-typed draft survives process death. */
internal fun EditorForm.toBundle() = Bundle().apply {
    putLong("id", id ?: -1); putBoolean("loaded", loaded); putString("expr", expr); putInt("direction", direction)
    putLong("categoryId", categoryId ?: -1); putLong("walletId", walletId ?: -1); putString("inputCurrency", inputCurrency)
    putDouble("manualRate", manualRate ?: Double.NaN); putLong("date", date); putString("description", description); putString("note", note)
    putBoolean("confirmed", confirmed); putBoolean("countInTotal", countInTotal); putBoolean("keypadOpen", keypadOpen)
    putBoolean("isTransferLeg", isTransferLeg); putBoolean("categoryPicked", categoryPicked); putBoolean("moreOpen", moreOpen)
}

internal fun editorFormOf(b: Bundle) = EditorForm(
    id = b.getLong("id", -1).takeIf { it > 0 }, loaded = b.getBoolean("loaded"), expr = b.getString("expr").orEmpty(),
    direction = b.getInt("direction"), categoryId = b.getLong("categoryId", -1).takeIf { it > 0 },
    walletId = b.getLong("walletId", -1).takeIf { it > 0 }, inputCurrency = b.getString("inputCurrency"),
    manualRate = b.getDouble("manualRate", Double.NaN).takeIf { !it.isNaN() }, date = b.getLong("date", Dates.now()),
    description = b.getString("description").orEmpty(), note = b.getString("note").orEmpty(),
    confirmed = b.getBoolean("confirmed", true), countInTotal = b.getBoolean("countInTotal", true), keypadOpen = b.getBoolean("keypadOpen", true),
    isTransferLeg = b.getBoolean("isTransferLeg"), categoryPicked = b.getBoolean("categoryPicked"), moreOpen = b.getBoolean("moreOpen"),
)

sealed class EditorEvent {
    data object Saved : EditorEvent()
    data object Deleted : EditorEvent()
    data class Error(val message: String) : EditorEvent()
}

/** Derived values shared by the screen and the save path. */
data class EditorComputed(
    val wallet: WalletWithBalance?,
    val walletCurrency: Currency,
    val inputCurrency: Currency,
    val foreign: Boolean,
    val liveRate: Double?,
    val rate: Double?,
    val inputMinor: Long?,
    val convertedMinor: Long?,
    val category: CategoryEntity?,
)

/**
 * Values to start a new transaction with, e.g. from a captured payment notification. [amountMinor]
 * is in [currency] (null = wallet currency); saving marks [captureId] as added.
 */
data class EditorPrefill(
    val amountMinor: Long? = null,
    val currency: String? = null,
    val description: String? = null,
    val date: Long? = null,
    val categoryId: Long? = null,
    val captureId: Long? = null,
)

fun categoryTypeFor(direction: Int) = if (direction == Direction.INCOME) CategoryType.INCOME else CategoryType.EXPENSE

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEditorViewModel(
    private val container: AppContainer,
    private val editId: Long?,
    private val presetWalletId: Long?,
    private val presetDirection: Int,
    private val prefill: EditorPrefill? = null,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val repo = container.repo
    private val db = container.db

    val ctx: StateFlow<CurrencyContext> = repo.currencyContext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EMPTY_CTX)
    val wallets: StateFlow<List<WalletWithBalance>> = repo.wallets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val currencies: StateFlow<List<CurrencyEntity>> = db.currencies().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** A new transaction with no active wallet to put it in: the screen asks to create one. */
    val noWallet: StateFlow<Boolean> = repo.wallets().map { l -> editId == null && l.none { !it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _form = MutableStateFlow(EditorForm(id = editId, direction = presetDirection, walletId = presetWalletId, loaded = editId == null))
    val form: StateFlow<EditorForm> = _form.asStateFlow()
    private val _saving = MutableStateFlow(false)
    /** True from a Save/Delete tap until it fails; further taps are ignored meanwhile. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()
    val events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 4)
    private var suggestJob: Job? = null

    /** Quick-add chips for the current direction, ranked by recent use. */
    val quickCategories: StateFlow<List<CategoryEntity>> = combine(_form.map { it.direction }.distinctUntilChanged(), repo.categories) { d, cats -> d to cats }
        .mapLatest { (d, cats) ->
            val now = Dates.now()
            QuickCategories.rank(db.editor().categoryUses(d, QuickCategories.since(now)), cats, categoryTypeFor(d), now)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val restored = savedState.get<Bundle>(FORM_KEY)?.let(::editorFormOf)
        if (restored != null) _form.value = restored
        if (restored == null || !restored.loaded || (editId == null && restored.walletId == null)) viewModelScope.launch { load() }
        viewModelScope.launch { _form.collect { savedState[FORM_KEY] = it.toBundle() } }
    }

    private suspend fun load() {
        if (editId != null) {
            val t = db.transactions().byId(editId)
            if (t == null) { events.tryEmit(EditorEvent.Error("Transaction not found")); return }
            val c = ctx.first { it.currencies.isNotEmpty() }
            val wallet = db.wallets().byId(t.walletId)
            val foreign = t.currency != null && t.currency != wallet?.currency
            val amountCurrency = if (foreign) c.currency(t.currency!!) else c.currency(wallet?.currency ?: c.baseIso)
            _form.value = EditorForm(
                id = t.id, loaded = true,
                expr = AmountExpression.fromMinor(if (foreign) (t.originalMoney ?: t.money) else t.money, amountCurrency),
                direction = t.direction, categoryId = t.categoryId, walletId = t.walletId,
                inputCurrency = if (foreign) t.currency else null, manualRate = if (foreign) t.exchangeRate else null,
                date = t.date, description = t.description, note = t.note ?: "", confirmed = t.confirmed, countInTotal = t.countInTotal,
                keypadOpen = false, isTransferLeg = t.transferId != null, categoryPicked = true,
                moreOpen = !t.note.isNullOrBlank() || !t.confirmed || !t.countInTotal,
            )
        } else {
            if (_form.value.walletId == null) {
                val selected = container.prefs.settings.first().selectedWalletId
                // waits (instead of crashing) while there is no active wallet; the screen offers to create one
                val active = repo.wallets().first { l -> l.any { !it.archived } }.filter { !it.archived }
                _form.update { f -> if (f.walletId == null) f.copy(walletId = (active.firstOrNull { it.id == selected } ?: active.first()).id) else f }
            }
            prefill?.let { applyPrefill(it) }
        }
    }

    private suspend fun applyPrefill(p: EditorPrefill) {
        val c = ctx.first { it.currencies.isNotEmpty() }
        val walletCurrency = _form.value.walletId?.let { id -> repo.wallets().first().firstOrNull { it.id == id }?.currency }
        val iso = p.currency?.takeIf { c.currencies.containsKey(it) }
        _form.update { f ->
            f.copy(
                expr = p.amountMinor?.let { AmountExpression.fromMinor(it, c.currency(iso ?: walletCurrency ?: c.baseIso)) } ?: f.expr,
                inputCurrency = iso?.takeIf { it != walletCurrency },
                description = p.description ?: f.description,
                date = p.date ?: f.date,
                categoryId = p.categoryId ?: f.categoryId,
                categoryPicked = p.categoryId != null || f.categoryPicked,
                // with an amount, the next step is a category chip, so keep the keypad out of the way
                keypadOpen = p.amountMinor == null,
            )
        }
        if (p.categoryId == null) p.description?.let { autoCategorise(it) }
    }

    /** Pre-selects the category last used with exactly this description (any case), unless one was picked. */
    private suspend fun autoCategorise(text: String) {
        val t = text.trim()
        val f = _form.value
        if (t.isEmpty() || f.categoryPicked || f.isTransferLeg) return
        val id = db.editor().lastCategoryFor(t, f.direction) ?: return
        if (repo.categories.first().none { it.id == id && it.type == categoryTypeFor(f.direction) }) return
        _form.update { if (it.categoryPicked || it.direction != f.direction || it.description.trim() != t) it else it.copy(categoryId = id) }
    }

    fun compute(form: EditorForm, ctx: CurrencyContext, wallets: List<WalletWithBalance>, categories: List<CategoryEntity>): EditorComputed {
        val wallet = wallets.firstOrNull { it.id == form.walletId }
        val walletCurrency = ctx.currency(wallet?.currency ?: ctx.baseIso)
        val inputCurrency = form.inputCurrency?.let { ctx.currency(it) } ?: walletCurrency
        val foreign = inputCurrency.iso != walletCurrency.iso
        val liveRate = if (foreign) ctx.rate(inputCurrency.iso, walletCurrency.iso) else null
        val rate = if (foreign) form.manualRate ?: liveRate else null
        val inputMinor = AmountExpression.toMinor(form.expr, inputCurrency)
        val converted = if (!foreign) inputMinor else if (inputMinor != null && rate != null) Convert.minor(inputCurrency, walletCurrency, inputMinor, rate) else null
        return EditorComputed(wallet, walletCurrency, inputCurrency, foreign, liveRate, rate, inputMinor, converted, categories.firstOrNull { it.id == form.categoryId })
    }

    // ---- keypad ----
    private fun inputCurrencyOf(f: EditorForm) =
        f.inputCurrency?.let { ctx.value.currency(it) } ?: ctx.value.currency(wallets.value.firstOrNull { it.id == f.walletId }?.currency ?: ctx.value.baseIso)
    fun key(k: String) = _form.update { f -> f.copy(expr = AmountExpression.append(f.expr, k, inputCurrencyOf(f).decimals)) }
    fun backspace() = _form.update { it.copy(expr = AmountExpression.backspace(it.expr)) }
    fun clearAmount() = _form.update { it.copy(expr = "") }
    fun done() = _form.update { f -> f.copy(expr = if (f.expr.isEmpty()) "" else AmountExpression.simplify(f.expr, inputCurrencyOf(f).decimals), keypadOpen = false) }
    fun setKeypadOpen(open: Boolean) = _form.update { it.copy(keypadOpen = open) }

    // ---- fields ----
    fun setDirection(d: Int) {
        _form.update { f ->
            val cat = categories.value.firstOrNull { it.id == f.categoryId }
            val keep = cat == null || cat.type == categoryTypeFor(d)
            f.copy(direction = d, categoryId = if (keep) f.categoryId else null, categoryPicked = f.categoryPicked && keep)
        }
        viewModelScope.launch { autoCategorise(_form.value.description) }
    }
    fun setCategory(id: Long?) = _form.update { it.copy(categoryId = id, categoryPicked = id != null) }

    /** Quick-category chip: selects it and, for a new transaction with a valid amount, saves right away. */
    fun quickPick(c: CategoryEntity) {
        setCategory(c.id)
        val f = _form.value
        if (f.id == null && (compute(f, ctx.value, wallets.value, categories.value).inputMinor ?: 0L) > 0L) { done(); save() }
    }
    fun setWallet(w: WalletWithBalance) = _form.update { f ->
        f.copy(walletId = w.id, inputCurrency = f.inputCurrency?.takeIf { it != w.currency }, manualRate = if (f.inputCurrency == null || f.inputCurrency == w.currency) null else f.manualRate)
    }
    fun setInputCurrency(iso: String) = _form.update { f ->
        val walletCur = wallets.value.firstOrNull { it.id == f.walletId }?.currency
        f.copy(inputCurrency = iso.takeIf { it != walletCur }, manualRate = null)
    }
    fun setManualRate(rate: Double?) = _form.update { it.copy(manualRate = rate) }
    fun setDate(millis: Long) = _form.update { it.copy(date = millis) }
    fun setNote(text: String) = _form.update { it.copy(note = text) }
    fun setConfirmed(v: Boolean) = _form.update { it.copy(confirmed = v) }
    fun setCountInTotal(v: Boolean) = _form.update { it.copy(countInTotal = v) }
    fun setMoreOpen(v: Boolean) = _form.update { it.copy(moreOpen = v) }

    fun setDescription(text: String) {
        _form.update { it.copy(description = text) }
        suggestJob?.cancel()
        if (text.isBlank()) { _suggestions.value = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(120)
            _suggestions.value = db.transactions().suggestDescriptions(text.trim()).filter { !it.equals(text.trim(), ignoreCase = true) }
            autoCategorise(text)
        }
    }
    fun pickSuggestion(text: String) {
        suggestJob?.cancel()
        _form.update { it.copy(description = text) }
        _suggestions.value = emptyList()
        suggestJob = viewModelScope.launch { autoCategorise(text) }
    }
    fun clearSuggestions() { _suggestions.value = emptyList() }

    // ---- persistence ----
    fun save() {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val f = _form.value
            val c = compute(f, ctx.value, wallets.value, categories.value)
            val error = when {
                c.wallet == null -> "Choose a wallet"
                c.inputMinor == null || c.inputMinor <= 0 -> "Enter an amount greater than zero"
                f.categoryId == null && !f.isTransferLeg -> "Choose a category"
                c.foreign && (c.rate == null || c.rate <= 0) -> "Enter an exchange rate"
                else -> null
            }
            if (error != null) { _saving.value = false; events.emit(EditorEvent.Error(error)); return@launch }
            try {
                val savedId = repo.saveTransaction(
                    WalletRepository.TransactionDraft(
                        id = f.id, walletId = c.wallet!!.id, walletCurrency = c.walletCurrency.iso, direction = f.direction, categoryId = f.categoryId,
                        inputMinor = c.inputMinor!!, inputCurrency = c.inputCurrency.iso, rate = c.rate, date = f.date,
                        description = f.description, note = f.note.ifBlank { null }, confirmed = f.confirmed, countInTotal = f.countInTotal,
                    ),
                    ctx.value,
                )
                prefill?.captureId?.let { db.captures().setStatus(it, CaptureStatus.ADDED, savedId) }
                events.emit(EditorEvent.Saved) // stays "saving": the screen is closing
            } catch (e: Exception) {
                _saving.value = false
                events.emit(EditorEvent.Error(e.message ?: "Could not save"))
            }
        }
    }

    fun delete() {
        val id = _form.value.id ?: return
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try {
                repo.deleteTransaction(id)
                events.emit(EditorEvent.Deleted)
            } catch (e: Exception) {
                _saving.value = false
                events.emit(EditorEvent.Error(e.message ?: "Could not delete"))
            }
        }
    }

    private companion object { const val FORM_KEY = "editor-form" }
}
