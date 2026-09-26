package com.smartclipboard.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

const val UNCATEGORIZED = "未分类"

@Entity(indices = [Index(value = ["name"], unique = true)])
data class LibraryCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconFile: String? = null
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM LibraryCategory ORDER BY id")
    fun observeAll(): Flow<List<LibraryCategory>>
    @Query("SELECT * FROM LibraryCategory ORDER BY id")
    suspend fun all(): List<LibraryCategory>
    @Query("SELECT * FROM LibraryCategory WHERE id = :id")
    suspend fun get(id: Long): LibraryCategory?
    @Query("SELECT * FROM LibraryCategory WHERE name = :name")
    suspend fun byName(name: String): LibraryCategory?
    @Insert suspend fun insert(category: LibraryCategory): Long
    @Update suspend fun update(category: LibraryCategory)
    @Query("DELETE FROM LibraryCategory WHERE id = :id")
    suspend fun delete(id: Long)
}

object CategoryRules {
    fun name(raw: String): String = raw.trim().also {
        require(it.isNotBlank() && it.length <= 24 && !it.contains('\n')) { "分类名称需要 1～24 个字，不能换行" }
    }
}
