package org.ekatra.alfred.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a chat session/conversation with the tutor
 */
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,           // Auto-generated from first message
    val subject: String? = null, // Selected subject if any
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val userId: String? = null
)
