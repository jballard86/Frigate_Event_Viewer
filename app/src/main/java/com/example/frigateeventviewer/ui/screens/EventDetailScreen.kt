package com.example.frigateeventviewer.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.ui.util.buildMediaUrl
import com.example.frigateeventviewer.ui.util.SwipeBackBox
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    selectedEvent: Event?,
    onBack: () -> Unit,
    viewModel: EventDetailViewModel = viewModel<EventDetailViewModel>(
        factory = EventDetailViewModelFactory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val baseUrl by viewModel.baseUrl.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(operationState) {
        when (val state = operationState) {
            is EventDetailOperationState.Success -> when (state.action) {
                EventDetailAction.DELETE, EventDetailAction.KEEP -> onBack()
                EventDetailAction.MARK_VIEWED -> {
                    snackbarHostState.showSnackbar("Marked as reviewed")
                    viewModel.resetOperationState()
                }
            }
            is EventDetailOperationState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        SwipeBackBox(
            enabled = true,
            onBack = onBack,
            modifier = Modifier.fillMaxSize()
        ) {
        if (selectedEvent == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "No event selected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onBack) { Text("Back") }
                }
            }
        } else {
        val event = selectedEvent
        val eventPath = "${event.camera}/${event.subdir}"
        val isOperationLoading = operationState is EventDetailOperationState.Loading

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            EventVideoSection(
                event = event,
                baseUrl = baseUrl
            )
            EventActionsSection(
                eventPath = eventPath,
                saved = event.saved == true,
                isLoading = isOperationLoading,
                onMarkReviewed = { viewModel.markViewed(eventPath) },
                onKeep = { viewModel.keepEvent(eventPath) },
                onDelete = { viewModel.deleteEvent(eventPath) }
            )
            EventMetadataSection(event = event)
        }
        }
        }
    }
}

@Composable
private fun EventVideoSection(
    event: Event,
    baseUrl: String?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val clipPath = event.hosted_clip?.takeIf { it.isNotBlank() }
        ?: event.hosted_clips.firstOrNull()?.url
    val clipUrl = buildMediaUrl(baseUrl, clipPath)

        if (clipUrl == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No clip available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

    val player = remember(event.camera, event.subdir, clipPath) {
        buildMediaUrl(baseUrl, clipPath)?.let { url ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                prepare()
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false
            }
        }
    }

    if (player != null) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, lifecycleEvent ->
                when (lifecycleEvent) {
                    Lifecycle.Event.ON_PAUSE -> player.pause()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                player.release()
            }
        }

        AndroidView(
            factory = {
                PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                    controllerShowTimeoutMs = 1000
                    this.player = player
                }
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
            onRelease = { playerView ->
                playerView.player = null
            }
        )
    }
}

@Composable
private fun EventActionsSection(
    eventPath: String,
    saved: Boolean,
    isLoading: Boolean,
    onMarkReviewed: () -> Unit,
    onKeep: () -> Unit,
    onDelete: () -> Unit
) {
    val actionShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onDelete,
            enabled = !isLoading,
            modifier = Modifier.weight(1f).height(40.dp),
            shape = actionShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Delete", maxLines = 1)
        }
        Button(
            onClick = onMarkReviewed,
            enabled = !isLoading,
            modifier = Modifier.weight(1.4f).height(40.dp),
            shape = actionShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Mark Reviewed", maxLines = 1)
        }
        Button(
            onClick = onKeep,
            enabled = !isLoading && !saved,
            modifier = Modifier.weight(1f).height(40.dp),
            shape = actionShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text(if (saved) "Saved" else "Keep", maxLines = 1)
        }
    }
}

@Composable
private fun EventMetadataSection(event: Event) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        event.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
        event.scene?.let { scene ->
            Text(
                text = scene,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Camera: ${formatCameraName(event.camera)}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Date: ${formatTimestamp(event.timestamp)}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Threat level: ${event.threat_level}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatTimestamp(timestamp: String): String {
    val seconds = timestamp.toLongOrNull() ?: 0L
    val instant = Instant.ofEpochSecond(seconds)
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

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
