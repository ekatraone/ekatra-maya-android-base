package org.ekatra.alfred.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_answers")
data class SavedAnswer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val answer: String,
    val subject: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val userId: String? = null
)
