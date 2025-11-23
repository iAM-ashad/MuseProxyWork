package com.iamashad.musesample.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel
) {
    val connectionState by viewModel.connectivityStatus.collectAsState()
    val devices by viewModel.devices.collectAsState()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var showNoDeviceDialog by remember { mutableStateOf(false) }
    var navigateAfterConnect by remember { mutableStateOf(false) }
    var isConnectingForRecording by remember { mutableStateOf(false) }

    // Initial scan
    LaunchedEffect(Unit) {
        viewModel.refreshDevices()
    }

    // When we are waiting to connect and state flips to Connected → go to Recording
    LaunchedEffect(connectionState, navigateAfterConnect) {
        if (navigateAfterConnect && connectionState is ConnectivityStatus.Connected) {
            // Small delay so the UI has time to show "Connected"
            delay(150)
            navigateAfterConnect = false
            isConnectingForRecording = false
            navController.navigate("record")
        }
    }

    fun onStartRecordingPressed() {
        when (connectionState) {
            is ConnectivityStatus.Connected -> {
                navController.navigate("record")
            }

            is ConnectivityStatus.Connecting -> {
                // already trying to connect; just mark that we want to go to recording when done
                navigateAfterConnect = true
                isConnectingForRecording = true
            }

            is ConnectivityStatus.Disconnected -> {
                // Make sure we have the latest device list
                viewModel.refreshDevices()
                val available = devices.filter { it.available }
                if (available.isEmpty()) {
                    showNoDeviceDialog = true
                } else if (available.size == 1) {
                    // Auto-pick the only available device
                    val device = available.first()
                    navigateAfterConnect = true
                    isConnectingForRecording = true
                    viewModel.connectTo(device)
                } else {
                    // Let the user pick a device from a simple list
                    showDeviceDialog = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("TAAL Stethoscope") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // Connection status card
            ConnectionStatusCard(
                state = connectionState,
                isConnectingForRecording = isConnectingForRecording,
                onDisconnect = {
                    viewModel.disconnect()
                    navigateAfterConnect = false
                    isConnectingForRecording = false
                }
            )

            Spacer(Modifier.height(24.dp))

            // Primary actions
            StartRecordingButton(onClick = ::onStartRecordingPressed)

            OutlinedButton(
                onClick = { navController.navigate("sessions") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("View sessions")
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "Connect the TAAL device via USB and tap “Start recording”.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "© 2025 MUSE Diagnostics",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = devices,
            onRefresh = { viewModel.refreshDevices() },
            onDismiss = { showDeviceDialog = false },
            onSelect = { device ->
                showDeviceDialog = false
                navigateAfterConnect = true
                isConnectingForRecording = true
                viewModel.connectTo(device)
            }
        )
    }

    if (showNoDeviceDialog) {
        NoDeviceDialog(
            onDismiss = { showNoDeviceDialog = false },
            onRetry = {
                showNoDeviceDialog = false
                viewModel.refreshDevices()
            }
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    state: ConnectivityStatus,
    isConnectingForRecording: Boolean,
    onDisconnect: () -> Unit
) {
    val (label, detail) = when (state) {
        is ConnectivityStatus.Disconnected ->
            "No device connected" to "Connect the TAAL device via USB."

        is ConnectivityStatus.Connecting ->
            "Connecting…" to "Please wait while we connect to the device."

        is ConnectivityStatus.Connected ->
            "Connected to ${state.deviceName}" to "You can start a new recording."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state is ConnectivityStatus.Connected) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            } else if (state is ConnectivityStatus.Connecting && isConnectingForRecording) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StartRecordingButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text("Start recording")
    }
}

/**
 * Simple device selection dialog.
 * - Lists all known devices with availability text.
 * - Keeps your refresh + connect behavior.
 */
@Composable
private fun DeviceSelectionDialog(
    devices: List<UiDevice>,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (UiDevice) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (devices.isEmpty()) {
                    Text(
                        "No USB devices detected.\nConnect the TAAL device and tap Refresh.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(devices, key = { it.id }) { device ->
                            DeviceRow(device = device, onSelect = onSelect)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) {
                Text("Refresh")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeviceRow(
    device: UiDevice,
    onSelect: (UiDevice) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        TextButton(
            onClick = { if (device.available) onSelect(device) },
            enabled = device.available,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                val statusText = if (device.available) "Available" else "Unavailable"
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NoDeviceDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No device found") },
        text = {
            Text(
                "No TAAL device is currently detected.\n" +
                        "Connect the device via USB and tap Retry.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
