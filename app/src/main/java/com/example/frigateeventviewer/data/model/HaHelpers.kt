package com.example.frigateeventviewer.data.model

/**
 * HA helpers section in GET /stats. Optional per contract §3.1.
 * Property names match API contract (snake_case for Gson).
 */
data class HaHelpers(
    val gemini_month_cost: Double? = null,
    val gemini_month_tokens: Int? = null
)
