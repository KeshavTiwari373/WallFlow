package com.keshav.wallflow.editor.upscale

import android.graphics.Bitmap

/**
 * Enhances an image to a higher resolution. The shipping implementation is [LanczosUpscaler]; a
 * TFLite Real-ESRGAN implementation can be dropped in behind this same interface when a model is
 * bundled (see [UpscaleModule]).
 */
interface Upscaler {
    /** Returns a new, higher-resolution bitmap. Caller owns both [src] and the result. */
    suspend fun upscale4x(src: Bitmap): Bitmap
}
