package com.iamashad.musesample.screens.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iamashad.musesample.screens.home.ConnectivityStatus
import com.iamashad.musesample.wrapper.UsbHelper
import com.iamashad.musesample.wrapper.ConnectionState
import com.iamashad.musesample.wrapper.TaalSdkHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class DeviceKind { STETHOSCOPE, ECG }

data class UiDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val available: Boolean,
    val usbDevice: UsbDevice? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val wrapper = TaalSdkHolder.get(app)

    /** Banner state reflects ONLY the TAAL monitor; starts as Disconnected until user taps Connect. */
    val connectivityStatus = wrapper.connection
        .map { st ->
            when (st) {
                is ConnectionState.Connected -> ConnectivityStatus.Connected("TAAL Stethoscope")
                is ConnectionState.Disconnected -> ConnectivityStatus.Disconnected
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectivityStatus.Disconnected)

    /** List shown in the device picker. */
    val devices = MutableStateFlow<List<UiDevice>>(emptyList())

    /** Are we currently running TAAL monitor (we control when it starts/stops)? */
    private var monitorStarted = false

    /** Presence receiver: updates picker on USB attach/detach & permission result. */
    private val usbPresenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED,
                UsbHelper.ACTION_USB_PERMISSION -> refreshDevices()
            }
        }
    }

    init {
        // IMPORTANT: do NOT start TAAL monitor here (prevents auto-connect).
        // We only register a presence receiver to keep the picker fresh.
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbHelper.ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            app.registerReceiver(usbPresenceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.registerReceiver(
                app,
                usbPresenceReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        refreshDevices()
    }

    /** Builds the picker list from current USB devices. */
    fun refreshDevices() {
        val mgr = app.getSystemService(Context.USB_SERVICE) as UsbManager
        val list = mgr.deviceList.values.toList()

        val stethoscopes = list.map { d ->
            UiDevice(
                id = d.deviceId.toString(),
                name = d.productName ?: "USB Device ${d.deviceId}",
                kind = DeviceKind.STETHOSCOPE,
                available = true,
                usbDevice = d
            )
        }

        val ecgPlaceholder = UiDevice(
            id = "ecg-placeholder",
            name = "Digital ECG (coming soon)",
            kind = DeviceKind.ECG,
            available = false
        )

        devices.value = stethoscopes + ecgPlaceholder
    }

    /**
     * User presses "Connect" in the dialog:
     * 1) Request USB permission for the selected device.
     * 2) Start TAAL monitor (only now!), which lets the SDK emit Connected/Disconnected.
     * 3) If permission denied, keep monitor stopped.
     */
    fun connectTo(device: UiDevice) {
        viewModelScope.launch {
            val usb = device.usbDevice ?: return@launch
            if (!device.available) return@launch

            val granted = UsbHelper.requestPermission(app, usb)
            if (!granted) {
                refreshDevices()
                return@launch
            }

            // Start monitoring only after explicit Connect
            if (!monitorStarted) {
                wrapper.startDeviceMonitor()
                monitorStarted = true
            } else {
                wrapper.stopDeviceMonitor()
                wrapper.startDeviceMonitor()
            }

            // 🔁 Force an immediate state snapshot so Start Session can enable right away
            wrapper.pollConnectionNow()

            // (optional) tiny delay + second poll if you want to be extra safe
            // delay(150); wrapper.pollConnectionNow()

            refreshDevices()
        }
    }


    /**
     * Optional call if you add a "Disconnect" button in UI.
     * This guarantees no further auto flips while the monitor is off.
     */
    fun disconnect() {
        if (monitorStarted) {
            wrapper.stopDeviceMonitor()
            monitorStarted = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { app.unregisterReceiver(usbPresenceReceiver) }
        // Do not leave monitor running if user navigates away from Home completely:
        // (comment this out if you want it to persist)
        if (monitorStarted) {
            wrapper.stopDeviceMonitor()
            monitorStarted = false
        }
    }
}
