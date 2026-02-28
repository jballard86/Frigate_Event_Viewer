package com.example.frigateeventviewer.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.frigateeventviewer.data.model.SnoozeEntry
import com.example.frigateeventviewer.ui.util.SwipeBackBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeScreen(
    onBack: () -> Unit,
    viewModel: SnoozeViewModel = viewModel(
        factory = SnoozeViewModelFactory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val state by viewModel.state.collectAsState()
    val selectedPresetIndex by viewModel.selectedPresetIndex.collectAsState()
    val snoozeNotifications by viewModel.snoozeNotifications.collectAsState()
    val snoozeAi by viewModel.snoozeAi.collectAsState()
    val selectedCamera by viewModel.selectedCamera.collectAsState()
    val operationInProgress by viewModel.operationInProgress.collectAsState()
    val view = LocalView.current

    Scaffold(
        modifier = Modifier.fillMaxWidth(),
        topBar = {
            TopAppBar(
                title = { Text("Snooze") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        SwipeBackBox(
            enabled = true,
            onBack = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (val s = state) {
                    is SnoozeState.Loading -> {
                        Text(
                            "Loading…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is SnoozeState.Error -> {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.load() }) {
                            Text("Retry")
                        }
                    }
                    is SnoozeState.Ready -> {
                        PresetChips(
                            selectedIndex = selectedPresetIndex,
                            onSelect = { index ->
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                viewModel.setSelectedPresetIndex(index)
                            }
                        )

                        NotificationSnoozeToggle(
                            checked = snoozeNotifications,
                            onCheckedChange = viewModel::setSnoozeNotifications
                        )

                        AiSnoozeToggle(
                            checked = snoozeAi,
                            onCheckedChange = viewModel::setSnoozeAi
                        )

                        CameraList(
                            cameras = s.cameras,
                            activeSnoozes = s.activeSnoozes,
                            selectedCamera = selectedCamera,
                            onSelectCamera = viewModel::setSelectedCamera,
                            onSnooze = viewModel::setSnooze,
                            onClearSnooze = viewModel::clearSnooze,
                            operationInProgress = operationInProgress
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChips(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Duration",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SNOOZE_PRESETS.forEachIndexed { index, minutes ->
                val label = when (minutes) {
                    30 -> "30m"
                    60 -> "1h"
                    120 -> "2h"
                    else -> "${minutes}m"
                }
                FilterChip(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    label = { Text(label, maxLines = 1) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun NotificationSnoozeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Notification Snooze",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Mutes push notifications for this camera.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AiSnoozeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "AI Snooze",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Mutes AI analysis but keeps basic motion alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CameraList(
    cameras: List<String>,
    activeSnoozes: Map<String, SnoozeEntry>,
    selectedCamera: String?,
    onSelectCamera: (String?) -> Unit,
    onSnooze: (String) -> Unit,
    onClearSnooze: (String) -> Unit,
    operationInProgress: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Cameras",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (cameras.isEmpty()) {
            Text(
                "No cameras",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            cameras.forEach { camera ->
                val entry = activeSnoozes[camera]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedCamera == camera) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                camera,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (entry != null) {
                                Text(
                                    "Snoozed until ${formatExpiration(entry.expiration_time)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSelectCamera(if (selectedCamera == camera) null else camera) },
                                modifier = Modifier.height(40.dp).weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !operationInProgress
                            ) {
                                Text(
                                    if (selectedCamera == camera) "Selected" else "Select",
                                    maxLines = 1
                                )
                            }
                            if (entry != null) {
                                Button(
                                    onClick = { onClearSnooze(camera) },
                                    modifier = Modifier.height(40.dp).weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !operationInProgress
                                ) {
                                    Text("Clear", maxLines = 1)
                                }
                            } else {
                                Button(
                                    onClick = { onSnooze(camera) },
                                    modifier = Modifier.height(40.dp).weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !operationInProgress
                                ) {
                                    Text("Snooze", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatExpiration(expirationTime: Double): String {
    val millis = (expirationTime * 1000).toLong()
    val now = System.currentTimeMillis()
    val diff = millis - now
    return when {
        diff <= 0 -> "expired"
        diff < 60_000 -> "${(diff / 1000)}s"
        diff < 3600_000 -> "${(diff / 60_000)}m"
        else -> "${(diff / 3600_000)}h"
    }
}
