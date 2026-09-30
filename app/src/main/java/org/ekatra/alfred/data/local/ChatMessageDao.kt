package org.ekatra.alfred.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.ekatra.alfred.data.model.ChatMessageEntity

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>>
    
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForSessionSync(sessionId: Long): List<ChatMessageEntity>
    
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getRecentMessages(sessionId: Long, limit: Int): List<ChatMessageEntity>
    
    @Insert
    suspend fun insert(message: ChatMessageEntity): Long
    
    @Insert
    suspend fun insertAll(messages: List<ChatMessageEntity>)
    
    @Delete
    suspend fun delete(message: ChatMessageEntity)
    
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteAllForSession(sessionId: Long)

    @Query("UPDATE chat_messages SET rating = :rating WHERE id = :messageId")
    suspend fun updateRating(messageId: Long, rating: Int)
}
