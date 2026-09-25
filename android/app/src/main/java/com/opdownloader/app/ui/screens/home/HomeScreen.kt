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
    val statusText: String = "✓ Saved to Gallery"
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
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowPreview: (url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var urlText by remember { mutableStateOf("") }
    var validationState by remember { mutableStateOf(LinkValidationState.IDLE) }
    var detectedProviderName by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Sample recent downloads for clean display
    val recentItems = remember {
        listOf(
            RecentDownloadItem("1", "Sample_Video_01.mp4", "82 MB", true, "Today, 14:20"),
            RecentDownloadItem("2", "Nature_Photo_04.jpg", "4.2 MB", false, "Yesterday")
        )
    }

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
                                // Reads clipboard strictly on user-triggered tap
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    urlText = clip.trim()
                                    validationState = evaluateUrl(clip.trim()) { provider ->
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Action CTA: DOWNLOAD
                    OpButton(
                        text = "DOWNLOAD",
                        enabled = validationState == LinkValidationState.VALID_DIRECT ||
                                  validationState == LinkValidationState.VALID_AUTHORIZED,
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            keyboardController?.hide()
                            onShowPreview(urlText)
                        }
                    )
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

            // Recent Download Compact Cards
            items(recentItems) { item ->
                RecentDownloadCard(item = item)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Compact Recent Download Card Component
 */
@Composable
fun RecentDownloadCard(
    item: RecentDownloadItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail or Type Icon Placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isVideo) Icons.Default.Movie else Icons.Default.Image,
                contentDescription = if (item.isVideo) "Video file" else "Image file",
                tint = AccentPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // File Details
        Column(modifier = Modifier.weight(1f)) {
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
    }
}

/**
 * Client-Side Immediate URL Syntax & Provider Detection Helper
 */
private fun evaluateUrl(
    url: String,
    onProviderDetected: (String) -> Unit
): LinkValidationState {
    if (url.isBlank()) return LinkValidationState.IDLE

    val trimmed = url.trim()
    val isHttps = trimmed.startsWith("https://", ignoreCase = true)
    if (!isHttps) return LinkValidationState.INVALID_UNSUPPORTED

    // Reject dangerous schemes or private addresses immediately
    val lower = trimmed.lowercase()
    if (lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("169.254")) {
        return LinkValidationState.INVALID_UNSUPPORTED
    }

    return when {
        lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.contains("commondatastorage") -> {
            onProviderDetected("Direct Media")
            LinkValidationState.VALID_DIRECT
        }
        lower.contains("unsplash.com") || lower.contains("archive.org") || lower.contains("wikimedia.org") -> {
            onProviderDetected("Authorized Public Provider")
            LinkValidationState.VALID_AUTHORIZED
        }
        else -> {
            LinkValidationState.INVALID_UNSUPPORTED
        }
    }
}
