package com.smartclipboard.app.data

import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction

/** Keeps persistence rules out of the screen and ViewModel. */
class ClipboardRepository(private val database: ClipboardDatabase) {
    private val dao = database.clipboardItemDao()
    private val categories = database.categoryDao()
    val categoryList = categories.observeAll()
    private suspend fun categoryName(id: Long) = categories.get(id)?.name ?: UNCATEGORIZED
    fun observeSearch(query: String, category: String?, favoritesOnly: Boolean): Flow<List<ClipboardItem>> =
        dao.observeSearch(query.trim(), category, favoritesOnly)

    private val suggestionSearch = SuggestionSearch(dao::findSuggestions, dao::suggestionPage,
        categoryLookup = { name ->
            database.withTransaction {
                // Null means no exact category; an empty list means the category exists but is empty.
                categories.byName(name)?.let { dao.categorySuggestions(it.name) }
            }
        })

    suspend fun findSuggestions(query: String, fuzzyEnabled: Boolean): SuggestionResult =
        suggestionSearch.search(query, fuzzyEnabled)

    suspend fun contains(content: String): Boolean = dao.contains(content)

    suspend fun save(content: String, tags: String = "", categoryId: Long = 1): Boolean = database.withTransaction {
        val normalized = content.trim()
        if (normalized.isEmpty()) return@withTransaction false
        val now = System.currentTimeMillis()
        val item = ClipboardItem(
            content = normalized,
            createdTime = now,
            updatedTime = now,
            category = categoryName(categoryId),
            tags = tags.trim()
        )
        dao.insert(item) != -1L
    }

    /** Explicit paste bypasses automatic-import filtering; the unique index handles stored repeats. */
    suspend fun importBatch(raw: String, stripSender: Boolean = false, categoryId: Long = 1): BatchImportResult = database.withTransaction {
        val parsed = BatchImportParser.parse(raw, stripSender)
        if (parsed.entries.isEmpty()) return@withTransaction BatchImportResult(0, parsed.skipped)
        val now = System.currentTimeMillis()
        val selectedCategory = categoryName(categoryId)
        val items = parsed.entries.mapIndexed { index, content ->
            ClipboardItem(
                content = content,
                createdTime = now - index,
                updatedTime = now - index,
                category = selectedCategory
            )
        }
        val added = dao.insertAll(items).count { it != -1L }
        BatchImportResult(added, parsed.skipped + items.size - added)
    }

    suspend fun edit(id: Long, content: String, tags: String, categoryId: Long = 1): Boolean = database.withTransaction {
        val old = dao.getById(id) ?: return@withTransaction false
        val normalized = content.trim()
        if (normalized.isEmpty()) return@withTransaction false
        val updated = old.copy(
            content = normalized,
            updatedTime = System.currentTimeMillis(),
            category = categoryName(categoryId),
            tags = tags.trim()
        )
        dao.update(updated) > 0
    }

    suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    suspend fun toggleFavorite(id: Long): Boolean = dao.toggleFavorite(id) > 0

    suspend fun recordUse(id: Long): Boolean = dao.recordUse(id, System.currentTimeMillis()) > 0

    suspend fun saveCategory(id: Long?, rawName: String, iconFile: String?): Long = database.withTransaction {
        val name = CategoryRules.name(rawName)
        require(id != 1L) { "未分类不能修改" }
        val duplicate = categories.byName(name)
        require(duplicate == null || duplicate.id == id) { "分类名称已存在" }
        if (id == null) categories.insert(LibraryCategory(name = name, iconFile = iconFile))
        else {
            val old = requireNotNull(categories.get(id)) { "分类已删除" }
            categories.update(old.copy(name = name, iconFile = iconFile))
            dao.renameCategory(old.name, name)
            id
        }
    }

    suspend fun deleteCategory(id: Long) = database.withTransaction {
        require(id != 1L) { "未分类不能删除" }
        val old = categories.get(id) ?: return@withTransaction
        dao.renameCategory(old.name, UNCATEGORIZED)
        categories.delete(id)
    }

    suspend fun moveItems(ids: Set<Long>, categoryId: Long): Int = database.withTransaction {
        val name = categoryName(categoryId)
        // Stay below SQLite's bind argument limit for large selections.
        ids.toList().chunked(400).sumOf { dao.moveToCategory(it, name, System.currentTimeMillis()) }
    }
}
