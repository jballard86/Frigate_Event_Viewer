package com.example.frigateeventviewer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore-backed preferences for app settings (e.g. server Base URL, Frigate IP).
 * Exposes flows and suspend functions to read/save base URL, Frigate IP, default Live camera, and landscape tab icon alpha.
 */
class SettingsPreferences(private val context: Context) {

    private val dataStore = context.dataStore

    /**
     * Flow of the saved base URL, or null if not set.
     */
    val baseUrl: Flow<String?> = dataStore.data.map { prefs ->
        prefs[BASE_URL_KEY]
    }

    /**
     * Saves the given base URL after normalizing and validating.
     * Trims whitespace and ensures a valid http/https scheme.
     *
     * @param url Raw input (e.g. "http://192.168.1.50:5000")
     * @return Normalized URL suitable for Retrofit, or null if invalid
     */
    suspend fun saveBaseUrl(url: String): String? {
        val normalized = normalizeBaseUrl(url) ?: return null
        dataStore.edit { prefs ->
            prefs[BASE_URL_KEY] = normalized
        }
        return normalized
    }

    /**
     * Reads the current base URL once (suspend). Prefer collecting [baseUrl] flow in UI.
     */
    suspend fun getBaseUrlOnce(): String? =
        dataStore.data.map { prefs -> prefs[BASE_URL_KEY] }.first()

    /**
     * Flow of the saved Frigate IP address, or null if not set.
     * Used to build the HTTPS Frigate API base URL (e.g. for go2rtc streams).
     */
    val frigateIp: Flow<String?> = dataStore.data.map { prefs ->
        prefs[FRIGATE_IP_KEY]?.takeIf { it.isNotBlank() }
    }

    /**
     * Saves the Frigate IP address. Trims whitespace; empty string clears the value.
     * No scheme or port—stored as hostname or IP only; [buildFrigateApiBaseUrl] adds scheme and port.
     *
     * @param value Raw input (e.g. "192.168.1.50")
     * @return The trimmed value if non-empty, or null if cleared
     */
    suspend fun saveFrigateIp(value: String): String? {
        val trimmed = value.trim()
        dataStore.edit { prefs ->
            if (trimmed.isEmpty()) prefs.remove(FRIGATE_IP_KEY)
            else prefs[FRIGATE_IP_KEY] = trimmed
        }
        return trimmed.ifBlank { null }
    }

    /**
     * Reads the current Frigate IP once (suspend). Prefer collecting [frigateIp] flow in UI.
     */
    suspend fun getFrigateIpOnce(): String? =
        dataStore.data.map { prefs -> prefs[FRIGATE_IP_KEY]?.takeIf { it.isNotBlank() } }.first()

    /**
     * Flow of the landscape tab bar icon alpha (0f..1f). Default 0.5f when unset.
     * Used for the "show tab bar" icon opacity in landscape.
     */
    val landscapeTabIconAlpha: Flow<Float> = dataStore.data.map { prefs ->
        prefs[LANDSCAPE_TAB_ICON_ALPHA_KEY] ?: DEFAULT_LANDSCAPE_TAB_ICON_ALPHA
    }

    /**
     * Saves the landscape tab bar icon alpha. Value should be in 0f..1f.
     */
    suspend fun saveLandscapeTabIconAlpha(value: Float) {
        dataStore.edit { prefs ->
            prefs[LANDSCAPE_TAB_ICON_ALPHA_KEY] = value.coerceIn(0f, 1f)
        }
    }

    /**
     * Flow of the default Live tab camera (go2rtc stream name), or null if not set.
     */
    val defaultLiveCamera: Flow<String?> = dataStore.data.map { prefs ->
        prefs[DEFAULT_LIVE_CAMERA_KEY]?.takeIf { it.isNotBlank() }
    }

    /**
     * Saves the default Live tab camera. Pass null or blank to clear.
     */
    suspend fun saveDefaultLiveCamera(value: String?) {
        dataStore.edit { prefs ->
            val trimmed = value?.trim()
            if (trimmed.isNullOrBlank()) prefs.remove(DEFAULT_LIVE_CAMERA_KEY)
            else prefs[DEFAULT_LIVE_CAMERA_KEY] = trimmed
        }
    }

    /**
     * Reads the default Live tab camera once (suspend).
     */
    suspend fun getDefaultLiveCameraOnce(): String? =
        dataStore.data.map { prefs -> prefs[DEFAULT_LIVE_CAMERA_KEY]?.takeIf { it.isNotBlank() } }.first()

    companion object {
        private val BASE_URL_KEY = stringPreferencesKey("server_base_url")
        private val FRIGATE_IP_KEY = stringPreferencesKey("frigate_ip")
        private val DEFAULT_LIVE_CAMERA_KEY = stringPreferencesKey("default_live_camera")
        private val LANDSCAPE_TAB_ICON_ALPHA_KEY = floatPreferencesKey("landscape_tab_icon_alpha")
        private const val DEFAULT_LANDSCAPE_TAB_ICON_ALPHA = 0.5f

        /**
         * Builds the Frigate API base URL from the stored Frigate IP.
         * Uses HTTP and port 5000 so it works with typical local Frigate installs that do not use TLS.
         *
         * @param frigateIp Stored IP or hostname (no scheme/port), or null
         * @return e.g. "http://192.168.1.50:5000/" or null if frigateIp is null/blank
         */
        fun buildFrigateApiBaseUrl(frigateIp: String?): String? {
            val trimmed = frigateIp?.trim() ?: return null
            if (trimmed.isBlank()) return null
            return "http://$trimmed:5000/"
        }

        /**
         * Normalizes and validates a base URL. Returns null if invalid.
         * - Trims whitespace
         * - Ensures scheme is http or https
         * - Ensures trailing slash for Retrofit compatibility
         */
        fun normalizeBaseUrl(input: String?): String? {
            val trimmed = input?.trim() ?: return null
            if (trimmed.isEmpty()) return null
            val withScheme = when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                trimmed.startsWith("//") -> "http:$trimmed"
                else -> "http://$trimmed"
            }
            return withScheme.trimEnd('/') + "/"
        }
    }
}
