package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generation_history")
data class GenerationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scriptText: String,
    val languageCode: String,
    val outputFilePath: String,
    val outputFormat: String,
    val referenceName: String,
    val engineUsed: String,
    val durationMs: Long,
    val speed: Float,
    val similarity: Float,
    val temperature: Float,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
