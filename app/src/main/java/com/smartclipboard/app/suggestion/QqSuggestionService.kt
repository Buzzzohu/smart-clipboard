package com.smartclipboard.app.suggestion

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.smartclipboard.app.data.ClipboardDatabase
import com.smartclipboard.app.data.ClipboardItem
import com.smartclipboard.app.data.ClipboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Optional QQ/WeChat experiment. Typed text is queried locally and never stored or logged. */
class QqSuggestionService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository by lazy {
        ClipboardRepository(ClipboardDatabase.getInstance(this).clipboardItemDao())
    }
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var queryJob: Job? = null
    private var strip: LinearLayout? = null
    private var currentQuery = ""
    private var currentPackage: String? = null
    // Keep this service component name stable so existing Android accessibility approval survives upgrades.
    private val supportedPackages = setOf("com.tencent.mobileqq", "com.tencent.mm")
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!QqSuggestionSettings.isEnabled(this)) hideStrip()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        QqSuggestionSettings.preferences(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!QqSuggestionSettings.isEnabled(this) || event == null) {
            hideStrip()
            return
        }
        val eventPackage = event.packageName?.toString()
        if (eventPackage == null || eventPackage !in supportedPackages) {
            // Keyboard and our overlay emit events while a supported app still owns input.
            if (rootInActiveWindow?.packageName?.toString() !in supportedPackages) hideStrip()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        // Only the focused editable node is examined. Password fields are excluded.
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node == null || node.packageName?.toString() != eventPackage ||
            !node.isEditable || node.isPassword || !node.isFocused) {
            hideStrip()
            return
        }
        val typed = node.text?.toString()?.trim().orEmpty()
        if (typed.length < 2 || typed.length > 40 || typed.contains('\n')) {
            hideStrip()
            return
        }
        currentQuery = typed
        currentPackage = eventPackage
        queryJob?.cancel()
        queryJob = scope.launch {
            delay(180) // Wait for the IME to finish a burst of edits.
            val suggestions = runCatching { repository.findSuggestions(typed) }
                .getOrDefault(emptyList())
                .filter { it.content != typed }
            if (currentQuery == typed && currentPackage == eventPackage &&
                focusedText(eventPackage) == typed) {
                showSuggestions(eventPackage, typed, suggestions)
            }
        }
    }

    private fun focusedText(packageName: String): String? {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return if (node?.packageName?.toString() == packageName && node.isEditable &&
            !node.isPassword && node.isFocused) node.text?.toString()?.trim() else null
    }

    private fun showSuggestions(packageName: String, query: String, suggestions: List<ClipboardItem>) {
        hideStripView()
        if (suggestions.isEmpty()) return
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return
        val bounds = Rect().also(ime::getBoundsInScreen)
        val rowHeight = dp(44)
        val height = suggestions.size * rowHeight + dp(8)
        if (bounds.top <= height + dp(8)) return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(0xFFF7F8FC.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFFCCD2DD.toInt())
            }
            elevation = dp(8).toFloat()
        }
        suggestions.forEach { item ->
            panel.addView(TextView(this).apply {
                text = "📋 ${item.content.replace('\n', ' ').take(70)}"
                textSize = 15f
                setTextColor(0xFF1A1D24.toInt())
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener { applySuggestion(packageName, query, item) }
            }, LinearLayout.LayoutParams(-1, rowHeight))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = bounds.top - height - dp(6)
            x = 0
        }
        runCatching {
            windowManager.addView(panel, params)
            strip = panel
        }
    }

    private fun applySuggestion(packageName: String, query: String, item: ClipboardItem) {
        if (currentQuery != query || currentPackage != packageName ||
            focusedText(packageName) != query) {
            hideStrip()
            return
        }
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, item.content)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            scope.launch { runCatching { repository.recordUse(item.id) } }
        }
        hideStrip()
    }

    private fun hideStrip() {
        queryJob?.cancel()
        currentQuery = ""
        currentPackage = null
        hideStripView()
    }

    private fun hideStripView() {
        strip?.let { runCatching { windowManager.removeView(it) } }
        strip = null
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

    override fun onInterrupt() = hideStrip()

    override fun onDestroy() {
        QqSuggestionSettings.preferences(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        hideStrip()
        scope.cancel()
        super.onDestroy()
    }
}
