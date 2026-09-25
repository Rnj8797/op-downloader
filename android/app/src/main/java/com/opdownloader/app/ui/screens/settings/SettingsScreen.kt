package com.opdownloader.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opdownloader.app.ui.components.OpCard
import com.opdownloader.app.ui.theme.*

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    // Setting States
    var defaultQuality by remember { mutableStateOf("Original") }
    var autoSaveToGallery by remember { mutableStateOf(true) }
    var wifiOnly by remember { mutableStateOf(false) }
    var concurrentDownloads by remember { mutableIntStateOf(2) }
    var autoRetry by remember { mutableStateOf(true) }
    var selectedTheme by remember { mutableStateOf("Dark") }
    var enableAnimations by remember { mutableStateOf(true) }
    var appLockEnabled by remember { mutableStateOf(false) }
    var showCacheClearedToast by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp)
                )
            }
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
            // -------------------------------------------------------------
            // SECTION: DOWNLOAD
            // -------------------------------------------------------------
            item {
                SettingsSectionHeader(title = "DOWNLOAD")
                Spacer(modifier = Modifier.height(8.dp))
                OpCard {
                    SettingsDropdownItem(
                        icon = Icons.Outlined.HighQuality,
                        title = "Default Quality",
                        subtitle = defaultQuality,
                        options = listOf("Original", "Best available", "Ask every time"),
                        onSelect = { defaultQuality = it }
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsInfoItem(
                        icon = Icons.Outlined.Folder,
                        title = "Save Location",
                        subtitle = "Movies/ & Pictures/ OP Downloader"
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = "Auto-save to Gallery",
                        subtitle = "Instantly register completed media in device Gallery",
                        checked = autoSaveToGallery,
                        onCheckedChange = { autoSaveToGallery = it }
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Wifi,
                        title = "Wi-Fi Only",
                        subtitle = "Avoid downloading large files over mobile data",
                        checked = wifiOnly,
                        onCheckedChange = { wifiOnly = it }
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsDropdownItem(
                        icon = Icons.Outlined.SyncAlt,
                        title = "Concurrent Downloads",
                        subtitle = "$concurrentDownloads active tasks",
                        options = listOf("1", "2", "3"),
                        onSelect = { concurrentDownloads = it.toInt() }
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Replay,
                        title = "Auto Retry",
                        subtitle = "Automatically resume on temporary network drops",
                        checked = autoRetry,
                        onCheckedChange = { autoRetry = it }
                    )
                }
            }

            // -------------------------------------------------------------
            // SECTION: APPEARANCE
            // -------------------------------------------------------------
            item {
                SettingsSectionHeader(title = "APPEARANCE")
                Spacer(modifier = Modifier.height(8.dp))
                OpCard {
                    SettingsDropdownItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "Theme",
                        subtitle = selectedTheme,
                        options = listOf("Dark", "System", "Light"),
                        onSelect = { selectedTheme = it }
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Animation,
                        title = "Micro-Animations",
                        subtitle = "Enable smooth progress and transition effects",
                        checked = enableAnimations,
                        onCheckedChange = { enableAnimations = it }
                    )
                }
            }

            // -------------------------------------------------------------
            // SECTION: SECURITY
            // -------------------------------------------------------------
            item {
                SettingsSectionHeader(title = "SECURITY")
                Spacer(modifier = Modifier.height(8.dp))
                OpCard {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Fingerprint,
                        title = "App Lock",
                        subtitle = "Require Biometric or PIN authentication upon open",
                        checked = appLockEnabled,
                        onCheckedChange = { appLockEnabled = it }
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsActionItem(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Clear Temporary Files",
                        subtitle = "Purge cached download chunks and temporary files",
                        onClick = { showCacheClearedToast = true }
                    )
                }
            }

            // -------------------------------------------------------------
            // SECTION: ABOUT
            // -------------------------------------------------------------
            item {
                SettingsSectionHeader(title = "ABOUT")
                Spacer(modifier = Modifier.height(8.dp))
                OpCard {
                    SettingsInfoItem(
                        icon = Icons.Outlined.Info,
                        title = "Version",
                        subtitle = "1.0.0 (Production Release)"
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsActionItem(
                        icon = Icons.Outlined.Policy,
                        title = "Privacy Policy",
                        subtitle = "Zero tracking, minimum permissions, no DRM bypass",
                        onClick = {}
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsActionItem(
                        icon = Icons.Outlined.Gavel,
                        title = "Terms of Service",
                        subtitle = "Authorized public and user-owned content guidelines",
                        onClick = {}
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsActionItem(
                        icon = Icons.Outlined.Code,
                        title = "Open-Source Licenses",
                        subtitle = "View software license notices",
                        onClick = {}
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = AccentPrimary,
            letterSpacing = 1.sp
        )
    )
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = AccentPrimary,
                uncheckedTrackColor = SurfaceElevated
            )
        )
    }
}

@Composable
fun SettingsDropdownItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(color = AccentPrimary, fontWeight = FontWeight.SemiBold)
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = TextSecondary
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceElevated)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option, color = TextPrimary) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextTertiary
        )
    }
}

@Composable
fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
        }
    }
}
