package org.ekatra.alfred.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.ekatra.alfred.data.model.SyncQueueItem

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(item: SyncQueueItem): Long

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 50): List<SyncQueueItem>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun getPendingCount(): Flow<Int>

    @Delete
    suspend fun delete(item: SyncQueueItem)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue")
    suspend fun deleteAll()

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)
}
