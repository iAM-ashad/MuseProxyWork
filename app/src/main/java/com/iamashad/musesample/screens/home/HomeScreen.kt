package com.iamashad.musesample.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.iamashad.musesample.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home screen:
 * - Shows connection status and allows connecting to a TAAL device.
 * - Entry points to "Start Session" and "Session History".
 * - Device picker is a bottom sheet with pull-to-refresh and grouping.
 *
 * Interaction flow:
 * 1) Tap the status indicator → opens device picker.
 * 2) Choose a device → show a permission explainer → request USB permission.
 * 3) ViewModel starts the monitor; banner updates live via a Flow.
 */

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel
) {
    val connectionState by viewModel.connectivityStatus.collectAsState()
    val devices by viewModel.devices.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshDevices() }

    var showPicker by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Pre-permission explainer and pending device
    var pendingDevice by remember { mutableStateOf<UiDevice?>(null) }
    var showUsbExplainer by remember { mutableStateOf(false) }

    // Disconnect confirmation
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.muse_logo),
                contentDescription = "Company Logo"
            )
        }

        // Status card (tap to connect/disconnect)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusIndicator(
                    state = connectionState,
                    onClick = {
                        when (connectionState) {
                            is ConnectivityStatus.Disconnected -> {
                                isRefreshing = true
                                viewModel.refreshDevices()
                                showPicker = true
                            }

                            is ConnectivityStatus.Connected -> {
                                showDisconnectConfirm = true
                            }

                            is ConnectivityStatus.Connecting -> Unit
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))
                ConnectionStateCard(connectionState)
                Spacer(Modifier.height(12.dp))

                Text(
                    text = when (connectionState) {
                        is ConnectivityStatus.Disconnected ->
                            "Tap the indicator to choose a device.\nPlug in your TAAL device to make it available."

                        is ConnectivityStatus.Connected ->
                            "Connected to ${(connectionState as ConnectivityStatus.Connected).deviceName}"

                        is ConnectivityStatus.Connecting -> "Connecting…"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Primary actions
        StartSessionButton(
            isEnabled = connectionState is ConnectivityStatus.Connected
        ) { navController.navigate("record") }

        FilledTonalButton(
            onClick = { navController.navigate("sessions") },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.history),
                contentDescription = "View Sessions",
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("View Sessions", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Text(
            text = "© 2025 MUSE Diagnostics",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
        )
    }

    // Device picker sheet
    if (showPicker) {
        DevicePickerSheet(
            devices = devices,
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refreshDevices()
            },
            onDismiss = { showPicker = false },
            onConnect = { device ->
                pendingDevice = device
                showPicker = false
                showUsbExplainer = true
            }
        )
    }

    // USB permission explainer sheet
    if (showUsbExplainer && pendingDevice != null) {
        UsbPermissionSheet(
            device = pendingDevice!!,
            onDismiss = { showUsbExplainer = false; pendingDevice = null },
            onContinue = {
                viewModel.connectTo(pendingDevice!!)
                showUsbExplainer = false
                pendingDevice = null
            }
        )
    }

    // Disconnect dialog
    if (showDisconnectConfirm) {
        DisconnectConfirmDialog(
            onDismiss = { showDisconnectConfirm = false },
            onConfirm = {
                viewModel.disconnect()
                showDisconnectConfirm = false
            }
        )
    }

    // Stop spinner when scan results arrive
    LaunchedEffect(devices) { isRefreshing = false }
}

/* ------- Bottom sheet + small UI helpers (unchanged logic, documented) ------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsbPermissionSheet(
    device: UiDevice,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        tonalElevation = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "USB access required",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Device summary
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconRes = when (device.kind) {
                    DeviceKind.STETHOSCOPE -> R.drawable.stethoscope
                    DeviceKind.ECG -> R.drawable.monitor_heart
                }
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    val vid = device.usbDevice?.vendorId?.toString() ?: "—"
                    val pid = device.usbDevice?.productId?.toString() ?: "—"
                    Text(
                        "VID $vid · PID $pid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                "To communicate with this device, Android will show a system dialog asking you to allow access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onContinue, modifier = Modifier.weight(1f)) { Text("Continue") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DisconnectConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect device?") },
        text = {
            Text(
                "You can reconnect anytime from the device picker.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DevicePickerSheet(
    devices: List<UiDevice>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onConnect: (UiDevice) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var availableOnly by remember { mutableStateOf(true) }
    var connectingId by remember { mutableStateOf<String?>(null) }
    var connectedId by remember { mutableStateOf<String?>(null) }

    val filtered = remember(devices, availableOnly) {
        devices
            .filter { if (availableOnly) it.available else true }
            .sortedWith(compareByDescending<UiDevice> { it.available }.thenBy { it.name.lowercase() })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetHandle() },
        tonalElevation = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        SheetTopBar(
            onClose = { scope.launch { sheetState.hide(); onDismiss() } },
            onScan = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRefresh()
            },
            isRefreshing = isRefreshing
        )

        SheetSearchRow(
            availableOnly = availableOnly,
            onToggleAvailable = { availableOnly = it }
        )

        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (filtered.isEmpty()) {
                EmptySheetState(isRefreshing = isRefreshing, onScan = onRefresh)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 560.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    val groups = listOf(
                        "Available" to filtered.filter { it.available },
                        "Unavailable" to filtered.filter { !it.available }
                    ).filter { it.second.isNotEmpty() }

                    groups.forEach { (title, groupItems) ->
                        stickyHeader { HeaderPill(title) }
                        items(groupItems, key = { it.name }) { device ->
                            DevicePickerRow(
                                device = device,
                                isConnecting = connectingId == device.name,
                                isConnected = connectedId == device.name,
                                onConnect = {
                                    if (!device.available) return@DevicePickerRow
                                    connectingId = device.name
                                    connectedId = null
                                    scope.launch {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        delay(250)
                                        onConnect(device)
                                        connectedId = device.name
                                        connectingId = null
                                        delay(250)
                                        sheetState.hide()
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun SheetTopBar(
    onClose: () -> Unit,
    onScan: () -> Unit,
    isRefreshing: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Select a Device",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        FilledTonalButton(
            onClick = onScan,
            enabled = !isRefreshing,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Scan")
            Spacer(Modifier.width(6.dp))
            Text(if (isRefreshing) "Scanning…" else "Scan")
        }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
    }
}

@Composable
private fun SheetSearchRow(
    availableOnly: Boolean,
    onToggleAvailable: (Boolean) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !availableOnly,
                onClick = { onToggleAvailable(false) },
                label = { Text("All") })
            FilterChip(
                selected = availableOnly,
                onClick = { onToggleAvailable(true) },
                label = { Text("Available") })
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HeaderPill(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun EmptySheetState(
    isRefreshing: Boolean,
    onScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Text("No devices found", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Connect your TAAL device via USB-C and scan again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onScan, enabled = !isRefreshing) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (isRefreshing) "Scanning…" else "Scan")
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DevicePickerRow(
    device: UiDevice,
    isConnecting: Boolean,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = device.available) { onConnect() }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconRes = when (device.kind) {
                DeviceKind.STETHOSCOPE -> R.drawable.stethoscope
                DeviceKind.ECG -> R.drawable.monitor_heart
            }
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = primary,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvailabilityDot(available = device.available)
                    Spacer(Modifier.width(6.dp))
                    val status = when {
                        isConnected -> "Connected"
                        isConnecting -> "Connecting…"
                        device.available -> "Available"
                        else -> "Unavailable"
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            AnimatedContent(
                targetState = Triple(device.available, isConnecting, isConnected),
                transitionSpec = {
                    (fadeIn(
                        tween(
                            150,
                            easing = FastOutSlowInEasing
                        )
                    ) + slideInVertically { it / 3 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                },
                label = "connect-anim"
            ) { (available, connecting, connected) ->
                when {
                    connected -> AssistChip(
                        onClick = {},
                        enabled = false,
                        leadingIcon = { Icon(Icons.Default.Check, null) },
                        label = { Text("Connected") })

                    connecting -> {
                        FilledTonalButton(onClick = {}, enabled = false) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Connecting…")
                        }
                    }

                    available -> {
                        Button(
                            onClick = onConnect,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Connect")
                        }
                    }

                    else -> {
                        OutlinedButton(onClick = {}, enabled = false) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Unavailable")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailabilityDot(available: Boolean) {
    val color = if (available) Color(0xFF1DB954) else Color(0xFFE53935)
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/* ------- Reused smaller pieces (unchanged logic, documented) ------- */

@Composable
fun ConnectionStateCard(state: ConnectivityStatus) {
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = when (state) {
            is ConnectivityStatus.Connected -> Color(0xFF4CAF50)
            is ConnectivityStatus.Connecting -> Color(0xFFFFC107)
            else -> Color(0xFFE53935)
        },
        label = "dotColor"
    )

    val label = when (state) {
        is ConnectivityStatus.Connected -> "Connected"
        is ConnectivityStatus.Connecting -> "Connecting..."
        else -> "Disconnected"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .padding(top = 4.dp)
            .height(36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun StatusIndicator(
    state: ConnectivityStatus,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(220.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is ConnectivityStatus.Disconnected -> {
                Image(
                    painter = painterResource(id = R.drawable.circle),
                    contentDescription = "Disconnected",
                    modifier = Modifier.fillMaxSize()
                )
            }

            is ConnectivityStatus.Connecting -> {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.connectivity_status))
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.fillMaxSize()
                )
            }

            is ConnectivityStatus.Connected -> {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.round),
                        contentDescription = "Connected",
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(pulse)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.stethoscope),
                        contentDescription = "Stethoscope",
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StartSessionButton(isEnabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = "Start Session",
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text("Start Session", fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
