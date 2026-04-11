package com.example.frigateeventviewer.data.model

/**
 * Response for POST /viewed/all. Contract §1.8.
 */
data class MarkAllViewedResponse(
    val status: String,
    val marked: Int
)
