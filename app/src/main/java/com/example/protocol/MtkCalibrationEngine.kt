package com.example.protocol

import com.example.model.LogLevel
import com.example.model.OperationProgress
import com.example.model.PartitionEntry
import com.example.model.TerminalLog
import com.example.storage.BackupStorageManager
import java.io.File

/**
 * MediaTek Calibration, Bootloader SecCfg & NVRAM Engine
 * Real hardware operations only - no simulation.
 */
class MtkCalibrationEngine(
    private val usb: TargetPhoneUsbManager,
    private val flashEngine: MtkFlashEngine,
    private val dumpEngine: MtkDumpEngine,
    private val storageManager: BackupStorageManager,
    private val logCallback: (TerminalLog) -> Unit,
    private val progressCallback: (OperationProgress) -> Unit
) {

    companion object {
        const val VBMETA_ENABLE_ALL = 0
        const val VBMETA_DISABLE_VERITY = 1
        const val VBMETA_DISABLE_VERIFICATION = 2
        const val VBMETA_DISABLE_BOTH = 3
        const val AVB_FLAGS_OFFSET = 0x78

        /**
         * Exact port of mtk_da_handler.py -> patch_vbmeta(vbmeta, vbmode).
         * Sets 4-byte Big-Endian AVB flags at offset 0x78 (0..3).
         */
        fun patchVbmetaBytes(vbmeta: ByteArray, vbMode: Int): ByteArray? {
            if (vbMode !in 0..3) return null
            if (vbmeta.size < AVB_FLAGS_OFFSET + 4) return null
            val patched = vbmeta.copyOf()
            patched[AVB_FLAGS_OFFSET] = ((vbMode ushr 24) and 0xFF).toByte()
            patched[AVB_FLAGS_OFFSET + 1] = ((vbMode ushr 16) and 0xFF).toByte()
            patched[AVB_FLAGS_OFFSET + 2] = ((vbMode ushr 8) and 0xFF).toByte()
            patched[AVB_FLAGS_OFFSET + 3] = (vbMode and 0xFF).toByte()
            return patched
        }

        /**
         * Parses active slot suffix ("_a", "_b", or "") from misc/para bytes at 0x800..0x802,
         * matching mtk_da_handler.py -> get_current_slot().
         */
        fun parseSlotFromMisc(miscOrParaBytes: ByteArray): String {
            if (miscOrParaBytes.size < 0x802) return ""
            val b0 = miscOrParaBytes[0x800]
            val b1 = miscOrParaBytes[0x801]
            if (b0 == 0.toByte() && b1 == 0.toByte()) return ""
            val raw = String(byteArrayOf(b0, b1), Charsets.US_ASCII)
            return if (raw == "_a" || raw == "_b") raw else ""
        }
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logCallback(TerminalLog("", message, level))
    }

    suspend fun detectCurrentSlot(
        partitions: List<PartitionEntry>,
        readPartitionBytes: suspend (PartitionEntry) -> ByteArray?
    ): String {
        val miscPart = partitions.find { it.partitionName.equals("misc", ignoreCase = true) }
            ?: partitions.find { it.partitionName.equals("para", ignoreCase = true) }
        if (miscPart != null) {
            val slicePart = miscPart.copy(sizeBytes = minOf(miscPart.sizeBytes.coerceAtLeast(4096L), 4096L))
            val data = readPartitionBytes(slicePart)
            if (data != null && data.size >= 0x802) {
                val parsed = parseSlotFromMisc(data)
                if (parsed.isNotEmpty()) {
                    log("[SLOT] Active A/B slot from ${miscPart.partitionName}: $parsed", LogLevel.INFO)
                    return parsed
                }
            }
        }
        val hasSlotA = partitions.any { it.partitionName.equals("vbmeta_a", ignoreCase = true) }
        val hasSlotB = partitions.any { it.partitionName.equals("vbmeta_b", ignoreCase = true) }
        if (hasSlotA && !hasSlotB) return "_a"
        if (hasSlotB && !hasSlotA) return "_b"
        if (hasSlotA && hasSlotB) {
            log("[SLOT] misc/para empty on dual-slot GPT; defaulting to slot _a.", LogLevel.INFO)
            return "_a"
        }
        return ""
    }

    /**
     * Unlocks bootloader by writing a valid SecCfg V4 unlock payload to seccfg partition.
     */
    suspend fun unlockBootloader(
        partitions: List<PartitionEntry>,
        isSimulation: Boolean = false,
        forceIsUfs: Boolean? = null,
        xmlEngine: MtkXmlFlashEngine? = null
    ): Result<Boolean> {
        log("==================================================", LogLevel.WARNING)
        log(">>> [BOOTLOADER UNLOCK] Generating SecCfg Unlock Payload", LogLevel.WARNING)
        log("==================================================", LogLevel.INFO)

        val seccfgPart = partitions.find { it.partitionName.lowercase() == "seccfg" }
            ?: return Result.failure(IllegalArgumentException("seccfg partition not found in partition list"))

        log("[SECCFG] Target partition: '${seccfgPart.partitionName}' @ ${seccfgPart.linearStartAddrHex}", LogLevel.INFO)

        val v4UnlockPayload = MtkSecCfgEngine.createV4Payload(unlock = true, critical = true)
        log("[+] Generated SecCfg V4 Payload (${v4UnlockPayload.size} bytes, State: 0x03 UNLOCKED)", LogLevel.INFO)

        val verify = MtkSecCfgEngine.parseSecCfg(v4UnlockPayload)
        log("[+] Payload Validation: ${verify.message} [Engine: ${verify.hwType}]", LogLevel.SUCCESS)

        val isUfs = forceIsUfs ?: (seccfgPart.region.contains("UFS", true) || seccfgPart.region.contains("LU", true))
        val (storageType, partSection) = flashEngine.resolveStorageTarget(
            partitionName = seccfgPart.partitionName,
            region = seccfgPart.region,
            isUfs = isUfs
        )

        val success = if (xmlEngine != null) {
            flashEngine.flashXml(seccfgPart, v4UnlockPayload, xmlEngine)
        } else {
            flashEngine.flashXFlash(
                partition = seccfgPart,
                data = v4UnlockPayload,
                storageType = storageType,
                partType = partSection
            )
        }

        if (success) {
            log("==================================================", LogLevel.SUCCESS)
            log("BOOTLOADER UNLOCK COMPLETE: SecCfg partition flashed [ 100% OK ].", LogLevel.SUCCESS)
            log("Device Bootloader State: [ UNLOCKED ]", LogLevel.SUCCESS)
            log("==================================================", LogLevel.SUCCESS)
            return Result.success(true)
        } else {
            log("[-] Failed to write SecCfg unlock payload.", LogLevel.ERROR)
            return Result.failure(IllegalStateException("SecCfg unlock payload write failed"))
        }
    }

    /**
     * Locks bootloader by writing a valid SecCfg V4 lock payload.
     */
    suspend fun lockBootloader(
        partitions: List<PartitionEntry>,
        isSimulation: Boolean = false,
        forceIsUfs: Boolean? = null,
        xmlEngine: MtkXmlFlashEngine? = null
    ): Result<Boolean> {
        log("==================================================", LogLevel.WARNING)
        log(">>> [BOOTLOADER LOCK] Generating SecCfg Lock Payload", LogLevel.WARNING)
        log("==================================================", LogLevel.INFO)

        val seccfgPart = partitions.find { it.partitionName.lowercase() == "seccfg" }
            ?: return Result.failure(IllegalArgumentException("seccfg partition not found in partition list"))

        val v4LockPayload = MtkSecCfgEngine.createV4Payload(unlock = false, critical = false)
        log("[+] Generated SecCfg V4 Payload (${v4LockPayload.size} bytes, State: 0x01 LOCKED)", LogLevel.INFO)

        val verify = MtkSecCfgEngine.parseSecCfg(v4LockPayload)
        log("[+] Payload Validation: ${verify.message}", LogLevel.INFO)

        val isUfs = forceIsUfs ?: (seccfgPart.region.contains("UFS", true) || seccfgPart.region.contains("LU", true))
        val (storageType, partSection) = flashEngine.resolveStorageTarget(
            partitionName = seccfgPart.partitionName,
            region = seccfgPart.region,
            isUfs = isUfs
        )

        val success = if (xmlEngine != null) {
            flashEngine.flashXml(seccfgPart, v4LockPayload, xmlEngine)
        } else {
            flashEngine.flashXFlash(
                partition = seccfgPart,
                data = v4LockPayload,
                storageType = storageType,
                partType = partSection
            )
        }

        if (success) {
            log("==================================================", LogLevel.SUCCESS)
            log("BOOTLOADER LOCK COMPLETE: SecCfg locked successfully.", LogLevel.SUCCESS)
            log("==================================================", LogLevel.SUCCESS)
            return Result.success(true)
        } else {
            return Result.failure(IllegalStateException("SecCfg lock write failed"))
        }
    }

    /**
     * Performs NVRAM / NVDATA calibration backup bundle.
     * Real read operations via MtkDumpEngine.
     */
    suspend fun backupNvramBundle(
        chipPlatform: String,
        partitions: List<PartitionEntry>,
        forceIsUfs: Boolean? = null,
        xmlEngine: MtkXmlFlashEngine? = null
    ): Result<String> {
        log("==================================================", LogLevel.INFO)
        log(">>> [NVRAM CALIBRATION BACKUP] Extracting Radio & IMEI Partitions", LogLevel.INFO)
        log("==================================================", LogLevel.INFO)

        val nvNames = setOf(
            "nvram", "nvdata", "nvcfg", "protect1", "protect2",
            "protect_f", "protect_s", "proinfo", "persist", "seccfg", "secro", "sec1"
        )
        val allNv = partitions.filter { it.isProtectedNv || it.partitionName.lowercase() in nvNames }
        val checkedNv = allNv.filter { it.isSelectedForFlashing }
        val targets = checkedNv.ifEmpty { allNv }

        if (targets.isEmpty()) {
            return Result.failure(IllegalStateException("No NV partitions found in partition list"))
        }

        val sessionFolder = "${chipPlatform}_NVRAM_Backup_${System.currentTimeMillis()}"
        val targetDir = File(storageManager.getBackupDirectory(), sessionFolder)
        targetDir.mkdirs()

        log("Found ${targets.size} NV calibration partitions to archive.", LogLevel.INFO)

        val dumpedPaths = mutableListOf<String>()
        for ((idx, part) in targets.withIndex()) {
            val outFile = File(targetDir, "${part.partitionName}.bin")
            log("[${idx + 1}/${targets.size}] Reading ${part.partitionName} (${part.formattedSize})...", LogLevel.INFO)

            val isUfs = forceIsUfs ?: (isUfsChip(chipPlatform) || part.region.contains("UFS", true) || part.region.contains("LU", true))
            val (storageType, partSection) = flashEngine.resolveStorageTarget(part.partitionName, part.region, isUfs)

            val res = dumpEngine.dumpPartition(
                partition = part,
                outputFile = outFile,
                storageType = storageType,
                partType = partSection,
                useXFlash = (xmlEngine == null),
                xmlEngine = xmlEngine
            )
            if (res.isSuccess) {
                dumpedPaths.add(outFile.absolutePath)
                log("[+] Saved: ${outFile.name} (${outFile.length() / 1024} KB)", LogLevel.SUCCESS)
            } else {
                log("[-] Failed to dump ${part.partitionName}", LogLevel.WARNING)
            }
        }

        storageManager.generateScatterFile(chipPlatform, partitions, sessionFolder)
        log("[+] NVRAM Calibration archive complete: ${targetDir.absolutePath}", LogLevel.SUCCESS)
        return Result.success(targetDir.absolutePath)
    }

    private fun isUfsChip(platform: String): Boolean {
        val p = platform.lowercase()
        return p.contains("6833") || p.contains("6877") || p.contains("6893") ||
                p.contains("6885") || p.contains("6853") || p.contains("6873") ||
                p.contains("6983") || p.contains("dimensity")
    }
}