package com.smartclipboard.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ClipboardItem::class], version = 2, exportSchema = true)
abstract class ClipboardDatabase : RoomDatabase() {
    abstract fun clipboardItemDao(): ClipboardItemDao

    companion object {
        @Volatile private var instance: ClipboardDatabase? = null
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
                ).addMigrations(migration1To2).build().also { instance = it }
            }
    }
}
