package com.iamashad.musesample.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.iamashad.musesample.model.SegmentLabel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Build stacked PCG bitmap and optionally paint segmentation overlays.
 *
 * - normalized: downsampled envelope samples (approx ±1000)
 * - secondsTotal: total duration in seconds
 * - segments: list of SegmentLabel(label:Int, startSec:Float, endSec:Float) in seconds.
 *
 * Colors:
 * 0 = S1 (red), 1 = Systole (green), 2 = S2 (blue), 3 = Diastole (orange)
 */
fun buildStackedPcgBitmap(
    context: Context,
    normalized: List<Float>,
    secondsTotal: Float,
    segmentSec: Float = 7.5f,
    widthPx: Int = 1900,
    heightPx: Int = 1200,
    rowSpacingPx: Int = 20,
    segments: List<SegmentLabel> = emptyList()
): Bitmap {
    require(secondsTotal > 0f && normalized.isNotEmpty())

    val segs = segments

    // --- Paddings ---
    val leftPadding = 80   // Space for Y-axis labels
    val rightPadding = 40
    val topPadding = 40
    val bottomPadding = 60 // Space for X-axis labels
    val axisLabelPadding = 10
    val timeLabelYOffset = 30f

    val graphWidth = widthPx - leftPadding - rightPadding
    val rows = max(1, ceil(secondsTotal / segmentSec).toInt())

    // Non-row space + row height calculation
    val nonRowSpace = topPadding + bottomPadding + ((rows - 1) * rowSpacingPx)
    val availableH = heightPx - nonRowSpace
    val rowH = (availableH.toFloat() / rows.toFloat()).coerceAtLeast(100f)

    val samplesPerSec = (normalized.size / secondsTotal).coerceAtLeast(1f)

    // --- Paints ---
    val bgPaint = Paint().apply { color = Color.WHITE }

    val topBottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#B0C4DE".toColorInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    val zeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#AAAAAA".toColorInt()
        strokeWidth = 1.2f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }
    val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    // NEW: vertical gridline paints
    val minorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#E0E7FF".toColorInt() // very light
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#C4CFEF".toColorInt()
        strokeWidth = 1.4f
        style = Paint.Style.STROKE
    }

    val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#666666".toColorInt()
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
    }

    val timeLabelPaint = Paint(axisLabelPaint).apply {
        textAlign = Paint.Align.CENTER
    }

    val overlayPaints = arrayOf(
        Paint().apply {
            color = Color.argb(110, 220, 38, 38); style = Paint.Style.FILL
        },   // S1 - red
        Paint().apply {
            color = Color.argb(90, 34, 197, 94); style = Paint.Style.FILL
        },   // Systole - green
        Paint().apply {
            color = Color.argb(110, 37, 99, 235); style = Paint.Style.FILL
        },   // S2 - blue
        Paint().apply {
            color = Color.argb(70, 255, 140, 0); style = Paint.Style.FILL
        }    // Diastole - orange
    )
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#0b4da2".toColorInt()
        strokeWidth = 1.2f
        style = Paint.Style.STROKE
    }

    // Y-axis mapping (±1.0 scaled to 60% of row height)
    fun mapY(v: Float, rowTop: Float, rowBottom: Float): Float {
        val mid = (rowTop + rowBottom) / 2f
        val half = (rowBottom - rowTop) * 0.60f
        return mid - (v / 1000f) * half
    }

    val bmp = createBitmap(widthPx, heightPx)
    val canvas = Canvas(bmp)
    canvas.drawPaint(bgPaint)

    // --- Per-row drawing ---
    for (r in 0 until rows) {
        val rowTop = (topPadding + r * (rowH + rowSpacingPx)).toFloat()
        val rowBottom = rowTop + rowH
        val rowMid = (rowTop + rowBottom) / 2f

        if (rowTop > heightPx - bottomPadding) break

        val rowStartSec = r * segmentSec
        val rowEndSec = min((r + 1) * segmentSec, secondsTotal)
        val timeInRow = rowEndSec - rowStartSec
        if (timeInRow <= 0f) continue

        val xLeft = leftPadding.toFloat()
        val xRight = (leftPadding + graphWidth).toFloat()

        // Row box
        canvas.drawRect(
            xLeft,
            rowTop,
            xRight,
            rowBottom,
            topBottomPaint
        )

        // Zero line (amplitude)
        canvas.drawLine(
            xLeft,
            rowMid,
            xRight,
            rowMid,
            zeroPaint
        )

        // --- NEW: vertical time gridlines (every 0.2s, stronger every 1.0s) ---
        val minorStep = 0.2f    // 200 ms
        val majorStep = 1.0f    // 1 second

        val firstMinor =
            ceil((rowStartSec / minorStep).toDouble()).toFloat() * minorStep

        var tGrid = firstMinor
        while (tGrid < rowEndSec) {
            val rel = (tGrid - rowStartSec) / timeInRow
            val x = xLeft + rel * graphWidth

            // treat near-integer seconds as major lines
            val nearestInt = (tGrid / majorStep).roundToInt()
            val isMajor = abs(tGrid - nearestInt * majorStep) < 1e-3f

            canvas.drawLine(
                x,
                rowTop,
                x,
                rowBottom,
                if (isMajor) majorGridPaint else minorGridPaint
            )

            tGrid += minorStep
        }

        // Y-axis labels
        canvas.drawText(
            "0.0",
            (leftPadding - axisLabelPadding).toFloat(),
            rowMid + 8f,
            axisLabelPaint
        )
        val yMax = mapY(-1000f, rowTop, rowBottom)
        val yMin = mapY(1000f, rowTop, rowBottom)
        canvas.drawText(
            "+1.0",
            (leftPadding - axisLabelPadding).toFloat(),
            yMax + 8f,
            axisLabelPaint
        )
        canvas.drawText(
            "-1.0",
            (leftPadding - axisLabelPadding).toFloat(),
            yMin + 8f,
            axisLabelPaint
        )

        // Segment overlays
        if (segs.isNotEmpty()) {
            for (seg in segs) {
                val s0 = seg.startSec.coerceAtLeast(rowStartSec)
                val s1 = seg.endSec.coerceAtMost(rowEndSec)
                if (s1 <= s0) continue

                val rel0 = (s0 - rowStartSec) / timeInRow
                val rel1 = (s1 - rowStartSec) / timeInRow
                val x0 = leftPadding + rel0 * graphWidth
                val x1 = leftPadding + rel1 * graphWidth

                val paint = overlayPaints.getOrNull(seg.label) ?: overlayPaints.last()
                canvas.drawRect(x0.toFloat(), rowTop, x1.toFloat(), rowBottom, paint)
                canvas.drawRect(x0.toFloat(), rowTop, x1.toFloat(), rowBottom, borderPaint)
            }
        }

        // Waveform path
        val segStartSec = rowStartSec
        val segEndSec = rowEndSec
        val sStart = floor(segStartSec * samplesPerSec).toInt().coerceAtLeast(0)
        val sEnd = min(normalized.size, ceil(segEndSec * samplesPerSec).toInt())
        if (sEnd > sStart + 1) {
            val count = (sEnd - sStart).coerceAtLeast(2)
            val denom = (count - 1).toFloat()
            val path = Path()

            for (i in 0 until count) {
                val idx = sStart + i
                val t = i / denom
                val x = xLeft + t * (xRight - xLeft)
                val y = mapY(normalized[idx], rowTop, rowBottom)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, tracePaint)
        }

        // X-axis labels (time, matching major gridlines)
        val timeLabelY = rowBottom + timeLabelYOffset

        // Start label
        val startLabel = String.format(Locale.US, "%.1fs", rowStartSec)
        timeLabelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(startLabel, xLeft, timeLabelY, timeLabelPaint)

        // End label
        val endLabel = String.format(Locale.US, "%.1fs", rowEndSec)
        timeLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(endLabel, xRight, timeLabelY, timeLabelPaint)

        // Intermediate integer-second labels
        timeLabelPaint.textAlign = Paint.Align.CENTER
        val firstFullSec = ceil(rowStartSec).toInt()
        val lastFullSec = floor(rowEndSec).toInt()

        for (sec in firstFullSec..lastFullSec) {
            val secF = sec.toFloat()
            if (secF - rowStartSec < 1.0f || rowEndSec - secF < 1.0f) continue

            val relTime = (secF - rowStartSec) / timeInRow
            val x = leftPadding + relTime * graphWidth
            canvas.drawText("${sec.toFloat()}s", x.toFloat(), timeLabelY, timeLabelPaint)
        }
    }

    // Legend is drawn in HTML
    return bmp
}
