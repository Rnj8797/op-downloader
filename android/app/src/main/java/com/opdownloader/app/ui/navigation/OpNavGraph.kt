package com.opdownloader.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.opdownloader.app.ui.components.OpBottomNav
import com.opdownloader.app.ui.screens.downloads.DownloadsScreen
import com.opdownloader.app.ui.screens.home.HomeScreen
import com.opdownloader.app.ui.screens.home.MediaPreviewData
import com.opdownloader.app.ui.screens.home.PreviewBottomSheet
import com.opdownloader.app.ui.screens.settings.SettingsScreen
import com.opdownloader.app.ui.screens.splash.SplashScreen
import com.opdownloader.app.ui.theme.BaseBackground

@Composable
fun OpNavGraph(
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf("splash") }
    var activePreviewUrl by remember { mutableStateOf<String?>(null) }

    if (currentScreen == "splash") {
        SplashScreen(
            onSplashFinished = { currentScreen = "home" },
            modifier = modifier
        )
    } else {
        Scaffold(
            containerColor = BaseBackground,
            bottomBar = {
                OpBottomNav(
                    currentRoute = currentScreen,
                    onNavigate = { route -> currentScreen = route }
                )
            },
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            val screenModifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)

            when (currentScreen) {
                "home" -> HomeScreen(
                    onNavigateToDownloads = { currentScreen = "downloads" },
                    onNavigateToSettings = { currentScreen = "settings" },
                    onShowPreview = { url -> activePreviewUrl = url },
                    modifier = screenModifier
                )
                "downloads" -> DownloadsScreen(
                    onNavigateToHome = { currentScreen = "home" },
                    modifier = screenModifier
                )
                "settings" -> SettingsScreen(
                    modifier = screenModifier
                )
            }

            // Preview Bottom Sheet Modal
            activePreviewUrl?.let { url ->
                val lower = url.lowercase()
                val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")

                val (platformName, mediaTitle, duration) = when {
                    lower.contains("instagram.com") || lower.contains("instagr.am") ->
                        Triple("Instagram Reel", "Instagram HD Reel Video", "00:45")
                    lower.contains("youtube.com") || lower.contains("youtu.be") ->
                        Triple("YouTube Media", "YouTube High-Definition Video", "04:12")
                    lower.contains("facebook.com") || lower.contains("fb.watch") ->
                        Triple("Facebook Watch", "Facebook Viral Reel Video", "01:20")
                    lower.contains("tiktok.com") ->
                        Triple("TikTok Media", "TikTok Trending Video", "00:30")
                    lower.contains("twitter.com") || lower.contains("x.com") ->
                        Triple("X / Twitter", "X Video Post", "01:05")
                    else ->
                        Triple("Direct Media", url.substringAfterLast("/").substringBefore("?").ifBlank { "Media_Download" }, "02:30")
                }

                val qualities = if (isImage) {
                    listOf(
                        com.opdownloader.app.ui.screens.home.QualityOption("original", "Original HD", "4.2 MB"),
                        com.opdownloader.app.ui.screens.home.QualityOption("compressed", "Compressed", "1.1 MB")
                    )
                } else {
                    listOf(
                        com.opdownloader.app.ui.screens.home.QualityOption("4k", "4K Ultra", "184 MB"),
                        com.opdownloader.app.ui.screens.home.QualityOption("1080p", "1080p FHD", "68 MB"),
                        com.opdownloader.app.ui.screens.home.QualityOption("720p", "720p HD", "35 MB"),
                        com.opdownloader.app.ui.screens.home.QualityOption("480p", "480p SD", "18 MB"),
                        com.opdownloader.app.ui.screens.home.QualityOption("audio", "Audio MP3 (320kbps)", "6.4 MB")
                    )
                }

                val previewData = MediaPreviewData(
                    title = mediaTitle,
                    provider = platformName,
                    mediaType = if (isImage) "Photo" else "Video",
                    durationText = if (isImage) null else duration,
                    sizeText = if (isImage) "4.2 MB" else "68 MB",
                    availableQualities = qualities
                )

                PreviewBottomSheet(
                    previewData = previewData,
                    onDismiss = { activePreviewUrl = null },
                    onConfirmDownload = { quality ->
                        activePreviewUrl = null
                        // Switches to Downloads tab to display live progress
                        currentScreen = "downloads"
                    }
                )
            }
        }
    }
}
