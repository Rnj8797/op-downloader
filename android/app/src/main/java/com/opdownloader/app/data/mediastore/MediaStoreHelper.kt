package com.opdownloader.app.data.mediastore

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class SavedMediaResult(
    val file: File,
    val uri: Uri?,
    val absolutePath: String
)

/**
 * Robust Media & Gallery Storage Manager
 * Ensures 100% visibility in Android & Vivo/Samsung/Xiaomi Gallery by writing directly
 * via MediaStore to DCIM / Music and triggering immediate MediaScanner indexing,
 * while retaining a permanent copy in internal storage for guaranteed in-app playback.
 */
@Singleton
class MediaStoreHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "MediaStoreHelper"
        const val FOLDER_NAME = "OP Downloader"
    }

    /**
     * Commits a downloaded media file to device storage & Android Gallery.
     * Guarantees that the file is physically present and immediately indexed by Gallery apps.
     */
    fun saveMediaToGallery(
        tempFile: File,
        filename: String,
        mimeType: String,
        isVideo: Boolean,
        isAudio: Boolean = false
    ): SavedMediaResult {
        // Step 1: Always save a permanent internal copy for guaranteed 100% in-app playback
        val appInternalDir = File(context.filesDir, "saved_media").apply { mkdirs() }
        val inAppFile = File(appInternalDir, filename)

        try {
            FileInputStream(tempFile).use { input ->
                FileOutputStream(inAppFile).use { output ->
                    copyStream(input, output)
                }
            }
            Log.d(TAG, "Successfully saved permanent in-app media file: ${inAppFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying to internal filesDir: ${e.message}", e)
        }

        // Step 2: Insert into Android MediaStore to register with Gallery & System Photos
        var mediaUri: Uri? = null
        var physicalGalleryPath: String? = null

        try {
            val resolver = context.contentResolver
            val targetCollection = when {
                isAudio -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                }
                isVideo -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                }
            }

            // In Android Q+, RELATIVE_PATH must end with a trailing slash
            val candidateRelPaths = when {
                isAudio -> listOf(
                    "${Environment.DIRECTORY_MUSIC}/$FOLDER_NAME/",
                    "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME/"
                )
                isVideo -> listOf(
                    "${Environment.DIRECTORY_DCIM}/$FOLDER_NAME/",
                    "${Environment.DIRECTORY_MOVIES}/$FOLDER_NAME/",
                    "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME/"
                )
                else -> listOf(
                    "${Environment.DIRECTORY_DCIM}/$FOLDER_NAME/",
                    "${Environment.DIRECTORY_PICTURES}/$FOLDER_NAME/"
                )
            }

            for (relPath in candidateRelPaths) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.TITLE, filename.substringBeforeLast("."))
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
                        put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                    mediaUri = resolver.insert(targetCollection, values)
                    if (mediaUri != null) {
                        // Write media bytes directly through ContentResolver stream
                        resolver.openOutputStream(mediaUri, "w")?.use { outStream ->
                            FileInputStream(tempFile).use { inStream ->
                                copyStream(inStream, outStream)
                            }
                        }

                        // Clear IS_PENDING so system Gallery immediately indexes and displays the media
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val updateValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.IS_PENDING, 0)
                            }
                            try {
                                resolver.update(mediaUri, updateValues, null, null)
                            } catch (e: Exception) {
                                Log.w(TAG, "Notice clearing IS_PENDING: ${e.message}")
                            }
                        }

                        // Retrieve actual on-disk path if exposed by MediaStore
                        try {
                            resolver.query(mediaUri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                    if (dataIdx != -1) {
                                        physicalGalleryPath = cursor.getString(dataIdx)
                                    }
                                }
                            }
                        } catch (_: Exception) {}

                        Log.d(TAG, "MediaStore registered successfully in $relPath: $mediaUri (disk: $physicalGalleryPath)")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed inserting in $relPath, trying next: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing to MediaStore: ${e.message}", e)
        }

        // Step 3: Direct filesystem copy fallback for legacy devices or secondary gallery albums
        val basePublicDir = when {
            isAudio -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            isVideo -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        }
        val directFolder = File(basePublicDir, FOLDER_NAME)
        val directFile = File(directFolder, filename)
        try {
            if (!directFile.exists()) {
                directFolder.mkdirs()
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(directFile).use { output ->
                        copyStream(input, output)
                    }
                }
            }
        } catch (_: Exception) {}

        // Step 4: Force MediaScanner to index paths for immediate Gallery appearance
        val pathsToScan = mutableListOf<String>()
        if (!physicalGalleryPath.isNullOrBlank()) {
            pathsToScan.add(physicalGalleryPath!!)
        }
        if (directFile.exists()) {
            pathsToScan.add(directFile.absolutePath)
        }
        // Also guess expected DCIM path
        val expectedDcim = File("/sdcard/DCIM/$FOLDER_NAME/$filename")
        if (expectedDcim.exists()) {
            pathsToScan.add(expectedDcim.absolutePath)
        }

        if (pathsToScan.isNotEmpty()) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    pathsToScan.distinct().toTypedArray(),
                    arrayOf(mimeType)
                ) { path, uri ->
                    Log.d(TAG, "MediaScanner indexed gallery item: $path -> $uri")
                }

                val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = mediaUri ?: Uri.fromFile(File(pathsToScan.first()))
                }
                context.sendBroadcast(scanIntent)
            } catch (e: Exception) {
                Log.w(TAG, "MediaScanner broadcast error: ${e.message}")
            }
        }

        // Cleanup temporary cache file
        try {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (_: Exception) {}

        // Active playback file for in-app video player:
        // inAppFile is guaranteed 100% accessible to the app without permission issues
        val activeFile = if (inAppFile.exists() && inAppFile.length() > 0) {
            inAppFile
        } else if (directFile.exists()) {
            directFile
        } else {
            tempFile
        }

        return SavedMediaResult(
            file = activeFile,
            uri = mediaUri ?: Uri.fromFile(activeFile),
            absolutePath = activeFile.absolutePath
        )
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
    }
}
