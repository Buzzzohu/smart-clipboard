package com.smartclipboard.app.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionEventPolicyTest {
    @Test
    fun leavingChatForQqMessageListHidesVisibleOverlay() {
        assertEquals(
            SuggestionEventPolicy.Action.HIDE,
            SuggestionEventPolicy.decide(
                kind = SuggestionEventPolicy.Kind.CONTENT,
                eventFromQq = true,
                activeQq = true,
                inputFocused = false,
                keyboardVisible = false,
                overlayVisible = true
            )
        )
    }

    @Test
    fun dismissingKeyboardHidesVisibleOverlayEvenIfInputKeepsFocus() {
        assertEquals(
            SuggestionEventPolicy.Action.HIDE,
            SuggestionEventPolicy.decide(
                kind = SuggestionEventPolicy.Kind.WINDOWS,
                eventFromQq = false,
                activeQq = true,
                inputFocused = true,
                keyboardVisible = false,
                overlayVisible = true
            )
        )
    }

    @Test
    fun activeQqInputStillStartsQuery() {
        assertEquals(
            SuggestionEventPolicy.Action.QUERY,
            SuggestionEventPolicy.decide(
                kind = SuggestionEventPolicy.Kind.TEXT,
                eventFromQq = true,
                activeQq = true,
                inputFocused = true,
                keyboardVisible = true,
                overlayVisible = false
            )
        )
    }
}
