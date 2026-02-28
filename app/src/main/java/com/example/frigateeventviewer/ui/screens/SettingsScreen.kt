package com.example.frigateeventviewer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.frigateeventviewer.ui.util.SwipeBackBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToDashboard: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val urlInput by viewModel.urlInput.collectAsState()
    val connectionTestState by viewModel.connectionTestState.collectAsState()
    val landscapeTabIconAlpha by viewModel.landscapeTabIconAlpha.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(connectionTestState) {
        when (val state = connectionTestState) {
            is ConnectionTestState.Success -> {
                snackbarHostState.showSnackbar("Connected successfully")
                viewModel.clearConnectionTestState()
            }
            is ConnectionTestState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearConnectionTestState()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("Settings") },
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
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        SwipeBackBox(
            enabled = onBack != null,
            onBack = onBack ?: {},
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Text(
            text = "Enter your Frigate Event Buffer server URL (e.g. http://192.168.1.50:5000)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = urlInput,
            onValueChange = viewModel::updateUrlInput,
            label = { Text("Base URL") },
            placeholder = { Text("http://192.168.1.50:5000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = connectionTestState !is ConnectionTestState.Loading
        )
        Button(
            onClick = {
                viewModel.saveBaseUrl(onSaved = onNavigateToDashboard)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = connectionTestState !is ConnectionTestState.Loading
        ) {
            Text("Save")
        }
        Button(
            onClick = { viewModel.testConnection() },
            modifier = Modifier.fillMaxWidth(),
            enabled = connectionTestState !is ConnectionTestState.Loading
        ) {
            if (connectionTestState is ConnectionTestState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp).fillMaxWidth(),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Test connection")
            }
        }
        Text(
            text = "Landscape tab bar icon transparency",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Slider(
            value = landscapeTabIconAlpha,
            onValueChange = { viewModel.setLandscapeTabIconAlpha(it) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
        }
        }
    }
}
