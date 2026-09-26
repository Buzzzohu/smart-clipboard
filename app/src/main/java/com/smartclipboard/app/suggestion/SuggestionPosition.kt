package com.smartclipboard.app.suggestion

/** Translate the desired screen position into this overlay window's layout coordinates. */
internal object SuggestionPosition {
    fun layoutY(currentY: Int, actualScreenTop: Int, keyboardTop: Int, height: Int, gap: Int): Int =
        currentY + keyboardTop - height - gap - actualScreenTop
}
