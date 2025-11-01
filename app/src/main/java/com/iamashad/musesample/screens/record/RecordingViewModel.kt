package com.iamashad.musesample.screens.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iamashad.musesample.wrapper.ConnectionState
import com.iamashad.musesample.wrapper.RecordConfig
import com.iamashad.musesample.wrapper.SdkRecordingState
import com.iamashad.musesample.wrapper.TaalSdkHolder
import `in`.museinc.android.surr_core.utils.PreFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

sealed interface RecordingEffect {
    data class NavigateToMetadata(val path: String?) : RecordingEffect
}

enum class RecordingState { Idle, Recording, Saving, Complete }

/** UI-facing prefilter options for the Recording screen. */
enum class PreFilterOption(val label: String) {
    None("None"),
    Heart("Heart"),
    Lungs("Lungs"),
    Bowel("Bowel"),
    Pregnancy("Pregnancy"),
    FullBody("Full body")
}

private fun PreFilterOption?.toSdk(): PreFilter? = when (this) {
    PreFilterOption.None, null -> null
    PreFilterOption.Heart -> PreFilter.HEART
    PreFilterOption.Lungs -> PreFilter.LUNGS
    PreFilterOption.Bowel -> PreFilter.BOWEL
    PreFilterOption.Pregnancy -> PreFilter.PREGNANCY
    PreFilterOption.FullBody -> PreFilter.FULL_BODY
}

class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<Application>()
    private val wrapper = TaalSdkHolder.get(app)

    private val _state = MutableStateFlow(RecordingState.Idle)
    val state = _state.asStateFlow()

    // Duration (null = untimed)
    private val _selectedDurationSec = MutableStateFlow<Int?>(null)
    val selectedDurationSec = _selectedDurationSec.asStateFlow()

    // NEW: Recording options that must be applied BEFORE start()
    private val _preFilter = MutableStateFlow(PreFilterOption.None)
    val preFilter = _preFilter.asStateFlow()

    private val _preAmpDb = MutableStateFlow(3) // 0..10
    val preAmpDb = _preAmpDb.asStateFlow()

    // Buffered so navigation events cannot be dropped
    private val _effects = MutableSharedFlow<RecordingEffect>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val effects: SharedFlow<RecordingEffect> = _effects

    private var currentWav: File? = null

    // Keep the paths we asked the SDK to use (fallback if SDK getters are null)
    private var lastPlannedRaw: String? = null
    private var lastPlannedPref: String? = null

    init {
        // Mirror UI state (but do not navigate here)
        viewModelScope.launch {
            wrapper.recording.collect { sdk ->
                when (sdk) {
                    is SdkRecordingState.Recording -> _state.value = RecordingState.Recording
                    is SdkRecordingState.Stopped,
                    is SdkRecordingState.Error,
                    is SdkRecordingState.Idle -> {
                        // stopAndSave() handles navigation + resets state
                    }
                }
            }
        }
    }

    fun setRecordingDuration(seconds: Int?) { _selectedDurationSec.value = seconds }
    fun setPreFilter(opt: PreFilterOption) { _preFilter.value = opt }
    fun setPreAmpDb(db: Int) { _preAmpDb.value = db.coerceIn(0, 10) }

    fun toggleRecording() {
        when (_state.value) {
            RecordingState.Idle, RecordingState.Complete -> startRecording()
            RecordingState.Recording -> stopAndSave()
            RecordingState.Saving -> Unit
        }
    }

    private fun startRecording() = viewModelScope.launch {
        if (wrapper.connection.value !is ConnectionState.Connected) return@launch

        val base = File(app.filesDir, "captures").apply { mkdirs() }
        val raw = File(base, "pcg_raw_${System.currentTimeMillis()}.wav").absolutePath
        val pre = File(base, "pcg_pref_${System.currentTimeMillis()}.wav").absolutePath

        lastPlannedRaw = raw
        lastPlannedPref = pre

        val selectedFilter = _preFilter.value.toSdk()
        val selectedPreAmp = _preAmpDb.value
        val duration = _selectedDurationSec.value

        _state.value = RecordingState.Recording
        runCatching {
            wrapper.startRecording(
                RecordConfig(
                    rawAudioPath = raw,
                    // Only provide a prefiltered path when a filter is chosen.
                    preFilteredAudioPath = if (selectedFilter != null) pre else null,
                    recordingTimeSec = duration,
                    preAmplificationDb = selectedPreAmp,
                    preFilter = selectedFilter,
                    playbackWhileRecording = false
                )
            )
        }.onFailure {
            _state.value = RecordingState.Idle
        }
    }

    private fun stopAndSave() = viewModelScope.launch {
        _state.value = RecordingState.Saving

        // Stop on IO thread
        withContext(Dispatchers.IO) { wrapper.stopRecording() }

        // Wait for wrapper to publish Stopped/Error
        val result: SdkRecordingState? = withTimeoutOrNull(5_000) {
            wrapper.recording.first { it is SdkRecordingState.Stopped || it is SdkRecordingState.Error }
        }

        // Prefer a wrapper-verified path (chosenPath). Otherwise try pref/raw/planned.
        val candidateOrder: List<String?> = when (result) {
            is SdkRecordingState.Stopped -> listOf(
                result.chosenPath,          // verified by wrapper
                result.prefilteredPath,     // may or may not exist
                result.rawPath,             // may or may not exist
                lastPlannedPref,            // fallbacks
                lastPlannedRaw
            )
            else -> listOf(lastPlannedPref, lastPlannedRaw)
        }

        val finalPath = withContext(Dispatchers.IO) {
            candidateOrder.firstOrNull { p ->
                p?.let { File(it).let { f -> f.exists() && f.length() > 44 } } == true
            }
        }

        // Clean planned values after stop
        lastPlannedPref = null
        lastPlannedRaw = null

        if (finalPath != null) {
            currentWav = File(finalPath)
            _effects.emit(RecordingEffect.NavigateToMetadata(finalPath))
        } else {
            _state.value = RecordingState.Idle
            return@launch
        }

        _state.value = RecordingState.Idle
    }

    fun lastWavPath(): String? = currentWav?.absolutePath
}
