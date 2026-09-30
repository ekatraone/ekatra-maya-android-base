package org.ekatra.alfred.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.ekatra.alfred.data.model.ChatSession

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>
    
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC LIMIT 10")
    fun getRecentSessions(): Flow<List<ChatSession>>
    
    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): ChatSession?
    
    @Insert
    suspend fun insert(session: ChatSession): Long
    
    @Update
    suspend fun update(session: ChatSession)
    
    @Query("UPDATE chat_sessions SET updatedAt = :time, messageCount = messageCount + 1 WHERE id = :sessionId")
    suspend fun updateSessionTimestamp(sessionId: Long, time: Long = System.currentTimeMillis())
    
    @Query("UPDATE chat_sessions SET title = :title WHERE id = :sessionId")
    suspend fun updateTitle(sessionId: Long, title: String)
    
    @Delete
    suspend fun delete(session: ChatSession)
    
    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long)
    
    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAll()

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    suspend fun getAllSessionsSync(): List<ChatSession>
}
