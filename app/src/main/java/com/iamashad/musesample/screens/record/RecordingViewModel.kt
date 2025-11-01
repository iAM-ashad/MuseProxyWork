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

/* ---------- One-off UI effects ---------- */

/** Navigation signal sent when a recording stops and we have a usable WAV path. */
sealed interface RecordingEffect {
    data class NavigateToMetadata(val path: String?) : RecordingEffect
}

/* ---------- UI state ---------- */

/** Top-level state for the recording button and UI. */
enum class RecordingState { Idle, Recording, Saving, Complete }

/**
 * UI-facing pre-filter options (mapped to SDK [PreFilter]).
 * These must be provided to the SDK *before* starting a recording.
 */
enum class PreFilterOption(val label: String) {
    None("None"),
    Heart("Heart"),
    Lungs("Lungs"),
    Bowel("Bowel"),
    Pregnancy("Pregnancy"),
    FullBody("Full body")
}

/** Map UI option to SDK enum (null = no prefilter file will be requested). */
private fun PreFilterOption?.toSdk(): PreFilter? = when (this) {
    PreFilterOption.None, null -> null
    PreFilterOption.Heart -> PreFilter.HEART
    PreFilterOption.Lungs -> PreFilter.LUNGS
    PreFilterOption.Bowel -> PreFilter.BOWEL
    PreFilterOption.Pregnancy -> PreFilter.PREGNANCY
    PreFilterOption.FullBody -> PreFilter.FULL_BODY
}

/**
 * ViewModel coordinating the recording lifecycle with the TAAL SDK.
 *
 * Responsibilities:
 * - Expose a simple [RecordingState] for the UI.
 * - Hold pre-start options (duration, prefilter, preamp).
 * - Translate start/stop events to the SDK and resolve the final WAV path.
 * - Emit a one-time navigation effect when saving completes successfully.
 */
class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val wrapper = TaalSdkHolder.get(app)

    /* --------- State exposed to UI --------- */

    private val _state = MutableStateFlow(RecordingState.Idle)
    val state = _state.asStateFlow()

    /** Duration cap in seconds; null means unlimited. */
    private val _selectedDurationSec = MutableStateFlow<Int?>(null)
    val selectedDurationSec = _selectedDurationSec.asStateFlow()

    /** Options that must be applied *before* startRecording(). */
    private val _preFilter = MutableStateFlow(PreFilterOption.None)
    val preFilter = _preFilter.asStateFlow()

    /** Digital pre-amplification in dB (0..10). */
    private val _preAmpDb = MutableStateFlow(3)
    val preAmpDb = _preAmpDb.asStateFlow()

    /** Buffered effects so navigation can’t be dropped if collectors are late. */
    private val _effects = MutableSharedFlow<RecordingEffect>(replay = 0, extraBufferCapacity = 1)
    val effects: SharedFlow<RecordingEffect> = _effects

    /* --------- Bookkeeping for file paths --------- */

    private var currentWav: File? = null

    // Paths we *asked* the SDK to use (in case getters are null on stop).
    private var lastPlannedRaw: String? = null
    private var lastPlannedPref: String? = null

    init {
        // Mirror wrapper state to our UI state.
        // We don't navigate here; stopping/cleanup/navigation is owned by stopAndSave().
        viewModelScope.launch {
            wrapper.recording.collect { sdk ->
                when (sdk) {
                    is SdkRecordingState.Recording -> _state.value = RecordingState.Recording
                    is SdkRecordingState.Stopped,
                    is SdkRecordingState.Error,
                    is SdkRecordingState.Idle -> {
                        // handled after stopAndSave() requests termination
                    }
                }
            }
        }
    }

    /* --------- Option setters (called by UI) --------- */

    fun setRecordingDuration(seconds: Int?) {
        _selectedDurationSec.value = seconds
    }

    fun setPreFilter(opt: PreFilterOption) {
        _preFilter.value = opt
    }

    fun setPreAmpDb(db: Int) {
        _preAmpDb.value = db.coerceIn(0, 10)
    }

    /* --------- Main actions --------- */

    /**
     * Toggle based on current state:
     * - Idle/Complete -> start
     * - Recording     -> stop & save
     * - Saving        -> no-op
     */
    fun toggleRecording() {
        when (_state.value) {
            RecordingState.Idle, RecordingState.Complete -> startRecording()
            RecordingState.Recording -> stopAndSave()
            RecordingState.Saving -> Unit
        }
    }

    /**
     * Start a new recording with the current options.
     * - Requires an active device connection.
     * - Prepares file paths (raw + optional prefiltered).
     * - Asks the SDK to start with [RecordConfig].
     */
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
                    // Only provide a prefiltered path to the SDK if a filter is selected.
                    preFilteredAudioPath = if (selectedFilter != null) pre else null,
                    recordingTimeSec = duration,
                    preAmplificationDb = selectedPreAmp,
                    preFilter = selectedFilter,
                    playbackWhileRecording = false
                )
            )
        }.onFailure {
            // Roll back UI state on any immediate failure from the SDK.
            _state.value = RecordingState.Idle
        }
    }

    /**
     * Stop the SDK, wait for a final state, and resolve a usable WAV path.
     * Path resolution priority:
     *   1) wrapper-chosen path (verified)
     *   2) wrapper prefiltered path
     *   3) wrapper raw path
     *   4) planned prefiltered path
     *   5) planned raw path
     *
     * Emits [RecordingEffect.NavigateToMetadata] when a file with >44B exists.
     */
    private fun stopAndSave() = viewModelScope.launch {
        _state.value = RecordingState.Saving

        // Stop on IO to avoid blocking main
        withContext(Dispatchers.IO) { wrapper.stopRecording() }

        // Await terminal state from the wrapper (Stopped/Error) with a timeout.
        val result: SdkRecordingState? = withTimeoutOrNull(5_000) {
            wrapper.recording.first { it is SdkRecordingState.Stopped || it is SdkRecordingState.Error }
        }

        // Choose the best candidate path.
        val candidateOrder: List<String?> = when (result) {
            is SdkRecordingState.Stopped -> listOf(
                result.chosenPath,
                result.prefilteredPath,
                result.rawPath,
                lastPlannedPref,
                lastPlannedRaw
            )

            else -> listOf(lastPlannedPref, lastPlannedRaw)
        }

        val finalPath = withContext(Dispatchers.IO) {
            candidateOrder.firstOrNull { p ->
                p?.let { File(it).let { f -> f.exists() && f.length() > 44 } } == true
            }
        }

        // Clean up stale planned paths.
        lastPlannedPref = null
        lastPlannedRaw = null

        if (finalPath != null) {
            currentWav = File(finalPath)
            _effects.emit(RecordingEffect.NavigateToMetadata(finalPath))
            _state.value = RecordingState.Idle
        } else {
            // No file materialized; return to Idle without navigation.
            _state.value = RecordingState.Idle
        }
    }

    /** Expose the last confirmed path in case the UI needs it after navigation. */
    fun lastWavPath(): String? = currentWav?.absolutePath
}
