package com.smartclipboard.app

import com.smartclipboard.app.classification.ClipboardCategory
import com.smartclipboard.app.classification.ClipboardCategoryClassifier
import com.smartclipboard.app.clipboard.ClipboardImportPolicy
import com.smartclipboard.app.clipboard.ClipboardImportResult
import com.smartclipboard.app.data.BatchImportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardRulesTest {
    @Test
    fun classifyWholeEntries() {
        assertEquals(ClipboardCategory.URL, ClipboardCategoryClassifier.classify("https://github.com/xxx"))
        assertEquals(ClipboardCategory.EMAIL, ClipboardCategoryClassifier.classify("xxx@qq.com"))
        assertEquals(ClipboardCategory.CONTACT, ClipboardCategoryClassifier.classify("+86 138 0013 8000"))
        assertEquals(ClipboardCategory.TEXT, ClipboardCategoryClassifier.classify("湖北大学智能制造学院机器人工程专业"))
        assertEquals(ClipboardCategory.TEXT, ClipboardCategoryClassifier.classify("请访问 https://github.com"))
    }

    @Test
    fun ignoreOnlyEmptyOrAsciiLettersAndDigits() {
        assertEquals(ClipboardImportResult.Empty, ClipboardImportPolicy.evaluate("  "))
        assertEquals(ClipboardImportResult.Ignored, ClipboardImportPolicy.evaluate("abc123"))
        assertEquals(ClipboardImportResult.Ignored, ClipboardImportPolicy.evaluate("13800138000"))
        assertTrue(ClipboardImportPolicy.evaluate("湖北大学") is ClipboardImportResult.Candidate)
        assertTrue(ClipboardImportPolicy.evaluate("https://github.com") is ClipboardImportResult.Candidate)
    }

    @Test
    fun batchPasteKeepsUniqueNonEmptyLinesInOrder() {
        val parsed = BatchImportParser.parse("湖北大学\nhttps://github.com\n\n湖北大学\n13800138000")
        assertEquals(listOf("湖北大学", "https://github.com", "13800138000"), parsed.entries)
        assertEquals(2, parsed.skipped)
    }
}
