package com.opdownloader.app.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object YouTubeExtractor {
    private const val TAG = "YouTubeExtractor"

    data class YouTubeMediaResult(
        val videoUrl: String,
        val audioUrl: String?,
        val title: String
    )

    private val YOUTUBE_ID_REGEX = Regex(
        """(?:youtu\.be/|youtube\.com/(?:embed/|v/|shorts/|live/|watch\?v=|watch\?.+?&v=))([\w-]{11})"""
    )

    fun isYouTubeUrl(rawUrl: String): Boolean {
        val lower = rawUrl.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }

    fun extractVideoId(rawUrl: String): String? {
        val match = YOUTUBE_ID_REGEX.find(rawUrl)
        if (match != null) {
            return match.groupValues[1]
        }
        return try {
            when {
                rawUrl.contains("youtu.be/") -> {
                    rawUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("/").trim()
                }
                rawUrl.contains("youtube.com/shorts/") -> {
                    rawUrl.substringAfter("youtube.com/shorts/").substringBefore("?").substringBefore("/").trim()
                }
                rawUrl.contains("v=") -> {
                    rawUrl.substringAfter("v=").substringBefore("&").substringBefore("#").trim()
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extractMedia(client: OkHttpClient, videoId: String): YouTubeMediaResult? {
        // Method 1: InnerTube API with varied mobile clients
        val clients = listOf(
            Pair("ANDROID_VR", "1.60.19"),
            Pair("ANDROID_TESTSUITE", "1.9")
        )

        for ((clientName, clientVersion) in clients) {
            try {
                val payload = JSONObject().apply {
                    val clientObj = JSONObject().apply {
                        put("clientName", clientName)
                        put("clientVersion", clientVersion)
                        put("hl", "en")
                        put("gl", "US")
                    }
                    put("context", JSONObject().apply { put("client", clientObj) })
                    put("videoId", videoId)
                }

                val request = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyStr = response.body?.string() ?: return@use
                    val json = JSONObject(bodyStr)

                    val status = json.optJSONObject("playabilityStatus")?.optString("status")
                    if (status != null && status != "OK") {
                        Log.d(TAG, "Client $clientName returned status: $status")
                        return@use
                    }

                    val details = json.optJSONObject("videoDetails")
                    val title = details?.optString("title")?.ifBlank { null } ?: "YouTube_Video"

                    val streamingData = json.optJSONObject("streamingData") ?: return@use
                    val formats = streamingData.optJSONArray("formats")
                    val adaptive = streamingData.optJSONArray("adaptiveFormats")

                    var videoUrl: String? = null
                    var audioUrl: String? = null

                    // 1. Muxed formats contain both audio and video
                    if (formats != null) {
                        for (i in 0 until formats.length()) {
                            val f = formats.optJSONObject(i) ?: continue
                            val u = f.optString("url")
                            if (u.isNotBlank()) {
                                videoUrl = u
                                break
                            }
                        }
                    }

                    // 2. Adaptive formats for audio / fallback video
                    if (adaptive != null) {
                        for (i in 0 until adaptive.length()) {
                            val a = adaptive.optJSONObject(i) ?: continue
                            val mime = a.optString("mimeType")
                            val u = a.optString("url")
                            if (u.isNotBlank()) {
                                if (mime.startsWith("audio/") && audioUrl == null) {
                                    audioUrl = u
                                }
                                if (videoUrl == null && mime.startsWith("video/")) {
                                    videoUrl = u
                                }
                            }
                        }
                    }

                    if (videoUrl != null) {
                        Log.d(TAG, "Successfully extracted YouTube media: $title from client: $clientName")
                        return YouTubeMediaResult(videoUrl, audioUrl, title)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube client $clientName failed: ${e.message}")
            }
        }

        // Method 2: Public Invidious API instance fallback
        val invidiousHosts = listOf(
            "https://invidious.f5.si",
            "https://inv.tux.pizza"
        )

        for (host in invidiousHosts) {
            try {
                val req = Request.Builder()
                    .url("$host/api/v1/videos/$videoId")
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val bodyStr = resp.body?.string() ?: return@use
                    val json = JSONObject(bodyStr)
                    val title = json.optString("title").ifBlank { "YouTube_Video" }

                    val fmts = json.optJSONArray("formatStreams")
                    var videoUrl: String? = null
                    if (fmts != null && fmts.length() > 0) {
                        videoUrl = fmts.optJSONObject(0)?.optString("url")
                    }

                    val adFmts = json.optJSONArray("adaptiveFormats")
                    var audioUrl: String? = null
                    if (adFmts != null) {
                        for (i in 0 until adFmts.length()) {
                            val af = adFmts.optJSONObject(i) ?: continue
                            val type = af.optString("type")
                            val u = af.optString("url")
                            if (type.startsWith("audio/") && u.isNotBlank()) {
                                audioUrl = u
                                break
                            }
                        }
                    }

                    val finalVideo = videoUrl
                    if (!finalVideo.isNullOrBlank()) {
                        Log.d(TAG, "Extracted from Invidious ($host): $title")
                        return YouTubeMediaResult(finalVideo, audioUrl, title)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invidious host $host failed: ${e.message}")
            }
        }

        return null
    }
}
