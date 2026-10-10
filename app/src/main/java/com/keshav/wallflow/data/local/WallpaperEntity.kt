package com.keshav.wallflow.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single imported wallpaper. The image bytes live on disk ([filePath]); only metadata is stored
 * in Room. [uuid] is the durable identity used for backup/restore and future sync — never rely on
 * the auto-generated [id] for anything that leaves the device.
 */
@Entity(
    tableName = "wallpapers",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class WallpaperEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val dateAdded: Long,
    val usageCount: Int = 0,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
    /** Processed bitmap rendered for the HOME surface; null until the wallpaper is edited. */
    val processedHomePath: String? = null,
    /** Processed bitmap rendered for the LOCK surface; null until the wallpaper is edited. */
    val processedLockPath: String? = null,
    /** Serialized [com.keshav.wallflow.editor.EditParams] so edits stay re-editable. */
    val editParamsJson: String? = null,
    /** Upscaled (enhanced) copy of the original; becomes the processing source when present. */
    val upscaledPath: String? = null,
)
