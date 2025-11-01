package com.iamashad.musesample.screens.home

sealed class ConnectivityStatus {
    object Disconnected : ConnectivityStatus()
    object Connecting: ConnectivityStatus()
    data class Connected(val deviceName: String) : ConnectivityStatus()
}