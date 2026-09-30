package com.opdownloader.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.opdownloader.app.data.DownloadStateManager
import com.opdownloader.app.ui.navigation.OpNavGraph
import com.opdownloader.app.ui.theme.OpDownloaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUrl = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }

        setContent {
            OpDownloaderTheme {
                OpNavGraph(initialSharedUrl = sharedUrl)
            }
        }
    }
}

class DownloadTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: "https://www.instagram.com/reel/C-0KqY1y5mC/"
        val quality = intent.getStringExtra("quality") ?: "1080p"
        DownloadStateManager.startDownload(
            context = context.applicationContext,
            url = url,
            qualityId = quality,
            platformTitle = "Instagram Reel",
            isVideo = true,
            sizeText = "68 MB"
        )
    }
}
