package com.example.protocol

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MediaTek DA V6 XML Protocol Command Builder & Constants
 * Direct, faithful Kotlin port of Python mtkclient:
 * - mtkclient/Library/DA/xmlflash/xml_cmd.py
 * - mtkclient/Library/DA/xmlflash/xml_param.py
 */
object MtkXmlCmd {

    const val MAGIC: Int = 0xFEEEEEEF.toInt()
    const val MAGIC_LONG: Long = 0xFEEEEEEFL
    const val MAX_NODE_VALUE_LENGTH = 256
    const val MAX_ADDRESS_LENGTH = 9
    const val MAX_XML_DATA_LENGTH = 0x200000

    object DataType {
        const val DT_PROTOCOL_FLOW = 1
        const val DT_MESSAGE = 2
    }

    object ChecksumAlgorithm {
        const val NONE = "NONE"
        const val USB = "USB"
        const val STORAGE = "STORAGE"
        const val USB_STORAGE = "USB-STORAGE"
    }

    object LogLevelXml {
        const val TRACE = "TRACE"
        const val DEBUG = "DEBUG"
        const val INFO = "INFO"
        const val WARN = "WARN"
        const val ERROR = "ERROR"
    }

    object LogChannel {
        const val USB = "USB"
        const val UART = "UART"
    }

    object BatterySetting {
        const val YES = "YES"
        const val NO = "NO"
        const val AUTO_DETECT = "AUTO-DETECT"
    }

    object FtSystemOSE {
        const val OS_WIN = "WINDOWS"
        const val OS_LINUX = "LINUX"
    }

    object BootModes {
        const val FASTBOOT = "FASTBOOT"
        const val META = "META"
        const val TESTMODE = "ANDROID-TEST-MODE"
    }

    object ShutDownModes {
        const val NORMAL = 0
        const val HOME_SCREEN = 1
        const val FASTBOOT = 2
        const val TEST = 3
        const val META = 4
    }

    internal fun toHex(value: Long): String =
        "0x" + java.lang.Long.toUnsignedString(value, 16)

    fun createCmd(
        cmd: String,
        content: Map<String, List<String>>? = null,
        version: String = "1.0"
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><da><version>")
            .append(version)
            .append("</version><command>CMD:")
            .append(cmd)
            .append("</command>")
        if (content != null) {
            for ((item, subitems) in content) {
                sb.append("<").append(item).append(">")
                for (sub in subitems) {
                    sb.append(sub)
                }
                sb.append("</").append(item).append(">")
            }
        }
        sb.append("</da>")
        return sb.toString()
    }

    // DA1 Commands

    fun cmdNotifyInitHw(): String = createCmd("NOTIFY-INIT-HW")

    fun cmdSecuritySetFlashPolicy(
        hostOffset: Long = 0x8000000L,
        length: Long = 0x100000L
    ): String = createCmd(
        "SECURITY-SET-FLASH-POLICY",
        mapOf("arg" to listOf("<source_file>MEM://${toHex(hostOffset)}:${toHex(length)}</source_file>"))
    )

    fun cmdSecuritySetAllinoneSignature(
        hostOffset: Long = 0x8000000L,
        length: Long = 0x100000L,
        filename: String? = null
    ): String {
        val src = filename ?: "MEM://${toHex(hostOffset)}:${toHex(length)}"
        return createCmd(
            "SECURITY-SET-ALLINONE-SIGNATURE",
            mapOf("arg" to listOf("<source_file>$src</source_file>"))
        )
    }

    fun cmdBootTo(
        atAddr: Long = 0x40000000L,
        jmpAddr: Long = 0x40000000L,
        hostOffset: Long = 0x7FE83C09A04CL,
        length: Long = 0x50C78L
    ): String = createCmd(
        "BOOT-TO",
        mapOf(
            "arg" to listOf(
                "<at_address>${toHex(atAddr)}</at_address>",
                "<jmp_address>${toHex(jmpAddr)}</jmp_address>",
                "<source_file>MEM://${toHex(hostOffset)}:${toHex(length)}</source_file>"
            )
        )
    )

    fun cmdSetRuntimeParameter(
        checksumLevel: String = ChecksumAlgorithm.NONE,
        batteryExist: String = BatterySetting.AUTO_DETECT,
        daLogLevel: String = LogLevelXml.INFO,
        logChannel: String = LogChannel.UART,
        systemOs: String = FtSystemOSE.OS_LINUX,
        version: String = "1.1",
        initializeDram: Boolean = true
    ): String {
        val dram = if (initializeDram) "YES" else "NO"
        return createCmd(
            "SET-RUNTIME-PARAMETER",
            linkedMapOf(
                "arg" to listOf(
                    "<checksum_level>$checksumLevel</checksum_level>",
                    "<battery_exist>$batteryExist</battery_exist>",
                    "<da_log_level>$daLogLevel</da_log_level>",
                    "<log_channel>$logChannel</log_channel>",
                    "<system_os>$systemOs</system_os>"
                ),
                "adv" to listOf(
                    "<initialize_dram>$dram</initialize_dram>"
                )
            ),
            version = version
        )
    }

    fun cmdHostSupportedCommands(
        hostCapability: String = "CMD:DOWNLOAD-FILE^1@CMD:FILE-SYS-OPERATION^1@CMD:PROGRESS-REPORT^1@CMD:UPLOAD-FILE^1@"
    ): String = createCmd(
        "HOST-SUPPORTED-COMMANDS",
        mapOf("arg" to listOf("<host_capability>$hostCapability</host_capability>"))
    )

    fun cmdRamTest(
        function: String = "FLIP",
        startAddress: Long = 0x4000000L,
        length: Long = 0x100000L,
        repeat: Long = 0xAL
    ): String {
        val content = if (function == "FLIP") {
            mapOf(
                "arg" to listOf(
                    "<function>FLIP</function>",
                    "<start_address>${toHex(startAddress)}</start_address>",
                    "<length>${toHex(length)}</length>",
                    "<repeat>${toHex(repeat)}</repeat>"
                )
            )
        } else {
            mapOf(
                "arg" to listOf(
                    "<function>CALIBRATION</function>",
                    "<target_file>ms-appdata:///local/calib.bin</target_file>"
                )
            )
        }
        return createCmd("RAM-TEST", content)
    }

    fun cmdDramRepair(memOffset: Long = 0x10000L, memLength: Long = 0x1000L): String =
        createCmd(
            "DRAM-REPAIR",
            mapOf(
                "arg" to listOf(
                    "<param_file>D:/dram.info</param_file>",
                    "<target_file>MEM://$memOffset:$memLength</target_file>"
                )
            )
        )

    // DA2 Commands

    fun cmdReadPartitionTable(
        hostMemOffset: Long = 0x7FE83C538720L,
        length: Long = 0x200000L
    ): String = createCmd(
        "READ-PARTITION-TABLE",
        mapOf("arg" to listOf("<target_file>MEM://${toHex(hostMemOffset)}:${toHex(length)}</target_file>"))
    )

    fun cmdCanHigherUsbSpeed(
        hostMemOffset: Long = 0x7FE8463ED240L,
        length: Long = 0x40L
    ): String = createCmd(
        "CAN-HIGHER-USB-SPEED",
        mapOf("arg" to listOf("<target_file>MEM://${toHex(hostMemOffset)}:${toHex(length)}</target_file>"))
    )

    fun cmdWriteEfuse(): String = createCmd(
        "WRITE-EFUSE",
        mapOf("arg" to listOf("<source_file>ms-appdata:///local/efuse.xml</source_file>"))
    )

    fun cmdReadEfuse(): String = createCmd(
        "READ-EFUSE",
        mapOf("arg" to listOf("<target_file>ms-appdata:///local/efuse.xml</target_file>"))
    )

    fun cmdGetDevInfo(
        hostMemOffset: Long = 0x8000000L,
        length: Long = 0x100000L
    ): String = createCmd(
        "SECURITY-GET-DEV-FW-INFO",
        mapOf("arg" to listOf("<target_file>MEM://${toHex(hostMemOffset)}:${toHex(length)}</target_file>"))
    )

    fun cmdGetHwInfo(
        hostMemOffset: Long = 0x7FE83C138700L,
        length: Long = 0x200000L
    ): String = createCmd(
        "GET-HW-INFO",
        mapOf("arg" to listOf("<target_file>MEM://${toHex(hostMemOffset)}:${toHex(length)}</target_file>"))
    )

    fun cmdSetBootMode(
        mode: String = BootModes.TESTMODE,
        adb: Boolean = true,
        mobileLog: Boolean = true,
        connectUart: Boolean = false
    ): String = createCmd(
        "SET-BOOT-MODE",
        mapOf(
            "arg" to listOf(
                "<mode>$mode</mode>",
                "<connect_type>${if (connectUart) "UART" else "USB"}</connect_type>",
                "<mobile_log>${if (mobileLog) "ON" else "OFF"}</mobile_log>",
                "<adb>${if (adb) "ON" else "OFF"}</adb>"
            )
        )
    )

    fun cmdReadReg(
        bitWidth: Int = 32,
        baseAddress: Long = 0x1000L,
        memOffset: Long = 0x8000000L,
        memLength: Long = 0x4L
    ): String = createCmd(
        "READ-REGISTER",
        mapOf(
            "arg" to listOf(
                "<bit_width>$bitWidth</bit_width>",
                "<base_address>${toHex(baseAddress)}</base_address>",
                "<target_file>MEM://${toHex(memOffset)}:${toHex(memLength)}</target_file>"
            )
        )
    )

    fun cmdWriteReg(
        bitWidth: Int = 32,
        baseAddress: Long = 0x1000L,
        memOffset: Long = 0x8000000L,
        memLength: Long = 0x4L
    ): String = createCmd(
        "WRITE-REGISTER",
        mapOf(
            "arg" to listOf(
                "<bit_width>$bitWidth</bit_width>",
                "<base_address>${toHex(baseAddress)}</base_address>",
                "<source_file>MEM://${toHex(memOffset)}:${toHex(memLength)}</source_file>"
            )
        )
    )

    fun cmdEmmcControl(
        function: String = "GET-RPMB-STATUS",
        memOffset: Long = 0x7FE83C338710L,
        memLength: Long = 0x200000L
    ): String = createCmd(
        "EMMC-CONTROL",
        mapOf(
            "arg" to listOf(
                "<function>$function</function>",
                "<target_file>MEM://${toHex(memOffset)}:${toHex(memLength)}</target_file>"
            )
        )
    )

    fun cmdReboot(disconnect: Boolean = false): String = createCmd(
        "REBOOT",
        mapOf("arg" to listOf("<action>${if (disconnect) "DISCONNECT" else "IMMEDIATE"}</action>"))
    )

    fun cmdWritePartition(
        partition: String = "system",
        memOffset: Long = 0x8000000L,
        memLength: Long = 0x100000L
    ): String = createCmd(
        "WRITE-FLASH",
        mapOf(
            "arg" to listOf(
                "<partition>$partition</partition>",
                "<source_file>MEM://${toHex(memOffset)}:${toHex(memLength)}</source_file>"
            )
        )
    )

    fun cmdWriteFlash(
        partition: String = "EMMC-USER",
        offset: Long = 0L,
        memOffset: Long = 0x8000000L,
        memLength: Long = 0x100000L
    ): String = createCmd(
        "WRITE-FLASH",
        mapOf(
            "arg" to listOf(
                "<partition>$partition</partition>",
                "<offset>${toHex(offset)}</offset>",
                "<source_file>MEM://${toHex(memOffset)}:${toHex(memLength)}</source_file>"
            )
        )
    )

    fun cmdReadPartition(partition: String = "system"): String = createCmd(
        "READ-PARTITION",
        mapOf(
            "arg" to listOf(
                "<partition>$partition</partition>",
                "<target_file>\"C:/file.bin\"</target_file>"
            )
        )
    )

    fun cmdReadFlash(
        partition: String = "EMMC-USER",
        offset: Long = 0L,
        length: Long = 0x100000L
    ): String = createCmd(
        "READ-FLASH",
        mapOf(
            "arg" to listOf(
                "<partition>$partition</partition>",
                "<offset>${toHex(offset)}</offset>",
                "<length>${toHex(length)}</length>",
                "<target_file>ROM_0</target_file>"
            )
        )
    )

    fun cmdFlashAll(): String = createCmd(
        "FLASH-ALL",
        mapOf(
            "arg" to listOf(
                "<path_separator>/</path_separator>",
                "<source_file>D:/scatter.xml</source_file>"
            )
        )
    )

    fun cmdErasePartition(partition: String = "system"): String = createCmd(
        "ERASE-PARTITION",
        mapOf("arg" to listOf("<partition>$partition</partition>"))
    )

    fun cmdEraseFlash(
        partition: String = "EMMC-USER",
        offset: Long = 0L,
        length: Long = 0x100000L
    ): String = createCmd(
        "ERASE-FLASH",
        mapOf(
            "arg" to listOf(
                "<partition>$partition</partition>",
                "<offset>${toHex(offset)}</offset>",
                "<length>${toHex(length)}</length>"
            )
        )
    )

    fun cmdFlashUpdate(): String = createCmd(
        "FLASH-UPDATE",
        mapOf(
            "arg" to listOf(
                "<path_separator>/</path_separator>",
                "<source_file>D:/scatter.xml</source_file>",
                "<backup_folder>D:/backup</backup_folder>"
            )
        )
    )

    fun cmdWritePartitions(partitions: List<String>): String {
        val flashList = buildString {
            for (p in partitions) {
                append("    <pt name=").append(p).append(">").append(p).append(".img</pt>\n")
            }
        }
        return createCmd(
            "WRITE-PARTITIONS",
            mapOf(
                "arg" to listOf(
                    "<source_file>D:/scatter.xml</source_file>",
                    "<flash_list>$flashList</flash_list>"
                )
            )
        )
    }

    fun cmdSetRsc(key: String = "k6885v1_64[op01]"): String = createCmd(
        "SET-RSC",
        mapOf(
            "arg" to listOf(
                "<key>$key</key>",
                "<source_file>ms-appdata:///local/RSC.bin</source_file>"
            )
        )
    )

    fun cmdWritePrivateCert(): String = createCmd(
        "WRITE-PRIVATE-CERT",
        mapOf("arg" to listOf("<source_file>ms-appdata:///local/cert.bin</source_file>"))
    )

    fun cmdGetDaInfo(memOffset: Long = 0x2000000L, memLength: Long = 0x20000L): String = createCmd(
        "GET-DA-INFO",
        mapOf("arg" to listOf("<target_file>MEM://${toHex(memOffset)}:${toHex(memLength)}</target_file>"))
    )

    fun cmdGetSysProperty(
        key: String = "DA.SLA",
        hostMemOffset: Long = 0x7FE83C138700L,
        length: Long = 0x200000L
    ): String = createCmd(
        "GET-SYS-PROPERTY",
        mapOf(
            "arg" to listOf(
                "<key>$key</key>",
                "<target_file>MEM://${toHex(hostMemOffset)}:${toHex(length)}</target_file>"
            )
        )
    )

    fun cmdSetHostInfo(hostInfo: String = ""): String {
        val info = hostInfo.ifEmpty {
            SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).format(Date())
        }
        return createCmd(
            "SET-HOST-INFO",
            mapOf("arg" to listOf("<info>$info</info>"))
        )
    }

    fun cmdGetDownloadedImageFeedback(
        memOffset: Long = 0x2000000L,
        memLength: Long = 0x20000L
    ): String = createCmd(
        "GET-DOWNLOADED-IMAGE-FEEDBACK",
        mapOf("arg" to listOf("<target_file>MEM://${toHex(memOffset)}:${toHex(memLength)}</target_file>"))
    )
}
