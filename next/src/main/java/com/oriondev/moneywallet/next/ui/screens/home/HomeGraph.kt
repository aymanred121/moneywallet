package com.oriondev.moneywallet.next.ui.screens.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.oriondev.moneywallet.next.ui.Routes

/**
 * Feature graph for the 'home' package (the dashboard). Analytics and the review live in
 * [com.oriondev.moneywallet.next.ui.screens.analytics.insightsGraph].
 */
fun NavGraphBuilder.homeGraph(navController: NavHostController) {
    composable(Routes.HOME) {
        HomeScreen(onNavigate = { navController.navigate(it) })
    }
}
