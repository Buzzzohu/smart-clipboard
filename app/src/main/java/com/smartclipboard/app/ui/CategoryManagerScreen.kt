package com.smartclipboard.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartclipboard.app.clipboard.ClipboardViewModel
import com.smartclipboard.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CategoryManagerScreen(viewModel: ClipboardViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<LibraryCategory?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LibraryCategory?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Scaffold { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text("分类管理", style = MaterialTheme.typography.headlineSmall)
        }
        Text("分类图标会显示在侧栏和悬浮候选左侧。", Modifier.padding(vertical = 12.dp))
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("新建分类") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f)) {
            items(categories, key = { it.id }) { category ->
                Row(Modifier.fillMaxWidth().clickable(enabled = category.id != 1L && !busy) { editing = category }
                    .padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(category, Modifier.size(36.dp))
                    Text(category.name, Modifier.weight(1f).padding(horizontal = 12.dp))
                    if (category.id == 1L) Text("固定保留", style = MaterialTheme.typography.labelSmall)
                    else TextButton(onClick = { deleting = category }, enabled = !busy) { Text("删除") }
                }
            }
        }
    } }
    if (creating || editing != null) CategoryEditorDialog(editing,
        onDismiss = { creating = false; editing = null },
        onSave = { name, icon -> viewModel.saveCategory(editing?.id, name, icon); Unit })
    deleting?.let { category ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("删除 ${category.name}？") },
            text = { Text("内容会保留并移到未分类。") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try { viewModel.deleteCategory(category.id); CategoryIcons.remove(context, category.iconFile); deleting = null }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "删除失败，请重试" }
                    finally { busy = false }
                }
            }) { Text("删除分类") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("取消") } })
    }
}

@Composable
private fun CategoryEditorDialog(category: LibraryCategory?, onDismiss: () -> Unit,
    onSave: suspend (String, String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var cropped by remember { mutableStateOf<Bitmap?>(null) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var resetIcon by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { source = CategoryIcons.decode(context, uri) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "无法读取图片，请选择其他图片" }
            finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (category == null) "新建分类" else "编辑分类") },
        text = { Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("分类名称（最多24字）") }, singleLine = true, enabled = !busy)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cropped != null) androidx.compose.foundation.Image(cropped!!.asImageBitmap(), null, Modifier.size(48.dp))
                else CategoryIcon(if (resetIcon) null else category, Modifier.size(48.dp))
                TextButton(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("选择图片") }
                TextButton(onClick = { cropped = null; resetIcon = true }, enabled = !busy) { Text("默认图标") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = !busy && name.trim().length in 1..24, onClick = {
            busy = true
            scope.launch {
                var newFile: String? = null
                var committed = false
                try {
                    CategoryRules.name(name)
                    newFile = cropped?.let { CategoryIcons.save(context, it) }
                    val icon = newFile ?: if (resetIcon) null else category?.iconFile
                    onSave(name, icon)
                    committed = true
                    if (icon != category?.iconFile) CategoryIcons.remove(context, category?.iconFile)
                    onDismiss()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "保存失败，请重试" }
                finally {
                    if (!committed && newFile != null) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { CategoryIcons.remove(context, newFile) }
                    busy = false
                }
            }
        }) { Text("保存") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } })
    source?.let { bitmap -> IconCropDialog(bitmap, onDismiss = { source = null }, onConfirm = { cropped = it; resetIcon = false; source = null }) }
}
