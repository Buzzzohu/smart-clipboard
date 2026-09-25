package com.smartclipboard.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smartclipboard.app.R
import com.smartclipboard.app.clipboard.ClipboardStatus
import com.smartclipboard.app.clipboard.ClipboardViewModel
import com.smartclipboard.app.clipboard.LibraryMessage
import com.smartclipboard.app.data.ClipboardItem
import com.smartclipboard.app.suggestion.QqSuggestionSettings
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.collect

/** Room's observable query updates this screen after every save or edit. */
@Composable
fun ClipboardHomeScreen(viewModel: ClipboardViewModel) {
    val status by viewModel.state.collectAsStateWithLifecycle()
    val entries by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var showBatchEntry by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClipboardItem?>(null) }
    var deleting by remember { mutableStateOf<ClipboardItem?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(message.text(context))
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.import_instruction), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::refresh, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.refresh_clipboard))
                }
                Button(onClick = { showManualEntry = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.add_manually))
                }
            }
            TextButton(onClick = { showBatchEntry = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.batch_import_button))
            }
            QqExperimentCard()
            Spacer(Modifier.height(12.dp))
            ImportStatusCard(status)
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.saved_count, entries.size),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(if (query.isBlank()) R.string.library_empty else R.string.search_empty),
                    style = MaterialTheme.typography.bodyMedium
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
                            onCopy = { viewModel.copy(item) },
                            onEdit = { editing = item },
                            onDelete = { deleting = item },
                            onFavorite = { viewModel.toggleFavorite(item.id) }
                        )
                    }
                }
            }
        }
    }

    if (status is ClipboardStatus.Candidate) {
        SaveConfirmationDialog(status as ClipboardStatus.Candidate, viewModel::save, viewModel::ignore)
    }
    if (showManualEntry) {
        EntryEditorDialog(
            title = stringResource(R.string.add_manually),
            initialContent = "",
            initialTags = "",
            onDismiss = { showManualEntry = false },
            onConfirm = { content, tags ->
                viewModel.addManual(content, tags)
                showManualEntry = false
            }
        )
    }
    if (showBatchEntry) {
        BatchImportDialog(
            onDismiss = { showBatchEntry = false },
            onConfirm = { text ->
                viewModel.importBatch(text)
                showBatchEntry = false
            }
        )
    }
    editing?.let { item ->
        EntryEditorDialog(
            title = stringResource(R.string.edit_item),
            initialContent = item.content,
            initialTags = item.tags,
            onDismiss = { editing = null },
            onConfirm = { content, tags ->
                viewModel.edit(item.id, content, tags)
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
}

/** The service is opt-in and can also be stopped instantly with this switch. */
@Composable
private fun QqExperimentCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(QqSuggestionSettings.isEnabled(context)) }
    var connected by remember { mutableStateOf(QqSuggestionSettings.isServiceConnected(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = QqSuggestionSettings.isEnabled(context)
                connected = QqSuggestionSettings.isServiceConnected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.qq_experiment_title), style = MaterialTheme.typography.titleSmall)
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    QqSuggestionSettings.setEnabled(context, it)
                })
            }
            Text(
                stringResource(when {
                    enabled && connected -> R.string.qq_experiment_active
                    enabled -> R.string.qq_experiment_waiting
                    else -> R.string.qq_experiment_inactive
                }),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) {
                Text(stringResource(R.string.qq_accessibility_settings))
            }
        }
    }
}

@Composable
private fun ImportStatusCard(status: ClipboardStatus) {
    val heading = when (status) {
        ClipboardStatus.Empty -> stringResource(R.string.clipboard_empty)
        ClipboardStatus.SkippedByPolicy -> stringResource(R.string.clipboard_ignored_by_rule)
        ClipboardStatus.ReadFailed -> stringResource(R.string.clipboard_read_failed)
        is ClipboardStatus.Candidate -> stringResource(R.string.clipboard_found)
        is ClipboardStatus.AlreadySaved -> stringResource(R.string.clipboard_already_saved)
        is ClipboardStatus.IgnoredByUser -> stringResource(R.string.clipboard_ignored_by_user)
        is ClipboardStatus.Saved -> stringResource(R.string.clipboard_saved)
        is ClipboardStatus.SaveFailed -> stringResource(R.string.clipboard_save_failed)
    }
    val content = when (status) {
        is ClipboardStatus.Candidate -> status.content
        is ClipboardStatus.AlreadySaved -> status.content
        is ClipboardStatus.IgnoredByUser -> status.content
        is ClipboardStatus.Saved -> status.content
        is ClipboardStatus.SaveFailed -> status.content
        else -> null
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(heading, style = MaterialTheme.typography.titleSmall)
            if (content != null) {
                Spacer(Modifier.height(6.dp))
                Text(content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ClipboardItemCard(
    item: ClipboardItem,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onCopy)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(item.category, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                if (item.favorite) Text("★", color = MaterialTheme.colorScheme.primary)
            }
            if (item.tags.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(item.tags, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            val date = remember(item.createdTime) {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(item.createdTime))
            }
            Text(
                stringResource(R.string.item_meta, date, item.useCount),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.copy_item))
                }
                TextButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.edit_item))
                }
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.delete_item))
                }
                TextButton(onClick = onFavorite, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (item.favorite) R.string.unfavorite_item else R.string.favorite_item))
                }
            }
        }
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
