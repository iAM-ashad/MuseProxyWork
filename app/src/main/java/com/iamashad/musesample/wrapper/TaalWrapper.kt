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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "TaalWrapper"

/* ------------------------------- Public types -------------------------------- */

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connected : ConnectionState()
}

sealed class SdkRecordingState {
    data object Idle : SdkRecordingState()
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

/** FOR LOGGING & TESTING ONLY: Capture diagnostics to objectively verify filtering/amplification. */
data class CaptureDiagnostics(
    val sampleRate: Int,
    val rmsRaw: Double?,
    val rmsPref: Double?,
    /** Center frequencies and energies (dBFS) for RAW */
    val bandsHz: IntArray,
    val bandDbRaw: DoubleArray?,
    /** Same bands for PREF */
    val bandDbPref: DoubleArray?,
    /** Pref minus Raw (dB) per band; negative = attenuation by filter */
    val bandDeltaDb: DoubleArray?
)

/* --------------------------------- Wrapper ----------------------------------- */

class TaalWrapper(
    private val appContext: Context,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection

    private val _recording = MutableStateFlow<SdkRecordingState>(SdkRecordingState.Idle)
    val recording: StateFlow<SdkRecordingState> = _recording

    private val _recorderState =
        MutableSharedFlow<TaalRecorderState>(replay = 0, extraBufferCapacity = 1)

    // Live audio + sample rate for UI/analysis
    val liveSamples: MutableStateFlow<FloatArray?> = MutableStateFlow(null)
    val sampleRateHz: MutableStateFlow<Int?> = MutableStateFlow(null)

    // Diagnostics from last completed capture
    private val _lastCaptureDiagnostics = MutableStateFlow<CaptureDiagnostics?>(null)
    val lastCaptureDiagnostics: StateFlow<CaptureDiagnostics?> = _lastCaptureDiagnostics

    private var recorder: TaalRecorder? = null
    private var connReceiver: TaalConnectionBroadcastReceiver? = null

    private var lastConfig: RecordConfig? = null

    @Volatile
    private var lastSdkState: TaalRecorderState? = null

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

    /** To be replaced with TAAL VID/PID filter when available. */
    fun pollConnectionNow() {
        val mgr = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val anyDevicePresent = mgr.deviceList.values.any()
        _connection.value =
            if (anyDevicePresent) ConnectionState.Connected else ConnectionState.Disconnected
    }

    /* -------------------------------- Recording -------------------------------- */

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

            r.setPlayback(cfg.playbackWhileRecording)
            cfg.recordingTimeSec?.let { r.setRecordingTime(it) }
            cfg.preAmplificationDb?.let { r.setPreAmplification(it) }

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
                    val n = stream.size / 2
                    if (n <= 0) return
                    val out = FloatArray(n)
                    var si = 0
                    var i = 0
                    while (si < stream.size - 1) {
                        val lo = stream[si].toInt() and 0xFF
                        val hi = stream[si + 1].toInt()
                        val s = (hi shl 8) or lo
                        out[i] = (s.toShort().toInt() / 32768f).coerceIn(-1f, 1f)
                        si += 2
                        i++
                    }
                    liveSamples.value = out
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

    fun stopRecording() {
        val r = recorder
        val cfg = lastConfig
        try {
            r?.stop()

            // Resolve paths (SDK first; fallback to cfg)
            val raw = r?.getRawAudioFilePathOrNull() ?: cfg?.rawAudioPath
            val pref = r?.getPreFilteredAudioFilePathOrNull() ?: cfg?.preFilteredAudioPath

            // Wait up to 6s for >44-byte WAV(s)
            val deadline = SystemClock.elapsedRealtime() + 6_000
            var rawOk = false
            var prefOk = false
            while (SystemClock.elapsedRealtime() < deadline) {
                if (!prefOk && !pref.isNullOrEmpty()) {
                    val f = File(pref); prefOk = f.exists() && f.length() > 44
                }
                if (!rawOk && !raw.isNullOrEmpty()) {
                    val f = File(raw); rawOk = f.exists() && f.length() > 44
                }
                if (prefOk || rawOk) break
                Thread.sleep(40)
            }

            val chosen = when {
                prefOk -> pref
                rawOk -> raw
                else -> null
            }

            _recording.value = SdkRecordingState.Stopped(
                rawPath = raw,
                prefilteredPath = pref,
                chosenPath = chosen
            )

            // Compute diagnostics (off main)
            if (!raw.isNullOrEmpty() || !pref.isNullOrEmpty()) {
                externalScope.launch(Dispatchers.Default) {
                    val diag = computeDiagnostics(raw, pref)
                    _lastCaptureDiagnostics.value = diag
                    if (diag != null) {
                        val bands = diag.bandsHz.joinToString()
                        val rawDb = diag.bandDbRaw?.joinToString { fmtDb(it) } ?: "—"
                        val prefDb = diag.bandDbPref?.joinToString { fmtDb(it) } ?: "—"
                        val deltaDb = diag.bandDeltaDb?.joinToString { fmtDb(it) } ?: "—"

                        Log.i(TAG, "Capture diagnostics")
                        Log.i(TAG, "  SR=${diag.sampleRate}, RMS raw=${fmt(diag.rmsRaw)} pref=${fmt(diag.rmsPref)}")
                        Log.i(TAG, "  Bands (Hz): [$bands]")
                        Log.i(TAG, "  RAW  dBFS : [$rawDb]")
                        Log.i(TAG, "  PREF dBFS : [$prefDb]")
                        Log.i(TAG, "  ΔdB(P-R)  : [$deltaDb]")
                    }

                }
            }
        } catch (t: Throwable) {
            _recording.value = SdkRecordingState.Error(t.message ?: "Error stopping record")
        } finally {
            runCatching { r?.reset() }
            recorder = null
            lastConfig = null
            liveSamples.value = null
            sampleRateHz.value = null
        }
    }

    /* -------------------------- WAV parsing & diagnostics ----------------------- */


    private data class WavData(
        val sampleRate: Int,
        val numChannels: Int,
        val bitsPerSample: Int,
        val pcm: ShortArray // mono extracted (ch1) from interleaved if needed
    )

    private fun readWavPcm16Mono(path: String): WavData? {
        val f = File(path)
        if (!f.exists() || f.length() <= 44) return null

        FileInputStream(f).use { fis ->
            val hdr12 = ByteArray(12)
            if (fis.read(hdr12) != 12) return null
            if (!hdr12.copyOfRange(0, 4).contentEquals("RIFF".toByteArray())) return null
            if (!hdr12.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) return null

            var sampleRate = 0
            var numChannels = 0
            var bitsPerSample = 0
            var dataSize = -1L
            var dataStartPos = -1L

            // Iterate chunks until we find "data"
            while (true) {
                val chunkHdr = ByteArray(8)
                val read = fis.read(chunkHdr)
                if (read < 8) break // EOF or truncated
                val id = String(chunkHdr, 0, 4)
                val size = ByteBuffer.wrap(chunkHdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (size < 0) return null

                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(size)
                        if (fis.read(fmt) != size) return null
                        val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        val audioFormat = bb.getShort(0).toInt() and 0xFFFF
                        numChannels = bb.getShort(2).toInt() and 0xFFFF
                        sampleRate = bb.getInt(4)
                        bitsPerSample = if (size >= 16) bb.getShort(14).toInt() and 0xFFFF else 16
                        if (audioFormat != 1 /* PCM */ || bitsPerSample != 16 || numChannels < 1) {
                            return null
                        }
                    }
                    "data" -> {
                        dataSize = size.toLong()
                        dataStartPos = f.length() - fis.available().toLong()
                        // We break AFTER recording location so that we can read it next.
                        break
                    }
                    else -> {
                        // Skip unknown chunk
                        val skipped = fis.skip(size.toLong())
                        if (skipped < size) return null
                    }
                }
            }

            if (dataSize <= 0 || dataStartPos < 0 || sampleRate <= 0) return null

            // Read data payload
            val bytes = fis.readExactBytes(dataSize.toInt())
            val totalFrames = bytes.size / (2 * numChannels) // 2 bytes per sample * channels
            val pcmMono = ShortArray(totalFrames)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until totalFrames) {
                val firstCh = bb.short
                // skip remaining channels, if any
                for (c in 1 until numChannels) bb.short
                pcmMono[i] = firstCh
            }

            return WavData(
                sampleRate = sampleRate,
                numChannels = numChannels,
                bitsPerSample = bitsPerSample,
                pcm = pcmMono
            )
        }
    }

    // Octave-ish band centers we’ll report (Hz)
    private val defaultBandsHz = intArrayOf(150, 300, 600, 1200, 2400)

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

        // Band energy in dBFS using Goertzel over small windows
        fun bandDb(s: ShortArray?, fs: Int): DoubleArray? {
            if (s == null || s.isEmpty()) return null
            val N = min(4096, s.size)
            if (N < 1024) return null
            val twoPiOverN = 2.0 * Math.PI / N

            fun powerAt(freq: Int): Double {
                val k = (0.5 + (N * freq) / fs.toDouble()).toInt()
                val w = twoPiOverN * k
                val cw = kotlin.math.cos(w)
                val sw = kotlin.math.sin(w)
                val coeff = 2.0 * cw
                var s0 = 0.0;
                var s1 = 0.0;
                var s2 = 0.0
                for (i in 0 until N) {
                    val x = s[i].toDouble() / 32768.0
                    s0 = x + coeff * s1 - s2
                    s2 = s1; s1 = s0
                }
                val re = s1 - s2 * cw
                val im = s2 * sw
                val p = re * re + im * im
                return p / N // scale roughly
            }

            // For each center frequency, average three bins: f/√2, f, f*√2 (≈ one octave width)
            val out = DoubleArray(defaultBandsHz.size)
            for (bi in defaultBandsHz.indices) {
                val fC = defaultBandsHz[bi]
                val f1 = (fC / sqrt(2.0)).roundToInt().coerceAtLeast(10)
                val f2 = fC
                val f3 = (fC * sqrt(2.0)).roundToInt()
                val p = powerAt(f1) + powerAt(f2) + powerAt(f3)
                // dBFS-ish from power (avoid log(0))
                out[bi] = 10.0 * ln(max(p, 1e-20)) / ln(10.0)
            }
            return out
        }

        val rmsRaw = rms(raw?.pcm)
        val rmsPref = rms(pref?.pcm)
        val bandsRaw = bandDb(raw?.pcm, raw?.sampleRate ?: sr)
        val bandsPref = bandDb(pref?.pcm, pref?.sampleRate ?: sr)
        val delta = if (bandsRaw != null && bandsPref != null) {
            DoubleArray(defaultBandsHz.size) { i -> bandsPref[i] - bandsRaw[i] }
        } else null

        // Helpful log: show a couple of bands (e.g., 600 & 1200 Hz) attenuation if we have both
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

    /* ------------------------------- Utilities --------------------------------- */

    private fun InputStream.readExactBytes(n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val read = this.read(buf, off, n - off)
            if (read < 0) break
            off += read
        }
        return if (off == n) buf else buf.copyOf(off)
    }

    private fun fmt(v: Double?): String = if (v == null) "--" else String.format("%.4f", v)
    private fun fmtDb(v: Double): String = String.format("%+.1f dB", v)
}

/* ---------------------------- Reflection helpers ----------------------------- */

// Reflection helpers (SDK getters might be non-public)
// Use instance-based reflection; avoid Class.forName to play nice with Live Edit / instrumentation.
private fun TaalRecorder.getRawAudioFilePathOrNull(): String? =
    runCatching {
        this.javaClass.getMethod("getRawAudioFilePath").invoke(this) as? String
    }.getOrNull()

private fun TaalRecorder.getPreFilteredAudioFilePathOrNull(): String? =
    runCatching {
        this.javaClass.getMethod("getPreFilteredAudioFilePath").invoke(this) as? String
    }.getOrNull()

