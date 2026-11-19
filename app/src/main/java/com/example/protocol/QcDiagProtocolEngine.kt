package com.example.protocol

import com.example.model.LogLevel
import com.example.model.TerminalLog
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Qualcomm Diagnostics (DIAG) Protocol Engine.
 * Handles HDLC-framed communication over Qualcomm Diag ports (05C6:9091 / 05C6:901D / 05C6:676C).
 * Provides reboot-to-EDL triggers, NV item read/write (IMEI repair), and SPC unlock.
 * Based on diag.py from bkerler/edl.
 */
class QcDiagProtocolEngine(
    private val usbManager: TargetPhoneUsbManager,
    private val logCallback: (TerminalLog) -> Unit
) {
    companion object {
        const val HDLC_FLAG = 0x7E.toByte()
        const val HDLC_ESC = 0x7D.toByte()
        const val HDLC_ESC_MASK = 0x20.toByte()

        // Diag Command Codes
        const val DIAG_VERNO_F = 0x00
        const val DIAG_ESN_F = 0x01
        const val DIAG_NV_READ_F = 0x26
        const val DIAG_NV_WRITE_F = 0x27
        const val DIAG_CONTROL_F = 0x29
        const val DIAG_SPC_F = 0x41
        const val DIAG_PASSWORD_F = 0x46
        const val DIAG_SUBSYS_CMD_F = 0x4B

        // Subsystems
        const val DIAG_SUBSYS_MODEM = 0x25
        const val MODEM_CMD_EDL_RESET = 0x03

        // CRC-16 CCITT Table
        private val CRC_TABLE = IntArray(256) { i ->
            var curr = i
            for (j in 0 until 8) {
                curr = if ((curr and 1) != 0) (curr ushr 1) xor 0x8408 else curr ushr 1
            }
            curr
        }

        fun calculateCrc16(data: ByteArray): Int {
            var crc = 0xFFFF
            for (b in data) {
                crc = (crc ushr 8) xor CRC_TABLE[(crc xor (b.toInt() and 0xFF)) and 0xFF]
            }
            return (crc xor 0xFFFF) and 0xFFFF
        }
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog(com.example.util.DeviceSafetyChecker.nowTime(), message, level))
    }

    /**
     * Frames raw data with HDLC escaping, CRC16-CCITT, and 0x7E boundary flags.
     */
    fun hdlcEncode(payload: ByteArray): ByteArray {
        val crc = calculateCrc16(payload)
        val withCrc = ByteArray(payload.size + 2)
        System.arraycopy(payload, 0, withCrc, 0, payload.size)
        withCrc[payload.size] = (crc and 0xFF).toByte()
        withCrc[payload.size + 1] = ((crc ushr 8) and 0xFF).toByte()

        val baos = ByteArrayOutputStream(withCrc.size * 2)
        baos.write(HDLC_FLAG.toInt())

        for (b in withCrc) {
            if (b == HDLC_FLAG || b == HDLC_ESC) {
                baos.write(HDLC_ESC.toInt())
                baos.write((b.toInt() xor HDLC_ESC_MASK.toInt()))
            } else {
                baos.write(b.toInt())
            }
        }
        baos.write(HDLC_FLAG.toInt())
        return baos.toByteArray()
    }

    /**
     * Sends an HDLC encoded frame and reads the response frame.
     */
    suspend fun sendDiagCommand(payload: ByteArray, timeoutMs: Int = 2000): ByteArray? = withContext(Dispatchers.IO) {
        val encoded = hdlcEncode(payload)
        usbManager.writeRaw(encoded, timeoutMs = timeoutMs)

        val respBuf = ByteArray(4096)
        val readLen = usbManager.readRaw(respBuf, timeoutMs = timeoutMs)
        if (readLen <= 0) return@withContext null

        return@withContext hdlcDecode(respBuf.copyOfRange(0, readLen))
    }

    fun hdlcDecode(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        var escaped = false

        for (b in data) {
            if (b == HDLC_FLAG) continue
            if (b == HDLC_ESC) {
                escaped = true
                continue
            }
            if (escaped) {
                baos.write(b.toInt() xor HDLC_ESC_MASK.toInt())
                escaped = false
            } else {
                baos.write(b.toInt())
            }
        }
        val unescaped = baos.toByteArray()
        return if (unescaped.size >= 2) {
            unescaped.copyOfRange(0, unescaped.size - 2) // Strip CRC16
        } else {
            unescaped
        }
    }

    /**
     * Sends the command to reboot the device from Qualcomm Diag mode into EDL Mode (05C6:9008).
     */
    suspend fun rebootToEdl(): Result<Boolean> = withContext(Dispatchers.IO) {
        log("[DIAG] Sending Qualcomm Reboot to Emergency Download Mode (EDL 9008)...", LogLevel.INFO)

        // Method 1: Subsystem command 0x4B 0x25 0x03 0x00
        val cmd1 = byteArrayOf(
            DIAG_SUBSYS_CMD_F.toByte(),
            DIAG_SUBSYS_MODEM.toByte(),
            MODEM_CMD_EDL_RESET.toByte(),
            0x00.toByte()
        )
        sendDiagCommand(cmd1, timeoutMs = 1000)

        // Method 2: DIAG_CONTROL_F mode 3 (0x29 0x01 0x00)
        val cmd2 = byteArrayOf(
            DIAG_CONTROL_F.toByte(),
            0x01.toByte(),
            0x00.toByte()
        )
        sendDiagCommand(cmd2, timeoutMs = 1000)

        log("[+] EDL Reboot command issued. Re-enumerating USB port...", LogLevel.SUCCESS)
        return@withContext Result.success(true)
    }

    /**
     * Unlocks security using default Qualcomm 6-digit SPC "000000".
     */
    suspend fun unlockSpc(spc: String = "000000"): Result<Boolean> = withContext(Dispatchers.IO) {
        log("[DIAG] Unlocking Qualcomm Security via SPC ($spc)...", LogLevel.INFO)
        val spcBytes = spc.padEnd(6, '0').take(6).toByteArray(Charsets.US_ASCII)
        val cmd = ByteArray(1 + 6)
        cmd[0] = DIAG_SPC_F.toByte()
        System.arraycopy(spcBytes, 0, cmd, 1, 6)

        val resp = sendDiagCommand(cmd, timeoutMs = 1500)
        if (resp != null && resp.isNotEmpty() && resp[0] == DIAG_SPC_F.toByte()) {
            val status = if (resp.size > 1) resp[1].toInt() else 1
            if (status == 1) {
                log("[+] [SPC UNLOCKED]: Qualcomm Diag security unlocked.", LogLevel.SUCCESS)
                return@withContext Result.success(true)
            }
        }
        log("[-] SPC unlock rejected or not supported on this target.", LogLevel.WARNING)
        return@withContext Result.failure(IllegalStateException("SPC Rejected"))
    }
}
