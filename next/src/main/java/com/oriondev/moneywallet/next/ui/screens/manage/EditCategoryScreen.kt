package com.oriondev.moneywallet.next.ui.screens.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.data.db.CategoryEntity
import com.oriondev.moneywallet.next.data.db.CategoryType
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.screens.manage.pickers.IconPickerSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class CategoryForm(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: Int = CategoryType.EXPENSE,
    val parentId: Long? = null,
    val icon: IconSpec? = null,
    val showReport: Boolean = true,
    val sortIndex: Int = 0,
    val tag: String? = null,
    val hasChildren: Boolean = false,
    val loaded: Boolean = false,
)

class EditCategoryViewModel(private val container: AppContainer, private val id: Long, initialType: Int) : ViewModel() {
    val form = MutableStateFlow(CategoryForm(id = id, type = initialType, loaded = id == 0L))
    val saved = MutableStateFlow(false)
    val all = container.repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (id != 0L) viewModelScope.launch {
            val c = container.db.categories().byId(id) ?: return@launch
            val kids = container.db.categories().all().any { it.parentId == id }
            form.value = CategoryForm(c.id, c.uuid, c.name, c.type, c.parentId, IconSpec.parse(c.icon), c.showReport, c.sortIndex, c.tag, kids, loaded = true)
        }
    }

    fun update(f: (CategoryForm) -> CategoryForm) { form.value = f(form.value) }

    fun save() = viewModelScope.launch {
        val f = form.value
        val icon = f.icon ?: IconSpec.letterFor(f.name, IconSpec.palette[(f.name.hashCode() and 0xffff) % IconSpec.palette.size])
        container.repo.saveCategory(
            CategoryEntity(id = f.id, uuid = f.uuid, name = f.name.trim(), icon = icon.encode(), type = f.type, parentId = f.parentId, showReport = f.showReport, sortIndex = f.sortIndex, tag = f.tag, lastEdit = System.currentTimeMillis()),
        )
        saved.value = true
    }

    fun delete() = viewModelScope.launch {
        container.db.categories().byId(id)?.let { container.repo.deleteCategory(it) }
        saved.value = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryScreen(navController: NavHostController, id: Long, type: Int) {
    val vm = containerViewModel(key = "category-$id") { EditCategoryViewModel(it, id, type) }
    val form by vm.form.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val all by vm.all.collectAsStateWithLifecycle()
    var showIcon by remember { mutableStateOf(false) }
    var showParent by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (saved) LaunchedEffect(Unit) { navController.popBackStack() }

    val previewIcon = form.icon ?: IconSpec.letterFor(form.name.ifBlank { "Category" }, IconSpec.palette[(form.name.hashCode() and 0xffff) % IconSpec.palette.size])
    // Valid parents: same type, top-level, not itself. No grandchildren: a category that has children cannot get a parent.
    val parentOptions = all.filter { it.type == form.type && it.parentId == null && it.id != form.id }
    val parent = parentOptions.firstOrNull { it.id == form.parentId }
    val isSystem = form.type == CategoryType.SYSTEM

    ManageScaffold(
        title = if (id == 0L) "New category" else "Edit category",
        onBack = { navController.popBackStack() },
        actions = { if (id != 0L && !isSystem) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") } },
    ) { padding ->
        if (!form.loaded) { CircularProgressIndicator(Modifier.padding(padding).padding(32.dp)); return@ManageScaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SpecIcon(previewIcon, size = 64.dp, modifier = Modifier.clickable(enabled = !isSystem) { showIcon = true })
                OutlinedTextField(value = form.name, onValueChange = { v -> vm.update { it.copy(name = v) } }, label = { Text("Name") }, singleLine = true, isError = form.name.isBlank(), enabled = !isSystem, modifier = Modifier.weight(1f))
            }
            if (!isSystem) TextButton(onClick = { showIcon = true }) { Text("Change icon") }
            if (!isSystem) {
                Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = form.type == CategoryType.EXPENSE, enabled = id == 0L, onClick = { vm.update { it.copy(type = CategoryType.EXPENSE, parentId = null) } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Expense") }
                    SegmentedButton(selected = form.type == CategoryType.INCOME, enabled = id == 0L, onClick = { vm.update { it.copy(type = CategoryType.INCOME, parentId = null) } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Income") }
                }
                if (id != 0L) Text("The type cannot be changed after creation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingRow(
                    title = "Parent category",
                    subtitle = when {
                        form.hasChildren -> "This category has subcategories and cannot be nested"
                        parent != null -> parent.name
                        else -> "None (top level)"
                    },
                    onClick = if (form.hasChildren) null else ({ showParent = true }),
                )
                SwitchRow("Show in reports", form.showReport, { v -> vm.update { it.copy(showReport = v) } }, subtitle = "Include in charts and category totals")
                Button(onClick = { vm.save() }, enabled = form.name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text(if (id == 0L) "Create category" else "Save changes")
                }
            } else {
                Text("System categories are managed by the app and cannot be edited.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showIcon) IconPickerSheet(current = previewIcon, nameHint = form.name, onDismiss = { showIcon = false }) { spec -> vm.update { it.copy(icon = spec) } }
    if (showParent) {
        ModalBottomSheet(onDismissRequest = { showParent = false }) {
            Text("Parent category", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                item {
                    ListItem(headlineContent = { Text("None (top level)") }, modifier = Modifier.clickable { vm.update { it.copy(parentId = null) }; showParent = false })
                }
                items(parentOptions, key = { it.id }) { p ->
                    ListItem(headlineContent = { Text(p.name) }, leadingContent = { SpecIcon(p.icon, 36.dp) }, modifier = Modifier.clickable { vm.update { it.copy(parentId = p.id) }; showParent = false })
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${form.name}\"?") },
            text = { Text("Transactions keep their data but lose this category. Subcategories become top-level categories.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
