package com.opdownloader.app.ui.screens.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opdownloader.app.ui.components.*
import com.opdownloader.app.ui.theme.*

enum class DownloadTab {
    ALL, VIDEOS, PHOTOS
}

enum class ItemStatus {
    DOWNLOADING, PAUSED, COMPLETED, FAILED
}

data class DownloadTask(
    val id: String,
    val filename: String,
    val isVideo: Boolean,
    val status: ItemStatus,
    val progress: Float, // 0.0f to 1.0f
    val downloadedBytesText: String,
    val totalBytesText: String,
    val speedText: String? = null,
    val etaText: String? = null,
    val dateText: String
)

@Composable
fun DownloadsScreen(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(DownloadTab.ALL) }

    val tasks = com.opdownloader.app.data.DownloadStateManager.tasks

    val filteredTasks = remember(tasks.toList(), selectedTab) {
        when (selectedTab) {
            DownloadTab.ALL -> tasks
            DownloadTab.VIDEOS -> tasks.filter { it.isVideo }
            DownloadTab.PHOTOS -> tasks.filter { !it.isVideo }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        },
        containerColor = BaseBackground
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            // Filter Tabs: All, Videos, Photos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DownloadTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AccentPrimary else Color.Transparent)
                            .clickable(role = Role.Tab) { selectedTab = tab },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Task List or Empty State
            if (filteredTasks.isEmpty()) {
                DownloadsEmptyState(onGoHome = onNavigateToHome)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        if (task.status == ItemStatus.DOWNLOADING || task.status == ItemStatus.PAUSED) {
                            ActiveDownloadCard(
                                task = task,
                                onPauseToggle = {
                                    if (task.status == ItemStatus.DOWNLOADING) {
                                        com.opdownloader.app.data.DownloadStateManager.pauseDownload(task.id)
                                    } else {
                                        com.opdownloader.app.data.DownloadStateManager.resumeDownload(task.id)
                                    }
                                },
                                onCancel = {
                                    com.opdownloader.app.data.DownloadStateManager.cancelDownload(task.id)
                                }
                            )
                        } else {
                            CompletedDownloadCard(
                                task = task,
                                onDeleteHistory = {
                                    com.opdownloader.app.data.DownloadStateManager.cancelDownload(task.id)
                                }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(30.dp)) }
                }
            }
        }
    }
}

/**
 * Active Downloading Card with Live Progress Bar, Speed, ETA & Controls
 */
@Composable
fun ActiveDownloadCard(
    task: DownloadTask,
    onPauseToggle: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    OpCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (task.isVideo) Icons.Default.Movie else Icons.Default.Image,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.filename,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (task.status == ItemStatus.DOWNLOADING) "Downloading..." else "Paused",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (task.status == ItemStatus.DOWNLOADING) AccentPrimary else StatusWarning,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // Percentage Display
            Text(
                text = "${(task.progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress Bar
        OpProgressBar(progress = task.progress)

        Spacer(modifier = Modifier.height(10.dp))

        // Download Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${task.downloadedBytesText} / ${task.totalBytesText}",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
            if (task.speedText != null && task.etaText != null && task.status == ItemStatus.DOWNLOADING) {
                Text(
                    text = "${task.speedText} • ${task.etaText}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Control Actions: PAUSE and CANCEL
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPauseToggle,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                Icon(
                    imageVector = if (task.status == ItemStatus.DOWNLOADING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = if (task.status == ItemStatus.DOWNLOADING) "PAUSE" else "RESUME")
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "CANCEL")
            }
        }
    }
}

/**
 * Completed Download Item Card with Quick Actions (Open, Share, Delete)
 */
@Composable
fun CompletedDownloadCard(
    task: DownloadTask,
    onDeleteHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    OpCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (task.isVideo) Icons.Default.Movie else Icons.Default.Image,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.filename,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.totalBytesText,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(text = " • ", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = task.dateText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                OpStatusBadge(
                    statusText = "✓ Saved to Gallery",
                    icon = Icons.Default.CheckCircle,
                    color = StatusSuccess
                )
            }

            // More Options Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.semantics { contentDescription = "Item Actions" }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SurfaceElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Open in Gallery", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = TextPrimary) },
                        onClick = { showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Share", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary) },
                        onClick = { showMenu = false }
                    )
                    Divider(color = SurfaceBorder)
                    DropdownMenuItem(
                        text = { Text("Remove from history", color = StatusError) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = StatusError) },
                        onClick = {
                            showMenu = false
                            onDeleteHistory()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Empty State for Downloads Screen
 */
@Composable
fun DownloadsEmptyState(
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No downloads yet",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Paste a supported link on Home to get started.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        OpButton(
            text = "Go to Home",
            onClick = onGoHome,
            modifier = Modifier.width(180.dp)
        )
    }
}
