package com.iamashad.musesample.screens.record

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
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
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    vm: RecordingViewModel,
    onStopAndSave: (path: String?) -> Unit,
    onCancel: () -> Unit
) {
    val state by vm.state.collectAsState()

    // Live samples + sample rate from wrapper
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val wrapper = remember(app) { TaalSdkHolder.get(app) }
    val samples by wrapper.liveSamples.collectAsState()
    val sampleRate by wrapper.sampleRateHz.collectAsState()

    // ---------- Permission state ----------
    val activity = context as? Activity
    val micPermission = AppPermissions.RECORD_AUDIO

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                micPermission
            ) == PackageManager.PERMISSION_GRANTED
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
            if (shouldExplain) {
                showMicRationale = true
            } else {
                requestMicPermission.launch(micPermission)
            }
        }
    }

    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is RecordingEffect.NavigateToMetadata -> onStopAndSave(effect.path)
            }
        }
    }

    val selectedDuration by vm.selectedDurationSec.collectAsState()
    val selectedFilter by vm.preFilter.collectAsState()
    val selectedAmp by vm.preAmpDb.collectAsState()

    val bpmEstimator = remember { HeartRateEstimator() }
    var bpm by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(samples, sampleRate, state) {
        if (state == RecordingState.Recording && samples != null && sampleRate != null) {
            bpmEstimator.append(samples!!, sampleRate!!)
            bpm = bpmEstimator.estimateBpm()
        } else if (state != RecordingState.Recording) {
            bpmEstimator.reset()
            bpm = null
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showOptions by remember { mutableStateOf(false) }
    var showReference by remember { mutableStateOf(false) }

    var secondsElapsed by remember(state == RecordingState.Recording) { mutableIntStateOf(0) }
    LaunchedEffect(state == RecordingState.Recording, selectedDuration) {
        if (state != RecordingState.Recording) {
            secondsElapsed = 0
            return@LaunchedEffect
        }
        secondsElapsed = 0
        while (isActive) {
            delay(1000)
            secondsElapsed++
            selectedDuration?.let {
                if (secondsElapsed >= it) {
                    vm.toggleRecording()
                    break
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stethoscope Capture") },
                actions = {
                    TextButton(onClick = { showOptions = true }) {
                        Icon(painterResource(R.drawable.tune), contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Options")
                    }
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
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
            bpm = bpm,
            selectedDuration = selectedDuration,
            onSelectDuration = vm::setRecordingDuration
        )
    }

    if (showOptions) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            sheetState = sheetState
        ) {
            RecordingOptionsSheet(
                preFilter = selectedFilter,
                onPreFilterChanged = vm::setPreFilter,
                preAmpDb = selectedAmp,
                onPreAmpChanged = vm::setPreAmpDb,
                onClose = { showOptions = false }
            )
        }
    }

    if (showReference) {
        ModalBottomSheet(
            onDismissRequest = { showReference = false },
            sheetState = sheetState
        ) {
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
            Text("To record heart sounds, allow microphone access. You can grant it now, or open Settings if you’ve previously denied it.")
        },
        confirmButton = {
            TextButton(onClick = onGrant) { Text("Grant") }
        },
        dismissButton = {
            TextButton(onClick = onOpenSettings) { Text("Open Settings") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingScreenContent(
    paddingTop: androidx.compose.foundation.layout.PaddingValues,
    state: RecordingState,
    samples: FloatArray?,
    bpm: Int?,
    selectedDuration: Int?,
    onSelectDuration: (Int?) -> Unit
) {
    val isRecording = state == RecordingState.Recording

    Column(
        Modifier
            .padding(paddingTop)
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isRecording) {
            DurationPicker(
                selected = selectedDuration,
                onSelect = onSelectDuration
            )
        }
        StatusCard(state = state, bpm = bpm)
        WaveformCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            isActive = isRecording,
            samples = samples
        )
    }
}

@Composable
private fun DurationPicker(
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    val options = listOf(15, 30, 45, 60, null)
    val labels = listOf("15s", "30s", "45s", "60s", "∞")

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
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

@Composable
private fun BottomControls(
    isRecording: Boolean,
    secondsElapsed: Int,
    totalDuration: Int?,
    onToggle: () -> Unit,
    onOpenReference: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onOpenReference,
                enabled = true,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Info")
            }

            RecordButton(recording = isRecording, onClick = {
                onToggle()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            })

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

@Composable
private fun AuscultationReferenceSheet(
    onClose: () -> Unit
) {
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
        Spacer(Modifier.height(6.dp))
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

        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendRow(
                color = MaterialTheme.colorScheme.primary,
                label = "Aortic — 2nd ICS, Right sternal border"
            )
            LegendRow(
                color = MaterialTheme.colorScheme.secondary,
                label = "Pulmonic — 2nd ICS, Left sternal border"
            )
            LegendRow(
                color = MaterialTheme.colorScheme.tertiary,
                label = "Tricuspid — 4th–5th ICS, Left sternal border"
            )
            LegendRow(
                color = MaterialTheme.colorScheme.error,
                label = "Mitral (Apex) — 5th ICS, Mid-clavicular line"
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Got it") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ZoomableImageCard(
    imageRes: Int,
    contentDescription: String?
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f; offsetY = 0f
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

@Composable
private fun StatusCard(state: RecordingState, bpm: Int?) {
    val (label, color) = when (state) {
        RecordingState.Idle -> "Ready" to Color(0xFF2E7D32) // green
        RecordingState.Recording -> "Recording…" to MaterialTheme.colorScheme.error
        RecordingState.Saving -> "Saving…" to MaterialTheme.colorScheme.primary
        RecordingState.Complete -> "Saved" to MaterialTheme.colorScheme.secondary
    }
    val dotColor by animateColorAsState(color, label = "statusColor")

    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BlinkingDot(color = dotColor)
            Spacer(Modifier.size(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (state == RecordingState.Recording) {
                HeartRatePill(bpm = bpm)
            }
        }
    }
}

@Composable
private fun HeartRatePill(bpm: Int?) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape
    ) {
        Text(
            text = if (bpm != null) "$bpm bpm" else "-- bpm",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun WaveformCard(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    samples: FloatArray? = null
) {
    ElevatedCard(modifier) {
        Box(Modifier.fillMaxSize()) {
            HeartSoundWaveform(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                samples = samples
            )
        }
    }
}

@Composable
fun HeartSoundWaveform(
    modifier: Modifier,
    samples: FloatArray?,
    lineColor: Color = Color(0xFFB388FF)
) {
    // Keep a few seconds of history for a smoother strip
    val visiblePoints = 800
    val ring = remember { FloatArray(visiblePoints) }
    var writeIndex by remember { mutableIntStateOf(0) }
    var filled by remember { mutableIntStateOf(0) }
    var dcMean by remember { mutableFloatStateOf(0f) }

    // Buffer incoming samples into a small visual ring (downsample for efficiency)
    LaunchedEffect(samples?.contentHashCode()) {
        samples?.let { chunk ->
            // light downsample for UI
            val step = max(1, chunk.size / 400)
            var i = 0
            while (i < chunk.size) {
                val v = chunk[i]
                dcMean += 0.01f * (v - dcMean)
                val hp = v - dcMean
                ring[writeIndex] = hp
                writeIndex = (writeIndex + 1) % visiblePoints
                filled = min(visiblePoints, filled + 1)
                i += step
            }
        }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2f

        // grid
        val gridColor = lineColor.copy(alpha = 0.06f)
        val gridSpacing = 40.dp.toPx()
        var gx = 0f
        while (gx <= w) {
            drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), 1f); gx += gridSpacing
        }
        var gy = 0f
        while (gy <= h) {
            drawLine(gridColor, Offset(0f, gy), Offset(w, gy), 1f); gy += gridSpacing
        }

        if (filled < 8) return@Canvas

        // extract
        val data = FloatArray(filled)
        val start = (writeIndex - filled + visiblePoints) % visiblePoints
        if (start + filled <= visiblePoints) {
            ring.copyInto(data, 0, start, start + filled)
        } else {
            val tail = visiblePoints - start
            ring.copyInto(data, 0, start, visiblePoints)
            ring.copyInto(data, tail, 0, filled - tail)
        }

        // simple smoothing
        val smooth = FloatArray(data.size)
        var prev = 0f
        for (i in data.indices) {
            prev += 0.35f * (data[i] - prev)
            smooth[i] = prev
        }

        // draw path
        val path = Path()
        path.moveTo(0f, mid)
        val stepX = w / (smooth.size - 1).coerceAtLeast(1)
        val gain = (h * 0.35f) / (smooth.maxOrNull()?.takeIf { it > 0f } ?: 1f)
        for (i in smooth.indices) {
            val x = i * stepX
            val y = mid - smooth[i] * gain
            path.lineTo(x, y)
        }
        drawPath(
            path,
            color = lineColor.copy(alpha = 0.8f),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )

        // baseline
        drawLine(
            color = lineColor.copy(alpha = 0.25f),
            start = Offset(0f, mid),
            end = Offset(w, mid),
            strokeWidth = 1f
        )
    }
}

private class HeartRateEstimator(
    // Keep ~8 seconds of audio for robust estimation
    private val windowSec: Float = 8f
) {
    private var ring: FloatArray = FloatArray(1)
    private var writeIndex = 0
    private var filled = 0
    private var fs: Int = 0

    fun reset() {
        ring = FloatArray(1)
        writeIndex = 0
        filled = 0
        fs = 0
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

        // 1) DC removal + rectification + low-pass (envelope)
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

        var mean = 0f
        for (x in buf) mean += x
        mean /= n
        for (i in 0 until n) buf[i] -= mean

        // Rectify & smooth (EMA ~20ms)
        var env = 0f
        for (i in 0 until n) {
            val r = abs(buf[i])
            env += (r - env) * 0.1f
            buf[i] = env
        }

        // 2) Downsample envelope to ~200 Hz for autocorrelation efficiency
        val targetFs = 200
        val step = max(1, fs / targetFs)
        val m = n / step
        if (m < targetFs) return null
        val ds = FloatArray(m)
        var idx = 0
        for (i in 0 until m) {
            ds[i] = buf[idx]
            idx += step
        }

        // 3) Normalize
        val maxV = ds.maxOrNull() ?: return null
        if (maxV <= 1e-6f) return null
        for (i in ds.indices) ds[i] /= maxV

        // 4) Autocorrelation in plausible HR band (40..180 bpm)
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
                bestScore = s
                bestLag = lag
            }
        }
        if (bestLag <= 0) return null

        val bpm = (60f * targetFs / bestLag.toFloat()).roundToInt().coerceIn(40, 180)
        return bpm
    }
}

@Composable
private fun RecordingOptionsSheet(
    preFilter: PreFilterOption,
    onPreFilterChanged: (PreFilterOption) -> Unit,
    preAmpDb: Int,
    onPreAmpChanged: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Recording Options", style = MaterialTheme.typography.titleLarge)

        Text("Pre-filter", style = MaterialTheme.typography.titleMedium)
        val filters = listOf(
            PreFilterOption.None, PreFilterOption.Heart, PreFilterOption.Lungs,
            PreFilterOption.Bowel, PreFilterOption.Pregnancy, PreFilterOption.FullBody
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { opt ->
                        FilterChip(
                            selected = preFilter == opt,
                            onClick = { onPreFilterChanged(opt) },
                            label = { Text(opt.label) }
                        )
                    }
                }
            }
        }

        Text("Pre-amplification (0–10 dB)", style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$preAmpDb dB", style = MaterialTheme.typography.titleLarge)
            androidx.compose.material3.Slider(
                value = preAmpDb.toFloat(),
                onValueChange = { onPreAmpChanged(it.roundToInt().coerceIn(0, 10)) },
                valueRange = 0f..10f,
                steps = 9
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("Done") }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit, size: Dp = 62.dp) {
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
                painter = if (recording) painterResource(R.drawable.stop) else painterResource(R.drawable.fiber_manual_record),
                contentDescription = if (recording) "Stop recording" else "Start recording",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun SavingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Saving recording…") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Please keep the device connected.")
            }
        }
    )
}

@Composable
private fun BlinkingDot(color: Color, size: Dp = 10.dp) {
    val inf = rememberInfiniteTransition(label = "blink")
    val alpha by inf.animateFloat(
        0.3f, 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "alphaAnim"
    )
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
