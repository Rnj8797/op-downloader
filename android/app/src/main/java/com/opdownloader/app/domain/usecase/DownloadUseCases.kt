package com.opdownloader.app.domain.usecase

import com.opdownloader.app.domain.model.DownloadItem
import com.opdownloader.app.domain.model.UrlInspectionResult
import com.opdownloader.app.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import java.net.URI
import javax.inject.Inject

/**
 * UseCase to rigorously validate and sanitize URLs before attempting network inspection
 */
class ValidateUrlUseCase @Inject constructor() {
    operator fun invoke(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult.Invalid("URL cannot be empty.")
        }

        var trimmed = url.trim().trim('\'', '"', '`')
        if (trimmed.startsWith("reel/", ignoreCase = true) || trimmed.startsWith("/reel/", ignoreCase = true) ||
            trimmed.startsWith("p/", ignoreCase = true) || trimmed.startsWith("/p/", ignoreCase = true) ||
            trimmed.startsWith("stories/", ignoreCase = true) || trimmed.startsWith("/stories/", ignoreCase = true)) {
            trimmed = "https://www.instagram.com/" + trimmed.removePrefix("/")
        } else if (trimmed.startsWith("instagram.com/", ignoreCase = true) || trimmed.startsWith("www.instagram.com/", ignoreCase = true)) {
            trimmed = "https://" + trimmed
        } else if (trimmed.startsWith("youtu.be/", ignoreCase = true) || trimmed.startsWith("youtube.com/", ignoreCase = true) || trimmed.startsWith("www.youtube.com/", ignoreCase = true)) {
            trimmed = "https://" + trimmed
        } else if (trimmed.startsWith("facebook.com/", ignoreCase = true) || trimmed.startsWith("fb.watch/", ignoreCase = true) || trimmed.startsWith("www.facebook.com/", ignoreCase = true)) {
            trimmed = "https://" + trimmed
        } else if (trimmed.startsWith("tiktok.com/", ignoreCase = true) || trimmed.startsWith("www.tiktok.com/", ignoreCase = true)) {
            trimmed = "https://" + trimmed
        } else if (trimmed.startsWith("x.com/", ignoreCase = true) || trimmed.startsWith("twitter.com/", ignoreCase = true)) {
            trimmed = "https://" + trimmed
        }

        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()

            // 1. Enforce HTTPS only (reject dangerous file://, javascript:, ftp://)
            if (scheme != "https") {
                return ValidationResult.Invalid("Only secure HTTPS links are supported.")
            }

            val host = uri.host?.lowercase() ?: return ValidationResult.Invalid("Malformed URL hostname.")

            // 2. Reject internal, link-local, loopback and cloud metadata hosts
            if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" ||
                host.startsWith("192.168.") || host.startsWith("10.") ||
                host.startsWith("172.16.") || host.startsWith("169.254.") || host == "::1"
            ) {
                return ValidationResult.Invalid("Access to local or private network addresses is prohibited.")
            }

            ValidationResult.Valid(trimmed)
        } catch (e: Exception) {
            ValidationResult.Invalid("Malformed URL syntax.")
        }
    }

    sealed class ValidationResult {
        data class Valid(val normalizedUrl: String) : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}

/**
 * UseCase to inspect link and extract available formats
 */
class InspectUrlUseCase @Inject constructor(
    private val repository: DownloadRepository,
    private val validateUrlUseCase: ValidateUrlUseCase
) {
    suspend operator fun invoke(url: String): Result<UrlInspectionResult> {
        return when (val validation = validateUrlUseCase(url)) {
            is ValidateUrlUseCase.ValidationResult.Valid -> {
                repository.inspectUrl(validation.normalizedUrl)
            }
            is ValidateUrlUseCase.ValidationResult.Invalid -> {
                Result.failure(IllegalArgumentException(validation.reason))
            }
        }
    }
}

/**
 * UseCase to enqueue a new download task
 */
class EnqueueDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(url: String, formatId: String, title: String): Result<String> {
        // Sanitize title to prevent path traversal or invalid characters
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(64)
            .ifBlank { "Media_${System.currentTimeMillis()}" }

        return repository.enqueueDownload(url, formatId, safeTitle)
    }
}

/**
 * UseCase to stream all download records reactively
 */
class GetDownloadsUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadItem>> = repository.getAllDownloads()
    fun getActive(): Flow<List<DownloadItem>> = repository.getActiveDownloads()
}

/**
 * UseCase to control download task states
 */
class ControlDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend fun pause(id: String) = repository.pauseDownload(id)
    suspend fun resume(id: String) = repository.resumeDownload(id)
    suspend fun cancel(id: String) = repository.cancelDownload(id)
    suspend fun delete(id: String, deleteLocalFile: Boolean = false) =
        repository.deleteDownload(id, deleteLocalFile)
}
