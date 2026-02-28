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
 * DataStore-backed preferences for app settings (e.g. server Base URL).
 * Exposes a flow of the saved base URL and a suspend function to save it.
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

    companion object {
        private val BASE_URL_KEY = stringPreferencesKey("server_base_url")
        private val LANDSCAPE_TAB_ICON_ALPHA_KEY = floatPreferencesKey("landscape_tab_icon_alpha")
        private const val DEFAULT_LANDSCAPE_TAB_ICON_ALPHA = 0.5f

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
