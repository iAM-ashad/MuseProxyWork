package com.iamashad.musesample.screens.home

/**
 * UI-facing connection banner states for the TAAL device.
 *
 * Source of truth:
 * - Derived from the SDK wrapper's [com.iamashad.musesample.wrapper.ConnectionState].
 * - The ViewModel maps wrapper states → this sealed class for presentation.
 */

sealed class ConnectivityStatus {
    /** No active connection and not attempting one. */
    object Disconnected : ConnectivityStatus()

    /** Transient spinner state while the user initiates a connection. */
    object Connecting : ConnectivityStatus()

    /** Connected to a device; [deviceName] is shown in the UI card. */
    data class Connected(val deviceName: String) : ConnectivityStatus()
}
