package com.smartclipboard.app.suggestion

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/** Per-app switches share one accessibility service; the old QQ preference is preserved. */
internal object SuggestionSettings {
    private const val PREFS = "qq_suggestion"
    private const val KEY_QQ_ENABLED = "enabled"
    private const val KEY_HEYBOX_ENABLED = "heybox_enabled"
    const val KEY_FUZZY_ENABLED = "fuzzy_enabled"
    const val KEY_OVERLAY_OPACITY = "overlay_opacity"
    private const val CUSTOM_APPS = "custom_apps"

    fun customPackages(context: Context): Set<String> =
        preferences(context).getStringSet(CUSTOM_APPS, emptySet()).orEmpty().toSet()

    fun appLabel(context: Context, packageName: String): String =
        preferences(context).getString("label:$packageName", packageName) ?: packageName

    fun addApp(context: Context, packageName: String, label: String) {
        require(packageName != context.packageName && packageName.isNotBlank())
        if (SuggestionApp.fromPackage(packageName) != null) return
        preferences(context).edit()
            .putStringSet(CUSTOM_APPS, customPackages(context) + packageName)
            .putString("label:$packageName", label)
            .putBoolean("app:$packageName", true).apply()
    }

    fun removeApp(context: Context, packageName: String) {
        preferences(context).edit()
            .putStringSet(CUSTOM_APPS, customPackages(context) - packageName)
            .remove("label:$packageName").remove("app:$packageName").apply()
    }

    fun isEnabled(context: Context, packageName: String?): Boolean {
        if (packageName == null || packageName == context.packageName) return false
        val preset = SuggestionApp.fromPackage(packageName)
        return if (preset != null) isEnabled(context, preset)
        else packageName in customPackages(context) && preferences(context).getBoolean("app:$packageName", false)
    }

    fun setEnabled(context: Context, packageName: String, enabled: Boolean) {
        val preset = SuggestionApp.fromPackage(packageName)
        if (preset != null) setEnabled(context, preset, enabled)
        else if (packageName in customPackages(context))
            preferences(context).edit().putBoolean("app:$packageName", enabled).apply()
    }

    fun overlayOpacity(context: Context): Float =
        preferences(context).getFloat(KEY_OVERLAY_OPACITY, 1f).coerceIn(0.2f, 1f)

    fun setOverlayOpacity(context: Context, opacity: Float) {
        preferences(context).edit().putFloat(KEY_OVERLAY_OPACITY, opacity.coerceIn(0.2f, 1f)).apply()
    }

    fun isFuzzyEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_FUZZY_ENABLED, true)

    fun setFuzzyEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_FUZZY_ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context, app: SuggestionApp): Boolean =
        preferences(context).getBoolean(app.preferenceKey(), false)

    fun setEnabled(context: Context, app: SuggestionApp, enabled: Boolean) {
        preferences(context).edit().putBoolean(app.preferenceKey(), enabled).apply()
    }

    fun isAnyEnabled(context: Context): Boolean =
        SuggestionApp.entries.any { isEnabled(context, it) } ||
            customPackages(context).any { isEnabled(context, it) }

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
        SuggestionApp.JMCOMIC2 -> "jmcomic2_enabled"
        SuggestionApp.JMCOMIC3 -> "jmcomic3_enabled"
    }
}
