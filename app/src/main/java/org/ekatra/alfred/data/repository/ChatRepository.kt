package org.ekatra.alfred.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import org.ekatra.alfred.data.local.ChatMessageDao
import org.ekatra.alfred.data.local.ChatSessionDao
import org.ekatra.alfred.data.model.ChatSession
import org.ekatra.alfred.data.model.ChatMessageEntity
import org.ekatra.alfred.persona.ActivePersona
import org.ekatra.alfred.persona.Persona

/**
 * Repository for managing chat sessions and messages.
 * Injected via Hilt (see DatabaseModule).
 */
class ChatRepository(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
    private val persona: Persona = ActivePersona
) {
    private val gson = Gson()

    // Session operations
    fun getAllSessions(): Flow<List<ChatSession>> = sessionDao.getAllSessions()
    
    fun getRecentSessions(): Flow<List<ChatSession>> = sessionDao.getRecentSessions()
    
    suspend fun getSession(sessionId: Long): ChatSession? = sessionDao.getSession(sessionId)
    
    suspend fun createSession(subject: String? = null, userId: String? = null): Long {
        val session = ChatSession(
            title = "New Chat",
            subject = subject,
            userId = userId
        )
        return sessionDao.insert(session)
    }
    
    suspend fun updateSessionTitle(sessionId: Long, title: String) {
        sessionDao.updateTitle(sessionId, title)
    }
    
    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteById(sessionId)
    }
    
    suspend fun deleteAllSessions() {
        sessionDao.deleteAll()
    }
    
    // Message operations
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>> {
        return messageDao.getMessagesForSession(sessionId)
    }
    
    suspend fun getMessagesForSessionSync(sessionId: Long): List<ChatMessageEntity> {
        return messageDao.getMessagesForSessionSync(sessionId)
    }
    
    suspend fun getRecentMessages(sessionId: Long, limit: Int = 10): List<ChatMessageEntity> {
        return messageDao.getRecentMessages(sessionId, limit)
    }
    
    suspend fun addMessage(sessionId: Long, text: String, isUser: Boolean, userId: String? = null): Long {
        val message = ChatMessageEntity(
            sessionId = sessionId,
            text = text,
            isUser = isUser,
            userId = userId
        )
        val messageId = messageDao.insert(message)
        
        // Update session timestamp and count
        sessionDao.updateSessionTimestamp(sessionId)
        
        // If this is the first user message, use it as the title
        if (isUser) {
            val session = sessionDao.getSession(sessionId)
            if (session?.title == "New Chat") {
                val title = text.take(40) + if (text.length > 40) "..." else ""
                sessionDao.updateTitle(sessionId, title)
            }
        }
        
        return messageId
    }

    suspend fun rateMessage(messageId: Long, rating: Int) {
        messageDao.updateRating(messageId, rating)
    }
    
    /**
     * Get conversation history formatted for LLM context
     */
    suspend fun getConversationContext(sessionId: Long, maxMessages: Int = 6): String {
        val allMessages = messageDao.getMessagesForSessionSync(sessionId)
        val messages = allMessages.takeLast(maxMessages)
        if (messages.isEmpty()) return ""

        val summary = if (allMessages.size > maxMessages) {
            summarizeOlderMessages(allMessages.dropLast(maxMessages))
        } else {
            ""
        }

        val recentTurns = messages.joinToString("\n") { msg ->
            if (msg.isUser) "${persona.studentLabel}: ${msg.text}" else "${persona.assistantName}: ${msg.text}"
        }

        return if (summary.isBlank()) recentTurns else "$summary\n$recentTurns"
    }

    /**
     * S.1: Return the last [maxMessages] turns as a plain list so PromptFormatter
     * can emit properly alternating chat-ML blocks rather than a flat string.
     * Excludes the most-recent user message if it is the last entry — the caller
     * supplies that as the new turn.
     */
    suspend fun getConversationTurns(sessionId: Long, maxMessages: Int = 6): List<ChatMessageEntity> {
        val all = messageDao.getMessagesForSessionSync(sessionId)
        val history = if (all.isNotEmpty() && all.last().isUser) all.dropLast(1) else all
        return history.takeLast(maxMessages)
    }

    private fun summarizeOlderMessages(messages: List<ChatMessageEntity>): String {
        if (messages.isEmpty()) return ""
        val userTopics = messages
            .filter { it.isUser }
            .takeLast(3)
            .map { it.text.take(80) }
        val assistantHints = messages
            .filter { !it.isUser }
            .takeLast(2)
            .map { it.text.take(120) }

        return buildString {
            appendLine(persona.conversationRecapHeader)
            if (userTopics.isNotEmpty()) {
                appendLine(persona.recapStudentAsked(userTopics.joinToString(" | ")))
            }
            if (assistantHints.isNotEmpty()) {
                appendLine(persona.recapAssistantExplained(assistantHints.joinToString(" | ")))
            }
        }.trimEnd()
    }

    /**
     * Export all chat data as JSON for privacy compliance
     */
    suspend fun exportAllAsJson(): String {
        val sessions = sessionDao.getAllSessionsSync()
        val data = sessions.map { session ->
            val messages = messageDao.getMessagesForSessionSync(session.id)
            mapOf(
                "session" to session,
                "messages" to messages
            )
        }
        return gson.toJson(data)
    }
}
