package com.keshav.wallflow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WallpaperEntity::class,
        TagEntity::class,
        WallpaperTagCrossRef::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun tagDao(): TagDao

    companion object {
        const val NAME = "wallflow.db"
    }
}
