package com.example.protocol

import android.content.Context
import android.util.Log
import com.example.model.LoaderManifest
import com.example.model.SyncProgressState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages full and incremental offline package synchronization from GitHub / Backend.
 * Saves loaders into /sdcard/NativeUnlockTool/loaders/[Brand]/ structure.
 */
class QcOfflineSyncManager(
    private val context: Context,
    private val repositorySource: ILoaderRepositorySource,
    private val loaderResolver: QcLoaderResolver
) {
    companion object {
        private const val TAG = "QcOfflineSyncManager"
        private const val BASE_TARGET_DIR = "/sdcard/NativeUnlockTool/loaders"
    }

    private val _syncState = MutableStateFlow(SyncProgressState())
    val syncState: StateFlow<SyncProgressState> = _syncState.asStateFlow()

    private var cancelRequested = false

    fun cancelSync() {
        cancelRequested = true
    }

    /**
     * Executes incremental synchronization. Only downloads missing or outdated loaders.
     */
    suspend fun performSync(forceFull: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        if (_syncState.value.isSyncing) {
            return@withContext Result.failure(IllegalStateException("Sync already in progress."))
        }

        cancelRequested = false
        _syncState.value = SyncProgressState(isSyncing = true, currentFileName = "Fetching remote manifest...")

        try {
            // 1. Fetch remote manifest
            val manifestRes = repositorySource.fetchManifest()
            if (manifestRes.isFailure) {
                val err = manifestRes.exceptionOrNull()?.message ?: "Failed to fetch remote repository manifest"
                _syncState.value = SyncProgressState(isSyncing = false, error = err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val manifest: LoaderManifest = manifestRes.getOrThrow()
            loaderResolver.setCachedManifest(manifest)

            // 2. Identify missing / outdated files inside safe writable loaders directory
            val baseDir = loaderResolver.getWritableLoadersDirectory()
            if (!baseDir.exists()) baseDir.mkdirs()

            // Save local model_map.json so offline sessions stay up-to-date
            try {
                val manifestJson = org.json.JSONObject().apply {
                    put("version", manifest.version)
                    put("updated_at", manifest.updatedAt)
                    put("primary_source", manifest.primarySource)
                    put("base_url", manifest.baseUrl)
                    val arr = org.json.JSONArray()
                    manifest.entries.forEach { e ->
                        arr.put(org.json.JSONObject().apply {
                            put("id", e.id)
                            put("brand", e.brand)
                            put("model", e.model)
                            put("soc", e.soc)
                            put("platform", e.platform)
                            put("storage_type", e.storageType)
                            put("msm_hwid", e.msmHwidHex)
                            put("pk_hash", e.pkHashHex)
                            put("file_name", e.fileName)
                            put("relative_path", e.relativePath)
                            put("file_size", e.fileSize)
                            put("sha256", e.sha256)
                        })
                    }
                    put("entries", arr)
                }
                File(baseDir, "model_map.json").writeText(manifestJson.toString(2))
            } catch (e: Exception) {
                Log.w(TAG, "Could not save local model_map.json: ${e.message}")
            }

            val filesToDownload = manifest.entries.filter { entry ->
                if (forceFull) return@filter true
                val brandDir = File(baseDir, entry.brand)
                val targetFile = File(brandDir, entry.fileName)
                val flatFile = File(baseDir, entry.fileName)

                val fileExists = (targetFile.exists() && targetFile.length() > 512) ||
                    (flatFile.exists() && flatFile.length() > 512)

                !fileExists
            }

            if (filesToDownload.isEmpty()) {
                val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                _syncState.value = SyncProgressState(
                    isSyncing = false,
                    totalFiles = manifest.entries.size,
                    completedFiles = manifest.entries.size,
                    percentage = 100f,
                    currentFileName = "All ${manifest.entries.size} loaders up-to-date.",
                    lastSyncTime = nowStr
                )
                return@withContext Result.success(0)
            }

            val total = filesToDownload.size
            var downloadedCount = 0

            for ((index, entry) in filesToDownload.withIndex()) {
                if (cancelRequested) {
                    _syncState.value = SyncProgressState(isSyncing = false, error = "Sync cancelled by user.")
                    return@withContext Result.failure(CancellationException("Sync cancelled"))
                }

                val pct = ((index.toFloat() / total.toFloat()) * 100f)
                _syncState.value = _syncState.value.copy(
                    totalFiles = total,
                    completedFiles = downloadedCount,
                    currentFileName = "[${index + 1}/$total] ${entry.brand}: ${entry.fileName}",
                    percentage = pct
                )

                // Fetch bytes from repository source
                val fetchRes = repositorySource.fetchLoaderBytes(entry)
                if (fetchRes.isSuccess) {
                    val bytes = fetchRes.getOrNull() ?: ByteArray(0)
                    if (bytes.isNotEmpty()) {
                        val brandDir = File(baseDir, entry.brand)
                        if (!brandDir.exists()) brandDir.mkdirs()

                        val targetFile = File(brandDir, entry.fileName)
                        targetFile.writeBytes(bytes)
                        downloadedCount++
                    }
                } else {
                    Log.w(TAG, "Failed to download loader ${entry.fileName}: ${fetchRes.exceptionOrNull()?.message}")
                }
            }

            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            _syncState.value = SyncProgressState(
                isSyncing = false,
                totalFiles = total,
                completedFiles = downloadedCount,
                percentage = 100f,
                currentFileName = "Sync completed! $downloadedCount loaders updated.",
                lastSyncTime = nowStr
            )

            return@withContext Result.success(downloadedCount)
        } catch (e: Exception) {
            _syncState.value = SyncProgressState(isSyncing = false, error = e.message)
            return@withContext Result.failure(e)
        }
    }
}
