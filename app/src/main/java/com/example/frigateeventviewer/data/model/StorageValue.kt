package com.example.frigateeventviewer.data.model

/**
 * Value with unit (e.g. 2.5 GB). Used in stats storage totals and breakdown.
 * Property names match API contract §3.1 (snake_case for Gson).
 */
data class StorageValue(
    val value: Double,
    val unit: String
)
