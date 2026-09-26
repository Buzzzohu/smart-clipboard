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
        assertEquals(SuggestionApp.JMCOMIC2, SuggestionApp.fromPackage("com.jiaohua_browser"))
        assertEquals(SuggestionApp.JMCOMIC3, SuggestionApp.fromPackage("com.a7m3p9xv.t6qk2z8.app"))
        assertNull(SuggestionApp.fromPackage("com.tencent.mm"))
        assertNull(SuggestionApp.fromPackage(null))
    }
}
