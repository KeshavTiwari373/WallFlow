package com.keshav.wallflow.data.repository

import android.net.Uri
import com.keshav.wallflow.data.local.TagDao
import com.keshav.wallflow.data.local.TagEntity
import com.keshav.wallflow.data.local.WallpaperDao
import com.keshav.wallflow.data.local.WallpaperEntity
import com.keshav.wallflow.data.local.WallpaperTagCrossRef
import com.keshav.wallflow.data.local.WallpaperWithTags
import android.graphics.Bitmap
import android.graphics.RectF
import com.keshav.wallflow.data.storage.ImageStorageManager
import com.keshav.wallflow.editor.DepthEffect
import com.keshav.wallflow.editor.EditParams
import com.keshav.wallflow.editor.ImageProcessor
import com.keshav.wallflow.editor.SmartCrop
import com.keshav.wallflow.editor.upscale.Upscaler
import com.keshav.wallflow.wallpaper.WallpaperApplier
import com.keshav.wallflow.wallpaper.WallpaperTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a successful [WallpaperRepository.applyUpscale]: source vs. output pixel size. */
data class UpscaleResult(
    val fromWidth: Int,
    val fromHeight: Int,
    val toWidth: Int,
    val toHeight: Int,
) {
    /** True when the output has meaningfully more pixels (not just the unsharp-mask pass). */
    val gainedResolution: Boolean get() = toWidth >= fromWidth * 1.2f
}

/**
 * Single source of truth for wallpapers, tags, and the apply/rotation actions. Both the UI
 * and the rotation strategies talk only to this class.
 */
@Singleton
class WallpaperRepository @Inject constructor(
    private val wallpaperDao: WallpaperDao,
    private val tagDao: TagDao,
    private val imageStorage: ImageStorageManager,
    private val wallpaperApplier: WallpaperApplier,
    private val imageProcessor: ImageProcessor,
    private val smartCrop: SmartCrop,
    private val depthEffect: DepthEffect,
    private val upscaler: Upscaler,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // --- Queries ---

    fun observeWallpapers(filterTagId: Long?): Flow<List<WallpaperWithTags>> =
        if (filterTagId == null) {
            wallpaperDao.observeAllWithTags()
        } else {
            wallpaperDao.observeByTagWithTags(filterTagId)
        }

    fun observeWallpaper(id: Long): Flow<WallpaperWithTags?> = wallpaperDao.observeWithTags(id)

    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    // --- Import / delete ---

    /** Copies each picked image into app storage and inserts a row. Returns count imported. */
    suspend fun importImages(uris: List<Uri>): Int {
        var imported = 0
        val now = System.currentTimeMillis()
        for (uri in uris) {
            val result = imageStorage.importImage(uri) ?: continue
            wallpaperDao.insert(
                WallpaperEntity(
                    uuid = result.uuid,
                    filePath = result.filePath,
                    thumbnailPath = result.thumbnailPath,
                    dateAdded = now,
                ),
            )
            imported++
        }
        return imported
    }

    /** Imports a single image (e.g. from the share sheet) and returns its new row id, or null. */
    suspend fun importAndGetId(uri: Uri): Long? {
        val result = imageStorage.importImage(uri) ?: return null
        return wallpaperDao.insert(
            WallpaperEntity(
                uuid = result.uuid,
                filePath = result.filePath,
                thumbnailPath = result.thumbnailPath,
                dateAdded = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteWallpaper(wallpaper: WallpaperEntity) {
        wallpaperDao.deleteById(wallpaper.id)
        imageStorage.deleteFiles(wallpaper.filePath, wallpaper.thumbnailPath)
        imageStorage.deleteProcessed(wallpaper.uuid)
        imageStorage.deleteUpscaled(wallpaper.uuid)
    }

    // --- Tags ---

    suspend fun addTagToWallpaper(wallpaperId: Long, tagName: String) {
        val trimmed = tagName.trim()
        if (trimmed.isEmpty()) return
        // Reuse an existing tag, or create one. INSERT IGNORE returns -1 if it already exists.
        val existing = tagDao.findByName(trimmed)
        val tagId = existing?.id
            ?: tagDao.insert(TagEntity(name = trimmed)).takeIf { it != -1L }
            ?: tagDao.findByName(trimmed)?.id
            ?: return
        wallpaperDao.insertTagCrossRef(WallpaperTagCrossRef(wallpaperId, tagId))
    }

    suspend fun removeTagFromWallpaper(wallpaperId: Long, tagId: Long) {
        wallpaperDao.deleteTagCrossRef(wallpaperId, tagId)
    }

    // --- Apply / rotation ---

    /** Applies a specific wallpaper (manual apply) and records usage. */
    suspend fun applyWallpaper(wallpaperId: Long, target: WallpaperTarget): Boolean {
        val wallpaper = wallpaperDao.getById(wallpaperId) ?: return false
        val ok = wallpaperApplier.apply(wallpaper.homePath(), wallpaper.lockPath(), target)
        if (ok) wallpaperDao.markUsed(wallpaper.id, System.currentTimeMillis())
        return ok
    }

    /**
     * Picks the least-recently-used wallpaper (optionally restricted to [filterTagId]), applies it
     * to [target], and records usage. Shared by both rotation strategies. Returns true on success.
     */
    suspend fun rotateToNext(target: WallpaperTarget, filterTagId: Long?): Boolean {
        val next = if (filterTagId == null) {
            wallpaperDao.pickLeastRecentlyUsed()
        } else {
            wallpaperDao.pickLeastRecentlyUsedByTag(filterTagId)
        } ?: return false

        val ok = wallpaperApplier.apply(next.homePath(), next.lockPath(), target)
        if (ok) wallpaperDao.markUsed(next.id, System.currentTimeMillis())
        return ok
    }

    suspend fun count(): Int = wallpaperDao.count()

    // --- Editing (Phase 1) ---

    /**
     * Runs [params] through the [ImageProcessor], writes the processed bitmap(s), and persists the
     * paths + serialized params so the edit is re-openable and the rotation engine applies the
     * processed versions automatically. Phase 1 produces one image shared by both surfaces.
     * Returns true when a requested depth effect could NOT be applied (no subject found or
     * segmentation unavailable), so the UI can tell the user.
     */
    suspend fun saveEdit(wallpaperId: Long, params: EditParams): Boolean {
        val wallpaper = wallpaperDao.getById(wallpaperId) ?: return false
        val (screenW, screenH) = imageProcessor.currentScreenSize()
        // Process from the upscaled copy when one exists, else the original.
        val source = wallpaper.upscaledPath ?: wallpaper.filePath

        val homeBitmap = imageProcessor.process(source, params, screenW, screenH)
        val homePath = imageStorage.saveProcessed(wallpaper.uuid, WallpaperTarget.HOME, homeBitmap)

        // Build the lock image: dim variant (if any), then the depth composite (if requested).
        var depthOmitted = false
        var lockBitmap: Bitmap? = if (params.lockVariantDim) imageProcessor.dimForLock(homeBitmap) else null
        if (params.depthBandTop != null) {
            val base = lockBitmap ?: homeBitmap
            val depth = depthEffect.composite(base, params.depthBandTop)
            if (depth != null) {
                lockBitmap?.recycle()
                lockBitmap = depth
            } else {
                depthOmitted = true
            }
        }

        val lockPath = if (lockBitmap != null) {
            val path = imageStorage.saveProcessed(wallpaper.uuid, WallpaperTarget.LOCK, lockBitmap)
            lockBitmap.recycle()
            path
        } else {
            imageStorage.deleteLockVariant(wallpaper.uuid)
            homePath
        }
        homeBitmap.recycle()

        wallpaperDao.updateProcessed(wallpaperId, homePath, lockPath, json.encodeToString(params))
        return depthOmitted
    }

    /** Suggests an initial subject-centered crop for the editor. Never throws. */
    suspend fun suggestCrop(wallpaperId: Long, screenAspect: Float): RectF? {
        val wallpaper = wallpaperDao.getById(wallpaperId) ?: return null
        return smartCrop.suggest(wallpaper.upscaledPath ?: wallpaper.filePath, screenAspect)
    }

    /**
     * Enhances the original image via the [Upscaler] and stores it as the new processing source.
     * Returns the size change on success, null on failure. Edits made afterwards render from the
     * upscaled copy.
     */
    suspend fun applyUpscale(wallpaperId: Long): UpscaleResult? {
        val wallpaper = wallpaperDao.getById(wallpaperId) ?: return null
        // runCatching includes OOM: a 4096px ARGB working set can exceed the heap on low-RAM
        // devices, and "Could not enhance" beats a crash.
        return runCatching {
            val source = imageStorage.decodeForUpscale(wallpaper.filePath) ?: return null
            val fromW = source.width
            val fromH = source.height
            val upscaled = upscaler.upscale4x(source)
            source.recycle()
            val path = imageStorage.saveUpscaled(wallpaper.uuid, upscaled)
            val result = UpscaleResult(fromW, fromH, upscaled.width, upscaled.height)
            upscaled.recycle()
            wallpaperDao.updateUpscaledPath(wallpaperId, path)
            result
        }.getOrNull()
    }

    /** Returns the saved [EditParams] for re-editing, or null if the wallpaper was never edited. */
    suspend fun getEditParams(wallpaperId: Long): EditParams? {
        val wallpaper = wallpaperDao.getById(wallpaperId) ?: return null
        return wallpaper.editParamsJson?.let {
            runCatching { json.decodeFromString<EditParams>(it) }.getOrNull()
        }
    }

    suspend fun getWallpaper(wallpaperId: Long): WallpaperEntity? = wallpaperDao.getById(wallpaperId)

    /** True if the processed home image has a vibrant color (used for the Material You hint). */
    suspend fun hasVibrantColor(wallpaperId: Long): Boolean {
        val wallpaper = wallpaperDao.getById(wallpaperId) ?: return false
        return imageProcessor.hasVibrantSwatch(wallpaper.homePath())
    }

    private fun WallpaperEntity.homePath(): String = processedHomePath ?: filePath
    private fun WallpaperEntity.lockPath(): String = processedLockPath ?: filePath
}
