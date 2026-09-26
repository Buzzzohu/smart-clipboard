package com.smartclipboard.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ClipboardItem::class, LibraryCategory::class], version = 3, exportSchema = true)
abstract class ClipboardDatabase : RoomDatabase() {
    abstract fun clipboardItemDao(): ClipboardItemDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile private var instance: ClipboardDatabase? = null
        private fun seedCategories(db: SupportSQLiteDatabase) {
            listOf(UNCATEGORIZED, "文本", "网址", "邮箱", "联系方式").forEach {
                db.execSQL("INSERT OR IGNORE INTO LibraryCategory(name, iconFile) VALUES (?, NULL)", arrayOf(it))
            }
            // Preserve all legacy names, including categories from earlier installations.
            db.execSQL("INSERT OR IGNORE INTO LibraryCategory(name, iconFile) SELECT DISTINCT category, NULL FROM ClipboardItem")
        }
        val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS LibraryCategory (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, iconFile TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_LibraryCategory_name ON LibraryCategory(name)")
                seedCategories(db)
            }
        }
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing rows remain intact; older items have no recorded last-use time.
                db.execSQL("ALTER TABLE ClipboardItem ADD COLUMN lastUsedTime INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): ClipboardDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClipboardDatabase::class.java,
                    "smart_clipboard.db"
                ).addMigrations(migration1To2, migration2To3)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) { seedCategories(db) }
                    }).build().also { instance = it }
            }
    }
}
