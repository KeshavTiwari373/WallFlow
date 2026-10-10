package com.keshav.wallflow.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.keshav.wallflow.data.storage.ImageStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over [WallpaperManager] that decodes a screen-sized bitmap and applies it to the
 * requested surface(s). Used by both manual apply and the rotation strategies.
 */
@Singleton
class WallpaperApplier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageStorage: ImageStorageManager,
) {
    /**
     * Applies wallpapers for [target], using the right image per surface: [homePath] for the home
     * screen and [lockPath] for the lock screen. Callers pass the processed (edited) path when one
     * exists, else the original. The two paths are usually identical (no per-target edit). Returns
     * true on success.
     */
    suspend fun apply(homePath: String, lockPath: String, target: WallpaperTarget): Boolean =
        withContext(Dispatchers.IO) {
            val (w, h) = screenSize()
            val manager = WallpaperManager.getInstance(context)
            runCatching {
                when (target) {
                    WallpaperTarget.HOME -> setSurface(manager, homePath, w, h, WallpaperManager.FLAG_SYSTEM)
                    WallpaperTarget.LOCK -> setSurface(manager, lockPath, w, h, WallpaperManager.FLAG_LOCK)
                    WallpaperTarget.BOTH ->
                        if (homePath == lockPath) {
                            setSurface(
                                manager,
                                homePath,
                                w,
                                h,
                                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                            )
                        } else {
                            setSurface(manager, homePath, w, h, WallpaperManager.FLAG_SYSTEM)
                            setSurface(manager, lockPath, w, h, WallpaperManager.FLAG_LOCK)
                        }
                }
            }.isSuccess
        }

    /** Decodes [path] to a screen-sized bitmap and applies it for [flags]; throws on decode failure. */
    private suspend fun setSurface(manager: WallpaperManager, path: String, w: Int, h: Int, flags: Int) {
        val bitmap = imageStorage.decodeForWallpaper(path, w, h)
            ?: error("Could not decode wallpaper: $path")
        try {
            manager.setBitmap(bitmap, null, true, flags)
        } finally {
            bitmap.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }
}
