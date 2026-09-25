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
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smartclipboard.app.R
import com.smartclipboard.app.suggestion.QqSuggestionSettings

/** Isolates the optional accessibility feature from clipboard management. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(QqSuggestionSettings.isEnabled(context)) }
    var connected by remember { mutableStateOf(QqSuggestionSettings.isServiceConnected(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = QqSuggestionSettings.isEnabled(context)
                connected = QqSuggestionSettings.isServiceConnected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                Text(
                    stringResource(R.string.settings_title),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.settings_description), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.qq_experiment_title), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = enabled, onCheckedChange = {
                            enabled = it
                            QqSuggestionSettings.setEnabled(context, it)
                        })
                    }
                    Text(
                        stringResource(when {
                            enabled && connected -> R.string.qq_experiment_active
                            enabled -> R.string.qq_experiment_waiting
                            else -> R.string.qq_experiment_inactive
                        }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text(stringResource(R.string.qq_accessibility_settings)) }
                }
            }
        }
    }
}
