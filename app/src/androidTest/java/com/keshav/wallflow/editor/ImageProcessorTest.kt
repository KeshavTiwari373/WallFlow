package com.keshav.wallflow.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Exercises the real [ImageProcessor] pipeline on device: a 100x200 source (red top half, blue
 * bottom half) cropped full-frame to a 50x100 screen must come out exactly 50x100 with the crop
 * regions landing in the right place. Instrumented (not a JVM unit test) because it relies on real
 * Android bitmap decoding / region decoding.
 */
@RunWith(AndroidJUnit4::class)
class ImageProcessorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        // 100x200 image: top half red, bottom half blue (aspect 0.5 == the test screen's aspect).
        val src = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        val paint = Paint()
        paint.color = Color.RED
        canvas.drawRect(0f, 0f, 100f, 100f, paint)
        paint.color = Color.BLUE
        canvas.drawRect(0f, 100f, 100f, 200f, paint)

        sourceFile = File(context.cacheDir, "image_processor_test_${System.nanoTime()}.jpg")
        FileOutputStream(sourceFile).use { src.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        src.recycle()
    }

    @After
    fun tearDown() {
        sourceFile.delete()
    }

    @Test
    fun process_outputsExactScreenSize() {
        val processor = ImageProcessor(context)
        val out = processor.process(sourceFile.absolutePath, EditParams(), screenW = 50, screenH = 100)

        assertEquals(50, out.width)
        assertEquals(100, out.height)
        out.recycle()
    }

    @Test
    fun process_fullCrop_preservesRegions() {
        val processor = ImageProcessor(context)
        val out = processor.process(sourceFile.absolutePath, EditParams(), screenW = 50, screenH = 100)

        val top = out.getPixel(25, 25)   // should come from the red half
        val bottom = out.getPixel(25, 75) // should come from the blue half

        // JPEG is lossy, so assert channel dominance rather than exact colors.
        assertTrue("top should be red-dominant", Color.red(top) > Color.blue(top))
        assertTrue("bottom should be blue-dominant", Color.blue(bottom) > Color.red(bottom))
        out.recycle()
    }

    @Test
    fun amoledFilter_reducesMeanLuminance() {
        // Mid-gray so the AMOLED shadow-crush actually lowers luminance (saturated pixels wouldn't).
        val grayFile = File(context.cacheDir, "gray_${System.nanoTime()}.jpg")
        val gray = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        Canvas(gray).drawColor(Color.rgb(100, 100, 100))
        FileOutputStream(grayFile).use { gray.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        gray.recycle()

        val processor = ImageProcessor(context)
        val plain = processor.process(grayFile.absolutePath, EditParams(), 50, 100)
        val amoled = processor.process(
            grayFile.absolutePath,
            EditParams(filter = FilterType.AMOLED, filterIntensity = 1f),
            50,
            100,
        )

        assertTrue(
            "AMOLED mean luminance should be lower",
            meanLuminance(amoled) < meanLuminance(plain),
        )

        plain.recycle()
        amoled.recycle()
        grayFile.delete()
    }

    private fun meanLuminance(bitmap: Bitmap): Double {
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                sum += 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
                count++
                x += 5
            }
            y += 5
        }
        return sum / count
    }
}
