package com.example.protocol

import android.content.Context
import com.example.model.AppOperatingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the user's persistent Online vs Offline operating mode,
 * startup mode prompt visibility, and repository source URL (GitHub vs Private Backend).
 */
class AppModeManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "native_unlock_mode_prefs"
        private const val KEY_MODE = "operating_mode"
        private const val KEY_HAS_CHOSEN = "has_chosen_startup_mode"
        private const val KEY_REPO_URL = "repository_base_url"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"

        const val DEFAULT_GITHUB_RAW_URL = "https://raw.githubusercontent.com/bkerler/Loaders/master/"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _operatingMode = MutableStateFlow(loadSavedMode())
    val operatingMode: StateFlow<AppOperatingMode> = _operatingMode.asStateFlow()

    private val _showStartupPrompt = MutableStateFlow(!prefs.getBoolean(KEY_HAS_CHOSEN, false))
    val showStartupPrompt: StateFlow<Boolean> = _showStartupPrompt.asStateFlow()

    private val _repositoryUrl = MutableStateFlow(
        prefs.getString(KEY_REPO_URL, DEFAULT_GITHUB_RAW_URL) ?: DEFAULT_GITHUB_RAW_URL
    )
    val repositoryUrl: StateFlow<String> = _repositoryUrl.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(
        prefs.getString(KEY_LAST_SYNC, "Never") ?: "Never"
    )
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private fun loadSavedMode(): AppOperatingMode {
        val raw = prefs.getString(KEY_MODE, AppOperatingMode.ONLINE.name)
        return try {
            AppOperatingMode.valueOf(raw ?: AppOperatingMode.ONLINE.name)
        } catch (_: Exception) {
            AppOperatingMode.ONLINE
        }
    }

    fun selectMode(mode: AppOperatingMode, dismissPrompt: Boolean = true) {
        _operatingMode.value = mode
        prefs.edit()
            .putString(KEY_MODE, mode.name)
            .putBoolean(KEY_HAS_CHOSEN, true)
            .apply()
        if (dismissPrompt) {
            _showStartupPrompt.value = false
        }
    }

    fun openModePrompt() {
        _showStartupPrompt.value = true
    }

    fun dismissStartupPrompt() {
        _showStartupPrompt.value = false
    }

    fun setRepositoryUrl(url: String) {
        val clean = url.trim().ifBlank { DEFAULT_GITHUB_RAW_URL }
        _repositoryUrl.value = clean
        prefs.edit().putString(KEY_REPO_URL, clean).apply()
    }

    fun updateLastSyncTime(timeLabel: String) {
        _lastSyncTime.value = timeLabel
        prefs.edit().putString(KEY_LAST_SYNC, timeLabel).apply()
    }
}
