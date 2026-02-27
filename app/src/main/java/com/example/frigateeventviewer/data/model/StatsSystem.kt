package com.example.frigateeventviewer.data.model

/**
 * System section in GET /stats response.
 * Property names match API contract §3.1 (snake_case for Gson).
 * Class named StatsSystem to avoid clashing with java.lang.System.
 */
data class StatsSystem(
    val uptime_seconds: Long = 0L,
    val mqtt_connected: Boolean = false,
    val active_events: Int = 0,
    val retention_days: Int = 0,
    val cleanup_interval_hours: Int = 0,
    val storage_path: String? = null,
    val stats_refresh_seconds: Int = 0
)
