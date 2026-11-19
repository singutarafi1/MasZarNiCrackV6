package com.example.protocol

import android.util.Log
import com.example.model.LogLevel
import com.example.model.TerminalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek XFlash Protocol Engine (DA Stage 1 & Stage 2 Protocol)
 * Direct, faithful Kotlin port of Python mtkclient:
 * - mtkclient/Library/DA/xflash/xflash_lib.py
 * - mtkclient/Library/DA/xflash/xflash_param.py
 * - mtkclient/Library/DA/xflash/extension/xflash.py
 * 100% Real Hardware Implementation - No Mock/Simulation Data.
 */
class MtkXFlashEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: ((TerminalLog) -> Unit)? = null
) {
    companion object {
        private const val TAG = "MtkXFlashEngine"

        const val MAGIC: Int = 0xFEEEEEEF.toInt()
        const val DEFAULT_PACKET_SIZE = 0x100000 // 1MB fallback matching xflash_lib.py
        const val MAX_PARAM_CHUNK = 0x200       // 512 bytes parameter sub-packet

        const val STATUS_OK = 0x00L
        const val STATUS_FORMAT_RUNNING = 0x40040004L
        const val STATUS_FORMAT_DONE = 0x40040005L
    }

    var readPacketLength: Int = DEFAULT_PACKET_SIZE
        private set
    var writePacketLength: Int = 0x20000
        private set
    var maxPacketSize: Int = 0x20000
        private set

    var sramInfo: RamInfo? = null
        private set
    var dramInfo: RamInfo? = null
        private set
    @get:JvmName("getCachedEmmcInfo")
    var emmcInfo: EmmcInfo? = null
        private set
    @get:JvmName("getCachedUfsInfo")
    var ufsInfo: UfsInfo? = null
        private set
    var detectedFlashType: String = "emmc"
        private set
    var daExtLoaded: Boolean = false

    private fun log(msg: String, level: LogLevel = LogLevel.INFO) {
        logCallback?.invoke(TerminalLog("", msg, level))
    }

    private fun intLE(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun longLE(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()

    fun calculateChecksum16(data: ByteArray, length: Int = data.size): Int {
        var chksum = 0
        for (i in 0 until length) {
            chksum = (chksum + (data[i].toInt() and 0xFF)) and 0xFFFF
        }
        return chksum
    }

    // =========================================================================
    // 1. Low-Level XFlash Frame Transmission (xsend / xread / status / ack)
    // =========================================================================

    fun xsend(data: ByteArray, dataType: Int = MtkXFlashConstants.DataType.DT_PROTOCOL_FLOW): Boolean {
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(MAGIC)
        header.putInt(dataType)
        header.putInt(data.size)

        if (usb.writeRaw(header.array(), 5000) != 12) return false
        if (data.isNotEmpty()) {
            return usb.writeRaw(data, 5000) == data.size
        }
        return true
    }

    fun xsendInt(value: Int, dataType: Int = MtkXFlashConstants.DataType.DT_PROTOCOL_FLOW): Boolean =
        xsend(intLE(value), dataType)

    fun xread(timeoutMs: Int = 5000): Pair<Int, ByteArray>? {
        val headerBuf = ByteArray(12)
        val hdrRead = usb.readRaw(headerBuf, timeoutMs, readFull = true)
        if (hdrRead != 12) return null

        val buf = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        if (magic != MAGIC) {
            Log.w(TAG, "XFlash Magic mismatch: Expected 0x%08X, got 0x%08X".format(MAGIC, magic))
            return null
        }

        val dataType = buf.int
        val length = buf.int
        if (length < 0 || length > 0x1000000) {
            Log.e(TAG, "Invalid payload length in XFlash frame: $length bytes")
            return null
        }
        if (length == 0) return Pair(dataType, ByteArray(0))

        val payload = ByteArray(length)
        val pRead = usb.readRaw(payload, timeoutMs.coerceAtLeast(8000), readFull = true)
        if (pRead != length) {
            Log.w(TAG, "Incomplete XFlash payload: Received $pRead of $length bytes")
            return null
        }
        return Pair(dataType, payload)
    }

    fun readStatus(timeoutMs: Int = 5000): XFlashStatus {
        val resp = xread(timeoutMs) ?: return XFlashStatus(-1L, "Timeout waiting for XFlash status")
        val data = resp.second
        val status = when {
            data.size == 2 -> (ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF).toLong()
            data.size == 4 -> {
                val v = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                if (v == 0xFEEEEEEFL) 0L else v
            }
            data.size > 4 -> ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            else -> 0L
        }
        return XFlashStatus(status, if (status == STATUS_OK) "OK" else "0x%08X".format(status))
    }

    fun ack(rstatus: Boolean = true): Boolean {
        val hdr = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(MAGIC)
            .putInt(MtkXFlashConstants.DataType.DT_PROTOCOL_FLOW)
            .putInt(4)
            .array()
        if (usb.writeRaw(hdr, 5000) != 12) return false
        if (usb.writeRaw(ByteArray(4), 5000) != 4) return false
        if (rstatus) return readStatus(5000).isOk
        return true
    }

    // =========================================================================
    // 2. Parameter Transmission & Device Control (send_param / send_devctrl)
    // =========================================================================

    private fun sendRawPacket(data: ByteArray): Boolean {
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(MAGIC)
        header.putInt(MtkXFlashConstants.DataType.DT_PROTOCOL_FLOW)
        header.putInt(data.size)

        if (usb.writeRaw(header.array(), 5000) != 12) return false

        var offset = 0
        while (offset < data.size) {
            val dsize = minOf(MAX_PARAM_CHUNK, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + dsize)
            if (usb.writeRaw(chunk, 8000) != dsize) return false
            offset += dsize
        }
        return true
    }

    fun sendParam(data: ByteArray, statusTimeoutMs: Int = 8000): Boolean = sendParam(listOf(data), statusTimeoutMs)

    fun sendParam(params: List<ByteArray>, statusTimeoutMs: Int = 8000): Boolean {
        for (p in params) {
            if (!sendRawPacket(p)) return false
        }
        val st = readStatus(statusTimeoutMs)
        if (!st.isOk && st.status != 0xC0040050L) {
            log("[-] sendParam error: ${st.msg}", LogLevel.ERROR)
        }
        return st.isOk
    }

    /**
     * Exact port of xflash_lib.py -> send_devctrl().
     * When param == null: if readTrailingStatus == true, also reads & verifies the trailing status() frame
     * that XFlash DA sends after nearly all GET_* queries (prevents USB FIFO desync!).
     * When param != null: calls sendParam(param) and returns empty ByteArray on success (never calls xread!).
     */
    fun sendDevCtrl(
        ctrlCode: Int,
        param: ByteArray? = null,
        readTrailingStatus: Boolean = true
    ): ByteArray? {
        if (!xsendInt(MtkXFlashConstants.Cmd.DEVICE_CTRL)) return null
        if (!readStatus(5000).isOk) return null

        if (!xsendInt(ctrlCode)) return null
        val st2 = readStatus(5000)
        if (!st2.isOk) {
            if (st2.status != 0xC0010004L) {
                log("[-] DevCtrl 0x%06X rejected: ${st2.msg}".format(ctrlCode), LogLevel.WARNING)
            }
            return null
        }

        return if (param == null) {
            val data = xread(5000)?.second ?: return null
            if (readTrailingStatus && data.isNotEmpty()) {
                val trailing = readStatus(5000)
                if (!trailing.isOk) {
                    log("[-] DevCtrl 0x%06X trailing status error: ${trailing.msg}".format(ctrlCode), LogLevel.WARNING)
                    return null
                }
            }
            data
        } else {
            if (sendParam(param)) ByteArray(0) else null
        }
    }

    // =========================================================================
    // 3. Stage 1 Initialization & Stage 2 Boot Sequence (upload_da1 / upload_da)
    // =========================================================================

    fun sync(): Boolean = xsendInt(MtkXFlashConstants.Cmd.SYNC_SIGNAL)

    fun setupEnv(
        daLogLevel: Int = 0,
        logChannel: Int = 1,
        systemOs: Int = MtkXFlashConstants.FtSystemOSE.OS_LINUX,
        ufsProvision: Int = 0
    ): Boolean {
        if (!xsendInt(MtkXFlashConstants.Cmd.SETUP_ENVIRONMENT)) return false
        val param = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(daLogLevel)
            .putInt(logChannel)
            .putInt(systemOs)
            .putInt(ufsProvision)
            .putInt(0)
            .array()
        return sendParam(param)
    }

    fun setupHwInit(): Boolean {
        if (!xsendInt(MtkXFlashConstants.Cmd.SETUP_HW_INIT_PARAMS)) return false
        return sendParam(intLE(0))
    }

    /**
     * Complete Stage 1 handshake right after receiving 0xC0 from DA Stage 1 (xflash_lib.py lines 1469-1477)
     */
    fun initStage1Session(): Boolean {
        if (!sync()) {
            log("[-] XFlash Stage 1 SYNC_SIGNAL send failed.", LogLevel.ERROR)
            return false
        }
        if (!setupEnv()) {
            log("[-] XFlash SETUP_ENVIRONMENT failed.", LogLevel.ERROR)
            return false
        }
        if (!setupHwInit()) {
            log("[-] XFlash SETUP_HW_INIT_PARAMS failed.", LogLevel.ERROR)
            return false
        }
        val resp = xread(5000)?.second
        if (resp == null || resp.size < 4) {
            log("[-] Failed receiving Stage 1 SYNC response.", LogLevel.ERROR)
            return false
        }
        val sig = ByteBuffer.wrap(resp).order(ByteOrder.LITTLE_ENDIAN).int
        if (sig != MtkXFlashConstants.Cmd.SYNC_SIGNAL) {
            log("[-] Unexpected Stage 1 SYNC response: 0x%08X".format(sig), LogLevel.ERROR)
            return false
        }
        log("[+] XFlash Stage 1 SYNC verified (0x434E5953).", LogLevel.SUCCESS)
        return true
    }

    fun getExpireDate(): ByteArray? = sendDevCtrl(MtkXFlashConstants.Cmd.GET_EXPIRE_DATE)

    fun setResetKey(resetKey: Int = 0x68): Boolean =
        sendDevCtrl(MtkXFlashConstants.Cmd.SET_RESET_KEY, intLE(resetKey)) != null

    fun setChecksumLevel(checksumLevel: Int = 0): Boolean =
        sendDevCtrl(MtkXFlashConstants.Cmd.SET_CHECKSUM_LEVEL, intLE(checksumLevel)) != null

    fun setBatteryOpt(option: Int = 2): Boolean =
        sendDevCtrl(MtkXFlashConstants.Cmd.SET_BATTERY_OPT, intLE(option)) != null

    fun getConnectionAgent(): String? {
        val res = sendDevCtrl(MtkXFlashConstants.Cmd.GET_CONNECTION_AGENT) ?: return null
        return res.toString(Charsets.US_ASCII).trim('\u0000').ifEmpty { null }
    }

    fun sendEmi(emi: ByteArray): Boolean {
        if (!xsendInt(MtkXFlashConstants.Cmd.INIT_EXT_RAM)) return false
        val st = readStatus(5000)
        if (!st.isOk) {
            log("[-] INIT_EXT_RAM rejected: ${st.msg}", LogLevel.ERROR)
            return false
        }
        Thread.sleep(10)
        if (!xsendInt(emi.size)) return false
        val ok = sendParam(emi, statusTimeoutMs = 10000)
        if (ok) {
            log("[+] DRAM / EMI setup passed.", LogLevel.SUCCESS)
        } else {
            log("[-] DRAM / EMI setup failed.", LogLevel.ERROR)
        }
        return ok
    }

    fun sendData(data: ByteArray, onProgress: ((Long, Long) -> Unit)? = null): Boolean {
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(MAGIC)
            .putInt(MtkXFlashConstants.DataType.DT_PROTOCOL_FLOW)
            .putInt(data.size)
            .array()
        if (usb.writeRaw(header, 5000) != 12) return false

        val chunkLimit = usb.getMaxOutPacketSize().coerceAtLeast(512)
        var pos = 0
        val total = data.size
        while (pos < total) {
            val sz = minOf(chunkLimit, total - pos)
            val chunk = data.copyOfRange(pos, pos + sz)
            if (usb.writeRaw(chunk, 8000) != sz) return false
            pos += sz
            onProgress?.invoke(pos.toLong(), total.toLong())
        }
        val st = readStatus(10000)
        if (!st.isOk) {
            log("[-] Error on send_data: ${st.msg}", LogLevel.ERROR)
            return false
        }
        return true
    }

    /**
     * Exact port of xflash_lib.py -> boot_to(addr, da, timeout=0.5)
     */
    fun bootTo(
        address: Long,
        data: ByteArray,
        timeoutMs: Long = 500L,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        if (!xsendInt(MtkXFlashConstants.Cmd.BOOT_TO)) return false
        if (!readStatus(5000).isOk) return false

        val param = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(address)
            .putLong(data.size.toLong())
            .array()

        val hdr = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(MAGIC)
            .putInt(MtkXFlashConstants.DataType.DT_PROTOCOL_FLOW)
            .putInt(param.size)
            .array()

        if (usb.writeRaw(hdr, 5000) != 12) return false
        if (usb.writeRaw(param, 5000) != param.size) return false

        if (!sendData(data, onProgress)) {
            log("[-] BOOT_TO send_data failed for 0x%08X".format(address), LogLevel.ERROR)
            return false
        }

        if (timeoutMs > 0) Thread.sleep(timeoutMs)

        val jumpStatus = readStatus(8000)
        if (jumpStatus.status == 0L || jumpStatus.status == 0x434E5953L) {
            log("[+] BOOT_TO succeeded @ 0x%08X.".format(address), LogLevel.SUCCESS)
            return true
        }
        log("[-] BOOT_TO failed @ 0x%08X (Status: ${jumpStatus.msg})".format(address), LogLevel.ERROR)
        return false
    }

    fun connect(): Boolean {
        log("[XFLASH] Negotiating XFlash Session...", LogLevel.INFO)
        getPacketLength()
        return true
    }

    // =========================================================================
    // 4. Command Setup Headers (WRITE_DATA, READ_DATA)
    // =========================================================================

    private fun cmdWriteData(storage: Int, partType: Int, address: Long, length: Long): Boolean {
        if (!xsendInt(MtkXFlashConstants.Cmd.WRITE_DATA)) return false
        if (!readStatus(5000).isOk) return false

        val param = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        param.putInt(storage)
        param.putInt(partType)
        param.putLong(address)
        param.putLong(length)
        param.put(NandExtension().toBytes())

        return sendParam(param.array())
    }

    private fun cmdReadData(storage: Int, partType: Int, address: Long, length: Long): Boolean {
        if (!xsendInt(MtkXFlashConstants.Cmd.READ_DATA)) return false
        if (!readStatus(5000).isOk) return false

        val param = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        param.putInt(storage)
        param.putInt(partType)
        param.putLong(address)
        param.putLong(length)
        param.put(NandExtension().toBytes())

        if (!sendParam(param.array())) return false
        // xflash_lib.py lines 1186-1188: DA sends second readiness status after send_param
        val readyStatus = readStatus(5000)
        if (!readyStatus.isOk) {
            log("[-] READ_DATA readiness rejected: ${readyStatus.msg}", LogLevel.ERROR)
            return false
        }
        return true
    }

    // =========================================================================
    // 5. High-Level Flash Operations (Write, Read, Stream Read, Format, Shutdown)
    // =========================================================================

    suspend fun writeFlash(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        data: ByteArray,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        getPacketLength()
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val paddedLength = length + pad

        if (!cmdWriteData(storage, partType, address, paddedLength)) {
            log("[-] cmdWriteData setup rejected by DA Stage 2.", LogLevel.ERROR)
            return@withContext false
        }

        var offset = 0
        val total = data.size
        val chunkSize = writePacketLength.coerceIn(0x1000, 0x200000)

        while (offset < total) {
            val curLen = minOf(chunkSize, total - offset)
            val chunk = ByteArray(curLen + if (offset + curLen >= total) pad else 0)
            System.arraycopy(data, offset, chunk, 0, curLen)
            val rem = chunk.size % 512
            val alignedChunk = if (rem != 0) chunk + ByteArray(512 - rem) else chunk

            val checksum = calculateChecksum16(alignedChunk)
            val p0 = intLE(0)
            val p1 = intLE(checksum)

            if (!sendParam(listOf(p0, p1, alignedChunk))) {
                log("[-] Write chunk failed at offset 0x%X".format(offset), LogLevel.ERROR)
                return@withContext false
            }
            offset += curLen
            onProgress?.invoke(offset.toLong(), total.toLong())
        }

        if (!readStatus(8000).isOk) return@withContext false
        sendDevCtrl(MtkXFlashConstants.Cmd.CC_OPTIONAL_DOWNLOAD_ACT, null, readTrailingStatus = false)

        log("[+] Write complete for address 0x%08X".format(address), LogLevel.SUCCESS)
        return@withContext true
    }

    suspend fun readFlash(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        onProgress: ((Long, Long) -> Unit)? = null
    ): ByteArray? = withContext(Dispatchers.IO) {
        getPacketLength()
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val paddedLength = length + pad

        if (!cmdReadData(storage, partType, address, paddedLength)) return@withContext null

        val result = ByteArray(length.toInt())
        var readBytes = 0L

        while (readBytes < length) {
            val resp = xread(8000) ?: return@withContext null
            val payload = resp.second

            if (payload.size > 4) {
                val copyLen = minOf(payload.size.toLong(), length - readBytes).toInt()
                System.arraycopy(payload, 0, result, readBytes.toInt(), copyLen)
                readBytes += copyLen
                ack(false)
                onProgress?.invoke(readBytes, length)
            } else if (payload.size == 4) {
                val status = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).int
                if (status != 0) return@withContext null
            }
        }
        // Consume final acknowledge frame (xflash_lib.py lines 1258-1264)
        readStatus(5000)
        return@withContext result
    }

    suspend fun readFlashToStream(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long,
        out: OutputStream,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        getPacketLength()
        val pad = if (length % 512L != 0L) (512L - (length % 512L)).toInt() else 0
        val paddedLength = length + pad

        if (!cmdReadData(storage, partType, address, paddedLength)) return@withContext false

        var readBytes = 0L
        while (readBytes < length) {
            val resp = xread(8000) ?: return@withContext false
            val payload = resp.second

            if (payload.size > 4) {
                val copyLen = minOf(payload.size.toLong(), length - readBytes).toInt()
                out.write(payload, 0, copyLen)
                readBytes += copyLen
                ack(false)
                onProgress?.invoke(readBytes, length)
            } else if (payload.size == 4) {
                val status = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).int
                if (status != 0) return@withContext false
            }
        }
        // Consume final acknowledge frame (xflash_lib.py lines 1258-1264)
        readStatus(5000)
        out.flush()
        return@withContext true
    }

    suspend fun formatFlash(
        storage: Int,
        partType: Int,
        address: Long,
        length: Long
    ): Boolean = withContext(Dispatchers.IO) {
        log("[XFLASH] Sending FORMAT command (Addr: 0x%X, Len: 0x%X)...".format(address, length), LogLevel.WARNING)

        if (!xsendInt(MtkXFlashConstants.Cmd.FORMAT)) return@withContext false
        if (!readStatus(5000).isOk) return@withContext false

        val param = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        param.putInt(storage)
        param.putInt(partType)
        param.putLong(address)
        param.putLong(length)
        param.put(NandExtension().toBytes())

        if (!sendParam(param.array())) return@withContext false

        var status = readStatus(10000)
        while (status.status == STATUS_FORMAT_RUNNING) {
            val sleepMs = readStatus(5000).status.coerceIn(10L, 2000L)
            delay(sleepMs)
            ack(false)
            status = readStatus(10000)
        }

        val success = status.status == STATUS_FORMAT_DONE || status.isOk
        log(if (success) "[+] Format operation completed successfully." else "[-] Format failed: ${status.msg}", if (success) LogLevel.SUCCESS else LogLevel.ERROR)
        return@withContext success
    }

    // =========================================================================
    // 6. Hardware Information Queries & Extensions
    // =========================================================================

    fun getPacketLength(): Int? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_PACKET_LENGTH) ?: return null
        if (data.size >= 8) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val w = buf.int
            val r = buf.int
            if (w in 0x1000..0x400000) writePacketLength = w
            if (r in 0x1000..0x400000) readPacketLength = r
            maxPacketSize = readPacketLength
            return maxPacketSize
        }
        return null
    }

    fun getRamInfo(): Pair<RamInfo, RamInfo>? {
        val resp = sendDevCtrl(MtkXFlashConstants.Cmd.GET_RAM_INFO) ?: return null
        val buf = ByteBuffer.wrap(resp).order(ByteOrder.LITTLE_ENDIAN)
        return when (resp.size) {
            24 -> {
                val sram = RamInfo(buf.int, buf.int.toLong() and 0xFFFFFFFFL, buf.int.toLong() and 0xFFFFFFFFL)
                val dram = RamInfo(buf.int, buf.int.toLong() and 0xFFFFFFFFL, buf.int.toLong() and 0xFFFFFFFFL)
                sramInfo = sram
                dramInfo = dram
                sram to dram
            }
            48 -> {
                val sram = RamInfo(buf.long.toInt(), buf.long, buf.long)
                val dram = RamInfo(buf.long.toInt(), buf.long, buf.long)
                sramInfo = sram
                dramInfo = dram
                sram to dram
            }
            else -> null
        }
    }

    fun getChipId(): ChipIdInfo? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_CHIP_ID) ?: return null
        if (data.size >= 8) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val hw = buf.short.toInt() and 0xFFFF
            val hwSub = buf.short.toInt() and 0xFFFF
            val hwVer = buf.short.toInt() and 0xFFFF
            val swVer = buf.short.toInt() and 0xFFFF
            return ChipIdInfo(hw, hwSub, hwVer, swVer, data.joinToString("") { "%02X".format(it) })
        }
        return null
    }

    fun getDaVersion(): String? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_DA_VERSION) ?: return null
        return data.toString(Charsets.UTF_8).trim('\u0000')
    }

    fun getRandomId(): ByteArray? = sendDevCtrl(MtkXFlashConstants.Cmd.GET_RANDOM_ID)

    fun getUsbSpeed(): String? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_USB_SPEED) ?: return null
        return data.toString(Charsets.US_ASCII).trim('\u0000')
    }

    fun getSlaStatus(): Int? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.SLA_ENABLED_STATUS) ?: return null
        if (data.isEmpty()) return 0
        var v = 0
        for (i in data.indices.reversed()) {
            v = (v shl 8) or (data[i].toInt() and 0xFF)
        }
        return v
    }

    fun setRemoteSecPolicy(slaSignature: ByteArray): Boolean =
        sendDevCtrl(MtkXFlashConstants.Cmd.SET_REMOTE_SEC_POLICY, slaSignature) != null

    fun getDevFwInfo(): ByteArray? =
        sendDevCtrl(MtkXFlashConstants.Cmd.GET_DEV_FW_INFO)

    /**
     * Port of xflash_lib.py -> handle_sla()
     * Attempts embedded DA SLA key matching, Redmi 14C (_lake/_tides/_moon), Motorola/Lamu signatures,
     * dynamic OAEP-SHA256 challenge signing, and 256-byte zeroed dummy auth fallback.
     */
    fun handleSla(da2: ByteArray, loaderName: String = ""): Boolean {
        val rsakey = MtkSlaKeyDatabase.findMatchingKey(da2, preferDaKeys = true)
        val lowerLoader = loaderName.lowercase()

        if (lowerLoader.contains("_lake") || lowerLoader.contains("_tides") || lowerLoader.contains("_moon")) {
            log("[XFLASH SLA] Trying Redmi 14C (_lake/_tides/_moon) policy...", LogLevel.INFO)
            val fwInfo = getDevFwInfo()
            if (fwInfo != null && fwInfo.isNotEmpty()) {
                val slaSig = MtkDaPatcher.hexToBytes(
                    "62737010D445F66526F0F52BACBCF1E8FE6522CC50617E7B20098E6243DE2E5D" +
                        "7BB71D3607BD8DBADA63521B9EA99EDEA069DCAD9F04D622AE62AC594010D62B" +
                        "EE7AF4B325115C6F0457238BE8D4CB89A7CD1EAF56ACB0C0A16EE016A6B9B030" +
                        "C5794C3B761999E9B684B7B3760B914571DE060FFB07182056F889DD047E3689" +
                        "FB14EFDEBC3CEACE9864E074534DFC5DBF23BCBAD571258CF48C61D7DCAF6ECC" +
                        "8BF908F7F6E0B841A8A7E11D2F64CA40CED98013A9FB381215E7B0051ACAB2C3" +
                        "ACDDD3D08F34FA38AEE8FAD02C9B9EB402750124727EA37B532100C329AF123F" +
                        "A702495740FD1FDBD9EC6EF32D8A76C1767F97986E83E9CE3EFB8E29D5803D84"
                )
                if (setRemoteSecPolicy(slaSig)) {
                    log("[+] XFlash SLA signature accepted (Redmi 14C).", LogLevel.SUCCESS)
                    return true
                }
            }
        }

        if (lowerLoader.contains("motorola") || lowerLoader.contains("lamu")) {
            log("[XFLASH SLA] Trying Motorola/Lamu policy...", LogLevel.INFO)
            val fwInfo = getDevFwInfo()
            if (fwInfo != null && fwInfo.isNotEmpty()) {
                val slaSig = MtkDaPatcher.hexToBytes(
                    "65E9F25C9A26488DD7948FFA3511B883963301821AD162376FAAF40FA207E925" +
                        "36F85529AD5A8C54BDAFB650C95B603E5643047EEEC1F83D051E02218B585CED" +
                        "C2181CBFFDEB8F6E8074CBAB17E39FD3A6F8F5F708F1BBA37816200BBC768F36" +
                        "C80A6A99883FCADE93D577B73C9CBB401C0E62BD5D1654EED9949FF8AB03635B" +
                        "682217EDC59B22517ED6EF45AD2B1CAFC9B9C18C46C138A58EEEFE8301D9CDA6" +
                        "59F7B35D43BDE6A64D6095FBD07FA762ADFC02082D3F61CD18A37B6B2D0321C5" +
                        "DE36F8148A1C46F6E651D8E695CAD841A05CB261AF78C9480EA5477211B3D6FD" +
                        "2DC9FC4B367DC21882485092C76C6DF658591C08AA60AF059004B2D11E4E4E0E"
                )
                if (setRemoteSecPolicy(slaSig)) {
                    log("[+] XFlash SLA signature accepted (Motorola static).", LogLevel.SUCCESS)
                    return true
                }
                val fixedChallenge = ByteArray(16) { 0x42 }
                for (motoKey in MtkSlaKeyDatabase.DA_SLA_KEYS.filter { it.vendor == "Motorola" }) {
                    val sig1 = runCatching { MtkSlaKeyDatabase.generateDaSlaSignature(fixedChallenge, motoKey) }.getOrNull()
                    if (sig1 != null && setRemoteSecPolicy(sig1)) {
                        log("[+] XFlash SLA signature accepted (Motorola 0x42 challenge).", LogLevel.SUCCESS)
                        return true
                    }
                    if (fwInfo.size >= 20) {
                        val chal = fwInfo.copyOfRange(4, 20)
                        val sig2 = runCatching { MtkSlaKeyDatabase.generateDaSlaSignature(chal, motoKey) }.getOrNull()
                        if (sig2 != null && setRemoteSecPolicy(sig2)) {
                            log("[+] XFlash SLA signature accepted (Motorola FW challenge).", LogLevel.SUCCESS)
                            return true
                        }
                    }
                }
            }
        }

        if (rsakey != null) {
            val fwInfo = getDevFwInfo()
            if (fwInfo != null && fwInfo.size >= 20) {
                val challenge = fwInfo.copyOfRange(4, 20)
                val slaSig = runCatching { MtkSlaKeyDatabase.generateDaSlaSignature(challenge, rsakey) }.getOrNull()
                if (slaSig != null && setRemoteSecPolicy(slaSig)) {
                    log("[+] XFlash DA SLA accepted using key '${rsakey.vendor}/${rsakey.name}'.", LogLevel.SUCCESS)
                    return true
                }
            }
        }

        log("[XFLASH SLA] Trying dummy 256-byte auth fallback...", LogLevel.INFO)
        val dummySig = ByteArray(0x100)
        if (setRemoteSecPolicy(dummySig)) {
            log("[+] XFlash DA SLA accepted (dummy auth).", LogLevel.SUCCESS)
            return true
        }
        return false
    }

    fun getEmmcInfo(): EmmcInfo? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_EMMC_INFO) ?: return null
        if (data.size >= 88) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val type = buf.int
            val blockSize = buf.int.toLong() and 0xFFFFFFFFL
            val boot1 = buf.long
            val boot2 = buf.long
            val rpmb = buf.long
            val gp1 = buf.long
            val gp2 = buf.long
            val gp3 = buf.long
            val gp4 = buf.long
            val user = buf.long
            val cidBytes = ByteArray(16)
            buf.get(cidBytes)
            val info = EmmcInfo(
                type, blockSize, boot1, boot2, rpmb, gp1, gp2, gp3, gp4, user,
                cidBytes.joinToString("") { "%02X".format(it) }, cidBytes
            )
            if (type != 0) {
                emmcInfo = info
                detectedFlashType = "emmc"
            }
            return info
        }
        return null
    }

    fun getUfsInfo(): UfsInfo? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_UFS_INFO) ?: return null
        if (data.size >= 32) {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val type = buf.int
            val blockSize = buf.int.toLong() and 0xFFFFFFFFL
            val lu2 = buf.long
            val lu1 = buf.long
            val lu0 = buf.long
            val cidBytes = if (data.size >= 48) ByteArray(16).also { buf.get(it) } else ByteArray(0)
            val info = UfsInfo(
                type, blockSize, lu0, lu1, lu2, 0L,
                cidBytes.joinToString("") { "%02X".format(it) }, cidBytes
            )
            if (type != 0) {
                ufsInfo = info
                detectedFlashType = "ufs"
            }
            return info
        }
        return null
    }

    fun reinitStage2(): Boolean {
        getRamInfo()
        val emmc = getEmmcInfo()
        val ufs = if (emmc == null || emmc.type == 0) getUfsInfo() else null
        getChipId()?.let {
            log("[DA2] HW=0x%04X Ver=0x%04X SW=0x%04X".format(it.hwCode, it.hwVersion, it.swVersion), LogLevel.INFO)
        }
        getDaVersion()?.let {
            if (it.isNotEmpty()) log("[DA2] DA-VERSION: $it", LogLevel.INFO)
        }
        getRandomId()
        getPacketLength()
        getUsbSpeed()?.let {
            if (it.isNotEmpty()) log("[DA2] USB Speed: $it", LogLevel.INFO)
        }
        if (emmc != null && emmc.type != 0) {
            log("[DA2] Storage: eMMC (${emmc.userSize / (1024L * 1024L)} MB, CID=${emmc.cid})", LogLevel.SUCCESS)
        } else if (ufs != null && ufs.type != 0) {
            log("[DA2] Storage: UFS (LU0=${ufs.lu0Size / (1024L * 1024L)} MB)", LogLevel.SUCCESS)
        }
        return true
    }

    fun getDaStorLifeCheck(): Int? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.DA_STOR_LIFE_CYCLE_CHECK, null, readTrailingStatus = false) ?: return null
        if (data.size < 4) return null
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
    }

    fun getBatteryVoltage(): Int? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_BATTERY_VOLTAGE) ?: return null
        if (data.size < 4) return null
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
    }

    fun getDramType(): Int? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_DRAM_TYPE) ?: return null
        if (data.size < 4) return null
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
    }

    fun getPartitionTableCategory(): String? {
        val data = sendDevCtrl(MtkXFlashConstants.Cmd.GET_PARTITION_TBL_CATA, null, readTrailingStatus = false) ?: return null
        if (data.size < 4) return null
        val value = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
        return when (value) {
            0x64 -> "GPT"
            0x65 -> "PMT"
            else -> "0x%X".format(value)
        }
    }

    private fun sendXCmd(cmd: Int): Boolean {
        if (!xsendInt(MtkXFlashConstants.Cmd.DEVICE_CTRL)) return false
        if (!readStatus(5000).isOk) return false
        if (!xsendInt(cmd)) return false
        return readStatus(5000).isOk
    }

    fun customAck(): Boolean {
        val res = sendDevCtrl(MtkXFlashConstants.XCmd.CUSTOM_ACK, null, readTrailingStatus = true) ?: return false
        if (res.size < 4) return false
        val val32 = ByteBuffer.wrap(res).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val ok = val32 == 0xA1A2A3A4L
        if (ok) daExtLoaded = true
        return ok
    }

    fun customSetStorage(isUfs: Boolean): Boolean {
        if (!sendXCmd(MtkXFlashConstants.XCmd.CUSTOM_SET_STORAGE)) return false
        if (!xsendInt(if (isUfs) 1 else 0)) return false
        return readStatus(5000).isOk
    }

    fun customRead(addr: Long, length: Int): ByteArray? {
        if (length <= 0) return ByteArray(0)
        val out = ByteArray(length)
        var pos = 0
        while (pos < length) {
            if (!sendXCmd(MtkXFlashConstants.XCmd.CUSTOM_READMEM)) return null
            if (!xsend(longLE(addr + pos))) return null
            val sz = minOf(length - pos, 0x10000)
            if (!xsendInt(sz)) return null
            val tmp = xread(8000)?.second ?: return null
            if (!readStatus(5000).isOk) return null
            val copyLen = minOf(tmp.size, length - pos)
            if (copyLen <= 0) break
            System.arraycopy(tmp, 0, out, pos, copyLen)
            pos += copyLen
        }
        return out.copyOf(pos)
    }

    fun customWrite(addr: Long, data: ByteArray): Boolean {
        if (!sendXCmd(MtkXFlashConstants.XCmd.CUSTOM_WRITEMEM)) return false
        if (!xsend(longLE(addr))) return false
        if (!xsendInt(data.size)) return false
        if (!xsend(data)) return false
        return readStatus(5000).isOk
    }

    fun customReadRegister(addr: Long, dwords: Int = 1): IntArray? {
        if (dwords <= 0) return IntArray(0)
        if (!sendXCmd(MtkXFlashConstants.XCmd.CUSTOM_READREGISTER)) return null
        if (!xsendInt((addr and 0xFFFFFFFFL).toInt())) return null
        if (!xsendInt(dwords)) return null
        val data = xread(5000)?.second ?: return null
        if (!readStatus(5000).isOk) return null
        val count = data.size / 4
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(count) { buf.int }
    }

    fun customWriteRegister(addr: Long, value: Int): Boolean {
        if (!sendXCmd(MtkXFlashConstants.XCmd.CUSTOM_WRITEREGISTER)) return false
        if (!xsendInt((addr and 0xFFFFFFFFL).toInt())) return false
        if (!xsendInt(value)) return false
        return readStatus(5000).isOk
    }

    fun customRpmbSetKey(rpmbKey: ByteArray): Boolean {
        if (!sendXCmd(MtkXFlashConstants.XCmd.CUSTOM_RPMB_SET_KEY)) return false
        if (!xsend(rpmbKey)) return false
        val readKey = xread(5000)?.second ?: return false
        if (!readStatus(5000).isOk) return false
        return rpmbKey.contentEquals(readKey)
    }

    fun customRpmbInit(hwCode: Int, rpmbKey: ByteArray? = null): ByteArray? {
        if (rpmbKey != null) {
            if (!customRpmbSetKey(rpmbKey)) {
                log("[!] Failed to set custom RPMB key; continuing with DA-derived key.", LogLevel.WARNING)
            }
        }
        if (!sendXCmd(MtkXFlashConstants.XCmd.CUSTOM_RPMB_INIT)) return null
        val mode = if (hwCode == 0x1209 || hwCode == 0x1129) 1 else 0
        if (!xsendInt(mode)) return null
        val st = readStatus(8000)
        if (!st.isOk) {
            log("[-] XFlash RPMB Init failed (status=${st.msg})", LogLevel.ERROR)
            return null
        }
        val derivedKey = xread(5000)?.second ?: ByteArray(0)
        readStatus(5000)
        if (derivedKey.isNotEmpty()) {
            log("[+] XFlash Derived RPMB Key: ${derivedKey.joinToString("") { "%02X".format(it) }}", LogLevel.SUCCESS)
        }
        return derivedKey
    }

    fun customRpmbRead(sector: Int, sectors: Int): ByteArray? {
        if (sectors <= 0) return ByteArray(0)
        if (!sendXCmd(MtkXFlashConstants.XCmd.CUSTOM_RPMB_READ)) return null
        if (!xsendInt(sector)) return null
        if (!xsendInt(sectors)) return null
        val out = ByteArray(sectors * 0x100)
        for (i in 0 until sectors) {
            val tmp = xread(8000)?.second ?: return null
            if (tmp.size != 0x100) {
                val errCode = if (tmp.size >= 4) {
                    ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).int
                } else -1
                log("[-] XFlash RPMB Read error at sector 0x%X (code=0x%X)".format(sector + i, errCode), LogLevel.ERROR)
                return null
            }
            System.arraycopy(tmp, 0, out, i * 0x100, 0x100)
        }
        if (!readStatus(5000).isOk) return null
        return out
    }

    fun customRpmbWrite(sector: Int, sectors: Int, data: ByteArray): Boolean {
        if (sectors <= 0 || data.size < sectors * 0x100 || data.size % 0x100 != 0) {
            log("[-] Invalid RPMB frame length (${data.size} bytes).", LogLevel.ERROR)
            return false
        }
        if (!sendXCmd(MtkXFlashConstants.XCmd.CUSTOM_RPMB_WRITE)) return false
        if (!xsendInt(sector)) return false
        if (!xsendInt(sectors)) return false
        for (i in 0 until sectors) {
            val frame = data.copyOfRange(i * 0x100, (i + 1) * 0x100)
            if (!xsend(frame)) return false
            val resp = xread(5000)?.second ?: return false
            val code = if (resp.size >= 2) {
                (resp[0].toInt() and 0xFF) or ((resp[1].toInt() and 0xFF) shl 8)
            } else 1
            if (code != 0) {
                log("[-] XFlash RPMB Write rejected at sector 0x%X (resp=0x%X)".format(sector + i, code), LogLevel.ERROR)
                readStatus(5000)
                return false
            }
        }
        return readStatus(5000).isOk
    }

    fun shutdown(asyncMode: Int = 0, dlBit: Int = 0, bootMode: Int = 1, enableWdt: Int = 1): Boolean {
        if (!xsendInt(MtkXFlashConstants.Cmd.SHUTDOWN)) return false
        if (!readStatus(5000).isOk) return false
        val hasFlags = if (asyncMode != 0 || dlBit != 0 || bootMode != 0 || enableWdt != 0) 1 else 0
        val payload = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(hasFlags)
            .putInt(enableWdt) // Enable hardware watchdog timer to trigger physical reset
            .putInt(asyncMode)
            .putInt(bootMode)  // 1 = HOME_SCREEN (System), 2 = FASTBOOT
            .putInt(dlBit)
            .putInt(0) // dont_resetrtc = 0
            .putInt(0) // leaveusb = 0
            .putInt(0)
            .array()
        if (!xsend(payload)) return false
        readStatus(2000)
        return true
    }

    fun reboot(bootMode: Int = 1): Boolean {
        log("[XFLASH] Sending DA Shutdown/Reboot command (0x010007, bootMode=$bootMode, wdt=1)...", LogLevel.INFO)
        val ok = shutdown(asyncMode = 0, dlBit = 0, bootMode = bootMode, enableWdt = 1)
        // Universal MTK DA Stage 2 magic reset fallback (0xF00DD00D 0x3000)
        runCatching {
            val resetMagic = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                .putInt(0xF00DD00D.toInt())
                .putInt(0x00003000)
                .array()
            usb.writeRaw(resetMagic, 1000)
        }
        return ok
    }
}
