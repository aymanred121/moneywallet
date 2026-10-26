package com.oriondev.moneywallet.next

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Configuration
import com.oriondev.moneywallet.next.data.backup.BackupCodec
import com.oriondev.moneywallet.next.data.currency.CurrencySeed
import com.oriondev.moneywallet.next.data.currency.RatesRepository
import com.oriondev.moneywallet.next.data.currency.RatesWorker
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import com.oriondev.moneywallet.next.data.prefs.UserPreferences
import com.oriondev.moneywallet.next.data.repo.WalletRepository
import com.oriondev.moneywallet.next.review.ReviewScheduler
import com.oriondev.moneywallet.next.widget.WidgetUpdater
import androidx.room.InvalidationTracker
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.prefs.Settings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    val db: WalletDatabase = WalletDatabase.create(context)
    val prefs = UserPreferences(context)
    val repo = WalletRepository(db, prefs)
    val rates = RatesRepository(db.rates())
    val backup = BackupCodec(db, prefs)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class WalletApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Receivers, workers and widgets can start this process cold; the month start day, cents
        // and privacy mask must be right before any of them builds a period or formats an amount.
        // DataStore is a small local file, so this blocks for a few milliseconds at most.
        applyGlobals(runBlocking { container.prefs.settings.first() })
        container.scope.launch {
            CurrencySeed.ensureSeeded(this@WalletApp, container.db.currencies())
            container.db.currencies().setSymbol("SAR", com.oriondev.moneywallet.next.core.CurrencySymbols.preferred("SAR", ""))
            container.repo.ensureDefaultCategories()
        }
        createChannels()
        RatesWorker.schedule(this)
        RatesWorker.refreshNow(this)
        ReviewScheduler.ensureScheduled(this)
        com.oriondev.moneywallet.next.capture.PaymentNotificationListener.ensureBound(this)
        com.oriondev.moneywallet.next.alerts.BudgetAlerts.start(this, container)
        // keep the home-screen widget in sync with anything it shows
        container.db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(
            arrayOf("transactions", "wallets", "exchange_rates", "budgets", "currencies"),
        ) {
            override fun onInvalidated(tables: Set<String>) = WidgetUpdater.requestUpdate(this@WalletApp)
        })
        container.scope.launch {
            container.prefs.settings.collectLatest {
                applyGlobals(it)
                ReviewScheduler.ensureScheduled(this@WalletApp)
            }
        }
        container.scope.launch {
            container.prefs.settings
                .map { listOf(it.baseCurrency, it.selectedWalletId, it.hideBalances, it.showCents, it.firstDayOfMonth) }
                .distinctUntilChanged().drop(1)
                .collect { WidgetUpdater.requestUpdate(this@WalletApp) }
        }
    }

    private fun applyGlobals(settings: Settings) {
        Dates.monthStartDay = settings.firstDayOfMonth.coerceIn(1, 28)
        MoneyFormat.showCents = settings.showCents
        MoneyFormat.masked = settings.hideBalances
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val review = NotificationChannel(CHANNEL_REVIEW, getString(R.string.review_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        review.description = getString(R.string.review_channel_description)
        nm.createNotificationChannel(review)
    }

    companion object {
        const val CHANNEL_REVIEW = "review"
        fun container(context: Context): AppContainer = (context.applicationContext as WalletApp).container
    }
}
