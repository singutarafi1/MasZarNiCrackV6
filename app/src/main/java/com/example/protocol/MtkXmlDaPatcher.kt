package com.example.protocol

import android.content.Context
import com.example.model.LogLevel
import com.example.model.TerminalLog

/**
 * MediaTek V6 XML DA1/DA2 Patcher & Stage-2 Extension RPC Controller
 * Direct, faithful Kotlin port of Python mtkclient:
 * - mtkclient/Library/DA/xmlflash/extension/v6.py (XmlFlashExt)
 */
object MtkXmlDaPatcher {

    fun patchDa1(da1: ByteArray): ByteArray = da1.copyOf()

    /**
     * Exact port of v6.py -> patch_custom_command(_da2)
     */
    fun patchCustomCommand(da2: ByteArray, da2Address: Long): ByteArray? {
        val data = da2.copyOf()
        val marker = "\u0000CMD:SET-HOST-INFO\u0000".toByteArray(Charsets.US_ASCII)
        val idx = MtkDaPatcher.findBinary(data, marker) ?: return null

        val isArm64 = data.size >= 8 && ((data[4].toInt() and 0xF0) == 0xC0)
        val newCmd = "CMD:CUSTOM\u0000".toByteArray(Charsets.US_ASCII)

        if (isArm64) {
            val at = Aarch64Tools(data, da2Address)
            val refOffset = at.findStringXref("CMD:SET-HOST-INFO") ?: return null
            val blOffset = at.getNextBlFromOff(refOffset) ?: return null
            val cmdSetHost = at.resolveRegisterValueBack(blOffset, reg = 2, lookback = 5) ?: return null
            val cmdSetHostOffset = (cmdSetHost - da2Address).toInt()
            if (cmdSetHostOffset < 0) return null

            MtkDaPatcher.replaceSlice(data, idx + 1, newCmd)
            val stub = MtkDaPatcher.hexToBytes(
                "fd7bbda9f50b00f9fd030091f44f02a9f30300aa750240f9e0ff13b0003c4091810080d2a0023fd6" +
                    "e0ff13b0e10300aa213c4091210040f9a0023fd6e0ff13b000003fd6e0031f2af44f42a9f50b40f9fd7bc3a8c0035fd6"
            )
            MtkDaPatcher.replaceSlice(data, cmdSetHostOffset, stub)
            return data
        } else {
            val (firstOpVal, secondOpVal) = offsetToOpMov((idx + 1).toLong(), 0, da2Address)
            val firstOpBytes = MtkDaPatcher.intLE(firstOpVal)
            val secondOpBytes = MtkDaPatcher.intLE(secondOpVal)
            val midx = MtkDaPatcher.findBinary(data, firstOpBytes) ?: return null
            val midx2 = MtkDaPatcher.findBinary(data, secondOpBytes, midx) ?: return null
            if (midx + 8 == midx2) {
                val instr1 = MtkDaPatcher.unpackU32LE(data, midx + 4)
                val instr2 = MtkDaPatcher.unpackU32LE(data, midx2 + 4)
                val resolved = opMovToOffset(instr1, instr2, 2) ?: return null
                val addr = (resolved - da2Address).toInt()
                if (addr >= 0) {
                    val stub = MtkDaPatcher.hexToBytes(
                        "704c2de910b08de20080a0e100000fe3000846e30410a0e3002098e532ff2fe100000fe3000846e3000000e3000846e3" +
                            "002098e532ff2fe1000000e3000846e330ff2fe1708cbde8"
                    )
                    MtkDaPatcher.replaceSlice(data, addr, stub)
                    MtkDaPatcher.replaceSlice(data, idx + 1, newCmd)
                    return data
                }
            }
            return da2.copyOf()
        }
    }

    /**
     * Exact port of v6.py -> patch_da2(_da2)
     */
    fun patchDa2(
        da2: ByteArray,
        da2Address: Long,
        log: (String) -> Unit = {}
    ): ByteArray {
        log("[V6] Patching DA2 (${da2.size} bytes @ 0x%08X)...".format(da2Address))
        var da2Patched = da2.copyOf()
        var patched = false

        val armRwPattern: List<Int?> = listOf(
            0x00, 0x00, 0xA0, 0xE3, 0x04, 0x10, 0xA0, 0xE1, 0x00, 0x20, 0xA0, 0xE3,
            null, null, 0x00, 0xEB, 0x01, 0x40, 0x00, 0xE3
        )
        var pos = 0
        while (pos < da2.size) {
            val idx = MtkDaPatcher.findBinaryWildcard(da2, armRwPattern, pos) ?: break
            val low16 = ((da2Patched[idx + 0xC].toInt() and 0xFF) or
                ((da2Patched[idx + 0xD].toInt() and 0xFF) shl 8)) - 1
            val repPrefix = MtkDaPatcher.hexToBytes("0000A0E30410A0E12C220EE3002044E3")
            val rep = repPrefix + byteArrayOf(
                (low16 and 0xFF).toByte(),
                ((low16 ushr 8) and 0xFF).toByte(),
                0x00,
                0xEB.toByte()
            )
            MtkDaPatcher.replaceSlice(da2Patched, idx, rep)
            patched = true
            pos = idx + 0x14
        }

        if (!patched) {
            val arm64Pat = MtkDaPatcher.hexToBytes("20008052C0035FD6E0031F2AC0035FD6E0031F2AC0035FD6")
            MtkDaPatcher.findBinary(da2, arm64Pat)?.let { idx ->
                val rep = MtkDaPatcher.hexToBytes("20008052C0035FD620008052C0035FD620008052C0035FD6")
                MtkDaPatcher.replaceSlice(da2Patched, idx, rep)
                patched = true
            }
        }

        if (!patched) {
            val tclPat = MtkDaPatcher.hexToBytes("0100A0E31EFF2FE10000A0E31EFF2FE10000A0E31EFF2FE1")
            MtkDaPatcher.findBinary(da2, tclPat)?.let { idx ->
                val rep = MtkDaPatcher.hexToBytes("0100A0E31EFF2FE10100A0E31EFF2FE10100A0E31EFF2FE1")
                MtkDaPatcher.replaceSlice(da2Patched, idx, rep)
                patched = true
            }
        }

        if (patched) {
            log("[V6] Patched read_register / write_register.")
            // Patch on already-patched DA2 so earlier patches are preserved
            patchCustomCommand(da2Patched, da2Address)?.let { customPatched ->
                da2Patched = customPatched
                log("[V6] Patched CMD:CUSTOM extension hook.")
            }
        }

        val allowRwPattern: List<Int?> = listOf(
            0x00, 0xA0, 0xE3, 0x1E, 0xFF, 0x2F, 0xE1, null,
            0x00, 0xA0, 0xE3, 0x1E, 0xFF, 0x2F, 0xE1, null,
            0x00, 0xA0, 0xE3, 0x1E, 0xFF, 0x2F, 0xE1, 0x70, 0x4C
        )
        MtkDaPatcher.findBinaryWildcard(da2Patched, allowRwPattern)?.let { idx ->
            if (idx >= 1) {
                val rep = MtkDaPatcher.hexToBytes("0100A0E31EFF2FE10100A0E31EFF2FE10100A0E31EFF2FE1")
                MtkDaPatcher.replaceSlice(da2Patched, idx - 1, rep)
                log("[V6] Patched write partitions / allow_read / allow_write.")
            }
        }

        MtkDaPatcher.findBinary(da2Patched, MtkDaPatcher.hexToBytes("0110A0E3001080E5"))?.let { idx ->
            MtkDaPatcher.replaceSlice(da2Patched, idx, MtkDaPatcher.hexToBytes("0010A0E3001080E5"))
            log("[V6] Patched hash binding.")
        }

        MtkDaPatcher.findBinary(da2Patched, MtkDaPatcher.hexToBytes("C40804E3050044E3"))?.let { idx ->
            MtkDaPatcher.replaceSlice(da2Patched, idx + 8, MtkDaPatcher.hexToBytes("0000A0E3"))
        }

        MtkDaPatcher.findBinary(
            da2Patched,
            MtkDaPatcher.hexToBytes("30482DE908B08DE208D04DE20040A0E104008DE20050A0E3")
        )?.let { idx ->
            MtkDaPatcher.replaceSlice(da2Patched, idx, MtkDaPatcher.hexToBytes("0000A0E31EFF2FE1"))
            log("[V6] Patched generic partition SGPT verification.")
        }

        val slaCheck1 = MtkDaPatcher.findBinary(da2Patched, MtkDaPatcher.hexToBytes("320000E302004CE3"))
        if (slaCheck1 != null) {
            MtkDaPatcher.replaceSlice(da2Patched, slaCheck1, MtkDaPatcher.hexToBytes("0000A0E30000A0E30040A0E3"))
            log("[V6] Patched SLA signature check 1.")
            MtkDaPatcher.findBinary(da2Patched, MtkDaPatcher.hexToBytes("324000E302404CE3"))?.let { slaCheck2 ->
                MtkDaPatcher.replaceSlice(da2Patched, slaCheck2, MtkDaPatcher.hexToBytes("0040A0E30040A0E3"))
                log("[V6] Patched SLA signature check 2.")
            }
        } else {
            val infinix = MtkDaPatcher.findBinary(da2Patched, MtkDaPatcher.hexToBytes("F04D2DE918B08DE2F0D04DE20150A0E1"))
            if (infinix != null) {
                MtkDaPatcher.replaceSlice(da2Patched, infinix, MtkDaPatcher.hexToBytes("0000A0E31EFF2FE1"))
                log("[V6] Patched Infinix Remote SLA v3 authentication.")
            } else {
                val oppo = MtkDaPatcher.findBinary(da2Patched, MtkDaPatcher.hexToBytes("704C2DE910B08DE20060A0E10206A0E3"))
                    ?: MtkDaPatcher.findBinary(da2Patched, MtkDaPatcher.hexToBytes("704C2DE910B08DE20140A0E10050A0E17F0000EB"))
                if (oppo != null) {
                    MtkDaPatcher.replaceSlice(da2Patched, oppo, MtkDaPatcher.hexToBytes("0000A0E31EFF2FE1"))
                    log("[V6] Patched Oppo Remote SLA authentication.")
                    MtkDaPatcher.findBinary(da2Patched, MtkDaPatcher.hexToBytes("03000000FFFFFFFF0000000001000000"))?.let { allowIdx ->
                        MtkDaPatcher.replaceSlice(da2Patched, allowIdx, MtkDaPatcher.hexToBytes("FF000000"))
                    }
                    MtkDaPatcher.findBinary(da2Patched, MtkDaPatcher.hexToBytes("4C0804E30310A0E3050044E32E62A0E3"))?.let { fwIdx ->
                        MtkDaPatcher.replaceSlice(da2Patched, fwIdx, MtkDaPatcher.hexToBytes("4C0804E30510A0E3050044E30060A0E3"))
                    }
                } else {
                    val oppoV2 = MtkDaPatcher.findBinary(
                        da2Patched,
                        MtkDaPatcher.hexToBytes("FD7BBDA9F65701A9F44F02A9FD030091F50302AAF303012A")
                    )
                    if (oppoV2 != null) {
                        MtkDaPatcher.replaceSlice(da2Patched, oppoV2, MtkDaPatcher.hexToBytes("20008052C0035FD6"))
                        log("[V6] Patched Oppo Remote SLA V2 authentication.")
                        MtkDaPatcher.findBinary(da2Patched, MtkDaPatcher.hexToBytes("03000000FFFFFFFF0100000008000000"))?.let { allowV2 ->
                            MtkDaPatcher.replaceSlice(da2Patched, allowV2, MtkDaPatcher.hexToBytes("FF000000"))
                        }
                    } else {
                        MtkDaPatcher.findBinary(
                            da2Patched,
                            MtkDaPatcher.hexToBytes("F04D2DE918B08DE282DF4DE20160A0E138190FE30070A0E1420F8DE2")
                        )?.let { vivoIdx ->
                            MtkDaPatcher.replaceSlice(da2Patched, vivoIdx, MtkDaPatcher.hexToBytes("0000A0E31EFF2FE1"))
                            log("[V6] Patched Vivo Remote SLA authentication.")
                        }
                    }
                }
            }
        }

        MtkDaPatcher.findBinary(da2Patched, "DA.SLA\u0000ENABLED".toByteArray(Charsets.US_ASCII))?.let { slaIdx ->
            MtkDaPatcher.replaceSlice(da2Patched, slaIdx, "DA.SLA\u0000DISABLE".toByteArray(Charsets.US_ASCII))
            log("[V6] Patched generic Remote SLA flag (DA.SLA -> DISABLE).")
        }

        return da2Patched
    }

    /**
     * Patches da_xml.bin / da_xml_64.bin Stage 2 extension binary with DA2 symbol addresses (v6.py -> patch()).
     */
    fun patchExtensions(
        context: Context,
        da2: ByteArray,
        da2Address: Long,
        efuseAddr: Long?,
        log: (String) -> Unit = {}
    ): ByteArray? {
        val isArm64 = da2.size >= 8 && ((da2[4].toInt() and 0xF0) == 0xC0)
        val assetName = if (isArm64) "da_xml_64.bin" else "da_xml.bin"
        val rawExt = MtkAssetManager.loadPayloadBytes(context, assetName) ?: return null
        val daExtData = rawExt.copyOf()

        fun u64LE(v: Long): ByteArray = ByteArray(8) { i -> ((v ushr (i * 8)) and 0xFFL).toByte() }

        val registerPtr = MtkDaPatcher.findBinary(daExtData, byteArrayOf(0x11, 0x11, 0x11, 0x11)) ?: return null
        val patternSize = if (isArm64) 8 else 4
        val mmcGetCardPtr = MtkDaPatcher.findBinary(daExtData, ByteArray(patternSize) { 0x22 })
        val mmcSetPartCfgPtr = MtkDaPatcher.findBinary(daExtData, ByteArray(patternSize) { 0x33 })
        val mmcRpmbSendPtr = MtkDaPatcher.findBinary(daExtData, ByteArray(patternSize) { 0x44 })
        val ufshcdQueuePtr = MtkDaPatcher.findBinary(daExtData, ByteArray(patternSize) { 0x55 })
        val ufshcdFreeTagPtr = MtkDaPatcher.findBinary(daExtData, ByteArray(patternSize) { 0x66 })
        val gUfsHbaPtr = MtkDaPatcher.findBinary(daExtData, ByteArray(patternSize) { 0x77 })
        val efusePtr = MtkDaPatcher.findBinary(daExtData, byteArrayOf(0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte()))

        var registerXmlCmd = 0L
        var gUfsHba = 0L
        var ufshcdQueuecommand = 0L
        var ufshcdGetFreeTag = 0L
        var mmcGetCard = 0L
        var mmcSetPartConfig = 0L
        var mmcRpmbSendCommand = 0L

        if (isArm64) {
            val regHit = MtkDaPatcher.findBinary(
                da2,
                MtkDaPatcher.hexToBytes("FD7BBDA9F50B00F9FD030091F44F02A9F50300AA00058052")
            )
            if (regHit != null) registerXmlCmd = regHit.toLong() + da2Address

            val ar = Aarch64Tools(da2, da2Address)
            ar.findFunctionFromString("Controller enable failed\n")?.let { ufsCtrlOff ->
                val instr = ar.readU32(ufsCtrlOff + 0xC)
                if (instr != null) {
                    gUfsHba = ar.decodeAdrp(instr, (ufsCtrlOff + 0xC).toLong() + da2Address)?.first ?: 0L
                }
            }
            MtkDaPatcher.findBinary(
                da2,
                MtkDaPatcher.hexToBytes("FD7BBCA9F70B00F9FD030091F65702A9F44F03A9143440F9F50301AA")
            )?.let { ufshcdQueuecommand = it.toLong() + da2Address }

            ar.findFunctionFromString("[UFS] ufshcd_get_free_tag fail\n")?.let {
                ufshcdGetFreeTag = it.toLong() + da2Address
            }
            ar.findFunctionFromString("mmc_switch_part")?.let { swOff ->
                val bl = ar.getNextBlFromOff(swOff)
                if (bl != null) mmcGetCard = ar.getBlTarget(bl) ?: 0L
            }
            MtkDaPatcher.findBinary(da2, MtkDaPatcher.hexToBytes("088040398800083708B44939"))?.let {
                mmcSetPartConfig = it.toLong()
            }
            ar.findFunctionFromString("%sreq/1 fail %d\n")?.let { rpmbOff ->
                val logl = ar.getNextBlFromOff(rpmbOff)
                if (logl != null) {
                    val bl2 = ar.getNextBlFromOff(logl + 0x8)
                    if (bl2 != null) mmcRpmbSendCommand = ar.getBlTarget(bl2) ?: 0L
                }
            }
        } else {
            MtkDaPatcher.findBinary(
                da2,
                MtkDaPatcher.hexToBytes("704C2DE910B08DE20050A0E11400A0E3")
            )?.let { registerXmlCmd = it.toLong() + da2Address }

            val ufsIdx = MtkDaPatcher.findBinary(da2, MtkDaPatcher.hexToBytes("000094E5341090E5010011E30300000A"))
            if (ufsIdx != null && ufsIdx >= 8) {
                val instr1 = MtkDaPatcher.unpackU32LE(da2, ufsIdx - 8)
                val instr2 = MtkDaPatcher.unpackU32LE(da2, ufsIdx - 4)
                gUfsHba = opMovToOffset(instr1, instr2, 4) ?: 0L
                val q1 = MtkDaPatcher.findBinary(da2, MtkDaPatcher.hexToBytes("F04D2DE918B08DE208D04DE2484090E5"))
                    ?: MtkDaPatcher.findBinary(da2, MtkDaPatcher.hexToBytes("F04F2DE91CB08DE20CD04DE248A090E50080A0E1"))
                if (q1 != null) ufshcdQueuecommand = q1.toLong() + da2Address
                val f1 = MtkDaPatcher.findBinary(da2, MtkDaPatcher.hexToBytes("104C2DE908B08DE20040A0E3000051E3"))
                if (f1 != null) ufshcdGetFreeTag = f1.toLong() + da2Address
            }

            val m1 = MtkDaPatcher.findBinary(da2, MtkDaPatcher.hexToBytes("901220E01EFF2FE1"))
            if (m1 != null && m1 >= 0xC) mmcGetCard = (m1 - 0xC).toLong()
            val m2 = MtkDaPatcher.findBinary(da2, MtkDaPatcher.hexToBytes("F04B2DE918B08DE223DE4DE2"))
                ?: MtkDaPatcher.findBinary(da2, MtkDaPatcher.hexToBytes("F04B2DE918B08DE28EDF4DE2"))
            if (m2 != null) mmcSetPartConfig = m2.toLong()
            MtkDaPatcher.findBinary(da2, MtkDaPatcher.hexToBytes("F0482DE910B08DE208709BE5"))?.let {
                mmcRpmbSendCommand = it.toLong()
            }
        }

        MtkDaPatcher.replaceSlice(daExtData, registerPtr, MtkDaPatcher.intLE(registerXmlCmd))
        fun writeSlot(slotOff: Int?, value: Long) {
            if (slotOff != null) {
                MtkDaPatcher.replaceSlice(daExtData, slotOff, if (isArm64) u64LE(value) else MtkDaPatcher.intLE(value))
            }
        }
        writeSlot(mmcGetCardPtr, mmcGetCard)
        writeSlot(mmcSetPartCfgPtr, mmcSetPartConfig)
        writeSlot(mmcRpmbSendPtr, mmcRpmbSendCommand)
        writeSlot(ufshcdFreeTagPtr, ufshcdGetFreeTag)
        writeSlot(ufshcdQueuePtr, ufshcdQueuecommand)
        writeSlot(gUfsHbaPtr, gUfsHba)
        if (efusePtr != null && efuseAddr != null) {
            MtkDaPatcher.replaceSlice(daExtData, efusePtr, MtkDaPatcher.intLE(efuseAddr))
        }

        log("[V6] Patched $assetName XML Stage 2 extension (${daExtData.size} bytes).")
        return daExtData
    }

    fun uploadAndActivateExtensions(
        context: Context,
        xml: MtkXmlFlashEngine,
        da2: ByteArray,
        da2Address: Long,
        efuseAddr: Long?,
        log: (String) -> Unit = {}
    ): Boolean {
        val extBytes = patchExtensions(context, da2, da2Address, efuseAddr, log) ?: return false
        val customCmd = MtkXmlCmd.createCmd("CUSTOM")
        if (!xml.xsend(customCmd)) return false
        val okResp = xml.getResponse()
        if (okResp != "OK") return false

        if (!xml.xsendInt32(extBytes.size)) return false
        if (!xml.xsend(extBytes)) return false

        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()

        if (customAck(xml)) {
            xml.daExtLoaded = true
            customSetStorage(xml, isUfs = xml.detectedFlashType == "ufs")
            log("[+] DA XML Stage 2 Extensions loaded & active.")
            return true
        }
        return false
    }

    // =========================================================================
    // Custom V6 XML Extension RPC Commands
    // =========================================================================

    fun customAck(xml: MtkXmlFlashEngine): Boolean {
        if (!xml.xsend(MtkXmlCmd.createCmd("CUSTOMACK"))) return false
        xml.getResponse() // OK
        val data = xml.getResponseRaw() ?: ByteArray(0)
        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()
        return data.contentEquals(byteArrayOf(0xA4.toByte(), 0xA3.toByte(), 0xA2.toByte(), 0xA1.toByte()))
    }

    fun customSetStorage(xml: MtkXmlFlashEngine, isUfs: Boolean): Boolean {
        if (!xml.xsend(MtkXmlCmd.createCmd("CUSTOMSTORAGE"))) return false
        if (xml.getResponse() != "OK") return false
        xml.xsendInt32(if (isUfs) 1 else 0)
        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()
        return true
    }

    fun customRead(xml: MtkXmlFlashEngine, addr: Long, length: Int): ByteArray? {
        if (!xml.xsend(MtkXmlCmd.createCmd("CUSTOMMEMR"))) return null
        if (xml.getResponse() != "OK") return null
        xml.xsendInt64(addr)
        xml.xsendInt32(length)
        val data = xml.getResponseRaw()
        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()
        return data
    }

    fun customWrite(xml: MtkXmlFlashEngine, addr: Long, data: ByteArray): Boolean {
        // Fixed upstream v6.py typo: uses CUSTOMMEMW (not CUSTOMMEMR) for memory write
        if (!xml.xsend(MtkXmlCmd.createCmd("CUSTOMMEMW"))) return false
        if (xml.getResponse() != "OK") return false
        xml.xsendInt64(addr)
        xml.xsendInt32(data.size)
        xml.xsend(data)
        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()
        return true
    }

    fun customReadRegister(xml: MtkXmlFlashEngine, addr: Long, dwords: Int = 1): List<Long>? {
        if (!xml.xsend(MtkXmlCmd.createCmd("CUSTOMREGR"))) return null
        if (xml.getResponse() != "OK") return null
        xml.xsendInt32((addr and 0xFFFFFFFFL).toInt())
        xml.xsendInt32(dwords)
        val data = xml.getResponseRaw() ?: return null
        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()
        val out = ArrayList<Long>(dwords)
        var off = 0
        while (off + 4 <= data.size) {
            out.add(MtkDaPatcher.unpackU32LE(data, off))
            off += 4
        }
        return out
    }

    fun customWriteRegister(xml: MtkXmlFlashEngine, addr: Long, value: Long): Boolean {
        if (!xml.xsend(MtkXmlCmd.createCmd("CUSTOMREGW"))) return false
        if (xml.getResponse() != "OK") return false
        xml.xsendInt32((addr and 0xFFFFFFFFL).toInt())
        xml.xsendInt32((value and 0xFFFFFFFFL).toInt())
        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()
        return true
    }

    fun customRpmbInit(xml: MtkXmlFlashEngine, hwCode: Int): Boolean {
        if (!xml.xsend(MtkXmlCmd.createCmd("CUSTOMRPMBI"))) return false
        if (xml.getResponse() == "OK") {
            val mode = if (hwCode == 0x1209 || hwCode == 0x1129) 1 else 0
            xml.xsendInt32(mode)
            val statusBytes = xml.getResponseRaw() ?: ByteArray(4)
            val status = MtkDaPatcher.unpackU32LE(statusBytes, 0)
            if (status == 0L) {
                xml.getResponseRaw() // derived rpmb key
                xml.getResponse() // CMD:END
                xml.ack()
                xml.getResponse() // CMD:START
                xml.ack()
                return true
            }
        }
        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()
        return false
    }

    fun customRpmbRead(xml: MtkXmlFlashEngine, sector: Int, sectors: Int): ByteArray? {
        if (!xml.xsend(MtkXmlCmd.createCmd("CUSTOMRPMBR"))) return null
        if (xml.getResponse() != "OK") return null
        xml.xsendInt32(sector)
        xml.xsendInt32(sectors)
        val out = ByteArray(sectors * 0x100)
        for (i in 0 until sectors) {
            val tmp = xml.getResponseRaw() ?: return null
            if (tmp.size != 0x100) return null
            System.arraycopy(tmp, 0, out, i * 0x100, 0x100)
        }
        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()
        return out
    }

    fun customRpmbWrite(xml: MtkXmlFlashEngine, sector: Int, sectors: Int, data: ByteArray): Boolean {
        if (data.size % 0x100 != 0) return false
        if (!xml.xsend(MtkXmlCmd.createCmd("CUSTOMRPMBW"))) return false
        if (xml.getResponse() != "OK") return false
        xml.xsendInt32(sector)
        xml.xsendInt32(sectors)
        for (i in 0 until sectors) {
            xml.xsend(data.copyOfRange(i * 0x100, i * 0x100 + 0x100))
            val resp = xml.getResponseRaw() ?: ByteArray(2)
            val err = if (resp.size >= 2) (resp[0].toInt() and 0xFF) or ((resp[1].toInt() and 0xFF) shl 8) else 1
            if (err != 0) {
                xml.getResponse() // CMD:END
                xml.ack()
                xml.getResponse() // CMD:START
                xml.ack()
                return false
            }
        }
        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()
        return true
    }

    fun customRpmbProg(xml: MtkXmlFlashEngine, rpmbKey: ByteArray): Boolean {
        if (!xml.xsend(MtkXmlCmd.createCmd("CUSTOMRPMBP"))) return false
        if (xml.getResponse() != "OK") return false
        xml.xsend(rpmbKey)
        val status = xml.getResponseRaw() ?: return false
        val code = if (status.size >= 4) MtkDaPatcher.unpackU32LE(status, 0) else 1L
        xml.getResponse() // CMD:END
        xml.ack()
        xml.getResponse() // CMD:START
        xml.ack()
        return code == 0L
    }
}
