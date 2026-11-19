package com.example.model

/**
 * Operating mode for loader resolution and storage usage.
 */
enum class AppOperatingMode(val label: String, val description: String) {
    ONLINE(
        "Online Mode",
        "Zero storage footprint. Fetches manifest live and streams loaders on-demand from repository."
    ),
    OFFLINE(
        "Offline Mode",
        "Downloads full loader package and manifest to local storage. 100% disconnected operation."
    )
}

/**
 * Metadata entry for a single Qualcomm Firehose / MTK loader in model_map.json.
 */
data class LoaderMetadataEntry(
    val id: String,
    val brand: String,
    val model: String,
    val soc: String,
    val platform: String = "qualcomm", // "qualcomm" or "mtk"
    val storageType: String = "eMMC",   // "eMMC" or "UFS"
    val msmHwidHex: String,            // e.g. "0x0005E0E1", "0x000E10E1"
    val pkHashHex: String,             // e.g. "3be970a8579cac80", "*" for generic
    val fileName: String,              // e.g. "prog_emmc_firehose_8953_ddr.mbn"
    val relativePath: String,          // e.g. "Xiaomi/Redmi_Note_4/prog_emmc_firehose_8953_ddr.mbn"
    val fileSize: Long = 0L,
    val sha256: String = "",
    val minDaVersion: String = ""
)

/**
 * Top-level structure of model_map.json.
 */
data class LoaderManifest(
    val version: Int = 101,
    val updatedAt: String = "2026-11-19T12:00:00Z",
    val primarySource: String = "github_raw",
    val baseUrl: String = "https://raw.githubusercontent.com/bkerler/Loaders/master/",
    val entries: List<LoaderMetadataEntry> = emptyList()
)

/**
 * Result of attempting to resolve a loader for a connected device.
 */
sealed class LoaderResolutionResult {
    data class FoundLocal(
        val entry: LoaderMetadataEntry,
        val localFilePath: String,
        val fileBytes: ByteArray
    ) : LoaderResolutionResult()

    data class FoundRemoteStream(
        val entry: LoaderMetadataEntry,
        val remoteUrl: String
    ) : LoaderResolutionResult()

    data class NotFound(
        val msmHwid: String,
        val pkHash: String,
        val searchedPaths: List<String>,
        val message: String
    ) : LoaderResolutionResult()
}

/**
 * Status of offline package sync.
 */
data class SyncProgressState(
    val isSyncing: Boolean = false,
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val currentFileName: String = "",
    val percentage: Float = 0f,
    val error: String? = null,
    val lastSyncTime: String = "Never"
)
