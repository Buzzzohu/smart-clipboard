package com.smartclipboard.app.data

/** Remove only one leading sender prefix; mentions inside the body remain intact. */
object ImportTextCleaner {
    private val sender = Regex("^@([^\\r\\n:：]+)[:：][ \\t]*")

    fun clean(raw: String, enabled: Boolean): String {
        val text = raw.trim()
        if (!enabled) return text
        val match = sender.find(text) ?: return text
        if (match.groupValues[1].isBlank()) return text
        return text.substring(match.range.last + 1).trim()
    }
}
