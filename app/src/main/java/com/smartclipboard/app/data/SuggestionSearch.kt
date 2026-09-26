package com.smartclipboard.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class SuggestionCandidate(val item: ClipboardItem, val matchStart: Int = 0)
data class SuggestionResult(val candidates: List<SuggestionCandidate>, val isFuzzy: Boolean = false)

/** The page reader is lazy: normal matches, short queries and an off switch never scan the library. */
internal class SuggestionSearch(
    private val exactLookup: suspend (String) -> List<ClipboardItem>,
    private val readPage: suspend (Long, Int) -> List<ClipboardItem>
) {
    private data class Ranked(val item: ClipboardItem, val match: FuzzyTextMatcher.Match)
    private val order = compareByDescending<Ranked> { it.match.score }
        .thenByDescending { it.item.favorite }
        .thenByDescending { it.item.lastUsedTime }
        .thenByDescending { it.item.updatedTime }
        .thenByDescending { it.item.id }

    suspend fun search(rawQuery: String, fuzzyEnabled: Boolean): SuggestionResult = withContext(Dispatchers.Default) {
        val query = rawQuery.trim()
        if (query.isBlank()) return@withContext SuggestionResult(emptyList())
        val exact = exactLookup(query).filterNot { it.content.equals(query, ignoreCase = true) }
        if (exact.isNotEmpty()) return@withContext SuggestionResult(exact.take(20).map {
            SuggestionCandidate(it, it.content.indexOf(query, ignoreCase = true).coerceAtLeast(0))
        })
        if (!fuzzyEnabled || query.length !in 4..40 || query.contains('\n')) {
            return@withContext SuggestionResult(emptyList())
        }
        val best = mutableListOf<Ranked>()
        var afterId = 0L
        // Keyset pagination keeps memory bounded and includes old entries, not just recent ones.
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = readPage(afterId, 200)
            if (page.isEmpty()) break
            for (item in page) {
                currentCoroutineContext().ensureActive()
                FuzzyTextMatcher.match(query, item.content)?.let { match ->
                    best.add(Ranked(item, match))
                    best.sortWith(order)
                    if (best.size > 5) best.removeAt(best.lastIndex)
                }
            }
            afterId = page.last().id
        }
        SuggestionResult(best.map { SuggestionCandidate(it.item, it.match.start) }, isFuzzy = best.isNotEmpty())
    }
}
