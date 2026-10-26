package com.oriondev.moneywallet.next.ui.screens.analytics

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.screens.recap.RecapScreen
import com.oriondev.moneywallet.next.ui.screens.review.ReviewScreen

/** Analytics tab, category detail, the review story and the recap. Shared routes live in [Routes]. */
fun NavGraphBuilder.insightsGraph(navController: NavHostController) {
    composable(Routes.ANALYTICS) {
        AnalyticsScreen(onNavigate = { navController.navigate(it) })
    }
    composable(
        Routes.CATEGORY_DETAIL,
        arguments = listOf(
            navArgument("id") { type = NavType.LongType },
            navArgument("from") { type = NavType.LongType; defaultValue = 0L },
            navArgument("to") { type = NavType.LongType; defaultValue = 0L },
        ),
    ) { entry ->
        val args = entry.arguments
        val id = args?.getLong("id") ?: 0L
        var from = args?.getLong("from") ?: 0L
        var to = args?.getLong("to") ?: 0L
        if (from <= 0L || to <= from) {
            val m = Period.currentMonth().range
            from = m.first; to = m.last + 1
        }
        CategoryDetailScreen(categoryId = id, from = from, to = to, onBack = { navController.popBackStack() }, onNavigate = { navController.navigate(it) })
    }
    composable(
        Routes.REVIEW,
        arguments = listOf(navArgument("period") { type = NavType.StringType }),
    ) { entry ->
        val period = Period.decode(entry.arguments?.getString("period") ?: "")
        ReviewScreen(initialPeriod = period, onBack = { navController.popBackStack() }, onNavigate = { navController.navigate(it) })
    }
    composable(
        Routes.RECAP,
        arguments = listOf(navArgument("period") { type = NavType.StringType }),
    ) { entry ->
        val period = Period.decode(entry.arguments?.getString("period") ?: "")
        RecapScreen(period = period, onBack = { navController.popBackStack() })
    }
}
