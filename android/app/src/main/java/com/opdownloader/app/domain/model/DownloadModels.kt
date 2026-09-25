package com.opdownloader.app.domain.model

import java.time.Instant

/**
 * Domain representations of Download Task Status
 */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    RETRYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class MediaType {
    VIDEO,
    IMAGE,
    AUDIO
}

/**
 * Core Domain Model for a Media Download Job
 */
data class DownloadItem(
    val id: String,
    val sourceUrl: String,
    val safeFilename: String,
    val mimeType: String,
    val mediaType: MediaType,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val transferRateBytesPerSec: Long = 0,
    val etaSeconds: Long = 0,
    val localUri: String? = null,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null
) {
    val progressPercentage: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()) else 0f

    val isFinished: Boolean
        get() = status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED
}

/**
 * URL Inspection Result Model
 */
data class UrlInspectionResult(
    val isValid: Boolean,
    val providerName: String,
    val mediaType: MediaType,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val availableFormats: List<FormatOption>,
    val failureReason: String? = null
)

data class FormatOption(
    val formatId: String,
    val label: String,
    val estimatedBytes: Long,
    val mimeType: String
)
