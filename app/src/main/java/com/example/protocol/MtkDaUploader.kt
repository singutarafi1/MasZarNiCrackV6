package com.example.protocol

import com.example.model.LogLevel
import com.example.model.TerminalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek Download Agent (DA) Upload Pipeline
 * Faithfully ported from Python mtkclient:
 * - mtkclient/Library/mtk_preloader.py -> prepare_data, upload_data, send_da, jump_da
 * Pure Native Implementation - Zero Mock/Fake Logic.
 */
object MtkDaUploader {

    const val CMD_SEND_DA: Byte = 0xD7.toByte()
    const val CMD_JUMP_DA: Byte = 0xD5.toByte()
    const val CMD_JUMP_DA64: Byte = 0xDE.toByte()
    const val STATUS_OK: Int = 0x0000
    const val STATUS_SLA_REQUIRED: Int = 0x1D0D

    private fun echoBytes(usb: TargetPhoneUsbManager, data: ByteArray, timeoutMs: Int = 1000): Boolean {
        if (usb.writeRaw(data, timeoutMs) != data.size) return false
        val echo = ByteArray(data.size)
        val r = usb.readRaw(echo, timeoutMs, readFull = true)
        return r == data.size && echo.contentEquals(data)
    }

    private fun readU16BE(usb: TargetPhoneUsbManager, timeoutMs: Int = 2000): Int? {
        val buf = ByteArray(2)
        val r = usb.readRaw(buf, timeoutMs, readFull = true)
        if (r != 2) return null
        return ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
    }

    private fun readU32BE(usb: TargetPhoneUsbManager, timeoutMs: Int = 2000): Long? {
        val buf = ByteArray(4)
        val r = usb.readRaw(buf, timeoutMs, readFull = true)
        if (r != 4) return null
        return ((buf[0].toLong() and 0xFFL) shl 24) or
            ((buf[1].toLong() and 0xFFL) shl 16) or
            ((buf[2].toLong() and 0xFFL) shl 8) or
            (buf[3].toLong() and 0xFFL)
    }

    suspend fun sendDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        daData: ByteArray,
        sigLen: Int = 0,
        maxPacketSize: Int = 0x400, // EP_IN wMaxPacketSize or 0x400 fallback
        hwCode: Int = 0,
        logCallback: (TerminalLog) -> Unit,
        onProgress: (Float) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val safeSigLen = if (sigLen in 1..daData.size) sigLen else 0
        val body = if (safeSigLen > 0) daData.copyOfRange(0, daData.size - safeSigLen) else daData
        val sigTail = if (safeSigLen > 0) daData.copyOfRange(daData.size - safeSigLen, daData.size) else ByteArray(0)
        var payload = body + sigTail

        // 16-bit word alignment (prepare_data in mtk_preloader.py)
        if (payload.size % 2 != 0) payload += byteArrayOf(0)

        // Calculate 16-bit XOR Checksum (<H words)
        var genChecksum = 0
        var ci = 0
        while (ci < payload.size) {
            val word = (payload[ci].toInt() and 0xFF) or ((payload[ci + 1].toInt() and 0xFF) shl 8)
            genChecksum = genChecksum xor word
            ci += 2
        }
        genChecksum = genChecksum and 0xFFFF

        logCallback(
            TerminalLog(
                "",
                "[DA UPLOADER] Sending DA Stage to 0x%08X (Size: %d bytes, Sig: %d)".format(daAddress, payload.size, safeSigLen),
                LogLevel.INFO
            )
        )

        // 1. Send CMD_SEND_DA (0xD7) and verify echo
        if (!echoBytes(usb, byteArrayOf(CMD_SEND_DA), 1000)) {
            return@withContext Result.failure(IllegalStateException("Failed writing CMD_SEND_DA command byte to USB (echo mismatch)."))
        }

        // 2. Send Target Address (Big Endian)
        val addrBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt((daAddress and 0xFFFFFFFFL).toInt()).array()
        if (!echoBytes(usb, addrBytes, 1000)) {
            return@withContext Result.failure(IllegalStateException("Failed writing DA address to USB (echo mismatch)."))
        }

        // 3. Send DA Length (Big Endian)
        val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array()
        if (!echoBytes(usb, lenBytes, 1000)) {
            return@withContext Result.failure(IllegalStateException("Failed writing DA length to USB (echo mismatch)."))
        }

        // 4. Send Signature Length (Big Endian)
        val sigLenBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(safeSigLen).array()
        if (!echoBytes(usb, sigLenBytes, 1000)) {
            return@withContext Result.failure(IllegalStateException("Failed writing DA sigLen to USB (echo mismatch)."))
        }

        // 5. Read Parameter Status (Must be <= 0xFF; 0x1D0D = SLA required)
        val status = readU16BE(usb, 2500)
        if (status == null) {
            return@withContext Result.failure(IllegalStateException("DA upload parameter rejected by BootROM (Status: Timeout)."))
        }
        if (status == STATUS_SLA_REQUIRED) {
            return@withContext Result.failure(
                IllegalStateException("DA upload requires BROM SLA auth (0x1D0D). Run Kamakiri2 BROM exploit first.")
            )
        }
        if (status > 0xFF) {
            return@withContext Result.failure(
                IllegalStateException("DA upload parameter rejected by BootROM (Status: 0x%04X).".format(status))
            )
        }

        // 6. Upload DA Binary with exact mtk_preloader.upload_data ZLP cadence
        val maxIn = usb.getMaxInPacketSize().coerceAtLeast(maxPacketSize)
        val chunkLimit = minOf(maxIn, 0x400).coerceAtLeast(64)
        val totalBytes = payload.size
        var offset = 0
        while (offset < totalBytes) {
            val thisChunk = minOf(chunkLimit, totalBytes - offset)
            val chunk = payload.copyOfRange(offset, offset + thisChunk)

            val w = usb.writeRaw(chunk, 3000)
            if (w != thisChunk) {
                return@withContext Result.failure(IllegalStateException("USB Pipe write error at offset $offset."))
            }

            offset += thisChunk
            if (offset % 0x2000 == 0) {
                usb.writeRaw(ByteArray(0), 1000)
            }
            onProgress(offset.toFloat() / totalBytes.toFloat())
        }
        if (hwCode != 0x2531) {
            usb.writeRaw(ByteArray(0), 1000)
            delay(120)
        }

        // 7. Verify Checksum and Final Status
        val checksum = readU16BE(usb, 3000)
        val finalStatus = readU16BE(usb, 3000)

        if (checksum != null && checksum != 0 && checksum != genChecksum) {
            logCallback(
                TerminalLog(
                    "",
                    "[DA UPLOADER] Warning: Checksum mismatch (got 0x%04X, expected 0x%04X).".format(checksum, genChecksum),
                    LogLevel.WARNING
                )
            )
        }
        if (finalStatus == null || finalStatus > 0xFF) {
            val statHex = if (finalStatus != null) "0x%04X".format(finalStatus) else "Timeout"
            return@withContext Result.failure(IllegalStateException("DA Stage data transfer incomplete (Status: $statHex)."))
        }

        logCallback(TerminalLog("", "[DA UPLOADER] DA Stage successfully staged in target memory.", LogLevel.SUCCESS))
        return@withContext Result.success(true)
    }

    suspend fun jumpDa(
        usb: TargetPhoneUsbManager,
        daAddress: Long,
        logCallback: (TerminalLog) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        logCallback(TerminalLog("", "[DA UPLOADER] Jumping to DA entry point 0x%08X...".format(daAddress), LogLevel.INFO))

        // 1. Send CMD_JUMP_DA (0xD5) and verify echo
        if (!echoBytes(usb, byteArrayOf(CMD_JUMP_DA), 1000)) {
            return@withContext Result.failure(IllegalStateException("CMD_JUMP_DA echo mismatch."))
        }

        // 2. Write target address (BE) and verify 4-byte resaddr (mtk_preloader.py lines 3058-3066)
        val addrBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((daAddress and 0xFFFFFFFFL).toInt()).array()
        if (usb.writeRaw(addrBuf, 1000) != 4) {
            return@withContext Result.failure(IllegalStateException("Failed writing DA jump address."))
        }
        val resAddr = readU32BE(usb, 2000)
        if (resAddr == null || (resAddr and 0xFFFFFFFFL) != (daAddress and 0xFFFFFFFFL)) {
            return@withContext Result.failure(
                IllegalStateException(
                    "JUMP_DA address echo mismatch (got ${resAddr?.let { "0x%08X".format(it) } ?: "timeout"}, expected 0x%08X)".format(daAddress)
                )
            )
        }

        // 3. Read execution status (Must be 0x0000) then wait 100ms per mtk_preloader.py line 3070
        val status = readU16BE(usb, 2500)
        if (status == null || status != STATUS_OK) {
            val statHex = if (status != null) "0x%04X".format(status) else "Timeout"
            return@withContext Result.failure(IllegalStateException("DA jump execution rejected (Status: $statHex)."))
        }
        delay(100)

        logCallback(TerminalLog("", "[DA UPLOADER] DA execution started successfully.", LogLevel.SUCCESS))
        return@withContext Result.success(true)
    }
}
