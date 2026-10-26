package com.oriondev.moneywallet.next.data.repo

import com.oriondev.moneywallet.next.core.Clock
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.data.db.GoalEntity
import com.oriondev.moneywallet.next.data.db.WalletDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Savings-goal arithmetic, kept free of Android and the database so it is unit tested directly. */
object GoalMath {
    data class Progress(
        val saved: Long, val target: Long, val remaining: Long, val fraction: Float, val complete: Boolean,
        /** Pay cycles left including the current one; null without a deadline, once reached or once it passed. */
        val monthsLeft: Int?,
        /** Amount to put aside each cycle (rounded up); null whenever [monthsLeft] is. */
        val perMonth: Long?,
        val overdue: Boolean,
    )

    fun progress(saved: Long, target: Long, deadline: LocalDate?, today: LocalDate, startDay: Int = 1): Progress {
        val remaining = (target - saved).coerceAtLeast(0)
        val complete = target > 0 && saved >= target
        val fraction = if (target <= 0) 0f else (saved.toDouble() / target).coerceIn(0.0, 1.0).toFloat()
        val overdue = !complete && deadline != null && deadline.isBefore(today)
        val months = if (deadline == null || overdue || complete) null else monthsLeft(today, deadline, startDay)
        val perMonth = months?.let { (remaining + it - 1) / it }
        return Progress(saved, target, remaining, fraction, complete, months, perMonth, overdue)
    }

    /**
     * Months (pay cycles starting on [startDay]) from the one containing [today] through the one
     * containing [deadline], both counted: with payday on the 27th, on 29 Sep a goal due 31 Dec can
     * still be funded from the Sep, Oct, Nov and Dec paychecks, so 4.
     */
    fun monthsLeft(today: LocalDate, deadline: LocalDate, startDay: Int = 1): Int =
        (ChronoUnit.MONTHS.between(Dates.monthAnchor(today, startDay), Dates.monthAnchor(deadline, startDay)) + 1).toInt().coerceAtLeast(1)
}

/** A goal with its live progress; [saved] is null when a linked wallet's currency has no rate to the goal's. */
data class GoalUi(val goal: GoalEntity, val saved: Long?, val walletName: String?, val progress: GoalMath.Progress?) {
    val complete: Boolean get() = progress?.complete == true
}

/** The goals worth a glance on Home: active ones, unfinished first, at most [limit]. */
fun List<GoalUi>.forHome(limit: Int = 2): List<GoalUi> =
    filter { !it.goal.archived }.sortedWith(compareBy({ it.complete }, { it.goal.sortIndex }, { it.goal.name })).take(limit)

class GoalRepository(private val db: WalletDatabase, repo: WalletRepository) {
    /** Linked goals follow their wallet's balance as of today, converted into the goal currency. */
    val goals: Flow<List<GoalUi>> = combine(db.goals().observeAll(), repo.wallets(), repo.currencyContext, Clock.today) { goals, wallets, ctx, today ->
        val byId = wallets.associateBy { it.id }
        goals.map { g ->
            val w = g.walletId?.let(byId::get)
            val saved = if (w != null) ctx.convert(w.balance, w.currency, g.currency) else g.savedMoney
            GoalUi(g, saved, w?.name, saved?.let { GoalMath.progress(it, g.targetMoney, g.deadline?.let(Dates::toLocalDate), today, Dates.monthStartDay) })
        }
    }

    suspend fun byId(id: Long): GoalEntity? = db.goals().byId(id)
    suspend fun save(g: GoalEntity): Long =
        if (g.id == 0L) db.goals().insert(g.copy(sortIndex = db.goals().maxSortIndex() + 1)) else { db.goals().update(g); g.id }
    suspend fun delete(id: Long) = db.goals().deleteById(id)
    suspend fun adjust(id: Long, delta: Long) = db.goals().adjustSaved(id, delta)
    suspend fun setArchived(id: Long, archived: Boolean) = db.goals().setArchived(id, archived)
}
