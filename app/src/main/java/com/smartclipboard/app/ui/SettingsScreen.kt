package com.smartclipboard.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Surface
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smartclipboard.app.R
import com.smartclipboard.app.data.ImportSettings
import com.smartclipboard.app.clipboard.ClipboardViewModel
import com.smartclipboard.app.suggestion.SuggestionApp
import com.smartclipboard.app.suggestion.SuggestionSettings

/** Each supported app opts in separately; all switches share the existing service. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var showApps by rememberSaveable { mutableStateOf(false) }
    val navigateBack: () -> Unit = { if (showApps) showApps = false else onBack() }
    // Separate page instances keep scrolling and system Back behavior independent.
    key(showApps) {
        SettingsPage(showApps, navigateBack, onOpenApps = { showApps = true })
    }
}

@Composable
private fun SettingsPage(appsPage: Boolean, onBack: () -> Unit, onOpenApps: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var connected by remember { mutableStateOf(SuggestionSettings.isServiceConnected(context)) }
    var fuzzyEnabled by remember { mutableStateOf(SuggestionSettings.isFuzzyEnabled(context)) }
    var stripSender by remember { mutableStateOf(ImportSettings.stripSender(context)) }
    var overlayOpacity by remember { mutableStateOf(SuggestionSettings.overlayOpacity(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                connected = SuggestionSettings.isServiceConnected(context)
                fuzzyEnabled = SuggestionSettings.isFuzzyEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                Text(stringResource(if (appsPage) R.string.app_suggestions_menu else R.string.settings_title), Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(24.dp))
            if (appsPage) {
                SuggestionAppsSection(connected)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("悬浮窗透明度  ${((1f - overlayOpacity) * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium)
                        Text("向右滑动更透明，文字和背景一起调整",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = 1f - overlayOpacity, valueRange = 0f..0.8f,
                            onValueChange = {
                                overlayOpacity = 1f - it
                                SuggestionSettings.setOverlayOpacity(context, overlayOpacity)
                            })
                        Surface(Modifier.fillMaxWidth().alpha(overlayOpacity),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface) {
                            Text("📋 候选内容预览", Modifier.padding(14.dp))
                        }
                        Text("0% 不透明 · 80% 更透明", Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.strip_sender_title), Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium)
                            Switch(checked = stripSender, onCheckedChange = {
                                stripSender = it
                                ImportSettings.setStripSender(context, it)
                            })
                        }
                        Text(stringResource(R.string.strip_sender_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_suggestions_menu), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.app_suggestions_menu_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.fuzzy_search_title), Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium)
                            Switch(checked = fuzzyEnabled, onCheckedChange = {
                                fuzzyEnabled = it
                                SuggestionSettings.setFuzzyEnabled(context, it)
                            })
                        }
                        Text(stringResource(R.string.fuzzy_search_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text(stringResource(R.string.qq_accessibility_settings)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
