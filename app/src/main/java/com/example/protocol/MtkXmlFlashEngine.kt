package com.example.protocol

import android.util.Log
import com.example.model.LogLevel
import com.example.model.TerminalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class XmlFileSysOp(
    val key: String,
    val filePath: String
)

data class XmlUpFile(
    val checksum: String,
    val info: String,
    val targetFile: String,
    val packetLength: Int
)

data class XmlDwnFile(
    val checksum: String,
    val info: String,
    val sourceFile: String,
    val packetLength: Int
)

data class XmlPartitionEntry(
    val name: String,
    val startSector: Long,
    val sizeSectors: Long
)

sealed class XmlCmdResult {
    object Ok : XmlCmdResult()
    object Unsupported : XmlCmdResult()
    data class Error(val message: String) : XmlCmdResult()
    data class Data(val bytes: ByteArray) : XmlCmdResult()
    data class DownloadFile(val file: XmlDwnFile) : XmlCmdResult()
    data class UploadFile(val file: XmlUpFile) : XmlCmdResult()
    data class FileSysOp(val op: XmlFileSysOp) : XmlCmdResult()
    object Start : XmlCmdResult()
    data class End(val result: String) : XmlCmdResult()
}

/**
 * MediaTek V6 XML Flash Protocol Engine
 * Direct, faithful Kotlin port of Python mtkclient:
 * - mtkclient/Library/DA/xmlflash/xml_lib.py
 * - mtkclient/Library/DA/storage.py (XML storage target naming)
 */
class MtkXmlFlashEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: ((TerminalLog) -> Unit)? = null
) {
    companion object {
        private const val TAG = "MtkXmlFlashEngine"
        const val MAGIC: Int = MtkXmlCmd.MAGIC
    }

    var emmcInfo: EmmcInfo? = null
        private set
    var ufsInfo: UfsInfo? = null
        private set
    var nandTotalSize: Long = 0L
        private set
    var detectedFlashType: String = "emmc"
        private set
    var storageTypeLabel: String = "EMMC"
        private set
    var flashSize: Long = 0L
        private set
    var pageSize: Int = 512
        private set
    var cid: String = ""
        private set
    var daExtLoaded: Boolean = false

    private fun log(msg: String, level: LogLevel = LogLevel.INFO) {
        logCallback?.invoke(TerminalLog("", msg, level))
    }

    fun getField(data: String, fieldName: String): String {
        val open = "<$fieldName>"
        val close = "</$fieldName>"
        val start = data.indexOf(open)
        if (start == -1) return ""
        val contentStart = start + open.length
        val end = data.indexOf(close, contentStart)
        return if (end != -1) data.substring(contentStart, end) else ""
    }

    private fun parseHexLong(s: String, default: Long = 0L): Long {
        val clean = s.trim().removePrefix("0x").removePrefix("0X")
        if (clean.isEmpty()) return default
        return clean.toLongOrNull(16) ?: s.trim().toLongOrNull() ?: default
    }

    // =========================================================================
    // 1. Low-Level Frame I/O (xsend / xread / getResponse / ack)
    // =========================================================================

    fun xsend(data: ByteArray, dataType: Int = MtkXmlCmd.DataType.DT_PROTOCOL_FLOW): Boolean {
        val hdr = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(MAGIC)
            .putInt(dataType)
            .putInt(data.size)
            .array()
        if (usb.writeRaw(hdr, 5000) != 12) return false
        if (data.isNotEmpty()) {
            return usb.writeRaw(data, 10000) == data.size
        }
        return true
    }

    fun xsend(text: String, dataType: Int = MtkXmlCmd.DataType.DT_PROTOCOL_FLOW): Boolean {
        val raw = text.toByteArray(Charsets.UTF_8)
        val terminated = ByteArray(raw.size + 1)
        System.arraycopy(raw, 0, terminated, 0, raw.size)
        return xsend(terminated, dataType)
    }

    fun xsendInt32(value: Int, dataType: Int = MtkXmlCmd.DataType.DT_PROTOCOL_FLOW): Boolean {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        return xsend(bytes, dataType)
    }

    fun xsendInt64(value: Long, dataType: Int = MtkXmlCmd.DataType.DT_PROTOCOL_FLOW): Boolean {
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
        return xsend(bytes, dataType)
    }

    fun xread(timeoutMs: Int = 8000): ByteArray? {
        val hdrBuf = ByteArray(12)
        if (usb.readRaw(hdrBuf, timeoutMs, readFull = true) != 12) return null
        val buf = ByteBuffer.wrap(hdrBuf).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        buf.int // datatype
        val length = buf.int
        if (magic != MAGIC) {
            Log.w(TAG, "xread wrong magic: 0x%08X".format(magic))
            return null
        }
        if (length < 0 || length > 0x1000000) return null
        if (length == 0) return ByteArray(0)
        val payload = ByteArray(length)
        val got = usb.readRaw(payload, timeoutMs.coerceAtLeast(10000), readFull = true)
        return if (got == length) payload else null
    }

    fun getResponseRaw(timeoutMs: Int = 8000): ByteArray? {
        val hdr = ByteArray(12)
        if (usb.readRaw(hdr, timeoutMs, readFull = true) != 12) return null
        val magic = ByteBuffer.wrap(hdr, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val dataType = ByteBuffer.wrap(hdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val length = ByteBuffer.wrap(hdr, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (magic != MAGIC || dataType != MtkXmlCmd.DataType.DT_PROTOCOL_FLOW) return null
        if (length < 0 || length > 0x1000000) return null
        if (length == 0) return ByteArray(0)
        val data = ByteArray(length)
        val got = usb.readRaw(data, timeoutMs.coerceAtLeast(10000), readFull = true)
        return if (got == length) data else null
    }

    fun getResponse(timeoutMs: Int = 8000): String {
        val raw = getResponseRaw(timeoutMs) ?: return ""
        var end = raw.size
        while (end > 0 && raw[end - 1] == 0.toByte()) {
            end--
        }
        return String(raw, 0, end, Charsets.UTF_8)
    }

    fun getResponseData(timeoutMs: Int = 10000): ByteArray {
        val hdr = ByteArray(12)
        if (usb.readRaw(hdr, timeoutMs, readFull = true) != 12) return ByteArray(0)
        val magic = ByteBuffer.wrap(hdr, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val dataType = ByteBuffer.wrap(hdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val length = ByteBuffer.wrap(hdr, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (magic != MAGIC || dataType != MtkXmlCmd.DataType.DT_PROTOCOL_FLOW || length <= 0 || length > 0x2000000) {
            return ByteArray(0)
        }
        val data = ByteArray(length)
        val got = usb.readRaw(data, timeoutMs.coerceAtLeast(15000), readFull = true)
        return if (got > 0) {
            if (got == length) data else data.copyOf(got)
        } else {
            ByteArray(0)
        }
    }

    fun ack(): Boolean = xsend("OK\u0000")

    fun ackValue(length: Long): Boolean = xsend("OK@${MtkXmlCmd.toHex(length)}\u0000")

    fun ackText(text: String): Boolean = xsend("OK@$text\u0000")

    // =========================================================================
    // 2. Command Dispatch & Transfer Primitives (sendCommand / upload / download)
    // =========================================================================

    fun sendCommand(xmlData: String, noAck: Boolean = false): XmlCmdResult {
        if (!xsend(xmlData)) return XmlCmdResult.Error("USB write failed")
        val result = getResponse()
        if (result == "OK") {
            if (noAck) return XmlCmdResult.Ok
            val (cmd, res) = getCommandResult()
            if (cmd == "CMD:END") {
                ack()
                val endMsg = (res as? XmlCmdResult.End)?.result ?: ""
                if (endMsg == "2nd DA address is invalid. reset.\r\n" ||
                    endMsg.contains("2nd DA address is invalid", ignoreCase = true)
                ) {
                    log("[-] $endMsg", LogLevel.ERROR)
                    return XmlCmdResult.Error(endMsg)
                }
                val (scmd, _) = getCommandResult()
                return if (scmd == "CMD:START") {
                    if (endMsg == "OK") {
                        XmlCmdResult.Ok
                    } else {
                        if (endMsg.isNotEmpty()) log("[-] XML Command error: $endMsg", LogLevel.ERROR)
                        XmlCmdResult.Error(endMsg.ifEmpty { "Unknown XML CMD:END error" })
                    }
                } else {
                    XmlCmdResult.Error("Expected CMD:START after CMD:END, received $scmd")
                }
            } else {
                return res
            }
        } else if (result == "ERR!UNSUPPORTED") {
            getCommandResult()
            ack()
            getCommandResult() // CMD:START
            return XmlCmdResult.Unsupported
        } else if (result.contains("ERR!")) {
            return XmlCmdResult.Error(result)
        }
        return XmlCmdResult.Error(result.ifEmpty { "Empty response from DA" })
    }

    fun getCommandResult(): Pair<String, XmlCmdResult> {
        var data = getResponse()
        var cmd = getField(data, "command")

        if (cmd.isEmpty() && data.contains("OK@")) {
            val lenHex = data.substringAfter("@")
            val length = parseHexLong(lenHex, 0L).toInt()
            ack()
            val sresp = getResponse()
            if (sresp.contains("OK")) {
                ack()
                val out = ByteArrayOutputStream()
                var bytesToRead = length
                while (bytesToRead > 0) {
                    val tmp = getResponseData()
                    if (tmp.isEmpty()) break
                    out.write(tmp)
                    bytesToRead -= tmp.size
                }
                ack()
                return "" to XmlCmdResult.Data(out.toByteArray())
            }
        }

        if (cmd == "CMD:PROGRESS-REPORT") {
            ack()
            var progressResp = ""
            var guard = 0
            while (progressResp != "OK!EOT" && guard < 1024) {
                progressResp = getResponse()
                if (progressResp.isEmpty()) break
                ack()
                guard++
            }
            data = getResponse()
            cmd = getField(data, "command")
        }

        return when (cmd) {
            "CMD:START" -> {
                ack()
                cmd to XmlCmdResult.Start
            }
            "CMD:DOWNLOAD-FILE" -> {
                val checksum = getField(data, "checksum")
                val info = getField(data, "info")
                val sourceFile = getField(data, "source_file")
                val pktLen = parseHexLong(getField(data, "packet_length"), 0x1000L).toInt().coerceAtLeast(512)
                ack()
                cmd to XmlCmdResult.DownloadFile(XmlDwnFile(checksum, info, sourceFile, pktLen))
            }
            "CMD:UPLOAD-FILE" -> {
                val checksum = getField(data, "checksum")
                val info = getField(data, "info")
                val targetFile = getField(data, "target_file")
                val pktLen = parseHexLong(getField(data, "packet_length"), 0x100000L).toInt().coerceAtLeast(512)
                ack()
                cmd to XmlCmdResult.UploadFile(XmlUpFile(checksum, info, targetFile, pktLen))
            }
            "CMD:FILE-SYS-OPERATION" -> {
                val key = getField(data, "key")
                val filePath = getField(data, "file_path")
                ack()
                cmd to XmlCmdResult.FileSysOp(XmlFileSysOp(key, filePath))
            }
            "CMD:END" -> {
                val resultStr = getField(data, "result")
                val finalMsg = if (data.contains("message") && resultStr != "OK") {
                    getField(data, "message").ifEmpty { resultStr }
                } else {
                    resultStr
                }
                cmd to XmlCmdResult.End(finalMsg)
            }
            else -> cmd to XmlCmdResult.Error("Unknown/empty XML command result: $data")
        }
    }

    fun upload(
        dwnFile: XmlDwnFile,
        data: ByteArray,
        raw: Boolean = false,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        val sourceFile = dwnFile.sourceFile
        val packetLength = dwnFile.packetLength.coerceAtLeast(512)
        val length = if (sourceFile.contains(":")) {
            val parts = sourceFile.split(":")
            if (parts.size >= 3) parseHexLong(parts[2], data.size.toLong()).toInt() else data.size
        } else {
            data.size
        }.coerceAtMost(data.size)

        if (!ackValue(length.toLong())) return false
        var resp = getResponse()
        if (resp != "OK") return false

        var pos = 0
        var bytesToWrite = length
        while (bytesToWrite > 0) {
            if (!ackValue(0L)) return false
            resp = getResponse()
            if (!resp.contains("OK")) {
                val msg = getField(resp, "message")
                log("[-] Error writing XML chunk ACK0 at 0x%X: $msg".format(pos), LogLevel.ERROR)
                return false
            }
            val end = minOf(pos + packetLength, data.size)
            val chunk = data.copyOfRange(pos, end)
            if (!xsend(chunk)) return false
            resp = getResponse()
            if (!resp.contains("OK")) {
                log("[-] Error writing XML chunk data at 0x%X".format(pos), LogLevel.ERROR)
                return false
            }
            pos += chunk.size
            bytesToWrite -= packetLength
            onProgress?.invoke(pos.toLong().coerceAtMost(length.toLong()), length.toLong())
        }

        if (raw) ack()

        val (cmd, res) = getCommandResult()
        ack()
        if (cmd == "CMD:END" && (res as? XmlCmdResult.End)?.result == "OK") {
            val (nextCmd, _) = getCommandResult()
            if (nextCmd == "CMD:START") return true
        } else {
            val errMsg = (res as? XmlCmdResult.End)?.result ?: res.toString()
            log("[-] XML upload ended with error: $errMsg", LogLevel.ERROR)
            getCommandResult()
        }
        return false
    }

    fun download(upFile: XmlUpFile): ByteArray? {
        val resp = getResponse()
        if (!resp.contains("OK@")) {
            log("[-] Error initiating XML download: $resp", LogLevel.ERROR)
            return null
        }
        val length = parseHexLong(resp.substringAfter("@"), 0L).toInt()
        ack()
        val sresp = getResponse()
        if (!sresp.contains("OK")) {
            log("[-] XML download handshake failed: $sresp", LogLevel.ERROR)
            return null
        }
        ack()
        val out = ByteArrayOutputStream(length.coerceAtLeast(64))
        var bytesToRead = length
        while (bytesToRead > 0) {
            val tmp = getResponseData()
            if (tmp.isEmpty()) break
            out.write(tmp)
            bytesToRead -= tmp.size
        }
        ack()
        return out.toByteArray()
    }

    fun downloadRaw(
        upFile: XmlUpFile,
        outStream: OutputStream? = null,
        onProgress: ((Long, Long) -> Unit)? = null
    ): ByteArray? {
        val resp = getResponse()
        if (!resp.contains("OK@")) {
            log("[-] Error on downloading raw XML data: $resp", LogLevel.ERROR)
            return null
        }
        val length = parseHexLong(resp.substringAfter("@"), 0L)
        ack()
        val sresp = getResponse()
        if (!sresp.contains("OK")) {
            log("[-] XML download_raw handshake failed: $sresp", LogLevel.ERROR)
            return null
        }
        ack()

        val memOut = if (outStream == null) ByteArrayOutputStream(length.toInt().coerceIn(64, 16 * 1024 * 1024)) else null
        var bytesRead = 0L
        var bytesToRead = length
        while (bytesToRead > 0L) {
            val tmp = getResponseData()
            if (tmp.isEmpty()) break
            if (outStream != null) {
                outStream.write(tmp)
            } else {
                memOut?.write(tmp)
            }
            bytesToRead -= tmp.size.toLong()
            bytesRead += tmp.size.toLong()
            onProgress?.invoke(bytesRead, length)
            ack()
            val chunkAck = getResponse()
            if (!chunkAck.contains("OK")) {
                break
            } else {
                ack()
            }
        }
        outStream?.flush()
        return memOut?.toByteArray() ?: ByteArray(0)
    }

    // =========================================================================
    // 3. Stage 1 / Stage 2 Boot & Security Handshake
    // =========================================================================

    fun setupEnv(uartLogLevel: Int = 2): Boolean {
        val levelStr = when (uartLogLevel) {
            0 -> MtkXmlCmd.LogLevelXml.TRACE
            1 -> MtkXmlCmd.LogLevelXml.DEBUG
            3 -> MtkXmlCmd.LogLevelXml.WARN
            4 -> MtkXmlCmd.LogLevelXml.ERROR
            else -> MtkXmlCmd.LogLevelXml.INFO
        }
        val res = sendCommand(
            MtkXmlCmd.cmdSetRuntimeParameter(
                daLogLevel = levelStr,
                systemOs = MtkXmlCmd.FtSystemOSE.OS_LINUX
            )
        )
        return res is XmlCmdResult.Ok
    }

    fun setupHwInit(): Boolean {
        sendCommand(
            MtkXmlCmd.cmdHostSupportedCommands(
                "CMD:DOWNLOAD-FILE^1@CMD:FILE-SYS-OPERATION^1@CMD:PROGRESS-REPORT^1@CMD:UPLOAD-FILE^1@"
            )
        )
        val res = sendCommand(MtkXmlCmd.cmdNotifyInitHw())
        return res is XmlCmdResult.Ok
    }

    fun setupHostInfo(hostInfo: String = ""): Boolean =
        sendCommand(MtkXmlCmd.cmdSetHostInfo(hostInfo)) is XmlCmdResult.Ok

    fun initStage1Session(): Boolean {
        val (cmd, _) = getCommandResult()
        if (cmd != "CMD:START") {
            log("[-] Expected CMD:START from V6 DA1, got '$cmd'", LogLevel.ERROR)
            return false
        }
        setupEnv()
        setupHwInit()
        setupHostInfo()
        log("[+] XML DA Stage 1 handshake completed.", LogLevel.SUCCESS)
        return true
    }

    fun bootTo(
        addr: Long,
        data: ByteArray,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        val res = sendCommand(MtkXmlCmd.cmdBootTo(atAddr = addr, jmpAddr = addr, length = data.size.toLong()))
        if (res is XmlCmdResult.DownloadFile) {
            log("[XML] Uploading stage 2 (${data.size} bytes @ 0x%08X)...".format(addr), LogLevel.INFO)
            if (upload(res.file, data, raw = false, onProgress = onProgress)) {
                log("[+] Successfully uploaded XML stage 2.", LogLevel.SUCCESS)
                return true
            }
        } else {
            log("[-] Unexpected BOOT-TO XML response: $res", LogLevel.ERROR)
        }
        return false
    }

    fun handleSla(data: ByteArray = ByteArray(0x100)): Boolean {
        val res = sendCommand(MtkXmlCmd.cmdSecuritySetFlashPolicy(length = data.size.toLong()))
        if (res is XmlCmdResult.DownloadFile) {
            log("[XML] Running SLA auth...", LogLevel.INFO)
            if (upload(res.file, data)) {
                log("[+] XML SLA auth accepted.", LogLevel.SUCCESS)
                return true
            }
        }
        return false
    }

    fun handleAllinoneSignature(
        data: ByteArray = ByteArray(0x100),
        filename: String? = null
    ): Boolean {
        val cmd = if (filename == null) {
            MtkXmlCmd.cmdSecuritySetAllinoneSignature(length = data.size.toLong(), filename = null)
        } else {
            MtkXmlCmd.cmdSecuritySetAllinoneSignature(filename = filename)
        }
        val res = sendCommand(cmd)
        if (res is XmlCmdResult.DownloadFile) {
            if (upload(res.file, data)) {
                return true
            }
        }
        return false
    }

    fun changeUsbSpeed(): Boolean =
        sendCommand(MtkXmlCmd.cmdCanHigherUsbSpeed()) is XmlCmdResult.Ok

    fun getSysProperty(key: String = "DA.SLA", length: Long = 0x200000L): ByteArray? {
        sendCommand(MtkXmlCmd.cmdGetSysProperty(key = key, length = length), noAck = true)
        val (_, res) = getCommandResult()
        if (res !is XmlCmdResult.UploadFile) return null
        val data = download(res.file) ?: return null
        val (_, endRes) = getCommandResult()
        ack()
        if ((endRes as? XmlCmdResult.End)?.result == "OK") {
            val (nextCmd, _) = getCommandResult()
            if (nextCmd == "CMD:START") return data
        }
        return null
    }

    fun checkSla(): Boolean? {
        val data = getSysProperty("DA.SLA", 0x200000L)?.toString(Charsets.UTF_8) ?: return null
        val keyMarker = "item key="
        val markerPos = data.indexOf(keyMarker)
        if (markerPos == -1) return null
        val sub = data.substring(markerPos + 8)
        val gt = sub.indexOf('>')
        val lt = if (gt != -1) sub.indexOf('<', gt + 1) else -1
        if (gt == -1 || lt == -1) return null
        val value = sub.substring(gt + 1, lt)
        return value != "DISABLED"
    }

    fun getDevInfo(): Map<String, ByteArray>? {
        if (sendCommand(MtkXmlCmd.cmdGetDevInfo(), noAck = true) !is XmlCmdResult.Ok) return null
        val (_, res) = getCommandResult()
        if (res !is XmlCmdResult.UploadFile) return null
        val data = download(res.file) ?: return null
        val (_, endRes) = getCommandResult()
        ack()
        if ((endRes as? XmlCmdResult.End)?.result == "OK") {
            val (_, startRes) = getCommandResult()
            if (startRes is XmlCmdResult.Start) {
                val text = data.toString(Charsets.UTF_8)
                val map = mutableMapOf<String, ByteArray>()
                if (text.contains("<rnd>")) map["rnd"] = MtkDaPatcher.hexToBytes(getField(text, "rnd"))
                if (text.contains("<hrid>")) map["hrid"] = MtkDaPatcher.hexToBytes(getField(text, "hrid"))
                if (text.contains("<socid>")) map["socid"] = MtkDaPatcher.hexToBytes(getField(text, "socid"))
                return map
            }
        }
        return null
    }

    /**
     * Authenticates XML DA2 SLA when enabled (mirrors xml_lib.py lines 4177-4222).
     */
    fun performXmlSlaAuth(da2: ByteArray): Boolean {
        val slaEnabled = checkSla()
        if (slaEnabled == false) {
            log("[XML] DA XML SLA is disabled.", LogLevel.INFO)
            return true
        }
        if (slaEnabled == null) {
            log("[XML] SLA property query unavailable, continuing...", LogLevel.WARNING)
            return true
        }
        log("[XML] DA XML SLA is ENABLED. Negotiating auth...", LogLevel.WARNING)

        val rsakey = MtkSlaKeyDatabase.findMatchingKey(da2, preferDaKeys = true)
        if (rsakey != null) {
            val devInfo = getDevInfo()
            val rnd = devInfo?.get("rnd")
            if (rnd != null && rnd.isNotEmpty()) {
                val sig = runCatching { MtkSlaKeyDatabase.generateDaSlaSignature(rnd, rsakey) }.getOrNull()
                if (sig != null && handleSla(sig)) {
                    log("[+] XML SLA signature accepted (${rsakey.vendor}/${rsakey.name}).", LogLevel.SUCCESS)
                    return true
                }
            }
        }

        if (MtkDaPatcher.findBinary(da2, "Transsion".toByteArray(Charsets.US_ASCII)) != null) {
            val transsionSig = MtkDaPatcher.hexToBytes(
                "46177D7539E98DF6D6FF46C2E5BB459A059F91E5100EA9AE5908804F009E3393" +
                    "72BD94D937C366B0320CD58ED905997D2D529E6285E3358EDE178C33BDD06729" +
                    "5F4B18E701C39B96A7B6F4C8B8CE3DAC115BAF30254561CC5A64FB9FDCD45A28" +
                    "F7058EBC0A707EC5146C296FF72E7C33C0CC254A2F2A78F6C7BD9D2692B2145C" +
                    "2334A10BEE6D8B9303D8F368E630B49AA2C783A170A5449F7E9DCFF8C8EA8690" +
                    "F05B6F3ED4D3DED5B1FAB30A82E93FBFC91D3B4F03728597AB27E3A4F06600D7" +
                    "6FA0D50DE3D8BAD6E0CF45654DBA23121F601130C2AB61B3F4C867E5BC0FD6B2" +
                    "5C3F9FEBCD1B3393EF6406AF8C440CE3454C3689AE88405F8A2B875EC0C32A9B"
            )
            if (handleSla(transsionSig)) {
                log("[+] Transsion/Infinix XML SLA accepted.", LogLevel.SUCCESS)
                return true
            }
        }

        if (handleSla(ByteArray(0x100))) {
            log("[+] XML dummy 256-byte SLA auth accepted.", LogLevel.SUCCESS)
            return true
        }
        return false
    }

    // =========================================================================
    // 4. Hardware Info, Partition Table & Flash Operations
    // =========================================================================

    fun getHwInfo(): Boolean {
        sendCommand(MtkXmlCmd.cmdGetHwInfo(), noAck = true)
        val (_, res) = getCommandResult()
        if (res !is XmlCmdResult.UploadFile) return false
        val rawData = download(res.file) ?: return false
        val (_, endRes) = getCommandResult()
        ack()
        if ((endRes as? XmlCmdResult.End)?.result != "OK") return false
        val (_, startRes) = getCommandResult()
        if (startRes !is XmlCmdResult.Start) return false

        val xml = rawData.toString(Charsets.UTF_8)
        val stype = getField(xml, "storage")
        storageTypeLabel = stype
        when (stype) {
            "UFS" -> {
                detectedFlashType = "ufs"
                pageSize = 4096
                val blk = parseHexLong(getField(xml, "block_size"), 4096L)
                val lu0 = parseHexLong(getField(xml, "lua0_size"))
                val lu1 = parseHexLong(getField(xml, "lua1_size"))
                val lu2 = parseHexLong(getField(xml, "lua2_size"))
                val lu3 = parseHexLong(getField(xml, "lua3_size"))
                var ufsCid = getField(xml, "id")
                if (ufsCid.isEmpty()) ufsCid = getField(xml, "ufs_cid")
                cid = ufsCid
                ufsInfo = UfsInfo(1, blk, lu0, lu1, lu2, lu3, ufsCid)
                flashSize = lu2
                log("[XML DA2] Storage: UFS (LU2=${lu2 / (1024L * 1024L)} MB, block=$blk)", LogLevel.SUCCESS)
            }
            "EMMC" -> {
                detectedFlashType = "emmc"
                pageSize = 512
                val blk = parseHexLong(getField(xml, "block_size"), 512L)
                val b1 = parseHexLong(getField(xml, "boot1_size"))
                val b2 = parseHexLong(getField(xml, "boot2_size"))
                val rpmb = parseHexLong(getField(xml, "rpmb_size"))
                val user = parseHexLong(getField(xml, "user_size"))
                val gp1 = parseHexLong(getField(xml, "gp1_size"))
                val gp2 = parseHexLong(getField(xml, "gp2_size"))
                val gp3 = parseHexLong(getField(xml, "gp3_size"))
                val gp4 = parseHexLong(getField(xml, "gp4_size"))
                var eCid = getField(xml, "id")
                if (eCid.isEmpty()) eCid = getField(xml, "emmc_cid")
                cid = eCid
                emmcInfo = EmmcInfo(1, blk, b1, b2, rpmb, gp1, gp2, gp3, gp4, user, eCid)
                flashSize = user
                log("[XML DA2] Storage: eMMC (USER=${user / (1024L * 1024L)} MB, CID=$eCid)", LogLevel.SUCCESS)
            }
            "NAND" -> {
                detectedFlashType = "nand"
                pageSize = parseHexLong(getField(xml, "page_size"), 2048L).toInt()
                nandTotalSize = parseHexLong(getField(xml, "total_size"))
                flashSize = nandTotalSize
                cid = getField(xml, "id")
                log("[XML DA2] Storage: NAND (${nandTotalSize / (1024L * 1024L)} MB)", LogLevel.SUCCESS)
            }
            else -> {
                log("[-] Unknown XML storage type: $stype", LogLevel.ERROR)
                return false
            }
        }
        return true
    }

    fun checkLifecycle(): Boolean {
        sendCommand(MtkXmlCmd.cmdEmmcControl("LIFE-CYCLE-STATUS"), noAck = true)
        val (cmd, res) = getCommandResult()
        if (res !is XmlCmdResult.UploadFile) {
            if (cmd == "CMD:END") {
                ack()
                getCommandResult()
            }
            return false
        }
        val data = download(res.file) ?: return false
        val (_, endRes) = getCommandResult()
        ack()
        if ((endRes as? XmlCmdResult.End)?.result == "OK") {
            val (_, startRes) = getCommandResult()
            if (startRes is XmlCmdResult.Start) {
                return data.toString(Charsets.UTF_8).trim('\u0000') == "OK"
            }
        }
        return false
    }

    fun runRamTest(
        function: String = "FLIP",
        startAddress: Long = 0x4000000L,
        length: Long = 0x100000L,
        repeat: Long = 10L
    ): Boolean {
        log("[XML] Running DA RAM-TEST ($function @ 0x%X, len=0x%X)...".format(startAddress, length), LogLevel.INFO)
        val res = sendCommand(
            MtkXmlCmd.cmdRamTest(
                function = function,
                startAddress = startAddress,
                length = length,
                repeat = repeat
            )
        )
        return res is XmlCmdResult.Ok
    }

    fun getDaInfo(): String? {
        sendCommand(MtkXmlCmd.cmdGetDaInfo(), noAck = true)
        val (_, res) = getCommandResult()
        if (res !is XmlCmdResult.UploadFile) return null
        val data = download(res.file) ?: return null
        val (_, endRes) = getCommandResult()
        ack()
        if ((endRes as? XmlCmdResult.End)?.result == "OK") {
            getCommandResult() // CMD:START
        }
        return data.toString(Charsets.UTF_8).trim('\u0000')
    }

    /**
     * Port of Storage.partitiontype_and_size(..., xml=True) from storage.py.
     */
    fun resolveXmlPartitionTarget(partType: String?, requestedLength: Long): Pair<String, Long> {
        val norm = partType?.lowercase()
        val pSize = pageSize.toLong().coerceAtLeast(512L)
        return when (detectedFlashType) {
            "ufs" -> {
                val ufs = ufsInfo
                val target = when (norm) {
                    null, "user", "ufs_lua2", "ufs-lua2" -> "UFS-LUA2"
                    "boot1", "lu0", "ufs_lua0", "ufs-lua0" -> "UFS-LUA0"
                    "boot2", "lu1", "ufs_lua1", "ufs-lua1" -> "UFS-LUA1"
                    "lu2" -> "UFS-LUA2"
                    "rpmb", "lu3", "ufs_lua3", "ufs-lua3" -> "UFS-LUA3"
                    else -> "UFS-LUA2"
                }
                val maxLen = when (target) {
                    "UFS-LUA0" -> ufs?.lu0Size ?: flashSize
                    "UFS-LUA1" -> ufs?.lu1Size ?: flashSize
                    "UFS-LUA3" -> ufs?.rpmbSize ?: flashSize
                    else -> ufs?.lu2Size ?: flashSize
                }.let { if (it > 0L) it else requestedLength.coerceAtLeast(pSize) }
                target to minOf(requestedLength, maxLen)
            }
            "nand" -> "NAND-WHOLE" to requestedLength
            "nor" -> "NOR-WHOLE" to requestedLength
            else -> {
                val emmc = emmcInfo
                val target = when (norm) {
                    null, "user", "emmc_user", "emmc-user" -> "EMMC-USER"
                    "boot1", "emmc_boot1", "emmc-boot1" -> "EMMC-BOOT1"
                    "boot2", "emmc_boot2", "emmc-boot2" -> "EMMC-BOOT2"
                    "gp1" -> "EMMC-GP1"
                    "gp2" -> "EMMC-GP2"
                    "gp3" -> "EMMC-GP3"
                    "gp4" -> "EMMC-GP4"
                    "rpmb" -> "EMMC-RPMB"
                    else -> "EMMC-USER"
                }
                val maxLen = when (target) {
                    "EMMC-BOOT1" -> emmc?.boot1Size ?: flashSize
                    "EMMC-BOOT2" -> emmc?.boot2Size ?: flashSize
                    "EMMC-RPMB" -> emmc?.rpmbSize ?: flashSize
                    "EMMC-GP1" -> emmc?.gp1Size ?: flashSize
                    "EMMC-GP2" -> emmc?.gp2Size ?: flashSize
                    "EMMC-GP3" -> emmc?.gp3Size ?: flashSize
                    "EMMC-GP4" -> emmc?.gp4Size ?: flashSize
                    else -> emmc?.userSize ?: flashSize
                }.let { if (it > 0L) it else requestedLength.coerceAtLeast(pSize) }
                target to minOf(requestedLength, maxLen)
            }
        }
    }

    fun readPartitionTable(): Pair<String, List<XmlPartitionEntry>>? {
        sendCommand(MtkXmlCmd.cmdReadPartitionTable(), noAck = true)
        val (_, res) = getCommandResult()
        if (res !is XmlCmdResult.UploadFile) return null
        val data = download(res.file) ?: return null
        val (_, endRes) = getCommandResult()
        ack()
        if ((endRes as? XmlCmdResult.End)?.result != "OK") return null
        val (_, startRes) = getCommandResult()
        if (startRes !is XmlCmdResult.Start) return null

        val text = data.toString(Charsets.UTF_8)
        val parts = mutableListOf<XmlPartitionEntry>()
        val div = pageSize.toLong().coerceAtLeast(512L)
        for (item in text.split("<pt>")) {
            val name = getField(item, "name")
            if (name.isEmpty()) continue
            val startStr = getField(item, "start")
            val sizeStr = getField(item, "size")
            if (sizeStr.isEmpty() || startStr.isEmpty()) continue
            val startBytes = parseHexLong(startStr, 0L)
            val sizeBytes = parseHexLong(sizeStr, 0L)
            parts.add(XmlPartitionEntry(name, startBytes / div, sizeBytes / div))
        }
        return text to parts
    }

    suspend fun readFlash(
        addr: Long,
        length: Long,
        partType: String? = null,
        onProgress: ((Long, Long) -> Unit)? = null
    ): ByteArray? = withContext(Dispatchers.IO) {
        val (targetPart, safeLen) = resolveXmlPartitionTarget(partType, length)
        val sendOk = sendCommand(MtkXmlCmd.cmdReadFlash(targetPart, addr, safeLen), noAck = true)
        if (sendOk !is XmlCmdResult.Ok) {
            log("[-] XML READ-FLASH rejected.", LogLevel.ERROR)
            return@withContext null
        }
        val (_, res) = getCommandResult()
        if (res !is XmlCmdResult.UploadFile) return@withContext null
        val bytes = downloadRaw(res.file, outStream = null, onProgress = onProgress)
        val (_, startRes) = getCommandResult()
        if (startRes is XmlCmdResult.Start) bytes else bytes
    }

    suspend fun readFlashToStream(
        addr: Long,
        length: Long,
        outStream: OutputStream,
        partType: String? = null,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val (targetPart, safeLen) = resolveXmlPartitionTarget(partType, length)
        val sendOk = sendCommand(MtkXmlCmd.cmdReadFlash(targetPart, addr, safeLen), noAck = true)
        if (sendOk !is XmlCmdResult.Ok) {
            log("[-] XML READ-FLASH stream rejected.", LogLevel.ERROR)
            return@withContext false
        }
        val (_, res) = getCommandResult()
        if (res !is XmlCmdResult.UploadFile) return@withContext false
        val ok = downloadRaw(res.file, outStream = outStream, onProgress = onProgress) != null
        val (_, startRes) = getCommandResult()
        return@withContext ok && (startRes is XmlCmdResult.Start)
    }

    suspend fun writeFlash(
        addr: Long,
        data: ByteArray,
        partType: String? = null,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val padded = if (data.size % 512 != 0) {
            data + ByteArray(512 - (data.size % 512))
        } else {
            data
        }
        val (targetPart, _) = resolveXmlPartitionTarget(partType, padded.size.toLong())
        sendCommand(
            MtkXmlCmd.cmdWriteFlash(partition = targetPart, offset = addr, memLength = padded.size.toLong()),
            noAck = true
        )
        val (_, opRes) = getCommandResult()
        if (opRes !is XmlCmdResult.FileSysOp || opRes.op.key != "FILE-SIZE") {
            log("[-] XML WRITE-FLASH missing FILE-SIZE request: $opRes", LogLevel.ERROR)
            return@withContext false
        }
        ackValue(padded.size.toLong())
        val (_, dwnRes) = getCommandResult()
        if (dwnRes !is XmlCmdResult.DownloadFile) {
            log("[-] XML WRITE-FLASH missing DOWNLOAD-FILE request: $dwnRes", LogLevel.ERROR)
            return@withContext false
        }
        val ok = upload(dwnRes.file, padded, raw = true, onProgress = onProgress)
        if (!ok) {
            log("[-] Error writing XML flash at 0x%08X".format(addr), LogLevel.ERROR)
        }
        ok
    }

    suspend fun formatFlash(
        addr: Long,
        length: Long,
        partType: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val (targetPart, safeLen) = resolveXmlPartitionTarget(partType, length)
        log("[XML] Erasing $targetPart @ 0x%X (len=0x%X)...".format(addr, safeLen), LogLevel.WARNING)
        val res = sendCommand(MtkXmlCmd.cmdEraseFlash(targetPart, addr, safeLen))
        val ok = res is XmlCmdResult.Ok
        if (ok) {
            log("[+] XML Format completed.", LogLevel.SUCCESS)
        } else {
            log("[-] XML Format failed: $res", LogLevel.ERROR)
        }
        ok
    }

    fun shutdown(bootMode: Int = MtkXmlCmd.ShutDownModes.NORMAL): Boolean {
        when (bootMode) {
            MtkXmlCmd.ShutDownModes.FASTBOOT -> sendCommand(MtkXmlCmd.cmdSetBootMode(MtkXmlCmd.BootModes.FASTBOOT))
            MtkXmlCmd.ShutDownModes.TEST -> sendCommand(MtkXmlCmd.cmdSetBootMode(MtkXmlCmd.BootModes.TESTMODE))
            MtkXmlCmd.ShutDownModes.META -> sendCommand(MtkXmlCmd.cmdSetBootMode(MtkXmlCmd.BootModes.META))
        }
        val res = sendCommand(MtkXmlCmd.cmdReboot(disconnect = false))
        return res is XmlCmdResult.Ok
    }

    fun reboot(): Boolean = shutdown(MtkXmlCmd.ShutDownModes.NORMAL)
}
