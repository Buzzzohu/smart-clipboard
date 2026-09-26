package com.smartclipboard.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartclipboard.app.R
import com.smartclipboard.app.classification.ClipboardCategory
import com.smartclipboard.app.data.LibraryCategory

private val sidebarBackground = Color.White
private val sidebarText = Color(0xFF1A1D24)

/** Left edge gesture keeps list-card swipes independent of library filtering. */
@Composable
fun BoxScope.FilterSidebar(
    visible: Boolean,
    categories: List<LibraryCategory>,
    category: String?,
    favoritesOnly: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onManageCategories: () -> Unit,
    onCategory: (String?) -> Unit,
    onFavorites: () -> Unit
) {
    val width = (LocalConfiguration.current.screenWidthDp * 0.8f).dp.coerceAtMost(360.dp)
    BackHandler(enabled = visible, onBack = onClose)
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
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).clickable(
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
            color = sidebarBackground,
            shadowElevation = 0.dp
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Smart Clipboard", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, color = sidebarText)
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_sidebar_close),
                            contentDescription = stringResource(R.string.close), tint = sidebarText)
                    }
                }
              Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(28.dp))
                SidebarHeading("资料库")
                FilterOption(stringResource(R.string.filter_all), category == null, R.drawable.ic_sidebar_library) { onCategory(null) }
                Spacer(Modifier.height(24.dp))
                SidebarHeading("分类")
                categories.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (category == item.name) Color(0xFFF0F0F0) else Color.Transparent)
                        .clickable { onCategory(item.name) }.padding(horizontal = 14.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(item)
                        Spacer(Modifier.width(18.dp))
                        Text(item.name, style = MaterialTheme.typography.bodyLarge, color = sidebarText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(24.dp))
                SidebarHeading("筛选")
                FilterOption(stringResource(R.string.filter_favorites), favoritesOnly, R.drawable.ic_action_star, onFavorites)
                Spacer(Modifier.height(24.dp))
              }
              HorizontalDivider(color = Color(0xFFEDEDED))
              FilterOption("分类管理", false, R.drawable.ic_action_edit, onManageCategories)
              Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SidebarHeading(title: String) {
    Text(title, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = Color(0xFF777777))
}

@Composable
private fun FilterOption(label: String, selected: Boolean, icon: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFFF0F0F0) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = sidebarText,
            modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = sidebarText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
