package com.smartclipboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.smartclipboard.app.clipboard.ClipboardViewModel
import com.smartclipboard.app.ui.ClipboardHomeScreen

/** The Activity owns only lifecycle and window-focus interactions. */
class MainActivity : ComponentActivity() {
    private val viewModel: ClipboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { ClipboardHomeScreen(viewModel) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) viewModel.refresh()
    }
}
