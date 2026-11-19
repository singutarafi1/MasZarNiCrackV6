package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.model.FirmwareInfoSummary
import com.example.model.FlashFileType
import com.example.model.FlashStepStatus
import com.example.model.ParsedFlashStep
import com.example.model.SelectedFlashFile
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FileInputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipInputStream

object FlashFileProcessor {

    private const val LARGE_PARTITION_THRESHOLD_BYTES = SparseChunkPlanner.DEFAULT_SAFE_CHUNK_BYTES

    data class FolderDocEntry(
        val relativePath: String,
        val fileName: String,
        val uri: Uri,
        val sizeBytes: Long
    )

    fun inspectAndParseUri(context: Context, uri: Uri, defaultPartition: String): SelectedFlashFile {
        val resolver = context.contentResolver
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "firmware.bin"
        var fileSize = -1L

        try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (fileSize <= 0L) {
            try {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    if (pfd.statSize > 0L) {
                        fileSize = pfd.statSize
                    }
                }
            } catch (_: Exception) {
            }
        }

        val header = readHeaderBytes(context, uri, 28)
        val headerRead = header.size
        val magicHex = if (headerRead > 0) {
            header.take(headerRead.coerceAtMost(8))
                .joinToString(" ") { String.format(Locale.US, "%02X", it) }
        } else {
            "EMPTY"
        }

        val sparseDetails = parseSparseHeaderDescription(header)
        val isSparse = sparseDetails != null
        val hasHuaweiMagic = checkUpdateAppMagic(context, uri)
        val lower = displayName.lowercase(Locale.US)

        val probeText = if (lower.endsWith(".txt") || lower.endsWith(".xml") ||
            lower.endsWith(".bat") || lower.endsWith(".cmd") || lower.endsWith(".sh")
        ) {
            readTextSafely(context, uri, maxBytes = 2 * 1024 * 1024)
        } else {
            ""
        }

        val type = when {
            lower.endsWith(".app") || hasHuaweiMagic -> FlashFileType.UPDATE_APP
            lower.contains("scatter") && lower.endsWith(".txt") ||
                probeText.contains("partition_index", ignoreCase = true) -> FlashFileType.SCATTER
            lower.endsWith(".xml") &&
                (lower.contains("rawprogram") || probeText.contains("<program", ignoreCase = true)) ->
                FlashFileType.RAWPROGRAM
            lower.endsWith(".bat") || lower.endsWith(".cmd") || lower.endsWith(".sh") ->
                FlashFileType.SCRIPT
            lower.endsWith(".zip") -> FlashFileType.ARCHIVE
            lower.endsWith(".img") || lower.endsWith(".bin") || isSparse -> FlashFileType.IMAGE
            else -> FlashFileType.RAW
        }

        return when (type) {
            FlashFileType.SCATTER -> {
                val (steps, fwInfo) = parseScatterContent(
                    text = probeText,
                    folderFiles = emptyMap(),
                    sparseChecker = { false }
                )
                SelectedFlashFile(
                    uriString = uri.toString(),
                    fileName = displayName,
                    fileSizeBytes = fileSize.coerceAtLeast(0L),
                    formattedSize = formatSize(fileSize),
                    fileType = type,
                    magicHeaderHex = magicHex,
                    isSparseImage = false,
                    sparseInfo = "MTK Scatter Partition Map",
                    steps = steps,
                    firmwareInfo = fwInfo
                )
            }

            FlashFileType.RAWPROGRAM -> {
                val (steps, fwInfo) = parseRawprogramXmlContent(
                    xmlList = listOf(displayName to probeText),
                    folderFiles = emptyMap(),
                    sparseChecker = { false }
                )
                SelectedFlashFile(
                    uriString = uri.toString(),
                    fileName = displayName,
                    fileSizeBytes = fileSize.coerceAtLeast(0L),
                    formattedSize = formatSize(fileSize),
                    fileType = type,
                    magicHeaderHex = magicHex,
                    isSparseImage = false,
                    sparseInfo = "Qualcomm Firehose Partition Table",
                    steps = steps,
                    firmwareInfo = fwInfo
                )
            }

            FlashFileType.SCRIPT -> {
                val (steps, fwInfo) = parseScriptContent(
                    scriptText = probeText,
                    fileName = displayName,
                    folderFiles = emptyMap(),
                    sparseChecker = { false }
                )
                SelectedFlashFile(
                    uriString = uri.toString(),
                    fileName = displayName,
                    fileSizeBytes = fileSize.coerceAtLeast(0L),
                    formattedSize = formatSize(fileSize),
                    fileType = type,
                    magicHeaderHex = magicHex,
                    isSparseImage = false,
                    sparseInfo = "Fastboot Flash Script",
                    steps = steps,
                    firmwareInfo = fwInfo
                )
            }

            FlashFileType.UPDATE_APP -> {
                val (steps, fwInfo) = parseRealUpdateAppWithChannel(context, uri)
                SelectedFlashFile(
                    uriString = uri.toString(),
                    fileName = displayName,
                    fileSizeBytes = fileSize.coerceAtLeast(0L),
                    formattedSize = formatSize(fileSize),
                    fileType = type,
                    magicHeaderHex = magicHex,
                    isSparseImage = false,
                    sparseInfo = "Huawei UPDATE.APP Container",
                    steps = steps,
                    firmwareInfo = fwInfo
                )
            }

            FlashFileType.ARCHIVE -> {
                val steps = parseRealZipEntries(context, uri)
                val totalPayload = steps.sumOf { it.dataSize }
                val largeParts = steps.filter { it.dataSize > LARGE_PARTITION_THRESHOLD_BYTES }
                    .map { it.targetPartition }
                SelectedFlashFile(
                    uriString = uri.toString(),
                    fileName = displayName,
                    fileSizeBytes = fileSize.coerceAtLeast(0L),
                    formattedSize = formatSize(fileSize),
                    fileType = type,
                    magicHeaderHex = magicHex,
                    isSparseImage = false,
                    sparseInfo = "ZIP Firmware Archive",
                    steps = steps,
                    firmwareInfo = FirmwareInfoSummary(
                        formatLabel = "ZIP Firmware Package",
                        chipsetOrPlatform = "Universal / OTA",
                        projectOrBoard = displayName.substringBeforeLast('.'),
                        storageType = "Archive",
                        resolvedImageCount = steps.size,
                        missingImageCount = 0,
                        largePartitionNames = largeParts,
                        extraMetadataLines = listOf("Uncompressed Payload: ${formatSize(totalPayload)}")
                    )
                )
            }

            FlashFileType.IMAGE, FlashFileType.RAW -> {
                val inferred = inferPartition(lower, defaultPartition)
                val safeSize = fileSize.coerceAtLeast(0L)
                val largeParts = if (safeSize > LARGE_PARTITION_THRESHOLD_BYTES) listOf(inferred) else emptyList()
                val step = ParsedFlashStep(
                    id = "img_1",
                    order = 1,
                    command = "fastboot flash $inferred $displayName",
                    targetPartition = inferred,
                    sourceName = displayName,
                    sourceUriString = uri.toString(),
                    dataOffset = 0L,
                    dataSize = safeSize,
                    isSparse = isSparse,
                    isFlashable = true,
                    status = FlashStepStatus.READY
                )
                SelectedFlashFile(
                    uriString = uri.toString(),
                    fileName = displayName,
                    fileSizeBytes = safeSize,
                    formattedSize = formatSize(safeSize),
                    fileType = type,
                    magicHeaderHex = magicHex,
                    isSparseImage = isSparse,
                    sparseInfo = sparseDetails ?: "Raw Binary Stream",
                    steps = listOf(step),
                    firmwareInfo = FirmwareInfoSummary(
                        formatLabel = if (isSparse) "Android Sparse Image" else "Raw Partition Image",
                        chipsetOrPlatform = "Single Partition Target: $inferred",
                        projectOrBoard = displayName,
                        storageType = if (isSparse) "Sparse (0xED26FF3A)" else "Raw Ext4/Binary",
                        resolvedImageCount = 1,
                        missingImageCount = 0,
                        largePartitionNames = largeParts,
                        extraMetadataLines = listOf(sparseDetails ?: "Direct Stream (${formatSize(safeSize)})")
                    )
                )
            }
        }
    }

    /**
     * Walks a SAF DocumentTree folder (root + images/ subfolders) to discover Scatter,
     * Rawprogram XML, Flash .bat/.sh, or UPDATE.APP packages and resolves all partition image URIs.
     */
    fun inspectAndParseFolderTree(
        context: Context,
        treeUri: Uri,
        defaultPartition: String
    ): SelectedFlashFile? {
        val entries = collectFolderEntries(context, treeUri)
        if (entries.isEmpty()) return null

        val folderName = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast('/').substringAfterLast(':')
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Firmware Folder"

        val byRelative = entries.associateBy { it.relativePath.lowercase(Locale.US) }
        val byName = mutableMapOf<String, FolderDocEntry>()
        entries.forEach { entry ->
            val key = entry.fileName.lowercase(Locale.US)
            if (!byName.containsKey(key)) {
                byName[key] = entry
            }
        }

        val scatterDoc = entries.firstOrNull {
            val l = it.fileName.lowercase(Locale.US)
            l.endsWith(".txt") && l.contains("scatter")
        }
        val rawprogramDocs = entries.filter {
            val l = it.fileName.lowercase(Locale.US)
            l.endsWith(".xml") && l.contains("rawprogram")
        }.sortedBy { it.fileName.lowercase(Locale.US) }

        val scriptDoc = entries.filter {
            val l = it.fileName.lowercase(Locale.US)
            l.endsWith(".bat") || l.endsWith(".cmd") || l.endsWith(".sh")
        }.sortedWith(
            compareBy<FolderDocEntry> {
                val l = it.fileName.lowercase(Locale.US)
                when {
                    l.startsWith("flash_all_except") -> 0
                    l.startsWith("flash_all") -> 1
                    l.contains("flash") -> 2
                    else -> 3
                }
            }.thenBy { it.fileName }
        ).firstOrNull()

        val updateAppDoc = entries.firstOrNull {
            it.fileName.equals("UPDATE.APP", ignoreCase = true) ||
                it.fileName.lowercase(Locale.US).endsWith(".app")
        }

        val sparseCheck: (Uri) -> Boolean = { imgUri ->
            val hdr = readHeaderBytes(context, imgUri, 4)
            isSparseMagic(hdr)
        }

        val allLookupMap = HashMap<String, FolderDocEntry>(byRelative.size + byName.size).apply {
            putAll(byName)
            putAll(byRelative)
        }

        return when {
            scatterDoc != null -> {
                val text = readTextSafely(context, scatterDoc.uri, maxBytes = 2 * 1024 * 1024)
                val (steps, fwInfo) = parseScatterContent(text, allLookupMap, sparseCheck)
                val totalImgBytes = steps.filter { it.sourceUriString != null }.sumOf { it.dataSize }
                SelectedFlashFile(
                    uriString = scatterDoc.uri.toString(),
                    fileName = "$folderName/${scatterDoc.fileName}",
                    fileSizeBytes = totalImgBytes,
                    formattedSize = formatSize(totalImgBytes),
                    fileType = FlashFileType.SCATTER,
                    magicHeaderHex = "SCATTER",
                    isSparseImage = steps.any { it.isSparse },
                    sparseInfo = "MTK Scatter Package",
                    steps = steps,
                    firmwareInfo = fwInfo.copy(isFolderPackage = true)
                )
            }

            rawprogramDocs.isNotEmpty() -> {
                val xmlPairs = rawprogramDocs.map { doc ->
                    doc.fileName to readTextSafely(context, doc.uri, maxBytes = 2 * 1024 * 1024)
                }
                val (steps, fwInfo) = parseRawprogramXmlContent(xmlPairs, allLookupMap, sparseCheck)
                val totalImgBytes = steps.filter { it.sourceUriString != null }.sumOf { it.dataSize }
                SelectedFlashFile(
                    uriString = rawprogramDocs.first().uri.toString(),
                    fileName = "$folderName (${rawprogramDocs.size} XML)",
                    fileSizeBytes = totalImgBytes,
                    formattedSize = formatSize(totalImgBytes),
                    fileType = FlashFileType.RAWPROGRAM,
                    magicHeaderHex = "XML",
                    isSparseImage = steps.any { it.isSparse },
                    sparseInfo = "Qualcomm Firehose Package",
                    steps = steps,
                    firmwareInfo = fwInfo.copy(isFolderPackage = true)
                )
            }

            scriptDoc != null -> {
                val text = readTextSafely(context, scriptDoc.uri, maxBytes = 1024 * 1024)
                val (steps, fwInfo) = parseScriptContent(text, scriptDoc.fileName, allLookupMap, sparseCheck)
                val totalImgBytes = steps.filter { it.sourceUriString != null }.sumOf { it.dataSize }
                SelectedFlashFile(
                    uriString = scriptDoc.uri.toString(),
                    fileName = "$folderName/${scriptDoc.fileName}",
                    fileSizeBytes = totalImgBytes,
                    formattedSize = formatSize(totalImgBytes),
                    fileType = FlashFileType.SCRIPT,
                    magicHeaderHex = "SCRIPT",
                    isSparseImage = steps.any { it.isSparse },
                    sparseInfo = "Fastboot Script Package",
                    steps = steps,
                    firmwareInfo = fwInfo.copy(isFolderPackage = true)
                )
            }

            updateAppDoc != null -> {
                inspectAndParseUri(context, updateAppDoc.uri, defaultPartition)
            }

            else -> {
                val imageEntries = entries.filter {
                    val l = it.fileName.lowercase(Locale.US)
                    l.endsWith(".img") || l.endsWith(".bin")
                }.sortedBy { it.fileName }
                if (imageEntries.isEmpty()) return null

                val steps = imageEntries.mapIndexed { idx, entry ->
                    val part = inferPartition(entry.fileName.lowercase(Locale.US), defaultPartition)
                    val isSp = sparseCheck(entry.uri)
                    ParsedFlashStep(
                        id = "fimg_${idx + 1}",
                        order = idx + 1,
                        command = "fastboot flash $part ${entry.fileName}",
                        targetPartition = part,
                        sourceName = entry.relativePath,
                        sourceUriString = entry.uri.toString(),
                        dataOffset = 0L,
                        dataSize = entry.sizeBytes,
                        isSparse = isSp,
                        isFlashable = true,
                        status = FlashStepStatus.READY
                    )
                }
                val totalBytes = steps.sumOf { it.dataSize }
                val largeParts = steps.filter { it.dataSize > LARGE_PARTITION_THRESHOLD_BYTES }
                    .map { it.targetPartition }
                SelectedFlashFile(
                    uriString = treeUri.toString(),
                    fileName = "$folderName (${steps.size} images)",
                    fileSizeBytes = totalBytes,
                    formattedSize = formatSize(totalBytes),
                    fileType = FlashFileType.IMAGE,
                    magicHeaderHex = "FOLDER",
                    isSparseImage = steps.any { it.isSparse },
                    sparseInfo = "Multi-Image Folder",
                    steps = steps,
                    firmwareInfo = FirmwareInfoSummary(
                        formatLabel = "Fastboot Image Folder",
                        chipsetOrPlatform = "Universal Fastboot",
                        projectOrBoard = folderName,
                        storageType = "Multi-Partition",
                        isFolderPackage = true,
                        resolvedImageCount = steps.size,
                        missingImageCount = 0,
                        largePartitionNames = largeParts
                    )
                )
            }
        }
    }

    fun collectFolderEntries(
        context: Context,
        treeUri: Uri
    ): List<FolderDocEntry> {
        val result = mutableListOf<FolderDocEntry>()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()

        fun scanDir(docId: String, prefix: String, depth: Int) {
            if (depth > 2) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                    val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                    while (c.moveToNext()) {
                        val childId = if (idIdx >= 0) c.getString(idIdx) else continue
                        val name = if (nameIdx >= 0) c.getString(nameIdx) else continue
                        val mime = if (mimeIdx >= 0) c.getString(mimeIdx) else ""
                        val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else 0L
                        val relPath = if (prefix.isEmpty()) name else "$prefix/$name"

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            scanDir(childId, relPath, depth + 1)
                        } else {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            result.add(
                                FolderDocEntry(
                                    relativePath = relPath,
                                    fileName = name,
                                    uri = fileUri,
                                    sizeBytes = size.coerceAtLeast(0L)
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        scanDir(rootDocId, "", 0)
        return result
    }

    /**
     * Parses MediaTek Android Scatter files (both classic INI and YAML `- partition_index:` styles).
     */
    fun parseScatterContent(
        text: String,
        folderFiles: Map<String, FolderDocEntry>,
        sparseChecker: (Uri) -> Boolean
    ): Pair<List<ParsedFlashStep>, FirmwareInfoSummary> {
        var configVersion = ""
        var platform = "MediaTek"
        var project = "Unknown"
        var storage = "EMMC"
        var bootChannel = ""

        data class MutableBlock(
            var index: String = "",
            var partName: String = "",
            var fileName: String = "",
            var isDownload: Boolean = false,
            var startAddr: String = "",
            var partitionSize: Long = 0L,
            var type: String = ""
        )

        val blocks = mutableListOf<MutableBlock>()
        var current: MutableBlock? = null
        var inGeneralSection = true

        text.lineSequence().forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach

            val clean = trimmed.removePrefix("-").trim()
            val colonIdx = clean.indexOf(':')
            if (colonIdx <= 0) return@forEach

            val key = clean.substring(0, colonIdx).trim().lowercase(Locale.US)
            val value = clean.substring(colonIdx + 1).trim().trim('"', '\'')

            if (key == "partition_index") {
                inGeneralSection = false
                current = MutableBlock(index = value).also { blocks.add(it) }
                return@forEach
            }

            if (inGeneralSection) {
                when (key) {
                    "config_version" -> configVersion = value
                    "platform" -> platform = value
                    "project" -> project = value
                    "storage" -> storage = value
                    "boot_channel" -> bootChannel = value
                }
            }

            val blk = current ?: return@forEach
            when (key) {
                "partition_name" -> blk.partName = value
                "file_name" -> blk.fileName = value
                "is_download" -> blk.isDownload = value.equals("true", ignoreCase = true)
                "linear_start_addr", "physical_start_addr" -> {
                    if (blk.startAddr.isEmpty() || key == "linear_start_addr") {
                        blk.startAddr = value
                    }
                }
                "partition_size" -> blk.partitionSize = parseHexOrDecLong(value)
                "type" -> blk.type = value
            }
        }

        var resolvedCount = 0
        var missingCount = 0
        val steps = mutableListOf<ParsedFlashStep>()

        blocks.forEachIndexed { idx, blk ->
            if (blk.partName.isBlank()) return@forEachIndexed
            val cleanPart = blk.partName.lowercase(Locale.US)
            val hasFile = blk.fileName.isNotBlank() && !blk.fileName.equals("NONE", ignoreCase = true)
            val docEntry = if (hasFile) resolveFolderFile(blk.fileName, folderFiles) else null
            val actualSize = docEntry?.sizeBytes?.takeIf { it > 0L } ?: blk.partitionSize
            val isSparse = docEntry != null && sparseChecker(docEntry.uri)

            if (blk.isDownload && hasFile) {
                if (docEntry != null) resolvedCount++ else missingCount++
            }

            val isFlashable = blk.isDownload && hasFile
            steps.add(
                ParsedFlashStep(
                    id = "scatter_${idx + 1}_$cleanPart",
                    order = idx + 1,
                    command = if (hasFile) "fastboot flash $cleanPart ${blk.fileName}" else "fastboot getvar partition-size:$cleanPart",
                    targetPartition = cleanPart,
                    sourceName = if (hasFile) blk.fileName else "NONE (${blk.type.ifEmpty { "SYS" }})",
                    sourceUriString = docEntry?.uri?.toString(),
                    dataOffset = 0L,
                    dataSize = actualSize,
                    startAddressHex = blk.startAddr,
                    isSparse = isSparse,
                    isFlashable = isFlashable,
                    status = if (isFlashable) FlashStepStatus.READY else FlashStepStatus.SKIPPED,
                    enabled = isFlashable
                )
            )
        }

        val largeParts = steps.filter { it.isFlashable && it.dataSize > LARGE_PARTITION_THRESHOLD_BYTES }
            .map { it.targetPartition }

        val extras = buildList {
            if (configVersion.isNotEmpty()) add("Scatter Version: $configVersion")
            if (bootChannel.isNotEmpty()) add("Boot Channel: $bootChannel")
            add("Total Partitions: ${steps.size} (${steps.count { it.isFlashable }} downloadable)")
        }

        val fwInfo = FirmwareInfoSummary(
            formatLabel = "MediaTek Android Scatter",
            chipsetOrPlatform = platform,
            projectOrBoard = project,
            storageType = storage,
            sectorSizeBytes = if (storage.contains("UFS", ignoreCase = true)) 4096 else 512,
            isFolderPackage = folderFiles.isNotEmpty(),
            resolvedImageCount = resolvedCount,
            missingImageCount = missingCount,
            largePartitionNames = largeParts,
            extraMetadataLines = extras
        )
        return steps to fwInfo
    }

    /**
     * Parses Qualcomm Firehose rawprogram*.xml files (`<program ... />` tags).
     */
    fun parseRawprogramXmlContent(
        xmlList: List<Pair<String, String>>,
        folderFiles: Map<String, FolderDocEntry>,
        sparseChecker: (Uri) -> Boolean
    ): Pair<List<ParsedFlashStep>, FirmwareInfoSummary> {
        val steps = mutableListOf<ParsedFlashStep>()
        var detectedSectorSize = 4096
        val luns = mutableSetOf<String>()
        var resolvedCount = 0
        var missingCount = 0
        var order = 1

        xmlList.forEach { (xmlName, xmlContent) ->
            try {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(StringReader(xmlContent))
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG &&
                        parser.name.equals("program", ignoreCase = true)
                    ) {
                        val label = parser.getAttributeValue(null, "label")?.trim().orEmpty()
                        val filename = parser.getAttributeValue(null, "filename")?.trim().orEmpty()
                        val sectorSzStr = parser.getAttributeValue(null, "SECTOR_SIZE_IN_BYTES")?.trim()
                        val numSectorsStr = parser.getAttributeValue(null, "num_partition_sectors")?.trim()
                        val startSectorStr = parser.getAttributeValue(null, "start_sector")?.trim().orEmpty()
                        val lun = parser.getAttributeValue(null, "physical_partition_number")?.trim().orEmpty()
                        val sparseAttr = parser.getAttributeValue(null, "sparse")?.trim().orEmpty()

                        val sectorSz = sectorSzStr?.toIntOrNull()?.takeIf { it > 0 } ?: 4096
                        detectedSectorSize = sectorSz
                        if (lun.isNotEmpty()) luns.add(lun)

                        val numSectors = numSectorsStr?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        val declaredBytes = numSectors * sectorSz.toLong()
                        val partName = label.ifEmpty {
                            inferPartition(filename.lowercase(Locale.US), "raw_$order")
                        }
                        val hasFile = filename.isNotBlank()
                        val docEntry = if (hasFile) resolveFolderFile(filename, folderFiles) else null
                        val actualBytes = docEntry?.sizeBytes?.takeIf { it > 0L } ?: declaredBytes
                        val isSparse = sparseAttr.equals("true", ignoreCase = true) ||
                            (docEntry != null && sparseChecker(docEntry.uri))

                        if (hasFile) {
                            if (docEntry != null) resolvedCount++ else missingCount++
                        }

                        val addrLabel = buildString {
                            if (lun.isNotEmpty()) append("LUN$lun ")
                            if (startSectorStr.isNotEmpty()) append("Sec:$startSectorStr")
                        }.trim()

                        steps.add(
                            ParsedFlashStep(
                                id = "rawxml_${order}_${partName}_${lun.ifEmpty { "0" }}",
                                order = order,
                                command = if (hasFile) "fastboot flash $partName $filename" else "fastboot getvar partition-size:$partName",
                                targetPartition = partName,
                                sourceName = if (hasFile) filename else "EMPTY ($xmlName)",
                                sourceUriString = docEntry?.uri?.toString(),
                                dataOffset = 0L,
                                dataSize = actualBytes,
                                startAddressHex = addrLabel,
                                isSparse = isSparse,
                                isFlashable = hasFile,
                                status = if (hasFile) FlashStepStatus.READY else FlashStepStatus.SKIPPED,
                                enabled = hasFile
                            )
                        )
                        order++
                    }
                    event = parser.next()
                }
            } catch (_: Exception) {
            }
        }

        val largeParts = steps.filter { it.isFlashable && it.dataSize > LARGE_PARTITION_THRESHOLD_BYTES }
            .map { it.targetPartition }
            .distinct()

        val storageGuess = if (detectedSectorSize >= 4096) "UFS (4096B Sector)" else "eMMC (512B Sector)"
        val fwInfo = FirmwareInfoSummary(
            formatLabel = "Qualcomm Firehose Rawprogram XML",
            chipsetOrPlatform = "Qualcomm Snapdragon",
            projectOrBoard = if (luns.isNotEmpty()) "LUNs: ${luns.sorted().joinToString(",")}" else "LUN 0",
            storageType = storageGuess,
            sectorSizeBytes = detectedSectorSize,
            isFolderPackage = folderFiles.isNotEmpty(),
            resolvedImageCount = resolvedCount,
            missingImageCount = missingCount,
            largePartitionNames = largeParts,
            extraMetadataLines = listOf(
                "XML Files: ${xmlList.joinToString(", ") { it.first }}",
                "Total Entries: ${steps.size} (${steps.count { it.isFlashable }} with image files)"
            )
        )
        return steps to fwInfo
    }

    /**
     * Parses Fastboot .bat / .cmd / .sh flash scripts (Xiaomi, Motorola, Pixel, Nothing).
     */
    fun parseScriptContent(
        scriptText: String,
        fileName: String,
        folderFiles: Map<String, FolderDocEntry>,
        sparseChecker: (Uri) -> Boolean
    ): Pair<List<ParsedFlashStep>, FirmwareInfoSummary> {
        val result = mutableListOf<ParsedFlashStep>()
        var detectedBoard = "Unknown"
        var resolvedCount = 0
        var missingCount = 0
        var order = 1

        val productRegex = Regex("""\^product:\s*\*?([A-Za-z0-9_-]+)""")
        val boardVarRegex = Regex("""(?:BOARD|PRODUCT|DEVICE)\s*=\s*["']?([A-Za-z0-9_-]+)["']?""", RegexOption.IGNORE_CASE)

        scriptText.lineSequence().forEach { rawLine ->
            val rawTrim = rawLine.trim()
            productRegex.find(rawTrim)?.groupValues?.getOrNull(1)?.let { detectedBoard = it }
            boardVarRegex.find(rawTrim)?.groupValues?.getOrNull(1)?.let {
                if (detectedBoard == "Unknown") detectedBoard = it
            }

            val line = rawTrim
                .replace("%~dp0", "")
                .replace("%*", "")
                .replace("\$ANDROID_PRODUCT_OUT/", "")
                .replace("\"${'$'}FASTBOOT\"", "fastboot")
                .replace("\\", "/")

            if (line.isNotEmpty() &&
                !line.startsWith("::") &&
                !line.startsWith("#") &&
                !line.startsWith("REM ", ignoreCase = true) &&
                !line.startsWith("@echo", ignoreCase = true) &&
                !line.startsWith("echo ", ignoreCase = true) &&
                !line.contains("getvar product", ignoreCase = true) &&
                (line.contains("fastboot", ignoreCase = true) || line.contains("adb", ignoreCase = true))
            ) {
                val cleanCmd = line
                    .replace(Regex("^.*fastboot(\\.exe)?", RegexOption.IGNORE_CASE), "fastboot")
                    .replace(Regex("\\s+"), " ")
                    .substringBefore("||")
                    .substringBefore("&&")
                    .trim()

                if (cleanCmd.equals("fastboot reboot", ignoreCase = true)) {
                    return@forEach
                }

                val parts = cleanCmd.split(" ").map { it.trim('"', '\'') }
                val flashIdx = parts.indexOfFirst { it.equals("flash", ignoreCase = true) }
                val eraseIdx = parts.indexOfFirst { it.equals("erase", ignoreCase = true) }

                val targetPart = when {
                    flashIdx >= 0 && flashIdx + 1 < parts.size -> parts[flashIdx + 1]
                    eraseIdx >= 0 && eraseIdx + 1 < parts.size -> parts[eraseIdx + 1]
                    else -> "device"
                }

                val srcFile = if (flashIdx >= 0 && flashIdx + 2 < parts.size) {
                    parts[flashIdx + 2]
                } else {
                    cleanCmd
                }

                val isFlashCmd = flashIdx >= 0 && flashIdx + 2 < parts.size
                val docEntry = if (isFlashCmd) resolveFolderFile(srcFile, folderFiles) else null
                if (isFlashCmd) {
                    if (docEntry != null) resolvedCount++ else missingCount++
                }
                val isSparse = docEntry != null && sparseChecker(docEntry.uri)

                result.add(
                    ParsedFlashStep(
                        id = "script_$order",
                        order = order,
                        command = cleanCmd,
                        targetPartition = targetPart,
                        sourceName = srcFile,
                        sourceUriString = docEntry?.uri?.toString(),
                        dataOffset = 0L,
                        dataSize = docEntry?.sizeBytes ?: 0L,
                        startAddressHex = if (isFlashCmd) "SCRIPT #$order" else "CMD",
                        isSparse = isSparse,
                        isFlashable = true,
                        status = FlashStepStatus.READY,
                        enabled = true
                    )
                )
                order++
            }
        }

        val largeParts = result.filter { it.dataSize > LARGE_PARTITION_THRESHOLD_BYTES }
            .map { it.targetPartition }
            .distinct()

        val fwInfo = FirmwareInfoSummary(
            formatLabel = "Fastboot Flash Script",
            chipsetOrPlatform = "Fastboot Protocol",
            projectOrBoard = detectedBoard,
            storageType = if (largeParts.isNotEmpty()) "Dynamic / Large Partition Ready" else "Fastboot Script",
            isFolderPackage = folderFiles.isNotEmpty(),
            resolvedImageCount = resolvedCount,
            missingImageCount = missingCount,
            largePartitionNames = largeParts,
            extraMetadataLines = listOf(
                "Script File: $fileName",
                "Script Steps: ${result.size} operations"
            )
        )
        return result to fwInfo
    }

    /**
     * Uses FileChannel O(1) seeking on ParcelFileDescriptor to parse multi-gigabyte
     * Huawei UPDATE.APP packages and extract real Hardware ID / Build Date metadata.
     */
    private fun parseRealUpdateAppWithChannel(
        context: Context,
        uri: Uri
    ): Pair<List<ParsedFlashStep>, FirmwareInfoSummary> {
        val steps = mutableListOf<ParsedFlashStep>()
        var hwId = "Huawei HiSilicon / Qualcomm"
        var buildDate = ""
        var buildTime = ""

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    val fileSize = channel.size()
                    var pos = 0L
                    val buf = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN)
                    val magicProbe = ByteBuffer.allocate(4)
                    var order = 1

                    while (pos + 96 <= fileSize && steps.size < 64) {
                        buf.clear()
                        val read = channel.read(buf, pos)
                        if (read < 96) break
                        buf.flip()

                        var magicIdx = -1
                        for (i in 0..92) {
                            if (buf.get(i) == 0x55.toByte() &&
                                buf.get(i + 1) == 0xAA.toByte() &&
                                buf.get(i + 2) == 0x5A.toByte() &&
                                buf.get(i + 3) == 0xA5.toByte()
                            ) {
                                magicIdx = i
                                break
                            }
                        }

                        if (magicIdx < 0) {
                            pos += 4L
                            continue
                        }
                        if (magicIdx > 0) {
                            pos += magicIdx.toLong()
                            continue
                        }

                        val headerSize = buf.getInt(4).toLong() and 0xFFFFFFFFL
                        val dataSize = buf.getInt(24).toLong() and 0xFFFFFFFFL

                        if (buildDate.isEmpty()) {
                            val hwBytes = ByteArray(8).also {
                                buf.position(12)
                                buf.get(it)
                            }
                            val dateBytes = ByteArray(16).also {
                                buf.position(28)
                                buf.get(it)
                            }
                            val timeBytes = ByteArray(16).also {
                                buf.position(44)
                                buf.get(it)
                            }
                            val h = String(hwBytes, Charsets.US_ASCII).substringBefore('\u0000').trim()
                            val d = String(dateBytes, Charsets.US_ASCII).substringBefore('\u0000').trim()
                            val t = String(timeBytes, Charsets.US_ASCII).substringBefore('\u0000').trim()
                            if (h.isNotEmpty()) hwId = h
                            if (d.isNotEmpty()) buildDate = d
                            if (t.isNotEmpty()) buildTime = t
                        }

                        val nameBytes = ByteArray(32)
                        buf.position(60)
                        buf.get(nameBytes)
                        val rawName = String(nameBytes, Charsets.US_ASCII)
                            .substringBefore('\u0000')
                            .trim()
                            .lowercase(Locale.US)

                        if (headerSize in 92..65536 &&
                            rawName.isNotEmpty() &&
                            rawName.all { it.isLetterOrDigit() || it == '_' || it == '-' }
                        ) {
                            val dataOffset = pos + headerSize
                            if (dataOffset + dataSize <= fileSize) {
                                magicProbe.clear()
                                val isSp = if (dataSize >= 4L && channel.read(magicProbe, dataOffset) == 4) {
                                    isSparseMagic(magicProbe.array())
                                } else {
                                    false
                                }

                                steps.add(
                                    ParsedFlashStep(
                                        id = "uapp_$order",
                                        order = order,
                                        command = "fastboot flash $rawName",
                                        targetPartition = rawName,
                                        sourceName = "${rawName.uppercase(Locale.US)}.IMG (@0x${dataOffset.toString(16)})",
                                        sourceUriString = uri.toString(),
                                        dataOffset = dataOffset,
                                        dataSize = dataSize,
                                        startAddressHex = "0x${dataOffset.toString(16)}",
                                        isSparse = isSp,
                                        isFlashable = true,
                                        status = FlashStepStatus.READY
                                    )
                                )
                                order++
                                val packetTotal = headerSize + dataSize
                                val rem = packetTotal % 4L
                                val alignedAdvance = if (rem == 0L) packetTotal else packetTotal + (4L - rem)
                                pos += alignedAdvance
                                continue
                            }
                        }
                        pos += 4L
                    }
                }
            }
        } catch (_: Exception) {
        }

        val largeParts = steps.filter { it.dataSize > LARGE_PARTITION_THRESHOLD_BYTES }
            .map { it.targetPartition }
        val extras = buildList {
            if (buildDate.isNotEmpty()) add("Build Date: $buildDate $buildTime".trim())
            add("Embedded Images: ${steps.size} partitions")
        }

        val fwInfo = FirmwareInfoSummary(
            formatLabel = "Huawei UPDATE.APP",
            chipsetOrPlatform = "Hardware ID: $hwId",
            projectOrBoard = "Huawei / Honor",
            storageType = "Container Package",
            isFolderPackage = false,
            resolvedImageCount = steps.size,
            missingImageCount = 0,
            largePartitionNames = largeParts,
            extraMetadataLines = extras
        )
        return steps to fwInfo
    }

    private fun parseRealZipEntries(context: Context, uri: Uri): List<ParsedFlashStep> {
        val steps = mutableListOf<ParsedFlashStep>()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zis ->
                    var entry = zis.nextEntry
                    var order = 1
                    while (entry != null && order <= 40) {
                        if (!entry.isDirectory) {
                            val name = entry.name.substringAfterLast('/')
                            val lower = name.lowercase(Locale.US)
                            if (lower.endsWith(".img") || lower.endsWith(".bin") || lower == "payload.bin") {
                                val part = inferPartition(lower, "super")
                                val cmd = if (lower == "payload.bin") {
                                    "adb sideload $name"
                                } else {
                                    "fastboot flash $part $name"
                                }
                                steps.add(
                                    ParsedFlashStep(
                                        id = "zip_$order",
                                        order = order,
                                        command = cmd,
                                        targetPartition = part,
                                        sourceName = entry.name,
                                        sourceUriString = null,
                                        dataOffset = 0L,
                                        dataSize = entry.size.coerceAtLeast(0L),
                                        isFlashable = true,
                                        status = FlashStepStatus.READY
                                    )
                                )
                                order++
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (_: Exception) {
        }
        return steps
    }

    private fun resolveFolderFile(
        rawPath: String,
        folderFiles: Map<String, FolderDocEntry>
    ): FolderDocEntry? {
        if (folderFiles.isEmpty()) return null
        val normalized = rawPath.replace("\\", "/").trim().lowercase(Locale.US)
        val baseName = normalized.substringAfterLast('/')
        return folderFiles[normalized]
            ?: folderFiles[baseName]
            ?: folderFiles["images/$baseName"]
    }

    private fun parseHexOrDecLong(raw: String): Long {
        val clean = raw.trim().lowercase(Locale.US)
        return try {
            if (clean.startsWith("0x")) {
                clean.removePrefix("0x").toLong(16)
            } else {
                clean.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun readHeaderBytes(context: Context, uri: Uri, maxBytes: Int): ByteArray {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(maxBytes)
                var readTotal = 0
                while (readTotal < maxBytes) {
                    val r = stream.read(buf, readTotal, maxBytes - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                buf.copyOf(readTotal)
            } ?: ByteArray(0)
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    fun readTextSafely(context: Context, uri: Uri, maxBytes: Int): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(maxBytes)
                var readTotal = 0
                while (readTotal < maxBytes) {
                    val r = stream.read(buf, readTotal, maxBytes - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                String(buf, 0, readTotal, Charsets.UTF_8)
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun isSparseMagic(header: ByteArray): Boolean {
        return header.size >= 4 &&
            header[0] == 0x3A.toByte() &&
            header[1] == 0xFF.toByte() &&
            header[2] == 0x26.toByte() &&
            header[3] == 0xED.toByte()
    }

    private fun parseSparseHeaderDescription(header: ByteArray): String? {
        if (header.size < 28 || !isSparseMagic(header)) return null
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(4)
        val major = bb.short.toInt() and 0xFFFF
        val minor = bb.short.toInt() and 0xFFFF
        bb.short // file_hdr_sz
        bb.short // chunk_hdr_sz
        val blkSz = bb.int.toLong() and 0xFFFFFFFFL
        val totalBlks = bb.int.toLong() and 0xFFFFFFFFL
        val totalChunks = bb.int.toLong() and 0xFFFFFFFFL
        return "Sparse v$major.$minor (${blkSz}B blk, $totalBlks blks, $totalChunks chunks)"
    }

    private fun checkUpdateAppMagic(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val probe = ByteArray(128)
                val read = stream.read(probe)
                if (read < 4) return false
                for (i in 0..(read - 4)) {
                    if (probe[i] == 0x55.toByte() &&
                        probe[i + 1] == 0xAA.toByte() &&
                        probe[i + 2] == 0x5A.toByte() &&
                        probe[i + 3] == 0xA5.toByte()
                    ) {
                        return true
                    }
                }
                false
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun inferPartition(lowerName: String, fallback: String = "boot"): String {
        return when {
            lowerName.contains("init_boot") -> "init_boot"
            lowerName.contains("vendor_boot") -> "vendor_boot"
            lowerName.contains("boot") -> "boot"
            lowerName.contains("vbmeta_system") -> "vbmeta_system"
            lowerName.contains("vbmeta_vendor") -> "vbmeta_vendor"
            lowerName.contains("vbmeta") -> "vbmeta"
            lowerName.contains("recovery") -> "recovery"
            lowerName.contains("super") -> "super"
            lowerName.contains("system_ext") -> "system_ext"
            lowerName.contains("system") -> "system"
            lowerName.contains("vendor") -> "vendor"
            lowerName.contains("product") -> "product"
            lowerName.contains("dtbo") -> "dtbo"
            lowerName.contains("modem") || lowerName.contains("non-hlos") -> "modem"
            lowerName.contains("persist") -> "persist"
            lowerName.contains("userdata") -> "userdata"
            lowerName.contains("metadata") -> "metadata"
            else -> fallback
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 0L) return "Unknown"
        if (bytes == 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
