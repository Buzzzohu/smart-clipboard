package com.smartclipboard.app.suggestion

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.smartclipboard.app.R
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

/** Optional QQ experiment. Typed text is queried locally and never stored or logged. */
class QqSuggestionService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository by lazy {
        ClipboardRepository(ClipboardDatabase.getInstance(this).clipboardItemDao())
    }
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var queryJob: Job? = null
    private var strip: FrameLayout? = null
    private var currentQuery = ""
    private var currentPackage: String? = null
    private var dismissedQuery: String? = null
    // Keep this service component name stable so existing Android accessibility approval survives upgrades.
    private val supportedPackages = setOf("com.tencent.mobileqq")
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
        val typed = focusedText(eventPackage).orEmpty()
        if (typed.length < 2 || typed.length > 40 || typed.contains('\n')) {
            if (typed != dismissedQuery) dismissedQuery = null
            hideStrip()
            return
        }
        if (typed == dismissedQuery) {
            hideStrip()
            return
        }
        dismissedQuery = null
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
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        if (ime == null) return
        val bounds = Rect().also(ime::getBoundsInScreen)
        val rowHeight = dp(48)
        val height = minOf(suggestions.size, 3) * rowHeight + dp(8)
        if (bounds.top <= height + dp(8)) return
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val backgroundColor = if (dark) 0xFF252B36.toInt() else 0xFFF7F8FC.toInt()
        val borderColor = if (dark) 0xFF566173.toInt() else 0xFFCCD2DD.toInt()
        val textColor = if (dark) 0xFFF0F4FC.toInt() else 0xFF1A1D24.toInt()
        val accentColor = if (dark) 0xFF9FC4FF.toInt() else 0xFF1769D2.toInt()

        val panel = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), borderColor)
            }
            elevation = dp(8).toFloat()
        }
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        suggestions.forEach { item ->
            val full = item.content.replace('\n', ' ')
            val match = full.indexOf(query, ignoreCase = true)
            val start = if (match > 12) match - 12 else 0
            val end = minOf(full.length, start + 80)
            val preview = buildString {
                if (item.favorite) append("★ ")
                if (start > 0) append("…")
                append(full.substring(start, end))
                if (end < full.length) append("…")
            }
            val styled = SpannableString(preview)
            var position = preview.indexOf(query, ignoreCase = true)
            while (position >= 0) {
                styled.setSpan(ForegroundColorSpan(accentColor), position, position + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(StyleSpan(Typeface.BOLD), position, position + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                position = preview.indexOf(query, position + query.length, ignoreCase = true)
            }
            rows.addView(TextView(this).apply {
                text = styled
                textSize = 15f
                setTextColor(textColor)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(46), 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener { applySuggestion(packageName, query, item) }
            }, LinearLayout.LayoutParams(-1, rowHeight))
        }
        panel.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            isFillViewport = false
            addView(rows)
        }, FrameLayout.LayoutParams(-1, -1))
        panel.addView(TextView(this).apply {
            text = "×"
            contentDescription = getString(R.string.close)
            textSize = 26f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setOnClickListener {
                dismissedQuery = query
                hideStrip()
            }
        }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END))
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
            dismissedQuery = item.content
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
