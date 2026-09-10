package com.stackhousethepoet.layercut.editor

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.sqrt

/**
 * Draws paint strokes, soft eraser (DST_OUT), or restore (copy from originalBitmap via
 * soft brush mask + SRC_OVER) onto a layer bitmap.
 *
 * Opacity ≥ 0.98 (UI 100%): full alpha 255 + hard edge (no BlurMaskFilter); pressure
 * does not reduce alpha. Below that: optional soft blur + aggressive opacity ease.
 *
 * [BrushSettings.size] is expected in **layer pixels** by the time it reaches stamp/
 * commit (ViewModel converts from screen-space slider values).
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

    /** Reusable scratch for restore stamps (avoids full-layer / per-move alloc). */
    private var restorePool: Bitmap? = null

    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val restoreMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val restoreOutPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hardClipPath = Path()

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
        val params = resolveParams(settings, pressureScale)

        strokePaint.strokeWidth = params.size
        strokePaint.maskFilter = if (params.useSoft) {
            BlurMaskFilter(params.size * 0.35f, BlurMaskFilter.Blur.NORMAL)
        } else null

        if (erase) {
            strokePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            strokePaint.color = Color.argb(params.alpha, 0, 0, 0)
        } else {
            strokePaint.xfermode = null
            val c = settings.color
            strokePaint.color = Color.argb(
                params.alpha,
                Color.red(c),
                Color.green(c),
                Color.blue(c)
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
        val params = resolveParams(settings, pressureScale)

        val temp = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
        try {
            val tempCanvas = Canvas(temp)
            tempCanvas.drawBitmap(original, 0f, 0f, null)
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = params.size
                color = Color.WHITE
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                if (params.useSoft) {
                    maskFilter = BlurMaskFilter(params.size * 0.35f, BlurMaskFilter.Blur.NORMAL)
                }
            }
            tempCanvas.drawPath(path, maskPaint)
            val outPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = params.alpha }
            Canvas(target).drawBitmap(temp, 0f, 0f, outPaint)
        } finally {
            temp.recycle()
        }
        path.reset()
        hasPoint = false
        return true
    }

    /** Stamp a single circle (useful for tap / live stroke). */
    fun stamp(
        target: Bitmap,
        x: Float,
        y: Float,
        settings: BrushSettings,
        erase: Boolean,
        pressureScale: Float = 1f
    ) {
        if (target.isRecycled) return
        val params = resolveParams(settings, pressureScale)
        stampPaint.maskFilter = if (params.useSoft) {
            BlurMaskFilter(params.size * 0.35f, BlurMaskFilter.Blur.NORMAL)
        } else {
            null
        }
        if (erase) {
            stampPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            stampPaint.color = Color.argb(params.alpha, 0, 0, 0)
        } else {
            stampPaint.xfermode = null
            val c = settings.color
            stampPaint.color = Color.argb(
                params.alpha,
                Color.red(c),
                Color.green(c),
                Color.blue(c)
            )
        }
        Canvas(target).drawCircle(x, y, params.size / 2f, stampPaint)
        stampPaint.xfermode = null
        stampPaint.maskFilter = null
    }

    /**
     * Restore stamp: circle mask copies pixels from [original] onto [target].
     * Clipped to brush bounds; reuses a pooled temp bitmap. Hard + full opacity uses
     * a cheap clipPath path (no temp alloc).
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

        val params = resolveParams(settings, pressureScale)
        val radius = params.size / 2f
        val blurPad = if (params.useSoft) params.size * 0.35f * 2f else 0f
        val pad = radius + blurPad

        val left = max(0, floor(x - pad).toInt())
        val top = max(0, floor(y - pad).toInt())
        val right = min(target.width, ceil(x + pad).toInt())
        val bottom = min(target.height, ceil(y + pad).toInt())
        if (left >= right || top >= bottom) return

        val tw = right - left
        val th = bottom - top
        val srcRect = Rect(left, top, right, bottom)
        val dstRect = Rect(left, top, right, bottom)

        // Hard + full opacity: clip circle and blit original region only (no temp).
        if (!params.useSoft && params.alpha >= 255) {
            val canvas = Canvas(target)
            hardClipPath.reset()
            hardClipPath.addCircle(x, y, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(hardClipPath)
            canvas.drawBitmap(original, srcRect, dstRect, null)
            canvas.restore()
            return
        }

        val temp = obtainRestoreTemp(tw, th)
        val tempCanvas = Canvas(temp)
        temp.eraseColor(Color.TRANSPARENT)
        val localDst = RectF(0f, 0f, tw.toFloat(), th.toFloat())
        tempCanvas.drawBitmap(original, srcRect, localDst, null)
        val cx = x - left
        val cy = y - top
        restoreMaskPaint.maskFilter = if (params.useSoft) {
            BlurMaskFilter(params.size * 0.35f, BlurMaskFilter.Blur.NORMAL)
        } else {
            null
        }
        tempCanvas.drawCircle(cx, cy, radius, restoreMaskPaint)
        restoreMaskPaint.maskFilter = null
        restoreOutPaint.alpha = params.alpha
        val outSrc = Rect(0, 0, tw, th)
        val outDst = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        Canvas(target).drawBitmap(temp, outSrc, outDst, restoreOutPaint)
    }

    private fun obtainRestoreTemp(w: Int, h: Int): Bitmap {
        val existing = restorePool
        if (existing != null && !existing.isRecycled &&
            existing.width >= w && existing.height >= h
        ) {
            return existing
        }
        existing?.let { if (!it.isRecycled) it.recycle() }
        // Grow with a little headroom so scrubbing with similar sizes reuses.
        val bw = max(w, ((w + 15) / 16) * 16)
        val bh = max(h, ((h + 15) / 16) * 16)
        val created = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        restorePool = created
        return created
    }

    /** Release pooled scratch (optional; bitmaps also GC with the engine). */
    fun releasePool() {
        restorePool?.let { if (!it.isRecycled) it.recycle() }
        restorePool = null
    }

    companion object {
        /** Opacity at or above this is treated as UI 100% — full punch-through. */
        const val FULL_OPACITY_THRESHOLD = 0.98f

        /** Stamp when finger moves at least this fraction of brush radius (layer px). */
        const val STAMP_SPACING_FACTOR = 0.4f

        /** Min interval between revision bumps during an active stroke (ms). ~60fps. */
        const val STROKE_BUMP_INTERVAL_MS = 16L

        /** Screen-space brush size slider range. */
        const val SCREEN_SIZE_MIN = 4f
        const val SCREEN_SIZE_MAX = 400f

        fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
            hypot(x2 - x1, y2 - y1)

        /**
         * Aggressive ease so mid-slider opacities still feel useful (sqrt curve).
         * At max opacity, alpha is always 255 and soft is forced off.
         */
        fun resolveParams(settings: BrushSettings, pressureScale: Float): StrokeParams {
            val sizePressure = pressureScale.coerceIn(0.15f, 2f)
            val size = max(1f, settings.size * sizePressure)
            val atMax = settings.opacity >= FULL_OPACITY_THRESHOLD
            val useSoft = settings.soft && !atMax
            val alpha = if (atMax) {
                255
            } else {
                val eased = sqrt(settings.opacity.coerceIn(0f, 1f).toDouble()).toFloat()
                val pressureAlpha = pressureScale.coerceIn(0.2f, 1f)
                (eased * pressureAlpha * 255f).toInt().coerceIn(1, 255)
            }
            return StrokeParams(size = size, alpha = alpha, useSoft = useSoft)
        }
    }

    data class StrokeParams(
        val size: Float,
        val alpha: Int,
        val useSoft: Boolean
    )

    private fun resolveParams(settings: BrushSettings, pressureScale: Float): StrokeParams =
        Companion.resolveParams(settings, pressureScale)
}
