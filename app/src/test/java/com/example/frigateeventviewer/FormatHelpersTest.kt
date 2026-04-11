package com.example.frigateeventviewer

import com.example.frigateeventviewer.ui.util.parseTimestampToEpochSeconds
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatHelpersTest {

    @Test
    fun parseTimestampToEpochSeconds_integerString() {
        assertEquals(1700000000L, parseTimestampToEpochSeconds("1700000000"))
    }

    @Test
    fun parseTimestampToEpochSeconds_fractionalString() {
        assertEquals(1700000000L, parseTimestampToEpochSeconds("1700000000.9"))
    }

    @Test
    fun parseTimestampToEpochSeconds_blankReturnsZero() {
        assertEquals(0L, parseTimestampToEpochSeconds(""))
    }
}
