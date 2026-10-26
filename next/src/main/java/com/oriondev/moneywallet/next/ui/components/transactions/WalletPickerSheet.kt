@file:OptIn(ExperimentalMaterial3Api::class)

package com.oriondev.moneywallet.next.ui.components.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.data.db.WalletWithBalance
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.SpecIcon

/** Bottom sheet listing wallets with their balances. */
@Composable
fun WalletPickerSheet(
    wallets: List<WalletWithBalance>,
    ctx: CurrencyContext,
    selectedId: Long?,
    title: String = "Wallet",
    excludeId: Long? = null,
    onPick: (WalletWithBalance) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pick = rememberPickAndHide<WalletWithBalance>(sheetState, onPick, onDismiss)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            val list = wallets.filter { !it.archived && it.id != excludeId }
            if (list.isEmpty()) Text("No wallets available", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn {
                items(list, key = { it.id }) { w ->
                    val selected = w.id == selectedId
                    ListItem(
                        modifier = Modifier.padding(horizontal = 8.dp).clip(MaterialTheme.shapes.medium)
                            .let { m -> if (selected) m.background(MaterialTheme.colorScheme.secondaryContainer) else m }
                            .clickable { pick(w) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { SpecIcon(w.icon, size = 40.dp) },
                        headlineContent = { Text(w.name) },
                        supportingContent = { Text(w.currency) },
                        trailingContent = {
                            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            else AmountText(w.balance, ctx.currency(w.currency), style = MaterialTheme.typography.bodyMedium)
                        },
                    )
                }
            }
        }
    }
}
