package com.iamashad.musesample.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Render a stacked phonocardiogram (PCG) bitmap suitable for print/PDF.
 *
 * Input contract:
 * - [normalized] contains samples scaled to about ±1000 (see downsampleWaveform()).
 * - We assume samples are uniformly spaced over [secondsTotal].
 *
 * Layout:
 * - The waveform is split into fixed-duration bands (rows) of [segmentSec] seconds.
 * - Each row has top/bottom borders and a dashed zero line.
 * - The trace starts at the left edge and fills the row width (no side gutters).
 *
 * Sizing:
 * - The function is deterministic given the inputs, so PDF layout is predictable.
 * - Vertical scale maps ±1000 to ~80% of available row height (headroom for spikes).
 */
fun buildStackedPcgBitmap(
    context: Context,
    normalized: List<Float>,
    secondsTotal: Float,
    segmentSec: Float = 10f,
    widthPx: Int = 2400,
    heightPx: Int = 1200,
    rowSpacingPx: Int = 56
): Bitmap {
    require(secondsTotal > 0f && normalized.isNotEmpty())

    val rows = max(1, ceil(secondsTotal / segmentSec).toInt())

    // Outer padding only (no inner side paddings so the trace uses full width).
    val topPadding = 40
    val bottomPadding = 64

    val availableH = heightPx - topPadding - bottomPadding - (rows - 1) * rowSpacingPx
    val rowH = max(160, availableH / rows)

    // Sample-to-time mapping (uniform sampling across the full duration).
    val samplesPerSec = (normalized.size / secondsTotal).coerceAtLeast(1f)

    // Styles
    val topBottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#B0C4DE".toColorInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    val zeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#888888".toColorInt()
        strokeWidth = 1.2f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }
    val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    // Maps ±1000 → row band with ~20% vertical margin.
    fun mapY(v: Float, rowTop: Int, rowBottom: Int): Float {
        val mid = (rowTop + rowBottom) / 2f
        val half = (rowBottom - rowTop) * 0.40f
        return mid - (v / 1000f) * half
    }

    val bmp = createBitmap(widthPx, heightPx)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)

    for (r in 0 until rows) {
        val rowTop = (topPadding + r * (rowH + rowSpacingPx)).toFloat()
        val rowBottom = rowTop + rowH

        // Row borders
        canvas.drawLine(0f, rowTop, widthPx.toFloat(), rowTop, topBottomPaint)
        canvas.drawLine(0f, rowBottom, widthPx.toFloat(), rowBottom, topBottomPaint)

        // Baseline (0)
        val zY = mapY(0f, rowTop.toInt(), rowBottom.toInt())
        canvas.drawLine(0f, zY, widthPx.toFloat(), zY, zeroPaint)

        // Segment bounds in seconds → sample indices
        val segStartSec = r * segmentSec
        val segEndSec = min((r + 1) * segmentSec, secondsTotal)

        val sStart = kotlin.math.floor(segStartSec * samplesPerSec).toInt().coerceAtLeast(0)
        val sEnd = min(normalized.size, ceil(segEndSec * samplesPerSec).toInt())
        if (sEnd <= sStart + 1) continue

        // Draw continuous path across the row
        val x0 = 0f
        val x1 = widthPx.toFloat()
        val count = (sEnd - sStart).coerceAtLeast(2)
        val denom = (count - 1).toFloat()
        val path = Path()
        for (i in 0 until count) {
            val idx = sStart + i
            val t = i / denom
            val x = x0 + t * (x1 - x0)
            val y = mapY(normalized[idx], rowTop.toInt(), rowBottom.toInt())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, tracePaint)
    }

    return bmp
}
