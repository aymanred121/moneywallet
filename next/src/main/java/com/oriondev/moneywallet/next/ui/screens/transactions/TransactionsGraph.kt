package com.oriondev.moneywallet.next.ui.screens.transactions

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.oriondev.moneywallet.next.ui.Routes
import com.oriondev.moneywallet.next.ui.screens.transactions.detail.TransactionDetailScreen
import com.oriondev.moneywallet.next.ui.screens.transactions.editor.EditorPrefill
import com.oriondev.moneywallet.next.ui.screens.transactions.editor.TransactionEditorScreen
import com.oriondev.moneywallet.next.ui.screens.transactions.transfer.TransferScreen

/**
 * Feature graph for the 'transactions' package. Register every destination this feature owns here;
 * the shared routes live in com.oriondev.moneywallet.next.ui.Routes.
 */
fun NavGraphBuilder.transactionsGraph(navController: NavHostController) {
    composable(Routes.TRANSACTIONS) {
        TransactionsScreen(
            onAdd = { walletId -> navController.navigate(Routes.addTransaction(walletId)) },
            onOpen = { id -> navController.navigate(Routes.transactionDetail(id)) },
            onTransfer = { navController.navigate(Routes.ADD_TRANSFER) },
        )
    }
    composable(
        Routes.ADD_TRANSACTION,
        arguments = listOf(
            navArgument("walletId") { type = NavType.LongType; defaultValue = -1L },
            navArgument("direction") { type = NavType.IntType; defaultValue = 0 },
            navArgument("amount") { type = NavType.LongType; defaultValue = -1L },
            navArgument("currency") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("description") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("date") { type = NavType.LongType; defaultValue = -1L },
            navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L },
            navArgument("captureId") { type = NavType.LongType; defaultValue = -1L },
        ),
    ) { entry ->
        val args = entry.arguments
        val prefill = EditorPrefill(
            amountMinor = args?.getLong("amount")?.takeIf { it > 0 },
            currency = args?.getString("currency"),
            description = args?.getString("description"),
            date = args?.getLong("date")?.takeIf { it > 0 },
            categoryId = args?.getLong("categoryId")?.takeIf { it > 0 },
            captureId = args?.getLong("captureId")?.takeIf { it > 0 },
        ).takeIf { it != EditorPrefill() }
        TransactionEditorScreen(
            id = null,
            presetWalletId = args?.getLong("walletId")?.takeIf { it > 0 },
            presetDirection = args?.getInt("direction") ?: 0,
            onClose = { navController.closeEntry(entry) },
            prefill = prefill,
            onCreateWallet = { navController.navigate(Routes.editWallet(0)) },
        )
    }
    composable(Routes.EDIT_TRANSACTION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
        TransactionEditorScreen(id = entry.arguments?.getLong("id"), presetWalletId = null, presetDirection = 0, onClose = { navController.closeEntry(entry) })
    }
    composable(Routes.TRANSACTION_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
        val id = entry.arguments?.getLong("id") ?: return@composable
        TransactionDetailScreen(
            id = id,
            onBack = { navController.popBackStack() },
            onEdit = { editId -> navController.navigate(Routes.editTransaction(editId)) },
        )
    }
    composable(Routes.ADD_TRANSFER) { entry -> TransferScreen(onClose = { navController.closeEntry(entry) }) }
}

/** Pops [entry] only while it is on top, so a late second close (Saved racing Back) can't pop the screen below. */
private fun NavHostController.closeEntry(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}
