package com.example.frigateeventviewer.data.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for unread-event state shared by the app icon badge and the events list.
 *
 * Holds the last fetched server unread count and the set of event IDs the user has marked as
 * reviewed (or deleted) locally. The badge uses [effectiveUnreadCount]; the events list uses
 * [locallyMarkedReviewedEventIds] to filter the unreviewed list. No duplicated state.
 *
 * Application-scoped: use from MainActivity, NotificationActionReceiver, and EventsViewModel.
 * Thread-safe for concurrent updates.
 */
object UnreadState {

    private data class State(
        val lastFetchedUnreadCount: Int,
        val locallyMarkedReviewedEventIds: Set<String>
    )

    private val mutex = Mutex()
    private val _state = MutableStateFlow(State(0, emptySet()))
    private val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /** Event IDs the user has marked as reviewed (or removed on delete). Used by Events list to filter. */
    val locallyMarkedReviewedEventIds: StateFlow<Set<String>> = state
        .map { it.locallyMarkedReviewedEventIds }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Effective count for the badge: max(0, lastFetched - locallyMarkedSize). */
    val effectiveUnreadCount: StateFlow<Int> = state
        .map { (count, set) -> maxOf(0, count - set.size) }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /** Current effective count for synchronous use (e.g. badge apply). */
    fun currentEffectiveUnreadCount(): Int {
        val s = _state.value
        return maxOf(0, s.lastFetchedUnreadCount - s.locallyMarkedReviewedEventIds.size)
    }

    /** Called after fetching GET /api/events/unread_count (e.g. on resume). */
    suspend fun recordFetchedUnreadCount(count: Int) {
        mutex.withLock {
            _state.value = _state.value.copy(lastFetchedUnreadCount = count)
        }
    }

    /** Called when the user marks an event as reviewed (in-app or from notification). */
    suspend fun recordMarkedReviewed(eventId: String) {
        mutex.withLock {
            _state.value = _state.value.copy(
                locallyMarkedReviewedEventIds = _state.value.locallyMarkedReviewedEventIds + eventId
            )
        }
    }

    /** Called when the user deletes an event (remove from local set). */
    suspend fun recordDeleted(eventId: String) {
        mutex.withLock {
            _state.value = _state.value.copy(
                locallyMarkedReviewedEventIds = _state.value.locallyMarkedReviewedEventIds - eventId
            )
        }
    }

    /** Prunes the local set to only IDs that still exist on the server. Call from Events watchdog. */
    suspend fun pruneToExistingIds(existingIds: Set<String>) {
        mutex.withLock {
            _state.value = _state.value.copy(
                locallyMarkedReviewedEventIds = _state.value.locallyMarkedReviewedEventIds.filter { it in existingIds }.toSet()
            )
        }
    }
}
