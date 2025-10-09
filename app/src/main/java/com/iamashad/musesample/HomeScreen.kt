package com.iamashad.musesample

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun HomeScreen(viewModel: ViewModel) {
    val connectionState by viewModel.connectivityStatus.collectAsState()
    var showDeviceDialog by remember { mutableStateOf(false) }

    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = viewModel.availableDevices,
            onDeviceSelected = { deviceName ->
                viewModel.connectToDevice(deviceName)
                showDeviceDialog = false
            },
            onDismiss = { showDeviceDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.muse_logo), // 👈 replace with your actual logo resource
                contentDescription = "Company Logo",
                modifier = Modifier

            )
        }

        // Connection Status Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
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
                            is ConnectivityStatus.Connected -> viewModel.disconnect()
                            else -> showDeviceDialog = true
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))

                // 🔴🟢 Small Connection Indicator Card
                ConnectionStateCard(connectionState)

                Spacer(Modifier.height(12.dp))

                Text(
                    text = when (connectionState) {
                        is ConnectivityStatus.Disconnected -> "Tap to connect your device"
                        is ConnectivityStatus.Connecting -> "Establishing connection..."
                        is ConnectivityStatus.Connected -> "Connected to ${viewModel.availableDevices[0]}"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Start session button
        StartSessionButton(
            isEnabled = connectionState is ConnectivityStatus.Connected
        ) {
            // TODO: Navigate to metadata / recording screen
        }

        // Footer
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
}

@Composable
fun ConnectionStateCard(state: ConnectivityStatus) {
    val color by animateColorAsState(
        targetValue = when (state) {
            is ConnectivityStatus.Connected -> Color(0xFF4CAF50) // Green
            is ConnectivityStatus.Connecting -> Color(0xFFFFC107) // Amber
            else -> Color(0xFFE53935) // Red
        },
        label = "dotColor"
    )

    val label = when (state) {
        is ConnectivityStatus.Connected -> "Connected"
        is ConnectivityStatus.Connecting -> "Connecting..."
        else -> "Disconnected"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
        ),
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

@Composable
fun DeviceSelectionDialog(
    devices: List<String>,
    onDeviceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                devices.forEach { deviceName ->
                    val icon = when {
                        "stethoscope" in deviceName.lowercase() -> Icons.Outlined.MedicalServices
                        "ecg" in deviceName.lowercase() -> Icons.Outlined.MonitorHeart
                        else -> null
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceSelected(deviceName) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                        }
                        Text(text = deviceName, fontSize = 18.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
