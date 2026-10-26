@file:OptIn(ExperimentalMaterial3Api::class)

package com.oriondev.moneywallet.next.ui.components.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.data.db.CurrencyEntity
import com.oriondev.moneywallet.next.ui.components.SectionHeader

/** Bottom sheet listing every currency, favourites first, with a search field. */
@Composable
fun CurrencyPickerSheet(
    currencies: List<CurrencyEntity>,
    selected: String?,
    onPick: (CurrencyEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pick = rememberPickAndHide<CurrencyEntity>(sheetState, onPick, onDismiss)
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Text("Currency", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search code or name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.large,
            )
            val filtered = currencies.filter { query.isBlank() || it.iso.contains(query, true) || it.name.contains(query, true) || it.symbol.contains(query, true) }
            val favourites = filtered.filter { it.favourite }
            val others = filtered.filter { !it.favourite }
            LazyColumn(Modifier.fillMaxWidth()) {
                if (favourites.isNotEmpty()) {
                    item("h-fav") { SectionHeader("Favourites") }
                    items(favourites, key = { "f-${it.iso}" }) { CurrencyRow(it, it.iso == selected, pick) }
                    if (others.isNotEmpty()) item("h-all") { SectionHeader("All currencies") }
                }
                items(others, key = { it.iso }) { CurrencyRow(it, it.iso == selected, pick) }
                if (filtered.isEmpty()) item("empty") { Text("No currency matches", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun CurrencyRow(c: CurrencyEntity, selected: Boolean, onPick: (CurrencyEntity) -> Unit) {
    ListItem(
        modifier = Modifier.padding(horizontal = 8.dp).clip(MaterialTheme.shapes.medium)
            .let { m -> if (selected) m.background(MaterialTheme.colorScheme.secondaryContainer) else m }
            .clickable { onPick(c) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(c.symbol.take(3), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            }
        },
        headlineContent = { Text(c.name) },
        supportingContent = { Text(c.iso) },
        trailingContent = {
            when {
                selected -> Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                c.favourite -> Icon(Icons.Default.Star, contentDescription = "Favourite", tint = MaterialTheme.colorScheme.tertiary)
            }
        },
    )
}
