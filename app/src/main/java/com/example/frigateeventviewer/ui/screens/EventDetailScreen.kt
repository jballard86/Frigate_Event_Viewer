package com.example.frigateeventviewer.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.ui.util.EventMediaPath
import com.example.frigateeventviewer.ui.util.buildMediaUrl
import com.example.frigateeventviewer.ui.util.SwipeBackBox
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    selectedEvent: Event?,
    onBack: () -> Unit,
    eventListLabel: String? = null,
    onCycleEvent: ((Int) -> Unit)? = null,
    onEventActionCompleted: (markedReviewedEventId: String?, deletedEventId: String?, advanceFromCurrent: Boolean) -> Unit = { _, _, _ -> },
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
        when (val opState = operationState) {
            is EventDetailOperationState.Success -> when (opState.action) {
                EventDetailAction.DELETE -> {
                    onEventActionCompleted(null, selectedEvent?.event_id, false)
                    onBack()
                }
                EventDetailAction.KEEP -> {
                    onEventActionCompleted(null, null, true)
                    viewModel.resetOperationState()
                }
                EventDetailAction.MARK_VIEWED -> {
                    onEventActionCompleted(selectedEvent?.event_id, null, false)
                    snackbarHostState.showSnackbar("Marked as reviewed")
                    viewModel.resetOperationState()
                }
            }
            is EventDetailOperationState.Error -> {
                snackbarHostState.showSnackbar(opState.message)
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    val baseTitle = "Event Detail"
    val fullTitle = eventListLabel?.let { "$baseTitle - $it" } ?: baseTitle

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fullTitle) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val cycleThresholdDp = 50.dp
                val density = LocalDensity.current
                val cycleThresholdPx = with(density) { cycleThresholdDp.toPx() }
                val verticalSwipeModifier =
                    if (onCycleEvent != null) {
                        Modifier.pointerInput(event.event_id, cycleThresholdPx) {
                            awaitPointerEventScope {
                                var totalDragX = 0f
                                var totalDragY = 0f
                                var triggered = false

                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    when (ev.type) {
                                        PointerEventType.Press -> {
                                            totalDragX = 0f
                                            totalDragY = 0f
                                            triggered = false
                                        }
                                        PointerEventType.Move -> {
                                            if (triggered) {
                                                ev.changes.forEach { it.consume() }
                                                continue
                                            }
                                            var deltaX = 0f
                                            var deltaY = 0f
                                            ev.changes.forEach { change ->
                                                deltaX += change.position.x - change.previousPosition.x
                                                deltaY += change.position.y - change.previousPosition.y
                                            }
                                            totalDragX += deltaX
                                            totalDragY += deltaY
                                            if (totalDragY < -cycleThresholdPx &&
                                                abs(totalDragY) > abs(totalDragX)
                                            ) {
                                                triggered = true
                                                onCycleEvent(1)
                                                ev.changes.forEach { it.consume() }
                                            } else if (totalDragY > cycleThresholdPx &&
                                                abs(totalDragY) > abs(totalDragX)
                                            ) {
                                                triggered = true
                                                onCycleEvent(-1)
                                                ev.changes.forEach { it.consume() }
                                            }
                                        }
                                        else -> {
                                            totalDragX = 0f
                                            totalDragY = 0f
                                            triggered = false
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                Box(modifier = Modifier.fillMaxWidth().then(verticalSwipeModifier)) {
                    EventVideoSection(
                        event = event,
                        baseUrl = baseUrl
                    )
                }
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
}

@Composable
private fun EventVideoSection(
    event: Event,
    baseUrl: String?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var videoAspectRatio by remember(event.camera, event.subdir) { mutableStateOf(16f / 9f) }

    val clipCandidateUrls = remember(event, baseUrl) {
        EventMediaPath.getClipPathCandidates(event).mapNotNull { buildMediaUrl(baseUrl, it) }.distinct()
    }
    val placeholderCandidateUrls = remember(event, baseUrl) {
        EventMediaPath.getPlaceholderPathCandidates(event).mapNotNull { buildMediaUrl(baseUrl, it) }.distinct()
    }

    val videoModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(videoAspectRatio)
        .clip(RoundedCornerShape(12.dp))

    if (clipCandidateUrls.isEmpty()) {
        if (placeholderCandidateUrls.isNotEmpty()) {
            EventPlaceholderImage(
                candidateUrls = placeholderCandidateUrls,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No video or image available for this event",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val firstClipUrl = clipCandidateUrls.first()
    val player = remember(event.camera, event.subdir, firstClipUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(firstClipUrl.toUri()))
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

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

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val h = videoSize.height
                if (h > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / h
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                controllerShowTimeoutMs = 1000
                this.player = player
            }
        },
        update = { playerView ->
            playerView.player = player
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        },
        modifier = videoModifier,
        onRelease = { playerView ->
            playerView.player = null
        }
    )
}

/**
 * Tries each candidate URL in order; shows first successful image or "No preview" when all fail.
 */
@Composable
private fun EventPlaceholderImage(
    candidateUrls: List<String>,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableStateOf(0) }
    val url = candidateUrls.getOrNull(currentIndex)
    val imageRequest = url?.let {
        remember(it) { ImageRequest.Builder(context).data(it).build() }
    }

    if (url != null && currentIndex < candidateUrls.size && imageRequest != null) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Event snapshot",
            modifier = modifier,
            contentScale = contentScale,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    if (currentIndex + 1 < candidateUrls.size) {
                        currentIndex += 1
                    } else {
                        currentIndex = candidateUrls.size
                    }
                }
            }
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No video or image available for this event",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
            colors = ButtonDefaults.buttonColors(
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
            colors = ButtonDefaults.buttonColors(
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
            colors = ButtonDefaults.buttonColors(
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
        event.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.getDefault())
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
