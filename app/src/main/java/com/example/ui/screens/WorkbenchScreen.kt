package com.example.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CommandRepository
import com.example.data.FlashFileProcessor
import com.example.data.SparseChunkPlanner
import com.example.model.ConsoleLogEntry
import com.example.model.FlashStepStatus
import com.example.model.ParsedFlashStep
import com.example.model.ServiceActionItem
import com.example.model.ServiceBrand
import com.example.model.ServiceLogLevel
import com.example.model.ToolScreenMode
import com.example.ui.ServiceUiState
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.LocalFontConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WorkbenchScreen(
    uiState: ServiceUiState,
    availableActions: List<ServiceActionItem>,
    onSelectBrand: (ServiceBrand) -> Unit,
    onSelectAction: (ServiceActionItem) -> Unit,
    onSelectPartition: (String) -> Unit,
    onToggleVerity: () -> Unit,
    onToggleClearUserdata: () -> Unit,
    onToggleAutoReboot: () -> Unit,
    onToggleChecksum: () -> Unit,
    onToggleFastbootd: () -> Unit,
    onToggleStep: (String) -> Unit,
    onToggleAllSteps: (Boolean) -> Unit,
    onPickFile: () -> Unit,
    onPickFolder: () -> Unit,
    onScanPorts: () -> Unit,
    onStartPressed: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val fontConfig = LocalFontConfig.current
    val scope = rememberCoroutineScope()
    val flashPagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

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
            // Top Control Deck = 1/3 of screen (0.33f)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.33f)
                    .padding(horizontal = deckHorizontalPad, vertical = deckVerticalPad)
                    .border(1.dp, palette.panelBorderBrush, RoundedCornerShape(10.dp))
                    .testTag("top_options_panel"),
                shape = RoundedCornerShape(10.dp),
                color = palette.cardBg
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (uiState.screenMode) {
                        ToolScreenMode.ADB_INFO,
                        ToolScreenMode.ADB_UNLOCK,
                        ToolScreenMode.ADB_SERVICE,
                        ToolScreenMode.FASTBOOT_INFO,
                        ToolScreenMode.FASTBOOT_SERVICE,
                        ToolScreenMode.FASTBOOT_UNLOCK -> DropdownActionStartPanel(
                            uiState = uiState,
                            availableActions = availableActions,
                            onSelectBrand = onSelectBrand,
                            onSelectAction = onSelectAction,
                            onScanPorts = onScanPorts,
                            onStartPressed = onStartPressed
                        )
                        ToolScreenMode.FASTBOOT_FLASH -> FlashDropdownStartPanel(
                            uiState = uiState,
                            onSelectPartition = onSelectPartition,
                            onToggleClearUserdata = onToggleClearUserdata,
                            onToggleAutoReboot = onToggleAutoReboot,
                            onToggleVerity = onToggleVerity,
                            onToggleFastbootd = onToggleFastbootd,
                            onOpenPartitionTable = {
                                scope.launch { flashPagerState.animateScrollToPage(1) }
                            },
                            onPickFile = onPickFile,
                            onPickFolder = onPickFolder,
                            onStartPressed = onStartPressed
                        )
                        ToolScreenMode.CMD_TERMINAL,
                        ToolScreenMode.SETTINGS,
                        ToolScreenMode.ABOUT -> Unit
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = palette.statusStripBg
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
                        Text(
                            text = uiState.statusBarText,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f%%", uiState.progressPercent),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = palette.accentPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (uiState.isBusy && uiState.progressPercent > 0f) {
                        LinearProgressIndicator(
                            progress = { (uiState.progressPercent / 100f).coerceIn(0f, 1f) },
                            color = palette.accentPrimary,
                            trackColor = palette.cardBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    } else if (uiState.isBusy) {
                        LinearProgressIndicator(
                            color = palette.accentPrimary,
                            trackColor = palette.cardBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                    } else {
                        HorizontalDivider(color = palette.divider)
                    }
                }
            }

            // Bottom Terminal Log / Partition Table = 2/3 of screen (0.67f)
            if (uiState.screenMode == ToolScreenMode.FASTBOOT_FLASH) {
                val selectedFile = uiState.selectedFlashFile
                val totalParts = selectedFile?.steps?.size ?: 0
                val enabledParts = selectedFile?.steps?.count { it.enabled } ?: 0

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.67f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.headerBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PagerModePill(
                                icon = Icons.Default.Terminal,
                                label = "LIVE LOG (${uiState.consoleLogs.size})",
                                selected = flashPagerState.currentPage == 0,
                                onClick = { scope.launch { flashPagerState.animateScrollToPage(0) } },
                                modifier = Modifier.testTag("pager_tab_log")
                            )
                            PagerModePill(
                                icon = Icons.Default.TableChart,
                                label = "PARTITION TABLE ($enabledParts/$totalParts)",
                                selected = flashPagerState.currentPage == 1,
                                onClick = { scope.launch { flashPagerState.animateScrollToPage(1) } },
                                modifier = Modifier.testTag("pager_tab_partition_table")
                            )
                        }
                    }
                    HorizontalDivider(color = palette.divider)

                    HorizontalPager(
                        state = flashPagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("flash_bottom_pager")
                    ) { page ->
                        if (page == 0) {
                            LiveLogConsolePanel(
                                uiState = uiState,
                                consoleFontSp = fontConfig.consoleSp,
                                onClearLogs = onClearLogs,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PartitionTablePanel(
                                uiState = uiState,
                                onToggleStep = onToggleStep,
                                onToggleAllSteps = onToggleAllSteps,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            } else {
                LiveLogConsolePanel(
                    uiState = uiState,
                    consoleFontSp = fontConfig.consoleSp,
                    onClearLogs = onClearLogs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.67f)
                )
            }
        }
    }
}

@Composable
private fun PagerModePill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) palette.accentPrimary.copy(alpha = 0.16f) else palette.cardAltBg,
        border = BorderStroke(1.dp, if (selected) palette.accentPrimary else palette.cardBorder),
        modifier = modifier
            .height(28.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) palette.accentPrimary else palette.textSecondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) palette.accentPrimary else palette.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DropdownActionStartPanel(
    uiState: ServiceUiState,
    availableActions: List<ServiceActionItem>,
    onSelectBrand: (ServiceBrand) -> Unit,
    onSelectAction: (ServiceActionItem) -> Unit,
    onScanPorts: () -> Unit,
    onStartPressed: () -> Unit
) {
    val palette = LocalAppPalette.current
    var brandExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            SelectorDropdownBox(
                text = "Brand: ${uiState.selectedBrand.displayName}",
                textColor = palette.textPrimary,
                onClick = { brandExpanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("brand_dropdown")
            )
            DropdownMenu(
                expanded = brandExpanded,
                onDismissRequest = { brandExpanded = false }
            ) {
                ServiceBrand.entries.forEach { brand ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = brand.displayName,
                                fontWeight = if (brand == uiState.selectedBrand) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            brandExpanded = false
                            onSelectBrand(brand)
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            SelectorDropdownBox(
                text = uiState.selectedAction.title,
                textColor = palette.actionHighlight,
                onClick = { actionExpanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("action_dropdown")
            )
            DropdownMenu(
                expanded = actionExpanded,
                onDismissRequest = { actionExpanded = false },
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                availableActions.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = item.title,
                                fontWeight = if (item.id == uiState.selectedAction.id) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            actionExpanded = false
                            onSelectAction(item)
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.cardAltBg,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier
                    .weight(0.85f)
                    .height(40.dp)
                    .clickable { onScanPorts() }
                    .testTag("scan_ports_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan USB",
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Scan OTG",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onStartPressed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isBusy) Color(0xFFDC2626) else palette.startButtonBg,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1.45f)
                    .height(40.dp)
                    .testTag("start_action_button")
            ) {
                Icon(
                    imageVector = if (uiState.isBusy) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isBusy) "Stop" else "Start",
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (uiState.isBusy) {
                        "STOP"
                    } else {
                        "START (${uiState.selectedBrand.shortTag.uppercase(Locale.US)})"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FlashDropdownStartPanel(
    uiState: ServiceUiState,
    onSelectPartition: (String) -> Unit,
    onToggleClearUserdata: () -> Unit,
    onToggleAutoReboot: () -> Unit,
    onToggleVerity: () -> Unit,
    onToggleFastbootd: () -> Unit,
    onOpenPartitionTable: () -> Unit,
    onPickFile: () -> Unit,
    onPickFolder: () -> Unit,
    onStartPressed: () -> Unit
) {
    val palette = LocalAppPalette.current
    var partitionExpanded by remember { mutableStateOf(false) }

    val selectedFile = uiState.selectedFlashFile
    val totalParts = selectedFile?.steps?.size ?: 0
    val enabledParts = selectedFile?.steps?.count { it.enabled } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.cardAltBg,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier
                    .weight(1.35f)
                    .height(38.dp)
                    .clickable(enabled = !uiState.isBusy) { onPickFile() }
                    .testTag("pick_flash_file_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Load Firmware File",
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = selectedFile?.fileName ?: "Scatter / XML / .bat / .app / .img",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.cardAltBg,
                border = BorderStroke(1.dp, palette.accentSecondary.copy(alpha = 0.65f)),
                modifier = Modifier
                    .weight(0.75f)
                    .height(38.dp)
                    .clickable(enabled = !uiState.isBusy) { onPickFolder() }
                    .testTag("pick_flash_folder_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Load Firmware Folder",
                        tint = palette.accentSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Folder",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accentSecondary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1.35f)) {
                SelectorDropdownBox(
                    text = if (selectedFile != null && selectedFile.steps.size > 1) {
                        "Format: ${selectedFile.firmwareInfo.formatLabel}"
                    } else {
                        "Target: ${uiState.selectedPartition}"
                    },
                    textColor = palette.accentPrimary,
                    onClick = { partitionExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = partitionExpanded,
                    onDismissRequest = { partitionExpanded = false },
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    CommandRepository.supportedPartitions.forEach { part ->
                        DropdownMenuItem(
                            text = { Text(part, fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                onSelectPartition(part)
                                partitionExpanded = false
                            }
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.cardAltBg,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier
                    .weight(0.75f)
                    .height(38.dp)
                    .clickable { onOpenPartitionTable() }
                    .testTag("parts_count_button")
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$enabledParts/$totalParts Parts",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accentPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1.22f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OptionTogglePill(
                    label = "Clear Userdata",
                    checked = uiState.clearUserdata,
                    onClick = onToggleClearUserdata,
                    modifier = Modifier.testTag("toggle_clear_userdata")
                )
                OptionTogglePill(
                    label = "Auto Reboot",
                    checked = uiState.autoReboot,
                    onClick = onToggleAutoReboot,
                    modifier = Modifier.testTag("toggle_auto_reboot")
                )
                OptionTogglePill(
                    label = "Verity",
                    checked = uiState.disableVerity,
                    onClick = onToggleVerity
                )
                OptionTogglePill(
                    label = "Fastbootd",
                    checked = uiState.useFastbootd,
                    onClick = onToggleFastbootd
                )
            }

            Button(
                onClick = onStartPressed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isBusy) Color(0xFFDC2626) else palette.startButtonBg,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(0.82f)
                    .height(38.dp)
                    .testTag("execute_flash_button")
            ) {
                Icon(
                    imageVector = if (uiState.isBusy) Icons.Default.Stop else Icons.Default.Bolt,
                    contentDescription = if (uiState.isBusy) "Stop Flash" else "Start Flash",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (uiState.isBusy) "STOP" else "FLASH",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PartitionTablePanel(
    uiState: ServiceUiState,
    onToggleStep: (String) -> Unit,
    onToggleAllSteps: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val selectedFile = uiState.selectedFlashFile

    Surface(
        modifier = modifier.testTag("partition_table_panel"),
        color = palette.consoleBg
    ) {
        if (selectedFile == null || selectedFile.steps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "No Firmware Loaded",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textSecondary
                    )
                }
            }
            return@Surface
        }

        val steps = selectedFile.steps
        val selectableSteps = steps.filter { it.isFlashable }.ifEmpty { steps }
        val allSelected = selectableSteps.isNotEmpty() && selectableSteps.all { it.enabled }
        val enabledSteps = steps.filter { it.enabled }
        val selectedTotalBytes = enabledSteps.sumOf { it.dataSize }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.cardBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !uiState.isBusy) {
                            onToggleAllSteps(!allSelected)
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("select_all_partitions_checkbox")
                ) {
                    Icon(
                        imageVector = if (allSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = "Select All Partitions",
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (allSelected) "Deselect All" else "Select All",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }

                Text(
                    text = "${selectedFile.firmwareInfo.formatLabel} • ${enabledSteps.size}/${steps.size} • ${FlashFileProcessor.formatSize(selectedTotalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = palette.accentPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = palette.divider)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.statusStripBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(26.dp))
                Text(
                    text = "PARTITION",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = palette.textMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    text = "SIZE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = palette.textMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(0.65f)
                )
                Text(
                    text = "FILE / OFFSET",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = palette.textMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.15f)
                )
                Text(
                    text = "STATE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = palette.textMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(56.dp)
                )
            }
            HorizontalDivider(color = palette.divider)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(steps, key = { it.id }) { step ->
                    PartitionStepRow(
                        step = step,
                        enabledInteractive = !uiState.isBusy,
                        onToggle = { onToggleStep(step.id) }
                    )
                    HorizontalDivider(color = palette.divider.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun PartitionStepRow(
    step: ParsedFlashStep,
    enabledInteractive: Boolean,
    onToggle: () -> Unit
) {
    val palette = LocalAppPalette.current
    val isLarge = step.dataSize > SparseChunkPlanner.DEFAULT_SAFE_CHUNK_BYTES

    val statusColor = when (step.status) {
        FlashStepStatus.DONE -> Color(0xFF10B981)
        FlashStepStatus.FLASHING -> palette.accentPrimary
        FlashStepStatus.ERROR -> Color(0xFFEF4444)
        FlashStepStatus.QUEUED -> Color(0xFFF59E0B)
        FlashStepStatus.SKIPPED -> palette.textMuted
        FlashStepStatus.READY -> palette.textSecondary
    }
    val statusText = if (step.status == FlashStepStatus.FLASHING) {
        "${(step.progress * 100f).toInt()}%"
    } else {
        step.status.label
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabledInteractive) { onToggle() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("partition_row_${step.targetPartition}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (step.enabled) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = "Toggle ${step.targetPartition}",
                tint = if (step.enabled) palette.accentPrimary else palette.textMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1.1f)) {
                Text(
                    text = step.targetPartition,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (step.enabled) palette.textPrimary else palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (step.isSparse) {
                        MiniBadge("SPARSE", palette.accentSecondary)
                    }
                    if (isLarge) {
                        MiniBadge("SPLIT", Color(0xFFF59E0B))
                    }
                }
            }

            Text(
                text = if (step.dataSize > 0L) FlashFileProcessor.formatSize(step.dataSize) else "CMD",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = palette.textSecondary,
                maxLines = 1,
                modifier = Modifier.weight(0.65f)
            )

            Column(modifier = Modifier.weight(1.15f)) {
                Text(
                    text = step.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (step.sourceUriString == null && step.isFlashable && step.command.startsWith("fastboot flash")) {
                        Color(0xFFF59E0B)
                    } else {
                        palette.textSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (step.startAddressHex.isNotEmpty()) {
                    Text(
                        text = step.startAddressHex,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = statusColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                modifier = Modifier.width(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        maxLines = 1
                    )
                }
            }
        }

        if (step.status == FlashStepStatus.FLASHING) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { step.progress.coerceIn(0f, 1f) },
                color = palette.accentPrimary,
                trackColor = palette.cardBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun SelectorDropdownBox(
    text: String,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.cardAltBg,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = modifier
            .heightIn(min = 38.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun OptionTogglePill(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (checked) palette.accentPrimary else palette.cardAltBg,
        border = BorderStroke(
            1.dp,
            if (checked) palette.accentPrimary else palette.cardBorder
        ),
        modifier = modifier
            .height(30.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (checked) Color.White else palette.textSecondary,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LiveLogConsolePanel(
    uiState: ServiceUiState,
    consoleFontSp: TextUnit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(1500)
            copiedFeedback = false
        }
    }

    LaunchedEffect(uiState.consoleLogs.size) {
        if (uiState.consoleLogs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.consoleLogs.lastIndex)
        }
    }

    Surface(
        modifier = modifier.testTag("bottom_terminal_panel"),
        color = palette.consoleBg
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(palette.accentPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE LOG CONSOLE (${uiState.consoleLogs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.accentPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (copiedFeedback) Color(0xFF10B981).copy(alpha = 0.18f) else palette.cardAltBg,
                        border = BorderStroke(1.dp, if (copiedFeedback) Color(0xFF10B981) else palette.cardBorder),
                        modifier = Modifier
                            .testTag("copy_logs_button")
                            .clickable {
                                val allLogs = uiState.consoleLogs.joinToString("\n") {
                                    "[${it.timestamp}] ${it.message}"
                                }
                                clipboard.setText(AnnotatedString(allLogs))
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
                                contentDescription = "Copy Console Logs",
                                tint = if (copiedFeedback) Color(0xFF10B981) else palette.accentPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (copiedFeedback) "Copied" else "Copy",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (copiedFeedback) Color(0xFF10B981) else palette.textPrimary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = palette.cardAltBg,
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier
                            .testTag("clear_logs_button")
                            .clickable { onClearLogs() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Console Logs",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Clear",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.divider)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("terminal_lazy_list"),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(uiState.consoleLogs, key = { it.id }) { entry ->
                    ConsoleTimestampRow(
                        entry = entry,
                        consoleFontSp = consoleFontSp
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsoleTimestampRow(
    entry: ConsoleLogEntry,
    consoleFontSp: TextUnit
) {
    val palette = LocalAppPalette.current
    val msgColor = if (palette.isDark) {
        when (entry.level) {
            ServiceLogLevel.INIT -> palette.accentPrimary
            ServiceLogLevel.CMD -> palette.accentSecondary
            ServiceLogLevel.USB -> Color(0xFF60A5FA)
            ServiceLogLevel.OKAY -> Color(0xFF34D399)
            ServiceLogLevel.INFO -> palette.textPrimary
            ServiceLogLevel.WARN -> Color(0xFFFACC15)
            ServiceLogLevel.FAIL -> Color(0xFFEF4444)
        }
    } else {
        when (entry.level) {
            ServiceLogLevel.INIT -> palette.accentPrimary
            ServiceLogLevel.CMD -> palette.accentSecondary
            ServiceLogLevel.USB -> Color(0xFF1D4ED8)
            ServiceLogLevel.OKAY -> Color(0xFF059669)
            ServiceLogLevel.INFO -> palette.textPrimary
            ServiceLogLevel.WARN -> Color(0xFFB45309)
            ServiceLogLevel.FAIL -> Color(0xFFDC2626)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[${entry.timestamp}]",
            fontSize = consoleFontSp,
            fontFamily = FontFamily.Monospace,
            color = palette.textMuted
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = entry.message,
            fontSize = consoleFontSp,
            fontFamily = FontFamily.Monospace,
            color = msgColor,
            modifier = Modifier.weight(1f)
        )
    }
}
