package com.smartclipboard.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartclipboard.app.R
import com.smartclipboard.app.data.ClipboardItem
import java.text.DateFormat
import java.util.Date

/** Only one row is revealed at a time; the host owns that shared state. */
@Composable
fun ClipboardItemCard(
    item: ClipboardItem,
    revealed: Boolean,
    onReveal: () -> Unit,
    onClose: () -> Unit,
    onCardTap: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit,
    onExpand: () -> Unit
) {
    val gestureThreshold = with(LocalDensity.current) { 52.dp.toPx() }
    var draggedBy by remember(item.id) { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(item.id, revealed) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (draggedBy < -gestureThreshold) onReveal()
                            if (draggedBy > gestureThreshold) onClose()
                            draggedBy = 0f
                        },
                        onDragCancel = { draggedBy = 0f },
                        onHorizontalDrag = { change, distance ->
                            draggedBy += distance
                            change.consume()
                        }
                    )
                }
                .clickable(onClick = onCardTap),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(item.content, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small) {
                        Text(item.category, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                    if (item.favorite) Text("  ★", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onExpand) { Text(stringResource(R.string.expand_content)) }
                }
                if (item.tags.isNotBlank()) {
                    Text(item.tags, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val savedAt = remember(item.createdTime) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(item.createdTime))
                }
                Text(savedAt, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(
            visible = revealed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CardAction(stringResource(R.string.copy_item), false) { onCopy(); onClose() }
                    CardAction(stringResource(R.string.edit_item), false) { onEdit(); onClose() }
                    CardAction(stringResource(if (item.favorite) R.string.unfavorite_item else R.string.favorite_item), false) {
                        onFavorite(); onClose()
                    }
                    CardAction(stringResource(R.string.delete_item), true) { onDelete(); onClose() }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CardAction(
    label: String,
    destructive: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ClipboardDetailDialog(item: ClipboardItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.content_detail_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(item.content)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
