package com.smartclipboard.app.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionPositionTest {
    @Test
    fun firstLayoutCorrectsMeasuredPhoneOffsetBeforeBeingShown() {
        // Toolbar top 1482, panel height 420, window origin 120.
        val corrected = SuggestionPosition.layoutY(1062, 1182, 1482, 420, 0)
        assertEquals(942, corrected)
        assertEquals(corrected, SuggestionPosition.layoutY(corrected, 1062, 1482, 420, 0))
    }

    @Test
    fun reopeningKeyboardMovesExistingPanelUpAndKeepsGap() {
        var layoutY = 1900
        // Replay the keyboard opening; include a 24px system-bar offset.
        for (keyboardTop in listOf(2200, 1900, 1600, 1400, 1400)) {
            layoutY = SuggestionPosition.layoutY(layoutY, layoutY + 24, keyboardTop, 300, 0)
            assertEquals(keyboardTop, layoutY + 24 + 300)
        }
    }

    @Test
    fun fuzzyHeaderHeightAndKeyboardResizeKeepSameGap() {
        assertEquals(1044, SuggestionPosition.layoutY(1400, 1430, 1400, 320, 6))
        assertEquals(1244, SuggestionPosition.layoutY(1044, 1074, 1600, 320, 6))
    }
}
