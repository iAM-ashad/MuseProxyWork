package com.iamashad.musesample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

open class ViewModel : ViewModel() {

    private val _connectivityStatus =
        MutableStateFlow<ConnectivityStatus>(ConnectivityStatus.Disconnected)
    val connectivityStatus = _connectivityStatus.asStateFlow()

    val availableDevices = listOf("TAAL Stethoscope", "TAAL ECG")

    fun connectToDevice(deviceName: String) {
        viewModelScope.launch {
            _connectivityStatus.value = ConnectivityStatus.Connecting
            delay(2000)
            _connectivityStatus.value = ConnectivityStatus.Connected(deviceName)

        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _connectivityStatus.value = ConnectivityStatus.Disconnected
        }
    }
}