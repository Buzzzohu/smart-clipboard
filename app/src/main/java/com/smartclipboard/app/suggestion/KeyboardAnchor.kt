package com.smartclipboard.app.suggestion

/** Geometry of Sogou's optional accessory strip, candidate toolbar, and key area. */
internal object KeyboardAnchor {
    data class Band(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun candidateTop(window: Band, children: List<Band>, maxStrip: Int, minKeys: Int): Int {
        val bands = children.filter { it.left == window.left && it.right == window.right && it.bottom > it.top }
            .sortedBy { it.top }
        if (bands.size != 3) return window.top
        val (accessory, toolbar, keys) = bands
        return if (accessory.top == window.top && accessory.bottom == toolbar.top &&
            toolbar.bottom == keys.top && accessory.bottom - accessory.top in 1..maxStrip &&
            toolbar.bottom - toolbar.top in 1..maxStrip && keys.bottom - keys.top >= minKeys) {
            toolbar.top
        } else window.top
    }
}
