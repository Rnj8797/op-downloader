package com.opdownloader.app.data.mediastore

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scoped Storage & Android MediaStore Manager
 * Writes completed downloads to Movies/OP Downloader and Pictures/OP Downloader
 * without requiring broad legacy storage permissions on Android 10+ (API 29+).
 */
@Singleton
class MediaStoreHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val DIRECTORY_NAME = "OP Downloader"
    }

    /**
     * Commits a completed temporary media file into the Android MediaStore
     */
    fun saveMediaToGallery(
        tempFile: File,
        filename: String,
        mimeType: String,
        isVideo: Boolean
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = if (isVideo) {
                    "${Environment.DIRECTORY_MOVIES}/$DIRECTORY_NAME"
                } else {
                    "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME"
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                // Mark pending during write so other apps don't access half-written file
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val targetCollection: Uri = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        val uri = resolver.insert(targetCollection, contentValues) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(tempFile).use { inputStream ->
                    copyStream(inputStream, outputStream)
                }
            }

            // Publish media file by clearing IS_PENDING
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            // Cleanup local temp file once safely committed to MediaStore
            if (tempFile.exists()) {
                tempFile.delete()
            }

            return uri
        } catch (e: Exception) {
            // Delete corrupt entry if write fails
            resolver.delete(uri, null, null)
            return null
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024) // 64 KB buffer for streaming efficiency
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
    }
}
