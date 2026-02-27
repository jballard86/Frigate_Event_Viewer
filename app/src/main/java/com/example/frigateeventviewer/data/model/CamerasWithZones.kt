package com.example.frigateeventviewer.data.model

/**
 * Cameras and zones entry in [Event.cameras_with_zones].
 * Property names match API contract §1.11 (snake_case for Gson).
 */
data class CamerasWithZones(
    val camera: String,
    val zones: List<String> = emptyList()
)
