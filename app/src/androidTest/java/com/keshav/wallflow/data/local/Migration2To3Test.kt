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
 * Verifies [MIGRATION_2_3] adds the `upscaledPath` column and preserves existing rows. Built with
 * raw SQL for the same reason as [Migration1To2Test] (no committed pre-v3 schema JSON needed).
 */
@RunWith(AndroidJUnit4::class)
class Migration2To3Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val factory = FrameworkSQLiteOpenHelperFactory()
    private val dbName = "migration_2_3_test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate2To3_addsUpscaledColumn_keepsRows() {
        context.deleteDatabase(dbName)

        val v2Helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // The v2 wallpapers table (post MIGRATION_1_2 shape).
                        db.execSQL(
                            "CREATE TABLE wallpapers (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "uuid TEXT NOT NULL, filePath TEXT NOT NULL, thumbnailPath TEXT, " +
                                "dateAdded INTEGER NOT NULL, usageCount INTEGER NOT NULL, " +
                                "last_used_at INTEGER, processedHomePath TEXT, processedLockPath TEXT, " +
                                "editParamsJson TEXT)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build(),
        )
        v2Helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO wallpapers (uuid, filePath, dateAdded, usageCount) " +
                    "VALUES ('u1', '/data/u1.jpg', 0, 2)",
            )
        }
        v2Helper.close()

        val v3Helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {
                        MIGRATION_2_3.migrate(db)
                    }
                })
                .build(),
        )
        v3Helper.writableDatabase.use { db ->
            db.query("SELECT usageCount, upscaledPath FROM wallpapers WHERE uuid = 'u1'").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
                assertEquals(true, c.isNull(1))
            }
        }
        v3Helper.close()
    }
}
