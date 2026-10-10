package com.keshav.wallflow.editor.upscale

import android.graphics.Bitmap
import com.keshav.wallflow.editor.blur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dependency-free "enhance": Lanczos-3 resampling (sharper edge reconstruction than the platform's
 * bilinear scaler) followed by a scale-aware unsharp mask. Output is capped at [MAX_OUTPUT] on the
 * longest side, so very large sources scale by less than 4x (and sources already at the cap only
 * get the sharpening pass).
 *
 * Lanczos runs on sources up to [LANCZOS_MAX_INPUT] on the longest side — the genuinely low-res
 * images where it visibly matters. Larger sources (barely upscaled anyway) use the platform scaler
 * to keep the float working set small.
 */
class LanczosUpscaler : Upscaler {

    override suspend fun upscale4x(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val longest = max(src.width, src.height)
        val scale = min(4f, MAX_OUTPUT.toFloat() / longest).coerceAtLeast(1f)
        val outW = (src.width * scale).roundToInt().coerceAtLeast(1)
        val outH = (src.height * scale).roundToInt().coerceAtLeast(1)

        val scaled = when {
            outW == src.width && outH == src.height -> src
            longest <= LANCZOS_MAX_INPUT -> lanczosResize(src, outW, outH)
            else -> Bitmap.createScaledBitmap(src, outW, outH, true)
        }

        // Sharpen relative to how far we stretched: a fixed small radius disappears once the
        // wallpaper is rendered back down to screen size.
        val radius = scale.roundToInt().coerceIn(2, 6)
        val sharpened = unsharpMask(scaled, amount = 0.8f, radius = radius)
        if (sharpened != scaled && scaled != src) scaled.recycle()
        sharpened
    }

    // --- Lanczos-3 separable resampling ---

    /** Per-axis resampling plan: for each output coordinate, the source taps and their weights. */
    private class Axis(inSize: Int, outSize: Int) {
        val taps = 2 * KERNEL_A + 1
        val indices = IntArray(outSize * taps)
        val weights = FloatArray(outSize * taps)

        init {
            val scale = outSize.toFloat() / inSize
            for (o in 0 until outSize) {
                val center = (o + 0.5f) / scale - 0.5f
                val start = floor(center).toInt() - KERNEL_A + 1
                var sum = 0f
                for (t in 0 until taps) {
                    val i = start + t
                    val w = lanczos3(i - center)
                    indices[o * taps + t] = i.coerceIn(0, inSize - 1)
                    weights[o * taps + t] = w
                    sum += w
                }
                if (sum != 0f) {
                    for (t in 0 until taps) weights[o * taps + t] /= sum
                }
            }
        }
    }

    private fun lanczosResize(src: Bitmap, outW: Int, outH: Int): Bitmap {
        val inW = src.width
        val inH = src.height
        val srcPx = IntArray(inW * inH)
        src.getPixels(srcPx, 0, inW, 0, 0, inW, inH)

        val xAxis = Axis(inW, outW)
        val yAxis = Axis(inH, outH)
        val taps = xAxis.taps

        // Horizontal pass: (inW x inH) -> (outW x inH), float channels.
        val midR = FloatArray(outW * inH)
        val midG = FloatArray(outW * inH)
        val midB = FloatArray(outW * inH)
        for (y in 0 until inH) {
            val rowBase = y * inW
            val midBase = y * outW
            for (x in 0 until outW) {
                var r = 0f
                var g = 0f
                var b = 0f
                val tapBase = x * taps
                for (t in 0 until taps) {
                    val px = srcPx[rowBase + xAxis.indices[tapBase + t]]
                    val w = xAxis.weights[tapBase + t]
                    r += w * ((px ushr 16) and 0xFF)
                    g += w * ((px ushr 8) and 0xFF)
                    b += w * (px and 0xFF)
                }
                midR[midBase + x] = r
                midG[midBase + x] = g
                midB[midBase + x] = b
            }
        }

        // Vertical pass: (outW x inH) -> (outW x outH), writing final ARGB pixels.
        val outPx = IntArray(outW * outH)
        for (y in 0 until outH) {
            val tapBase = y * taps
            val outBase = y * outW
            for (x in 0 until outW) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (t in 0 until taps) {
                    val srcRow = yAxis.indices[tapBase + t] * outW
                    val w = yAxis.weights[tapBase + t]
                    r += w * midR[srcRow + x]
                    g += w * midG[srcRow + x]
                    b += w * midB[srcRow + x]
                }
                outPx[outBase + x] = (0xFF shl 24) or
                    (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)
            }
        }

        return Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPx, 0, outW, 0, 0, outW, outH)
        }
    }

    private fun clamp255(v: Float): Int = v.roundToInt().coerceIn(0, 255)

    // --- Unsharp mask ---

    /** out = clamp(orig + amount * (orig - blurred)). */
    private fun unsharpMask(src: Bitmap, amount: Float, radius: Int): Bitmap {
        val blurred = blur(src, radius)
        if (blurred == src) return src
        val w = src.width
        val h = src.height
        val orig = IntArray(w * h)
        val blurPixels = IntArray(w * h)
        src.getPixels(orig, 0, w, 0, 0, w, h)
        blurred.getPixels(blurPixels, 0, w, 0, 0, w, h)
        blurred.recycle()

        for (i in orig.indices) {
            val o = orig[i]
            val b = blurPixels[i]
            val r = sharpenChannel((o ushr 16) and 0xFF, (b ushr 16) and 0xFF, amount)
            val g = sharpenChannel((o ushr 8) and 0xFF, (b ushr 8) and 0xFF, amount)
            val bl = sharpenChannel(o and 0xFF, b and 0xFF, amount)
            orig[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(orig, 0, w, 0, 0, w, h)
        }
    }

    private fun sharpenChannel(orig: Int, blurred: Int, amount: Float): Int =
        (orig + amount * (orig - blurred)).roundToInt().coerceIn(0, 255)

    private companion object {
        const val MAX_OUTPUT = 4096
        const val LANCZOS_MAX_INPUT = 1600
        const val KERNEL_A = 3

        fun lanczos3(x: Float): Float {
            if (x == 0f) return 1f
            if (abs(x) >= KERNEL_A) return 0f
            val pix = PI.toFloat() * x
            return KERNEL_A * sin(pix) * sin(pix / KERNEL_A) / (pix * pix)
        }
    }
}
