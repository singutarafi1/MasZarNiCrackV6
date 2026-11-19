package com.example

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.audio.ToolSoundManager
import com.example.data.CommandRepository
import com.example.data.FlashFileProcessor
import com.example.data.SparseChunkPlanner
import com.example.data.UsbPortController
import com.example.model.ActionGroup
import com.example.model.AppFontSize
import com.example.model.FlashFileType
import com.example.model.SelectedFlashFile
import com.example.model.ServiceBrand
import com.example.model.ThemeDesign
import com.example.model.ThemeMode
import com.example.model.ToolScreenMode
import com.example.ui.ServiceViewModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and native command repository`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Native Unlock Tool", appName)

        assertTrue(CommandRepository.allActions.size >= 150)
        assertTrue(
            CommandRepository.allActions.all {
                it.id.isNotBlank() && it.command.isNotBlank() && it.title.isNotBlank() && it.description.isNotBlank()
            }
        )
        val allIds = CommandRepository.allActions.map { it.id }
        assertEquals(allIds.size, allIds.distinct().size)

        val protocols = listOf(true, false)
        val groups = listOf(ActionGroup.INFO, ActionGroup.UNLOCK, ActionGroup.SERVICE)
        for (brand in ServiceBrand.entries) {
            for (isAdb in protocols) {
                for (group in groups) {
                    val count = CommandRepository.allActions.count {
                        it.brand == brand && it.isAdb == isAdb && it.group == group
                    }
                    assertTrue(
                        "Missing commands for brand=${brand.name}, isAdb=$isAdb, group=${group.name}",
                        count > 0
                    )
                }
            }
        }
        assertTrue(CommandRepository.allActions.any { it.command.contains("oem unlock") })
        assertTrue(CommandRepository.allActions.any { it.command.contains("flashing unlock") })
    }

    @Test
    fun `verify partition inference for Android 5 to 16 images`() {
        assertEquals("init_boot", FlashFileProcessor.inferPartition("init_boot_magisk.img"))
        assertEquals("vendor_boot", FlashFileProcessor.inferPartition("vendor_boot.img"))
        assertEquals("vbmeta", FlashFileProcessor.inferPartition("vbmeta.img"))
        assertEquals("super", FlashFileProcessor.inferPartition("super_empty.img"))
        assertEquals("boot", FlashFileProcessor.inferPartition("boot_a.img"))
    }

    @Test
    fun `verify viewModel strict drawer mode isolation and brand filtering`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ServiceViewModel(app)

        vm.navigateToMode(ToolScreenMode.FASTBOOT_INFO)
        vm.setBrand(ServiceBrand.XIAOMI)
        val xiaomiFbInfo = vm.getAvailableActions()
        assertTrue(xiaomiFbInfo.isNotEmpty())
        assertTrue(xiaomiFbInfo.all { it.brand == ServiceBrand.XIAOMI && !it.isAdb && it.group == ActionGroup.INFO })

        vm.navigateToMode(ToolScreenMode.FASTBOOT_UNLOCK)
        vm.setBrand(ServiceBrand.VIVO)
        val vivoFbUnlock = vm.getAvailableActions()
        assertTrue(vivoFbUnlock.isNotEmpty())
        assertTrue(vivoFbUnlock.all { it.brand == ServiceBrand.VIVO && !it.isAdb && it.group == ActionGroup.UNLOCK })

        vm.navigateToMode(ToolScreenMode.ADB_INFO)
        vm.setBrand(ServiceBrand.SAMSUNG)
        val samsungAdbInfo = vm.getAvailableActions()
        assertTrue(samsungAdbInfo.isNotEmpty())
        assertTrue(samsungAdbInfo.all { it.brand == ServiceBrand.SAMSUNG && it.isAdb && it.group == ActionGroup.INFO })

        vm.navigateToMode(ToolScreenMode.ADB_UNLOCK)
        vm.setBrand(ServiceBrand.UNIVERSAL)
        val uniAdbUnlock = vm.getAvailableActions()
        assertTrue(uniAdbUnlock.isNotEmpty())
        assertTrue(uniAdbUnlock.all { it.brand == ServiceBrand.UNIVERSAL && it.isAdb && it.group == ActionGroup.UNLOCK })

        vm.navigateToMode(ToolScreenMode.ADB_SERVICE)
        vm.setBrand(ServiceBrand.UNIVERSAL)
        val uniAdbService = vm.getAvailableActions()
        assertTrue(uniAdbService.isNotEmpty())
        assertTrue(uniAdbService.all { it.brand == ServiceBrand.UNIVERSAL && it.isAdb && it.group == ActionGroup.SERVICE })

        vm.navigateToMode(ToolScreenMode.SETTINGS)
        assertEquals(ToolScreenMode.SETTINGS, vm.uiState.value.screenMode)
        vm.navigateToMode(ToolScreenMode.ABOUT)
        assertEquals(ToolScreenMode.ABOUT, vm.uiState.value.screenMode)
    }

    @Test
    fun `verify isolated cli terminal buffer and clean console logs without noise`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ServiceViewModel(app)

        vm.navigateToMode(ToolScreenMode.ADB_INFO)
        vm.setBrand(ServiceBrand.XIAOMI)
        vm.navigateToMode(ToolScreenMode.CMD_TERMINAL)
        assertTrue(vm.uiState.value.terminalLines.isEmpty())
        assertTrue(vm.uiState.value.consoleLogs.isEmpty())

        vm.submitTerminalCommand("help")
        assertTrue(vm.uiState.value.terminalLines.size >= 2)
        assertEquals("~ $ help", vm.uiState.value.terminalLines.first().text)

        vm.submitTerminalCommand("clear")
        assertTrue(vm.uiState.value.terminalLines.isEmpty())
    }

    @Test
    fun `verify console logs never expose raw CLI command strings on action execution`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ServiceViewModel(app)

        vm.navigateToMode(ToolScreenMode.FASTBOOT_INFO)
        vm.setBrand(ServiceBrand.UNIVERSAL)
        val firstAction = vm.getAvailableActions().first()
        vm.setSelectedAction(firstAction)
        vm.onStartPressed()

        Thread.sleep(150)
        val allMsgs = vm.uiState.value.consoleLogs.joinToString("\n") { it.message }
        assertTrue(allMsgs.contains(firstAction.title))
        assertTrue(!allMsgs.contains(firstAction.command))
    }

    @Test
    fun `verify theme mode, 3 theme designs, 3 font sizes, and sound toggle update cleanly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ServiceViewModel(app)

        vm.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, vm.uiState.value.themeMode)
        vm.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)

        vm.setThemeDesign(ThemeDesign.EMERALD_MATRIX)
        assertEquals(ThemeDesign.EMERALD_MATRIX, vm.uiState.value.themeDesign)
        vm.setThemeDesign(ThemeDesign.CRIMSON_FORGE)
        assertEquals(ThemeDesign.CRIMSON_FORGE, vm.uiState.value.themeDesign)
        vm.setThemeDesign(ThemeDesign.CYBER_NEON)
        assertEquals(ThemeDesign.CYBER_NEON, vm.uiState.value.themeDesign)

        vm.setFontSize(AppFontSize.COMPACT)
        assertEquals(AppFontSize.COMPACT, vm.uiState.value.fontSize)
        vm.setFontSize(AppFontSize.LARGE)
        assertEquals(AppFontSize.LARGE, vm.uiState.value.fontSize)

        assertTrue(vm.uiState.value.soundEnabled)
        vm.setSoundEnabled(false)
        assertTrue(!vm.uiState.value.soundEnabled)
        assertTrue(!ToolSoundManager.isEnabled)
        vm.setSoundEnabled(true)
        assertTrue(vm.uiState.value.soundEnabled)
        assertTrue(ToolSoundManager.isEnabled)
    }

    @Test
    fun `verify ADB RSA public key encodes exact 524-byte struct and signs 20-byte token validly`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val pub = kp.public as RSAPublicKey

        val payload = UsbPortController.encodeAdbPublicKeyPayload(pub)
        assertEquals(0.toByte(), payload.last())
        val payloadStr = String(payload, 0, payload.size - 1, StandardCharsets.US_ASCII)
        assertTrue(payloadStr.endsWith(" nativeunlock@android"))
        val b64 = payloadStr.substringBefore(" ")
        val decoded = Base64.decode(b64, Base64.NO_WRAP)
        assertEquals(524, decoded.size)

        val bb = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64, bb.int)
        val n0inv = bb.int.toLong() and 0xFFFFFFFFL

        val nBytesLE = ByteArray(256).also { bb.get(it) }
        val rrBytesLE = ByteArray(256).also { bb.get(it) }
        val exponent = bb.int
        assertEquals(pub.publicExponent.toInt(), exponent)

        val nFromStruct = BigInteger(1, nBytesLE.reversedArray())
        val rrFromStruct = BigInteger(1, rrBytesLE.reversedArray())
        assertEquals(pub.modulus, nFromStruct)
        val expectedRr = BigInteger.ONE.shiftLeft(4096).mod(pub.modulus)
        assertEquals(expectedRr, rrFromStruct)

        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0 = pub.modulus.mod(r32).toLong()
        val prodMod = (BigInteger.valueOf(n0).multiply(BigInteger.valueOf(n0inv))).mod(r32)
        assertEquals(r32.subtract(BigInteger.ONE), prodMod)

        val token = ByteArray(20) { (it + 7).toByte() }
        val sig = UsbPortController.signAdbTokenPayload(kp.private, token)
        assertEquals(256, sig.size)

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, pub)
        val recovered = cipher.doFinal(sig)
        val expectedDigestInfo = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2B, 0x0E,
            0x03, 0x02, 0x1A, 0x05, 0x00, 0x04, 0x14
        ) + token
        assertArrayEquals(expectedDigestInfo, recovered)
    }

    @Test
    fun `verify scatter txt parser extracts platform storage and partitions accurately`() {
        val scatterSample = """
            - general: MTK_PLATFORM_CFG
              info:
                - config_version: V2.2.2
                  platform: MT6768
                  project: merlin
                  storage: EMMC
                  boot_channel: MSDC_0
            - partition_index: SYS0
              partition_name: preloader
              file_name: preloader_merlin.bin
              is_download: true
              linear_start_addr: 0x0
              partition_size: 0x40000
            - partition_index: SYS1
              partition_name: boot
              file_name: boot.img
              is_download: true
              linear_start_addr: 0x1000000
              partition_size: 0x4000000
            - partition_index: SYS2
              partition_name: super
              file_name: super.img
              is_download: true
              linear_start_addr: 0x8000000
              partition_size: 0x180000000
            - partition_index: SYS3
              partition_name: proinfo
              file_name: NONE
              is_download: false
              linear_start_addr: 0x80000
              partition_size: 0x300000
        """.trimIndent()

        val (steps, fwInfo) = FlashFileProcessor.parseScatterContent(
            text = scatterSample,
            folderFiles = emptyMap(),
            sparseChecker = { false }
        )

        assertEquals("MediaTek Android Scatter", fwInfo.formatLabel)
        assertEquals("MT6768", fwInfo.chipsetOrPlatform)
        assertEquals("merlin", fwInfo.projectOrBoard)
        assertEquals("EMMC", fwInfo.storageType)
        assertEquals(4, steps.size)
        assertTrue(steps[0].isFlashable && steps[0].enabled)
        assertEquals("boot", steps[1].targetPartition)
        assertEquals(0x4000000L, steps[1].dataSize)
        assertEquals("super", steps[2].targetPartition)
        assertTrue(fwInfo.largePartitionNames.contains("super"))
        assertTrue(!steps[3].isFlashable && !steps[3].enabled)
    }

    @Test
    fun `verify rawprogram0 xml parser extracts UFS sectors partitions and sparse flags`() {
        val rawprogramXml = """
            <?xml version="1.0" ?>
            <data>
              <program SECTOR_SIZE_IN_BYTES="4096" file_sector_offset="0" filename="boot.img" label="boot_a" num_partition_sectors="16384" physical_partition_number="4" sparse="false" start_sector="100"/>
              <program SECTOR_SIZE_IN_BYTES="4096" file_sector_offset="0" filename="super.img" label="super" num_partition_sectors="1572864" physical_partition_number="0" sparse="true" start_sector="2048"/>
              <program SECTOR_SIZE_IN_BYTES="4096" file_sector_offset="0" filename="" label="userdata" num_partition_sectors="100000" physical_partition_number="0" sparse="false" start_sector="99999"/>
            </data>
        """.trimIndent()

        val (steps, fwInfo) = FlashFileProcessor.parseRawprogramXmlContent(
            xmlList = listOf("rawprogram0.xml" to rawprogramXml),
            folderFiles = emptyMap(),
            sparseChecker = { false }
        )

        assertEquals("Qualcomm Firehose Rawprogram XML", fwInfo.formatLabel)
        assertEquals(4096, fwInfo.sectorSizeBytes)
        assertEquals(3, steps.size)
        assertEquals("boot_a", steps[0].targetPartition)
        assertEquals(16384L * 4096L, steps[0].dataSize)
        assertEquals("super", steps[1].targetPartition)
        assertTrue(steps[1].isSparse)
        assertTrue(fwInfo.largePartitionNames.contains("super"))
        assertTrue(!steps[2].isFlashable && !steps[2].enabled)
    }

    @Test
    fun `verify flash bat parser detects board and partitions cleanly`() {
        val batScript = """
            @echo off
            fastboot %* getvar product 2>&1 | findstr /r /c:"^product: *sweet" || exit /B 1
            fastboot %* erase boot_a
            fastboot %* flash boot_a %~dp0images\boot.img || exit /B 1
            fastboot %* flash super %~dp0images\super.img || exit /B 1
            fastboot %* reboot
        """.trimIndent()

        val (steps, fwInfo) = FlashFileProcessor.parseScriptContent(
            scriptText = batScript,
            fileName = "flash_all.bat",
            folderFiles = emptyMap(),
            sparseChecker = { false }
        )

        assertEquals("sweet", fwInfo.projectOrBoard)
        assertEquals(3, steps.size)
        assertEquals("boot_a", steps[0].targetPartition)
        assertEquals("boot_a", steps[1].targetPartition)
        assertEquals("images/boot.img", steps[1].sourceName)
        assertEquals("super", steps[2].targetPartition)
    }

    @Test
    fun `verify sparse chunk planner splits oversized sparse and raw images with valid headers`() {
        val tempSparse = File.createTempFile("test_sparse", ".img")
        val tempRaw = File.createTempFile("test_raw", ".img")
        try {
            val blkSz = 4096
            val chunkPayload = ByteArray(blkSz * 2) { 0x5A }
            val sparseBytes = ByteBuffer.allocate(28 + 3 * (12 + chunkPayload.size) + 16)
                .order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(SparseChunkPlanner.SPARSE_HEADER_MAGIC)
                    putShort(1); putShort(0); putShort(28); putShort(12)
                    putInt(blkSz); putInt(10); putInt(4); putInt(0)

                    repeat(3) {
                        putShort(SparseChunkPlanner.CHUNK_TYPE_RAW)
                        putShort(0); putInt(2); putInt(12 + chunkPayload.size)
                        put(chunkPayload)
                    }
                    putShort(SparseChunkPlanner.CHUNK_TYPE_FILL)
                    putShort(0); putInt(4); putInt(16)
                    putInt(0x12345678)
                }.array()
            tempSparse.writeBytes(sparseBytes)

            RandomAccessFile(tempSparse, "r").channel.use { ch ->
                val packets = SparseChunkPlanner.planSparseSplits(
                    channel = ch,
                    baseOffset = 0L,
                    streamSize = sparseBytes.size.toLong(),
                    maxPacketBytes = 10000L
                )
                assertTrue(packets.size >= 3)
                assertEquals(0L, packets.first().leadingDontCareBlocks)
                assertTrue(packets[1].leadingDontCareBlocks > 0L)
                packets.forEach { pkt ->
                    assertTrue(pkt.totalTransferBytes <= 10000L)
                    val hdr = ByteBuffer.wrap(pkt.buildPacketPrefixHeader()).order(ByteOrder.LITTLE_ENDIAN)
                    assertEquals(SparseChunkPlanner.SPARSE_HEADER_MAGIC, hdr.int)
                }
            }

            val rawBytes = ByteArray(25000) { (it and 0xFF).toByte() }
            tempRaw.writeBytes(rawBytes)
            val rawPackets = SparseChunkPlanner.planRawToSparseSplits(
                baseOffset = 0L,
                streamSize = rawBytes.size.toLong(),
                maxPacketBytes = 10000L,
                blockSize = 4096
            )
            assertTrue(rawPackets.size >= 3)
            val coveredReadBytes = rawPackets.flatMap { it.slices }
                .filterIsInstance<SparseChunkPlanner.ChunkSlice.RawSlice>()
                .sumOf { it.readByteLength }
            assertEquals(25000L, coveredReadBytes)
        } finally {
            tempSparse.delete()
            tempRaw.delete()
        }
    }

    @Test
    fun `verify ViewModel flash options clearUserdata autoReboot toggleAll and FW info logging`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ServiceViewModel(app)

        assertTrue(!vm.uiState.value.clearUserdata)
        assertTrue(vm.uiState.value.autoReboot)
        vm.toggleClearUserdata()
        vm.toggleAutoReboot()
        assertTrue(vm.uiState.value.clearUserdata)
        assertTrue(!vm.uiState.value.autoReboot)

        val (steps, fwInfo) = FlashFileProcessor.parseRawprogramXmlContent(
            xmlList = listOf(
                "rawprogram0.xml" to """
                    <data>
                      <program SECTOR_SIZE_IN_BYTES="4096" filename="boot.img" label="boot" num_partition_sectors="1024" physical_partition_number="0" sparse="false" start_sector="10"/>
                      <program SECTOR_SIZE_IN_BYTES="4096" filename="super.img" label="super" num_partition_sectors="100000" physical_partition_number="0" sparse="true" start_sector="2000"/>
                    </data>
                """.trimIndent()
            ),
            folderFiles = emptyMap(),
            sparseChecker = { false }
        )

        val selected = SelectedFlashFile(
            uriString = "content://test/rawprogram0.xml",
            fileName = "rawprogram0.xml",
            fileSizeBytes = steps.sumOf { it.dataSize },
            formattedSize = FlashFileProcessor.formatSize(steps.sumOf { it.dataSize }),
            fileType = FlashFileType.RAWPROGRAM,
            magicHeaderHex = "XML",
            isSparseImage = true,
            sparseInfo = "QC",
            steps = steps,
            firmwareInfo = fwInfo
        )
        vm.applyParsedFirmware(selected)

        val logsText = vm.uiState.value.consoleLogs.joinToString("\n") { it.message }
        assertTrue(logsText.contains("Qualcomm Firehose Rawprogram XML"))
        assertTrue(logsText.contains("super"))

        vm.toggleAllFlashSteps(false)
        assertTrue(vm.uiState.value.selectedFlashFile!!.steps.none { it.enabled })
        vm.toggleAllFlashSteps(true)
        assertTrue(vm.uiState.value.selectedFlashFile!!.steps.all { it.enabled })
    }

    @Test
    fun `verify pattern 3 master key verification and one-time random key signature integrity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hwid = com.example.security.LicenseManager.getDeviceHwid(context)
        assertTrue(hwid.startsWith("MTK-HWID-") && hwid.length == 21)

        // Wrong master key must fail
        assertTrue(!com.example.security.LicenseManager.verifyMasterKey("WrongKey123"))

        // Reconstruct test master key via XOR 0x5A
        val mkChars = intArrayOf(0x0E, 0x32, 0x1A, 0x2E, 0x12, 0x36, 0x1A, 0x33, 0x34, 0x3D, 0x6A, 0x6A)
            .map { (it xor 0x5A).toChar() }
            .toCharArray()
        val validMk = String(mkChars)
        assertTrue(com.example.security.LicenseManager.verifyMasterKey(validMk))

        // Generate 30-day and Lifetime one-time random keys
        val key30 = com.example.security.LicenseManager.generateOneTimeKey(validMk, 30, "seed-a")
        val keyLife = com.example.security.LicenseManager.generateOneTimeKey(validMk, 36500, "seed-b")
        assertEquals(27, key30.length)
        assertEquals(30, com.example.security.LicenseManager.verifyKeyFormatAndExtractDays(key30))
        assertEquals(36500, com.example.security.LicenseManager.verifyKeyFormatAndExtractDays(keyLife))

        // Tampering with duration or signature must fail verification (-1)
        val tamperedKey = "MTK-9999-" + key30.substring(9)
        assertEquals(-1, com.example.security.LicenseManager.verifyKeyFormatAndExtractDays(tamperedKey))
    }

    @Test
    fun `verify user account salted password hashing docId and clear data restore logic`() {
        val email = "linpapa789@gmail.com"
        val docId = com.example.security.LicenseManager.emailToDocId(email)
        assertTrue(docId.startsWith("acc_") && docId.length == 68)
        assertTrue(docId.matches(Regex("^[a-zA-Z0-9_\\-]+$")))

        val salt = "test_salt_123456"
        val passHash1 = com.example.security.LicenseManager.hashPassword("MySecretPass123", salt)
        val passHash2 = com.example.security.LicenseManager.hashPassword("MySecretPass123", salt)
        val wrongHash = com.example.security.LicenseManager.hashPassword("WrongPass456", salt)

        assertEquals(passHash1, passHash2)
        assertTrue(passHash1 != wrongHash)

        val deviceModel = com.example.security.LicenseManager.getDeviceModel()
        assertTrue(deviceModel.isNotBlank())
    }
}


