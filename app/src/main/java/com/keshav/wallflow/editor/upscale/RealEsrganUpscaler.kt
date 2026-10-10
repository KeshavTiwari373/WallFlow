package com.keshav.wallflow.editor.upscale

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Enhance HD" backed by Real-ESRGAN (general-x4v3, TFLite float, bundled in assets — fully
 * offline). The model upscales fixed 128x128 tiles to 512x512, so larger images are processed as a
 * grid of overlapping tiles; each tile contributes only its central region to hide seam artifacts.
 *
 * Sources above [MAX_AI_INPUT] on the longest side are delegated to [fallback] (they're barely
 * upscaled by the 4096px output cap anyway, and AI time would be wasted). Any inference failure
 * (corrupt asset, OOM, unexpected tensor shape) also falls back — Enhance never hard-fails because
 * the AI path is unavailable.
 */
class RealEsrganUpscaler(
    private val context: Context,
    private val fallback: Upscaler,
) : Upscaler {

    private val interpreter: Interpreter? by lazy {
        runCatching {
            val buffer = context.assets.openFd(MODEL_ASSET).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
            Interpreter(
                buffer,
                Interpreter.Options().setNumThreads(
                    Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                ),
            )
        }.getOrElse {
            Log.w(TAG, "Real-ESRGAN unavailable, using fallback upscaler", it)
            null
        }
    }

    override suspend fun upscale4x(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val tflite = interpreter
        if (tflite == null || max(src.width, src.height) > MAX_AI_INPUT) {
            return@withContext fallback.upscale4x(src)
        }
        runCatching { inferTiled(tflite, src) }.getOrElse {
            Log.w(TAG, "Real-ESRGAN inference failed, using fallback upscaler", it)
            fallback.upscale4x(src)
        }
    }

    private fun inferTiled(tflite: Interpreter, src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val outW = w * SCALE
        val outH = h * SCALE
        val srcPx = IntArray(w * h)
        src.getPixels(srcPx, 0, w, 0, 0, w, h)
        val outPx = IntArray(outW * outH)

        val input = ByteBuffer.allocateDirect(TILE * TILE * 3 * 4).order(ByteOrder.nativeOrder())
        val output = ByteBuffer.allocateDirect(OUT_TILE * OUT_TILE * 3 * 4).order(ByteOrder.nativeOrder())

        val xTiles = tilePositions(w)
        val yTiles = tilePositions(h)
        synchronized(tflite) {
            for (y0 in yTiles) {
                for (x0 in xTiles) {
                    fillInput(input, srcPx, w, h, x0, y0)
                    output.rewind()
                    tflite.run(input, output)
                    writeOutput(output, outPx, outW, outH, x0, y0, w, h)
                }
            }
        }

        return Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPx, 0, outW, 0, 0, outW, outH)
        }
    }

    /** Tile origins covering [size] with at least [PAD] of context overlap between neighbors. */
    private fun tilePositions(size: Int): IntArray {
        if (size <= TILE) return intArrayOf(0)
        val step = TILE - 2 * PAD
        val positions = ArrayList<Int>()
        var p = 0
        while (p + TILE < size) {
            positions.add(p)
            p += step
        }
        positions.add(size - TILE)
        return positions.toIntArray()
    }

    /** RGB 0-1 floats, edge pixels replicated when the tile hangs past the image border. */
    private fun fillInput(input: ByteBuffer, srcPx: IntArray, w: Int, h: Int, x0: Int, y0: Int) {
        input.rewind()
        val floats = input.asFloatBuffer()
        for (ty in 0 until TILE) {
            val sy = (y0 + ty).coerceIn(0, h - 1)
            val rowBase = sy * w
            for (tx in 0 until TILE) {
                val sx = (x0 + tx).coerceIn(0, w - 1)
                val px = srcPx[rowBase + sx]
                floats.put(((px ushr 16) and 0xFF) / 255f)
                floats.put(((px ushr 8) and 0xFF) / 255f)
                floats.put((px and 0xFF) / 255f)
            }
        }
    }

    /**
     * Copies the tile's valid central region into the final image. Margins that touch the image
     * border are kept; interior margins are dropped (the neighboring tile provides them with more
     * context).
     */
    private fun writeOutput(
        output: ByteBuffer,
        outPx: IntArray,
        outW: Int,
        outH: Int,
        x0: Int,
        y0: Int,
        srcW: Int,
        srcH: Int,
    ) {
        output.rewind()
        val floats = output.asFloatBuffer()
        val outPad = PAD * SCALE
        val tileOutX = x0 * SCALE
        val tileOutY = y0 * SCALE

        val startX = if (x0 == 0) 0 else outPad
        val endX = if (x0 + TILE >= srcW) min(OUT_TILE, outW - tileOutX) else OUT_TILE - outPad
        val startY = if (y0 == 0) 0 else outPad
        val endY = if (y0 + TILE >= srcH) min(OUT_TILE, outH - tileOutY) else OUT_TILE - outPad

        for (oy in startY until endY) {
            val destRow = (tileOutY + oy) * outW
            val srcRow = oy * OUT_TILE * 3
            for (ox in startX until endX) {
                val base = srcRow + ox * 3
                val r = clamp255(floats.get(base))
                val g = clamp255(floats.get(base + 1))
                val b = clamp255(floats.get(base + 2))
                outPx[destRow + tileOutX + ox] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun clamp255(v: Float): Int = (v * 255f).roundToInt().coerceIn(0, 255)

    private companion object {
        const val TAG = "RealEsrganUpscaler"
        const val MODEL_ASSET = "real_esrgan_general_x4v3.tflite"
        const val TILE = 128
        const val SCALE = 4
        const val OUT_TILE = TILE * SCALE
        const val PAD = 8
        const val MAX_AI_INPUT = 1024
    }
}
