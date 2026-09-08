package com.stackhousethepoet.layercut.editor

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Draws paint strokes, soft eraser (DST_OUT), or restore (copy from originalBitmap via
 * soft brush mask + SRC_OVER) onto a layer bitmap.
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

    /**
     * Restore stroke: soft brush mask copies pixels from [original] onto [target].
     * Draw white soft path as mask, SRC_IN original, then SRC_OVER onto target with opacity.
     * Does not use paint color.
     */
    fun commitRestoreStroke(
        target: Bitmap,
        original: Bitmap,
        settings: BrushSettings,
        pressureScale: Float = 1f
    ): Boolean {
        if (target.isRecycled || original.isRecycled) return false
        if (target.width != original.width || target.height != original.height) return false
        val size = max(1f, settings.size * pressureScale.coerceIn(0.15f, 2f))
        val alpha = (settings.opacity * pressureScale.coerceIn(0.2f, 1f) * 255f).toInt().coerceIn(1, 255)

        val temp = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
        try {
            val tempCanvas = Canvas(temp)
            // 1) Draw original pixels into temp
            tempCanvas.drawBitmap(original, 0f, 0f, null)
            // 2) DST_IN soft brush path — keep original only under the stroke
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = size
                color = android.graphics.Color.WHITE
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                if (settings.soft) {
                    maskFilter = BlurMaskFilter(size * 0.35f, BlurMaskFilter.Blur.NORMAL)
                }
            }
            tempCanvas.drawPath(path, maskPaint)
            // 3) SRC_OVER onto working bitmap with opacity (no paint color)
            val outPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
            Canvas(target).drawBitmap(temp, 0f, 0f, outPaint)
        } finally {
            temp.recycle()
        }
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

    /**
     * Restore stamp: soft circle mask copies pixels from [original] onto [target].
     * PorterDuff: soft white circle mask, SRC_IN original region, SRC_OVER onto target
     * with opacity. Does not use paint color.
     */
    fun stampRestore(
        target: Bitmap,
        original: Bitmap,
        x: Float,
        y: Float,
        settings: BrushSettings,
        pressureScale: Float = 1f
    ) {
        if (target.isRecycled || original.isRecycled) return
        if (target.width != original.width || target.height != original.height) return

        val size = max(1f, settings.size * pressureScale.coerceIn(0.15f, 2f))
        val alpha = (settings.opacity * pressureScale.coerceIn(0.2f, 1f) * 255f).toInt().coerceIn(1, 255)
        val radius = size / 2f
        val blurPad = if (settings.soft) size * 0.35f * 2f else 0f
        val pad = radius + blurPad

        val left = max(0, floor(x - pad).toInt())
        val top = max(0, floor(y - pad).toInt())
        val right = min(target.width, ceil(x + pad).toInt())
        val bottom = min(target.height, ceil(y + pad).toInt())
        if (left >= right || top >= bottom) return

        val tw = right - left
        val th = bottom - top
        val temp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        try {
            val tempCanvas = Canvas(temp)
            val srcRect = Rect(left, top, right, bottom)
            val dstRect = RectF(0f, 0f, tw.toFloat(), th.toFloat())
            // 1) Draw original region into temp
            tempCanvas.drawBitmap(original, srcRect, dstRect, null)
            // 2) DST_IN soft circle — keep original only under the brush
            val cx = x - left
            val cy = y - top
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = android.graphics.Color.WHITE
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                if (settings.soft) {
                    maskFilter = BlurMaskFilter(size * 0.35f, BlurMaskFilter.Blur.NORMAL)
                }
            }
            tempCanvas.drawCircle(cx, cy, radius, maskPaint)
            // 3) SRC_OVER onto working bitmap with opacity (no paint color)
            val outPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
            Canvas(target).drawBitmap(temp, left.toFloat(), top.toFloat(), outPaint)
        } finally {
            temp.recycle()
        }
    }

    companion object {
        fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
            hypot(x2 - x1, y2 - y1)
    }
}
