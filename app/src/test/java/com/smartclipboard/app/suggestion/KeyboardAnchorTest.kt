package com.smartclipboard.app.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardAnchorTest {
    private val window = KeyboardAnchor.Band(0, 1386, 1080, 2376)
    @Test fun measuredAccessoryStripMustNotLeave96PixelGap() {
        assertEquals(1482, KeyboardAnchor.candidateTop(window, listOf(
            KeyboardAnchor.Band(0, 1386, 1080, 1482),
            KeyboardAnchor.Band(840, 1386, 1080, 1482),
            KeyboardAnchor.Band(0, 1482, 1080, 1602),
            KeyboardAnchor.Band(0, 1602, 1080, 2244)
        ), 144, 360))
    }
    @Test fun ordinaryKeyboardKeepsWindowAnchor() {
        assertEquals(1386, KeyboardAnchor.candidateTop(window, listOf(
            KeyboardAnchor.Band(0, 1386, 1080, 1506),
            KeyboardAnchor.Band(0, 1506, 1080, 2244)
        ), 144, 360))
    }
}
