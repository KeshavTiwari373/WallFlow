package com.keshav.wallflow.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Smart crop must always return a valid, in-bounds, screen-aspect crop and never crash — even with a
 * featureless image where no subject is detected (it falls back to a centered crop).
 */
@RunWith(AndroidJUnit4::class)
class SmartCropTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var file: File

    @After
    fun tearDown() {
        if (::file.isInitialized) file.delete()
    }

    @Test
    fun suggest_noSubject_returnsValidCenteredCrop() = runBlocking {
        // 200x400 solid image (aspect 0.5) and a matching 0.5 screen aspect -> expect a full crop.
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.GRAY)
        file = File(context.cacheDir, "smartcrop_${System.nanoTime()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()

        val rect = SmartCrop().suggest(file.absolutePath, screenAspect = 0.5f)

        assertTrue("left in bounds", rect.left >= 0f && rect.left <= 1f)
        assertTrue("top in bounds", rect.top >= 0f && rect.top <= 1f)
        assertTrue("non-empty width", rect.width() > 0f)
        assertTrue("non-empty height", rect.height() > 0f)
        assertTrue("right within bounds", rect.right <= 1.001f)
        assertTrue("bottom within bounds", rect.bottom <= 1.001f)
    }
}
