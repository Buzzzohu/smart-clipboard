package com.smartclipboard.app

import com.smartclipboard.app.data.CategoryRules
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRulesTest {
    @Test fun trimsNamesWithoutChangingTheirMeaning() {
        assertEquals("学校", CategoryRules.name("  学校  "))
        assertEquals("工作 回复", CategoryRules.name("工作 回复"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyNames() { CategoryRules.name("   ") }
    @Test(expected = IllegalArgumentException::class) fun rejectsMultilineNames() { CategoryRules.name("工作\n回复") }
    @Test(expected = IllegalArgumentException::class) fun rejectsOverlongNames() { CategoryRules.name("字".repeat(25)) }
}
