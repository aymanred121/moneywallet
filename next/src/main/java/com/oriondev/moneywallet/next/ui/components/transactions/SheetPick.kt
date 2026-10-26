@file:OptIn(ExperimentalMaterial3Api::class)

package com.oriondev.moneywallet.next.ui.components.transactions

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

/**
 * Wraps a sheet's pick callback: applies the pick at once (so the selection shows while the sheet
 * leaves), animates the sheet away and only then calls [onDismiss]. Repeat taps during the exit
 * animation are ignored. Callers must not clear their sheet state in onPick themselves.
 */
@Composable
internal fun <T> rememberPickAndHide(state: SheetState, onPick: (T) -> Unit, onDismiss: () -> Unit): (T) -> Unit {
    val scope = rememberCoroutineScope()
    val pick by rememberUpdatedState(onPick)
    val dismiss by rememberUpdatedState(onDismiss)
    return remember(state) {
        var done = false
        { value: T ->
            if (!done) {
                done = true
                pick(value)
                scope.launch { state.hide() }.invokeOnCompletion { dismiss() }
            }
        }
    }
}
