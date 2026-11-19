package com.example

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.UsbPortController
import com.example.model.AppNavDestination
import com.example.model.AppOperatingMode
import com.example.model.MainPlatformCategory
import com.example.model.ThemeMode
import com.example.model.ToolScreenMode
import com.example.protocol.RealtimePortStatus
import com.example.protocol.TargetPhoneState
import com.example.protocol.TargetPhoneUsbManager
import com.example.protocol.UsbDeviceMode
import com.example.security.LicenseManager
import com.example.ui.ServiceUiState
import com.example.ui.ServiceViewModel
import com.example.ui.components.AppModeSelectionDialog
import com.example.ui.components.StartupSafetyNoticeDialog
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.FullscreenCliTerminalScreen
import com.example.ui.screens.LicenseGateScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UnlockToolFlashScreen
import com.example.ui.screens.WorkbenchScreen
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.MyApplicationTheme
import com.example.util.DeviceSafetyChecker
import com.example.util.DeviceSafetySnapshot
import com.example.viewmodel.MtkBridgeViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mtkViewModel: MtkBridgeViewModel by viewModels()
    private val serviceViewModel: ServiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LicenseManager.initializeAndVerifySession(this)
        handleUsbDeviceIntent(intent)
        setContent {
            val serviceUiState by serviceViewModel.uiState.collectAsStateWithLifecycle()
            val operationProgress by mtkViewModel.operationProgress.collectAsStateWithLifecycle()
            val licenseSession by LicenseManager.sessionState.collectAsStateWithLifecycle()
            val isDark = serviceUiState.themeMode == ThemeMode.DARK
            val view = LocalView.current

            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDark
                    controller.isAppearanceLightNavigationBars = !isDark
                }
            }

            // Keep screen on during active firmware dumping / flashing
            DisposableEffect(operationProgress.isRunning) {
                val window = (view.context as? Activity)?.window
                if (operationProgress.isRunning) {
                    window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Notification permission for background progress service
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {}
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val context = LocalContext.current
            var hasAgreedNotice by remember {
                mutableStateOf(DeviceSafetyChecker.hasAgreedStartupNotice(context))
            }
            var safetySnapshot by remember {
                mutableStateOf(
                    DeviceSafetyChecker.collectSnapshot(
                        context = context,
                        storagePath = mtkViewModel.storageManager.getBackupDirectory()
                    )
                )
            }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        safetySnapshot = DeviceSafetyChecker.collectSnapshot(
                            context = context,
                            storagePath = mtkViewModel.storageManager.getBackupDirectory()
                        )
                        mtkViewModel.refreshBackupStorageInfo()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            MyApplicationTheme(
                themeMode = serviceUiState.themeMode,
                themeDesign = serviceUiState.themeDesign,
                fontSize = serviceUiState.fontSize
            ) {
                val operatingMode by mtkViewModel.operatingMode.collectAsStateWithLifecycle()
                val showStartupModePrompt by mtkViewModel.showStartupModePrompt.collectAsStateWithLifecycle()
                val loaderManifest by mtkViewModel.loaderManifest.collectAsStateWithLifecycle()

                if (!hasAgreedNotice) {
                    StartupSafetyNoticeDialog(
                        snapshot = safetySnapshot,
                        isFirstInstallRequireAgree = true,
                        onAgreeOrDismiss = {
                            DeviceSafetyChecker.setStartupNoticeAgreed(context, true)
                            hasAgreedNotice = true
                        }
                    )
                }

                if (!licenseSession.isUnlocked) {
                    LicenseGateScreen(
                        sessionState = licenseSession,
                        onLicenseActivated = {},
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalAppPalette.current.screenBg)
                    )
                } else {
                    if (hasAgreedNotice && showStartupModePrompt) {
                        AppModeSelectionDialog(
                            currentMode = operatingMode,
                            manifestVersion = loaderManifest?.version ?: 105,
                            entryCount = loaderManifest?.entries?.size ?: 0,
                            onSelectOnline = {
                                mtkViewModel.selectOperatingMode(AppOperatingMode.ONLINE, triggerSyncIfOffline = false)
                            },
                            onSelectOfflineAndSync = {
                                mtkViewModel.selectOperatingMode(AppOperatingMode.OFFLINE, triggerSyncIfOffline = true)
                            },
                            onSelectOfflineOnly = {
                                mtkViewModel.selectOperatingMode(AppOperatingMode.OFFLINE, triggerSyncIfOffline = false)
                            },
                            onDismiss = {
                                mtkViewModel.dismissOperatingModePrompt()
                            }
                        )
                    }

                    MtkMainApp(
                        viewModel = mtkViewModel,
                        serviceViewModel = serviceViewModel,
                        serviceUiState = serviceUiState
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbDeviceIntent(intent)
    }

    private fun handleUsbDeviceIntent(intent: Intent?) {
        if (intent == null) return
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
            serviceViewModel.scanUsbPorts(userInitiated = false)
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (device != null) {
                lifecycleScope.launch {
                    try {
                        val usb = mtkViewModel.targetPhoneUsb
                        if (usb.usbManager.hasPermission(device)) {
                            usb.connectDevice(device)
                        } else if (usb.detectDeviceMode(device) in TargetPhoneUsbManager.AUTO_PROMPT_MODES) {
                            usb.requestDevicePermission(device, force = false)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }
}

@Composable
fun MtkMainApp(
    viewModel: MtkBridgeViewModel,
    serviceViewModel: ServiceViewModel,
    serviceUiState: ServiceUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentDestination by viewModel.currentDestination.collectAsState()
    val targetPhoneState by viewModel.targetPhoneState.collectAsState()
    val realtimePortStatus by viewModel.realtimePortStatus.collectAsState()

    fun navigateToDestination(dest: AppNavDestination) {
        viewModel.navigateTo(dest)
        dest.toToolScreenMode()?.let { serviceMode ->
            serviceViewModel.navigateToMode(serviceMode)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            navigateToDestination(AppNavDestination.FASTBOOT_FLASH)
            serviceViewModel.onFilePicked(uri)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            navigateToDestination(AppNavDestination.FASTBOOT_FLASH)
            serviceViewModel.onFolderPicked(treeUri)
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED,
                    UsbPortController.ACTION_USB_PERMISSION -> {
                        serviceViewModel.scanUsbPorts(userInitiated = false)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbPortController.ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = !drawerState.isOpen && currentDestination != AppNavDestination.MTK_FLASH) {
        navigateToDestination(AppNavDestination.MTK_FLASH)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = palette.drawerBg,
                drawerContentColor = palette.textPrimary,
                windowInsets = WindowInsets.safeDrawing,
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .widthIn(max = 315.dp)
            ) {
            val drawerOperatingMode by viewModel.operatingMode.collectAsState()
            UnifiedToolDrawerContent(
                    currentDestination = currentDestination,
                    serviceUiState = serviceUiState,
                    operatingMode = drawerOperatingMode,
                    onOpenModeSwitch = {
                        scope.launch { drawerState.close() }
                        viewModel.openOperatingModePrompt()
                    },
                    onSelectDestination = { dest ->
                        navigateToDestination(dest)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("mtk_app_scaffold"),
            containerColor = palette.screenBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                ToolHeaderTopBar(
                    currentDestination = currentDestination,
                    targetPhoneState = targetPhoneState,
                    realtimePortStatus = realtimePortStatus,
                    serviceUiState = serviceUiState,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onCycleMode = {
                        val cycleDestinations = listOf(
                            AppNavDestination.MTK_FLASH,
                            AppNavDestination.MTK_BACKUP,
                            AppNavDestination.MTK_SERVICE,
                            AppNavDestination.QC_FLASH,
                            AppNavDestination.ADB_INFO,
                            AppNavDestination.ADB_UNLOCK,
                            AppNavDestination.ADB_SERVICE,
                            AppNavDestination.FASTBOOT_FLASH,
                            AppNavDestination.FASTBOOT_INFO,
                            AppNavDestination.FASTBOOT_SERVICE,
                            AppNavDestination.FASTBOOT_UNLOCK,
                            AppNavDestination.CMD_TERMINAL
                        )
                        val idx = cycleDestinations.indexOf(currentDestination)
                        val next = if (idx >= 0) {
                            cycleDestinations[(idx + 1) % cycleDestinations.size]
                        } else {
                            AppNavDestination.MTK_FLASH
                        }
                        navigateToDestination(next)
                    },
                    onUsbBadgeClick = {
                        viewModel.targetPhoneUsb.refreshRealtimePortStatus()
                        val st = viewModel.realtimePortStatus.value
                        val msg = if (st.isConnected) {
                            "Real-Time Mode: ${st.modeLabel}\nDevice: ${st.deviceName.ifBlank { "Attached USB Phone" }}\nVID:PID: ${st.vidHex}:${st.pidHex}"
                        } else {
                            "No USB Device Connected\n(Connect target phone via USB OTG cable)"
                        }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        if (currentDestination.toToolScreenMode() != null) {
                            serviceViewModel.requestActivePortPermission()
                        } else {
                            viewModel.scanTargetPhone()
                        }
                    },
                    onToggleTheme = {
                        serviceViewModel.toggleTheme()
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                val contentModifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()

                when (currentDestination) {
                AppNavDestination.CMD_TERMINAL -> FullscreenCliTerminalScreen(
                    uiState = serviceUiState,
                    onTerminalInputChange = { serviceViewModel.updateTerminalInput(it) },
                    onSubmitCommand = { serviceViewModel.submitTerminalCommand(it) },
                    modifier = contentModifier
                )
                AppNavDestination.SETTINGS_PAGE -> SettingsScreen(
                    uiState = serviceUiState,
                    onSelectThemeMode = { serviceViewModel.setThemeMode(it) },
                    onSelectThemeDesign = { serviceViewModel.setThemeDesign(it) },
                    onSelectFontSize = { serviceViewModel.setFontSize(it) },
                    onToggleSound = { serviceViewModel.setSoundEnabled(it) },
                    mtkViewModel = viewModel,
                    modifier = contentModifier
                )
                AppNavDestination.ABOUT_PAGE -> AboutScreen(
                    modifier = contentModifier
                )
                AppNavDestination.ADB_INFO,
                AppNavDestination.ADB_UNLOCK,
                AppNavDestination.ADB_SERVICE,
                AppNavDestination.FASTBOOT_FLASH,
                AppNavDestination.FASTBOOT_INFO,
                AppNavDestination.FASTBOOT_SERVICE,
                AppNavDestination.FASTBOOT_UNLOCK -> WorkbenchScreen(
                    uiState = serviceUiState,
                    availableActions = serviceViewModel.getAvailableActions(),
                    onSelectBrand = { serviceViewModel.setBrand(it) },
                    onSelectAction = { serviceViewModel.setSelectedAction(it) },
                    onSelectPartition = { serviceViewModel.setPartition(it) },
                    onToggleVerity = { serviceViewModel.toggleDisableVerity() },
                    onToggleClearUserdata = { serviceViewModel.toggleClearUserdata() },
                    onToggleAutoReboot = { serviceViewModel.toggleAutoReboot() },
                    onToggleChecksum = { serviceViewModel.toggleChecksum() },
                    onToggleFastbootd = { serviceViewModel.toggleUseFastbootd() },
                    onToggleStep = { serviceViewModel.toggleFlashStep(it) },
                    onToggleAllSteps = { serviceViewModel.toggleAllFlashSteps(it) },
                    onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onPickFolder = { folderPickerLauncher.launch(null) },
                    onScanPorts = { serviceViewModel.scanUsbPorts(userInitiated = true) },
                    onStartPressed = { serviceViewModel.onStartPressed() },
                    onClearLogs = { serviceViewModel.clearLogs() },
                    modifier = contentModifier
                )
                else -> UnlockToolFlashScreen(
                    viewModel = viewModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    showTopBar = false,
                    modifier = contentModifier
                )
            }
            }
        }
    }
}

@Composable
private fun ToolHeaderTopBar(
    currentDestination: AppNavDestination,
    targetPhoneState: TargetPhoneState,
    realtimePortStatus: RealtimePortStatus,
    serviceUiState: ServiceUiState,
    onOpenDrawer: () -> Unit,
    onCycleMode: () -> Unit,
    onUsbBadgeClick: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val palette = LocalAppPalette.current
    val isTerminal = currentDestination == AppNavDestination.CMD_TERMINAL
    val barBg = if (isTerminal) palette.terminalBg else palette.headerBg

    val serviceMode = currentDestination.toToolScreenMode()
    val badgeBorderColor = when {
        serviceMode?.isAdbSection == true -> palette.badgeAdb
        serviceMode == ToolScreenMode.FASTBOOT_FLASH -> palette.badgeFlash
        serviceMode?.isFastbootSection == true -> palette.badgeFastboot
        serviceMode == ToolScreenMode.CMD_TERMINAL -> palette.badgeTerminal
        serviceMode == ToolScreenMode.SETTINGS -> palette.badgeSetting
        serviceMode == ToolScreenMode.ABOUT -> palette.badgeAbout
        else -> currentDestination.category.accentColor
    }
    val badgeTitle = serviceMode?.badgeTitle
        ?: "[${currentDestination.category.shortName}] ${currentDestination.shortTitle}"

    Surface(
        color = barBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("drawer_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Menu",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBorderColor.copy(alpha = if (palette.isDark) 0.14f else 0.10f),
                        border = BorderStroke(1.dp, badgeBorderColor),
                        modifier = Modifier
                            .heightIn(min = 28.dp)
                            .clickable { onCycleMode() }
                            .testTag("active_mode_badge")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = badgeTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    val badgeBg = when (realtimePortStatus.mode) {
                        UsbDeviceMode.NONE -> palette.cardAltBg
                        else -> dotColor.copy(alpha = 0.12f)
                    }
                    val badgeBorder = when (realtimePortStatus.mode) {
                        UsbDeviceMode.NONE -> palette.usbBadgeBorder
                        else -> dotColor.copy(alpha = 0.45f)
                    }
                    val textColor = when (realtimePortStatus.mode) {
                        UsbDeviceMode.NONE -> palette.textSecondary
                        else -> dotColor
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg,
                        border = BorderStroke(1.dp, badgeBorder),
                        modifier = Modifier
                            .heightIn(min = 28.dp)
                            .clickable { onUsbBadgeClick() }
                            .testTag("usb_otg_host_badge")
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
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = textColor,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!isTerminal) {
                        Spacer(modifier = Modifier.width(2.dp))

                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("topbar_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (palette.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Light or Dark Theme",
                                tint = if (palette.isDark) Color(0xFFFACC15) else palette.textPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = palette.divider)
        }
    }
}

@Composable
private fun UnifiedToolDrawerContent(
    currentDestination: AppNavDestination,
    serviceUiState: ServiceUiState,
    operatingMode: AppOperatingMode,
    onOpenModeSwitch: () -> Unit,
    onSelectDestination: (AppNavDestination) -> Unit
) {
    val palette = LocalAppPalette.current
    // Accordion: Only expand the currently active category by default.
    // Tapping a category closes all other sections automatically so the user doesn't have to close them manually.
    var expandedCategory by remember(currentDestination.category) {
        mutableStateOf<MainPlatformCategory?>(currentDestination.category)
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .systemBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NATIVE UNLOCK TOOL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                val modeColor = if (operatingMode == AppOperatingMode.ONLINE) Color(0xFF06B6D4) else Color(0xFF10B981)
                Surface(
                    color = modeColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, modeColor),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { onOpenModeSwitch() }
                ) {
                    Text(
                        text = if (operatingMode == AppOperatingMode.ONLINE) "ONLINE" else "OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        color = modeColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    color = palette.accentPrimary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, palette.accentPrimary),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "PRO v3.1",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accentPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = palette.divider)

            MainPlatformCategory.entries.forEach { category ->
                val subTabs = AppNavDestination.entries.filter { it.category == category }
                val isCurrentCategory = currentDestination.category == category

                if (category == MainPlatformCategory.TERMINAL ||
                    category == MainPlatformCategory.SETTINGS ||
                    category == MainPlatformCategory.ABOUT
                ) {
                    val dest = subTabs.first()
                    val iconTint = when (category) {
                        MainPlatformCategory.TERMINAL -> palette.badgeTerminal
                        MainPlatformCategory.SETTINGS -> palette.badgeSetting
                        else -> palette.badgeAbout
                    }
                    val tag = when (category) {
                        MainPlatformCategory.TERMINAL -> "drawer_terminal_cli"
                        MainPlatformCategory.SETTINGS -> "drawer_settings"
                        else -> "drawer_about"
                    }
                    DrawerPrimaryCard(
                        iconTint = iconTint,
                        selected = currentDestination == dest,
                        title = category.title,
                        subtitle = category.subtitle,
                        onClick = { onSelectDestination(dest) },
                        tag = tag
                    )
                } else {
                    val sectionAccent = when (category) {
                        MainPlatformCategory.ADB -> palette.badgeAdb
                        MainPlatformCategory.FASTBOOT -> palette.badgeFastboot
                        else -> category.accentColor
                    }
                    val isExpanded = expandedCategory == category
                    val headerTag = when (category) {
                        MainPlatformCategory.ADB -> "drawer_adb_header"
                        MainPlatformCategory.FASTBOOT -> "drawer_fastboot_header"
                        else -> "drawer_${category.name.lowercase()}_header"
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.cardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isCurrentCategory) sectionAccent else palette.cardBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategory = if (isExpanded) null else category
                                    }
                                    .padding(vertical = 4.dp)
                                    .testTag(headerTag),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(sectionAccent)
                                        )
                                        Text(
                                            text = category.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = palette.textPrimary
                                        )
                                        if (category.isPlaceholder) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = palette.cardAltBg
                                            ) {
                                                Text(
                                                    text = "SOON",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = palette.textMuted,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle ${category.title}",
                                    tint = sectionAccent
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HorizontalDivider(color = palette.divider)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    subTabs.forEach { subTab ->
                                        val subTint = when (subTab) {
                                            AppNavDestination.FASTBOOT_FLASH -> palette.badgeFlash
                                            AppNavDestination.ADB_UNLOCK, AppNavDestination.FASTBOOT_UNLOCK -> palette.actionHighlight
                                            AppNavDestination.ADB_SERVICE -> palette.accentSecondary
                                            AppNavDestination.FASTBOOT_SERVICE -> palette.accentPrimary
                                            else -> sectionAccent
                                        }
                                        DrawerSubItem(
                                            accentColor = subTint,
                                            title = subTab.title,
                                            icon = subTab.icon,
                                            subtitle = subTab.subtitle,
                                            selected = currentDestination == subTab,
                                            onClick = { onSelectDestination(subTab) },
                                            tag = "drawer_${subTab.name.lowercase()}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column {
            HorizontalDivider(color = palette.divider)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Native libusb + OTG Engine",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textMuted
                )
                Text(
                    text = serviceUiState.themeDesign.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accentPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DrawerSubItem(
    accentColor: Color,
    title: String,
    icon: ImageVector,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val palette = LocalAppPalette.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) palette.cardAltBg else palette.cardBg.copy(alpha = 0.45f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else palette.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = accentColor.copy(alpha = if (selected) 0.22f else 0.12f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (selected) palette.textPrimary else palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 9.5.sp,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
private fun DrawerPrimaryCard(
    iconTint: Color,
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    val palette = LocalAppPalette.current

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) palette.cardAltBg else palette.cardBg,
        border = BorderStroke(1.dp, if (selected) iconTint else palette.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(iconTint)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
