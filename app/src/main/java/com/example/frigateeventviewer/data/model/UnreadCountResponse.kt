package com.example.frigateeventviewer.data.model

/**
 * Response for GET /api/events/unread_count. Contract §7.1.
 */
data class UnreadCountResponse(
    val unread_count: Int = 0
)
