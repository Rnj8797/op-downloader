package com.opdownloader.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

data class QualityOption(
    val id: String,
    val label: String,
    val sizeLabel: String
)

data class MediaPreviewData(
    val title: String,
    val provider: String,
    val mediaType: String, // "Video" | "Photo"
    val durationText: String? = null,
    val sizeText: String? = null,
    val availableQualities: List<QualityOption> = listOf(
        QualityOption("original", "Original", "82 MB"),
        QualityOption("1080p", "1080p", "54 MB"),
        QualityOption("720p", "720p", "31 MB"),
        QualityOption("480p", "480p", "18 MB")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewBottomSheet(
    previewData: MediaPreviewData,
    onDismiss: () -> Unit,
    onConfirmDownload: (selectedQuality: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedQualityId by remember { mutableStateOf(previewData.availableQualities.firstOrNull()?.id ?: "original") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceBorder)
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close preview",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Thumbnail / Visual Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (previewData.mediaType == "Video") Icons.Default.PlayCircleFilled else Icons.Default.Image,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = previewData.provider,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Duration badge if video
                if (previewData.durationText != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = previewData.durationText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title & Meta Info
            Text(
                text = previewData.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = previewData.mediaType,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                if (previewData.durationText != null) {
                    Text(text = " • ", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = previewData.durationText,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quality Selection Chips
            Text(
                text = "Select Download Quality:",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Video Qualities Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val videoQualities = previewData.availableQualities.filter { it.id != "audio" }
                val audioOption = previewData.availableQualities.find { it.id == "audio" }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    videoQualities.forEach { option ->
                        val isSelected = selectedQualityId == option.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AccentPrimary.copy(alpha = 0.2f) else SurfaceElevated)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) AccentPrimary else SurfaceBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable(role = Role.RadioButton) {
                                    selectedQualityId = option.id
                                }
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = option.sizeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = if (isSelected) AccentPrimary else TextTertiary
                                    )
                                )
                            }
                        }
                    }
                }

                // Audio Extract Option (MP3)
                if (audioOption != null) {
                    val isAudioSelected = selectedQualityId == audioOption.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isAudioSelected) Color(0xFF10B981).copy(alpha = 0.2f) else SurfaceElevated)
                            .border(
                                width = if (isAudioSelected) 1.5.dp else 1.dp,
                                color = if (isAudioSelected) Color(0xFF10B981) else SurfaceBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(role = Role.RadioButton) {
                                selectedQualityId = audioOption.id
                            }
                            .padding(vertical = 10.dp, horizontal = 14.dp),
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
                                    tint = if (isAudioSelected) Color(0xFF10B981) else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = audioOption.label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isAudioSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isAudioSelected) TextPrimary else TextSecondary
                                    )
                                )
                            }
                            Text(
                                text = audioOption.sizeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isAudioSelected) Color(0xFF10B981) else TextTertiary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Download Button
            OpButton(
                text = "START DOWNLOAD",
                icon = Icons.Default.Download,
                onClick = { onConfirmDownload(selectedQualityId) }
            )
        }
    }
}
