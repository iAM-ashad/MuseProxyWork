package com.iamashad.musesample

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.iamashad.musesample.audio.bandpassFilter
import com.iamashad.musesample.audio.downsampleWaveform
import com.iamashad.musesample.audio.readNumChannels
import com.iamashad.musesample.audio.readSampleRate
import com.iamashad.musesample.audio.readWavPcm16
import com.iamashad.musesample.model.PcgReportMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ───────────────────────────────
// Core PCG waveform renderer
// ───────────────────────────────
/*
fun buildPcgWaveformBitmap(
    context: Context,
    normalized: List<Float>,
    secondsTotal: Float,
    widthPx: Int = 1800,
    heightPx: Int = 700
): Bitmap {
    val chart = LineChart(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(widthPx, heightPx)
        setBackgroundColor(Color.WHITE)
        description = Description().apply { text = "" }
        legend.isEnabled = false
        setTouchEnabled(false)
        setPinchZoom(false)
        axisRight.isEnabled = false

        // X axis (time)
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(true)
            gridColor = Color.LTGRAY
            textColor = Color.DKGRAY
            textSize = 10f
        }

        // Y axis (amplitude)
        axisLeft.apply {
            setDrawGridLines(true)
            gridColor = Color.LTGRAY
            textColor = Color.DKGRAY
            textSize = 10f
            axisMinimum = -1100f
            axisMaximum = 1100f
        }
    }

    val entries = normalized.mapIndexed { i, y ->
        val x = if (normalized.size <= 1) 0f else i * (secondsTotal / (normalized.size - 1))
        Entry(x, y)
    }

    val dataSet = LineDataSet(entries, "PCG").apply {
        setDrawCircles(false)
        setDrawValues(false)
        lineWidth = 1.3f
        color = Color.BLACK
        mode = LineDataSet.Mode.CUBIC_BEZIER
    }

    chart.data = LineData(dataSet)
    chart.xAxis.axisMinimum = 0f
    chart.xAxis.axisMaximum = maxOf(secondsTotal, 1f)

    // Layout offscreen
    val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
    val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    chart.measure(wSpec, hSpec)
    chart.layout(0, 0, widthPx, heightPx)

    val bmp = createBitmap(widthPx, heightPx)
    val canvas = Canvas(bmp)
    chart.draw(canvas)
    return bmp
}*/
// In your main file
fun buildPcgWaveformBitmap(
    context: Context,
    normalized: List<Float>,
    secondsTotal: Float, // THIS VALUE IS KEY
    widthPx: Int = 1800,
    heightPx: Int = 700
): Bitmap {
    Log.d("PCG_DEBUG", "buildPcgWaveformBitmap: secondsTotal=$secondsTotal, normalized.size=${normalized.size}")

    val chart = LineChart(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(widthPx, heightPx)
        setBackgroundColor(Color.WHITE)
        description = Description().apply { text = "" }
        legend.isEnabled = false
        setTouchEnabled(false)
        setPinchZoom(false)
        axisRight.isEnabled = false

        // X axis (time)
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(true)
            gridColor = Color.LTGRAY
            textColor = Color.DKGRAY
            textSize = 10f
        }

        // Y axis (amplitude)
        axisLeft.apply {
            setDrawGridLines(true)
            gridColor = Color.LTGRAY
            textColor = Color.DKGRAY
            textSize = 10f
            axisMinimum = -1100f
            axisMaximum = 1100f
        }
    }

    val numPoints = normalized.size
    val numTimeSteps = (numPoints / 2).coerceAtLeast(1) // Ensure at least 1 for division
    Log.d("PCG_DEBUG", "buildPcgWaveformBitmap: numPoints=${numPoints}, numTimeSteps=${numTimeSteps}")

    val entries = normalized.mapIndexed { i, y ->
        val segmentIndex = i / 2
        val x = segmentIndex * (secondsTotal / numTimeSteps.toFloat()) // Calculate x

        // Log a few sample entries to see their x and y values
        if (i < 10 || i > numPoints - 10) { // Log first few and last few
            Log.d("PCG_DEBUG_ENTRIES", "Entry $i: x=$x, y=$y")
        }
        Entry(x, y)
    }
    val dataSet = LineDataSet(entries, "PCG").apply {
        setDrawCircles(false)
        setDrawValues(false)
        lineWidth = 1.3f
        color = Color.BLACK
        mode = LineDataSet.Mode.CUBIC_BEZIER
    }

    // ... (rest of the chart setup) ...
    chart.data = LineData(dataSet)
    chart.xAxis.axisMinimum = 0f
    chart.xAxis.axisMaximum = maxOf(secondsTotal, 1f) // This should reflect the true duration
    Log.d("PCG_DEBUG", "Chart X-axis max: ${chart.xAxis.axisMaximum}")

    // ... (Layout and draw) ...
    val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
    val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    chart.measure(wSpec, hSpec)
    chart.layout(0, 0, widthPx, heightPx)

    val bmp = createBitmap(widthPx, heightPx)
    val canvas = Canvas(bmp)
    chart.draw(canvas)
    return bmp
}

// ───────────────────────────────
// Bitmap → Base64 PNG
// ───────────────────────────────
fun bitmapToBase64Png(bmp: Bitmap, quality: Int = 100): String {
    val bos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, quality, bos)
    return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
}

// ───────────────────────────────
// HTML template for the PDF report
// ───────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
fun buildHtml(meta: PcgReportMeta, base64Png: String): String {
    val logoUrl = "https://appassets.androidplatform.net/res/drawable/muse_logo.png"

    return """
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8"/>
        <style>
          body { font-family: -apple-system, Roboto, "Segoe UI", Arial, sans-serif; margin: 40px; color: #222; }
          .header { display:flex; justify-content:space-between; align-items:center; margin-bottom: 24px; }
          .logo img { height: 90px; }
          .title-block { text-align:right; }
          .title { font-size:22px; font-weight:700; color:#004d99; }
          .meta { font-size:11px; color:#555; }
          .card { border:1px solid #ddd; border-radius:10px; padding:20px; margin-top:16px; background:#fafafa; }
          table { border-collapse:collapse; width:100%; }
          th, td { padding:6px 8px; border-bottom:1px solid #eee; font-size:13px; }
          th { text-align:left; color:#555; font-weight:600; width:30%; }
          h3 { font-size:16px; margin-top:20px; color:#004d99; }
          .wave { text-align:center; margin-top:20px; }
          .wave img { max-width:100%; height:auto; border:1px solid #bbb; border-radius:6px; }
          .footer { margin-top:40px; font-size:10px; text-align:center; color:#666; }
        </style>
      </head>
      <body>
        <div class="header">
          <div class="logo">
            <img src="$logoUrl" alt="Company Logo"/>
          </div>
          <div class="title-block">
            <div class="title">PCG Session Report</div>
            <div class="meta">Generated: ${
        java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
    }</div>
          </div>
        </div>

        <div class="card">
          <h3>Patient Information</h3>
          <table>
            <tr><th>Name</th><td>${meta.patientName} (ID: ${meta.patientId})</td></tr>
            <tr><th>Age</th><td>${meta.age}</td></tr>
            <tr><th>Sex</th><td>${meta.sex}</td></tr>
            <tr><th>Height</th><td>${meta.height}</td></tr>
            <tr><th>Weight</th><td>${meta.weight}</td></tr>
            <tr><th>BMI</th><td>${meta.bmi}</td></tr>
            <tr><th>Posture / Position</th><td>${meta.posture}</td></tr>
          </table>

          <h3>Session Details</h3>
          <table>
            <tr><th>Session Start</th><td>${meta.sessionStart}</td></tr>
            <tr><th>Device</th><td>${meta.deviceModel}</td></tr>
            <tr><th>Notes</th><td>${meta.notes}</td></tr>
          </table>

          <div class="wave">
            <h3>Recorded Waveform</h3>
            <img alt="PCG Waveform" src="data:image/png;base64,$base64Png" />
          </div>
        </div>

        <div class="footer">
          © ${java.time.Year.now()} Your Clinic. Clinical Study – Internal Use Only.
        </div>
      </body>
    </html>
    """.trimIndent()
}


suspend fun htmlToPdf(
    context: Context, html: String, outFile: File = defaultPdfLocation(context)
): File = withContext(Dispatchers.Main) {
    val webView = WebView(context).apply {
        settings.javaScriptEnabled = false
    }

    // Serve res/ and assets/ at a fixed, safe origin for WebView
    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()

    try {
        withTimeout(20_000L) {
            suspendCancellableCoroutine { cont ->
                webView.webViewClient = object : WebViewClientCompat() {

                    override fun shouldInterceptRequest(
                        view: WebView, request: WebResourceRequest
                    ): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        try {
                            outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                            if (outFile.exists()) outFile.delete()
                            val pfd = ParcelFileDescriptor.open(
                                outFile,
                                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
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

                    override fun onRenderProcessGone(
                        view: WebView, detail: android.webkit.RenderProcessGoneDetail
                    ): Boolean {
                        if (!cont.isCompleted) {
                            cont.resumeWithException(
                                IllegalStateException("WebView renderer died")
                            )
                        }
                        return true
                    }
                }

                webView.loadDataWithBaseURL(/* baseUrl = */ "https://appassets.androidplatform.net/",/* data    = */
                    html,/* mimeType = */
                    "text/html",/* encoding = */
                    "utf-8",/* historyUrl = */
                    null
                )
            }
        }
    } finally {
        webView.destroy()
    }
}

// ───────────────────────────────
// File helpers + generator entrypoint
// ───────────────────────────────
private fun defaultPdfLocation(context: Context): File {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    return File(dir, "PCG_Report_${System.currentTimeMillis()}.pdf")
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun generatePcgPdf(
    context: Context,
    wavPath: String,
    meta: PcgReportMeta
): File {
    val t0 = System.currentTimeMillis()
    Log.d("PCG_DEBUG", "=== Report generation started ===")
    Log.d("PCG_DEBUG", "WAV Path: $wavPath")

    val file = File(wavPath)
    require(file.exists()) { "WAV file does not exist at path: $wavPath" } // Added safety check

    val sampleRate = readSampleRate(file)
    val numChannels = readNumChannels(file)
    val t1 = System.currentTimeMillis()
    Log.d("PCG_DEBUG", "Sample rate read in ${t1 - t0} ms. SampleRate: $sampleRate Hz, Channels: $numChannels")

    // THIS IS THE CRITICAL CALL - ensure numChannels is passed
    val pcm = readWavPcm16(file, numChannels)
    val t2 = System.currentTimeMillis()
    Log.d("PCG_DEBUG", "PCM read in ${t2 - t1} ms. Raw PCM size: ${pcm.size} samples.")

    // Calculate the TRUE duration based on the MONO-extracted PCM size
    val trueDurationSeconds = pcm.size.toFloat() / sampleRate
    Log.d("PCG_DEBUG", "Calculated true audio duration: $trueDurationSeconds seconds.")


    val filtered = bandpassFilter(pcm, sampleRate)
    val t3 = System.currentTimeMillis()
    Log.d("PCG_DEBUG", "Bandpass filter done in ${t3 - t2} ms. Filtered size: ${filtered.size}")

    val targetDownsampledPoints = 3200 // Aim for ~1600 peaks/troughs
    val normalized = downsampleWaveform(filtered, targetDownsampledPoints)
    val t4 = System.currentTimeMillis()
    Log.d("PCG_DEBUG", "Downsample done in ${t4 - t3} ms. Normalized size: ${normalized.size} points.")
    // Expect normalized.size to be close to targetDownsampledPoints (e.g., 3200)

    val bmp = buildPcgWaveformBitmap(context, normalized, trueDurationSeconds) // Pass trueDurationSeconds
    val t5 = System.currentTimeMillis()
    Log.d("PCG_DEBUG", "Bitmap built in ${t5 - t4} ms.")

    val base64 = bitmapToBase64Png(bmp)
    bmp.recycle() // Ensure recycling is happening
    Log.d("PCG_DEBUG", "Bitmap recycled.")

    val html = buildHtml(meta, base64)

    val t6 = System.currentTimeMillis()
    val pdf = htmlToPdf(context, html)
    val t7 = System.currentTimeMillis()

    Log.d("PCG_DEBUG", "HTML built in ${t6 - t5} ms")
    Log.d("PCG_DEBUG", "PDF written in ${t7 - t6} ms")
    Log.d("PCG_DEBUG", "=== Total report generation time: ${t7 - t0} ms ===")

    return pdf
}


