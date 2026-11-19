package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.model.AppFontSize
import com.example.model.AppOperatingMode
import com.example.model.ThemeDesign
import com.example.model.ThemeMode
import com.example.protocol.GitHubLoaderRepositorySource
import com.example.security.LicenseManager
import com.example.ui.ServiceUiState
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.resolvePalette
import com.example.viewmodel.MtkBridgeViewModel

@Composable
fun SettingsScreen(
    uiState: ServiceUiState,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectThemeDesign: (ThemeDesign) -> Unit,
    onSelectFontSize: (AppFontSize) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    mtkViewModel: MtkBridgeViewModel? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val licenseSession by LicenseManager.sessionState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.screenBg)
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section 0: License Status (Compact)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = palette.cardBg,
            border = BorderStroke(
                1.2.dp,
                if (licenseSession.isUnlocked) Color(0xFF10B981) else Color(0xFFEF4444)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setting_license_security_card")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "License Security",
                            tint = if (licenseSession.isUnlocked) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "License",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (licenseSession.isUnlocked) Color(0xFF10B981).copy(alpha = 0.16f)
                        else Color(0xFFEF4444).copy(alpha = 0.16f),
                        border = BorderStroke(
                            1.dp,
                            if (licenseSession.isUnlocked) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    ) {
                        Text(
                            text = if (licenseSession.isUnlocked) "UNLOCKED" else "LOCKED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (licenseSession.isUnlocked) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = buildString {
                        if (licenseSession.username.isNotEmpty()) {
                            append("${licenseSession.username} • ")
                        }
                        append("HWID: ${licenseSession.deviceHwid}")
                        if (licenseSession.activeKeyCode.isNotEmpty()) {
                            append(" • ${licenseSession.activeKeyCode}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (BuildConfig.DEBUG) {
                    HorizontalDivider(color = palette.divider)
                    AdminMasterKeyPanel()
                }
            }
        }

        // Section 1: Theme Mode (Dark / Light)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = palette.cardBg,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (uiState.themeMode == ThemeMode.DARK) "Dark" else "Light",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentPrimary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeModeOptionCard(
                        title = "Dark Mode",
                        icon = Icons.Default.DarkMode,
                        selected = uiState.themeMode == ThemeMode.DARK,
                        onClick = { onSelectThemeMode(ThemeMode.DARK) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("setting_theme_dark")
                    )
                    ThemeModeOptionCard(
                        title = "Light Mode",
                        icon = Icons.Default.LightMode,
                        selected = uiState.themeMode == ThemeMode.LIGHT,
                        onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("setting_theme_light")
                    )
                }
            }
        }

        // Section 2: Theme Color Design
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = palette.cardBg,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Theme Design",
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Theme Color",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeDesign.entries.forEach { design ->
                        val previewPalette = resolvePalette(uiState.themeMode, design)
                        val isSelected = uiState.themeDesign == design

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) palette.cardAltBg else palette.screenBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    brush = if (isSelected) {
                                        previewPalette.panelBorderBrush
                                    } else {
                                        Brush.horizontalGradient(
                                            listOf(palette.cardBorder, palette.cardBorder)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectThemeDesign(design) }
                                .testTag("theme_design_${design.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ColorSwatchDot(previewPalette.accentPrimary)
                                    ColorSwatchDot(previewPalette.accentSecondary)
                                    ColorSwatchDot(previewPalette.startButtonBg)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = design.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary
                                    )
                                }

                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = previewPalette.accentPrimary.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, previewPalette.accentPrimary),
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = previewPalette.accentPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Font Size
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = palette.cardBg,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatSize,
                        contentDescription = "Font Size",
                        tint = palette.accentSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Font Size",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppFontSize.entries.forEach { sizeOption ->
                        val selected = uiState.fontSize == sizeOption
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) palette.accentPrimary.copy(alpha = 0.16f) else palette.cardAltBg,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) palette.accentPrimary else palette.cardBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectFontSize(sizeOption) }
                                .testTag("font_size_${sizeOption.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = sizeOption.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selected) palette.accentPrimary else palette.textPrimary
                                )
                                Text(
                                    text = "${sizeOption.consoleSp}sp",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Sound Effects
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = palette.cardBg,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (uiState.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Sound Status",
                        tint = if (uiState.soundEnabled) palette.accentPrimary else palette.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sound Effects",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }
                Switch(
                    checked = uiState.soundEnabled,
                    onCheckedChange = { onToggleSound(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = palette.accentPrimary,
                        uncheckedThumbColor = palette.textSecondary,
                        uncheckedTrackColor = palette.cardAltBg
                    ),
                    modifier = Modifier.testTag("setting_sound_switch")
                )
            }
        }

        // Section 5: MTK BROM / DA Engine & Custom Loaders
        if (mtkViewModel != null) {
            val debugMode by mtkViewModel.debugMode.collectAsState()

            // Terminal Debug Mode Toggle Card (Clean single row)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.cardBg,
                border = BorderStroke(
                    width = if (debugMode) 1.5.dp else 1.dp,
                    color = if (debugMode) palette.accentPrimary else palette.cardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setting_debug_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Debug Mode",
                            tint = if (debugMode) palette.accentPrimary else palette.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Terminal Debug Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }
                    Switch(
                        checked = debugMode,
                        onCheckedChange = { mtkViewModel.setDebugMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = palette.accentPrimary,
                            uncheckedThumbColor = palette.textSecondary,
                            uncheckedTrackColor = palette.cardAltBg
                        ),
                        modifier = Modifier.testTag("setting_debug_switch")
                    )
                }
            }

            DatabaseSyncSettingsCard(mtkViewModel = mtkViewModel)
            MtkLoadersSettingsCard(mtkViewModel = mtkViewModel)
        }
    }
}

@Composable
private fun DatabaseSyncSettingsCard(
    mtkViewModel: MtkBridgeViewModel
) {
    val palette = LocalAppPalette.current
    val operatingMode by mtkViewModel.operatingMode.collectAsState()
    val repositoryUrl by mtkViewModel.repositoryUrl.collectAsState()
    val loaderManifest by mtkViewModel.loaderManifest.collectAsState()
    val syncState by mtkViewModel.syncState.collectAsState()

    var editableUrl by remember(repositoryUrl) { mutableStateOf(repositoryUrl) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.cardBg,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setting_database_sync_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Database Sync",
                        tint = if (operatingMode == AppOperatingMode.ONLINE) Color(0xFF06B6D4) else Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Database & Loader Sync",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }

                Text(
                    text = "v${loaderManifest?.version ?: 105} · ${loaderManifest?.entries?.size ?: 0} loaders",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentPrimary
                )
            }

            // Online / Offline Mode Toggle Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isOnline = operatingMode == AppOperatingMode.ONLINE
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOnline) Color(0xFF06B6D4).copy(alpha = 0.16f) else palette.cardAltBg,
                    border = BorderStroke(if (isOnline) 1.5.dp else 1.dp, if (isOnline) Color(0xFF06B6D4) else palette.cardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { mtkViewModel.selectOperatingMode(AppOperatingMode.ONLINE) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = if (isOnline) Color(0xFF06B6D4) else palette.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ONLINE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (isOnline) Color(0xFF06B6D4) else palette.textPrimary)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isOnline) Color(0xFF10B981).copy(alpha = 0.16f) else palette.cardAltBg,
                    border = BorderStroke(if (!isOnline) 1.5.dp else 1.dp, if (!isOnline) Color(0xFF10B981) else palette.cardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { mtkViewModel.selectOperatingMode(AppOperatingMode.OFFLINE) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = if (!isOnline) Color(0xFF10B981) else palette.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OFFLINE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (!isOnline) Color(0xFF10B981) else palette.textPrimary)
                    }
                }
            }

            // Repository Endpoint URL (GitHub Raw -> Future Private Server)
            OutlinedTextField(
                value = editableUrl,
                onValueChange = { editableUrl = it },
                label = { Text("Repository URL (GitHub Raw or Private Backend)", fontSize = 10.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = palette.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accentPrimary,
                    unfocusedBorderColor = palette.cardBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallActionChip(
                    label = "Apply URL",
                    icon = Icons.Default.Check,
                    tint = palette.accentPrimary,
                    onClick = { mtkViewModel.updateRepositoryUrl(editableUrl) },
                    modifier = Modifier.weight(1f)
                )
                SmallActionChip(
                    label = "Default GitHub",
                    icon = Icons.Default.Refresh,
                    tint = palette.accentSecondary,
                    onClick = {
                        editableUrl = GitHubLoaderRepositorySource.DEFAULT_GITHUB_RAW_URL
                        mtkViewModel.updateRepositoryUrl(GitHubLoaderRepositorySource.DEFAULT_GITHUB_RAW_URL)
                    },
                    modifier = Modifier.weight(1f)
                )
                SmallActionChip(
                    label = "Refresh Map",
                    icon = Icons.Default.Sync,
                    tint = Color(0xFF06B6D4),
                    onClick = { mtkViewModel.refreshLoaderManifest(forceNetwork = true) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (syncState.isSyncing) {
                Text(
                    text = "${syncState.currentFileName} (${syncState.downloadedFiles}/${syncState.totalFiles})",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF10B981),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LinearProgressIndicator(
                    progress = { (syncState.percentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color(0xFF10B981),
                    trackColor = palette.cardBorder
                )
                OutlinedButton(
                    onClick = { mtkViewModel.cancelOfflineSync() },
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                }
            } else {
                Button(
                    onClick = { mtkViewModel.syncOfflineLoaders() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Offline Package (Last: ${syncState.lastSyncTime})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun MtkLoadersSettingsCard(
    mtkViewModel: MtkBridgeViewModel
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val daAgentPath by mtkViewModel.daAgentPath.collectAsState()
    val preloaderPath by mtkViewModel.preloaderPath.collectAsState()
    val authFilePath by mtkViewModel.authFilePath.collectAsState()

    val daPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = resolveFileNameFromUri(context, it, "custom_da.bin")
            mtkViewModel.setCustomDaFromUri(it, fileName)
        }
    }

    val preloaderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = resolveFileNameFromUri(context, it, "preloader.bin")
            mtkViewModel.setCustomPreloaderFromUri(it, fileName)
        }
    }

    val authPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = resolveFileNameFromUri(context, it, "auth_sv5.auth")
            mtkViewModel.setCustomAuthFromUri(it, fileName)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.cardBg,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "MTK Engine",
                    tint = palette.accentPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MTK BROM / DA Loaders",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            }

            LoaderPickerItem(
                label = "Custom DA (.bin)",
                currentValue = daAgentPath,
                isCustomActive = !daAgentPath.startsWith("Built-in"),
                icon = Icons.Default.FileOpen,
                accentColor = palette.accentPrimary,
                onClick = { daPickerLauncher.launch(arrayOf("*/*")) },
                tag = "custom_da_picker"
            )

            LoaderPickerItem(
                label = "Custom Preloader (.bin)",
                currentValue = preloaderPath.ifEmpty { "Auto-extract from BROM SRAM" },
                isCustomActive = preloaderPath.isNotEmpty(),
                icon = Icons.Default.Memory,
                accentColor = Color(0xFF10B981),
                onClick = { preloaderPickerLauncher.launch(arrayOf("*/*")) },
                tag = "custom_preloader_picker"
            )

            LoaderPickerItem(
                label = "Custom Auth File (.auth)",
                currentValue = authFilePath.ifEmpty { "Auto Kamakiri2 / Carbonara" },
                isCustomActive = authFilePath.isNotEmpty(),
                icon = Icons.Default.Key,
                accentColor = Color(0xFFF59E0B),
                onClick = { authPickerLauncher.launch(arrayOf("*/*")) },
                tag = "custom_auth_picker"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallActionChip(
                    label = "Reset Loaders",
                    icon = Icons.Default.Refresh,
                    tint = palette.accentPrimary,
                    onClick = { mtkViewModel.resetCustomLoaders() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reset_loaders_button")
                )
                SmallActionChip(
                    label = "Clear Logs",
                    icon = Icons.Default.DeleteSweep,
                    tint = palette.accentSecondary,
                    onClick = { mtkViewModel.clearLogs() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clear_logs_button")
                )
                SmallActionChip(
                    label = "WDT Reset",
                    icon = Icons.Default.Security,
                    tint = Color(0xFFF59E0B),
                    onClick = { mtkViewModel.sendWatchdogReset() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("watchdog_reset_button")
                )
            }
        }
    }
}

@Composable
private fun LoaderPickerItem(
    label: String,
    currentValue: String,
    isCustomActive: Boolean,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCustomActive) accentColor.copy(alpha = 0.12f) else palette.cardAltBg,
        border = BorderStroke(1.dp, if (isCustomActive) accentColor else palette.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isCustomActive) accentColor else palette.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = currentValue,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isCustomActive) accentColor else palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "LOAD",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun SmallActionChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.cardAltBg,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = modifier
            .height(34.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun resolveFileNameFromUri(context: Context, uri: Uri, fallback: String): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                if (!name.isNullOrBlank()) return name
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: fallback
}

@Composable
private fun ThemeModeOptionCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) palette.accentPrimary.copy(alpha = 0.16f) else palette.cardAltBg,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) palette.accentPrimary else palette.cardBorder
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) palette.accentPrimary else palette.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) palette.accentPrimary else palette.textPrimary
            )
        }
    }
}

@Composable
private fun ColorSwatchDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}
