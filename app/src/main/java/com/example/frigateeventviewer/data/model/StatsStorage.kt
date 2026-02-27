package com.example.frigateeventviewer.data.model

/**
 * Storage section in GET /stats response.
 * Property names match API contract §3.1 (snake_case for Gson).
 */
data class StatsStorage(
    val total_display: StorageValue? = null,
    val by_camera: Map<String, StorageValue>? = null,
    val breakdown: StorageBreakdown? = null
)
