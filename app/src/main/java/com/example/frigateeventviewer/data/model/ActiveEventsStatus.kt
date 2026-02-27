package com.example.frigateeventviewer.data.model

/**
 * Active events section in GET /status response.
 * Property names match API contract §3.2 (snake_case for Gson).
 */
data class ActiveEventsStatus(
    val total_active: Int = 0,
    val by_phase: Map<String, Int>? = null,
    val by_camera: Map<String, Int>? = null
)
