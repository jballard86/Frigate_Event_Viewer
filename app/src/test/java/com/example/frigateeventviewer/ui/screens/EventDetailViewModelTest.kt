package com.example.frigateeventviewer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [EventDetailAction] and [EventDetailOperationState].
 * Verifies the sealed class and enum used by [EventDetailViewModel] so the UI can branch on action type.
 */
class EventDetailViewModelTest {

    @Test
    fun successCarriesAction_delete() {
        val state = EventDetailOperationState.Success(EventDetailAction.DELETE)

        assertEquals(EventDetailAction.DELETE, state.action)
    }

    @Test
    fun successCarriesAction_keep() {
        val state = EventDetailOperationState.Success(EventDetailAction.KEEP)

        assertEquals(EventDetailAction.KEEP, state.action)
    }

    @Test
    fun successCarriesAction_markViewed() {
        val state = EventDetailOperationState.Success(EventDetailAction.MARK_VIEWED)

        assertEquals(EventDetailAction.MARK_VIEWED, state.action)
    }

    @Test
    fun errorCarriesMessage() {
        val state = EventDetailOperationState.Error("No server URL")

        assertEquals("No server URL", state.message)
    }
}
