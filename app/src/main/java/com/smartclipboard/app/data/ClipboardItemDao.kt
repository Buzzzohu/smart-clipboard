package com.smartclipboard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardItemDao {
    @Query("UPDATE ClipboardItem SET category = :newName WHERE category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String)

    @Query("UPDATE ClipboardItem SET category = :category, updatedTime = :time WHERE id IN (:ids)")
    suspend fun moveToCategory(ids: List<Long>, category: String, time: Long): Int
    /** instr searches for literal substrings, so %, _ and Chinese text work as entered. */
    @Query("""
        SELECT * FROM ClipboardItem
        WHERE (:category IS NULL OR category = :category)
          AND (:favoritesOnly = 0 OR favorite = 1)
          AND (:query = ''
            OR instr(lower(content), lower(:query)) > 0
            OR instr(lower(category), lower(:query)) > 0
            OR instr(lower(tags), lower(:query)) > 0)
        ORDER BY updatedTime DESC, id DESC
    """)
    fun observeSearch(query: String, category: String?, favoritesOnly: Boolean): Flow<List<ClipboardItem>>

    /** Bound cross-app queries so the scrollable candidate list stays responsive. */
    @Query("""
        SELECT * FROM ClipboardItem
        WHERE instr(lower(content), lower(:query)) > 0
          AND lower(content) != lower(:query)
        ORDER BY favorite DESC, lastUsedTime DESC, updatedTime DESC
        LIMIT 20
    """)
    suspend fun findSuggestions(query: String): List<ClipboardItem>

    @Query("SELECT * FROM ClipboardItem WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun suggestionPage(afterId: Long, limit: Int): List<ClipboardItem>

    @Query("SELECT EXISTS(SELECT 1 FROM ClipboardItem WHERE content = :content)")
    suspend fun contains(content: String): Boolean

    @Query("SELECT * FROM ClipboardItem WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ClipboardItem?

    /** A unique index and IGNORE keep repeated imports from creating duplicate rows. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ClipboardItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ClipboardItem>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun update(item: ClipboardItem): Int

    @Query("DELETE FROM ClipboardItem WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE ClipboardItem SET favorite = CASE favorite WHEN 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleFavorite(id: Long): Int

    @Query("UPDATE ClipboardItem SET useCount = useCount + 1, lastUsedTime = :time WHERE id = :id")
    suspend fun recordUse(id: Long, time: Long): Int

    @Query("SELECT * FROM ClipboardItem WHERE category = '文本'")
    suspend fun getTextCategoryItems(): List<ClipboardItem>

    @Query("UPDATE ClipboardItem SET category = :category, updatedTime = :updatedTime WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String, updatedTime: Long)
}
