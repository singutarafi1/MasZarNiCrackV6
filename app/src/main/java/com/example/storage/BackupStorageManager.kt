package com.example.storage

import android.content.Context
import android.os.Environment
import com.example.model.PartitionEntry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupStorageManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private var customBackupPath: String? = null

    fun setCustomBackupPath(path: String) {
        customBackupPath = path
    }

    fun getBackupDirectory(): File {
        if (!customBackupPath.isNullOrBlank()) {
            val customDir = File(customBackupPath!!)
            if (isDirWritable(customDir)) {
                return customDir
            }
        }

        // On Android 10+ (API 29+), Scoped Storage restricts direct public storage writes unless MANAGE_EXTERNAL_STORAGE is granted
        val hasAllFilesAccess = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            Environment.isExternalStorageLegacy()
        } else {
            true
        }

        if (hasAllFilesAccess) {
            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val candidate = File(publicDownloads, "MTK_Backups")
                if (isDirWritable(candidate)) {
                    return candidate
                }
            } catch (_: Exception) {
                // Fallback on Scoped Storage / EACCES
            }
        }

        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir
        val backupDir = File(baseDir, "MTK_Backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        return backupDir
    }

    private fun isDirWritable(dir: File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            val probe = File(dir, ".probe_${System.currentTimeMillis()}.tmp")
            val created = probe.createNewFile()
            if (created) {
                probe.delete()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun savePartitionDump(
        partitionName: String,
        data: ByteArray,
        sha256: String,
        sessionFolder: String? = null,
        customFileName: String? = null
    ): String {
        val baseDir = getBackupDirectory()
        val targetDir = if (!sessionFolder.isNullOrBlank()) {
            val sub = File(baseDir, sessionFolder)
            if (!sub.exists()) sub.mkdirs()
            sub
        } else {
            baseDir
        }

        val fileName = customFileName ?: "${partitionName}.bin"
        val outFile = File(targetDir, fileName)

        try {
            if (outFile.exists()) outFile.delete()
            FileOutputStream(outFile).use { fos ->
                fos.write(data)
            }
            val checksumFile = File(targetDir, "${fileName}.sha256")
            checksumFile.writeText("$sha256  $fileName\n")
            return outFile.absolutePath
        } catch (_: Exception) {
            // Guaranteed fallback to app-specific storage (Never blocked by Scoped Storage on any Android OS)
            val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "MTK_Backups")
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            val fallbackFile = File(fallbackDir, fileName)
            if (fallbackFile.exists()) fallbackFile.delete()
            FileOutputStream(fallbackFile).use { fos ->
                fos.write(data)
            }
            val fallbackCs = File(fallbackDir, "${fileName}.sha256")
            fallbackCs.writeText("$sha256  $fileName\n")
            return fallbackFile.absolutePath
        }
    }

    fun generateScatterFile(
        platform: String,
        partitions: List<PartitionEntry>,
        sessionFolder: String? = null
    ): String {
        val baseDir = getBackupDirectory()
        val targetDir = if (!sessionFolder.isNullOrBlank()) {
            val sub = File(baseDir, sessionFolder)
            if (!sub.exists()) sub.mkdirs()
            sub
        } else {
            baseDir
        }

        val fileName = "${platform}_Android_scatter.txt"
        val scatterFile = File(targetDir, fileName)

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
        sb.append("      storage: EMMC\n")
        sb.append("      boot_channel: MSDC_0\n")
        sb.append("      block_size: 0x20000\n\n")
        sb.append("############################################################################################################\n")
        sb.append("#\n")
        sb.append("#  Layout Setting\n")
        sb.append("#\n")
        sb.append("############################################################################################################\n")

        for (p in partitions) {
            sb.append("- partition_index: SYS${p.partitionIndex}\n")
            sb.append("  partition_name: ${p.partitionName}\n")
            sb.append("  file_name: ${p.fileName.ifBlank { "NONE" }}\n")
            sb.append("  is_download: ${if (p.isDownload) "true" else "false"}\n")
            sb.append("  type: NORMAL_ROM\n")
            sb.append("  linear_start_addr: ${p.linearStartAddrHex}\n")
            sb.append("  physical_start_addr: ${p.physicalStartAddrHex}\n")
            sb.append("  partition_size: ${p.partitionSizeHex}\n")
            sb.append("  region: ${p.region}\n")
            sb.append("  storage: HW_STORAGE_EMMC\n")
            sb.append("  boundary_check: true\n")
            sb.append("  is_reserved: false\n")
            sb.append("  operation_type: UPDATE\n")
            sb.append("  reserve: 0x00\n\n")
        }

        val content = sb.toString()
        try {
            if (scatterFile.exists()) scatterFile.delete()
            scatterFile.writeText(content)
            return scatterFile.absolutePath
        } catch (_: Exception) {
            // Guaranteed fallback to app-specific storage (Never blocked by Scoped Storage on any Android OS)
            val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "MTK_Backups")
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            val fallbackFile = File(fallbackDir, fileName)
            if (fallbackFile.exists()) fallbackFile.delete()
            fallbackFile.writeText(content)
            return fallbackFile.absolutePath
        }
    }

    fun listBackups(): List<File> {
        val dir = getBackupDirectory()
        return dir.walkTopDown().filter { it.extension == "bin" || it.extension == "txt" }.toList()
            .sortedByDescending { it.lastModified() }
    }
}
