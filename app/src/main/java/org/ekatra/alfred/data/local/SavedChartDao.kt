package org.ekatra.alfred.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ekatra.alfred.data.model.SavedChart

@Dao
interface SavedChartDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chart: SavedChart): Long

    @Query("SELECT * FROM saved_charts ORDER BY createdAt DESC")
    suspend fun getAll(): List<SavedChart>
}
