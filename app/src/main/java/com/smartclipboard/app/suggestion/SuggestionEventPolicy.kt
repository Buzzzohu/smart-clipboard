package com.smartclipboard.app.suggestion

/** Keeps the overlay event decision independent of Android view objects. */
internal object SuggestionEventPolicy {
    enum class Kind { TEXT, FOCUS, WINDOW_STATE, WINDOWS, CONTENT, OTHER }
    enum class Action { HIDE, QUERY, IGNORE }

    fun decide(
        kind: Kind,
        eventFromSupportedApp: Boolean,
        activeSupportedApp: Boolean,
        inputFocused: Boolean,
        keyboardVisible: Boolean,
        overlayVisible: Boolean
    ): Action {
        if (!activeSupportedApp) return Action.HIDE
        // A message list is still in the same package, but has no active editor.
        // Hiding the IME can leave the editor focused, so check both signals.
        if (overlayVisible && (!inputFocused || !keyboardVisible)) return Action.HIDE
        if (!eventFromSupportedApp) return Action.IGNORE
        if (kind !in setOf(Kind.TEXT, Kind.FOCUS, Kind.WINDOW_STATE, Kind.WINDOWS)) {
            return Action.IGNORE
        }
        return if (inputFocused && keyboardVisible) Action.QUERY else Action.HIDE
    }
}
