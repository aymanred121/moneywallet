package com.oriondev.moneywallet.next.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.oriondev.moneywallet.next.WalletApp
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.data.currency.CurrencySeed
import com.oriondev.moneywallet.next.data.db.BudgetEntity
import com.oriondev.moneywallet.next.data.db.BudgetType
import com.oriondev.moneywallet.next.data.db.CaptureEntity
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.ExchangeRateEntity
import com.oriondev.moneywallet.next.data.db.GoalEntity
import com.oriondev.moneywallet.next.data.db.TransactionEntity
import com.oriondev.moneywallet.next.data.db.WalletEntity
import com.oriondev.moneywallet.next.data.repo.WalletRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.random.Random

/**
 * Debug-only: replaces the database with a neutral demo dataset for README screenshots.
 * `adb shell am broadcast -n com.oriondev.moneywallet.next/.debug.DemoDataReceiver`
 */
class DemoDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = WalletApp.container(context)
        container.scope.launch {
            try { seed(context, container) } catch (e: Exception) { Log.e("DemoData", "seed failed", e) } finally { pending.finish() }
        }
    }

    private suspend fun seed(context: Context, container: com.oriondev.moneywallet.next.AppContainer) {
        val db = container.db
        val repo = container.repo
        db.clearAllTables()
        CurrencySeed.ensureSeeded(context, db.currencies())
        val now = System.currentTimeMillis()
        db.rates().upsertAll(listOf(ExchangeRateEntity("USD", 1.0, now), ExchangeRateEntity("EUR", 0.92, now), ExchangeRateEntity("GBP", 0.79, now)))
        val prefs = container.prefs
        prefs.setBaseCurrency("USD"); prefs.setFirstDayOfMonth(START_DAY); prefs.setOnboardingDone(true); prefs.setSelectedWallet(null)
        prefs.setCaptureEnabled(true); prefs.setHideBalances(false); prefs.setBiometricLock(false)

        fun uuid() = UUID.randomUUID().toString()
        fun sym(key: String, color: String) = IconSpec.Symbol(key, color).encode()
        val checking = db.wallets().insert(WalletEntity(uuid = uuid(), name = "Checking", icon = sym("account_balance", "#3F51B5"), currency = "USD", startMoney = 3_000_00, sortIndex = 0))
        val savings = db.wallets().insert(WalletEntity(uuid = uuid(), name = "Savings", icon = sym("savings", "#006C4C"), currency = "USD", startMoney = 8_500_00, sortIndex = 1))
        val travel = db.wallets().insert(WalletEntity(uuid = uuid(), name = "Travel card", icon = sym("flight", "#00BCD4"), currency = "EUR", startMoney = 900_00, sortIndex = 2))

        db.categories().insertAll(listOf(
            CategoryEntity(uuid = "system-uuid-system::transfer", name = "Transfer", icon = IconSpec.letterFor("Transfer", "#E5A319").encode(), type = CategoryType.SYSTEM, tag = "system::transfer"),
            CategoryEntity(uuid = "system-uuid-system::transfer_tax", name = "Transfer tax", icon = IconSpec.letterFor("Transfer tax", "#CCC618").encode(), type = CategoryType.SYSTEM, tag = "system::transfer_tax"),
        ))
        suspend fun cat(name: String, key: String, color: String, type: Int = CategoryType.EXPENSE) =
            db.categories().insert(CategoryEntity(uuid = uuid(), name = name, icon = sym(key, color), type = type))
        val groceries = cat("Groceries", "local_grocery_store", "#4CAF50")
        val dining = cat("Dining out", "restaurant", "#FF9800")
        val coffee = cat("Coffee", "local_cafe", "#795548")
        val transport = cat("Transport", "directions_car", "#2196F3")
        val rent = cat("Rent", "home", "#3F51B5")
        val bills = cat("Bills", "bolt", "#FFC107")
        val subs = cat("Subscriptions", "subscriptions", "#E91E63")
        val health = cat("Health", "fitness_center", "#009688")
        val shopping = cat("Shopping", "shopping_bag", "#9C27B0")
        val travelCat = cat("Travel", "flight", "#00BCD4")
        val salary = cat("Salary", "payments", "#006C4C", CategoryType.INCOME)
        val freelance = cat("Freelance", "work", "#8BC34A", CategoryType.INCOME)

        val rnd = Random(42)
        val today = LocalDate.now()
        val from = today.minusDays(125)
        val rows = ArrayList<TransactionEntity>()
        fun add(day: LocalDate, hour: Int, cents: Long, category: Long, desc: String, wallet: Long = checking, dir: Int = Direction.EXPENSE) {
            if (day.isBefore(from) || day.isAfter(today)) return
            rows += TransactionEntity(
                uuid = uuid(), money = cents, date = Dates.toMillis(day.atTime(hour, rnd.nextInt(0, 59))), description = desc,
                categoryId = category, direction = dir, walletId = wallet,
            )
        }
        var m = YearMonth.from(from)
        while (!m.isAfter(YearMonth.from(today))) {
            add(m.atDay(START_DAY), 9, 5_400_00, salary, "Northwind Ltd", dir = Direction.INCOME)
            add(m.atDay(1), 8, 1_650_00, rent, "Rent")
            add(m.atDay(2), 7, 39_00, health, "City Gym")
            add(m.atDay(3), 6, 2_99, subs, "iCloud+")
            add(m.atDay(5), 10, rnd.nextLong(85_00, 121_00), bills, "Electricity")
            add(m.atDay(9), 20, 15_49, subs, "Netflix")
            add(m.atDay(12), 11, 59_99, bills, "Internet")
            add(m.atDay(17), 8, 10_99, subs, "Spotify")
            if (m.monthValue % 2 == 0) add(m.atDay(14), 15, 650_00, freelance, "Design project", dir = Direction.INCOME)
            m = m.plusMonths(1)
        }
        var d = from
        while (!d.isAfter(today)) {
            if (rnd.nextInt(100) < 55) add(d, 8, rnd.nextLong(4_25, 6_80), coffee, "Daily Grind Café")
            if (rnd.nextInt(100) < 20) add(d, 18, rnd.nextLong(42_00, 138_00), groceries, listOf("Fresh Market", "Corner Grocery", "Green Basket").random(rnd))
            if (rnd.nextInt(100) < 18) add(d, 20, rnd.nextLong(18_00, 72_00), dining, listOf("Pizza Napoli", "Sushi Bar", "Taco Stand", "Noodle House").random(rnd))
            if (rnd.nextInt(100) < 12) add(d, 17, rnd.nextLong(38_00, 61_00), transport, "Fuel")
            if (rnd.nextInt(100) < 10) add(d, 22, rnd.nextLong(9_00, 26_00), transport, "Ride share")
            if (rnd.nextInt(100) < 6) add(d, 16, rnd.nextLong(24_00, 115_00), shopping, listOf("Bookstore", "Home goods", "Sneakers").random(rnd))
            d = d.plusDays(1)
        }
        add(today.minusDays(3), 14, 1_299_00, shopping, "Laptop")
        // a trip paid with the EUR card a couple of months ago
        val trip = today.minusDays(70)
        add(trip, 15, 320_00, travelCat, "Hotel Lisbon", wallet = travel)
        add(trip.plusDays(1), 12, 45_00, travelCat, "Train to Porto", wallet = travel)
        add(trip.plusDays(2), 21, 62_00, dining, "Tasca dinner", wallet = travel)
        db.transactions().insertAll(rows)
        var t = YearMonth.from(from)
        while (!t.isAfter(YearMonth.from(today))) {
            val day = t.atDay(START_DAY + 1)
            if (!day.isBefore(from) && !day.isAfter(today)) {
                repo.saveTransfer(WalletRepository.TransferDraft(checking, savings, 600_00, 600_00, date = Dates.toMillis(day.atTime(10, 0)), description = "Monthly savings", note = null))
            }
            t = t.plusMonths(1)
        }

        val cycleStart = Dates.monthStart(Dates.currentMonthAnchor(START_DAY), START_DAY)
        val cycleEnd = Dates.monthEndExclusive(Dates.currentMonthAnchor(START_DAY), START_DAY).minusDays(1)
        db.budgets().save(BudgetEntity(uuid = uuid(), type = BudgetType.EXPENSES, startDate = Dates.startOfDay(cycleStart), endDate = Dates.startOfDay(cycleEnd), money = 3_200_00, currency = "USD"), listOf(checking))
        db.budgets().save(BudgetEntity(uuid = uuid(), type = BudgetType.CATEGORY, categoryId = dining, startDate = Dates.startOfDay(cycleStart), endDate = Dates.startOfDay(cycleEnd), money = 250_00, currency = "USD"), listOf(checking))

        db.goals().insertAll(listOf(
            GoalEntity(uuid = uuid(), name = "Emergency fund", icon = sym("shield", "#006C4C"), targetMoney = 15_000_00, currency = "USD", walletId = savings, sortIndex = 0),
            GoalEntity(uuid = uuid(), name = "Trip to Japan", icon = sym("flight", "#E91E63"), targetMoney = 4_000_00, currency = "USD", savedMoney = 1_350_00, deadline = Dates.startOfDay(today.plusMonths(8)), sortIndex = 1),
        ))

        db.captures().insert(CaptureEntity(
            dedupeKey = uuid(), packageName = "com.google.android.apps.walletnfcrel", appName = "Google Wallet", title = "Daily Grind Café",
            text = "USD 6.25 with Visa ••4242", postedAt = now - 25 * 60_000, amount = 6_25, currency = "USD", merchant = "Daily Grind Café", direction = Direction.EXPENSE,
        ))
        db.captures().insert(CaptureEntity(
            dedupeKey = uuid(), packageName = "com.google.android.apps.messaging", appName = "Messages", title = "Bank",
            text = "Incoming transfer\nAmount: USD 250.00\nFrom: ALEX KIM\nTo: account **0000", postedAt = now - 3 * 60 * 60_000,
            amount = 250_00, currency = "USD", merchant = "Alex Kim", direction = Direction.INCOME,
        ))
        Log.i("DemoData", "seeded ${rows.size} transactions")
    }

    /** Payday about two weeks ago, so the screenshots show a cycle in full swing. */
    private val START_DAY: Int get() = LocalDate.now().minusDays(15).dayOfMonth.coerceIn(1, 28)
}
