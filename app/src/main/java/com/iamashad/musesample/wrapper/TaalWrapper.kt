package com.iamashad.musesample.wrapper

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.SystemClock
import android.util.Log
import `in`.museinc.android.surr_core.recorder.OnInfoListener
import `in`.museinc.android.surr_core.recorder.OnLiveStreamListener
import `in`.museinc.android.surr_core.recorder.TaalRecorder
import `in`.museinc.android.surr_core.recorder.TaalRecorderState
import `in`.museinc.android.surr_core.taalConnectionUtils.TaalConnectionBroadcastReceiver
import `in`.museinc.android.surr_core.taalConnectionUtils.TaalConnectionListener
import `in`.museinc.android.surr_core.utils.PreFilter
import `in`.museinc.android.surr_core.utils.SurrUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

private const val TAG = "TaalWrapper"

/* ------------------------------- Public types -------------------------------- */

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connected : ConnectionState()
}

sealed class SdkRecordingState {
    object Idle : SdkRecordingState()
    data class Recording(val startedAtMs: Long) : SdkRecordingState()
    data class Stopped(
        val rawPath: String?,
        val prefilteredPath: String?,
        val chosenPath: String?
    ) : SdkRecordingState()

    data class Error(val message: String) : SdkRecordingState()
}

data class RecordConfig(
    val rawAudioPath: String,
    val preFilteredAudioPath: String? = null,
    val recordingTimeSec: Int? = null,
    val preAmplificationDb: Int? = null,
    val preFilter: PreFilter? = null,
    val playbackWhileRecording: Boolean = false
)

/**
 * Result returned by stopRecordingAndAwaitResult
 */
data class RecordingResult(
    val rawPath: String?,
    val prefilteredPath: String?,
    val chosenPath: String?,
    val diagnostics: CaptureDiagnostics?
)

data class CaptureDiagnostics(
    val sampleRate: Int,
    val rmsRaw: Double?,
    val rmsPref: Double?,
    val bandsHz: IntArray,
    val bandDbRaw: DoubleArray?,
    val bandDbPref: DoubleArray?,
    val bandDeltaDb: DoubleArray?
)

/**
 * Lightweight frame representing a live stream emission
 */
data class LiveStreamFrame(val sampleRate: Int, val samples: FloatArray)

/* ------------------------------- Wrapper ----------------------------------- */

class TaalWrapper(
    private val appContext: Context,
    /**
     * Provide a scope for background operations (e.g., from Application or Activity).
     * Defaults to SupervisorScope on Dispatchers.Default so library doesn't create a Main scope.
     */
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _recording = MutableStateFlow<SdkRecordingState>(SdkRecordingState.Idle)
    val recording: StateFlow<SdkRecordingState> = _recording.asStateFlow()

    private val _recorderState =
        MutableSharedFlow<TaalRecorderState>(replay = 0, extraBufferCapacity = 1)

    // Live audio + sample rate for UI/analysis (easy immediate access)
    val liveSamples: MutableStateFlow<FloatArray?> = MutableStateFlow(null)
    val sampleRateHz: MutableStateFlow<Int?> = MutableStateFlow(null)

    // Flow that emits raw frames with backpressure (optional)
    private val _liveStreamFlow =
        MutableSharedFlow<LiveStreamFrame>(replay = 0, extraBufferCapacity = 2)

    fun liveStreamFlow(): SharedFlow<LiveStreamFrame> = _liveStreamFlow.asSharedFlow()

    // A lower-rate envelope flow for UI (computed in background)
    private val _liveEnvelopeFlow =
        MutableSharedFlow<Pair<Int, FloatArray>>(replay = 0, extraBufferCapacity = 1)

    fun liveEnvelopeFlow(): SharedFlow<Pair<Int, FloatArray>> = _liveEnvelopeFlow.asSharedFlow()

    // Diagnostics from last completed capture
    private val _lastCaptureDiagnostics = MutableStateFlow<CaptureDiagnostics?>(null)
    val lastCaptureDiagnostics: StateFlow<CaptureDiagnostics?> =
        _lastCaptureDiagnostics.asStateFlow()

    private var recorder: TaalRecorder? = null
    private var connReceiver: TaalConnectionBroadcastReceiver? = null

    private var lastConfig: RecordConfig? = null

    @Volatile
    private var lastSdkState: TaalRecorderState? = null

    // Default bands (same as original)
    private val defaultBandsHz = intArrayOf(150, 300, 600, 1200, 2400)

    /* ---------------------------- Connection monitor --------------------------- */

    fun startDeviceMonitor() {
        if (connReceiver != null) return
        _connection.value = ConnectionState.Disconnected
        connReceiver = TaalConnectionBroadcastReceiver(object : TaalConnectionListener {
            override fun onTaalConnect() {
                _connection.value = ConnectionState.Connected
            }

            override fun onTaalDisconnect() {
                _connection.value = ConnectionState.Disconnected
                if (recording.value is SdkRecordingState.Recording) {
                    _recording.value =
                        SdkRecordingState.Error("Device disconnected during recording")
                }
            }
        }).also { it.register(appContext) }

        pollConnectionNow()
    }

    fun stopDeviceMonitor() {
        connReceiver?.unregister(appContext)
        connReceiver = null
        _connection.value = ConnectionState.Disconnected
    }

    fun pollConnectionNow() {
        val mgr = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val anyDevicePresent = mgr.deviceList.values.any()
        _connection.value =
            if (anyDevicePresent) ConnectionState.Connected else ConnectionState.Disconnected
    }

    /* -------------------------------- Recording -------------------------------- */

    /**
     * Start a recording (non-suspending; kept for compatibility).
     * Prefer using stopRecordingAndAwaitResult to get final paths & diagnostics.
     */
    fun startRecording(cfg: RecordConfig) {
        val r = TaalRecorder(appContext)
        try {
            r.reset()

            Log.d(
                TAG,
                "startRecording: raw='${cfg.rawAudioPath}', pref='${cfg.preFilteredAudioPath}', " +
                        "duration=${cfg.recordingTimeSec}, preAmpDb=${cfg.preAmplificationDb}, preFilter=${cfg.preFilter}, " +
                        "playback=${cfg.playbackWhileRecording}"
            )

            r.setRawAudioFilePath(cfg.rawAudioPath)

            if (cfg.preFilter != null) {
                cfg.preFilteredAudioPath?.let { r.setPreFilteredAudioFilePath(it) }
                r.setPreFilter(cfg.preFilter)
            }

            // enable/disable live playback from device
            r.setPlayback(cfg.playbackWhileRecording)

            cfg.recordingTimeSec?.let { r.setRecordingTime(it) }
            cfg.preAmplificationDb?.let { r.setPreAmplification(it) }

            // Info listener -> propagate state + sampleRate
            r.setOnInfoListener(object : OnInfoListener {
                override fun onStateChange(state: TaalRecorderState) {
                    lastSdkState = state
                    _recorderState.tryEmit(state)
                }

                override fun onProgressUpdate(
                    sampleRate: Int,
                    bufferSize: Int,
                    timeStamp: Double,
                    data: FloatArray
                ) {
                    if (sampleRateHz.value != sampleRate) sampleRateHz.value = sampleRate
                }
            })

            // Livestream: 16-bit LE PCM -> Float [-1, 1]
            r.setOnLiveStreamListener(object : OnLiveStreamListener {
                override fun onNewStream(stream: ByteArray) {
                    externalScope.launch {
                        try {
                            val frame =
                                decodePcm16leToFloatFrame(stream, sampleRateHz.value ?: 44100)
                            liveSamples.value = frame
                            _liveStreamFlow.tryEmit(
                                LiveStreamFrame(
                                    sampleRateHz.value ?: 44100,
                                    frame
                                )
                            )
                            val envelope = computeEnvelope(
                                frame,
                                windowSize = (sampleRateHz.value ?: 44100) / 200 // ~5 ms window
                            )
                            _liveEnvelopeFlow.tryEmit(
                                Pair(
                                    sampleRateHz.value ?: 44100,
                                    envelope
                                )
                            )
                        } catch (t: Throwable) {
                            Log.w(TAG, "live stream decode failed: ${t.message}")
                        }
                    }
                }
            })

            lastSdkState = null
            r.start()
            recorder = r
            lastConfig = cfg
            _lastCaptureDiagnostics.value = null // new capture
            _recording.value = SdkRecordingState.Recording(System.currentTimeMillis())
        } catch (t: Throwable) {
            _recording.value = SdkRecordingState.Error(t.message ?: "Error starting record")
            runCatching { r.reset() }
        }
    }

    /**
     * Stop recording and await result (suspending). Returns RecordingResult with chosen path and diagnostics.
     */
    suspend fun stopRecordingAndAwaitResult(timeoutMs: Long = 6_000L): RecordingResult =
        withContext(externalScope.coroutineContext) {
            val r = recorder
            val cfg = lastConfig
            if (r == null) {
                return@withContext RecordingResult(null, null, null, null)
            }

            try {
                r.stop()
            } catch (t: Throwable) {
                Log.w(TAG, "Exception while stopping recorder: ${t.message}")
                runCatching { r.reset() }
                recorder = null
                lastConfig = null
                return@withContext RecordingResult(null, null, null, null)
            }

            val sdkRaw = r.getRawAudioFilePathOrNull()
            val sdkPref = r.getPreFilteredAudioFilePathOrNull()
            val raw = sdkRaw ?: cfg?.rawAudioPath
            val pref = sdkPref ?: cfg?.preFilteredAudioPath

            val prefOkDeferred = async { waitForFileReady(pref, timeoutMs) }
            val rawOkDeferred = async { waitForFileReady(raw, timeoutMs) }
            val prefOk = prefOkDeferred.await()
            val rawOk = rawOkDeferred.await()

            val chosen = when {
                prefOk && !pref.isNullOrEmpty() -> pref
                rawOk && !raw.isNullOrEmpty() -> raw
                else -> null
            }

            val diag = withContext(Dispatchers.Default) {
                computeDiagnostics(raw, pref)
            }

            runCatching { r.reset() }
            recorder = null
            lastConfig = null
            liveSamples.value = null
            sampleRateHz.value = null
            _lastCaptureDiagnostics.value = diag
            _recording.value = SdkRecordingState.Stopped(
                rawPath = raw,
                prefilteredPath = pref,
                chosenPath = chosen
            )

            RecordingResult(raw, pref, chosen, diag)
        }

    /* -------------------------- WAV parsing & diagnostics ----------------------- */

    fun getSdkFilteredSamples(filePath: String): Pair<Int, FloatArray> {
        val file = File(filePath)
        if (!file.exists() || file.length() <= 44) {
            Log.w(TAG, "getSdkFilteredSamples: File not found or is empty: $filePath")
            return 44100 to FloatArray(0)
        }

        try {
            val sampleRate = SurrUtils.readSampleRate(filePath)
            val floatList = SurrUtils.getFloatBuffer(filePath)
            val floatArray = FloatArray(floatList.size)
            for (i in floatList.indices) floatArray[i] = floatList[i]
            Log.i(
                TAG,
                "getSdkFilteredSamples: Loaded via SurrUtils SR=$sampleRate Samples=${floatArray.size}"
            )
            return sampleRate to floatArray
        } catch (e: Exception) {
            Log.w(TAG, "getSdkFilteredSamples: SurrUtils read failed, falling back: ${e.message}")
        }

        return try {
            val wav = readWavPcm16Mono(filePath)
            if (wav == null) {
                Log.w(TAG, "getSdkFilteredSamples: fallback read returned null")
                44100 to FloatArray(0)
            } else {
                val floats = FloatArray(wav.pcm.size)
                for (i in wav.pcm.indices) floats[i] = wav.pcm[i].toInt() / 32768f
                Log.i(
                    TAG,
                    "getSdkFilteredSamples: Fallback loaded SR=${wav.sampleRate} Samples=${floats.size}"
                )
                wav.sampleRate to floats
            }
        } catch (e2: Exception) {
            Log.e(TAG, "getSdkFilteredSamples: Fallback failed", e2)
            44100 to FloatArray(0)
        }
    }

    private data class WavData(
        val sampleRate: Int,
        val numChannels: Int,
        val bitsPerSample: Int,
        val pcm: ShortArray
    )

    private fun readWavPcm16Mono(path: String): WavData? {
        val f = File(path)
        if (!f.exists() || f.length() <= 44) return null

        FileInputStream(f).use { fis ->
            val all = fis.readBytes()
            if (all.size < 44) return null
            val bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN)

            val riff = ByteArray(4)
            bb.get(riff)
            if (!riff.contentEquals("RIFF".toByteArray())) return null
            bb.int
            val wave = ByteArray(4)
            bb.get(wave)
            if (!wave.contentEquals("WAVE".toByteArray())) return null

            var fmtFound = false
            var audioFormat = 1
            var numChannels = 1
            var sampleRate = 44100
            var bitsPerSample = 16
            var dataOffset = -1
            var dataSize = 0

            while (bb.position() + 8 <= bb.capacity()) {
                val id = ByteArray(4); bb.get(id)
                val chunkSize = bb.int
                val idStr = String(id)
                if (idStr == "fmt ") {
                    fmtFound = true
                    audioFormat = bb.short.toInt() and 0xFFFF
                    numChannels = bb.short.toInt() and 0xFFFF
                    sampleRate = bb.int
                    bb.int
                    bb.short
                    bitsPerSample = bb.short.toInt() and 0xFFFF
                    val remaining = chunkSize - 16
                    if (remaining > 0) bb.position(bb.position() + remaining)
                } else if (idStr == "data") {
                    dataOffset = bb.position()
                    dataSize = chunkSize
                    break
                } else {
                    bb.position(bb.position() + chunkSize)
                }
            }

            if (!fmtFound || dataOffset < 0 || dataSize <= 0) return null
            if (audioFormat != 1 || bitsPerSample != 16 || numChannels < 1) return null

            val bytesPerSample = 2
            val totalFrames = dataSize / (bytesPerSample * numChannels)
            val pcmMono = ShortArray(totalFrames)
            val dataStart = dataOffset
            val dataEnd = dataOffset + dataSize
            var frameIdx = 0
            var pos = dataStart
            while (pos + 1 < dataEnd && frameIdx < totalFrames) {
                val lo = all[pos].toInt() and 0xFF
                val hi = all[pos + 1].toInt()
                val s = ((hi shl 8) or lo).toShort()
                pcmMono[frameIdx] = s
                pos += bytesPerSample * numChannels
                frameIdx++
            }

            return WavData(
                sampleRate = sampleRate,
                numChannels = numChannels,
                bitsPerSample = bitsPerSample,
                pcm = pcmMono
            )
        }
    }

    /* ------------------------------- Diagnostics -------------------------------- */

    private fun fmtDb(v: Double): String = String.format("%+.1f dB", v)

    private fun computeDiagnostics(rawPath: String?, prefPath: String?): CaptureDiagnostics? {
        val raw = rawPath?.let { readWavPcm16Mono(it) }
        val pref = prefPath?.let { readWavPcm16Mono(it) }
        val sr = pref?.sampleRate ?: raw?.sampleRate ?: return null

        fun rms(s: ShortArray?): Double? {
            if (s == null || s.isEmpty()) return null
            var acc = 0.0
            for (v in s) {
                val f = v.toDouble() / 32768.0
                acc += f * f
            }
            return sqrt(acc / s.size)
        }

        val rmsRaw = rms(raw?.pcm)
        val rmsPref = rms(pref?.pcm)

        val bandsRaw = raw?.let { computeBandDbGoertzel(it.pcm, it.sampleRate, defaultBandsHz) }
        val bandsPref = pref?.let { computeBandDbGoertzel(it.pcm, it.sampleRate, defaultBandsHz) }

        val delta = if (bandsRaw != null && bandsPref != null) {
            DoubleArray(defaultBandsHz.size) { i -> bandsPref[i] - bandsRaw[i] }
        } else null

        if (delta != null) {
            val idx600 = defaultBandsHz.indexOf(600).takeIf { it >= 0 }
            val idx1200 = defaultBandsHz.indexOf(1200).takeIf { it >= 0 }
            val a600 = idx600?.let { delta[it] }?.let { fmtDb(it) }
            val a1200 = idx1200?.let { delta[it] }?.let { fmtDb(it) }
            Log.d(TAG, "Attenuation (pref-raw): 600Hz=${a600}, 1200Hz=${a1200}")
        }

        return CaptureDiagnostics(
            sampleRate = sr,
            rmsRaw = rmsRaw,
            rmsPref = rmsPref,
            bandsHz = defaultBandsHz,
            bandDbRaw = bandsRaw,
            bandDbPref = bandsPref,
            bandDeltaDb = delta
        )
    }

    // ----------------------- DSP helpers (Goertzel + windowing) ------------------

    private fun goertzelPowerDoubles(samples: DoubleArray, sampleRate: Int, targetHz: Int): Double {
        val N = samples.size
        if (N <= 0) return 0.0
        val k = (0.5 + (N * targetHz) / sampleRate.toDouble()).toInt()
        val omega = 2.0 * Math.PI * k / N
        val coeff = 2.0 * cos(omega)
        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0
        for (i in 0 until N) {
            q0 = coeff * q1 - q2 + samples[i]
            q2 = q1
            q1 = q0
        }
        val real = q1 - q2 * cos(omega)
        val imag = q2 * kotlin.math.sin(omega)
        val magnitudeSquared = real * real + imag * imag
        return magnitudeSquared / N
    }

    private fun powerToDbfs(powerLinear: Double): Double {
        val eps = 1e-20
        val rms = sqrt(max(powerLinear, eps))
        return 20.0 * ln(max(rms, eps)) / ln(10.0)
    }

    private fun shortArrayToWindowedDouble(samples: ShortArray): DoubleArray {
        val N = samples.size
        val out = DoubleArray(N)
        if (N == 0) return out
        for (i in 0 until N) {
            val hann = 0.5 * (1 - cos(2.0 * Math.PI * i / (N - 1)))
            out[i] = (samples[i].toDouble() / 32768.0) * hann
        }
        return out
    }

    private fun computeBandDbGoertzel(
        samplesShort: ShortArray?,
        sampleRate: Int,
        centersHz: IntArray
    ): DoubleArray? {
        if (samplesShort == null || samplesShort.isEmpty()) return null
        val windowed = shortArrayToWindowedDouble(samplesShort)
        return DoubleArray(centersHz.size) { i ->
            val p = goertzelPowerDoubles(windowed, sampleRate, centersHz[i])
            powerToDbfs(p)
        }
    }

    // ----------------------- Utility / streaming helpers ------------------------

    private fun decodePcm16leToFloatFrame(stream: ByteArray, sampleRate: Int): FloatArray {
        if (stream.isEmpty()) return FloatArray(0)
        val bb = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN)
        val shortCount = stream.size / 2
        val shorts = ShortArray(shortCount)
        bb.asShortBuffer().get(shorts)
        val floats = FloatArray(shortCount)
        for (i in 0 until shortCount) {
            floats[i] = shorts[i].toInt() / 32768f
        }
        return floats
    }

    private fun computeEnvelope(samples: FloatArray, windowSize: Int = 128): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        val N = samples.size
        val out = FloatArray(N)
        val w = max(1, windowSize)
        val buf = DoubleArray(w)
        var idx = 0
        var filled = 0
        for (i in 0 until N) {
            val v = abs(samples[i].toDouble())
            buf[idx] = v
            idx = (idx + 1) % w
            if (filled < w) filled++
            var sum = 0.0
            for (j in 0 until filled) sum += buf[j]
            out[i] = (sum / filled).toFloat()
        }
        return out
    }

    private suspend fun waitForFileReady(
        path: String?,
        timeoutMs: Long = 6000L,
        minBytes: Long = 44
    ): Boolean {
        if (path.isNullOrEmpty()) return false

        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val f = File(path)
            if (f.exists() && f.length() > minBytes) return true
            delay(40)
        }
        return false
    }

    /* ---------------------------- Reflection helpers ----------------------------- */

    private fun TaalRecorder.getRawAudioFilePathOrNull(): String? =
        runCatching {
            this.javaClass.getMethod("getRawAudioFilePath").invoke(this) as? String
        }.getOrNull()

    private fun TaalRecorder.getPreFilteredAudioFilePathOrNull(): String? =
        runCatching {
            this.javaClass.getMethod("getPreFilteredAudioFilePath").invoke(this) as? String
        }.getOrNull()
}
