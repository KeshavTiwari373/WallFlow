package com.keshav.wallflow.editor.upscale

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the bundled Real-ESRGAN model end-to-end on a real low-res photo and writes the result to
 * the app's filesDir so it can be pulled and inspected from the host
 * (`adb exec-out run-as com.keshav.wallflow cat files/esrgan_test_out.png`).
 */
@RunWith(AndroidJUnit4::class)
class RealEsrganUpscalerTest {

    @Test
    fun upscalesLowResSampleBy4x(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val src = instrumentation.context.assets.open("lowres_sample.jpg").use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull("test asset must decode", src)

        val upscaler = RealEsrganUpscaler(appContext, LanczosUpscaler())
        val out = upscaler.upscale4x(src!!)

        assertEquals(src.width * 4, out.width)
        assertEquals(src.height * 4, out.height)

        File(appContext.filesDir, "esrgan_test_out.png").outputStream().use { stream ->
            out.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
