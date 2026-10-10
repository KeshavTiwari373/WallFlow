package com.keshav.wallflow.di

import android.content.Context
import androidx.room.Room
import com.keshav.wallflow.data.local.AppDatabase
import com.keshav.wallflow.data.local.MIGRATION_1_2
import com.keshav.wallflow.data.local.MIGRATION_2_3
import com.keshav.wallflow.data.local.TagDao
import com.keshav.wallflow.data.local.WallpaperDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideWallpaperDao(db: AppDatabase): WallpaperDao = db.wallpaperDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
}
