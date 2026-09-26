package com.smartclipboard.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The schema includes the requested fields now, so later features can extend behavior in place. */
@Entity(indices = [Index(value = ["content"], unique = true)])
data class ClipboardItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val createdTime: Long,
    val updatedTime: Long,
    val category: String = UNCATEGORIZED,
    val tags: String = "",
    val favorite: Boolean = false,
    val useCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastUsedTime: Long = 0
)
