package com.iamashad.musesample.screens.record

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun RecordingScreen(
    vm: RecordingViewModel,
    onStopAndSave: (path: String?) -> Unit,
    onCancel: () -> Unit
) {
    val state by vm.state.collectAsState()

    // Collect one-shot effects from the VM. Navigate after save completes.
    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is RecordingEffect.NavigateToMetadata -> onStopAndSave(effect.path)
            }
        }
    }

    RecordingScreenContent(
        state = state,
        onToggle = { vm.toggleRecording() },
        onCancel = onCancel,
        onBookmark = { /* future hook */ }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingScreenContent(
    state: RecordingState,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
    onBookmark: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isRecording = state == RecordingState.Recording

    var secondsElapsed by remember(isRecording) { mutableIntStateOf(0) }
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            secondsElapsed = 0
            return@LaunchedEffect
        }
        while (isActive) {
            delay(1000)
            secondsElapsed++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cardio Capture") },
                actions = {
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
                isRecording = isRecording,
                secondsElapsed = secondsElapsed,
                onToggle = {
                    onToggle()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onBookmark = onBookmark
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(state = state)
            WaveformCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                isActive = isRecording
            )
            TipsCard()
        }
    }

    if (state == RecordingState.Saving) SavingDialog()
    if (state == RecordingState.Complete)
        SnackbarHost(hostState = remember { SnackbarHostState() })
}

@Composable
private fun StatusCard(state: RecordingState) {
    val (label, color) = when (state) {
        RecordingState.Idle -> "Ready" to MaterialTheme.colorScheme.tertiary
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
            AssistChip(
                onClick = {},
                label = { Text(if (state == RecordingState.Recording) "Clean signal" else "Device idle") }
            )
        }
    }
}

@Composable
private fun WaveformCard(
    isActive: Boolean,
    samples: FloatArray? = null,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier) {
        Box(Modifier.fillMaxSize()) {
            MedicalWaveform(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 18.dp),
                mode = if (isActive) WaveformMode.Recording else WaveformMode.Idle,
                samples = samples
            )

            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(if (isActive) "Artefact: none" else "Device idle") })
                Spacer(Modifier.size(8.dp))
                SuggestionChip(onClick = {}, label = { Text("Noise: low") })
            }
        }
    }
}

@Composable
private fun BottomControls(
    isRecording: Boolean,
    secondsElapsed: Int,
    onToggle: () -> Unit,
    onBookmark: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onBookmark,
                enabled = isRecording,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Mark")
            }
            RecordButton(recording = isRecording, onClick = onToggle)
            Text(
                text = "%02d:%02d".format(secondsElapsed / 60, secondsElapsed % 60),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
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
                imageVector = if (recording) Icons.Outlined.Stop else Icons.Outlined.FiberManualRecord,
                contentDescription = if (recording) "Stop recording" else "Start recording",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun TipsCard() {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.size(10.dp))
            Column {
                Text("Placement tips", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Apex (5th ICS, MCL). Hold steady; reduce clothing noise.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

private enum class WaveformMode { Idle, Recording }

@Composable
private fun MedicalWaveform(
    modifier: Modifier,
    mode: WaveformMode,
    samples: FloatArray?
) {
    val isRecording = mode == WaveformMode.Recording
    val lineColor =
        if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)
    val midlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)

    val visiblePoints = if (isRecording) 120 else 60
    val buffer = remember(isRecording) { FloatArray(visiblePoints) }

    val synth = remember { DemoPhonoEngine(baseBpm = 72f) }

    LaunchedEffect(isRecording, samples) {
        var lastTime = 0L
        while (isActive) {
            withFrameNanos { t ->
                if (lastTime == 0L) lastTime = t
                val dtSec = (t - lastTime) / 1_000_000_000f
                lastTime = t

                if (isRecording) {
                    if (samples != null && samples.isNotEmpty()) {
                        val start = ((samples.size - 1) * ((t / 900_000_000L) % 1)).toInt()
                        fillWindowWrapNormalized(samples, buffer, start)
                    } else {
                        synth.advance(dtSec)
                        synth.fill(buffer)
                    }
                } else fillIdle(buffer)
            }
        }
    }

    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val centerY = h / 2f
        val amp = h * if (isRecording) 0.42f else 0.22f

        drawLine(gridColor, Offset(0f, h * 0.25f), Offset(w, h * 0.25f), 1f)
        drawLine(midlineColor, Offset(0f, centerY), Offset(w, centerY), 1.2f)
        drawLine(gridColor, Offset(0f, h * 0.75f), Offset(w, h * 0.75f), 1f)

        val stepX = w / (buffer.size - 1).coerceAtLeast(1)
        val lastIndex = (buffer.size - 1).coerceAtLeast(1)
        for (i in 0..lastIndex) {
            val x = w - i * stepX
            val y = buffer[i].coerceIn(-1f, 1f) * amp
            val alpha = 0.15f + 0.85f * (i / lastIndex.toFloat())
            drawLine(
                color = lineColor.copy(alpha = alpha),
                start = Offset(x, centerY - y),
                end = Offset(x, centerY + y),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round
            )
        }

        drawLine(lineColor, Offset(w, 0f), Offset(w, h), strokeWidth = 3.5f)
    }
}

/* -------------------- Helpers -------------------- */

private fun fillIdle(out: FloatArray) {
    for (i in out.indices) out[i] = (Math.random().toFloat() - 0.5f) * 0.005f
}

private fun fillWindowWrapNormalized(src: FloatArray, out: FloatArray, start: Int) {
    val n = out.size
    val size = src.size
    var idx = ((start % size) + size) % size
    var max = 0f
    for (i in 0 until n) {
        val v = src[idx]
        out[i] = v
        val a = kotlin.math.abs(v)
        if (a > max) max = a
        idx++
        if (idx >= size) idx = 0
    }
    val m = if (max > 0f) max else 1f
    for (i in 0 until n) out[i] = (out[i] / m).coerceIn(-1f, 1f)
}

/** Clean, stable demo phonocardiogram — no breathing, no modulation */
private class DemoPhonoEngine(baseBpm: Float) {
    private var beatT = 0f
    private var beatDur = 60f / baseBpm

    fun advance(dt: Float) {
        beatT += dt
        if (beatT >= beatDur) beatT -= beatDur
    }

    fun fill(out: FloatArray) {
        val n = out.size
        val dt = beatDur / n
        var localT = beatT
        for (i in 0 until n) {
            val s1 = heartImpulse(localT, 0.04f * beatDur, 0.010f * beatDur, 1.0f)
            val s2 = heartImpulse(localT, 0.30f * beatDur, 0.008f * beatDur, 0.65f)
            out[i] = (s1 + s2).coerceIn(-1f, 1f)
            localT += dt
            if (localT >= beatDur) localT -= beatDur
        }
        smoothInPlace(out, radius = 2)
        var m = 0f
        for (v in out) m = maxOf(m, kotlin.math.abs(v))
        if (m > 0f) for (i in out.indices) out[i] /= m
    }

    private fun heartImpulse(t: Float, center: Float, width: Float, gain: Float): Float {
        val x = (t - center) / width
        val g = kotlin.math.exp(-x * x)
        val skew = 1f + 0.6f * x
        return g * skew * gain
    }
}

private fun smoothInPlace(a: FloatArray, radius: Int) {
    if (radius <= 0) return
    val n = a.size
    val out = FloatArray(n)
    for (i in 0 until n) {
        var sum = 0f
        var cnt = 0
        val from = (i - radius).coerceAtLeast(0)
        val to = (i + radius).coerceAtMost(n - 1)
        for (j in from..to) {
            sum += a[j]; cnt++
        }
        out[i] = sum / cnt
    }
    System.arraycopy(out, 0, a, 0, n)
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
