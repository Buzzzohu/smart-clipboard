package com.smartclipboard.app.clipboard

import android.content.ClipboardManager
import android.content.Context

/** Reads text only while the app is foregrounded and focused. */
class ClipboardTextReader(context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun read(): String? {
        return try {
            val clip = clipboard.primaryClip ?: return null
            if (clip.itemCount == 0 || !clip.description.hasMimeType("text/*")) return null
            clip.getItemAt(0).text?.toString()
        } catch (_: SecurityException) {
            // Access may be revoked when the app loses focus during this read.
            null
        }
    }
}
