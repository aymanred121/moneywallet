package com.oriondev.moneywallet.next.ui.screens.review

import com.oriondev.moneywallet.next.core.Clock
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.data.db.ExpenseRow
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.domain.analytics.PeriodSelection
import com.oriondev.moneywallet.next.domain.analytics.Total
import com.oriondev.moneywallet.next.domain.analytics.total
import com.oriondev.moneywallet.next.domain.insights.Charge
import com.oriondev.moneywallet.next.domain.insights.Recurring
import com.oriondev.moneywallet.next.domain.insights.RecurringCharge
import com.oriondev.moneywallet.next.domain.insights.Unusual
import com.oriondev.moneywallet.next.domain.insights.UnusualExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

/** A period choice resolved against today and the start days, re-emitted at midnight and on settings changes. */
fun resolvedPeriod(selection: Flow<PeriodSelection>, settings: Flow<Settings>): Flow<Pair<Period, LocalDate>> =
    combine(selection, settings.map { it.firstDayOfMonth.coerceIn(1, 28) to it.firstDayOfWeek }.distinctUntilChanged(), Clock.today) { s, (startDay, fdow), today ->
        s.resolve(today, startDay, DayOfWeek.of(fdow.coerceIn(1, 7))) to today
    }.distinctUntilChanged()

fun ExpenseRow.toCharge() = Charge(Dates.toLocalDate(date), description, currency, money)

/** Subscriptions and bills detected from the ~13 months of expenses up to [today]. */
fun recurringCharges(db: WalletDatabase, walletId: Long?, today: LocalDate): Flow<List<RecurringCharge>> =
    db.history().observeExpenses(walletId, Dates.startOfDay(today.minusDays(400)), Dates.startOfDay(today.plusDays(1)))
        .map { rows -> Recurring.detect(rows.map { it.toCharge() }, today) }

/** Monthly cost of [charges] in base currency. */
fun monthlyTotal(charges: List<RecurringCharge>, ctx: CurrencyContext): Total =
    ctx.total(MoneyBag().also { bag -> charges.forEach { bag.add(it.currency, it.monthlyCost) } })

/** Biggest expense across wallet currencies: every row is converted before ranking. */
fun biggestExpense(rows: List<ExpenseRow>, ctx: CurrencyContext): Pair<ExpenseRow, Total>? =
    rows.map { it to ctx.total(it.money, it.currency) }.filter { it.second.base != null }.maxByOrNull { it.second.base!! }

/** See [Unusual]: [current] are the period's expenses, [history] the look-back window (both raw rows). */
fun unusualExpense(current: List<ExpenseRow>, history: List<ExpenseRow>, spent: Long?, ctx: CurrencyContext): UnusualExpense? {
    fun items(rows: List<ExpenseRow>) = rows.mapNotNull { r ->
        ctx.toBaseOrNull(r.money, r.currency)?.let { Unusual.Item(r.id, r.categoryId, it, r.description, r.categoryName) }
    }
    return Unusual.find(items(current), items(history), spent ?: return null)
}
