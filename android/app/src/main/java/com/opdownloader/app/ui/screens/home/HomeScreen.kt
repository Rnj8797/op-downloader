package com.opdownloader.app.ui.screens.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opdownloader.app.ui.components.*
import com.opdownloader.app.ui.theme.*

data class RecentDownloadItem(
    val id: String,
    val filename: String,
    val sizeText: String,
    val isVideo: Boolean,
    val dateText: String,
    val statusText: String = "✓ Saved to Gallery • Tap to Play",
    val filePath: String? = null
)

enum class LinkValidationState {
    IDLE,
    VALIDATING,
    VALID_DIRECT,
    VALID_AUTHORIZED,
    INVALID_UNSUPPORTED
}

@Composable
fun HomeScreen(
    initialUrl: String = "",
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowPreview: (url: String) -> Unit,
    onStartDownload: (url: String, quality: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var urlText by rememberSaveable { mutableStateOf(initialUrl) }
    var detectedProviderName by remember { mutableStateOf("") }
    var validationState by remember {
        mutableStateOf(
            if (initialUrl.isNotBlank()) {
                evaluateUrl(initialUrl) { detectedProviderName = it }
            } else LinkValidationState.IDLE
        )
    }
    var selectedQuality by rememberSaveable { mutableStateOf("1080p") }
    var activePlayingItem by remember { mutableStateOf<RecentDownloadItem?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        com.opdownloader.app.data.DownloadStateManager.initExistingDownloads(context)
    }

    // Real reactive recent downloads from DownloadStateManager
    val recentItems = com.opdownloader.app.data.DownloadStateManager.recentDownloads

    Scaffold(
        topBar = {
            OpTopBar(
                title = "OP Downloader",
                onSettingsClick = onNavigateToSettings
            )
        },
        containerColor = BaseBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Headline & Subtitle Hero
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Save your media",
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Paste a supported link to get started.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 2. Main Input Card with Smart Paste
            item {
                OpCard {
                    Text(
                        text = "Paste your link...",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Input Field Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        TextField(
                            value = urlText,
                            onValueChange = { newUrl ->
                                urlText = newUrl
                                validationState = evaluateUrl(newUrl) { provider ->
                                    detectedProviderName = provider
                                }
                            },
                            placeholder = {
                                Text(
                                    text = "https://example.com/media.mp4",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextTertiary)
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { keyboardController?.hide() }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Media URL Input Field" }
                        )

                        // Clear Button (visible if text present)
                        if (urlText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    urlText = ""
                                    validationState = LinkValidationState.IDLE
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .semantics { contentDescription = "Clear URL Text" }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Smart Paste Button
                        IconButton(
                            onClick = {
                                // Reads clipboard strictly on user-triggered tap and normalizes
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    val cleaned = cleanAndNormalizeUrl(clip)
                                    urlText = cleaned
                                    validationState = evaluateUrl(cleaned) { provider ->
                                        detectedProviderName = provider
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceBorder)
                                .semantics { contentDescription = "Paste Link from Clipboard" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Smart Validation Feedback Pill
                    AnimatedVisibility(visible = validationState != LinkValidationState.IDLE) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            when (validationState) {
                                LinkValidationState.VALID_DIRECT, LinkValidationState.VALID_AUTHORIZED -> {
                                    OpStatusBadge(
                                        statusText = "Link detected: $detectedProviderName",
                                        icon = Icons.Default.CheckCircle,
                                        color = StatusSuccess
                                    )
                                }
                                LinkValidationState.INVALID_UNSUPPORTED -> {
                                    OpStatusBadge(
                                        statusText = "This link isn't supported.",
                                        icon = Icons.Default.ErrorOutline,
                                        color = StatusError
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    // Quality Selection Chips (Visible when link is valid)
                    val isValid = validationState == LinkValidationState.VALID_DIRECT ||
                                  validationState == LinkValidationState.VALID_AUTHORIZED

                    AnimatedVisibility(visible = isValid) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text(
                                text = "Select Quality / Format:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Video qualities
                            val options = listOf(
                                "4k" to "4K Ultra",
                                "1080p" to "1080p FHD",
                                "720p" to "720p HD",
                                "480p" to "480p SD",
                                "audio" to "MP3 Audio"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                options.take(4).forEach { (id, label) ->
                                    val isSelected = selectedQuality == id
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) AccentPrimary.copy(alpha = 0.25f) else SurfaceElevated)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) AccentPrimary else SurfaceBorder,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedQuality = id }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = id.uppercase(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) TextPrimary else TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // MP3 Audio Row
                            val isAudioSelected = selectedQuality == "audio"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isAudioSelected) androidx.compose.ui.graphics.Color(0xFF10B981).copy(alpha = 0.2f) else SurfaceElevated)
                                    .border(
                                        width = if (isAudioSelected) 1.5.dp else 1.dp,
                                        color = if (isAudioSelected) androidx.compose.ui.graphics.Color(0xFF10B981) else SurfaceBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedQuality = "audio" }
                                    .padding(vertical = 7.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Audiotrack,
                                            contentDescription = null,
                                            tint = if (isAudioSelected) androidx.compose.ui.graphics.Color(0xFF10B981) else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Extract MP3 Audio",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isAudioSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isAudioSelected) TextPrimary else TextSecondary
                                            )
                                        )
                                    }
                                    Text(
                                        text = "320 kbps",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isAudioSelected) androidx.compose.ui.graphics.Color(0xFF10B981) else TextTertiary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Action CTA: DIRECT DOWNLOAD
                    val btnLabel = if (selectedQuality == "audio") "DOWNLOAD MP3 AUDIO" else "DOWNLOAD ${selectedQuality.uppercase()}"
                    OpButton(
                        text = btnLabel,
                        enabled = isValid,
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            keyboardController?.hide()
                            val cleaned = cleanAndNormalizeUrl(urlText)
                            onStartDownload(cleaned, selectedQuality)
                        }
                    )

                    // Secondary Preview Button
                    if (isValid) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = {
                                    keyboardController?.hide()
                                    val cleaned = cleanAndNormalizeUrl(urlText)
                                    onShowPreview(cleaned)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AccentPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Preview Details & Thumbnail",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = AccentPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 3. Supported / Authorized Disclaimer Badge
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Only user-owned or authorized public content can be saved.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            // 4. Recent Downloads Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Downloads",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 18.sp)
                    )
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AccentPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.clickable { onNavigateToDownloads() }
                    )
                }
            }

            // Recent Download Compact Cards or Empty State
            if (recentItems.isEmpty()) {
                item {
                    OpCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No downloads yet. Paste an Instagram Reel or YouTube link above.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
            } else {
                items(recentItems, key = { it.id }) { item ->
                    RecentDownloadCard(
                        item = item,
                        onPlayClick = {
                            val file = item.filePath?.let { java.io.File(it) }
                            if (file != null && file.exists() && file.length() > 0) {
                                activePlayingItem = item
                            } else {
                                android.widget.Toast.makeText(context, "Media file not found on device.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // In-App Video & Audio Player Dialog
        activePlayingItem?.let { item ->
            com.opdownloader.app.ui.components.OpVideoPlayerDialog(
                title = item.filename,
                filePath = item.filePath ?: "",
                isVideo = item.isVideo,
                onDismiss = { activePlayingItem = null }
            )
        }
    }
}

/**
 * Compact Recent Download Card Component
 */
@Composable
fun RecentDownloadCard(
    item: RecentDownloadItem,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
            .clickable { onPlayClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail or Type Icon Placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AccentPrimary.copy(alpha = 0.2f))
                .clickable { onPlayClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isVideo) Icons.Default.PlayCircleFilled else Icons.Default.Image,
                contentDescription = if (item.isVideo) "Play Video" else "View Image",
                tint = AccentPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // File Details
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onPlayClick() }
        ) {
            Text(
                text = item.filename,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.sizeText,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = StatusSuccess,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        // Quick Play Icon Button
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(AccentPrimary.copy(alpha = 0.2f))
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Video in App",
                tint = AccentPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Auto-cleans and normalizes URLs from clipboard or user input,
 * auto-fixing missing schemes or cut-off Instagram/YouTube prefixes.
 */
fun cleanAndNormalizeUrl(rawInput: String): String {
    var text = rawInput.trim().trim('\'', '"', '`')

    // Extract URL if user pasted surrounded text (e.g., "Check this out https://instagram.com/reel/...")
    val urlRegex = Regex("""https?://[^\s]+""")
    val match = urlRegex.find(text)
    if (match != null) {
        text = match.value
    }

    // Auto-fix Instagram URLs if domain was omitted
    if (text.startsWith("reel/", ignoreCase = true) || text.startsWith("/reel/", ignoreCase = true) ||
        text.startsWith("p/", ignoreCase = true) || text.startsWith("/p/", ignoreCase = true) ||
        text.startsWith("stories/", ignoreCase = true) || text.startsWith("/stories/", ignoreCase = true)) {
        text = "https://www.instagram.com/" + text.removePrefix("/")
    } else if (text.startsWith("instagram.com/", ignoreCase = true) || text.startsWith("www.instagram.com/", ignoreCase = true)) {
        text = "https://" + text
    } else if (text.startsWith("youtu.be/", ignoreCase = true) || text.startsWith("youtube.com/", ignoreCase = true) || text.startsWith("www.youtube.com/", ignoreCase = true)) {
        text = "https://" + text
    } else if (text.startsWith("facebook.com/", ignoreCase = true) || text.startsWith("fb.watch/", ignoreCase = true) || text.startsWith("www.facebook.com/", ignoreCase = true)) {
        text = "https://" + text
    } else if (text.startsWith("tiktok.com/", ignoreCase = true) || text.startsWith("www.tiktok.com/", ignoreCase = true)) {
        text = "https://" + text
    } else if (text.startsWith("x.com/", ignoreCase = true) || text.startsWith("twitter.com/", ignoreCase = true)) {
        text = "https://" + text
    }

    return text
}

/**
 * Client-Side Immediate URL Syntax & Multi-Platform Provider Detection Helper
 */
private fun evaluateUrl(
    url: String,
    onProviderDetected: (String) -> Unit
): LinkValidationState {
    if (url.isBlank()) return LinkValidationState.IDLE

    val normalized = cleanAndNormalizeUrl(url)
    val lower = normalized.lowercase()

    // Reject dangerous schemes or private addresses immediately
    if (lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("169.254")) {
        return LinkValidationState.INVALID_UNSUPPORTED
    }

    return when {
        lower.contains("instagram.com") || lower.contains("instagr.am") -> {
            onProviderDetected("Instagram Reel / Post")
            LinkValidationState.VALID_AUTHORIZED
        }
        lower.contains("youtube.com") || lower.contains("youtu.be") -> {
            onProviderDetected("YouTube Video / Short")
            LinkValidationState.VALID_AUTHORIZED
        }
        lower.contains("facebook.com") || lower.contains("fb.watch") -> {
            onProviderDetected("Facebook Video / Reel")
            LinkValidationState.VALID_AUTHORIZED
        }
        lower.contains("tiktok.com") -> {
            onProviderDetected("TikTok Video")
            LinkValidationState.VALID_AUTHORIZED
        }
        lower.contains("twitter.com") || lower.contains("x.com") -> {
            onProviderDetected("X / Twitter Media")
            LinkValidationState.VALID_AUTHORIZED
        }
        lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") || lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.contains("commondatastorage") -> {
            onProviderDetected("Direct Media")
            LinkValidationState.VALID_DIRECT
        }
        lower.contains("unsplash.com") || lower.contains("archive.org") || lower.contains("wikimedia.org") || lower.contains("pexels.com") -> {
            onProviderDetected("Authorized Public Provider")
            LinkValidationState.VALID_AUTHORIZED
        }
        normalized.startsWith("https://") -> {
            onProviderDetected("Online Video / Media")
            LinkValidationState.VALID_AUTHORIZED
        }
        else -> {
            LinkValidationState.INVALID_UNSUPPORTED
        }
    }
}
