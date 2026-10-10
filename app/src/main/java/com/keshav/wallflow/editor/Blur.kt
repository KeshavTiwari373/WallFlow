package com.keshav.wallflow.editor

import android.graphics.Bitmap

/**
 * Dependency-free separable box blur (three passes ≈ Gaussian). Operates on an int pixel array so
 * it works on every supported API level (26+) without RenderScript or RenderEffect readback. Always
 * run it on a downscaled bitmap (the blur-extend fill downsamples ~4x first) — the cost is roughly
 * O(passes * w * h), independent of radius, but memory scales with the bitmap size.
 */
fun blur(src: Bitmap, radius: Int): Bitmap {
    if (radius < 1) return src
    val w = src.width
    val h = src.height
    if (w < 2 || h < 2) return src

    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    repeat(3) {
        boxBlurPass(pixels, w, h, radius, horizontal = true)
        boxBlurPass(pixels, w, h, radius, horizontal = false)
    }

    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, w, 0, 0, w, h)
    }
}

/** One box-blur pass over rows (horizontal) or columns (vertical), averaging an ARGB window. */
private fun boxBlurPass(pixels: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val lineCount = if (horizontal) h else w
    val lineLength = if (horizontal) w else h
    val window = radius * 2 + 1
    val scratch = IntArray(lineLength)

    for (line in 0 until lineCount) {
        var sumA = 0
        var sumR = 0
        var sumG = 0
        var sumB = 0

        fun pixelAt(i: Int): Int {
            val idx = i.coerceIn(0, lineLength - 1)
            return if (horizontal) pixels[line * w + idx] else pixels[idx * w + line]
        }

        // Prime the window centered on index 0 (edges clamped).
        for (k in -radius..radius) {
            val p = pixelAt(k)
            sumA += (p ushr 24) and 0xFF
            sumR += (p ushr 16) and 0xFF
            sumG += (p ushr 8) and 0xFF
            sumB += p and 0xFF
        }

        for (i in 0 until lineLength) {
            scratch[i] = ((sumA / window) shl 24) or
                ((sumR / window) shl 16) or
                ((sumG / window) shl 8) or
                (sumB / window)

            // Slide the window: remove the leftmost, add the next one to the right.
            val outgoing = pixelAt(i - radius)
            val incoming = pixelAt(i + radius + 1)
            sumA += ((incoming ushr 24) and 0xFF) - ((outgoing ushr 24) and 0xFF)
            sumR += ((incoming ushr 16) and 0xFF) - ((outgoing ushr 16) and 0xFF)
            sumG += ((incoming ushr 8) and 0xFF) - ((outgoing ushr 8) and 0xFF)
            sumB += (incoming and 0xFF) - (outgoing and 0xFF)
        }

        for (i in 0 until lineLength) {
            if (horizontal) pixels[line * w + i] = scratch[i] else pixels[i * w + line] = scratch[i]
        }
    }
}
