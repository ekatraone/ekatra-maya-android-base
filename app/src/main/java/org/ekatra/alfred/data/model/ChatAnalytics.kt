package org.ekatra.alfred.data.model

/**
 * Analytics data for a chat session — synced to Firestore.
 * Not a Room entity; serialized to JSON for the sync queue.
 */
data class ChatAnalytics(
    val sessionId: String,
    val userId: String,
    val startTime: Long,
    val endTime: Long,
    val messageCount: Int,
    val userMessageCount: Int,
    val aiMessageCount: Int,
    val subject: String?,
    val modelId: String,
    val avgResponseTimeMs: Long,
    val deviceModel: String,
    val gpuUsed: Boolean,
    val savedCount: Int = 0,
    val sharedCount: Int = 0,
    val thumbsUpCount: Int = 0,
    val thumbsDownCount: Int = 0
)
