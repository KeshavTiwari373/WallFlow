package com.keshav.wallflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {

    @Insert
    suspend fun insert(wallpaper: WallpaperEntity): Long

    @Transaction
    @Query("SELECT * FROM wallpapers ORDER BY dateAdded DESC")
    fun observeAllWithTags(): Flow<List<WallpaperWithTags>>

    @Transaction
    @Query(
        """
        SELECT * FROM wallpapers
        WHERE id IN (SELECT wallpaperId FROM wallpaper_tag_cross_ref WHERE tagId = :tagId)
        ORDER BY dateAdded DESC
        """,
    )
    fun observeByTagWithTags(tagId: Long): Flow<List<WallpaperWithTags>>

    @Transaction
    @Query("SELECT * FROM wallpapers WHERE id = :id")
    fun observeWithTags(id: Long): Flow<WallpaperWithTags?>

    @Query("SELECT * FROM wallpapers WHERE id = :id")
    suspend fun getById(id: Long): WallpaperEntity?

    @Query("SELECT COUNT(*) FROM wallpapers")
    suspend fun count(): Int

    /**
     * Least-recently-used pick: never-used first (lastUsedAt NULL), then oldest lastUsedAt, then
     * least usageCount; RANDOM() breaks ties. Avoids "NULLS FIRST" syntax for SQLite < 3.30 (API 26).
     */
    @Query(
        """
        SELECT * FROM wallpapers
        ORDER BY (last_used_at IS NULL) DESC, last_used_at ASC, usageCount ASC, RANDOM()
        LIMIT 1
        """,
    )
    suspend fun pickLeastRecentlyUsed(): WallpaperEntity?

    @Query(
        """
        SELECT * FROM wallpapers
        WHERE id IN (SELECT wallpaperId FROM wallpaper_tag_cross_ref WHERE tagId = :tagId)
        ORDER BY (last_used_at IS NULL) DESC, last_used_at ASC, usageCount ASC, RANDOM()
        LIMIT 1
        """,
    )
    suspend fun pickLeastRecentlyUsedByTag(tagId: Long): WallpaperEntity?

    @Query("UPDATE wallpapers SET usageCount = usageCount + 1, last_used_at = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)

    @Query(
        """
        UPDATE wallpapers
        SET processedHomePath = :homePath, processedLockPath = :lockPath, editParamsJson = :editParamsJson
        WHERE id = :id
        """,
    )
    suspend fun updateProcessed(id: Long, homePath: String?, lockPath: String?, editParamsJson: String?)

    @Query("UPDATE wallpapers SET upscaledPath = :upscaledPath WHERE id = :id")
    suspend fun updateUpscaledPath(id: Long, upscaledPath: String?)

    @Query("DELETE FROM wallpapers WHERE id = :id")
    suspend fun deleteById(id: Long)

    // --- Tag relations ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagCrossRef(ref: WallpaperTagCrossRef)

    @Query("DELETE FROM wallpaper_tag_cross_ref WHERE wallpaperId = :wallpaperId AND tagId = :tagId")
    suspend fun deleteTagCrossRef(wallpaperId: Long, tagId: Long)
}
