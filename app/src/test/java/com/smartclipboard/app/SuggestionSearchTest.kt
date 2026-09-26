package com.smartclipboard.app

import com.smartclipboard.app.data.ClipboardItem
import com.smartclipboard.app.data.FuzzyTextMatcher
import com.smartclipboard.app.data.SuggestionSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SuggestionSearchTest {
    @Test fun userFiveCharacterExampleMatchesLongRepeatedEntry() = runBlocking {
        val content = "真拿你没办法坐好喽 " + "站起来 坐好喽 ".repeat(80)
        val search = SuggestionSearch({ emptyList() }, { after, _ ->
            if (after == 0L) listOf(item(1, content)) else emptyList()
        })
        val result = search.search("办法做好喽", true)
        assertTrue(result.isFuzzy)
        assertEquals(1L, result.candidates.single().item.id)
        assertEquals(listOf(4, 5, 7, 8), result.candidates.single().matchedIndices)
    }

    @Test fun highlightsOnlyAlignedCharactersInFuzzyResults() = runBlocking {
        assertEquals(listOf(3, 4, 7, 8),
            FuzzyTextMatcher.match("湖北大学", "学校：湖北工业大学")!!.matchedIndices)
        assertEquals(listOf(0, 1, 2),
            FuzzyTextMatcher.match("智能制照", "智能制造")!!.matchedIndices)
        assertEquals(listOf(0, 1, 3, 4),
            FuzzyTextMatcher.match("智能制造", "智能的制造")!!.matchedIndices)
        assertEquals(listOf(0, 1, 2),
            FuzzyTextMatcher.match("智能制造", "智能制")!!.matchedIndices)
        val search = SuggestionSearch({ emptyList() }, { after, _ ->
            if (after == 0L) listOf(item(1, "学校：湖北工业大学")) else emptyList()
        })
        assertEquals(listOf(3, 4, 7, 8), search.search("湖北大学", true)
            .candidates.single().matchedIndices)
    }

    private fun item(id: Long, content: String) = ClipboardItem(id, content, 0, 0)

    @Test fun orderedGapsFindTheRequestedExampleAndPreviewOffset() = runBlocking {
        val found = FuzzyTextMatcher.match("湖北制造", "学校：湖北大学智能制造学院")
        assertNotNull(found)
        assertEquals(3, found!!.start)
        assertNotNull(FuzzyTextMatcher.match("湖北制造学院", "湖北大学制造工程学院"))
        // Do not let an earlier partial fragment hide a later full fragment.
        assertNotNull(FuzzyTextMatcher.match("湖北制造", "湖北制图制造"))
    }

    @Test fun acceptsOneSubstitutionInsertionOrDeletion() = runBlocking {
        assertNotNull(FuzzyTextMatcher.match("智能制照", "智能制造学院"))
        assertNotNull(FuzzyTextMatcher.match("智能制造", "智能制学院"))
        assertNotNull(FuzzyTextMatcher.match("智能制造", "智能的制造学院"))
        assertNotNull(FuzzyTextMatcher.match("GITHIB", "https://github.com"))
    }

    @Test fun rejectsShortReorderedScatteredDistantAndUnrelatedMatches() = runBlocking {
        assertNull(FuzzyTextMatcher.match("湖大", "湖北大学"))
        assertNull(FuzzyTextMatcher.match("制造湖北", "湖北大学智能制造学院"))
        assertNull(FuzzyTextMatcher.match("湖北制造", "湖边北方制图造型"))
        assertNull(FuzzyTextMatcher.match("湖北制造", "湖北" + "很远".repeat(12) + "制造"))
        assertNull(FuzzyTextMatcher.match("智能指招", "智能制造"))
        assertNull(FuzzyTextMatcher.match("学校地址", "明天一起去吃饭"))
    }

    @Test fun ordinaryMatchesNeverRequestFuzzyPages() = runBlocking {
        val search = SuggestionSearch({ listOf(item(1, "湖北大学智能制造学院")) },
            { _, _ -> error("Unexpected fallback") })
        val result = search.search("湖北大学", true)
        assertFalse(result.isFuzzy)
        assertEquals(1L, result.candidates.single().item.id)
    }

    @Test fun identicalItemIsRemovedBeforeDecidingToFallBack() = runBlocking {
        val search = SuggestionSearch({ listOf(item(1, "智能制照")) }, { after, _ ->
            if (after == 0L) listOf(item(1, "智能制照"), item(2, "智能制造学院")) else emptyList()
        })
        val result = search.search("智能制照", true)
        assertTrue(result.isFuzzy)
        assertEquals(2L, result.candidates.single().item.id)
    }

    @Test fun shortQueriesAndDisabledSwitchDoNotScanLibrary() = runBlocking {
        val search = SuggestionSearch({ emptyList() }, { _, _ -> error("Unexpected scan") })
        assertTrue(search.search("湖北", true).candidates.isEmpty())
        assertTrue(search.search("湖北制造", false).candidates.isEmpty())
    }

    @Test fun collectsBestFiveAcrossPagesInsteadOfOnlyRecentItems() = runBlocking {
        val search = SuggestionSearch({ emptyList() }, { after, _ ->
            when (after) {
                0L -> (1L..6L).map { item(it, "智能制照$it") }
                6L -> listOf(item(7, "智能制照7").copy(favorite = true))
                else -> emptyList()
            }
        })
        val result = search.search("智能制造", true)
        assertTrue(result.isFuzzy)
        assertEquals(5, result.candidates.size)
        assertEquals(7L, result.candidates.first().item.id)
    }

    @Test fun similarityComesBeforeFavoriteStatus() = runBlocking {
        val search = SuggestionSearch({ emptyList() }, { after, _ ->
            if (after == 0L) listOf(item(1, "湖北大学智能制造学院").copy(favorite = true),
                item(2, "湖北制照学院")) else emptyList()
        })
        assertEquals(2L, search.search("湖北制造", true).candidates.first().item.id)
    }

    @Test fun databaseErrorDoesNotBecomeFallback() = runBlocking {
        val search = SuggestionSearch({ throw IllegalStateException("test") },
            { _, _ -> error("Must not scan on query failure") })
        try {
            search.search("湖北制造", true)
            fail("Expected database failure")
        } catch (expected: IllegalStateException) { assertEquals("test", expected.message) }
    }

    @Test fun cancellationIsNotSwallowed() = runBlocking {
        val search = SuggestionSearch({ throw CancellationException("test") },
            { _, _ -> error("Must not scan after cancellation") })
        try {
            search.search("湖北制造", true)
            fail("Expected cancellation")
        } catch (_: CancellationException) { /* Expected. */ }
    }
}
