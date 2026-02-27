package com.example.frigateeventviewer.data.model

/**
 * Single error entry in `recent_errors` (GET /status) and `errors` (GET /stats).
 * The Python backend returns arrays of these objects, not arrays of strings.
 * Property names match JSON keys for Gson.
 */
data class SystemError(
    val ts: String? = null,
    val message: String? = null
)
