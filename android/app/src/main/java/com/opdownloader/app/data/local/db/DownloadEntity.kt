package com.opdownloader.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.opdownloader.app.domain.model.DownloadItem
import com.opdownloader.app.domain.model.DownloadStatus
import com.opdownloader.app.domain.model.MediaType
import java.time.Instant

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAtEpochMs"])
    ]
)
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val sourceUrlHash: String,
    val sourceUrlMasked: String,
    val safeFilename: String,
    val mimeType: String,
    val mediaType: String,
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localUri: String? = null,
    val errorMessage: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val completedAtEpochMs: Long? = null
) {
    fun toDomain(): DownloadItem {
        return DownloadItem(
            id = id,
            sourceUrl = sourceUrlMasked,
            safeFilename = safeFilename,
            mimeType = mimeType,
            mediaType = try { MediaType.valueOf(mediaType) } catch (e: Exception) { MediaType.VIDEO },
            status = try { DownloadStatus.valueOf(status) } catch (e: Exception) { DownloadStatus.QUEUED },
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            localUri = localUri,
            errorMessage = errorMessage,
            createdAt = Instant.ofEpochMilli(createdAtEpochMs),
            completedAt = completedAtEpochMs?.let { Instant.ofEpochMilli(it) }
        )
    }

    companion object {
        fun fromDomain(item: DownloadItem, urlHash: String): DownloadEntity {
            return DownloadEntity(
                id = item.id,
                sourceUrlHash = urlHash,
                sourceUrlMasked = item.sourceUrl,
                safeFilename = item.safeFilename,
                mimeType = item.mimeType,
                mediaType = item.mediaType.name,
                status = item.status.name,
                downloadedBytes = item.downloadedBytes,
                totalBytes = item.totalBytes,
                localUri = item.localUri,
                errorMessage = item.errorMessage,
                createdAtEpochMs = item.createdAt.toEpochMilli(),
                completedAtEpochMs = item.completedAt?.toEpochMilli()
            )
        }
    }
}
