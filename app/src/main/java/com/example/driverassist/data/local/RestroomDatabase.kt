package com.example.driverassist.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OfflineRestroom::class], version = 1, exportSchema = false)
abstract class RestroomDatabase : RoomDatabase() {
    abstract fun restroomDao(): RestroomDao

    companion object {
        @Volatile
        private var INSTANCE: RestroomDatabase? = null

        fun getDatabase(context: Context): RestroomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RestroomDatabase::class.java,
                    "restroom_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
