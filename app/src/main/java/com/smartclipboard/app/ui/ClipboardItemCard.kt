package com.smartclipboard.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.smartclipboard.app.R
import com.smartclipboard.app.data.ClipboardItem
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    val actionWidth = 256.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val settledOffset = remember(item.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dragging by remember(item.id) { mutableStateOf(false) }
    var dragOffset by remember(item.id) { mutableFloatStateOf(0f) }
    val visibleOffset = if (dragging) dragOffset else settledOffset.value

    // A shared state change also closes the previously opened row with the same spring.
    LaunchedEffect(revealed, dragging, actionWidthPx) {
        if (!dragging) {
            settledOffset.animateTo(
                if (revealed) -actionWidthPx else 0f,
                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.matchParentSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large
        ) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.align(Alignment.CenterEnd).width(actionWidth).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(visibleOffset.roundToInt(), 0) }
                .pointerInput(item.id, revealed) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragOffset = settledOffset.value
                            dragging = true
                        },
                        onDragEnd = {
                            val open = if (revealed) {
                                dragOffset < -actionWidthPx * 0.7f
                            } else {
                                dragOffset < -actionWidthPx * 0.3f
                            }
                            scope.launch {
                                settledOffset.snapTo(dragOffset)
                                if (open) onReveal() else onClose()
                                dragging = false
                            }
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, distance ->
                            dragOffset = (dragOffset + distance).coerceIn(-actionWidthPx, 0f)
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
