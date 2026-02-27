package com.example.frigateeventviewer.data.model

/**
 * Response for GET /api/daily-review/current and GET /api/daily-review/&lt;date_str&gt;.
 * Contract §4.2, §4.3: JSON body has a single "summary" field containing markdown.
 */
data class DailyReviewResponse(
    val summary: String = ""
)
