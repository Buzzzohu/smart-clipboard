package com.smartclipboard.app.clipboard

/** Applies the user's rule before showing a save prompt. */
object ClipboardImportPolicy {
    private val lettersAndDigitsOnly = Regex("^[A-Za-z0-9]+$")

    fun evaluate(rawText: String?): ClipboardImportResult {
        val content = rawText?.trim().orEmpty()
        return when {
            content.isEmpty() -> ClipboardImportResult.Empty
            lettersAndDigitsOnly.matches(content) -> ClipboardImportResult.Ignored
            else -> ClipboardImportResult.Candidate(content)
        }
    }
}

sealed interface ClipboardImportResult {
    data object Empty : ClipboardImportResult
    data object Ignored : ClipboardImportResult
    data class Candidate(val content: String) : ClipboardImportResult
}
