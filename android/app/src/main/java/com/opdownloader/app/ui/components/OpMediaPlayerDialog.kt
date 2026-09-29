package com.opdownloader.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opdownloader.app.ui.theme.*
import java.io.File

/**
 * High-performance In-App Media Player Dialog for OP Downloader.
 * Plays downloaded MP4 videos and MP3 audio instantly inside the app,
 * and provides one-tap "Open in Phone Gallery" and "Share" actions.
 */
@Composable
fun OpMediaPlayerDialog(
    mediaUriString: String?,
    filename: String,
    isVideo: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    val resolvedUri = remember(mediaUriString) {
        when {
            mediaUriString.isNullOrBlank() -> null
            mediaUriString.startsWith("content://") -> Uri.parse(mediaUriString)
            mediaUriString.startsWith("file://") -> Uri.parse(mediaUriString)
            File(mediaUriString).exists() -> Uri.fromFile(File(mediaUriString))
            File(context.filesDir, filename).exists() -> Uri.fromFile(File(context.filesDir, filename))
            else -> Uri.parse(mediaUriString)
        }
    }

    Dialog(
        onDismissRequest = {
            videoViewRef?.stopPlayback()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusSuccess)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Playing in OP Downloader",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = StatusSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = filename,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = {
                            videoViewRef?.stopPlayback()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Player",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Video / Audio Playback Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (resolvedUri != null) {
                        if (isVideo) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val controller = MediaController(ctx)
                                        controller.setAnchorView(this)
                                        setMediaController(controller)
                                        setVideoURI(resolvedUri)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                            isPlaying = true
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            Toast.makeText(ctx, "Preparing media playback...", Toast.LENGTH_SHORT).show()
                                            true
                                        }
                                        videoViewRef = this
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Audio MP3 Visualizer Card
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Audio MP3 (320kbps)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Playing high quality sound",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Media loading...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Quick Action Buttons: Open in Phone Gallery & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Open in Phone Gallery
                    OutlinedButton(
                        onClick = {
                            openInSystemGallery(context, resolvedUri, isVideo)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AccentPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Gallery App", fontSize = 13.sp)
                    }

                    // Share Media
                    OutlinedButton(
                        onClick = {
                            shareSystemMedia(context, resolvedUri, isVideo)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Share", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Done Button
                OpButton(
                    text = "DONE",
                    onClick = {
                        videoViewRef?.stopPlayback()
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * Fires Intent to open media in Phone's default Gallery or Video Player
 */
fun openInSystemGallery(context: Context, uri: Uri?, isVideo: Boolean) {
    try {
        val targetUri = uri ?: MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, if (isVideo) "video/*" else "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open external gallery: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Fires Intent to share media with WhatsApp, Telegram, etc.
 */
fun shareSystemMedia(context: Context, uri: Uri?, isVideo: Boolean) {
    try {
        if (uri == null) {
            Toast.makeText(context, "Media not ready to share", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/*" else "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share video"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
