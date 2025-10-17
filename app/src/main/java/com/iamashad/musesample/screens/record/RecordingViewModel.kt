package com.iamashad.musesample.screens.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iamashad.musesample.audio.FakeRecorder
import com.iamashad.musesample.audio.Recorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface RecordingEffect {
    data class NavigateToMetadata(val path: String?) : RecordingEffect
}

class RecordingViewModel(
    application: Application
) : AndroidViewModel(application) {

    // Swap FakeRecorder → real implementation later without changing UI
    private val recorder: Recorder = FakeRecorder(application)

    private val _state = MutableStateFlow(RecordingState.Idle)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<RecordingEffect>()
    val effects: SharedFlow<RecordingEffect> = _effects

    private var currentWav: File? = null

    fun toggleRecording() {
        when (_state.value) {
            RecordingState.Idle, RecordingState.Complete -> startRecording()
            RecordingState.Recording -> stopAndSave()
            RecordingState.Saving -> Unit // ignore taps while saving
        }
    }

    private fun startRecording() = viewModelScope.launch {
        currentWav = recorder.startRecording()
        _state.value = RecordingState.Recording
    }

    private fun stopAndSave() = viewModelScope.launch {
        _state.value = RecordingState.Saving
        // Do the actual stop/saving work off the main thread
        val savedFile = withContext(Dispatchers.IO) {
            recorder.stopRecording() // return file after fully written
        }
        currentWav = savedFile
        // Emit effect so UI can navigate with the path
        _effects.emit(RecordingEffect.NavigateToMetadata(currentWav?.absolutePath))
        // Optionally mark complete or reset to Idle. We'll reset to Idle.
        _state.value = RecordingState.Idle
    }

    fun lastWavPath(): String? = currentWav?.absolutePath
}
