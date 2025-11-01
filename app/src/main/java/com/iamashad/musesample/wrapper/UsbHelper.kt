package com.iamashad.musesample.wrapper

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UsbHelper {
    const val ACTION_USB_PERMISSION = "com.iamashad.musesample.USB_PERMISSION"

    suspend fun requestPermission(context: Context, device: UsbDevice): Boolean =
        withContext(Dispatchers.Main) {
            val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
            if (mgr.hasPermission(device)) return@withContext true

            val result = CompletableDeferred<Boolean>()
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_USB_PERMISSION) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        runCatching { ctx?.unregisterReceiver(this) }
                        result.complete(granted)
                    }
                }
            }

            // Android 14+: explicit NOT_EXPORTED flag for custom/unprotected broadcast
            if (Build.VERSION.SDK_INT >= 34) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }

            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE
            )
            mgr.requestPermission(device, pi)

            result.await()
        }
}
