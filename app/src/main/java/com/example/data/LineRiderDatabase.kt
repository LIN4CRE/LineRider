package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class], version = 1, exportSchema = false)
abstract class LineRiderDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile
        private var INSTANCE: LineRiderDatabase? = null

        fun getInstance(context: Context): LineRiderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LineRiderDatabase::class.java,
                    "line_rider.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
