package com.oriondev.moneywallet.next.ui.screens.manage.pickers

import com.oriondev.moneywallet.next.ui.components.SymbolIcons
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import com.oriondev.moneywallet.next.ui.theme.Motion
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.data.repo.IconCatalog
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.bestOnColor
import com.oriondev.moneywallet.next.ui.components.parseColor

/**
 * Icon picker shared by wallets, categories and goals. Three modes: Material symbols on a colour
 * (grouped, searchable), the classic illustrated icons, or coloured letters derived from
 * [nameHint]. The colour is shared by symbols and letters. Shows a live preview at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(current: IconSpec, nameHint: String, onDismiss: () -> Unit, onPick: (IconSpec) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(when (current) { is IconSpec.Resource -> MODE_CLASSIC; is IconSpec.Letter -> MODE_LETTERS; is IconSpec.Symbol -> MODE_SYMBOLS }) }
    var draft by remember { mutableStateOf(current) }
    var letters by remember { mutableStateOf((current as? IconSpec.Letter)?.text ?: (IconSpec.letterFor(nameHint, IconSpec.palette.first()) as IconSpec.Letter).text) }
    var color by remember {
        mutableStateOf(
            when (current) {
                is IconSpec.Letter -> current.color
                is IconSpec.Symbol -> current.color
                else -> IconSpec.palette.first()
            },
        )
    }
    var query by remember { mutableStateOf("") }
    val classic = remember { IconCatalog.names.sorted() }
    val symbolGroups = remember(query) {
        val q = query.trim()
        SymbolIcons.groups.mapNotNull { (group, list) ->
            val hits = if (q.isEmpty()) list else list.filter { it.label.contains(q, ignoreCase = true) || group.contains(q, ignoreCase = true) }
            hits.takeIf { it.isNotEmpty() }?.let { group to it }
        }
    }
    val classicHits = remember(query, classic) { classic.filter { it.removePrefix("ic_icon_").contains(query.trim().replace(' ', '_'), ignoreCase = true) } }

    fun symbol(key: String) = IconSpec.Symbol(key, color, (SymbolIcons.label(key) ?: key).take(2).uppercase())
    fun recolor(hex: String) {
        color = hex
        draft = when (val d = draft) {
            is IconSpec.Symbol -> d.copy(color = hex)
            is IconSpec.Letter -> d.copy(color = hex)
            else -> d
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SpecIcon(draft, size = 64.dp)
                Text("Choose an icon", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(MODE_SYMBOLS to "Symbols", MODE_CLASSIC to "Classic", MODE_LETTERS to "Letters").forEachIndexed { i, (m, label) ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = {
                            mode = m
                            draft = when (m) {
                                MODE_LETTERS -> IconSpec.Letter(color, letters.ifBlank { "?" })
                                MODE_CLASSIC -> (draft as? IconSpec.Resource) ?: classic.firstOrNull()?.let { IconSpec.Resource(it) } ?: draft
                                else -> (draft as? IconSpec.Symbol) ?: symbol(SymbolIcons.groups.first().second.first().key)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (mode != MODE_LETTERS) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search icons") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }
            if (mode != MODE_CLASSIC) {
                // one row of colours, shared by symbols and letters
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(IconSpec.palette, key = { it }) { hex ->
                        val selected = hex == color
                        val scale by animateFloatAsState(if (selected) 1f else 0.82f, Motion.spatialFast(), label = "swatch")
                        Box(
                            Modifier.size(48.dp).scale(scale).clip(CircleShape).background(parseColor(hex))
                                .clickable(onClickLabel = "Use this colour") { recolor(hex) }
                                .semantics { this.selected = selected },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = bestOnColor(parseColor(hex))) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            when (mode) {
                MODE_LETTERS -> OutlinedTextField(
                    value = letters, onValueChange = { v -> letters = v.take(2).uppercase(); draft = IconSpec.Letter(color, letters.ifBlank { "?" }) },
                    label = { Text("Letters (max 2)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                MODE_CLASSIC -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(classicHits, key = { it }) { name ->
                        IconCell(selected = (draft as? IconSpec.Resource)?.name == name, label = name.removePrefix("ic_icon_").replace('_', ' '), onClick = { draft = IconSpec.Resource(name) }) {
                            SpecIcon(IconSpec.Resource(name), size = 48.dp)
                        }
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    symbolGroups.forEach { (group, list) ->
                        item(key = "h:$group", span = { GridItemSpan(maxLineSpan) }) {
                            Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(list, key = { it.key }) { sym ->
                            IconCell(selected = (draft as? IconSpec.Symbol)?.key == sym.key, label = sym.label, onClick = { draft = symbol(sym.key) }) {
                                SpecIcon(IconSpec.Symbol(sym.key, color), size = 48.dp)
                            }
                        }
                    }
                    if (symbolGroups.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("No icon matches \"$query\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }) { Text("Cancel") }
                Button(onClick = { onPick(draft); scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }) { Text("Use icon") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IconCell(selected: Boolean, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f), Motion.effects(), label = "border")
    Box(
        Modifier.size(56.dp).clip(CircleShape).border(2.dp, border, CircleShape)
            .clickable(onClickLabel = "Choose") { onClick() }
            .semantics { contentDescription = label; this.selected = selected }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

private const val MODE_SYMBOLS = 0
private const val MODE_CLASSIC = 1
private const val MODE_LETTERS = 2
