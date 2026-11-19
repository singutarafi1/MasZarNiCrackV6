package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ServiceFunction
import com.example.protocol.Aarch64Tools
import com.example.protocol.ArmTools
import com.example.protocol.DaMode
import com.example.protocol.MtkAssetManager
import com.example.protocol.MtkCalibrationEngine
import com.example.protocol.MtkCarbonaraExploit
import com.example.protocol.MtkChipConfigDatabase
import com.example.protocol.MtkDaParser
import com.example.protocol.MtkDaPatcher
import com.example.protocol.MtkEfuseDatabase
import com.example.protocol.MtkHeapbaitExploit
import com.example.protocol.MtkSlaKeyDatabase
import com.example.protocol.MtkSparseImageDecoder
import com.example.protocol.MtkXmlCmd
import com.example.protocol.MtkXmlDaPatcher
import com.example.protocol.offsetToOpMov
import com.example.protocol.opMovToOffset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.MGF1ParameterSpec
import java.security.spec.RSAPrivateKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MtkProtocolUpdateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `chip config database registers full 88 SoC catalog and handles fallback and watchdog`() {
        val allConfigs = MtkChipConfigDatabase.getAllConfigs()
        assertEquals(88, allConfigs.size)

        val mt6765 = MtkChipConfigDatabase.findConfig(0x0766)
        assertNotNull(mt6765)
        assertEquals(DaMode.XFLASH, mt6765?.damode)

        val mt6833 = MtkChipConfigDatabase.findConfig(0x0989)
        assertNotNull(mt6833)
        assertEquals(DaMode.XFLASH, mt6833?.damode)

        val fallback = MtkChipConfigDatabase.createDefaultFallback(0x9999)
        assertEquals(0x9999, fallback.hwCode)
        assertEquals("MT9999", fallback.name)
        assertEquals(0x200000L, fallback.daPayloadAddr)
        assertEquals(DaMode.LEGACY, fallback.damode)

        val wd6276 = MtkChipConfigDatabase.findConfig(0x6276)
        if (wd6276 != null && wd6276.watchdog == 0x2200L) {
            val (addr, value) = MtkChipConfigDatabase.getWatchdogAddrAndValue(wd6276)
            assertEquals(0x2200L, addr)
            assertEquals(0x610C0000L, value)
        }
    }

    @Test
    fun `sla key database has 8 DA and 36 BROM keys with valid OAEP SHA256 signing`() {
        assertEquals(8, MtkSlaKeyDatabase.DA_SLA_KEYS.size)
        assertEquals(36, MtkSlaKeyDatabase.BROM_SLA_KEYS.size)

        val sampleKey = MtkSlaKeyDatabase.DA_SLA_KEYS.first()
        val mod = MtkSlaKeyDatabase.modulusBytes(sampleKey)
        assertTrue(mod.isNotEmpty())
        assertTrue((mod[0].toInt() and 0xFF) != 0)

        val fakeDa2 = ByteArray(1024)
        System.arraycopy(mod, 0, fakeDa2, 128, mod.size)
        val matched = MtkSlaKeyDatabase.findMatchingKey(fakeDa2, preferDaKeys = true)
        assertEquals(sampleKey.name, matched?.name)

        val challenge = ByteArray(16) { (it + 1).toByte() }
        val sig = MtkSlaKeyDatabase.generateDaSlaSignature(challenge, sampleKey)
        assertEquals(mod.size, sig.size)

        val privSpec = RSAPrivateKeySpec(sampleKey.n, sampleKey.d)
        val privKey = KeyFactory.getInstance("RSA").generatePrivate(privSpec)
        val oaepSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.DECRYPT_MODE, privKey, oaepSpec)
        val decrypted = cipher.doFinal(sig)
        assertArrayEquals(challenge, decrypted)

        val bromKey = MtkSlaKeyDatabase.BROM_SLA_KEYS.first()
        val bromChallenge = ByteArray(16) { (0x10 + it).toByte() }
        val bromResp = MtkSlaKeyDatabase.generateBromSlaChallenge(bromChallenge, bromKey)
        assertEquals(MtkSlaKeyDatabase.modulusBytes(bromKey).size, bromResp.size)
    }

    @Test
    fun `da parser detects MTK_DA_v6 and oldLdr stride`() {
        val totalSize = 16384
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("MTK_DOWNLOAD_AGENT".toByteArray(Charsets.US_ASCII))
        buf.position(0x20)
        buf.put("MTK_DA_v6".toByteArray(Charsets.US_ASCII))

        // Modern 20-byte DA entry header starts at 0x70
        val entryOff = 0x70
        buf.position(entryOff)
        buf.putShort(0xDADA.toShort()) // magic
        buf.putShort(0x1209.toShort()) // hwCode
        buf.putShort(0x8A00.toShort()) // hwSubCode
        buf.putShort(0xCA00.toShort()) // hwVersion
        buf.putShort(0x0001.toShort()) // swVersion
        buf.putShort(0x0000.toShort()) // reserved1
        buf.putShort(512.toShort())    // pageSize
        buf.putShort(0x0001.toShort()) // reserved3
        buf.putShort(0)                // entryRegionIndex
        buf.putShort(2)                // entryRegionCount
        // Region 0 (Stage 1, >= 4096 bytes)
        buf.putInt(0x1000)             // mBuf
        buf.putInt(0x1000)             // mLen
        buf.putInt(0x200000)           // mStartAddr
        buf.putInt(0)                  // mStartOffset
        buf.putInt(0x100)              // mSigLen
        // Region 1 (Stage 2)
        buf.putInt(0x2000)             // mBuf
        buf.putInt(0x1000)             // mLen
        buf.putInt(0x40000000)         // mStartAddr
        buf.putInt(0)                  // mStartOffset
        buf.putInt(0x100)              // mSigLen

        val info = MtkDaParser.parseDaLoader(buf.array(), hwCode = 0x1209, daCode = 0x1209)
        assertNotNull(info)
        assertTrue(info!!.header.isV6)
        assertEquals(0x1209, info.header.hwCode)
        assertEquals(0x200000L, info.stage1?.startAddress)
        assertEquals(0x40000000L, info.stage2?.startAddress)
    }

    @Test
    fun `da patcher applies hash_check3 wildcard and recomputes embedded hash`() {
        val da1 = ByteArray(512)
        // Embed hash_check3 wildcard pattern: 14 2C F6 ?? FE E7
        val hc3 = byteArrayOf(0x14, 0x2C, 0xF6.toByte(), 0x55, 0xFE.toByte(), 0xE7.toByte())
        System.arraycopy(hc3, 0, da1, 32, hc3.size)

        val da2SigLen = 64
        val da2Code = ByteArray(256) { (it and 0xFF).toByte() }
        val da2 = da2Code + ByteArray(da2SigLen) { 0xAA.toByte() }
        val origSha256 = MessageDigest.getInstance("SHA-256").digest(da2Code)
        System.arraycopy(origSha256, 0, da1, 128, origSha256.size)

        val patchedDa1 = MtkDaPatcher.patchPreloaderSecurityDa1(da1)
        assertArrayEquals(ByteArray(6), patchedDa1.copyOfRange(32, 38))

        val hashPos = MtkDaPatcher.computeHashPos(da1, da2, da1SigLen = 0, da2SigLen = da2SigLen, isV6 = true)
        assertNotNull(hashPos)
        assertEquals(128, hashPos!!.hashAddr)
        assertEquals(2, hashPos.hashMode)
        assertEquals(256, hashPos.hashLen)

        val modifiedDa2 = da2.copyOf().apply { this[10] = (this[10].toInt() xor 0xFF).toByte() }
        val fixedDa1 = MtkDaPatcher.fixHash(patchedDa1, modifiedDa2, hashPos)
        val expectedNewHash = MessageDigest.getInstance("SHA-256").digest(modifiedDa2.copyOfRange(0, 256))
        assertArrayEquals(expectedNewHash, fixedDa1.copyOfRange(128, 160))
    }

    @Test
    fun `arm and aarch64 arch tools decode branches and mov offsets accurately`() {
        val (op1, op2) = offsetToOpMov(addr = 0x1234L, register = 0, base = 0x40000000L)
        val resolved = opMovToOffset(op1, op2, register = 0)
        assertEquals(0x40001234L, resolved)

        val armBuf = ByteArray(16)
        val arm = ArmTools(armBuf, 0x40000000L)
        val retArm = arm.forceReturn(ByteArray(8), 0, 1, thumb = false)
        assertEquals(0xE12FFF1EL, MtkDaPatcher.unpackU32LE(retArm, 4))

        val a64 = Aarch64Tools(ByteArray(16), 0x40000000L)
        val retA64 = a64.forceReturn(ByteArray(8), 0, 0, false)
        assertEquals(0xD65F03C0L, MtkDaPatcher.unpackU32LE(retA64, 4))
    }

    @Test
    fun `xml cmd builders and v6 xml da patcher and exploits match specification`() {
        val rt = MtkXmlCmd.cmdSetRuntimeParameter()
        assertTrue(rt.contains("<command>CMD:SET-RUNTIME-PARAMETER</command>"))
        assertTrue(rt.contains("<version>1.1</version>"))
        assertTrue(rt.contains("<initialize_dram>YES</initialize_dram>"))

        val readCmd = MtkXmlCmd.cmdReadFlash(partition = "UFS-LUA2", offset = 0x1000L, length = 0x2000L)
        assertTrue(readCmd.contains("<partition>UFS-LUA2</partition>"))
        assertTrue(readCmd.contains("<offset>0x1000</offset>"))
        assertTrue(readCmd.contains("<length>0x2000</length>"))

        val writeCmd = MtkXmlCmd.cmdWriteFlash(partition = "EMMC-USER", offset = 0L, memLength = 0x4000L)
        assertTrue(writeCmd.contains("<command>CMD:WRITE-FLASH</command>"))
        assertTrue(writeCmd.contains("<partition>EMMC-USER</partition>"))

        val eraseCmd = MtkXmlCmd.cmdEraseFlash(partition = "EMMC-USER", offset = 0x200L, length = 0x400L)
        assertTrue(eraseCmd.contains("<command>CMD:ERASE-FLASH</command>"))

        val aioFile = MtkXmlCmd.cmdSecuritySetAllinoneSignature(filename = "aio.bin")
        assertTrue(aioFile.contains("<source_file>aio.bin</source_file>"))

        val expCall = MtkHeapbaitExploit.cmdExpCallFunc(0x40001000L)
        assertTrue(expCall.contains("<command>CMD:EXP-CALL-FUNC</command>"))
        assertTrue(expCall.contains("<address>0x40001000</address>"))

        val expPatch = MtkHeapbaitExploit.cmdExpPatchMem(0x40002000L, 8)
        assertTrue(expPatch.contains("<command>CMD:EXP-PATCH-MEM</command>"))
        assertTrue(expPatch.contains("<length>0x8</length>"))

        val patchedDa1Marker = "2nd DA address is invalid".toByteArray(Charsets.US_ASCII)
        assertTrue(MtkCarbonaraExploit.isPatched(ByteArray(32) + patchedDa1Marker + ByteArray(32)))
        assertFalse(MtkCarbonaraExploit.isPatched(ByteArray(64)))

        val vulnDa2 = "Normal DA2 payload\u0000DA.SLA\u0000ENABLED\u0000".toByteArray(Charsets.US_ASCII)
        assertTrue(MtkHeapbaitExploit.isVulnerable(vulnDa2))
        val nonVulnDa2 = vulnDa2 + "Dest XML file name buffer overflow".toByteArray(Charsets.US_ASCII)
        assertFalse(MtkHeapbaitExploit.isVulnerable(nonVulnDa2))

        val patchedV6Da2 = MtkXmlDaPatcher.patchDa2(vulnDa2, 0x40000000L)
        assertTrue(patchedV6Da2.toString(Charsets.US_ASCII).contains("DA.SLA\u0000DISABLE"))
    }

    @Test
    fun `bundled v6 xml and exploit binary payloads are present in assets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(MtkAssetManager.loadPayloadBytes(context, "da_x.bin"))
        assertNotNull(MtkAssetManager.loadPayloadBytes(context, "da_xml.bin"))
        assertNotNull(MtkAssetManager.loadPayloadBytes(context, "da_xml_64.bin"))
        assertNotNull(MtkAssetManager.loadPayloadBytes(context, "heapbait_arm.bin"))
        assertNotNull(MtkAssetManager.loadPayloadBytes(context, "heapbait_arm64.bin"))
    }

    @Test
    fun `phase1 vbmeta patching slot parsing custom loader and service functions work accurately`() {
        val fakeVbmeta = ByteArray(256)
        System.arraycopy("AVB0".toByteArray(Charsets.US_ASCII), 0, fakeVbmeta, 0, 4)

        for (mode in 0..3) {
            val patched = MtkCalibrationEngine.patchVbmetaBytes(fakeVbmeta, mode)
            assertNotNull(patched)
            assertEquals(0, patched!![0x78].toInt())
            assertEquals(0, patched[0x79].toInt())
            assertEquals(0, patched[0x7A].toInt())
            assertEquals(mode, patched[0x7B].toInt() and 0xFF)
        }
        assertNull(MtkCalibrationEngine.patchVbmetaBytes(fakeVbmeta, -1))
        assertNull(MtkCalibrationEngine.patchVbmetaBytes(fakeVbmeta, 4))
        assertNull(MtkCalibrationEngine.patchVbmetaBytes(ByteArray(64), 3))

        val miscA = ByteArray(4096)
        miscA[0x800] = '_'.code.toByte()
        miscA[0x801] = 'a'.code.toByte()
        assertEquals("_a", MtkCalibrationEngine.parseSlotFromMisc(miscA))

        val miscB = ByteArray(4096)
        miscB[0x800] = '_'.code.toByte()
        miscB[0x801] = 'b'.code.toByte()
        assertEquals("_b", MtkCalibrationEngine.parseSlotFromMisc(miscB))

        assertEquals("", MtkCalibrationEngine.parseSlotFromMisc(ByteArray(4096)))
        assertEquals("", MtkCalibrationEngine.parseSlotFromMisc(ByteArray(512)))

        val customFile = tempFolder.newFile("custom_da_test.bin")
        val samplePayload = ByteArray(128) { (it and 0x7F).toByte() }
        customFile.writeBytes(samplePayload)
        assertArrayEquals(samplePayload, MtkAssetManager.loadCustomFileBytes(customFile.absolutePath, 64))
        assertNull(MtkAssetManager.loadCustomFileBytes(customFile.absolutePath, 256))
        assertNull(MtkAssetManager.loadCustomFileBytes("/non/existent/file.bin", 1))

        val names = ServiceFunction.values().map { it.name }.toSet()
        assertTrue(names.contains("PATCH_VBMETA"))
        assertTrue(names.contains("WRITE_RPMB"))
        assertTrue(names.contains("ERASE_RPMB"))
        assertTrue(names.contains("ENTER_META_MODE"))
        assertTrue(names.contains("FULL_FLASH_DUMP"))
        assertTrue(names.contains("READ_OFFSET"))
        assertTrue(names.contains("WRITE_OFFSET"))
        assertTrue(names.contains("ERASE_RANGE"))
        assertTrue(names.contains("DUMP_FOOTER"))
        assertTrue(names.contains("DA_PEEK"))
        assertTrue(names.contains("DA_POKE"))
        assertTrue(names.contains("DUMP_EFUSE"))
        assertTrue(names.contains("DUMP_BROM"))
        assertTrue(names.contains("RUN_PAYLOAD"))
    }

    @Test
    fun `sparse image decoder decodes raw fill dont_care crc32 chunks and skips non-sparse`() {
        assertFalse(MtkSparseImageDecoder.isSparse(byteArrayOf(0x00, 0x01, 0x02, 0x03)))

        // Build a synthetic sparse image: blockSize=4096, 4 chunks (RAW 1 blk, DONT_CARE 2 blks, FILL 1 blk, CRC32)
        val rawBlock = ByteArray(4096) { (it and 0xFF).toByte() }
        val totalSize = 28 + (12 + 4096) + 12 + (12 + 4) + (12 + 4)
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(MtkSparseImageDecoder.SPARSE_MAGIC.toInt())
        buf.putShort(1) // major
        buf.putShort(0) // minor
        buf.putShort(28) // fileHdrSz
        buf.putShort(12) // chunkHdrSz
        buf.putInt(4096) // blockSize
        buf.putInt(4) // totalBlocks (1 + 2 + 1)
        buf.putInt(4) // totalChunks
        buf.putInt(0) // imageChecksum

        // Chunk 0: RAW 1 block
        buf.putShort(MtkSparseImageDecoder.CHUNK_TYPE_RAW.toShort())
        buf.putShort(0)
        buf.putInt(1)
        buf.putInt(12 + 4096)
        buf.put(rawBlock)

        // Chunk 1: DONT_CARE 2 blocks (skipped in output spans)
        buf.putShort(MtkSparseImageDecoder.CHUNK_TYPE_DONT_CARE.toShort())
        buf.putShort(0)
        buf.putInt(2)
        buf.putInt(12)

        // Chunk 2: FILL 1 block with 0xAA551122
        buf.putShort(MtkSparseImageDecoder.CHUNK_TYPE_FILL.toShort())
        buf.putShort(0)
        buf.putInt(1)
        buf.putInt(12 + 4)
        buf.put(byteArrayOf(0x22, 0x11, 0x55, 0xAA.toByte()))

        // Chunk 3: CRC32
        buf.putShort(MtkSparseImageDecoder.CHUNK_TYPE_CRC32.toShort())
        buf.putShort(0)
        buf.putInt(0)
        buf.putInt(12 + 4)
        buf.putInt(0x12345678)

        val sparseBytes = buf.array()
        assertTrue(MtkSparseImageDecoder.isSparse(sparseBytes))
        val hdr = MtkSparseImageDecoder.parseHeader(sparseBytes)
        assertNotNull(hdr)
        assertEquals(16384L, hdr!!.outputSizeBytes)

        val memSpans = mutableListOf<Pair<Long, Int>>()
        assertTrue(MtkSparseImageDecoder.streamSpans(sparseBytes) { span ->
            memSpans.add(span.outputOffset to span.data.size)
            if (span.outputOffset == 0L) {
                assertArrayEquals(rawBlock, span.data)
            } else if (span.outputOffset == 3L * 4096L) {
                assertEquals(0x22.toByte(), span.data[0])
                assertEquals(0xAA.toByte(), span.data[3])
            }
            true
        })
        assertEquals(2, memSpans.size)
        assertEquals(0L to 4096, memSpans[0])
        assertEquals(12288L to 4096, memSpans[1])

        // Also verify streaming from File
        val sparseFile = tempFolder.newFile("test_sparse.img")
        sparseFile.writeBytes(sparseBytes)
        assertTrue(MtkSparseImageDecoder.isSparseFile(sparseFile))
        val fileSpans = mutableListOf<Long>()
        kotlinx.coroutines.runBlocking {
            assertTrue(MtkSparseImageDecoder.streamSpansFromFile(sparseFile) { span ->
                fileSpans.add(span.outputOffset)
                true
            })
        }
        assertEquals(listOf(0L, 12288L), fileSpans)
    }

    @Test
    fun `efuse database and dump payloads match mtkclient configuration`() {
        val ef6765 = MtkEfuseDatabase.getEfuses(0x11F10000L, 0x0766)
        assertTrue(ef6765.contains(0x11F10020L))
        assertTrue(ef6765.size > 30)

        val ef1209 = MtkEfuseDatabase.getEfuses(0x11C10000L, 0x1209)
        assertTrue(ef1209.contains(0x11C107A0L))

        val efFallback = MtkEfuseDatabase.getEfuses(0x10206000L, 0xFFFF)
        assertEquals(0x10206020L, efFallback.first())

        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(MtkAssetManager.loadPayloadBytes(context, "generic_dump_payload.bin"))
        assertNotNull(MtkAssetManager.loadPayloadBytes(context, "generic_sram_payload.bin"))
    }
}
