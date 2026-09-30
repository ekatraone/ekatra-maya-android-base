package org.ekatra.alfred.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.ekatra.alfred.data.model.SavedAnswer
import org.ekatra.alfred.data.model.SavedChart
import org.ekatra.alfred.data.model.ChatSession
import org.ekatra.alfred.data.model.ChatMessageEntity
import org.ekatra.alfred.data.model.UserProfile
import org.ekatra.alfred.data.model.SyncQueueItem

/**
 * Room migration v3 → v4:
 * - Add userId column to chat_sessions, chat_messages, saved_answers
 * - Add rating column to chat_messages
 * - Create user_profiles table
 * - Create sync_queue table
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add userId to chat_sessions
        db.execSQL("ALTER TABLE chat_sessions ADD COLUMN userId TEXT")
        // Add userId and rating to chat_messages
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN userId TEXT")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
        // Add userId to saved_answers
        db.execSQL("ALTER TABLE saved_answers ADD COLUMN userId TEXT")
        // Create user_profiles table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS user_profiles (
                uid TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                phoneNumber TEXT NOT NULL DEFAULT '',
                country TEXT NOT NULL DEFAULT 'India',
                grade TEXT NOT NULL DEFAULT '',
                preferredLanguage TEXT NOT NULL DEFAULT 'en',
                createdAt INTEGER NOT NULL DEFAULT 0,
                lastActiveAt INTEGER NOT NULL DEFAULT 0,
                analyticsConsent INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        // Create sync_queue table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type TEXT NOT NULL,
                payload TEXT NOT NULL,
                createdAt INTEGER NOT NULL DEFAULT 0,
                retryCount INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

@Database(
    entities = [
        SavedAnswer::class,
        SavedChart::class,
        ChatSession::class,
        ChatMessageEntity::class,
        UserProfile::class,
        SyncQueueItem::class
    ],
    version = 4,
    exportSchema = true
)
abstract class EkatraDatabase : RoomDatabase() {
    abstract fun savedAnswerDao(): SavedAnswerDao
    abstract fun savedChartDao(): SavedChartDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile private var INSTANCE: EkatraDatabase? = null

        fun getInstance(context: Context): EkatraDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EkatraDatabase::class.java,
                    "ekatra.db"
                )
                .addMigrations(MIGRATION_3_4)
                .build().also {
                    it.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                    INSTANCE = it
                    Log.d("EkatraDatabase", "Room database initialized v4")
                }
            }
    }
}
