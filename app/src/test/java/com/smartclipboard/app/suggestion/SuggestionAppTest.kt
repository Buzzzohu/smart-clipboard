package com.smartclipboard.app.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionAppTest {
    @Test
    fun onlyExplicitlySupportedPackagesCanTriggerSuggestions() {
        assertEquals(SuggestionApp.QQ, SuggestionApp.fromPackage("com.tencent.mobileqq"))
        assertEquals(SuggestionApp.HEYBOX, SuggestionApp.fromPackage("com.max.xiaoheihe"))
        assertEquals(SuggestionApp.BILIBILI, SuggestionApp.fromPackage("tv.danmaku.bili"))
        assertEquals(SuggestionApp.DOUYIN, SuggestionApp.fromPackage("com.ss.android.ugc.aweme"))
        assertNull(SuggestionApp.fromPackage("com.tencent.mm"))
        assertNull(SuggestionApp.fromPackage(null))
    }
}
