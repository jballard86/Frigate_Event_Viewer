package com.example.frigateeventviewer.data.model

/**
 * Response for POST /api/mobile/register.
 * Contract §6.1: 200 returns { "status": "success" }.
 */
data class RegisterDeviceResponse(
    val status: String = ""
)
