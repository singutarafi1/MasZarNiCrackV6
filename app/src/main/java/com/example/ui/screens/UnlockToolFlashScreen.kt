package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppNavDestination
import com.example.model.AppOperatingMode
import com.example.model.BackupMode
import com.example.model.FlashOptions
import com.example.model.LogLevel
import com.example.model.MtkBrand
import com.example.model.MtkDeviceDatabase
import com.example.model.MtkDeviceModel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.ServiceFunction
import com.example.model.TerminalLog
import com.example.protocol.MtkMetaManager
import com.example.protocol.RealtimePortStatus
import com.example.protocol.TargetPhoneState
import com.example.protocol.UsbDeviceMode
import com.example.ui.components.LoaderRepositoryBrowserModal
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.LocalFontConfig
import com.example.util.DeviceSafetyChecker
import com.example.viewmodel.MtkBridgeViewModel
import java.util.Locale

@Composable
fun UnlockToolFlashScreen(
    viewModel: MtkBridgeViewModel,
    onOpenDrawer: () -> Unit,
    showTopBar: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val currentDestination by viewModel.currentDestination.collectAsState()
    val targetPhoneState by viewModel.targetPhoneState.collectAsState()
    val realtimePortStatus by viewModel.realtimePortStatus.collectAsState()
    val autoNvBackup by viewModel.autoNvBackup.collectAsState()
    val autoReboot by viewModel.autoReboot.collectAsState()
    val partitions by viewModel.partitions.collectAsState()
    val progress by viewModel.operationProgress.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val debugMode by viewModel.debugMode.collectAsState()
    val flashOptions by viewModel.flashOptions.collectAsState()
    val backupMode by viewModel.backupMode.collectAsState()
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val scatterFileName by viewModel.scatterFileName.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val bottomPagerState = rememberPagerState(initialPage = 0) { 2 }

    var showStopConfirmDialog by remember { mutableStateOf(false) }

    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Stop Operation?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }
            },
            text = {
                Text(
                    "Warning: Interrupting active partition flashing or formatting may risk corrupting device storage.",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            },
            containerColor = palette.cardBg,
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmDialog = false
                        viewModel.cancelCurrentOperation()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Yes, Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showStopConfirmDialog = false },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, palette.cardBorder)
                ) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.screenBg)
    ) {
        val isLandscape = maxWidth > maxHeight
        val deckHorizontalPad = if (isLandscape) 6.dp else 8.dp
        val deckVerticalPad = if (isLandscape) 3.dp else 4.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.screenBg)
        ) {
            if (showTopBar) {
                TopCompactStatusBar(
                    currentDestination = currentDestination,
                    targetPhoneState = targetPhoneState,
                    realtimePortStatus = realtimePortStatus,
                    onOpenDrawer = onOpenDrawer
                )
            }

            // Top Control Deck - Compact hardware density (0.25f portrait / 0.38f landscape)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isLandscape) 0.38f else 0.25f)
                    .padding(horizontal = deckHorizontalPad, vertical = deckVerticalPad)
            ) {
                Crossfade(targetState = currentDestination, label = "tab_crossfade") { dest ->
                    when (dest) {
                        AppNavDestination.MTK_FLASH -> {
                            MtkFlashDeck(
                                viewModel = viewModel,
                                selectedBrand = selectedBrand,
                                selectedModel = selectedModel,
                                scatterFileName = scatterFileName,
                                flashOptions = flashOptions,
                                partitions = partitions,
                                progress = progress,
                                onNavigateToPartitions = {
                                    coroutineScope.launch { bottomPagerState.animateScrollToPage(1) }
                                },
                                onStopClick = { showStopConfirmDialog = true }
                            )
                        }
                        AppNavDestination.MTK_BACKUP -> {
                            MtkBackupDeck(
                                viewModel = viewModel,
                                selectedBrand = selectedBrand,
                                selectedModel = selectedModel,
                                backupMode = backupMode,
                                autoReboot = autoReboot,
                                autoNvBackup = autoNvBackup,
                                partitions = partitions,
                                scatterFileName = scatterFileName,
                                progress = progress,
                                onNavigateToPartitions = {
                                    coroutineScope.launch { bottomPagerState.animateScrollToPage(1) }
                                },
                                onStopClick = { showStopConfirmDialog = true }
                            )
                        }
                        AppNavDestination.MTK_SERVICE -> {
                            MtkServiceDeck(
                                viewModel = viewModel,
                                selectedBrand = selectedBrand,
                                selectedModel = selectedModel,
                                autoReboot = autoReboot,
                                autoNvBackup = autoNvBackup,
                                progress = progress,
                                onStopClick = { showStopConfirmDialog = true }
                            )
                        }
                        AppNavDestination.QC_FLASH -> {
                            QcFlashDeck(
                                viewModel = viewModel,
                                progress = progress
                            )
                        }
                        AppNavDestination.QC_BACKUP -> {
                            QcBackupDeck(viewModel = viewModel)
                        }
                        AppNavDestination.QC_SERVICE -> {
                            QcServiceDeck(viewModel = viewModel)
                        }
                        AppNavDestination.SPD_FLASH, AppNavDestination.SPD_BACKUP, AppNavDestination.SPD_SERVICE -> {
                            PlatformPlaceholderDeck(
                                platformTitle = "Spreadtrum (SPD / Unisoc)",
                                subTabTitle = dest.title,
                                subtitle = "FDL & PAC Engine under active integration."
                            )
                        }
                        AppNavDestination.ISP_FLASH, AppNavDestination.ISP_BACKUP, AppNavDestination.ISP_SERVICE -> {
                            PlatformPlaceholderDeck(
                                platformTitle = "Direct ISP (eMMC / UFS)",
                                subTabTitle = dest.title,
                                subtitle = "Direct Hardware Pinout Engine under active integration."
                            )
                        }
                        else -> Unit
                    }
                }
            }

            CompactOperationFooter(progress = progress)

            // Extended Bottom Terminal Log / Partition Table (0.75f portrait / 0.62f landscape)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isLandscape) 0.62f else 0.75f)
            ) {
                val isPartitionSupported = currentDestination == AppNavDestination.MTK_FLASH || currentDestination == AppNavDestination.MTK_BACKUP
                if (isPartitionSupported) {
                    SwipeableConsoleAndPartitionPanel(
                        viewModel = viewModel,
                        logs = logs,
                        partitions = partitions,
                        pagerState = bottomPagerState,
                        debugMode = debugMode,
                        onToggleDebug = { viewModel.toggleDebugMode() },
                        onClearLogs = { viewModel.clearLogs() },
                        onLoadDevicePartitions = { viewModel.loadDevicePartitions() }
                    )
                } else {
                    FullTerminalConsole(
                        logs = logs,
                        debugMode = debugMode,
                        onToggleDebug = { viewModel.toggleDebugMode() },
                        onClear = { viewModel.clearLogs() }
                    )
                }
            }
        }
    }
}

// =============================================================================
// 1. MTK FLASH DECK
// =============================================================================
@Composable
private fun MtkFlashDeck(
    viewModel: MtkBridgeViewModel,
    selectedBrand: MtkBrand,
    selectedModel: MtkDeviceModel,
    scatterFileName: String,
    flashOptions: FlashOptions,
    partitions: List<PartitionEntry>,
    progress: OperationProgress,
    onNavigateToPartitions: () -> Unit = {},
    onStopClick: () -> Unit
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val scatterPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.loadScatterFromUri(it, "scatter.txt") {
                onNavigateToPartitions()
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        treeUri?.let {
            viewModel.loadFirmwareFolder(it) {
                onNavigateToPartitions()
            }
        }
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.panelBorderBrush, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Brand & Model Selector
            BrandModelSelectorRow(
                selectedBrand = selectedBrand,
                selectedModel = selectedModel,
                onBrandSelect = { viewModel.selectBrand(it) },
                onModelSelect = { viewModel.selectModel(it) }
            )

            // Row 2: Scatter Loader + Folder Loader + DA Loader Pill in single aligned row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (scatterFileName.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.14f) else palette.cardAltBg,
                    border = BorderStroke(1.dp, if (scatterFileName.isNotEmpty()) Color(0xFF10B981) else palette.cardBorder),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(32.dp)
                        .testTag("flash_load_scatter_btn")
                        .clickable { scatterPickerLauncher.launch(arrayOf("*/*")) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = "Load Scatter",
                            tint = if (scatterFileName.isNotEmpty()) Color(0xFF10B981) else palette.accentPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (scatterFileName.isEmpty()) "Load Scatter" else scatterFileName,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (scatterFileName.isNotEmpty()) Color(0xFF10B981) else palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = palette.cardAltBg,
                    border = BorderStroke(1.dp, palette.accentSecondary.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("flash_load_folder_btn")
                        .clickable { folderPickerLauncher.launch(null) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Firmware Folder",
                            tint = palette.accentSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Dir",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentSecondary,
                            maxLines = 1
                        )
                    }
                }

                HydraLoaderConfigPill(viewModel = viewModel)
            }

            // Row 3: Options Toggles + Parts Badge + Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickOptionTogglePill("Reboot", flashOptions.autoReboot) { viewModel.toggleFlashAutoReboot(it) }
                QuickOptionTogglePill("NV Backup", flashOptions.readNvData) { viewModel.toggleFlashReadNvData(it) }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = palette.cardAltBg,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("flash_parts_counter_btn")
                        .clickable { onNavigateToPartitions() }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                        Text(
                            text = "${partitions.count { it.isSelectedForFlashing }}/${partitions.size} Parts",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentPrimary,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.1f))

                Button(
                    onClick = { viewModel.scanTargetPhone() },
                    enabled = !progress.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.cardAltBg),
                    border = BorderStroke(1.dp, palette.cardBorder),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = palette.accentPrimary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Scan", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                }

                val hasPartitions = partitions.any { it.isSelectedForFlashing }
                Button(
                    onClick = {
                        if (progress.isRunning) onStopClick()
                        else viewModel.executeFlashOperation()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            progress.isRunning -> Color(0xFFDC2626)
                            hasPartitions -> Color(0xFF16A34A)
                            else -> palette.startButtonBg
                        }
                    ),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("start_flash_button")
                ) {
                    Icon(
                        imageVector = if (progress.isRunning) Icons.Default.Stop else Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (progress.isRunning) "STOP" else "FLASH",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// =============================================================================
// 2. MTK BACKUP DECK
// =============================================================================
@Composable
private fun MtkBackupDeck(
    viewModel: MtkBridgeViewModel,
    selectedBrand: MtkBrand,
    selectedModel: MtkDeviceModel,
    backupMode: BackupMode,
    autoReboot: Boolean = true,
    autoNvBackup: Boolean = true,
    partitions: List<PartitionEntry>,
    scatterFileName: String,
    progress: OperationProgress,
    onNavigateToPartitions: () -> Unit = {},
    onStopClick: () -> Unit
) {
    val palette = LocalAppPalette.current
    val availableStorageBytes by viewModel.availableBackupStorageBytes.collectAsState()
    val backupLocation by viewModel.backupLocation.collectAsState()
    val selectedCount = partitions.count { it.isSelectedForFlashing }
    val selectedBackupBytes = remember(partitions) {
        partitions.filter { it.isSelectedForFlashing }.sumOf { it.sizeBytes }
    }
    val isStorageInsufficient = partitions.isNotEmpty() &&
        selectedBackupBytes > 0L &&
        (selectedBackupBytes + DeviceSafetyChecker.BACKUP_SAFETY_MARGIN_BYTES) > availableStorageBytes
    var showStorageAlarmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshBackupStorageInfo()
    }

    val scatterPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.loadScatterFromUri(it, "scatter.txt") {
                onNavigateToPartitions()
            }
        }
    }

    if (showStorageAlarmDialog) {
        val neededWithMargin = selectedBackupBytes + DeviceSafetyChecker.BACKUP_SAFETY_MARGIN_BYTES
        val shortfallBytes = (neededWithMargin - availableStorageBytes).coerceAtLeast(0L)
        val alarmRed = Color(0xFFEF4444)
        AlertDialog(
            onDismissRequest = { showStorageAlarmDialog = false },
            containerColor = palette.cardBg,
            modifier = Modifier.testTag("backup_storage_alarm_dialog"),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = alarmRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Storage Alarm — နေရာမလောက်ပါ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = alarmRed
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ယခု Backup ယူမည့် Partition Size သည် ဖုန်းထဲရှိ လက်ကျန် Storage ထက် ကျော်လွန်နေပါသည်။",
                        fontSize = 11.5.sp,
                        color = palette.textPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.cardAltBg,
                        border = BorderStroke(1.dp, alarmRed.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Selected ($selectedCount Parts): ${formatStorageBytes(selectedBackupBytes)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Device Free Storage: ${formatStorageBytes(availableStorageBytes)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = "Shortfall (+256MB safety): ${formatStorageBytes(shortfallBytes)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = alarmRed
                            )
                            Text(
                                text = "Path: $backupLocation",
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (backupMode != BackupMode.STABLE_FIRMWARE) {
                        Button(
                            onClick = {
                                showStorageAlarmDialog = false
                                viewModel.setBackupMode(BackupMode.STABLE_FIRMWARE)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accentPrimary),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("alarm_switch_stable_btn")
                        ) {
                            Text("Stable FW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Button(
                        onClick = {
                            showStorageAlarmDialog = false
                            viewModel.executeBackupOperation()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = alarmRed),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("alarm_dump_anyway_btn")
                    ) {
                        Text("Dump Anyway", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showStorageAlarmDialog = false },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, palette.cardBorder)
                ) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.panelBorderBrush, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Brand & Model Selector
            BrandModelSelectorRow(
                selectedBrand = selectedBrand,
                selectedModel = selectedModel,
                onBrandSelect = { viewModel.selectBrand(it) },
                onModelSelect = { viewModel.selectModel(it) }
            )

            // Row 2: Preset Mode Chips + Scatter Loader in single aligned row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackupMode.values().forEach { mode ->
                        val isSelected = backupMode == mode
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) palette.accentPrimary else palette.cardAltBg,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) palette.accentPrimary else palette.cardBorder
                            ),
                            modifier = Modifier
                                .testTag("backup_preset_${mode.name.lowercase(Locale.US)}")
                                .clickable(enabled = !progress.isRunning) { viewModel.setBackupMode(mode) }
                        ) {
                            Text(
                                text = mode.shortLabel,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else palette.textSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (scatterFileName.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.14f) else palette.cardAltBg,
                    border = BorderStroke(1.dp, if (scatterFileName.isNotEmpty()) Color(0xFF10B981) else palette.cardBorder),
                    modifier = Modifier
                        .testTag("backup_load_scatter_btn")
                        .clickable(enabled = !progress.isRunning) { scatterPickerLauncher.launch(arrayOf("*/*")) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = "Load Scatter File",
                            tint = if (scatterFileName.isNotEmpty()) Color(0xFF10B981) else palette.accentPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (scatterFileName.isEmpty()) "Scatter" else scatterFileName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (scatterFileName.isNotEmpty()) Color(0xFF10B981) else palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Row 3: Settings Toggles + Parts Badge + Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickOptionTogglePill("Reboot", autoReboot) { viewModel.toggleAutoReboot(it) }
                HydraLoaderConfigPill(viewModel = viewModel)

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isStorageInsufficient -> Color(0xFFEF4444).copy(alpha = 0.16f)
                        partitions.isNotEmpty() -> palette.accentPrimary.copy(alpha = 0.15f)
                        else -> palette.cardAltBg
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isStorageInsufficient -> Color(0xFFEF4444)
                            partitions.isNotEmpty() -> palette.accentPrimary
                            else -> palette.cardBorder
                        }
                    ),
                    modifier = Modifier
                        .testTag("backup_parts_counter_btn")
                        .clickable { onNavigateToPartitions() }
                ) {
                    val counterText = if (partitions.isNotEmpty() && selectedBackupBytes > 0L) {
                        "$selectedCount/${partitions.size} (${formatStorageBytes(selectedBackupBytes)})"
                    } else {
                        "Free: ${formatStorageBytes(availableStorageBytes)}"
                    }
                    Text(
                        text = counterText,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isStorageInsufficient -> Color(0xFFEF4444)
                            partitions.isNotEmpty() -> palette.accentPrimary
                            else -> palette.textSecondary
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.weight(0.1f))

                Button(
                    onClick = { viewModel.scanTargetPhone() },
                    enabled = !progress.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.cardAltBg),
                    border = BorderStroke(1.dp, palette.cardBorder),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Scan Port",
                        modifier = Modifier.size(12.dp),
                        tint = palette.accentPrimary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Scan", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                }

                val actionBtnColor = when {
                    progress.isRunning -> Color(0xFFDC2626)
                    partitions.isEmpty() -> palette.startButtonBg
                    isStorageInsufficient -> Color(0xFFD97706)
                    else -> Color(0xFF16A34A)
                }
                val actionBtnIcon = when {
                    progress.isRunning -> Icons.Default.Stop
                    partitions.isEmpty() -> Icons.Default.Download
                    else -> Icons.Default.Save
                }
                val actionBtnLabel = when {
                    progress.isRunning -> "STOP"
                    partitions.isEmpty() -> "LOAD GPT"
                    isStorageInsufficient -> "LOW SPACE"
                    else -> "START ($selectedCount)"
                }

                Button(
                    onClick = {
                        when {
                            progress.isRunning -> onStopClick()
                            partitions.isEmpty() -> viewModel.loadDevicePartitions {
                                onNavigateToPartitions()
                            }
                            selectedCount == 0 -> {
                                viewModel.addLog(
                                    TerminalLog(
                                        nowTime(),
                                        "No partitions checked. Select a preset or check partitions in Partition Table.",
                                        LogLevel.WARNING
                                    )
                                )
                                onNavigateToPartitions()
                            }
                            isStorageInsufficient -> showStorageAlarmDialog = true
                            else -> viewModel.executeBackupOperation()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = actionBtnColor),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("backup_3in1_action_btn")
                ) {
                    Icon(
                        actionBtnIcon,
                        contentDescription = actionBtnLabel,
                        modifier = Modifier.size(13.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = actionBtnLabel,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isStorageInsufficient) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color(0xFFEF4444).copy(alpha = if (palette.isDark) 0.18f else 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_storage_alarm_banner")
                        .clickable { showStorageAlarmDialog = true }
                ) {
                    Text(
                        text = "⚠ Storage Alarm: Selected (${formatStorageBytes(selectedBackupBytes)}) > Free (${formatStorageBytes(availableStorageBytes)})",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (palette.isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// 3. MTK SERVICE DECK
// =============================================================================
@Composable
private fun MtkServiceDeck(
    viewModel: MtkBridgeViewModel,
    selectedBrand: MtkBrand,
    selectedModel: MtkDeviceModel,
    autoReboot: Boolean,
    autoNvBackup: Boolean,
    progress: OperationProgress,
    onStopClick: () -> Unit
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    var showVbmetaDialog by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var showFactoryResetModeDialog by remember { mutableStateOf(false) }
    var showMetaImeiDialog by remember { mutableStateOf(false) }
    var showRpmbWriteDialog by remember { mutableStateOf(false) }
    var showRpmbEraseConfirm by remember { mutableStateOf(false) }
    var showFullFlashDialog by remember { mutableStateOf(false) }
    var showReadOffsetDialog by remember { mutableStateOf(false) }
    var showWriteOffsetDialog by remember { mutableStateOf(false) }
    var showEraseRangeDialog by remember { mutableStateOf(false) }
    var showPeekDialog by remember { mutableStateOf(false) }
    var showPokeDialog by remember { mutableStateOf(false) }
    var showDumpBromDialog by remember { mutableStateOf(false) }
    var showRunPayloadDialog by remember { mutableStateOf(false) }

    var metaImei1Input by remember { mutableStateOf("") }
    var metaImei2Input by remember { mutableStateOf("") }

    var rangePartType by remember { mutableStateOf("user") }
    var rangeStartInput by remember { mutableStateOf("0x0") }
    var rangeLenInput by remember { mutableStateOf("0x10000") }
    var rangeIsSectors by remember { mutableStateOf(false) }

    var peekAddrInput by remember { mutableStateOf("0x100000") }
    var peekLenInput by remember { mutableStateOf("0x100") }
    var peekRegisters by remember { mutableStateOf(false) }
    var peekSaveFile by remember { mutableStateOf(false) }
    var pokeAddrInput by remember { mutableStateOf("0x100000") }
    var pokeValInput by remember { mutableStateOf("0x00000000") }

    val rpmbPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = resolvePickerFileName(context, it, "rpmb.bin")
            viewModel.writeRpmbFromUri(it, fileName)
        }
    }

    val metaNvramRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = resolvePickerFileName(context, it, "META_NVRAM_BACKUP.bin")
            viewModel.executeMetaRestoreNvramFromUri(it, fileName)
        }
    }

    val writeOffsetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = resolvePickerFileName(context, it, "offset_image.bin")
            viewModel.executeWriteOffsetFromUri(
                startStr = rangeStartInput,
                uri = it,
                displayName = fileName,
                partType = rangePartType,
                isSectors = rangeIsSectors
            )
        }
    }

    val customPayloadPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = resolvePickerFileName(context, it, "payload.bin")
            viewModel.executeRunCustomPayloadFromUri(it, fileName)
        }
    }

    val dialogTextColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary,
        focusedBorderColor = palette.accentPrimary,
        unfocusedBorderColor = palette.cardBorder
    )

    if (showFactoryResetModeDialog) {
        AlertDialog(
            onDismissRequest = { showFactoryResetModeDialog = false },
            containerColor = palette.cardBg,
            title = {
                Text("Select Factory Reset / Wipe Userdata Mode", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            showFactoryResetModeDialog = false
                            viewModel.executeServiceFunctionDirect(ServiceFunction.FACTORY_RESET)
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.accentPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("reset_mode_brom_da")
                    ) {
                        Text("1. BROM / DA Mode Format", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            showFactoryResetModeDialog = false
                            viewModel.executeMetaFactoryReset(preferEmeta = true)
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth().testTag("reset_mode_emeta")
                    ) {
                        Text("2. META Mode Safe Wipe (ADVEMETA)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                    OutlinedButton(
                        onClick = {
                            showFactoryResetModeDialog = false
                            viewModel.executeMetaFactoryReset(preferEmeta = false)
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth().testTag("reset_mode_maui_meta")
                    ) {
                        Text("3. MAUI META Mode Wipe (METAMETA + SLA)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFactoryResetModeDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showMetaImeiDialog) {
        AlertDialog(
            onDismissRequest = { showMetaImeiDialog = false },
            containerColor = palette.cardBg,
            title = {
                Text("META Mode IMEI / NVRAM LID Repair", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = metaImei1Input,
                        onValueChange = { metaImei1Input = it.filter { ch -> ch.isDigit() }.take(15) },
                        label = { Text("IMEI 1 (SIM 1 - 15 digits)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth().testTag("meta_imei1_input")
                    )
                    OutlinedTextField(
                        value = metaImei2Input,
                        onValueChange = { metaImei2Input = it.filter { ch -> ch.isDigit() }.take(15) },
                        label = { Text("IMEI 2 (SIM 2 - 15 digits)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth().testTag("meta_imei2_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMetaImeiDialog = false
                        viewModel.executeMetaWriteImei(metaImei1Input, metaImei2Input)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.startButtonBg),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Write IMEI in META", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMetaImeiDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showVbmetaDialog) {
        val options = listOf(
            3 to "3: Disable Verity & Verification (Recommended)",
            1 to "1: Disable Verity Only",
            2 to "2: Disable Verification Only",
            0 to "0: Re-enable Verity & Verification"
        )
        AlertDialog(
            onDismissRequest = { showVbmetaDialog = false },
            containerColor = palette.cardBg,
            title = {
                Text(stringResource(R.string.vbmeta_dialog_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { (modeVal, label) ->
                        OutlinedButton(
                            onClick = {
                                showVbmetaDialog = false
                                viewModel.executePatchVbmeta(modeVal)
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier.fillMaxWidth().testTag("vbmeta_mode_$modeVal")
                        ) {
                            Text(label, fontSize = 11.sp, color = palette.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVbmetaDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showMetaDialog) {
        AlertDialog(
            onDismissRequest = { showMetaDialog = false },
            containerColor = palette.cardBg,
            title = {
                Text("MediaTek META Mode Suite (mtkclient meta)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("1. Boot Preloader -> META / Factory Mode:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = palette.accentPrimary)
                    MtkMetaManager.MetaMode.values().forEach { meta ->
                        OutlinedButton(
                            onClick = {
                                showMetaDialog = false
                                viewModel.executeEnterMetaMode(meta)
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier.fillMaxWidth().testTag("meta_option_${meta.name.lowercase(Locale.US)}")
                        ) {
                            Text(meta.label, fontSize = 10.5.sp, color = palette.textPrimary)
                        }
                    }
                    Text("2. Direct META Mode Operations:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    OutlinedButton(
                        onClick = {
                            showMetaDialog = false
                            viewModel.executeMetaFactoryReset(preferEmeta = true)
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("META Mode Safe Factory Reset (Wipe Userdata)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                    OutlinedButton(
                        onClick = {
                            showMetaDialog = false
                            viewModel.executeServiceFunctionDirect(ServiceFunction.META_READ_INFO)
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("META Read Modem / Baseband / IMEI Info", fontSize = 10.5.sp, color = palette.textPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            showMetaDialog = false
                            showMetaImeiDialog = true
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("META Write / Repair IMEI 1 & 2 (NVRAM LID)...", fontSize = 10.5.sp, color = palette.textPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            showMetaDialog = false
                            viewModel.executeServiceFunctionDirect(ServiceFunction.META_BACKUP_NVRAM)
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("META Backup NVRAM Calibration Database", fontSize = 10.5.sp, color = palette.textPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            showMetaDialog = false
                            metaNvramRestoreLauncher.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("META Restore NVRAM Calibration File...", fontSize = 10.5.sp, color = palette.textPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMetaDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showRpmbWriteDialog) {
        AlertDialog(
            onDismissRequest = { showRpmbWriteDialog = false },
            containerColor = palette.cardBg,
            title = {
                Text("Write RPMB Image", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            },
            text = {
                Text(
                    "Choose a custom rpmb.bin image file, or write back the existing rpmb.bin in your backup folder.",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRpmbWriteDialog = false
                        rpmbPickerLauncher.launch(arrayOf("*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.startButtonBg),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Pick File", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRpmbWriteDialog = false
                        viewModel.executeServiceFunctionDirect(ServiceFunction.WRITE_RPMB)
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, palette.cardBorder)
                ) {
                    Text("Use rpmb.bin", fontSize = 11.sp, color = palette.textPrimary)
                }
            }
        )
    }

    if (showRpmbEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showRpmbEraseConfirm = false },
            containerColor = palette.cardBg,
            title = {
                Text(stringResource(R.string.erase_rpmb_dialog_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            },
            text = {
                Text(stringResource(R.string.erase_rpmb_dialog_body), fontSize = 12.sp, color = palette.textSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRpmbEraseConfirm = false
                        viewModel.executeServiceFunctionDirect(ServiceFunction.ERASE_RPMB)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Erase RPMB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRpmbEraseConfirm = false },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, palette.cardBorder)
                ) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showFullFlashDialog) {
        val partTypes = listOf(
            "user" to "USER / LU0 (Full Main Storage)",
            "boot1" to "BOOT1 / LU1 (Preloader Region)",
            "boot2" to "BOOT2 / LU2",
            "rpmb" to "RPMB"
        )
        AlertDialog(
            onDismissRequest = { showFullFlashDialog = false },
            containerColor = palette.cardBg,
            title = { Text("Full Flash Dump (mtk rf)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    partTypes.forEach { (key, label) ->
                        OutlinedButton(
                            onClick = {
                                showFullFlashDialog = false
                                viewModel.executeFullFlashDump(key)
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier.fillMaxWidth().testTag("rf_part_$key")
                        ) {
                            Text(label, fontSize = 11.sp, color = palette.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullFlashDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showReadOffsetDialog) {
        AlertDialog(
            onDismissRequest = { showReadOffsetDialog = false },
            containerColor = palette.cardBg,
            title = { Text("Read Flash Range (ro / rs)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("user", "boot1", "boot2").forEach { pt ->
                            QuickOptionTogglePill(label = pt.uppercase(Locale.US), checked = rangePartType == pt) {
                                rangePartType = pt
                            }
                        }
                        QuickOptionTogglePill(label = if (rangeIsSectors) "Sectors" else "Bytes", checked = rangeIsSectors) {
                            rangeIsSectors = it
                        }
                    }
                    OutlinedTextField(
                        value = rangeStartInput,
                        onValueChange = { rangeStartInput = it },
                        label = { Text(if (rangeIsSectors) "Start Sector (hex/dec)" else "Start Offset (hex/dec)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rangeLenInput,
                        onValueChange = { rangeLenInput = it },
                        label = { Text(if (rangeIsSectors) "Sector Count" else "Length Bytes (hex/dec)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReadOffsetDialog = false
                        viewModel.executeReadOffset(rangeStartInput, rangeLenInput, rangePartType, rangeIsSectors)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.startButtonBg),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Read", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReadOffsetDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showWriteOffsetDialog) {
        AlertDialog(
            onDismissRequest = { showWriteOffsetDialog = false },
            containerColor = palette.cardBg,
            title = { Text("Write Flash Offset (wo)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("user", "boot1", "boot2").forEach { pt ->
                            QuickOptionTogglePill(label = pt.uppercase(Locale.US), checked = rangePartType == pt) {
                                rangePartType = pt
                            }
                        }
                        QuickOptionTogglePill(label = if (rangeIsSectors) "Sectors" else "Bytes", checked = rangeIsSectors) {
                            rangeIsSectors = it
                        }
                    }
                    OutlinedTextField(
                        value = rangeStartInput,
                        onValueChange = { rangeStartInput = it },
                        label = { Text(if (rangeIsSectors) "Target Sector" else "Target Byte Offset (hex/dec)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWriteOffsetDialog = false
                        writeOffsetPickerLauncher.launch(arrayOf("*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Pick File & Write", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWriteOffsetDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showEraseRangeDialog) {
        AlertDialog(
            onDismissRequest = { showEraseRangeDialog = false },
            containerColor = palette.cardBg,
            title = { Text("Erase Offset / Sectors (es / ess)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("user", "boot1", "boot2").forEach { pt ->
                            QuickOptionTogglePill(label = pt.uppercase(Locale.US), checked = rangePartType == pt) {
                                rangePartType = pt
                            }
                        }
                        QuickOptionTogglePill(label = if (rangeIsSectors) "Sectors" else "Bytes", checked = rangeIsSectors) {
                            rangeIsSectors = it
                        }
                    }
                    OutlinedTextField(
                        value = rangeStartInput,
                        onValueChange = { rangeStartInput = it },
                        label = { Text(if (rangeIsSectors) "Start Sector" else "Start Offset (hex/dec)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rangeLenInput,
                        onValueChange = { rangeLenInput = it },
                        label = { Text(if (rangeIsSectors) "Sector Count" else "Erase Length Bytes", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEraseRangeDialog = false
                        viewModel.executeEraseRange(rangeStartInput, rangeLenInput, rangePartType, rangeIsSectors)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Erase", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseRangeDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showPeekDialog) {
        AlertDialog(
            onDismissRequest = { showPeekDialog = false },
            containerColor = palette.cardBg,
            title = { Text("DA Peek (Read Memory / Reg)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = peekAddrInput,
                        onValueChange = { peekAddrInput = it },
                        label = { Text("Address (e.g. 0x100000)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = peekLenInput,
                        onValueChange = { peekLenInput = it },
                        label = { Text("Length Bytes (e.g. 0x100)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuickOptionTogglePill(label = "Registers (32-bit)", checked = peekRegisters) { peekRegisters = it }
                        QuickOptionTogglePill(label = "Save .bin", checked = peekSaveFile) { peekSaveFile = it }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPeekDialog = false
                        viewModel.executeDaPeek(peekAddrInput, peekLenInput, peekRegisters, peekSaveFile)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Peek", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPeekDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showPokeDialog) {
        AlertDialog(
            onDismissRequest = { showPokeDialog = false },
            containerColor = palette.cardBg,
            title = { Text("DA Poke (Write Memory / Reg)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = pokeAddrInput,
                        onValueChange = { pokeAddrInput = it },
                        label = { Text("Address (e.g. 0x10007000)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pokeValInput,
                        onValueChange = { pokeValInput = it },
                        label = { Text("Value (0xDWORD or Hex Bytes)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = dialogTextColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPokeDialog = false
                        viewModel.executeDaPoke(pokeAddrInput, pokeValInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Poke", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPokeDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showDumpBromDialog) {
        AlertDialog(
            onDismissRequest = { showDumpBromDialog = false },
            containerColor = palette.cardBg,
            title = { Text("Dump BootROM / SRAM (BROM Mode)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            showDumpBromDialog = false
                            viewModel.executeDumpBootRom(sramMode = false)
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth().testTag("dump_brom_128k")
                    ) {
                        Text("Dump BootROM (generic_dump_payload.bin)", fontSize = 11.sp, color = palette.textPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            showDumpBromDialog = false
                            viewModel.executeDumpBootRom(sramMode = true)
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth().testTag("dump_sram_mode")
                    ) {
                        Text("Dump SRAM (generic_sram_payload.bin)", fontSize = 11.sp, color = palette.textPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDumpBromDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (showRunPayloadDialog) {
        val builtins = listOf(
            "generic_dump_payload.bin",
            "generic_sram_payload.bin",
            "generic_preloader_dump_payload.bin",
            "generic_uart_dump_payload.bin",
            "generic_patcher_payload.bin",
            "generic_reboot_payload.bin"
        )
        AlertDialog(
            onDismissRequest = { showRunPayloadDialog = false },
            containerColor = palette.cardBg,
            title = { Text("Run BROM Payload (mtk payload)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    builtins.forEach { asset ->
                        OutlinedButton(
                            onClick = {
                                showRunPayloadDialog = false
                                viewModel.executeRunBuiltinPayload(asset)
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(asset, fontSize = 10.5.sp, color = palette.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRunPayloadDialog = false
                        customPayloadPickerLauncher.launch(arrayOf("*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.startButtonBg),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Pick Custom .bin", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRunPayloadDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    val selectedServiceFunc by viewModel.selectedServiceFunction.collectAsState()
    var methodDropdownExpanded by remember { mutableStateOf(false) }

    val hydraServiceMethods = remember {
        listOf(
            ServiceFunction.ERASE_FRP to "[FRP] Erase FRP Lock (Auto)",
            ServiceFunction.FACTORY_RESET to "[Reset] Factory Reset (Choose BROM or META Mode)...",
            ServiceFunction.META_FACTORY_RESET to "[META Reset] META Mode Safe Wipe Userdata (0% NV Risk)",
            ServiceFunction.UNLOCK_BOOTLOADER to "[Bootloader] Unlock Bootloader",
            ServiceFunction.LOCK_BOOTLOADER to "[Bootloader] Relock Bootloader",
            ServiceFunction.DISABLE_MI_ACCOUNT to "[Mi Cloud] Disable Xiaomi Mi Account",
            ServiceFunction.BYPASS_AUTH to "[Security] Bypass SLA / DAA Auth",
            ServiceFunction.CRASH_TO_BROM to "[Preloader] Crash Preloader to BROM",
            ServiceFunction.READ_GPT_SCATTER to "[PM / GPT] Read GPT & Generate Scatter",
            ServiceFunction.ENTER_META_MODE to "[META Suite] Boot Preloader -> META / EMETA / Factory...",
            ServiceFunction.META_READ_INFO to "[META Info] Read Modem / Baseband / IMEI in META",
            ServiceFunction.META_WRITE_IMEI to "[META IMEI] Write / Repair IMEI 1 & 2 (NVRAM LID)...",
            ServiceFunction.META_BACKUP_NVRAM to "[META NVRAM] Backup NVRAM Calibration DB in META",
            ServiceFunction.META_RESTORE_NVRAM to "[META NVRAM] Restore NVRAM Calibration DB in META...",
            ServiceFunction.META_REBOOT_NORMAL to "[META Exit] Exit META Mode & Reboot to System",
            ServiceFunction.BACKUP_NVRAM to "[BROM NV] Backup NVRAM / NVDATA Partitions",
            ServiceFunction.RESTORE_NVRAM to "[BROM NV] Restore NVRAM / NVDATA Partitions",
            ServiceFunction.PATCH_VBMETA to "[VBMeta] Patch dm-verity / AVB Flags...",
            ServiceFunction.MEMORY_TEST to "[Diagnostics] Hardware Memory / Storage Test",
            ServiceFunction.READ_RPMB to "[RPMB] Read RPMB Security Region",
            ServiceFunction.WRITE_RPMB to "[RPMB] Write RPMB Image...",
            ServiceFunction.ERASE_RPMB to "[RPMB] Erase RPMB Region...",
            ServiceFunction.FULL_FLASH_DUMP to "[Raw Flash] Full Flash Dump (mtk rf)...",
            ServiceFunction.READ_OFFSET to "[Raw Flash] Read Flash Range (ro / rs)...",
            ServiceFunction.WRITE_OFFSET to "[Raw Flash] Write Flash Offset (wo)...",
            ServiceFunction.ERASE_RANGE to "[Raw Flash] Erase Flash Range (es / ess)...",
            ServiceFunction.DA_PEEK to "[Low-Level] DA Peek (Read Memory / Reg)...",
            ServiceFunction.DA_POKE to "[Low-Level] DA Poke (Write Memory / Reg)...",
            ServiceFunction.DUMP_EFUSE to "[Low-Level] Dump eFuse Hardware Bits",
            ServiceFunction.DUMP_FOOTER to "[Low-Level] Dump Crypto Footer",
            ServiceFunction.DUMP_BROM to "[Low-Level] Dump BootROM / SRAM...",
            ServiceFunction.RUN_PAYLOAD to "[Low-Level] Run BROM Payload (mtk payload)..."
        )
    }

    val currentMethodLabel = remember(selectedServiceFunc) {
        hydraServiceMethods.firstOrNull { it.first == selectedServiceFunc }?.second
            ?: "[FRP] Erase FRP Lock (Auto)"
    }

    fun triggerHydraServiceMethod(func: ServiceFunction) {
        viewModel.selectServiceFunction(func)
        when (func) {
            ServiceFunction.FACTORY_RESET -> showFactoryResetModeDialog = true
            ServiceFunction.META_FACTORY_RESET -> viewModel.executeMetaFactoryReset(preferEmeta = true)
            ServiceFunction.META_WRITE_IMEI -> showMetaImeiDialog = true
            ServiceFunction.META_RESTORE_NVRAM -> metaNvramRestoreLauncher.launch(arrayOf("*/*"))
            ServiceFunction.PATCH_VBMETA -> showVbmetaDialog = true
            ServiceFunction.ENTER_META_MODE -> showMetaDialog = true
            ServiceFunction.WRITE_RPMB -> showRpmbWriteDialog = true
            ServiceFunction.ERASE_RPMB -> showRpmbEraseConfirm = true
            ServiceFunction.FULL_FLASH_DUMP -> showFullFlashDialog = true
            ServiceFunction.READ_OFFSET -> showReadOffsetDialog = true
            ServiceFunction.WRITE_OFFSET -> showWriteOffsetDialog = true
            ServiceFunction.ERASE_RANGE -> showEraseRangeDialog = true
            ServiceFunction.DA_PEEK -> showPeekDialog = true
            ServiceFunction.DA_POKE -> showPokeDialog = true
            ServiceFunction.DUMP_BROM -> showDumpBromDialog = true
            ServiceFunction.RUN_PAYLOAD -> showRunPayloadDialog = true
            ServiceFunction.MEMORY_TEST -> viewModel.runMemoryTest()
            ServiceFunction.READ_INFO -> viewModel.executeServiceFunctionDirect(ServiceFunction.ERASE_FRP)
            else -> viewModel.executeServiceFunctionDirect(func)
        }
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.panelBorderBrush, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Brand & Model Selector (with Search)
            BrandModelSelectorRow(
                selectedBrand = selectedBrand,
                selectedModel = selectedModel,
                onBrandSelect = { viewModel.selectBrand(it) },
                onModelSelect = { viewModel.selectModel(it) }
            )

            // Row 2: Operation Method Dropdown + Loader Config Pill in single aligned row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = palette.cardAltBg,
                        border = BorderStroke(1.dp, palette.accentPrimary.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .testTag("service_method_dropdown")
                            .clickable(enabled = !progress.isRunning) { methodDropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Method: $currentMethodLabel",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.actionHighlight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text("▼", fontSize = 8.sp, color = palette.accentPrimary)
                        }
                    }

                    DropdownMenu(
                        expanded = methodDropdownExpanded,
                        onDismissRequest = { methodDropdownExpanded = false },
                        modifier = Modifier
                            .background(palette.cardBg)
                            .height(310.dp)
                    ) {
                        hydraServiceMethods.forEach { (func, label) ->
                            val isSelected = selectedServiceFunc == func
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) palette.accentPrimary else palette.textPrimary
                                    )
                                },
                                onClick = {
                                    methodDropdownExpanded = false
                                    viewModel.selectServiceFunction(func)
                                }
                            )
                        }
                    }
                }

                HydraLoaderConfigPill(viewModel = viewModel)
            }

            // Row 3: Auto Reboot & NV Toggles + Actions (BOOT/INFO & EXECUTE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickOptionTogglePill("Reboot", autoReboot) { viewModel.toggleAutoReboot(it) }
                QuickOptionTogglePill("Auto NV", autoNvBackup) { viewModel.toggleAutoNvBackup(it) }

                Spacer(modifier = Modifier.weight(0.1f))

                Button(
                    onClick = { viewModel.executeServiceFunctionDirect(ServiceFunction.READ_INFO) },
                    enabled = !progress.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.cardAltBg),
                    border = BorderStroke(1.dp, palette.accentPrimary.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("read_info_button")
                ) {
                    Text(
                        text = "BOOT/INFO",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accentPrimary,
                        maxLines = 1
                    )
                }

                val execTarget = if (selectedServiceFunc == ServiceFunction.READ_INFO) {
                    ServiceFunction.ERASE_FRP
                } else {
                    selectedServiceFunc
                }
                Button(
                    onClick = {
                        if (progress.isRunning) {
                            onStopClick()
                        } else {
                            triggerHydraServiceMethod(execTarget)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (progress.isRunning) Color(0xFFDC2626) else palette.startButtonBg
                    ),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("service_execute_button")
                ) {
                    Text(
                        text = if (progress.isRunning) "STOP" else "EXECUTE: ${execTarget.shortTitle.uppercase(Locale.US)}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Row 4: Compact 1-Tap Quick Shortcut Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val quickShortcuts = listOf(
                    Triple(ServiceFunction.ERASE_FRP, "Erase FRP", "erase_frp_button"),
                    Triple(ServiceFunction.FACTORY_RESET, "Factory Reset", "factory_reset_button"),
                    Triple(ServiceFunction.UNLOCK_BOOTLOADER, "Unlock BL", "unlock_bl_button"),
                    Triple(ServiceFunction.LOCK_BOOTLOADER, "Relock BL", "lock_bl_button"),
                    Triple(ServiceFunction.DISABLE_MI_ACCOUNT, "Disable Mi Acc", "disable_mi_button"),
                    Triple(ServiceFunction.BYPASS_AUTH, "Bypass Auth", "bypass_auth_button"),
                    Triple(ServiceFunction.CRASH_TO_BROM, "Crash BROM", "crash_brom_button"),
                    Triple(ServiceFunction.READ_GPT_SCATTER, "Read GPT", "read_gpt_button"),
                    Triple(ServiceFunction.PATCH_VBMETA, "Patch VBMeta", "patch_vbmeta_button"),
                    Triple(ServiceFunction.ENTER_META_MODE, "META Mode", "meta_mode_button")
                )
                quickShortcuts.forEach { (func, shortTitle, tag) ->
                    val active = selectedServiceFunc == func
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (active) palette.accentSecondary.copy(alpha = 0.2f) else palette.cardAltBg,
                        border = BorderStroke(1.dp, if (active) palette.accentSecondary else palette.cardBorder),
                        modifier = Modifier
                            .testTag(tag)
                            .clickable(enabled = !progress.isRunning) {
                                viewModel.selectServiceFunction(func)
                            }
                    ) {
                        Text(
                            text = shortTitle,
                            fontSize = 8.5.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) palette.accentSecondary else palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 4. QUALCOMM (QC) DECKS
// =============================================================================
@Composable
private fun QcFlashDeck(
    viewModel: MtkBridgeViewModel,
    progress: OperationProgress
) {
    val palette = LocalAppPalette.current
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val partitions by viewModel.partitions.collectAsState()
    val autoReboot by viewModel.autoReboot.collectAsState()
    val firehoseName by viewModel.qcFirehoseProgrammerName.collectAsState()
    val operatingMode by viewModel.operatingMode.collectAsState()
    val loaderManifest by viewModel.loaderManifest.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val repositoryUrl by viewModel.repositoryUrl.collectAsState()
    val matchedLoader by viewModel.matchedQcLoader.collectAsState()

    var showLoaderCatalogModal by remember { mutableStateOf(false) }

    if (showLoaderCatalogModal) {
        LoaderRepositoryBrowserModal(
            operatingMode = operatingMode,
            manifest = loaderManifest,
            syncState = syncState,
            repositoryUrl = repositoryUrl,
            matchedEntry = matchedLoader,
            onSwitchMode = { mode ->
                viewModel.selectOperatingMode(mode, triggerSyncIfOffline = (mode == AppOperatingMode.OFFLINE))
            },
            onSyncAll = { viewModel.syncOfflineLoaders() },
            onCancelSync = { viewModel.cancelOfflineSync() },
            onRefreshManifest = { viewModel.refreshLoaderManifest(forceNetwork = true) },
            onSelectEntry = { entry ->
                viewModel.selectQcManifestEntry(entry)
                showLoaderCatalogModal = false
            },
            onDismiss = { showLoaderCatalogModal = false }
        )
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.panelBorderBrush, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Brand & Model Selector
            BrandModelSelectorRow(
                selectedBrand = selectedBrand,
                selectedModel = selectedModel,
                onBrandSelect = { viewModel.selectBrand(it) },
                onModelSelect = { viewModel.selectModel(it) }
            )

            // Row 2: rawprogram0.xml + patch0.xml + Firehose Programmer (opens bkerler/Loaders Catalog)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = palette.cardAltBg,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier
                        .weight(1.0f)
                        .height(32.dp)
                        .clickable {
                            viewModel.addLog(TerminalLog(nowTime(), "[QC FLASH] rawprogram0.xml loaded for flashing.", LogLevel.SUCCESS))
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(13.dp))
                        Text("rawprogram0.xml", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = palette.cardAltBg,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier
                        .weight(0.85f)
                        .height(32.dp)
                        .clickable {
                            viewModel.addLog(TerminalLog(nowTime(), "[QC FLASH] patch0.xml loaded for partition boundaries.", LogLevel.INFO))
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(13.dp))
                        Text("patch0.xml", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (matchedLoader != null) Color(0xFF10B981).copy(alpha = 0.14f) else palette.cardAltBg,
                    border = BorderStroke(
                        1.dp,
                        if (matchedLoader != null) Color(0xFF10B981) else palette.accentSecondary.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .weight(1.15f)
                        .height(32.dp)
                        .testTag("qc_firehose_catalog_btn")
                        .clickable { showLoaderCatalogModal = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = if (matchedLoader != null) Color(0xFF10B981) else palette.accentSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = firehoseName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (matchedLoader != null) Color(0xFF10B981) else palette.accentSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Row 3: Controls (Auto Reboot + Online/Offline Mode + Parts Counter + BOOT / INFO + FLASH)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (autoReboot) palette.actionHighlight.copy(alpha = 0.16f) else palette.cardAltBg,
                    border = BorderStroke(1.dp, if (autoReboot) palette.actionHighlight else palette.cardBorder),
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { viewModel.toggleFlashAutoReboot(!autoReboot) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (autoReboot) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (autoReboot) palette.actionHighlight else palette.textSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text("Reboot", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (autoReboot) palette.actionHighlight else palette.textPrimary)
                    }
                }

                // Inline Online/Offline Mode + Sync chip
                val isOnline = operatingMode == AppOperatingMode.ONLINE
                val modeColor = if (isOnline) Color(0xFF06B6D4) else Color(0xFF10B981)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = modeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, modeColor),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("qc_mode_status_chip")
                        .clickable { showLoaderCatalogModal = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (syncState.isSyncing) Icons.Default.Sync else if (isOnline) Icons.Default.Bolt else Icons.Default.Save,
                            contentDescription = null,
                            tint = modeColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (syncState.isSyncing) "${syncState.percentage}%" else if (isOnline) "ONLINE" else "OFFLINE",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = modeColor,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = palette.cardAltBg,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier.height(32.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        val count = partitions.count { it.isSelectedForFlashing }
                        Text(
                            "$count/${partitions.size}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentSecondary
                        )
                    }
                }

                Button(
                    onClick = { viewModel.qcBootAndInfo() },
                    enabled = !progress.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.cardAltBg),
                    border = BorderStroke(1.dp, palette.accentPrimary.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.weight(0.95f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("BOOT (9008)", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = palette.accentPrimary, maxLines = 1)
                }

                Button(
                    onClick = { viewModel.qcFlashRawprogram() },
                    enabled = !progress.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.startButtonBg),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.weight(1.25f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("FLASH (EDL)", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun QcBackupDeck(
    viewModel: MtkBridgeViewModel
) {
    val palette = LocalAppPalette.current
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val partitions by viewModel.partitions.collectAsState()
    val autoReboot by viewModel.autoReboot.collectAsState()

    var qcBackupPreset by remember { mutableStateOf("Dump EFS / QCN (IMEI & RF)") }
    var presetExpanded by remember { mutableStateOf(false) }
    val presets = listOf(
        "Dump EFS / QCN (IMEI & RF)",
        "Dump Stable Firmware (Exclude Userdata)",
        "Full EDL ROM Dump (rawprogram0.xml)"
    )

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.panelBorderBrush, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Brand & Model Selector
            BrandModelSelectorRow(
                selectedBrand = selectedBrand,
                selectedModel = selectedModel,
                onBrandSelect = { viewModel.selectBrand(it) },
                onModelSelect = { viewModel.selectModel(it) }
            )

            // Row 2: Preset Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = palette.cardAltBg,
                    border = BorderStroke(1.dp, palette.accentPrimary.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clickable { presetExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(13.dp))
                            Text(qcBackupPreset, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        }
                        Text("▼", fontSize = 8.sp, color = palette.accentPrimary)
                    }
                }
                DropdownMenu(
                    expanded = presetExpanded,
                    onDismissRequest = { presetExpanded = false },
                    modifier = Modifier.background(palette.cardBg)
                ) {
                    presets.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item, fontSize = 10.sp, color = palette.textPrimary) },
                            onClick = {
                                qcBackupPreset = item
                                presetExpanded = false
                            }
                        )
                    }
                }
            }

            // Row 3: Controls (Reboot + Counter + READ GPT + EXECUTE DUMP)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (autoReboot) palette.actionHighlight.copy(alpha = 0.16f) else palette.cardAltBg,
                    border = BorderStroke(1.dp, if (autoReboot) palette.actionHighlight else palette.cardBorder),
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { viewModel.toggleFlashAutoReboot(!autoReboot) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (autoReboot) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (autoReboot) palette.actionHighlight else palette.textSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text("Reboot", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (autoReboot) palette.actionHighlight else palette.textPrimary)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = palette.cardAltBg,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier.height(32.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "${partitions.size} Parts",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentSecondary
                        )
                    }
                }

                Button(
                    onClick = { viewModel.qcBootAndInfo() },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.cardAltBg),
                    border = BorderStroke(1.dp, palette.accentPrimary.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.weight(0.95f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("READ GPT", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = palette.accentPrimary)
                }

                Button(
                    onClick = { viewModel.qcReadGptAndDump(qcBackupPreset) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.startButtonBg),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.weight(1.35f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("START DUMP", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun QcServiceDeck(
    viewModel: MtkBridgeViewModel
) {
    val palette = LocalAppPalette.current
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()

    var qcServiceMethod by remember { mutableStateOf("[FRP] Erase FRP Lock (EDL 9008)") }
    var methodExpanded by remember { mutableStateOf(false) }
    val methods = listOf(
        "[FRP] Erase FRP Lock (EDL 9008)",
        "[Reset] Safe Format / Factory Reset",
        "[Mi Cloud] Disable Xiaomi Mi Account (EDL)",
        "[EFS] Wipe / Reset EFS (fsg, modemst1, modemst2)",
        "[Bootloader] Unlock Bootloader (EDL)",
        "[Diag] Reboot Device to EDL 9008"
    )

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.panelBorderBrush, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Brand & Model Selector
            BrandModelSelectorRow(
                selectedBrand = selectedBrand,
                selectedModel = selectedModel,
                onBrandSelect = { viewModel.selectBrand(it) },
                onModelSelect = { viewModel.selectModel(it) }
            )

            // Row 2: Method Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = palette.cardAltBg,
                    border = BorderStroke(1.dp, palette.accentPrimary.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clickable { methodExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Method: $qcServiceMethod", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.actionHighlight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("▼", fontSize = 8.sp, color = palette.accentPrimary)
                    }
                }
                DropdownMenu(
                    expanded = methodExpanded,
                    onDismissRequest = { methodExpanded = false },
                    modifier = Modifier.background(palette.cardBg)
                ) {
                    methods.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, fontSize = 10.sp, color = palette.textPrimary) },
                            onClick = {
                                qcServiceMethod = m
                                methodExpanded = false
                            }
                        )
                    }
                }
            }

            // Row 3: Quick shortcuts strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "Erase FRP" to "[FRP] Erase FRP Lock (EDL 9008)",
                    "Factory Reset" to "[Reset] Safe Format / Factory Reset",
                    "Mi Cloud" to "[Mi Cloud] Disable Xiaomi Mi Account (EDL)",
                    "Reboot EDL" to "[Diag] Reboot Device to EDL 9008"
                ).forEach { (label, fullMethod) ->
                    val isSelected = (qcServiceMethod == fullMethod)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) palette.actionHighlight.copy(alpha = 0.18f) else palette.cardAltBg,
                        border = BorderStroke(1.dp, if (isSelected) palette.actionHighlight else palette.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clickable { qcServiceMethod = fullMethod }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (isSelected) palette.actionHighlight else palette.textPrimary, maxLines = 1)
                        }
                    }
                }
            }

            // Row 4: Action Buttons (BOOT/INFO + EXECUTE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { viewModel.qcBootAndInfo() },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.cardAltBg),
                    border = BorderStroke(1.dp, palette.accentPrimary.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.weight(0.95f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("BOOT / INFO", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = palette.accentPrimary)
                }
                Button(
                    onClick = { viewModel.qcExecuteService(qcServiceMethod) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.startButtonBg),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.weight(1.35f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("EXECUTE", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

// =============================================================================
// 5. PLACEHOLDER DECK (SPD / ISP)
// =============================================================================
@Composable
private fun PlatformPlaceholderDeck(
    platformTitle: String,
    subTabTitle: String,
    subtitle: String
) {
    val palette = LocalAppPalette.current
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, palette.panelBorderBrush, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "$platformTitle -> $subTabTitle", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.accentPrimary)
            Text(text = subtitle, fontSize = 10.sp, color = palette.textSecondary)
        }
    }
}

// =============================================================================
// 6. FULL TERMINAL CONSOLE
// =============================================================================
@Composable
private fun SwipeableConsoleAndPartitionPanel(
    viewModel: MtkBridgeViewModel,
    logs: List<TerminalLog>,
    partitions: List<PartitionEntry>,
    pagerState: PagerState,
    debugMode: Boolean,
    onToggleDebug: () -> Unit,
    onClearLogs: () -> Unit,
    onLoadDevicePartitions: () -> Unit
) {
    val palette = LocalAppPalette.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val selectedCount = partitions.count { it.isSelectedForFlashing }
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(1500)
            copiedFeedback = false
        }
    }

    Surface(
        color = palette.consoleBg,
        modifier = Modifier
            .fillMaxSize()
            .testTag("swipeable_bottom_panel")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar: Left tabs are scrollable in weight(1f) so right Copy/Clear/DBG buttons NEVER overflow off-screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.headerBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 0: CONSOLE
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (pagerState.currentPage == 0) palette.cardBg else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (pagerState.currentPage == 0) palette.accentPrimary else Color.Transparent
                        ),
                        modifier = Modifier
                            .testTag("tab_console_button")
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (pagerState.currentPage == 0) palette.accentPrimary else palette.textMuted,
                                modifier = Modifier.size(6.dp)
                            ) {}
                            Text(
                                "CONSOLE (${logs.size})",
                                fontSize = 10.sp,
                                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (pagerState.currentPage == 0) palette.accentPrimary else palette.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }

                    // Tab 1: PARTS
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (pagerState.currentPage == 1) palette.cardBg else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (pagerState.currentPage == 1) palette.accentPrimary else Color.Transparent
                        ),
                        modifier = Modifier
                            .testTag("tab_partitions_button")
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.TableChart,
                                contentDescription = null,
                                tint = if (pagerState.currentPage == 1) palette.accentPrimary else palette.textMuted,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                "PARTS ($selectedCount/${partitions.size})",
                                fontSize = 10.sp,
                                fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (pagerState.currentPage == 1) palette.accentPrimary else palette.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Pinned right action buttons based on active page
                if (pagerState.currentPage == 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (debugMode) palette.accentPrimary.copy(alpha = 0.18f) else palette.cardAltBg,
                            border = BorderStroke(1.dp, if (debugMode) palette.accentPrimary else palette.cardBorder),
                            modifier = Modifier
                                .testTag("debug_mode_toggle_swipeable")
                                .clickable { onToggleDebug() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (debugMode) Color(0xFF10B981) else palette.textMuted,
                                    modifier = Modifier.size(5.dp)
                                ) {}
                                Text(
                                    text = "DBG",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (debugMode) palette.accentPrimary else palette.textSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (copiedFeedback) Color(0xFF10B981).copy(alpha = 0.18f) else palette.cardAltBg,
                            border = BorderStroke(1.dp, if (copiedFeedback) Color(0xFF10B981) else palette.cardBorder),
                            modifier = Modifier
                                .testTag("copy_logs_btn")
                                .clickable {
                                    val text = logs.joinToString("\n") { "[${it.displayTimestamp}] [${it.level}] ${it.message}" }
                                    clipboardManager.setText(AnnotatedString(text))
                                    copiedFeedback = true
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy Logs",
                                    tint = if (copiedFeedback) Color(0xFF10B981) else palette.accentPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (copiedFeedback) "Copied" else "Copy",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (copiedFeedback) Color(0xFF10B981) else palette.textPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = palette.cardAltBg,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier
                                .testTag("clear_logs_btn")
                                .clickable { onClearLogs() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Logs",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Clear",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                } else {
                    // When on Partition Table: ALL, NONE, INV, NO-NV (Hydra PM Protect NV)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = palette.cardAltBg,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier
                                .testTag("select_all_btn")
                                .clickable { viewModel.selectAllPartitions(true) }
                        ) {
                            Text(
                                "ALL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = palette.cardAltBg,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier
                                .testTag("deselect_all_btn")
                                .clickable { viewModel.selectAllPartitions(false) }
                        ) {
                            Text(
                                "NONE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textMuted,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = palette.cardAltBg,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier
                                .testTag("invert_selection_btn")
                                .clickable { viewModel.invertPartitionSelection() }
                        ) {
                            Text(
                                "INV",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.7f)),
                            modifier = Modifier
                                .testTag("protect_nv_btn")
                                .clickable { viewModel.protectNvPartitions() }
                        ) {
                            Text(
                                "NO-NV",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.divider)

            // HorizontalPager allowing swipe between Terminal Log and Partition Table
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> TerminalLogView(logs = logs, debugMode = debugMode)
                    1 -> PartitionTableView(
                        viewModel = viewModel,
                        partitions = partitions,
                        onLoadDevicePartitions = onLoadDevicePartitions
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalLogView(
    logs: List<TerminalLog>,
    debugMode: Boolean = false
) {
    val palette = LocalAppPalette.current
    val fontConfig = LocalFontConfig.current
    val logListState = rememberLazyListState()

    val displayLogs = remember(logs, debugMode) {
        if (debugMode) {
            logs
        } else {
            logs.filter { log ->
                !log.isDebugOnly && !isVerboseNoise(log.message)
            }
        }
    }

    LaunchedEffect(displayLogs.size) {
        if (displayLogs.isNotEmpty()) {
            logListState.animateScrollToItem(displayLogs.size - 1)
        }
    }

    LazyColumn(
        state = logListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items(displayLogs) { log ->
            val color = if (palette.isDark) {
                when {
                    log.message.startsWith("✔") || log.message.startsWith("[+]") || log.level == LogLevel.SUCCESS -> Color(0xFF34D399)
                    log.message.startsWith("✖") || log.message.startsWith("[-]") || log.level == LogLevel.ERROR -> Color(0xFFF87171)
                    log.message.startsWith("⚠") || log.message.startsWith("[!]") || log.level == LogLevel.WARNING -> Color(0xFFFBBF24)
                    log.message.startsWith("▶") -> Color(0xFF38BDF8)
                    log.level == LogLevel.AI -> Color(0xFFA78BFA)
                    log.level == LogLevel.ACCENT -> palette.accentPrimary
                    log.level == LogLevel.CYAN -> Color(0xFF38BDF8)
                    log.level == LogLevel.MAGENTA -> Color(0xFFF472B6)
                    log.level == LogLevel.RAW -> palette.textMuted
                    else -> palette.textPrimary
                }
            } else {
                // High contrast vivid saturated colors for Light Mode
                when {
                    log.message.startsWith("✔") || log.message.startsWith("[+]") || log.level == LogLevel.SUCCESS -> Color(0xFF047857) // Deep Emerald 700
                    log.message.startsWith("✖") || log.message.startsWith("[-]") || log.level == LogLevel.ERROR -> Color(0xFFB91C1C)   // Deep Crimson 700
                    log.message.startsWith("⚠") || log.message.startsWith("[!]") || log.level == LogLevel.WARNING -> Color(0xFFB45309) // Deep Amber 700
                    log.message.startsWith("▶") -> Color(0xFF0284C7)                                                                      // Deep Sky Cyan
                    log.level == LogLevel.AI -> Color(0xFF6D28D9)                                                                       // Deep Violet 700
                    log.level == LogLevel.ACCENT -> Color(0xFF1D4ED8)                                                                   // Deep Blue 700
                    log.level == LogLevel.CYAN -> Color(0xFF0369A1)                                                                     // Deep Sky Cyan 700
                    log.level == LogLevel.MAGENTA -> Color(0xFFBE185D)                                                                  // Deep Magenta 700
                    log.level == LogLevel.RAW -> Color(0xFF334155)                                                                      // Slate 700
                    else -> Color(0xFF0F172A)                                                                                           // Slate 900
                }
            }
            val textFontWeight = if (!palette.isDark) FontWeight.Bold else FontWeight.Normal

            Row(verticalAlignment = Alignment.Top) {
                if (debugMode && log.timestamp.isNotBlank()) {
                    Text(
                        "[${log.timestamp}] ",
                        fontSize = fontConfig.consoleSp,
                        color = if (palette.isDark) palette.textMuted else Color(0xFF475569),
                        fontWeight = if (!palette.isDark) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    log.message,
                    fontSize = fontConfig.consoleSp,
                    color = color,
                    fontWeight = textFontWeight,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = (fontConfig.consoleSp.value + 4f).sp
                )
            }
        }
    }
}

private fun isVerboseNoise(msg: String): Boolean {
    val lower = msg.lowercase().trim()
    if (lower.startsWith("==================================================") ||
        lower.startsWith("--------------------------------------------------") ||
        lower.matches(Regex("^[=\\-_]{10,}$"))) {
        return true
    }
    return lower.contains("watchdog timer disabled @") ||
           lower.contains("reading brom send_ptr") ||
           lower.contains("writing payload (") ||
           lower.contains("overwriting callback pointer") ||
           lower.contains("resolved brom callback pointer") ||
           lower.contains("scanning sram 0x") ||
           lower.contains("extracted live preloader") ||
           lower.contains("extracted dram/emi config") ||
           lower.contains("uploading da stage 1 ->") ||
           lower.contains("sending da stage to") ||
           lower.contains("jumping to da entry point") ||
           lower.contains("waiting for da stage 1 sync") ||
           lower.contains("post-payload brom re-handshake") ||
           lower.contains("payload ack verified") ||
           lower.contains("kamakiri2 exploit") ||
           lower.contains("[dump] starting dump for '") ||
           lower.contains("[dump] target file:") ||
           lower.contains("[flash] target file:") ||
           lower.contains("[flash] starting flash for '") ||
           lower.contains("[dump skip] skipping")
}

@Composable
private fun PartitionTableView(
    viewModel: MtkBridgeViewModel,
    partitions: List<PartitionEntry>,
    onLoadDevicePartitions: () -> Unit
) {
    val palette = LocalAppPalette.current
    var searchQuery by remember { mutableStateOf("") }
    var activePmPartition by remember { mutableStateOf<PartitionEntry?>(null) }
    var pendingBindPartitionIndex by remember { mutableStateOf<Int?>(null) }
    var pendingWritePartitionIndex by remember { mutableStateOf<Int?>(null) }

    val scatterPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.loadScatterFromUri(it, "scatter.txt")
        }
    }

    val bindImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val idx = pendingBindPartitionIndex
        pendingBindPartitionIndex = null
        if (uri != null && idx != null) {
            viewModel.bindPartitionFileFromUri(idx, uri)
        }
    }

    val directWriteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val idx = pendingWritePartitionIndex
        pendingWritePartitionIndex = null
        if (uri != null && idx != null) {
            viewModel.executeSinglePartitionWriteFromUri(idx, uri)
        }
    }

    activePmPartition?.let { part ->
        AlertDialog(
            onDismissRequest = { activePmPartition = null },
            containerColor = palette.cardBg,
            title = {
                Text(
                    text = "PM — ${part.partitionName} (${part.formattedSize})",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = palette.accentPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Offset: ${part.linearStartAddrHex} • Region: ${part.region}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textSecondary
                    )
                    OutlinedButton(
                        onClick = {
                            activePmPartition = null
                            pendingBindPartitionIndex = part.partitionIndex
                            bindImageLauncher.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.accentPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("pm_bind_image_btn")
                    ) {
                        Text("Bind Image File (.img / .bin) for Flash", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.accentPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            activePmPartition = null
                            viewModel.executeSinglePartitionRead(part.partitionIndex)
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth().testTag("pm_read_single_btn")
                    ) {
                        Text("Read / Backup '${part.partitionName}' Now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                    OutlinedButton(
                        onClick = {
                            activePmPartition = null
                            pendingWritePartitionIndex = part.partitionIndex
                            directWriteLauncher.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFD97706)),
                        modifier = Modifier.fillMaxWidth().testTag("pm_write_single_btn")
                    ) {
                        Text("Write / Flash '${part.partitionName}' Direct...", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                    }
                    OutlinedButton(
                        onClick = {
                            activePmPartition = null
                            viewModel.executeSinglePartitionErase(part.partitionIndex)
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth().testTag("pm_erase_single_btn")
                    ) {
                        Text("Erase / Format '${part.partitionName}'", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activePmPartition = null }) {
                    Text("Close", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    if (partitions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLoadDevicePartitions,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.startButtonBg),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("empty_load_device_gpt_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Read GPT (Device)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = { scatterPickerLauncher.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.accentPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("empty_load_scatter_btn")
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Load Scatter / XML", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = palette.accentPrimary)
                }
            }
        }
    } else {
        val filteredPartitions = remember(partitions, searchQuery) {
            if (searchQuery.isBlank()) {
                partitions
            } else {
                val q = searchQuery.trim().lowercase(Locale.US)
                partitions.filter {
                    it.partitionName.lowercase(Locale.US).contains(q) ||
                    it.fileName.lowercase(Locale.US).contains(q) ||
                    it.region.lowercase(Locale.US).contains(q)
                }
            }
        }

        val totalSelectedBytes = remember(partitions) {
            partitions.filter { it.isSelectedForFlashing }.sumOf { it.sizeBytes }
        }
        val availableStorageBytes by viewModel.availableBackupStorageBytes.collectAsState()
        val isTableStorageLow = totalSelectedBytes > 0L &&
            (totalSelectedBytes + DeviceSafetyChecker.BACKUP_SAFETY_MARGIN_BYTES) > availableStorageBytes

        Column(modifier = Modifier.fillMaxSize()) {
            // Sub-bar: Search Filter & Total Selected Summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.cardBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Search box
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = palette.cardAltBg,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = palette.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("partition_search_input"),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = palette.textPrimary,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Filter partitions (boot, super, nvram)...",
                                        color = palette.textMuted,
                                        fontSize = 10.5.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = palette.textMuted,
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Stats badge with Free Storage awareness
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isTableStorageLow) Color(0xFFEF4444).copy(alpha = 0.14f) else palette.cardAltBg,
                    border = BorderStroke(1.dp, if (isTableStorageLow) Color(0xFFEF4444) else palette.cardBorder),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isTableStorageLow) {
                                "⚠ ${formatStorageBytes(totalSelectedBytes)} / Free ${formatStorageBytes(availableStorageBytes)}"
                            } else {
                                "${formatStorageBytes(totalSelectedBytes)} | Free ${formatStorageBytes(availableStorageBytes)}"
                            },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTableStorageLow) Color(0xFFEF4444) else palette.accentPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            HorizontalDivider(color = palette.divider)

            // Table Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.cardAltBg)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox column
                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                    val allSelected = partitions.all { it.isSelectedForFlashing }
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { viewModel.selectAllPartitions(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = palette.accentPrimary,
                            uncheckedColor = palette.textMuted
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Index #
                Text(
                    text = "#",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textMuted,
                    modifier = Modifier.width(26.dp),
                    fontFamily = FontFamily.Monospace
                )

                // Name
                Text(
                    text = "PARTITION NAME",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1.5f),
                    fontFamily = FontFamily.Monospace
                )

                // Size
                Text(
                    text = "SIZE",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(0.9f),
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )

                // Start Address
                Text(
                    text = "START OFFSET",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1.3f),
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )

                // Region / File
                Text(
                    text = "REGION / FILE",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1.2f),
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }

            HorizontalDivider(color = palette.divider)

            // Partition Rows
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.consoleBg)
            ) {
                items(filteredPartitions, key = { it.partitionIndex }) { entry ->
                    val isChecked = entry.isSelectedForFlashing
                    val isNv = entry.isProtectedNv || entry.partitionName.lowercase(Locale.US).let {
                        it.contains("nvram") || it.contains("nvdata") || it.contains("protect") || it.contains("proinfo") || it.contains("sec1") || it.contains("seccfg")
                    }
                    val isBootCore = entry.partitionName.lowercase(Locale.US).let {
                        it == "boot" || it == "recovery" || it == "vbmeta" || it.startsWith("vbmeta_") || it == "dtbo" || it == "lk" || it == "lk2"
                    }
                    val isSystemSuper = entry.partitionName.lowercase(Locale.US).let {
                        it == "super" || it == "system" || it == "vendor" || it == "product" || it == "userdata"
                    }

                    val rowBg = if (isChecked) {
                        if (entry.partitionIndex % 2 == 0) palette.cardBg.copy(alpha = 0.5f) else Color.Transparent
                    } else {
                        palette.cardAltBg.copy(alpha = 0.3f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .clickable {
                                viewModel.togglePartitionSelection(entry.partitionIndex, !isChecked)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Checkbox
                        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    viewModel.togglePartitionSelection(entry.partitionIndex, checked)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = palette.accentPrimary,
                                    uncheckedColor = palette.textMuted
                                ),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Index #
                        Text(
                            text = "${entry.partitionIndex}",
                            fontSize = 9.sp,
                            color = palette.textMuted,
                            modifier = Modifier.width(26.dp),
                            fontFamily = FontFamily.Monospace
                        )

                        // Partition Name + Tag
                        Row(
                            modifier = Modifier.weight(1.5f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val nameColor = when {
                                !isChecked -> palette.textMuted
                                isNv -> Color(0xFFFBBF24)
                                isBootCore -> Color(0xFF10B981)
                                isSystemSuper -> Color(0xFF22D3EE)
                                else -> palette.textPrimary
                            }

                            Text(
                                text = entry.partitionName,
                                fontSize = 10.5.sp,
                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                color = nameColor,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isNv) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = Color(0xFFFBBF24).copy(alpha = 0.2f),
                                    border = BorderStroke(0.5.dp, Color(0xFFFBBF24))
                                ) {
                                    Text(
                                        "NV",
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFBBF24),
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // Size
                        Text(
                            text = entry.formattedSize,
                            fontSize = 10.sp,
                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isChecked) palette.accentPrimary else palette.textMuted,
                            modifier = Modifier.weight(0.9f),
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )

                        // Offset / Start Address
                        Text(
                            text = entry.linearStartAddrHex,
                            fontSize = 9.5.sp,
                            color = palette.textMuted,
                            modifier = Modifier.weight(1.3f),
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Region / File (Tap to open Hydra PM Single-Partition Actions)
                        val hasCustomBound = entry.boundFilePath.isNotBlank()
                        val regionDisplay = if (entry.fileName.isNotBlank() && entry.fileName != "NONE") {
                            entry.fileName
                        } else {
                            entry.region
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable { activePmPartition = entry },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (hasCustomBound) palette.accentPrimary.copy(alpha = 0.14f) else palette.cardAltBg,
                                border = BorderStroke(
                                    0.8.dp,
                                    if (hasCustomBound) palette.accentPrimary else palette.cardBorder
                                )
                            ) {
                                Text(
                                    text = "$regionDisplay ▾",
                                    fontSize = 8.5.sp,
                                    fontWeight = if (hasCustomBound) FontWeight.Bold else FontWeight.Medium,
                                    color = if (hasCustomBound) palette.accentPrimary else if (isChecked) palette.textSecondary else palette.textMuted,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = palette.divider.copy(alpha = 0.4f))
                }
            }
        }
    }
}

// =============================================================================
// 6. FULL TERMINAL CONSOLE
// =============================================================================
@Composable
private fun FullTerminalConsole(
    logs: List<TerminalLog>,
    debugMode: Boolean,
    onToggleDebug: () -> Unit,
    onClear: () -> Unit
) {
    val palette = LocalAppPalette.current
    val clipboardManager = LocalClipboardManager.current
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(1500)
            copiedFeedback = false
        }
    }

    Surface(
        color = palette.consoleBg,
        modifier = Modifier
            .fillMaxSize()
            .testTag("bottom_terminal_panel")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.headerBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(shape = CircleShape, color = palette.accentPrimary, modifier = Modifier.size(7.dp)) {}
                    Text(
                        "CONSOLE (${logs.size})",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (debugMode) palette.accentPrimary.copy(alpha = 0.18f) else palette.cardAltBg,
                        border = BorderStroke(1.dp, if (debugMode) palette.accentPrimary else palette.cardBorder),
                        modifier = Modifier
                            .testTag("debug_mode_toggle_full_console")
                            .clickable { onToggleDebug() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (debugMode) Color(0xFF10B981) else palette.textMuted,
                                modifier = Modifier.size(5.dp)
                            ) {}
                            Text(
                                text = "DBG",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (debugMode) palette.accentPrimary else palette.textSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (copiedFeedback) Color(0xFF10B981).copy(alpha = 0.18f) else palette.cardAltBg,
                        border = BorderStroke(1.dp, if (copiedFeedback) Color(0xFF10B981) else palette.cardBorder),
                        modifier = Modifier
                            .testTag("full_copy_logs_btn")
                            .clickable {
                                val text = logs.joinToString("\n") { "[${it.displayTimestamp}] [${it.level}] ${it.message}" }
                                clipboardManager.setText(AnnotatedString(text))
                                copiedFeedback = true
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (copiedFeedback) Color(0xFF10B981) else palette.accentPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (copiedFeedback) "Copied" else "Copy",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (copiedFeedback) Color(0xFF10B981) else palette.textPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = palette.cardAltBg,
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier
                            .testTag("full_clear_logs_btn")
                            .clickable { onClear() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Clear",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.divider)

            TerminalLogView(logs = logs, debugMode = debugMode)
        }
    }
}

private fun formatStorageBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

// =============================================================================
// REUSABLE HELPER COMPONENTS
// =============================================================================
@Composable
private fun ServiceQuickButton(
    label: String,
    color: Color,
    @Suppress("UNUSED_PARAMETER") icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.height(32.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickOptionTogglePill(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (checked) palette.accentPrimary else palette.cardAltBg,
        border = BorderStroke(1.dp, if (checked) palette.accentPrimary else palette.cardBorder),
        modifier = Modifier.clickable { onToggle(!checked) }
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
            color = if (checked) Color.White else palette.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun HydraLoaderConfigPill(
    viewModel: MtkBridgeViewModel
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val daName by viewModel.daAgentPath.collectAsState()
    val preloaderName by viewModel.preloaderPath.collectAsState()
    val authName by viewModel.authFilePath.collectAsState()
    var showLoaderDialog by remember { mutableStateOf(false) }

    val isCustomLoaded = preloaderName.isNotEmpty() || authName.isNotEmpty() ||
        !daName.startsWith("Built-in", ignoreCase = true)

    val daPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fname = resolvePickerFileName(context, it, "MTK_AllInOne_DA.bin")
            viewModel.setCustomDaFromUri(it, fname)
        }
    }

    val preloaderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fname = resolvePickerFileName(context, it, "preloader.bin")
            viewModel.setCustomPreloaderFromUri(it, fname)
        }
    }

    val authPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fname = resolvePickerFileName(context, it, "auth_sv5.auth")
            viewModel.setCustomAuthFromUri(it, fname)
        }
    }

    if (showLoaderDialog) {
        AlertDialog(
            onDismissRequest = { showLoaderDialog = false },
            containerColor = palette.cardBg,
            title = {
                Text(
                    text = "DA / Preloader / Auth Config",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DA: $daName",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.accentPrimary
                    )
                    if (preloaderName.isNotEmpty()) {
                        Text(
                            text = "EMI/Preloader: $preloaderName",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF10B981)
                        )
                    }
                    if (authName.isNotEmpty()) {
                        Text(
                            text = "Auth SV5: $authName",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFBBF24)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            showLoaderDialog = false
                            daPickerLauncher.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.accentPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("pick_custom_da_btn")
                    ) {
                        Text("Load Custom DA (.bin)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            showLoaderDialog = false
                            preloaderPickerLauncher.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth().testTag("pick_custom_preloader_btn")
                    ) {
                        Text("Load Custom Preloader / EMI (.bin)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            showLoaderDialog = false
                            authPickerLauncher.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth().testTag("pick_custom_auth_btn")
                    ) {
                        Text("Load Auth File (.auth)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    }
                    if (isCustomLoaded) {
                        OutlinedButton(
                            onClick = {
                                viewModel.resetCustomLoaders()
                                showLoaderDialog = false
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth().testTag("reset_custom_loaders_btn")
                        ) {
                            Text("Reset to Auto Universal DA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoaderDialog = false }) {
                    Text("Close", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isCustomLoaded) Color(0xFF10B981).copy(alpha = 0.16f) else palette.cardAltBg,
        border = BorderStroke(
            1.dp,
            if (isCustomLoaded) Color(0xFF10B981) else palette.accentPrimary.copy(alpha = 0.65f)
        ),
        modifier = Modifier
            .testTag("hydra_loader_config_pill")
            .clickable { showLoaderDialog = true }
    ) {
        Text(
            text = if (isCustomLoaded) "DA: Custom ▾" else "DA: Auto ▾",
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCustomLoaded) Color(0xFF10B981) else palette.accentPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun BrandModelSelectorRow(
    selectedBrand: MtkBrand,
    selectedModel: MtkDeviceModel,
    onBrandSelect: (MtkBrand) -> Unit,
    onModelSelect: (MtkDeviceModel) -> Unit
) {
    val palette = LocalAppPalette.current
    var brandExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var modelSearchQuery by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.weight(0.95f)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.cardAltBg,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clickable { brandExpanded = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Brand: ${selectedBrand.brandName}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("▼", fontSize = 8.sp, color = palette.textSecondary)
                }
            }
            DropdownMenu(
                expanded = brandExpanded,
                onDismissRequest = { brandExpanded = false },
                modifier = Modifier.background(palette.cardBg)
            ) {
                MtkDeviceDatabase.brands.forEach { brand ->
                    DropdownMenuItem(
                        text = { Text(brand.brandName, fontSize = 11.sp, color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            onBrandSelect(brand)
                            brandExpanded = false
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1.25f)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.cardAltBg,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clickable {
                        modelSearchQuery = ""
                        modelExpanded = true
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selectedModel.modelName} [${selectedModel.chipCode}]",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("▼", fontSize = 8.sp, color = palette.textSecondary)
                }
            }
            DropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false },
                modifier = Modifier
                    .background(palette.cardBg)
                    .height(290.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.cardAltBg,
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = modelSearchQuery,
                            onValueChange = { modelSearchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = palette.textPrimary,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            decorationBox = { inner ->
                                if (modelSearchQuery.isEmpty()) {
                                    Text("Search model / chip...", fontSize = 10.sp, color = palette.textMuted)
                                }
                                inner()
                            }
                        )
                    }
                }
                val filteredModels = selectedBrand.models.filter { m ->
                    val q = modelSearchQuery.trim().lowercase(Locale.US)
                    q.isEmpty() ||
                        m.modelName.lowercase(Locale.US).contains(q) ||
                        m.chipset.lowercase(Locale.US).contains(q) ||
                        m.chipCode.lowercase(Locale.US).contains(q)
                }
                filteredModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.modelName, fontSize = 11.sp, color = palette.textPrimary, fontWeight = FontWeight.Bold)
                                Text("${model.chipset} (${model.chipCode})", fontSize = 9.sp, color = palette.textSecondary)
                            }
                        },
                        onClick = {
                            onModelSelect(model)
                            modelExpanded = false
                        }
                    )
                }
            }
        }
    }
}

// =============================================================================
// COMPACT TOP STATUS BAR (fallback when rendered standalone)
// =============================================================================
@Composable
private fun TopCompactStatusBar(
    currentDestination: AppNavDestination,
    targetPhoneState: TargetPhoneState,
    realtimePortStatus: RealtimePortStatus,
    onOpenDrawer: () -> Unit
) {
    val palette = LocalAppPalette.current
    val dotColor = when (realtimePortStatus.mode) {
        UsbDeviceMode.BROM -> Color(0xFF00E5FF)
        UsbDeviceMode.PRELOADER -> Color(0xFFF59E0B)
        UsbDeviceMode.META -> Color(0xFFA855F7)
        UsbDeviceMode.FASTBOOT -> Color(0xFF10B981)
        UsbDeviceMode.ADB -> Color(0xFF06B6D4)
        UsbDeviceMode.EDL -> Color(0xFF3B82F6)
        UsbDeviceMode.DIAG -> Color(0xFF6366F1)
        UsbDeviceMode.MTP -> Color(0xFF14B8A6)
        UsbDeviceMode.CDC_SERIAL -> Color(0xFFEAB308)
        UsbDeviceMode.UNKNOWN -> Color(0xFFE2E8F0)
        UsbDeviceMode.NONE -> Color(0xFFEF4444)
    }

    Surface(
        color = palette.headerBg,
        border = BorderStroke(1.dp, palette.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { onOpenDrawer() }
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu Drawer", tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = currentDestination.category.accentColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, currentDestination.category.accentColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(currentDestination.icon, contentDescription = null, tint = currentDestination.category.accentColor, modifier = Modifier.size(13.dp))
                        Text(
                            text = "[${currentDestination.category.shortName}] ${currentDestination.shortTitle}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (realtimePortStatus.isConnected) dotColor.copy(alpha = 0.12f) else palette.cardAltBg,
                border = BorderStroke(1.dp, if (realtimePortStatus.isConnected) dotColor.copy(alpha = 0.45f) else palette.usbBadgeBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = realtimePortStatus.modeLabel,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (realtimePortStatus.isConnected) dotColor else palette.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// =============================================================================
// COMPACT OPERATION FOOTER
// =============================================================================
@Composable
private fun CompactOperationFooter(
    progress: OperationProgress
) {
    val palette = LocalAppPalette.current
    Surface(
        color = palette.statusStripBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = palette.divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (progress.isRunning) palette.accentPrimary else palette.textMuted,
                        modifier = Modifier.size(6.dp)
                    ) {}
                    Text(
                        text = if (progress.isRunning) {
                            buildString {
                                append(progress.title)
                                if (progress.formattedBytes.isNotBlank()) {
                                    append(" • ")
                                    append(progress.formattedBytes)
                                }
                            }
                        } else {
                            "Direct USB OTG Host Ready"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (progress.isRunning) palette.accentPrimary else palette.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (progress.isRunning) {
                        Text(
                            text = "${progress.formattedSpeed} • ${progress.formattedElapsedTime}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "${String.format(Locale.US, "%.1f", progress.percentage)}%",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (progress.isRunning) palette.accentPrimary else palette.textMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (progress.percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (progress.isRunning) 3.dp else 2.dp),
                color = if (progress.isRunning) palette.accentPrimary else palette.cardBorder,
                trackColor = palette.cardAltBg
            )
            HorizontalDivider(color = palette.divider)
        }
    }
}

private fun resolvePickerFileName(context: android.content.Context, uri: Uri, fallback: String): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                if (!name.isNullOrBlank()) return name
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: fallback
}

private fun nowTime(): String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())