package com.iamashad.musesample

sealed class ConnectivityStatus {
    object Disconnected : ConnectivityStatus()
    object Connecting: ConnectivityStatus()
    data class Connected(val deviceName: String) : ConnectivityStatus()
}