@file:OptIn(ExperimentalMaterial3Api::class)

package com.oriondev.moneywallet.next.ui.components.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.ui.components.SpecIcon

/**
 * Grid of categories for one [type] (CategoryType.EXPENSE / INCOME). Parents with children open a
 * second level where the parent itself or any child can be chosen.
 */
@Composable
fun CategoryPickerSheet(
    categories: List<CategoryEntity>,
    type: Int,
    selectedId: Long?,
    onPick: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pick = rememberPickAndHide<CategoryEntity>(sheetState, onPick, onDismiss)
    var openParent by rememberSaveable { mutableStateOf<Long?>(null) }
    val ofType = categories.filter { it.type == type }
    val parents = ofType.filter { it.parentId == null }
    val childrenOf = ofType.filter { it.parentId != null }.groupBy { it.parentId!! }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (openParent != null) IconButton(onClick = { openParent = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                val parent = parents.firstOrNull { it.id == openParent }
                Text(parent?.name ?: "Category", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            AnimatedContent(
                targetState = openParent,
                transitionSpec = {
                    val forward = targetState != null
                    (slideInHorizontally(spring(stiffness = 400f)) { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally(spring(stiffness = 400f)) { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                label = "categoryLevel",
            ) { parentId ->
                val list = if (parentId == null) parents else listOfNotNull(parents.firstOrNull { it.id == parentId }) + (childrenOf[parentId] ?: emptyList())
                if (list.isEmpty()) {
                    Text("No categories yet. Create some from More › Categories.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(list, key = { it.id }) { c ->
                            val hasChildren = parentId == null && !childrenOf[c.id].isNullOrEmpty()
                            val isParentRow = parentId != null && c.id == parentId
                            CategoryCell(
                                c = c,
                                label = if (isParentRow) "All ${c.name}" else c.name,
                                selected = c.id == selectedId,
                                badge = hasChildren,
                                onClick = { if (hasChildren) openParent = c.id else pick(c) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(c: CategoryEntity, label: String, selected: Boolean, badge: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        Modifier.clip(MaterialTheme.shapes.large).background(bg).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            SpecIcon(c.icon, size = 44.dp)
            if (badge) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.ExpandMore, contentDescription = "Has subcategories", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
