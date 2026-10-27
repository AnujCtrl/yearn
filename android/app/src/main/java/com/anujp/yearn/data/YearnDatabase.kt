package com.anujp.yearn.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CaptureEntity::class, FileEntity::class], version = 1, exportSchema = true)
abstract class YearnDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    companion object {
        fun create(context: Context): YearnDatabase =
            Room.databaseBuilder(context.applicationContext, YearnDatabase::class.java, "yearn.db").build()
    }
}
