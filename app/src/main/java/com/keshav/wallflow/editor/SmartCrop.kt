package com.keshav.wallflow.editor

import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suggests an initial crop centered on the photo's subject. Uses ML Kit face detection to find the
 * subject; if no face is found (or detection is unavailable) it falls back to a centered crop. The
 * returned rect is normalized (0..1) in source space and locked to [screenAspect], matching
 * [EditParams]. Always returns a valid rect — it never throws.
 */
@Singleton
class SmartCrop @Inject constructor() {

    suspend fun suggest(sourcePath: String, screenAspect: Float): RectF = withContext(Dispatchers.IO) {
        val focal = runCatching { detectFocalPoint(sourcePath) }
            .getOrElse {
                Log.w(TAG, "Face detection unavailable; using center crop", it)
                null
            } ?: PointF(0.5f, 0.5f)
        screenAspectCrop(sourcePath, screenAspect, focal)
    }

    private data class PointF(val x: Float, val y: Float)

    /** Normalized center of the detected faces, or null when none are found. */
    private fun detectFocalPoint(sourcePath: String): PointF? {
        val bitmap = decodeForDetection(sourcePath) ?: return null
        return try {
            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build(),
            )
            val faces = try {
                Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
            } finally {
                detector.close()
            }
            if (faces.isEmpty()) return null

            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = Float.MIN_VALUE
            var bottom = Float.MIN_VALUE
            for (face in faces) {
                val b = face.boundingBox
                left = minOf(left, b.left.toFloat())
                top = minOf(top, b.top.toFloat())
                right = maxOf(right, b.right.toFloat())
                bottom = maxOf(bottom, b.bottom.toFloat())
            }
            PointF(
                x = ((left + right) / 2f / bitmap.width).coerceIn(0f, 1f),
                y = ((top + bottom) / 2f / bitmap.height).coerceIn(0f, 1f),
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** Largest screen-aspect rect fitting the image, centered on [focal] (clamped to bounds). */
    private fun screenAspectCrop(sourcePath: String, screenAspect: Float, focal: PointF): RectF {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourcePath, bounds)
        val srcW = bounds.outWidth.coerceAtLeast(1)
        val srcH = bounds.outHeight.coerceAtLeast(1)
        val imageAspect = srcW.toFloat() / srcH.toFloat()

        val nW: Float
        val nH: Float
        if (screenAspect <= imageAspect) {
            nH = 1f
            nW = screenAspect / imageAspect
        } else {
            nW = 1f
            nH = imageAspect / screenAspect
        }
        val left = (focal.x - nW / 2f).coerceIn(0f, 1f - nW)
        val top = (focal.y - nH / 2f).coerceIn(0f, 1f - nH)
        return RectF(left, top, left + nW, top + nH)
    }

    private fun decodeForDetection(sourcePath: String) = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourcePath, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > 1024) sample *= 2
        BitmapFactory.decodeFile(sourcePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private companion object {
        const val TAG = "SmartCrop"
    }
}
