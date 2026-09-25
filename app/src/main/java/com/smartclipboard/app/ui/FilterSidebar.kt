package com.smartclipboard.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartclipboard.app.R
import com.smartclipboard.app.classification.ClipboardCategory

/** Left edge gesture keeps list-card swipes independent of library filtering. */
@Composable
fun BoxScope.FilterSidebar(
    visible: Boolean,
    category: String?,
    favoritesOnly: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onCategory: (String?) -> Unit,
    onFavorites: () -> Unit
) {
    val width = (LocalConfiguration.current.screenWidthDp / 2).dp
    val threshold = with(LocalDensity.current) { 42.dp.toPx() }
    var draggedBy by remember { mutableFloatStateOf(0f) }

    if (!visible) {
        // The 20 dp edge zone does not cover the cards' visible content.
        Box(
            Modifier.align(Alignment.CenterStart).width(20.dp).fillMaxHeight()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { draggedBy = 0f },
                        onDragEnd = { if (draggedBy > threshold) onOpen() },
                        onHorizontalDrag = { change, distance ->
                            draggedBy += distance
                            change.consume()
                        }
                    )
                }
        )
    } else {
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.CenterStart),
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.width(width).fillMaxHeight()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { draggedBy = 0f },
                        onDragEnd = { if (draggedBy < -threshold) onClose() },
                        onHorizontalDrag = { change, distance ->
                            draggedBy += distance
                            change.consume()
                        }
                    )
                },
            shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.filter_title),
                    Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider()
                FilterOption(stringResource(R.string.filter_all), category == null) { onCategory(null) }
                ClipboardCategory.entries.forEach { item ->
                    FilterOption(item.label, category == item.label) { onCategory(item.label) }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                FilterOption(stringResource(R.string.filter_favorites), favoritesOnly, onFavorites)
            }
        }
    }
}

@Composable
private fun FilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 11.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
