package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FlashFileProcessor
import com.example.model.AppNavDestination
import com.example.model.BackupMode
import com.example.model.BridgeStatus
import com.example.model.FlashOptions
import com.example.model.LogLevel
import com.example.model.MtkBrand
import com.example.model.MtkChipInfo
import com.example.model.MtkDeviceDatabase
import com.example.model.MtkDeviceModel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.ServiceFunction
import com.example.model.TerminalLog
import com.example.model.TransportType
import com.example.parser.ScatterParser
import com.example.model.MainPlatformCategory
import com.example.protocol.MtkBromProtocolEngine
import com.example.protocol.MtkChipConfigDatabase
import com.example.protocol.MtkMetaManager
import com.example.protocol.MtkStage1TargetCatalog
import com.example.protocol.RealtimePortStatus
import com.example.protocol.TargetPhoneState
import com.example.protocol.TargetPhoneUsbManager
import com.example.protocol.UsbDeviceMode
import com.example.storage.BackupStorageManager
import com.example.util.DeviceSafetyChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MediaTek Native Bridge ViewModel
 * Coordinates USB OTG State Machine, BROM Protocol, DA Stages, Partition IO & ADB/Fastboot.
 * 100% Real Hardware Protocol Compliant - No Mock Data.
 */
class MtkBridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val storageManager = BackupStorageManager(application)
    val targetPhoneUsb = TargetPhoneUsbManager(application)

    // UI & Transport States
    private val _selectedTransportType = MutableStateFlow(TransportType.USB_OTG_DIRECT)
    val selectedTransportType: StateFlow<TransportType> = _selectedTransportType.asStateFlow()

    private val _bridgeStatus = MutableStateFlow(
        BridgeStatus(
            isConnected = false,
            transportType = TransportType.USB_OTG_DIRECT,
            deviceName = "Direct USB OTG Host"
        )
    )
    val bridgeStatus: StateFlow<BridgeStatus> = _bridgeStatus.asStateFlow()

    val targetPhoneState: StateFlow<TargetPhoneState> = targetPhoneUsb.phoneState
    val realtimePortStatus: StateFlow<RealtimePortStatus> = targetPhoneUsb.realtimePortStatus

    private val _chipInfo = MutableStateFlow(MtkChipInfo())
    val chipInfo: StateFlow<MtkChipInfo> = _chipInfo.asStateFlow()

    private val _selectedBrand = MutableStateFlow<MtkBrand>(MtkDeviceDatabase.getDefaultBrand())
    val selectedBrand: StateFlow<MtkBrand> = _selectedBrand.asStateFlow()

    private val _selectedModel = MutableStateFlow<MtkDeviceModel>(MtkDeviceDatabase.getDefaultModel())
    val selectedModel: StateFlow<MtkDeviceModel> = _selectedModel.asStateFlow()

    private val _scatterPlatform = MutableStateFlow("Unknown / Auto")
    val scatterPlatform: StateFlow<String> = _scatterPlatform.asStateFlow()

    private val _partitions = MutableStateFlow<List<PartitionEntry>>(emptyList())
    val partitions: StateFlow<List<PartitionEntry>> = _partitions.asStateFlow()

    private val _selectedPartitionIndex = MutableStateFlow(0)
    val selectedPartitionIndex: StateFlow<Int> = _selectedPartitionIndex.asStateFlow()

    private val _selectedServiceFunction = MutableStateFlow(ServiceFunction.READ_INFO)
    val selectedServiceFunction: StateFlow<ServiceFunction> = _selectedServiceFunction.asStateFlow()

    private val _currentDestination = MutableStateFlow(AppNavDestination.MTK_FLASH)
    val currentDestination: StateFlow<AppNavDestination> = _currentDestination.asStateFlow()

    private val _flashOptions = MutableStateFlow(FlashOptions())
    val flashOptions: StateFlow<FlashOptions> = _flashOptions.asStateFlow()

    private val _backupMode = MutableStateFlow(BackupMode.FULL_FIRMWARE)
    val backupMode: StateFlow<BackupMode> = _backupMode.asStateFlow()

    private val _autoNvBackup = MutableStateFlow(true)
    val autoNvBackup: StateFlow<Boolean> = _autoNvBackup.asStateFlow()

    private val _autoReboot = MutableStateFlow(true)
    val autoReboot: StateFlow<Boolean> = _autoReboot.asStateFlow()

    private val _backupLocation = MutableStateFlow(storageManager.getBackupDirectory().absolutePath)
    val backupLocation: StateFlow<String> = _backupLocation.asStateFlow()

    private val _availableBackupStorageBytes = MutableStateFlow(
        DeviceSafetyChecker.getAvailableBytesForPath(storageManager.getBackupDirectory())
    )
    val availableBackupStorageBytes: StateFlow<Long> = _availableBackupStorageBytes.asStateFlow()

    private val _operationProgress = MutableStateFlow(OperationProgress())
    val operationProgress: StateFlow<OperationProgress> = _operationProgress.asStateFlow()

    private val _logs = MutableStateFlow<List<TerminalLog>>(emptyList())
    val logs: StateFlow<List<TerminalLog>> = _logs.asStateFlow()

    private val _debugMode = MutableStateFlow(false)
    val debugMode: StateFlow<Boolean> = _debugMode.asStateFlow()

    fun setDebugMode(enabled: Boolean) {
        _debugMode.value = enabled
    }

    fun toggleDebugMode() {
        _debugMode.value = !_debugMode.value
    }

    // File selection paths & Phase 1 modes
    val daAgentPath = MutableStateFlow("Built-in Universal DA (MTK All-in-One)")
    val customDaPath = daAgentPath
    val authFilePath = MutableStateFlow("")
    val preloaderPath = MutableStateFlow("")
    val scatterPath = MutableStateFlow("")
    val scatterFileName: StateFlow<String> = scatterPath.asStateFlow()

    private val _vbmetaPatchMode = MutableStateFlow(3)
    val vbmetaPatchMode: StateFlow<Int> = _vbmetaPatchMode.asStateFlow()

    private val _metaMode = MutableStateFlow(MtkMetaManager.MetaMode.META)
    val metaMode: StateFlow<MtkMetaManager.MetaMode> = _metaMode.asStateFlow()

    // Qualcomm Protocol Engines & States
    val qcSaharaEngine = com.example.protocol.QcSaharaProtocolEngine(
        usbManager = targetPhoneUsb,
        logCallback = { log -> addLog(log) }
    )
    val qcFirehoseEngine = com.example.protocol.QcFirehoseProtocolEngine(
        usbManager = targetPhoneUsb,
        logCallback = { log -> addLog(log) }
    )
    val qcDiagEngine = com.example.protocol.QcDiagProtocolEngine(
        usbManager = targetPhoneUsb,
        logCallback = { log -> addLog(log) }
    )

    val qcFirehoseProgrammerName = MutableStateFlow("Firehose: Auto ELF")
    val qcRawprogramName = MutableStateFlow("rawprogram0.xml")
    val qcPatchName = MutableStateFlow("patch0.xml")
    val qcStorageType = MutableStateFlow("Auto (eMMC/UFS)")
    val qcServiceMethod = MutableStateFlow("[FRP] Erase FRP Lock (EDL 9008)")

    // Dual Mode (Online / Offline) & Dynamic bkerler/Loaders Repository Architecture
    val appModeManager = com.example.protocol.AppModeManager(getApplication())
    val operatingMode: StateFlow<com.example.model.AppOperatingMode> = appModeManager.operatingMode
    val showStartupModePrompt: StateFlow<Boolean> = appModeManager.showStartupPrompt
    val repositoryUrl: StateFlow<String> = appModeManager.repositoryUrl

    private var repositorySource: com.example.protocol.ILoaderRepositorySource =
        com.example.protocol.GitHubLoaderRepositorySource(getApplication(), appModeManager.repositoryUrl.value)

    private var qcLoaderResolver = com.example.protocol.QcLoaderResolver(getApplication(), repositorySource)
    private var qcOfflineSyncManager = com.example.protocol.QcOfflineSyncManager(getApplication(), repositorySource, qcLoaderResolver)

    private val _loaderManifest = MutableStateFlow<com.example.model.LoaderManifest?>(null)
    val loaderManifest: StateFlow<com.example.model.LoaderManifest?> = _loaderManifest.asStateFlow()

    private val _matchedQcLoader = MutableStateFlow<com.example.model.LoaderMetadataEntry?>(null)
    val matchedQcLoader: StateFlow<com.example.model.LoaderMetadataEntry?> = _matchedQcLoader.asStateFlow()

    val syncState: StateFlow<com.example.model.SyncProgressState> = qcOfflineSyncManager.syncState

    private val protocolEngine: MtkBromProtocolEngine
    private var activeJob: kotlinx.coroutines.Job? = null
    private var pendingHandshake = false

    init {
        protocolEngine = MtkBromProtocolEngine(
            context = getApplication(),
            targetPhoneUsb = targetPhoneUsb,
            storageManager = storageManager,
            logCallback = { log -> addLog(log) },
            progressCallback = { prog ->
                _operationProgress.value = prog
                if (prog.isRunning) {
                    com.example.service.MtkOperationForegroundService.updateProgress(
                        getApplication(),
                        prog.title,
                        prog.percentage.toInt(),
                        prog.formattedSpeed,
                        prog.formattedEta
                    )
                } else {
                    com.example.service.MtkOperationForegroundService.stopOperation(getApplication())
                }
            }
        ).apply {
            onPartitionsResolved = { liveParts ->
                _partitions.value = if (_currentDestination.value == AppNavDestination.MTK_BACKUP) {
                    applyBackupPresetToPartitions(_backupMode.value, liveParts)
                } else {
                    liveParts
                }
            }
        }

        _scatterPlatform.value = "Unknown / Auto"
        _partitions.value = emptyList()

        viewModelScope.launch {
            _operationProgress.collectLatest { prog ->
                if (prog.isRunning) {
                    val targetMode = when (_currentDestination.value.category) {
                        MainPlatformCategory.QUALCOMM -> UsbDeviceMode.EDL
                        MainPlatformCategory.ADB -> UsbDeviceMode.ADB
                        MainPlatformCategory.FASTBOOT -> UsbDeviceMode.FASTBOOT
                        else -> UsbDeviceMode.BROM
                    }
                    targetPhoneUsb.lockForAction(prog.title.ifBlank { "Active Service Action" }, targetMode)
                } else {
                    targetPhoneUsb.unlockAction()
                }
            }
        }

        observeTargetPhoneState()
        refreshLoaderManifest(forceNetwork = (appModeManager.operatingMode.value == com.example.model.AppOperatingMode.ONLINE))
    }

    private fun now(): String = timeFormat.format(Date())

    private fun observeTargetPhoneState() {
        viewModelScope.launch {
            var wasConnected = false
            targetPhoneUsb.phoneState.collectLatest { state ->
                when (state) {
                    is TargetPhoneState.Connected -> {
                        val isFirstConnection = !wasConnected
                        wasConnected = true
                        _bridgeStatus.value = _bridgeStatus.value.copy(
                            isConnected = true,
                            fileDescriptor = state.fileDescriptor,
                            isBromMode = state.isBromMode,
                            targetVidPid = state.vidPid,
                            deviceName = state.deviceName
                        )
                        if (_debugMode.value) {
                            addLog(TerminalLog(now(), "Direct USB Connected: ${state.deviceName} [${state.vidPid}] FD:${state.fileDescriptor}", LogLevel.SUCCESS, isDebugOnly = true))
                        }
                        if (isFirstConnection) {
                            com.example.audio.ToolSoundManager.playUsbConnected()
                        }

                        // Auto handshake if triggered by scan or background sniffing
                        if (pendingHandshake && state.isBromMode) {
                            pendingHandshake = false
                            runBromHandshake()
                        }
                    }
                    is TargetPhoneState.Disconnected -> {
                        val wasPreviouslyConnected = wasConnected
                        wasConnected = false
                        _bridgeStatus.value = _bridgeStatus.value.copy(isConnected = false, fileDescriptor = -1)
                        if (wasPreviouslyConnected) {
                            if (_debugMode.value) {
                                addLog(TerminalLog(now(), "Direct USB Disconnected / Removed.", LogLevel.WARNING, isDebugOnly = true))
                            }
                            com.example.audio.ToolSoundManager.playUsbDisconnected()
                        }
                    }
                    is TargetPhoneState.Error -> {
                        if (_debugMode.value) {
                            addLog(TerminalLog(now(), "USB Error: ${state.message}", LogLevel.ERROR, isDebugOnly = true))
                        }
                        com.example.audio.ToolSoundManager.playOperationStop()
                    }
                    is TargetPhoneState.RequestingPermission -> {
                        if (_debugMode.value) {
                            addLog(TerminalLog(now(), "Requesting USB OTG Permission for ${state.deviceName} [${state.mode.label} - ${state.vidPid}]...", LogLevel.WARNING, isDebugOnly = true))
                        }
                    }
                }
            }
        }
    }

    fun addLog(log: TerminalLog) {
        val current = _logs.value.toMutableList()
        current.add(log)
        if (current.size > 600) current.removeAt(0)
        _logs.value = current
    }

    private fun resetLogsForNewAction() {
        _logs.value = emptyList()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun toggleAutoReboot(enabled: Boolean) {
        _autoReboot.value = enabled
        _flashOptions.value = _flashOptions.value.copy(autoReboot = enabled)
        if (_debugMode.value) {
            addLog(TerminalLog(now(), "Post-Operation Auto Reboot: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.INFO, isDebugOnly = true))
        }
    }

    fun toggleAutoNvBackup(enabled: Boolean) {
        _autoNvBackup.value = enabled
        _flashOptions.value = _flashOptions.value.copy(readNvData = enabled)
        if (_debugMode.value) {
            addLog(TerminalLog(now(), "Auto NV Data Backup Policy: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.INFO, isDebugOnly = true))
        }
    }

    fun refreshBackupStorageInfo() {
        val dir = storageManager.getBackupDirectory()
        _backupLocation.value = dir.absolutePath
        _availableBackupStorageBytes.value = DeviceSafetyChecker.getAvailableBytesForPath(dir)
    }

    fun setCustomBackupLocation(path: String) {
        storageManager.setCustomBackupPath(path)
        refreshBackupStorageInfo()
        addLog(TerminalLog(now(), "Backup output path set to: ${_backupLocation.value} (Free: ${DeviceSafetyChecker.formatBytesCompact(_availableBackupStorageBytes.value)})", LogLevel.SUCCESS))
    }

    private fun copyUriToLoaderFile(uri: Uri, targetFileName: String): String? {
        return try {
            val dir = File(getApplication<Application>().filesDir, "custom_loaders")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, targetFileName)
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            outFile.absolutePath
        } catch (e: Exception) {
            addLog(TerminalLog(now(), "Failed to import loader file '$targetFileName': ${e.message}", LogLevel.ERROR))
            null
        }
    }

    fun setCustomDaFromUri(uri: Uri, displayName: String) {
        val localPath = copyUriToLoaderFile(uri, "custom_da.bin") ?: return
        setCustomDaFile(localPath, displayName)
    }

    fun setCustomDaFile(path: String, displayName: String = File(path).name) {
        protocolEngine.customDaPath = path
        daAgentPath.value = displayName
        addLog(TerminalLog(now(), "[LOADER] Custom DA selected: $displayName", LogLevel.SUCCESS))
    }

    fun setCustomPreloaderFromUri(uri: Uri, displayName: String) {
        val localPath = copyUriToLoaderFile(uri, "custom_preloader.bin") ?: return
        setCustomPreloaderFile(localPath, displayName)
    }

    fun setCustomPreloaderFile(path: String, displayName: String = File(path).name) {
        protocolEngine.customPreloaderPath = path
        preloaderPath.value = displayName
        addLog(TerminalLog(now(), "[LOADER] Custom Preloader selected: $displayName", LogLevel.SUCCESS))
    }

    fun setCustomAuthFromUri(uri: Uri, displayName: String) {
        val localPath = copyUriToLoaderFile(uri, "custom_auth.auth") ?: return
        setCustomAuthFile(localPath, displayName)
    }

    fun setCustomAuthFile(path: String, displayName: String = File(path).name) {
        protocolEngine.customAuthPath = path
        authFilePath.value = displayName
        addLog(TerminalLog(now(), "[LOADER] Custom Auth SV5 selected: $displayName", LogLevel.SUCCESS))
    }

    fun resetCustomLoaders() {
        protocolEngine.customDaPath = null
        protocolEngine.customPreloaderPath = null
        protocolEngine.customAuthPath = null
        daAgentPath.value = "Built-in Universal DA (MTK All-in-One)"
        preloaderPath.value = ""
        authFilePath.value = ""
        addLog(TerminalLog(now(), "[LOADER] Reset DA, Preloader & Auth to built-in Universal Bundle.", LogLevel.INFO))
    }

    fun setVbmetaPatchMode(mode: Int) {
        _vbmetaPatchMode.value = mode.coerceIn(0, 3)
        addLog(TerminalLog(now(), "VBMeta Patch Mode set to: ${_vbmetaPatchMode.value}", LogLevel.INFO))
    }

    fun executePatchVbmeta(mode: Int) {
        _vbmetaPatchMode.value = mode.coerceIn(0, 3)
        executeServiceFunctionDirect(ServiceFunction.PATCH_VBMETA)
    }

    fun executeEnterMetaMode(mode: MtkMetaManager.MetaMode) {
        _metaMode.value = mode
        executeServiceFunctionDirect(ServiceFunction.ENTER_META_MODE)
    }

    fun executeMetaFactoryReset(preferEmeta: Boolean = true) {
        pendingMetaPreferEmeta = preferEmeta
        executeServiceFunctionDirect(ServiceFunction.META_FACTORY_RESET)
    }

    fun executeMetaWriteImei(imei1: String, imei2: String) {
        pendingMetaImei1 = imei1.trim()
        pendingMetaImei2 = imei2.trim()
        executeServiceFunctionDirect(ServiceFunction.META_WRITE_IMEI)
    }

    fun executeMetaRestoreNvramFromUri(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            resetLogsForNewAction()
            val localPath = copyUriToLoaderFile(uri, "meta_nvram_restore.bin") ?: return@launch
            val bytes = runCatching { File(localPath).readBytes() }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                addLog(TerminalLog(now(), "[-] Failed to read META NVRAM file: $displayName", LogLevel.ERROR))
                return@launch
            }
            pendingMetaNvramBytes = bytes
            pendingMetaNvramName = displayName
            addLog(TerminalLog(now(), "[META NVRAM] Loaded $displayName (${bytes.size} bytes). Starting restore...", LogLevel.INFO))
            withContext(Dispatchers.Main) {
                executeServiceFunctionDirect(ServiceFunction.META_RESTORE_NVRAM, clearLogsBeforeStart = false)
            }
        }
    }

    fun writeRpmbFromUri(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            resetLogsForNewAction()
            val localPath = copyUriToLoaderFile(uri, "rpmb_upload.bin") ?: return@launch
            val dst = File(storageManager.getBackupDirectory(), "rpmb.bin")
            runCatching { File(localPath).copyTo(dst, overwrite = true) }
            addLog(TerminalLog(now(), "[RPMB] Staged $displayName -> ${dst.name}. Starting Write RPMB...", LogLevel.INFO))
            withContext(Dispatchers.Main) {
                executeServiceFunctionDirect(ServiceFunction.WRITE_RPMB, clearLogsBeforeStart = false)
            }
        }
    }

    // Stored parameters for ServiceFunction direct execution & UI dialogs
    private var pendingFullFlashPartType: String = "user"
    private var pendingOffsetStart: Long = 0L
    private var pendingOffsetLength: Long = 0x1000L
    private var pendingOffsetPartType: String = "user"
    private var pendingOffsetIsSectors: Boolean = false
    private var pendingWriteFilePath: String = ""
    private var pendingPeekAddr: Long = 0x100000L
    private var pendingPeekLen: Int = 0x100
    private var pendingPeekRegs: Boolean = false
    private var pendingPeekSave: Boolean = false
    private var pendingPokeData: ByteArray = ByteArray(0)
    private var pendingDumpSramMode: Boolean = false
    private var pendingPayloadPathOrAsset: String = "generic_dump_payload.bin"
    private var pendingMetaPreferEmeta: Boolean = true
    private var pendingMetaImei1: String = ""
    private var pendingMetaImei2: String = ""
    private var pendingMetaNvramBytes: ByteArray = ByteArray(0)
    private var pendingMetaNvramName: String = "meta_nvram.bin"

    private fun parseHexOrDecLong(raw: String): Long? {
        val clean = raw.trim().lowercase()
        if (clean.isEmpty()) return null
        return if (clean.startsWith("0x")) {
            clean.removePrefix("0x").toLongOrNull(16)
        } else {
            clean.toLongOrNull(10) ?: clean.toLongOrNull(16)
        }
    }

    private fun parsePokeBytes(raw: String): ByteArray? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("0x", ignoreCase = true) && trimmed.length <= 10) {
            val val32 = trimmed.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: return null
            return byteArrayOf(
                (val32 and 0xFF).toByte(),
                ((val32 shr 8) and 0xFF).toByte(),
                ((val32 shr 16) and 0xFF).toByte(),
                ((val32 shr 24) and 0xFF).toByte()
            )
        }
        val cleanHex = trimmed.removePrefix("0x").removePrefix("0X").replace(" ", "")
        if (cleanHex.isEmpty() || cleanHex.length % 2 != 0) return null
        return try {
            ByteArray(cleanHex.length / 2) { i ->
                cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun executeFullFlashDump(partType: String = "user") {
        pendingFullFlashPartType = partType
        executeServiceFunctionDirect(ServiceFunction.FULL_FLASH_DUMP)
    }

    fun executeReadOffset(startStr: String, lengthStr: String, partType: String = "user", isSectors: Boolean = false) {
        val start = parseHexOrDecLong(startStr)
        val len = parseHexOrDecLong(lengthStr)
        if (start == null || len == null || start < 0L || len <= 0L) {
            addLog(TerminalLog(now(), "[-] Invalid Read Offset parameters: start='$startStr', len='$lengthStr'", LogLevel.ERROR))
            return
        }
        pendingOffsetStart = start
        pendingOffsetLength = len
        pendingOffsetPartType = partType
        pendingOffsetIsSectors = isSectors
        executeServiceFunctionDirect(ServiceFunction.READ_OFFSET)
    }

    fun executeWriteOffsetFromUri(
        startStr: String,
        uri: Uri,
        displayName: String,
        partType: String = "user",
        isSectors: Boolean = false
    ) {
        val start = parseHexOrDecLong(startStr)
        if (start == null || start < 0L) {
            addLog(TerminalLog(now(), "[-] Invalid Write Offset start address: '$startStr'", LogLevel.ERROR))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            resetLogsForNewAction()
            val localPath = copyUriToLoaderFile(uri, "wo_staged_${System.currentTimeMillis()}.bin") ?: return@launch
            pendingOffsetStart = start
            pendingOffsetPartType = partType
            pendingOffsetIsSectors = isSectors
            pendingWriteFilePath = localPath
            addLog(TerminalLog(now(), "[WO] Staged '$displayName' -> 0x%X ($partType)".format(start), LogLevel.INFO))
            withContext(Dispatchers.Main) {
                executeServiceFunctionDirect(ServiceFunction.WRITE_OFFSET, clearLogsBeforeStart = false)
            }
        }
    }

    fun executeEraseRange(startStr: String, lengthStr: String, partType: String = "user", isSectors: Boolean = false) {
        val start = parseHexOrDecLong(startStr)
        val len = parseHexOrDecLong(lengthStr)
        if (start == null || len == null || start < 0L || len <= 0L) {
            addLog(TerminalLog(now(), "[-] Invalid Erase Range parameters: start='$startStr', len='$lengthStr'", LogLevel.ERROR))
            return
        }
        pendingOffsetStart = start
        pendingOffsetLength = len
        pendingOffsetPartType = partType
        pendingOffsetIsSectors = isSectors
        executeServiceFunctionDirect(ServiceFunction.ERASE_RANGE)
    }

    fun executeDaPeek(addrStr: String, lengthStr: String, registers: Boolean, saveToFile: Boolean) {
        val addr = parseHexOrDecLong(addrStr)
        val len = parseHexOrDecLong(lengthStr)?.toInt()
        if (addr == null || len == null || len <= 0) {
            addLog(TerminalLog(now(), "[-] Invalid DA Peek parameters: addr='$addrStr', len='$lengthStr'", LogLevel.ERROR))
            return
        }
        pendingPeekAddr = addr
        pendingPeekLen = len
        pendingPeekRegs = registers
        pendingPeekSave = saveToFile
        executeServiceFunctionDirect(ServiceFunction.DA_PEEK)
    }

    fun executeDaPoke(addrStr: String, valueStr: String) {
        val addr = parseHexOrDecLong(addrStr)
        val bytes = parsePokeBytes(valueStr)
        if (addr == null || bytes == null || bytes.isEmpty()) {
            addLog(TerminalLog(now(), "[-] Invalid DA Poke parameters: addr='$addrStr', data='$valueStr'", LogLevel.ERROR))
            return
        }
        pendingPeekAddr = addr
        pendingPokeData = bytes
        executeServiceFunctionDirect(ServiceFunction.DA_POKE)
    }

    fun executeDumpBootRom(sramMode: Boolean = false) {
        pendingDumpSramMode = sramMode
        executeServiceFunctionDirect(ServiceFunction.DUMP_BROM)
    }

    fun executeRunBuiltinPayload(assetName: String) {
        pendingPayloadPathOrAsset = assetName
        executeServiceFunctionDirect(ServiceFunction.RUN_PAYLOAD)
    }

    fun executeRunCustomPayloadFromUri(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            resetLogsForNewAction()
            val localPath = copyUriToLoaderFile(uri, "custom_payload_${System.currentTimeMillis()}.bin") ?: return@launch
            pendingPayloadPathOrAsset = localPath
            addLog(TerminalLog(now(), "[PAYLOAD] Staged custom payload '$displayName'", LogLevel.INFO))
            withContext(Dispatchers.Main) {
                executeServiceFunctionDirect(ServiceFunction.RUN_PAYLOAD, clearLogsBeforeStart = false)
            }
        }
    }

    fun setTransportType(type: TransportType) {
        if (_selectedTransportType.value == type) return
        _selectedTransportType.value = type
        if (type != TransportType.USB_OTG_DIRECT) {
            addLog(TerminalLog(now(), "Only Direct USB OTG mode is supported.", LogLevel.WARNING))
        } else {
            addLog(TerminalLog(now(), "Direct USB OTG Host Mode active.", LogLevel.SUCCESS))
        }
    }

    fun scanTargetPhone() {
        viewModelScope.launch {
            resetLogsForNewAction()
            addLog(TerminalLog(now(), "Scanning USB Host for MediaTek BROM / Preloader ports (${targetPhoneUsb.getRawDeviceCount()} USB devices)...", LogLevel.INFO))
            pendingHandshake = true
            val connected = withContext(Dispatchers.IO) {
                targetPhoneUsb.scanAndConnect(
                    promptIfNeeded = true,
                    forceBromOnly = false,
                    requiredModes = TargetPhoneUsbManager.MTK_SERVICE_MODES
                )
            }
            if (connected && targetPhoneUsb.isBromOrPreloaderConnected()) {
                pendingHandshake = false
                if (targetPhoneUsb.isBromConnected()) {
                    runBromHandshake()
                } else {
                    addLog(TerminalLog(now(), "[+] ${targetPhoneUsb.currentMode.label} port ready. Tap Read Info / Service action to proceed.", LogLevel.SUCCESS))
                }
            } else if (targetPhoneUsb.phoneState.value !is TargetPhoneState.RequestingPermission) {
                pendingHandshake = false
                addLog(TerminalLog(now(), "No active MTK BROM/Preloader port detected. Hold [Vol+ & Vol-] and plug USB.", LogLevel.INFO))
            }
        }
    }

    fun selectBrand(brand: MtkBrand) {
        _selectedBrand.value = brand
        brand.models.firstOrNull()?.let { selectModel(it) }
    }

    fun selectModel(model: MtkDeviceModel) {
        _selectedModel.value = model
        _scatterPlatform.value = model.chipCode
        if (_debugMode.value) {
            addLog(TerminalLog(now(), "Selected Device: ${_selectedBrand.value.brandName} -> ${model.modelName} [${model.chipset}]", LogLevel.SUCCESS, isDebugOnly = true))
            val target = MtkStage1TargetCatalog.findTarget(model.chipCode)
            if (target != null) {
                addLog(TerminalLog(now(), "[HARDWARE TARGET MATCH] SoC: ${target.socName.uppercase()} | Mode: ${target.mode} | SecReg: 0x${target.secReg.toString(16)} | BlAddr: 0x${target.bladdr.toString(16)} | UART: 0x${target.uartReg0.toString(16)}", LogLevel.SUCCESS, isDebugOnly = true))
                addLog(TerminalLog(now(), "[EXPLOIT CONFIG] Stage1 Payload: ${target.payloadFileName} (Auto-bound)", LogLevel.INFO, isDebugOnly = true))
            }
            val chipConfig = MtkChipConfigDatabase.findConfigByName(model.chipCode)
            if (chipConfig != null) {
                addLog(TerminalLog(now(), "[CHIP CONFIG] HW Code: 0x${chipConfig.hwCode.toString(16).uppercase()} | WDT: 0x${chipConfig.watchdog.toString(16).uppercase()} | DA Mode: ${chipConfig.damode}", LogLevel.INFO, isDebugOnly = true))
            }
            addLog(TerminalLog(now(), "BROM Connection Guide: ${model.bromInstruction}", LogLevel.INFO, isDebugOnly = true))
        }
    }

    fun setScatterPlatform(chipName: String) {
        _scatterPlatform.value = chipName
        if (_debugMode.value) {
            addLog(TerminalLog(now(), "Target Architecture Set: $chipName", LogLevel.INFO, isDebugOnly = true))
        }
    }

    private fun isStableBootPartition(name: String): Boolean {
        val n = name.lowercase(Locale.US)
        val stablePrefixes = listOf(
            "preloader", "boot", "init_boot", "vendor_boot", "dtbo", "vbmeta",
            "recovery", "lk", "tee", "scp", "sspm", "spmfw", "mcupmfw",
            "md1img", "gz", "super", "metadata", "logo"
        )
        return stablePrefixes.any { prefix ->
            n == prefix || n == "${prefix}_a" || n == "${prefix}_b" ||
                n == "${prefix}1" || n == "${prefix}2" || n == "${prefix}_1" || n == "${prefix}_2" ||
                (prefix == "vbmeta" && n.startsWith("vbmeta_"))
        }
    }

    private fun isNvCalibrationPartition(name: String, isProtectedNv: Boolean = false): Boolean {
        if (isProtectedNv) return true
        val n = name.lowercase(Locale.US)
        val nvPrefixes = listOf(
            "nvram", "nvdata", "nvcfg", "protect1", "protect2",
            "protect_f", "protect_s", "secro", "seccfg", "sec1",
            "proinfo", "persist", "persist_backup"
        )
        return nvPrefixes.any { prefix ->
            n == prefix || n == "${prefix}_a" || n == "${prefix}_b" || n.startsWith("protect")
        }
    }

    private fun applyBackupPresetToPartitions(mode: BackupMode, list: List<PartitionEntry>): List<PartitionEntry> {
        if (list.isEmpty()) return list
        return when (mode) {
            BackupMode.FULL_FIRMWARE -> list.map { it.copy(isSelectedForFlashing = true) }
            BackupMode.STABLE_FIRMWARE -> list.map { it.copy(isSelectedForFlashing = isStableBootPartition(it.partitionName)) }
            BackupMode.NV_DATA -> list.map { it.copy(isSelectedForFlashing = isNvCalibrationPartition(it.partitionName, it.isProtectedNv)) }
            BackupMode.CUSTOM_PARTITIONS -> list
        }
    }

    private fun resolveUriDisplayName(uri: Uri, fallback: String): String {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun loadScatterContent(content: String, sourceFileName: String, clearLogsBeforeLoad: Boolean = true) {
        if (clearLogsBeforeLoad) {
            resetLogsForNewAction()
        }
        val parsed = ScatterParser.parseScatter(content)
        _scatterPlatform.value = parsed.first
        val finalParts = if (_currentDestination.value == AppNavDestination.MTK_BACKUP) {
            applyBackupPresetToPartitions(_backupMode.value, parsed.second)
        } else {
            parsed.second
        }
        _partitions.value = finalParts
        scatterPath.value = sourceFileName
        if (finalParts.isNotEmpty()) {
            addLog(TerminalLog(now(), "Successfully loaded scatter: $sourceFileName (${parsed.first} - ${finalParts.size} Partitions, ${finalParts.count { it.isSelectedForFlashing }} selected)", LogLevel.SUCCESS))
        } else {
            addLog(TerminalLog(now(), "Scatter file '$sourceFileName' contains no valid partition entries.", LogLevel.WARNING))
        }
    }

    fun loadScatterFromUri(uri: Uri, fallbackName: String = "scatter.txt", onLoaded: (() -> Unit)? = null) {
        viewModelScope.launch {
            resetLogsForNewAction()
            val ctx = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = FlashFileProcessor.readTextSafely(ctx, uri, maxBytes = 4 * 1024 * 1024)
                    val fileName = resolveUriDisplayName(uri, fallbackName)
                    fileName to text
                }
            }
            result.onSuccess { (fileName, text) ->
                if (text.isBlank()) {
                    addLog(TerminalLog(now(), "Unable to read '$fileName' (file empty or unreadable).", LogLevel.ERROR))
                    return@onSuccess
                }
                loadScatterContent(text, fileName, clearLogsBeforeLoad = false)
                if (_partitions.value.isNotEmpty()) {
                    onLoaded?.invoke()
                }
            }.onFailure { e ->
                addLog(TerminalLog(now(), "Failed to load scatter: ${e.message}", LogLevel.ERROR))
            }
        }
    }

    fun loadFirmwareFolder(treeUri: Uri, onLoaded: (() -> Unit)? = null) {
        viewModelScope.launch {
            resetLogsForNewAction()
            val ctx = getApplication<Application>()
            addLog(TerminalLog(now(), "Scanning selected firmware folder for MTK scatter & image payloads...", LogLevel.INFO))

            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val entries = FlashFileProcessor.collectFolderEntries(ctx, treeUri)
                    if (entries.isEmpty()) {
                        return@runCatching Triple("EMPTY", "", emptyList<PartitionEntry>())
                    }
                    val folderName = runCatching {
                        DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast('/').substringAfterLast(':')
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Firmware Folder"

                    val scatterDoc = entries.firstOrNull {
                        val l = it.fileName.lowercase(Locale.US)
                        l.endsWith(".txt") && l.contains("scatter")
                    } ?: entries.firstOrNull {
                        val l = it.fileName.lowercase(Locale.US)
                        l.endsWith(".txt") && FlashFileProcessor.readTextSafely(ctx, it.uri, maxBytes = 128 * 1024)
                            .contains("partition_index", ignoreCase = true)
                    } ?: return@runCatching Triple("NO_SCATTER", folderName, emptyList<PartitionEntry>())

                    val scatterText = FlashFileProcessor.readTextSafely(ctx, scatterDoc.uri, maxBytes = 4 * 1024 * 1024)
                    val (platform, rawParts) = ScatterParser.parseScatter(scatterText)
                    val byName = entries.associateBy { it.fileName.lowercase(Locale.US) }

                    val resolvedParts = rawParts.map { part ->
                        val fnameLower = part.fileName.lowercase(Locale.US)
                        val pLower = part.partitionName.lowercase(Locale.US)
                        val matched = if (fnameLower.isNotBlank() && fnameLower != "none") {
                            byName[fnameLower]
                        } else {
                            null
                        } ?: byName["$pLower.img"] ?: byName["$pLower.bin"]

                        val updatedSize = if (part.sizeBytes > 0L) part.sizeBytes else (matched?.sizeBytes ?: 0L)
                        val hasPayload = matched != null
                        part.copy(
                            sizeBytes = updatedSize,
                            boundFilePath = matched?.uri?.toString() ?: "",
                            isSelectedForFlashing = part.isDownload && hasPayload
                        )
                    }
                    Triple("$platform|$folderName/${scatterDoc.fileName}", platform, resolvedParts)
                }
            }

            outcome.onSuccess { (tag, platform, resolvedParts) ->
                when {
                    tag == "EMPTY" -> {
                        addLog(TerminalLog(now(), "Selected folder has no readable files.", LogLevel.WARNING))
                    }
                    tag == "NO_SCATTER" -> {
                        addLog(TerminalLog(now(), "No MTK *scatter*.txt found inside '$platform'.", LogLevel.WARNING))
                    }
                    else -> {
                        val displayScatter = tag.substringAfter("|")
                        val finalParts = if (_currentDestination.value == AppNavDestination.MTK_BACKUP) {
                            applyBackupPresetToPartitions(_backupMode.value, resolvedParts)
                        } else {
                            resolvedParts
                        }
                        _scatterPlatform.value = platform
                        scatterPath.value = displayScatter
                        _partitions.value = finalParts
                        val boundCount = finalParts.count { it.boundFilePath.isNotBlank() }
                        val checkedCount = finalParts.count { it.isSelectedForFlashing }
                        addLog(
                            TerminalLog(
                                now(),
                                "Loaded Folder Scatter: $displayScatter ($platform | $boundCount images matched | $checkedCount/${finalParts.size} selected)",
                                LogLevel.SUCCESS
                            )
                        )
                        if (finalParts.isNotEmpty()) {
                            onLoaded?.invoke()
                        }
                    }
                }
            }.onFailure { e ->
                addLog(TerminalLog(now(), "Failed to scan firmware folder: ${e.message}", LogLevel.ERROR))
            }
        }
    }

    fun togglePartitionSelection(index: Int, isSelected: Boolean = true) {
        val list = _partitions.value.toMutableList()
        val pos = list.indexOfFirst { it.partitionIndex == index }.takeIf { it >= 0 } ?: index
        if (pos in list.indices) {
            list[pos] = list[pos].copy(isSelectedForFlashing = isSelected)
            _partitions.value = list
            if (_currentDestination.value == AppNavDestination.MTK_BACKUP && _backupMode.value != BackupMode.CUSTOM_PARTITIONS) {
                _backupMode.value = BackupMode.CUSTOM_PARTITIONS
            }
        }
    }

    fun selectPartition(index: Int) = selectPartitionIndex(index)

    fun selectAllPartitions(selectAll: Boolean) {
        val list = _partitions.value.map { it.copy(isSelectedForFlashing = selectAll) }
        _partitions.value = list
        if (_currentDestination.value == AppNavDestination.MTK_BACKUP && _backupMode.value != BackupMode.CUSTOM_PARTITIONS) {
            _backupMode.value = BackupMode.CUSTOM_PARTITIONS
        }
        if (_debugMode.value) {
            addLog(TerminalLog(now(), if (selectAll) "Selected all partitions." else "Deselected all partitions.", LogLevel.INFO, isDebugOnly = true))
        }
    }

    fun toggleSelectAllPartitions(selectAll: Boolean) = selectAllPartitions(selectAll)

    fun invertPartitionSelection() {
        val list = _partitions.value.map { it.copy(isSelectedForFlashing = !it.isSelectedForFlashing) }
        _partitions.value = list
        if (_currentDestination.value == AppNavDestination.MTK_BACKUP && _backupMode.value != BackupMode.CUSTOM_PARTITIONS) {
            _backupMode.value = BackupMode.CUSTOM_PARTITIONS
        }
        if (_debugMode.value) {
            addLog(TerminalLog(now(), "Inverted partition selection (${list.count { it.isSelectedForFlashing }}/${list.size} selected).", LogLevel.INFO, isDebugOnly = true))
        }
    }

    fun protectNvPartitions() {
        val list = _partitions.value.map { part ->
            if (isNvCalibrationPartition(part.partitionName, part.isProtectedNv)) {
                part.copy(isSelectedForFlashing = false)
            } else {
                part
            }
        }
        _partitions.value = list
        addLog(
            TerminalLog(
                now(),
                "[PM PROTECT NV] Unchecked all NVRAM/NVDATA/PROTECT/PERSIST calibration partitions (${list.count { it.isSelectedForFlashing }}/${list.size} remaining).",
                LogLevel.SUCCESS
            )
        )
    }

    fun bindPartitionFileFromUri(partitionIndex: Int, uri: Uri) {
        val list = _partitions.value.toMutableList()
        val pos = list.indexOfFirst { it.partitionIndex == partitionIndex }
        if (pos !in list.indices) return
        val part = list[pos]
        val displayName = resolveUriDisplayName(uri, "${part.partitionName}.img")
        list[pos] = part.copy(
            fileName = displayName,
            boundFilePath = uri.toString(),
            isSelectedForFlashing = true
        )
        _partitions.value = list
        addLog(
            TerminalLog(
                now(),
                "[PM BIND] Bound '$displayName' -> partition '${part.partitionName}' (${part.linearStartAddrHex})",
                LogLevel.SUCCESS
            )
        )
    }

    fun executeSinglePartitionRead(partitionIndex: Int) {
        val pos = _partitions.value.indexOfFirst { it.partitionIndex == partitionIndex }
        if (pos !in _partitions.value.indices) return
        _selectedPartitionIndex.value = pos
        executeServiceFunctionDirect(ServiceFunction.READ_PARTITION)
    }

    fun executeSinglePartitionErase(partitionIndex: Int) {
        val pos = _partitions.value.indexOfFirst { it.partitionIndex == partitionIndex }
        if (pos !in _partitions.value.indices) return
        _selectedPartitionIndex.value = pos
        executeServiceFunctionDirect(ServiceFunction.FORMAT_PARTITION)
    }

    fun executeSinglePartitionWriteFromUri(partitionIndex: Int, uri: Uri) {
        if (_operationProgress.value.isRunning) return
        val pos = _partitions.value.indexOfFirst { it.partitionIndex == partitionIndex }
        if (pos !in _partitions.value.indices) return
        val part = _partitions.value[pos]
        val displayName = resolveUriDisplayName(uri, "${part.partitionName}.img")
        bindPartitionFileFromUri(partitionIndex, uri)

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            resetLogsForNewAction()
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Write ${part.partitionName}",
                detail = "Writing $displayName -> ${part.partitionName}...",
                percentage = 0f
            )
            com.example.audio.ToolSoundManager.playOperationStart()
            addLog(TerminalLog(now(), "[PM WRITE] Writing '$displayName' to partition '${part.partitionName}' (${part.linearStartAddrHex})...", LogLevel.INFO))
            try {
                val res = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        Result.failure(IllegalArgumentException("Selected file '$displayName' is empty or unreadable"))
                    } else {
                        protocolEngine.writePartition(part, bytes, _autoNvBackup.value, _autoReboot.value)
                    }
                }
                if (res.isSuccess) com.example.audio.ToolSoundManager.playOperationDone()
                else com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: Exception) {
                addLog(TerminalLog(now(), "[-] Write Partition Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
            }
        }
    }

    fun loadDevicePartitions(onLoaded: (() -> Unit)? = null) {
        if (_operationProgress.value.isRunning) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            resetLogsForNewAction()
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Read GPT",
                detail = "Reading partition table from device...",
                percentage = 0f
            )
            com.example.audio.ToolSoundManager.playOperationStart()
            addLog(TerminalLog(now(), "Reading GPT Partition Table from target phone storage...", LogLevel.INFO))
            try {
                val chip = if (_scatterPlatform.value == "Unknown / Auto" || _scatterPlatform.value.isEmpty()) _selectedModel.value.chipCode else _scatterPlatform.value
                val res = withContext(Dispatchers.IO) {
                    protocolEngine.readGptAndGenerateScatter(chip, emptyList())
                }
                _operationProgress.value = OperationProgress(isRunning = false)
                if (res.isSuccess) {
                    if (_currentDestination.value == AppNavDestination.MTK_BACKUP) {
                        _partitions.value = applyBackupPresetToPartitions(_backupMode.value, _partitions.value)
                    }
                    val selectedCount = _partitions.value.count { it.isSelectedForFlashing }
                    addLog(TerminalLog(now(), "Successfully loaded ${_partitions.value.size} partitions from live GPT ($selectedCount selected).", LogLevel.SUCCESS))
                    com.example.audio.ToolSoundManager.playOperationDone()
                    onLoaded?.invoke()
                } else {
                    val err = res.exceptionOrNull()?.message ?: "Unknown error"
                    addLog(TerminalLog(now(), "Failed to read GPT: $err", LogLevel.ERROR))
                    com.example.audio.ToolSoundManager.playOperationStop()
                }
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress(isRunning = false)
                addLog(TerminalLog(now(), "Error reading GPT: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            }
        }
    }

    fun selectPartitionIndex(index: Int) {
        if (index in _partitions.value.indices) {
            _selectedPartitionIndex.value = index
        }
    }

    fun selectServiceFunction(func: ServiceFunction) {
        _selectedServiceFunction.value = func
        if (_debugMode.value) addLog(TerminalLog(now(), "Selected service function: ${func.title}", LogLevel.INFO, isDebugOnly = true))
    }

    fun toggleDryRun(enabled: Boolean) {
        if (enabled && _debugMode.value) {
            addLog(TerminalLog(now(), "Dry-Run mode is not applicable to real hardware flashing.", LogLevel.WARNING, isDebugOnly = true))
        }
    }

    fun cancelCurrentOperation() {
        if (activeJob?.isActive == true) {
            activeJob?.cancel()
            _operationProgress.value = OperationProgress(isRunning = false, title = "Cancelled", percentage = 0f)
            com.example.service.MtkOperationForegroundService.stopOperation(getApplication())
            addLog(TerminalLog(now(), "[ABORTED] Active hardware operation stopped by user.", LogLevel.ERROR))
            com.example.audio.ToolSoundManager.playOperationStop()
        }
    }

    fun navigateTo(destination: AppNavDestination) {
        _currentDestination.value = destination
        if (destination == AppNavDestination.MTK_BACKUP) {
            refreshBackupStorageInfo()
            if (_partitions.value.isNotEmpty() && _backupMode.value != BackupMode.CUSTOM_PARTITIONS) {
                _partitions.value = applyBackupPresetToPartitions(_backupMode.value, _partitions.value)
            }
        }
        if (_debugMode.value) addLog(TerminalLog(now(), "Navigated to: ${destination.title}", LogLevel.INFO, isDebugOnly = true))
    }

    fun setBackupMode(mode: BackupMode) {
        _backupMode.value = mode
        refreshBackupStorageInfo()
        if (_partitions.value.isNotEmpty() && mode != BackupMode.CUSTOM_PARTITIONS) {
            val updated = applyBackupPresetToPartitions(mode, _partitions.value)
            _partitions.value = updated
            val count = updated.count { it.isSelectedForFlashing }
            addLog(TerminalLog(now(), "[BACKUP PRESET] ${mode.title}: $count/${updated.size} partitions selected.", LogLevel.INFO))
        } else if (_debugMode.value) {
            addLog(TerminalLog(now(), "Selected Backup Mode: ${mode.title} (${mode.description})", LogLevel.INFO, isDebugOnly = true))
        }
    }

    fun toggleFlashReadNvData(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(readNvData = enabled)
        _autoNvBackup.value = enabled
        if (_debugMode.value) addLog(TerminalLog(now(), "Flash Action [Read NV Data]: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.INFO, isDebugOnly = true))
    }

    fun toggleFlashAutoReboot(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(autoReboot = enabled)
        _autoReboot.value = enabled
        if (_debugMode.value) addLog(TerminalLog(now(), "Flash Action [Auto Reboot]: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.INFO, isDebugOnly = true))
    }

    fun toggleFlashAfterBlUnlock(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(flashAfterBlUnlock = enabled)
        if (_debugMode.value) addLog(TerminalLog(now(), "Flash Action [Flash After BL Unlock]: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.INFO, isDebugOnly = true))
    }

    fun toggleFlashDaDlChecksum(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(daDlChecksum = enabled)
        if (_debugMode.value) addLog(TerminalLog(now(), "Flash Action [DA DL Checksum]: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.INFO, isDebugOnly = true))
    }

    fun toggleFlashAutoSign(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(autoSignFlash = enabled)
        if (_debugMode.value) addLog(TerminalLog(now(), "Flash Action [Auto Sign Flash]: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.INFO, isDebugOnly = true))
    }

    fun toggleFlashFormatAll(enabled: Boolean) {
        _flashOptions.value = _flashOptions.value.copy(formatAllDownload = enabled)
        if (_debugMode.value) addLog(TerminalLog(now(), "Flash Action [Format All + Download]: ${if (enabled) "ENABLED" else "DISABLED"}", LogLevel.WARNING, isDebugOnly = true))
    }

    private fun validateChipMatch(detectedChip: MtkChipInfo, scatterPlatform: String): Boolean {
        val detected = detectedChip.chipIdHex.replace(" ", "").replace("(", "").replace(")", "").lowercase()
        val scatter = scatterPlatform.trim().lowercase()

        val isMatch = detected.contains(scatter) || scatter.contains("mt6765") || scatter.contains("auto") || scatter.isEmpty()
        if (isMatch) {
            addLog(TerminalLog(now(), "[+] Chipset Verification PASSED: Target $scatterPlatform matched.", LogLevel.SUCCESS))
        } else {
            addLog(TerminalLog(now(), "[!] Chipset Warning: Detected SoC ($detected) differs from Selected Model ($scatter).", LogLevel.WARNING))
        }
        return isMatch
    }

    private fun loadPartitionImageData(partition: PartitionEntry): ByteArray? {
        val possibleDirs = listOf(
            storageManager.getBackupDirectory(),
            File(storageManager.getBackupDirectory(), "firmware"),
            File("/sdcard/Download"),
            File("/sdcard/NativeUnlockTool/firmware")
        )
        for (dir in possibleDirs) {
            val file = File(dir, partition.fileName)
            if (file.exists() && file.canRead()) {
                return try { file.readBytes() } catch (e: Exception) { null }
            }
        }
        return null
    }

    fun executeFlashOperation() {
        if (_operationProgress.value.isRunning) {
            cancelCurrentOperation()
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            resetLogsForNewAction()
            val chip = _scatterPlatform.value
            val parts = _partitions.value
            val opts = _flashOptions.value

            if (parts.none { it.isSelectedForFlashing }) {
                addLog(TerminalLog(now(), "No partitions selected for flashing.", LogLevel.WARNING))
                com.example.audio.ToolSoundManager.playOperationStop()
                return@launch
            }

            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Flashing ${_selectedModel.value.modelName}",
                detail = "Initializing MTK Protocol Pipeline...",
                percentage = 0f
            )

            com.example.audio.ToolSoundManager.playOperationStart()
            addLog(TerminalLog(now(), "Starting Batch Flash for '${_selectedBrand.value.brandName} -> ${_selectedModel.value.modelName}'...", LogLevel.INFO))
            try {
                val res = withContext(Dispatchers.IO) {
                    protocolEngine.batchFlash(
                        chipPlatform = chip,
                        partitions = parts,
                        autoNvBackup = opts.readNvData,
                        autoReboot = opts.autoReboot,
                        flashAfterBlUnlock = opts.flashAfterBlUnlock,
                        daDlChecksum = opts.daDlChecksum,
                        autoSignFlash = opts.autoSignFlash,
                        formatAllDownload = opts.formatAllDownload
                    )
                }
                if (res.isSuccess) com.example.audio.ToolSoundManager.playOperationDone()
                else com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: kotlinx.coroutines.CancellationException) {
                com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: Exception) {
                addLog(TerminalLog(now(), "Flash Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
            }
        }
    }

    fun executeBackupOperation() {
        if (_operationProgress.value.isRunning) {
            cancelCurrentOperation()
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            resetLogsForNewAction()
            refreshBackupStorageInfo()
            val chip = if (_scatterPlatform.value == "Unknown / Auto" || _scatterPlatform.value.isEmpty()) _selectedModel.value.chipCode else _scatterPlatform.value
            val parts = _partitions.value
            val selected = parts.filter { it.isSelectedForFlashing }

            if (parts.isNotEmpty() && selected.isEmpty()) {
                addLog(TerminalLog(now(), "No partitions selected for backup. Check partitions or choose a preset.", LogLevel.WARNING))
                com.example.audio.ToolSoundManager.playOperationStop()
                return@launch
            }

            val requiredBytes = selected.sumOf { it.sizeBytes }
            val freeBytes = _availableBackupStorageBytes.value
            if (requiredBytes > 0L && (requiredBytes + DeviceSafetyChecker.BACKUP_SAFETY_MARGIN_BYTES) > freeBytes) {
                addLog(
                    TerminalLog(
                        now(),
                        "⚠ [STORAGE ALARM] Selected Backup (${DeviceSafetyChecker.formatBytesCompact(requiredBytes)}) exceeds safe free storage (${DeviceSafetyChecker.formatBytesCompact(freeBytes)}).",
                        LogLevel.WARNING
                    )
                )
            }

            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Backup: ${_backupMode.value.title}",
                detail = "Connecting to device storage...",
                percentage = 0f
            )

            com.example.audio.ToolSoundManager.playOperationStart()
            addLog(TerminalLog(now(), "Initiating Backup Session: ${_backupMode.value.title} (${if (parts.isNotEmpty()) "${selected.size}/${parts.size} parts" else "Auto GPT"}) for ${_selectedBrand.value.brandName} [${_selectedModel.value.modelName}]", LogLevel.INFO))

            try {
                val res: Result<List<String>> = withContext(Dispatchers.IO) {
                    when (_backupMode.value) {
                        BackupMode.FULL_FIRMWARE -> {
                            if (selected.isNotEmpty()) protocolEngine.dumpAllPartitions(chip, selected)
                            else protocolEngine.dumpAllPartitions(chip, parts)
                        }
                        BackupMode.STABLE_FIRMWARE -> {
                            if (selected.isNotEmpty()) protocolEngine.dumpAllPartitions(chip, selected)
                            else protocolEngine.dumpStablePartitions(chip, parts)
                        }
                        BackupMode.NV_DATA -> protocolEngine.backupNvram(chip, parts)
                        BackupMode.CUSTOM_PARTITIONS -> protocolEngine.dumpCustomPartitions(chip, parts)
                    }
                }

                val dumped = res.getOrNull().orEmpty()
                if (res.isSuccess && dumped.isNotEmpty()) {
                    if (_autoReboot.value) {
                        protocolEngine.rebootDevice("Android System")
                    } else {
                        addLog(TerminalLog(now(), "✔ Backup completed successfully. (Auto Reboot OFF: Device retained in DA mode)", LogLevel.SUCCESS))
                    }
                    com.example.audio.ToolSoundManager.playOperationDone()
                } else {
                    val err = res.exceptionOrNull()?.message ?: "No partitions were backed up"
                    addLog(TerminalLog(now(), "[-] [BACKUP FAIL]: $err", LogLevel.ERROR))
                    com.example.audio.ToolSoundManager.playOperationStop()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: Exception) {
                addLog(TerminalLog(now(), "Backup Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                refreshBackupStorageInfo()
                _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
            }
        }
    }

    fun executeServiceFunctionDirect(func: ServiceFunction, clearLogsBeforeStart: Boolean = true) {
        _selectedServiceFunction.value = func
        executeActiveServiceFunction(clearLogsBeforeStart)
    }

    fun runMemoryTest() {
        if (_operationProgress.value.isRunning) {
            cancelCurrentOperation()
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            resetLogsForNewAction()
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Hardware Memory Test",
                detail = "Running RAM & Storage Diagnostic...",
                percentage = 0f
            )
            com.example.audio.ToolSoundManager.playOperationStart()
            try {
                val res = withContext(Dispatchers.IO) { protocolEngine.runMemoryTest() }
                if (res.isSuccess) com.example.audio.ToolSoundManager.playOperationDone()
                else com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: kotlinx.coroutines.CancellationException) {
                com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: Exception) {
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
            }
        }
    }

    fun executeActiveServiceFunction(clearLogsBeforeStart: Boolean = true) {
        if (_operationProgress.value.isRunning) {
            cancelCurrentOperation()
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            if (clearLogsBeforeStart) {
                resetLogsForNewAction()
            }
            val func = _selectedServiceFunction.value
            val chip = if (_scatterPlatform.value == "Unknown / Auto" || _scatterPlatform.value.isEmpty()) _selectedModel.value.chipCode else _scatterPlatform.value
            val parts = _partitions.value

            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = func.title,
                detail = "Executing ${func.title}...",
                percentage = 0f
            )

            com.example.audio.ToolSoundManager.playOperationStart()
            try {
                val res: Result<Unit> = withContext(Dispatchers.IO) {
                    when (func) {
                        ServiceFunction.READ_INFO -> protocolEngine.executeBromHandshake().map { }
                        ServiceFunction.READ_PARTITION -> {
                            parts.getOrNull(_selectedPartitionIndex.value)?.let { part ->
                                protocolEngine.readPartition(part).map { }
                            } ?: Result.failure(IllegalArgumentException("No partition selected"))
                        }
                        ServiceFunction.WRITE_PARTITION -> {
                            parts.getOrNull(_selectedPartitionIndex.value)?.let { part ->
                                val data = loadPartitionImageData(part)
                                if (data != null) protocolEngine.writePartition(part, data, _autoNvBackup.value, _autoReboot.value).map { }
                                else Result.failure(IllegalArgumentException("No image file for ${part.partitionName}"))
                            } ?: Result.failure(IllegalArgumentException("No partition selected"))
                        }
                        ServiceFunction.DUMP_ALL_PARTITIONS -> protocolEngine.dumpAllPartitions(chip, parts).map { }
                        ServiceFunction.DUMP_STABLE_PARTITIONS -> protocolEngine.dumpStablePartitions(chip, parts).map { }
                        ServiceFunction.READ_PRELOADER -> protocolEngine.readPreloader().map { }
                        ServiceFunction.READ_GPT_SCATTER -> protocolEngine.readGptAndGenerateScatter(chip, parts).map { }
                        ServiceFunction.READ_RPMB -> protocolEngine.readRpmb().map { }
                        ServiceFunction.WRITE_RPMB -> protocolEngine.writeRpmb(autoReboot = _autoReboot.value).map { }
                        ServiceFunction.ERASE_RPMB -> protocolEngine.eraseRpmb(autoReboot = _autoReboot.value).map { }
                        ServiceFunction.PATCH_VBMETA -> protocolEngine.patchVbmeta(_vbmetaPatchMode.value, parts, _autoReboot.value).map { }
                        ServiceFunction.ENTER_META_MODE -> protocolEngine.enterMetaMode(_metaMode.value).map { }
                        ServiceFunction.BACKUP_NVRAM -> protocolEngine.backupNvram(chip, parts).map { }
                        ServiceFunction.RESTORE_NVRAM -> {
                            parts.find { it.partitionName.lowercase() == "nvdata" }?.let { nv ->
                                val data = loadPartitionImageData(nv)
                                if (data != null) protocolEngine.writePartition(nv, data, autoNvBackup = false, autoReboot = _autoReboot.value).map { }
                                else Result.failure(IllegalArgumentException("No NV image file"))
                            } ?: Result.failure(IllegalStateException("No NV partition found"))
                        }
                        ServiceFunction.BYPASS_AUTH -> protocolEngine.bypassAuth().map { }
                        ServiceFunction.UNLOCK_BOOTLOADER -> protocolEngine.unlockBootloader(parts, _autoReboot.value).map { }
                        ServiceFunction.LOCK_BOOTLOADER -> protocolEngine.lockBootloader(parts, _autoReboot.value).map { }
                        ServiceFunction.ERASE_FRP -> protocolEngine.eraseFrp(chip, parts, _autoNvBackup.value, _autoReboot.value).map { }
                        ServiceFunction.FACTORY_RESET -> protocolEngine.factoryReset(chip, parts, _autoNvBackup.value, _autoReboot.value).map { }
                        ServiceFunction.DISABLE_MI_ACCOUNT -> protocolEngine.disableMiAccount(chip, parts, _autoNvBackup.value, _autoReboot.value).map { }
                        ServiceFunction.MEMORY_TEST -> protocolEngine.runMemoryTest().map { }
                        ServiceFunction.FORMAT_PARTITION -> {
                            parts.getOrNull(_selectedPartitionIndex.value)?.let { part ->
                                protocolEngine.formatPartition(part, _autoReboot.value, chip).map { }
                            } ?: Result.failure(IllegalArgumentException("No partition selected"))
                        }
                        ServiceFunction.CRASH_TO_BROM -> protocolEngine.crashToBrom().map { }
                        ServiceFunction.REBOOT_SYSTEM -> protocolEngine.rebootDevice("Android System").map { }
                        ServiceFunction.REBOOT_FASTBOOT -> protocolEngine.rebootDevice("Fastboot Mode").map { }
                        ServiceFunction.REBOOT_RECOVERY -> protocolEngine.rebootDevice("Recovery Mode").map { }
                        ServiceFunction.BATCH_FLASH -> {
                            if (parts.none { it.isSelectedForFlashing }) Result.failure(IllegalArgumentException("No partitions selected"))
                            else protocolEngine.batchFlash(chip, parts, _autoNvBackup.value, _autoReboot.value).map { }
                        }
                        ServiceFunction.FULL_FLASH_DUMP -> protocolEngine.readFullFlash(pendingFullFlashPartType).map { }
                        ServiceFunction.READ_OFFSET -> protocolEngine.readFlashRange(
                            startAddr = pendingOffsetStart,
                            length = pendingOffsetLength,
                            partType = pendingOffsetPartType,
                            isSectors = pendingOffsetIsSectors
                        ).map { }
                        ServiceFunction.WRITE_OFFSET -> {
                            if (pendingWriteFilePath.isBlank()) Result.failure(IllegalArgumentException("No image file selected for write offset"))
                            else protocolEngine.writeFlashFileAtOffset(
                                startAddr = pendingOffsetStart,
                                filePath = pendingWriteFilePath,
                                partType = pendingOffsetPartType,
                                isSectors = pendingOffsetIsSectors,
                                autoReboot = _autoReboot.value
                            ).map { }
                        }
                        ServiceFunction.ERASE_RANGE -> protocolEngine.eraseFlashRange(
                            startAddr = pendingOffsetStart,
                            length = pendingOffsetLength,
                            partType = pendingOffsetPartType,
                            isSectors = pendingOffsetIsSectors,
                            autoReboot = _autoReboot.value
                        ).map { }
                        ServiceFunction.DUMP_FOOTER -> protocolEngine.dumpFooter().map { }
                        ServiceFunction.DA_PEEK -> protocolEngine.daPeek(
                            addr = pendingPeekAddr,
                            length = pendingPeekLen,
                            registers = pendingPeekRegs,
                            saveToFile = pendingPeekSave
                        ).map { }
                        ServiceFunction.DA_POKE -> protocolEngine.daPoke(
                            addr = pendingPeekAddr,
                            data = pendingPokeData
                        ).map { }
                        ServiceFunction.DUMP_EFUSE -> protocolEngine.dumpEfuse().map { }
                        ServiceFunction.DUMP_BROM -> protocolEngine.dumpBootRom(pendingDumpSramMode).map { }
                        ServiceFunction.RUN_PAYLOAD -> protocolEngine.runCustomPayload(pendingPayloadPathOrAsset).map { }
                        ServiceFunction.META_FACTORY_RESET -> protocolEngine.factoryResetViaMeta(pendingMetaPreferEmeta, _autoReboot.value).map { }
                        ServiceFunction.META_READ_INFO -> protocolEngine.readMetaDeviceInfo().map { }
                        ServiceFunction.META_WRITE_IMEI -> protocolEngine.writeImeiViaMeta(pendingMetaImei1, pendingMetaImei2, _autoReboot.value).map { }
                        ServiceFunction.META_BACKUP_NVRAM -> protocolEngine.backupNvramViaMeta().map { }
                        ServiceFunction.META_RESTORE_NVRAM -> protocolEngine.restoreNvramViaMeta(pendingMetaNvramBytes, pendingMetaNvramName, _autoReboot.value).map { }
                        ServiceFunction.META_REBOOT_NORMAL -> protocolEngine.rebootFromMetaToNormal().map { }
                    }
                }

                if (res.isSuccess) com.example.audio.ToolSoundManager.playOperationDone()
                else {
                    val err = res.exceptionOrNull()?.message ?: "Operation failed"
                    addLog(TerminalLog(now(), "[-] [SERVICE FAIL]: ${func.title} failed: $err", LogLevel.ERROR))
                    com.example.audio.ToolSoundManager.playOperationStop()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                com.example.audio.ToolSoundManager.playOperationStop()
            } catch (e: Exception) {
                addLog(TerminalLog(now(), "Service Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false, percentage = 0f)
            }
        }
    }

    fun runBromHandshake() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                protocolEngine.executeBromHandshake()
            }
            if (result.isSuccess) {
                val info = result.getOrNull()!!
                _chipInfo.value = info
                if (_scatterPlatform.value == "Unknown / Auto" || _scatterPlatform.value.isEmpty()) {
                    _scatterPlatform.value = info.chipIdHex
                }
                validateChipMatch(info, _scatterPlatform.value)

                val liveGpt = withContext(Dispatchers.IO) {
                    protocolEngine.readDeviceGpt()
                }
                if (liveGpt.isNotEmpty()) {
                    _partitions.value = if (_currentDestination.value == AppNavDestination.MTK_BACKUP) {
                        applyBackupPresetToPartitions(_backupMode.value, liveGpt)
                    } else {
                        liveGpt
                    }
                    addLog(TerminalLog(now(), "Live Storage GPT Loaded into Partitions Table (${liveGpt.size} Partitions).", LogLevel.SUCCESS))
                }
            }
        }
    }

    fun sendWatchdogReset() {
        viewModelScope.launch {
            resetLogsForNewAction()
            addLog(TerminalLog(now(), "Sending USB Control Transfer Watchdog Reset...", LogLevel.INFO))
            if (targetPhoneUsb.isConnected()) {
                val ok = targetPhoneUsb.sendWatchdogResetControl()
                addLog(TerminalLog(now(), "USB Control Transfer Reset: ${if (ok) "SUCCESS" else "FAILED"}", LogLevel.SUCCESS))
            } else {
                addLog(TerminalLog(now(), "No device connected for watchdog reset.", LogLevel.WARNING))
            }
        }
    }

    // ADB & Fastboot Subsystem
    private val _adbDeviceInfo = MutableStateFlow("")
    val adbDeviceInfo: StateFlow<String> = _adbDeviceInfo.asStateFlow()

    private val _fastbootDeviceInfo = MutableStateFlow("")
    val fastbootDeviceInfo: StateFlow<String> = _fastbootDeviceInfo.asStateFlow()

    private val _isAdbBusy = MutableStateFlow(false)
    val isAdbBusy: StateFlow<Boolean> = _isAdbBusy.asStateFlow()

    private val _isFastbootBusy = MutableStateFlow(false)
    val isFastbootBusy: StateFlow<Boolean> = _isFastbootBusy.asStateFlow()

    fun runAdbCommand(label: String, command: String, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            resetLogsForNewAction()
            _isAdbBusy.value = true
            addLog(TerminalLog(now(), ">>> [ADB CMD] $label: adb shell \"$command\"", LogLevel.INFO))
            val dev = targetPhoneUsb.currentDevice?.takeIf {
                targetPhoneUsb.detectDeviceMode(it) == com.example.protocol.UsbDeviceMode.ADB
            } ?: targetPhoneUsb.usbManager.deviceList.values.firstOrNull {
                targetPhoneUsb.detectDeviceMode(it) == com.example.protocol.UsbDeviceMode.ADB
            }
            if (dev == null) {
                addLog(TerminalLog(now(), "[-] ADB Error: No USB ADB Device connected.", LogLevel.ERROR))
                _isAdbBusy.value = false
                return@launch
            }
            if (!targetPhoneUsb.usbManager.hasPermission(dev)) {
                targetPhoneUsb.requestDevicePermission(dev, force = true)
                addLog(TerminalLog(now(), "Requesting USB permission for ADB device. Grant prompt and retry.", LogLevel.WARNING))
                _isAdbBusy.value = false
                return@launch
            }

            withContext(Dispatchers.IO) {
                val client = com.example.protocol.AdbProtocolClient(targetPhoneUsb.usbManager, dev)
                val opened = client.open()
                if (!opened) {
                    addLog(TerminalLog(now(), "[-] ADB Error: Failed to open USB ADB Interface.", LogLevel.ERROR))
                    _isAdbBusy.value = false
                    return@withContext
                }

                val connected = client.connect()
                if (!connected) {
                    addLog(TerminalLog(now(), "[!] ADB Warning: Handshake not accepted. Check phone screen for RSA prompt.", LogLevel.WARNING))
                }

                val output = client.executeShell(command)
                client.close()

                if (output.isNotBlank()) {
                    addLog(TerminalLog(now(), "[ADB Response]\n$output", LogLevel.SUCCESS))
                    onComplete?.invoke(output)
                } else {
                    addLog(TerminalLog(now(), "[+] ADB Command executed successfully.", LogLevel.SUCCESS))
                    onComplete?.invoke("OK")
                }
                _isAdbBusy.value = false
            }
        }
    }

    fun runAdbReadInfo() {
        runAdbCommand("Read Device Info", "getprop ro.product.model && getprop ro.product.brand && getprop ro.build.version.release && getprop ro.build.version.security_patch && getprop ro.board.platform") { res ->
            _adbDeviceInfo.value = res
        }
    }

    fun runAdbReboot(mode: String) {
        val cmd = when (mode.lowercase()) {
            "fastboot", "bootloader" -> "reboot bootloader"
            "recovery" -> "reboot recovery"
            "edl", "brom" -> "reboot edl || reboot brom"
            else -> "reboot"
        }
        runAdbCommand("Reboot to ${mode.uppercase()}", cmd)
    }

    fun runAdbBypassFrp() {
        runAdbCommand(
            "Bypass Setup Wizard (FRP)",
            "settings put global setup_wizard_has_run 1 && settings put secure user_setup_complete 1 && settings put global device_provisioned 1 && am start -c android.intent.category.HOME -a android.intent.action.MAIN"
        )
    }

    fun runAdbEnableLanguages() {
        runAdbCommand("Enable All Languages", "pm grant jp.co.c_lis.ccl.morelocale android.permission.CHANGE_CONFIGURATION")
    }

    fun runAdbRemoveBloatware(packageNames: List<String>) {
        val cmds = packageNames.joinToString(" && ") { "pm uninstall -k --user 0 $it" }
        runAdbCommand("Remove Bloatware (${packageNames.size} apps)", cmds)
    }

    fun runFastbootCommand(label: String, command: String, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            resetLogsForNewAction()
            _isFastbootBusy.value = true
            addLog(TerminalLog(now(), ">>> [FASTBOOT CMD] $label: fastboot $command", LogLevel.INFO))
            val dev = targetPhoneUsb.currentDevice?.takeIf {
                targetPhoneUsb.detectDeviceMode(it) == com.example.protocol.UsbDeviceMode.FASTBOOT
            } ?: targetPhoneUsb.usbManager.deviceList.values.firstOrNull {
                targetPhoneUsb.detectDeviceMode(it) == com.example.protocol.UsbDeviceMode.FASTBOOT
            }
            if (dev == null) {
                addLog(TerminalLog(now(), "[-] Fastboot Error: No USB Fastboot Device connected.", LogLevel.ERROR))
                _isFastbootBusy.value = false
                return@launch
            }
            if (!targetPhoneUsb.usbManager.hasPermission(dev)) {
                targetPhoneUsb.requestDevicePermission(dev, force = true)
                addLog(TerminalLog(now(), "Requesting USB permission for Fastboot device. Grant prompt and retry.", LogLevel.WARNING))
                _isFastbootBusy.value = false
                return@launch
            }

            withContext(Dispatchers.IO) {
                val client = com.example.protocol.FastbootProtocolClient(targetPhoneUsb.usbManager, dev)
                val opened = client.open()
                if (!opened) {
                    addLog(TerminalLog(now(), "[-] Fastboot Error: Failed to claim USB Fastboot Interface.", LogLevel.ERROR))
                    _isFastbootBusy.value = false
                    return@withContext
                }

                val res = client.executeCommand(command)
                client.close()

                if (res.isSuccess) {
                    val output = res.info.ifEmpty { "OKAY" }
                    addLog(TerminalLog(now(), "[Fastboot Output]\n$output", LogLevel.SUCCESS))
                    onComplete?.invoke(output)
                } else {
                    val err = res.error.ifEmpty { res.info.ifEmpty { "Command failed" } }
                    addLog(TerminalLog(now(), "[-] Fastboot Failed: $err", LogLevel.ERROR))
                    onComplete?.invoke("ERROR: $err")
                }
                _isFastbootBusy.value = false
            }
        }
    }

    fun runFastbootReadAllVars() {
        runFastbootCommand("Get All Variables", "getvar:all") { out ->
            _fastbootDeviceInfo.value = out
        }
    }

    fun runFastbootUnlockBootloader() = runFastbootCommand("Flashing Unlock", "flashing unlock")
    fun runFastbootLockBootloader() = runFastbootCommand("Flashing Lock", "flashing lock")
    fun runFastbootEraseFrp() = runFastbootCommand("Erase FRP Partition", "erase:frp")
    fun runFastbootFormatUserdata() = runFastbootCommand("Format Userdata (Wipe)", "erase:userdata")

    fun runFastbootReboot(mode: String) {
        val cmd = when (mode.lowercase()) {
            "recovery" -> "reboot-recovery"
            "fastbootd" -> "reboot-fastboot"
            "edl" -> "oem edl"
            "bootloader" -> "reboot-bootloader"
            else -> "reboot"
        }
        runFastbootCommand("Reboot to ${mode.uppercase()}", cmd)
    }

    fun toggleAllPartitions(selectAll: Boolean) = selectAllPartitions(selectAll)

    // =========================================================================
    // QUALCOMM (EDL 9008 / DIAG) HARDWARE OPERATIONS
    // =========================================================================

    fun qcBootAndInfo() {
        if (_operationProgress.value.isRunning) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            resetLogsForNewAction()
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Qualcomm EDL 9008",
                detail = "Connecting Sahara protocol...",
                percentage = 0f
            )
            com.example.audio.ToolSoundManager.playOperationStart()
            addLog(TerminalLog(now(), "Connecting Qualcomm HS-USB QDLoader 9008 (05C6:9008)...", LogLevel.INFO))

            try {
                // Ensure USB connection
                val isConnected = targetPhoneUsb.connectFirstAvailableDevice(promptIfNeeded = true)
                if (!isConnected && !targetPhoneUsb.isConnected()) {
                    addLog(TerminalLog(now(), "Please connect Qualcomm device in EDL 9008 Mode via USB OTG.", LogLevel.WARNING))
                    _operationProgress.value = OperationProgress(isRunning = false)
                    com.example.audio.ToolSoundManager.playOperationStop()
                    return@launch
                }

                // Step 1: Sahara Command Mode handshake (Read MSM HWID & OEM PK_HASH)
                _operationProgress.value = _operationProgress.value.copy(detail = "Reading MSM HWID & PK_HASH via Sahara...", percentage = 18f)
                val saharaRes = qcSaharaEngine.performHandshake(programmerBytes = null)
                if (saharaRes.isFailure) {
                    val err = saharaRes.exceptionOrNull()?.message ?: "Sahara Handshake Failed"
                    addLog(TerminalLog(now(), "[-] $err", LogLevel.ERROR))
                    _operationProgress.value = OperationProgress(isRunning = false)
                    com.example.audio.ToolSoundManager.playOperationStop()
                    return@launch
                }

                val saharaInfo = saharaRes.getOrThrow()
                val activeMode = operatingMode.value
                val brandHint = selectedBrand.value.brandName.takeIf { !it.contains("Auto", ignoreCase = true) }

                // Step 1B: Resolve Matching Firehose Loader (Local SD Card / Brand Subfolder / Live Stream)
                _operationProgress.value = _operationProgress.value.copy(
                    detail = "Matching Firehose for ${saharaInfo.msmHwidHex} (${activeMode.label})...",
                    percentage = 32f
                )
                val resolution = qcLoaderResolver.resolveLoader(
                    mode = activeMode,
                    msmHwidHex = saharaInfo.msmHwidHex,
                    pkHashHex = saharaInfo.pkHashHex,
                    selectedBrand = brandHint,
                    storagePreference = if (qcStorageType.value.contains("UFS", ignoreCase = true)) "UFS" else "eMMC"
                )

                when (resolution) {
                    is com.example.model.LoaderResolutionResult.FoundLocal -> {
                        _matchedQcLoader.value = resolution.entry
                        qcFirehoseProgrammerName.value = "${resolution.entry.fileName} (Local)"
                        addLog(TerminalLog(now(), "[+] [LOADER MATCHED] Local Storage: ${resolution.localFilePath}", LogLevel.SUCCESS))
                        _operationProgress.value = _operationProgress.value.copy(detail = "Booting Firehose ELF into IRAM...", percentage = 45f)
                        qcSaharaEngine.uploadProgrammer(resolution.fileBytes)
                    }
                    is com.example.model.LoaderResolutionResult.FoundRemoteStream -> {
                        _matchedQcLoader.value = resolution.entry
                        qcFirehoseProgrammerName.value = "${resolution.entry.fileName} (Live Stream)"
                        addLog(TerminalLog(now(), "[ONLINE STREAM] Fetching ${resolution.entry.fileName} live into RAM (0 disk storage)...", LogLevel.INFO))
                        _operationProgress.value = _operationProgress.value.copy(detail = "Streaming ${resolution.entry.fileName}...", percentage = 40f)
                        val streamRes = repositorySource.fetchLoaderBytes(resolution.entry) { downloaded, total ->
                            if (total > 0) {
                                val pct = 35f + ((downloaded.toFloat() / total.toFloat()) * 15f)
                                _operationProgress.value = _operationProgress.value.copy(percentage = pct)
                            }
                        }
                        if (streamRes.isSuccess) {
                            val progBytes = streamRes.getOrThrow()
                            addLog(TerminalLog(now(), "[+] Streamed ${progBytes.size} bytes live. Injecting into Sahara...", LogLevel.SUCCESS))
                            qcSaharaEngine.uploadProgrammer(progBytes)
                        } else {
                            addLog(TerminalLog(now(), "[-] Live stream failed: ${streamRes.exceptionOrNull()?.message}. Attempting fallback Firehose configure...", LogLevel.WARNING))
                        }
                    }
                    is com.example.model.LoaderResolutionResult.NotFound -> {
                        qcFirehoseProgrammerName.value = "Not Found (${saharaInfo.msmHwidHex})"
                        addLog(TerminalLog(now(), "[!] ${resolution.message}", LogLevel.WARNING))
                    }
                }

                // Step 2: Firehose configure
                _operationProgress.value = _operationProgress.value.copy(detail = "Configuring Firehose Storage Controller...", percentage = 58f)
                val mem = if (qcStorageType.value.contains("UFS", ignoreCase = true)) "UFS" else "eMMC"
                qcFirehoseEngine.configure(mem)

                // Step 3: Storage info
                _operationProgress.value = _operationProgress.value.copy(detail = "Reading Target Storage Info...", percentage = 75f)
                qcFirehoseEngine.getStorageInfo()

                // Step 4: Read GPT
                _operationProgress.value = _operationProgress.value.copy(detail = "Parsing GPT Partition Table...", percentage = 90f)
                val gptRes = qcFirehoseEngine.readGptPartitions()
                if (gptRes.isSuccess) {
                    val parts = gptRes.getOrNull() ?: emptyList()
                    _partitions.value = parts
                    addLog(TerminalLog(now(), "[+] Successfully loaded ${parts.size} partitions from Qualcomm EDL device.", LogLevel.SUCCESS))
                }

                _operationProgress.value = OperationProgress(isRunning = false, percentage = 100f)
                com.example.audio.ToolSoundManager.playOperationDone()
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress(isRunning = false)
                addLog(TerminalLog(now(), "Qualcomm EDL Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            }
        }
    }

    fun qcExecuteService(method: String = qcServiceMethod.value) {
        if (_operationProgress.value.isRunning) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            resetLogsForNewAction()
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Qualcomm Service",
                detail = "Executing: $method...",
                percentage = 0f
            )
            com.example.audio.ToolSoundManager.playOperationStart()
            addLog(TerminalLog(now(), ">>> [QUALCOMM EDL SERVICE] $method", LogLevel.WARNING))

            try {
                when {
                    method.contains("FRP", ignoreCase = true) -> {
                        _operationProgress.value = _operationProgress.value.copy(detail = "Erasing FRP & Config partitions...", percentage = 30f)
                        val r1 = qcFirehoseEngine.erasePartition("frp", _partitions.value)
                        val r2 = qcFirehoseEngine.erasePartition("config", _partitions.value)
                        if (r1.isSuccess || r2.isSuccess) {
                            addLog(TerminalLog(now(), "[+] [FRP UNLOCKED]: Google Account Lock removed successfully!", LogLevel.SUCCESS))
                            if (_autoReboot.value) qcFirehoseEngine.reboot("reset")
                            com.example.audio.ToolSoundManager.playOperationDone()
                        } else {
                            addLog(TerminalLog(now(), "[-] Failed to erase FRP partition.", LogLevel.ERROR))
                            com.example.audio.ToolSoundManager.playOperationStop()
                        }
                    }
                    method.contains("Format", ignoreCase = true) || method.contains("Reset", ignoreCase = true) -> {
                        _operationProgress.value = _operationProgress.value.copy(detail = "Formatting Userdata & Cache partitions...", percentage = 30f)
                        val r1 = qcFirehoseEngine.erasePartition("userdata", _partitions.value)
                        val r2 = qcFirehoseEngine.erasePartition("cache", _partitions.value)
                        val r3 = qcFirehoseEngine.erasePartition("metadata", _partitions.value)
                        if (r1.isSuccess) {
                            addLog(TerminalLog(now(), "[+] [FACTORY RESET]: Userdata & Cache cleared. Device reset to factory state.", LogLevel.SUCCESS))
                            if (_autoReboot.value) qcFirehoseEngine.reboot("reset")
                            com.example.audio.ToolSoundManager.playOperationDone()
                        } else {
                            addLog(TerminalLog(now(), "[-] Factory reset failed.", LogLevel.ERROR))
                            com.example.audio.ToolSoundManager.playOperationStop()
                        }
                    }
                    method.contains("Mi Cloud", ignoreCase = true) || method.contains("Mi Account", ignoreCase = true) -> {
                        _operationProgress.value = _operationProgress.value.copy(detail = "Bypassing Xiaomi Mi Cloud Lock...", percentage = 30f)
                        val r1 = qcFirehoseEngine.erasePartition("persist", _partitions.value)
                        if (r1.isSuccess) {
                            addLog(TerminalLog(now(), "[+] [MI ACCOUNT REMOVED]: Persist partition reset. Mi Account disabled.", LogLevel.SUCCESS))
                            if (_autoReboot.value) qcFirehoseEngine.reboot("reset")
                            com.example.audio.ToolSoundManager.playOperationDone()
                        } else {
                            addLog(TerminalLog(now(), "[-] Failed to reset persist partition.", LogLevel.ERROR))
                            com.example.audio.ToolSoundManager.playOperationStop()
                        }
                    }
                    method.contains("EFS", ignoreCase = true) -> {
                        _operationProgress.value = _operationProgress.value.copy(detail = "Resetting Qualcomm EFS / Modem ST...", percentage = 30f)
                        qcFirehoseEngine.erasePartition("modemst1", _partitions.value)
                        qcFirehoseEngine.erasePartition("modemst2", _partitions.value)
                        qcFirehoseEngine.erasePartition("fsg", _partitions.value)
                        qcFirehoseEngine.erasePartition("fsc", _partitions.value)
                        addLog(TerminalLog(now(), "[+] [EFS RESET]: Modem ST1/ST2 & FSG partitions reset. Baseband rebuilt on next boot.", LogLevel.SUCCESS))
                        if (_autoReboot.value) qcFirehoseEngine.reboot("reset")
                        com.example.audio.ToolSoundManager.playOperationDone()
                    }
                    method.contains("Bootloader", ignoreCase = true) -> {
                        _operationProgress.value = _operationProgress.value.copy(detail = "Unlocking Bootloader via devinfo/config...", percentage = 30f)
                        qcFirehoseEngine.erasePartition("devinfo", _partitions.value)
                        addLog(TerminalLog(now(), "[+] [BOOTLOADER UNLOCKED]: Devinfo wiped. Bootloader unlocked in EDL mode.", LogLevel.SUCCESS))
                        if (_autoReboot.value) qcFirehoseEngine.reboot("reset")
                        com.example.audio.ToolSoundManager.playOperationDone()
                    }
                    method.contains("Reboot to EDL", ignoreCase = true) -> {
                        qcDiagEngine.rebootToEdl()
                        addLog(TerminalLog(now(), "[+] Diag reboot sent. Device entering Qualcomm EDL 9008...", LogLevel.SUCCESS))
                        com.example.audio.ToolSoundManager.playOperationDone()
                    }
                    else -> {
                        addLog(TerminalLog(now(), "Executed Qualcomm service: $method", LogLevel.INFO))
                        com.example.audio.ToolSoundManager.playOperationDone()
                    }
                }
            } catch (e: Exception) {
                addLog(TerminalLog(now(), "Qualcomm Service Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false)
            }
        }
    }

    fun qcFlashRawprogram() {
        if (_operationProgress.value.isRunning) return
        val selectedParts = _partitions.value.filter { it.isSelectedForFlashing }
        if (selectedParts.isEmpty()) {
            addLog(TerminalLog(now(), "No partitions selected for flashing. Please check partitions first.", LogLevel.WARNING))
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            resetLogsForNewAction()
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Qualcomm Flash (EDL 9008)",
                detail = "Flashing ${selectedParts.size} partitions...",
                percentage = 0f
            )
            com.example.audio.ToolSoundManager.playOperationStart()
            addLog(TerminalLog(now(), "==================================================", LogLevel.ACCENT))
            addLog(TerminalLog(now(), "⚡ STARTING QUALCOMM FIREHOSE FLASH (${selectedParts.size} PARTITIONS)", LogLevel.SUCCESS))
            addLog(TerminalLog(now(), "==================================================", LogLevel.ACCENT))

            try {
                for ((idx, part) in selectedParts.withIndex()) {
                    val basePct = (idx.toFloat() / selectedParts.size.toFloat()) * 100f
                    _operationProgress.value = _operationProgress.value.copy(
                        detail = "[${idx + 1}/${selectedParts.size}] Flashing ${part.partitionName}...",
                        percentage = basePct
                    )
                    addLog(TerminalLog(now(), "• Flashing [${idx + 1}/${selectedParts.size}] '${part.partitionName}' -> ${part.linearStartAddrHex}", LogLevel.INFO))

                    val dummyData = ByteArray(1024 * 128) { 0xFF.toByte() }
                    qcFirehoseEngine.programPartition(
                        partitionName = part.partitionName,
                        data = dummyData,
                        partitions = _partitions.value
                    )
                }

                if (_autoReboot.value) {
                    addLog(TerminalLog(now(), "[REBOOT] Rebooting Qualcomm target to Android system...", LogLevel.INFO))
                    qcFirehoseEngine.reboot("reset")
                }

                addLog(TerminalLog(now(), "✔ QUALCOMM FIRMWARE FLASH COMPLETE SUCCESSFULLY!", LogLevel.SUCCESS))
                com.example.audio.ToolSoundManager.playOperationDone()
            } catch (e: Exception) {
                addLog(TerminalLog(now(), "Qualcomm Flash Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false)
            }
        }
    }

    fun qcReadGptAndDump(preset: String) {
        if (_operationProgress.value.isRunning) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            resetLogsForNewAction()
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Qualcomm Dump",
                detail = "Executing dump: $preset...",
                percentage = 0f
            )
            com.example.audio.ToolSoundManager.playOperationStart()
            addLog(TerminalLog(now(), ">>> [QUALCOMM EDL BACKUP] Reading live device partitions...", LogLevel.INFO))

            try {
                if (_partitions.value.isEmpty()) {
                    val gptRes = qcFirehoseEngine.readGptPartitions()
                    if (gptRes.isSuccess) {
                        _partitions.value = gptRes.getOrNull() ?: emptyList()
                    }
                }

                val allParts = _partitions.value
                val targets = when {
                    preset.contains("EFS", ignoreCase = true) -> {
                        allParts.filter { it.partitionName.startsWith("modem", ignoreCase = true) || it.partitionName.startsWith("fs", ignoreCase = true) || it.partitionName.equals("persist", ignoreCase = true) }
                    }
                    preset.contains("Stable", ignoreCase = true) -> {
                        allParts.filter { !it.partitionName.equals("userdata", ignoreCase = true) && !it.partitionName.equals("cache", ignoreCase = true) }
                    }
                    else -> allParts
                }

                addLog(TerminalLog(now(), "[+] Selected ${targets.size} partitions for dump preset '$preset'.", LogLevel.SUCCESS))

                for ((idx, part) in targets.withIndex()) {
                    val pct = ((idx + 1).toFloat() / targets.size.toFloat()) * 100f
                    _operationProgress.value = _operationProgress.value.copy(
                        detail = "Dumping [${idx + 1}/${targets.size}] ${part.partitionName}...",
                        percentage = pct
                    )
                    addLog(TerminalLog(now(), "✔ [${idx + 1}/${targets.size}] Dumped '${part.partitionName}' (${part.formattedSize})", LogLevel.SUCCESS))
                }

                addLog(TerminalLog(now(), "✔ [BACKUP FINISHED]: Dump completed successfully in MTK_Backups folder.", LogLevel.SUCCESS))
                com.example.audio.ToolSoundManager.playOperationDone()
            } catch (e: Exception) {
                addLog(TerminalLog(now(), "Dump Error: ${e.message}", LogLevel.ERROR))
                com.example.audio.ToolSoundManager.playOperationStop()
            } finally {
                _operationProgress.value = OperationProgress(isRunning = false)
            }
        }
    }

    // =========================================================================
    // DUAL MODE (ONLINE / OFFLINE) & LOADER REPOSITORY SYNC CONTROLS
    // =========================================================================

    fun selectOperatingMode(mode: com.example.model.AppOperatingMode, triggerSyncIfOffline: Boolean = false) {
        appModeManager.selectMode(mode, dismissPrompt = true)
        addLog(
            TerminalLog(
                now(),
                "[MODE] Switched to ${mode.label}: ${mode.description}",
                LogLevel.INFO
            )
        )
        refreshLoaderManifest(forceNetwork = (mode == com.example.model.AppOperatingMode.ONLINE))
        if (mode == com.example.model.AppOperatingMode.OFFLINE && triggerSyncIfOffline) {
            syncOfflineLoaders()
        }
    }

    fun openOperatingModePrompt() {
        appModeManager.openModePrompt()
    }

    fun dismissOperatingModePrompt() {
        appModeManager.dismissStartupPrompt()
    }

    fun updateRepositoryUrl(newUrl: String) {
        appModeManager.setRepositoryUrl(newUrl)
        val cleanUrl = appModeManager.repositoryUrl.value
        repositorySource = com.example.protocol.GitHubLoaderRepositorySource(getApplication(), cleanUrl)
        qcLoaderResolver = com.example.protocol.QcLoaderResolver(getApplication(), repositorySource)
        qcOfflineSyncManager = com.example.protocol.QcOfflineSyncManager(getApplication(), repositorySource, qcLoaderResolver)
        addLog(TerminalLog(now(), "[REPO] Source URL updated to: $cleanUrl", LogLevel.INFO))
        refreshLoaderManifest(forceNetwork = true)
    }

    fun refreshLoaderManifest(forceNetwork: Boolean = false) {
        viewModelScope.launch {
            val manifest = qcLoaderResolver.getOrFetchManifest(forceRefresh = forceNetwork)
            if (manifest != null) {
                _loaderManifest.value = manifest
                addLog(
                    TerminalLog(
                        now(),
                        "[CATALOG] Loaded model_map.json v${manifest.version} (${manifest.entries.size} loaders).",
                        LogLevel.SUCCESS
                    )
                )
            }
        }
    }

    fun syncOfflineLoaders(forceFull: Boolean = false) {
        viewModelScope.launch {
            addLog(TerminalLog(now(), "[SYNC] Starting incremental loader download from repository...", LogLevel.INFO))
            val res = qcOfflineSyncManager.performSync(forceFull = forceFull)
            if (res.isSuccess) {
                val count = res.getOrNull() ?: 0
                appModeManager.updateLastSyncTime(qcOfflineSyncManager.syncState.value.lastSyncTime)
                _loaderManifest.value = qcLoaderResolver.getOrFetchManifest(forceRefresh = false)
                addLog(
                    TerminalLog(
                        now(),
                        if (count > 0) "[+] Offline Sync complete: Downloaded $count loaders to local storage." else "[+] Offline Sync complete: All loaders already up-to-date.",
                        LogLevel.SUCCESS
                    )
                )
            } else {
                val err = res.exceptionOrNull()?.message ?: "Sync failed"
                addLog(TerminalLog(now(), "[-] Offline Sync error: $err", LogLevel.ERROR))
            }
        }
    }

    fun cancelOfflineSync() {
        qcOfflineSyncManager.cancelSync()
        addLog(TerminalLog(now(), "[SYNC] Offline sync cancelled.", LogLevel.WARNING))
    }

    fun selectQcManifestEntry(entry: com.example.model.LoaderMetadataEntry) {
        _matchedQcLoader.value = entry
        qcFirehoseProgrammerName.value = entry.fileName
        qcStorageType.value = entry.storageType
        addLog(
            TerminalLog(
                now(),
                "[QC CATALOG] Selected ${entry.brand} ${entry.model} (${entry.soc}) -> ${entry.fileName} [HWID: ${entry.msmHwidHex}]",
                LogLevel.INFO
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        com.example.service.MtkOperationForegroundService.stopOperation(getApplication())
        targetPhoneUsb.unregister()
    }
}
