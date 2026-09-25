package com.opdownloader.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OpDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        private const val DB_NAME = "op_downloader.db"

        @Volatile
        private var INSTANCE: OpDatabase? = null

        fun getInstance(context: Context): OpDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OpDatabase::class.java,
                    DB_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
