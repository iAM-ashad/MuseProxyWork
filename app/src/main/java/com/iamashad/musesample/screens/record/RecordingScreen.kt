package com.iamashad.musesample.screens.record

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.iamashad.musesample.R
import com.iamashad.musesample.permissions.AppPermissions
import com.iamashad.musesample.wrapper.TaalSdkHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    vm: RecordingViewModel,
    onStopAndSave: (filteredPath: String?, rawPath: String?) -> Unit,
    onCancel: () -> Unit
) {
    val state by vm.state.collectAsState()

    val context = LocalContext.current
    val app = context.applicationContext as Application
    val wrapper = remember(app) { TaalSdkHolder.get(app) }
    val samples by wrapper.liveSamples.collectAsState()
    val sampleRate by wrapper.sampleRateHz.collectAsState()

    // Navigation effect when VM has saved + inserted DB row.
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is RecordingEffect.NavigateAfterSave -> {
                    onStopAndSave(effect.filteredPath, effect.rawPath)
                }
            }
        }
    }

    // Permission flow for RECORD_AUDIO.
    val activity = context as? Activity
    val micPermission = AppPermissions.RECORD_AUDIO
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, micPermission) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var showMicRationale by remember { mutableStateOf(false) }
    val requestMicPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        showMicRationale = !granted && activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, micPermission)
        } == true
        if (granted && (state == RecordingState.Idle || state == RecordingState.Complete)) {
            vm.toggleRecording()
        }
    }

    fun ensureMicPermissionThenStart() {
        if (hasMicPermission) {
            vm.toggleRecording()
        } else {
            val shouldExplain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, micPermission)
            } ?: false
            if (shouldExplain) showMicRationale = true
            else requestMicPermission.launch(micPermission)
        }
    }

    // User-selected options (duration/filter/gain).
    val selectedDuration by vm.selectedDurationSec.collectAsState()
    val selectedFilter by vm.preFilter.collectAsState()
    val selectedAmp by vm.preAmpDb.collectAsState()

    // Bottom sheet for auscultation reference
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showReference by remember { mutableStateOf(false) }

    // Timer purely for display; auto-stop is handled in the VM.
    var secondsElapsed by remember(state == RecordingState.Recording) { mutableIntStateOf(0) }

    LaunchedEffect(state == RecordingState.Recording) {
        if (state != RecordingState.Recording) {
            secondsElapsed = 0
            return@LaunchedEffect
        }
        secondsElapsed = 0
        while (isActive) {
            delay(1000)
            secondsElapsed++
        }
    }

    // Trigger to clear the waveform when a new recording starts
    var clearWaveformTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state == RecordingState.Recording) {
            clearWaveformTrigger = true
        }
    }
    LaunchedEffect(clearWaveformTrigger) {
        if (clearWaveformTrigger) {
            delay(100)
            clearWaveformTrigger = false
        }
    }

    // Status in top bar
    val (statusLabel, statusColor) = when (state) {
        RecordingState.Idle -> "Ready" to Color(0xFF2E7D32)
        RecordingState.Recording -> "Recording" to MaterialTheme.colorScheme.error
        RecordingState.Saving -> "Saving…" to MaterialTheme.colorScheme.primary
        RecordingState.Complete -> "Saved" to MaterialTheme.colorScheme.secondary
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TAAL Recorder", style = MaterialTheme.typography.titleLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusDot(color = statusColor, size = 8.dp)
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
            )
        },
        bottomBar = {
            BottomControls(
                isRecording = state == RecordingState.Recording,
                secondsElapsed = secondsElapsed,
                totalDuration = selectedDuration,
                onToggle = {
                    when (state) {
                        RecordingState.Idle, RecordingState.Complete -> ensureMicPermissionThenStart()
                        RecordingState.Recording -> vm.toggleRecording()
                        RecordingState.Saving -> Unit
                    }
                },
                onOpenReference = { showReference = true }
            )
        }
    ) { inner ->
        RecordingScreenContent(
            paddingTop = inner,
            state = state,
            samples = samples,
            sampleRate = sampleRate,
            clearTrigger = clearWaveformTrigger,
            selectedDuration = selectedDuration,
            onSelectDuration = vm::setRecordingDuration,
            selectedFilter = selectedFilter,
            onSelectFilter = vm::setPreFilter,
            preAmpDb = selectedAmp,
            onPreAmpChanged = vm::setPreAmpDb
        )
    }

    if (showReference) {
        ModalBottomSheet(onDismissRequest = { showReference = false }, sheetState = sheetState) {
            AuscultationReferenceSheet(onClose = { showReference = false })
        }
    }
    if (showMicRationale) {
        MicPermissionRationaleDialog(
            onDismiss = { showMicRationale = false },
            onGrant = {
                showMicRationale = false
                requestMicPermission.launch(micPermission)
            },
            onOpenSettings = {
                showMicRationale = false
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            }
        )
    }
    if (state == RecordingState.Saving) {
        SavingDialog()
    }
}

/** One-tap dialog for microphone permission rationale with Settings fallback. */
@Composable
private fun MicPermissionRationaleDialog(
    onDismiss: () -> Unit,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Microphone permission needed") },
        text = {
            Text(
                "To record heart sounds, allow microphone access. You can grant it now, " +
                        "or open Settings if you’ve previously denied it."
            )
        },
        confirmButton = { TextButton(onClick = onGrant) { Text("Grant") } },
        dismissButton = { TextButton(onClick = onOpenSettings) { Text("Open Settings") } }
    )
}

/**
 * Main page body:
 * - Filter presets (icon-only).
 * - Frequency band (when Custom filter selected).
 * - Duration presets (when idle).
 * - Live waveform (dominant).
 * - Pre-amp slider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingScreenContent(
    paddingTop: PaddingValues,
    state: RecordingState,
    samples: FloatArray?,
    sampleRate: Int?,
    clearTrigger: Boolean,
    selectedDuration: Int?,
    onSelectDuration: (Int?) -> Unit,
    selectedFilter: PreFilterOption,
    onSelectFilter: (PreFilterOption) -> Unit,
    preAmpDb: Int,
    onPreAmpChanged: (Int) -> Unit
) {
    val isRecording = state == RecordingState.Recording

    var lowHz by remember { mutableFloatStateOf(250f) }
    var highHz by remember { mutableFloatStateOf(12000f) }

    Column(
        Modifier
            .padding(paddingTop)
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilterRowCompact(selected = selectedFilter, onSelect = onSelectFilter)

        if (selectedFilter == PreFilterOption.None) {
            FrequencyBandRow(
                lowHz = lowHz,
                highHz = highHz,
                onChange = { l, h ->
                    lowHz = l
                    highHz = h
                    // Future: pass custom band into VM/RecordConfig when SDK exposes it.
                }
            )
        }

        if (!isRecording) {
            DurationPicker(selected = selectedDuration, onSelect = onSelectDuration)
        }

        WaveformCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            samples = samples,
            sampleRate = sampleRate,
            clearTrigger = clearTrigger
        )

        PreAmpRow(preAmpDb = preAmpDb, onPreAmpChanged = onPreAmpChanged)
    }
}

/** Icon-only filter selection row. */
@Composable
private fun FilterRowCompact(
    selected: PreFilterOption,
    onSelect: (PreFilterOption) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            "Filter preset",
            style = MaterialTheme.typography.titleSmall
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterIconChip(
                selected = selected == PreFilterOption.Heart,
                iconRes = R.drawable.heart,
                contentDescription = "Heart",
                onClick = { onSelect(PreFilterOption.Heart) }
            )
            FilterIconChip(
                selected = selected == PreFilterOption.Lungs,
                iconRes = R.drawable.lungs,
                contentDescription = "Lungs",
                onClick = { onSelect(PreFilterOption.Lungs) }
            )
            FilterIconChip(
                selected = selected == PreFilterOption.Bowel,
                iconRes = R.drawable.bowel,
                contentDescription = "Bowel",
                onClick = { onSelect(PreFilterOption.Bowel) }
            )
            FilterIconChip(
                selected = selected == PreFilterOption.Pregnancy,
                iconRes = R.drawable.pregnancy,
                contentDescription = "Maternity",
                onClick = { onSelect(PreFilterOption.Pregnancy) }
            )
            FilterIconChip(
                selected = selected == PreFilterOption.FullBody,
                iconRes = R.drawable.full_body,
                contentDescription = "Full body",
                onClick = { onSelect(PreFilterOption.FullBody) }
            )
            FilterIconChip(
                selected = selected == PreFilterOption.None,
                iconRes = R.drawable.custom,
                contentDescription = "Custom",
                onClick = { onSelect(PreFilterOption.None) }
            )
        }
    }
}

/** Tiny circular chip that just shows an icon, with selected state. */
@Composable
private fun FilterIconChip(
    selected: Boolean,
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

/** Frequency band control with manual numeric input at each end. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyBandRow(
    lowHz: Float,
    highHz: Float,
    onChange: (Float, Float) -> Unit
) {
    val minHz = 20f
    val maxHz = 24000f
    val minGapHz = 10f

    var showLowDialog by remember { mutableStateOf(false) }
    var showHighDialog by remember { mutableStateOf(false) }
    var lowInput by remember { mutableStateOf(lowHz.toInt().toString()) }
    var highInput by remember { mutableStateOf(highHz.toInt().toString()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Frequency band",
            style = MaterialTheme.typography.titleSmall
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${lowHz.toInt()} Hz",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable {
                    lowInput = lowHz.toInt().toString()
                    showLowDialog = true
                }
            )
            Text(
                "${highHz.toInt()} Hz",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable {
                    highInput = highHz.toInt().toString()
                    showHighDialog = true
                }
            )
        }

        RangeSlider(
            value = lowHz..highHz,
            onValueChange = { range ->
                val clampedStart = range.start.coerceIn(minHz, maxHz)
                val clampedEnd = range.endInclusive.coerceIn(minHz, maxHz)
                if (clampedEnd - clampedStart >= minGapHz) {
                    onChange(clampedStart, clampedEnd)
                }
            },
            valueRange = minHz..maxHz,
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        )
    }

    // Low-end dialog
    if (showLowDialog) {
        AlertDialog(
            onDismissRequest = { showLowDialog = false },
            title = { Text("Set low frequency") },
            text = {
                Column {
                    Text("Enter a value between ${minHz.toInt()} Hz and ${(highHz - minGapHz).toInt()} Hz.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lowInput,
                        onValueChange = { lowInput = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Low cutoff (Hz)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = lowInput.toFloatOrNull()
                    if (parsed != null) {
                        val newLow = parsed
                            .coerceIn(minHz, highHz - minGapHz)
                        onChange(newLow, highHz)
                    }
                    showLowDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLowDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // High-end dialog
    if (showHighDialog) {
        AlertDialog(
            onDismissRequest = { showHighDialog = false },
            title = { Text("Set high frequency") },
            text = {
                Column {
                    Text("Enter a value between ${(lowHz + minGapHz).toInt()} Hz and ${maxHz.toInt()} Hz.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = highInput,
                        onValueChange = { highInput = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("High cutoff (Hz)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = highInput.toFloatOrNull()
                    if (parsed != null) {
                        val newHigh = parsed
                            .coerceIn(lowHz + minGapHz, maxHz)
                        onChange(lowHz, newHigh)
                    }
                    showHighDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHighDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Pre-amplification slider row (0–10 dB). */
@Composable
private fun PreAmpRow(
    preAmpDb: Int,
    onPreAmpChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "Pre-amplification",
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$preAmpDb dB", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = preAmpDb.toFloat(),
                onValueChange = { onPreAmpChanged(it.roundToInt().coerceIn(0, 10)) },
                valueRange = 0f..10f,
                steps = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .padding(bottom = 4.dp)
            )
        }
    }
}

/** Fixed set of duration presets; `null` = unlimited. */
@Composable
private fun DurationPicker(
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    val options = listOf(15, 30, 45, 60, null)
    val labels = listOf("15 s", "30 s", "45 s", "60 s", "No limit")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Recording duration",
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, value ->
                val isSelected = selected == value
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(value) },
                    label = { Text(labels[index]) }
                )
            }
        }
    }
}

/**
 * Bottom action row:
 * - Info (landmarks),
 * - Big record toggle,
 * - Time counter (elapsed or countdown).
 */
@Composable
private fun BottomControls(
    isRecording: Boolean,
    secondsElapsed: Int,
    totalDuration: Int?,
    onToggle: () -> Unit,
    onOpenReference: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Surface(tonalElevation = 1.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onOpenReference,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null)
            }

            RecordButton(
                recording = isRecording,
                onClick = {
                    onToggle()
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                }
            )

            val displaySec =
                totalDuration?.let { (it - secondsElapsed).coerceAtLeast(0) } ?: secondsElapsed
            val min = displaySec / 60
            val sec = displaySec % 60
            Text(
                text = "%02d:%02d".format(min, sec),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Reference sheet with zoomable image for auscultation landmarks. */
@Composable
private fun AuscultationReferenceSheet(onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Anterior auscultation points", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClose) { Text("Close") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Use these landmarks for Aortic, Pulmonic, Tricuspid and Mitral areas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        ZoomableImageCard(
            imageRes = R.drawable.auscultation,
            contentDescription = "Anterior auscultation points reference"
        )
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Got it")
        }
    }
}

/** Simple pinch-to-zoom container for the reference image. */
@Composable
private fun ZoomableImageCard(imageRes: Int, contentDescription: String?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color.White)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = contentDescription,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationX = offsetX
                        translationY = offsetY
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}

/** Static status dot (used in top bar). */
@Composable
private fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/** Card wrapper around the live waveform canvas. */
@Composable
private fun WaveformCard(
    modifier: Modifier = Modifier,
    samples: FloatArray? = null,
    sampleRate: Int?,
    clearTrigger: Boolean
) {
    ElevatedCard(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(
                text = "Live waveform",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxSize()) {
                LiveWaveformView(
                    modifier = Modifier.fillMaxSize(),
                    samples = samples,
                    sampleRate = sampleRate,
                    isInteractionEnabled = true,
                    clearTrigger = clearTrigger
                )
            }
        }
    }
}

/**
 * Small rolling estimator for HR (bpm).
 * (Kept for potential reuse; currently not shown in UI).
 */
private class HeartRateEstimator(
    private val windowSec: Float = 8f
) {
    private var ring: FloatArray = FloatArray(1)
    private var writeIndex = 0
    private var filled = 0
    private var fs: Int = 0

    fun reset() {
        ring = FloatArray(1); writeIndex = 0; filled = 0; fs = 0
    }

    fun append(chunk: FloatArray, sampleRateHz: Int) {
        if (sampleRateHz <= 0) return
        if (fs != sampleRateHz || ring.size <= 1) {
            fs = sampleRateHz
            val size = (fs * windowSec).toInt().coerceAtLeast(1024)
            ring = FloatArray(size)
            writeIndex = 0
            filled = 0
        }
        for (v in chunk) {
            ring[writeIndex] = v
            writeIndex = (writeIndex + 1) % ring.size
            if (filled < ring.size) filled++
        }
    }

    fun estimateBpm(): Int? {
        if (fs <= 0 || filled < fs / 2) return null

        val n = filled
        val buf = FloatArray(n)
        val start = (writeIndex - filled + ring.size) % ring.size
        if (start + n <= ring.size) {
            ring.copyInto(buf, 0, start, start + n)
        } else {
            val tail = ring.size - start
            ring.copyInto(buf, 0, start, ring.size)
            ring.copyInto(buf, tail, 0, n - tail)
        }

        var mean = 0f; for (x in buf) mean += x; mean /= n
        for (i in 0 until n) buf[i] -= mean
        var env = 0f
        for (i in 0 until n) {
            val r = abs(buf[i]); env += (r - env) * 0.1f; buf[i] = env
        }

        val targetFs = 200
        val step = max(1, fs / targetFs)
        val m = n / step
        if (m < targetFs) return null
        val ds = FloatArray(m)
        var idx = 0
        for (i in 0 until m) {
            ds[i] = buf[idx]; idx += step
        }

        val maxV = ds.maxOrNull() ?: return null
        if (maxV <= 1e-6f) return null
        for (i in ds.indices) ds[i] /= maxV

        val minLag = (targetFs * 60f / 180f).toInt().coerceAtLeast(1)
        val maxLag = (targetFs * 60f / 40f).toInt().coerceAtMost(m - 1)
        var bestLag = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var s = 0f
            val limit = m - lag
            for (i in 0 until limit) {
                s += ds[i] * ds[i + lag]
            }
            if (s > bestScore) {
                bestScore = s; bestLag = lag
            }
        }
        if (bestLag <= 0) return null
        return (60f * targetFs / bestLag.toFloat()).roundToInt().coerceIn(40, 180)
    }
}

/** Big circular toggle button with a record/stop icon. */
@Composable
private fun RecordButton(
    recording: Boolean,
    onClick: () -> Unit,
    size: Dp = 64.dp
) {
    val bg = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(size)) {
            Icon(
                painter = if (recording)
                    painterResource(R.drawable.stop)
                else
                    painterResource(R.drawable.fiber_manual_record),
                contentDescription = if (recording) "Stop recording" else "Start recording",
                tint = Color.White
            )
        }
    }
}

/** Modal spinner shown while the SDK is finalizing the recording to disk. */
@Composable
private fun SavingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Saving recording…") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Please keep the device connected.")
            }
        }
    )
}
