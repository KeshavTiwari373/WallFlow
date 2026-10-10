package com.keshav.wallflow.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.keshav.wallflow.wallpaper.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** Result of copying an image into app-private storage. */
data class ImportedImage(
    val uuid: String,
    val filePath: String,
    val thumbnailPath: String?,
)

/**
 * Owns all on-disk image handling: copying imported images into app-private storage, generating
 * grid thumbnails, decoding screen-sized bitmaps for applying, and cleanup on delete.
 */
@Singleton
class ImageStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val wallpapersDir: File
        get() = File(context.filesDir, "wallpapers").apply { mkdirs() }

    private val thumbsDir: File
        get() = File(context.filesDir, "thumbs").apply { mkdirs() }

    private val processedDir: File
        get() = File(context.filesDir, "processed").apply { mkdirs() }

    private val upscaledDir: File
        get() = File(context.filesDir, "upscaled").apply { mkdirs() }

    /**
     * Copies [uri]'s bytes into wallpapers/<uuid>.jpg and writes a downscaled thumbnail into
     * thumbs/<uuid>.jpg. Returns null if the source could not be read.
     */
    suspend fun importImage(uri: Uri): ImportedImage? = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val target = File(wallpapersDir, "$uuid.jpg")

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)

        if (!copied || !target.exists() || target.length() == 0L) {
            target.delete()
            return@withContext null
        }

        // Reject files the platform can't decode (corrupt data, HEIC before API 28): a row whose
        // image can never render is worse than a failed import.
        if (!isDecodable(target)) {
            target.delete()
            return@withContext null
        }

        // Bake EXIF rotation into the pixels once, so every downstream consumer (region decoder,
        // thumbnails, ML Kit, WallpaperManager) sees upright pixels without EXIF awareness.
        runCatching { normalizeOrientation(target) }

        val thumbPath = runCatching { generateThumbnail(target, uuid) }.getOrNull()

        ImportedImage(uuid = uuid, filePath = target.absolutePath, thumbnailPath = thumbPath)
    }

    private fun isDecodable(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    /**
     * If the file carries an EXIF orientation, re-writes it with the rotation/flip baked into the
     * pixels (and the EXIF tag gone). Long side capped at [NORMALIZE_MAX_DIMEN] to bound memory —
     * far above any screen and equal to the upscaler's own output cap. Images without an EXIF
     * rotation (the common case for downloads/screenshots) are left byte-identical. Failures leave
     * the original bytes in place: a sideways wallpaper beats a broken import.
     */
    private fun normalizeOrientation(file: File) {
        val exif = ExifInterface(file.absolutePath)
        val rotation = exif.rotationDegrees
        val flipped = exif.isFlipped
        if (rotation == 0 && !flipped) return

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return

        // Sample so the longest side lands at or below the cap (never a full-size decode of a
        // 48 MP camera JPEG).
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > NORMALIZE_MAX_DIMEN) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return
        val matrix = Matrix().apply {
            if (flipped) postScale(-1f, 1f)
            postRotate(rotation.toFloat())
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright != decoded) decoded.recycle()
        FileOutputStream(file).use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        upright.recycle()
    }

    private fun generateThumbnail(source: File, uuid: String): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, THUMB_TARGET, THUMB_TARGET)
        }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, options) ?: return null
        val scaled = scaleDownTo(decoded, THUMB_TARGET)
        if (scaled != decoded) decoded.recycle()

        val thumbFile = File(thumbsDir, "$uuid.jpg")
        FileOutputStream(thumbFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        scaled.recycle()
        return thumbFile.absolutePath
    }

    /**
     * Decodes a wallpaper bitmap downsampled to roughly the target screen size to avoid OOM on
     * large source images.
     */
    suspend fun decodeForWallpaper(filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            }
            BitmapFactory.decodeFile(filePath, options)
        }

    fun deleteFiles(filePath: String, thumbnailPath: String?) {
        runCatching { File(filePath).delete() }
        thumbnailPath?.let { runCatching { File(it).delete() } }
    }

    /**
     * Writes a processed (edited) bitmap to processed/<uuid>_home.jpg or <uuid>_lock.jpg and returns
     * its absolute path. [WallpaperTarget.BOTH] is stored under the home suffix.
     */
    fun saveProcessed(uuid: String, target: WallpaperTarget, bitmap: Bitmap): String {
        val suffix = if (target == WallpaperTarget.LOCK) "lock" else "home"
        val file = File(processedDir, "${uuid}_$suffix.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        return file.absolutePath
    }

    /** Removes both processed variants for a wallpaper, if present. */
    fun deleteProcessed(uuid: String) {
        runCatching { File(processedDir, "${uuid}_home.jpg").delete() }
        runCatching { File(processedDir, "${uuid}_lock.jpg").delete() }
    }

    /** Removes only the lock variant (e.g. when the user turns off per-lock dimming). */
    fun deleteLockVariant(uuid: String) {
        runCatching { File(processedDir, "${uuid}_lock.jpg").delete() }
    }

    /** Decodes the source downsampled to ~1600px on the longest side for upscaling input. */
    suspend fun decodeForUpscale(filePath: String): Bitmap? = decodeForWallpaper(filePath, 1600, 1600)

    /** Writes an upscaled image to upscaled/<uuid>.jpg (high quality) and returns its path. */
    fun saveUpscaled(uuid: String, bitmap: Bitmap): String {
        val file = File(upscaledDir, "$uuid.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
        return file.absolutePath
    }

    fun deleteUpscaled(uuid: String) {
        runCatching { File(upscaledDir, "$uuid.jpg").delete() }
    }

    private fun scaleDownTo(bitmap: Bitmap, maxDimen: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimen) return bitmap
        val ratio = maxDimen.toFloat() / largest
        val w = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sample >= reqHeight && halfWidth / sample >= reqWidth) {
                sample *= 2
            }
        }
        return sample
    }

    private companion object {
        const val THUMB_TARGET = 400
        const val NORMALIZE_MAX_DIMEN = 4096
    }
}
