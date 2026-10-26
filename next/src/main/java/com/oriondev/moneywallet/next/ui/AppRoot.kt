package com.oriondev.moneywallet.next.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.data.prefs.Settings
import androidx.compose.runtime.CompositionLocalProvider
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.components.LocalHideAmounts
import com.oriondev.moneywallet.next.ui.theme.Motion

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val ANALYTICS = "analytics"
    const val MORE = "more"
    const val ONBOARDING = "onboarding"
    const val ADD_TRANSACTION = "transaction/new?walletId={walletId}&direction={direction}&amount={amount}&currency={currency}&description={description}&date={date}&categoryId={categoryId}&captureId={captureId}"
    const val EDIT_TRANSACTION = "transaction/{id}"
    const val TRANSACTION_DETAIL = "transaction/{id}/detail"
    const val ADD_TRANSFER = "transfer/new"
    const val WALLETS = "wallets"
    const val EDIT_WALLET = "wallet/{id}"
    const val CATEGORIES = "categories"
    const val EDIT_CATEGORY = "category/{id}?type={type}"
    const val BUDGETS = "budgets"
    const val EDIT_BUDGET = "budget/{id}"
    const val REVIEW = "review/{period}"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val CATEGORY_DETAIL = "analytics/category/{id}?from={from}&to={to}"
    // feature destinations (each feature registers its own graph)
    const val CAPTURES = "captures"
    const val CAPTURE_SETTINGS = "captures/settings"
    const val GOALS = "goals"
    const val EDIT_GOAL = "goal/{id}"
    const val RECAP = "recap/{period}"

    /** New transaction pre-filled (e.g. from a captured notification); see EditorPrefill. */
    fun addTransaction(
        walletId: Long? = null, direction: Int = 0, amountMinor: Long? = null, currency: String? = null,
        description: String? = null, date: Long? = null, categoryId: Long? = null, captureId: Long? = null,
    ): String = buildString {
        append("transaction/new?walletId=${walletId ?: -1}&direction=$direction&amount=${amountMinor ?: -1}")
        currency?.let { append("&currency=").append(android.net.Uri.encode(it)) }
        description?.let { append("&description=").append(android.net.Uri.encode(it)) }
        append("&date=${date ?: -1}&categoryId=${categoryId ?: -1}&captureId=${captureId ?: -1}")
    }
    fun editTransaction(id: Long) = "transaction/$id"
    fun transactionDetail(id: Long) = "transaction/$id/detail"
    fun editWallet(id: Long) = "wallet/$id"
    fun editCategory(id: Long, type: Int = 1) = "category/$id?type=$type"
    fun editBudget(id: Long) = "budget/$id"
    fun review(period: String) = "review/$period"
    fun categoryDetail(id: Long, from: Long, to: Long) = "analytics/category/$id?from=$from&to=$to"
    fun editGoal(id: Long) = "goal/$id"
    fun recap(period: String) = "recap/$period"
}

data class TopLevel(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

val topLevelDestinations = listOf(
    TopLevel(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TopLevel(Routes.TRANSACTIONS, "Transactions", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    TopLevel(Routes.ANALYTICS, "Analytics", Icons.Filled.Insights, Icons.Outlined.Insights),
    TopLevel(Routes.MORE, "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
)

@Composable
fun AppRoot(container: AppContainer, settings: Settings, pendingRoute: String?, onRouteConsumed: () -> Unit) {
    CompositionLocalProvider(LocalAppContainer provides container, LocalHideAmounts provides settings.hideBalances) {
    val navController = rememberNavController()
    LaunchedEffect(pendingRoute, settings.onboardingDone) {
        if (pendingRoute != null && settings.onboardingDone) {
            navController.navigate(pendingRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = topLevelDestinations.any { d -> backStack?.destination?.hierarchy?.any { it.route == d.route } == true }

    // The bar's measured height animates (clipped reveal reads as a slide), so the content padding
    // moves with it. No content insets here: each screen's own Scaffold handles the system bars, and
    // the insets covered by the bar are consumed so they are not added a second time.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = expandVertically(Motion.spatial(), expandFrom = Alignment.Top),
                exit = shrinkVertically(Motion.spatial(), shrinkTowards = Alignment.Top),
            ) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) dest.selected else dest.unselected, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        WalletNavHost(
            navController = navController,
            container = container,
            settings = settings,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
    }
}
