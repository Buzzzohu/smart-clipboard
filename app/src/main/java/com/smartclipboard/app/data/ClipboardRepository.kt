package com.smartclipboard.app.data

import com.smartclipboard.app.classification.ClipboardCategory
import com.smartclipboard.app.classification.ClipboardCategoryClassifier
import kotlinx.coroutines.flow.Flow

/** Keeps persistence rules out of the screen and ViewModel. */
class ClipboardRepository(private val dao: ClipboardItemDao) {
    fun observeSearch(query: String, category: String?, favoritesOnly: Boolean): Flow<List<ClipboardItem>> =
        dao.observeSearch(query.trim(), category, favoritesOnly)

    private val suggestionSearch = SuggestionSearch(dao::findSuggestions, dao::suggestionPage)

    suspend fun findSuggestions(query: String, fuzzyEnabled: Boolean): SuggestionResult =
        suggestionSearch.search(query, fuzzyEnabled)

    suspend fun contains(content: String): Boolean = dao.contains(content)

    suspend fun save(content: String, tags: String = ""): Boolean {
        val normalized = content.trim()
        if (normalized.isEmpty()) return false
        val now = System.currentTimeMillis()
        val item = ClipboardItem(
            content = normalized,
            createdTime = now,
            updatedTime = now,
            category = ClipboardCategoryClassifier.classify(normalized).label,
            tags = tags.trim()
        )
        return dao.insert(item) != -1L
    }

    /** Explicit paste bypasses automatic-import filtering; the unique index handles stored repeats. */
    suspend fun importBatch(raw: String, stripSender: Boolean = false): BatchImportResult {
        val parsed = BatchImportParser.parse(raw, stripSender)
        if (parsed.entries.isEmpty()) return BatchImportResult(0, parsed.skipped)
        val now = System.currentTimeMillis()
        val items = parsed.entries.mapIndexed { index, content ->
            ClipboardItem(
                content = content,
                createdTime = now - index,
                updatedTime = now - index,
                category = ClipboardCategoryClassifier.classify(content).label
            )
        }
        val added = dao.insertAll(items).count { it != -1L }
        return BatchImportResult(added, parsed.skipped + items.size - added)
    }

    suspend fun edit(id: Long, content: String, tags: String): Boolean {
        val old = dao.getById(id) ?: return false
        val normalized = content.trim()
        if (normalized.isEmpty()) return false
        val updated = old.copy(
            content = normalized,
            updatedTime = System.currentTimeMillis(),
            category = ClipboardCategoryClassifier.classify(normalized).label,
            tags = tags.trim()
        )
        return dao.update(updated) > 0
    }

    suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    suspend fun toggleFavorite(id: Long): Boolean = dao.toggleFavorite(id) > 0

    suspend fun recordUse(id: Long): Boolean = dao.recordUse(id, System.currentTimeMillis()) > 0

    /** Updates entries saved before automatic classification was introduced. */
    suspend fun reclassifyExisting() {
        dao.getTextCategoryItems().forEach { item ->
            val category = ClipboardCategoryClassifier.classify(item.content)
            if (category != ClipboardCategory.TEXT) {
                dao.updateCategory(item.id, category.label, System.currentTimeMillis())
            }
        }
    }
}
