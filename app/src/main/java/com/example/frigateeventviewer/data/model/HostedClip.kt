package com.example.frigateeventviewer.data.model

/**
 * Single clip entry in [Event.hosted_clips].
 * Property names match API contract §1.11 (snake_case for Gson).
 */
data class HostedClip(
    val camera: String,
    val url: String
)
