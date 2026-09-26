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
                eventFromSupportedApp = true,
                activeSupportedApp = true,
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
                eventFromSupportedApp = false,
                activeSupportedApp = true,
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
                eventFromSupportedApp = true,
                activeSupportedApp = true,
                inputFocused = true,
                keyboardVisible = true,
                overlayVisible = false
            )
        )
    }
}
