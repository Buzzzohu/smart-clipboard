package com.smartclipboard.app.suggestion

import android.accessibilityservice.AccessibilityService
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
import com.smartclipboard.app.data.SuggestionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Optional suggestions for explicitly enabled apps. Typed text stays on this device. */
class QqSuggestionService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository by lazy {
        ClipboardRepository(ClipboardDatabase.getInstance(this).clipboardItemDao())
    }
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var queryJob: Job? = null
    private var strip: FrameLayout? = null
    private var positionJob: Job? = null
    private var currentQuery = ""
    private var currentPackage: String? = null
    private var dismissedSuggestion: Pair<String, String>? = null
    // Keep this service component name stable so existing Android accessibility approval survives upgrades.
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val currentApp = SuggestionApp.fromPackage(currentPackage)
        if (key == SuggestionSettings.KEY_FUZZY_ENABLED ||
            (currentApp != null && !SuggestionSettings.isEnabled(this, currentApp))) hideStrip()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        SuggestionSettings.preferences(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!SuggestionSettings.isAnyEnabled(this) || event == null) {
            hideStrip()
            return
        }
        val kind = event.kind()
        if (strip == null && kind in setOf(
                SuggestionEventPolicy.Kind.CONTENT,
                SuggestionEventPolicy.Kind.OTHER
            )) return
        val eventPackage = event.packageName?.toString()
        val activePackage = rootInActiveWindow?.packageName?.toString()
        val activeApp = SuggestionApp.fromPackage(activePackage)
        if (activeApp != null && !SuggestionSettings.isEnabled(this, activeApp)) {
            hideStrip()
            return
        }
        val typed = activeApp?.let { focusedText(it.packageName) }
        val action = SuggestionEventPolicy.decide(
            kind = kind,
            eventFromSupportedApp = activeApp != null && eventPackage == activePackage,
            activeSupportedApp = activeApp != null,
            inputFocused = typed != null,
            keyboardVisible = keyboardVisible(),
            overlayVisible = strip != null
        )
        when (action) {
            SuggestionEventPolicy.Action.HIDE -> { hideStrip(); return }
            SuggestionEventPolicy.Action.IGNORE -> return
            SuggestionEventPolicy.Action.REPOSITION -> { refreshStripPosition(); return }
            SuggestionEventPolicy.Action.QUERY -> Unit
        }
        val queryPackage = activePackage ?: return
        val query = typed.orEmpty()
        if (query.length < 2 || query.length > 40 || query.contains('\n')) {
            if (dismissedSuggestion != (queryPackage to query)) dismissedSuggestion = null
            hideStrip()
            return
        }
        if (dismissedSuggestion == (queryPackage to query)) {
            hideStrip()
            return
        }
        dismissedSuggestion = null
        currentQuery = query
        currentPackage = queryPackage
        queryJob?.cancel()
        queryJob = scope.launch {
            delay(180) // Wait for the IME to finish a burst of edits.
            val suggestions = try {
                repository.findSuggestions(query, SuggestionSettings.isFuzzyEnabled(this@QqSuggestionService))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Database failures are not "no matches" and never start a fallback scan.
                SuggestionResult(emptyList())
            }
            if (currentQuery == query && currentPackage == queryPackage &&
                inputContextValid(queryPackage, query)) {
                showSuggestions(queryPackage, query, suggestions)
            }
        }
    }

    private fun AccessibilityEvent.kind(): SuggestionEventPolicy.Kind = when (eventType) {
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> SuggestionEventPolicy.Kind.TEXT
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> SuggestionEventPolicy.Kind.FOCUS
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> SuggestionEventPolicy.Kind.WINDOW_STATE
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> SuggestionEventPolicy.Kind.WINDOWS
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> SuggestionEventPolicy.Kind.CONTENT
        else -> SuggestionEventPolicy.Kind.OTHER
    }

    private fun keyboardVisible(): Boolean =
        windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

    private fun inputContextValid(packageName: String, query: String): Boolean =
        SuggestionApp.fromPackage(packageName)?.let { SuggestionSettings.isEnabled(this, it) } == true &&
            rootInActiveWindow?.packageName?.toString() == packageName &&
            keyboardVisible() && focusedText(packageName) == query

    private fun focusedText(packageName: String): String? =
        focusedEditor(packageName)?.text?.toString()?.trim()

    private fun focusedEditor(packageName: String): AccessibilityNodeInfo? {
        if (SuggestionApp.fromPackage(packageName) == null) return null
        val roots = sequence {
            rootInActiveWindow?.let { yield(it) }
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
                .forEach { window -> window.root?.let { yield(it) } }
        }
        return roots.filter { it.packageName?.toString() == packageName }
            .mapNotNull { it.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }
            .firstOrNull { node ->
                node.packageName?.toString() == packageName && node.isEditable &&
                    !node.isPassword && node.isFocused
            }
    }

    private fun showSuggestions(packageName: String, query: String, result: SuggestionResult) {
        hideStripView()
        val suggestions = result.candidates
        if (suggestions.isEmpty()) return
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        if (ime == null) return
        val bounds = Rect().also(ime::getBoundsInScreen)
        val rowHeight = dp(44)
        val headerHeight = if (result.isFuzzy) dp(30) else 0
        val height = minOf(suggestions.size, 3) * rowHeight + dp(8) + headerHeight
        if (bounds.top <= height + dp(8)) return
        val backgroundColor = 0xFFF7F8FC.toInt()
        val borderColor = 0xFFCCD2DD.toInt()
        val textColor = 0xFF1A1D24.toInt()
        val accentColor = 0xFF1769D2.toInt()

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
        suggestions.forEach { candidate ->
            val item = candidate.item
            val full = item.content.replace('\n', ' ')
            val match = candidate.matchStart
            val start = if (match > 12) match - 12 else 0
            val end = minOf(full.length, start + 80)
            val preview = buildString {
                append("📋 ")
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
        }, FrameLayout.LayoutParams(-1, -1).apply { topMargin = headerHeight })
        if (result.isFuzzy) {
            panel.addView(TextView(this).apply {
                text = getString(R.string.fuzzy_match_label)
                textSize = 12f
                setTextColor(accentColor)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(4), dp(46), 0)
            }, FrameLayout.LayoutParams(-1, headerHeight, Gravity.TOP))
        }
        panel.addView(TextView(this).apply {
            text = "×"
            contentDescription = getString(R.string.close)
            textSize = 26f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setOnClickListener {
                dismissedSuggestion = packageName to query
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
            y = bounds.top - height
            x = 0
        }
        runCatching {
            windowManager.addView(panel, params)
            strip = panel
            // Some IMEs coalesce window events during their opening animation. Keep
            // following their bounds while visible, without recreating the scroll list.
            positionJob = scope.launch {
                while (strip === panel) {
                    delay(80)
                    if (!inputContextValid(packageName, query)) {
                        hideStrip()
                        return@launch
                    }
                    refreshStripPosition()
                }
            }
        }
    }

    private fun refreshStripPosition() {
        val panel = strip ?: return
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?: run { hideStrip(); return }
        val bounds = Rect().also(ime::getBoundsInScreen)
        val params = panel.layoutParams as? WindowManager.LayoutParams ?: return
        if (bounds.isEmpty || bounds.top <= params.height + dp(8)) {
            hideStrip()
            return
        }
        if (!panel.isLaidOut) return
        val location = IntArray(2)
        panel.getLocationOnScreen(location)
        // IME bounds use screen coordinates; the overlay's layout origin can have
        // system-bar insets. Correct from its actual screen location, not a guessed inset.
        val nextY = SuggestionPosition.layoutY(
            params.y, location[1], bounds.top, params.height, 0
        )
        if (params.y == nextY) return
        params.y = nextY
        runCatching { windowManager.updateViewLayout(panel, params) }
            .onFailure { hideStrip() }
    }

    private fun applySuggestion(packageName: String, query: String, item: ClipboardItem) {
        if (currentQuery != query || currentPackage != packageName ||
            !inputContextValid(packageName, query)) {
            hideStrip()
            return
        }
        val node = focusedEditor(packageName) ?: run { hideStrip(); return }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, item.content)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            dismissedSuggestion = packageName to item.content
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
        positionJob?.cancel()
        positionJob = null
        strip?.let { runCatching { windowManager.removeView(it) } }
        strip = null
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

    override fun onInterrupt() = hideStrip()

    override fun onDestroy() {
        SuggestionSettings.preferences(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        hideStrip()
        scope.cancel()
        super.onDestroy()
    }
}
