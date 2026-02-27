package com.example.frigateeventviewer.ui.screens

import android.app.Application
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.ui.util.buildMediaUrl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EventsScreen(
    onEventClick: (Event) -> Unit,
    viewModel: EventsViewModel = viewModel(
        factory = EventsViewModelFactory(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val state by viewModel.state.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)
    ) {
        when (val s = state) {
            is EventsState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is EventsState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }
            is EventsState.Success -> {
                val events = s.response.events
                if (events.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No unreviewed events",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(0.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(events, key = { it.event_id }) { event ->
                            EventCard(
                                event = event,
                                baseUrl = baseUrl,
                                onClick = { onEventClick(event) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: Event,
    baseUrl: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnailPath = when {
        !event.hosted_snapshot.isNullOrBlank() -> event.hosted_snapshot
        else -> event.hosted_clip
    }
    val thumbnailUrl = buildMediaUrl(baseUrl, thumbnailPath)
    val imageRequest = thumbnailUrl?.let { url ->
        ImageRequest.Builder(context)
            .data(url)
            .build()
    }
    val formattedTime = formatTimestamp(event.timestamp)
    val cameraLabel = formatCameraName(event.camera)
    val threatColor = when (event.threat_level) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.tertiary
        2 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val threatIcon = when (event.threat_level) {
        0 -> Icons.Default.CheckCircle
        1 -> Icons.Default.Warning
        2 -> Icons.Default.Error
        else -> Icons.Default.CheckCircle
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = imageRequest!!,
                        contentDescription = "Event snapshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Error) {
                                Log.e(
                                    "CoilError",
                                    "Thumbnail failed: ${state.result.throwable.message}",
                                    state.result.throwable
                                )
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = cameraLabel,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    event.label?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Icon(
                        imageVector = threatIcon,
                        contentDescription = "Threat level ${event.threat_level}",
                        modifier = Modifier.size(16.dp),
                        tint = threatColor
                    )
                }
            }
        }
    }
}

/**
 * Formats Unix timestamp string to readable date/time using java.time.
 */
private fun formatTimestamp(timestamp: String): String {
    val seconds = timestamp.toLongOrNull() ?: 0L
    val instant = Instant.ofEpochSecond(seconds)
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

/**
 * Formats camera name for display (e.g. "front_door" -> "Front door").
 */
private fun formatCameraName(camera: String): String {
    return camera
        .replace('_', ' ')
        .split(' ')
        .joinToString(" ") { word ->
            word.replaceFirstChar { c ->
                if (c.isLowerCase()) c.uppercase() else c.toString()
            }
        }
}
