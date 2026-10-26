package com.oriondev.moneywallet.next.ui.screens.capture

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.oriondev.moneywallet.next.ui.Routes

/** Payment capture: the inbox of captured notifications and its settings. */
fun NavGraphBuilder.captureGraph(navController: NavHostController) {
    composable(Routes.CAPTURES) { CaptureInboxScreen(navController) }
    composable(Routes.CAPTURE_SETTINGS) { CaptureSettingsScreen(navController) }
}
