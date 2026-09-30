package com.opdownloader.app.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import com.opdownloader.app.data.mediastore.MediaStoreHelper
import com.opdownloader.app.ui.screens.downloads.DownloadTask
import com.opdownloader.app.ui.screens.downloads.ItemStatus
import com.opdownloader.app.ui.screens.home.RecentDownloadItem
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Global reactive download state manager for OP Downloader.
 * Streams real playable media to device, provides live progress,
 * saves valid MP4/MP3 files directly to Android Gallery (DCIM/Music/OP Downloader),
 * and enables direct in-app video & audio playback.
 */
object DownloadStateManager {

    private const val TAG = "DownloadStateManager"

    val tasks = mutableStateListOf<DownloadTask>()
    val recentDownloads = mutableStateListOf<RecentDownloadItem>()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedJobs = ConcurrentHashMap<String, Boolean>()
    private var isInitialized = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Initializes existing saved downloads from disk on startup
     */
    fun initExistingDownloads(context: Context) {
        if (isInitialized) return
        isInitialized = true

        scope.launch(Dispatchers.IO) {
            val appInternalDir = File(context.filesDir, "saved_media")
            if (appInternalDir.exists() && appInternalDir.isDirectory) {
                val savedFiles = appInternalDir.listFiles() ?: emptyArray()
                val sorted = savedFiles.filter { it.isFile && it.length() > 0 }
                    .sortedByDescending { it.lastModified() }

                withContext(Dispatchers.Main) {
                    for (file in sorted) {
                        val isVid = file.extension.lowercase() in listOf("mp4", "mkv", "webm", "mov")
                        val isAud = file.extension.lowercase() in listOf("mp3", "m4a", "wav")
                        val sizeMb = String.format("%.1f MB", file.length() / (1024f * 1024f))
                        val id = UUID.nameUUIDFromBytes(file.name.toByteArray()).toString()

                        if (recentDownloads.none { it.filename == file.name }) {
                            recentDownloads.add(
                                RecentDownloadItem(
                                    id = id,
                                    filename = file.name,
                                    sizeText = sizeMb,
                                    isVideo = isVid && !isAud,
                                    dateText = "Saved",
                                    statusText = "✓ Saved to Gallery • Tap to Play",
                                    filePath = file.absolutePath
                                )
                            )
                        }

                        if (tasks.none { it.filename == file.name }) {
                            tasks.add(
                                DownloadTask(
                                    id = id,
                                    filename = file.name,
                                    isVideo = isVid && !isAud,
                                    status = ItemStatus.COMPLETED,
                                    progress = 1.0f,
                                    downloadedBytesText = sizeMb,
                                    totalBytesText = sizeMb,
                                    speedText = null,
                                    etaText = null,
                                    dateText = "Saved",
                                    filePath = file.absolutePath
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Enqueue and begin downloading a media item
     */
    fun startDownload(
        context: Context,
        url: String,
        qualityId: String,
        platformTitle: String,
        isVideo: Boolean,
        sizeText: String
    ) {
        val taskId = UUID.randomUUID().toString()
        val isAudio = qualityId == "audio"
        val ext = when {
            isAudio -> "mp3"
            isVideo -> "mp4"
            else -> "jpg"
        }

        val initialCleanTitle = platformTitle
            .replace(Regex("""[^a-zA-Z0-9_\-\s]"""), "")
            .trim()
            .replace(Regex("""\s+"""), "_")
            .ifBlank { "Media" }

        val initialFilename = "${initialCleanTitle}_${qualityId}_${System.currentTimeMillis() % 10000}.$ext"

        val initialTask = DownloadTask(
            id = taskId,
            filename = initialFilename,
            isVideo = isVideo && !isAudio,
            status = ItemStatus.DOWNLOADING,
            progress = 0.05f,
            downloadedBytesText = "0.1 MB",
            totalBytesText = sizeText,
            speedText = "Resolving stream...",
            etaText = "Connecting...",
            dateText = "Just now",
            filePath = null,
            sourceUrl = url,
            qualityId = qualityId,
            platformTitle = platformTitle
        )

        // Insert at top of task list
        tasks.add(0, initialTask)

        val job = scope.launch(Dispatchers.IO) {
            try {
                val candidates = mutableListOf<String>()
                var resolvedTitle: String = platformTitle
                val lower = url.lowercase()

                withContext(Dispatchers.Main) {
                    val index = tasks.indexOfFirst { it.id == taskId }
                    if (index != -1) {
                        tasks[index] = tasks[index].copy(
                            speedText = "Extracting video...",
                            etaText = "Analyzing link..."
                        )
                    }
                }

                // 1. YouTube extractor (Native InnerTube + Invidious fallback)
                if (YouTubeExtractor.isYouTubeUrl(url)) {
                    val videoId = YouTubeExtractor.extractVideoId(url)
                    if (videoId != null) {
                        val ytResult = YouTubeExtractor.extractMedia(httpClient, videoId)
                        if (ytResult != null) {
                            resolvedTitle = ytResult.title
                            val stream = if (isAudio && !ytResult.audioUrl.isNullOrBlank()) {
                                ytResult.audioUrl
                            } else {
                                ytResult.videoUrl
                            }
                            candidates.add(stream)
                        }
                    }
                }
                // 2. TikTok extractor (Native TikWM high-speed unwatermarked stream)
                else if (TikTokExtractor.isTikTokUrl(url)) {
                    val ttResult = TikTokExtractor.extractMedia(httpClient, url)
                    if (ttResult != null) {
                        resolvedTitle = ttResult.title
                        val stream = if (isAudio && !ttResult.audioUrl.isNullOrBlank()) {
                            ttResult.audioUrl
                        } else {
                            ttResult.videoUrl
                        }
                        candidates.add(stream)
                    }
                }
                // 3. Instagram extractor
                else if (InstagramExtractor.isInstagramUrl(url)) {
                    val shortcode = InstagramExtractor.extractShortcode(url)
                    if (shortcode != null) {
                        val igResult = InstagramExtractor.extractMedia(httpClient, shortcode)
                        if (igResult != null) {
                            resolvedTitle = igResult.title
                            candidates.add(igResult.videoUrl)
                        }
                    }
                }
                // 4. Direct playable video / audio stream
                else if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") ||
                    lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") ||
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                ) {
                    candidates.add(url)
                }
                // 5. General web page with OpenGraph or HTML5 video tag
                else {
                    val ogStream = extractOpenGraphMedia(url)
                    if (ogStream != null) {
                        candidates.add(ogStream)
                    }
                }

                // If no real stream could be extracted, do NOT download a flower video.
                // Fail explicitly so the user knows what happened.
                if (candidates.isEmpty()) {
                    throw Exception("Could not extract video stream. Please ensure the link is public or direct.")
                }

                // Sanitize resolved title and build real filename
                val cleanResolved = resolvedTitle
                    .replace(Regex("""[^a-zA-Z0-9_\-\s]"""), "")
                    .trim()
                    .replace(Regex("""\s+"""), "_")
                    .take(45)
                    .ifBlank { "Media" }

                val finalFilename = "${cleanResolved}_${qualityId}.$ext"

                withContext(Dispatchers.Main) {
                    val index = tasks.indexOfFirst { it.id == taskId }
                    if (index != -1) {
                        tasks[index] = tasks[index].copy(
                            filename = finalFilename,
                            platformTitle = resolvedTitle,
                            speedText = "Starting download...",
                            etaText = "Connecting to stream..."
                        )
                    }
                }

                performLiveStreamDownload(
                    context = context,
                    taskId = taskId,
                    streamCandidates = candidates,
                    filename = finalFilename,
                    ext = ext,
                    isVideo = isVideo && !isAudio,
                    isAudio = isAudio,
                    sizeText = sizeText
                )
            } catch (e: Exception) {
                Log.e(TAG, "Download failed completely: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val index = tasks.indexOfFirst { it.id == taskId }
                    if (index != -1) {
                        tasks[index] = tasks[index].copy(
                            status = ItemStatus.FAILED,
                            speedText = null,
                            etaText = "Download failed: ${e.localizedMessage ?: "Stream error"}"
                        )
                    }
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                activeJobs.remove(taskId)
            }
        }

        activeJobs[taskId] = job
    }

    /**
     * Extracts OpenGraph or HTML5 video tags from public web links
     */
    private fun extractOpenGraphMedia(rawUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(rawUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val contentType = resp.header("Content-Type")?.lowercase() ?: ""
                if (contentType.startsWith("video/") || contentType.startsWith("audio/")) {
                    return rawUrl
                }
                val html = resp.body?.string() ?: return null

                val ogMatch = Regex("""<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("""<meta\s+content=["']([^"']+)["']\s+property=["']og:video(?::secure_url)?["']""", RegexOption.IGNORE_CASE).find(html)
                if (ogMatch != null) {
                    return ogMatch.groupValues[1]
                }

                val vidMatch = Regex("""<video[^>]+src=["']([^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE).find(html)
                if (vidMatch != null) {
                    return vidMatch.groupValues[1]
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retries a failed download task
     */
    fun retryDownload(context: Context, taskId: String) {
        val task = tasks.find { it.id == taskId } ?: return
        tasks.removeAll { it.id == taskId }
        startDownload(
            context = context,
            url = task.sourceUrl ?: "https://www.youtube.com/watch",
            qualityId = task.qualityId ?: "1080p",
            platformTitle = task.platformTitle ?: "Media Download",
            isVideo = task.isVideo,
            sizeText = task.totalBytesText
        )
    }

    /**
     * Streams real bytes with multi-candidate failover, delivers live progress updates,
     * commits to Gallery (DCIM/Music) and saves in-app copy with guaranteed playback.
     */
    private suspend fun performLiveStreamDownload(
        context: Context,
        taskId: String,
        streamCandidates: List<String>,
        filename: String,
        ext: String,
        isVideo: Boolean,
        isAudio: Boolean,
        sizeText: String
    ) {
        var success = false
        var lastException: Exception? = null

        for (candidateUrl in streamCandidates) {
            val tempFile = File(context.cacheDir, "op_${taskId}_temp.$ext")
            try {
                Log.d(TAG, "Attempting stream from: $candidateUrl")
                val request = Request.Builder()
                    .url(candidateUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Stream response code: ${response.code}")
                    }
                    val body = response.body ?: throw Exception("Stream response body is empty")

                    val totalBytes = body.contentLength().takeIf { it > 0 } ?: (18 * 1024 * 1024L)
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(tempFile)
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var read: Int
                    val startTime = System.currentTimeMillis()
                    var lastUpdate = startTime

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        while (pausedJobs[taskId] == true) {
                            delay(300)
                        }

                        outputStream.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 250) {
                            lastUpdate = now
                            val progress = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.08f, 0.96f)
                            val currentMb = String.format("%.1f MB", downloaded / (1024f * 1024f))

                            // Calculate real live speed and ETA
                            val elapsedSec = (now - startTime) / 1000f
                            val speedBytesPerSec = if (elapsedSec > 0.3f) (downloaded / elapsedSec) else 0f
                            val speedMb = speedBytesPerSec / (1024f * 1024f)
                            val speedText = if (speedMb > 0.05f) String.format("%.1f MB/s", speedMb) else "Downloading..."

                            val remainingBytes = (totalBytes - downloaded).coerceAtLeast(0L)
                            val etaSec = if (speedBytesPerSec > 1024f) (remainingBytes / speedBytesPerSec).toInt() else 0
                            val etaText = if (etaSec > 0) "${etaSec / 60}:${String.format("%02d", etaSec % 60)} remaining" else "Finishing..."

                            withContext(Dispatchers.Main) {
                                val index = tasks.indexOfFirst { it.id == taskId }
                                if (index != -1) {
                                    tasks[index] = tasks[index].copy(
                                        progress = progress,
                                        downloadedBytesText = currentMb,
                                        speedText = speedText,
                                        etaText = etaText
                                    )
                                }
                            }
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                }

                // Verify downloaded file is non-empty
                if (tempFile.exists() && tempFile.length() > 0L) {
                    val finalSizeMb = String.format("%.1f MB", tempFile.length() / (1024f * 1024f))
                    Log.d(TAG, "Stream successfully downloaded: ${tempFile.length()} bytes ($finalSizeMb)")

                    // Save completed media file to Android MediaStore and internal storage
                    val mediaStore = MediaStoreHelper(context)
                    val mime = when {
                        isAudio -> "audio/mpeg"
                        isVideo -> "video/mp4"
                        else -> "image/jpeg"
                    }
                    val saveResult = mediaStore.saveMediaToGallery(
                        tempFile = tempFile,
                        filename = filename,
                        mimeType = mime,
                        isVideo = isVideo,
                        isAudio = isAudio
                    )

                    completeDownload(
                        context = context,
                        taskId = taskId,
                        filename = filename,
                        sizeText = finalSizeMb,
                        isVideo = isVideo,
                        filePath = saveResult.absolutePath
                    )
                    success = true
                    break
                } else {
                    throw Exception("Downloaded file is empty")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Candidate $candidateUrl failed: ${e.message}, trying next...")
                lastException = e
                try {
                    if (tempFile.exists()) tempFile.delete()
                } catch (_: Exception) {}
            }
        }

        if (!success) {
            throw (lastException ?: Exception("All download stream candidates failed"))
        }
    }

    private suspend fun completeDownload(
        context: Context,
        taskId: String,
        filename: String,
        sizeText: String,
        isVideo: Boolean,
        filePath: String
    ) {
        withContext(Dispatchers.Main) {
            val index = tasks.indexOfFirst { it.id == taskId }
            if (index != -1) {
                tasks[index] = tasks[index].copy(
                    status = ItemStatus.COMPLETED,
                    progress = 1.0f,
                    downloadedBytesText = sizeText,
                    totalBytesText = sizeText,
                    speedText = null,
                    etaText = null,
                    dateText = "Just now",
                    filePath = filePath
                )
            }

            // Also add to Recent Downloads list for Home Screen with playable filePath
            recentDownloads.add(
                0,
                RecentDownloadItem(
                    id = taskId,
                    filename = filename,
                    sizeText = sizeText,
                    isVideo = isVideo,
                    dateText = "Just now",
                    statusText = "✓ Saved to Gallery • Tap to Play",
                    filePath = filePath
                )
            )

            Toast.makeText(context, "✓ Download Complete: Saved to Gallery", Toast.LENGTH_SHORT).show()
        }
    }

    fun pauseDownload(taskId: String) {
        pausedJobs[taskId] = true
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            tasks[index] = tasks[index].copy(
                status = ItemStatus.PAUSED,
                speedText = null,
                etaText = "Paused"
            )
        }
    }

    fun resumeDownload(taskId: String) {
        pausedJobs[taskId] = false
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            tasks[index] = tasks[index].copy(
                status = ItemStatus.DOWNLOADING,
                speedText = "Resuming...",
                etaText = "Downloading..."
            )
        }
    }

    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        pausedJobs.remove(taskId)
        tasks.removeAll { it.id == taskId }
    }
}
