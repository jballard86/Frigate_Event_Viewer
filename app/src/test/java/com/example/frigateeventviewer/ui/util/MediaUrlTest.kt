package com.example.frigateeventviewer.ui.util

import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Unit tests for [buildMediaUrl].
 */
class MediaUrlTest {

    @Test
    fun buildMediaUrl_validBaseUrlAndPath_returnsConcatenatedUrl() {
        val baseUrl = "http://host/"
        val path = "/path/snapshot.jpg"

        val result = buildMediaUrl(baseUrl, path)

        assertEquals("http://host/path/snapshot.jpg", result)
    }

    @Test
    fun buildMediaUrl_nullBaseUrl_returnsNull() {
        val result = buildMediaUrl(null, "/path")

        assertNull(result)
    }

    @Test
    fun buildMediaUrl_blankPath_returnsNull() {
        val result = buildMediaUrl("http://host/", "  ")

        assertNull(result)
    }
}
