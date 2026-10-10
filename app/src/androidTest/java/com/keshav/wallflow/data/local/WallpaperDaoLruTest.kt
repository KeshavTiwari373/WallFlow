package com.keshav.wallflow.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the least-recently-used selection that powers both rotation modes: never-used wallpapers
 * are picked first, then the one used longest ago, and applying a wallpaper moves it to the back of
 * the line. Runs against a real (in-memory) SQLite database so the ORDER BY behaves exactly as on
 * device — including the `(last_used_at IS NULL) DESC` ordering used to stand in for NULLS FIRST.
 */
@RunWith(AndroidJUnit4::class)
class WallpaperDaoLruTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WallpaperDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.wallpaperDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun wallpaper(uuid: String, usageCount: Int = 0, lastUsedAt: Long? = null) =
        WallpaperEntity(
            uuid = uuid,
            filePath = "/data/$uuid.jpg",
            thumbnailPath = null,
            dateAdded = 0L,
            usageCount = usageCount,
            lastUsedAt = lastUsedAt,
        )

    @Test
    fun pick_returnsNull_whenEmpty() = runBlocking {
        assertNull(dao.pickLeastRecentlyUsed())
    }

    @Test
    fun pick_prefersNeverUsed_overUsed() = runBlocking {
        dao.insert(wallpaper("used-old", usageCount = 1, lastUsedAt = 1_000L))
        dao.insert(wallpaper("never-used")) // lastUsedAt = null
        dao.insert(wallpaper("used-recent", usageCount = 1, lastUsedAt = 5_000L))

        // A never-used wallpaper (last_used_at IS NULL) must come before any used one.
        assertEquals("never-used", dao.pickLeastRecentlyUsed()?.uuid)
    }

    @Test
    fun pick_prefersOldest_amongUsed() = runBlocking {
        dao.insert(wallpaper("b", usageCount = 1, lastUsedAt = 1_000L))
        dao.insert(wallpaper("c", usageCount = 1, lastUsedAt = 2_000L))
        dao.insert(wallpaper("a", usageCount = 1, lastUsedAt = 3_000L))

        // All used, so the one with the oldest last_used_at wins.
        assertEquals("b", dao.pickLeastRecentlyUsed()?.uuid)
    }

    @Test
    fun markUsed_movesWallpaperToBackOfQueue() = runBlocking {
        val firstId = dao.insert(wallpaper("first", usageCount = 1, lastUsedAt = 1_000L))
        dao.insert(wallpaper("second", usageCount = 1, lastUsedAt = 2_000L))

        // "first" is least-recently-used and is picked.
        assertEquals("first", dao.pickLeastRecentlyUsed()?.uuid)

        // After applying it (recording usage with a newer timestamp), "second" becomes the LRU pick.
        dao.markUsed(firstId, now = 3_000L)
        assertEquals("second", dao.pickLeastRecentlyUsed()?.uuid)
    }

    @Test
    fun markUsed_incrementsUsageCountAndSetsTimestamp() = runBlocking {
        val id = dao.insert(wallpaper("w"))

        dao.markUsed(id, now = 4_242L)

        val updated = dao.getById(id)!!
        assertEquals(1, updated.usageCount)
        assertEquals(4_242L, updated.lastUsedAt)
    }

    @Test
    fun pickByTag_onlyConsidersTaggedWallpapers() = runBlocking {
        val taggedId = dao.insert(wallpaper("tagged", usageCount = 5, lastUsedAt = 9_000L))
        // An untagged wallpaper that is "more" least-recently-used (never used) must be ignored.
        dao.insert(wallpaper("untagged-never-used"))

        val tagId = db.tagDao().insert(TagEntity(name = "scenery"))
        dao.insertTagCrossRef(WallpaperTagCrossRef(wallpaperId = taggedId, tagId = tagId))

        assertEquals("tagged", dao.pickLeastRecentlyUsedByTag(tagId)?.uuid)
    }
}
