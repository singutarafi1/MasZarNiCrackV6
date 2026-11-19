package com.example.protocol

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Universal MediaTek Download Agent (DA) Binary Parser
 * Supports: All-In-One DA Containers, DA V5 (0xDA 0xDA), DA V6, and Raw Stage Binaries.
 * Strictly Ported from Python mtkclient: mtkclient/Library/DA/daconfig.py
 */
object MtkDaParser {

    private const val TAG = "MtkDaParser"

    data class EntryRegion(
        val mBuf: Long,
        val mLen: Long,
        val mStartAddr: Long,
        val mStartOffset: Long,
        val mSigLen: Long
    )

    data class DA(
        val magic: Int,
        val hwCode: Int,
        val hwSubCode: Int,
        val hwVersion: Int,
        val swVersion: Int,
        val reserved1: Int,
        val pageSize: Int,
        val reserved3: Int,
        val entryRegionIndex: Int,
        val entryRegionCount: Int,
        val regions: List<EntryRegion>,
        val isV6: Boolean = false
    )

    data class DaRegion(
        val index: Int,
        val name: String,
        val bufOffset: Long,
        val length: Long,
        val startAddress: Long,
        val sigLength: Long
    )

    data class DaHeader(
        val magic: String,
        val hwCode: Int,
        val hwSubCode: Int,
        val hwVersion: Int,
        val swVersion: Int,
        val pageSize: Int,
        val entryRegionIndex: Int,
        val entryRegionCount: Int,
        val regions: List<DaRegion>,
        val isV6: Boolean = false
    )

    data class DaLoaderInfo(
        val header: DaHeader,
        val stage1: DaRegion?,
        val stage2: DaRegion?,
        val rawData: ByteArray,
        val da: DA? = null
    ) {
        fun getStage1Bytes(): ByteArray? {
            val region = stage1 ?: return null
            val start = region.bufOffset.toInt()
            val end = (region.bufOffset + region.length).toInt()
            if (start < 0 || end > rawData.size || start >= end) return null
            return rawData.copyOfRange(start, end)
        }

        fun getStage2Bytes(): ByteArray? {
            val region = stage2 ?: return null
            val start = region.bufOffset.toInt()
            val end = (region.bufOffset + region.length).toInt()
            if (start < 0 || end > rawData.size || start >= end) return null
            return rawData.copyOfRange(start, end)
        }
    }

    /**
     * Primary DA Parser Entry Point.
     * Automatically handles Header-based Containers and Raw Split DA architectures.
     */
    fun parseDaLoader(
        daData: ByteArray,
        hwCode: Int? = null,
        defaultLoadAddr: Long = 0x201000L,
        daCode: Int? = null
    ): DaLoaderInfo? {
        if (daData.size < 4096) {
            Log.e(TAG, "DA Binary too small (${daData.size} bytes). Minimum 4KB required.")
            return null
        }

        // 1. Try Parsing as Structured DA Container (MTK_DA_V5 / MTK_DA_V6 / AllInOne)
        val daList = parseStructuredDa(daData)
        if (daList.isNotEmpty()) {
            val selectedDa = findDa(daList, hwCode, daCode)
            if (selectedDa == null && (hwCode != null || daCode != null)) {
                Log.d(TAG, "DA Container does not contain requested hwCode=0x%04X or daCode=0x%04X".format(hwCode ?: 0, daCode ?: 0))
                return null
            }
            val activeDa = selectedDa ?: daList.first()
            val mappedRegions = activeDa.regions.mapIndexed { idx, reg ->
                val startAddr = if (reg.mStartAddr != 0L) reg.mStartAddr else (if (idx == 0) defaultLoadAddr else 0x40000000L)
                DaRegion(
                    index = idx,
                    name = if (idx == 0) "DA_STAGE1 (DA_PL)" else "DA_STAGE2 (XFLASH)",
                    bufOffset = reg.mBuf,
                    length = reg.mLen,
                    startAddress = startAddr,
                    sigLength = reg.mSigLen
                )
            }

            // In MTK_DA_V5 / V6 containers with 3+ regions:
            // region[0] is either a 624-byte DA_INIT header (0x50000000) or a duplicate of region[1],
            // while region[1] is Stage 1 (0x200000) and region[2] is Stage 2 (0x40000000).
            val hasPreInitOrDupRegion0 = mappedRegions.size >= 3 && (
                mappedRegions[0].length < 4096 ||
                (mappedRegions[0].bufOffset == mappedRegions[1].bufOffset && mappedRegions[0].length == mappedRegions[1].length)
            )
            val stage1Region = if (hasPreInitOrDupRegion0) {
                mappedRegions[1].copy(name = "DA_STAGE1 (DA_PL)")
            } else {
                mappedRegions.getOrNull(0)
            }
            val stage2Region = if (hasPreInitOrDupRegion0) {
                mappedRegions[2].copy(name = "DA_STAGE2 (XFLASH)")
            } else {
                mappedRegions.getOrNull(1)
            }

            if (stage1Region != null && stage1Region.length >= 4096) {
                Log.d(
                    TAG,
                    "Parsed Structured DA (0x%04X, v6=%s): Stage1 Size=%d (0x%08X), Stage2 Size=%d (0x%08X)".format(
                        activeDa.hwCode,
                        activeDa.isV6,
                        stage1Region.length,
                        stage1Region.startAddress,
                        stage2Region?.length ?: 0L,
                        stage2Region?.startAddress ?: 0L
                    )
                )
                return DaLoaderInfo(
                    header = DaHeader(
                        magic = "0x%04X".format(activeDa.magic),
                        hwCode = activeDa.hwCode,
                        hwSubCode = activeDa.hwSubCode,
                        hwVersion = activeDa.hwVersion,
                        swVersion = activeDa.swVersion,
                        pageSize = activeDa.pageSize,
                        entryRegionIndex = activeDa.entryRegionIndex,
                        entryRegionCount = activeDa.entryRegionCount,
                        regions = mappedRegions,
                        isV6 = activeDa.isV6
                    ),
                    stage1 = stage1Region,
                    stage2 = stage2Region,
                    rawData = daData,
                    da = activeDa
                )
            }
        }

        // 2. Fallback: Parse as Raw Stage 1/2 Binary (when MTK_DA_V5.bin is a direct concatenated loader)
        Log.d(TAG, "Parsing as Raw Binary Split Loader...")
        return parseRawSplitDa(daData, daCode ?: hwCode, defaultLoadAddr)
    }

    private fun parseStructuredDa(data: ByteArray): List<DA> {
        val daList = mutableListOf<DA>()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Check MTK_DA_v6 ASCII signature in 0x68-byte container header (daconfig.py)
        val hdrLimit = minOf(0x68, data.size)
        val hdrStr = String(data, 0, hdrLimit, Charsets.US_ASCII)
        val isV6 = hdrStr.contains("MTK_DA_v6")

        // Scan for 0xDA 0xDA Magic markers within first 64KB
        val scanLimit = minOf(65536, data.size - 2)
        val possibleOffsets = mutableListOf<Int>()
        for (i in 0 until scanLimit) {
            if (data[i] == 0xDA.toByte() && data[i + 1] == 0xDA.toByte()) {
                possibleOffsets.add(i)
            }
        }

        // Determine legacy 216-byte (0xD8) vs modern 220-byte (0xDC) stride from first table entry
        val firstOffset = possibleOffsets.firstOrNull()
        val oldLdr = firstOffset != null &&
            firstOffset + 0xD9 < data.size &&
            data[firstOffset + 0xD8] == 0xDA.toByte() &&
            data[firstOffset + 0xD9] == 0xDA.toByte()
        val entryStride = if (oldLdr) 0xD8 else 0xDC

        var nextAllowedOffset = 0
        for (offset in possibleOffsets) {
            if (offset < nextAllowedOffset) continue
            try {
                buf.position(offset)
                val minHdrBytes = if (oldLdr) 16 else 20
                while (buf.remaining() >= minHdrBytes) {
                    val entryStart = buf.position()
                    val magic = buf.short.toInt() and 0xFFFF
                    if (magic != 0xDADA) break

                    val hwCode = buf.short.toInt() and 0xFFFF
                    val hwSubCode = buf.short.toInt() and 0xFFFF
                    val hwVersion = buf.short.toInt() and 0xFFFF
                    val swVersion = if (!oldLdr) buf.short.toInt() and 0xFFFF else 0
                    val reserved1 = if (!oldLdr) buf.short.toInt() and 0xFFFF else 0
                    val pageSize = buf.short.toInt() and 0xFFFF
                    val reserved3 = buf.short.toInt() and 0xFFFF
                    val entryRegionIndex = buf.short.toInt() and 0xFFFF
                    val entryRegionCount = buf.short.toInt() and 0xFFFF

                    if (entryRegionCount !in 1..10) break

                    val regions = mutableListOf<EntryRegion>()
                    for (r in 0 until entryRegionCount) {
                        if (buf.remaining() < 20) break
                        val mBuf = buf.int.toLong() and 0xFFFFFFFFL
                        val mLen = buf.int.toLong() and 0xFFFFFFFFL
                        val mStartAddr = buf.int.toLong() and 0xFFFFFFFFL
                        val mStartOffset = buf.int.toLong() and 0xFFFFFFFFL
                        val mSigLen = buf.int.toLong() and 0xFFFFFFFFL

                        if (mBuf + mLen <= data.size && mLen > 0) {
                            regions.add(EntryRegion(mBuf, mLen, mStartAddr, mStartOffset, mSigLen))
                        }
                    }

                    if (regions.isNotEmpty()) {
                        daList.add(
                            DA(
                                magic = magic,
                                hwCode = hwCode,
                                hwSubCode = hwSubCode,
                                hwVersion = hwVersion,
                                swVersion = swVersion,
                                reserved1 = reserved1,
                                pageSize = pageSize,
                                reserved3 = reserved3,
                                entryRegionIndex = entryRegionIndex,
                                entryRegionCount = entryRegionCount,
                                regions = regions,
                                isV6 = isV6
                            )
                        )
                    }

                    // Each DA table entry occupies 220 bytes (or 216 bytes for legacy old_ldr)
                    val nextEntryPos = entryStart + entryStride
                    if (nextEntryPos > data.size) break
                    nextAllowedOffset = maxOf(nextAllowedOffset, nextEntryPos)
                    buf.position(nextEntryPos)
                }
            } catch (_: Exception) {}
        }

        return daList
    }

    private fun parseRawSplitDa(data: ByteArray, hwCode: Int?, defaultLoadAddr: Long): DaLoaderInfo? {
        val totalLen = data.size.toLong()
        
        // Typical Stage 1 size in MTK DA V5 is 0x20000 (128KB) or entire file if single-stage
        val stage1Len = if (totalLen > 0x20000) 0x20000L else totalLen
        val stage2Len = if (totalLen > 0x20000) totalLen - 0x20000L else 0L

        val regions = mutableListOf<DaRegion>()
        regions.add(
            DaRegion(
                index = 0,
                name = "DA_STAGE1 (DA_PL)",
                bufOffset = 0L,
                length = stage1Len,
                startAddress = defaultLoadAddr,
                sigLength = 0L
            )
        )

        if (stage2Len > 0) {
            regions.add(
                DaRegion(
                    index = 1,
                    name = "DA_STAGE2 (XFLASH)",
                    bufOffset = stage1Len,
                    length = stage2Len,
                    startAddress = 0x40000000L,
                    sigLength = 0L
                )
            )
        }

        return DaLoaderInfo(
            header = DaHeader(
                magic = "0xDADA",
                hwCode = hwCode ?: 0x0766,
                hwSubCode = 0,
                hwVersion = 0,
                swVersion = 0,
                pageSize = 2048,
                entryRegionIndex = 0,
                entryRegionCount = regions.size,
                regions = regions
            ),
            stage1 = regions[0],
            stage2 = regions.getOrNull(1),
            rawData = data
        )
    }

    private fun findDa(daList: List<DA>, hwCode: Int?, daCode: Int? = null): DA? {
        if (daList.isEmpty()) return null
        val targetCodes = listOfNotNull(daCode, hwCode)
            .flatMap { listOf(it, it and 0xFFFF) }
            .distinct()
        if (targetCodes.isEmpty()) return daList.firstOrNull()

        for (target in targetCodes) {
            val exact = daList.filter { it.hwCode == target }
            if (exact.isNotEmpty()) {
                return exact.maxByOrNull { it.swVersion } ?: exact.first()
            }
        }
        return null
    }
}
