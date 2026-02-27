package com.example.frigateeventviewer.ui.util

/**
 * Builds a full media URL from the server base URL and a path returned by the API.
 *
 * Per API contract §2.4: full URL = `{baseUrl}{path}`. Paths (e.g. [hosted_snapshot])
 * typically start with `/`; we avoid double slashes by trimming the base URL trailing slash.
 *
 * @param baseUrl Saved server base URL (e.g. "http://192.168.1.50:5000/"), or null.
 * @param path API path (e.g. "/files/events/ce_id/camera/snapshot.jpg"), or null.
 * @return Full URL, or null if either argument is null/blank.
 */
fun buildMediaUrl(baseUrl: String?, path: String?): String? {
    if (baseUrl.isNullOrBlank() || path.isNullOrBlank()) return null
    return baseUrl.trimEnd('/') + path
}
