package com.example.frigateeventviewer.data.model

/**
 * Events counts section in GET /stats response.
 * Property names match API contract §3.1 (snake_case for Gson).
 */
data class StatsEvents(
    val today: Int = 0,
    val this_week: Int = 0,
    val this_month: Int = 0,
    val total_reviewed: Int = 0,
    val total_unreviewed: Int = 0,
    val by_camera: Map<String, Int>? = null
)
