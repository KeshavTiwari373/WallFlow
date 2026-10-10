package com.keshav.wallflow.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [MIGRATION_1_2]: the new processing columns are added, the dropped group tables are
 * gone, and existing wallpaper rows survive untouched.
 *
 * This builds the v1 schema by hand with raw SQL rather than using Room's `MigrationTestHelper`,
 * so it does not depend on a committed v1 schema JSON (the project's exported schemas only start
 * at v2). The migration logic under test is identical to what Room runs on a real device.
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val factory = FrameworkSQLiteOpenHelperFactory()
    private val dbName = "migration_1_2_test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1To2_addsColumns_dropsGroups_keepsRows() {
        context.deleteDatabase(dbName)

        // --- 1. Create a v1 database by hand (the columns/tables the migration touches). ---
        val v1Helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE wallpapers (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "uuid TEXT NOT NULL, filePath TEXT NOT NULL, thumbnailPath TEXT, " +
                                "dateAdded INTEGER NOT NULL, usageCount INTEGER NOT NULL, " +
                                "last_used_at INTEGER)",
                        )
                        db.execSQL(
                            "CREATE TABLE groups (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "uuid TEXT NOT NULL, name TEXT NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE wallpaper_group_cross_ref (" +
                                "wallpaperId INTEGER NOT NULL, groupId INTEGER NOT NULL, " +
                                "PRIMARY KEY(wallpaperId, groupId))",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build(),
        )
        v1Helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO wallpapers (uuid, filePath, thumbnailPath, dateAdded, usageCount, last_used_at) " +
                    "VALUES ('u1', '/data/u1.jpg', NULL, 100, 3, 999)",
            )
        }
        v1Helper.close()

        // --- 2. Reopen at v2 so onUpgrade runs our real migration. ---
        val v2Helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {
                        MIGRATION_1_2.migrate(db)
                    }
                })
                .build(),
        )

        v2Helper.writableDatabase.use { db ->
            // New columns are queryable and the original row survived with its values intact.
            db.query(
                "SELECT usageCount, last_used_at, processedHomePath, processedLockPath, editParamsJson " +
                    "FROM wallpapers WHERE uuid = 'u1'",
            ).use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals(3, c.getInt(0))
                assertEquals(999, c.getInt(1))
                // New columns default to NULL.
                assertEquals(true, c.isNull(2))
                assertEquals(true, c.isNull(3))
                assertEquals(true, c.isNull(4))
            }
            // The scaffolded group tables are gone.
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('groups', 'wallpaper_group_cross_ref')",
            ).use { c ->
                assertEquals(0, c.count)
            }
        }
        v2Helper.close()
    }
}
