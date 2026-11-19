package com.example.protocol

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class UsbDeviceMode(val label: String, val description: String) {
    BROM("MTK BROM", "MediaTek BootROM Mode (Flash & Service Ready)"),
    PRELOADER("Preloader", "MTK Preloader / DA VCOM Port"),
    META("MTK META", "MediaTek META Engineering Mode"),
    EDL("Qualcomm EDL", "Qualcomm HS-USB QDLoader 9008"),
    DIAG("Qualcomm Diag", "Qualcomm Diagnostics / Modem Port"),
    FASTBOOT("Fastboot", "Android Fastboot Bootloader Mode"),
    ADB("ADB Debugging", "Android USB Debugging Interface"),
    MTP("MTP / File Transfer", "Media Transfer Protocol (Normal Android Boot)"),
    CDC_SERIAL("CDC Serial", "Serial / UART Bridge Device"),
    UNKNOWN("USB Device", "Connected USB Peripheral"),
    NONE("Unplugged", "No USB Device Connected")
}

typealias UsbMode = UsbDeviceMode

data class RealtimePortStatus(
    val mode: UsbDeviceMode = UsbDeviceMode.NONE,
    val modeLabel: String = "NO DEVICE",
    val isConnected: Boolean = false,
    val deviceName: String = "",
    val vidHex: String = "",
    val pidHex: String = "",
    val isLockedByAction: Boolean = false,
    val lockedActionName: String = ""
)

sealed class TargetPhoneState {
    object Disconnected : TargetPhoneState()
    data class RequestingPermission(
        val deviceName: String,
        val mode: UsbDeviceMode,
        val vidPid: String
    ) : TargetPhoneState()
    data class Connected(
        val deviceName: String,
        val mode: UsbDeviceMode,
        val isBromMode: Boolean,
        val vidPid: String,
        val fileDescriptor: Int = -1
    ) : TargetPhoneState()
    data class Error(val message: String) : TargetPhoneState()
}

class TargetPhoneUsbManager(
    private val context: Context
) {
    private val _realtimePortStatus = MutableStateFlow(RealtimePortStatus())
    val realtimePortStatus: StateFlow<RealtimePortStatus> = _realtimePortStatus.asStateFlow()

    @Volatile
    var lockedActionTargetMode: UsbDeviceMode? = null
    @Volatile
    var lockedActionName: String = ""
    @Volatile
    var lockedDeviceKey: String? = null

    fun lockForAction(actionName: String, targetMode: UsbDeviceMode, device: UsbDevice? = null) {
        lockedActionName = actionName
        lockedActionTargetMode = targetMode
        lockedDeviceKey = device?.let { deviceKey(it) }
        val current = _realtimePortStatus.value
        _realtimePortStatus.value = current.copy(
            isLockedByAction = true,
            lockedActionName = actionName,
            modeLabel = "🔒 ${current.modeLabel.removePrefix("🔒 ")}"
        )
    }

    fun unlockAction() {
        lockedActionName = ""
        lockedActionTargetMode = null
        lockedDeviceKey = null
        refreshRealtimePortStatus()
    }

    fun isModeCompatibleWithLockedAction(mode: UsbDeviceMode, lockedMode: UsbDeviceMode): Boolean {
        if (mode == lockedMode) return true
        if (lockedMode == UsbDeviceMode.BROM && (mode == UsbDeviceMode.PRELOADER || mode == UsbDeviceMode.CDC_SERIAL)) {
            return true
        }
        return false
    }

    fun refreshRealtimePortStatus() {
        val devices = runCatching { usbManager.deviceList?.values?.toList().orEmpty() }.getOrDefault(emptyList())
        if (devices.isEmpty()) {
            _realtimePortStatus.value = RealtimePortStatus(
                mode = UsbDeviceMode.NONE,
                modeLabel = "NO DEVICE",
                isConnected = false,
                isLockedByAction = lockedActionTargetMode != null,
                lockedActionName = lockedActionName
            )
            return
        }

        val prioritizedDevice = if (lockedActionTargetMode != null) {
            devices.firstOrNull { isModeCompatibleWithLockedAction(detectDeviceMode(it), lockedActionTargetMode!!) }
                ?: devices.firstOrNull()
        } else {
            devices.firstOrNull { dev ->
                val m = detectDeviceMode(dev)
                m in FLASH_SERVICE_MODES || m == UsbDeviceMode.ADB
            } ?: devices.firstOrNull()
        }

        if (prioritizedDevice == null) {
            _realtimePortStatus.value = RealtimePortStatus(
                mode = UsbDeviceMode.NONE,
                modeLabel = "NO DEVICE",
                isConnected = false,
                isLockedByAction = lockedActionTargetMode != null,
                lockedActionName = lockedActionName
            )
            return
        }

        val mode = detectDeviceMode(prioritizedDevice)
        val modeLabel = when (mode) {
            UsbDeviceMode.BROM -> "BROM"
            UsbDeviceMode.PRELOADER -> "PRELOADER"
            UsbDeviceMode.META -> "META"
            UsbDeviceMode.FASTBOOT -> "FASTBOOT"
            UsbDeviceMode.ADB -> "ADB"
            UsbDeviceMode.EDL -> "EDL (9008)"
            UsbDeviceMode.DIAG -> "DIAG"
            UsbDeviceMode.MTP -> "MTP"
            UsbDeviceMode.CDC_SERIAL -> "SERIAL"
            UsbDeviceMode.UNKNOWN -> "USB (0x%04X)".format(prioritizedDevice.vendorId)
            UsbDeviceMode.NONE -> "NO DEVICE"
        }

        val displayLabel = if (lockedActionTargetMode != null) "🔒 $modeLabel" else modeLabel

        _realtimePortStatus.value = RealtimePortStatus(
            mode = mode,
            modeLabel = displayLabel,
            isConnected = true,
            deviceName = prioritizedDevice.deviceName,
            vidHex = String.format("0x%04X", prioritizedDevice.vendorId),
            pidHex = String.format("0x%04X", prioritizedDevice.productId),
            isLockedByAction = lockedActionTargetMode != null,
            lockedActionName = lockedActionName
        )
    }
    companion object {
        const val ACTION_USB_PHONE_PERMISSION = "com.example.mtkbridge.USB_PHONE_PERMISSION"
        const val VID_MTK = 0x0E8D
        const val MTK_VID = 0x0E8D
        const val QCOM_VID = 0x05C6
        const val PID_BROM = 0x0003
        const val PID_PRELOADER = 0x2000
        const val MTK_PID_BROM = 0x0003
        const val MTK_PID_PRELOADER = 0x2000
        const val MTK_PID_PRELOADER_2 = 0x2001
        const val MTK_PID_CDC = 0x2004
        const val MTK_PID_DEBUG = 0x2005
        const val MTK_PID_BOOTROM_GENERIC = 0x0001
        const val MTK_PID_DA_HIGH_SPEED = 0x0002
        const val MTK_PID_PRELOADER_ALT = 0x0005

        val MTK_SERVICE_MODES = setOf(
            UsbDeviceMode.BROM,
            UsbDeviceMode.PRELOADER,
            UsbDeviceMode.META,
            UsbDeviceMode.CDC_SERIAL
        )

        val FLASH_SERVICE_MODES = setOf(
            UsbDeviceMode.BROM,
            UsbDeviceMode.PRELOADER,
            UsbDeviceMode.META,
            UsbDeviceMode.CDC_SERIAL,
            UsbDeviceMode.EDL,
            UsbDeviceMode.DIAG,
            UsbDeviceMode.FASTBOOT
        )

        val AUTO_PROMPT_MODES = setOf(
            UsbDeviceMode.BROM,
            UsbDeviceMode.PRELOADER,
            UsbDeviceMode.META,
            UsbDeviceMode.EDL,
            UsbDeviceMode.DIAG,
            UsbDeviceMode.FASTBOOT
        )

        val SUPPORTED_VIDS = setOf(
            0x0E8D, // MediaTek Inc
            0x05C6, // Qualcomm Inc (EDL / Diag)
            0x1004, // LG Electronics MTK
            0x0BB4, // HTC MTK
            0x2A45, // Meizu MTK
            0x1782, // Spreadtrum/Unisoc
            0x1A86, // CH340 Serial
            0x10C4, // CP210x Serial
            0x0403, // FTDI Serial
            0x18D1, // Google / Fastboot
            0x2717, // Xiaomi
            0x04E8, // Samsung
            0x22D9  // Oppo / Realme
        )
    }

    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var ctrlInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var nativeHandle: Long = 0L

    var currentDevice: UsbDevice? = null
        private set

    var currentMode: UsbDeviceMode = UsbDeviceMode.NONE
        private set

    var strictBromOnlyMode: Boolean = false

    @Volatile
    var isBromHandshakeLocked: Boolean = false
        private set

    private val scope = CoroutineScope(Dispatchers.IO)
    private val connectionMutex = Mutex()
    var onDeviceAutoConnectedListener: ((TargetPhoneState.Connected) -> Unit)? = null

    private val _phoneState = MutableStateFlow<TargetPhoneState>(TargetPhoneState.Disconnected)
    val phoneState: StateFlow<TargetPhoneState> = _phoneState.asStateFlow()

    // Track devices already prompted in the current plug session to prevent repeated permission dialogs
    private val promptedDeviceKeys = ConcurrentHashMap.newKeySet<String>()

    private fun deviceKey(device: UsbDevice): String =
        "${device.vendorId}:${device.productId}:${device.deviceName}"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ACTION_USB_PHONE_PERMISSION -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        promptedDeviceKeys.remove(deviceKey(device))
                        scope.launch {
                            connectDevice(device)
                        }
                    } else if (device != null) {
                        val mode = detectDeviceMode(device)
                        if (mode in AUTO_PROMPT_MODES) {
                            _phoneState.value = TargetPhoneState.Error("USB Permission was not granted for ${mode.label}.")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    refreshRealtimePortStatus()
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val mode = detectDeviceMode(device)
                        val lockedMode = lockedActionTargetMode
                        // Action isolation: During active action, ignore foreign/incompatible USB modes
                        if (lockedMode != null && !isModeCompatibleWithLockedAction(mode, lockedMode)) {
                            android.util.Log.w("TargetPhoneUsb", "Action $lockedActionName running: ignored device mode $mode ($lockedMode required)")
                            return
                        }

                        scope.launch {
                            delay(if (mode in MTK_SERVICE_MODES) 25 else 200)
                            if (usbManager.hasPermission(device)) {
                                if (mode in FLASH_SERVICE_MODES || mode == UsbDeviceMode.ADB) {
                                    connectDevice(device)
                                }
                            } else if (mode in AUTO_PROMPT_MODES) {
                                requestDevicePermission(device, force = false)
                            }
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        promptedDeviceKeys.remove(deviceKey(device))
                    } else {
                        promptedDeviceKeys.clear()
                    }
                    disconnect()
                    refreshRealtimePortStatus()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PHONE_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        refreshRealtimePortStatus()
    }

    fun getAttachedDevices(): List<UsbDevice> =
        usbManager.deviceList.values.filter { isMediaTekDevice(it) }

    fun getDevice(): UsbDevice? = currentDevice

    fun detectDeviceMode(device: UsbDevice?): UsbDeviceMode {
        if (device == null) return UsbDeviceMode.NONE
        val vid = device.vendorId
        val pid = device.productId

        if (vid == MTK_VID) {
            if (pid == 0x0003 || pid == 0x0001 || pid == 0x0002 || pid == 0x0005) {
                return UsbDeviceMode.BROM
            }
            if (pid == 0x2000 || pid == 0x2001) {
                return UsbDeviceMode.PRELOADER
            }
            if (pid == 0x2004 || pid == 0x2005 || pid == 0x2006 || pid == 0x2007 || pid == 0x200A || pid == 0x200E || pid == 0x202D || pid == 0x20FF) {
                return UsbDeviceMode.META
            }
        }

        if (vid == QCOM_VID) {
            if (pid == 0x9008) return UsbDeviceMode.EDL
            if (pid == 0x9091 || pid == 0x901D || pid == 0x9025) return UsbDeviceMode.DIAG
        }

        var hasFastboot = false
        var hasAdb = false
        var hasMtp = false
        var hasCdc = false
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            val cls = iface.interfaceClass
            val subCls = iface.interfaceSubclass
            val proto = iface.interfaceProtocol
            if (cls == UsbConstants.USB_CLASS_VENDOR_SPEC && subCls == 0x42 && proto == 0x03) {
                hasFastboot = true
            }
            if (cls == UsbConstants.USB_CLASS_VENDOR_SPEC && subCls == 0x42 && proto == 0x01) {
                hasAdb = true
            }
            if (cls == UsbConstants.USB_CLASS_STILL_IMAGE || (cls == 0xFF && subCls == 0xFF && proto == 0x00)) {
                hasMtp = true
            }
            if (cls == UsbConstants.USB_CLASS_CDC_DATA || cls == UsbConstants.USB_CLASS_COMM) {
                hasCdc = true
            }
        }
        if (hasFastboot || (vid == 0x18D1 && (pid == 0x4EE0 || pid == 0xD00D)) || (vid == 0x2717 && (pid == 0xFF40 || pid == 0xFF48))) {
            return UsbDeviceMode.FASTBOOT
        }
        if (hasAdb) return UsbDeviceMode.ADB
        if (vid == MTK_VID) return UsbDeviceMode.BROM
        if (hasCdc || vid == 0x1A86 || vid == 0x10C4 || vid == 0x0403) return UsbDeviceMode.CDC_SERIAL
        if (hasMtp) return UsbDeviceMode.MTP
        return UsbDeviceMode.UNKNOWN
    }

    fun isBromDevice(device: UsbDevice): Boolean =
        detectDeviceMode(device) == UsbDeviceMode.BROM

    fun isMediaTekDevice(device: UsbDevice): Boolean {
        if (SUPPORTED_VIDS.contains(device.vendorId)) return true
        if (device.deviceClass == UsbConstants.USB_CLASS_COMM || device.deviceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) return true
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                iface.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
            ) {
                return true
            }
        }
        return false
    }

    fun getRawDeviceCount(): Int = usbManager.deviceList.size

    fun getRawDeviceList(): String = usbManager.deviceList.values
        .joinToString(", ") { "VID:0x%04X PID:0x%04X".format(it.vendorId, it.productId) }

    fun requestDevicePermission(device: UsbDevice, force: Boolean = false) {
        if (usbManager.hasPermission(device)) {
            scope.launch { connectDevice(device) }
            return
        }
        val key = deviceKey(device)
        if (!force && !promptedDeviceKeys.add(key)) {
            return
        }
        if (force) {
            promptedDeviceKeys.add(key)
        }

        val mode = detectDeviceMode(device)
        val vidPidStr = String.format("0x%04X:0x%04X", device.vendorId, device.productId)
        _phoneState.value = TargetPhoneState.RequestingPermission(
            deviceName = device.productName ?: "Target Phone",
            mode = mode,
            vidPid = vidPidStr
        )

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            Intent(ACTION_USB_PHONE_PERMISSION).setPackage(context.packageName),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    @JvmOverloads
    suspend fun scanAndConnect(
        promptIfNeeded: Boolean = false,
        forceBromOnly: Boolean = false,
        requiredModes: Set<UsbDeviceMode>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val strictBrom = forceBromOnly || strictBromOnlyMode
        val effectiveModes = when {
            strictBrom -> setOf(UsbDeviceMode.BROM)
            requiredModes != null -> requiredModes
            else -> FLASH_SERVICE_MODES
        }

        if (isConnected()) {
            if (currentMode in effectiveModes) {
                return@withContext true
            }
            disconnect()
        }

        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            if (promptedDeviceKeys.isEmpty()) {
                _phoneState.value = TargetPhoneState.Disconnected
            }
            return@withContext false
        }

        val candidates = deviceList.values.filter { detectDeviceMode(it) in effectiveModes }
        if (candidates.isEmpty()) {
            return@withContext false
        }

        val targetDevice =
            candidates.firstOrNull { detectDeviceMode(it) == UsbDeviceMode.BROM }
                ?: candidates.firstOrNull { detectDeviceMode(it) == UsbDeviceMode.PRELOADER }
                ?: candidates.firstOrNull { detectDeviceMode(it) in MTK_SERVICE_MODES }
                ?: candidates.firstOrNull { detectDeviceMode(it) == UsbDeviceMode.EDL || detectDeviceMode(it) == UsbDeviceMode.DIAG }
                ?: candidates.firstOrNull { detectDeviceMode(it) == UsbDeviceMode.FASTBOOT }
                ?: candidates.firstOrNull()
                ?: return@withContext false

        if (!usbManager.hasPermission(targetDevice)) {
            if (promptIfNeeded || detectDeviceMode(targetDevice) in AUTO_PROMPT_MODES) {
                requestDevicePermission(targetDevice, force = promptIfNeeded)
            }
            return@withContext false
        }
        return@withContext connectDevice(targetDevice)
    }

    suspend fun connectDevice(targetDevice: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            try {
                if (usbConnection != null && nativeHandle != 0L &&
                    currentDevice?.deviceName == targetDevice.deviceName &&
                    currentDevice?.vendorId == targetDevice.vendorId &&
                    currentDevice?.productId == targetDevice.productId
                ) {
                    return@withContext true
                }

                closeInternal()

                val connection = usbManager.openDevice(targetDevice) ?: run {
                    _phoneState.value = TargetPhoneState.Error("Failed to open target USB port (OTG connection refused).")
                    return@withContext false
                }

                var foundCtrlIface: UsbInterface? = null
                var bulkIn: UsbEndpoint? = null
                var bulkOut: UsbEndpoint? = null
                var claimedIface: UsbInterface? = null

                for (i in 0 until targetDevice.interfaceCount) {
                    val iface = targetDevice.getInterface(i)
                    if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM && foundCtrlIface == null) {
                        foundCtrlIface = iface
                    }
                    var tempIn: UsbEndpoint? = null
                    var tempOut: UsbEndpoint? = null
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (ep.direction == UsbConstants.USB_DIR_IN) tempIn = ep
                            else tempOut = ep
                        }
                    }
                    if (tempIn != null && tempOut != null) {
                        claimedIface = iface
                        bulkIn = tempIn
                        bulkOut = tempOut
                        break
                    }
                }

                if (claimedIface == null || bulkIn == null || bulkOut == null) {
                    for (i in 0 until targetDevice.interfaceCount) {
                        val iface = targetDevice.getInterface(i)
                        for (j in 0 until iface.endpointCount) {
                            val ep = iface.getEndpoint(j)
                            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                                if (ep.direction == UsbConstants.USB_DIR_IN && bulkIn == null) {
                                    bulkIn = ep
                                    if (claimedIface == null) claimedIface = iface
                                } else if (ep.direction == UsbConstants.USB_DIR_OUT && bulkOut == null) {
                                    bulkOut = ep
                                    if (claimedIface == null) claimedIface = iface
                                }
                            }
                        }
                    }
                }

                foundCtrlIface?.let { runCatching { connection.claimInterface(it, true) } }
                claimedIface?.let { runCatching { connection.claimInterface(it, true) } }

                val rawFd = connection.fileDescriptor
                val handle = JinUsbNative.nativeOpen(
                    fd = rawFd,
                    ctrlIface = foundCtrlIface?.id ?: -1,
                    dataIface = claimedIface?.id ?: -1,
                    epIn = bulkIn?.address ?: 0,
                    epOut = bulkOut?.address ?: 0
                )

                if (handle == 0L) {
                    runCatching { connection.close() }
                    _phoneState.value = TargetPhoneState.Error("Native libusb open failed on FD:$rawFd")
                    return@withContext false
                }

                val mode = detectDeviceMode(targetDevice)
                if (mode in MTK_SERVICE_MODES) {
                    JinUsbNative.nativeSetupCdcLineCoding(handle, 115200, 8, 0, 0, dtr = true, rts = true)
                }

                usbConnection = connection
                ctrlInterface = foundCtrlIface
                usbInterface = claimedIface
                inEndpoint = bulkIn
                outEndpoint = bulkOut
                nativeHandle = handle
                currentDevice = targetDevice
                currentMode = mode

                // Lock BROM handshake immediately on real BROM attach before the ~4.7s WDT timeout
                isBromHandshakeLocked = if (mode == UsbDeviceMode.BROM) {
                    JinUsbNative.nativeMtkBromHandshake(handle, 10)
                } else {
                    false
                }

                val isBrom = (mode == UsbDeviceMode.BROM || mode == UsbDeviceMode.PRELOADER)
                val vidPidStr = String.format("0x%04X:0x%04X", targetDevice.vendorId, targetDevice.productId)
                val state = TargetPhoneState.Connected(
                    deviceName = targetDevice.productName ?: "MediaTek Device",
                    mode = mode,
                    isBromMode = isBrom,
                    vidPid = "$vidPidStr [${mode.label}]",
                    fileDescriptor = rawFd
                )
                _phoneState.value = state
                runCatching { onDeviceAutoConnectedListener?.invoke(state) }
                return@withContext true
            } catch (e: Exception) {
                _phoneState.value = TargetPhoneState.Error("Target USB Error: ${e.message}")
                return@withContext false
            }
        }
    }

    fun blastBromHandshakeSync(maxAttempts: Int = 60): Boolean {
        val h = nativeHandle
        if (h == 0L) return false
        val ok = JinUsbNative.nativeMtkBromHandshake(h, maxAttempts)
        if (ok) isBromHandshakeLocked = true
        return ok
    }

    fun resetBromHandshakeLock() {
        isBromHandshakeLocked = false
    }

    fun setupCdcLineCoding(baudRate: Int = 115200, dtr: Boolean = true, rts: Boolean = true): Boolean {
        val h = nativeHandle
        if (h == 0L) return false
        return JinUsbNative.nativeSetupCdcLineCoding(h, baudRate, 8, 0, 0, dtr, rts) >= 0
    }

    fun getMaxOutPacketSize(): Int = outEndpoint?.maxPacketSize?.takeIf { it > 0 } ?: 512

    fun getMaxInPacketSize(): Int = inEndpoint?.maxPacketSize?.takeIf { it > 0 } ?: 512

    fun getFileDescriptor(): Int = usbConnection?.fileDescriptor ?: -1

    @JvmOverloads
    fun writeRaw(bytes: ByteArray, timeoutMs: Int = 1000, sendZlp: Boolean = false): Int {
        val h = nativeHandle
        if (h == 0L) return -1
        return JinUsbNative.nativeBulkWrite(h, bytes, 0, bytes.size, timeoutMs, sendZlp)
    }

    @JvmOverloads
    fun readRaw(buffer: ByteArray, timeoutMs: Int = 1000, readFull: Boolean = false): Int {
        val h = nativeHandle
        if (h == 0L) return -1
        return JinUsbNative.nativeBulkRead(h, buffer, 0, buffer.size, timeoutMs, readFull)
    }

    @JvmOverloads
    fun controlTransfer(
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        buffer: ByteArray?,
        length: Int,
        timeoutMs: Int = 1000
    ): Int {
        val h = nativeHandle
        if (h == 0L) return -1
        return JinUsbNative.nativeControlTransfer(h, requestType, request, value, index, buffer, length, timeoutMs)
    }

    fun controlTransferAsyncCancel(
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        buffer: ByteArray?,
        length: Int,
        cancelAfterUs: Int = 500
    ): Int {
        val h = nativeHandle
        if (h == 0L) return -1
        return JinUsbNative.nativeControlTransferAsyncCancel(h, requestType, request, value, index, buffer, length, cancelAfterUs)
    }

    fun clearHalt(inEndpoint: Boolean): Int {
        val h = nativeHandle
        return if (h != 0L) JinUsbNative.nativeClearHalt(h, inEndpoint) else -1
    }

    fun clearEndpointHalt() {
        clearHalt(true)
        clearHalt(false)
    }

    fun flush(timeoutMs: Int = 50) {
        if (nativeHandle == 0L) return
        val buf = ByteArray(512)
        for (i in 0 until 32) {
            if (readRaw(buf, timeoutMs, readFull = false) <= 0) break
        }
    }

    fun resetDevice(): Int {
        val h = nativeHandle
        return if (h != 0L) JinUsbNative.nativeResetDevice(h) else -1
    }

    fun sendWatchdogResetControl(): Boolean {
        val wdtData = byteArrayOf(0xD4.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val rCdc = controlTransfer(0x21, 0x20, 0, 0, wdtData, wdtData.size, 500)
        val rVendor = controlTransfer(0x40, 0x01, 0x0000, 0x0000, null, 0, 500)
        return rCdc >= 0 || rVendor >= 0
    }

    private fun closeInternal() {
        isBromHandshakeLocked = false
        currentMode = UsbDeviceMode.NONE
        if (nativeHandle != 0L) {
            runCatching { JinUsbNative.nativeClose(nativeHandle) }
            nativeHandle = 0L
        }
        runCatching { usbInterface?.let { usbConnection?.releaseInterface(it) } }
        runCatching { ctrlInterface?.let { usbConnection?.releaseInterface(it) } }
        runCatching { usbConnection?.close() }
        usbConnection = null
        usbInterface = null
        ctrlInterface = null
        inEndpoint = null
        outEndpoint = null
        currentDevice = null
    }

    fun disconnect() {
        closeInternal()
        _phoneState.value = TargetPhoneState.Disconnected
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(usbReceiver) }
    }

    fun isConnected(): Boolean = (usbConnection != null && nativeHandle != 0L)

    fun isBromConnected(): Boolean = isConnected() && (currentMode == UsbDeviceMode.BROM)

    fun isBromOrPreloaderConnected(): Boolean = isConnected() && (currentMode in MTK_SERVICE_MODES)
}
