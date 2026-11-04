package com.shubhraghav.lensqivo

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

/** Worker-owned photo transaction. The native JPEG survives until a public copy commits. */
class PhotoStore(private val context: Context,
    private val directory: File = File(context.noBackupFilesDir, "pending-photos"),
    private val renderPhoto: (Context, File, File, RenderRecipe) -> Unit = PhotoRenderer::render) {
    data class Result(val media: SavedMedia, val message: String)

    fun newInput(): File {
        check(directory.isDirectory || directory.mkdirs()) { "Private capture storage unavailable" }
        return File(directory, "${UUID.randomUUID()}.source.jpg")
    }

    fun export(input: File, recipe: RenderRecipe, keepOriginal: Boolean): Result {
        requireCompleteJpeg(input)
        if (recipe.isOriginal()) return publishOriginal(input, "Photo saved to DCIM/Lensqivo.")
        val edited = File(directory, "${input.nameWithoutExtension}.edit.jpg")
        try {
            try {
                renderPhoto(context, input, edited, recipe)
            } catch (_: Exception) {
                return publishOriginal(input, "Original saved. The creative edit could not be applied at full resolution.")
            } catch (_: OutOfMemoryError) {
                return publishOriginal(input, "Original saved. There was not enough memory for a full-resolution creative edit.")
            }
            val uri = publish(edited, "")
            if (keepOriginal) {
                try {
                    publish(input, "_Original")
                } catch (_: Exception) {
                    return Result(SavedMedia(uri, "image/jpeg"),
                        "Edited photo saved. Original retained privately; free storage, then recover it in Settings.")
                }
            }
            input.delete()
            return Result(SavedMedia(uri, "image/jpeg"),
                if (keepOriginal) "Photo and original saved to DCIM/Lensqivo." else "Photo saved to DCIM/Lensqivo.")
        } finally {
            edited.delete()
        }
    }

    fun recover(): List<Result> {
        removeInterruptedPublications()
        directory.listFiles { file -> file.name.endsWith(".edit.jpg") ||
            file.name.startsWith("photo-export-") && file.name.endsWith(".jpg") }?.forEach { it.delete() }
        val recovered = mutableListOf<Result>()
        directory.listFiles { file -> file.name.endsWith(".source.jpg") }?.sortedBy { it.lastModified() }
            ?.forEach { file ->
                if (runCatching { requireCompleteJpeg(file) }.isSuccess) {
                    recovered += publishOriginal(file, "An interrupted capture was recovered as an original.")
                }
            }
        return recovered
    }

    fun latest(): SavedMedia? = listOf(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "image/jpeg",
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "video/mp4",
    ).mapNotNull { (collection, mime) ->
        context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
            arrayOf(context.packageName), "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC")?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val name = cursor.getString(2)
                val edit = if (mime == "image/jpeg" && name.endsWith("_Original.jpg"))
                    committedPhoto(name.removeSuffix("_Original.jpg") + ".jpg") else null
                cursor.getLong(1) to SavedMedia(edit ?: android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)), mime)
            }
        }
    }.maxByOrNull { it.first }?.second

    private fun publishOriginal(input: File, message: String): Result {
        val uri = publish(input, "_Original")
        input.delete()
        return Result(SavedMedia(uri, "image/jpeg"), message)
    }

    private fun publish(file: File, suffix: String): Uri {
        val resolver = context.contentResolver
        val name = "Lensqivo_${file.name.substringBefore('.')}$suffix.jpg"
        // Retrying after process death must not duplicate a photo that already committed.
        committedPhoto(name)?.let { return it }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Lensqivo")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Media storage unavailable")
        try {
            requireNotNull(resolver.openOutputStream(uri, "w")).use { output ->
                file.inputStream().use { it.copyTo(output) }
            }
            val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            check(resolver.update(uri, ready, null, null) == 1) { "Photo publication failed" }
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun committedPhoto(name: String): Uri? {
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.IS_PENDING} = 0 AND ${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ?",
            arrayOf(name, "DCIM/Lensqivo/", context.packageName), null)?.use { cursor ->
            if (cursor.moveToFirst()) return android.content.ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun removeInterruptedPublications() {
        val captures = directory.listFiles { file -> file.name.endsWith(".source.jpg") }
            ?.map { "Lensqivo_${it.name.substringBefore('.')}" }?.toSet().orEmpty()
        if (captures.isEmpty()) return
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Images.Media.IS_PENDING} = 1 AND ${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(context.packageName, "DCIM/Lensqivo/")
        // MediaStore hides pending rows unless explicitly included, even with an IS_PENDING selection.
        val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            resolver.query(collection, projection, Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }, null)
        } else {
            resolver.query(MediaStore.setIncludePending(collection), projection, selection, selectionArgs, null)
        }
        pending?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (captures.any { name == "$it.jpg" || name == "${it}_Original.jpg" }) {
                    resolver.delete(android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)), null, null)
                }
            }
        }
    }

    internal fun requireCompleteJpeg(file: File) {
        require(file.length() >= 4) { "Incomplete capture" }
        RandomAccessFile(file, "r").use {
            require(it.readUnsignedShort() == 0xffd8) { "Not a JPEG capture" }
            it.seek(it.length() - 2)
            require(it.readUnsignedShort() == 0xffd9) { "Incomplete JPEG capture" }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid capture dimensions" }
    }
}
