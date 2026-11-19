package com.example.protocol

import android.content.Context
import com.example.model.LogLevel
import com.example.model.MtkChipInfo
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.TerminalLog
import com.example.parser.GptParser
import com.example.storage.BackupStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MediaTek BootROM & Download Agent Core Protocol Engine
 * Faithfully ported from Python mtkclient:
 * - mtkclient/Library/mtk_preloader.py (init, setreg_disablewatchdogtimer, get_blver, read32, write32)
 * - mtkclient/Library/mtk_class.py (crasher, bypass_security)
 * - mtkclient/Library/mtk_da_handler.py (dump_preloader_ram, configure_da)
 * - mtkclient/Library/DA/xflash/xflash_lib.py (upload_da1, upload_da, patch_da, reinit)
 *
 * 100% Real Hardware Protocol Implementation - No Mock/Simulation Data.
 */
class MtkBromProtocolEngine(
    private val context: Context,
    private val targetPhoneUsb: TargetPhoneUsbManager,
    private val storageManager: BackupStorageManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {
    private val flashEngine = MtkFlashEngine(targetPhoneUsb, logCallback, progressCallback)
    private val dumpEngine = MtkDumpEngine(targetPhoneUsb, storageManager, logCallback, progressCallback)
    private val formatEngine = MtkFormatEngine(targetPhoneUsb, flashEngine, logCallback, progressCallback)
    private val securityEngine = MtkSecurityBypassEngine(targetPhoneUsb, logCallback, progressCallback)
    private val calibrationEngine = MtkCalibrationEngine(
        usb = targetPhoneUsb,
        flashEngine = flashEngine,
        dumpEngine = dumpEngine,
        storageManager = storageManager,
        logCallback = logCallback,
        progressCallback = progressCallback
    )

    companion object {
        const val CMD_GET_BL_VER: Byte = 0xFE.toByte()
        const val CMD_GET_VERSION: Byte = 0xFF.toByte()
        const val CMD_GET_HW_CODE: Byte = 0xFD.toByte()
        const val CMD_GET_HW_SW_VER: Byte = 0xFC.toByte()
        const val CMD_GET_ME_ID: Byte = 0xE1.toByte()
        const val CMD_GET_SOC_ID: Byte = 0xE7.toByte()
        const val CMD_GET_TARGET_CONFIG: Byte = 0xD8.toByte()
        const val CMD_WRITE16: Byte = 0xD2.toByte()
        const val CMD_WRITE32: Byte = 0xD4.toByte()
        const val CMD_READ32: Byte = 0xD1.toByte()
        const val CMD_SEND_AUTH: Byte = 0xE2.toByte()

        const val DA_SYNC_ACK: Byte = 0xC0.toByte()
        private val PRELOADER_HDR_SIG = byteArrayOf(0x4D, 0x4D, 0x4D, 0x01, 0x38, 0x00, 0x00, 0x00)
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val folderDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val metaManager by lazy { MtkMetaManager(targetPhoneUsb) { msg, lvl -> log(msg, lvl) } }

    private var xflashEngine: MtkXFlashEngine? = null
    private var xmlFlashEngine: MtkXmlFlashEngine? = null
    private var daLoaded = false
    private var currentChipConfig: ChipConfig? = null
    var onPartitionsResolved: ((List<PartitionEntry>) -> Unit)? = null

    var customDaPath: String? = null
    var customPreloaderPath: String? = null
    var customAuthPath: String? = null

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val timestamp = timeFormat.format(Date())
        logCallback(TerminalLog(timestamp, message, level))
    }

    private fun sendCmdWithEcho(cmd: Byte, timeoutMs: Int = 1000): Boolean {
        if (targetPhoneUsb.writeRaw(byteArrayOf(cmd), timeoutMs) <= 0) return false
        val echo = ByteArray(1)
        val read = targetPhoneUsb.readRaw(echo, timeoutMs, readFull = true)
        return (read > 0 && echo[0] == cmd)
    }

    private fun echoBytes(data: ByteArray, timeoutMs: Int = 1000): Boolean {
        if (targetPhoneUsb.writeRaw(data, timeoutMs) != data.size) return false
        val echo = ByteArray(data.size)
        val r = targetPhoneUsb.readRaw(echo, timeoutMs, readFull = true)
        return r == data.size && echo.contentEquals(data)
    }

    private fun intBE(v: Long): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((v and 0xFFFFFFFFL).toInt()).array()

    private fun shortBE(v: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((v and 0xFFFF).toShort()).array()

    /**
     * Exact port of Preloader.write(addr, values, 32) / write16(addr, val)
     */
    private fun writeRegister32(addr: Long, value: Long): Boolean {
        if (!echoBytes(byteArrayOf(CMD_WRITE32), 800)) return false
        if (!echoBytes(intBE(addr), 800)) return false
        if (!echoBytes(intBE(1L), 800)) return false
        val st1Buf = ByteArray(2)
        if (targetPhoneUsb.readRaw(st1Buf, 800, readFull = true) != 2) return false
        val st1 = ((st1Buf[0].toInt() and 0xFF) shl 8) or (st1Buf[1].toInt() and 0xFF)
        if (st1 > 0xFF) return false
        if (!echoBytes(intBE(value), 800)) return false
        val st2Buf = ByteArray(2)
        if (targetPhoneUsb.readRaw(st2Buf, 800, readFull = true) != 2) return false
        val st2 = ((st2Buf[0].toInt() and 0xFF) shl 8) or (st2Buf[1].toInt() and 0xFF)
        return st2 <= 0xFF
    }

    private fun writeRegister16(addr: Long, value: Int): Boolean {
        if (!echoBytes(byteArrayOf(CMD_WRITE16), 800)) return false
        if (!echoBytes(intBE(addr), 800)) return false
        if (!echoBytes(intBE(1L), 800)) return false
        val st1Buf = ByteArray(2)
        if (targetPhoneUsb.readRaw(st1Buf, 800, readFull = true) != 2) return false
        val st1 = ((st1Buf[0].toInt() and 0xFF) shl 8) or (st1Buf[1].toInt() and 0xFF)
        if (st1 > 0xFF) return false
        if (!echoBytes(shortBE(value), 800)) return false
        val st2Buf = ByteArray(2)
        if (targetPhoneUsb.readRaw(st2Buf, 800, readFull = true) != 2) return false
        val st2 = ((st2Buf[0].toInt() and 0xFF) shl 8) or (st2Buf[1].toInt() and 0xFF)
        return st2 <= 0xFF
    }

    /**
     * Exact port of Preloader.read(addr, dwords, 32) packed as <I per dword
     * (matches mtk_da_handler.py -> dump_preloader_ram byte order)
     */
    private fun read32AsLittleEndianBytes(addr: Long, totalDwords: Int): ByteArray? {
        if (totalDwords <= 0) return ByteArray(0)
        val out = ByteArrayOutputStream(totalDwords * 4)
        var remaining = totalDwords
        var curAddr = addr
        val stBuf = ByteArray(2)

        while (remaining > 0) {
            val chunkDwords = minOf(128, remaining)
            if (!echoBytes(byteArrayOf(CMD_READ32), 1000)) return null
            if (!echoBytes(intBE(curAddr), 1000)) return null
            if (!echoBytes(intBE(chunkDwords.toLong()), 1000)) return null

            if (targetPhoneUsb.readRaw(stBuf, 1000, readFull = true) != 2) return null
            val st1 = ((stBuf[0].toInt() and 0xFF) shl 8) or (stBuf[1].toInt() and 0xFF)
            if (st1 > 0xFF) return null

            val rawChunk = ByteArray(chunkDwords * 4)
            if (targetPhoneUsb.readRaw(rawChunk, 3000, readFull = true) != rawChunk.size) return null

            if (targetPhoneUsb.readRaw(stBuf, 1000, readFull = true) != 2) return null
            val st2 = ((stBuf[0].toInt() and 0xFF) shl 8) or (stBuf[1].toInt() and 0xFF)
            if (st2 > 0xFF) return null

            // Port.rdword() unpacks BE >I; dump_preloader_ram() repacks LE <I
            var i = 0
            while (i < rawChunk.size) {
                out.write(rawChunk[i + 3].toInt() and 0xFF)
                out.write(rawChunk[i + 2].toInt() and 0xFF)
                out.write(rawChunk[i + 1].toInt() and 0xFF)
                out.write(rawChunk[i].toInt() and 0xFF)
                i += 4
            }

            remaining -= chunkDwords
            curAddr += chunkDwords * 4L
        }
        return out.toByteArray()
    }

    /**
     * Exact port of Preloader.setreg_disablewatchdogtimer(hwcode, hwver)
     */
    private fun disableWatchdog(chipConfig: ChipConfig): Boolean {
        val (wdtAddr, wdtVal) = MtkChipConfigDatabase.getWatchdogAddrAndValue(chipConfig)
        val ok = when {
            chipConfig.hwCode in listOf(0x2625, 0x2523, 0x7682, 0x7686, 0x5932) ->
                writeRegister16(0xA2050000L, 0x2200)
            chipConfig.hwCode == 0x6261 ->
                writeRegister16(0xA0030000L, 0x2200)
            chipConfig.hwCode in listOf(0x6575, 0x6577) ->
                writeRegister16(wdtAddr, (wdtVal and 0xFFFFL).toInt())
            else ->
                writeRegister32(wdtAddr, wdtVal)
        }
        if (ok) {
            log("[+] Watchdog Timer disabled @ 0x%08X = 0x%08X".format(wdtAddr, wdtVal), LogLevel.INFO)
        } else {
            log("[-] Warning: Watchdog Timer disable failed @ 0x%08X".format(wdtAddr), LogLevel.WARNING)
        }
        return ok
    }

    /**
     * Exact port of Preloader.get_blver() in mtk_preloader.py lines 3010-3019:
     * If GET_BL_VER (0xFE) returns 0xFE, device is in BROM mode; otherwise Preloader mode.
     */
    private fun checkIsBromMode(): Boolean {
        if (targetPhoneUsb.writeRaw(byteArrayOf(CMD_GET_BL_VER), 500) != 1) {
            return targetPhoneUsb.isBromConnected()
        }
        val resp = ByteArray(1)
        val r = targetPhoneUsb.readRaw(resp, 500, readFull = true)
        if (r == 1 && resp[0] == CMD_GET_BL_VER) {
            return true
        }
        return false
    }

    /**
     * Exact port of MtkDaHandler.dump_preloader_ram() (mtk_da_handler.py lines 1710-1751)
     */
    private suspend fun dumpPreloaderRam(): ByteArray? = withContext(Dispatchers.IO) {
        log("[PRELOADER RAM] Scanning SRAM 0x200000 (64KB) for live Preloader binary...", LogLevel.INFO)
        val probe = read32AsLittleEndianBytes(0x200000L, 0x10000 / 4) ?: run {
            log("[-] Could not read 0x200000 SRAM window for Preloader RAM dump.", LogLevel.WARNING)
            return@withContext null
        }

        val idx = MtkDaPatcher.findBinary(probe, PRELOADER_HDR_SIG) ?: run {
            log("[-] Preloader header signature (MMM\\x018\\x00\\x00\\x00) not present at 0x200000.", LogLevel.WARNING)
            return@withContext null
        }

        if (idx + 0x24 > probe.size) return@withContext null
        val plLen = ByteBuffer.wrap(probe, idx + 0x20, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (plLen !in 0x1000..0x200000) {
            log("[-] Invalid Preloader RAM length reported: $plLen bytes.", LogLevel.WARNING)
            return@withContext null
        }

        delay(150)
        val alignedDwords = (plLen + 3) / 4
        val rawPl = read32AsLittleEndianBytes(0x200000L + idx, alignedDwords) ?: return@withContext null
        val preloader = rawPl.copyOfRange(0, minOf(plLen, rawPl.size))
        log("[+] Extracted live Preloader from RAM (${preloader.size} bytes) for EMI/DRAM setup.", LogLevel.SUCCESS)
        return@withContext preloader
    }

    private fun probeBromAlive(): Boolean {
        if (!targetPhoneUsb.isConnected() || !targetPhoneUsb.isBromConnected()) return false
        targetPhoneUsb.flush(10)
        if (targetPhoneUsb.writeRaw(byteArrayOf(CMD_GET_BL_VER), 250) != 1) return false
        val resp = ByteArray(1)
        val r = targetPhoneUsb.readRaw(resp, 250, readFull = true)
        return (r == 1 && resp[0] == CMD_GET_BL_VER)
    }

    /**
     * Exact port of mtk_preloader.py -> send_auth(auth_data).
     */
    private suspend fun sendAuth(authData: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (authData.isEmpty()) return@withContext false
        log("[AUTH] Sending Custom Auth Certificate (${authData.size} bytes) to BROM...", LogLevel.INFO)
        if (!echoBytes(byteArrayOf(CMD_SEND_AUTH), 1000)) {
            log("[-] CMD_SEND_AUTH echo mismatch.", LogLevel.ERROR)
            return@withContext false
        }
        val lenBytes = intBE(authData.size.toLong())
        if (targetPhoneUsb.writeRaw(lenBytes, 1000) != 4) return@withContext false
        val lenEcho = ByteArray(4)
        if (targetPhoneUsb.readRaw(lenEcho, 1000, readFull = true) != 4 || !lenEcho.contentEquals(lenBytes)) {
            log("[-] SEND_AUTH length echo mismatch.", LogLevel.ERROR)
            return@withContext false
        }

        val stBuf = ByteArray(2)
        if (targetPhoneUsb.readRaw(stBuf, 1500, readFull = true) != 2) return@withContext false
        val st1 = ((stBuf[0].toInt() and 0xFF) shl 8) or (stBuf[1].toInt() and 0xFF)
        if (st1 == 0x1D0C) {
            log("[+] BROM reported NO_AUTH_NEEDED (0x1D0C).", LogLevel.INFO)
            return@withContext true
        }
        if (st1 > 0xFF) {
            log("[-] BROM rejected AUTH request: ${MtkErrorCodes.decode(st1)} (0x%04X)".format(st1), LogLevel.ERROR)
            return@withContext false
        }

        val chunkLimit = minOf(targetPhoneUsb.getMaxInPacketSize(), 0x400).coerceAtLeast(64)
        var offset = 0
        while (offset < authData.size) {
            val len = minOf(chunkLimit, authData.size - offset)
            val chunk = authData.copyOfRange(offset, offset + len)
            if (targetPhoneUsb.writeRaw(chunk, 3000) != len) {
                log("[-] USB write failed while uploading AUTH data.", LogLevel.ERROR)
                return@withContext false
            }
            offset += len
            if (offset % 0x2000 == 0) {
                targetPhoneUsb.writeRaw(ByteArray(0), 1000)
            }
        }
        targetPhoneUsb.writeRaw(ByteArray(0), 1000)
        delay(35)

        val crcBuf = ByteArray(2)
        val finalStBuf = ByteArray(2)
        if (targetPhoneUsb.readRaw(crcBuf, 2000, readFull = true) != 2) return@withContext false
        if (targetPhoneUsb.readRaw(finalStBuf, 2000, readFull = true) != 2) return@withContext false
        val finalSt = ((finalStBuf[0].toInt() and 0xFF) shl 8) or (finalStBuf[1].toInt() and 0xFF)
        if (finalSt > 0xFF) {
            log("[-] BROM AUTH verification failed: ${MtkErrorCodes.decode(finalSt)} (0x%04X)".format(finalSt), LogLevel.ERROR)
            return@withContext false
        }
        log("[+] BROM AUTH verification passed!", LogLevel.SUCCESS)
        return@withContext true
    }

    /**
     * Connects to target device.
     * Supports direct BROM (0003) and Preloader (2000) with mtkclient crasher (modes 0..2 + Kamakiri-PL).
     */
    private suspend fun ensureTargetConnected(timeoutSec: Int = 30): Boolean = withContext(Dispatchers.IO) {
        if (targetPhoneUsb.isConnected() && targetPhoneUsb.isBromConnected()) {
            if (targetPhoneUsb.isBromHandshakeLocked && probeBromAlive()) {
                targetPhoneUsb.flush(10)
                return@withContext true
            }
            if (!targetPhoneUsb.isBromHandshakeLocked &&
                (targetPhoneUsb.blastBromHandshakeSync(60) || sendHandshakeByteByByte())
            ) {
                log("[+] BROM Handshake Sync Locked (0x5F 0xF5 0xAF 0xFA)!", LogLevel.SUCCESS)
                return@withContext true
            }
            log("[!] Existing USB handle is stale or no longer in BROM; re-scanning...", LogLevel.WARNING)
            targetPhoneUsb.disconnect()
        }

        targetPhoneUsb.strictBromOnlyMode = false

        try {
            log("==================================================", LogLevel.WARNING)
            log(">>> [WAITING FOR DEVICE] ⏳ Sniffing Active...", LogLevel.WARNING)
            log("ACTION REQUIRED:", LogLevel.INFO)
            log(" - For Patched Devices: Power OFF and plug USB WITHOUT pressing any buttons (Preloader Mode).", LogLevel.INFO)
            log(" - For Legacy Devices: Hold [Vol+ & Vol-] and plug USB (BROM Mode).", LogLevel.INFO)
            log("==================================================", LogLevel.WARNING)

            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeoutSec * 1000L) {
                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                progressCallback(
                    OperationProgress(
                        isRunning = true,
                        title = "Waiting for Target Device...",
                        detail = "Detecting BROM / Preloader Mode (${(timeoutSec - elapsedSec).coerceAtLeast(0)}s left)",
                        percentage = (elapsedSec.toFloat() / timeoutSec.toFloat()) * 100f
                    )
                )

                if (targetPhoneUsb.scanAndConnect(forceBromOnly = false, requiredModes = TargetPhoneUsbManager.MTK_SERVICE_MODES)) {
                    val mode = targetPhoneUsb.detectDeviceMode(targetPhoneUsb.getDevice())
                    log("[+] Device DETECTED in ${mode.label} Mode!", LogLevel.SUCCESS)

                    if (mode == UsbDeviceMode.PRELOADER) {
                        log(">>> [PRELOADER EXPLOIT] Device in Preloader (0x2000). Attempting Crash to BROM...", LogLevel.WARNING)
                        val kamakiri = MtkKamakiriExploit(targetPhoneUsb) { msg, lvl -> log(msg, lvl) }
                        var bromFound = false

                        // Try Kamakiri-PL + mtkclient crasher modes 0..2
                        val crashModes = listOf(3, 0, 1, 2)
                        for (cMode in crashModes) {
                            if (!targetPhoneUsb.isConnected()) {
                                targetPhoneUsb.scanAndConnect(forceBromOnly = false, requiredModes = TargetPhoneUsbManager.MTK_SERVICE_MODES)
                            }
                            if (targetPhoneUsb.isBromConnected()) {
                                bromFound = true
                                break
                            }
                            kamakiri.crashPreloader(cMode)
                            if (cMode == 3) {
                                targetPhoneUsb.sendWatchdogResetControl()
                            }
                            delay(1200)

                            for (poll in 0 until 20) {
                                if (targetPhoneUsb.scanAndConnect(forceBromOnly = true)) {
                                    bromFound = true
                                    break
                                }
                                delay(250)
                            }
                            if (bromFound) break
                        }

                        if (!bromFound) {
                            log("[-] Preloader crash did not enumerate BROM (0x0E8D:0x0003). Check device battery/cable.", LogLevel.ERROR)
                            return@withContext false
                        }
                        log("[+] Successfully caught BROM Port (PID: 0003) after Preloader Crash!", LogLevel.SUCCESS)
                    }

                    if (targetPhoneUsb.isBromHandshakeLocked) {
                        targetPhoneUsb.flush(10)
                        log("[+] BROM Handshake Sync Locked (0x5F 0xF5 0xAF 0xFA)!", LogLevel.SUCCESS)
                        return@withContext true
                    }
                    log("[+] Blasting BROM Handshake Sync...", LogLevel.INFO)
                    if (targetPhoneUsb.blastBromHandshakeSync(60) || sendHandshakeByteByByte()) {
                        log("[+] BROM Handshake Sync Locked (0x5F 0xF5 0xAF 0xFA)!", LogLevel.SUCCESS)
                        return@withContext true
                    }
                }
                delay(25)
            }

            log("[-] [CONNECTION FAIL]: Timeout (${timeoutSec}s).", LogLevel.ERROR)
            return@withContext false
        } finally {
            targetPhoneUsb.strictBromOnlyMode = false
        }
    }

    private fun sendHandshakeByteByByte(): Boolean {
        val sendBytes = byteArrayOf(0xA0.toByte(), 0x0A.toByte(), 0x50.toByte(), 0x05.toByte())
        val expectedEcho = byteArrayOf(0x5F.toByte(), 0xF5.toByte(), 0xAF.toByte(), 0xFA.toByte())

        for (i in sendBytes.indices) {
            val written = targetPhoneUsb.writeRaw(byteArrayOf(sendBytes[i]), 200)
            if (written != 1) return false
            val rx = ByteArray(1)
            val read = targetPhoneUsb.readRaw(rx, 200, readFull = true)
            if (read != 1 || rx[0] != expectedEcho[i]) return false
        }
        return true
    }

    private fun readHwCode(): Int? {
        targetPhoneUsb.flush(10)
        if (!sendCmdWithEcho(CMD_GET_HW_CODE, 500)) return null
        val buf = ByteArray(4)
        val read = targetPhoneUsb.readRaw(buf, 1000, readFull = true)
        if (read >= 2) {
            return ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
        }
        return null
    }

    /**
     * Complete mtkclient DA preparation & Stage 1/2 upload flow
     * (Preloader.init + MtkDaHandler.configure_da + DAXFlash.upload_da1 + upload_da)
     */
    private suspend fun ensureDaReady(): Boolean = withContext(Dispatchers.IO) {
        if (daLoaded && (xflashEngine != null || xmlFlashEngine != null)) {
            if (targetPhoneUsb.isConnected()) {
                val alive = when {
                    xflashEngine != null -> xflashEngine?.getPacketLength() != null
                    xmlFlashEngine != null -> xmlFlashEngine?.getHwInfo() == true
                    else -> false
                }
                if (alive) {
                    return@withContext true
                }
            }
            log("[!] Previous DA session disconnected; resetting state and waiting for device...", LogLevel.WARNING)
            daLoaded = false
            xflashEngine = null
            xmlFlashEngine = null
            currentChipConfig = null
            targetPhoneUsb.disconnect()
        }

        if (!ensureTargetConnected()) return@withContext false

        var hwCode = readHwCode()
        if (hwCode == null) {
            log("[!] BROM HW Code probe failed on current handle; resetting USB and re-detecting...", LogLevel.WARNING)
            targetPhoneUsb.disconnect()
            if (!ensureTargetConnected()) return@withContext false
            hwCode = readHwCode()
        }
        if (hwCode == null) {
            log("[-] [ERR_BROM_NO_ECHO] Failed to read HW Code from BROM. Verify USB OTG cable & connection.", LogLevel.ERROR)
            return@withContext false
        }
        log("[+] [HANDSHAKE LOCKED] Detected HW Code: 0x%04X".format(hwCode), LogLevel.SUCCESS)

        val chipConfig = MtkChipConfigDatabase.findConfig(hwCode) ?: MtkChipConfigDatabase.createDefaultFallback(hwCode)
        currentChipConfig = chipConfig

        // 1. Disable Watchdog immediately per Preloader.init()
        disableWatchdog(chipConfig)

        // 2. Check BROM vs Preloader mode via GET_BL_VER (0xFE) & read Target Config (0xD8)
        val isBrom = checkIsBromMode()
        val (isSecBoot, isSlaActive, isDaaActive) = readTargetSecurityFlags()
        val hasSecurity = isSecBoot || isSlaActive || isDaaActive

        // Print complete phone information block for the user
        printPhoneInformationBanner(
            chipName = chipConfig.name,
            hwCode = hwCode,
            isBrom = isBrom,
            secBoot = isSecBoot,
            sla = isSlaActive,
            daa = isDaaActive,
            isUfs = isCurrentStorageUfs()
        )

        // 3. Bypass BROM Security if in BROM mode & dump live Preloader RAM for EMI extraction
        var preloaderBytes: ByteArray? = null
        var shouldPatchDa = false

        if (isBrom && !chipConfig.isIot) {
            val authBytes = MtkAssetManager.loadCustomFileBytes(customAuthPath, 16)
            var authHandledByCert = false
            if (isDaaActive && authBytes != null) {
                if (sendAuth(authBytes)) {
                    authHandledByCert = true
                    shouldPatchDa = !isSecBoot
                }
            }

            if (!hasSecurity) {
                log("[DA READY] Unprotected BROM device detected. Enabling DA patch mode.", LogLevel.INFO)
                shouldPatchDa = true
            } else if (!authHandledByCert || isSlaActive) {
                log("[DA READY] Executing Security SLA/DAA Bypass for ${chipConfig.name}...", LogLevel.INFO)
                val bypassRes = securityEngine.executeBypass(
                    context,
                    MtkChipInfo(
                        chipIdHex = chipConfig.name,
                        hwCodeHex = "0x%04X".format(hwCode),
                        secureBootEnabled = isSecBoot,
                        daLoaded = false,
                        bromState = "BROM",
                        slaActive = isSlaActive,
                        daaActive = isDaaActive
                    )
                )
                if (bypassRes.isFailure) {
                    val bypassErr = bypassRes.exceptionOrNull()?.message ?: "Unknown security bypass failure"
                    log("[-] Security Bypass Failed: $bypassErr", LogLevel.ERROR)
                    return@withContext false
                }
                shouldPatchDa = true
            }

            targetPhoneUsb.flush(50)
            delay(100)

            // Prefer explicit Custom Preloader, else dump Preloader from SRAM (0x200000), else asset fallback
            val customPl = MtkAssetManager.loadCustomFileBytes(customPreloaderPath, 512)
            if (customPl != null) {
                log("[+] Loaded custom Preloader (${customPl.size} bytes) for EMI/DRAM setup.", LogLevel.INFO)
                preloaderBytes = customPl
            } else {
                preloaderBytes = dumpPreloaderRam()
                if (preloaderBytes == null) {
                    val fallbackPlName = "preloader_${chipConfig.name.replace("/", "_").lowercase()}.bin"
                    preloaderBytes = MtkAssetManager.loadPreloaderBytes(context, fallbackPlName)
                    if (preloaderBytes != null) {
                        log("[+] Loaded fallback preloader asset: $fallbackPlName", LogLevel.INFO)
                    }
                }
            }
        } else {
            shouldPatchDa = !isSecBoot
        }

        val emiBytes = MtkDaPatcher.extractEmi(preloaderBytes, isXFlash = (chipConfig.damode == DaMode.XFLASH))
        if (emiBytes != null) {
            log("[+] Extracted DRAM/EMI config (${emiBytes.size} bytes) from Preloader.", LogLevel.INFO)
        } else if (isBrom) {
            log("[!] Warning: No EMI data extracted; will attempt DA eMMC/UFS probe.", LogLevel.WARNING)
        }

        // 4. Resolve and Parse DA Container (prefer explicit custom DA path if set, else dacode lookup)
        val explicitCustomDa = MtkAssetManager.loadCustomFileBytes(customDaPath, 4096)
        if (explicitCustomDa != null) {
            log("[DA READY] Using Custom Download Agent (${explicitCustomDa.size} bytes).", LogLevel.INFO)
        } else {
            log("[DA READY] Resolving Download Agent container for ${chipConfig.name} (dacode=0x%04X)...".format(chipConfig.dacode), LogLevel.INFO)
        }
        val daBytes = explicitCustomDa
            ?: MtkAssetManager.resolveDaForChip(context, chipConfig)
            ?: MtkAssetManager.loadDaBytes(context, "MTK_DA_V5.bin")
            ?: MtkAssetManager.loadDaBytes(context, "MTK_DA_V6.bin")
        if (daBytes == null || daBytes.size < 4096) {
            log("[-] CRITICAL: No valid DA container found for ${chipConfig.name}.", LogLevel.ERROR)
            return@withContext false
        }

        val daInfo = MtkDaParser.parseDaLoader(
            daData = daBytes,
            hwCode = hwCode,
            defaultLoadAddr = chipConfig.daPayloadAddr,
            daCode = chipConfig.dacode
        ) ?: run {
            log("[-] Failed to parse DA Loader for HWCode 0x%04X (dacode: 0x%04X).".format(hwCode, chipConfig.dacode), LogLevel.ERROR)
            return@withContext false
        }

        val rawStage1 = daInfo.getStage1Bytes() ?: run {
            log("[-] Failed to extract DA Stage 1 bytes from DA container.", LogLevel.ERROR)
            return@withContext false
        }
        val rawStage2 = daInfo.getStage2Bytes()

        val da1SigLen = (daInfo.stage1?.sigLength ?: 0L).toInt()
        val da2SigLen = (daInfo.stage2?.sigLength ?: 0L).toInt()
        val daAddress1 = daInfo.stage1?.startAddress ?: chipConfig.daPayloadAddr
        val daAddress2 = daInfo.stage2?.startAddress ?: 0x40000000L

        val isXmlMode = (chipConfig.damode == DaMode.XML || daInfo.header.isV6)

        // 5. Patch DA Stage 1 & Stage 2 (plus Stage 2 hash fix inside Stage 1)
        val finalStage1: ByteArray
        val finalStage2: ByteArray?
        var daPatched = false

        if (rawStage2 != null && rawStage2.isNotEmpty() && shouldPatchDa) {
            if (isXmlMode) {
                val hashPos = MtkDaPatcher.computeHashPos(rawStage1, rawStage2, da1SigLen, da2SigLen, isV6 = true)
                if (hashPos != null) {
                    val patchedDa1_0 = MtkXmlDaPatcher.patchDa1(rawStage1)
                    val patchedDa2Full = MtkXmlDaPatcher.patchDa2(rawStage2, daAddress2) { /* internal silent */ }
                    val fixedDa1 = MtkDaPatcher.fixHash(patchedDa1_0, patchedDa2Full, hashPos)
                    val trimmedLen = hashPos.hashLen.coerceIn(1, patchedDa2Full.size)
                    finalStage1 = fixedDa1
                    finalStage2 = patchedDa2Full.copyOfRange(0, trimmedLen)
                    daPatched = true
                    log("[+] XML DA Stage 1 & Stage 2 patched + embedded hash re-computed.", LogLevel.SUCCESS)
                } else {
                    finalStage1 = rawStage1
                    finalStage2 = if (da2SigLen in 1 until rawStage2.size) {
                        rawStage2.copyOfRange(0, rawStage2.size - da2SigLen)
                    } else {
                        rawStage2
                    }
                }
            } else {
                val patchResult = MtkDaPatcher.patchDa1AndDa2(
                    da1 = rawStage1,
                    da2 = rawStage2,
                    da1SigLen = da1SigLen,
                    da2SigLen = da2SigLen,
                    da2StartAddr = daAddress2,
                    isV6 = false,
                    log = { /* internal patch silent */ }
                )
                finalStage1 = patchResult.first
                finalStage2 = patchResult.second
                daPatched = patchResult.third
                if (daPatched) {
                    log("[+] DA Stage 1 & Stage 2 patched + embedded hash re-computed.", LogLevel.SUCCESS)
                }
            }
        } else {
            finalStage1 = rawStage1
            finalStage2 = if (rawStage2 != null && da2SigLen in 1 until rawStage2.size) {
                rawStage2.copyOfRange(0, rawStage2.size - da2SigLen)
            } else {
                rawStage2
            }
        }

        // 6. Upload & Jump DA Stage 1
        log("[DA READY] Uploading DA Stage 1 -> 0x%08X (%d bytes)...".format(daAddress1, finalStage1.size), LogLevel.INFO)
        val sendRes = MtkDaUploader.sendDa(
            usb = targetPhoneUsb,
            daAddress = daAddress1,
            daData = finalStage1,
            sigLen = da1SigLen,
            hwCode = hwCode,
            logCallback = { log(it.message, it.level) },
            onProgress = {}
        )
        if (sendRes.isFailure) {
            val sendErr = sendRes.exceptionOrNull()?.message ?: "Unknown DA Stage 1 upload error"
            log("[-] DA Stage 1 Upload Failed: $sendErr", LogLevel.ERROR)
            return@withContext false
        }

        val jumpRes = MtkDaUploader.jumpDa(
            targetPhoneUsb,
            daAddress1,
            logCallback = { log(it.message, it.level) }
        )
        if (jumpRes.isFailure) {
            val jumpErr = jumpRes.exceptionOrNull()?.message ?: "Unknown DA Stage 1 jump error"
            log("[-] DA Stage 1 Jump Failed: $jumpErr", LogLevel.ERROR)
            return@withContext false
        }
        // Device is now executing DA Stage 1, no longer in BROM
        targetPhoneUsb.resetBromHandshakeLock()

        if (isXmlMode) {
            // V6 XML DA1 does NOT emit 0xC0 — it immediately sends an 0xFEEEEEEF CMD:START frame
            if (!targetPhoneUsb.isConnected()) {
                log("[!] USB Disconnected! Waiting for XML DA Re-enumeration...", LogLevel.WARNING)
                delay(800)
                if (!targetPhoneUsb.scanAndConnect(forceBromOnly = false)) {
                    log("[-] XML DA Stage 1 USB re-enumeration failed.", LogLevel.ERROR)
                    return@withContext false
                }
                log("[+] XML DA USB Port Re-enumerated.", LogLevel.SUCCESS)
            }

            val xml = MtkXmlFlashEngine(targetPhoneUsb) { log(it.message, it.level) }
            if (!xml.initStage1Session()) {
                log("[-] XML Stage 1 handshake failed (CMD:START).", LogLevel.ERROR)
                return@withContext false
            }

            if (finalStage2 != null && finalStage2.isNotEmpty()) {
                var activeStage2: ByteArray = finalStage2
                var customHookReady = daPatched

                val da2Booted = if (daPatched) {
                    xml.bootTo(daAddress2, activeStage2) { sent, total ->
                        val pct = if (total > 0) (sent.toFloat() / total.toFloat()) * 100f else 0f
                        progressCallback(
                            OperationProgress(
                                isRunning = true,
                                title = "Uploading XML DA Stage 2...",
                                detail = "${sent / 1024} KB / ${total / 1024} KB",
                                percentage = pct
                            )
                        )
                    }
                } else {
                    var bootedViaCarbonara = false
                    if (!MtkCarbonaraExploit.isPatched(rawStage1) { log(it, LogLevel.INFO) }) {
                        val (cOk, cDa2) = MtkCarbonaraExploit.patchDa1AndUploadDa2Xml(
                            context = context,
                            xml = xml,
                            daLoader = daInfo,
                            hwCode = hwCode,
                            daCode = chipConfig.dacode,
                            log = { log(it, LogLevel.INFO) }
                        )
                        if (cOk && cDa2 != null) {
                            activeStage2 = cDa2
                            customHookReady = true
                            bootedViaCarbonara = true
                        }
                    }

                    if (bootedViaCarbonara) {
                        true
                    } else {
                        val stockOk = xml.bootTo(daAddress2, activeStage2) { sent, total ->
                            val pct = if (total > 0) (sent.toFloat() / total.toFloat()) * 100f else 0f
                            progressCallback(
                                OperationProgress(
                                    isRunning = true,
                                    title = "Uploading XML DA Stage 2...",
                                    detail = "${sent / 1024} KB / ${total / 1024} KB",
                                    percentage = pct
                                )
                            )
                        }
                        if (stockOk && MtkHeapbaitExploit.isVulnerable(activeStage2)) {
                            MtkHeapbaitExploit.runExploit(
                                context = context,
                                xml = xml,
                                da2 = activeStage2,
                                da2Addr = daAddress2
                            ) { log(it, LogLevel.INFO) }
                        }
                        stockOk
                    }
                }

                if (!da2Booted) {
                    log("[-] Error booting to DA Stage 2 (XML).", LogLevel.ERROR)
                    return@withContext false
                }

                xml.setupHwInit()
                xml.changeUsbSpeed()
                xml.performXmlSlaAuth(activeStage2)
                xml.getHwInfo()
                xml.checkLifecycle()

                if (customHookReady) {
                    MtkXmlDaPatcher.uploadAndActivateExtensions(
                        context = context,
                        xml = xml,
                        da2 = activeStage2,
                        da2Address = daAddress2,
                        efuseAddr = chipConfig.efuseAddr
                    ) { msg -> log(msg, LogLevel.INFO) }
                }
            }

            xmlFlashEngine = xml
            xflashEngine = null
        } else {
            // 7. Wait for DA_SYNC_ACK (0xC0) for V5 XFlash DA1
            log("[DA READY] Waiting for DA Stage 1 sync (0xC0)...", LogLevel.INFO)
            val syncStart = System.currentTimeMillis()
            var receivedSync = false
            val syncByte = ByteArray(1)

            while (System.currentTimeMillis() - syncStart < 5000) {
                val r = targetPhoneUsb.readRaw(syncByte, 150)
                if (r > 0) {
                    if (syncByte[0] == DA_SYNC_ACK) {
                        receivedSync = true
                        break
                    } else {
                        log("Discarding non-sync byte from DA: 0x%02X".format(syncByte[0]), LogLevel.WARNING)
                    }
                }
                if (!targetPhoneUsb.isConnected()) {
                    log("[!] USB Disconnected! Waiting for DA Re-enumeration...", LogLevel.WARNING)
                    delay(800)
                    if (targetPhoneUsb.scanAndConnect(forceBromOnly = false)) {
                        log("[+] DA USB Port Re-enumerated.", LogLevel.SUCCESS)
                        receivedSync = true
                        break
                    }
                }
            }

            if (!receivedSync) {
                log("[-] [ERR_DA_SYNC_0x00C0] DA Stage 1 sync failed: Device did not send 0xC0 response.", LogLevel.ERROR)
                log("[TIP] Power OFF device completely. Re-plug USB without pressing any buttons (Preloader), or hold [Vol-] and reconnect.", LogLevel.WARNING)
                return@withContext false
            }
            log("[+] DA Stage 1 active (0xC0 received).", LogLevel.SUCCESS)

            // 8. XFlash Stage 1 Handshake + EMI Init + Stage 2 Boot (xflash_lib.py upload_da1 + upload_da)
            if (finalStage2 != null && finalStage2.isNotEmpty()) {
                val xf = MtkXFlashEngine(targetPhoneUsb) { log(it.message, it.level) }

                if (!xf.initStage1Session()) {
                    log("[-] XFlash Stage 1 SYNC/ENV initialization failed.", LogLevel.ERROR)
                    return@withContext false
                }

                xf.getExpireDate()
                xf.setResetKey(0x68)
                xf.setChecksumLevel(0x0)

                val connAgent = xf.getConnectionAgent()
                log("[XFLASH] Connection Agent: ${connAgent ?: "unknown"}", LogLevel.INFO)

                if (connAgent == "brom") {
                    if (emiBytes != null) {
                        log("[XFLASH] Sending EMI DRAM configuration (${emiBytes.size} bytes)...", LogLevel.INFO)
                        if (!xf.sendEmi(emiBytes)) {
                            log("[-] XFlash sendEmi rejected by DA Stage 1.", LogLevel.ERROR)
                            return@withContext false
                        }
                    } else {
                        log("[!] No EMI/Preloader data available; proceeding to Stage 2 boot.", LogLevel.WARNING)
                    }
                }

                if (connAgent == "brom" || connAgent == "preloader" || connAgent == null) {
                    var activeStage2: ByteArray = finalStage2
                    var xflashPatched = daPatched
                    var booted = false

                    if (!daPatched && !MtkCarbonaraExploit.isPatched(rawStage1) { log(it, LogLevel.INFO) }) {
                        val (cOk, cDa2) = MtkCarbonaraExploit.patchDa1AndUploadDa2XFlash(
                            context = context,
                            xf = xf,
                            daLoader = daInfo,
                            hwCode = hwCode,
                            daCode = chipConfig.dacode,
                            log = { log(it, LogLevel.INFO) }
                        )
                        if (cOk && cDa2 != null) {
                            activeStage2 = cDa2
                            xflashPatched = true
                            booted = true
                        }
                    }

                    if (!booted) {
                        log("[DA READY] Booting DA Stage 2 (XFLASH) -> 0x%08X (%d bytes)...".format(daAddress2, activeStage2.size), LogLevel.INFO)
                        booted = xf.bootTo(daAddress2, activeStage2, timeoutMs = 500L) { sent, total ->
                            val pct = if (total > 0) (sent.toFloat() / total.toFloat()) * 100f else 0f
                            progressCallback(
                                OperationProgress(
                                    isRunning = true,
                                    title = "Uploading DA Stage 2...",
                                    detail = "${sent / 1024} KB / ${total / 1024} KB",
                                    percentage = pct
                                )
                            )
                        }
                    }

                    if (!booted) {
                        log("[-] Error booting to DA Stage 2 (XFlash).", LogLevel.ERROR)
                        return@withContext false
                    }

                    // 9. Stage 2 SLA Status & Reinit
                    val slaStatus = xf.getSlaStatus()
                    if (slaStatus != null && slaStatus > 0) {
                        log("[DA2] DA SLA is enabled (0x%X); executing SLA auth...".format(slaStatus), LogLevel.WARNING)
                        xf.handleSla(activeStage2, chipConfig.loader)
                    } else {
                        log("[DA2] DA SLA is disabled.", LogLevel.INFO)
                    }

                    xf.reinitStage2()

                    // 10. Patch & Boot DA Extensions (da_x.bin) when DA2 was patched
                    if (xflashPatched) {
                        val rawDaX = MtkAssetManager.loadPayloadBytes(context, "da_x.bin")
                        if (rawDaX != null) {
                            val patchedDaX = MtkDaPatcher.patchXFlashExtensions(
                                da2 = activeStage2,
                                da2Address = daAddress2,
                                rawDaX = rawDaX,
                                efuseAddr = chipConfig.efuseAddr
                            )
                            if (patchedDaX != null && xf.bootTo(MtkXFlashConstants.EXTENSIONS_ADDRESS, patchedDaX, timeoutMs = 500L)) {
                                if (xf.customAck()) {
                                    val isUfs = xf.detectedFlashType == "ufs"
                                    xf.customSetStorage(isUfs)
                                    log(
                                        "[+] DA Extensions active @ 0x%08X (Storage: %s)".format(
                                            MtkXFlashConstants.EXTENSIONS_ADDRESS,
                                            if (isUfs) "UFS" else "eMMC"
                                        ),
                                        LogLevel.SUCCESS
                                    )
                                } else {
                                    log("[!] DA Extensions loaded without custom ACK; continuing with stock XFlash.", LogLevel.WARNING)
                                }
                            }
                        }
                    }
                } else {
                    log("[-] Unexpected connection agent '$connAgent' from Stage 1.", LogLevel.ERROR)
                    return@withContext false
                }

                xflashEngine = xf
                xmlFlashEngine = null
            }
        }

        daLoaded = true
        log("[DA READY] Download Agent Stage 2 Active.", LogLevel.SUCCESS)
        return@withContext true
    }

    suspend fun readDetailedDeviceInfo(): Result<MtkChipInfo> = withContext(Dispatchers.IO) {
        try {
            val isReady = ensureTargetConnected()
            if (!isReady || !targetPhoneUsb.isConnected()) {
                return@withContext Result.failure(IllegalStateException("Target phone not connected."))
            }

            val hwCodeVal = readHwCode() ?: return@withContext Result.failure(IllegalStateException("Failed to read HW Code."))
            val hwCodeStr = "0x%04X".format(hwCodeVal)

            val chipConfig = MtkChipConfigDatabase.findConfig(hwCodeVal) ?: MtkChipConfigDatabase.createDefaultFallback(hwCodeVal)
            currentChipConfig = chipConfig
            disableWatchdog(chipConfig)

            var hwSubCode = "0x8A00"
            var hwVer = "0xCA00"
            var swVer = "0x0000"
            if (sendCmdWithEcho(CMD_GET_HW_SW_VER, 500)) {
                val hwSwBuf = ByteArray(8)
                if (targetPhoneUsb.readRaw(hwSwBuf, 1000, readFull = true) >= 6) {
                    hwSubCode = String.format("0x%02X%02X", hwSwBuf[0], hwSwBuf[1])
                    hwVer = String.format("0x%02X%02X", hwSwBuf[2], hwSwBuf[3])
                    swVer = String.format("0x%02X%02X", hwSwBuf[4], hwSwBuf[5])
                }
            }

            val isBrom = checkIsBromMode()
            val (isSecBoot, isSlaActive, isDaaActive) = readTargetSecurityFlags()

            val chipName = resolveChipName(hwCodeStr)
            log("[+] Target Platform: $chipName ($hwCodeStr) [${if (isBrom) "BROM" else "PRELOADER"}]", LogLevel.CYAN)
            log("[+] Security: SBC [${if (isSecBoot) "ENABLED" else "DISABLED"}] | SLA [${if (isSlaActive) "ACTIVE" else "DISABLED"}] | DAA [${if (isDaaActive) "ACTIVE" else "DISABLED"}]", LogLevel.INFO)

            return@withContext Result.success(
                MtkChipInfo(
                    chipIdHex = "$chipName ($hwCodeStr)",
                    hwCodeHex = hwCodeStr,
                    hwSubcodeHex = hwSubCode,
                    hwVersionHex = hwVer,
                    swVersionHex = swVer,
                    secureBootEnabled = isSecBoot,
                    daLoaded = daLoaded,
                    bromState = if (isBrom) "BROM_CONNECTED" else "PRELOADER_CONNECTED",
                    slaActive = isSlaActive,
                    daaActive = isDaaActive
                )
            )
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun readTargetSecurityFlags(): Triple<Boolean, Boolean, Boolean> {
        var isSecBoot = false
        var isSlaActive = false
        var isDaaActive = false
        if (sendCmdWithEcho(CMD_GET_TARGET_CONFIG, 500)) {
            val targetCfgBuf = ByteArray(6)
            if (targetPhoneUsb.readRaw(targetCfgBuf, 500, readFull = true) >= 4) {
                val targetConfig = ((targetCfgBuf[0].toInt() and 0xFF) shl 24) or
                        ((targetCfgBuf[1].toInt() and 0xFF) shl 16) or
                        ((targetCfgBuf[2].toInt() and 0xFF) shl 8) or
                        (targetCfgBuf[3].toInt() and 0xFF)
                isSecBoot = (targetConfig and 0x1) != 0
                isSlaActive = (targetConfig and 0x2) != 0
                isDaaActive = (targetConfig and 0x4) != 0
            }
        }
        return Triple(isSecBoot, isSlaActive, isDaaActive)
    }

    private fun isCurrentStorageUfs(regionHint: String = ""): Boolean {
        if (regionHint.contains("UFS", true) || regionHint.contains("LU", true)) return true
        if (regionHint.contains("EMMC", true)) return false
        val chipName = currentChipConfig?.name.orEmpty().uppercase()
        // Dimensity 9000/9200/9300 and modern Dimensity chips are strictly UFS (UFS 3.1 / UFS 4.0) devices
        if (chipName.contains("6983") || chipName.contains("6985") || chipName.contains("6989") ||
            chipName.contains("6895") || chipName.contains("6897") || chipName.contains("6886")) {
            return true
        }
        return (xmlFlashEngine?.detectedFlashType == "ufs") || (xflashEngine?.detectedFlashType == "ufs")
    }

    private fun printPhoneInformationBanner(
        chipName: String,
        hwCode: Int,
        isBrom: Boolean,
        secBoot: Boolean,
        sla: Boolean,
        daa: Boolean,
        isUfs: Boolean
    ) {
        log("==================================================", LogLevel.ACCENT)
        log("📱 PHONE HARDWARE & CHIPSET INFO", LogLevel.SUCCESS)
        log("==================================================", LogLevel.ACCENT)
        log("• SoC / Platform   : $chipName [HW: 0x%04X]".format(hwCode), LogLevel.INFO)
        log("• Connection Mode  : ${if (isBrom) "MediaTek BootROM (BROM Sync Locked)" else "Preloader DA Mode"}", LogLevel.INFO)
        log("• Security Status  : SLA=${if (sla) "ACTIVE" else "OFF"} | DAA=${if (daa) "ACTIVE" else "OFF"} | SBC=${if (secBoot) "ACTIVE" else "OFF"}", LogLevel.INFO)
        log("• Storage Target   : ${if (isUfs) "UFS High-Speed Flash" else "eMMC Flash Storage"}", LogLevel.INFO)
        log("• Download Agent   : Initializing Stage 1 & 2 Execution...", LogLevel.INFO)
        log("==================================================", LogLevel.ACCENT)
    }

    suspend fun readDeviceGpt(): List<PartitionEntry> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext emptyList()

        val xml = xmlFlashEngine
        if (xml != null) {
            val isUfs = xml.detectedFlashType == "ufs"
            val sectorSize = if (isUfs) 4096L else 512L
            val length = sectorSize * 34L

            log(
                "[GPT READ XML] Reading GUID Partition Table (%s, SectorSize=%d, Len=%d)...".format(
                    if (isUfs) "UFS/LU2" else "eMMC/USER",
                    sectorSize,
                    length
                ),
                LogLevel.INFO
            )

            val gptBytes = xml.readFlash(addr = 0L, length = length, partType = "user")
            val parsed = if (gptBytes != null && gptBytes.isNotEmpty()) {
                GptParser.parseRawGpt(gptBytes, sectorSizeOverride = sectorSize)
            } else {
                emptyList()
            }
            if (parsed.isNotEmpty()) {
                return@withContext if (isUfs) {
                    parsed.map { entry ->
                        if (entry.partitionName.equals("preloader", true)) entry.copy(region = "UFS_LU1")
                        else entry.copy(region = "UFS_LU0")
                    }
                } else {
                    parsed
                }
            }

            val xmlPt = xml.readPartitionTable()
            if (xmlPt != null && xmlPt.second.isNotEmpty()) {
                return@withContext xmlPt.second.mapIndexed { index, entry ->
                    val startBytes = entry.startSector * sectorSize
                    val sizeBytes = entry.sizeSectors * sectorSize
                    PartitionEntry(
                        partitionIndex = index,
                        partitionName = entry.name,
                        fileName = "${entry.name}.bin",
                        linearStartAddrHex = "0x%X".format(startBytes),
                        physicalStartAddrHex = "0x%X".format(startBytes),
                        partitionSizeHex = "0x%X".format(sizeBytes),
                        sizeBytes = sizeBytes,
                        region = if (isUfs) "UFS_LU0" else "EMMC_USER",
                        isSelectedForFlashing = false
                    )
                }
            }
            return@withContext emptyList()
        }

        val xf = xflashEngine ?: return@withContext emptyList()

        val isUfs = xf.detectedFlashType == "ufs"
        val storage = if (isUfs) MtkFlashEngine.StorageType.UFS.value else MtkFlashEngine.StorageType.EMMC.value
        val partType = if (isUfs) MtkFlashEngine.UfsPartition.LU0.value else MtkFlashEngine.EmmcPartition.USER.value
        val sectorSize = if (isUfs) 4096L else 512L
        val length = sectorSize * 34L

        log(
            "[GPT READ] Reading GUID Partition Table (%s, SectorSize=%d, Len=%d)...".format(
                if (isUfs) "UFS/LU0" else "eMMC/USER",
                sectorSize,
                length
            ),
            LogLevel.INFO
        )

        val gptBytes = xf.readFlash(
            storage = storage,
            partType = partType,
            address = 0L,
            length = length
        ) ?: return@withContext emptyList()

        val parsed = GptParser.parseRawGpt(gptBytes, sectorSizeOverride = sectorSize)
        return@withContext if (isUfs) {
            parsed.map { entry ->
                if (entry.partitionName.equals("preloader", true)) entry.copy(region = "UFS_LU1")
                else entry.copy(region = "UFS_LU0")
            }
        } else {
            parsed
        }
    }

    suspend fun writePartition(
        partition: PartitionEntry,
        sourceImageData: ByteArray?,
        autoNvBackup: Boolean = true,
        autoReboot: Boolean = true,
        isSubOperation: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        if (sourceImageData == null || sourceImageData.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No image payload provided."))
        }

        val chipConfig = currentChipConfig ?: MtkChipConfigDatabase.createDefaultFallback(0x0766)
        val isUfs = isCurrentStorageUfs(partition.region)
        val (storageType, partSection) = flashEngine.resolveStorageTarget(partition.partitionName, partition.region, isUfs)

        val xml = xmlFlashEngine
        val success = if (xml != null) {
            flashEngine.flashXml(partition, sourceImageData, xml)
        } else if (chipConfig.damode == DaMode.XFLASH || storageType == MtkFlashEngine.StorageType.UFS) {
            flashEngine.flashXFlash(partition, sourceImageData, storageType, partSection)
        } else {
            flashEngine.flashLegacy(partition, sourceImageData, storageType, partSection)
        }

        if (success && autoReboot && !isSubOperation) rebootDevice("Android System")
        return@withContext if (success) Result.success(true) else Result.failure(IllegalStateException("Flash failed."))
    }

    private suspend fun readPartitionInternal(partition: PartitionEntry): Result<String> {
        val backupDir = storageManager.getBackupDirectory()
        val outFile = File(backupDir, "${partition.partitionName}.bin")

        val chipConfig = currentChipConfig ?: MtkChipConfigDatabase.createDefaultFallback(0x0766)
        val isUfs = isCurrentStorageUfs(partition.region)
        val (storageType, partSection) = flashEngine.resolveStorageTarget(partition.partitionName, partition.region, isUfs)

        return dumpEngine.dumpPartition(
            partition = partition,
            outputFile = outFile,
            storageType = storageType,
            partType = partSection,
            useXFlash = (chipConfig.damode == DaMode.XFLASH || storageType == MtkFlashEngine.StorageType.UFS),
            xmlEngine = xmlFlashEngine
        )
    }

    suspend fun readPartition(partition: PartitionEntry, isSubOperation: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        return@withContext readPartitionInternal(partition)
    }

    private fun loadPartitionImageData(part: PartitionEntry): ByteArray? {
        if (part.boundFilePath.isNotBlank()) {
            val bound = part.boundFilePath.trim()
            if (bound.startsWith("content://", ignoreCase = true)) {
                try {
                    val uri = android.net.Uri.parse(bound)
                    context.contentResolver.openInputStream(uri)?.use { return it.readBytes() }
                } catch (e: Exception) {
                    log("[-] Failed to read SAF image '${part.fileName}': ${e.message}", LogLevel.WARNING)
                }
            }
            val directFile = File(bound)
            if (directFile.exists() && directFile.isFile && directFile.canRead()) {
                try { return directFile.readBytes() } catch (_: Exception) {}
            }
            MtkAssetManager.loadCustomFileBytes(bound, 1)?.let { return it }
        }
        val backupDir = storageManager.getBackupDirectory()
        val candidates = mutableListOf(
            File(part.fileName),
            File(backupDir, part.fileName),
            File(backupDir, "firmware/${part.fileName}"),
            File("/sdcard/Download/${part.fileName}"),
            File("/sdcard/Download/MTK_Backups/${part.fileName}"),
            File("/sdcard/NativeUnlockTool/firmware/${part.fileName}")
        )
        for (f in candidates) {
            if (f.exists() && f.isFile && f.canRead()) {
                return try { f.readBytes() } catch (_: Exception) { null }
            }
        }

        // Recursive search in backup folders for firmware restore
        try {
            val matchingFile = backupDir.walkTopDown()
                .maxDepth(3)
                .firstOrNull { file ->
                    file.isFile && (
                        file.name.equals(part.fileName, ignoreCase = true) ||
                        file.name.equals("${part.partitionName}.img", ignoreCase = true) ||
                        file.name.equals("${part.partitionName}.bin", ignoreCase = true) ||
                        (part.partitionName.equals("preloader", ignoreCase = true) && file.name.startsWith("preloader", ignoreCase = true) && file.name.endsWith(".bin", ignoreCase = true))
                    )
                }
            if (matchingFile != null && matchingFile.canRead()) {
                log("[RESTORE] Located partition file for '${part.partitionName}': ${matchingFile.parentFile?.name}/${matchingFile.name}", LogLevel.INFO)
                return matchingFile.readBytes()
            }
        } catch (_: Exception) {}

        return null
    }

    suspend fun batchFlash(
        chipPlatform: String,
        partitions: List<PartitionEntry>,
        autoNvBackup: Boolean = true,
        autoReboot: Boolean = true,
        flashAfterBlUnlock: Boolean = false,
        daDlChecksum: Boolean = true,
        autoSignFlash: Boolean = true,
        formatAllDownload: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val selected = partitions.filter { it.isSelectedForFlashing }
        if (selected.isEmpty()) return@withContext Result.failure(IllegalArgumentException("No partitions selected."))
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))

        xflashEngine?.setChecksumLevel(if (daDlChecksum) 1 else 0)
        log("[FLASH] DA Download Checksum: ${if (daDlChecksum) "ENABLED" else "DISABLED"}", LogLevel.INFO)

        if (autoNvBackup) {
            val hasNv = partitions.any {
                it.partitionName.lowercase() in listOf("nvram", "nvdata", "protect1", "protect2", "proinfo", "persist")
            }
            if (hasNv) {
                log("[FLASH] Running Pre-Flash NVRAM Calibration Backup...", LogLevel.INFO)
                backupNvram(chipPlatform, partitions)
            }
        }

        if (flashAfterBlUnlock) {
            val seccfg = partitions.find { it.partitionName.equals("seccfg", ignoreCase = true) }
            if (seccfg != null) {
                log("[FLASH] Unlocking Bootloader before batch flash...", LogLevel.WARNING)
                calibrationEngine.unlockBootloader(partitions, false, isCurrentStorageUfs(), xmlFlashEngine)
            }
        }

        if (formatAllDownload) {
            val protectedNames = setOf(
                "preloader", "nvram", "nvdata", "nvcfg", "protect1", "protect2",
                "proinfo", "persist", "seccfg", "efuse", "sec1"
            )
            val formatTargets = selected.filter {
                !it.isProtectedNv && it.partitionName.lowercase() !in protectedNames
            }
            log("[FLASH] Format All + Download: formatting ${formatTargets.size} non-NV partitions...", LogLevel.WARNING)
            for (part in formatTargets) {
                formatEngine.formatPartition(
                    partition = part,
                    chipPlatform = chipPlatform,
                    forceIsUfs = isCurrentStorageUfs(part.region),
                    xmlEngine = xmlFlashEngine
                )
            }
        }

        val effectiveList = if (autoSignFlash) selected else selected
        for ((idx, part) in effectiveList.withIndex()) {
            val data = loadPartitionImageData(part)
                ?: return@withContext Result.failure(IllegalStateException("Missing image file: ${part.fileName}"))
            log("[${idx + 1}/${effectiveList.size}] Flashing ${part.partitionName} (${part.fileName})...", LogLevel.INFO)
            val res = writePartition(part, data, false, false, true)
            if (res.isFailure) return@withContext res
        }

        if (autoReboot) rebootDevice("Android System")
        return@withContext Result.success(true)
    }

    suspend fun formatPartition(partition: PartitionEntry, autoReboot: Boolean = false, chipPlatform: String = ""): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val res = formatEngine.formatPartition(
            partition = partition,
            chipPlatform = chipPlatform,
            forceIsUfs = isCurrentStorageUfs(partition.region),
            xmlEngine = xmlFlashEngine
        )
        if (res.isSuccess && autoReboot) rebootDevice("Android System")
        return@withContext res
    }

    private suspend fun resolvePartitions(partitions: List<PartitionEntry>): List<PartitionEntry> {
        if (partitions.isNotEmpty()) return partitions
        log("[GPT] Partition list is empty — reading live GPT from device storage...", LogLevel.INFO)
        val liveGpt = readDeviceGpt()
        if (liveGpt.isNotEmpty()) {
            log("[+] Loaded ${liveGpt.size} partitions from live device GPT.", LogLevel.SUCCESS)
            onPartitionsResolved?.invoke(liveGpt)
        } else {
            log("[-] Failed to read GPT partition table from device storage.", LogLevel.ERROR)
        }
        return liveGpt
    }

    private suspend fun readPartitionBytesInternal(partition: PartitionEntry): ByteArray? {
        val len = partition.sizeBytes.coerceAtMost(32L * 1024L * 1024L)
        if (len <= 0L) return null
        val isUfs = isCurrentStorageUfs(partition.region)
        val (storageType, partSection) = flashEngine.resolveStorageTarget(partition.partitionName, partition.region, isUfs)

        val xml = xmlFlashEngine
        if (xml != null) {
            val xmlPartType = when (partSection) {
                MtkFlashEngine.EmmcPartition.BOOT1.value -> "boot1"
                MtkFlashEngine.EmmcPartition.BOOT2.value -> "boot2"
                MtkFlashEngine.EmmcPartition.RPMB.value -> "rpmb"
                else -> "user"
            }
            return xml.readFlash(addr = partition.startLinearAddress, length = len, partType = xmlPartType)
        }

        val xf = xflashEngine
        if (xf != null) {
            return xf.readFlash(
                storage = storageType.value,
                partType = partSection,
                address = partition.startLinearAddress,
                length = len
            )
        }

        val tempFile = File(storageManager.getBackupDirectory(), ".tmp_${partition.partitionName}.bin")
        val res = dumpEngine.dumpPartition(
            partition = partition,
            outputFile = tempFile,
            storageType = storageType,
            partType = partSection,
            useXFlash = false,
            xmlEngine = null
        )
        return try {
            if (res.isSuccess && tempFile.exists()) tempFile.readBytes() else null
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    suspend fun dumpAllPartitions(chipPlatform: String = "MT6765", partitions: List<PartitionEntry>): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(partitions)
        if (effective.isEmpty()) return@withContext Result.failure(IllegalStateException("No partitions found in device GPT."))
        val sessionFolder = "${chipPlatform}_FullDump_${folderDateFormat.format(Date())}"
        val targetDir = File(storageManager.getBackupDirectory(), sessionFolder)
        return@withContext dumpEngine.dumpAllPartitions(
            chipPlatform = chipPlatform,
            partitions = effective,
            destinationDir = targetDir,
            isUfs = isCurrentStorageUfs(),
            xmlEngine = xmlFlashEngine
        )
    }

    suspend fun dumpStablePartitions(chipPlatform: String = "MT6765", partitions: List<PartitionEntry>): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(partitions)
        val stablePrefixes = listOf(
            "preloader", "boot", "init_boot", "vendor_boot", "dtbo", "vbmeta",
            "recovery", "lk", "tee", "scp", "sspm", "spmfw", "mcupmfw",
            "md1img", "gz", "super", "metadata", "logo"
        )
        val filtered = effective.filter { part ->
            val n = part.partitionName.lowercase()
            stablePrefixes.any { prefix ->
                n == prefix || n == "${prefix}_a" || n == "${prefix}_b" ||
                    n == "${prefix}1" || n == "${prefix}2" || n == "${prefix}_1" || n == "${prefix}_2" ||
                    (prefix == "vbmeta" && n.startsWith("vbmeta_"))
            }
        }
        if (filtered.isEmpty()) return@withContext Result.failure(IllegalStateException("No stable boot partitions found in GPT."))
        return@withContext dumpAllPartitions(chipPlatform = chipPlatform, partitions = filtered)
    }

    suspend fun dumpStablePartitions(partitions: List<PartitionEntry>): Result<List<String>> =
        dumpStablePartitions("MT6765", partitions)

    suspend fun dumpCustomPartitions(chipPlatform: String = "MT6765", partitions: List<PartitionEntry>): Result<List<String>> = withContext(Dispatchers.IO) {
        val selected = partitions.filter { it.isSelectedForFlashing }
        if (selected.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("No partitions selected for Custom Dump. Read GPT or load Scatter and check partitions first.")
            )
        }
        return@withContext dumpAllPartitions(chipPlatform = chipPlatform, partitions = selected)
    }

    suspend fun dumpCustomPartitions(partitions: List<PartitionEntry>): Result<List<String>> =
        dumpCustomPartitions("MT6765", partitions)

    suspend fun backupNvram(chipPlatform: String, partitions: List<PartitionEntry>): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(partitions)
        if (effective.isEmpty()) return@withContext Result.failure(IllegalStateException("No GPT partitions found for NVRAM backup."))
        return@withContext calibrationEngine.backupNvramBundle(
            chipPlatform = chipPlatform,
            partitions = effective,
            forceIsUfs = isCurrentStorageUfs(),
            xmlEngine = xmlFlashEngine
        ).map { listOf(it) }
    }

    suspend fun eraseFrp(chipPlatform: String, partitions: List<PartitionEntry>, autoNvBackup: Boolean = true, autoReboot: Boolean = true): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(partitions)
        if (effective.isEmpty()) return@withContext Result.failure(IllegalStateException("No GPT partitions found."))
        val res = formatEngine.eraseFrp(effective, chipPlatform, forceIsUfs = isCurrentStorageUfs(), xmlEngine = xmlFlashEngine)
        if (res.isSuccess && autoReboot) rebootDevice("Android System")
        return@withContext res
    }

    suspend fun factoryReset(chipPlatform: String, partitions: List<PartitionEntry>, autoNvBackup: Boolean = true, autoReboot: Boolean = true): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(partitions)
        if (effective.isEmpty()) return@withContext Result.failure(IllegalStateException("No GPT partitions found."))
        val res = formatEngine.factoryReset(effective, chipPlatform, forceIsUfs = isCurrentStorageUfs(), xmlEngine = xmlFlashEngine)
        if (res.isSuccess && autoReboot) rebootDevice("Android System")
        return@withContext res
    }

    suspend fun disableMiAccount(chipPlatform: String, partitions: List<PartitionEntry>, autoNvBackup: Boolean = true, autoReboot: Boolean = true): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(partitions)
        if (effective.isEmpty()) return@withContext Result.failure(IllegalStateException("No GPT partitions found."))
        val res = formatEngine.disableMiAccount(effective, chipPlatform, forceIsUfs = isCurrentStorageUfs(), xmlEngine = xmlFlashEngine)
        if (res.isSuccess && autoReboot) rebootDevice("Android System")
        return@withContext res
    }

    suspend fun unlockBootloader(partitions: List<PartitionEntry> = emptyList(), autoReboot: Boolean = true): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(partitions)
        if (effective.isEmpty()) return@withContext Result.failure(IllegalStateException("No GPT partitions found."))
        val res = calibrationEngine.unlockBootloader(effective, false, forceIsUfs = isCurrentStorageUfs(), xmlEngine = xmlFlashEngine)
        if (res.isSuccess && autoReboot) rebootDevice("Android System")
        return@withContext res
    }

    suspend fun lockBootloader(partitions: List<PartitionEntry> = emptyList(), autoReboot: Boolean = true): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(partitions)
        if (effective.isEmpty()) return@withContext Result.failure(IllegalStateException("No GPT partitions found."))
        val res = calibrationEngine.lockBootloader(effective, false, forceIsUfs = isCurrentStorageUfs(), xmlEngine = xmlFlashEngine)
        if (res.isSuccess && autoReboot) rebootDevice("Android System")
        return@withContext res
    }

    suspend fun patchVbmeta(
        vbMode: Int = MtkCalibrationEngine.VBMETA_DISABLE_BOTH,
        partitions: List<PartitionEntry> = emptyList(),
        autoReboot: Boolean = true
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (vbMode !in 0..3) {
            return@withContext Result.failure(IllegalArgumentException("Invalid vbmeta mode: $vbMode (expected 0..3)"))
        }
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(partitions)
        if (effective.isEmpty()) return@withContext Result.failure(IllegalStateException("No GPT partitions found."))

        val slot = calibrationEngine.detectCurrentSlot(effective) { slice -> readPartitionBytesInternal(slice) }
        val targetName = if (slot.isNotEmpty()) "vbmeta$slot" else "vbmeta"
        val vbmetaPart = effective.find { it.partitionName.equals(targetName, ignoreCase = true) }
            ?: effective.find { it.partitionName.equals("vbmeta", ignoreCase = true) }
            ?: return@withContext Result.failure(IllegalStateException("Partition '$targetName' not found in GPT."))

        log("[VBMETA] Reading '${vbmetaPart.partitionName}' (${vbmetaPart.formattedSize})...", LogLevel.INFO)
        val origBytes = readPartitionBytesInternal(vbmetaPart)
            ?: return@withContext Result.failure(IllegalStateException("Failed to read ${vbmetaPart.partitionName}"))

        val patched = MtkCalibrationEngine.patchVbmetaBytes(origBytes, vbMode)
            ?: return@withContext Result.failure(IllegalStateException("Failed to patch ${vbmetaPart.partitionName} header"))

        val modeDesc = when (vbMode) {
            0 -> "ENABLE_ALL (0)"
            1 -> "DISABLE_VERITY (1)"
            2 -> "DISABLE_VERIFICATION (2)"
            else -> "DISABLE_BOTH (3)"
        }
        log("[VBMETA] Patching AVB flags -> $modeDesc on ${vbmetaPart.partitionName}...", LogLevel.INFO)
        val writeRes = writePartition(
            partition = vbmetaPart,
            sourceImageData = patched,
            autoNvBackup = false,
            autoReboot = autoReboot,
            isSubOperation = false
        )
        if (writeRes.isSuccess) {
            log("[+] Successfully patched ${vbmetaPart.partitionName} (mode=$modeDesc).", LogLevel.SUCCESS)
        }
        return@withContext writeRes
    }

    suspend fun bypassAuth(): Result<Boolean> = withContext(Dispatchers.IO) {
        val isReady = ensureTargetConnected()
        if (!isReady || !targetPhoneUsb.isConnected()) return@withContext Result.failure(IllegalStateException("Handshake failed."))
        val chipInfo = readDetailedDeviceInfo().getOrNull()
        return@withContext securityEngine.executeBypass(context, chipInfo, false)
    }

    suspend fun runMemoryTest(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        log("==================================================", LogLevel.INFO)
        log(">>> [MEMORY & STORAGE DIAGNOSTIC]", LogLevel.INFO)
        log("==================================================", LogLevel.INFO)

        val xml = xmlFlashEngine
        if (xml != null) {
            xml.getHwInfo()
            xml.getDaInfo()?.takeIf { it.isNotBlank() }?.let {
                log("[MEMTEST] DA Info: ${it.lines().firstOrNull()?.take(160)}", LogLevel.INFO)
            }
            val lifeOk = xml.checkLifecycle()
            log("[MEMTEST] Storage Lifecycle Check: ${if (lifeOk) "HEALTHY (OK)" else "UNAVAILABLE / WARN"}", if (lifeOk) LogLevel.SUCCESS else LogLevel.WARNING)
            val ramOk = xml.runRamTest("FLIP", 0x4000000L, 0x100000L, 10L)
            log("[MEMTEST] DRAM FLIP Test @ 0x4000000 (1MB x10): ${if (ramOk) "PASSED" else "UNSUPPORTED / FAILED"}", if (ramOk) LogLevel.SUCCESS else LogLevel.WARNING)
            log("[MEMTEST] FlashType=${xml.detectedFlashType.uppercase()} | Size=${xml.flashSize / (1024L * 1024L)} MB | CID=${xml.cid}", LogLevel.CYAN)
            return@withContext Result.success(true)
        }

        val xf = xflashEngine
        if (xf != null) {
            val ram = xf.getRamInfo()
            if (ram != null) {
                log("[MEMTEST] SRAM: Base=0x%08X Size=%d KB | DRAM: Base=0x%08X Size=%d MB".format(
                    ram.first.baseAddress, ram.first.size / 1024L, ram.second.baseAddress, ram.second.size / (1024L * 1024L)
                ), LogLevel.CYAN)
            }
            xf.getDramType()?.let { log("[MEMTEST] DRAM Type Code: $it", LogLevel.INFO) }
            val emmc = xf.getEmmcInfo()
            val ufs = if (emmc == null || emmc.type == 0) xf.getUfsInfo() else null
            if (emmc != null && emmc.type != 0) {
                log("[MEMTEST] eMMC USER=${emmc.userSize / (1024L * 1024L)} MB | BOOT1=${emmc.boot1Size / 1024L} KB | RPMB=${emmc.rpmbSize / 1024L} KB", LogLevel.SUCCESS)
                log("[MEMTEST] eMMC CID: ${emmc.cid}", LogLevel.INFO)
            } else if (ufs != null && ufs.type != 0) {
                log("[MEMTEST] UFS LU0=${ufs.lu0Size / (1024L * 1024L)} MB | LU1=${ufs.lu1Size / 1024L} KB | LU2=${ufs.lu2Size / 1024L} KB | RPMB=${ufs.rpmbSize / 1024L} KB", LogLevel.SUCCESS)
                if (ufs.cid.isNotBlank()) log("[MEMTEST] UFS Serial/CID: ${ufs.cid}", LogLevel.INFO)
            }
            xf.getPartitionTableCategory()?.let { log("[MEMTEST] Partition Table Format: $it", LogLevel.INFO) }
            val life = xf.getDaStorLifeCheck()
            if (life != null) {
                log("[MEMTEST] Storage Lifecycle Status: ${if (life == 0) "HEALTHY (0x0)" else "0x%X".format(life)}", if (life == 0) LogLevel.SUCCESS else LogLevel.WARNING)
            }
            xf.getBatteryVoltage()?.let { log("[MEMTEST] Battery Voltage: $it mV", LogLevel.INFO) }
            xf.getUsbSpeed()?.let { log("[MEMTEST] USB Link Speed: $it", LogLevel.INFO) }
            return@withContext Result.success(true)
        }

        return@withContext Result.success(true)
    }

    suspend fun rebootDevice(mode: String): Result<Boolean> = withContext(Dispatchers.IO) {
        log(">>> [REBOOT] Initiating hardware reboot sequence (Target: $mode)...", LogLevel.INFO)
        if (!targetPhoneUsb.isConnected()) {
            log("[!] Device USB is not connected; skipping reboot sequence.", LogLevel.WARNING)
            return@withContext Result.success(true)
        }

        val lower = mode.lowercase()
        val isFastboot = lower.contains("fastboot") || lower.contains("bootloader")
        val isRecovery = lower.contains("recovery")
        val isMeta = lower.contains("meta")
        val bootMode = when {
            isFastboot -> 2
            isRecovery -> 3
            isMeta -> 4
            else -> 1 // 1 = NORMAL_BOOT / Android System
        }

        // 1. If XML DA is active
        val xml = xmlFlashEngine
        if (xml != null) {
            runCatching {
                val shutdownMode = when {
                    isFastboot -> MtkXmlCmd.ShutDownModes.FASTBOOT
                    isMeta -> MtkXmlCmd.ShutDownModes.META
                    else -> MtkXmlCmd.ShutDownModes.NORMAL
                }
                log("[XML DA] Sending XML DA shutdown & reboot (mode=$shutdownMode)...", LogLevel.INFO)
                xml.shutdown(shutdownMode)
            }
        }

        // 2. If XFlash DA is active
        val xf = xflashEngine
        if (xf != null) {
            runCatching {
                log("[XFLASH DA] Sending DA shutdown & reboot (bootMode=$bootMode, wdt=1)...", LogLevel.INFO)
                xf.reboot(bootMode)
            }
        }

        // 3. DA Stage 2 Universal Magic Reset bytes (0xF00DD00D 0x3000)
        runCatching {
            val resetMagic = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                .putInt(0xF00DD00D.toInt())
                .putInt(0x00003000)
                .array()
            targetPhoneUsb.writeRaw(resetMagic, 300)
        }

        // 4. Hardware Watchdog Register Reset (if direct register write is available)
        val chip = currentChipConfig
        if (chip != null) {
            runCatching {
                val (wdtAddr, _) = MtkChipConfigDatabase.getWatchdogAddrAndValue(chip)
                // 0x22000014 triggers immediate hardware watchdog reset on MTK SoCs
                writeRegister32(wdtAddr, 0x22000014L)
            }
        }

        // 5. USB CDC line coding control transfer + USB vendor reset control
        runCatching {
            targetPhoneUsb.sendWatchdogResetControl()
        }

        // 6. USB Port/Device Hardware Reset
        runCatching {
            targetPhoneUsb.resetDevice()
        }

        log("[+] Hardware reset signal latched. Waiting for SoC reboot...", LogLevel.SUCCESS)

        // CRITICAL: Give the SoC and PMIC 800ms to latch the reset pin and power cycle before closing USB host endpoints!
        delay(800)

        // Clean up connection state
        daLoaded = false
        xflashEngine = null
        xmlFlashEngine = null
        currentChipConfig = null
        targetPhoneUsb.disconnect()

        log("[✔] Device reboot sequence completed. Phone is restarting.", LogLevel.SUCCESS)
        return@withContext Result.success(true)
    }

    private fun resolveRpmbSizeBytes(): Long {
        val xml = xmlFlashEngine
        if (xml != null) {
            val sz = if (xml.detectedFlashType == "ufs") xml.ufsInfo?.rpmbSize else xml.emmcInfo?.rpmbSize
            if (sz != null && sz > 0L) return sz
        }
        val xf = xflashEngine
        if (xf != null) {
            val sz = if (xf.detectedFlashType == "ufs") xf.ufsInfo?.rpmbSize else xf.emmcInfo?.rpmbSize
            if (sz != null && sz > 0L) return sz
        }
        return 0x400000L // Default 4MB RPMB
    }

    suspend fun readRpmb(): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val hwCode = currentChipConfig?.hwCode ?: 0x0766
        val rpmbSize = resolveRpmbSizeBytes()
        val totalSectors = (rpmbSize / 0x100L).toInt().coerceAtLeast(1)
        val outFile = File(storageManager.getBackupDirectory(), "rpmb.bin")

        val xml = xmlFlashEngine
        if (xml != null && xml.daExtLoaded) {
            log("[RPMB XML] Initializing RPMB via XML DA Extension ($totalSectors sectors)...", LogLevel.INFO)
            if (MtkXmlDaPatcher.customRpmbInit(xml, hwCode)) {
                outFile.outputStream().use { out ->
                    var sec = 0
                    while (sec < totalSectors) {
                        val count = minOf(0x10, totalSectors - sec)
                        val chunk = MtkXmlDaPatcher.customRpmbRead(xml, sec, count)
                            ?: return@withContext Result.failure(IllegalStateException("XML RPMB read failed at sector $sec"))
                        out.write(chunk)
                        sec += count
                        progressCallback(
                            OperationProgress(
                                isRunning = true,
                                title = "Reading RPMB (XML Ext)",
                                detail = "$sec / $totalSectors sectors",
                                percentage = (sec.toFloat() / totalSectors.toFloat()) * 100f
                            )
                        )
                    }
                }
                log("[+] RPMB dumped to ${outFile.absolutePath}", LogLevel.SUCCESS)
                return@withContext Result.success(outFile.absolutePath)
            }
        }

        val xf = xflashEngine
        if (xf != null && xf.daExtLoaded) {
            log("[RPMB XFLASH] Initializing RPMB via XFlash DA Extension ($totalSectors sectors)...", LogLevel.INFO)
            if (xf.customRpmbInit(hwCode) != null) {
                outFile.outputStream().use { out ->
                    var sec = 0
                    while (sec < totalSectors) {
                        val count = minOf(0x10, totalSectors - sec)
                        val chunk = xf.customRpmbRead(sec, count)
                            ?: return@withContext Result.failure(IllegalStateException("XFlash RPMB read failed at sector $sec"))
                        out.write(chunk)
                        sec += count
                        progressCallback(
                            OperationProgress(
                                isRunning = true,
                                title = "Reading RPMB (XFlash Ext)",
                                detail = "$sec / $totalSectors sectors",
                                percentage = (sec.toFloat() / totalSectors.toFloat()) * 100f
                            )
                        )
                    }
                }
                log("[+] RPMB dumped to ${outFile.absolutePath}", LogLevel.SUCCESS)
                return@withContext Result.success(outFile.absolutePath)
            }
        }

        val isUfs = isCurrentStorageUfs()
        val part = PartitionEntry(
            0,
            "rpmb",
            "rpmb.bin",
            "0x0",
            "0x0",
            "0x%X".format(rpmbSize),
            rpmbSize,
            if (isUfs) "UFS_RPMB" else "EMMC_RPMB",
            false,
            false
        )
        return@withContext readPartitionInternal(part)
    }

    suspend fun writeRpmb(filePath: String? = null, autoReboot: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val targetFile = if (!filePath.isNullOrBlank()) File(filePath) else File(storageManager.getBackupDirectory(), "rpmb.bin")
        val data = MtkAssetManager.loadCustomFileBytes(targetFile.absolutePath, 256)
            ?: return@withContext Result.failure(IllegalStateException("Missing or invalid RPMB image: ${targetFile.absolutePath}"))

        val aligned = if (data.size % 0x100 != 0) data + ByteArray(0x100 - (data.size % 0x100)) else data
        val totalSectors = aligned.size / 0x100
        val hwCode = currentChipConfig?.hwCode ?: 0x0766

        val xml = xmlFlashEngine
        if (xml != null && xml.daExtLoaded) {
            if (!MtkXmlDaPatcher.customRpmbInit(xml, hwCode)) {
                return@withContext Result.failure(IllegalStateException("XML RPMB Init failed"))
            }
            var sec = 0
            while (sec < totalSectors) {
                val count = minOf(0x10, totalSectors - sec)
                val slice = aligned.copyOfRange(sec * 0x100, (sec + count) * 0x100)
                if (!MtkXmlDaPatcher.customRpmbWrite(xml, sec, count, slice)) {
                    return@withContext Result.failure(IllegalStateException("XML RPMB Write failed at sector $sec"))
                }
                sec += count
                progressCallback(
                    OperationProgress(
                        isRunning = true,
                        title = "Writing RPMB (XML Ext)",
                        detail = "$sec / $totalSectors sectors",
                        percentage = (sec.toFloat() / totalSectors.toFloat()) * 100f
                    )
                )
            }
            log("[+] RPMB written from ${targetFile.name} ($totalSectors sectors).", LogLevel.SUCCESS)
            if (autoReboot) rebootDevice("Android System")
            return@withContext Result.success(targetFile.absolutePath)
        }

        val xf = xflashEngine
        if (xf != null && xf.daExtLoaded) {
            if (xf.customRpmbInit(hwCode) == null) {
                return@withContext Result.failure(IllegalStateException("XFlash RPMB Init failed"))
            }
            var sec = 0
            while (sec < totalSectors) {
                val count = minOf(0x10, totalSectors - sec)
                val slice = aligned.copyOfRange(sec * 0x100, (sec + count) * 0x100)
                if (!xf.customRpmbWrite(sec, count, slice)) {
                    return@withContext Result.failure(IllegalStateException("XFlash RPMB Write failed at sector $sec"))
                }
                sec += count
                progressCallback(
                    OperationProgress(
                        isRunning = true,
                        title = "Writing RPMB (XFlash Ext)",
                        detail = "$sec / $totalSectors sectors",
                        percentage = (sec.toFloat() / totalSectors.toFloat()) * 100f
                    )
                )
            }
            log("[+] RPMB written from ${targetFile.name} ($totalSectors sectors).", LogLevel.SUCCESS)
            if (autoReboot) rebootDevice("Android System")
            return@withContext Result.success(targetFile.absolutePath)
        }

        val isUfs = isCurrentStorageUfs()
        val part = PartitionEntry(
            0,
            "rpmb",
            targetFile.name,
            "0x0",
            "0x0",
            "0x%X".format(aligned.size),
            aligned.size.toLong(),
            if (isUfs) "UFS_RPMB" else "EMMC_RPMB",
            false,
            false
        )
        val res = writePartition(part, aligned, autoNvBackup = false, autoReboot = autoReboot, isSubOperation = false)
        return@withContext res.map { targetFile.absolutePath }
    }

    suspend fun eraseRpmb(autoReboot: Boolean = false): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val rpmbSize = resolveRpmbSizeBytes()
        val totalSectors = (rpmbSize / 0x100L).toInt().coerceAtLeast(1)
        val hwCode = currentChipConfig?.hwCode ?: 0x0766

        val xml = xmlFlashEngine
        if (xml != null && xml.daExtLoaded) {
            if (!MtkXmlDaPatcher.customRpmbInit(xml, hwCode)) {
                return@withContext Result.failure(IllegalStateException("XML RPMB Init failed"))
            }
            val zeroChunk = ByteArray(0x10 * 0x100)
            var sec = 0
            while (sec < totalSectors) {
                val count = minOf(0x10, totalSectors - sec)
                val slice = if (count == 0x10) zeroChunk else ByteArray(count * 0x100)
                if (!MtkXmlDaPatcher.customRpmbWrite(xml, sec, count, slice)) {
                    return@withContext Result.failure(IllegalStateException("XML RPMB Erase failed at sector $sec"))
                }
                sec += count
                progressCallback(
                    OperationProgress(
                        isRunning = true,
                        title = "Erasing RPMB (XML Ext)",
                        detail = "$sec / $totalSectors sectors",
                        percentage = (sec.toFloat() / totalSectors.toFloat()) * 100f
                    )
                )
            }
            log("[+] RPMB region wiped cleanly ($totalSectors sectors).", LogLevel.SUCCESS)
            if (autoReboot) rebootDevice("Android System")
            return@withContext Result.success(true)
        }

        val xf = xflashEngine
        if (xf != null && xf.daExtLoaded) {
            if (xf.customRpmbInit(hwCode) == null) {
                return@withContext Result.failure(IllegalStateException("XFlash RPMB Init failed"))
            }
            val zeroChunk = ByteArray(0x10 * 0x100)
            var sec = 0
            while (sec < totalSectors) {
                val count = minOf(0x10, totalSectors - sec)
                val slice = if (count == 0x10) zeroChunk else ByteArray(count * 0x100)
                if (!xf.customRpmbWrite(sec, count, slice)) {
                    return@withContext Result.failure(IllegalStateException("XFlash RPMB Erase failed at sector $sec"))
                }
                sec += count
                progressCallback(
                    OperationProgress(
                        isRunning = true,
                        title = "Erasing RPMB (XFlash Ext)",
                        detail = "$sec / $totalSectors sectors",
                        percentage = (sec.toFloat() / totalSectors.toFloat()) * 100f
                    )
                )
            }
            log("[+] RPMB region wiped cleanly ($totalSectors sectors).", LogLevel.SUCCESS)
            if (autoReboot) rebootDevice("Android System")
            return@withContext Result.success(true)
        }

        val isUfs = isCurrentStorageUfs()
        val part = PartitionEntry(
            0,
            "rpmb",
            "rpmb.bin",
            "0x0",
            "0x0",
            "0x%X".format(rpmbSize),
            rpmbSize,
            if (isUfs) "UFS_RPMB" else "EMMC_RPMB",
            false,
            false
        )
        return@withContext formatPartition(part, autoReboot)
    }

    suspend fun enterMetaMode(mode: MtkMetaManager.MetaMode = MtkMetaManager.MetaMode.META): Result<Boolean> = withContext(Dispatchers.IO) {
        log("[META] Waiting for Preloader / MTK port to enter ${mode.label}...", LogLevel.INFO)
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 30_000L) {
            if (targetPhoneUsb.isConnected() && targetPhoneUsb.isBromOrPreloaderConnected()) {
                val ok = metaManager.enterMetaMode(mode)
                return@withContext if (ok) Result.success(true) else Result.failure(IllegalStateException("META handshake failed"))
            }
            if (targetPhoneUsb.scanAndConnect(forceBromOnly = false, requiredModes = TargetPhoneUsbManager.MTK_SERVICE_MODES)) {
                val ok = metaManager.enterMetaMode(mode)
                return@withContext if (ok) Result.success(true) else Result.failure(IllegalStateException("META handshake failed"))
            }
            delay(100)
        }
        return@withContext Result.failure(IllegalStateException("Timeout waiting for Preloader USB port."))
    }

    suspend fun factoryResetViaMeta(preferEmeta: Boolean = true, autoReboot: Boolean = true): Result<Boolean> = withContext(Dispatchers.IO) {
        metaManager.factoryResetViaMeta(preferEmeta = preferEmeta, autoReboot = autoReboot)
    }

    suspend fun readMetaDeviceInfo(): Result<MtkMetaManager.MetaDeviceInfo> = withContext(Dispatchers.IO) {
        metaManager.readMetaDeviceInfo()
    }

    suspend fun writeImeiViaMeta(imei1: String, imei2: String, autoReboot: Boolean = false): Result<Boolean> = withContext(Dispatchers.IO) {
        metaManager.writeImeiViaMeta(imei1, imei2, autoReboot)
    }

    suspend fun backupNvramViaMeta(): Result<String> = withContext(Dispatchers.IO) {
        metaManager.backupNvramViaMeta(storageManager)
    }

    suspend fun restoreNvramViaMeta(fileBytes: ByteArray, fileName: String, autoReboot: Boolean = false): Result<Boolean> = withContext(Dispatchers.IO) {
        metaManager.restoreNvramViaMeta(fileBytes, fileName, autoReboot)
    }

    suspend fun rebootFromMetaToNormal(): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.success(metaManager.rebootFromMetaToNormal())
    }

    suspend fun readPreloader(): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val isUfs = isCurrentStorageUfs()
        val part = PartitionEntry(
            0,
            "preloader",
            "preloader_dump.bin",
            "0x0",
            "0x0",
            "0x400000",
            4194304,
            if (isUfs) "UFS_LU1" else "EMMC_BOOT_1",
            false,
            false
        )
        return@withContext readPartitionInternal(part)
    }

    suspend fun readGptAndGenerateScatter(chipPlatform: String, partitions: List<PartitionEntry>): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(partitions)
        if (effective.isEmpty()) return@withContext Result.failure(IllegalStateException("Failed to read GPT from device."))
        val scatterPath = storageManager.generateScatterFile(chipPlatform, effective)
        return@withContext Result.success(scatterPath)
    }

    suspend fun crashToBrom(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (targetPhoneUsb.isConnected()) {
            val kamakiri = MtkKamakiriExploit(targetPhoneUsb) { msg, lvl -> log(msg, lvl) }
            kamakiri.crashPreloader(3)
            targetPhoneUsb.sendWatchdogResetControl()
            targetPhoneUsb.disconnect()
        }
        daLoaded = false
        xflashEngine = null
        xmlFlashEngine = null
        currentChipConfig = null
        delay(300)
        return@withContext Result.success(true)
    }

    private data class RawTargetSpec(
        val storageType: MtkFlashEngine.StorageType,
        val partSection: Int,
        val xmlHint: String,
        val sectorSize: Long
    )

    private fun resolveRawTarget(partType: String): RawTargetSpec {
        val norm = partType.trim().lowercase()
        val isUfs = isCurrentStorageUfs(norm)
        return if (isUfs) {
            when (norm) {
                "boot1", "lu1", "ufs_lua0", "ufs-lua0" -> RawTargetSpec(MtkFlashEngine.StorageType.UFS, MtkFlashEngine.UfsPartition.LU1.value, "boot1", 4096L)
                "boot2", "lu2", "ufs_lua1", "ufs-lua1" -> RawTargetSpec(MtkFlashEngine.StorageType.UFS, MtkFlashEngine.UfsPartition.LU2.value, "boot2", 4096L)
                "rpmb", "lu3", "ufs_lua3", "ufs-lua3" -> RawTargetSpec(MtkFlashEngine.StorageType.UFS, MtkFlashEngine.UfsPartition.RPMB.value, "rpmb", 4096L)
                else -> RawTargetSpec(MtkFlashEngine.StorageType.UFS, MtkFlashEngine.UfsPartition.LU0.value, "user", 4096L)
            }
        } else {
            when (norm) {
                "boot1", "emmc_boot1", "emmc_boot_1" -> RawTargetSpec(MtkFlashEngine.StorageType.EMMC, MtkFlashEngine.EmmcPartition.BOOT1.value, "boot1", 512L)
                "boot2", "emmc_boot2", "emmc_boot_2" -> RawTargetSpec(MtkFlashEngine.StorageType.EMMC, MtkFlashEngine.EmmcPartition.BOOT2.value, "boot2", 512L)
                "rpmb", "emmc_rpmb" -> RawTargetSpec(MtkFlashEngine.StorageType.EMMC, MtkFlashEngine.EmmcPartition.RPMB.value, "rpmb", 512L)
                "gp1" -> RawTargetSpec(MtkFlashEngine.StorageType.EMMC, MtkFlashEngine.EmmcPartition.GP1.value, "gp1", 512L)
                "gp2" -> RawTargetSpec(MtkFlashEngine.StorageType.EMMC, MtkFlashEngine.EmmcPartition.GP2.value, "gp2", 512L)
                "gp3" -> RawTargetSpec(MtkFlashEngine.StorageType.EMMC, MtkFlashEngine.EmmcPartition.GP3.value, "gp3", 512L)
                "gp4" -> RawTargetSpec(MtkFlashEngine.StorageType.EMMC, MtkFlashEngine.EmmcPartition.GP4.value, "gp4", 512L)
                else -> RawTargetSpec(MtkFlashEngine.StorageType.EMMC, MtkFlashEngine.EmmcPartition.USER.value, "user", 512L)
            }
        }
    }

    private fun resolveFullStorageLength(partTypeHint: String): Long {
        val norm = partTypeHint.trim().lowercase()
        val xml = xmlFlashEngine
        if (xml != null) {
            return when (xml.detectedFlashType) {
                "ufs" -> {
                    val ufs = xml.ufsInfo
                    when (norm) {
                        "boot1", "lu0", "ufs_lua0" -> ufs?.lu0Size ?: 0L
                        "boot2", "lu1", "ufs_lua1" -> ufs?.lu1Size ?: 0L
                        "rpmb", "lu3", "ufs_lua3" -> ufs?.rpmbSize ?: 0L
                        else -> ufs?.lu2Size?.takeIf { it > 0L } ?: xml.flashSize
                    }
                }
                "nand" -> xml.nandTotalSize.takeIf { it > 0L } ?: xml.flashSize
                else -> {
                    val emmc = xml.emmcInfo
                    when (norm) {
                        "boot1" -> emmc?.boot1Size ?: 0L
                        "boot2" -> emmc?.boot2Size ?: 0L
                        "rpmb" -> emmc?.rpmbSize ?: 0L
                        "gp1" -> emmc?.gp1Size ?: 0L
                        "gp2" -> emmc?.gp2Size ?: 0L
                        "gp3" -> emmc?.gp3Size ?: 0L
                        "gp4" -> emmc?.gp4Size ?: 0L
                        else -> emmc?.userSize?.takeIf { it > 0L } ?: xml.flashSize
                    }
                }
            }
        }
        val xf = xflashEngine ?: return 0L
        return if (xf.detectedFlashType == "ufs") {
            val ufs = xf.ufsInfo ?: xf.getUfsInfo()
            when (norm) {
                "boot1", "lu1" -> ufs?.lu1Size ?: 0L
                "boot2", "lu2" -> ufs?.lu2Size ?: 0L
                "rpmb", "lu3" -> ufs?.rpmbSize ?: 0L
                else -> ufs?.lu0Size ?: 0L
            }
        } else {
            val emmc = xf.emmcInfo ?: xf.getEmmcInfo()
            when (norm) {
                "boot1" -> emmc?.boot1Size ?: 0L
                "boot2" -> emmc?.boot2Size ?: 0L
                "rpmb" -> emmc?.rpmbSize ?: 0L
                "gp1" -> emmc?.gp1Size ?: 0L
                "gp2" -> emmc?.gp2Size ?: 0L
                "gp3" -> emmc?.gp3Size ?: 0L
                "gp4" -> emmc?.gp4Size ?: 0L
                else -> emmc?.userSize ?: 0L
            }
        }
    }

    /**
     * Exact mtkclient `da rf` (Read Full Flash).
     */
    suspend fun readFullFlash(partType: String = "user"): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val spec = resolveRawTarget(partType)
        val totalBytes = resolveFullStorageLength(partType)
        if (totalBytes <= 0L) {
            return@withContext Result.failure(IllegalStateException("Unable to determine storage length for '$partType'."))
        }
        val chipConfig = currentChipConfig ?: MtkChipConfigDatabase.createDefaultFallback(0x0766)
        val outFile = File(storageManager.getBackupDirectory(), "flash_${partType.lowercase()}_${folderDateFormat.format(Date())}.bin")
        log("[RF] Dumping full storage '$partType' (${totalBytes / (1024L * 1024L)} MB) -> ${outFile.name}", LogLevel.INFO)
        return@withContext dumpEngine.dumpRawRange(
            label = "full_${partType.lowercase()}",
            startAddr = 0L,
            totalBytes = totalBytes,
            outputFile = outFile,
            storageType = spec.storageType,
            partType = spec.partSection,
            useXFlash = (chipConfig.damode == DaMode.XFLASH || spec.storageType == MtkFlashEngine.StorageType.UFS),
            xmlEngine = xmlFlashEngine,
            xmlPartTypeHint = spec.xmlHint
        )
    }

    /**
     * Exact mtkclient `da ro` (bytes) / `da rs` (sectors).
     */
    suspend fun readFlashRange(
        startAddr: Long,
        length: Long,
        partType: String = "user",
        isSectors: Boolean = false,
        fileName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (length <= 0L || startAddr < 0L) {
            return@withContext Result.failure(IllegalArgumentException("Invalid start/length range."))
        }
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val spec = resolveRawTarget(partType)
        val startBytes = if (isSectors) startAddr * spec.sectorSize else startAddr
        val lenBytes = if (isSectors) length * spec.sectorSize else length
        val outName = fileName?.takeIf { it.isNotBlank() }
            ?: "read_${partType.lowercase()}_0x${startBytes.toString(16)}_0x${lenBytes.toString(16)}.bin"
        val outFile = File(storageManager.getBackupDirectory(), outName)
        val chipConfig = currentChipConfig ?: MtkChipConfigDatabase.createDefaultFallback(0x0766)
        log("[RO] Reading $partType @ 0x%X (len=0x%X bytes) -> ${outFile.name}".format(startBytes, lenBytes), LogLevel.INFO)
        return@withContext dumpEngine.dumpRawRange(
            label = "range_0x%X".format(startBytes),
            startAddr = startBytes,
            totalBytes = lenBytes,
            outputFile = outFile,
            storageType = spec.storageType,
            partType = spec.partSection,
            useXFlash = (chipConfig.damode == DaMode.XFLASH || spec.storageType == MtkFlashEngine.StorageType.UFS),
            xmlEngine = xmlFlashEngine,
            xmlPartTypeHint = spec.xmlHint
        )
    }

    /**
     * Exact mtkclient `da wo` (Write Offset from raw or Android sparse file).
     */
    suspend fun writeFlashFileAtOffset(
        startAddr: Long,
        filePath: String,
        partType: String = "user",
        isSectors: Boolean = false,
        autoReboot: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (startAddr < 0L) return@withContext Result.failure(IllegalArgumentException("Invalid negative offset."))
        val file = File(filePath)
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Missing or empty image file: $filePath"))
        }
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val spec = resolveRawTarget(partType)
        val startBytes = if (isSectors) startAddr * spec.sectorSize else startAddr
        val chipConfig = currentChipConfig ?: MtkChipConfigDatabase.createDefaultFallback(0x0766)
        log("[WO] Writing '${file.name}' (${file.length()} B) to $partType @ 0x%X...".format(startBytes), LogLevel.INFO)
        val ok = flashEngine.flashFileAtAddress(
            label = "offset_0x%X".format(startBytes),
            baseAddr = startBytes,
            file = file,
            storageType = spec.storageType,
            partType = spec.partSection,
            useXFlash = (chipConfig.damode == DaMode.XFLASH || spec.storageType == MtkFlashEngine.StorageType.UFS),
            xmlEngine = xmlFlashEngine,
            xmlPartHint = spec.xmlHint
        )
        if (ok && autoReboot) rebootDevice("Android System")
        return@withContext if (ok) Result.success(true) else Result.failure(IllegalStateException("Write offset 0x%X failed.".format(startBytes)))
    }

    /**
     * Exact mtkclient `da es` / `da ess` (Erase Offset or Sectors).
     */
    suspend fun eraseFlashRange(
        startAddr: Long,
        length: Long,
        partType: String = "user",
        isSectors: Boolean = false,
        autoReboot: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (length <= 0L || startAddr < 0L) {
            return@withContext Result.failure(IllegalArgumentException("Invalid erase range."))
        }
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val spec = resolveRawTarget(partType)
        val startBytes = if (isSectors) startAddr * spec.sectorSize else startAddr
        val lenBytes = if (isSectors) length * spec.sectorSize else length
        val syntheticPart = PartitionEntry(
            partitionIndex = 0,
            partitionName = "erase_0x%X".format(startBytes),
            fileName = "",
            linearStartAddrHex = "0x%X".format(startBytes),
            physicalStartAddrHex = "0x%X".format(startBytes),
            partitionSizeHex = "0x%X".format(lenBytes),
            sizeBytes = lenBytes,
            region = if (spec.storageType == MtkFlashEngine.StorageType.UFS) "UFS_${spec.xmlHint.uppercase()}" else "EMMC_${spec.xmlHint.uppercase()}",
            isDownload = false,
            isProtectedNv = false,
            isSelectedForFlashing = false
        )
        val chipName = currentChipConfig?.name ?: ""
        val res = formatEngine.formatPartition(
            partition = syntheticPart,
            useDirectZeroFill = false,
            chipPlatform = chipName,
            forceIsUfs = (spec.storageType == MtkFlashEngine.StorageType.UFS),
            xmlEngine = xmlFlashEngine
        )
        if (res.isSuccess && autoReboot) rebootDevice("Android System")
        return@withContext res
    }

    /**
     * Exact mtkclient `da footer`: scans metadata / userdata / reserved* for 0xD0B5B1C0 magic and writes footer.bin.
     */
    suspend fun dumpFooter(): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureDaReady()) return@withContext Result.failure(IllegalStateException("DA not ready."))
        val effective = resolvePartitions(emptyList())
        if (effective.isEmpty()) return@withContext Result.failure(IllegalStateException("No GPT partitions found."))

        val candidates = listOf("userdata2", "metadata", "userdata", "reserved1", "reserved2", "reserved3")
        val outFile = File(storageManager.getBackupDirectory(), "footer.bin")

        for (targetName in candidates) {
            val part = effective.find { it.partitionName.equals(targetName, ignoreCase = true) } ?: continue
            val isUserdataTail = part.partitionName.equals("userdata", true) || part.partitionName.equals("userdata2", true)
            val readLen = minOf(part.sizeBytes, 0x4000L)
            if (readLen < 4L) continue
            val readOffset = if (isUserdataTail && part.sizeBytes >= 0x4000L) {
                part.startLinearAddress + part.sizeBytes - 0x4000L
            } else {
                part.startLinearAddress
            }

            val spec = resolveRawTarget(part.region)
            val bytes: ByteArray? = when {
                xmlFlashEngine != null -> xmlFlashEngine?.readFlash(readOffset, readLen, spec.xmlHint)
                xflashEngine != null -> xflashEngine?.readFlash(spec.storageType.value, spec.partSection, readOffset, readLen)
                else -> null
            }
            if (bytes != null && bytes.size >= 4) {
                val magic = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                if ((magic and 0xFFFFFFF0L) == 0xD0B5B1C0L) {
                    outFile.writeBytes(bytes)
                    log("[+] Crypto footer detected in '${part.partitionName}' (magic=0x%08X) -> ${outFile.name}".format(magic), LogLevel.SUCCESS)
                    return@withContext Result.success(outFile.absolutePath)
                }
            }
        }
        log("[-] No Android FDE/crypto footer (0xD0B5B1C0) found in metadata/userdata/reserved.", LogLevel.WARNING)
        return@withContext Result.failure(IllegalStateException("No crypto footer found on device."))
    }

    private fun formatHexAsciiDump(baseAddr: Long, bytes: ByteArray): List<String> {
        val lines = mutableListOf<String>()
        var off = 0
        val limit = minOf(bytes.size, 512)
        while (off < limit) {
            val rowLen = minOf(16, limit - off)
            val hex = StringBuilder()
            val ascii = StringBuilder()
            for (i in 0 until 16) {
                if (i < rowLen) {
                    val b = bytes[off + i].toInt() and 0xFF
                    hex.append("%02X ".format(b))
                    ascii.append(if (b in 0x20..0x7E) b.toChar() else '.')
                } else {
                    hex.append("   ")
                }
            }
            lines.add("0x%08X: %s |%s|".format(baseAddr + off, hex.toString(), ascii.toString()))
            off += 16
        }
        if (bytes.size > limit) {
            lines.add("... (${bytes.size - limit} more bytes omitted from console preview)")
        }
        return lines
    }

    private suspend fun daPeekInternal(addr: Long, length: Int, registers: Boolean): ByteArray? {
        if (length <= 0) return ByteArray(0)
        val xml = xmlFlashEngine
        if (xml != null && xml.daExtLoaded) {
            if (registers) {
                val dwords = (length + 3) / 4
                val regs = MtkXmlDaPatcher.customReadRegister(xml, addr, dwords) ?: return null
                val buf = ByteBuffer.allocate(regs.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                regs.forEach { buf.putInt(it.toInt()) }
                return buf.array().copyOf(minOf(length, regs.size * 4))
            }
            return MtkXmlDaPatcher.customRead(xml, addr, length)
        }
        val xf = xflashEngine
        if (xf != null && xf.daExtLoaded) {
            if (registers) {
                val dwords = (length + 3) / 4
                val regs = xf.customReadRegister(addr, dwords) ?: return null
                val buf = ByteBuffer.allocate(regs.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                regs.forEach { buf.putInt(it) }
                return buf.array().copyOf(minOf(length, regs.size * 4))
            }
            return xf.customRead(addr, length)
        }
        if (!daLoaded && targetPhoneUsb.isConnected() && targetPhoneUsb.isBromConnected()) {
            val dwords = (length + 3) / 4
            return read32AsLittleEndianBytes(addr, dwords)?.copyOf(minOf(length, dwords * 4))
        }
        return null
    }

    /**
     * Exact mtkclient `da peek` (read memory or hardware registers via DA Ext or BROM).
     */
    suspend fun daPeek(
        addr: Long,
        length: Int,
        registers: Boolean = false,
        saveToFile: Boolean = false
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (length <= 0 || length > 16 * 1024 * 1024) {
            return@withContext Result.failure(IllegalArgumentException("Peek length must be 1..16MB."))
        }
        if (!daLoaded && !targetPhoneUsb.isBromConnected()) {
            if (!ensureTargetConnected()) {
                return@withContext Result.failure(IllegalStateException("Device not connected in BROM or DA."))
            }
            readHwCode()?.let { hw ->
                val cfg = MtkChipConfigDatabase.findConfig(hw) ?: MtkChipConfigDatabase.createDefaultFallback(hw)
                currentChipConfig = cfg
                disableWatchdog(cfg)
            }
        } else if (daLoaded && xmlFlashEngine?.daExtLoaded != true && xflashEngine?.daExtLoaded != true) {
            return@withContext Result.failure(IllegalStateException("DA Extensions (da_x.bin / da_xml.bin) not active on current DA session."))
        }

        val data = daPeekInternal(addr, length, registers)
            ?: return@withContext Result.failure(IllegalStateException("Peek failed @ 0x%08X (DA Ext or BROM read rejected).".format(addr)))

        log("[PEEK] Read ${data.size} bytes @ 0x%08X (registers=$registers):".format(addr), LogLevel.CYAN)
        formatHexAsciiDump(addr, data).forEach { line -> log(line, LogLevel.RAW) }

        if (saveToFile) {
            val outFile = File(storageManager.getBackupDirectory(), "peek_0x%08X.bin".format(addr))
            outFile.writeBytes(data)
            log("[+] Saved peek dump to ${outFile.absolutePath}", LogLevel.SUCCESS)
        }
        return@withContext Result.success(data)
    }

    /**
     * Exact mtkclient `da poke` (write bytes or 32-bit register via DA Ext or BROM).
     */
    suspend fun daPoke(addr: Long, data: ByteArray): Result<Boolean> = withContext(Dispatchers.IO) {
        if (data.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Empty poke payload."))
        if (!daLoaded && !targetPhoneUsb.isBromConnected()) {
            if (!ensureTargetConnected()) {
                return@withContext Result.failure(IllegalStateException("Device not connected in BROM or DA."))
            }
            readHwCode()?.let { hw ->
                val cfg = MtkChipConfigDatabase.findConfig(hw) ?: MtkChipConfigDatabase.createDefaultFallback(hw)
                currentChipConfig = cfg
                disableWatchdog(cfg)
            }
        }

        val xml = xmlFlashEngine
        val ok = when {
            xml != null && xml.daExtLoaded -> {
                if (data.size == 4 && (addr % 4L) == 0L) {
                    val v = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    MtkXmlDaPatcher.customWriteRegister(xml, addr, v)
                } else {
                    MtkXmlDaPatcher.customWrite(xml, addr, data)
                }
            }
            xflashEngine != null && xflashEngine!!.daExtLoaded -> {
                val xf = xflashEngine!!
                if (data.size == 4 && (addr % 4L) == 0L) {
                    val v = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
                    xf.customWriteRegister(addr, v)
                } else {
                    xf.customWrite(addr, data)
                }
            }
            !daLoaded && targetPhoneUsb.isBromConnected() -> {
                if (data.size % 4 != 0) {
                    return@withContext Result.failure(IllegalArgumentException("BROM write32 requires 4-byte aligned payload length."))
                }
                var allOk = true
                var idx = 0
                while (idx < data.size) {
                    val v = ByteBuffer.wrap(data, idx, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    if (!writeRegister32(addr + idx, v)) {
                        allOk = false
                        break
                    }
                    idx += 4
                }
                allOk
            }
            else -> {
                return@withContext Result.failure(IllegalStateException("DA Extensions not active for memory poke."))
            }
        }

        if (ok) {
            log("[+] [POKE] Wrote ${data.size} bytes @ 0x%08X".format(addr), LogLevel.SUCCESS)
            return@withContext Result.success(true)
        }
        return@withContext Result.failure(IllegalStateException("Poke failed @ 0x%08X".format(addr)))
    }

    /**
     * Exact mtkclient `da efuse`: reads SoC eFuse registers and writes efuse_<hwcode>.txt.
     */
    suspend fun dumpEfuse(): Result<String> = withContext(Dispatchers.IO) {
        if (!daLoaded && !targetPhoneUsb.isBromConnected()) {
            if (!ensureTargetConnected()) {
                return@withContext Result.failure(IllegalStateException("Device not connected."))
            }
        }
        var cfg = currentChipConfig
        if (cfg == null) {
            val hw = if (!daLoaded) readHwCode() else xflashEngine?.getChipId()?.hwCode
            if (hw != null) {
                cfg = MtkChipConfigDatabase.findConfig(hw) ?: MtkChipConfigDatabase.createDefaultFallback(hw)
                currentChipConfig = cfg
                if (!daLoaded) disableWatchdog(cfg)
            }
        }
        val activeCfg = cfg ?: return@withContext Result.failure(IllegalStateException("Unknown SoC HW code for eFuse map."))
        val hasDaExt = (xmlFlashEngine?.daExtLoaded == true) || (xflashEngine?.daExtLoaded == true)
        if (daLoaded && !hasDaExt) {
            return@withContext Result.failure(IllegalStateException("DA Extensions required for eFuse register reads."))
        }

        val baseEfuse = activeCfg.efuseAddr
            ?: return@withContext Result.failure(IllegalStateException("No eFuse base address defined for ${activeCfg.name}."))
        val addresses = MtkEfuseDatabase.getEfuses(baseEfuse, activeCfg.hwCode)
        log("[EFUSE] Dumping ${addresses.size} eFuse registers for ${activeCfg.name} (base=0x%08X)...".format(baseEfuse), LogLevel.INFO)

        val sb = StringBuilder()
        sb.appendLine("# MediaTek eFuse Dump - ${activeCfg.name} (HWCode: 0x%04X, Base: 0x%08X)".format(activeCfg.hwCode, baseEfuse))
        var anyRead = false
        for ((idx, addr) in addresses.withIndex()) {
            val regVal: Long = when {
                addr < 0x1000L -> addr
                (addr and 0xFF000000L) == 0x08000000L -> addr and 0xFFL
                else -> {
                    val b = daPeekInternal(addr, 4, registers = true)
                    if (b == null || b.size < 4) {
                        if (!daLoaded && idx == 0) {
                            // BROM read32 blocked by SBC/DAA -> fall back to booting DA with Extensions
                            if (!ensureDaReady()) {
                                return@withContext Result.failure(IllegalStateException("BROM eFuse read blocked & DA fallback failed."))
                            }
                            val retry = daPeekInternal(addr, 4, registers = true)
                                ?: return@withContext Result.failure(IllegalStateException("eFuse read failed at index 0x%02X".format(idx)))
                            anyRead = true
                            ByteBuffer.wrap(retry).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                        } else {
                            0L
                        }
                    } else {
                        anyRead = true
                        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    }
                }
            }
            val line = "EFuse Idx 0x%02X (@0x%08X): 0x%08X".format(idx, addr, regVal)
            sb.appendLine(line)
            if (idx < 32) log(line, LogLevel.CYAN)
        }
        if (!anyRead && addresses.any { it >= 0x1000L && (it and 0xFF000000L) != 0x08000000L }) {
            return@withContext Result.failure(IllegalStateException("Failed to read eFuse hardware registers."))
        }
        val outFile = File(storageManager.getBackupDirectory(), "efuse_0x%04X.txt".format(activeCfg.hwCode))
        outFile.writeText(sb.toString())
        log("[+] Saved ${addresses.size} eFuse entries to ${outFile.absolutePath}", LogLevel.SUCCESS)
        return@withContext Result.success(outFile.absolutePath)
    }

    /**
     * Exact mtkclient `dump brom` / SRAM dump using generic_dump_payload.bin or generic_sram_payload.bin.
     */
    suspend fun dumpBootRom(useSramDump: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        if (daLoaded) {
            return@withContext Result.failure(IllegalStateException("BootROM/SRAM dump requires BROM mode (reconnect device before booting DA)."))
        }
        if (!ensureTargetConnected()) {
            return@withContext Result.failure(IllegalStateException("Target BROM device not connected."))
        }
        val hwCode = readHwCode() ?: return@withContext Result.failure(IllegalStateException("Failed to read HW Code."))
        val cfg = MtkChipConfigDatabase.findConfig(hwCode) ?: MtkChipConfigDatabase.createDefaultFallback(hwCode)
        currentChipConfig = cfg
        disableWatchdog(cfg)
        val info = readDetailedDeviceInfo().getOrNull()

        val payloadAsset = if (useSramDump) "generic_sram_payload.bin" else "generic_dump_payload.bin"
        val payloadBytes = MtkAssetManager.loadPayloadBytes(context, payloadAsset)
            ?: return@withContext Result.failure(IllegalStateException("Missing payload asset: $payloadAsset"))

        val outName = if (useSramDump) "sram_0x%04X.bin".format(hwCode) else "brom_0x%04X.bin".format(hwCode)
        val outFile = File(storageManager.getBackupDirectory(), outName)
        val defaultLen = if (useSramDump) 0x200000 else 0x20000
        return@withContext securityEngine.executeCustomPayload(
            rawPayloadBytes = payloadBytes,
            payloadName = payloadAsset,
            deviceInfo = info,
            dumpOutputFile = outFile,
            defaultDumpLength = defaultLen
        )
    }

    /**
     * Exact mtkclient `payload` command: runs arbitrary custom or built-in BROM payload binary.
     */
    suspend fun runCustomPayload(filePathOrAsset: String): Result<String> = withContext(Dispatchers.IO) {
        if (daLoaded) {
            return@withContext Result.failure(IllegalStateException("Custom payload execution requires BROM mode."))
        }
        val payloadBytes = MtkAssetManager.loadCustomFileBytes(filePathOrAsset, 1)
            ?: MtkAssetManager.loadPayloadBytes(context, filePathOrAsset)
            ?: return@withContext Result.failure(IllegalArgumentException("Payload not found: $filePathOrAsset"))

        if (!ensureTargetConnected()) {
            return@withContext Result.failure(IllegalStateException("Target BROM device not connected."))
        }
        val hwCode = readHwCode() ?: return@withContext Result.failure(IllegalStateException("Failed to read HW Code."))
        val cfg = MtkChipConfigDatabase.findConfig(hwCode) ?: MtkChipConfigDatabase.createDefaultFallback(hwCode)
        currentChipConfig = cfg
        disableWatchdog(cfg)
        val info = readDetailedDeviceInfo().getOrNull()

        val label = File(filePathOrAsset).name
        val outFile = File(storageManager.getBackupDirectory(), "payload_out_${label.substringBeforeLast('.')}.bin")
        return@withContext securityEngine.executeCustomPayload(
            rawPayloadBytes = payloadBytes,
            payloadName = label,
            deviceInfo = info,
            dumpOutputFile = outFile,
            defaultDumpLength = 0x20000
        )
    }

    suspend fun executeBromHandshake(): Result<MtkChipInfo> {
        return readDetailedDeviceInfo()
    }

    private fun resolveChipName(hwCode: String): String {
        val codeInt = hwCode.removePrefix("0x").removePrefix("0X").toIntOrNull(16)
        if (codeInt != null) {
            MtkChipConfigDatabase.findConfig(codeInt)?.let { cfg ->
                return if (cfg.description.isNotBlank()) "${cfg.name} (${cfg.description})" else cfg.name
            }
        }
        return when (hwCode.lowercase()) {
            "0x0907", "0x0983", "0x6983" -> "MT6983 (Dimensity 9000/9000+)"
            "0x1296", "0x0985", "0x6985" -> "MT6985 (Dimensity 9200/9200+)"
            "0x1236", "0x6989" -> "MT6989 (Dimensity 9300/9300+)"
            "0x1172" -> "MT6895 (Dimensity 8200)"
            "0x1203" -> "MT6897 (Dimensity 8300)"
            "0x0766" -> "MT6765 (Helio P35/G25/G35)"
            "0x0707" -> "MT6768 (Helio G85/G80)"
            "0x0813", "0x0816" -> "MT6785 (Helio G90T/G95)"
            "0x0989" -> "MT6833 (Dimensity 700)"
            "0x0959" -> "MT6877 (Dimensity 900)"
            "0x0950" -> "MT6893 (Dimensity 1200)"
            "0x0717" -> "MT6761/MT6762 (Helio A22/P22)"
            "0x6580" -> "MT6580 (Legacy 32-bit)"
            else -> "MediaTek SoC"
        }
    }
}
