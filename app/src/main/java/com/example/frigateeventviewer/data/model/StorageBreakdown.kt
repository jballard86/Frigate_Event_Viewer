package com.example.frigateeventviewer.data.model

/**
 * Storage breakdown (clips, snapshots, descriptions) in stats.
 * Property names match API contract §3.1 (snake_case for Gson).
 */
data class StorageBreakdown(
    val clips: StorageValue? = null,
    val snapshots: StorageValue? = null,
    val descriptions: StorageValue? = null
)
