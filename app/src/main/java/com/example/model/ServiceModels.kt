package com.example.model

enum class ThemeMode {
    DARK,
    LIGHT
}

enum class ThemeDesign(
    val displayName: String,
    val subtitle: String
) {
    CYBER_NEON(
        displayName = "Cyber Neon",
        subtitle = "Electric Cyan & Indigo Glow"
    ),
    EMERALD_MATRIX(
        displayName = "Emerald Matrix",
        subtitle = "Phosphor Green Terminal Grid"
    ),
    CRIMSON_FORGE(
        displayName = "Crimson Forge",
        subtitle = "Volcanic Red & Amber Lab"
    )
}

enum class AppFontSize(
    val displayName: String,
    val scaleFactor: Float,
    val consoleSp: Float
) {
    COMPACT(
        displayName = "Small",
        scaleFactor = 0.90f,
        consoleSp = 11.0f
    ),
    MEDIUM(
        displayName = "Medium",
        scaleFactor = 1.00f,
        consoleSp = 12.5f
    ),
    LARGE(
        displayName = "Large",
        scaleFactor = 1.15f,
        consoleSp = 14.5f
    )
}

enum class ToolScreenMode(
    val badgeTitle: String,
    val headerTitle: String
) {
    ADB_INFO(
        badgeTitle = "[ADB] Info",
        headerTitle = "ADB Mode — Device & System Info"
    ),
    ADB_UNLOCK(
        badgeTitle = "[ADB] Unlock",
        headerTitle = "ADB Mode — FRP & Unlock Helpers"
    ),
    ADB_SERVICE(
        badgeTitle = "[ADB] Service",
        headerTitle = "ADB Mode — Debloat & System Service"
    ),
    FASTBOOT_FLASH(
        badgeTitle = "[FB] Flash",
        headerTitle = "Fastboot Flash (Scatter / XML / .bat / APP)"
    ),
    FASTBOOT_INFO(
        badgeTitle = "[FB] Info",
        headerTitle = "Fastboot Mode — Bootloader & OEM Info"
    ),
    FASTBOOT_SERVICE(
        badgeTitle = "[FB] Service",
        headerTitle = "Fastboot Mode — Wipe, Slots & Partitions"
    ),
    FASTBOOT_UNLOCK(
        badgeTitle = "[FB] Unlock",
        headerTitle = "Fastboot Mode — Bootloader & FRP Unlock"
    ),
    CMD_TERMINAL(
        badgeTitle = "[CMD] Terminal",
        headerTitle = "Dedicated Command Shell Terminal"
    ),
    SETTINGS(
        badgeTitle = "Setting",
        headerTitle = "App Settings & Theme Studio"
    ),
    ABOUT(
        badgeTitle = "About",
        headerTitle = "About & Command Reference"
    );

    val isAdbSection: Boolean
        get() = this == ADB_INFO || this == ADB_UNLOCK || this == ADB_SERVICE

    val isFastbootSection: Boolean
        get() = this == FASTBOOT_FLASH ||
            this == FASTBOOT_INFO ||
            this == FASTBOOT_SERVICE ||
            this == FASTBOOT_UNLOCK
}

enum class ServiceBrand(val displayName: String, val shortTag: String) {
    UNIVERSAL("Universal (All Android 5–16)", "Universal"),
    XIAOMI("Xiaomi / Redmi / Poco", "Xiaomi"),
    HUAWEI("Huawei / Honor (HiSilicon)", "Huawei"),
    VIVO("Vivo / iQOO (BBK)", "Vivo"),
    OPPO_ONEPLUS("Oppo / Realme / OnePlus", "Oplus"),
    SAMSUNG("Samsung Galaxy", "Samsung"),
    MOTO_PIXEL("Google Pixel / Motorola", "Moto/Pixel"),
    TRANSSION("Infinix / Tecno / Itel", "Transsion"),
    ASUS_SONY("Asus ROG / Sony Xperia", "Asus/Sony")
}

enum class ActionGroup(val displayName: String) {
    INFO("Info"),
    UNLOCK("Unlock"),
    SERVICE("Service")
}

data class ServiceActionItem(
    val id: String,
    val title: String,
    val command: String,
    val description: String = "",
    val brand: ServiceBrand,
    val group: ActionGroup,
    val isAdb: Boolean = false
)

enum class UsbProtocolType(val label: String) {
    ADB("ADB (FF:42:01)"),
    FASTBOOT("FASTBOOT (FF:42:03)"),
    VENDOR_PORT("USB PORT")
}

data class UsbPortInfo(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val vendorHex: String,
    val productHex: String,
    val productName: String,
    val serialNumber: String,
    val interfaceId: Int,
    val endpointInAddress: Int,
    val endpointOutAddress: Int,
    val maxPacketSize: Int,
    val hasPermission: Boolean,
    val protocolType: UsbProtocolType
)

enum class FlashFileType(val label: String) {
    SCATTER("MTK SCATTER"),
    RAWPROGRAM("QC FIREHOSE XML"),
    UPDATE_APP("UPDATE.APP"),
    SCRIPT("SCRIPT (.bat/.sh)"),
    IMAGE("IMAGE (.img/.bin)"),
    ARCHIVE("ZIP ARCHIVE"),
    RAW("RAW FILE")
}

enum class FlashStepStatus(val label: String) {
    READY("READY"),
    QUEUED("QUEUED"),
    FLASHING("FLASHING"),
    DONE("DONE"),
    SKIPPED("SKIP"),
    ERROR("ERROR")
}

data class ParsedFlashStep(
    val id: String,
    val order: Int,
    val command: String,
    val targetPartition: String,
    val sourceName: String,
    val sourceUriString: String? = null,
    val dataOffset: Long = 0L,
    val dataSize: Long = 0L,
    val startAddressHex: String = "",
    val isSparse: Boolean = false,
    val isFlashable: Boolean = true,
    val status: FlashStepStatus = FlashStepStatus.READY,
    val progress: Float = 0f,
    val enabled: Boolean = true
)

data class FirmwareInfoSummary(
    val formatLabel: String,
    val chipsetOrPlatform: String = "Generic Android",
    val projectOrBoard: String = "Unknown",
    val storageType: String = "EMMC / UFS",
    val sectorSizeBytes: Int = 4096,
    val isFolderPackage: Boolean = false,
    val resolvedImageCount: Int = 0,
    val missingImageCount: Int = 0,
    val largePartitionNames: List<String> = emptyList(),
    val extraMetadataLines: List<String> = emptyList()
)

data class SelectedFlashFile(
    val uriString: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val formattedSize: String,
    val fileType: FlashFileType,
    val magicHeaderHex: String,
    val isSparseImage: Boolean,
    val sparseInfo: String,
    val steps: List<ParsedFlashStep>,
    val firmwareInfo: FirmwareInfoSummary = FirmwareInfoSummary(formatLabel = fileType.label)
)

enum class ServiceLogLevel {
    INIT,
    CMD,
    USB,
    OKAY,
    INFO,
    WARN,
    FAIL
}

data class ConsoleLogEntry(
    val id: Long,
    val timestamp: String,
    val tag: String,
    val message: String,
    val level: ServiceLogLevel
)

data class TerminalLine(
    val id: Long,
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false
)
