package com.example.frigateeventviewer.data.model

/**
 * GenAI entry in [Event.genai_entries].
 * Property names match API contract §1.11 (snake_case for Gson).
 */
data class GenAiEntry(
    val title: String? = null,
    val scene: String? = null,
    val shortSummary: String? = null,
    val time: String? = null,
    val potential_threat_level: Int? = null
)
