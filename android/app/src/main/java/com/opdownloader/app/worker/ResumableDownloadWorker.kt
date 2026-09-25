package com.opdownloader.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.opdownloader.app.data.local.db.DownloadDao
import com.opdownloader.app.data.mediastore.MediaStoreHelper
import com.opdownloader.app.domain.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

@HiltWorker
class ResumableDownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val mediaStoreHelper: MediaStoreHelper,
    private val notificationManager: DownloadNotificationManager
) : CoroutineWorker(appContext, params) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString("JOB_ID") ?: return@withContext Result.failure()
        val jobEntity = downloadDao.getDownloadByIdSync(jobId) ?: return@withContext Result.failure()

        val tempFile = File(appContext.cacheDir, "download_${jobId}.tmp")
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        // Initial foreground notification
        setForeground(createForegroundInfo(jobId, jobEntity.safeFilename, 0, "0 MB", "0 MB", "0 KB/s", "Starting..."))
        downloadDao.updateStatus(jobId, DownloadStatus.DOWNLOADING.name)

        try {
            // Build HTTP Request with Range support for resumption
            val requestBuilder = Request.Builder()
                .url(jobEntity.sourceUrlMasked) // Authorized direct media URL
                .addHeader("User-Agent", "OP-Downloader-Android/1.0")

            if (existingBytes > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            }

            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful && response.code != 206) {
                // If range request is not supported (416), restart from beginning
                if (response.code == 416) {
                    tempFile.delete()
                    existingBytes = 0L
                } else {
                    downloadDao.updateStatus(jobId, DownloadStatus.FAILED.name)
                    return@withContext Result.retry()
                }
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) existingBytes + contentLength else jobEntity.totalBytes
            var downloadedBytes = existingBytes

            val inputStream: InputStream = body.byteStream()
            val raf = RandomAccessFile(tempFile, "rw")
            raf.seek(downloadedBytes)

            val buffer = ByteArray(64 * 1024) // 64 KB memory-safe chunk buffer
            var bytesRead: Int
            var lastUpdateEpoch = System.currentTimeMillis()
            var bytesReadSinceLastUpdate = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isStopped) {
                    raf.close()
                    inputStream.close()
                    downloadDao.updateStatus(jobId, DownloadStatus.PAUSED.name)
                    return@withContext Result.success()
                }

                raf.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                bytesReadSinceLastUpdate += bytesRead

                val now = System.currentTimeMillis()
                val elapsed = now - lastUpdateEpoch

                // Update notification and database progress every 500ms
                if (elapsed >= 500) {
                    val speedBytesPerSec = (bytesReadSinceLastUpdate * 1000) / elapsed
                    val speedText = formatSpeed(speedBytesPerSec)
                    val remainingBytes = totalBytes - downloadedBytes
                    val etaSeconds = if (speedBytesPerSec > 0) remainingBytes / speedBytesPerSec else 0
                    val etaText = formatEta(etaSeconds)
                    val pct = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0

                    downloadDao.updateProgress(jobId, downloadedBytes, totalBytes)
                    setForeground(
                        createForegroundInfo(
                            jobId,
                            jobEntity.safeFilename,
                            pct,
                            formatBytes(downloadedBytes),
                            formatBytes(totalBytes),
                            speedText,
                            etaText
                        )
                    )

                    lastUpdateEpoch = now
                    bytesReadSinceLastUpdate = 0L
                }
            }

            raf.close()
            inputStream.close()

            // Download finished! Atomically insert into device MediaStore (Gallery)
            val isVideo = jobEntity.mediaType == "VIDEO"
            val galleryUri = mediaStoreHelper.saveMediaToGallery(
                tempFile = tempFile,
                filename = jobEntity.safeFilename,
                mimeType = jobEntity.mimeType,
                isVideo = isVideo
            )

            if (galleryUri != null) {
                downloadDao.updateStatus(jobId, DownloadStatus.COMPLETED.name)
                notificationManager.showCompletionNotification(jobId, jobEntity.safeFilename)
                notificationManager.cancelNotification(jobId)
                Result.success()
            } else {
                downloadDao.updateStatus(jobId, DownloadStatus.FAILED.name)
                Result.failure()
            }

        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                downloadDao.updateStatus(jobId, DownloadStatus.RETRYING.name)
                Result.retry()
            } else {
                downloadDao.updateStatus(jobId, DownloadStatus.FAILED.name)
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(
        jobId: String,
        filename: String,
        progressPct: Int,
        downloadedText: String,
        totalText: String,
        speedText: String,
        etaText: String
    ): ForegroundInfo {
        val notification = notificationManager.buildProgressNotification(
            jobId = jobId,
            filename = filename,
            progressPct = progressPct,
            speedText = speedText,
            etaText = etaText,
            downloadedText = downloadedText,
            totalText = totalText
        )
        return ForegroundInfo(jobId.hashCode(), notification)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes.toFloat() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toFloat() / (1024 * 1024))
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return "${formatBytes(bytesPerSec)}/s"
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "Calculating..."
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d remaining".format(m, s)
    }
}
