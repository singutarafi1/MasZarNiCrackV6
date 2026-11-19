package com.example.protocol

/**
 * MediaTek Chipset Configuration Database
 * Faithfully ported from Python mtkclient: mtkclient/config/brom_config.py
 */

enum class DaMode(val modeCode: Int) {
    LEGACY(3),
    XFLASH(5),
    XML(6)
}

data class BlacklistEntry(
    val address: Long,
    val value: Long
)

data class ChipConfig(
    val hwCode: Int,
    val name: String,
    val description: String = "",
    val var1: Int = 0,
    val watchdog: Long = 0x10007000L,
    val uart: Long = 0x11002000L,
    val bromPayloadAddr: Long = 0x100A00L,
    val daPayloadAddr: Long = 0x201000L,
    val plPayloadAddr: Long = 0x40200000L,
    val gcpuBase: Long? = 0x10050000L,
    val sejBase: Long? = 0x1000A000L,
    val dxccBase: Long? = 0x10210000L,
    val cqdmaBase: Long? = 0x10212000L,
    val apDmaMem: Long? = 0x11000000L + 0x1A0L,
    val blacklist: List<BlacklistEntry> = emptyList(),
    val dacode: Int = hwCode,
    val damode: DaMode = DaMode.XFLASH,
    val loader: String = "",
    val miscLock: Long? = null,
    val efuseAddr: Long? = null,
    val meidAddr: Long? = null,
    val socidAddr: Long? = null,
    val provAddr: Long? = null,
    val ssrBase: Long? = null,
    val ssrClkBase: Long? = null,
    val has64Bit: Boolean = false,
    val isIot: Boolean = false,
    val isPatchedBrom: Boolean = false,
    val bromRegisterAccess: List<Pair<Long, Long>>? = null,
    val sendPtr: List<Pair<Long, Long>>? = null,
    val blacklistCount: Int = 0x0A,
    val ctrlBuffer: Long? = null,
    val cmdHandler: Long? = null
)

object MtkChipConfigDatabase {

    private val configs = mutableMapOf<Int, ChipConfig>()

    init {
        MtkChipConfigData.registerAll(::register)
    }

    private fun register(config: ChipConfig) {
        configs[config.hwCode] = config
    }

    fun findConfig(hwCode: Int): ChipConfig? {
        val hw = hwCode and 0xFFFF
        configs[hwCode]?.let { return it }
        configs[hw]?.let { return it }
        val aliasHw = when (hw) {
            0x0983, 0x6983 -> 0x0907
            0x0985, 0x6985 -> 0x1296
            0x6989 -> 0x1236
            else -> hw
        }
        return configs[aliasHw]
    }

    fun findConfigByName(name: String): ChipConfig? {
        val cleanName = name.replace("MT", "").replace("/", "").lowercase()
        return configs.values.firstOrNull {
            val cfgName = it.name.replace("MT", "").replace("/", "").lowercase()
            cfgName.contains(cleanName) || cleanName.contains(cfgName)
        }
    }

    fun getAllConfigs(): List<ChipConfig> = configs.values.toList()

    /**
     * Exact port of mtk_config.py -> default_values() for unknown hwCodes.
     */
    fun createDefaultFallback(hwCode: Int): ChipConfig = ChipConfig(
        hwCode = hwCode,
        name = "MT%04X".format(hwCode and 0xFFFF),
        description = "Unknown MediaTek SoC (0x%04X)".format(hwCode and 0xFFFF),
        var1 = 0x0A,
        watchdog = 0x10007000L,
        uart = 0x11002000L,
        bromPayloadAddr = 0x100A00L,
        daPayloadAddr = 0x200000L,
        plPayloadAddr = 0x40200000L,
        gcpuBase = null,
        sejBase = null,
        dxccBase = null,
        cqdmaBase = null,
        apDmaMem = 0x11000000L + 0x1A0L,
        dacode = hwCode,
        damode = DaMode.LEGACY,
        isIot = false
    )

    /**
     * Exact port of mtk_config.py -> get_watchdog_addr()
     */
    fun getWatchdogAddrAndValue(config: ChipConfig): Pair<Long, Long> {
        val wd = config.watchdog
        val hw = config.hwCode and 0xFFFF
        val valToWrite = when (wd) {
            0x10007000L -> 0x22000064L
            0x10212000L -> 0x22000000L
            0x10211000L -> 0x22000064L
            0x10007400L -> 0x22000000L
            0x1C007000L -> 0x22000064L // MT6983 (Dimensity 9000), MT6985 (Dimensity 9200)
            0x1C00A000L -> 0x22000064L // MT6878 (Dimensity 7300)
            0x1C00B000L -> 0x22000064L // MT6989W (Dimensity 9300)
            0xC0000000L -> 0x2264L
            0xA0030000L -> 0x2200L
            0x2200L -> when (hw) {
                0x6276, 0x8163 -> 0x610C0000L
                0x6251, 0x6516 -> 0x80030000L
                0x6255 -> 0x701E0000L
                else -> 0x70025000L
            }
            else -> 0x22000064L
        }
        return wd to valToWrite
    }
}
