package org.ekatra.alfred.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "saved_charts",
    foreignKeys = [
        ForeignKey(
            entity = SavedAnswer::class,
            parentColumns = ["id"],
            childColumns = ["relatedAnswerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("relatedAnswerId")]
)
data class SavedChart(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val imageData: ByteArray?,
    val relatedAnswerId: Long?,
    val createdAt: Long = System.currentTimeMillis()
)
