package com.smartclipboard.app.suggestion

/** Never expose intermediate IME animation positions to the user. */
internal class OverlayVisibilityGate(private val settleMillis: Long = 160) {
    private var anchor: Int? = null
    private var changedAt = 0L

    fun ready(position: Int, now: Long): Boolean {
        if (anchor != position) {
            anchor = position
            changedAt = now
        }
        return now - changedAt >= settleMillis
    }
}
