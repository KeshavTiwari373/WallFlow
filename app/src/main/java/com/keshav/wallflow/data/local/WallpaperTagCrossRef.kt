package com.keshav.wallflow.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "wallpaper_tag_cross_ref",
    primaryKeys = ["wallpaperId", "tagId"],
    indices = [Index("tagId"), Index("wallpaperId")],
)
data class WallpaperTagCrossRef(
    val wallpaperId: Long,
    val tagId: Long,
)
