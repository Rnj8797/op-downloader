package com.opdownloader.app.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object TikTokExtractor {
    private const val TAG = "TikTokExtractor"

    data class TikTokMediaResult(
        val videoUrl: String,
        val audioUrl: String?,
        val title: String
    )

    fun isTikTokUrl(rawUrl: String): Boolean {
        val lower = rawUrl.lowercase()
        return lower.contains("tiktok.com")
    }

    suspend fun extractMedia(client: OkHttpClient, rawUrl: String): TikTokMediaResult? {
        return try {
            val encoded = URLEncoder.encode(rawUrl, "UTF-8")
            val apiUrl = "https://www.tikwm.com/api/?url=$encoded"
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                if (json.optInt("code", -1) != 0) return null

                val data = json.optJSONObject("data") ?: return null
                val videoUrl = data.optString("play").ifBlank { data.optString("wmplay") }
                val audioUrl = data.optString("music").ifBlank { null }
                val title = data.optString("title").ifBlank { "TikTok_Video" }

                if (videoUrl.isNotBlank()) {
                    Log.d(TAG, "Successfully extracted TikTok video: $title")
                    TikTokMediaResult(videoUrl, audioUrl, title)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TikTok extraction failed: ${e.message}")
            null
        }
    }
}
