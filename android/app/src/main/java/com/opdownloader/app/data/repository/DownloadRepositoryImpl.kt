package com.opdownloader.app.data.repository

import com.opdownloader.app.data.local.db.DownloadDao
import com.opdownloader.app.data.local.db.DownloadEntity
import com.opdownloader.app.data.mediastore.MediaStoreHelper
import com.opdownloader.app.domain.model.*
import com.opdownloader.app.domain.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao,
    private val mediaStoreHelper: MediaStoreHelper
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getAllDownloads().map { list -> list.map { it.toDomain() } }
    }

    override fun getActiveDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getActiveDownloads().map { list -> list.map { it.toDomain() } }
    }

    override fun getDownloadById(id: String): Flow<DownloadItem?> {
        return downloadDao.getDownloadById(id).map { it?.toDomain() }
    }

    override suspend fun enqueueDownload(
        url: String,
        formatId: String,
        safeTitle: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val isVideo = !url.endsWith(".jpg", ignoreCase = true) && !url.endsWith(".png", ignoreCase = true)
            val extension = if (isVideo) "mp4" else "jpg"
            val safeFilename = "${safeTitle}.$extension"
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE
            val urlHash = sha256(url)
            val jobId = UUID.randomUUID().toString()

            val entity = DownloadEntity(
                id = jobId,
                sourceUrlHash = urlHash,
                sourceUrlMasked = maskUrl(url),
                safeFilename = safeFilename,
                mimeType = mimeType,
                mediaType = mediaType.name,
                status = DownloadStatus.QUEUED.name,
                downloadedBytes = 0,
                totalBytes = 0,
                createdAtEpochMs = System.currentTimeMillis()
            )

            downloadDao.insertOrUpdate(entity)
            Result.success(jobId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pauseDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            downloadDao.updateStatus(id, DownloadStatus.PAUSED.name)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resumeDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            downloadDao.updateStatus(id, DownloadStatus.QUEUED.name)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            downloadDao.updateStatus(id, DownloadStatus.CANCELLED.name)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDownload(id: String, deleteLocalFile: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            downloadDao.deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun inspectUrl(url: String): Result<UrlInspectionResult> = withContext(Dispatchers.IO) {
        try {
            val lower = url.lowercase()
            val isVideo = !lower.endsWith(".jpg") && !lower.endsWith(".png") && !lower.contains("photo")
            val mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE
            val providerName = when {
                lower.contains("unsplash") -> "Unsplash Authorized API"
                lower.contains("archive.org") -> "Internet Archive (Open Access)"
                lower.contains("wikimedia") -> "Wikimedia Commons"
                else -> "Direct Public Stream"
            }

            val formats = if (isVideo) {
                listOf(
                    FormatOption("original", "Original", 85_983_232L, "video/mp4"),
                    FormatOption("1080p", "1080p", 56_623_104L, "video/mp4"),
                    FormatOption("720p", "720p", 32_505_856L, "video/mp4"),
                    FormatOption("480p", "480p", 18_874_368L, "video/mp4")
                )
            } else {
                listOf(
                    FormatOption("original", "Original", 4_410_291L, "image/jpeg"),
                    FormatOption("1080p", "1080p", 1_245_192L, "image/jpeg")
                )
            }

            val result = UrlInspectionResult(
                isValid = true,
                providerName = providerName,
                mediaType = mediaType,
                title = "Authorized Media ${System.currentTimeMillis().toString().takeLast(4)}",
                thumbnailUrl = null,
                durationSeconds = if (isVideo) 151 else null,
                availableFormats = formats
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun maskUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}${uri.path.take(30)}..."
        } catch (e: Exception) {
            "https://.../media"
        }
    }
}
