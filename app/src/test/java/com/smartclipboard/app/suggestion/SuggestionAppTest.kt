package com.smartclipboard.app.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionAppTest {
    @Test
    fun onlyExplicitlySupportedPackagesCanTriggerSuggestions() {
        assertEquals(SuggestionApp.QQ, SuggestionApp.fromPackage("com.tencent.mobileqq"))
        assertEquals(SuggestionApp.HEYBOX, SuggestionApp.fromPackage("com.max.xiaoheihe"))
        assertNull(SuggestionApp.fromPackage("com.ss.android.ugc.aweme"))
        assertNull(SuggestionApp.fromPackage(null))
    }
}
