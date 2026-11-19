package com.example.protocol

import androidx.annotation.Keep

@Keep
object JinUsbNative {
    @Volatile
    var isNativeLoaded: Boolean = false
        private set

    init {
        isNativeLoaded = try {
            System.loadLibrary("jin_usb")
            true
        } catch (_: Throwable) {
            false
        }
    }

    external fun nativeOpen(
        fd: Int,
        ctrlIface: Int = -1,
        dataIface: Int = -1,
        epIn: Int = 0,
        epOut: Int = 0
    ): Long

    external fun nativeSetupCdcLineCoding(
        handle: Long,
        baudRate: Int = 115200,
        dataBits: Int = 8,
        stopBits: Int = 0,
        parity: Int = 0,
        dtr: Boolean = true,
        rts: Boolean = true
    ): Int

    external fun nativeControlTransfer(
        handle: Long,
        reqType: Int,
        req: Int,
        value: Int,
        index: Int,
        buffer: ByteArray?,
        length: Int,
        timeoutMs: Int
    ): Int

    external fun nativeControlTransferAsyncCancel(
        handle: Long,
        reqType: Int,
        req: Int,
        value: Int,
        index: Int,
        buffer: ByteArray?,
        length: Int,
        cancelAfterUs: Int
    ): Int

    external fun nativeBulkWrite(
        handle: Long,
        data: ByteArray,
        offset: Int,
        length: Int,
        timeoutMs: Int,
        sendZlp: Boolean
    ): Int

    external fun nativeBulkRead(
        handle: Long,
        buffer: ByteArray,
        offset: Int,
        maxLen: Int,
        timeoutMs: Int,
        readFull: Boolean
    ): Int

    external fun nativeMtkBromHandshake(handle: Long, maxAttempts: Int): Boolean

    external fun nativeClearHalt(handle: Long, inEndpoint: Boolean): Int

    external fun nativeResetDevice(handle: Long): Int

    external fun nativeClose(handle: Long)

    // =========================================================================
    // Native Security, Anti-SO Replacement & License Engine JNI Methods
    // =========================================================================

    external fun nativeMutualHandshake(nonce: ByteArray): ByteArray?

    external fun nativeVerifyMasterKey(candidate: String): Boolean

    external fun nativeGenerateOneTimeKey(
        masterKey: String,
        durationDays: Int,
        randomSeed: String
    ): String

    external fun nativeVerifyKeyFormat(keyCode: String): Int

    external fun nativeComputeKeySignatureHex(keyCode: String): String

    external fun nativeIssueOfflineCertificate(
        hwid: String,
        keyCode: String,
        expiryEpochSec: Long
    ): String

    external fun nativeUnlockUsbEngine(
        hwid: String,
        keyCode: String,
        expiryEpochSec: Long,
        offlineCert: String,
        currentEpochSec: Long
    ): Int

    external fun nativeIsUsbEngineUnlocked(): Boolean

    external fun nativeLockUsbEngine()
}
