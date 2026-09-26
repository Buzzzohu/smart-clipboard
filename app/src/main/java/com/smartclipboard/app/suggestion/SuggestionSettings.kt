package com.smartclipboard.app.suggestion

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/** Per-app switches share one accessibility service; the old QQ preference is preserved. */
internal object SuggestionSettings {
    private const val PREFS = "qq_suggestion"
    private const val KEY_QQ_ENABLED = "enabled"
    private const val KEY_HEYBOX_ENABLED = "heybox_enabled"

    fun isEnabled(context: Context, app: SuggestionApp): Boolean =
        preferences(context).getBoolean(app.preferenceKey(), false)

    fun setEnabled(context: Context, app: SuggestionApp, enabled: Boolean) {
        preferences(context).edit().putBoolean(app.preferenceKey(), enabled).apply()
    }

    fun isAnyEnabled(context: Context): Boolean =
        SuggestionApp.entries.any { isEnabled(context, it) }

    fun isServiceConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val component = "${context.packageName}/${QqSuggestionService::class.java.name}"
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id == component }
    }

    fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun SuggestionApp.preferenceKey(): String = when (this) {
        SuggestionApp.QQ -> KEY_QQ_ENABLED
        SuggestionApp.HEYBOX -> KEY_HEYBOX_ENABLED
        SuggestionApp.BILIBILI -> "bilibili_enabled"
        SuggestionApp.DOUYIN -> "douyin_enabled"
    }
}
