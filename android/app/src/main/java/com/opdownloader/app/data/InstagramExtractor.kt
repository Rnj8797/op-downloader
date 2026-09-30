package com.opdownloader.app.data

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object InstagramExtractor {
    private const val TAG = "InstagramExtractor"

    data class InstagramMediaResult(
        val videoUrl: String,
        val title: String
    )

    fun isInstagramUrl(rawUrl: String): Boolean {
        val lower = rawUrl.lowercase()
        return lower.contains("instagram.com") || lower.contains("instagr.am")
    }

    fun extractShortcode(rawUrl: String): String? {
        return try {
            val clean = rawUrl.substringBefore("?").substringBefore("#").trimEnd('/')
            when {
                clean.contains("/reel/") -> clean.substringAfter("/reel/").substringBefore("/")
                clean.contains("/reels/") -> clean.substringAfter("/reels/").substringBefore("/")
                clean.contains("/p/") -> clean.substringAfter("/p/").substringBefore("/")
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extractMedia(client: OkHttpClient, shortcode: String): InstagramMediaResult? {
        // Method 1: Instagram internal GraphQL API
        try {
            val formBody = FormBody.Builder()
                .add("variables", JSONObject().put("shortcode", shortcode).toString())
                .add("doc_id", "10015901848480474")
                .add("lsd", "AVqbxe3J_YA")
                .build()

            val request = Request.Builder()
                .url("https://www.instagram.com/api/graphql")
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
                .addHeader("X-FB-LSD", "AVqbxe3J_YA")
                .addHeader("X-IG-App-ID", "1217981644879628")
                .addHeader("Accept", "*/*")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@use
                    val json = JSONObject(bodyStr)
                    val data = json.optJSONObject("data")
                    val media = data?.optJSONObject("xdt_shortcode_media")
                    val videoUrl = media?.optString("video_url")
                    if (!videoUrl.isNullOrBlank()) {
                        Log.d(TAG, "Successfully extracted Instagram Reel via GraphQL: $shortcode")
                        return InstagramMediaResult(videoUrl, "Instagram_Reel_$shortcode")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Instagram GraphQL method failed: ${e.message}")
        }

        // Method 2: Public embed page inspection
        try {
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
            val request = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: return@use
                    val match = Regex(""""video_url"\s*:\s*"([^"]+)"""").find(html)
                    if (match != null) {
                        val videoUrl = match.groupValues[1]
                            .replace("\\u0026", "&")
                            .replace("&amp;", "&")
                            .replace("\\/", "/")
                        Log.d(TAG, "Successfully extracted Instagram media via embed: $shortcode")
                        return InstagramMediaResult(videoUrl, "Instagram_Reel_$shortcode")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Instagram Embed method failed: ${e.message}")
        }

        return null
    }
}
