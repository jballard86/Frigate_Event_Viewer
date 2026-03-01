package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.FrigateEventViewerApplication
import com.example.frigateeventviewer.data.Go2RtcStreamsState
import com.example.frigateeventviewer.data.Go2RtcStreamsRepository
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the Live tab (go2rtc streams).
 */
sealed class LiveState {
    data object Loading : LiveState()
    data class Success(val streamNames: List<String>) : LiveState()
    data class Error(val message: String) : LiveState()
}

/**
 * ViewModel for the Live tab. Uses shared [Go2RtcStreamsRepository] state; no per-tab fetch.
 */
class LiveViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)
    private val go2RtcRepository = (application as? FrigateEventViewerApplication)?.go2RtcStreamsRepository

    /** Maps shared repository state to LiveState for UI (Error message for Unavailable). */
    val state: StateFlow<LiveState> = (go2RtcRepository?.state ?: MutableStateFlow(Go2RtcStreamsState.Unavailable(message = null)).asStateFlow())
        .map { repoState ->
            when (repoState) {
                is Go2RtcStreamsState.Loading -> LiveState.Loading
                is Go2RtcStreamsState.Success -> LiveState.Success(repoState.streamNames)
                is Go2RtcStreamsState.Unavailable -> LiveState.Error(repoState.message ?: "Set Frigate IP in Settings")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LiveState.Loading
        )

    private val _selectedStreamName = MutableStateFlow<String?>(null)
    val selectedStreamName: StateFlow<String?> = _selectedStreamName.asStateFlow()

    /** Stream names for the dropdown: default camera (from Settings) first, then the rest. Same items as repository, reordered. */
    private val _displayStreamNames = MutableStateFlow<List<String>>(emptyList())
    val displayStreamNames: StateFlow<List<String>> = _displayStreamNames.asStateFlow()

    init {
        viewModelScope.launch {
            go2RtcRepository?.state?.collect { repoState ->
                if (repoState is Go2RtcStreamsState.Success) {
                    val names = repoState.streamNames
                    val savedDefault = preferences.getDefaultLiveCameraOnce()
                    _selectedStreamName.value = when {
                        !savedDefault.isNullOrBlank() && names.contains(savedDefault) -> savedDefault
                        names.isNotEmpty() -> names[0]
                        else -> null
                    }
                    _displayStreamNames.value = if (!savedDefault.isNullOrBlank() && savedDefault in names) {
                        listOf(savedDefault) + names.filter { it != savedDefault }
                    } else {
                        names
                    }
                }
            }
        }
    }

    /** Re-fetches go2rtc streams (shared cache). Called on Retry. */
    fun refresh() {
        viewModelScope.launch {
            go2RtcRepository?.refresh()
        }
    }

    fun setSelectedStreamName(name: String?) {
        _selectedStreamName.value = name
    }
}

/**
 * Factory for [LiveViewModel] so it receives [Application].
 */
class LiveViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LiveViewModel::class.java)) {
            return LiveViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}

/**
 * Live tab screen: "Select Camera" dropdown (from go2rtc streams) and "Coming soon" body.
 * Styling matches other main tab screens (16.dp padding, 12.dp shapes). Per UI_MAP.
 */
@Composable
fun LiveScreen(
    currentPage: Int,
    pageIndex: Int,
    viewModel: LiveViewModel = viewModel(
        factory = LiveViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val state by viewModel.state.collectAsState()
    val selectedStreamName by viewModel.selectedStreamName.collectAsState()
    val displayStreamNames by viewModel.displayStreamNames.collectAsState()
    var dropdownExpanded by remember { mutableStateOf(false) }
    var dropdownWidthDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val dropdownLabel = when (val s = state) {
        is LiveState.Loading -> "Loading…"
        is LiveState.Success -> selectedStreamName ?: displayStreamNames.firstOrNull() ?: "No cameras"
        is LiveState.Error -> "No camera"
    }
    val isDropdownEnabled = state is LiveState.Success

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select Camera",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coordinates ->
                        dropdownWidthDp = with(density) { coordinates.size.width.toDp() }
                    }
            ) {
                OutlinedTextField(
                    value = dropdownLabel,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                if (isDropdownEnabled) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { dropdownExpanded = true }
                    )
                }
                if (state is LiveState.Success) {
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = if (dropdownWidthDp > 0.dp) Modifier.width(dropdownWidthDp) else Modifier,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (displayStreamNames.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No cameras") },
                                onClick = { dropdownExpanded = false }
                            )
                        } else {
                            displayStreamNames.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        viewModel.setSelectedStreamName(name)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is LiveState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    Text(
                        text = "Coming soon",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
