package com.iamashad.musesample

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.iamashad.musesample.audio.bandpassFilter
import com.iamashad.musesample.audio.downsampleWaveform
import com.iamashad.musesample.audio.readNumChannels
import com.iamashad.musesample.audio.readSampleRate
import com.iamashad.musesample.audio.readWavPcm16
import com.iamashad.musesample.ml.runSegmentationOverClip
import com.iamashad.musesample.model.PcgReportMeta
import com.iamashad.musesample.print.buildStackedPcgBitmap
import com.iamashad.musesample.utils.TAG_PCG_DEBUG
import com.iamashad.musesample.utils.logAndDumpSegments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/* ---------------------------------------------------------------------------
 * Utilities to turn a recorded WAV into a printable 2-page PDF report.
 * --------------------------------------------------------------------------- */

/** Encode a [Bitmap] as Base64 (PNG). Used for inlining into the HTML <img src="data:...">. */
fun bitmapToBase64Png(bmp: Bitmap, quality: Int = 100): String {
    val bos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, quality, bos)
    return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
}

/**
 * Build the HTML for the two-page report.
 */
fun buildHtml(meta: PcgReportMeta, base64Png: String): String = """
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8"/>
        <style>
          @page { size: A4; margin: 18mm; }
          * { box-sizing: border-box; }
          body { font-family: -apple-system, Roboto, "Segoe UI", Arial, sans-serif; color:#222; margin:0; }
          .page { page-break-after: always; }
          .header { display:flex; justify-content:space-between; align-items:center; margin-bottom:18px; }
          .logo img { height:192px; }
          .title-block { text-align:right; }
          .title { font-size:44px; font-weight:800; color:#0b4da2; }
          .meta  { font-size:20px; color:#555; }
          .card { border:1.4px solid #d1d5db; border-radius:12px; padding:18px; background:#fafafa; }
          h3 { font-size:24px; margin:14px 0 10px; color:#0b4da2; border-bottom:1px solid #e5e7eb; padding-bottom:4px; }
          table { border-collapse:collapse; width:100%; }
          th, td { padding:8px 10px; border-bottom:1px solid #eee; font-size:20px; }
          th { text-align:left; color:#555; font-weight:600; width:36%; }
          .two-col { display:flex; gap:16px; }
          .two-col .card { flex:1; }
          .footer { margin-top:28px; font-size:20px; text-align:center; color:#666; }
          .page-break { page-break-before:always; }
          .wave-wrapper { display:flex; justify-content:center; align-items:flex-start; min-height:245mm; }
          .wave-card {
            border:2px solid #b0c4de; border-radius:12px; padding:12px; background:#fff;
            max-width:300mm; width:100%; margin:0 auto; box-shadow:0 2mm 4mm rgba(0,0,0,0.10);
          }
          .wave-title { font-size:24px; font-weight:700; text-align:center; color:#0b4da2; margin:8px 0 12px; }
          .wave img { display:block; margin:0 auto; width:100%; height:auto; }
        </style>
      </head>
      <body>

        <!-- PAGE 1 -->
        <div class="page">
          <div class="header">
            <div class="logo">
              <img src="https://appassets.androidplatform.net/res/drawable/muse_logo.png" alt="Clinic Logo"/>
            </div>
            <div class="title-block">
              <div class="title">PCG Session Report</div>
              <div class="meta">Generated: ${
    java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
}</div>
            </div>
          </div>

          <div class="two-col">
            <div class="card">
              <h3>Patient Information</h3>
              <table>
                <tr><th>Name</th><td>${meta.patientName} (ID: ${meta.patientId})</td></tr>
                <tr><th>Age</th><td>${meta.age.ifBlank { "—" }}</td></tr>
                <tr><th>Sex</th><td>${meta.sex.ifBlank { "—" }}</td></tr>
                <tr><th>Height</th><td>${meta.height.ifBlank { "—" }}</td></tr>
                <tr><th>Weight</th><td>${meta.weight.ifBlank { "—" }}</td></tr>
                <tr><th>BMI</th><td>${meta.bmi.ifBlank { "—" }}</td></tr>
                <tr><th>Posture</th><td>${meta.posture.ifBlank { "—" }}</td></tr>
                <tr><th>Position</th><td>${meta.position.ifBlank { "—" }}</td></tr>
              </table>
            </div>

            <div class="card">
              <h3>Session Details</h3>
              <table>
                <tr><th>Session Start</th><td>${meta.sessionStart}</td></tr>
                <tr><th>Device</th><td>${meta.deviceModel}</td></tr>
                <tr><th>Notes</th><td>${meta.notes.ifBlank { "—" }}</td></tr>
              </table>
            </div>
          </div>

          <div class="footer">
            © ${java.time.Year.now()} Muse Diagnostics — Phonocardiogram Analysis Report.
          </div>
        </div>

        <!-- PAGE 2 -->
        <div class="page-break">
          <div class="wave-wrapper">
            <div class="wave-card">
              <div class="wave-title">PHONOCARDIOGRAM</div>
              <img alt="PCG Waveform" src="data:image/png;base64,$base64Png"/>
            </div>
          </div>
        </div>

      </body>
    </html>
""".trimIndent()

/** Convert given HTML to a PDF file via WebView print job. */
suspend fun htmlToPdf(
    context: Context,
    html: String,
    outFile: File = defaultPdfLocation(context)
): File = withContext(Dispatchers.Main) {
    val webView = WebView(context).apply {
        settings.javaScriptEnabled = false
    }

    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    try {
        withTimeout(20_000L) {
            suspendCancellableCoroutine { cont ->
                webView.webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String?) {
                        try {
                            outFile.parentFile?.mkdirs()
                            if (outFile.exists()) outFile.delete()

                            val pfd = ParcelFileDescriptor.open(
                                outFile,
                                ParcelFileDescriptor.MODE_READ_WRITE or
                                        ParcelFileDescriptor.MODE_CREATE or
                                        ParcelFileDescriptor.MODE_TRUNCATE
                            )

                            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    android.print.writeWebViewToPdf(webView, pfd)
                                    if (!cont.isCompleted) cont.resume(outFile)
                                } catch (t: Throwable) {
                                    if (!cont.isCompleted) cont.resumeWithException(t)
                                }
                            }
                        } catch (t: Throwable) {
                            if (!cont.isCompleted) cont.resumeWithException(t)
                        }
                    }
                }

                webView.loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }
    } finally {
        webView.destroy()
    }
}

private fun defaultPdfLocation(context: Context): File {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    return File(dir, "PCG_Report_${System.currentTimeMillis()}.pdf")
}

/**
 * High-level entry point: takes a WAV path + metadata and returns a generated PDF file.
 */
suspend fun generatePcgPdf(
    context: Context,
    wavPath: String,
    meta: PcgReportMeta
): File {
    val t0 = System.currentTimeMillis()
    Log.d(TAG_PCG_DEBUG, "=== Report generation started ===")
    Log.d(TAG_PCG_DEBUG, "WAV Path: $wavPath")

    val file = File(wavPath)
    require(file.exists()) { "WAV file does not exist at path: $wavPath" }

    // 1) Parse WAV headers
    val sampleRate = readSampleRate(file)
    val numChannels = readNumChannels(file)
    Log.d(TAG_PCG_DEBUG, "SampleRate=$sampleRate Hz, Channels=$numChannels")

    // 2) Read PCM samples
    val pcm = readWavPcm16(file, numChannels)
    val totalDuration = pcm.size.toFloat() / sampleRate
    Log.d(TAG_PCG_DEBUG, "Audio duration: $totalDuration s")

    // 3) Filter and downsample
    val filtered = bandpassFilter(pcm, sampleRate)
    val normalized = downsampleWaveform(filtered, 3200)
    Log.d(TAG_PCG_DEBUG, "Downsampled waveform: ${normalized.size} pts")

    // 4) Segmentation inference
    // 4) Segmentation inference
    // choose datasetFlag based on your app knowledge; default 0 if unknown
    val datasetFlag = 0L // or 1L if this clip is Physionet2022-like

    val segments = try {
        runSegmentationOverClip(
            context = context,
            pcm = filtered,
            originalSampleRate = sampleRate,
            datasetFlag = datasetFlag,
            metaFromReport = meta, // your PcgReportMeta
            medianKernel = 5,
            inferenceBatchSize = 8
        )
    } catch (t: Throwable) {
        Log.w(TAG_PCG_DEBUG, "Segmentation failed: ${t.message}")
        emptyList()
    }

    logAndDumpSegments(TAG_PCG_DEBUG, segments)

    // 5) Draw waveform + segments
    val bmp = buildStackedPcgBitmap(
        context = context,
        normalized = normalized,
        secondsTotal = totalDuration,
        segmentSec = 5f,
        widthPx = 2400,
        heightPx = 1200,
        rowSpacingPx = 40,
        segments = segments
    )

    // 6) Build HTML → PDF
    val base64 = bitmapToBase64Png(bmp)
    bmp.recycle()
    val html = buildHtml(meta, base64)
    val pdf = htmlToPdf(context, html)

    Log.d(TAG_PCG_DEBUG, "PDF generated successfully in ${System.currentTimeMillis() - t0} ms")
    return pdf
}
