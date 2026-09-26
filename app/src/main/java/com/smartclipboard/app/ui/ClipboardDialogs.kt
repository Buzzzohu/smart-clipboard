package com.smartclipboard.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartclipboard.app.R
import com.smartclipboard.app.clipboard.ClipboardStatus
import com.smartclipboard.app.data.LibraryCategory

@Composable
fun SaveConfirmationDialog(
    candidate: ClipboardStatus.Candidate,
    categories: List<LibraryCategory>,
    initialCategoryId: Long,
    onSave: (Long) -> Unit,
    onIgnore: () -> Unit
) {
    var expanded by rememberSaveable(candidate.content) { mutableStateOf(false) }
    var categoryId by rememberSaveable(candidate.content) { mutableStateOf(initialCategoryId) }
    // Resolve only for display/save: the category list loads asynchronously on first opening.
    val selectedCategoryId = categoryId.takeIf { id -> categories.any { it.id == id } } ?: 1L
    AlertDialog(
        onDismissRequest = { if (!candidate.isSaving) onIgnore() },
        title = { Text(stringResource(R.string.save_dialog_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                CategoryPicker(categories, selectedCategoryId, enabled = !candidate.isSaving && categories.isNotEmpty()) { categoryId = it }
                Text(
                    text = candidate.content,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis
                )
                if (candidate.content.length > 120 || candidate.content.count { it == '\n' } >= 5) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.collapse_content else R.string.expand_content))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedCategoryId) }, enabled = !candidate.isSaving && categories.isNotEmpty()) {
                Text(stringResource(R.string.save_to_library))
            }
        },
        dismissButton = {
            TextButton(onClick = onIgnore, enabled = !candidate.isSaving) {
                Text(stringResource(R.string.ignore_clipboard))
            }
        }
    )
}

/** Used by manual creation and editing; validation stays in the repository too. */
@Composable
fun EntryEditorDialog(
    title: String,
    initialContent: String,
    initialTags: String,
    categories: List<LibraryCategory>,
    initialCategoryId: Long = 1,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long) -> Unit
) {
    var content by rememberSaveable(initialContent) { mutableStateOf(initialContent) }
    var tags by rememberSaveable(initialTags) { mutableStateOf(initialTags) }
    var categoryId by rememberSaveable(initialCategoryId) { mutableStateOf(initialCategoryId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                CategoryPicker(categories, categoryId) { categoryId = it }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.content_label)) },
                    minLines = 3,
                    maxLines = 7
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.tags_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(content.trim(), tags.trim(), categoryId) }, enabled = content.isNotBlank()) {
                Text(stringResource(R.string.save_item))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun BatchImportDialog(categories: List<LibraryCategory>, onDismiss: () -> Unit, onConfirm: (String, Long) -> Unit) {
    var pastedText by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(1L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_import_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.batch_import_hint))
                CategoryPicker(categories, categoryId) { categoryId = it }
                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it },
                    label = { Text(stringResource(R.string.batch_import_label)) },
                    minLines = 6,
                    maxLines = 12
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pastedText, categoryId) }, enabled = pastedText.isNotBlank()) {
                Text(stringResource(R.string.batch_import_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun DeleteItemDialog(content: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(content, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_item)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
