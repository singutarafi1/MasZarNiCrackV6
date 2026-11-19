package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEffectManager
import com.example.audio.ToolSoundManager
import com.example.data.CommandRepository
import com.example.data.FlashFileProcessor
import com.example.data.UsbPortController
import com.example.model.ActionGroup
import com.example.model.AppFontSize
import com.example.model.ConsoleLogEntry
import com.example.model.FlashFileType
import com.example.model.FlashStepStatus
import com.example.model.ParsedFlashStep
import com.example.model.SelectedFlashFile
import com.example.model.ServiceActionItem
import com.example.model.ServiceBrand
import com.example.model.ServiceLogLevel
import com.example.model.TerminalLine
import com.example.model.ThemeDesign
import com.example.model.ThemeMode
import com.example.model.ToolScreenMode
import com.example.model.UsbPortInfo
import com.example.model.UsbProtocolType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class ServiceUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val themeDesign: ThemeDesign = ThemeDesign.CYBER_NEON,
    val fontSize: AppFontSize = AppFontSize.MEDIUM,
    val soundEnabled: Boolean = true,
    val screenMode: ToolScreenMode = ToolScreenMode.FASTBOOT_INFO,
    val usbPorts: List<UsbPortInfo> = emptyList(),
    val selectedPortName: String? = null,
    val selectedBrand: ServiceBrand = ServiceBrand.UNIVERSAL,
    val selectedAction: ServiceActionItem = CommandRepository.allActions.first {
        !it.isAdb && it.group == ActionGroup.INFO
    },
    val selectedPartition: String = CommandRepository.supportedPartitions.first(),
    val disableVerity: Boolean = true,
    val clearUserdata: Boolean = false,
    val autoReboot: Boolean = true,
    val checksumVerify: Boolean = true,
    val useFastbootd: Boolean = false,
    val selectedFlashFile: SelectedFlashFile? = null,
    val isBusy: Boolean = false,
    val statusBarText: String = "USB OTG Ready",
    val progressPercent: Float = 0f,
    val currentFlashingPartition: String = "",
    val currentChunkLabel: String = "",
    val consoleLogs: List<ConsoleLogEntry> = emptyList(),
    val terminalInput: String = "",
    val terminalLines: List<TerminalLine> = emptyList(),
    val terminalHistory: List<String> = emptyList()
)

class ServiceViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.applicationContext.getSharedPreferences(
        "fastdeck_otg_prefs",
        Context.MODE_PRIVATE
    )
    private val usbController = UsbPortController(application.applicationContext)
    private val soundManager = SoundEffectManager()
    private val logSequence = AtomicLong(1L)
    private val terminalSequence = AtomicLong(1L)

    private var activeOperationJob: Job? = null
    private var hasCompletedInitialUsbScan = false
    private var knownPortsByName: Map<String, UsbPortInfo> = emptyMap()

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<ServiceUiState> = _uiState.asStateFlow()

    init {
        val enabled = _uiState.value.soundEnabled
        soundManager.isEnabled = enabled
        ToolSoundManager.isEnabled = enabled
        scanUsbPorts(userInitiated = false)
    }

    private fun loadInitialState(): ServiceUiState {
        val savedMode = prefs.getString("theme_mode", ThemeMode.DARK.name)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DARK
        val savedDesign = prefs.getString("theme_design", ThemeDesign.CYBER_NEON.name)
            ?.let { runCatching { ThemeDesign.valueOf(it) }.getOrNull() }
            ?: ThemeDesign.CYBER_NEON
        val savedFont = prefs.getString("font_size", AppFontSize.MEDIUM.name)
            ?.let { runCatching { AppFontSize.valueOf(it) }.getOrNull() }
            ?: AppFontSize.MEDIUM
        val savedSound = prefs.getBoolean("sound_enabled", true)

        return ServiceUiState(
            themeMode = savedMode,
            themeDesign = savedDesign,
            fontSize = savedFont,
            soundEnabled = savedSound
        )
    }

    fun getAvailableActions(
        mode: ToolScreenMode = _uiState.value.screenMode,
        brand: ServiceBrand = _uiState.value.selectedBrand
    ): List<ServiceActionItem> {
        val modeFiltered = when (mode) {
            ToolScreenMode.ADB_INFO -> CommandRepository.allActions.filter {
                it.isAdb && it.group == ActionGroup.INFO
            }
            ToolScreenMode.ADB_UNLOCK -> CommandRepository.allActions.filter {
                it.isAdb && it.group == ActionGroup.UNLOCK
            }
            ToolScreenMode.ADB_SERVICE -> CommandRepository.allActions.filter {
                it.isAdb && it.group == ActionGroup.SERVICE
            }
            ToolScreenMode.FASTBOOT_INFO -> CommandRepository.allActions.filter {
                !it.isAdb && it.group == ActionGroup.INFO
            }
            ToolScreenMode.FASTBOOT_UNLOCK -> CommandRepository.allActions.filter {
                !it.isAdb && it.group == ActionGroup.UNLOCK
            }
            ToolScreenMode.FASTBOOT_SERVICE,
            ToolScreenMode.FASTBOOT_FLASH -> CommandRepository.allActions.filter {
                !it.isAdb && it.group == ActionGroup.SERVICE
            }
            ToolScreenMode.CMD_TERMINAL,
            ToolScreenMode.SETTINGS,
            ToolScreenMode.ABOUT -> emptyList()
        }
        val brandFiltered = modeFiltered.filter { it.brand == brand }
        return brandFiltered.ifEmpty {
            modeFiltered.filter { it.brand == ServiceBrand.UNIVERSAL }.ifEmpty { modeFiltered }
        }
    }

    fun toggleTheme() {
        val next = if (_uiState.value.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        setThemeMode(next)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setThemeDesign(design: ThemeDesign) {
        prefs.edit().putString("theme_design", design.name).apply()
        _uiState.update { it.copy(themeDesign = design) }
    }

    fun setFontSize(fontSize: AppFontSize) {
        prefs.edit().putString("font_size", fontSize.name).apply()
        _uiState.update { it.copy(fontSize = fontSize) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
        soundManager.isEnabled = enabled
        ToolSoundManager.isEnabled = enabled
        _uiState.update { it.copy(soundEnabled = enabled) }
    }

    fun navigateToMode(mode: ToolScreenMode) {
        val actions = getAvailableActions(mode = mode, brand = _uiState.value.selectedBrand)
        _uiState.update { state ->
            val nextAction = if (actions.isNotEmpty() && state.selectedAction !in actions) {
                actions.first()
            } else {
                state.selectedAction
            }
            state.copy(
                screenMode = mode,
                selectedAction = nextAction
            )
        }
    }

    fun setBrand(brand: ServiceBrand) {
        val available = getAvailableActions(mode = _uiState.value.screenMode, brand = brand)
        _uiState.update { state ->
            state.copy(
                selectedBrand = brand,
                selectedAction = available.firstOrNull() ?: state.selectedAction
            )
        }
    }

    fun setSelectedAction(action: ServiceActionItem) {
        _uiState.update {
            it.copy(selectedAction = action)
        }
    }

    fun setPartition(partitionLabel: String) {
        val cleanPart = partitionLabel.substringBefore(" ").lowercase(Locale.US)
        _uiState.update { state ->
            val updatedFile = state.selectedFlashFile?.let { file ->
                if (file.steps.size == 1 && cleanPart != "auto") {
                    file.copy(
                        steps = file.steps.map {
                            it.copy(
                                targetPartition = cleanPart,
                                command = "fastboot flash $cleanPart ${file.fileName}"
                            )
                        }
                    )
                } else {
                    file
                }
            }
            state.copy(
                selectedPartition = partitionLabel,
                selectedFlashFile = updatedFile
            )
        }
    }

    fun toggleDisableVerity() {
        _uiState.update { it.copy(disableVerity = !it.disableVerity) }
    }

    fun toggleClearUserdata() {
        _uiState.update { it.copy(clearUserdata = !it.clearUserdata) }
    }

    fun toggleAutoReboot() {
        _uiState.update { it.copy(autoReboot = !it.autoReboot) }
    }

    fun toggleChecksum() {
        _uiState.update { it.copy(checksumVerify = !it.checksumVerify) }
    }

    fun toggleUseFastbootd() {
        _uiState.update { it.copy(useFastbootd = !it.useFastbootd) }
    }

    fun toggleFlashStep(stepId: String) {
        _uiState.update { state ->
            val file = state.selectedFlashFile ?: return@update state
            val updated = file.steps.map { s ->
                if (s.id == stepId) {
                    val nextEnabled = !s.enabled
                    s.copy(
                        enabled = nextEnabled,
                        status = if (nextEnabled) FlashStepStatus.READY else FlashStepStatus.SKIPPED,
                        progress = 0f
                    )
                } else {
                    s
                }
            }
            state.copy(selectedFlashFile = file.copy(steps = updated))
        }
    }

    fun toggleAllFlashSteps(selectAll: Boolean) {
        _uiState.update { state ->
            val file = state.selectedFlashFile ?: return@update state
            val hasExplicitFlashable = file.steps.any { it.isFlashable }
            val updated = file.steps.map { s ->
                val enableTarget = if (selectAll) {
                    if (hasExplicitFlashable) s.isFlashable else true
                } else {
                    false
                }
                s.copy(
                    enabled = enableTarget,
                    status = if (enableTarget) FlashStepStatus.READY else FlashStepStatus.SKIPPED,
                    progress = 0f
                )
            }
            state.copy(selectedFlashFile = file.copy(steps = updated))
        }
    }

    fun scanUsbPorts(userInitiated: Boolean = true) {
        viewModelScope.launch {
            if (userInitiated) {
                clearLogs()
            }
            val ports = withContext(Dispatchers.IO) {
                usbController.scanPorts()
            }
            val currentMap = ports.associateBy { it.deviceName }
            val newlyAttached = ports.filter { !knownPortsByName.containsKey(it.deviceName) }
            val detached = knownPortsByName.values.filter { !currentMap.containsKey(it.deviceName) }

            if (hasCompletedInitialUsbScan) {
                if (newlyAttached.isNotEmpty()) {
                    soundManager.playPortAttached()
                } else if (detached.isNotEmpty()) {
                    soundManager.playPortDetached()
                }
            }

            detached.forEach { removed ->
                appendLog(
                    tag = "USB",
                    message = "Port Disconnected: ${removed.productName}",
                    level = ServiceLogLevel.WARN
                )
            }

            newlyAttached.forEach { p ->
                appendLog(
                    tag = "USB",
                    message = "Port Connected: ${p.productName} [${p.protocolType.label}]",
                    level = ServiceLogLevel.USB
                )
            }

            ports.filter { !it.hasPermission }.forEach { p ->
                usbController.requestPermission(p.deviceName)
            }

            knownPortsByName = currentMap
            hasCompletedInitialUsbScan = true

            val first = ports.firstOrNull()
            _uiState.update {
                it.copy(
                    usbPorts = ports,
                    selectedPortName = first?.deviceName,
                    statusBarText = if (first != null) {
                        "Connected: ${first.productName} (${first.protocolType.label})"
                    } else {
                        "USB OTG Ready"
                    }
                )
            }

            if (userInitiated && ports.isEmpty() && detached.isEmpty()) {
                appendLog(
                    tag = "USB",
                    message = "No USB OTG device detected.",
                    level = ServiceLogLevel.INFO
                )
            }
        }
    }

    fun requestActivePortPermission() {
        val port = _uiState.value.usbPorts.firstOrNull()
        if (port != null) {
            usbController.requestPermission(port.deviceName)
        } else {
            scanUsbPorts(userInitiated = true)
        }
    }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    statusBarText = "Parsing firmware..."
                )
            }
            val context = getApplication<Application>().applicationContext
            val rawPart = _uiState.value.selectedPartition.substringBefore(" ").lowercase(Locale.US)
            val fallbackPart = if (rawPart == "auto") "boot" else rawPart

            val parsed = withContext(Dispatchers.IO) {
                FlashFileProcessor.inspectAndParseUri(context, uri, fallbackPart)
            }
            applyParsedFirmware(parsed)
        }
    }

    fun onFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    statusBarText = "Scanning firmware folder..."
                )
            }
            val context = getApplication<Application>().applicationContext
            val rawPart = _uiState.value.selectedPartition.substringBefore(" ").lowercase(Locale.US)
            val fallbackPart = if (rawPart == "auto") "boot" else rawPart

            val parsed = withContext(Dispatchers.IO) {
                FlashFileProcessor.inspectAndParseFolderTree(context, treeUri, fallbackPart)
            }
            if (parsed != null) {
                applyParsedFirmware(parsed)
            } else {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusBarText = "No firmware files found in folder"
                    )
                }
                appendLog(
                    tag = "FW",
                    message = "No scatter.txt, rawprogram*.xml, flash.bat, UPDATE.APP, or .img found in folder.",
                    level = ServiceLogLevel.WARN
                )
            }
        }
    }

    fun applyParsedFirmware(parsed: SelectedFlashFile) {
        val enabledCount = parsed.steps.count { it.enabled }
        _uiState.update {
            it.copy(
                selectedFlashFile = parsed,
                isBusy = false,
                progressPercent = 0f,
                currentFlashingPartition = "",
                currentChunkLabel = "",
                statusBarText = "${parsed.firmwareInfo.formatLabel}: ${parsed.fileName} ($enabledCount/${parsed.steps.size})",
                screenMode = ToolScreenMode.FASTBOOT_FLASH
            )
        }

        val info = parsed.firmwareInfo
        appendLog(
            tag = "FW",
            message = "FW Format: ${info.formatLabel} | Package: ${parsed.fileName}",
            level = ServiceLogLevel.OKAY
        )
        appendLog(
            tag = "FW",
            message = "Platform: ${info.chipsetOrPlatform} | Target: ${info.projectOrBoard} | Storage: ${info.storageType}",
            level = ServiceLogLevel.INFO
        )
        appendLog(
            tag = "FW",
            message = "Partitions: $enabledCount/${parsed.steps.size} selected | Total Size: ${parsed.formattedSize} | Sector: ${info.sectorSizeBytes}B",
            level = ServiceLogLevel.INFO
        )
        info.extraMetadataLines.forEach { extra ->
            appendLog(tag = "FW", message = extra, level = ServiceLogLevel.INFO)
        }
        if (info.largePartitionNames.isNotEmpty()) {
            appendLog(
                tag = "SPLIT",
                message = "Large Partitions (${info.largePartitionNames.joinToString(", ")}) -> Auto Sparse/Chunk Split Ready (128MB/chunk).",
                level = ServiceLogLevel.USB
            )
        }
        if (info.missingImageCount > 0 && !info.isFolderPackage) {
            appendLog(
                tag = "FW",
                message = "Manifest parsed (${info.missingImageCount} image payloads external). Load Firmware Folder to attach .img files.",
                level = ServiceLogLevel.WARN
            )
        }
    }

    fun onStartPressed() {
        if (_uiState.value.isBusy) {
            stopActiveOperation()
            return
        }
        val state = _uiState.value
        when (state.screenMode) {
            ToolScreenMode.FASTBOOT_FLASH -> executeDirectUsbFlash()
            ToolScreenMode.CMD_TERMINAL -> submitTerminalCommand(state.terminalInput)
            ToolScreenMode.ADB_INFO,
            ToolScreenMode.ADB_UNLOCK,
            ToolScreenMode.ADB_SERVICE,
            ToolScreenMode.FASTBOOT_INFO,
            ToolScreenMode.FASTBOOT_SERVICE,
            ToolScreenMode.FASTBOOT_UNLOCK -> {
                executeServiceAction(state.selectedAction, state.selectedBrand)
            }
            ToolScreenMode.SETTINGS,
            ToolScreenMode.ABOUT -> Unit
        }
    }

    fun stopActiveOperation() {
        val job = activeOperationJob
        activeOperationJob = null
        job?.cancel()
        if (_uiState.value.isBusy) {
            soundManager.playActionStop()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    statusBarText = "Operation Stopped"
                )
            }
            appendLog(
                tag = "STOP",
                message = "Operation stopped.",
                level = ServiceLogLevel.WARN
            )
        }
    }

    fun updateTerminalInput(input: String) {
        _uiState.update { it.copy(terminalInput = input) }
    }

    /**
     * Executes pure Termux-style CLI commands strictly inside the dedicated Terminal screen.
     */
    fun submitTerminalCommand(rawCmd: String) {
        if (_uiState.value.isBusy) return
        val cmd = rawCmd.trim()
        _uiState.update { it.copy(terminalInput = "") }

        if (cmd.isEmpty()) {
            appendTerminalLine("~ $ ", isCommand = true)
            return
        }

        if (cmd.equals("clear", ignoreCase = true) || cmd.equals("cls", ignoreCase = true)) {
            _uiState.update { it.copy(terminalLines = emptyList()) }
            return
        }

        appendTerminalLine("~ $ $cmd", isCommand = true)
        _uiState.update { state ->
            val updatedHistory = if (state.terminalHistory.lastOrNull() == cmd) {
                state.terminalHistory
            } else {
                (state.terminalHistory + cmd).takeLast(50)
            }
            state.copy(terminalHistory = updatedHistory)
        }

        if (cmd.equals("help", ignoreCase = true)) {
            appendTerminalLine("fastboot devices | fastboot <cmd> | adb devices | adb shell <cmd> | adb reboot | clear")
            return
        }

        activeOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }

            try {
                withContext(Dispatchers.IO) {
                    val lower = cmd.lowercase(Locale.US)
                    val ports = usbController.scanPorts()
                    val activePort = ports.firstOrNull {
                        if (lower.startsWith("fastboot")) {
                            it.protocolType == UsbProtocolType.FASTBOOT
                        } else if (lower.startsWith("adb")) {
                            it.protocolType == UsbProtocolType.ADB
                        } else {
                            true
                        }
                    } ?: ports.firstOrNull()

                    if (activePort != null) {
                        _uiState.update { it.copy(usbPorts = ports, selectedPortName = activePort.deviceName) }
                    }

                    when {
                        lower == "fastboot devices" || lower == "fastboot devices -l" -> {
                            val fbPorts = ports.filter { it.protocolType == UsbProtocolType.FASTBOOT }
                            if (fbPorts.isEmpty()) {
                                ports.forEach { p ->
                                    appendTerminalLine("${p.serialNumber}\t${p.protocolType.label}")
                                }
                            } else {
                                fbPorts.forEach { p ->
                                    appendTerminalLine("${p.serialNumber}\tfastboot")
                                }
                            }
                        }

                        lower == "adb devices" || lower == "adb devices -l" -> {
                            appendTerminalLine("List of devices attached")
                            val adbPorts = ports.filter { it.protocolType == UsbProtocolType.ADB }
                            adbPorts.forEach { p ->
                                val status = if (p.hasPermission) "device" else "unauthorized"
                                appendTerminalLine("${p.serialNumber}\t$status")
                                if (!p.hasPermission) {
                                    usbController.requestPermission(p.deviceName)
                                }
                            }
                        }

                        lower.startsWith("fastboot ") -> {
                            val portName = activePort?.deviceName
                            if (portName == null) {
                                appendTerminalLine("< waiting for any device >", isError = true)
                            } else {
                                val tokens = cmd.split(Regex("\\s+"))
                                val flashIdx = tokens.indexOfFirst { it.equals("flash", ignoreCase = true) }
                                val loadedFile = _uiState.value.selectedFlashFile
                                if (flashIdx >= 0 && flashIdx + 1 < tokens.size && loadedFile != null) {
                                    val targetPart = tokens[flashIdx + 1]
                                    val uri = Uri.parse(loadedFile.uriString)
                                    usbController.flashBinaryStreamOverUsb(
                                        deviceName = portName,
                                        uri = uri,
                                        partition = targetPart,
                                        dataOffset = 0L,
                                        dataSize = loadedFile.fileSizeBytes,
                                        disableVerity = _uiState.value.disableVerity,
                                        verboseWireLogs = false,
                                        onProgress = {},
                                        onLog = { msg, lvl ->
                                            appendTerminalLine(msg, isError = lvl == ServiceLogLevel.FAIL)
                                        }
                                    )
                                } else {
                                    val wireList = toFastbootWireProtocolList(cmd)
                                    usbController.executeFastbootWireCommands(
                                        deviceName = portName,
                                        wireCommands = wireList,
                                        verboseWireLogs = false,
                                        onLog = { msg, lvl ->
                                            appendTerminalLine(msg, isError = lvl == ServiceLogLevel.FAIL)
                                        }
                                    )
                                }
                            }
                        }

                        lower == "adb" || lower.startsWith("adb ") -> {
                            val portName = activePort?.deviceName
                            if (portName == null) {
                                appendTerminalLine("error: no devices/emulators found", isError = true)
                            } else {
                                usbController.executeAdbOverUsbOtg(
                                    deviceName = portName,
                                    command = cmd,
                                    verboseWireLogs = false,
                                    onLog = { msg, lvl ->
                                        appendTerminalLine(msg, isError = lvl == ServiceLogLevel.FAIL)
                                    }
                                )
                            }
                        }

                        else -> {
                            val portName = activePort?.deviceName
                            if (portName != null && activePort.protocolType == UsbProtocolType.ADB) {
                                usbController.executeAdbOverUsbOtg(
                                    deviceName = portName,
                                    command = cmd,
                                    verboseWireLogs = false,
                                    onLog = { msg, lvl ->
                                        appendTerminalLine(msg, isError = lvl == ServiceLogLevel.FAIL)
                                    }
                                )
                            } else {
                                usbController.executeLocalShell(
                                    command = cmd,
                                    onLog = { msg, lvl ->
                                        appendTerminalLine(msg, isError = lvl == ServiceLogLevel.FAIL)
                                    }
                                )
                            }
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun appendTerminalLine(text: String, isCommand: Boolean = false, isError: Boolean = false) {
        val entry = TerminalLine(
            id = terminalSequence.getAndIncrement(),
            text = text,
            isCommand = isCommand,
            isError = isError
        )
        _uiState.update { s ->
            s.copy(terminalLines = (s.terminalLines + entry).takeLast(500))
        }
    }

    fun executeDirectUsbFlash() {
        if (_uiState.value.isBusy) return
        clearLogs()
        val state = _uiState.value
        val file = state.selectedFlashFile ?: run {
            appendLog("FLASH", "Load a firmware file or folder first.", ServiceLogLevel.WARN)
            return
        }
        val enabledSteps = file.steps.filter { it.enabled }
        if (enabledSteps.isEmpty()) {
            appendLog("FLASH", "No partitions selected in Partition Table.", ServiceLogLevel.WARN)
            return
        }
        val portName = state.selectedPortName ?: run {
            appendLog("FLASH", "No USB OTG device connected.", ServiceLogLevel.FAIL)
            return
        }

        updateFlashSteps { step ->
            if (step.enabled) {
                step.copy(status = FlashStepStatus.QUEUED, progress = 0f)
            } else {
                step.copy(status = FlashStepStatus.SKIPPED, progress = 0f)
            }
        }

        soundManager.playActionStart()
        activeOperationJob = viewModelScope.launch {
            var completedNormally = false
            try {
                _uiState.update {
                    it.copy(
                        isBusy = true,
                        progressPercent = 0f,
                        currentFlashingPartition = "",
                        currentChunkLabel = "",
                        statusBarText = "Starting Flash (${enabledSteps.size} partitions)..."
                    )
                }
                appendLog(
                    tag = "FLASH",
                    message = "Flash Start: ${file.fileName} | Selected: ${enabledSteps.size} | ClearUserdata: ${if (state.clearUserdata) "ON" else "OFF"} | AutoReboot: ${if (state.autoReboot) "ON" else "OFF"}",
                    level = ServiceLogLevel.CMD
                )

                val totalWeightBytes = enabledSteps.sumOf { it.dataSize.coerceAtLeast(1024L * 1024L) }
                    .coerceAtLeast(1L)
                var finishedWeightBytes = 0L
                var allSucceeded = true

                withContext(Dispatchers.IO) {
                    if (state.useFastbootd) {
                        appendLog("FLASH", "Switching to Fastbootd...", ServiceLogLevel.USB)
                        usbController.executeFastbootWireCommands(
                            deviceName = portName,
                            wireCommands = listOf("reboot-fastboot"),
                            verboseWireLogs = false
                        ) { msg, lvl ->
                            appendLog("FLASH", msg, lvl)
                        }
                    }

                    for (idx in enabledSteps.indices) {
                        coroutineContext.ensureActive()
                        val step = enabledSteps[idx]
                        val stepWeight = step.dataSize.coerceAtLeast(1024L * 1024L)
                        var currentChunkSuffix = ""
                        var lastLoggedPctMilestone = 0

                        updateSingleFlashStep(step.id, FlashStepStatus.FLASHING, 0f)
                        _uiState.update {
                            it.copy(
                                currentFlashingPartition = step.targetPartition,
                                currentChunkLabel = "",
                                statusBarText = "[${idx + 1}/${enabledSteps.size}] Flashing ${step.targetPartition} (0%)..."
                            )
                        }
                        appendLog(
                            tag = "FLASH",
                            message = "[${idx + 1}/${enabledSteps.size}] Writing ${step.targetPartition} (${FlashFileProcessor.formatSize(step.dataSize)}) — 0%",
                            level = ServiceLogLevel.CMD
                        )

                        val resolvedUriStr = step.sourceUriString
                            ?: file.uriString.takeIf {
                                file.fileType == FlashFileType.UPDATE_APP ||
                                    file.fileType == FlashFileType.IMAGE ||
                                    file.fileType == FlashFileType.RAW
                            }

                        val stepOk: Boolean = when {
                            resolvedUriStr != null && step.dataSize > 0L -> {
                                val stepUri = Uri.parse(resolvedUriStr)
                                usbController.flashBinaryStreamOverUsb(
                                    deviceName = portName,
                                    uri = stepUri,
                                    partition = step.targetPartition,
                                    dataOffset = step.dataOffset,
                                    dataSize = step.dataSize,
                                    disableVerity = state.disableVerity,
                                    verboseWireLogs = false,
                                    isCancelled = { activeOperationJob?.isActive != true },
                                    onChunkInfo = { cIdx, cTotal, _ ->
                                        currentChunkSuffix = if (cTotal > 1) "Chunk $cIdx/$cTotal" else ""
                                        _uiState.update { it.copy(currentChunkLabel = currentChunkSuffix) }
                                    },
                                    onProgress = { ratio ->
                                        val clamped = ratio.coerceIn(0f, 1f)
                                        val partPct = (clamped * 100f).toInt()
                                        val globalPct = ((finishedWeightBytes + stepWeight * clamped) / totalWeightBytes.toFloat()) * 100f

                                        updateSingleFlashStep(step.id, FlashStepStatus.FLASHING, clamped)
                                        _uiState.update {
                                            it.copy(
                                                progressPercent = globalPct.coerceIn(0f, 100f),
                                                statusBarText = "[${idx + 1}/${enabledSteps.size}] ${step.targetPartition}: $partPct% ${if (currentChunkSuffix.isNotEmpty()) "• $currentChunkSuffix" else ""}".trim()
                                            )
                                        }

                                        val milestone = (partPct / 25) * 25
                                        if (milestone in listOf(25, 50, 75) && milestone > lastLoggedPctMilestone) {
                                            lastLoggedPctMilestone = milestone
                                            appendLog(
                                                tag = "PROG",
                                                message = "  ↳ ${step.targetPartition}: $milestone% ${if (currentChunkSuffix.isNotEmpty()) "($currentChunkSuffix)" else ""}".trim(),
                                                level = ServiceLogLevel.USB
                                            )
                                        }
                                    },
                                    onLog = { msg, lvl -> appendLog("USB", msg, lvl) }
                                )
                            }

                            step.command.startsWith("fastboot flash", ignoreCase = true) -> {
                                appendLog(
                                    tag = "SKIP",
                                    message = "  ↳ Payload missing for ${step.targetPartition} (${step.sourceName}). Load Firmware Folder.",
                                    level = ServiceLogLevel.WARN
                                )
                                false
                            }

                            else -> {
                                val wireCmds = toFastbootWireProtocolList(step.command)
                                usbController.executeFastbootWireCommands(
                                    deviceName = portName,
                                    wireCommands = wireCmds,
                                    verboseWireLogs = false,
                                    onLog = { msg, lvl -> appendLog("USB", msg, lvl) }
                                )
                            }
                        }

                        finishedWeightBytes += stepWeight
                        if (stepOk) {
                            updateSingleFlashStep(step.id, FlashStepStatus.DONE, 1f)
                            appendLog(
                                tag = "OKAY",
                                message = "[${idx + 1}/${enabledSteps.size}] ${step.targetPartition} — 100% DONE",
                                level = ServiceLogLevel.OKAY
                            )
                        } else {
                            allSucceeded = false
                            updateSingleFlashStep(step.id, FlashStepStatus.ERROR, 0f)
                            appendLog(
                                tag = "FAIL",
                                message = "[${idx + 1}/${enabledSteps.size}] ${step.targetPartition} — FAILED",
                                level = ServiceLogLevel.FAIL
                            )
                        }
                    }

                    if (state.clearUserdata) {
                        coroutineContext.ensureActive()
                        _uiState.update { it.copy(statusBarText = "Wiping userdata & metadata...") }
                        appendLog("WIPE", "Clearing userdata & metadata...", ServiceLogLevel.CMD)
                        usbController.executeFastbootWireCommands(
                            deviceName = portName,
                            wireCommands = listOf("erase:userdata", "erase:metadata"),
                            verboseWireLogs = false
                        ) { msg, lvl ->
                            appendLog("WIPE", msg, lvl)
                        }
                    }

                    if (state.autoReboot) {
                        coroutineContext.ensureActive()
                        _uiState.update { it.copy(statusBarText = "Rebooting device...") }
                        appendLog("FLASH", "Auto Rebooting device...", ServiceLogLevel.INFO)
                        usbController.executeFastbootWireCommands(
                            deviceName = portName,
                            wireCommands = listOf("reboot"),
                            verboseWireLogs = false
                        ) { msg, lvl ->
                            appendLog("USB", msg, lvl)
                        }
                    } else {
                        appendLog("FLASH", "Auto Reboot OFF — staying in Fastboot mode.", ServiceLogLevel.INFO)
                    }
                }
                completedNormally = allSucceeded
                appendLog(
                    tag = "DONE",
                    message = if (allSucceeded) "All selected partitions flashed (100%)." else "Flash finished with errors.",
                    level = if (allSucceeded) ServiceLogLevel.OKAY else ServiceLogLevel.WARN
                )
            } catch (c: CancellationException) {
                throw c
            } finally {
                if (completedNormally) {
                    soundManager.playActionStop()
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progressPercent = if (completedNormally) 100f else it.progressPercent,
                        currentFlashingPartition = "",
                        currentChunkLabel = "",
                        statusBarText = if (completedNormally) "Flash Completed (100%)" else "USB OTG Ready"
                    )
                }
            }
        }
    }

    private fun updateFlashSteps(transform: (ParsedFlashStep) -> ParsedFlashStep) {
        _uiState.update { state ->
            val file = state.selectedFlashFile ?: return@update state
            state.copy(
                selectedFlashFile = file.copy(
                    steps = file.steps.map(transform)
                )
            )
        }
    }

    private fun updateSingleFlashStep(stepId: String, status: FlashStepStatus, progress: Float) {
        updateFlashSteps { step ->
            if (step.id == stepId) step.copy(status = status, progress = progress) else step
        }
    }

    private fun executeServiceAction(action: ServiceActionItem, brand: ServiceBrand) {
        if (_uiState.value.isBusy) return
        val cmd = action.command.trim()
        if (cmd.isEmpty()) return

        clearLogs()
        soundManager.playActionStart()
        activeOperationJob = viewModelScope.launch {
            var completedNormally = false
            try {
                _uiState.update {
                    it.copy(
                        isBusy = true,
                        statusBarText = "Running: ${action.title}",
                        progressPercent = 0f
                    )
                }
                appendLog(
                    tag = "START",
                    message = "[${brand.shortTag}] ${action.title}",
                    level = ServiceLogLevel.CMD
                )

                val state = _uiState.value
                val portName = state.selectedPortName

                withContext(Dispatchers.IO) {
                    when {
                        cmd.startsWith("fastboot", ignoreCase = true) -> {
                            if (portName == null) {
                                appendLog(
                                    tag = "FAIL",
                                    message = "No Fastboot USB OTG device connected.",
                                    level = ServiceLogLevel.FAIL
                                )
                            } else {
                                val wireList = toFastbootWireProtocolList(cmd)
                                usbController.executeFastbootWireCommands(
                                    deviceName = portName,
                                    wireCommands = wireList,
                                    verboseWireLogs = false,
                                    onLog = { msg, lvl -> appendLog("FB", msg, lvl) }
                                )
                            }
                        }
                        else -> {
                            if (portName != null) {
                                usbController.executeAdbOverUsbOtg(
                                    deviceName = portName,
                                    command = cmd,
                                    verboseWireLogs = false,
                                    onLog = { msg, lvl -> appendLog("ADB", msg, lvl) }
                                )
                            } else {
                                appendLog(
                                    tag = "FAIL",
                                    message = "No ADB USB OTG device connected.",
                                    level = ServiceLogLevel.FAIL
                                )
                            }
                        }
                    }
                }
                completedNormally = true
                appendLog(
                    tag = "DONE",
                    message = "${action.title} finished.",
                    level = ServiceLogLevel.OKAY
                )
            } catch (c: CancellationException) {
                throw c
            } finally {
                if (completedNormally) {
                    soundManager.playActionStop()
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusBarText = "USB OTG Ready"
                    )
                }
            }
        }
    }

    private fun toFastbootWireProtocolList(cliCommand: String): List<String> {
        val body = cliCommand
            .replace(Regex("^fastboot(\\s+--[a-zA-Z0-9_-]+)*\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
        return when {
            body.equals("-w", ignoreCase = true) -> listOf("erase:userdata", "erase:metadata")
            body.equals("erase modemst1", ignoreCase = true) -> listOf("erase:modemst1", "erase:modemst2")
            body.equals("reboot fastboot", ignoreCase = true) -> listOf("reboot-fastboot")
            body.equals("reboot recovery", ignoreCase = true) -> listOf("reboot-recovery")
            body.startsWith("getvar ", ignoreCase = true) -> listOf("getvar:${body.substringAfter(" ").trim()}")
            body.startsWith("erase ", ignoreCase = true) -> listOf("erase:${body.substringAfter(" ").trim()}")
            body.startsWith("set_active ", ignoreCase = true) -> listOf("set_active:${body.substringAfter(" ").trim()}")
            body.startsWith("--set-active=", ignoreCase = true) -> listOf("set_active:${body.substringAfter("=").trim()}")
            body.startsWith("snapshot-update ", ignoreCase = true) -> listOf("snapshot-update:${body.substringAfter(" ").trim()}")
            body.startsWith("create-logical-partition ", ignoreCase = true) -> {
                val args = body.removePrefix("create-logical-partition ").trim().replace(Regex("\\s+"), ":")
                listOf("create-logical-partition:$args")
            }
            body.startsWith("delete-logical-partition ", ignoreCase = true) ->
                listOf("delete-logical-partition:${body.substringAfter(" ").trim()}")
            body.startsWith("resize-logical-partition ", ignoreCase = true) -> {
                val args = body.removePrefix("resize-logical-partition ").trim().replace(Regex("\\s+"), ":")
                listOf("resize-logical-partition:$args")
            }
            else -> listOf(body)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(consoleLogs = emptyList()) }
    }

    private fun appendLog(tag: String, message: String, level: ServiceLogLevel) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = ConsoleLogEntry(
            id = logSequence.getAndIncrement(),
            timestamp = ts,
            tag = tag,
            message = message,
            level = level
        )
        _uiState.update { s ->
            s.copy(consoleLogs = (s.consoleLogs + entry).takeLast(300))
        }
    }
}
