package com.example.frigateeventviewer.ui.util

import com.example.frigateeventviewer.data.model.Event

/**
 * Builds candidate media paths for an event so the app can try primary (API) path
 * then fallback path(s) when the server may serve files under either folder form.
 *
 * - Single-camera: path is /files/{camera}/{segment}/{rest}. If subdir != event_id,
 *   fallback swaps segment to the other id.
 * - Consolidated: path is /files/events/{segment}/... Fallback uses ce_{segment}
 *   for deployments that use ce_ prefix on disk.
 */
object EventMediaPath {

    /**
     * Returns candidate paths for the event thumbnail (snapshot or clip), primary first.
     * Use with [buildMediaUrl] and try each URL until one loads.
     */
    fun getThumbnailPathCandidates(event: Event): List<String> {
        val primary = event.hosted_snapshot?.takeIf { it.isNotBlank() }
            ?: event.hosted_clip?.takeIf { it.isNotBlank() }
        if (primary == null) return emptyList()
        return buildCandidateList(primary, event)
    }

    /**
     * Returns candidate paths for the event clip (video), primary first.
     * Primary is [Event.hosted_clip] or first [Event.hosted_clips] url.
     */
    fun getClipPathCandidates(event: Event): List<String> {
        val primary = event.hosted_clip?.takeIf { it.isNotBlank() }
            ?: event.hosted_clips.firstOrNull()?.url?.takeIf { it.isNotBlank() }
        if (primary == null) return emptyList()
        return buildCandidateList(primary, event)
    }

    /**
     * Returns candidate paths for placeholder/snapshot in detail view (same as thumbnail).
     */
    fun getPlaceholderPathCandidates(event: Event): List<String> {
        val primary = when {
            !event.hosted_snapshot.isNullOrBlank() -> event.hosted_snapshot
            !event.hosted_clip.isNullOrBlank() -> event.hosted_clip
            else -> event.hosted_clips.firstOrNull()?.url?.takeIf { it.isNotBlank() }
        }
        if (primary == null) return emptyList()
        return buildCandidateList(primary, event)
    }

    private fun buildCandidateList(primary: String, event: Event): List<String> {
        val fallback = buildFallbackPath(primary, event) ?: return listOf(primary)
        if (fallback == primary) return listOf(primary)
        return listOf(primary, fallback)
    }

    /**
     * Builds a fallback path by rewriting the segment that might differ (subdir vs event_id
     * for single-camera; add ce_ prefix for consolidated). Returns null if no fallback applies.
     */
    private fun buildFallbackPath(path: String, event: Event): String? {
        val trimmed = path.trim().trimStart('/')
        if (trimmed.isBlank()) return null
        val segments = trimmed.split('/').filter { it.isNotBlank() }
        if (segments.size < 3) return null

        return when {
            event.camera == "events" -> buildConsolidatedFallback(segments)
            else -> buildSingleCameraFallback(segments, event)
        }
    }

    /**
     * Path shape: files/events/{segment}/{rest}. Fallback: use ce_{segment} if segment
     * does not already start with ce_.
     */
    private fun buildConsolidatedFallback(segments: List<String>): String? {
        if (segments[0] != "files" || segments.getOrNull(1) != "events") return null
        val segment = segments.getOrNull(2) ?: return null
        if (segment.startsWith("ce_")) return null
        val fallbackSegment = "ce_$segment"
        val newSegments = segments.toMutableList()
        newSegments[2] = fallbackSegment
        return "/" + newSegments.joinToString("/")
    }

    /**
     * Path shape: files/{camera}/{segment}/{rest}. If segment equals subdir or event_id
     * and subdir != event_id, return path with segment replaced by the other id.
     */
    private fun buildSingleCameraFallback(segments: List<String>, event: Event): String? {
        if (segments[0] != "files") return null
        val pathCamera = segments.getOrNull(1) ?: return null
        if (pathCamera != event.camera) return null
        val segment = segments.getOrNull(2) ?: return null
        if (event.subdir == event.event_id) return null
        val otherSegment = when (segment) {
            event.subdir -> event.event_id
            event.event_id -> event.subdir
            else -> return null
        }
        val newSegments = segments.toMutableList()
        newSegments[2] = otherSegment
        return "/" + newSegments.joinToString("/")
    }
}
