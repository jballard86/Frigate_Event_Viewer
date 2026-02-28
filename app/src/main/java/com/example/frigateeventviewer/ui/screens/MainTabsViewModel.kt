package com.example.frigateeventviewer.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Activity-scoped ViewModel that holds the selected main-tabs page index (Dashboard / Events / Daily Review).
 * Uses [SavedStateHandle] so the index survives configuration changes (e.g. rotation) regardless of
 * NavHost composition key handling.
 */
class MainTabsViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    /** Current tab index (0 = Dashboard, 1 = Events, 2 = Daily Review). Persisted in saved state. */
    val selectedTabIndex: StateFlow<Int> = savedStateHandle.getStateFlow(KEY_SELECTED_INDEX, 0)

    /** Updates the selected tab index; written to [SavedStateHandle] for rotation survival. */
    fun setSelectedTabIndex(index: Int) {
        savedStateHandle[KEY_SELECTED_INDEX] = index
    }

    companion object {
        private const val KEY_SELECTED_INDEX = "main_tabs_selected_index"
    }
}
