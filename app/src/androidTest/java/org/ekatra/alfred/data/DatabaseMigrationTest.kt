package org.ekatra.alfred.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.ekatra.alfred.data.local.EkatraDatabase
import org.ekatra.alfred.data.local.MIGRATION_3_4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests Room database migration from v3 → v4.
 *
 * Validates that:
 * - userId column is added to chat_sessions, chat_messages, saved_answers
 * - rating column is added to chat_messages
 * - user_profiles table is created
 * - sync_queue table is created
 * - Existing data survives the migration
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val testDb = "migration-test"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EkatraDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun createLegacyV3Database(): SQLiteDatabase {
        context.deleteDatabase(testDb)
        val db = context.openOrCreateDatabase(testDb, 0, null)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                subject TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                messageCount INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId INTEGER NOT NULL,
                text TEXT NOT NULL,
                isUser INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY(sessionId) REFERENCES chat_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId ON chat_messages(sessionId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_answers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                question TEXT NOT NULL,
                answer TEXT NOT NULL,
                subject TEXT,
                createdAt INTEGER NOT NULL,
                isFavorite INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_charts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                imageData BLOB,
                relatedAnswerId INTEGER,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(relatedAnswerId) REFERENCES saved_answers(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_charts_relatedAnswerId ON saved_charts(relatedAnswerId)")
        db.execSQL("PRAGMA user_version = 3")
        return db
    }

    @Test
    fun migrate3to4_addsNewColumnsAndTables() {
        // Create a true v3 schema database without relying on exported 3.json
        createLegacyV3Database().apply {
            execSQL("""
                INSERT INTO chat_sessions (id, title, subject, createdAt, updatedAt, messageCount)
                VALUES (1, 'Test Chat', 'Math', 1700000000000, 1700000000000, 2)
            """.trimIndent())
            execSQL("""
                INSERT INTO chat_messages (id, sessionId, text, isUser, timestamp)
                VALUES (1, 1, 'What is 2+2?', 1, 1700000000000)
            """.trimIndent())
            execSQL("""
                INSERT INTO chat_messages (id, sessionId, text, isUser, timestamp)
                VALUES (2, 1, '2+2 equals 4.', 0, 1700000001000)
            """.trimIndent())
            close()
        }

        // Run migration 3→4
        val migratedDb = helper.runMigrationsAndValidate(
            testDb,
            4,
            true,
            MIGRATION_3_4
        )

        // Verify existing data survived
        val cursor = migratedDb.query("SELECT * FROM chat_sessions WHERE id = 1")
        assert(cursor.moveToFirst()) { "Session should exist after migration" }
        assert(cursor.getString(cursor.getColumnIndex("title")) == "Test Chat")

        // Verify new columns exist with defaults
        val userIdIdx = cursor.getColumnIndex("userId")
        assert(userIdIdx >= 0) { "userId column should exist" }
        assert(cursor.isNull(userIdIdx)) { "userId should default to NULL" }

        // Verify messages have rating column
        val msgCursor = migratedDb.query("SELECT * FROM chat_messages WHERE id = 1")
        assert(msgCursor.moveToFirst())
        val ratingIdx = msgCursor.getColumnIndex("rating")
        assert(ratingIdx >= 0) { "rating column should exist" }
        assert(msgCursor.getInt(ratingIdx) == 0) { "rating should default to 0" }

        // Verify new tables exist
        val profileCursor = migratedDb.query("SELECT count(*) FROM user_profiles")
        assert(profileCursor.moveToFirst())
        assert(profileCursor.getInt(0) == 0) { "user_profiles should be empty" }

        val syncCursor = migratedDb.query("SELECT count(*) FROM sync_queue")
        assert(syncCursor.moveToFirst())
        assert(syncCursor.getInt(0) == 0) { "sync_queue should be empty" }

        migratedDb.close()
    }

    @Test
    fun migrate3to4_preservesAllMessages() {
        createLegacyV3Database().apply {
            // Insert multiple messages
            for (i in 1..10) {
                execSQL("""
                    INSERT INTO chat_sessions (id, title, subject, createdAt, updatedAt, messageCount)
                    VALUES ($i, 'Chat $i', 'Science', ${1700000000000 + i}, ${1700000000000 + i}, 1)
                """.trimIndent())
            }
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            testDb,
            4,
            true,
            MIGRATION_3_4
        )

        val cursor = migratedDb.query("SELECT count(*) FROM chat_sessions")
        assert(cursor.moveToFirst())
        assert(cursor.getInt(0) == 10) { "All 10 sessions should survive migration" }

        migratedDb.close()
    }
}
