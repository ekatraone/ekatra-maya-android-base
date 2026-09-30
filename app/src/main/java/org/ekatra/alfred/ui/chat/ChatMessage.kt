package org.ekatra.alfred.ui.chat


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Stable

@Stable
data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val relatedQuestion: String? = null,
    val subject: String? = null,
    val rating: Int = 0  // 0 = unrated, 1 = thumbs up, -1 = thumbs down
)

// Example questions - defined once to avoid recreation
