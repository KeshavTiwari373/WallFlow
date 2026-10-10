package com.keshav.wallflow.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2:
 *  - Adds the non-destructive processing columns to `wallpapers` (`processedHomePath`,
 *    `processedLockPath`, `editParamsJson`). Originals are never touched.
 *  - Drops the unused scaffolded group tables that were removed from the schema.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wallpapers ADD COLUMN processedHomePath TEXT")
        db.execSQL("ALTER TABLE wallpapers ADD COLUMN processedLockPath TEXT")
        db.execSQL("ALTER TABLE wallpapers ADD COLUMN editParamsJson TEXT")
        db.execSQL("DROP TABLE IF EXISTS wallpaper_group_cross_ref")
        db.execSQL("DROP TABLE IF EXISTS groups")
    }
}

/** v2 -> v3: adds the upscaled-source column for the AI "Enhance HD" feature. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wallpapers ADD COLUMN upscaledPath TEXT")
    }
}
