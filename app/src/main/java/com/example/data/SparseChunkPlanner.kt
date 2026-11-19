package com.example.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Zero-OOM Fastboot Sparse & Chunked Streaming Planner.
 *
 * Prevents OutOfMemoryError and bootloader buffer overflow crashes on multi-gigabyte
 * partitions (system, super, vendor, userdata) by:
 * 1. Splitting Android Sparse images (0xED26FF3A) strictly along chunk/block boundaries
 *    into valid sub-sparse packets (each <= bootloader max-download-size) with leading
 *    DONT_CARE (0xCAC3) seek chunks.
 * 2. Wrapping oversized Raw images (> max-download-size) into sparse sub-packets on-the-fly
 *    so the entire partition writes cleanly using only a 16 KB reusable streaming buffer.
 */
object SparseChunkPlanner {

    const val SPARSE_HEADER_MAGIC = 0xED26FF3A.toInt()
    const val SPARSE_HEADER_SIZE = 28
    const val CHUNK_HEADER_SIZE = 12

    const val CHUNK_TYPE_RAW: Short = 0xCAC1.toShort()
    const val CHUNK_TYPE_FILL: Short = 0xCAC2.toShort()
    const val CHUNK_TYPE_DONT_CARE: Short = 0xCAC3.toShort()
    const val CHUNK_TYPE_CRC32: Short = 0xCAC4.toShort()

    const val DEFAULT_SAFE_CHUNK_BYTES = 128L * 1024L * 1024L // 128 MB

    sealed class ChunkSlice {
        abstract val outputBlocks: Long
        abstract val packetBytesContribution: Long

        data class RawSlice(
            val fileOffset: Long,
            val readByteLength: Long,
            override val outputBlocks: Long,
            val blockAlignedByteLength: Long = readByteLength
        ) : ChunkSlice() {
            override val packetBytesContribution: Long
                get() = CHUNK_HEADER_SIZE + blockAlignedByteLength
        }

        data class FillSlice(
            val fillBytes4: ByteArray,
            override val outputBlocks: Long
        ) : ChunkSlice() {
            override val packetBytesContribution: Long
                get() = CHUNK_HEADER_SIZE + 4L
        }

        data class DontCareSlice(
            override val outputBlocks: Long
        ) : ChunkSlice() {
            override val packetBytesContribution: Long
                get() = CHUNK_HEADER_SIZE.toLong()
        }
    }

    data class SparseTransferPacket(
        val packetIndex: Int,
        val blockSize: Int,
        val leadingDontCareBlocks: Long,
        val slices: List<ChunkSlice>,
        val totalTransferBytes: Long,
        val totalOutputBlocks: Long
    ) {
        fun buildPacketPrefixHeader(): ByteArray {
            val totalChunks = (if (leadingDontCareBlocks > 0L) 1 else 0) + slices.size
            val prefixSize = SPARSE_HEADER_SIZE + if (leadingDontCareBlocks > 0L) CHUNK_HEADER_SIZE else 0
            val bb = ByteBuffer.allocate(prefixSize).order(ByteOrder.LITTLE_ENDIAN)

            // sparse_header_t (28 bytes)
            bb.putInt(SPARSE_HEADER_MAGIC)
            bb.putShort(1) // major_version
            bb.putShort(0) // minor_version
            bb.putShort(SPARSE_HEADER_SIZE.toShort())
            bb.putShort(CHUNK_HEADER_SIZE.toShort())
            bb.putInt(blockSize)
            bb.putInt(totalOutputBlocks.toInt())
            bb.putInt(totalChunks)
            bb.putInt(0) // image_checksum

            if (leadingDontCareBlocks > 0L) {
                // chunk_header_t: DONT_CARE seek offset
                bb.putShort(CHUNK_TYPE_DONT_CARE)
                bb.putShort(0)
                bb.putInt(leadingDontCareBlocks.toInt())
                bb.putInt(CHUNK_HEADER_SIZE)
            }
            return bb.array()
        }

        companion object {
            fun buildChunkHeader(type: Short, chunkBlocks: Long, totalBytes: Long): ByteArray {
                return ByteBuffer.allocate(CHUNK_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putShort(type)
                    putShort(0)
                    putInt(chunkBlocks.toInt())
                    putInt(totalBytes.toInt())
                }.array()
            }
        }
    }

    /**
     * Plans zero-RAM chunk splits for an Android Sparse image stored inside [channel]
     * starting at [baseOffset] with length [streamSize].
     */
    fun planSparseSplits(
        channel: FileChannel,
        baseOffset: Long,
        streamSize: Long,
        maxPacketBytes: Long
    ): List<SparseTransferPacket> {
        val headerBuf = ByteBuffer.allocate(SPARSE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        if (channel.read(headerBuf, baseOffset) < SPARSE_HEADER_SIZE) return emptyList()
        headerBuf.flip()

        val magic = headerBuf.int
        if (magic != SPARSE_HEADER_MAGIC) return emptyList()

        headerBuf.short // major
        headerBuf.short // minor
        val fileHdrSz = (headerBuf.short.toInt() and 0xFFFF).coerceAtLeast(SPARSE_HEADER_SIZE)
        val chunkHdrSz = (headerBuf.short.toInt() and 0xFFFF).coerceAtLeast(CHUNK_HEADER_SIZE)
        val blkSz = headerBuf.int
        headerBuf.int // total_blks
        val totalChunks = headerBuf.int.toLong() and 0xFFFFFFFFL
        if (blkSz <= 0 || totalChunks <= 0L) return emptyList()

        val minOverhead = SPARSE_HEADER_SIZE + CHUNK_HEADER_SIZE + CHUNK_HEADER_SIZE
        val safeLimit = maxPacketBytes.coerceAtLeast(minOverhead + blkSz.toLong())

        val packets = mutableListOf<SparseTransferPacket>()
        var currentSlices = mutableListOf<ChunkSlice>()
        var currentLeadingDontCare = 0L
        var currentPacketBytes = SPARSE_HEADER_SIZE.toLong()
        var currentPacketContentBlocks = 0L
        var globalOutputBlock = 0L

        fun flushPacket() {
            if (currentSlices.isEmpty()) return
            val hasUsefulData = currentSlices.any { it !is ChunkSlice.DontCareSlice }
            if (hasUsefulData) {
                packets.add(
                    SparseTransferPacket(
                        packetIndex = packets.size + 1,
                        blockSize = blkSz,
                        leadingDontCareBlocks = currentLeadingDontCare,
                        slices = currentSlices,
                        totalTransferBytes = currentPacketBytes,
                        totalOutputBlocks = currentLeadingDontCare + currentPacketContentBlocks
                    )
                )
            }
            currentSlices = mutableListOf()
            currentLeadingDontCare = globalOutputBlock
            currentPacketBytes = SPARSE_HEADER_SIZE.toLong() +
                if (currentLeadingDontCare > 0L) CHUNK_HEADER_SIZE.toLong() else 0L
            currentPacketContentBlocks = 0L
        }

        val chunkHdrBuf = ByteBuffer.allocate(chunkHdrSz).order(ByteOrder.LITTLE_ENDIAN)
        var cursor = baseOffset + fileHdrSz
        val endLimit = baseOffset + streamSize

        for (c in 0L until totalChunks) {
            if (cursor + chunkHdrSz > endLimit) break
            chunkHdrBuf.clear()
            if (channel.read(chunkHdrBuf, cursor) < chunkHdrSz) break
            chunkHdrBuf.flip()

            val chunkType = chunkHdrBuf.short
            chunkHdrBuf.short // reserved
            val chunkSzBlocks = chunkHdrBuf.int.toLong() and 0xFFFFFFFFL
            val chunkTotalBytes = chunkHdrBuf.int.toLong() and 0xFFFFFFFFL
            val payloadOffset = cursor + chunkHdrSz
            val payloadBytes = (chunkTotalBytes - chunkHdrSz).coerceAtLeast(0L)

            when (chunkType) {
                CHUNK_TYPE_RAW -> {
                    var remBlocks = chunkSzBlocks
                    var sliceOffset = payloadOffset
                    while (remBlocks > 0L) {
                        val availableForRawPayload = safeLimit - currentPacketBytes - CHUNK_HEADER_SIZE
                        if (availableForRawPayload < blkSz) {
                            flushPacket()
                            continue
                        }
                        val maxBlocksFit = (availableForRawPayload / blkSz).coerceAtLeast(1L)
                        val takeBlocks = minOf(remBlocks, maxBlocksFit)
                        val takeBytes = takeBlocks * blkSz.toLong()

                        val slice = ChunkSlice.RawSlice(
                            fileOffset = sliceOffset,
                            readByteLength = takeBytes,
                            outputBlocks = takeBlocks,
                            blockAlignedByteLength = takeBytes
                        )
                        currentSlices.add(slice)
                        currentPacketBytes += slice.packetBytesContribution
                        currentPacketContentBlocks += takeBlocks
                        globalOutputBlock += takeBlocks
                        sliceOffset += takeBytes
                        remBlocks -= takeBlocks

                        if (remBlocks > 0L) {
                            flushPacket()
                        }
                    }
                }

                CHUNK_TYPE_FILL -> {
                    val needed = CHUNK_HEADER_SIZE + 4L
                    if (currentPacketBytes + needed > safeLimit) {
                        flushPacket()
                    }
                    val fillBuf = ByteBuffer.allocate(4)
                    if (payloadBytes >= 4L) {
                        channel.read(fillBuf, payloadOffset)
                    }
                    val slice = ChunkSlice.FillSlice(
                        fillBytes4 = fillBuf.array(),
                        outputBlocks = chunkSzBlocks
                    )
                    currentSlices.add(slice)
                    currentPacketBytes += slice.packetBytesContribution
                    currentPacketContentBlocks += chunkSzBlocks
                    globalOutputBlock += chunkSzBlocks
                }

                CHUNK_TYPE_DONT_CARE -> {
                    if (currentSlices.isEmpty()) {
                        globalOutputBlock += chunkSzBlocks
                        currentLeadingDontCare = globalOutputBlock
                        currentPacketBytes = SPARSE_HEADER_SIZE.toLong() +
                            if (currentLeadingDontCare > 0L) CHUNK_HEADER_SIZE.toLong() else 0L
                    } else {
                        if (currentPacketBytes + CHUNK_HEADER_SIZE > safeLimit) {
                            flushPacket()
                            globalOutputBlock += chunkSzBlocks
                            currentLeadingDontCare = globalOutputBlock
                            currentPacketBytes = SPARSE_HEADER_SIZE.toLong() +
                                if (currentLeadingDontCare > 0L) CHUNK_HEADER_SIZE.toLong() else 0L
                        } else {
                            val slice = ChunkSlice.DontCareSlice(outputBlocks = chunkSzBlocks)
                            currentSlices.add(slice)
                            currentPacketBytes += slice.packetBytesContribution
                            currentPacketContentBlocks += chunkSzBlocks
                            globalOutputBlock += chunkSzBlocks
                        }
                    }
                }

                CHUNK_TYPE_CRC32 -> {
                    // CRC chunks cover 0 output blocks and become invalid across split packets; skip.
                }

                else -> {
                    globalOutputBlock += chunkSzBlocks
                }
            }

            cursor += chunkTotalBytes.coerceAtLeast(chunkHdrSz.toLong())
        }

        flushPacket()
        return packets
    }

    /**
     * Wraps an oversized non-sparse Raw partition image (e.g., raw super.img or system.img)
     * into block-aligned Sparse sub-packets <= [maxPacketBytes] so Fastboot can flash
     * arbitrarily large raw partitions without truncating or exhausting device RAM.
     */
    fun planRawToSparseSplits(
        baseOffset: Long,
        streamSize: Long,
        maxPacketBytes: Long,
        blockSize: Int = 4096
    ): List<SparseTransferPacket> {
        if (streamSize <= 0L) return emptyList()
        val overhead = (SPARSE_HEADER_SIZE + CHUNK_HEADER_SIZE + CHUNK_HEADER_SIZE).toLong()
        val safePayloadLimit = (maxPacketBytes - overhead).coerceAtLeast(blockSize.toLong())
        val maxBlocksPerPacket = (safePayloadLimit / blockSize).coerceAtLeast(1L)
        val maxBytesPerPacket = maxBlocksPerPacket * blockSize

        val packets = mutableListOf<SparseTransferPacket>()
        var offset = 0L
        var outputBlock = 0L

        while (offset < streamSize) {
            val remaining = streamSize - offset
            val readLen = minOf(remaining, maxBytesPerPacket)
            val blocks = (readLen + blockSize - 1L) / blockSize
            val alignedLen = blocks * blockSize

            val leadingDontCare = outputBlock
            val prefixBytes = SPARSE_HEADER_SIZE.toLong() +
                if (leadingDontCare > 0L) CHUNK_HEADER_SIZE.toLong() else 0L

            val slice = ChunkSlice.RawSlice(
                fileOffset = baseOffset + offset,
                readByteLength = readLen,
                outputBlocks = blocks,
                blockAlignedByteLength = alignedLen
            )
            packets.add(
                SparseTransferPacket(
                    packetIndex = packets.size + 1,
                    blockSize = blockSize,
                    leadingDontCareBlocks = leadingDontCare,
                    slices = listOf(slice),
                    totalTransferBytes = prefixBytes + slice.packetBytesContribution,
                    totalOutputBlocks = leadingDontCare + blocks
                )
            )
            offset += readLen
            outputBlock += blocks
        }
        return packets
    }
}
