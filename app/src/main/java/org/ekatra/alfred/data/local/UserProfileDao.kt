package org.ekatra.alfred.data.local

import androidx.room.*
import org.ekatra.alfred.data.model.UserProfile

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE uid = :uid")
    suspend fun getProfile(uid: String): UserProfile?

    @Upsert
    suspend fun upsert(profile: UserProfile)

    @Query("DELETE FROM user_profiles WHERE uid = :uid")
    suspend fun deleteProfile(uid: String)

    @Query("SELECT * FROM user_profiles LIMIT 1")
    suspend fun getAnyProfile(): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE uid LIKE 'offline_%' ORDER BY lastActiveAt DESC LIMIT 1")
    suspend fun getLatestOfflineProfile(): UserProfile?

    @Query("UPDATE chat_sessions SET userId = :newUid WHERE userId = :oldUid")
    suspend fun migrateSessionUserId(oldUid: String, newUid: String)

    @Query("UPDATE chat_messages SET userId = :newUid WHERE userId = :oldUid")
    suspend fun migrateMessageUserId(oldUid: String, newUid: String)

    @Query("UPDATE saved_answers SET userId = :newUid WHERE userId = :oldUid")
    suspend fun migrateSavedAnswerUserId(oldUid: String, newUid: String)
}
