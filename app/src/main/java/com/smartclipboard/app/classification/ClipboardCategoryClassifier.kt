package com.smartclipboard.app.classification

import java.net.URI
import java.net.URISyntaxException

/** Classifies complete clipboard entries, not substrings inside longer messages. */
object ClipboardCategoryClassifier {
    private val email = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val phoneCharacters = Regex("^[+0-9()\\s-]+$")
    private val chineseMobile = Regex("^(?:\\+?86)?1[3-9][0-9]{9}$")

    fun classify(content: String): ClipboardCategory {
        val text = content.trim()
        return when {
            isWebUrl(text) -> ClipboardCategory.URL
            email.matches(text) -> ClipboardCategory.EMAIL
            isMobileNumber(text) -> ClipboardCategory.CONTACT
            else -> ClipboardCategory.TEXT
        }
    }

    private fun isWebUrl(text: String): Boolean = try {
        val uri = URI(text)
        (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) && !uri.host.isNullOrBlank()
    } catch (_: URISyntaxException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun isMobileNumber(text: String): Boolean {
        if (!phoneCharacters.matches(text)) return false
        val normalized = text.replace(Regex("[()\\s-]"), "")
        return chineseMobile.matches(normalized)
    }
}

enum class ClipboardCategory(val label: String) {
    URL("网址"),
    EMAIL("邮箱"),
    CONTACT("联系方式"),
    TEXT("文本")
}
