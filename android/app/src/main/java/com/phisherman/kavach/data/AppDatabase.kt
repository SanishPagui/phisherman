package com.phisherman.kavach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Decision::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun decisionDao(): DecisionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                ctx.applicationContext, AppDatabase::class.java, "kavach.db"
            ).build().also { instance = it }
        }
    }
}
