package com.dev.where.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GpsPoint::class], version = 1, exportSchema = false)
abstract class WhereDatabase : RoomDatabase() {

    abstract fun gpsPointDao(): GpsPointDao

    companion object {
        @Volatile
        private var INSTANCE: WhereDatabase? = null

        fun getInstance(context: Context): WhereDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WhereDatabase::class.java,
                    "where_db"
                ).build().also { INSTANCE = it }
            }
    }
}