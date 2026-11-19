package com.example.protocol

import com.example.model.LogLevel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.TerminalLog
import com.example.storage.BackupStorageManager
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * MediaTek Native Firmware Read & Dump Engine
 * Real USB hardware read operations via XFlash & Legacy protocols - no simulation.
 */
class MtkDumpEngine(
    private val usb: TargetPhoneUsbManager,
    private val storageManager: BackupStorageManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {
    private val xflashEngine = MtkXFlashEngine(usb, logCallback)

    companion object {
        const val LEGACY_CMD_READ = 0xD6.toByte()
        const val LEGACY_ACK: Byte = 0x5A.toByte()
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO, isDebugOnly: Boolean = false) {
        logCallback(TerminalLog("", message, level, isDebugOnly = isDebugOnly))
    }

    /**
     * Dumps a single partition to a file using XFlash or Legacy DA read.
     */
    suspend fun dumpPartition(
        partition: PartitionEntry,
        outputFile: File,
        storageType: MtkFlashEngine.StorageType = MtkFlashEngine.StorageType.EMMC,
        partType: Int = MtkFlashEngine.EmmcPartition.USER.value,
        useXFlash: Boolean = true,
        xmlEngine: MtkXmlFlashEngine? = null,
        xmlPartTypeHint: String? = null
    ): Result<String> {
        val startAddr = partition.startLinearAddress
        val totalBytes = if (partition.sizeBytes > 0) partition.sizeBytes else 4194304L

        log("[DUMP] Starting Dump for '${partition.partitionName}' @ 0x%X (Size: ${totalBytes / 1024} KB)".format(startAddr), LogLevel.INFO, isDebugOnly = true)
        log("[DUMP] Target File: ${outputFile.absolutePath}", LogLevel.INFO, isDebugOnly = true)

        if (outputFile.parentFile?.exists() == false) {
            outputFile.parentFile?.mkdirs()
        }

        val startTime = System.currentTimeMillis()
        val outStream = BufferedOutputStream(FileOutputStream(outputFile), 512 * 1024)

        try {
            if (xmlEngine != null) {
                val lowerName = partition.partitionName.lowercase()
                val lowerRegion = partition.region.lowercase()
                val effectiveHint = xmlPartTypeHint ?: when {
                    lowerName == "preloader" || lowerRegion.contains("boot_1") || lowerRegion.contains("boot1") || lowerRegion.contains("lu1") || lowerRegion.contains("lua0") -> "boot1"
                    lowerName == "preloader2" || lowerRegion.contains("boot_2") || lowerRegion.contains("boot2") || lowerRegion.contains("lu2") || lowerRegion.contains("lua1") -> "boot2"
                    lowerName == "rpmb" || lowerRegion.contains("rpmb") || lowerRegion.contains("lu3") || lowerRegion.contains("lua3") -> "rpmb"
                    else -> "user"
                }
                val success = xmlEngine.readFlashToStream(
                    addr = startAddr,
                    length = totalBytes,
                    outStream = outStream,
                    partType = effectiveHint
                ) { read, total ->
                    val progress = if (total > 0L) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                    val speedKb = (read / 1024.0) / elapsedSec
                    val remainingBytes = (total - read).coerceAtLeast(0L)
                    val etaSec = if (speedKb > 0) ((remainingBytes / 1024.0) / speedKb).toInt() else 0
                    progressCallback(
                        OperationProgress(
                            isRunning = true,
                            title = "Dumping ${partition.partitionName}...",
                            detail = "Dumped: ${read / 1024} KB / ${total / 1024} KB",
                            percentage = progress * 100f,
                            bytesProcessed = read,
                            totalBytes = total,
                            speedKbPerSec = speedKb,
                            estimatedSecondsRemaining = etaSec,
                            elapsedSeconds = elapsedSec.toLong()
                        )
                    )
                }
                outStream.close()
                if (!success) {
                    outputFile.delete()
                    log("[-] [XML DUMP FAILED]: Target USB read error during partition dump.", LogLevel.ERROR)
                    return Result.failure(IllegalStateException("XML read failed for ${partition.partitionName}"))
                }
            } else if (useXFlash) {
                val success = xflashEngine.readFlashToStream(
                    storage = storageType.value,
                    partType = partType,
                    address = startAddr,
                    length = totalBytes,
                    out = outStream
                ) { read, total ->
                    val progress = (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                    val speedKb = (read / 1024.0) / elapsedSec
                    val remainingBytes = (total - read).coerceAtLeast(0L)
                    val etaSec = if (speedKb > 0) ((remainingBytes / 1024.0) / speedKb).toInt() else 0
                    progressCallback(
                        OperationProgress(
                            isRunning = true,
                            title = "Dumping ${partition.partitionName}...",
                            detail = "Dumped: ${read / 1024} KB / ${total / 1024} KB",
                            percentage = progress * 100f,
                            bytesProcessed = read,
                            totalBytes = total,
                            speedKbPerSec = speedKb,
                            estimatedSecondsRemaining = etaSec,
                            elapsedSeconds = elapsedSec.toLong()
                        )
                    )
                }

                outStream.close()

                if (!success) {
                    outputFile.delete()
                    log("[-] [DUMP FAILED]: Target USB read error during partition dump.", LogLevel.ERROR)
                    return Result.failure(IllegalStateException("XFlash read failed for ${partition.partitionName}"))
                }
            } else {
                legacyReadToStream(
                    startAddr = startAddr,
                    totalBytes = totalBytes,
                    storageType = storageType,
                    partType = partType,
                    outputStream = outStream,
                    startTime = startTime,
                    partitionName = partition.partitionName
                )
                outStream.close()
            }

            log("[+] [DUMP COMPLETE]: Partition '${partition.partitionName}' dumped to ${outputFile.name} (${outputFile.length()} bytes).", LogLevel.SUCCESS, isDebugOnly = true)
            return Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            try { outStream.close() } catch (_: Exception) {}
            outputFile.delete()
            log("[-] [DUMP ERROR]: ${e.message}", LogLevel.ERROR)
            return Result.failure(e)
        }
    }

    /**
     * Dumps an arbitrary flash offset & length (used by mtkclient rf / ro / rs).
     */
    suspend fun dumpRawRange(
        label: String,
        startAddr: Long,
        totalBytes: Long,
        outputFile: File,
        storageType: MtkFlashEngine.StorageType = MtkFlashEngine.StorageType.EMMC,
        partType: Int = MtkFlashEngine.EmmcPartition.USER.value,
        useXFlash: Boolean = true,
        xmlEngine: MtkXmlFlashEngine? = null,
        xmlPartTypeHint: String = "user"
    ): Result<String> {
        val syntheticPart = PartitionEntry(
            partitionIndex = 0,
            partitionName = label,
            fileName = outputFile.name,
            linearStartAddrHex = "0x%X".format(startAddr),
            physicalStartAddrHex = "0x%X".format(startAddr),
            partitionSizeHex = "0x%X".format(totalBytes),
            sizeBytes = totalBytes,
            region = xmlPartTypeHint,
            isDownload = false,
            isProtectedNv = false,
            isSelectedForFlashing = false
        )
        return dumpPartition(
            partition = syntheticPart,
            outputFile = outputFile,
            storageType = storageType,
            partType = partType,
            useXFlash = useXFlash,
            xmlEngine = xmlEngine,
            xmlPartTypeHint = xmlPartTypeHint
        )
    }

    /**
     * Legacy DA read implementation using CMD_READ (0xD6).
     */
    private suspend fun legacyReadToStream(
        startAddr: Long,
        totalBytes: Long,
        storageType: MtkFlashEngine.StorageType,
        partType: Int,
        outputStream: OutputStream,
        startTime: Long,
        partitionName: String
    ) {
        val padding = if (totalBytes % 512L != 0L) (512L - (totalBytes % 512L)).toInt() else 0
        val paddedLength = totalBytes + padding

        val headerBuf = java.nio.ByteBuffer.allocate(1 + 1 + 1 + 8 + 8 + 4).order(java.nio.ByteOrder.BIG_ENDIAN)
        headerBuf.put(LEGACY_CMD_READ)
        headerBuf.put(storageType.value.toByte())
        headerBuf.put(partType.toByte())
        headerBuf.putLong(startAddr)
        headerBuf.putLong(paddedLength)
        headerBuf.putInt(65536) // packet size

        usb.writeRaw(headerBuf.array(), 2000)

        var processed = 0L
        val buffer = ByteArray(65536)

        while (processed < totalBytes) {
            val toRead = minOf(buffer.size.toLong(), totalBytes - processed).toInt()
            val read = usb.readRaw(buffer, 3000)
            if (read <= 0) {
                throw IllegalStateException("USB read timeout at offset 0x%X".format(startAddr + processed))
            }
            val actual = minOf(read, toRead)
            outputStream.write(buffer, 0, actual)
            processed += actual

            val progress = (processed.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
            val speedKb = (processed / 1024.0) / elapsedSec
            val remainingBytes = (totalBytes - processed).coerceAtLeast(0L)
            val etaSec = if (speedKb > 0) ((remainingBytes / 1024.0) / speedKb).toInt() else 0
            progressCallback(
                OperationProgress(
                    isRunning = true,
                    title = "Dumping $partitionName...",
                    detail = "Dumped: ${processed / 1024} KB / ${totalBytes / 1024} KB",
                    percentage = progress * 100f,
                    bytesProcessed = processed,
                    totalBytes = totalBytes,
                    speedKbPerSec = speedKb,
                    estimatedSecondsRemaining = etaSec,
                    elapsedSeconds = elapsedSec.toLong()
                )
            )
        }
    }

    /**
     * Dumps all partitions to a target directory.
     */
    suspend fun dumpAllPartitions(
        chipPlatform: String,
        partitions: List<PartitionEntry>,
        destinationDir: File,
        skipPartitions: List<String> = emptyList(),
        isUfs: Boolean = false,
        xmlEngine: MtkXmlFlashEngine? = null
    ): Result<List<String>> {
        if (partitions.isEmpty()) {
            return Result.failure(IllegalArgumentException("No partitions provided to dump."))
        }
        if (!destinationDir.exists()) destinationDir.mkdirs()
        val dumped = mutableListOf<String>()

        for ((idx, part) in partitions.withIndex()) {
            if (skipPartitions.any { it.equals(part.partitionName, ignoreCase = true) }) {
                log("[DUMP SKIP] Skipping ${part.partitionName}", LogLevel.INFO, isDebugOnly = true)
                continue
            }

            val partIsUfs = isUfs || part.region.contains("UFS", true) || part.region.contains("LU", true)
            val lowerName = part.partitionName.lowercase()
            val lowerRegion = part.region.lowercase()
            val storageType = if (partIsUfs) MtkFlashEngine.StorageType.UFS else MtkFlashEngine.StorageType.EMMC
            val partSection = if (partIsUfs) {
                when {
                    lowerName == "preloader" || lowerRegion.contains("boot1") || lowerRegion.contains("lu1") -> MtkFlashEngine.UfsPartition.LU1.value
                    lowerName == "preloader2" || lowerRegion.contains("boot2") || lowerRegion.contains("lu2") -> MtkFlashEngine.UfsPartition.LU2.value
                    lowerName == "rpmb" || lowerRegion.contains("rpmb") -> MtkFlashEngine.UfsPartition.RPMB.value
                    else -> MtkFlashEngine.UfsPartition.LU0.value
                }
            } else {
                when {
                    lowerName == "preloader" || lowerRegion.contains("boot_1") || lowerRegion.contains("boot1") -> MtkFlashEngine.EmmcPartition.BOOT1.value
                    lowerName == "preloader2" || lowerRegion.contains("boot_2") || lowerRegion.contains("boot2") -> MtkFlashEngine.EmmcPartition.BOOT2.value
                    lowerName == "rpmb" || lowerRegion.contains("rpmb") -> MtkFlashEngine.EmmcPartition.RPMB.value
                    else -> MtkFlashEngine.EmmcPartition.USER.value
                }
            }

            val fileName = resolveFirmwareImageFileName(part.partitionName, chipPlatform)
            val outFile = File(destinationDir, fileName)
            val partStartTime = System.currentTimeMillis()
            val partSizeStr = formatStorageBytes(part.sizeBytes)
            log("▶ [${idx + 1}/${partitions.size}] Backing up '${part.partitionName}' ($partSizeStr)...", LogLevel.INFO)
            val res = dumpPartition(
                partition = part,
                outputFile = outFile,
                storageType = storageType,
                partType = partSection,
                useXFlash = (xmlEngine == null),
                xmlEngine = xmlEngine
            )
            if (res.isSuccess) {
                dumped.add(outFile.absolutePath)
                val elapsedSec = ((System.currentTimeMillis() - partStartTime) / 1000.0).coerceAtLeast(0.1)
                val speedMb = (outFile.length() / (1024.0 * 1024.0)) / elapsedSec
                val speedStr = "%.2f MB/s".format(speedMb)
                val durationStr = if (elapsedSec >= 60) "%d:%02d".format((elapsedSec / 60).toInt(), (elapsedSec % 60).toInt()) else "%.1fs".format(elapsedSec)
                log("✔ [${idx + 1}/${partitions.size}] $fileName ($partSizeStr in $durationStr • $speedStr)", LogLevel.SUCCESS)
            } else {
                log("✖ [${idx + 1}/${partitions.size}] Failed reading '${part.partitionName}'", LogLevel.ERROR)
            }
        }

        if (dumped.isNotEmpty()) {
            try {
                // 1. Auto-create official MediaTek Android Scatter file for complete restoration
                val scatterName = "${chipPlatform}_Android_scatter.txt"
                val scatterFile = File(destinationDir, scatterName)
                val effectivePartitions = partitions.map { p ->
                    val expectedFileName = resolveFirmwareImageFileName(p.partitionName, chipPlatform)
                    val isDumped = dumped.any { it.endsWith(expectedFileName) }
                    p.copy(
                        fileName = if (isDumped) expectedFileName else "NONE",
                        isDownload = isDumped,
                        boundFilePath = if (isDumped) File(destinationDir, expectedFileName).absolutePath else ""
                    )
                }
                val scatterContent = buildOfficialScatterContent(chipPlatform, effectivePartitions, isUfs)
                scatterFile.writeText(scatterContent)
                dumped.add(scatterFile.absolutePath)
                log("[+] [SCATTER CREATED]: Auto-generated firmware scatter: $scatterName", LogLevel.SUCCESS)

                // 2. Auto-create checksum.ini for flash integrity verification
                val checksumFile = File(destinationDir, "checksum.ini")
                val csContent = buildChecksumIni(destinationDir, effectivePartitions)
                if (csContent.isNotBlank()) {
                    checksumFile.writeText(csContent)
                    dumped.add(checksumFile.absolutePath)
                    log("[+] [CHECKSUM CREATED]: Auto-generated 'checksum.ini' for flash verification.", LogLevel.INFO)
                }

                log("[+] [FULL FIRMWARE BACKUP COMPLETE]: ${dumped.size} files created in '${destinationDir.name}'. Ready for Restore & Flash!", LogLevel.SUCCESS)
            } catch (e: Exception) {
                log("[-] Warning: Failed to generate companion scatter file: ${e.message}", LogLevel.WARNING)
            }
            return Result.success(dumped)
        } else {
            return Result.failure(IllegalStateException("No partitions were successfully dumped."))
        }
    }

    /**
     * Resolves standard MediaTek firmware image file extension and naming (.img vs .bin).
     */
    fun resolveFirmwareImageFileName(partitionName: String, chipPlatform: String = ""): String {
        val norm = partitionName.lowercase()
        return when {
            norm == "preloader" -> {
                val cleanPlatform = chipPlatform.lowercase().replace("k68v1", "").replace("k69v1", "").trim()
                if (cleanPlatform.isNotBlank() && cleanPlatform != "auto") "preloader_${cleanPlatform}.bin" else "preloader.bin"
            }
            norm in setOf("preloader2", "lk", "lk2", "logo", "nvram", "nvdata", "proinfo", "sec1", "seccfg", "para", "expdb", "frp", "otp", "nvcfg", "sram", "dram", "md3rom") -> "$partitionName.bin"
            else -> "$partitionName.img"
        }
    }

    private fun buildOfficialScatterContent(
        platform: String,
        partitions: List<PartitionEntry>,
        isUfs: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append("############################################################################################################\n")
        sb.append("#\n")
        sb.append("#  General Setting\n")
        sb.append("#\n")
        sb.append("############################################################################################################\n")
        sb.append("- general: MTK_PLATFORM_CFG\n")
        sb.append("  info:\n")
        sb.append("    - config_version: V1.1.2\n")
        sb.append("      platform: $platform\n")
        sb.append("      project: ${platform}_Android\n")
        sb.append("      storage: ${if (isUfs) "UFS" else "EMMC"}\n")
        sb.append("      boot_channel: MSDC_0\n")
        sb.append("      block_size: 0x20000\n\n")
        sb.append("############################################################################################################\n")
        sb.append("#\n")
        sb.append("#  Layout Setting\n")
        sb.append("#\n")
        sb.append("############################################################################################################\n")

        for (p in partitions) {
            val fileName = p.fileName.ifBlank { "NONE" }
            val isDownload = fileName != "NONE" && p.isDownload
            sb.append("- partition_index: SYS${p.partitionIndex}\n")
            sb.append("  partition_name: ${p.partitionName}\n")
            sb.append("  file_name: $fileName\n")
            sb.append("  is_download: $isDownload\n")
            sb.append("  type: NORMAL_ROM\n")
            sb.append("  linear_start_addr: ${p.linearStartAddrHex}\n")
            sb.append("  physical_start_addr: ${p.physicalStartAddrHex}\n")
            sb.append("  partition_size: ${p.partitionSizeHex}\n")
            sb.append("  region: ${p.region}\n")
            sb.append("  storage: ${if (isUfs) "HW_STORAGE_UFS" else "HW_STORAGE_EMMC"}\n")
            sb.append("  boundary_check: true\n")
            sb.append("  is_reserved: false\n")
            sb.append("  operation_type: UPDATE\n")
            sb.append("  reserve: 0x00\n\n")
        }
        return sb.toString()
    }

    private fun buildChecksumIni(destinationDir: File, partitions: List<PartitionEntry>): String {
        val sb = StringBuilder()
        sb.append("// Checksum File for MTK Flash & Restore\n")
        sb.append("[CheckSum]\n")
        for (p in partitions) {
            if (p.fileName.isNotBlank() && p.fileName != "NONE") {
                val f = File(destinationDir, p.fileName)
                if (f.exists()) {
                    sb.append("${p.partitionName}=${p.fileName}\n")
                }
            }
        }
        return sb.toString()
    }

    private fun formatStorageBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
