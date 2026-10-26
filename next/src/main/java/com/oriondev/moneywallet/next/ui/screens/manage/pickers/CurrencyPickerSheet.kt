package com.oriondev.moneywallet.next.ui.screens.manage.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.data.db.CurrencyEntity
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import kotlinx.coroutines.launch

/** Searchable currency list; favourites first. Reusable inside sheets and full pages. */
@Composable
fun CurrencyList(
    selected: String?,
    onSelect: (CurrencyEntity) -> Unit,
    modifier: Modifier = Modifier,
    showFavouriteToggle: Boolean = true,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val currencies by remember(container) { container.db.currencies().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    val filtered = remember(currencies, query) {
        val q = query.trim()
        if (q.isEmpty()) currencies else currencies.filter { it.iso.contains(q, true) || it.name.contains(q, true) || it.symbol.contains(q, true) }
    }
    Column(modifier) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search currency") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(filtered, key = { it.iso }) { c ->
                val isSelected = c.iso == selected
                ListItem(
                    headlineContent = { Text(c.name, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                    supportingContent = { Text("${c.iso} · ${c.symbol}") },
                    leadingContent = {
                        Box(
                            Modifier.size(40.dp).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            else Text(c.symbol.take(3), style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    trailingContent = if (showFavouriteToggle) {
                        {
                            IconButton(onClick = { scope.launch { container.db.currencies().setFavourite(c.iso, !c.favourite) } }) {
                                Icon(
                                    if (c.favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (c.favourite) "Remove favourite" else "Add favourite",
                                    tint = if (c.favourite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.clickable { onSelect(c) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(selected: String?, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Text("Choose currency", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        CurrencyList(
            selected = selected,
            onSelect = { onSelect(it.iso); onDismiss() },
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(bottom = 16.dp),
        )
    }
}
