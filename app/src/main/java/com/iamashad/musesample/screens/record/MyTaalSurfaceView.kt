package com.iamashad.musesample.screens.record

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


/**
 * NOTE:  In Use.
 */


class MyTaalSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val holderRef = holder.apply { addCallback(this@MyTaalSurfaceView) }

    @Volatile
    private var running = false
    private var drawThread: Thread? = null

    // paints
    private val bgPaint = Paint().apply { color = Color.WHITE }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#F2F2F2")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val wavePaint = Paint().apply {
        color = Color.parseColor("#1976D2")
        strokeWidth = 4.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val axisPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1.2f
    }

    // queue
    private val SAMPLE_QUEUE_CAPACITY = 220_500
    private val sampleQueue = ArrayBlockingQueue<Float>(SAMPLE_QUEUE_CAPACITY)

    // waveform window
    @Volatile
    private var visibleWindowSeconds = 10f
    private var sampleRate = 44100
    private val DOWNSAMPLE = 40

    private val gridCols = 10
    private val gridRows = 10

    // zoom control
    private var minWindowSec = 1f
    private var maxWindowSec = 60f
    private var interactionEnabled = false
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    init {
        setZOrderOnTop(false)

        scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (!interactionEnabled) return false
                    val scale = detector.scaleFactor
                    // invert scaling (pinch out -> zoom in)
                    val newWin = (visibleWindowSeconds / scale).coerceIn(minWindowSec, maxWindowSec)
                    visibleWindowSeconds = newWin
                    return true
                }
            })

        gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!interactionEnabled) return false
                    visibleWindowSeconds = 10f // reset
                    return true
                }
            })
    }

    // region public api
    fun pushSamples(sampleRate: Int, startTimeSec: Double, data: FloatArray) {
        if (data.isEmpty()) return
        this.sampleRate = sampleRate
        var i = 0
        while (i < data.size) {
            val clamped = max(-1f, min(1f, data[i]))
            sampleQueue.offer(clamped)
            i += DOWNSAMPLE
        }
        while (sampleQueue.remainingCapacity() < 100) sampleQueue.poll()
    }

    fun clear() = sampleQueue.clear()

    fun setInteractionEnabled(enabled: Boolean) {
        interactionEnabled = enabled
    }

    fun setVisibleWindowSeconds(sec: Float) {
        visibleWindowSeconds = sec.coerceIn(minWindowSec, maxWindowSec)
    }
    // endregion

    // region touch
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactionEnabled) return false
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
    // endregion

    // region surface callbacks
    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        drawThread = Thread(::drawLoop, "mytaal-wave-draw").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try {
            drawThread?.join(300)
        } catch (_: InterruptedException) {
        }
        drawThread = null
    }
    // endregion

    // region draw loop
    private fun drawLoop() {
        var lastTime = System.currentTimeMillis()
        while (running) {
            val canvas = try {
                holderRef.lockCanvas()
            } catch (_: Throwable) {
                null
            }
            if (canvas == null) {
                Thread.sleep(10)
                continue
            }

            try {
                drawBackground(canvas)
                drawGrid(canvas)
                drawWave(canvas)
            } finally {
                holderRef.unlockCanvasAndPost(canvas)
            }

            val elapsed = System.currentTimeMillis() - lastTime
            val sleep = 16L - elapsed
            if (sleep > 0) Thread.sleep(sleep)
            lastTime = System.currentTimeMillis()
        }
    }
    // endregion

    // region draw
    private fun drawBackground(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    }

    private fun drawGrid(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val colW = w / gridCols
        val rowH = h / gridRows
        for (i in 0..gridCols) c.drawLine(i * colW, 0f, i * colW, h, gridPaint)
        for (j in 0..gridRows) c.drawLine(0f, j * rowH, w, j * rowH, gridPaint)
    }

    private fun drawWave(c: Canvas) {
        val w = width
        val h = height
        if (w == 0 || h == 0) return

        val samplesPerSecond = if (sampleRate > 0) sampleRate / DOWNSAMPLE else 882
        val totalVisibleSamples = (visibleWindowSeconds * samplesPerSecond).roundToInt()
        if (totalVisibleSamples < 2) return

        val queueArray = sampleQueue.toTypedArray()
        val qlen = queueArray.size
        val start = max(0, qlen - totalVisibleSamples)

        val path = Path()
        val centerY = h / 2f
        val xStep = w.toFloat() / max(1, totalVisibleSamples - 1)
        val ampScale = 1.8f

        for (i in start until qlen) {
            val idx = i - start
            val amp = (queueArray[i] as Float)
            val y = centerY - (amp * h * 0.45f * ampScale)
            val x = idx * xStep
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        c.drawLine(0f, centerY, w.toFloat(), centerY, axisPaint)
        c.drawPath(path, wavePaint)
    }
}
