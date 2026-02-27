package com.example.frigateeventviewer.data.model

/**
 * Event object returned by /events and /events/<camera>.
 * Full shape per API contract §1.11; property names match JSON for Gson.
 */
data class Event(
    val event_id: String,
    val camera: String,
    val subdir: String,
    val timestamp: String,
    val summary: String? = null,
    val title: String? = null,
    val description: String? = null,
    val scene: String? = null,
    val label: String? = null,
    val severity: String? = null,
    val threat_level: Int = 0,
    val review_summary: String? = null,
    val has_clip: Boolean = false,
    val has_snapshot: Boolean = false,
    val viewed: Boolean = false,
    val hosted_clip: String? = null,
    val hosted_snapshot: String? = null,
    val hosted_clips: List<HostedClip> = emptyList(),
    val cameras: List<String>? = null,
    val cameras_with_zones: List<CamerasWithZones> = emptyList(),
    val consolidated: Boolean? = null,
    val ongoing: Boolean = false,
    val genai_entries: List<GenAiEntry> = emptyList(),
    val end_timestamp: Double? = null,
    val saved: Boolean? = null
)
