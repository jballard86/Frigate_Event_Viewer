package com.example.frigateeventviewer.data.model

/**
 * Last cleanup info in GET /stats. Nullable per contract §3.1.
 * Property names match API contract (snake_case for Gson).
 */
data class LastCleanup(
    val at: String,
    val deleted: Int
)
