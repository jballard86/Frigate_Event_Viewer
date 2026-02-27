package com.example.frigateeventviewer.data.model

/**
 * Response for GET /stats.
 * Property names match API contract §3.1 (snake_case for Gson).
 */
data class StatsResponse(
    val events: StatsEvents? = null,
    val storage: StatsStorage? = null,
    val errors: List<SystemError> = emptyList(),
    val last_cleanup: LastCleanup? = null,
    val most_recent: MostRecent? = null,
    val system: StatsSystem? = null,
    val ha_helpers: HaHelpers? = null
)
