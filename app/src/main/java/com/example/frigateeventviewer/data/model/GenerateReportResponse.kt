package com.example.frigateeventviewer.data.model

/**
 * Response for POST /api/daily-review/generate.
 * Contract §4.4: 200 returns { "success": true, "date": "YYYY-MM-DD" }.
 */
data class GenerateReportResponse(
    val success: Boolean = false,
    val date: String? = null
)
