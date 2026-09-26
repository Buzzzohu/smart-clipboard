package com.smartclipboard.app.data

import android.content.Context

object ImportSettings {
    private fun preferences(context: Context) = context.getSharedPreferences("import_settings", Context.MODE_PRIVATE)
    fun stripSender(context: Context): Boolean = preferences(context).getBoolean("strip_sender", true)
    fun setStripSender(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("strip_sender", enabled).apply()
    }
}
