package com.example.protocol

import com.example.model.LoaderManifest
import com.example.model.LoaderMetadataEntry

/**
 * Pluggable abstraction layer for the Loader repository.
 * Phase 1: GitHub Raw (bkerler/Loaders)
 * Phase 2: Private authenticated backend server
 */
interface ILoaderRepositorySource {
    /**
     * Unique identifier for this source (e.g. "github_raw", "private_backend").
     */
    fun getSourceId(): String

    /**
     * Base endpoint URL of the repository.
     */
    fun getBaseUrl(): String

    /**
     * Fetches the latest manifest map (model_map.json) from the repository.
     */
    suspend fun fetchManifest(): Result<LoaderManifest>

    /**
     * Fetches binary programmer bytes for a given loader entry on-demand (streaming).
     */
    suspend fun fetchLoaderBytes(
        entry: LoaderMetadataEntry,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<ByteArray>
}
