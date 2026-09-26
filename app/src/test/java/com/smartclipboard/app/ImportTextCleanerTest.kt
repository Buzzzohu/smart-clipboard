package com.smartclipboard.app

import com.smartclipboard.app.data.ImportTextCleaner
import com.smartclipboard.app.data.BatchImportParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportTextCleanerTest {
    @Test fun stripsOneLeadingPrefixAndPreservesBodyMentions() {
        assertEquals("你好 @李四: 早上好", ImportTextCleaner.clean("@张三: 你好 @李四: 早上好", true))
        assertEquals("你好", ImportTextCleaner.clean("  @昵称 🎁：你好  ", true))
        assertEquals("@李四: 你好", ImportTextCleaner.clean("@张三: @李四: 你好", true))
    }
    @Test fun disabledOrUnrecognizedContentStaysIntact() {
        for (text in listOf("@张三 你好", "邮件 a@b.com: 你好", "@张三\n正文: 你好", "@ : 你好")) {
            assertEquals(text, ImportTextCleaner.clean(text, true))
        }
        assertEquals("@张三: 你好", ImportTextCleaner.clean("@张三: 你好", false))
    }
    @Test fun batchDeduplicatesAndSkipsEmptyAfterCleaning() {
        val parsed = BatchImportParser.parse("@甲: 你好\n@乙：你好\n@丙: \n普通内容", true)
        assertEquals(listOf("你好", "普通内容"), parsed.entries)
        assertEquals(2, parsed.skipped)
    }
}
