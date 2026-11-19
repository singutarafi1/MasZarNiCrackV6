package com.example.protocol

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.model.AppOperatingMode
import com.example.model.LoaderManifest
import com.example.model.LoaderMetadataEntry
import com.example.model.LoaderResolutionResult
import java.io.File

/**
 * Intelligent Qualcomm Firehose & MediaTek Loader Resolver.
 * Handles both flat hash filenames, OEM subfolder structures, and remote live stream resolution.
 */
class QcLoaderResolver(
    private val context: Context,
    private val repositorySource: ILoaderRepositorySource
) {
    companion object {
        private const val TAG = "QcLoaderResolver"
        private const val EXTERNAL_LOADERS_PATH = "/sdcard/NativeUnlockTool/loaders"
        private const val MANIFEST_FILENAME = "model_map.json"
    }

    private var cachedManifest: LoaderManifest? = null

    fun getWritableLoadersDirectory(): File {
        val primary = File(EXTERNAL_LOADERS_PATH)
        if ((primary.exists() || primary.mkdirs()) && primary.canWrite()) {
            return primary
        }
        val scopedDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
            File(it, "loaders")
        }
        if (scopedDir != null && (scopedDir.exists() || scopedDir.mkdirs())) {
            return scopedDir
        }
        return File(context.filesDir, "loaders").apply { mkdirs() }
    }

    /**
     * Initializes or refreshes the manifest.
     * In OFFLINE mode, checks synced local model_map.json first before hitting network/assets.
     */
    suspend fun getOrFetchManifest(forceRefresh: Boolean = false): LoaderManifest? {
        if (cachedManifest != null && !forceRefresh) return cachedManifest

        // Try local synced manifest on disk first
        val localManifestFile = File(getWritableLoadersDirectory(), MANIFEST_FILENAME)
        if (!forceRefresh && localManifestFile.exists() && localManifestFile.canRead()) {
            val localParsed = GitHubLoaderRepositorySource.parseManifestJsonStatic(
                jsonString = localManifestFile.readText(),
                fallbackBaseUrl = repositorySource.getBaseUrl()
            )
            if (localParsed != null && localParsed.entries.isNotEmpty()) {
                cachedManifest = localParsed
                return cachedManifest
            }
        }

        val res = repositorySource.fetchManifest()
        if (res.isSuccess) {
            cachedManifest = res.getOrNull()
        }
        return cachedManifest
    }

    fun setCachedManifest(manifest: LoaderManifest) {
        cachedManifest = manifest
    }

    /**
     * Attempts to resolve a Firehose programmer for the given hardware signature.
     */
    suspend fun resolveLoader(
        mode: AppOperatingMode,
        msmHwidHex: String,
        pkHashHex: String,
        selectedBrand: String? = null,
        storagePreference: String = "eMMC"
    ): LoaderResolutionResult {
        val cleanHwid = msmHwidHex.trim().uppercase()
        val cleanPkHash = pkHashHex.trim().lowercase()
        val searchedPaths = mutableListOf<String>()

        Log.d(TAG, "[RESOLVE] Resolving loader for HWID=$cleanHwid PK_HASH=$cleanPkHash (Mode=$mode)")

        // 1. Scan Local Storage (both flat hash filenames and OEM brand subfolders)
        val localMatch = scanLocalStorage(cleanHwid, cleanPkHash, selectedBrand, searchedPaths)
        if (localMatch != null) {
            Log.d(TAG, "[RESOLVE SUCCESS] Found local loader: ${localMatch.first.name} (${localMatch.second.size} bytes)")
            val syntheticEntry = LoaderMetadataEntry(
                id = "local_${localMatch.first.name}",
                brand = selectedBrand ?: "Generic",
                model = "Detected Device",
                soc = cleanHwid,
                msmHwidHex = cleanHwid,
                pkHashHex = cleanPkHash,
                fileName = localMatch.first.name,
                relativePath = localMatch.first.absolutePath,
                fileSize = localMatch.first.length()
            )
            return LoaderResolutionResult.FoundLocal(
                entry = syntheticEntry,
                localFilePath = localMatch.first.absolutePath,
                fileBytes = localMatch.second
            )
        }

        // 2. Query Manifest Map (live from GitHub in ONLINE mode, or synced local/assets)
        val manifest = getOrFetchManifest(forceRefresh = (mode == AppOperatingMode.ONLINE))
        if (manifest != null && manifest.entries.isNotEmpty()) {
            val matchedEntry = findMatchingEntryInManifest(manifest, cleanHwid, cleanPkHash, selectedBrand, storagePreference)
            if (matchedEntry != null) {
                if (mode == AppOperatingMode.ONLINE) {
                    val fullUrl = if (matchedEntry.relativePath.startsWith("http")) {
                        matchedEntry.relativePath
                    } else {
                        "${manifest.baseUrl.trimEnd('/')}/${matchedEntry.relativePath.trimStart('/')}"
                    }
                    Log.d(TAG, "[RESOLVE SUCCESS] Found remote loader in manifest for online stream: ${matchedEntry.fileName}")
                    return LoaderResolutionResult.FoundRemoteStream(matchedEntry, fullUrl)
                } else {
                    // Offline mode, but file not downloaded locally yet
                    return LoaderResolutionResult.NotFound(
                        msmHwid = cleanHwid,
                        pkHash = cleanPkHash,
                        searchedPaths = searchedPaths,
                        message = "Programmer '${matchedEntry.fileName}' is in catalog but not downloaded locally. Tap 'Sync Loaders' or switch to Online Mode."
                    )
                }
            }
        }

        // 3. Fallback: Check APK Assets
        val assetBytes = checkAssetLoaders(cleanHwid, cleanPkHash, searchedPaths)
        if (assetBytes != null) {
            val syntheticEntry = LoaderMetadataEntry(
                id = "asset_fallback",
                brand = selectedBrand ?: "Generic",
                model = "Detected Device",
                soc = cleanHwid,
                msmHwidHex = cleanHwid,
                pkHashHex = cleanPkHash,
                fileName = "asset_firehose.elf",
                relativePath = "assets/loaders/",
                fileSize = assetBytes.size.toLong()
            )
            return LoaderResolutionResult.FoundLocal(syntheticEntry, "assets/loaders/", assetBytes)
        }

        return LoaderResolutionResult.NotFound(
            msmHwid = cleanHwid,
            pkHash = cleanPkHash,
            searchedPaths = searchedPaths,
            message = "No matching Firehose programmer found locally or in manifest for HWID $cleanHwid (PK_HASH: $cleanPkHash)."
        )
    }

    /**
     * Scans both flat [msmid]_[pkhash] filenames and OEM brand subfolders (Xiaomi/, Oppo/, etc.).
     */
    private fun scanLocalStorage(
        hwid: String,
        pkHash: String,
        brand: String?,
        searchedPaths: MutableList<String>
    ): Pair<File, ByteArray>? {
        val rootDirs = listOfNotNull(
            File(EXTERNAL_LOADERS_PATH),
            File("/sdcard/Download/MTK_Loaders"),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "loaders") },
            File(context.filesDir, "loaders")
        ).distinctBy { it.absolutePath }

        val hwidCompact = hwid.removePrefix("0X").lowercase()
        val pkShort = if (pkHash.length >= 8 && pkHash != "none / unsigned") pkHash.substring(0, 8) else ""

        for (rootDir in rootDirs) {
            if (!rootDir.exists() || !rootDir.isDirectory) continue
            searchedPaths.add(rootDir.absolutePath)

            // A. Flat search with bkerler filename convention: [msmid]_[pkhash]*.bin / elf
            val files = rootDir.listFiles() ?: continue
            for (file in files) {
                if (file.isFile && file.canRead() && file.length() > 512) {
                    val nameLower = file.name.lowercase()
                    if (nameLower.contains(hwidCompact) && (pkShort.isBlank() || nameLower.contains(pkShort))) {
                        return try { Pair(file, file.readBytes()) } catch (_: Exception) { null }
                    }
                }
            }

            // B. Brand subfolder search: loaders/{brand}/* or walk all OEM brand subfolders
            val targetBrandDirs = if (!brand.isNullOrBlank()) {
                listOf(File(rootDir, brand)) + (files.filter { it.isDirectory })
            } else {
                files.filter { it.isDirectory }
            }

            for (brandDir in targetBrandDirs.distinctBy { it.absolutePath }) {
                if (brandDir.exists() && brandDir.isDirectory) {
                    searchedPaths.add(brandDir.absolutePath)
                    var matched: Pair<File, ByteArray>? = null
                    brandDir.walkTopDown().maxDepth(3).forEach { subFile ->
                        if (matched == null && subFile.isFile && subFile.canRead() && subFile.length() > 512) {
                            val nameLower = subFile.name.lowercase()
                            if (nameLower.contains(hwidCompact) || (pkShort.isNotBlank() && nameLower.contains(pkShort))) {
                                matched = try { Pair(subFile, subFile.readBytes()) } catch (_: Exception) { null }
                            }
                        }
                    }
                    if (matched != null) return matched
                }
            }
        }
        return null
    }

    private fun checkAssetLoaders(hwid: String, pkHash: String, searchedPaths: MutableList<String>): ByteArray? {
        searchedPaths.add("apk_assets/loaders/")
        val hwidCompact = hwid.removePrefix("0X").lowercase()
        val assetList = try { context.assets.list("loaders") } catch (_: Exception) { null } ?: return null
        for (name in assetList) {
            val nameLower = name.lowercase()
            if (nameLower.contains(hwidCompact)) {
                try {
                    context.assets.open("loaders/$name").use { return it.readBytes() }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun findMatchingEntryInManifest(
        manifest: LoaderManifest,
        hwid: String,
        pkHash: String,
        brand: String?,
        storagePref: String
    ): LoaderMetadataEntry? {
        val cleanHwid = hwid.uppercase()
        val cleanPk = pkHash.lowercase()

        // 1. Strict match: HWID + PK_HASH
        val exactMatch = manifest.entries.firstOrNull { entry ->
            entry.msmHwidHex.equals(cleanHwid, ignoreCase = true) &&
                (entry.pkHashHex == "*" || cleanPk.contains(entry.pkHashHex.lowercase())) &&
                (brand == null || entry.brand.equals(brand, ignoreCase = true))
        }
        if (exactMatch != null) return exactMatch

        // 2. Hardware ID match (any PK_HASH wildcard)
        val hwidMatch = manifest.entries.firstOrNull { entry ->
            entry.msmHwidHex.equals(cleanHwid, ignoreCase = true) &&
                entry.storageType.equals(storagePref, ignoreCase = true)
        }
        if (hwidMatch != null) return hwidMatch

        // 3. Fallback: HWID match ignoring storage type
        return manifest.entries.firstOrNull { entry ->
            entry.msmHwidHex.equals(cleanHwid, ignoreCase = true)
        }
    }
}
