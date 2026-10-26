package com.oriondev.moneywallet.next.ui.screens.manage

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.ui.Routes

/**
 * Feature graph for the 'manage' package: More tab, wallets, categories, budgets, settings,
 * backup and onboarding. Shared routes live in com.oriondev.moneywallet.next.ui.Routes.
 */
fun NavGraphBuilder.manageGraph(navController: NavHostController) {
    composable(Routes.ONBOARDING) { OnboardingScreen(navController) }
    composable(Routes.MORE) { MoreScreen(navController) }
    composable(Routes.WALLETS) { WalletsScreen(navController) }
    composable(Routes.EDIT_WALLET, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
        EditWalletScreen(navController, entry.arguments?.getLong("id") ?: 0L)
    }
    composable(Routes.CATEGORIES) { CategoriesScreen(navController) }
    composable(
        Routes.EDIT_CATEGORY,
        arguments = listOf(
            navArgument("id") { type = NavType.LongType },
            navArgument("type") { type = NavType.IntType; defaultValue = CategoryType.EXPENSE },
        ),
    ) { entry ->
        EditCategoryScreen(navController, entry.arguments?.getLong("id") ?: 0L, entry.arguments?.getInt("type") ?: CategoryType.EXPENSE)
    }
    composable(Routes.BUDGETS) { BudgetsScreen(navController) }
    composable(Routes.EDIT_BUDGET, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
        EditBudgetScreen(navController, entry.arguments?.getLong("id") ?: 0L)
    }
    composable(Routes.SETTINGS) { SettingsScreen(navController) }
    composable(Routes.BACKUP) { BackupScreen(navController) }
}
