package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GenerationRecord::class], version = 1, exportSchema = false)
abstract class VoxCloneDatabase : RoomDatabase() {
    abstract fun generationDao(): GenerationDao

    companion object {
        @Volatile
        private var INSTANCE: VoxCloneDatabase? = null

        fun getInstance(context: Context): VoxCloneDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoxCloneDatabase::class.java,
                    "voxclone_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
