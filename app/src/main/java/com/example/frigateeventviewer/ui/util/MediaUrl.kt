package com.example.frigateeventviewer.ui.util

/**
 * Builds a full media URL from the server base URL and a path returned by the API.
 *
 * Per API contract §2.4: full URL = `{baseUrl}{path}`. Paths (e.g. [hosted_snapshot])
 * may or may not start with `/`; we normalize so there is exactly one slash between
 * base and path. If [path] is already absolute (http/https), it is returned as-is.
 *
 * @param baseUrl Saved server base URL (e.g. "http://192.168.1.50:5000/"), or null.
 * @param path API path (e.g. "/files/events/ce_id/camera/snapshot.jpg" or "files/..."), or null.
 * @return Full URL, or null if either argument is null/blank (or path is only slashes).
 */
fun buildMediaUrl(baseUrl: String?, path: String?): String? {
    if (path.isNullOrBlank()) return null
    val trimmedPath = path.trim()
    if (trimmedPath.isBlank()) return null
    // Path is already absolute: use as-is so we don't double-prefix.
    if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
        return trimmedPath
    }
    if (baseUrl.isNullOrBlank()) return null
    val base = baseUrl.trimEnd('/')
    val pathWithoutLeadingSlash = trimmedPath.trimStart('/')
    if (pathWithoutLeadingSlash.isBlank()) return null
    return "$base/$pathWithoutLeadingSlash"
}
