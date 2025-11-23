@file:Suppress("unused")

package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Print a WebView’s current content into a PDF file descriptor.
 *
 * Placed in `package android.print` to access nested framework callback constructors.
 */
suspend fun writeWebViewToPdf(
    webView: WebView,
    outputPfd: ParcelFileDescriptor,
    attrs: PrintAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build(),
    jobName: String = "pcg_report"
): Unit = suspendCancellableCoroutine { cont ->
    val adapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(jobName)

    val layoutCallback = object : PrintDocumentAdapter.LayoutResultCallback() {
        override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
            val writeCallback = object : PrintDocumentAdapter.WriteResultCallback() {
                override fun onWriteFinished(pageRanges: Array<out PageRange>?) {
                    try {
                        outputPfd.close()
                    } catch (_: Throwable) {
                    }
                    cont.resume(Unit)
                }

                override fun onWriteFailed(error: CharSequence?) {
                    try {
                        outputPfd.close()
                    } catch (_: Throwable) {
                    }
                    cont.resumeWithException(
                        IllegalStateException(error?.toString() ?: "Write failed")
                    )
                }
            }

            adapter.onWrite(
                arrayOf(PageRange.ALL_PAGES),
                outputPfd,
                CancellationSignal(),
                writeCallback
            )
        }

        override fun onLayoutFailed(error: CharSequence?) {
            cont.resumeWithException(
                IllegalStateException(error?.toString() ?: "Layout failed")
            )
        }
    }

    adapter.onLayout(
        attrs,
        attrs,
        CancellationSignal(),
        layoutCallback,
        Bundle()
    )
}
