package com.smartclipboard.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.painterResource
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit,
    onExpand: () -> Unit
) {
    val actionWidth = 180.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val settledOffset = remember(item.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dragging by remember(item.id) { mutableStateOf(false) }
    var dragOffset by remember(item.id) { mutableFloatStateOf(0f) }
    var releaseVelocity by remember(item.id) { mutableFloatStateOf(0f) }
    val velocityTracker = remember(item.id) { VelocityTracker() }

    // A shared state change also closes the previously opened row with the same spring.
    LaunchedEffect(revealed, dragging, actionWidthPx) {
        if (!dragging) {
            settledOffset.animateTo(
                if (revealed) -actionWidthPx else 0f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f),
                initialVelocity = releaseVelocity
            )
            releaseVelocity = 0f
        }
    }

    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Row(
            Modifier.align(Alignment.CenterEnd).width(actionWidth).padding(start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardAction(stringResource(R.string.edit_item), R.drawable.ic_action_edit, revealed) { onEdit(); onClose() }
            CardAction(stringResource(if (item.favorite) R.string.unfavorite_item else R.string.favorite_item),
                R.drawable.ic_action_star, revealed, selected = item.favorite) { onFavorite(); onClose() }
            CardAction(stringResource(R.string.delete_item), R.drawable.ic_action_delete, revealed) { onDelete(); onClose() }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset((if (dragging) dragOffset else settledOffset.value).roundToInt(), 0) }
                .pointerInput(item.id, revealed) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                            releaseVelocity = 0f
                            dragOffset = settledOffset.value
                            dragging = true
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity().x
                            val open = when {
                                velocity < -900f -> true
                                velocity > 900f -> false
                                else -> dragOffset < -actionWidthPx * 0.5f
                            }
                            releaseVelocity = velocity.coerceIn(-4000f, 4000f)
                            scope.launch {
                                settledOffset.snapTo(dragOffset)
                                if (open) onReveal() else onClose()
                                dragging = false
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                settledOffset.snapTo(dragOffset)
                                releaseVelocity = 0f
                                dragging = false
                            }
                        },
                        onHorizontalDrag = { change, distance ->
                            // The card itself moves; track in its parent's coordinates.
                            velocityTracker.addPosition(change.uptimeMillis,
                                change.position + androidx.compose.ui.geometry.Offset(dragOffset, 0f))
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
private fun CardAction(
    label: String,
    icon: Int,
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(22.dp))
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
