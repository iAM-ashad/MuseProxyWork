// package: com.iamashad.musesample
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
import com.iamashad.musesample.audio.downsampleWaveform
import com.iamashad.musesample.audio.readWavMono16
import com.iamashad.musesample.ml.Diagnostics
import com.iamashad.musesample.ml.runSegmentationOverClip
import com.iamashad.musesample.model.PcgReportMeta
import com.iamashad.musesample.model.WavData
import com.iamashad.musesample.print.buildStackedPcgBitmap
import com.iamashad.musesample.utils.TAG_PCG_DEBUG
import com.iamashad.musesample.utils.logAndDumpSegments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Encode Bitmap to base64 PNG */
fun bitmapToBase64Png(bmp: Bitmap, quality: Int = 100): String {
    val bos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, quality, bos)
    return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
}

/** Build HTML for the report */
fun buildHtml(meta: PcgReportMeta, base64Png: String): String = """
<!doctype html>
<html>
  <head>
    <meta charset="utf-8"/>
    <style>
      @page { size: A4; margin: 18mm; }
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

      /* Waveform page */
      .wave-wrapper {
        display:flex;
        justify-content:center;
        align-items:flex-start;
        padding-top:4mm;
        padding-bottom:4mm;
        page-break-inside: avoid;
        break-inside: avoid;
      }
      .wave-card {
        border:2px solid #b0c4de;
        border-radius:12px;
        padding:12px;
        background:#fff;
        max-width:185mm;
        width:100%;
        margin:0 auto;
        box-shadow:0 2mm 4mm rgba(0,0,0,0.10);
        page-break-inside: avoid;
        break-inside: avoid;
      }
      .wave-title {
        font-size:24px;
        font-weight:700;
        text-align:center;
        color:#0b4da2;
        margin:4px 0 10px;
      }
      .wave img {
        display:block;
        margin:0 auto;
        width:100%;
        height:auto;
        max-height:250mm;
        object-fit:contain;
        page-break-inside: avoid;
        break-inside: avoid;
      }

      /* Legend */
      .legend {
        display:flex;
        justify-content:center;
        align-items:center;
        gap:24px;
        margin:8px 0 12px;
        font-size:18px;
        font-weight:600;
        color:#0b4da2;
        page-break-inside: avoid;
        break-inside: avoid;
      }
      .legend-item { display:flex; align-items:center; gap:8px; }
      .legend-box {
        width:18px;
        height:18px;
        border:1.2px solid #0b4da2;
      }
      .s1-box { background-color: rgba(220, 38, 38, 0.7); }
      .systole-box { background-color: rgba(34, 197, 94, 0.6); }
      .s2-box { background-color: rgba(37, 99, 235, 0.7); }
      .diastole-box { background-color: rgba(255, 140, 0, 0.5); }
    </style>
  </head>
  <body>
    <div class="page">
      <div class="header">
        <div class="logo">
          <img src="https://appassets.androidplatform.net/res/drawable/muse_logo.png" alt="Logo"/>
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
            <tr><th>Name</th><td>${meta.patientName}</td></tr>
            <tr><th>Patient-ID</th><td>${meta.patientId}</td></tr>
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

    <div class="page-break">
      <div class="wave-wrapper">
        <div class="wave-card">
          <div class="wave-title">PHONOCARDIOGRAM</div>

          <div class="legend">
            <div class="legend-item">
              <div class="legend-box s1-box"></div><span>S1</span>
            </div>
            <div class="legend-item">
              <div class="legend-box systole-box"></div><span>Systole</span>
            </div>
            <div class="legend-item">
              <div class="legend-box s2-box"></div><span>S2</span>
            </div>
            <div class="legend-item">
              <div class="legend-box diastole-box"></div><span>Diastole</span>
            </div>
          </div>

          <div class="wave">
            <img alt="PCG Waveform" src="data:image/png;base64,$base64Png"/>
          </div>
        </div>
      </div>
    </div>
  </body>
</html>
""".trimIndent()

suspend fun htmlToPdf(
    context: Context,
    html: String,
    outFile: File = defaultPdfLocation(context)
): File = withContext(Dispatchers.Main) {
    val webView = WebView(context).apply { settings.javaScriptEnabled = false }
    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    val parent = outFile.parentFile ?: context.filesDir
    if (!parent.exists()) parent.mkdirs()
    val tempFile = File(
        parent,
        outFile.nameWithoutExtension + "_tmp_" + System.currentTimeMillis() + ".pdf"
    )

    try {
        withTimeout(20_000L) {
            suspendCancellableCoroutine<File> { cont ->
                webView.webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? =
                        assetLoader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String?) {
                        try {
                            val pfd = ParcelFileDescriptor.open(
                                tempFile,
                                ParcelFileDescriptor.MODE_READ_WRITE or
                                        ParcelFileDescriptor.MODE_CREATE or
                                        ParcelFileDescriptor.MODE_TRUNCATE
                            )
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    android.print.writeWebViewToPdf(webView, pfd)

                                    if (outFile.exists() && outFile != tempFile) {
                                        outFile.delete()
                                    }

                                    val finalFile = if (tempFile.renameTo(outFile)) {
                                        outFile
                                    } else {
                                        Log.w(
                                            TAG_PCG_DEBUG,
                                            "Failed to rename temp PDF to target; using temp path."
                                        )
                                        tempFile
                                    }

                                    if (!cont.isCompleted) cont.resume(finalFile)
                                } catch (t: Throwable) {
                                    if (!cont.isCompleted) cont.resumeWithException(t)
                                }
                            }
                        } catch (t: Throwable) {
                            if (!cont.isCompleted) cont.resumeWithException(t)
                        }
                    }

                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: android.webkit.RenderProcessGoneDetail
                    ): Boolean {
                        if (!cont.isCompleted) {
                            cont.resumeWithException(
                                IllegalStateException("WebView renderer died")
                            )
                        }
                        return true
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
        if (tempFile.exists() && tempFile != outFile && outFile.length() == 0L) {
            tempFile.delete()
        }
    }
}

private fun defaultPdfLocation(context: Context): File {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    return File(dir, "PCG_Report_${System.currentTimeMillis()}.pdf")
}

/**
 * High-level entry:
 *  - Visual PCG uses the SDK-filtered WAV (what the clinician hears).
 *  - ML segmentation uses the raw WAV (if available) to match training.
 */
suspend fun generatePcgPdf(
    context: Context,
    filteredWavPath: String,
    rawWavPath: String?,
    meta: PcgReportMeta,
    outFile: File? = null
): File {
    val t0 = System.currentTimeMillis()
    Log.d(TAG_PCG_DEBUG, "=== Report generation started ===")
    Log.d(TAG_PCG_DEBUG, "Filtered WAV (visual) path: $filteredWavPath")
    Log.d(TAG_PCG_DEBUG, "Raw WAV (ML) path: $rawWavPath")

    // 1) Load WAVs
    val filtered: WavData = withContext(Dispatchers.IO) {
        readWavMono16(filteredWavPath)
    }

    val raw: WavData = withContext(Dispatchers.IO) {
        try {
            if (!rawWavPath.isNullOrBlank() && rawWavPath != filteredWavPath) {
                readWavMono16(rawWavPath)
            } else {
                filtered
            }
        } catch (t: Throwable) {
            Log.w(
                TAG_PCG_DEBUG,
                "Failed to load raw WAV ($rawWavPath), falling back to filtered. Reason=${t.message}"
            )
            filtered
        }
    }

    require(filtered.samples.isNotEmpty()) {
        "Filtered WAV decode produced empty samples: $filteredWavPath"
    }
    require(raw.samples.isNotEmpty()) {
        "Raw WAV decode produced empty samples."
    }

    val totalDuration = raw.samples.size.toFloat() / raw.sampleRate.toFloat()
    Log.d(
        TAG_PCG_DEBUG,
        "Filtered: SR=${filtered.sampleRate}Hz, n=${filtered.samples.size}; " +
                "Raw: SR=${raw.sampleRate}Hz, n=${raw.samples.size}; duration=$totalDuration s"
    )

    // 2) Visual pipeline (filtered → compressed, resampled waveform)
    val normalized = downsampleWaveform(filtered.samples, targetCount = 3200)
    Log.d(TAG_PCG_DEBUG, "Downsampled waveform: ${normalized.size} pts")

    // 3) ML segmentation (raw)
    val datasetFlag = 0L
    val segments = try {
        runSegmentationOverClip(
            context = context,
            pcm = raw.samples,
            originalSampleRate = raw.sampleRate,
            datasetFlag = datasetFlag,
            metaFromReport = meta,
            medianKernel = 5,
            inferenceBatchSize = 8
        )
    } catch (t: Throwable) {
        Log.w(TAG_PCG_DEBUG, "Segmentation failed: ${t.message}")
        t.printStackTrace()
        emptyList()
    }

    // 4) Diagnostics + bitmap
    logAndDumpSegments(TAG_PCG_DEBUG, segments)
    val samplesPerSec = normalized.size.toFloat() / totalDuration

    Diagnostics.logSummary(segments)

    val peaks = Diagnostics.detectPeaks(normalized, halfWin = 8, minProminence = 0.25f)
    val matched = Diagnostics.matchPeaksToSegments(peaks, normalized, samplesPerSec, segments)
    Log.d(
        "PCG_DIAG",
        "Detected peaks=${peaks.size}; matched labels: ${matched.mapValues { it.value.size }}"
    )

    val cycles = Diagnostics.findBeatCycles(segments)
    Diagnostics.logBeatStats(cycles)

    val csv = Diagnostics.findSuspiciousPeaksCsv(
        normalized,
        samplesPerSec,
        segments,
        halfWin = 8,
        minProminence = 0.25f
    )
    Log.d("PCG_DIAG", "Suspicious peaks CSV:\n$csv")

    val bmp = buildStackedPcgBitmap(
        context = context,
        normalized = normalized,
        secondsTotal = totalDuration,
        segmentSec = 6f,
        widthPx = 2400,
        heightPx = 3200,
        rowSpacingPx = 50,
        segments = segments
    )

    val base64 = bitmapToBase64Png(bmp)
    bmp.recycle()

    val html = buildHtml(meta, base64)
    val pdf = htmlToPdf(
        context = context,
        html = html,
        outFile = outFile ?: defaultPdfLocation(context)
    )

    Log.d(TAG_PCG_DEBUG, "PDF generated successfully in ${System.currentTimeMillis() - t0} ms")
    return pdf
}
