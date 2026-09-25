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
                val previewData = MediaPreviewData(
                    title = if (url.contains(".mp4") || url.contains("sample")) "Sample Authorized Video" else "High-Res Authorized Image",
                    provider = if (url.contains("unsplash")) "Unsplash Open Photos" else "Direct Public Stream",
                    mediaType = if (url.contains(".jpg") || url.contains(".png")) "Photo" else "Video",
                    durationText = if (url.contains(".jpg")) null else "02:31",
                    sizeText = "82 MB"
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
