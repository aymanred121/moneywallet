package com.oriondev.moneywallet.next.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oriondev.moneywallet.next.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Create a ViewModel that receives the AppContainer, keyed so different screens don't share instances. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create(container) as T
    })
}

/**
 * Like [containerViewModel], plus a [SavedStateHandle] from the owner (the nav back stack entry),
 * so state written to it survives process death.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModelWithState(key: String? = null, crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key) { create(container, createSavedStateHandle()) }
}
