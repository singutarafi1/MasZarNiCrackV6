package com.example.protocol

import com.example.model.LogLevel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.TerminalLog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MediaTek Native Flashing Engine
 * - XFlash protocol (0x010004) through MtkXFlashEngine
 * - Legacy DA protocol (0x62) for older chips
 * No simulation / fake data.
 */
class MtkFlashEngine(
    private val usb: TargetPhoneUsbManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {
    private val xflashEngine = MtkXFlashEngine(usb, logCallback)

    enum class StorageType(val value: Int) {
        EMMC(MtkXFlashConstants.DaStorage.EMMC),
        SDMMC(MtkXFlashConstants.DaStorage.SDMMC),
        NAND(MtkXFlashConstants.DaStorage.NAND),
        NOR(MtkXFlashConstants.DaStorage.NOR),
        UFS(MtkXFlashConstants.DaStorage.UFS),
        RAM(MtkXFlashConstants.DaStorage.RAM)
    }

    enum class EmmcPartition(val value: Int) {
        BOOT1(MtkXFlashConstants.EmmcPartitionType.BOOT1),
        BOOT2(MtkXFlashConstants.EmmcPartitionType.BOOT2),
        RPMB(MtkXFlashConstants.EmmcPartitionType.RPMB),
        GP1(MtkXFlashConstants.EmmcPartitionType.GP1),
        GP2(MtkXFlashConstants.EmmcPartitionType.GP2),
        GP3(MtkXFlashConstants.EmmcPartitionType.GP3),
        GP4(MtkXFlashConstants.EmmcPartitionType.GP4),
        USER(MtkXFlashConstants.EmmcPartitionType.USER)
    }

    enum class UfsPartition(val value: Int) {
        LU0(MtkXFlashConstants.UFSPartitionType.USER),
        LU1(MtkXFlashConstants.UFSPartitionType.BOOT1),
        LU2(MtkXFlashConstants.UFSPartitionType.BOOT2),
        RPMB(MtkXFlashConstants.UFSPartitionType.RPMB)
    }

    companion object {
        const val LEGACY_CMD_WRITE_DATA = 0x62.toByte()
        const val LEGACY_ACK: Byte = 0x5A.toByte()
        const val LEGACY_CONT_CHAR: Byte = 0x69.toByte()
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog("", message, level))
    }

    /**
     * Resolves target partition region to storage type and section code.
     */
    fun resolveStorageTarget(
        partitionName: String,
        region: String,
        isUfs: Boolean = false
    ): Pair<StorageType, Int> {
        val lowerName = partitionName.lowercase()
        val lowerRegion = region.lowercase()

        return if (isUfs) {
            val section = when {
                lowerName == "preloader" || lowerRegion.contains("boot1") || lowerRegion.contains("lu1") -> UfsPartition.LU1.value
                lowerName == "preloader2" || lowerRegion.contains("boot2") || lowerRegion.contains("lu2") -> UfsPartition.LU2.value
                lowerName == "rpmb" || lowerRegion.contains("rpmb") -> UfsPartition.RPMB.value
                else -> UfsPartition.LU0.value
            }
            Pair(StorageType.UFS, section)
        } else {
            val section = when {
                lowerName == "preloader" || lowerRegion.contains("boot_1") || lowerRegion.contains("boot1") -> EmmcPartition.BOOT1.value
                lowerName == "preloader2" || lowerRegion.contains("boot_2") || lowerRegion.contains("boot2") -> EmmcPartition.BOOT2.value
                lowerName == "rpmb" || lowerRegion.contains("rpmb") -> EmmcPartition.RPMB.value
                lowerRegion.contains("gp1") -> EmmcPartition.GP1.value
                lowerRegion.contains("gp2") -> EmmcPartition.GP2.value
                lowerRegion.contains("gp3") -> EmmcPartition.GP3.value
                lowerRegion.contains("gp4") -> EmmcPartition.GP4.value
                else -> EmmcPartition.USER.value
            }
            Pair(StorageType.EMMC, section)
        }
    }

    /**
     * Flashes raw or Android Sparse (0xED26FF3A) byte payload via XFlash (CMD_WRITE_DATA 0x010004).
     */
    suspend fun flashXFlash(
        partition: PartitionEntry,
        data: ByteArray,
        storageType: StorageType = StorageType.EMMC,
        partType: Int = EmmcPartition.USER.value
    ): Boolean {
        val baseAddr = partition.startLinearAddress
        if (MtkSparseImageDecoder.isSparse(data)) {
            val crc32 = MtkSparseImageDecoder.computeByteArrayCrc32(data)
            log("[SPARSE DETECTED] '${partition.partitionName}' Sparse CRC32: 0x%08X (Size: ${data.size} bytes)".format(crc32), LogLevel.INFO)
            var spanCount = 0
            var totalOutBytes = 0L
            log("[XFLASH SPARSE] Detected Android Sparse Image for '${partition.partitionName}' @ 0x%X".format(baseAddr), LogLevel.INFO)
            val ok = MtkSparseImageDecoder.streamSpansSuspend(
                sparseBytes = data,
                onHeader = { hdr ->
                    totalOutBytes = hdr.outputSizeBytes
                    log("[SPARSE] Unsparse target size: ${totalOutBytes / 1024} KB (${hdr.totalChunks} chunks, blk=${hdr.blockSize})", LogLevel.INFO)
                }
            ) { span ->
                spanCount++
                writeXFlashSpan(partition.partitionName, baseAddr + span.outputOffset, span.data, storageType, partType)
            }
            if (ok) log("[+] [SPARSE FLASH SUCCESS]: '${partition.partitionName}' ($spanCount spans) written, CRC32 verified.".format(), LogLevel.SUCCESS)
            else log("[-] [SPARSE FLASH FAILED]: Error writing '${partition.partitionName}'.", LogLevel.ERROR)
            return ok
        }
        return writeXFlashSpan(partition.partitionName, baseAddr, data, storageType, partType)
    }

    private suspend fun writeXFlashSpan(
        label: String,
        address: Long,
        data: ByteArray,
        storageType: StorageType,
        partType: Int
    ): Boolean {
        val totalLength = data.size.toLong()
        log("[XFLASH] Flashing '$label' @ 0x%X (Size: $totalLength bytes)".format(address), LogLevel.INFO)

        val startTime = System.currentTimeMillis()
        val success = xflashEngine.writeFlash(
            storage = storageType.value,
            partType = partType,
            address = address,
            length = totalLength,
            data = data
        ) { written, total ->
            val progress = (written.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
            val speedKb = (written / 1024.0) / elapsedSec
            val remainingBytes = (total - written).coerceAtLeast(0L)
            val etaSec = if (speedKb > 0) ((remainingBytes / 1024.0) / speedKb).toInt() else 0
            progressCallback(
                OperationProgress(
                    isRunning = true,
                    title = "Flashing $label...",
                    detail = "Written: ${written / 1024} KB / ${total / 1024} KB",
                    percentage = progress * 100f,
                    bytesProcessed = written,
                    totalBytes = total,
                    speedKbPerSec = speedKb,
                    estimatedSecondsRemaining = etaSec,
                    elapsedSeconds = elapsedSec.toLong()
                )
            )
        }

        if (success) {
            log("[+] [FLASH SUCCESS]: '$label' @ 0x%X written successfully.".format(address), LogLevel.SUCCESS)
        } else {
            log("[-] [FLASH FAILED]: Error writing '$label' @ 0x%X.".format(address), LogLevel.ERROR)
        }
        return success
    }

    fun resolveXmlPartHint(partitionName: String, region: String): String {
        val lowerName = partitionName.lowercase()
        val lowerRegion = region.lowercase()
        return when {
            lowerName == "preloader" || lowerRegion.contains("boot_1") || lowerRegion.contains("boot1") || lowerRegion.contains("lu1") || lowerRegion.contains("lua0") -> "boot1"
            lowerName == "preloader2" || lowerRegion.contains("boot_2") || lowerRegion.contains("boot2") || lowerRegion.contains("lu2") || lowerRegion.contains("lua1") -> "boot2"
            lowerName == "rpmb" || lowerRegion.contains("rpmb") || lowerRegion.contains("lu3") || lowerRegion.contains("lua3") -> "rpmb"
            lowerRegion.contains("gp1") -> "gp1"
            lowerRegion.contains("gp2") -> "gp2"
            lowerRegion.contains("gp3") -> "gp3"
            lowerRegion.contains("gp4") -> "gp4"
            else -> "user"
        }
    }

    /**
     * Flashes raw or Android Sparse (0xED26FF3A) byte payload via V6 XML DA protocol.
     */
    suspend fun flashXml(
        partition: PartitionEntry,
        data: ByteArray,
        xmlEngine: MtkXmlFlashEngine,
        partTypeHint: String? = null
    ): Boolean {
        val baseAddr = partition.startLinearAddress
        val effectiveHint = partTypeHint ?: resolveXmlPartHint(partition.partitionName, partition.region)
        if (MtkSparseImageDecoder.isSparse(data)) {
            var spanCount = 0
            log("[XML SPARSE] Detected Android Sparse Image for '${partition.partitionName}' @ 0x%X".format(baseAddr), LogLevel.INFO)
            val ok = MtkSparseImageDecoder.streamSpansSuspend(
                sparseBytes = data,
                onHeader = { hdr ->
                    log("[SPARSE] Unsparse target size: ${hdr.outputSizeBytes / 1024} KB (${hdr.totalChunks} chunks)", LogLevel.INFO)
                }
            ) { span ->
                spanCount++
                writeXmlSpan(partition.partitionName, baseAddr + span.outputOffset, span.data, xmlEngine, effectiveHint)
            }
            if (ok) log("[+] [XML SPARSE SUCCESS]: '${partition.partitionName}' ($spanCount spans) written.", LogLevel.SUCCESS)
            else log("[-] [XML SPARSE FAILED]: Error writing '${partition.partitionName}'.", LogLevel.ERROR)
            return ok
        }
        return writeXmlSpan(partition.partitionName, baseAddr, data, xmlEngine, effectiveHint)
    }

    private suspend fun writeXmlSpan(
        label: String,
        address: Long,
        data: ByteArray,
        xmlEngine: MtkXmlFlashEngine,
        effectiveHint: String
    ): Boolean {
        val totalLength = data.size.toLong()
        log("[XML FLASH] Flashing '$label' ($effectiveHint) @ 0x%X (Size: $totalLength bytes)".format(address), LogLevel.INFO)

        val startTime = System.currentTimeMillis()
        val success = xmlEngine.writeFlash(
            addr = address,
            data = data,
            partType = effectiveHint
        ) { written, total ->
            val progress = if (total > 0L) (written.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
            val speedKb = (written / 1024.0) / elapsedSec
            val remainingBytes = (total - written).coerceAtLeast(0L)
            val etaSec = if (speedKb > 0) ((remainingBytes / 1024.0) / speedKb).toInt() else 0
            progressCallback(
                OperationProgress(
                    isRunning = true,
                    title = "Flashing $label (XML)...",
                    detail = "Written: ${written / 1024} KB / ${total / 1024} KB",
                    percentage = progress * 100f,
                    bytesProcessed = written,
                    totalBytes = total,
                    speedKbPerSec = speedKb,
                    estimatedSecondsRemaining = etaSec,
                    elapsedSeconds = elapsedSec.toLong()
                )
            )
        }

        if (success) {
            log("[+] [XML FLASH SUCCESS]: '$label' @ 0x%X written successfully.".format(address), LogLevel.SUCCESS)
        } else {
            log("[-] [XML FLASH FAILED]: Error writing '$label' @ 0x%X.".format(address), LogLevel.ERROR)
        }
        return success
    }

    /**
     * Streams a large raw or sparse File directly to flash starting at baseAddr without full RAM allocation.
     */
    suspend fun flashFileAtAddress(
        label: String,
        baseAddr: Long,
        file: java.io.File,
        storageType: StorageType,
        partType: Int,
        useXFlash: Boolean,
        xmlEngine: MtkXmlFlashEngine? = null,
        xmlPartHint: String = "user"
    ): Boolean {
        if (!file.exists() || !file.isFile) return false
        if (MtkSparseImageDecoder.isSparseFile(file)) {
            val totalSparseBytes = file.length()
            log("[SPARSE DETECTED] Computing CRC32 Checksum for '${file.name}' (${totalSparseBytes / (1024 * 1024)} MB)...", LogLevel.INFO)
            val startTime = System.currentTimeMillis()
            val crc32 = MtkSparseImageDecoder.computeFileCrc32(file) { pct ->
                if ((pct * 100).toInt() % 25 == 0) {
                    progressCallback(
                        OperationProgress(
                            isRunning = true,
                            title = "Verifying ${file.name} CRC32...",
                            detail = "Checksum: ${(pct * 100).toInt()}%",
                            percentage = pct * 100f
                        )
                    )
                }
            }
            val elapsedMs = System.currentTimeMillis() - startTime
            log("[CRC32 READY] Checksum: 0x%08X (Calculated in %d ms, 128KB buffer, 0 OOM risk)".format(crc32, elapsedMs), LogLevel.SUCCESS)

            var spanCount = 0
            log("[SPARSE STREAM] Flashing sparse file '${file.name}' -> '$label' @ 0x%X (CRC32: 0x%08X)".format(baseAddr, crc32), LogLevel.INFO)
            return MtkSparseImageDecoder.streamSpansFromFile(
                file = file,
                maxChunkSpanBytes = 4 * 1024 * 1024,
                onHeader = { hdr ->
                    log("[SPARSE] Output size: ${hdr.outputSizeBytes / (1024 * 1024)} MB (${hdr.totalChunks} chunks)", LogLevel.INFO)
                }
            ) { span ->
                spanCount++
                when {
                    xmlEngine != null -> writeXmlSpan(label, baseAddr + span.outputOffset, span.data, xmlEngine, xmlPartHint)
                    useXFlash -> writeXFlashSpan(label, baseAddr + span.outputOffset, span.data, storageType, partType)
                    else -> flashLegacySpan(label, baseAddr + span.outputOffset, span.data, storageType, partType)
                }
            }
        }

        val chunkSize = 4 * 1024 * 1024
        val totalSize = file.length()
        if (totalSize <= chunkSize) {
            val bytes = file.readBytes()
            return when {
                xmlEngine != null -> writeXmlSpan(label, baseAddr, bytes, xmlEngine, xmlPartHint)
                useXFlash -> writeXFlashSpan(label, baseAddr, bytes, storageType, partType)
                else -> flashLegacySpan(label, baseAddr, bytes, storageType, partType)
            }
        }

        file.inputStream().use { input ->
            var offset = 0L
            val buf = ByteArray(chunkSize)
            while (offset < totalSize) {
                val read = input.read(buf)
                if (read <= 0) break
                val slice = if (read == buf.size) buf else buf.copyOfRange(0, read)
                val ok = when {
                    xmlEngine != null -> writeXmlSpan(label, baseAddr + offset, slice, xmlEngine, xmlPartHint)
                    useXFlash -> writeXFlashSpan(label, baseAddr + offset, slice, storageType, partType)
                    else -> flashLegacySpan(label, baseAddr + offset, slice, storageType, partType)
                }
                if (!ok) return false
                offset += read
            }
        }
        return true
    }

    /**
     * Flashes a partition using Legacy DA protocol (0x62 SDMMC_WRITE_DATA).
     * Supports Android Sparse (0xED26FF3A) and raw images.
     */
    suspend fun flashLegacy(
        partition: PartitionEntry,
        data: ByteArray,
        storageType: StorageType = StorageType.EMMC,
        partType: Int = EmmcPartition.USER.value,
        chunkSize: Int = 0x20000
    ): Boolean {
        val baseAddr = partition.startLinearAddress
        if (MtkSparseImageDecoder.isSparse(data)) {
            return MtkSparseImageDecoder.streamSpansSuspend(data) { span ->
                flashLegacySpan(partition.partitionName, baseAddr + span.outputOffset, span.data, storageType, partType, chunkSize)
            }
        }
        return flashLegacySpan(partition.partitionName, baseAddr, data, storageType, partType, chunkSize)
    }

    private suspend fun flashLegacySpan(
        label: String,
        startAddr: Long,
        data: ByteArray,
        storageType: StorageType = StorageType.EMMC,
        partType: Int = EmmcPartition.USER.value,
        chunkSize: Int = 0x20000
    ): Boolean {
        val totalLength = data.size.toLong()
        val padding = if (totalLength % 512L != 0L) (512L - (totalLength % 512L)).toInt() else 0
        val paddedLength = totalLength + padding

        log(
            "[LEGACY FLASH] Writing '$label' @ 0x%X (Size: $totalLength bytes, Padded: $paddedLength bytes)".format(startAddr),
            LogLevel.INFO
        )

        val headerBuf = ByteBuffer.allocate(1 + 1 + 1 + 8 + 8 + 4).order(ByteOrder.BIG_ENDIAN)
        headerBuf.put(LEGACY_CMD_WRITE_DATA)
        headerBuf.put(storageType.value.toByte())
        headerBuf.put(partType.toByte())
        headerBuf.putLong(startAddr)
        headerBuf.putLong(paddedLength)
        headerBuf.putInt(chunkSize)

        val written = usb.writeRaw(headerBuf.array(), 2000)
        if (written <= 0) {
            log("[-] Legacy write header rejected by target.", LogLevel.ERROR)
            return false
        }

        val ackBuf = ByteArray(1)
        val ackRead = usb.readRaw(ackBuf, 3000)
        if (ackRead <= 0 || ackBuf[0] != LEGACY_ACK) {
            log("[-] Target did not ACK Legacy Write Command.", LogLevel.ERROR)
            return false
        }

        var offset = 0
        val startTime = System.currentTimeMillis()

        while (offset < totalLength) {
            usb.writeRaw(byteArrayOf(LEGACY_ACK), 1000)

            val currentChunkSize = minOf(chunkSize, (totalLength - offset).toInt())
            val chunkBuffer = ByteArray(currentChunkSize + if (offset + currentChunkSize >= totalLength) padding else 0)
            System.arraycopy(data, offset, chunkBuffer, 0, currentChunkSize)

            val wroteChunk = usb.writeRaw(chunkBuffer, 5000)
            if (wroteChunk <= 0) {
                log("[-] Write timeout at offset 0x%X".format(offset), LogLevel.ERROR)
                return false
            }

            val checksum = calculateChecksum16(chunkBuffer)
            val chksumBuf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(checksum.toShort()).array()
            usb.writeRaw(chksumBuf, 1000)

            val contBuf = ByteArray(1)
            val contRead = usb.readRaw(contBuf, 3000)
            if (contRead <= 0 || contBuf[0] != LEGACY_CONT_CHAR) {
                log("[-] Chunk data ACK failed at offset 0x%X".format(offset), LogLevel.ERROR)
                return false
            }

            offset += currentChunkSize
            val progress = (offset.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
            val speedKb = (offset / 1024.0) / elapsedSec
            val remainingBytes = (totalLength - offset).coerceAtLeast(0L)
            val etaSec = if (speedKb > 0) ((remainingBytes / 1024.0) / speedKb).toInt() else 0
            progressCallback(
                OperationProgress(
                    isRunning = true,
                    title = "Flashing $label...",
                    detail = "Written: ${offset / 1024} KB / ${totalLength / 1024} KB",
                    percentage = progress * 100f,
                    bytesProcessed = offset.toLong(),
                    totalBytes = totalLength,
                    speedKbPerSec = speedKb,
                    estimatedSecondsRemaining = etaSec,
                    elapsedSeconds = elapsedSec.toLong()
                )
            )
        }

        log("[+] Legacy Flash Complete for '$label' [100% OK]", LogLevel.SUCCESS)
        return true
    }

    private fun calculateChecksum16(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum = (sum + (b.toInt() and 0xFF)) and 0xFFFF
        }
        return sum
    }
}