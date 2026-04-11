package com.example.frigateeventviewer.data.model

/**
 * Response for GET /api/daily-review/dates.
 * Contract §4.1: JSON body has a "dates" array of YYYY-MM-DD strings, newest first.
 */
data class DailyReviewDatesResponse(
    val dates: List<String> = emptyList(),
)
