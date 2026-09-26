package com.smartclipboard.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.smartclipboard.app.R
import com.smartclipboard.app.data.*

@Composable
fun CategoryIcon(category: LibraryCategory?, modifier: Modifier = Modifier.size(24.dp)) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, category?.iconFile) {
        value = CategoryIcons.load(context, category?.iconFile)
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, modifier.clip(RoundedCornerShape(5.dp)))
    else Icon(painterResource(R.drawable.ic_sidebar_library), null, modifier)
}

@Composable
fun CategoryPicker(categories: List<LibraryCategory>, selectedId: Long, enabled: Boolean = true, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) {
            CategoryIcon(categories.firstOrNull { it.id == selectedId })
            Spacer(Modifier.width(8.dp))
            Text("分类：${categories.firstOrNull { it.id == selectedId }?.name ?: UNCATEGORIZED} ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.name) }, leadingIcon = { CategoryIcon(category) },
                    onClick = { onSelect(category.id); open = false })
            }
        }
    }
}
