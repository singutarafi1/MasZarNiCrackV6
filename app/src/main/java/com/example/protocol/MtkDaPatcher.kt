package com.example.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * MediaTek DA1/DA2 Binary Patcher, Hash Fixer, EMI Extractor, and XFlash Extension Patcher
 * Faithfully ported from Python mtkclient:
 * - mtkclient/Library/mtk_class.py (patch_preloader_security_da1 / da2)
 * - mtkclient/Library/DA/daconfig.py (m_extract_emi)
 * - mtkclient/Library/DA/mtk_daloader.py (compute_hash_pos, find_da_hash_v5/v6, calc_da_hash, fix_hash)
 * - mtkclient/Library/DA/xflash/extension/xflash.py (patch, patch_da1, patch_da2)
 */
object MtkDaPatcher {

    internal fun intLE(v: Long): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((v and 0xFFFFFFFFL).toInt()).array()

    internal fun unpackU32LE(data: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > data.size) return 0L
        return (data[offset].toLong() and 0xFFL) or
            ((data[offset + 1].toLong() and 0xFFL) shl 8) or
            ((data[offset + 2].toLong() and 0xFFL) shl 16) or
            ((data[offset + 3].toLong() and 0xFFL) shl 24)
    }

    internal fun hexToBytes(hex: String): ByteArray {
        val len = hex.length / 2
        val out = ByteArray(len)
        for (i in 0 until len) {
            out[i] = ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    /**
     * Exact byte finder supporting nullable wildcard elements (null == match any byte).
     */
    fun findBinary(data: ByteArray, pattern: ByteArray, startPos: Int = 0): Int? {
        if (pattern.isEmpty() || data.size < pattern.size) return null
        val limit = data.size - pattern.size
        for (i in startPos.coerceAtLeast(0)..limit) {
            var match = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return null
    }

    fun findBinaryWildcard(data: ByteArray, pattern: List<Int?>, startPos: Int = 0): Int? {
        if (pattern.isEmpty() || data.size < pattern.size) return null
        val limit = data.size - pattern.size
        for (i in startPos.coerceAtLeast(0)..limit) {
            var match = true
            for (j in pattern.indices) {
                val expected = pattern[j] ?: continue
                if ((data[i + j].toInt() and 0xFF) != expected) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return null
    }

    internal fun replaceSlice(target: ByteArray, offset: Int, replacement: ByteArray) {
        if (offset >= 0 && offset + replacement.size <= target.size) {
            System.arraycopy(replacement, 0, target, offset, replacement.size)
        }
    }

    /**
     * Exact port of daconfig.py -> m_extract_emi()
     */
    fun extractEmi(preloaderBytes: ByteArray?, isXFlash: Boolean = true): ByteArray? {
        if (preloaderBytes == null || preloaderBytes.isEmpty()) return null
        var data = preloaderBytes
        val headerSig = byteArrayOf(0x4D, 0x4D, 0x4D, 0x01, 0x38, 0x00, 0x00, 0x00)
        val hdrIdx = findBinary(data, headerSig)
        if (hdrIdx != null) {
            data = data.copyOfRange(hdrIdx, data.size)
            if (data.size < 0x34) return null
            val mlen = unpackU32LE(data, 0x20).toInt()
            val sigLen = unpackU32LE(data, 0x2C).toInt()
            val trimmedEnd = (mlen - sigLen).coerceIn(4, data.size)
            data = data.copyOfRange(0, trimmedEnd)
            if (data.size < 4) return null
            var dramSize = unpackU32LE(data, data.size - 4).toInt()
            if (dramSize == 0 && data.size > 0x804) {
                data = data.copyOfRange(0, data.size - 0x800)
                dramSize = unpackU32LE(data, data.size - 4).toInt()
            }
            if (dramSize > 0 && dramSize + 4 <= data.size) {
                data = data.copyOfRange(data.size - dramSize - 4, data.size - 4)
            }
        }

        val bldrStr = "MTK_BLOADER_INFO_v".toByteArray(Charsets.US_ASCII)
        val bldrIdx = findBinary(data, bldrStr) ?: return null
        if (bldrIdx == 0 && isXFlash) {
            return data
        }
        val mtkBinIdx = findBinary(data, "MTK_BIN".toByteArray(Charsets.US_ASCII))
        if (mtkBinIdx != null && mtkBinIdx + 0x0C < data.size) {
            return data.copyOfRange(mtkBinIdx + 0x0C, data.size)
        }
        return if (isXFlash) data else null
    }

    /**
     * Exact port of mtk_class.py -> patch_preloader_security_da1()
     */
    fun patchPreloaderSecurityDa1(data: ByteArray, log: (String) -> Unit = {}): ByteArray {
        val patched = data.copyOf()
        val patches = listOf(
            Triple("A3687BB12846", "0123A3602846", "oppo security"),
            Triple("B3F5807F01D1", "B3F5807F01D14FF000004FF000007047", "mt6739 c30"),
            Triple("B3F5807F04BF4FF4807305F011B84FF0FF307047", "B3F5807F04BF4FF480734FF000004FF000007047", "regular"),
            Triple("10B50C680268", "10B5012010BD", "ram blacklist"),
            Triple("08B5104B7B441B681B68", "00207047000000000000", "seclib_sec_usbdl_enabled"),
            Triple("5072656C6F61646572205374617274", "50617463686564204C205374617274", "Patched loader msg"),
            Triple("F0B58BB002AE20250C460746", "002070470000000000205374617274", "sec_img_auth"),
            Triple("FFC0F3400008BD", "FF4FF0000008BD", "get_vfy_policy"),
            Triple("040007C0", "00000000", "hash_check"),
            Triple("CCF20709", "4FF00009", "hash_check2")
        )
        for ((patHex, repHex, label) in patches) {
            val idx = findBinary(patched, hexToBytes(patHex))
            if (idx != null) {
                replaceSlice(patched, idx, hexToBytes(repHex))
                log("Patched \"$label\" in DA1")
            }
        }
        val hashCheck3Pattern: List<Int?> = listOf(0x14, 0x2C, 0xF6, null, 0xFE, 0xE7)
        findBinaryWildcard(patched, hashCheck3Pattern)?.let { idx ->
            replaceSlice(patched, idx, ByteArray(6))
            log("Patched \"hash_check3\" in DA1")
        }
        return patched
    }

    /**
     * Exact port of mtk_class.py -> patch_preloader_security_da2()
     */
    fun patchPreloaderSecurityDa2(data: ByteArray, log: (String) -> Unit = {}): ByteArray {
        val patched = data.copyOf()
        val patches = listOf(
            Triple("A3687BB12846", "0123A3602846", "oppo security"),
            Triple("B3F5807F01D1", "B3F5807F01D14FF000004FF000007047", "mt6739 c30"),
            Triple("B3F5807F04BF4FF4807305F011B84FF0FF307047", "B3F5807F04BF4FF480734FF000004FF000007047", "regular"),
            Triple("10B50C680268", "10B5012010BD", "ram blacklist"),
            Triple("08B5104B7B441B681B68", "00207047000000000000", "seclib_sec_usbdl_enabled"),
            Triple("5072656C6F61646572205374617274", "50617463686564204C205374617274", "Patched loader msg"),
            Triple("F0B58BB002AE20250C460746", "002070470000000000205374617274", "sec_img_auth"),
            Triple("FFC0F3400008BD", "FF4FF0000008BD", "get_vfy_policy")
        )
        for ((patHex, repHex, label) in patches) {
            val idx = findBinary(patched, hexToBytes(patHex))
            if (idx != null) {
                replaceSlice(patched, idx, hexToBytes(repHex))
                log("Patched \"$label\" in DA2")
            }
        }
        return patched
    }

    /**
     * Exact port of xflash.py -> patch_da1()
     */
    fun patchXFlashDa1(da1: ByteArray, log: (String) -> Unit = {}): ByteArray {
        val patched = patchPreloaderSecurityDa1(da1, log)
        val verChk = findBinary(da1, hexToBytes("1FB5002301A8009300F0"))
        if (verChk != null) {
            replaceSlice(patched, verChk, hexToBytes("00207047"))
            log("Patched DA1 version check")
        }
        return patched
    }

    /**
     * Exact port of xflash.py -> patch_da2()
     */
    fun patchXFlashDa2(da2: ByteArray, da2StartAddr: Long, log: (String) -> Unit = {}): ByteArray {
        val patched = patchPreloaderSecurityDa2(da2, log)

        // Huawei RMA check
        findBinary(da2, hexToBytes("012B03D10123"))?.let {
            replaceSlice(patched, it, ByteArray(4))
        }

        // Oppo/Oplus security
        if (findBinary(da2, "[oplus]".toByteArray()) != null || findBinary(da2, "[OPPO]".toByteArray()) != null) {
            var flag = false
            val oppoWildcard = listOf<Int?>(0x0A, 0x00, 0x00, 0xE0, null, 0x00, 0x00, 0xE0)
            findBinaryWildcard(da2, oppoWildcard)?.let { pos ->
                if (pos >= 4) {
                    val authFlagPtr = unpackU32LE(patched, pos - 4)
                    val offset = (authFlagPtr - da2StartAddr).toInt()
                    if (offset in 0 until patched.size - 4 && unpackU32LE(patched, offset) == 3L) {
                        patched[offset] = 0x01
                        log("Oppo g_oppo_auth_status patched")
                        flag = true
                    }
                }
            }
            if (!flag) {
                val p1 = hexToBytes("FFFFFFFF00000000000000000000000003")
                val p2 = hexToBytes("6388741800000000000000000000000003")
                val off = findBinary(da2, p1) ?: findBinary(da2, p2)
                if (off != null && off + 0x10 < patched.size) {
                    patched[off + 0x10] = 0x01
                    log("Oppo g_oppo_auth_status patched")
                }
            }
            var pos = 0
            while (true) {
                val hit = findBinary(da2, hexToBytes("013B012B08D9"), pos) ?: break
                replaceSlice(patched, hit, hexToBytes("012008BD"))
                pos = hit + 1
            }
        }

        // Hash binding 0xC0020004 / 0xC0020005
        val hb1 = findBinary(da2, hexToBytes("012303600020704770B5"))
        if (hb1 != null) {
            patched[hb1] = 0x00
        } else {
            val hb2 = findBinary(da2, hexToBytes("0110A0E3001080E50000A0E31EFF2FE1"))
            if (hb2 != null) {
                patched[hb2] = 0x00
                findBinary(da2, hexToBytes("F04F2DE91CB08DE254D04DE20050A0E1"))?.let {
                    replaceSlice(patched, it, hexToBytes("0000A0E31EFF2FE1"))
                }
            }
        }

        // Hash check cmd_boot_to (0xC0070004)
        val authAddr1 = findBinary(da2, intLE(0xC0070004L))
        if (authAddr1 != null) {
            replaceSlice(patched, authAddr1, ByteArray(4))
        } else {
            val authAddr2 = findBinary(da2, hexToBytes("4FF00409CCF20709"))
            if (authAddr2 != null) {
                replaceSlice(patched, authAddr2, hexToBytes("4FF000094FF00009"))
            } else {
                findBinary(da2, hexToBytes("4FF00409324601980399CCF20709"))?.let {
                    replaceSlice(patched, it, hexToBytes("4FF000093246019803994FF00009"))
                }
            }
        }

        // Disable security checks
        findBinary(da2, hexToBytes("012303600020704770B5"))?.let {
            replaceSlice(patched, it, hexToBytes("0023"))
            log("DA2 security check patched")
        }

        // Disable DA anti-rollback (0xC0020053)
        findBinary(da2, intLE(0xC0020053L))?.let {
            replaceSlice(patched, it, ByteArray(4))
            log("DA2 anti-rollback patched")
        }

        // Disable SBC
        findBinary(da2, hexToBytes("024B1868C0F340007047"))?.let {
            replaceSlice(patched, it + 4, hexToBytes("4FF00000"))
            log("DA2 SBC disabled")
        }

        // Disable Motorola SLA
        findBinary(da2, hexToBytes("010001C001207047"))?.let {
            replaceSlice(patched, it + 4, hexToBytes("0020"))
            log("DA2 SLA disabled")
        }

        // Allow register read/write (0xC004000D)
        findBinary(da2, intLE(0xC004000DL))?.let {
            replaceSlice(patched, it, ByteArray(4))
            log("DA2 register R/W unlocked")
        }

        // Patch write not allowed
        var idx = findBinary(patched, hexToBytes("37B50023044602A8"))
        while (idx != null) {
            replaceSlice(patched, idx, hexToBytes("37B5002003B030BD"))
            idx = findBinary(patched, hexToBytes("37B50023044602A8"))
        }
        val wn1 = findBinary(patched, hexToBytes("0C23CCF20203"))
        if (wn1 != null) {
            replaceSlice(patched, wn1, hexToBytes("002300230023"))
            findBinary(patched, hexToBytes("2A23CCF20203"))?.let {
                replaceSlice(patched, it, hexToBytes("002300230023"))
            }
        }

        return patched
    }

    data class HashPosition(
        val hashAddr: Int,
        val hashMode: Int, // 0=MD5, 1=SHA1, 2=SHA256
        val hashLen: Int
    )

    private fun digest(algo: String, input: ByteArray): ByteArray =
        MessageDigest.getInstance(algo).digest(input)

    private fun calcDaHash(da1: ByteArray, da2Slice: ByteArray): Pair<Int, Int> {
        val md5 = digest("MD5", da2Slice)
        findBinary(da1, md5)?.let { return 0 to it }
        val sha1 = digest("SHA-1", da2Slice)
        findBinary(da1, sha1)?.let { return 1 to it }
        val sha256 = digest("SHA-256", da2Slice)
        findBinary(da1, sha256)?.let { return 2 to it }
        return -1 to -1
    }

    private fun findDaHashV5(da1: ByteArray): Pair<Int, Int> {
        val mmuIdx = findBinary(da1, "MMU MAP: VA".toByteArray(Charsets.US_ASCII)) ?: return -1 to -1
        if (mmuIdx < 0x30) return -1 to -1
        val tailZero = da1[mmuIdx - 4] == 0.toByte() &&
            da1[mmuIdx - 3] == 0.toByte() &&
            da1[mmuIdx - 2] == 0.toByte() &&
            da1[mmuIdx - 1] == 0.toByte()
        return if (tailZero) (mmuIdx - 0x30) to 2 else (mmuIdx - 0x14) to 1
    }

    private fun findDaHashV6(da1: ByteArray, sigLen: Int): Pair<Int, Int> {
        val pos = da1.size - sigLen - 0x30
        if (pos < 0 || pos + 0x30 > da1.size) return -1 to -1
        val tailZero = da1[pos + 0x2C] == 0.toByte() &&
            da1[pos + 0x2D] == 0.toByte() &&
            da1[pos + 0x2E] == 0.toByte() &&
            da1[pos + 0x2F] == 0.toByte()
        return if (tailZero) pos to 2 else -1 to -1
    }

    fun computeHashPos(da1: ByteArray, da2: ByteArray, da1SigLen: Int, da2SigLen: Int, isV6: Boolean): HashPosition? {
        val sig = if (da2SigLen in 1 until da2.size) da2SigLen else 0
        var hashLen = da2.size - sig
        var (hashMode, idx) = calcDaHash(da1, da2.copyOfRange(0, hashLen))
        if (idx == -1) {
            hashLen = da2.size
            val res = calcDaHash(da1, da2)
            hashMode = res.first
            idx = res.second
            if (idx == -1 && !isV6) {
                hashLen = da2.size - sig
                val v5 = findDaHashV5(da1)
                idx = v5.first
                hashMode = v5.second
            } else if (idx == -1 && isV6) {
                hashLen = da2.size - sig
                val v6 = findDaHashV6(da1, da1SigLen)
                idx = v6.first
                hashMode = v6.second
            }
        }
        return if (idx != -1 && hashMode != -1) HashPosition(idx, hashMode, hashLen) else null
    }

    fun fixHash(da1: ByteArray, da2: ByteArray, pos: HashPosition): ByteArray {
        val out = da1.copyOf()
        val slice = da2.copyOfRange(0, pos.hashLen.coerceAtMost(da2.size))
        val newHash = when (pos.hashMode) {
            0 -> digest("MD5", slice)
            1 -> digest("SHA-1", slice)
            2 -> digest("SHA-256", slice)
            else -> return out
        }
        replaceSlice(out, pos.hashAddr, newHash)
        return out
    }

    /**
     * Patches DA1 & DA2 and fixes DA2 hash embedded in DA1 (matches xflash_lib.py -> patch_da)
     * Returns Triple(da1Patched, da2PatchedTrimmed, patchApplied)
     */
    fun patchDa1AndDa2(
        da1: ByteArray,
        da2: ByteArray,
        da1SigLen: Int,
        da2SigLen: Int,
        da2StartAddr: Long,
        isV6: Boolean,
        log: (String) -> Unit = {}
    ): Triple<ByteArray, ByteArray, Boolean> {
        val hashPos = computeHashPos(da1, da2, da1SigLen, da2SigLen, isV6)
        if (hashPos != null) {
            val da1Patched0 = patchXFlashDa1(da1, log)
            val da2PatchedFull = patchXFlashDa2(da2, da2StartAddr, log)
            val da1Fixed = fixHash(da1Patched0, da2PatchedFull, hashPos)
            val trimmedLen = hashPos.hashLen.coerceIn(1, da2PatchedFull.size)
            return Triple(da1Fixed, da2PatchedFull.copyOfRange(0, trimmedLen), true)
        }
        val trimEnd = (da2.size - da2SigLen.coerceIn(0, da2.size - 1)).coerceAtLeast(1)
        return Triple(da1, da2.copyOfRange(0, trimEnd), false)
    }

    /**
     * Exact port of xflash.py -> XFlashExt.patch() for da_x.bin Stage 2 extensions
     */
    fun patchXFlashExtensions(
        da2: ByteArray,
        da2Address: Long,
        rawDaX: ByteArray,
        efuseAddr: Long?
    ): ByteArray? {
        val daExt = rawDaX.copyOf()
        val regDevCtrlRaw = findBinary(da2, hexToBytes("38B505460C20"))
        var mmcGetCardRaw = findBinary(da2, hexToBytes("4B4FF43C72"))
        if (mmcGetCardRaw != null) {
            mmcGetCardRaw -= 1
        } else {
            val alt = findBinary(da2, hexToBytes("A3EB0013181A02EB0010"))
            if (alt != null) mmcGetCardRaw = alt - 10
        }

        var mmcSetPartCfgRaw: Int? = null
        var pos = 0
        while (true) {
            val hit = findBinary(da2, hexToBytes("C3690A4610B5"), pos) ?: break
            if (hit + 22 <= da2.size && da2[hit + 20] == 0xB3.toByte() && da2[hit + 21] == 0x21.toByte()) {
                mmcSetPartCfgRaw = hit
                break
            }
            pos = hit + 1
        }
        if (mmcSetPartCfgRaw == null) {
            mmcSetPartCfgRaw = findBinary(da2, hexToBytes("C36913F00103"))
        }

        val mmcRpmbRaw = findBinary(da2, hexToBytes("F8B506469DF81850"))
            ?: findBinary(da2, hexToBytes("2DE9F0414FF6FD74"))

        var gUfsHba: Long? = null
        var ptrUfs = findBinary(da2, hexToBytes("20460BB0BDE8F08300BF"))
        if (ptrUfs != null && ptrUfs + 14 <= da2.size) {
            gUfsHba = unpackU32LE(da2, ptrUfs + 10)
        } else {
            ptrUfs = findBinary(da2, hexToBytes("20460DB0BDE8F083"))
            if (ptrUfs != null && ptrUfs + 12 <= da2.size) {
                gUfsHba = unpackU32LE(da2, ptrUfs + 8)
            } else {
                ptrUfs = findBinary(da2, hexToBytes("214602F002FB1BE600BF"))
                if (ptrUfs != null && ptrUfs + 22 <= da2.size) {
                    gUfsHba = unpackU32LE(da2, ptrUfs + 18)
                }
            }
        }

        val ufsFreeTagRaw = if (ptrUfs != null) {
            findBinaryWildcard(da2, listOf(0xB5, null, 0xB1, 0x90, 0xF8))
        } else null
        val ufsQueueCmdRaw = if (ptrUfs != null) {
            findBinary(da2, hexToBytes("2DE9F8430127"))
        } else null

        val p1 = findBinary(daExt, hexToBytes("11111111")) ?: return null
        val p2 = findBinary(daExt, hexToBytes("22222222")) ?: return null
        val p3 = findBinary(daExt, hexToBytes("33333333"))
        val p4 = findBinary(daExt, hexToBytes("44444444"))
        val p5 = findBinary(daExt, hexToBytes("55555555"))
        val p6 = findBinary(daExt, hexToBytes("66666666"))
        val p7 = findBinary(daExt, hexToBytes("77777777"))
        val p8 = findBinary(daExt, hexToBytes("88888888"))

        fun thumbAddr(off: Int?, sub: Long = 0L): Long =
            if (off != null) ((off.toLong() + (da2Address - sub)) or 1L) else 0L

        replaceSlice(daExt, p1, intLE(thumbAddr(regDevCtrlRaw)))
        replaceSlice(daExt, p2, intLE(thumbAddr(mmcGetCardRaw)))
        if (p3 != null) replaceSlice(daExt, p3, intLE(thumbAddr(mmcSetPartCfgRaw)))
        if (p4 != null) replaceSlice(daExt, p4, intLE(thumbAddr(mmcRpmbRaw)))
        if (p6 != null) replaceSlice(daExt, p6, intLE(thumbAddr(ufsFreeTagRaw, 1L)))
        if (p5 != null) replaceSlice(daExt, p5, intLE(thumbAddr(ufsQueueCmdRaw)))
        if (p7 != null) replaceSlice(daExt, p7, intLE(gUfsHba ?: 0L))
        if (p8 != null && efuseAddr != null) replaceSlice(daExt, p8, intLE(efuseAddr))

        return daExt
    }
}
