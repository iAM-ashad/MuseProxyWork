package com.iamashad.musesample.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.iamashad.musesample.model.SegmentLabel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Build stacked PCG bitmap and optionally paint segmentation overlays.
 *
 * - normalized: downsampled envelope samples (approx ±1000)
 * - secondsTotal: total duration in seconds (must match how normalized was created)
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
    segments: List<SegmentLabel> = emptyList(),        // optional segmentation overlays
    minSegmentDurationSec: Float = 0.04f              // merge segments shorter than this
): Bitmap {
    require(secondsTotal > 0f && normalized.isNotEmpty())

    // optionally reduce speckle noise by merging very short segments
    val segs = mergeShortSegments(segments, minSegmentDurationSec)

    val rows = max(1, ceil(secondsTotal / segmentSec).toInt())
    val topPadding = 40
    val bottomPadding = 64
    val availableH = heightPx - topPadding - bottomPadding - (rows - 1) * rowSpacingPx
    val rowH = max(160, availableH / rows)
    val samplesPerSec = (normalized.size / secondsTotal).coerceAtLeast(1f)

    // paints
    val topBottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#B0C4DE".toColorInt(); strokeWidth = 2f; style = Paint.Style.STROKE
    }
    val zeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#888888".toColorInt(); strokeWidth = 1.2f; pathEffect =
        DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }
    val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; strokeWidth = 2f; style = Paint.Style.STROKE
    }

    // overlay paints per class (semi-transparent fills)
    val overlayPaints = arrayOf(
        Paint().apply {
            color = Color.argb(110, 220, 38, 38); style = Paint.Style.FILL
        },   // S1 - red
        Paint().apply {
            color = Color.argb(90, 34, 197, 94); style = Paint.Style.FILL
        },   // Systole - green
        Paint().apply {
            color = Color.argb(110, 37, 99, 235); style = Paint.Style.FILL
        },  // S2 - blue
        Paint().apply {
            color = Color.argb(70, 255, 140, 0); style = Paint.Style.FILL
        }    // Diastole - orange
    )
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#0b4da2".toColorInt(); strokeWidth = 1.2f; style = Paint.Style.STROKE
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#0b4da2".toColorInt(); textSize = 20f; typeface =
        Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun mapY(v: Float, rowTop: Int, rowBottom: Int): Float {
        val mid = (rowTop + rowBottom) / 2f
        val half = (rowBottom - rowTop) * 0.40f
        return mid - (v / 1000f) * half
    }

    val bmp = createBitmap(widthPx, heightPx)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)

    // For each row: draw borders, zero line, trace, and overlays overlapping this row.
    for (r in 0 until rows) {
        val rowTop = (topPadding + r * (rowH + rowSpacingPx)).toFloat()
        val rowBottom = rowTop + rowH

        // Row borders
        canvas.drawLine(0f, rowTop, widthPx.toFloat(), rowTop, topBottomPaint)
        canvas.drawLine(0f, rowBottom, widthPx.toFloat(), rowBottom, topBottomPaint)

        // Baseline (0)
        val zY = mapY(0f, rowTop.toInt(), rowBottom.toInt())
        canvas.drawLine(0f, zY, widthPx.toFloat(), zY, zeroPaint)

        // Compute seconds covered by this row
        val rowStartSec = r * segmentSec
        val rowEndSec = min((r + 1) * segmentSec, secondsTotal)

        // Draw segmentation overlays that intersect this row
        if (segs.isNotEmpty()) {
            for (seg in segs) {
                val s0 = seg.startSec.coerceAtLeast(rowStartSec)
                val s1 = seg.endSec.coerceAtMost(rowEndSec)
                if (s1 <= s0) continue
                // Map seconds -> x (left..right)
                val rel0 = (s0 - rowStartSec) / (rowEndSec - rowStartSec)
                val rel1 = (s1 - rowStartSec) / (rowEndSec - rowStartSec)
                val x0 = rel0 * widthPx
                val x1 = rel1 * widthPx
                // paint rectangle covering full row band
                val paint = overlayPaints.getOrNull(seg.label) ?: overlayPaints.last()
                canvas.drawRect(x0, rowTop, x1, rowBottom, paint)
                // optional border
                canvas.drawRect(x0, rowTop, x1, rowBottom, borderPaint)
            }
        }
        // Draw the trace on top of overlays
        val segStartSec = rowStartSec
        val sStart = kotlin.math.floor(segStartSec * samplesPerSec).toInt().coerceAtLeast(0)
        val sEnd = min(normalized.size, ceil(rowEndSec * samplesPerSec).toInt())
        if (sEnd <= sStart + 1) continue

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

    // Draw legend at bottom-left
    val legendLeft = 12f
    var legendTop = heightPx - bottomPadding + 6f
    val boxSize = 18f
    val gap = 8f
    val labels = arrayOf("S1", "Systole", "S2", "Diastole")
    for (i in labels.indices) {
        val p = overlayPaints[i]
        val rect = RectF(legendLeft, legendTop, legendLeft + boxSize, legendTop + boxSize)
        canvas.drawRect(rect, p)
        canvas.drawRect(rect, borderPaint)
        canvas.drawText(labels[i], legendLeft + boxSize + gap, legendTop + boxSize - 4f, labelPaint)
        legendTop += boxSize + 6f
    }

    return bmp
}

/** Merge extremely short segments into neighbor segments to reduce speckle visually. */
private fun mergeShortSegments(segs: List<SegmentLabel>, minDur: Float): List<SegmentLabel> {
    if (segs.isEmpty() || minDur <= 0f) return segs
    val out = mutableListOf<SegmentLabel>()
    var cur = segs[0]
    for (i in 1 until segs.size) {
        val s = segs[i]
        val dur = cur.endSec - cur.startSec
        if (dur < minDur && out.isNotEmpty()) {
            // merge short cur into previous if same label else into next by expanding previous end
            val prev = out.removeAt(out.lastIndex)
            val merged = if (prev.label == cur.label) {
                SegmentLabel(prev.label, prev.startSec, cur.endSec)
            } else {
                // merge by extending prev to cur.endSec (safer visually)
                SegmentLabel(prev.label, prev.startSec, cur.endSec)
            }
            out.add(merged)
        } else {
            out.add(cur)
        }
        cur = s
    }
    // push last
    out.add(cur)
    // a second pass: if first segment is short, merge into next
    if (out.size >= 2) {
        val firstDur = out[0].endSec - out[0].startSec
        if (firstDur < minDur) {
            val merged = SegmentLabel(out[1].label, out[0].startSec, out[1].endSec)
            val rest = out.drop(2)
            val newList = mutableListOf<SegmentLabel>(merged); newList.addAll(rest)
            return newList
        }
    }
    return out
}
