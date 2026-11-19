package com.example.protocol

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native Fastboot Protocol Client backed by libjin_usb.so (libusb).
 * Fastboot commands are ASCII strings followed by single/multiple responses:
 * "INFO<msg>", "OKAY<msg>", "FAIL<msg>", "DATA<hex_len>".
 */
class FastbootProtocolClient(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) {
    private var connection: UsbDeviceConnection? = null
    private var fastbootInterface: UsbInterface? = null
    private var nativeHandle: Long = 0L

    suspend fun open(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = usbManager.openDevice(device) ?: return@withContext false
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                    iface.interfaceSubclass == 0x42 &&
                    iface.interfaceProtocol == 0x03
                ) {
                    var epInAddr = 0
                    var epOutAddr = 0
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (ep.direction == UsbConstants.USB_DIR_IN) epInAddr = ep.address
                            else epOutAddr = ep.address
                        }
                    }
                    if (epInAddr != 0 && epOutAddr != 0) {
                        runCatching { conn.claimInterface(iface, true) }
                        val handle = JinUsbNative.nativeOpen(
                            fd = conn.fileDescriptor,
                            ctrlIface = -1,
                            dataIface = iface.id,
                            epIn = epInAddr,
                            epOut = epOutAddr
                        )
                        if (handle != 0L) {
                            connection = conn
                            fastbootInterface = iface
                            nativeHandle = handle
                            return@withContext true
                        }
                    }
                }
            }
            conn.close()
            return@withContext false
        } catch (_: Exception) {
            return@withContext false
        }
    }

    fun close() {
        if (nativeHandle != 0L) {
            runCatching { JinUsbNative.nativeClose(nativeHandle) }
            nativeHandle = 0L
        }
        runCatching { fastbootInterface?.let { connection?.releaseInterface(it) } }
        runCatching { connection?.close() }
        connection = null
        fastbootInterface = null
    }

    suspend fun executeCommand(command: String): FastbootResult = withContext(Dispatchers.IO) {
        val h = nativeHandle
        if (h == 0L) return@withContext FastbootResult(false, "", "Device not connected")

        val cmdBytes = command.toByteArray(Charsets.US_ASCII)
        val written = JinUsbNative.nativeBulkWrite(h, cmdBytes, 0, cmdBytes.size, 3000, sendZlp = false)
        if (written != cmdBytes.size) {
            return@withContext FastbootResult(false, "", "Failed to write fastboot command")
        }

        val infoMessages = mutableListOf<String>()
        val rxBuf = ByteArray(512)
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 10000L) {
            val read = JinUsbNative.nativeBulkRead(h, rxBuf, 0, rxBuf.size, 4000, readFull = false)
            if (read <= 0) break
            val response = String(rxBuf, 0, read, Charsets.US_ASCII)
            val lines = response.split("\r\n", "\n").filter { it.isNotEmpty() }
            for (line in lines) {
                when {
                    line.startsWith("INFO") -> infoMessages.add(line.substring(4).trim())
                    line.startsWith("OKAY") -> {
                        val payload = line.substring(4).trim()
                        val fullOut = (infoMessages + listOfNotNull(payload.takeIf { it.isNotBlank() })).joinToString("\n")
                        return@withContext FastbootResult(true, fullOut, "")
                    }
                    line.startsWith("FAIL") -> {
                        val payload = line.substring(4).trim()
                        return@withContext FastbootResult(
                            false,
                            infoMessages.joinToString("\n"),
                            payload.ifBlank { "Command failed" }
                        )
                    }
                    line.startsWith("DATA") -> {
                        val payload = line.substring(4).trim()
                        return@withContext FastbootResult(true, "DATA_READY:$payload", "")
                    }
                }
            }
        }
        return@withContext FastbootResult(false, infoMessages.joinToString("\n"), "Timeout waiting for fastboot response")
    }
}

data class FastbootResult(
    val isSuccess: Boolean,
    val info: String,
    val error: String
)
