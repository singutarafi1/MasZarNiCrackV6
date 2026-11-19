package com.example.protocol

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android Sparse Image (0xED26FF3A) Streaming Decoder
 * Matches mtkclient flashing semantics:
 * - Magic: 0xED26FF3A (little-endian)
 * - Chunk types: RAW (0xCAC1), FILL (0xCAC2), DONT_CARE (0xCAC3), CRC32 (0xCAC4)
 * Produces contiguous write spans so large DONT_CARE gaps are skipped cleanly.
 */
object MtkSparseImageDecoder {

    const val SPARSE_MAGIC = 0xED26FF3AL
    const val CHUNK_TYPE_RAW = 0xCAC1
    const val CHUNK_TYPE_FILL = 0xCAC2
    const val CHUNK_TYPE_DONT_CARE = 0xCAC3
    const val CHUNK_TYPE_CRC32 = 0xCAC4

    data class SparseHeader(
        val majorVersion: Int,
        val minorVersion: Int,
        val fileHdrSz: Int,
        val chunkHdrSz: Int,
        val blockSize: Int,
        val totalBlocks: Long,
        val totalChunks: Int,
        val imageChecksum: Long
    ) {
        val outputSizeBytes: Long get() = totalBlocks * blockSize.toLong()
    }

    data class SparseWriteSpan(
        val outputOffset: Long,
        val data: ByteArray
    )

    fun isSparse(headerBytes: ByteArray): Boolean {
        if (headerBytes.size < 4) return false
        val magic = (headerBytes[0].toLong() and 0xFFL) or
            ((headerBytes[1].toLong() and 0xFFL) shl 8) or
            ((headerBytes[2].toLong() and 0xFFL) shl 16) or
            ((headerBytes[3].toLong() and 0xFFL) shl 24)
        return magic == SPARSE_MAGIC
    }

    fun isSparseFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 28L) return false
        return try {
            val head = ByteArray(4)
            file.inputStream().use { if (it.read(head) < 4) return false }
            isSparse(head)
        } catch (_: Exception) {
            false
        }
    }

    fun parseHeader(bytes: ByteArray): SparseHeader? {
        if (bytes.size < 28 || !isSparse(bytes)) return null
        val buf = ByteBuffer.wrap(bytes, 0, 28).order(ByteOrder.LITTLE_ENDIAN)
        buf.int // magic
        val major = buf.short.toInt() and 0xFFFF
        val minor = buf.short.toInt() and 0xFFFF
        val fileHdrSz = buf.short.toInt() and 0xFFFF
        val chunkHdrSz = buf.short.toInt() and 0xFFFF
        val blkSz = buf.int
        val totalBlks = buf.int.toLong() and 0xFFFFFFFFL
        val totalChunks = buf.int
        val checksum = buf.int.toLong() and 0xFFFFFFFFL
        if (fileHdrSz < 28 || chunkHdrSz < 12 || blkSz <= 0 || totalChunks < 0) return null
        return SparseHeader(major, minor, fileHdrSz, chunkHdrSz, blkSz, totalBlks, totalChunks, checksum)
    }

    /**
     * Streams contiguous decoded write spans from an in-memory sparse ByteArray.
     */
    fun streamSpans(
        sparseBytes: ByteArray,
        maxChunkSpanBytes: Int = 8 * 1024 * 1024,
        onHeader: ((SparseHeader) -> Unit)? = null,
        onSpan: suspend (SparseWriteSpan) -> Boolean
    ): Boolean {
        return kotlinx.coroutines.runBlocking {
            streamSpansSuspend(sparseBytes, maxChunkSpanBytes, onHeader, onSpan)
        }
    }

    suspend fun streamSpansSuspend(
        sparseBytes: ByteArray,
        maxChunkSpanBytes: Int = 8 * 1024 * 1024,
        onHeader: ((SparseHeader) -> Unit)? = null,
        onSpan: suspend (SparseWriteSpan) -> Boolean
    ): Boolean {
        val hdr = parseHeader(sparseBytes) ?: return false
        onHeader?.invoke(hdr)

        var filePos = hdr.fileHdrSz
        var outOffset = 0L

        for (chunkIdx in 0 until hdr.totalChunks) {
            if (filePos + hdr.chunkHdrSz > sparseBytes.size) return false
            val cBuf = ByteBuffer.wrap(sparseBytes, filePos, 12).order(ByteOrder.LITTLE_ENDIAN)
            val chunkType = cBuf.short.toInt() and 0xFFFF
            cBuf.short // reserved
            val chunkBlocks = cBuf.int.toLong() and 0xFFFFFFFFL
            val totalChunkBytes = cBuf.int.toLong() and 0xFFFFFFFFL
            filePos += hdr.chunkHdrSz

            val dataPayloadBytes = (totalChunkBytes - hdr.chunkHdrSz).coerceAtLeast(0L)
            val outBytes = chunkBlocks * hdr.blockSize.toLong()

            when (chunkType) {
                CHUNK_TYPE_RAW -> {
                    if (filePos + outBytes > sparseBytes.size) return false
                    var written = 0L
                    while (written < outBytes) {
                        val sliceLen = minOf(maxChunkSpanBytes.toLong(), outBytes - written).toInt()
                        val start = (filePos + written).toInt()
                        val slice = sparseBytes.copyOfRange(start, start + sliceLen)
                        if (!onSpan(SparseWriteSpan(outOffset + written, slice))) return false
                        written += sliceLen
                    }
                    filePos += dataPayloadBytes.toInt()
                    outOffset += outBytes
                }
                CHUNK_TYPE_FILL -> {
                    if (filePos + 4 > sparseBytes.size) return false
                    val pattern = sparseBytes.copyOfRange(filePos, filePos + 4)
                    filePos += dataPayloadBytes.toInt()
                    var written = 0L
                    while (written < outBytes) {
                        val sliceLen = minOf(maxChunkSpanBytes.toLong(), outBytes - written).toInt()
                        val fillBuf = ByteArray(sliceLen)
                        var i = 0
                        while (i + 4 <= sliceLen) {
                            fillBuf[i] = pattern[0]
                            fillBuf[i + 1] = pattern[1]
                            fillBuf[i + 2] = pattern[2]
                            fillBuf[i + 3] = pattern[3]
                            i += 4
                        }
                        if (!onSpan(SparseWriteSpan(outOffset + written, fillBuf))) return false
                        written += sliceLen
                    }
                    outOffset += outBytes
                }
                CHUNK_TYPE_DONT_CARE -> {
                    filePos += dataPayloadBytes.toInt()
                    outOffset += outBytes
                }
                CHUNK_TYPE_CRC32 -> {
                    filePos += dataPayloadBytes.toInt()
                }
                else -> return false
            }
        }
        return true
    }

    /**
     * Streams contiguous decoded write spans directly from a File without loading the full image into RAM.
     */
    suspend fun streamSpansFromFile(
        file: File,
        maxChunkSpanBytes: Int = 4 * 1024 * 1024,
        onHeader: ((SparseHeader) -> Unit)? = null,
        onSpan: suspend (SparseWriteSpan) -> Boolean
    ): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 28L) return false
        RandomAccessFile(file, "r").use { raf ->
            val hdrBytes = ByteArray(28)
            raf.readFully(hdrBytes)
            val hdr = parseHeader(hdrBytes) ?: return false
            onHeader?.invoke(hdr)

            var filePos = hdr.fileHdrSz.toLong()
            var outOffset = 0L
            val chunkHdr = ByteArray(12)

            for (chunkIdx in 0 until hdr.totalChunks) {
                if (filePos + hdr.chunkHdrSz > raf.length()) return false
                raf.seek(filePos)
                raf.readFully(chunkHdr)
                val cBuf = ByteBuffer.wrap(chunkHdr).order(ByteOrder.LITTLE_ENDIAN)
                val chunkType = cBuf.short.toInt() and 0xFFFF
                cBuf.short
                val chunkBlocks = cBuf.int.toLong() and 0xFFFFFFFFL
                val totalChunkBytes = cBuf.int.toLong() and 0xFFFFFFFFL
                filePos += hdr.chunkHdrSz

                val dataPayloadBytes = (totalChunkBytes - hdr.chunkHdrSz).coerceAtLeast(0L)
                val outBytes = chunkBlocks * hdr.blockSize.toLong()

                when (chunkType) {
                    CHUNK_TYPE_RAW -> {
                        if (filePos + outBytes > raf.length()) return false
                        raf.seek(filePos)
                        var written = 0L
                        while (written < outBytes) {
                            val sliceLen = minOf(maxChunkSpanBytes.toLong(), outBytes - written).toInt()
                            val slice = ByteArray(sliceLen)
                            raf.readFully(slice)
                            if (!onSpan(SparseWriteSpan(outOffset + written, slice))) return false
                            written += sliceLen
                        }
                        filePos += dataPayloadBytes
                        outOffset += outBytes
                    }
                    CHUNK_TYPE_FILL -> {
                        if (filePos + 4 > raf.length()) return false
                        raf.seek(filePos)
                        val pattern = ByteArray(4)
                        raf.readFully(pattern)
                        filePos += dataPayloadBytes
                        var written = 0L
                        while (written < outBytes) {
                            val sliceLen = minOf(maxChunkSpanBytes.toLong(), outBytes - written).toInt()
                            val fillBuf = ByteArray(sliceLen)
                            var i = 0
                            while (i + 4 <= sliceLen) {
                                fillBuf[i] = pattern[0]
                                fillBuf[i + 1] = pattern[1]
                                fillBuf[i + 2] = pattern[2]
                                fillBuf[i + 3] = pattern[3]
                                i += 4
                            }
                            if (!onSpan(SparseWriteSpan(outOffset + written, fillBuf))) return false
                            written += sliceLen
                        }
                        outOffset += outBytes
                    }
                    CHUNK_TYPE_DONT_CARE -> {
                        filePos += dataPayloadBytes
                        outOffset += outBytes
                    }
                    CHUNK_TYPE_CRC32 -> {
                        filePos += dataPayloadBytes
                    }
                    else -> return false
                }
            }
            return true
        }
    }

    /**
     * Computes CRC32 checksum of a File using zero-OOM 128KB streaming buffer.
     * Prevents Android heap OutOfMemoryError for 4GB-10GB super.img files.
     */
    fun computeFileCrc32(
        file: File,
        bufferSize: Int = 128 * 1024,
        onProgress: ((Float) -> Unit)? = null
    ): Long {
        if (!file.exists() || !file.isFile) return 0L
        val crc = java.util.zip.CRC32()
        val totalBytes = file.length()
        var processed = 0L
        val buf = ByteArray(bufferSize)

        file.inputStream().buffered(bufferSize).use { input ->
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                crc.update(buf, 0, n)
                processed += n
                if (totalBytes > 0 && onProgress != null) {
                    onProgress(processed.toFloat() / totalBytes.toFloat())
                }
            }
        }
        return crc.value
    }

    /**
     * Computes CRC32 checksum of an in-memory ByteArray.
     */
    fun computeByteArrayCrc32(data: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(data)
        return crc.value
    }
}
