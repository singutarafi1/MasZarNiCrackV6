package com.example.protocol

/**
 * Full 88-SoC MediaTek Chipset Database
 * Faithfully ported from mtkclient/config/brom_config.py + mtkclient/config/mtk_config.py (default_values)
 */
internal object MtkChipConfigData {

    private fun cfg(
        hwCode: Int,
        name: String,
        description: String = "",
        var1: Int = 0x0A,
        watchdog: Long = 0x10007000L,
        uart: Long = 0x11002000L,
        bromPayloadAddr: Long = 0x100A00L,
        daPayloadAddr: Long = 0x200000L,
        plPayloadAddr: Long = 0x40200000L,
        gcpuBase: Long? = null,
        sejBase: Long? = null,
        dxccBase: Long? = null,
        cqdmaBase: Long? = null,
        apDmaMem: Long? = 0x11000000L + 0x1A0L,
        blacklist: List<BlacklistEntry> = emptyList(),
        dacode: Int = hwCode,
        damode: DaMode = DaMode.LEGACY,
        loader: String = "",
        miscLock: Long? = null,
        efuseAddr: Long? = null,
        meidAddr: Long? = null,
        socidAddr: Long? = null,
        provAddr: Long? = null,
        has64Bit: Boolean = false,
        isIot: Boolean = false,
        isPatchedBrom: Boolean = false,
        bromRegisterAccess: List<Pair<Long, Long>>? = null,
        sendPtr: List<Pair<Long, Long>>? = null,
        blacklistCount: Int = 0x0A,
        ctrlBuffer: Long? = null,
        cmdHandler: Long? = null
    ) = ChipConfig(
        hwCode = hwCode,
        name = name,
        description = description,
        var1 = var1,
        watchdog = watchdog,
        uart = uart,
        bromPayloadAddr = bromPayloadAddr,
        daPayloadAddr = daPayloadAddr,
        plPayloadAddr = plPayloadAddr,
        gcpuBase = gcpuBase,
        sejBase = sejBase,
        dxccBase = dxccBase,
        cqdmaBase = cqdmaBase,
        apDmaMem = apDmaMem,
        blacklist = blacklist,
        dacode = dacode,
        damode = damode,
        loader = loader,
        miscLock = miscLock,
        efuseAddr = efuseAddr,
        meidAddr = meidAddr,
        socidAddr = socidAddr,
        provAddr = provAddr,
        has64Bit = has64Bit,
        isIot = isIot,
        isPatchedBrom = isPatchedBrom,
        bromRegisterAccess = bromRegisterAccess,
        sendPtr = sendPtr,
        blacklistCount = blacklistCount,
        ctrlBuffer = ctrlBuffer,
        cmdHandler = cmdHandler
    )

    fun registerAll(register: (ChipConfig) -> Unit) {
        val entries = listOf(
            // 1. MT0571
            cfg(
                hwCode = 0x0571, name = "MT0571",
                watchdog = 0x10007000L, damode = DaMode.LEGACY, dacode = 0x0571
            ),
            // 2. ELBRUS / MT0598
            cfg(
                hwCode = 0x0598, name = "ELBRUS/MT0598",
                watchdog = 0x10211000L, uart = 0x11020000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L,
                gcpuBase = 0x10224000L, sejBase = 0x1000A000L,
                cqdmaBase = 0x10212C00L, apDmaMem = 0x11000000L + 0x1A0L,
                damode = DaMode.LEGACY, dacode = 0x0598
            ),
            // 3. MT6880/MT6890 Modem
            cfg(
                hwCode = 0x0992, name = "MT6880/MT6890 Modem",
                watchdog = 0x10007000L, uart = 0x11003000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L,
                gcpuBase = 0x10360000L, sejBase = 0x1000A000L,
                efuseAddr = 0x11EC0000L, damode = DaMode.XFLASH, dacode = 0x0992
            ),
            // 4. MT2601
            cfg(
                hwCode = 0x2601, name = "MT2601", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11005000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x2008000L, plPayloadAddr = 0x81E00000L,
                sejBase = 0x1000A000L,
                blacklist = listOf(BlacklistEntry(0x11141F0CL, 0x0L), BlacklistEntry(0x11144BC4L, 0x0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x11141F4CL to 0xBA68L),
                ctrlBuffer = 0x11142BE0L, cmdHandler = 0x0040C5AFL,
                bromRegisterAccess = listOf(0x40BD48L to 0x40BEFCL),
                meidAddr = 0x11142C34L, dacode = 0x2601, damode = DaMode.LEGACY,
                isIot = true, loader = "mt2601_payload.bin"
            ),
            // 5. MT2523
            cfg(
                hwCode = 0x2523, name = "MT2523", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11005000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x2008000L, plPayloadAddr = 0x81E00000L,
                sejBase = 0x1000A000L,
                sendPtr = listOf(0x11141F4CL to 0xBA68L), ctrlBuffer = 0x11142BE0L,
                cmdHandler = 0x0040C5AFL, meidAddr = 0x11142C34L,
                dacode = 0x2523, damode = DaMode.LEGACY, isIot = true
            ),
            // 6. MT2625
            cfg(
                hwCode = 0x2625, name = "MT2625", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11005000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x4001000L,
                sejBase = 0x1000A000L,
                dacode = 0x2625, damode = DaMode.LEGACY, isIot = true
            ),
            // 7. MT3967
            cfg(
                hwCode = 0x3967, name = "MT3967",
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40020000L,
                dacode = 0x3967, damode = DaMode.LEGACY
            ),
            // 8. MT5932
            cfg(
                hwCode = 0x5932, name = "MT5932",
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40020000L,
                dacode = 0x5932, damode = DaMode.LEGACY, isIot = true
            ),
            // 9. MT7682
            cfg(
                hwCode = 0x7682, name = "MT7682",
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40020000L,
                dacode = 0x7682, damode = DaMode.LEGACY, isIot = true
            ),
            // 10. MT7686
            cfg(
                hwCode = 0x7686, name = "MT7686",
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40020000L,
                dacode = 0x7686, damode = DaMode.LEGACY, isIot = true
            ),
            // 11. MT6225
            cfg(
                hwCode = 0x6225, name = "MT6225",
                sejBase = 0x80140000L, damode = DaMode.LEGACY, dacode = 0x6225, isIot = true
            ),
            // 12. MT6226
            cfg(
                hwCode = 0x6226, name = "MT6226",
                sejBase = 0x80140000L, damode = DaMode.LEGACY, dacode = 0x6226, isIot = true
            ),
            // 13. MT6236
            cfg(
                hwCode = 0x6236, name = "MT6236",
                damode = DaMode.LEGACY, dacode = 0x6236, isIot = true
            ),
            // 14. MT6238
            cfg(
                hwCode = 0x6238, name = "MT6238",
                damode = DaMode.LEGACY, dacode = 0x6238, isIot = true
            ),
            // 15. MT6253
            cfg(
                hwCode = 0x6253, name = "MT6253",
                damode = DaMode.LEGACY, dacode = 0x6253, isIot = true
            ),
            // 16. MT6255
            cfg(
                hwCode = 0x6255, name = "MT6255",
                sejBase = 0x80140000L, damode = DaMode.LEGACY, dacode = 0x6255, isIot = true
            ),
            // 17. MT6256
            cfg(
                hwCode = 0x6256, name = "MT6256",
                damode = DaMode.LEGACY, dacode = 0x6256, isIot = true
            ),
            // 18. MT625a
            cfg(
                hwCode = 0x625A, name = "MT625a",
                damode = DaMode.LEGACY, dacode = 0x625A, isIot = true
            ),
            // 19. MT6261/MT2503
            cfg(
                hwCode = 0x6261, name = "MT6261/MT2503", var1 = 0x28,
                watchdog = 0xA0030000L, uart = 0xA0080000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L,
                sejBase = 0xA0110000L,
                blacklist = listOf(BlacklistEntry(0xE003FC83L, 0L)),
                sendPtr = listOf(0x700044B0L to 0x700058ECL),
                ctrlBuffer = 0x700041A8L, cmdHandler = 0x700061F6L,
                damode = DaMode.LEGACY, isIot = true, dacode = 0x6261,
                loader = "mt6261_payload.bin"
            ),
            // 20. MT6268
            cfg(
                hwCode = 0x6268, name = "MT6268",
                damode = DaMode.LEGACY, dacode = 0x6268, isIot = true
            ),
            // 21. MT6270
            cfg(
                hwCode = 0x6270, name = "MT6270",
                damode = DaMode.LEGACY, dacode = 0x6270, isIot = true
            ),
            // 22. MT6276
            cfg(
                hwCode = 0x6276, name = "MT6276",
                damode = DaMode.LEGACY, dacode = 0x6276, isIot = true
            ),
            // 23. MT6280
            cfg(
                hwCode = 0x6280, name = "MT6280",
                sejBase = 0x80080000L, damode = DaMode.LEGACY, isIot = true
            ),
            // 24. MT6291
            cfg(
                hwCode = 0x6291, name = "MT6291",
                damode = DaMode.LEGACY, dacode = 0x6291, isIot = true
            ),
            // 25. MT6516
            cfg(
                hwCode = 0x6516, name = "MT6516",
                watchdog = 0x10003000L, uart = 0x10023000L, daPayloadAddr = 0x201000L,
                sejBase = 0x1002D000L, damode = DaMode.LEGACY, dacode = 0x6516
            ),
            // 26. MT6570/MT8321 (0x0633)
            cfg(
                hwCode = 0x0633, name = "MT6570/MT8321",
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x80001000L,
                gcpuBase = 0x1020D000L, sejBase = 0x1000A000L, cqdmaBase = 0x1020AC00L,
                apDmaMem = 0x11000000L + 0x1A0L, efuseAddr = 0x10009000L,
                damode = DaMode.XFLASH, dacode = 0x6570
            ),
            // 27. MT6571
            cfg(
                hwCode = 0x6571, name = "MT6571",
                watchdog = 0x10007400L, daPayloadAddr = 0x2009000L, plPayloadAddr = 0x80001000L,
                miscLock = 0x1000141CL, damode = DaMode.LEGACY, dacode = 0x6571
            ),
            // 28. MT6572
            cfg(
                hwCode = 0x6572, name = "MT6572", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11005000L,
                bromPayloadAddr = 0x10036A0L, daPayloadAddr = 0x2008000L, plPayloadAddr = 0x81E00000L,
                apDmaMem = 0x11000000L + 0x19CL,
                blacklist = listOf(BlacklistEntry(0x11141F0CL, 0L), BlacklistEntry(0x11144BC4L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x11141F4CL to 0x40BA68L),
                ctrlBuffer = 0x11142BE0L, cmdHandler = 0x40C5AFL,
                bromRegisterAccess = listOf(0x40BD48L to 0x40BEFCL),
                meidAddr = 0x11142C34L, miscLock = 0x1000141CL, efuseAddr = 0x10009000L,
                damode = DaMode.LEGACY, dacode = 0x6572, loader = "mt6572_payload.bin"
            ),
            // 29. MT6573/MT6260
            cfg(
                hwCode = 0x6573, name = "MT6573/MT6260",
                watchdog = 0x70025000L, daPayloadAddr = 0x90006000L, plPayloadAddr = 0xF1020000L,
                sejBase = 0x7002A000L, damode = DaMode.LEGACY, dacode = 0x6573
            ),
            // 30. MT6575/MT8317
            cfg(
                hwCode = 0x6575, name = "MT6575/MT8317",
                watchdog = 0xC0000000L, uart = 0xC1009000L,
                bromPayloadAddr = 0xF0000A00L, daPayloadAddr = 0xC2001000L, plPayloadAddr = 0xC2058000L,
                sejBase = 0xC101A000L, apDmaMem = 0xC100119CL,
                sendPtr = listOf(0xF00025FCL to 0xFFFFA0A0L),
                cmdHandler = 0xFFFFAD5CL, bromRegisterAccess = listOf(0xFFFFA3AAL to 0xFFFFA4C4L),
                meidAddr = 0xF0002AF4L, efuseAddr = 0xC1019000L,
                damode = DaMode.LEGACY, dacode = 0x6575, loader = "mt6575_payload.bin"
            ),
            // 31. MT6577
            cfg(
                hwCode = 0x6577, name = "MT6577",
                watchdog = 0xC0000000L, uart = 0xC1009000L,
                daPayloadAddr = 0xC2001000L, plPayloadAddr = 0xC2058000L,
                sejBase = 0xC101A000L, apDmaMem = 0xC100119CL,
                damode = DaMode.LEGACY, dacode = 0x6577
            ),
            // 32. MT6580
            cfg(
                hwCode = 0x6580, name = "MT6580",
                description = "Quad-Core 32-bit Legacy SoC", var1 = 0xAC,
                watchdog = 0x10007000L, uart = 0x11005000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x80001000L,
                sejBase = 0x1000A000L, cqdmaBase = 0x1020AC00L, apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102764L, 0L), BlacklistEntry(0x001071D4L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027A4L to 0xB60CL),
                ctrlBuffer = 0x00103060L, cmdHandler = 0x0000C113L,
                bromRegisterAccess = listOf(0xB8E0L to 0xBA94L),
                efuseAddr = 0x10009000L, miscLock = 0x10001838L, meidAddr = 0x1030B4L,
                damode = DaMode.LEGACY, dacode = 0x6580, loader = "mt6580_payload.bin"
            ),
            // 33. MT6582/MT6574/MT8382
            cfg(
                hwCode = 0x6582, name = "MT6582/MT6574/MT8382", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x80001000L,
                gcpuBase = 0x1101B000L, sejBase = 0x1000A000L, apDmaMem = 0x11000000L + 0x320L,
                blacklist = listOf(BlacklistEntry(0x102788L, 0L), BlacklistEntry(0x00105BE4L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027C8L to 0xA5FCL),
                ctrlBuffer = 0x00103078L, cmdHandler = 0x0000B2E7L,
                bromRegisterAccess = listOf(0xA8D0L to 0xAA84L),
                efuseAddr = 0x10206000L, meidAddr = 0x1030CCL, miscLock = 0x10002050L,
                damode = DaMode.LEGACY, dacode = 0x6582, loader = "mt6582_payload.bin"
            ),
            // 34. MT6583/6589
            cfg(
                hwCode = 0x6583, name = "MT6583/6589",
                watchdog = 0x10000000L, uart = 0x11006000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x12001000L, plPayloadAddr = 0x80001000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000000L + 0x320L, miscLock = 0x10002050L,
                damode = DaMode.LEGACY, dacode = 0x6583
            ),
            // 35. MT6592/MT8392
            cfg(
                hwCode = 0x6592, name = "MT6592/MT8392", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x111000L, plPayloadAddr = 0x80001000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212000L,
                apDmaMem = 0x11000000L + 0x320L,
                blacklist = listOf(BlacklistEntry(0x00102764L, 0L), BlacklistEntry(0x00105BF0L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027A4L to 0xA564L),
                ctrlBuffer = 0x00103054L, cmdHandler = 0x0000B09FL,
                bromRegisterAccess = listOf(0xA838L to 0xA9ECL),
                meidAddr = 0x1030A8L, miscLock = 0x10002050L, efuseAddr = 0x10206000L,
                damode = DaMode.LEGACY, dacode = 0x6592, loader = "mt6592_payload.bin"
            ),
            // 36. MT6595
            cfg(
                hwCode = 0x6595, name = "MT6595", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x111000L,
                sejBase = 0x1000A000L, apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x00102768L, 0L), BlacklistEntry(0x0106C88L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027A8L to 0xB218L),
                ctrlBuffer = 0x00103050L, cmdHandler = 0x0000BD53L,
                bromRegisterAccess = listOf(0xB4ECL to 0xB6A0L),
                meidAddr = 0x1030A4L, efuseAddr = 0x10206000L,
                damode = DaMode.LEGACY, dacode = 0x6595, loader = "mt6595_payload.bin"
            ),
            // 37. MT6735/T, MT8735A (0x0321)
            cfg(
                hwCode = 0x0321, name = "MT6735/T,MT8735A", var1 = 0x28,
                watchdog = 0x10212000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10216000L, sejBase = 0x10008000L, cqdmaBase = 0x10217C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x00102760L, 0L), BlacklistEntry(0x00105704L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027A0L to 0x95F8L),
                ctrlBuffer = 0x0010305CL, cmdHandler = 0x0000A17FL,
                bromRegisterAccess = listOf(0x98CCL to 0x9A94L),
                meidAddr = 0x1030B0L, miscLock = 0x10001838L, efuseAddr = 0x11C50000L,
                damode = DaMode.LEGACY, dacode = 0x6735, loader = "mt6735_payload.bin"
            ),
            // 38. MT6737M/MT6735G (0x0335)
            cfg(
                hwCode = 0x0335, name = "MT6737M/MT6735G", var1 = 0x28,
                watchdog = 0x10212000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10216000L, sejBase = 0x10008000L, cqdmaBase = 0x10217C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x00102760L, 0L), BlacklistEntry(0x00105704L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027A0L to 0x9608L),
                ctrlBuffer = 0x0010305CL, cmdHandler = 0x0000A18FL,
                bromRegisterAccess = listOf(0x98DCL to 0x9AA4L),
                meidAddr = 0x1030B0L, efuseAddr = 0x10206000L,
                damode = DaMode.LEGACY, dacode = 0x6735, loader = "mt6737_payload.bin"
            ),
            // 39. MT6739/MT6731/MT8765 (0x0699)
            cfg(
                hwCode = 0x0699, name = "MT6739/MT6731/MT8765",
                description = "Quad-Core Entry SoC", var1 = 0xB4,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, sejBase = 0x1000A000L, dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x10282CL, 0L), BlacklistEntry(0x001076ACL, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102870L to 0xDF1CL),
                ctrlBuffer = 0x00102A28L, cmdHandler = 0x0000EC49L,
                bromRegisterAccess = listOf(0xE330L to 0xE3E8L),
                meidAddr = 0x102AF8L, socidAddr = 0x102B08L, provAddr = 0x10720CL,
                miscLock = 0x1001A100L, efuseAddr = 0x11C00000L,
                damode = DaMode.XFLASH, dacode = 0x6739, loader = "mt6739_payload.bin"
            ),
            // 40. MT6750 (0x0601)
            cfg(
                hwCode = 0x0601, name = "MT6750", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212C00L,
                apDmaMem = 0x11000000L + 0x1A0L, efuseAddr = 0x10206000L, miscLock = 0x10001838L,
                damode = DaMode.XFLASH, dacode = 0x6755
            ),
            // 41. MT6752
            cfg(
                hwCode = 0x6752, name = "MT6752", var1 = 0x28,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40001000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x00102764L, 0L), BlacklistEntry(0x00105704L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027A4L to 0x990CL),
                ctrlBuffer = 0x00103060L, cmdHandler = 0x0000A493L,
                bromRegisterAccess = listOf(0x9BE0L to 0x9DA8L),
                efuseAddr = 0x10206000L, meidAddr = 0x1030B4L,
                damode = DaMode.LEGACY, dacode = 0x6752, loader = "mt6752_payload.bin"
            ),
            // 42. MT6753 (0x0337)
            cfg(
                hwCode = 0x0337, name = "MT6753", var1 = 0x28,
                watchdog = 0x10212000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10216000L, sejBase = 0x10008000L, cqdmaBase = 0x10217C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x00102760L, 0L), BlacklistEntry(0x00105704L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027A0L to 0x9668L),
                ctrlBuffer = 0x0010305CL, cmdHandler = 0x0000A1EFL,
                bromRegisterAccess = listOf(0x993CL to 0x9B04L),
                meidAddr = 0x1030B0L, miscLock = 0x10001838L,
                damode = DaMode.LEGACY, dacode = 0x6735, loader = "mt6753_payload.bin"
            ),
            // 43. MT6755/MT6750/M/T/S (0x0326)
            cfg(
                hwCode = 0x0326, name = "MT6755/MT6750/M/T/S",
                description = "Helio P10/P15/P18", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x10276CL, 0L), BlacklistEntry(0x00105704L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027B0L to 0x9A6CL),
                ctrlBuffer = 0x00103058L, cmdHandler = 0x0000A5FFL,
                bromRegisterAccess = listOf(0x9D4CL to 0x9F14L),
                meidAddr = 0x1030ACL, miscLock = 0x10001838L, efuseAddr = 0x10206000L,
                damode = DaMode.XFLASH, dacode = 0x6755, loader = "mt6755_payload.bin"
            ),
            // 44. MT6757/MT6757D (0x0551)
            cfg(
                hwCode = 0x0551, name = "MT6757/MT6757D",
                description = "Helio P20", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102774L, 0L), BlacklistEntry(0x00105704L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x1027B8L to 0x9C2CL),
                ctrlBuffer = 0x00103060L, cmdHandler = 0x0000A8FBL,
                bromRegisterAccess = listOf(0xA030L to 0xA0E8L),
                meidAddr = 0x1030B4L, miscLock = 0x10001838L, efuseAddr = 0x10206000L,
                damode = DaMode.XFLASH, dacode = 0x6757, loader = "mt6757_payload.bin"
            ),
            // 45. MT6758 (0x0688)
            cfg(
                hwCode = 0x0688, name = "MT6758",
                description = "Helio P30", var1 = 0x0A,
                watchdog = 0x10211000L, uart = 0x11020000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, sejBase = 0x10080000L, dxccBase = 0x11240000L,
                cqdmaBase = 0x10200000L, apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102830L, 0L), BlacklistEntry(0x106A60L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102874L to 0xD860L),
                ctrlBuffer = 0x102B28L, cmdHandler = 0xE58DL,
                bromRegisterAccess = listOf(0xDC74L to 0xDD2CL),
                meidAddr = 0x102BF8L, socidAddr = 0x102C08L, efuseAddr = 0x10450000L,
                damode = DaMode.XFLASH, dacode = 0x6758, loader = "mt6758_payload.bin"
            ),
            // 46. MT6759 (0x0507)
            cfg(
                hwCode = 0x0507, name = "MT6759",
                description = "Helio P30",
                watchdog = 0x10210000L, uart = 0x11020000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L,
                gcpuBase = 0x10210000L, apDmaMem = 0x1030000L + 0x1A0L,
                damode = DaMode.LEGACY, dacode = 0x6758
            ),
            // 47. MT6761/MT6762 (0x0717)
            cfg(
                hwCode = 0x0717, name = "MT6761/MT6762/MT3369/MT8766B/MT8761",
                description = "Helio A20/P22/A22/A25/G25", var1 = 0x25,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, sejBase = 0x1000A000L, dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11000A80L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102828L, 0L), BlacklistEntry(0x00105994L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x10286CL to 0xBC8CL),
                ctrlBuffer = 0x00102A28L, cmdHandler = 0x0000C9B9L,
                bromRegisterAccess = listOf(0xC0A0L to 0xC158L),
                meidAddr = 0x102AF8L, socidAddr = 0x102B08L, provAddr = 0x1054F4L,
                miscLock = 0x1001A100L, efuseAddr = 0x11C50000L,
                damode = DaMode.XFLASH, dacode = 0x6761, loader = "mt6761_payload.bin",
                isPatchedBrom = true
            ),
            // 48. MT6763 (0x0690)
            cfg(
                hwCode = 0x0690, name = "MT6763",
                description = "Helio P23", var1 = 0x7F,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11000A80L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102834L, 0L), BlacklistEntry(0x00106CA4L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102878L to 0xD66CL),
                ctrlBuffer = 0x00102A90L, cmdHandler = 0x0000E383L,
                bromRegisterAccess = listOf(0xDA80L to 0xDB38L),
                meidAddr = 0x102B78L, socidAddr = 0x102B88L, provAddr = 0x106804L,
                miscLock = 0x1001A100L, efuseAddr = 0x11F10000L,
                damode = DaMode.XFLASH, dacode = 0x6763, loader = "mt6763_payload.bin"
            ),
            // 49. MT6765/MT8768t (0x0766)
            cfg(
                hwCode = 0x0766, name = "MT6765/MT8768t",
                description = "Helio P35 / G35 (Redmi 9A, Redmi 9C, Honor Play, Galaxy A03s)", var1 = 0x25,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, sejBase = 0x1000A000L, dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102828L, 0L), BlacklistEntry(0x00105994L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x10286CL to 0xBDC0L),
                ctrlBuffer = 0x00102A28L, cmdHandler = 0x0000CAEDL,
                bromRegisterAccess = listOf(0xC1D4L to 0xC28CL),
                meidAddr = 0x102AF8L, socidAddr = 0x102B08L, provAddr = 0x1054F4L,
                miscLock = 0x1001A100L, efuseAddr = 0x11C50000L,
                damode = DaMode.XFLASH, dacode = 0x6765, loader = "mt6765_payload.bin",
                isPatchedBrom = true
            ),
            // 50. MT6768/MT6769 (0x0707)
            cfg(
                hwCode = 0x0707, name = "MT6768/MT6769",
                description = "Helio P65 / G80 / G85 k68v1", var1 = 0x25,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, sejBase = 0x1000A000L, dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x10282CL, 0L), BlacklistEntry(0x00105994L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x10286CL to 0xC190L),
                ctrlBuffer = 0x00102A28L, cmdHandler = 0x0000CF15L,
                bromRegisterAccess = listOf(0xC598L to 0xC650L),
                meidAddr = 0x102AF8L, socidAddr = 0x102B08L, provAddr = 0x1054F4L,
                miscLock = 0x1001A100L, efuseAddr = 0x11CE0000L,
                damode = DaMode.XFLASH, dacode = 0x6768, loader = "mt6768_payload.bin",
                isPatchedBrom = true
            ),
            // 51. MT6771/MT8385/MT8183/MT8666 (0x0788)
            cfg(
                hwCode = 0x0788, name = "MT6771/MT8385/MT8183/MT8666",
                description = "Helio P60/P70/G80", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, sejBase = 0x1000A000L, dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11000000L + 0x158L,
                blacklist = listOf(BlacklistEntry(0x00102834L, 0L), BlacklistEntry(0x00106A60L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102878L to 0xDEBCL),
                ctrlBuffer = 0x00102A80L, cmdHandler = 0x0000EBE9L,
                bromRegisterAccess = listOf(0xE2D0L to 0xE388L),
                meidAddr = 0x102B38L, socidAddr = 0x102B48L, provAddr = 0x1065C0L,
                miscLock = 0x1001A100L, efuseAddr = 0x11F10000L,
                damode = DaMode.XFLASH, dacode = 0x6771, loader = "mt6771_payload.bin"
            ),
            // 52. MT6779 (0x0725)
            cfg(
                hwCode = 0x0725, name = "MT6779",
                description = "Helio P90 k79v1", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, sejBase = 0x1000A000L, dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11000000L + 0x158L,
                blacklist = listOf(BlacklistEntry(0x102838L, 0L), BlacklistEntry(0x00106A60L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102878L to 0xE04CL),
                ctrlBuffer = 0x00102A80L, cmdHandler = 0x0000ED6DL,
                bromRegisterAccess = listOf(0xE454L to 0xE50CL),
                meidAddr = 0x102B38L, socidAddr = 0x102B48L, provAddr = 0x1065C0L,
                miscLock = 0x1001A100L, efuseAddr = 0x11C10000L,
                damode = DaMode.XFLASH, dacode = 0x6779, loader = "mt6779_payload.bin"
            ),
            // 53. MT6781 (0x1066)
            cfg(
                hwCode = 0x1066, name = "MT6781",
                description = "Helio G96", var1 = 0x73,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, sejBase = 0x1000A000L, dxccBase = 0x10210000L,
                blacklist = listOf(BlacklistEntry(0x10284CL, 0x106B54L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102890L to 0xE5D8L),
                ctrlBuffer = 0x00102AB4L, cmdHandler = 0x0000F3C1L,
                bromRegisterAccess = listOf(0xE9DCL to 0xEA94L),
                meidAddr = 0x102B98L, socidAddr = 0x102BA8L, efuseAddr = 0x11CB0000L,
                damode = DaMode.XFLASH, dacode = 0x6781, loader = "mt6781_payload.bin"
            ),
            // 54. MT6785 (0x0813)
            cfg(
                hwCode = 0x0813, name = "MT6785",
                description = "Helio G90 / G90T / G95 (Redmi Note 8 Pro, Realme 6, Realme 7)", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, sejBase = 0x1000A000L, dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11000000L + 0x158L,
                blacklist = listOf(BlacklistEntry(0x102838L, 0L), BlacklistEntry(0x00106A60L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102878L to 0xE2A4L),
                ctrlBuffer = 0x00102A80L, cmdHandler = 0x0000F029L,
                bromRegisterAccess = listOf(0xE6ACL to 0xE764L),
                meidAddr = 0x102B38L, socidAddr = 0x102B48L, provAddr = 0x1065C0L,
                miscLock = 0x1001A100L, efuseAddr = 0x11C10000L,
                damode = DaMode.XFLASH, dacode = 0x6785, loader = "mt6785_payload.bin",
                has64Bit = true, isPatchedBrom = true
            ),
            // 55. MT6795
            cfg(
                hwCode = 0x6795, name = "MT6795",
                description = "Helio X10", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x110000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102764L, 0L), BlacklistEntry(0x00105704L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027A4L to 0x978CL),
                ctrlBuffer = 0x0010304CL, cmdHandler = 0x0000A313L,
                bromRegisterAccess = listOf(0x9A60L to 0x9C28L),
                meidAddr = 0x1030A0L, efuseAddr = 0x10206000L,
                damode = DaMode.LEGACY, dacode = 0x6795, loader = "mt6795_payload.bin"
            ),
            // 56. MT6797/MT6767 (0x0279)
            cfg(
                hwCode = 0x0279, name = "MT6797/MT6767",
                description = "Helio X23/X25/X27", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x0010276CL, 0L), BlacklistEntry(0x00105704L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1027B0L to 0x9EACL),
                ctrlBuffer = 0x00103058L, cmdHandler = 0x0000AA3FL,
                bromRegisterAccess = listOf(0xA18CL to 0xA354L),
                meidAddr = 0x1030ACL, miscLock = 0x10002050L, efuseAddr = 0x10206000L,
                damode = DaMode.XFLASH, dacode = 0x6797, loader = "mt6797_payload.bin"
            ),
            // 57. MT6799 (0x0562)
            cfg(
                hwCode = 0x0562, name = "MT6799",
                description = "Helio X30/X35", var1 = 0x0A,
                watchdog = 0x10211000L, uart = 0x11020000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10210000L, cqdmaBase = 0x11B30000L, apDmaMem = 0x11000000L + 0x1A0L,
                dxccBase = 0x11B20000L, sejBase = 0x1000A000L,
                blacklist = listOf(BlacklistEntry(0x00102870L, 0L), BlacklistEntry(0x00107070L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x1028B4L to 0xF5ACL),
                ctrlBuffer = 0x001032F0L, cmdHandler = 0x000102C3L,
                bromRegisterAccess = listOf(0xF9C0L to 0xFA78L),
                meidAddr = 0x1033B8L, socidAddr = 0x1033C8L, efuseAddr = 0x11F10000L,
                damode = DaMode.XFLASH, dacode = 0x6799, loader = "mt6799_payload.bin"
            ),
            // 58. MT6833 (0x0989)
            cfg(
                hwCode = 0x0989, name = "MT6833",
                description = "Dimensity 700 / 810 5G k6833", var1 = 0x73,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x10217A80L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x00102844L, 0L), BlacklistEntry(0x00106B54L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102884L to 0xDFE0L),
                ctrlBuffer = 0x00102AA4L, cmdHandler = 0x0000EDADL,
                bromRegisterAccess = listOf(0xE3E8L to 0xE4A0L),
                meidAddr = 0x102B98L, socidAddr = 0x102BA8L, provAddr = 0x1066B4L,
                efuseAddr = 0x11C10000L,
                damode = DaMode.XFLASH, dacode = 0x6833, loader = "mt6833_payload.bin",
                has64Bit = true, isPatchedBrom = true
            ),
            // 59. MT6853 (0x0996)
            cfg(
                hwCode = 0x0996, name = "MT6853",
                description = "Dimensity 720 5G", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x10210000L, cqdmaBase = 0x10212000L,
                sejBase = 0x1000A000L, apDmaMem = 0x10217A80L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x10284CL, 0L), BlacklistEntry(0x00106B60L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x10288CL to 0xEA64L),
                ctrlBuffer = 0x00102AA0L, cmdHandler = 0x0000F831L,
                bromRegisterAccess = listOf(0xEE6CL to 0xEF24L),
                meidAddr = 0x102B78L, socidAddr = 0x102B88L, provAddr = 0x1066C0L,
                miscLock = 0x1001A100L, efuseAddr = 0x11C10000L,
                damode = DaMode.XFLASH, dacode = 0x6853, loader = "mt6853_payload.bin"
            ),
            // 60. MT6873 (0x0886)
            cfg(
                hwCode = 0x0886, name = "MT6873",
                description = "Dimensity 800/820 5G", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x10217A80L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x10284CL, 0L), BlacklistEntry(0x00106B60L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x10288CL to 0xEA78L),
                ctrlBuffer = 0x00102AA0L, cmdHandler = 0x0000F7FDL,
                bromRegisterAccess = listOf(0xEE80L to 0xEF38L),
                meidAddr = 0x102B78L, socidAddr = 0x102B88L, provAddr = 0x1066C0L,
                miscLock = 0x1001A100L, efuseAddr = 0x11C10000L,
                damode = DaMode.XFLASH, dacode = 0x6873, loader = "mt6873_payload.bin"
            ),
            // 61. MT6877/MT6877V/MT8791N (0x0959)
            cfg(
                hwCode = 0x0959, name = "MT6877/MT6877V/MT8791N",
                description = "Dimensity 900/1080/7050", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, sejBase = 0x1000A000L, dxccBase = 0x10210000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x10217A80L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102848L, 0L), BlacklistEntry(0x00106B60L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102888L to 0xE8D0L),
                ctrlBuffer = 0x00102A9CL, cmdHandler = 0x0000F69DL,
                bromRegisterAccess = listOf(0xECD8L to 0xED90L),
                meidAddr = 0x102B98L, socidAddr = 0x102BA8L, provAddr = 0x1066C0L,
                efuseAddr = 0x11F10000L,
                damode = DaMode.XFLASH, dacode = 0x6877, loader = "mt6877_payload.bin",
                has64Bit = true, isPatchedBrom = true
            ),
            // 62. MT6885/MT6883/MT6889/MT6880/MT6890 (0x0816)
            cfg(
                hwCode = 0x0816, name = "MT6885/MT6883/MT6889/MT6880/MT6890",
                description = "Dimensity 1000L/1000", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11000A80L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102848L, 0L), BlacklistEntry(0x00106B60L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102888L to 0xE6FCL),
                ctrlBuffer = 0x00102A9CL, cmdHandler = 0x0000F481L,
                bromRegisterAccess = listOf(0xEB04L to 0xEBBCL),
                meidAddr = 0x102B78L, socidAddr = 0x102B88L, provAddr = 0x1066C0L,
                miscLock = 0x1001A100L, efuseAddr = 0x11C10000L,
                damode = DaMode.XFLASH, dacode = 0x6885, loader = "mt6885_payload.bin",
                has64Bit = true, isPatchedBrom = true
            ),
            // 63. MT6891/MT6893 (0x0950)
            cfg(
                hwCode = 0x0950, name = "MT6891/MT6893",
                description = "Dimensity 1200 / 1100 5G", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11000A80L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102848L, 0L), BlacklistEntry(0x00106B60L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x102888L to 0xE79CL),
                ctrlBuffer = 0x00102A9CL, cmdHandler = 0x0000F569L,
                bromRegisterAccess = listOf(0xEBA4L to 0xEC5CL),
                meidAddr = 0x102B98L, socidAddr = 0x102BA8L, provAddr = 0x1066C0L,
                efuseAddr = 0x11C10000L,
                damode = DaMode.XFLASH, dacode = 0x6893, loader = "mt6893_payload.bin",
                has64Bit = true, isPatchedBrom = true
            ),
            // 64. MT6983 (0x0907 / 0x0983 / 0x6983) - Dimensity 9000/9000+
            cfg(
                hwCode = 0x0907, name = "MT6983",
                description = "Dimensity 9000/9000+", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x11001000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11300800L + 0x1A0L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11EE0000L,
                damode = DaMode.XML, dacode = 0x0907, has64Bit = true,
                loader = "mt6983_payload.bin"
            ),
            cfg(
                hwCode = 0x0983, name = "MT6983",
                description = "Dimensity 9000/9000+", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x11001000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11300800L + 0x1A0L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11EE0000L,
                damode = DaMode.XML, dacode = 0x0907, has64Bit = true,
                loader = "mt6983_payload.bin"
            ),
            cfg(
                hwCode = 0x6983, name = "MT6983",
                description = "Dimensity 9000/9000+", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x11001000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11300800L + 0x1A0L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11EE0000L,
                damode = DaMode.XML, dacode = 0x0907, has64Bit = true,
                loader = "mt6983_payload.bin"
            ),
            // 65. MT6855 (0x1129)
            cfg(
                hwCode = 0x1129, name = "MT6855",
                description = "Dimensity 8100 / 7020", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x11001000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11300800L + 0x1A0L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L,
                damode = DaMode.XML, dacode = 0x1129
            ),
            // 66. MT6895 (0x1172)
            cfg(
                hwCode = 0x1172, name = "MT6895",
                description = "Dimensity 8200", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x11001000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10050000L, dxccBase = 0x1C807000L, sejBase = 0x1C009000L,
                cqdmaBase = 0x10212000L, apDmaMem = 0x11300800L + 0x1A0L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11F10000L,
                damode = DaMode.XML, dacode = 0x1172
            ),
            // 67. MT6897 (0x1203)
            cfg(
                hwCode = 0x1203, name = "MT6897",
                description = "Dimensity 8300 Ultra", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x1000D000L, dxccBase = 0x10403000L, sejBase = 0x1040E000L,
                socidAddr = 0x20E7090L, damode = DaMode.XML, dacode = 0x1203
            ),
            // 68. MT6789/MT8781V (0x1208)
            cfg(
                hwCode = 0x1208, name = "MT6789/MT8781V",
                description = "MTK Helio G99", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                blacklist = listOf(BlacklistEntry(0x102D5CL, 0L)),
                ctrlBuffer = 0x00103024L, cmdHandler = 0x000101E8L,
                bromRegisterAccess = listOf(0xF99AL to 0xFA0CL),
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11C10000L,
                damode = DaMode.XML, dacode = 0x1208
            ),
            // 69. MT6835V/ZA (0x1209)
            cfg(
                hwCode = 0x1209, name = "MT6835V/ZA",
                description = "MTK Dimensity 6100+", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x11002000L,
                daPayloadAddr = 0x2001000L, plPayloadAddr = 0x40200000L,
                dxccBase = 0x10210000L, sejBase = 0x1000A000L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11C10000L,
                damode = DaMode.XML, dacode = 0x1209
            ),
            // 70. MT6886 (0x1229)
            cfg(
                hwCode = 0x1229, name = "MT6886",
                description = "Dimensity 7200 Ultra", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x2001000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x1000D000L, dxccBase = 0x1C807000L, sejBase = 0x1C009000L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11E30000L,
                damode = DaMode.XML, dacode = 0x1229, has64Bit = true
            ),
            // 71. MT6989W (0x1236)
            cfg(
                hwCode = 0x1236, name = "MT6989W",
                description = "Dimensity 9300 Plus",
                watchdog = 0x1C00B000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x2001000L, plPayloadAddr = 0x40200000L,
                dxccBase = 0x10403000L, sejBase = 0x1040E000L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11F10000L,
                damode = DaMode.XML, dacode = 0x1236, has64Bit = true
            ),
            // 72. MT6985 (0x1296 / 0x0985 / 0x6985) - Dimensity 9200/9200+
            cfg(
                hwCode = 0x1296, name = "MT6985",
                description = "Dimensity 9200/9200+", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x1C011000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                dxccBase = 0x1C807000L, sejBase = 0x1C009000L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11E80000L,
                damode = DaMode.XML, dacode = 0x1296, has64Bit = true,
                loader = "mt6985_payload.bin"
            ),
            cfg(
                hwCode = 0x0985, name = "MT6985",
                description = "Dimensity 9200/9200+", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x1C011000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                dxccBase = 0x1C807000L, sejBase = 0x1C009000L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11E80000L,
                damode = DaMode.XML, dacode = 0x1296, has64Bit = true,
                loader = "mt6985_payload.bin"
            ),
            cfg(
                hwCode = 0x6985, name = "MT6985",
                description = "Dimensity 9200/9200+", var1 = 0x0A,
                watchdog = 0x1C007000L, uart = 0x1C011000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                dxccBase = 0x1C807000L, sejBase = 0x1C009000L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x11E80000L,
                damode = DaMode.XML, dacode = 0x1296, has64Bit = true,
                loader = "mt6985_payload.bin"
            ),
            // 73. MT6878 (0x1375)
            cfg(
                hwCode = 0x1375, name = "MT6878",
                description = "Dimensity 7300", var1 = 0x0A,
                watchdog = 0x1C00A000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x2010000L, plPayloadAddr = 0x40200000L,
                dxccBase = 0x10400000L, sejBase = 0x1040E000L,
                efuseAddr = 0x11F10000L,
                damode = DaMode.XML, dacode = 0x1375, has64Bit = true
            ),
            // 74. MT6899 (0x6899)
            cfg(
                hwCode = 0x6899, name = "MT6899",
                description = "Dimensity 8400 Turbo", var1 = 0x0A,
                watchdog = 0x1C010000L, uart = 0x16010000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                dxccBase = 0x18005000L, sejBase = 0x1800E000L,
                efuseAddr = 0x13260000L,
                damode = DaMode.XML, dacode = 0x1357, has64Bit = true
            ),
            // 75. MT6993 (0x1471)
            cfg(
                hwCode = 0x1471, name = "MT6993",
                description = "Dimensity 9500", var1 = 0x0A,
                watchdog = 0x1C010000L, uart = 0x16010000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                dxccBase = 0x18005000L, sejBase = 0x1800E000L,
                meidAddr = 0x1008ECL, socidAddr = 0x100934L, efuseAddr = 0x10160000L,
                damode = DaMode.XML, dacode = 0x1471, has64Bit = true
            ),
            // 76. MT8127/MT3367/AC8227L
            cfg(
                hwCode = 0x8127, name = "MT8127/MT3367/AC8227L", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x80001000L,
                gcpuBase = 0x11010000L, sejBase = 0x1000A000L, apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102870L, 0L), BlacklistEntry(0x00106C7CL, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1028B0L to 0xB2B8L),
                ctrlBuffer = 0x00103178L, cmdHandler = 0x0000BDF3L,
                bromRegisterAccess = listOf(0xB58CL to 0xB740L),
                meidAddr = 0x1031CCL, miscLock = 0x10002050L,
                damode = DaMode.LEGACY, dacode = 0x8127, loader = "mt8127_payload.bin"
            ),
            // 77. MT8135
            cfg(
                hwCode = 0x8135, name = "MT8135",
                watchdog = 0x10000000L, uart = 0x11002000L,
                daPayloadAddr = 0x12001000L, plPayloadAddr = 0x80001000L,
                gcpuBase = 0x11018000L, damode = DaMode.LEGACY, dacode = 0x8135
            ),
            // 78. MT8163
            cfg(
                hwCode = 0x8163, name = "MT8163", var1 = 0xB1,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40001000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102868L, 0L), BlacklistEntry(0x001072DCL, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1028A8L to 0xC12CL),
                ctrlBuffer = 0x0010316CL, cmdHandler = 0x0000CCB3L,
                bromRegisterAccess = listOf(0xC400L to 0xC5C8L),
                meidAddr = 0x1031C0L, miscLock = 0x10002050L, efuseAddr = 0x10206000L,
                damode = DaMode.LEGACY, dacode = 0x8163, loader = "mt8163_payload.bin"
            ),
            // 79. MT8167/MT8516/MT8362
            cfg(
                hwCode = 0x8167, name = "MT8167/MT8516/MT8362", var1 = 0xCC,
                watchdog = 0x10007000L, uart = 0x11005000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40001000L,
                gcpuBase = 0x1020D000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x102968L, 0L), BlacklistEntry(0x00107954L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x1029ACL to 0xD2E4L),
                ctrlBuffer = 0x0010339CL, cmdHandler = 0x0000DFF7L,
                bromRegisterAccess = listOf(0xD6F2L to 0xD7ACL),
                meidAddr = 0x103478L, socidAddr = 0x103488L, efuseAddr = 0x10009000L,
                damode = DaMode.XFLASH, dacode = 0x8167, loader = "mt8167_payload.bin"
            ),
            // 80. MT8168/MT6357
            cfg(
                hwCode = 0x8168, name = "MT8168/MT6357", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40001000L,
                gcpuBase = 0x10241000L, sejBase = 0x1000A000L, apDmaMem = 0x11000280L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x10303CL, 0L), BlacklistEntry(0x10A540L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x103080L to 0x13834L),
                ctrlBuffer = 0x0010637CL, cmdHandler = 0x1436FL,
                bromRegisterAccess = listOf(0x13C18L to 0x13D78L),
                meidAddr = 0x106438L, socidAddr = 0x106448L, efuseAddr = 0x10009000L,
                damode = DaMode.XFLASH, dacode = 0x8168, loader = "mt8168_payload.bin"
            ),
            // 81. MT8172 (MT8173)
            cfg(
                hwCode = 0x8172, name = "MT8173", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x120A00L, daPayloadAddr = 0xC0000L, plPayloadAddr = 0x40001000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x122774L, 0L), BlacklistEntry(0x00125904L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1227B4L to 0xA0E4L),
                ctrlBuffer = 0x0012305CL, cmdHandler = 0x0000AC6BL,
                bromRegisterAccess = listOf(0xA3B8L to 0xA580L),
                meidAddr = 0x1230B0L, miscLock = 0x1202050L,
                damode = DaMode.LEGACY, dacode = 0x8173, loader = "mt8173_payload.bin"
            ),
            // 82. MT8176
            cfg(
                hwCode = 0x8176, name = "MT8176", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x120A00L, daPayloadAddr = 0xC0000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x10210000L, sejBase = 0x1000A000L, cqdmaBase = 0x10212C00L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x122774L, 0L), BlacklistEntry(0x00125904L, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1227B4L to 0xA0E4L),
                ctrlBuffer = 0x0012305CL, cmdHandler = 0x0000AC6BL,
                bromRegisterAccess = listOf(0xA3B8L to 0xA580L),
                meidAddr = 0x1230B0L, miscLock = 0x1202050L, efuseAddr = 0x10206000L,
                damode = DaMode.LEGACY, dacode = 0x8173, loader = "mt8176_payload.bin"
            ),
            // 83. MT8195 Chromebook (0x0930)
            cfg(
                hwCode = 0x0930, name = "MT8195 Chromebook",
                watchdog = 0x10007000L, uart = 0x11001200L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40200000L,
                efuseAddr = 0x11C10000L, miscLock = 0x1001A100L,
                damode = DaMode.XFLASH, dacode = 0x8195
            ),
            // 84. MT8512
            cfg(
                hwCode = 0x8512, name = "MT8512", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x111000L, plPayloadAddr = 0x40200000L,
                gcpuBase = 0x1020F000L, sejBase = 0x1000A000L, cqdmaBase = 0x10214000L,
                apDmaMem = 0x11000000L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x001041E4L, 0L), BlacklistEntry(0x0010AA84L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x104258L to 0xCC44L),
                ctrlBuffer = 0x00104570L, cmdHandler = 0x0000D7ABL,
                bromRegisterAccess = listOf(0xD034L to 0xD194L),
                meidAddr = 0x104638L, socidAddr = 0x104648L, efuseAddr = 0x11C50000L,
                damode = DaMode.XFLASH, dacode = 0x8512, loader = "mt8512_payload.bin"
            ),
            // 85. MT8518 VoiceAssistant
            cfg(
                hwCode = 0x8518, name = "MT8518 VoiceAssistant",
                efuseAddr = 0x10009000L, damode = DaMode.XFLASH, dacode = 0x8518
            ),
            // 86. MT8590/MT7683/MT8521/MT7623
            cfg(
                hwCode = 0x8590, name = "MT8590/MT7683/MT8521/MT7623", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x80001000L,
                gcpuBase = 0x1101B000L, sejBase = 0x1000A000L,
                blacklist = listOf(BlacklistEntry(0x102870L, 0L), BlacklistEntry(0x106C7CL, 0L)),
                blacklistCount = 0x08, sendPtr = listOf(0x1028B0L to 0xBBE4L),
                ctrlBuffer = 0x00103184L, cmdHandler = 0x0000C71FL,
                bromRegisterAccess = listOf(0xBEB8L to 0xC06CL),
                meidAddr = 0x1031D8L,
                damode = DaMode.LEGACY, dacode = 0x8590, loader = "mt8590_payload.bin"
            ),
            // 87. MT8695
            cfg(
                hwCode = 0x8695, name = "MT8695", var1 = 0x0A,
                watchdog = 0x10007000L, uart = 0x11002000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L, plPayloadAddr = 0x40001000L,
                sejBase = 0x1000A000L, apDmaMem = 0x11000280L + 0x1A0L,
                blacklist = listOf(BlacklistEntry(0x103048L, 0L), BlacklistEntry(0x00106EC4L, 0L)),
                blacklistCount = 0x0A, sendPtr = listOf(0x103088L to 0xBEECL),
                ctrlBuffer = 0x001031ECL, cmdHandler = 0x0000CAA7L,
                bromRegisterAccess = listOf(0xC298L to 0xC3F8L),
                meidAddr = 0x1032B8L, efuseAddr = 0x10206000L,
                damode = DaMode.XFLASH, dacode = 0x8695, loader = "mt8695_payload.bin"
            ),
            // 88. MT8696 (0x0908)
            cfg(
                hwCode = 0x0908, name = "MT8696",
                watchdog = 0x10007000L,
                bromPayloadAddr = 0x100A00L, daPayloadAddr = 0x201000L,
                efuseAddr = 0x11C10000L,
                damode = DaMode.XFLASH, dacode = 0x8696
            )
        )
        entries.forEach(register)
    }
}
