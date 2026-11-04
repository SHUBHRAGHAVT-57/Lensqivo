package com.shubhraghav.lensqivo

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import com.shubhraghav.lensqivo.core.Look
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun failedPublicationRemovesItsPendingRowAndRetainsNativeCapture() {
        withScene { directory, input ->
            val store = PhotoStore(context, directory) { _, source, edited, _ ->
                source.copyTo(edited)
                Os.chmod(edited.path, 0)
            }
            assertTrue(runCatching { store.export(input, RenderRecipe(Look.SOL), false) }.isFailure)
            assertTrue(input.exists())
            assertEquals(0, rowsFor(input).size)
        }
    }

    @Test fun secondPublicationFailureRetainsOriginalButReportsSuccessfulEdit() {
        withScene { directory, input ->
            val store = PhotoStore(context, directory) { _, source, edited, _ ->
                source.copyTo(edited)
                Os.chmod(source.path, 0)
            }
            try {
                val result = store.export(input, RenderRecipe(Look.SOL), true)
                assertTrue(result.message.contains("Original retained privately"))
                assertTrue(input.exists())
                assertEquals(listOf(result.media.uri), rowsFor(input))
            } finally { Os.chmod(input.path, 384) }
        }
    }

    @Test fun retryAfterCommittedOriginalDoesNotDuplicateIt() {
        withScene { directory, input ->
            val bytes = input.readBytes()
            val store = PhotoStore(context, directory)
            val first = store.export(input, RenderRecipe(), false)
            // Simulate a crash after MediaStore commit but before deletion of the private source.
            input.writeBytes(bytes)
            val retry = store.export(input, RenderRecipe(), false)
            assertEquals(first.media.uri, retry.media.uri)
            assertEquals(1, rowsFor(input).size)
        }
    }

    @Test fun recoveryRemovesOnlyPendingRowsForItsRetainedCapture() {
        withScene { directory, input ->
            val staleExport = File(directory, "photo-export-interrupted.jpg").apply { writeText("interrupted export") }
            fun pending(name: String): Uri = requireNotNull(context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Lensqivo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }))
            val interrupted = pending("Lensqivo_${input.name.substringBefore('.')}.jpg")
            val unrelated = pending("Lensqivo_unrelated_${UUID.randomUUID()}.jpg")
            try {
                val recovered = PhotoStore(context, directory).recover()
                assertEquals(1, recovered.size)
                assertFalse(staleExport.exists())
                assertFalse(input.exists())
                context.contentResolver.query(interrupted, arrayOf(MediaStore.Images.Media._ID), null, null, null).use {
                    assertEquals(0, requireNotNull(it).count)
                }
                context.contentResolver.query(unrelated, arrayOf(MediaStore.Images.Media._ID), null, null, null).use {
                    assertEquals(1, requireNotNull(it).count)
                }
            } finally {
                context.contentResolver.delete(interrupted, null, null)
                context.contentResolver.delete(unrelated, null, null)
            }
        }
    }

    @Test fun galleryResumePrefersTheEditedCopyOfAPairedCapture() {
        withScene { directory, input ->
            val store = PhotoStore(context, directory) { _, source, edited, _ -> source.copyTo(edited); Unit }
            val result = store.export(input, RenderRecipe(Look.SOL), true)
            assertEquals(2, rowsFor(input).size)
            assertEquals(result.media, store.latest())
        }
    }

    private fun withScene(action: (File, File) -> Unit) {
        val directory = File(context.cacheDir, "photo-test-${UUID.randomUUID()}")
        val input = PhotoStore(context, directory).newInput()
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.rgb(180, 110, 75))
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            action(directory, input)
        } finally {
            rowsFor(input).forEach { context.contentResolver.delete(it, null, null) }
            bitmap.recycle()
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }

    private fun rowsFor(input: File): List<Uri> {
        val rows = mutableListOf<Uri>()
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ?",
            arrayOf("Lensqivo_${input.name.substringBefore('.')}%", context.packageName), null)?.use { cursor ->
            while (cursor.moveToNext()) rows += android.content.ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
        }
        return rows
    }

    @Test fun rendererFailurePublishesExactNativeOriginalAndCommitsPendingRow() {
        val directory = File(context.cacheDir, "photo-test-${UUID.randomUUID()}")
        val store = PhotoStore(context, directory) { _, _, _, _ -> throw IOException("Test export failure") }
        val input = store.newInput()
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.rgb(180, 110, 75))
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            val expected = input.readBytes()
            val result = store.export(input, RenderRecipe(Look.SOL, 1f), false)
            try {
                assertTrue(result.message.contains("Original saved"))
                assertFalse(input.exists())
                context.contentResolver.openInputStream(result.media.uri).use {
                    assertArrayEquals(expected, requireNotNull(it).readBytes())
                }
                context.contentResolver.query(result.media.uri, arrayOf(MediaStore.Images.Media.IS_PENDING),
                    null, null, null).use {
                    assertTrue(requireNotNull(it).moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
            } finally { context.contentResolver.delete(result.media.uri, null, null) }
        } finally {
            bitmap.recycle()
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }

    @Test fun incompleteJpegCannotBePublishedOrDeletedByRecovery() {
        val directory = File(context.cacheDir, "photo-test-${UUID.randomUUID()}")
        val store = PhotoStore(context, directory)
        val input = store.newInput()
        try {
            input.writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 3))
            assertTrue(store.recover().isEmpty())
            assertTrue(input.exists())
            assertTrue(runCatching { store.export(input, RenderRecipe(), false) }.isFailure)
        } finally { input.delete(); directory.delete() }
    }
}
