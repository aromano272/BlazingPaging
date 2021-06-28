package com.andreromano.blazingpaging.sample.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DataEntity::class,
        CheckedDataEntity::class,
    ],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dataDao(): DataDao
}