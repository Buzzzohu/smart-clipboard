package com.smartclipboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.smartclipboard.app.clipboard.ClipboardViewModel
import com.smartclipboard.app.ui.ClipboardHomeScreen
import com.smartclipboard.app.ui.SmartClipboardTheme

/** The Activity owns only lifecycle and window-focus interactions. */
class MainActivity : ComponentActivity() {
    private val viewModel: ClipboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartClipboardTheme { ClipboardHomeScreen(viewModel) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) viewModel.refresh(passive = true)
    }
}
