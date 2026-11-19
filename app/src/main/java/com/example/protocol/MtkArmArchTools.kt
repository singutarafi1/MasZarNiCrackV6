package com.example.protocol

/**
 * Complete ARM32 & AArch64 Exploitation / Disassembly Toolkit
 * Direct, faithful port of Python mtkclient:
 * - mtkclient/Library/Exploit/exptools/arch.py (ArchTools)
 * - mtkclient/Library/Exploit/exptools/arm_tools.py (ArmTools)
 * - mtkclient/Library/Exploit/exptools/aarch_tools.py (Aarch64Tools)
 * - mtkclient/Library/DA/xml/extension/v6.py (offset_to_op_mov, op_mov_to_offset)
 */
abstract class ArchTools(
    val data: ByteArray,
    val baseAddr: Long
) {
    fun vaToOffset(va: Long): Int? {
        val off = va - baseAddr
        if (off < 0L || off >= data.size.toLong()) return null
        return off.toInt()
    }

    fun offsetToVa(offset: Int): Long? {
        if (offset < 0 || offset >= data.size) return null
        return baseAddr + offset.toLong()
    }

    open fun isBxLr(instr: Long): Boolean = false

    abstract fun resolveRegisterValue(at: Int, reg: Int, lookahead: Int): Long?

    abstract fun resolveRegisterValueBack(at: Int, reg: Int, lookback: Int): Long?

    fun readU32(offset: Int): Long? {
        if (offset < 0 || offset + 4 > data.size) return null
        return (data[offset].toLong() and 0xFFL) or
            ((data[offset + 1].toLong() and 0xFFL) shl 8) or
            ((data[offset + 2].toLong() and 0xFFL) shl 16) or
            ((data[offset + 3].toLong() and 0xFFL) shl 24)
    }

    fun readU64(offset: Int): Long? {
        if (offset < 0 || offset + 8 > data.size) return null
        var res = 0L
        for (i in 0 until 8) {
            res = res or ((data[offset + i].toLong() and 0xFFL) shl (i * 8))
        }
        return res
    }

    open fun findString(s: String): Int? {
        val targetBytes = s.toByteArray(Charsets.UTF_8)
        val withNull = targetBytes + byteArrayOf(0)
        MtkDaPatcher.findBinary(data, withNull)?.let { return it }
        return MtkDaPatcher.findBinary(data, targetBytes)
    }

    fun getRawData(): ByteArray = data

    abstract fun decodeBl(instr: Long, pc: Long): Long?

    fun getBlTarget(offset: Int): Long? {
        val instr = readU32(offset) ?: return null
        val pc = offsetToVa(offset) ?: return null
        return decodeBl(instr, pc)
    }

    fun getNextBlFromOff(offset: Int): Int? {
        var off = offset
        while (off < data.size) {
            if (getBlTarget(off) != null) return off
            off += 4
        }
        return null
    }

    fun getNextBFromOff(offset: Int): Int? = getNextBlFromOff(offset)

    fun getBTarget(offset: Int): Long? = getBlTarget(offset)

    fun getPreviousBlFromOff(offset: Int): Int? {
        var scanOff = offset - 4
        val limit = maxOf(0, offset - 0x1000)
        while (scanOff >= limit) {
            if (getBlTarget(scanOff) != null) return scanOff
            scanOff -= 4
        }
        return null
    }
}

class ArmTools(data: ByteArray, baseAddr: Long) : ArchTools(data, baseAddr) {

    fun isMovtImm(instr: Long, imm16: Int): Boolean {
        val decoded = decodeMovt(instr) ?: return false
        return decoded.second == imm16
    }

    fun forceReturn(target: ByteArray, offset: Int, value: Int, thumb: Boolean = false): ByteArray {
        if (thumb) {
            val movR0 = 0x2000 or (value and 0xFF)
            val bxLr = 0x4770
            if (offset >= 0 && offset + 4 <= target.size) {
                target[offset] = (movR0 and 0xFF).toByte()
                target[offset + 1] = ((movR0 ushr 8) and 0xFF).toByte()
                target[offset + 2] = (bxLr and 0xFF).toByte()
                target[offset + 3] = ((bxLr ushr 8) and 0xFF).toByte()
            }
            return target
        }
        val movR0 = 0xE3A00000L or (value and 0xFF).toLong() or ((value shl 4) and 0xF00).toLong()
        val bxLr = 0xE12FFF1EL
        if (offset >= 0 && offset + 8 <= target.size) {
            MtkDaPatcher.replaceSlice(target, offset, MtkDaPatcher.intLE(movR0))
            MtkDaPatcher.replaceSlice(target, offset + 4, MtkDaPatcher.intLE(bxLr))
        }
        return target
    }

    override fun decodeBl(instr: Long, pc: Long): Long? {
        val opcode = instr and 0xFF000000L
        if (opcode != 0xEB000000L && opcode != 0xEA000000L) return null
        var imm24 = instr and 0x00FFFFFFL
        if ((imm24 and 0x00800000L) != 0L) {
            imm24 -= 0x01000000L
        }
        val armPc = pc + 8L
        return armPc + (imm24 * 4L)
    }

    fun isPrologue(instr: Long): Boolean {
        return (instr and 0xFFFF0000L) == 0xE92D0000L && (instr and (1L shl 14)) != 0L
    }

    fun getMovwReg(instr: Long): Int = ((instr ushr 12) and 0xFL).toInt()

    fun isMovwImm(instr: Long, imm16: Int): Boolean {
        val decoded = decodeMovw(instr) ?: return false
        return decoded.second == imm16
    }

    fun getMovtReg(instr: Long): Int = ((instr ushr 12) and 0xFL).toInt()

    fun findStringXref(s: String): Int? {
        val strOff = findString(s) ?: return null
        val strVa = baseAddr + strOff.toLong()
        val low16 = (strVa and 0xFFFFL).toInt()
        val high16 = ((strVa ushr 16) and 0xFFFFL).toInt()

        for (off in 0 until (data.size - 8) step 4) {
            val instr1 = readU32(off) ?: continue
            if (isMovwImm(instr1, low16)) {
                val reg = getMovwReg(instr1)
                var found = false
                val lookaheadStart = off + 4
                val lookaheadEnd = minOf(off + 80, data.size)
                for (lookaheadOff in lookaheadStart until lookaheadEnd step 4) {
                    val instr2 = readU32(lookaheadOff) ?: break
                    if (isMovtImm(instr2, high16) && getMovtReg(instr2) == reg) {
                        found = true
                        break
                    }
                }
                if (found) return off
            }
        }

        for (off in 0 until (data.size - 4) step 4) {
            val instr = readU32(off) ?: continue
            val pc = offsetToVa(off) ?: continue
            val ldr = decodeLdrPc(instr, pc)
            if (ldr != null && ldr.second == strVa) {
                return off
            }
        }
        return null
    }

    fun findFunctionStartFromOff(offset: Int): Int? {
        val limit = 0x2000
        val searchStart = maxOf(0, offset - limit)
        var off = offset
        while (off >= searchStart) {
            val instr = readU32(off)
            if (instr != null && isPrologue(instr)) {
                return off
            }
            off -= 4
        }
        return null
    }

    fun decodeSubImm(instr: Long): Triple<Int, Int, Int>? {
        val masked = instr and 0x0FF00000L
        if (masked == 0x02400000L || masked == 0x00400000L) {
            val rn = ((instr ushr 16) and 0xFL).toInt()
            val rd = ((instr ushr 12) and 0xFL).toInt()
            val imm12 = (instr and 0xFFFL).toInt()
            return Triple(rn, rd, imm12)
        }
        return null
    }

    fun decodeMov(instr: Long): Pair<Int, Int>? {
        if ((instr and 0x0FE00FF0L) == 0x01A00000L) {
            val rd = ((instr ushr 12) and 0xFL).toInt()
            val rm = (instr and 0xFL).toInt()
            return rm to rd
        }
        return null
    }

    fun decodeMovt(instr: Long): Pair<Int, Int>? {
        if ((instr and 0x0FF00000L) == 0x03400000L) {
            val rd = ((instr ushr 12) and 0xFL).toInt()
            val imm4 = ((instr ushr 16) and 0xFL).toInt()
            val imm12 = (instr and 0xFFFL).toInt()
            val imm16 = (imm4 shl 12) or imm12
            return rd to imm16
        }
        return null
    }

    fun decodeMovw(instr: Long): Pair<Int, Int>? {
        if ((instr and 0x0FF00000L) == 0x03000000L) {
            val rd = ((instr ushr 12) and 0xFL).toInt()
            val imm4 = ((instr ushr 16) and 0xFL).toInt()
            val imm12 = (instr and 0xFFFL).toInt()
            val imm16 = (imm4 shl 12) or imm12
            return rd to imm16
        }
        return null
    }

    fun decodeLdrPc(instr: Long, pc: Long): Pair<Int, Long>? {
        if ((instr and 0x0C5F0000L) == 0x041F0000L) {
            val uBit = ((instr ushr 23) and 1L).toInt()
            val rd = ((instr ushr 12) and 0xFL).toInt()
            val imm12 = instr and 0xFFFL
            val armPc = pc + 8L
            val targetAddr = if (uBit == 1) armPc + imm12 else armPc - imm12
            return rd to targetAddr
        }
        return null
    }

    fun decodeSubReg(instr: Long): Triple<Int, Int, Int>? {
        if ((instr and 0x0FE00FF0L) == 0x00400000L) {
            val rn = ((instr ushr 16) and 0xFL).toInt()
            val rd = ((instr ushr 12) and 0xFL).toInt()
            val rm = (instr and 0xFL).toInt()
            return Triple(rn, rm, rd)
        }
        return null
    }

    fun findCallArgFromString(s: String, argIdx: Int): Long? {
        val start = findStringXref(s) ?: return null
        var off = start
        while (off < data.size) {
            if (getBlTarget(off) != null) {
                return resolveRegisterValue(off, argIdx, 50)
            }
            off += 4
        }
        return null
    }

    fun decodeAddImm(instr: Long): Triple<Int, Int, Int>? {
        val masked = instr and 0x0FF00000L
        if (masked == 0x02800000L || masked == 0x00800000L) {
            val rn = ((instr ushr 16) and 0xFL).toInt()
            val rd = ((instr ushr 12) and 0xFL).toInt()
            val imm12 = (instr and 0xFFFL).toInt()
            return Triple(rn, rd, imm12)
        }
        return null
    }

    fun decodeAddReg(instr: Long): Triple<Int, Int, Int>? {
        if ((instr and 0x0FE00FF0L) == 0x00800000L) {
            val rn = ((instr ushr 16) and 0xFL).toInt()
            val rd = ((instr ushr 12) and 0xFL).toInt()
            val rm = (instr and 0xFL).toInt()
            return Triple(rn, rm, rd)
        }
        return null
    }

    fun findFunctionFromString(s: String): Int? {
        val xref = findStringXref(s) ?: return null
        return findFunctionStartFromOff(xref)
    }

    override fun isBxLr(instr: Long): Boolean = (instr and 0x0FFFFFFFL) == 0x012FFF1EL

    override fun resolveRegisterValue(at: Int, reg: Int, lookahead: Int): Long? {
        val registers = LongArray(16)
        var currentOff = at
        for (step in 0 until lookahead) {
            val instr = readU32(currentOff) ?: return null

            decodeMovw(instr)?.let { (rd, imm16) ->
                registers[rd] = imm16.toLong() and 0xFFFFL
            }
            decodeMovt(instr)?.let { (rd, imm16) ->
                registers[rd] = registers[rd] or ((imm16.toLong() and 0xFFFFL) shl 16)
            }
            decodeSubReg(instr)?.let { (rn, rm, rd) ->
                registers[rd] = (registers[rn] - registers[rm]) and 0xFFFFFFFFL
            }
            decodeAddImm(instr)?.let { (rn, rd, imm) ->
                registers[rd] = (registers[rn] + imm.toLong()) and 0xFFFFFFFFL
            }
            decodeAddReg(instr)?.let { (rn, rm, rd) ->
                registers[rd] = (registers[rn] + registers[rm]) and 0xFFFFFFFFL
            }
            decodeSubImm(instr)?.let { (rn, rd, imm) ->
                registers[rd] = (registers[rn] - imm.toLong()) and 0xFFFFFFFFL
            }
            decodeMov(instr)?.let { (rm, rd) ->
                registers[rd] = registers[rm]
            }
            decodeLdrPc(instr, currentOff.toLong() + 8L)?.let { (rd, addr) ->
                val poolOff = vaToOffset(addr)
                if (poolOff != null) {
                    val v = readU32(poolOff)
                    if (v != null) registers[rd] = v
                }
            }

            if ((instr and 0xFF000000L) == 0xEA000000L) {
                return registers[reg]
            }
            if (isBxLr(instr)) {
                return registers[reg]
            }
            currentOff += 4
        }
        return null
    }

    override fun resolveRegisterValueBack(at: Int, reg: Int, lookback: Int): Long? {
        val start = maxOf(0, at - lookback * 4)
        var curReg = reg
        var off = at - 4
        while (off >= start) {
            val instr = readU32(off)
            if (instr == null) {
                off -= 4
                continue
            }
            val pc = offsetToVa(off)
            if (pc == null) {
                off -= 4
                continue
            }

            decodeLdrPc(instr, pc)?.let { (rd, addr) ->
                if (rd == curReg) {
                    val poolOff = vaToOffset(addr)
                    if (poolOff != null) return readU32(poolOff)
                }
            }

            decodeSubReg(instr)?.let { (rn, rm, rd) ->
                if (rd == curReg) {
                    val rnVal = resolveRegisterValue(off, rn, lookback)
                    val rmVal = resolveRegisterValue(off, rm, lookback)
                    if (rnVal != null && rmVal != null) {
                        return (rnVal - rmVal) and 0xFFFFFFFFL
                    }
                }
            }

            decodeMov(instr)?.let { (rm, rd) ->
                if (rd == curReg) curReg = rm
            }

            decodeMovw(instr)?.let { (rd, _) ->
                if (rd == curReg) {
                    // Continue scanning backwards for preceding MOVT
                }
            }

            decodeMovt(instr)?.let { (rd, imm16) ->
                if (rd == curReg) {
                    val lowVal = resolveRegisterValue(off, rd, lookback)
                    return if (lowVal != null) {
                        ((imm16.toLong() and 0xFFFFL) shl 16) or (lowVal and 0xFFFFL)
                    } else {
                        (imm16.toLong() and 0xFFFFL) shl 16
                    }
                }
            }

            off -= 4
        }
        return null
    }
}

class Aarch64Tools(data: ByteArray, baseAddr: Long) : ArchTools(data, baseAddr) {

    fun forceReturn(target: ByteArray, offset: Int, value: Int, thumb: Boolean = false): ByteArray {
        _unused(thumb)
        val movX0 = 0xD2800000L or ((value and 0xFFFF).toLong() shl 5)
        val ret = 0xD65F03C0L
        if (offset >= 0 && offset + 8 <= target.size) {
            MtkDaPatcher.replaceSlice(target, offset, MtkDaPatcher.intLE(movX0))
            MtkDaPatcher.replaceSlice(target, offset + 4, MtkDaPatcher.intLE(ret))
        }
        return target
    }

    private fun _unused(@Suppress("UNUSED_PARAMETER") flag: Boolean) {}

    fun decodeMovt(instr: Long): Pair<Int, Int>? {
        if ((instr and 0x0FF00000L) == 0x03400000L) {
            val rd = ((instr ushr 12) and 0xFL).toInt()
            val imm4 = ((instr ushr 16) and 0xFL).toInt()
            val imm12 = (instr and 0xFFFL).toInt()
            return rd to ((imm4 shl 12) or imm12)
        }
        return null
    }

    fun decodeMovw(instr: Long): Pair<Int, Int>? {
        if ((instr and 0x0FF00000L) == 0x03000000L) {
            val rd = ((instr ushr 12) and 0xFL).toInt()
            val imm4 = ((instr ushr 16) and 0xFL).toInt()
            val imm12 = (instr and 0xFFFL).toInt()
            return rd to ((imm4 shl 12) or imm12)
        }
        return null
    }

    fun findFunctionFromString(s: String): Int? {
        val xref = findStringXref(s) ?: return null
        return findFunctionStartFromOff(xref)
    }

    fun isPointerAuth(instr: Long): Boolean =
        instr == 0xD503233FL || instr == 0xD503237FL || instr == 0xD50303BFL

    fun decodeAdrp(instr: Long, pc: Long): Pair<Long, Int>? {
        if ((instr and 0x9F000000L) != 0x90000000L) return null
        val rd = (instr and 0x1FL).toInt()
        val immlo = (instr ushr 29) and 0x3L
        val immhi = (instr ushr 5) and 0x7FFFFL
        var imm = (immhi shl 2) or immlo
        if ((imm and 0x100000L) != 0L) {
            imm -= 0x200000L
        }
        val page = pc and -0x1000L
        return (page + (imm shl 12)) to rd
    }

    fun decodeAddImm(instr: Long): Triple<Int, Int, Int>? {
        if ((instr and 0xFF000000L) != 0x91000000L) return null
        val rn = ((instr ushr 5) and 0x1FL).toInt()
        val rd = (instr and 0x1FL).toInt()
        val imm12 = ((instr ushr 10) and 0xFFFL).toInt()
        val shift = ((instr ushr 22) and 1L).toInt()
        val imm = if (shift != 0) imm12 shl 12 else imm12
        return Triple(rn, rd, imm)
    }

    override fun decodeBl(instr: Long, pc: Long): Long? {
        val opcode = instr and 0xFC000000L
        if (opcode != 0x94000000L && opcode != 0x14000000L) return null
        var off = instr and 0x03FFFFFFL
        if ((off and 0x02000000L) != 0L) {
            off -= 0x04000000L
        }
        return pc + (off * 4L)
    }

    fun decodeMovRegister(instr: Long): Pair<Int, Int>? {
        if ((instr and 0xFFE0FFE0L) != 0xAA0003E0L) return null
        return ((instr ushr 16) and 0x1FL).toInt() to (instr and 0x1FL).toInt()
    }

    fun findStringXref(s: String): Int? {
        val strOff = findString(s) ?: return null
        val va = baseAddr + strOff.toLong()
        val page = va and -0x1000L
        for (off in data.indices step 4) {
            val instr = readU32(off) ?: continue
            val pc = baseAddr + off.toLong()
            val adrp = decodeAdrp(instr, pc) ?: continue
            val (adrpPage, reg) = adrp
            if (adrpPage != page) continue
            val addOff = findMatchingAdd(off, reg, va)
            if (addOff != null) return addOff
        }
        return null
    }

    fun findMatchingAdd(baseOff: Int, reg: Int, va: Long): Int? {
        val end = minOf(baseOff + 64, data.size)
        for (off in (baseOff + 4) until end step 4) {
            val instr = readU32(off) ?: continue
            val add = decodeAddImm(instr) ?: continue
            val (rn, _, imm) = add
            if (rn == reg && ((va and -0x1000L) + imm.toLong()) == va) {
                return off
            }
        }
        return null
    }

    fun findFunctionStartFromOff(fromOff: Int): Int? {
        val mask = 0xFFC07FFFL
        val pattern = 0xA9807BFDL
        val maskFallback = 0xFFC003FFL
        val patternFallback = 0xA9007BFDL
        val limit = 0x5000
        val end = maxOf(0, fromOff - limit)
        var off = fromOff
        while (off > end) {
            val instr = readU32(off)
            if (instr == null) {
                off -= 4
                continue
            }
            if (off >= 4) {
                val prev = readU32(off - 4)
                if (prev == null) {
                    off -= 4
                    continue
                }
                if (prev == 0xD503233FL || prev == 0xD503237FL) {
                    return off - 4
                }
                if ((prev and maskFallback) == 0xD10003FFL &&
                    (instr and maskFallback) == (patternFallback and maskFallback)
                ) {
                    return off - 4
                }
            }
            if ((instr and mask) == pattern) {
                return off
            }
            off -= 4
        }
        return null
    }

    fun decodeSubReg(instr: Long): Triple<Int, Int, Int>? {
        if ((instr and 0xFF200000L) == 0xCB000000L) {
            val rm = ((instr ushr 16) and 0x1FL).toInt()
            val rn = ((instr ushr 5) and 0x1FL).toInt()
            val rd = (instr and 0x1FL).toInt()
            return Triple(rn, rm, rd)
        }
        return null
    }

    fun decodeSubImm(instr: Long): Triple<Int, Int, Int>? {
        if ((instr and 0xFF000000L) == 0xD1000000L) {
            val rn = ((instr ushr 5) and 0x1FL).toInt()
            val rd = (instr and 0x1FL).toInt()
            val imm12 = ((instr ushr 10) and 0xFFFL).toInt()
            val shift = ((instr ushr 22) and 1L).toInt()
            val imm = if (shift != 0) imm12 shl 12 else imm12
            return Triple(rn, rd, imm)
        }
        return null
    }

    override fun resolveRegisterValueBack(at: Int, reg: Int, lookback: Int): Long? {
        val start = maxOf(0, at - lookback * 4)
        val registers = arrayOfNulls<Long>(32)
        var currentOff = at
        while (currentOff > start) {
            val instr = readU32(currentOff)
            if (instr == null) {
                currentOff -= 4
                continue
            }
            val pc = offsetToVa(currentOff) ?: run {
                currentOff -= 4
                return@run null
            } ?: continue

            decodeAdrp(instr, pc)?.let { (page, rd) ->
                registers[rd] = (registers[rd] ?: 0L) + page
                currentOff -= 4
                continue
            }

            decodeAddImm(instr)?.let { (rn, rd, imm) ->
                val rnVal = registers[rn]
                if (rnVal != null) {
                    registers[rd] = rnVal + imm.toLong()
                } else if (registers[rd] == null) {
                    registers[rd] = imm.toLong()
                }
                currentOff -= 4
                continue
            }

            decodeSubReg(instr)?.let { (rn, rm, rd) ->
                val rnVal = registers[rn]
                val rmVal = registers[rm]
                if (rnVal != null && rmVal != null) {
                    registers[rd] = rnVal - rmVal
                }
                currentOff -= 4
                continue
            }

            currentOff -= 4
        }
        return registers[reg]
    }

    override fun resolveRegisterValue(at: Int, reg: Int, lookahead: Int): Long? {
        val registers = arrayOfNulls<Long>(32)
        var currentOff = at
        val maxInstructions = maxOf(100, lookahead)
        for (step in 0 until maxInstructions) {
            val instr = readU32(currentOff) ?: return null
            val pc = offsetToVa(currentOff) ?: return null

            decodeAdrp(instr, pc)?.let { (page, rd) ->
                registers[rd] = page
                currentOff += 4
                continue
            }

            decodeAddImm(instr)?.let { (rn, rd, imm) ->
                val rnVal = registers[rn]
                if (rnVal != null) {
                    registers[rd] = rnVal + imm.toLong()
                }
                currentOff += 4
                continue
            }

            decodeSubReg(instr)?.let { (rn, rm, rd) ->
                val rnVal = registers[rn]
                val rmVal = registers[rm]
                if (rnVal != null && rmVal != null) {
                    registers[rd] = rnVal - rmVal
                }
                currentOff += 4
                continue
            }

            if ((instr and 0xFC000000L) == 0x14000000L) {
                return registers[reg]
            }
            if ((instr and 0xFC000000L) == 0x94000000L) {
                return registers[reg]
            }
            // RET / RET X30 (0xD65F03C0 LE -> int 0xD65F03C0)
            if (instr == 0xD65F03C0L || instr == 0xC0035FD6L) {
                return registers[reg]
            }

            currentOff += 4
        }
        return null
    }

    fun resolveAdrpPart(fromOff: Int, limit: Int, reg: Int, imm: Int): Long? {
        var off = fromOff
        while (off > limit) {
            val instr = readU32(off) ?: break
            val pc = baseAddr + off.toLong()
            val adrp = decodeAdrp(instr, pc)
            if (adrp != null && adrp.second == reg) {
                return adrp.first + imm.toLong()
            }
            off -= 4
        }
        return null
    }
}

/**
 * Exact port of v6.py -> offset_to_op_mov(addr, register, base)
 */
fun offsetToOpMov(addr: Long, register: Int, base: Long): Pair<Long, Long> {
    var offset = addr + base
    val low = ((((offset and 0xFFFFL) ushr 12) and 0xFL) shl 16) or
        (register.toLong() shl 14) or
        (offset and 0xFFFL)
    offset = offset ushr 16
    val shift = 4L
    val high = ((((offset and 0xFFFFL) ushr 12) and 0xFL) shl 16) or
        (register.toLong() shl 14) or
        (offset and 0xFFFL) or
        (shift shl 20)
    val firstOp = ((0xE3L shl 24) + low) and 0xFFFFFFFFL
    val secondOp = ((0xE3L shl 24) + high) and 0xFFFFFFFFL
    return firstOp to secondOp
}

/**
 * Exact port of v6.py -> op_mov_to_offset(first_op, second_op, register)
 */
fun opMovToOffset(firstOp: Long, secondOp: Long, register: Int): Long? {
    val regLo = ((firstOp and 0xF000L) ushr 12).toInt()
    val regHi = ((secondOp and 0xF000L) ushr 12).toInt()
    val shiftLo = ((firstOp and 0xF00000L) ushr 20).toInt()
    val shiftHi = ((secondOp and 0xF00000L) ushr 20).toInt()
    val hi = (((secondOp and 0xF0000L) ushr 4) or (secondOp and 0xFFFL)) shl (shiftHi * 4)
    val lo = (((firstOp and 0xF0000L) ushr 4) or (firstOp and 0xFFFL)) shl (shiftLo * 4)
    return if (regLo == register && regHi == register) {
        (hi or lo) and 0xFFFFFFFFL
    } else {
        null
    }
}
