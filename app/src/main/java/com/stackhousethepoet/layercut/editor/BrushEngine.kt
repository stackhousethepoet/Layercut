package com.stackhousethepoet.layercut.editor

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.hypot
import kotlin.math.max

/**
 * Draws paint strokes or soft eraser (clears alpha via DST_OUT) onto a layer bitmap.
 */
class BrushEngine {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isDither = true
    }

    private val path = Path()
    private var lastX = 0f
    private var lastY = 0f
    private var hasPoint = false

    fun beginStroke(x: Float, y: Float) {
        path.reset()
        path.moveTo(x, y)
        lastX = x
        lastY = y
        hasPoint = true
    }

    fun appendStroke(x: Float, y: Float) {
        if (!hasPoint) {
            beginStroke(x, y)
            return
        }
        val midX = (lastX + x) / 2f
        val midY = (lastY + y) / 2f
        path.quadTo(lastX, lastY, midX, midY)
        lastX = x
        lastY = y
    }

    fun endStroke() {
        hasPoint = false
    }

    /**
     * Apply current path onto [target] as paint or eraser.
     * Returns true if anything was drawn.
     */
    fun commitStroke(
        target: Bitmap,
        settings: BrushSettings,
        erase: Boolean,
        pressureScale: Float = 1f
    ): Boolean {
        if (target.isRecycled) return false
        val size = max(1f, settings.size * pressureScale.coerceIn(0.15f, 2f))
        val alpha = (settings.opacity * pressureScale.coerceIn(0.2f, 1f) * 255f).toInt().coerceIn(1, 255)

        strokePaint.strokeWidth = size
        strokePaint.maskFilter = if (settings.soft) {
            BlurMaskFilter(size * 0.35f, BlurMaskFilter.Blur.NORMAL)
        } else null

        if (erase) {
            strokePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            strokePaint.color = android.graphics.Color.argb(alpha, 0, 0, 0)
        } else {
            strokePaint.xfermode = null
            val c = settings.color
            strokePaint.color = android.graphics.Color.argb(
                alpha,
                android.graphics.Color.red(c),
                android.graphics.Color.green(c),
                android.graphics.Color.blue(c)
            )
        }

        val canvas = Canvas(target)
        canvas.drawPath(path, strokePaint)
        strokePaint.xfermode = null
        strokePaint.maskFilter = null
        path.reset()
        hasPoint = false
        return true
    }

    /** Stamp a single soft circle (useful for tap). */
    fun stamp(
        target: Bitmap,
        x: Float,
        y: Float,
        settings: BrushSettings,
        erase: Boolean,
        pressureScale: Float = 1f
    ) {
        if (target.isRecycled) return
        val size = max(1f, settings.size * pressureScale.coerceIn(0.15f, 2f))
        val alpha = (settings.opacity * pressureScale.coerceIn(0.2f, 1f) * 255f).toInt().coerceIn(1, 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            if (settings.soft) {
                maskFilter = BlurMaskFilter(size * 0.35f, BlurMaskFilter.Blur.NORMAL)
            }
            if (erase) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                color = android.graphics.Color.argb(alpha, 0, 0, 0)
            } else {
                val c = settings.color
                color = android.graphics.Color.argb(
                    alpha,
                    android.graphics.Color.red(c),
                    android.graphics.Color.green(c),
                    android.graphics.Color.blue(c)
                )
            }
        }
        Canvas(target).drawCircle(x, y, size / 2f, paint)
    }

    companion object {
        fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
            hypot(x2 - x1, y2 - y1)
    }
}
