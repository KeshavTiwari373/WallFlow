package com.keshav.wallflow.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lock-screen "depth" effect: segments the subject from the (already cropped + scaled) lock image
 * and re-draws it on top of a darkened clock band, so the subject appears to sit in front of the
 * clock zone. Android won't let an app draw behind the real system clock, so the result is a single
 * flattened bitmap; the user positions the system clock over the band.
 *
 * Segmentation runs on the processed image itself (not the original) so the subject mask is already
 * aligned to the output — no crop/scale remapping needed. Returns null if no subject is found or
 * segmentation is unavailable (caller treats that as "depth not applied").
 */
@Singleton
class DepthEffect @Inject constructor() {

    /** [bandTop] is the normalized (0..1) top of the clock band. Caller owns [base]. */
    fun composite(base: Bitmap, bandTop: Float): Bitmap? {
        val foreground = runCatching { segmentForeground(base) }
            .getOrElse {
                Log.w(TAG, "Subject segmentation unavailable", it)
                null
            } ?: return null

        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        val top = bandTop.coerceIn(0f, 1f) * h
        canvas.drawRect(
            0f, top, w, (top + h * BAND_HEIGHT_FRACTION).coerceAtMost(h),
            Paint().apply { color = Color.argb(90, 0, 0, 0) },
        )

        // The foreground bitmap matches the input size, so it overlays in place.
        val fg = if (foreground.width == out.width && foreground.height == out.height) {
            foreground
        } else {
            Bitmap.createScaledBitmap(foreground, out.width, out.height, true)
        }
        canvas.drawBitmap(fg, 0f, 0f, null)
        if (fg != foreground) fg.recycle()
        foreground.recycle()
        return out
    }

    private fun segmentForeground(bitmap: Bitmap): Bitmap? {
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build(),
        )
        return try {
            val result = Tasks.await(segmenter.process(InputImage.fromBitmap(bitmap, 0)))
            result.foregroundBitmap
        } finally {
            segmenter.close()
        }
    }

    private companion object {
        const val TAG = "DepthEffect"
        const val BAND_HEIGHT_FRACTION = 0.22f
    }
}
