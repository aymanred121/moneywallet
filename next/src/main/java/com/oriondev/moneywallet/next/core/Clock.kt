package com.oriondev.moneywallet.next.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

object Clock {
    /**
     * Today's date, re-emitted right after every local midnight. Screens key their periods and
     * balance cut-offs on it so a long-lived ViewModel rolls over into a new day or payday month.
     */
    val today: Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDate.now()
            emit(now)
            val wait = Dates.startOfDay(now.plusDays(1)) - System.currentTimeMillis()
            delay(wait.coerceIn(1_000L, 60 * 60 * 1000L)) // re-check at least hourly (timezone / clock changes)
        }
    }.distinctUntilChanged()
}
