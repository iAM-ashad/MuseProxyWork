package com.iamashad.musesample.screens.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iamashad.musesample.model.Session
import com.iamashad.musesample.repository.SessionRepository
import com.iamashad.musesample.wrapper.ConnectionState
import com.iamashad.musesample.wrapper.RecordConfig
import com.iamashad.musesample.wrapper.RecordingResult
import com.iamashad.musesample.wrapper.SdkRecordingState
import com.iamashad.musesample.wrapper.TaalSdkHolder
import `in`.museinc.android.surr_core.utils.PreFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/* ---------- One-off UI effects ---------- */

sealed interface RecordingEffect {
    data class NavigateAfterSave(val filteredPath: String?, val rawPath: String?) : RecordingEffect
}

/* ---------- UI state ---------- */

enum class RecordingState { Idle, Recording, Saving, Complete }

/**
 * UI-facing pre-filter options (mapped to SDK [PreFilter]).
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
    private var autoStopJob: Job? = null

    /** Options that must be applied *before* startRecording(). */
    private val _preFilter = MutableStateFlow(PreFilterOption.Heart) // Default to Heart
    val preFilter = _preFilter.asStateFlow()

    /** Digital pre-amplification in dB (0..10). */
    private val _preAmpDb = MutableStateFlow(0)
    val preAmpDb = _preAmpDb.asStateFlow()

    /** Buffered effects so navigation can’t be dropped if collectors are late. */
    private val _effects =
        MutableSharedFlow<RecordingEffect>(replay = 0, extraBufferCapacity = 1)
    val effects: SharedFlow<RecordingEffect> = _effects

    /* --------- Bookkeeping for file paths --------- */

    private var currentFilteredWav: File? = null
    private var currentRawWav: File? = null

    // Paths we *asked* the SDK to use (in case getters are null on stop).
    private var lastPlannedRaw: String? = null
    private var lastPlannedPref: String? = null

    init {
        // Mirror wrapper state to our UI state.
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
        val duration = _selectedDurationSec.value   // seconds or null

        // cancel any previous auto-stop
        autoStopJob?.cancel()
        _state.value = RecordingState.Recording

        val startResult = runCatching {
            wrapper.startRecording(
                RecordConfig(
                    rawAudioPath = raw,
                    preFilteredAudioPath = if (selectedFilter != null) pre else null,
                    recordingTimeSec = duration,           // let SDK handle duration
                    preAmplificationDb = selectedPreAmp,
                    preFilter = selectedFilter,
                    playbackWhileRecording = true          // live playback enabled
                )
            )
        }

        startResult.onFailure {
            _state.value = RecordingState.Idle
            lastPlannedRaw = null
            lastPlannedPref = null
            autoStopJob = null
            return@launch
        }

        // Our own timeout as a fallback (slightly after SDK duration)
        if (duration != null) {
            autoStopJob = viewModelScope.launch {
                val totalMs = duration * 1000L + 300L
                delay(totalMs)
                if (_state.value == RecordingState.Recording) {
                    stopAndSave()
                }
            }
        } else {
            autoStopJob = null
        }
    }

    /**
     * Stop the SDK, wait for a final result (suspend), and resolve both usable WAV paths.
     */
    private fun stopAndSave() = viewModelScope.launch {
        autoStopJob?.cancel()
        autoStopJob = null

        _state.value = RecordingState.Saving

        // Stop and await result using the suspend API on IO dispatcher
        val result: RecordingResult? = withContext(Dispatchers.IO) {
            runCatching {
                wrapper.stopRecordingAndAwaitResult()
            }.getOrNull()
        }

        // Determine the best final file paths (prefer SDK-provided values; fall back to planned)
        val finalFilteredPath: String?
        val finalRawPath: String?

        if (result != null) {
            finalFilteredPath = result.prefilteredPath ?: lastPlannedPref
            finalRawPath = result.rawPath ?: lastPlannedRaw
        } else {
            finalFilteredPath = lastPlannedPref
            finalRawPath = lastPlannedRaw
        }

        // Clear planned paths so they don't linger for next capture
        lastPlannedPref = null
        lastPlannedRaw = null

        // Validate file existence and minimal size
        val filteredFile = finalFilteredPath?.let { File(it) }
        val rawFile = finalRawPath?.let { File(it) }

        val filteredOk = filteredFile?.exists() == true && filteredFile.length() > 44
        val rawOk = rawFile?.exists() == true && rawFile.length() > 44

        // Decide what to navigate with depending on whether a filter was requested
        val filterRequested = _preFilter.value.toSdk() != null

        val navFilteredPath: String?
        val navRawPath: String?

        if (filterRequested) {
            if (filteredOk && rawOk) {
                navFilteredPath = finalFilteredPath
                navRawPath = finalRawPath
            } else {
                navFilteredPath = null
                navRawPath = null
            }
        } else {
            if (rawOk) {
                navFilteredPath = finalRawPath
                navRawPath = finalRawPath
            } else {
                navFilteredPath = null
                navRawPath = null
            }
        }

        if (navFilteredPath != null) {
            currentFilteredWav = File(navFilteredPath)
            currentRawWav = navRawPath?.let { File(it) }

            // Unique placeholder ID so sessions never overwrite each other
            val placeholderId = "REC-${System.currentTimeMillis()}"

            val timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))

            val minimalSession = Session(
                patientName = "",
                patientId = placeholderId,
                age = "",
                sex = "",
                height = "",
                weight = "",
                bmi = "",
                sessionStart = timestamp,
                deviceModel = "",
                notes = "",
                posture = "",
                position = "",
                wavPath = navFilteredPath,
                rawWavPath = navRawPath ?: navFilteredPath,
                pdfPath = null
            )

            SessionRepository.add(minimalSession)

            _effects.emit(RecordingEffect.NavigateAfterSave(navFilteredPath, navRawPath))
            _state.value = RecordingState.Idle
        } else {
            _state.value = RecordingState.Idle
        }
    }

    fun lastWavPath(): String? = currentFilteredWav?.absolutePath
    fun lastRawWavPath(): String? = currentRawWav?.absolutePath
}
