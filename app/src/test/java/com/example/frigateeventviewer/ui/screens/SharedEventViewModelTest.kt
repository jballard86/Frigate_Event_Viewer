package com.example.frigateeventviewer.ui.screens

import com.example.frigateeventviewer.data.model.Event
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [SharedEventViewModel].
 * Setup → Execute → Verify; no complex logic in tests.
 */
class SharedEventViewModelTest {

    @Test
    fun selectEvent_setsSelectedEvent() = runTest {
        val viewModel = SharedEventViewModel()
        val event = Event(
            event_id = "id1",
            camera = "front_door",
            subdir = "1739123456_abc",
            timestamp = "1739123456"
        )

        viewModel.selectEvent(event)
        val result = viewModel.selectedEvent.first()

        assertEquals(event, result)
    }

    @Test
    fun selectEvent_null_clearsSelection() = runTest {
        val viewModel = SharedEventViewModel()
        val event = Event(
            event_id = "id1",
            camera = "cam",
            subdir = "sub",
            timestamp = "0"
        )
        viewModel.selectEvent(event)
        viewModel.selectEvent(null)
        val result = viewModel.selectedEvent.first()

        assertNull(result)
    }

    @Test
    fun initialSelectedEvent_isNull() = runTest {
        val viewModel = SharedEventViewModel()

        val result = viewModel.selectedEvent.first()

        assertNull(result)
    }
}
