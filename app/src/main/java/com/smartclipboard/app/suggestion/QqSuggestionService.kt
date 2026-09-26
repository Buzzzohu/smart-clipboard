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
import android.widget.ListView
import android.widget.ScrollView
import android.widget.BaseAdapter
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import android.graphics.Bitmap
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import com.smartclipboard.app.data.CategoryIcons
import kotlinx.coroutines.flow.first
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
        ClipboardRepository(ClipboardDatabase.getInstance(this))
    }
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var queryJob: Job? = null
    private var strip: FrameLayout? = null
    private var positionJob: Job? = null
    private var visibilityGate = OverlayVisibilityGate()
    private var currentEditorWindowId: Int? = null
    private var currentQuery = ""
    private var currentPackage: String? = null
    private var dismissedSuggestion: Pair<String, String>? = null
    // Keep this service component name stable so existing Android accessibility approval survives upgrades.
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SuggestionSettings.KEY_OVERLAY_OPACITY) {
            // Leave transition-hidden panels hidden; apply the setting when they become visible.
            strip?.takeIf { it.alpha > 0f }?.alpha = SuggestionSettings.overlayOpacity(this)
        }
        if (key == SuggestionSettings.KEY_FUZZY_ENABLED ||
            (currentPackage != null && !SuggestionSettings.isEnabled(this, currentPackage))) hideStrip()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // IMEs often mark layout containers as unimportant for spoken feedback.
        // Include them so geometry matches the visible toolbar rather than only buttons.
        serviceInfo = serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
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
        val activeEnabled = SuggestionSettings.isEnabled(this, activePackage)
        val typed = activePackage?.takeIf { activeEnabled }?.let { focusedText(it) }
        val action = SuggestionEventPolicy.decide(
            kind = kind,
            eventFromSupportedApp = activeEnabled && eventPackage == activePackage,
            activeSupportedApp = activeEnabled,
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
        if (query.isBlank() || query.length > 40 || query.contains('\n')) {
            if (dismissedSuggestion != (queryPackage to query)) dismissedSuggestion = null
            hideStrip()
            return
        }
        if (dismissedSuggestion == (queryPackage to query)) {
            hideStrip()
            return
        }
        dismissedSuggestion = null
        val editorWindowId = focusedEditor(queryPackage)?.windowId ?: return
        // Window notifications often repeat while navigating. Do not recreate an
        // unchanged list or reset an already pending search on every notification.
        if (currentQuery == query && currentPackage == queryPackage &&
            currentEditorWindowId == editorWindowId &&
            (strip != null || queryJob?.isActive == true)) return
        hideStripView()
        currentQuery = query
        currentPackage = queryPackage
        currentEditorWindowId = editorWindowId
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
                val needed = suggestions.candidates.map { it.item.category }.toSet()
                val icons = try {
                    repository.categoryList.first().filter { it.name in needed }
                        .associate { it.name to CategoryIcons.loadCategory(this@QqSuggestionService, it) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyMap() }
                if (currentQuery == query && currentPackage == queryPackage && inputContextValid(queryPackage, query)) {
                    showSuggestions(queryPackage, query, suggestions, icons)
                }
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
        SuggestionSettings.isEnabled(this, packageName) &&
            rootInActiveWindow?.packageName?.toString() == packageName &&
            keyboardVisible() && focusedEditor(packageName)?.let {
                it.windowId == currentEditorWindowId && it.text?.toString()?.trim() == query
            } == true

    private fun focusedText(packageName: String): String? =
        focusedEditor(packageName)?.text?.toString()?.trim()

    private fun focusedEditor(packageName: String): AccessibilityNodeInfo? {
        if (!SuggestionSettings.isEnabled(this, packageName)) return null
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

    private fun showSuggestions(packageName: String, query: String, result: SuggestionResult, icons: Map<String, Bitmap?>) {
        hideStripView()
        val suggestions = result.candidates
        if (suggestions.isEmpty()) return
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        if (ime == null) return
        val bounds = Rect().also(ime::getBoundsInScreen)
        val rowHeight = dp(44)
        val height = minOf(suggestions.size, 3) * rowHeight + dp(8)
        if (bounds.top <= height + dp(8)) return
        val backgroundColor = 0xFFF7F8FC.toInt()
        val borderColor = 0xFFCCD2DD.toInt()
        val textColor = 0xFF1A1D24.toInt()
        val accentColor = 0xFF1769D2.toInt()

        val panel = FrameLayout(this).apply {
            alpha = 0f
            background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), borderColor)
            }
            elevation = dp(8).toFloat()
        }
        fun resizePanel(newHeight: Int) {
            val layout = panel.layoutParams as? WindowManager.LayoutParams ?: return
            // Keep the lower edge attached to the IME while switching between list and preview.
            panel.alpha = 0f
            layout.y += layout.height - newHeight
            layout.height = newHeight
            layout.flags = layout.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            visibilityGate = OverlayVisibilityGate()
            runCatching { windowManager.updateViewLayout(panel, layout) }.onFailure { hideStrip() }
        }
        fun previewItem(item: ClipboardItem) {
            if (strip !== panel || !inputContextValid(packageName, query)) return
            val currentIme = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return
            val currentBounds = Rect().also(currentIme::getBoundsInScreen)
            val previewHeight = minOf(dp(320), currentBounds.top - dp(32))
            if (previewHeight < dp(100)) return
            val originalViews = (0 until panel.childCount).map { panel.getChildAt(it) }
            originalViews.forEach { it.visibility = View.GONE }
            val preview = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(4), dp(12), dp(8))
            }
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(this).apply {
                text = "内容预览"
                textSize = 16f
                setTextColor(textColor)
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            header.addView(TextView(this).apply {
                text = "×"
                contentDescription = "关闭预览"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(textColor)
                setOnClickListener {
                    if (strip === panel) {
                        panel.removeView(preview)
                        originalViews.forEach { it.visibility = View.VISIBLE }
                        resizePanel(height)
                    }
                }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            preview.addView(header)
            preview.addView(ScrollView(this).apply {
                isFillViewport = true
                addView(TextView(this@QqSuggestionService).apply {
                    text = item.content
                    textSize = 16f
                    setTextColor(textColor)
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                }, FrameLayout.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            panel.addView(preview, FrameLayout.LayoutParams(-1, -1))
            resizePanel(previewHeight)
        }
        fun candidateRow(position: Int): View {
            val candidate = suggestions[position]
            val item = candidate.item
            val full = item.content.replace('\n', ' ')
            val match = candidate.matchStart
            val start = if (match > 12) match - 12 else 0
            val end = minOf(full.length, start + 80)
            var contentOffset = 0
            val preview = buildString {
                if (item.favorite) append("★ ")
                if (start > 0) append("…")
                contentOffset = length
                append(full.substring(start, end))
                if (end < full.length) append("…")
            }
            val styled = SpannableString(preview)
            // Matching supplies original UTF-16 offsets. Translate past the icon,
            // favorite marker and preview ellipsis; never color skipped characters.
            candidate.matchedIndices.filter { it in start until end }.forEach { index ->
                val offset = contentOffset + index - start
                styled.setSpan(ForegroundColorSpan(accentColor), offset, offset + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(StyleSpan(Typeface.BOLD), offset, offset + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            var position = preview.indexOf(query, ignoreCase = true)
            while (position >= 0) {
                styled.setSpan(ForegroundColorSpan(accentColor), position, position + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(StyleSpan(Typeface.BOLD), position, position + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                position = preview.indexOf(query, position + query.length, ignoreCase = true)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(46), 0)
                setOnClickListener { applySuggestion(packageName, query, item) }
                setOnLongClickListener { previewItem(item); true }
            }
            row.addView(ImageView(this).apply {
                val icon = icons[item.category]
                if (icon != null) setImageBitmap(icon) else setImageResource(R.drawable.ic_sidebar_library)
                contentDescription = item.category
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(5).toFloat())
                    }
                }
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(8) })
            row.addView(TextView(this).apply {
                text = styled
                textSize = 15f
                setTextColor(textColor)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, rowHeight, 1f))
            row.layoutParams = android.widget.AbsListView.LayoutParams(-1, rowHeight)
            return row
        }
        // Inflate visible rows on demand so category results need no arbitrary item cap.
        panel.addView(ListView(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            divider = null
            dividerHeight = 0
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            adapter = object : BaseAdapter() {
                override fun getCount() = suggestions.size
                override fun getItem(position: Int) = suggestions[position]
                override fun getItemId(position: Int) = suggestions[position].item.id
                override fun hasStableIds() = true
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = candidateRow(position)
            }
        }, FrameLayout.LayoutParams(-1, -1))
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
            y = keyboardAnchor(ime, bounds) - height
            x = 0
            windowAnimations = 0
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        visibilityGate = OverlayVisibilityGate()
        // Screen coordinates and window coordinates have different origins on
        // some phones. Suppress the first draw until the measured position is
        // corrected; otherwise users see the wrong position for one polling cycle.
        panel.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (strip !== panel) return true
                if (!refreshStripPosition()) return false
                panel.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        runCatching {
            windowManager.addView(panel, params)
            strip = panel
            // Some IMEs coalesce window events during their opening animation. Keep
            // following their bounds while visible, without recreating the scroll list.
            positionJob = scope.launch {
                while (strip === panel) {
                    delay(40)
                    when (SuggestionTrackingPolicy.decide(currentQuery == query,
                        currentPackage == packageName, inputContextValid(packageName, query))) {
                        SuggestionTrackingPolicy.Action.REMOVE_STALE_VIEW -> {
                            hideStripView()
                            return@launch
                        }
                        SuggestionTrackingPolicy.Action.HIDE_SESSION -> {
                            hideStrip()
                            return@launch
                        }
                        SuggestionTrackingPolicy.Action.UPDATE_POSITION -> refreshStripPosition()
                    }
                }
            }
        }
    }

    private fun refreshStripPosition(): Boolean {
        val panel = strip ?: return false
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?: run { hideStrip(); return false }
        val bounds = Rect().also(ime::getBoundsInScreen)
        val params = panel.layoutParams as? WindowManager.LayoutParams ?: return false
        if (bounds.isEmpty || bounds.top <= params.height + dp(8)) {
            hideStrip()
            return false
        }
        if (!panel.isLaidOut) return false
        val location = IntArray(2)
        panel.getLocationOnScreen(location)
        // IME bounds use screen coordinates; the overlay's layout origin can have
        // system-bar insets. Correct from its actual screen location, not a guessed inset.
        val nextY = SuggestionPosition.layoutY(
            params.y, location[1], keyboardAnchor(ime, bounds), params.height, 0
        )
        val settled = visibilityGate.ready(location[1] + nextY - params.y + params.height,
            android.os.SystemClock.uptimeMillis())
        val ready = settled && params.y == nextY
        // Hide before changing geometry. A transparent panel must not intercept
        // touches intended for the chat or keyboard during navigation.
        panel.alpha = if (ready) SuggestionSettings.overlayOpacity(this) else 0f
        val flags = if (ready) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (params.y == nextY && params.flags == flags) return ready
        params.y = nextY
        params.flags = flags
        runCatching { windowManager.updateViewLayout(panel, params) }
            .onFailure { hideStrip() }
        return false
    }

    private fun keyboardAnchor(ime: AccessibilityWindowInfo, bounds: Rect): Int {
        val root = ime.root ?: return bounds.top
        // Only the measured Sogou structure is adapted. Other keyboards retain
        // their normal window anchor. Never inspect IME text or descriptions.
        if (root.packageName?.toString() != "com.sohu.inputmethod.sogou") return bounds.top
        fun Rect.band() = KeyboardAnchor.Band(left, top, right, bottom)
        var remaining = 120
        fun find(node: AccessibilityNodeInfo, depth: Int): Int {
            if (--remaining < 0 || depth > 10) return bounds.top
            val children = (0 until node.childCount).mapNotNull(node::getChild)
                .filter { it.isVisibleToUser }
            val bands = children.map { Rect().also(it::getBoundsInScreen).band() }
            val anchor = KeyboardAnchor.candidateTop(bounds.band(), bands, dp(48), dp(120))
            if (anchor != bounds.top) return anchor
            for (child in children) {
                val found = find(child, depth + 1)
                if (found != bounds.top) return found
            }
            return bounds.top
        }
        return find(root, 0)
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
        currentEditorWindowId = null
        hideStripView()
    }

    private fun hideStripView() {
        positionJob?.cancel()
        positionJob = null
        strip?.let {
            it.alpha = 0f
            runCatching { windowManager.removeViewImmediate(it) }
        }
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
