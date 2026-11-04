package com.shubhraghav.lensqivo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.shubhraghav.lensqivo.core.Look
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.math.abs

class PhotoRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun originalAndZeroStrengthCopyTheJpegWithoutReencoding() = withFiles { input, output ->
        writeScene(input, 87, 53)
        for (recipe in listOf(RenderRecipe(), RenderRecipe(Look.SOL, 0f))) {
            PhotoRenderer.render(context, input, output, recipe)
            assertArrayEquals(input.readBytes(), output.readBytes())
        }
        assertFalse(RenderRecipe(smooth = .2f).isOriginal())
        assertFalse(RenderRecipe(glow = .2f).isOriginal())
    }

    @Test fun colorReachesEveryTileAndPreservesNonSquareGeometryAndExif() = withFiles { input, output ->
        writeScene(input, 1123, 1091)
        for (orientation in listOf(ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_270)) {
            ExifInterface(input).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/125")
                setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "45/10")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "12/1,0/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                saveAttributes()
            }
            val before = input.readBytes()
            PhotoRenderer.render(context, input, output, RenderRecipe(Look.SILVER, 1f))
            assertArrayEquals(before, input.readBytes())
            val image = requireNotNull(BitmapFactory.decodeFile(output.path))
            try {
                assertEquals(1123, image.width)
                assertEquals(1091, image.height)
                // Every source pixel is strongly colored; a skipped tile remains colored.
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val pixel = image.getPixel(x, y)
                    assertTrue("Unprocessed/channel-swapped pixel $x,$y", abs(Color.red(pixel) - Color.green(pixel)) <= 4)
                    assertTrue("Unprocessed/channel-swapped pixel $x,$y", abs(Color.blue(pixel) - Color.green(pixel)) <= 4)
                }
                // Brightness is deliberately asymmetric in both axes: catches flips and transposes.
                assertTrue(Color.red(image.getPixel(40, 40)) + 35 < Color.red(image.getPixel(1080, 1040)))
                assertTrue(Color.red(image.getPixel(1080, 40)) + 12 < Color.red(image.getPixel(40, 1040)))
                // Hand-calculated sRGB -> linear BT.709 luma + .006 lift -> sRGB values.
                assertEquals(64.0, Color.red(image.getPixel(40, 40)).toDouble(), 5.0)
                assertEquals(147.0, Color.red(image.getPixel(1080, 1040)).toDouble(), 5.0)
            } finally { image.recycle() }
            val exif = ExifInterface(output)
            assertEquals(orientation, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            assertEquals(1.0 / 125, exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0), .00001)
            assertEquals(4.5, exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0), .00001)
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        }
    }

    @Test fun smoothingAndGlowDoNotIntroduceTileSeamsOrBlurStrongEdges() = withFiles { input, output ->
        val bitmap = Bitmap.createBitmap(1157, 1133, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val noise = (x * 17 + y * 31) % 19 - 9
                bitmap.setPixel(x, y, if (x < 550) Color.rgb(181 + noise, 126 + noise, 102 + noise) else Color.rgb(25, 30, 100))
            }
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
        } finally { bitmap.recycle() }
        PhotoRenderer.render(context, input, output, RenderRecipe(smooth = 1f, glow = .5f))
        val image = requireNotNull(BitmapFactory.decodeFile(output.path))
        val source = requireNotNull(BitmapFactory.decodeFile(input.path))
        try {
            for (y in listOf(500, 1023, 1024, 1025, 1100)) {
                assertTrue(abs(Color.red(image.getPixel(1023, y)) - Color.red(image.getPixel(1024, y))) <= 5)
                assertTrue(Color.red(image.getPixel(546, y)) - Color.red(image.getPixel(554, y)) > 100)
            }
            // The horizontal boundary crosses the textured skin-colored half of the image.
            for (x in 80..500 step 23) {
                val seam = abs(Color.red(image.getPixel(x, 1023)) - Color.red(image.getPixel(x, 1024)))
                val nearby = abs(Color.red(image.getPixel(x, 1007)) - Color.red(image.getPixel(x, 1008)))
                assertTrue("Visible horizontal tile seam", seam <= nearby + 7)
            }
            fun variation(b: Bitmap): Int = (80..120).sumOf { x ->
                abs(Color.red(b.getPixel(x, 300)) - Color.red(b.getPixel(x + 1, 300)))
            }
            assertTrue("Skin texture should soften", variation(image) < variation(source))
        } finally { image.recycle(); source.recycle() }
    }

    @Test fun invalidInputLeavesOriginalAndExistingOutputUntouched() = withFiles { input, output ->
        input.writeText("invalid jpeg")
        output.writeText("existing")
        assertThrows(IllegalArgumentException::class.java) { PhotoRenderer.render(context, input, output, RenderRecipe(Look.SOL)) }
        assertThrows(IllegalArgumentException::class.java) { PhotoRenderer.render(context, input, output, RenderRecipe()) }
        assertEquals("invalid jpeg", input.readText())
        assertEquals("existing", output.readText())
        assertThrows(IllegalArgumentException::class.java) { PhotoRenderer.render(context, input, input, RenderRecipe()) }
    }

    @Test fun interruptedProcessingReleasesResourcesAndPreservesExistingFiles() = withFiles { input, output ->
        writeScene(input, 131, 73)
        val original = input.readBytes()
        output.writeText("previous export")
        Thread.currentThread().interrupt()
        try {
            assertThrows(IOException::class.java) { PhotoRenderer.render(context, input, output, RenderRecipe(Look.SOL)) }
        } finally { Thread.interrupted() }
        assertArrayEquals(original, input.readBytes())
        assertEquals("previous export", output.readText())
        assertEquals(2, requireNotNull(input.parentFile?.listFiles()).size)
        // A new export must be able to create a context and complete after cleanup.
        PhotoRenderer.render(context, input, output, RenderRecipe(Look.SILVER))
        val recovered = requireNotNull(BitmapFactory.decodeFile(output.path))
        try { assertEquals(131, recovered.width); assertEquals(73, recovered.height) }
        finally { recovered.recycle() }
    }

    @Test fun exportRejectsMainThreadWork() = withFiles { input, output ->
        writeScene(input, 20, 10)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertThrows(IllegalStateException::class.java) { PhotoRenderer.render(context, input, output, RenderRecipe()) }
        }
        assertFalse(output.exists())
    }

    private fun writeScene(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) for (x in 0 until width) {
                val light = 15 + x * 25 / width + y * 75 / height
                bitmap.setPixel(x, y, Color.rgb(light + 95, light + 15, light))
            }
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun withFiles(block: (File, File) -> Unit) {
        val directory = File(context.cacheDir, "renderer-test-${System.nanoTime()}").apply { mkdirs() }
        try { block(File(directory, "input.jpg"), File(directory, "output.jpg")) }
        finally { directory.deleteRecursively() }
    }
}
