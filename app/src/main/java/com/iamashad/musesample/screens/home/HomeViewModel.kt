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
import com.iamashad.musesample.wrapper.UsbHelper
import com.iamashad.musesample.wrapper.ConnectionState
import com.iamashad.musesample.wrapper.TaalSdkHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Device categories shown in the picker. */
enum class DeviceKind { STETHOSCOPE, ECG }

/** Simple UI model for a USB device row in the sheet. */
data class UiDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val available: Boolean,
    val usbDevice: UsbDevice? = null
)

/**
 * ViewModel powering the Home screen device UX.
 *
 * Responsibilities:
 * - Reflect wrapper connection as a UI-friendly [ConnectivityStatus].
 * - Discover USB devices and present them in the picker.
 * - Orchestrate the "connect with permission" flow.
 *
 * - We DO NOT auto-start the monitor in init. The user explicitly connects.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val wrapper = TaalSdkHolder.get(app)

    /** Banner state; Connected shows a friendly name rather than raw VID/PID. */
    val connectivityStatus = wrapper.connection
        .map { st ->
            when (st) {
                is ConnectionState.Connected -> ConnectivityStatus.Connected("TAAL Stethoscope")
                is ConnectionState.Disconnected -> ConnectivityStatus.Disconnected
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectivityStatus.Disconnected)

    /** List rendered in the device picker. */
    val devices = MutableStateFlow<List<UiDevice>>(emptyList())

    /** We start/stop the monitor explicitly, not in init. */
    private var monitorStarted = false

    /** Keep the picker up to date with USB attach/detach and permission changes. */
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
        // Register presence receiver only; do not start monitoring here.
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbHelper.ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            app.registerReceiver(usbPresenceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.registerReceiver(app, usbPresenceReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        refreshDevices()
    }

    /** Build the picker list from current attached USB devices. */
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
     * User chooses a device:
     * 1) Request OS-level USB permission for that device.
     * 2) Start (or restart) the TAAL monitor if permission granted.
     * 3) Force a connection poll so the Start Session button enables quickly.
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

            if (!monitorStarted) {
                wrapper.startDeviceMonitor()
                monitorStarted = true
            } else {
                wrapper.stopDeviceMonitor()
                wrapper.startDeviceMonitor()
            }

            wrapper.pollConnectionNow()
            refreshDevices()
        }
    }

    /** Optional: manual disconnect from UI. */
    fun disconnect() {
        if (monitorStarted) {
            wrapper.stopDeviceMonitor()
            monitorStarted = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { app.unregisterReceiver(usbPresenceReceiver) }
        // Do not leave the monitor running if the VM is cleared.
        if (monitorStarted) {
            wrapper.stopDeviceMonitor()
            monitorStarted = false
        }
    }
}
