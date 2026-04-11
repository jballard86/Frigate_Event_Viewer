package com.example.frigateeventviewer.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.ui.util.EventMediaPath
import com.example.frigateeventviewer.ui.util.buildMediaUrl
import com.example.frigateeventviewer.ui.util.formatCameraName
import com.example.frigateeventviewer.ui.util.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onEventClick: (Event) -> Unit,
    currentPage: Int,
    pageIndex: Int,
    sharedEventViewModel: SharedEventViewModel,
    viewModel: EventsViewModel
) {
    val state by viewModel.state.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val filterMode by viewModel.filterMode.collectAsState()
    val dropdownLabel = when (filterMode) {
        EventsFilterMode.Unreviewed -> "Unreviewed"
        EventsFilterMode.Reviewed -> "Reviewed"
        EventsFilterMode.Saved -> "Saved"
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var dropdownExpanded by remember { mutableStateOf(false) }
    var cameraDropdownExpanded by remember { mutableStateOf(false) }
    var dropdownWidthDp by remember { mutableStateOf(0.dp) }
    var cameraDropdownWidthDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val cameraFilter by viewModel.cameraFilter.collectAsState()
    val cameraNamesForFilter by viewModel.cameraNamesForFilter.collectAsState()
    val showEventsCameraFilter by viewModel.showEventsCameraFilter.collectAsState()
    val markAllViewedError by viewModel.markAllViewedError.collectAsState()

    LaunchedEffect(markAllViewedError) {
        val msg = markAllViewedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMarkAllViewedError()
    }

    LaunchedEffect(lifecycle, currentPage, pageIndex) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (currentPage == pageIndex) {
                viewModel.refresh(force = false)
            }
        }
    }

    val isLoading = state is EventsState.Loading
    val previousResponse = when (val s = state) {
        is EventsState.Loading -> s.previous
        is EventsState.Error -> s.previous
        is EventsState.Success -> null
    }
    val displayEvents by viewModel.displayedEvents.collectAsState()
    val showFullScreenSpinner = isLoading && previousResponse == null
    val showList = displayEvents.isNotEmpty() && !showFullScreenSpinner
    val showEmptyState = displayEvents.isEmpty() && !showFullScreenSpinner && state is EventsState.Success
    val showErrorState = state is EventsState.Error && previousResponse == null
    val showErrorBanner = state is EventsState.Error && previousResponse != null

    Box(modifier = Modifier.fillMaxSize()) {
    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = { viewModel.refresh(force = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(bottom = 8.dp)
                    .onGloballyPositioned { coordinates ->
                        dropdownWidthDp = with(density) { coordinates.size.width.toDp() }
                    }
            ) {
                OutlinedTextField(
                    value = dropdownLabel,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Filter events: $dropdownLabel",
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { dropdownExpanded = true }
                )
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = if (dropdownWidthDp > 0.dp) Modifier.width(dropdownWidthDp) else Modifier,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Unreviewed") },
                        onClick = {
                            viewModel.setFilterMode(EventsFilterMode.Unreviewed)
                            dropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reviewed") },
                        onClick = {
                            viewModel.setFilterMode(EventsFilterMode.Reviewed)
                            dropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Saved") },
                        onClick = {
                            viewModel.setFilterMode(EventsFilterMode.Saved)
                            dropdownExpanded = false
                        }
                    )
                }
            }
            if (showEventsCameraFilter) {
                val cameraTriggerLabel = cameraFilter ?: "All cameras"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(bottom = 8.dp)
                        .onGloballyPositioned { coordinates ->
                            cameraDropdownWidthDp = with(density) { coordinates.size.width.toDp() }
                        }
                ) {
                    OutlinedTextField(
                        value = cameraTriggerLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Filter by camera: $cameraTriggerLabel",
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { cameraDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = cameraDropdownExpanded,
                        onDismissRequest = { cameraDropdownExpanded = false },
                        modifier = if (cameraDropdownWidthDp > 0.dp) Modifier.width(cameraDropdownWidthDp) else Modifier,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All cameras") },
                            onClick = {
                                viewModel.setCameraFilter(null)
                                cameraDropdownExpanded = false
                            }
                        )
                        cameraNamesForFilter.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    viewModel.setCameraFilter(name)
                                    cameraDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            if (filterMode == EventsFilterMode.Unreviewed && showList) {
                Button(
                    onClick = { viewModel.markAllViewed() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Mark all reviewed", maxLines = 1)
                }
            }
            if (showFullScreenSpinner) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (showErrorState) {
                val err = state as EventsState.Error
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = err.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = { viewModel.refresh(force = true) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Retry")
                    }
                }
            } else if (showEmptyState) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyText = when (filterMode) {
                        EventsFilterMode.Unreviewed -> "No unreviewed events"
                        EventsFilterMode.Reviewed -> "No reviewed events"
                        EventsFilterMode.Saved -> "No saved events"
                    }
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (showList) {
                if (showErrorBanner) {
                    val err = state as EventsState.Error
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = err.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { viewModel.refresh(force = true) }) { Text("Retry") }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayEvents, key = { it.event_id }) { event ->
                        val item = eventToCardItem(event)
                        EventCard(
                            item = item,
                            baseUrl = baseUrl,
                            onClick = { onEventClick(event) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }
}

/**
 * Minimal, stable display model for a single event row. Used so EventCard receives only
 * primitives and strings, avoiding recomposition from unstable Event (List<> fields).
 */
private data class EventCardItem(
    val event_id: String,
    val thumbnailPathCandidates: List<String>,
    val formattedTime: String,
    val displayTitle: String,
    val threat_level: Int
)

private fun eventToCardItem(event: Event): EventCardItem = EventCardItem(
    event_id = event.event_id,
    thumbnailPathCandidates = EventMediaPath.getThumbnailPathCandidates(event),
    formattedTime = formatTimestamp(event.timestamp),
    displayTitle = event.title?.takeIf { it.isNotBlank() } ?: formatCameraName(event.camera),
    threat_level = event.threat_level
)

@Composable
private fun EventCard(
    item: EventCardItem,
    baseUrl: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { 80.dp.roundToPx() }
    val heightPx = with(density) { 60.dp.roundToPx() }
    val candidateUrls = remember(item.thumbnailPathCandidates, baseUrl) {
        item.thumbnailPathCandidates.mapNotNull { buildMediaUrl(baseUrl, it) }.distinct()
    }
    var currentUrlIndex by remember { mutableStateOf(0) }
    val thumbnailUrl = candidateUrls.getOrNull(currentUrlIndex)
    val imageRequest = thumbnailUrl?.let { url ->
        remember(url, widthPx, heightPx) {
            ImageRequest.Builder(context)
                .data(url)
                .size(widthPx, heightPx)
                .build()
        }
    }
    val cardColor = when (item.threat_level) {
        1 -> lerp(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.tertiaryContainer,
            0.25f
        )
        2 -> lerp(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.errorContainer,
            0.25f
        )
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                when {
                    candidateUrls.isEmpty() -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "No preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    currentUrlIndex >= candidateUrls.size -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "No preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    else -> {
                        AsyncImage(
                            model = imageRequest!!,
                            contentDescription = "Event snapshot",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onState = { imgState ->
                                if (imgState is AsyncImagePainter.State.Error) {
                                    Log.e(
                                        "CoilError",
                                        "Thumbnail failed: ${imgState.result.throwable.message}",
                                        imgState.result.throwable
                                    )
                                    if (currentUrlIndex + 1 < candidateUrls.size) {
                                        currentUrlIndex += 1
                                    } else {
                                        currentUrlIndex = candidateUrls.size
                                    }
                                }
                            }
                        )
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Text(
                    text = item.formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
