package com.stackhousethepoet.layercut.editor

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * PicSay-style local bulge/pinch on a layer bitmap region.
 *
 * [amount] > 0 enlarges (bulge) the region under [cx],[cy];
 * [amount] < 0 shrinks (pinch). Soft radial falloff outside the core.
 * Samples from [source] into [dest] (same size); only the radius bbox is rewritten.
 */
object DistortEngine {

    /** Max |amount| applied after strength scaling (gentle enough for incremental gestures). */
    const val MAX_AMOUNT = 0.85f

    /**
     * @param radius Effect radius in layer pixels.
     * @param amount Signed warp strength in roughly [-MAX_AMOUNT, MAX_AMOUNT].
     */
    fun applyRadialWarp(
        source: Bitmap,
        dest: Bitmap,
        cx: Float,
        cy: Float,
        radius: Float,
        amount: Float
    ) {
        if (source.isRecycled || dest.isRecycled) return
        if (source.width != dest.width || source.height != dest.height) return
        val r = radius.coerceAtLeast(1f)
        val amt = amount.coerceIn(-MAX_AMOUNT, MAX_AMOUNT)
        if (kotlin.math.abs(amt) < 1e-4f) {
            // Neutral — restore dest from source in the last dirty region by full copy of bbox
            // callers re-warp from source each frame, so just copy bbox unchanged.
            copyRegion(source, dest, cx, cy, r)
            return
        }

        val w = source.width
        val h = source.height
        val x0 = max(0, floor(cx - r).toInt())
        val y0 = max(0, floor(cy - r).toInt())
        val x1 = min(w - 1, ceil(cx + r).toInt())
        val y1 = min(h - 1, ceil(cy + r).toInt())
        if (x0 > x1 || y0 > y1) return

        val rw = x1 - x0 + 1
        val rh = y1 - y0 + 1
        val srcPixels = IntArray(rw * rh)
        val dstPixels = IntArray(rw * rh)
        source.getPixels(srcPixels, 0, rw, x0, y0, rw, rh)

        // Also need a slightly larger sample pad for bilinear when pulling from outside bbox.
        // For simplicity, sample from full source via getPixel / bilinear on srcPixels when inside,
        // and fall back to source.getPixel for out-of-bbox (rare at edges).
        val r2 = r * r
        for (row in 0 until rh) {
            val py = y0 + row
            val dy = py - cy
            for (col in 0 until rw) {
                val px = x0 + col
                val dx = px - cx
                val d2 = dx * dx + dy * dy
                val idx = row * rw + col
                if (d2 >= r2) {
                    dstPixels[idx] = srcPixels[idx]
                    continue
                }
                val d = kotlin.math.sqrt(d2.toDouble()).toFloat()
                if (d < 0.5f) {
                    dstPixels[idx] = srcPixels[idx]
                    continue
                }
                val t = d / r
                // Smoothstep-ish soft falloff: (1 - t^2)^2
                val u = 1f - t * t
                val falloff = u * u
                val strength = amt * falloff
                // factor > 1 → sample closer to center (bulge); < 1 → farther (pinch)
                val factor = (1f + strength).coerceAtLeast(0.15f)
                val srcD = d / factor
                val sx = cx + dx / d * srcD
                val sy = cy + dy / d * srcD
                dstPixels[idx] = sampleBilinearRegion(source, srcPixels, x0, y0, rw, rh, sx, sy)
            }
        }
        dest.setPixels(dstPixels, 0, rw, x0, y0, rw, rh)
    }

    /** Copy the circular bbox from source → dest (identity / reset region). */
    fun copyRegion(source: Bitmap, dest: Bitmap, cx: Float, cy: Float, radius: Float) {
        if (source.isRecycled || dest.isRecycled) return
        val r = radius.coerceAtLeast(1f)
        val w = source.width
        val h = source.height
        val x0 = max(0, floor(cx - r).toInt())
        val y0 = max(0, floor(cy - r).toInt())
        val x1 = min(w - 1, ceil(cx + r).toInt())
        val y1 = min(h - 1, ceil(cy + r).toInt())
        if (x0 > x1 || y0 > y1) return
        val rw = x1 - x0 + 1
        val rh = y1 - y0 + 1
        val buf = IntArray(rw * rh)
        source.getPixels(buf, 0, rw, x0, y0, rw, rh)
        dest.setPixels(buf, 0, rw, x0, y0, rw, rh)
    }

    private fun sampleBilinearRegion(
        full: Bitmap,
        region: IntArray,
        x0: Int,
        y0: Int,
        rw: Int,
        rh: Int,
        fx: Float,
        fy: Float
    ): Int {
        val w = full.width
        val h = full.height
        if (fx < 0f || fy < 0f || fx >= w - 1f || fy >= h - 1f) {
            val ix = fx.toInt().coerceIn(0, w - 1)
            val iy = fy.toInt().coerceIn(0, h - 1)
            // Prefer region buffer when possible
            if (ix in x0 until (x0 + rw) && iy in y0 until (y0 + rh)) {
                return region[(iy - y0) * rw + (ix - x0)]
            }
            return full.getPixel(ix, iy)
        }
        val x1 = floor(fx).toInt()
        val y1 = floor(fy).toInt()
        val x2 = min(w - 1, x1 + 1)
        val y2 = min(h - 1, y1 + 1)
        val tx = fx - x1
        val ty = fy - y1

        fun pix(x: Int, y: Int): Int {
            if (x in x0 until (x0 + rw) && y in y0 until (y0 + rh)) {
                return region[(y - y0) * rw + (x - x0)]
            }
            return full.getPixel(x, y)
        }

        val c00 = pix(x1, y1)
        val c10 = pix(x2, y1)
        val c01 = pix(x1, y2)
        val c11 = pix(x2, y2)
        return bilinearArgb(c00, c10, c01, c11, tx, ty)
    }

    private fun bilinearArgb(c00: Int, c10: Int, c01: Int, c11: Int, tx: Float, ty: Float): Int {
        fun chan(shift: Int): Int {
            val v00 = (c00 ushr shift) and 0xFF
            val v10 = (c10 ushr shift) and 0xFF
            val v01 = (c01 ushr shift) and 0xFF
            val v11 = (c11 ushr shift) and 0xFF
            val a = v00 + (v10 - v00) * tx
            val b = v01 + (v11 - v01) * tx
            return (a + (b - a) * ty).toInt().coerceIn(0, 255)
        }
        val a = chan(24)
        val r = chan(16)
        val g = chan(8)
        val b = chan(0)
        return Color.argb(a, r, g, b)
    }
}
