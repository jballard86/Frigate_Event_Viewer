package com.example.frigateeventviewer.data.model

/**
 * Response for GET /cameras. Contract §1.1.
 */
data class CamerasResponse(
    val cameras: List<String> = emptyList(),
    val default: String? = null
)
