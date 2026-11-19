package com.example.protocol

import android.content.Context
import android.util.Log
import com.example.model.LoaderManifest
import com.example.model.LoaderMetadataEntry
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Phase 1 Repository Source: Direct GitHub Raw retrieval (bkerler/Loaders).
 */
class GitHubLoaderRepositorySource(
    private val context: Context,
    private val customBaseUrl: String = "https://raw.githubusercontent.com/bkerler/Loaders/master/"
) : ILoaderRepositorySource {

    companion object {
        private const val TAG = "GitHubLoaderRepo"
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 15000

        fun parseManifestJsonStatic(jsonString: String, fallbackBaseUrl: String): LoaderManifest? {
            return try {
                val root = JSONObject(jsonString)
                val version = root.optInt("version", 100)
                val updatedAt = root.optString("updated_at", "")
                val primarySource = root.optString("primary_source", "github_raw")
                val baseUrl = root.optString("base_url", fallbackBaseUrl)

                val entriesList = mutableListOf<LoaderMetadataEntry>()
                val entriesArray = root.optJSONArray("entries")
                if (entriesArray != null) {
                    for (i in 0 until entriesArray.length()) {
                        val obj = entriesArray.getJSONObject(i)
                        entriesList.add(
                            LoaderMetadataEntry(
                                id = obj.optString("id", "entry_$i"),
                                brand = obj.optString("brand", "Generic"),
                                model = obj.optString("model", "Generic Model"),
                                soc = obj.optString("soc", "Qualcomm Snapdragon"),
                                platform = obj.optString("platform", "qualcomm"),
                                storageType = obj.optString("storage_type", "eMMC"),
                                msmHwidHex = obj.optString("msm_hwid", "0x00000000"),
                                pkHashHex = obj.optString("pk_hash", "*"),
                                fileName = obj.optString("file_name", "prog_firehose.elf"),
                                relativePath = obj.optString("relative_path", ""),
                                fileSize = obj.optLong("file_size", 0L),
                                sha256 = obj.optString("sha256", "")
                            )
                        )
                    }
                }

                LoaderManifest(
                    version = version,
                    updatedAt = updatedAt,
                    primarySource = primarySource,
                    baseUrl = baseUrl,
                    entries = entriesList
                )
            } catch (e: Exception) {
                Log.e(TAG, "JSON parsing error: ${e.message}")
                null
            }
        }
    }

    override fun getSourceId(): String = "github_raw"

    override fun getBaseUrl(): String = customBaseUrl

    override suspend fun fetchManifest(): Result<LoaderManifest> = withContext(Dispatchers.IO) {
        // 1. Try remote GitHub Raw fetch first
        try {
            val manifestUrl = URL("${customBaseUrl.trimEnd('/')}/model_map.json")
            val conn = (manifestUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "NativeUnlockTool-Android/3.1")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseManifestJsonStatic(jsonStr, customBaseUrl)
                if (parsed != null && parsed.entries.isNotEmpty()) {
                    Log.d(TAG, "Successfully fetched live manifest v${parsed.version} (${parsed.entries.size} entries) from GitHub.")
                    return@withContext Result.success(parsed)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch remote manifest from GitHub: ${e.message}. Falling back to bundled assets.")
        }

        // 2. Fallback to bundled asset model_map.json
        try {
            context.assets.open("model_map.json").use { stream: InputStream ->
                val jsonStr = stream.bufferedReader().use { it.readText() }
                val parsed = parseManifestJsonStatic(jsonStr, customBaseUrl)
                if (parsed != null) {
                    Log.d(TAG, "Loaded baseline asset manifest v${parsed.version} (${parsed.entries.size} entries).")
                    return@withContext Result.success(parsed)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bundled asset manifest: ${e.message}")
        }

        return@withContext Result.failure(IllegalStateException("Failed to load loader manifest from GitHub or Assets."))
    }

    override suspend fun fetchLoaderBytes(
        entry: LoaderMetadataEntry,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)?
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val targetUrlStr = if (entry.relativePath.startsWith("http://") || entry.relativePath.startsWith("https://")) {
            entry.relativePath
        } else {
            "${customBaseUrl.trimEnd('/')}/${entry.relativePath.trimStart('/')}"
        }

        try {
            val targetUrl = URL(targetUrlStr)
            val conn = (targetUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 30000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "NativeUnlockTool-Android/3.1")
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    IllegalStateException("Server returned HTTP ${conn.responseCode} for ${entry.fileName}")
                )
            }

            val totalBytes = conn.contentLengthLong.let { if (it > 0) it else entry.fileSize }
            val inStream = conn.inputStream
            val outStream = ByteArrayOutputStream()
            val buf = ByteArray(16384)
            var downloaded = 0L

            var bytesRead: Int
            while (inStream.read(buf).also { bytesRead = it } != -1) {
                outStream.write(buf, 0, bytesRead)
                downloaded += bytesRead
                onProgress?.invoke(downloaded, totalBytes)
            }

            val result = outStream.toByteArray()
            Log.d(TAG, "Streamed ${result.size} bytes for ${entry.fileName} from GitHub.")
            return@withContext Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming loader '${entry.fileName}': ${e.message}")
            return@withContext Result.failure(e)
        }
    }
}
