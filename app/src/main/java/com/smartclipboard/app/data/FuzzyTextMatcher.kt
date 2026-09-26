package com.smartclipboard.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Local, conservative matching. Never combines spelling errors with skipped phrases. */
internal object FuzzyTextMatcher {
    data class Match(val score: Int, val start: Int, val end: Int)

    suspend fun match(query: String, content: String): Match? {
        if (query.length !in 4..40 || query.contains('\n') ||
            content.contains(query, ignoreCase = true)) return null
        var best: Match? = null
        fun keep(candidate: Match) {
            val old = best
            if (old == null || candidate.score > old.score ||
                (candidate.score == old.score && candidate.end - candidate.start < old.end - old.start)) {
                best = candidate
            }
        }

        // One insertion, deletion or substitution within a local text span.
        // Stop comparing a span immediately on its second error.
        for (start in content.indices) {
            if (start % 256 == 0) currentCoroutineContext().ensureActive()
            for (size in (query.length - 1)..(query.length + 1)) {
                if (start + size <= content.length && oneEdit(query, content, start, size)) {
                    keep(Match(1000 - 1000 / query.length, start, start + size))
                }
            }
        }

        // Ordered phrase fragments, each at least two characters. At most two
        // gaps and eight skipped characters avoid matching scattered common words.
        val maxSkipped = minOf(8, query.length)
        fun extend(start: Int, qStart: Int, tStart: Int, skipped: Int, gaps: Int) {
            var run = 0
            while (qStart + run < query.length && tStart + run < content.length &&
                query[qStart + run].equals(content[tStart + run], ignoreCase = true)) {
                run++
                if (run < 2) continue
                val nextQ = qStart + run
                val nextT = tStart + run
                if (nextQ == query.length) {
                    if (gaps > 0) keep(Match(1000 - 100 * gaps - 200 * skipped / query.length, start, nextT))
                } else if (gaps < 2 && query.length - nextQ >= 2) {
                    for (gap in 1..(maxSkipped - skipped)) {
                        if (nextT + gap + 2 <= content.length &&
                            content.regionMatches(nextT + gap, query, nextQ, 2, ignoreCase = true)) {
                            extend(start, nextQ, nextT + gap, skipped + gap, gaps + 1)
                        }
                    }
                }
            }
        }
        for (start in content.indices) {
            if (start % 128 == 0) currentCoroutineContext().ensureActive()
            if (content.regionMatches(start, query, 0, 2, ignoreCase = true)) {
                extend(start, 0, start, 0, 0)
            }
        }
        return best?.takeIf { it.score >= 650 }
    }

    private fun oneEdit(query: String, content: String, start: Int, size: Int): Boolean {
        var q = 0
        var t = 0
        var errors = 0
        while (q < query.length && t < size) {
            if (query[q].equals(content[start + t], ignoreCase = true)) { q++; t++; continue }
            if (++errors > 1) return false
            when {
                query.length > size -> q++
                query.length < size -> t++
                else -> { q++; t++ }
            }
        }
        return errors + (query.length - q) + (size - t) == 1
    }
}
