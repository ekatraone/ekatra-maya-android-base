package org.ekatra.alfred.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,           // "chat_analytics", "user_profile"
    val payload: String,        // JSON payload
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
