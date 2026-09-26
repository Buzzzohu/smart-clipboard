package com.smartclipboard.app.suggestion

/** An old rendered result must never cancel a search for newer input. */
internal object SuggestionTrackingPolicy {
    enum class Action { REMOVE_STALE_VIEW, HIDE_SESSION, UPDATE_POSITION }

    fun decide(sameQuery: Boolean, samePackage: Boolean, inputValid: Boolean): Action = when {
        !sameQuery || !samePackage -> Action.REMOVE_STALE_VIEW
        !inputValid -> Action.HIDE_SESSION
        else -> Action.UPDATE_POSITION
    }
}
