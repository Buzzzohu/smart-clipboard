package com.smartclipboard.app.ui

import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartclipboard.app.suggestion.SuggestionApp
import com.smartclipboard.app.suggestion.SuggestionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(val packageName: String, val label: String)

/** Package selection is local; existing preset preference keys survive upgrades. */
@Composable
internal fun SuggestionAppsSection(connected: Boolean) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val preferences = SuggestionSettings.preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val presets = remember { listOf("QQ", "小黑盒", "bilibili", "抖音", "JMComic2", "JMComic3") }
    val apps = remember(revision) {
        SuggestionApp.entries.mapIndexed { index, app -> InstalledApp(app.packageName, presets[index]) } +
            SuggestionSettings.customPackages(context).map { InstalledApp(it, SuggestionSettings.appLabel(context, it)) }
                .sortedBy { it.label }
    }
    Text("自行添加需要输入联想的应用。能否读取和填入文字取决于应用的输入框。")
    TextButton(onClick = { adding = true }) { Text("＋ 添加应用") }
    apps.forEach { app ->
        val enabled = remember(revision, app.packageName) { SuggestionSettings.isEnabled(context, app.packageName) }
        val custom = SuggestionApp.fromPackage(app.packageName) == null
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(enabled, { SuggestionSettings.setEnabled(context, app.packageName, it) })
                }
                Text(when {
                    !enabled -> "已关闭"
                    !connected -> "请先开启无障碍服务"
                    custom -> "已开启 · 输入框兼容性待验证"
                    else -> "已开启"
                }, style = MaterialTheme.typography.bodyMedium)
                if (custom) TextButton(onClick = { SuggestionSettings.removeApp(context, app.packageName) }) { Text("移除") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (adding) AppPicker(apps.map { it.packageName }.toSet(), onClose = { adding = false }) { app ->
        SuggestionSettings.addApp(context, app.packageName, app.label)
        adding = false
    }
}

@Composable
private fun AppPicker(added: Set<String>, onClose: () -> Unit, onSelect: (InstalledApp) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        try {
            apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.packageName != context.packageName }
                    .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .distinctBy { it.packageName }.sortedBy { it.label }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
          catch (_: Exception) { failed = true }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("添加应用") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("搜索应用名称") }, singleLine = true)
            val visible = apps.orEmpty().filter { it.label.contains(query.trim(), true) || it.packageName.contains(query.trim(), true) }
            when {
                failed -> Text("无法加载应用列表，请关闭后重试。")
                apps == null -> CircularProgressIndicator()
                visible.isEmpty() -> Text("没有找到应用")
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = app.packageName !in added) { onSelect(app) }.padding(vertical = 12.dp)) {
                            Text(app.label + if (app.packageName in added) " · 已添加" else "")
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("关闭") } })
}
