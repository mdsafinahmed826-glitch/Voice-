package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationDao {
    @Query("SELECT * FROM generation_history ORDER BY createdAtEpochMs DESC LIMIT 50")
    fun observeHistory(): Flow<List<GenerationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: GenerationRecord): Long

    @Query("DELETE FROM generation_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM generation_history")
    suspend fun clearAll()
}
