package com.keshav.wallflow.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * A wallpaper together with its tags, resolved through the many-to-many join table.
 */
data class WallpaperWithTags(
    @Embedded
    val wallpaper: WallpaperEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = WallpaperTagCrossRef::class,
            parentColumn = "wallpaperId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)
