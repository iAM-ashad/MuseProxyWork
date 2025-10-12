package com.iamashad.musesample.screens.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iamashad.musesample.audio.FakeRecorder
import com.iamashad.musesample.audio.Recorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class RecordingViewModel(
    application: Application
) : AndroidViewModel(application) {
    // Swap FakeRecorder → TaalRecorder later without changing UI:
    private val recorder: Recorder = FakeRecorder(application)

    private val _state = MutableStateFlow(RecordingState.Idle)
    val state = _state.asStateFlow()

    private var currentWav: File? = null

    fun toggleRecording() {
        viewModelScope.launch {
            if (_state.value == RecordingState.Idle) {
                currentWav = recorder.startRecording()
                _state.value = RecordingState.Recording
            } else {
                currentWav = recorder.stopRecording()
                _state.value = RecordingState.Idle
            }
        }
    }

    fun lastWavPath(): String? = currentWav?.absolutePath
}