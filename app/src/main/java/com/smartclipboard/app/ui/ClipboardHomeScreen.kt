package com.smartclipboard.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartclipboard.app.R
import com.smartclipboard.app.clipboard.ClipboardStatus
import com.smartclipboard.app.clipboard.ClipboardViewModel
import com.smartclipboard.app.clipboard.LibraryMessage
import com.smartclipboard.app.data.ClipboardItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private data class Notice(val id: Long, val text: String)

/** Search, filters, and selection state are separated from the persisted Room data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardHomeScreen(viewModel: ClipboardViewModel) {
    val context = LocalContext.current
    val status by viewModel.state.collectAsStateWithLifecycle()
    val entries by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var showManualSheet by rememberSaveable { mutableStateOf(false) }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var showBatchEntry by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClipboardItem?>(null) }
    var deleting by remember { mutableStateOf<ClipboardItem?>(null) }
    var detail by remember { mutableStateOf<ClipboardItem?>(null) }
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var noticeId by remember { mutableLongStateOf(0L) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var choosingCategory by remember { mutableStateOf(false) }
    var bulkCategoryId by remember { mutableStateOf(1L) }
    LaunchedEffect(category, categories) {
        if (category != null && categories.isNotEmpty() && categories.none { it.name == category }) viewModel.setCategory(null)
    }
    LaunchedEffect(entries) { selectedIds = selectedIds.intersect(entries.map { it.id }.toSet()) }
    BackHandler(selectedIds.isNotEmpty()) { selectedIds = emptySet() }

    LaunchedEffect(viewModel) {
        launch {
            viewModel.notices.collect { resource ->
                notice = Notice(++noticeId, context.getString(resource))
            }
        }
        launch {
            viewModel.messages.collect { message ->
                notice = Notice(++noticeId, message.text(context))
            }
        }
    }
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            delay(2_000)
            notice = null
        }
    }

    if (showCategories) {
        CategoryManagerScreen(viewModel) { showCategories = false; filterOpen = true }
    } else if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else {
        BackHandler(filterOpen) { filterOpen = false }
        Box(Modifier.fillMaxSize()) {
                Scaffold { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                revealedId = null
                                filterOpen = true
                            }) { Text("☰", style = MaterialTheme.typography.headlineSmall) }
                            Text(stringResource(R.string.app_name),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.headlineSmall)
                            IconButton(onClick = {
                                revealedId = null
                                showSettings = true
                            }) { Text("⚙", style = MaterialTheme.typography.headlineSmall) }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                revealedId = null
                                viewModel.setQuery(it)
                            },
                            label = { Text(stringResource(R.string.search_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val filterName = buildString {
                            append(category ?: context.getString(R.string.filter_all))
                            if (favoritesOnly) append(" · ${context.getString(R.string.filter_favorites)}")
                        }
                        Text(stringResource(R.string.filter_current, filterName),
                            Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium)
                        Button(onClick = {
                            revealedId = null
                            showManualSheet = true
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.add_manually))
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(stringResource(R.string.saved_count, entries.size),
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        if (selectedIds.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("已选 ${selectedIds.size}", Modifier.weight(1f))
                                TextButton(onClick = { selectedIds = entries.map { it.id }.toSet() }) { Text("全选") }
                                TextButton(onClick = { selectedIds = emptySet() }) { Text("取消") }
                                TextButton(onClick = { choosingCategory = true }) { Text("改分类") }
                            }
                        }
                        if (entries.isEmpty()) {
                            Text(
                                stringResource(if (query.isBlank() && category == null && !favoritesOnly)
                                    R.string.library_empty else R.string.search_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(entries, key = { it.id }) { item ->
                                    ClipboardItemCard(
                                        item = item,
                                        revealed = revealedId == item.id,
                                        onReveal = { revealedId = item.id },
                                        onClose = { revealedId = null },
                                        selected = item.id in selectedIds,
                                        selectionMode = selectedIds.isNotEmpty(),
                                        onLongPress = { revealedId = null; selectedIds = selectedIds + item.id },
                                        onCardTap = {
                                            if (selectedIds.isNotEmpty()) {
                                                selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                                            } else if (revealedId != null) revealedId = null
                                            else viewModel.copy(item)
                                        },
                                        onEdit = { editing = item },
                                        onDelete = { deleting = item },
                                        onFavorite = { viewModel.toggleFavorite(item.id) },
                                        onExpand = { detail = item }
                                    )
                                }
                            }
                        }
                    }
                }
            notice?.let { current ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp
                ) {
                    Text(current.text, Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            FilterSidebar(
                visible = filterOpen,
                categories = categories,
                category = category,
                favoritesOnly = favoritesOnly,
                onOpen = { revealedId = null; filterOpen = true },
                onClose = { filterOpen = false },
                onManageCategories = { filterOpen = false; showCategories = true },
                onCategory = {
                    viewModel.setCategory(it)
                    revealedId = null
                    filterOpen = false
                },
                onFavorites = {
                    viewModel.setFavoritesOnly(!favoritesOnly)
                    revealedId = null
                    filterOpen = false
                }
            )
        }

        if (choosingCategory) {
            androidx.compose.material3.AlertDialog(onDismissRequest = { choosingCategory = false },
                title = { Text("移动 ${selectedIds.size} 条内容") },
                text = { CategoryPicker(categories, bulkCategoryId) { bulkCategoryId = it } },
                confirmButton = { TextButton(onClick = {
                    viewModel.moveItems(selectedIds, bulkCategoryId)
                    selectedIds = emptySet(); choosingCategory = false
                }) { Text("确定") } },
                dismissButton = { TextButton(onClick = { choosingCategory = false }) { Text("取消") } })
        }
        if (showManualSheet) {
            ModalBottomSheet(onDismissRequest = { showManualSheet = false }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text(stringResource(R.string.manual_method_title),
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        showManualSheet = false
                        showManualEntry = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.single_manual_entry))
                    }
                    TextButton(onClick = {
                        showManualSheet = false
                        showBatchEntry = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.batch_import_button))
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        if (status is ClipboardStatus.Candidate) {
            SaveConfirmationDialog(status as ClipboardStatus.Candidate, categories, viewModel::save, viewModel::ignore)
        }
        if (showManualEntry) {
            EntryEditorDialog(
                title = stringResource(R.string.single_manual_entry),
                initialContent = "",
                initialTags = "",
                categories = categories,
                onDismiss = { showManualEntry = false },
                onConfirm = { content, tags, categoryId ->
                    viewModel.addManual(content, tags, categoryId)
                    showManualEntry = false
                }
            )
        }
        if (showBatchEntry) {
            BatchImportDialog(
                categories = categories,
                onDismiss = { showBatchEntry = false },
                onConfirm = { text, categoryId ->
                    viewModel.importBatch(text, categoryId)
                    showBatchEntry = false
                }
            )
        }
        editing?.let { item ->
            EntryEditorDialog(
                title = stringResource(R.string.edit_item),
                initialContent = item.content,
                initialTags = item.tags,
                categories = categories,
                initialCategoryId = categories.firstOrNull { it.name == item.category }?.id ?: 1L,
                onDismiss = { editing = null },
                onConfirm = { content, tags, categoryId ->
                    viewModel.edit(item.id, content, tags, categoryId)
                    editing = null
                }
            )
        }
        deleting?.let { item ->
            DeleteItemDialog(
                content = item.content,
                onDismiss = { deleting = null },
                onConfirm = {
                    viewModel.delete(item.id)
                    deleting = null
                }
            )
        }
        detail?.let { item -> ClipboardDetailDialog(item, onDismiss = { detail = null }) }
    }
}

private fun LibraryMessage.text(context: Context): String = when (this) {
    LibraryMessage.Saved -> context.getString(R.string.clipboard_saved)
    LibraryMessage.Duplicate -> context.getString(R.string.clipboard_already_saved)
    LibraryMessage.DuplicateOrMissing -> context.getString(R.string.edit_duplicate_or_missing)
    LibraryMessage.Edited -> context.getString(R.string.item_edited)
    LibraryMessage.Deleted -> context.getString(R.string.item_deleted)
    LibraryMessage.Copied -> context.getString(R.string.item_copied)
    LibraryMessage.Failed -> context.getString(R.string.operation_failed)
    is LibraryMessage.BatchImported -> context.getString(R.string.batch_import_result, added, skipped)
}
