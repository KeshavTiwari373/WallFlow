package com.keshav.wallflow.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.palette.graphics.Palette
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.random.Random

/**
 * Pure image pipeline shared by the editor and (via the repository) the apply/rotation paths:
 * decode the original, apply a crop locked to the screen aspect, scale to exactly the screen size,
 * optionally fill uncovered area with a blurred copy, then apply a filter (Phase 2; [FilterType.NONE]
 * is the identity). Never touches the original file. Region decoding keeps memory bounded even when
 * the user has zoomed into a small part of a very large source image.
 */
@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Exact physical screen size in pixels — the target resolution for processed wallpapers. */
    @Suppress("DEPRECATION")
    fun currentScreenSize(): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    fun process(sourcePath: String, params: EditParams, screenW: Int, screenH: Int): Bitmap {
        val fitted = renderFitted(sourcePath, params, screenW, screenH)
        return applyFilter(fitted, params)
    }

    /**
     * Lock-screen variant of an already-processed bitmap: a soft blur + dimming so the clock and
     * notifications stay legible. Caller owns [src]; a new bitmap is returned.
     */
    fun dimForLock(src: Bitmap): Bitmap {
        val blurred = blur(src, 12)
        val dimmed = applyColorMatrix(blurred, brightnessMatrix(0.8f))
        if (blurred != src) blurred.recycle()
        return dimmed
    }

    /**
     * Applies just the filter (for the editor's live preview). Does not recycle [src] so the caller
     * can keep reusing the source preview bitmap when the filter/intensity changes.
     */
    fun applyFilterForPreview(src: Bitmap, filter: FilterType, intensity: Float): Bitmap =
        if (filter == FilterType.NONE) src else blendFilter(src, filter, intensity)

    /** True if [path]'s image has a vibrant swatch — drives the "pairs with dynamic color" hint. */
    fun hasVibrantSwatch(path: String): Boolean {
        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = 4 },
        ) ?: return false
        return try {
            Palette.from(bitmap).clearFilters().generate().vibrantSwatch != null
        } finally {
            bitmap.recycle()
        }
    }

    /** Crop + scale + optional blur-extend, producing exactly [screenW] x [screenH]. */
    private fun renderFitted(sourcePath: String, params: EditParams, screenW: Int, screenH: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourcePath, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        require(srcW > 0 && srcH > 0) { "Unreadable source image: $sourcePath" }

        // Crop rect in source pixels. May extend past the image bounds when zoomed out (blur-extend).
        val cropL = params.cropLeft * srcW
        val cropT = params.cropTop * srcH
        val cropR = params.cropRight * srcW
        val cropB = params.cropBottom * srcH
        val cropW = (cropR - cropL).coerceAtLeast(1f)
        val cropH = (cropB - cropT).coerceAtLeast(1f)
        val outScaleX = screenW / cropW
        val outScaleY = screenH / cropH

        val output = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        if (params.blurExtend) {
            drawBlurredFill(canvas, sourcePath, srcW, srcH, screenW, screenH)
        }

        // The part of the crop that overlaps the real image; the rest (if any) stays as blur/black.
        val regL = cropL.coerceIn(0f, srcW.toFloat())
        val regT = cropT.coerceIn(0f, srcH.toFloat())
        val regR = cropR.coerceIn(0f, srcW.toFloat())
        val regB = cropB.coerceIn(0f, srcH.toFloat())
        if (regR - regL >= 1f && regB - regT >= 1f) {
            val destW = ((regR - regL) * outScaleX).toInt().coerceAtLeast(1)
            val destH = ((regB - regT) * outScaleY).toInt().coerceAtLeast(1)
            val sample = calcInSampleSize((regR - regL).toInt(), (regB - regT).toInt(), destW, destH)
            val region = decodeRegion(
                sourcePath,
                Rect(regL.toInt(), regT.toInt(), regR.toInt(), regB.toInt()),
                sample,
            )
            if (region != null) {
                val dst = RectF(
                    (regL - cropL) * outScaleX,
                    (regT - cropT) * outScaleY,
                    (regR - cropL) * outScaleX,
                    (regB - cropT) * outScaleY,
                )
                canvas.drawBitmap(region, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                region.recycle()
            }
        }
        return output
    }

    /** Decode a downsampled, screen-covering, blurred copy of the whole image as a backdrop. */
    private fun drawBlurredFill(canvas: Canvas, sourcePath: String, srcW: Int, srcH: Int, screenW: Int, screenH: Int) {
        val sample = calcInSampleSize(srcW, srcH, (screenW / 4).coerceAtLeast(1), (screenH / 4).coerceAtLeast(1))
        val small = BitmapFactory.decodeFile(
            sourcePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return

        val scale = max(screenW.toFloat() / small.width, screenH.toFloat() / small.height)
        val scaledW = (small.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (small.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(small, scaledW, scaledH, true)
        if (scaled != small) small.recycle()

        val blurred = blur(scaled, 24)
        if (blurred != scaled) scaled.recycle()

        val left = (screenW - blurred.width) / 2f
        val top = (screenH - blurred.height) / 2f
        canvas.drawBitmap(blurred, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
        blurred.recycle()
    }

    private fun decodeRegion(sourcePath: String, rect: Rect, sample: Int): Bitmap? = runCatching {
        FileInputStream(sourcePath).use { fis ->
            @Suppress("DEPRECATION")
            val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(fis)
            } else {
                BitmapRegionDecoder.newInstance(fis, false)
            } ?: return null
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            decoder.decodeRegion(rect, options).also { decoder.recycle() }
        }
    }.getOrNull()

    /** Applies [params]'s filter to [bitmap], recycling [bitmap] (it owns the input). */
    private fun applyFilter(bitmap: Bitmap, params: EditParams): Bitmap {
        if (params.filter == FilterType.NONE) return bitmap
        val result = blendFilter(bitmap, params.filter, params.filterIntensity)
        bitmap.recycle()
        return result
    }

    /**
     * Produces the filtered bitmap and blends it over the original by [intensity] (0 == original,
     * 1 == full filter). Never recycles [src].
     */
    private fun blendFilter(src: Bitmap, filter: FilterType, intensity: Float): Bitmap {
        val filtered = when (filter) {
            FilterType.NONE -> return src.copy(Bitmap.Config.ARGB_8888, true)
            FilterType.AMOLED -> applyColorMatrix(src, amoledMatrix())
            FilterType.VIBRANT -> applyColorMatrix(src, vibrantMatrix())
            FilterType.SOFT -> applySoft(src)
            FilterType.DUOTONE -> applyColorMatrix(src, duotoneMatrix(DUOTONE_DARK, DUOTONE_LIGHT))
            FilterType.GRAIN_VIGNETTE -> applyGrainVignette(src)
        }
        val i = intensity.coerceIn(0f, 1f)
        if (i >= 1f) return filtered

        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawBitmap(src, 0f, 0f, null)
            drawBitmap(filtered, 0f, 0f, Paint().apply { alpha = (i * 255).toInt() })
        }
        filtered.recycle()
        return out
    }

    private fun applyColorMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src,
            0f,
            0f,
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) },
        )
        return out
    }

    /** AMOLED: lift contrast and crush shadows toward true black. */
    private fun amoledMatrix() = ColorMatrix(
        floatArrayOf(
            1.12f, 0f, 0f, 0f, -18f,
            0f, 1.12f, 0f, 0f, -18f,
            0f, 0f, 1.12f, 0f, -18f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    /** VIBRANT: boost saturation with a touch of contrast. */
    private fun vibrantMatrix() = ColorMatrix().apply {
        setSaturation(1.35f)
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    1.1f, 0f, 0f, 0f, -12f,
                    0f, 1.1f, 0f, 0f, -12f,
                    0f, 0f, 1.1f, 0f, -12f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    private fun brightnessMatrix(scale: Float) = ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, 0f,
            0f, scale, 0f, 0f, 0f,
            0f, 0f, scale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private fun liftBlacksMatrix(amount: Float) = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, amount,
            0f, 1f, 0f, 0f, amount,
            0f, 0f, 1f, 0f, amount,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    /** DUOTONE: map luminance onto a [dark]→[light] gradient. */
    private fun duotoneMatrix(dark: IntArray, light: IntArray): ColorMatrix {
        val lr = 0.299f
        val lg = 0.587f
        val lb = 0.114f
        fun row(c: Int) = floatArrayOf(
            (light[c] - dark[c]) * lr,
            (light[c] - dark[c]) * lg,
            (light[c] - dark[c]) * lb,
            0f,
            dark[c].toFloat(),
        )
        return ColorMatrix(row(0) + row(1) + row(2) + floatArrayOf(0f, 0f, 0f, 1f, 0f))
    }

    /** SOFT: blend a slight blur over the original and lift the blacks. */
    private fun applySoft(src: Bitmap): Bitmap {
        val blurred = blur(src, 4)
        val mixed = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(mixed).apply {
            drawBitmap(src, 0f, 0f, null)
            drawBitmap(blurred, 0f, 0f, Paint().apply { alpha = (0.3f * 255).toInt() })
        }
        if (blurred != src) blurred.recycle()
        val lifted = applyColorMatrix(mixed, liftBlacksMatrix(12f))
        mixed.recycle()
        return lifted
    }

    /** GRAIN_VIGNETTE: tiled noise overlay + a radial darkening at the edges. */
    private fun applyGrainVignette(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        val noise = generateNoise(64)
        canvas.drawRect(
            0f, 0f, w, h,
            Paint().apply {
                shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                alpha = (0.08f * 255).toInt()
            },
        )
        noise.recycle()

        canvas.drawRect(
            0f, 0f, w, h,
            Paint().apply {
                shader = RadialGradient(
                    w / 2f, h / 2f, max(w, h) / 2f,
                    intArrayOf(Color.TRANSPARENT, Color.argb((0.25f * 255).toInt(), 0, 0, 0)),
                    floatArrayOf(0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        return out
    }

    private fun generateNoise(size: Int): Bitmap {
        val pixels = IntArray(size * size) {
            val v = Random.nextInt(256)
            Color.argb(255, v, v, v)
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    private fun calcInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
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
        // Duotone palette: deep purple shadows -> cyan highlights. (R, G, B)
        val DUOTONE_DARK = intArrayOf(48, 12, 72)
        val DUOTONE_LIGHT = intArrayOf(0, 230, 255)
    }
}
