package com.oriondev.moneywallet.next.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val baseCurrency: String? = null,
    val selectedWalletId: Long? = null, // null = all wallets
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val firstDayOfWeek: Int = 1, // java.time.DayOfWeek value, 1 = Monday
    val firstDayOfMonth: Int = 1,
    val ratesLastUpdate: Long = 0,
    val onboardingDone: Boolean = false,
    val biometricLock: Boolean = false,
    val reviewNotifications: Boolean = true,
    val showCents: Boolean = true,
    val hideBalances: Boolean = false,
    /** Notify when a budget reaches 80% and 100%. */
    val budgetAlerts: Boolean = true,
    /** Read payment notifications from other apps into the capture inbox. */
    val captureEnabled: Boolean = false,
    /** Packages whose notifications are captured (empty = the built-in list of wallet/bank apps). */
    val captureApps: Set<String> = emptySet(),
    /** Wallet captured payments go to (null = the selected / first wallet). */
    val captureWalletId: Long? = null,
    /** Also read bank SMS from the SMS app (strict parsing: payment wording + currency required). */
    val captureSms: Boolean = true,
    /** Message types the user taught ("I|credit transfer local"), see capture.HeaderRule. */
    val captureRules: Set<String> = emptySet(),
)

class UserPreferences(private val context: Context) {
    private object K {
        val baseCurrency = stringPreferencesKey("base_currency")
        val wallet = longPreferencesKey("selected_wallet")
        val theme = intPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val fdow = intPreferencesKey("first_day_of_week")
        val fdom = intPreferencesKey("first_day_of_month")
        val rates = longPreferencesKey("rates_last_update")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val biometric = booleanPreferencesKey("biometric_lock")
        val review = booleanPreferencesKey("review_notifications")
        val cents = booleanPreferencesKey("show_cents")
        val hide = booleanPreferencesKey("hide_balances")
        val budgetAlerts = booleanPreferencesKey("budget_alerts")
        val capture = booleanPreferencesKey("capture_enabled")
        val captureApps = stringSetPreferencesKey("capture_apps")
        val captureWallet = longPreferencesKey("capture_wallet")
        val captureSms = booleanPreferencesKey("capture_sms")
        val captureRules = stringSetPreferencesKey("capture_rules")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            baseCurrency = p[K.baseCurrency],
            selectedWalletId = p[K.wallet]?.takeIf { it > 0 },
            themeMode = ThemeMode.entries.getOrElse(p[K.theme] ?: 0) { ThemeMode.SYSTEM },
            dynamicColor = p[K.dynamic] ?: true,
            firstDayOfWeek = p[K.fdow] ?: 1,
            firstDayOfMonth = p[K.fdom] ?: 1,
            ratesLastUpdate = p[K.rates] ?: 0L,
            onboardingDone = p[K.onboarding] ?: false,
            biometricLock = p[K.biometric] ?: false,
            reviewNotifications = p[K.review] ?: true,
            showCents = p[K.cents] ?: true,
            hideBalances = p[K.hide] ?: false,
            budgetAlerts = p[K.budgetAlerts] ?: true,
            captureEnabled = p[K.capture] ?: false,
            captureApps = p[K.captureApps] ?: emptySet(),
            captureWalletId = p[K.captureWallet]?.takeIf { it > 0 },
            captureSms = p[K.captureSms] ?: true,
            captureRules = p[K.captureRules] ?: emptySet(),
        )
    }

    suspend fun setBaseCurrency(iso: String) = context.dataStore.edit { it[K.baseCurrency] = iso }
    suspend fun setSelectedWallet(id: Long?) = context.dataStore.edit { it[K.wallet] = id ?: 0L }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[K.theme] = mode.ordinal }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[K.dynamic] = enabled }
    suspend fun setFirstDayOfWeek(day: Int) = context.dataStore.edit { it[K.fdow] = day }
    suspend fun setRatesLastUpdate(ts: Long) = context.dataStore.edit { it[K.rates] = ts }
    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[K.onboarding] = done }
    suspend fun setBiometricLock(enabled: Boolean) = context.dataStore.edit { it[K.biometric] = enabled }
    suspend fun setReviewNotifications(enabled: Boolean) = context.dataStore.edit { it[K.review] = enabled }
    suspend fun setShowCents(enabled: Boolean) = context.dataStore.edit { it[K.cents] = enabled }
    suspend fun setHideBalances(enabled: Boolean) = context.dataStore.edit { it[K.hide] = enabled }
    suspend fun setBudgetAlerts(enabled: Boolean) = context.dataStore.edit { it[K.budgetAlerts] = enabled }
    suspend fun setCaptureEnabled(enabled: Boolean) = context.dataStore.edit { it[K.capture] = enabled }
    suspend fun setCaptureApps(packages: Set<String>) = context.dataStore.edit { it[K.captureApps] = packages }
    suspend fun setCaptureRules(rules: Set<String>) = context.dataStore.edit { it[K.captureRules] = rules }
    suspend fun setCaptureSms(enabled: Boolean) = context.dataStore.edit { it[K.captureSms] = enabled }
    suspend fun setCaptureWallet(id: Long?) = context.dataStore.edit { it[K.captureWallet] = id ?: 0L }
    suspend fun setFirstDayOfMonth(day: Int) = context.dataStore.edit { it[K.fdom] = day.coerceIn(1, 28) }
}
