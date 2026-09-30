package org.ekatra.alfred.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.ekatra.alfred.data.model.SavedAnswer

@Dao
interface SavedAnswerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(answer: SavedAnswer): Long

    @Query("SELECT * FROM saved_answers ORDER BY createdAt DESC")
    suspend fun getAll(): List<SavedAnswer>

    @Update
    suspend fun update(answer: SavedAnswer)

    @Query("DELETE FROM saved_answers WHERE id = :id")
    suspend fun delete(id: Long)
}
