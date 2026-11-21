package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CompletedLevelEntity::class,
        DailyChallengeEntity::class,
        SavedBoardEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ArrowBrainDatabase : RoomDatabase() {
    abstract fun arrowBrainDao(): ArrowBrainDao

    companion object {
        @Volatile
        private var INSTANCE: ArrowBrainDatabase? = null

        fun getInstance(context: Context): ArrowBrainDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArrowBrainDatabase::class.java,
                    "arrow_brain_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
