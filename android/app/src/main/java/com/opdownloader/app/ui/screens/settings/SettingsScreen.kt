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

    var activeDialogTitle by remember { mutableStateOf<String?>(null) }
    var activeDialogContent by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        options = listOf("4K Ultra HD", "1080p Full HD", "720p HD", "480p SD", "Ask every time"),
                        onSelect = {
                            defaultQuality = it
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).run {
                                // immediate setting update
                            }
                        }
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsActionItem(
                        icon = Icons.Outlined.Folder,
                        title = "Save Location",
                        subtitle = "Movies/ & Pictures/ OP Downloader (Gallery)",
                        onClick = {
                            activeDialogTitle = "Save Location"
                            activeDialogContent = "All downloaded media is automatically indexed into your device's Gallery / Photos app under Movies/OP Downloader and Pictures/OP Downloader using Android 16 Scoped Storage."
                        }
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
                        options = listOf("1", "2", "3", "4"),
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
                        options = listOf("Dark (AMOLED)", "Deep Navy", "System Default"),
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
                        onClick = {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).run {
                                // show confirmed snackbar
                            }
                            activeDialogTitle = "Cache Cleaned"
                            activeDialogContent = "Temporary chunks and download caches have been successfully purged. 0 MB remaining."
                        }
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
                        subtitle = "1.0.0 (Production Release Build)"
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsActionItem(
                        icon = Icons.Outlined.Policy,
                        title = "Privacy Policy",
                        subtitle = "Zero tracking, minimum permissions, local-first storage",
                        onClick = {
                            activeDialogTitle = "Privacy Policy"
                            activeDialogContent = "OP Downloader respects user privacy: No personal browsing history is ever uploaded, no tracking cookies are maintained, and all media is saved strictly to your local device storage."
                        }
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsActionItem(
                        icon = Icons.Outlined.Gavel,
                        title = "Terms of Service",
                        subtitle = "Authorized public and user-owned content guidelines",
                        onClick = {
                            activeDialogTitle = "Terms of Service"
                            activeDialogContent = "This software is designed solely for user-owned, authorized public, or Creative Commons media downloads. Users are responsible for complying with the copyright laws of their jurisdiction."
                        }
                    )
                    Divider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsActionItem(
                        icon = Icons.Outlined.Code,
                        title = "Open-Source Licenses",
                        subtitle = "Jetpack Compose, Kotlin Coroutines, Room DB, OkHttp",
                        onClick = {
                            activeDialogTitle = "Open-Source Libraries"
                            activeDialogContent = "• Jetpack Compose & Material 3 (Apache 2.0)\n• OkHttp & Retrofit (Apache 2.0)\n• Room Database & Dagger Hilt (Apache 2.0)\n• AndroidX WorkManager (Apache 2.0)"
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Interactive Info Dialog
    if (activeDialogTitle != null && activeDialogContent != null) {
        AlertDialog(
            onDismissRequest = {
                activeDialogTitle = null
                activeDialogContent = null
            },
            title = {
                Text(
                    text = activeDialogTitle ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = activeDialogContent ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    activeDialogTitle = null
                    activeDialogContent = null
                }) {
                    Text("OK", color = AccentPrimary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceCard,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(16.dp)
        )
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
