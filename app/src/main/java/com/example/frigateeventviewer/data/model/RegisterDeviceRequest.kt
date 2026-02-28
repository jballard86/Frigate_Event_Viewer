package com.example.frigateeventviewer.data.model

/**
 * Request body for POST /api/mobile/register.
 * Contract §6.1: JSON body has "token" (FCM device token).
 */
data class RegisterDeviceRequest(
    val token: String
)
