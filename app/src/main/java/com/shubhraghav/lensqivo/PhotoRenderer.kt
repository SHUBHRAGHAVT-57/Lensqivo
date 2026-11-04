package com.shubhraghav.lensqivo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Rect
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Looper
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.ceil
import kotlin.math.min

/** Worker-only native-size JPEG export. A failed export never replaces either source or destination. */
@androidx.annotation.OptIn(UnstableApi::class)
object PhotoRenderer {
    private const val TILE_SIZE = 1024
    private const val MEMORY_HEADROOM = 48L * 1024 * 1024

    fun render(context: Context, input: File, output: File, recipe: RenderRecipe) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Photo export must run on a worker thread" }
        require(input.canonicalFile != output.canonicalFile) { "Export must preserve the original file" }
        require(input.isFile) { "Original photo is missing" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType == "image/jpeg") {
            "A valid native JPEG is required"
        }
        val directory = requireNotNull(output.absoluteFile.parentFile)
        check(directory.isDirectory) { "Export directory does not exist" }
        val temporary = File.createTempFile("photo-export-", ".jpg", directory)
        try {
            if (recipe.isOriginal()) {
                input.inputStream().use { source -> FileOutputStream(temporary).use { target ->
                    source.copyTo(target)
                    target.fd.sync()
                } }
            } else {
                process(context, input, temporary, recipe, bounds.outWidth, bounds.outHeight)
                copyPhotographicMetadata(input, temporary)
                FileOutputStream(temporary, true).use { it.fd.sync() }
            }
            Files.move(temporary.toPath(), output.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: OutOfMemoryError) {
            throw IOException("Not enough memory to process this photo at its native size", error)
        } finally {
            temporary.delete()
        }
    }

    private fun process(context: Context, input: File, output: File, recipe: RenderRecipe, width: Int, height: Int) {
        val pixelCount = width.toLong() * height
        check(pixelCount <= Int.MAX_VALUE / 4) { "Native photo exceeds Android bitmap allocation limits" }
        val required = pixelCount * 4 + MEMORY_HEADROOM
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        check(required <= available) { "Not enough memory for native-size photo export" }
        // shortcut: Android's JPEG compressor needs one full output bitmap; use a streaming native
        // encoder if qualified devices must process photos exceeding this explicit heap budget.
        val destination = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            renderTiles(context, input, destination, recipe)
            FileOutputStream(output).use {
                check(destination.compress(Bitmap.CompressFormat.JPEG, 97, it)) { "JPEG encoding failed" }
                it.fd.sync()
            }
        } finally { destination.recycle() }
    }

    @Suppress("DEPRECATION") // Region decoding is required to avoid a second full-resolution bitmap.
    private fun renderTiles(context: Context, input: File, destination: Bitmap, recipe: RenderRecipe) {
        check(EGL14.eglGetCurrentContext() == EGL14.EGL_NO_CONTEXT) { "Exporter requires a dedicated worker" }
        val display = GlUtil.getDefaultEglDisplay()
        var eglContext: EGLContext? = null
        var surface: EGLSurface? = null
        try {
            eglContext = GlUtil.createEglContext(display)
            surface = GlUtil.createFocusedPlaceholderEglSurface(eglContext, display)
            val limits = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, limits, 0)
            GlUtil.checkGlError()
            val radius = sampleRadius(destination.width, destination.height)
            val overlap = ceil(radius).toInt() + 2
            val tileSize = min(TILE_SIZE, limits[0] - overlap * 2)
            check(tileSize >= 64) { "GPU texture limit is too small for photo export" }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                inSampleSize = 1
                inScaled = false
            }
            val decoder = BitmapRegionDecoder.newInstance(input.path, false)
                ?: throw IOException("Native JPEG region decoder is unavailable")
            try {
                CreativeShader(context, true).use { shader ->
                    val readback = ByteBuffer.allocateDirect(tileSize * tileSize * 4)
                    val pixels = IntArray(tileSize * tileSize)
                    for (top in 0 until destination.height step tileSize) {
                        for (left in 0 until destination.width step tileSize) {
                            if (Thread.currentThread().isInterrupted) throw IOException("Photo export interrupted")
                            val right = min(left + tileSize, destination.width)
                            val bottom = min(top + tileSize, destination.height)
                            val region = Rect((left - overlap).coerceAtLeast(0), (top - overlap).coerceAtLeast(0),
                                min(right + overlap, destination.width), min(bottom + overlap, destination.height))
                            val tile = decoder.decodeRegion(region, options)
                                ?: throw IOException("JPEG tile could not be decoded")
                            try {
                                check(tile.width == region.width() && tile.height == region.height()) {
                                    "JPEG decoder changed native tile dimensions"
                                }
                                drawTile(shader, tile, recipe, radius, left - region.left, top - region.top,
                                    right - left, bottom - top, readback, pixels)
                                destination.setPixels(pixels, 0, right - left, left, top, right - left, bottom - top)
                            } finally { tile.recycle() }
                        }
                    }
                }
            } finally { decoder.recycle() }
        } finally {
            // The default display is shared with live Media3 preview: release only our resources.
            try { GlUtil.destroyEglSurface(display, surface) }
            finally {
                try { GlUtil.destroyEglContext(display, eglContext) }
                finally { EGL14.eglReleaseThread() }
            }
        }
    }

    private fun drawTile(
        shader: CreativeShader, tile: Bitmap, recipe: RenderRecipe, radius: Float,
        cropX: Int, cropY: Int, width: Int, height: Int,
        readback: ByteBuffer, pixels: IntArray,
    ) {
        var source = 0
        var target = 0
        var framebuffer = 0
        try {
            source = GlUtil.createTexture(tile)
            target = GlUtil.createTexture(tile.width, tile.height, false)
            framebuffer = GlUtil.createFboForTexture(target)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
                "GPU framebuffer is incomplete"
            }
            GlUtil.focusFramebufferUsingCurrentContext(framebuffer, tile.width, tile.height)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDisable(GLES20.GL_DITHER)
            shader.configure(tile.width, tile.height, radius)
            shader.draw(source, recipe)
            readback.clear()
            GLES20.glReadPixels(cropX, cropY, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, readback)
            GlUtil.checkGlError()
            readback.rewind()
            // Bitmap upload row zero maps to texture y=0, so GL readback row zero is also the
            // original top row. Decode RGBA explicitly, independent of native Int byte order.
            for (index in 0 until width * height) {
                val red = readback.get().toInt() and 255
                val green = readback.get().toInt() and 255
                val blue = readback.get().toInt() and 255
                readback.get()
                pixels[index] = (255 shl 24) or (red shl 16) or (green shl 8) or blue
            }
        } finally {
            try { if (framebuffer != 0) GlUtil.deleteFbo(framebuffer) }
            finally {
                try { if (target != 0) GlUtil.deleteTexture(target) }
                finally { if (source != 0) GlUtil.deleteTexture(source) }
            }
        }
    }

    private fun copyPhotographicMetadata(input: File, output: File) {
        val source = ExifInterface(input)
        val target = ExifInterface(output)
        for (tag in PHOTOGRAPHIC_TAGS) source.getAttribute(tag)?.let { target.setAttribute(tag, it) }
        // Pixel data remains in native sensor orientation; EXIF carries all eight rotations/mirrors.
        target.setAttribute(ExifInterface.TAG_COLOR_SPACE, "1")
        target.saveAttributes()
    }

    private val PHOTOGRAPHIC_TAGS = listOf(
        ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_SENSITIVITY_TYPE, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_FLASH, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_BRIGHTNESS_VALUE,
    )
}
