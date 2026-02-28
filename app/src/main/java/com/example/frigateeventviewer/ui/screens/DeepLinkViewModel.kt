package com.example.frigateeventviewer.ui.screens

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds pending deep-link state so MainActivity can resolve buffer://event_detail/{ce_id}
 * after NavHost is ready, and so onNewIntent / Refresh can trigger a new resolve.
 */
class DeepLinkViewModel : ViewModel() {

    private val _pendingCeId = MutableStateFlow<String?>(null)
    val pendingCeId: StateFlow<String?> = _pendingCeId.asStateFlow()

    private val _resolveTrigger = MutableStateFlow(0)
    /** Increment to run deep-link resolution (initial parse, onNewIntent, or Refresh). */
    val resolveTrigger: StateFlow<Int> = _resolveTrigger.asStateFlow()

    /**
     * Parses intent.data for buffer://event_detail/{ce_id} and sets pending ce_id,
     * then increments resolve trigger so LaunchedEffect runs resolution.
     */
    fun setFromIntent(uri: android.net.Uri?) {
        val ceId = parseDeepLinkCeId(uri) ?: return
        _pendingCeId.value = ceId
        _resolveTrigger.value += 1
    }

    /** Call when user taps Refresh on the event-not-found screen to retry resolution. */
    fun retryResolve() {
        if (_pendingCeId.value != null) {
            _resolveTrigger.value += 1
        }
    }

    fun clearPending() {
        _pendingCeId.value = null
    }

    companion object {
        /** Returns ce_id from buffer://event_detail/{ce_id} or null. */
        fun parseDeepLinkCeId(uri: android.net.Uri?): String? {
            if (uri == null) return null
            if (uri.scheme != "buffer" || uri.host != "event_detail") return null
            val path = uri.path ?: return null
            val segment = path.trimStart('/').trimEnd('/').takeIf { it.isNotBlank() } ?: return null
            return segment
        }
    }
}
