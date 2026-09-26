package com.smartclipboard.app.suggestion

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/** Only explicitly added apps may use the shared accessibility service. */
internal object SuggestionSettings {
    private const val PREFS = "qq_suggestion"
    const val KEY_FUZZY_ENABLED = "fuzzy_enabled"
    const val KEY_OVERLAY_OPACITY = "overlay_opacity"
    private const val CUSTOM_APPS = "custom_apps"

    fun customPackages(context: Context): Set<String> =
        preferences(context).getStringSet(CUSTOM_APPS, emptySet()).orEmpty().toSet()

    fun appLabel(context: Context, packageName: String): String =
        preferences(context).getString("label:$packageName", packageName) ?: packageName

    fun addApp(context: Context, packageName: String, label: String) {
        require(packageName != context.packageName && packageName.isNotBlank())
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
        return packageName in customPackages(context) && preferences(context).getBoolean("app:$packageName", false)
    }

    fun setEnabled(context: Context, packageName: String, enabled: Boolean) {
        if (packageName in customPackages(context))
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

    fun isAnyEnabled(context: Context): Boolean =
        customPackages(context).any { isEnabled(context, it) }

    fun isServiceConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { AccessibilityServiceIdentity.matches(it.id, context.packageName, QqSuggestionService::class.java.name) }
    }

    fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

}
