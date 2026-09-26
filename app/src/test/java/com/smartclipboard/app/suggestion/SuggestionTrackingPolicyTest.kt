package com.smartclipboard.app.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionTrackingPolicyTest {
    @Test fun oldOverlayDuringNewSearchRemovesOnlyItsView() {
        // Old rendered input is now invalid, but the new debounced query must survive.
        assertEquals(SuggestionTrackingPolicy.Action.REMOVE_STALE_VIEW,
            SuggestionTrackingPolicy.decide(false, true, false))
    }

    @Test fun leavingChatStillHidesCurrentSession() {
        assertEquals(SuggestionTrackingPolicy.Action.HIDE_SESSION,
            SuggestionTrackingPolicy.decide(true, true, false))
        assertEquals(SuggestionTrackingPolicy.Action.UPDATE_POSITION,
            SuggestionTrackingPolicy.decide(true, true, true))
    }
}
