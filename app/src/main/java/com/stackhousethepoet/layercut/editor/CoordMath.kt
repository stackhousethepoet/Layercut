package com.stackhousethepoet.layercut.editor

import android.graphics.Matrix
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Map a screen/canvas touch point into the active layer's bitmap pixel space.
 */
object CoordMath {

    fun screenToLayer(
        screenX: Float,
        screenY: Float,
        viewport: CanvasViewport,
        layer: EditorLayer,
        canvasWidth: Float,
        canvasHeight: Float,
        contentWidth: Float,
        contentHeight: Float
    ): Pair<Float, Float> {
        // Undo viewport: screen → content space (centered content)
        val contentOriginX = canvasWidth / 2f + viewport.offsetX - (contentWidth * viewport.scale) / 2f
        val contentOriginY = canvasHeight / 2f + viewport.offsetY - (contentHeight * viewport.scale) / 2f
        val contentX = (screenX - contentOriginX) / viewport.scale
        val contentY = (screenY - contentOriginY) / viewport.scale

        // Undo layer transform (content → layer local, origin at layer top-left)
        val t = layer.transform
        val cx = layer.bitmap.width / 2f
        val cy = layer.bitmap.height / 2f
        // Point relative to transformed layer center in content coords
        var x = contentX - (cx + t.offsetX)
        var y = contentY - (cy + t.offsetY)
        // Inverse rotate
        val rad = Math.toRadians(-t.rotationDeg.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        val rx = x * cos - y * sin
        val ry = x * sin + y * cos
        // Inverse scale
        val s = if (t.scale == 0f) 1f else t.scale
        x = rx / s + cx
        y = ry / s + cy
        return x to y
    }

    /**
     * Convert a brush diameter measured in on-screen pixels into layer bitmap pixels.
     * Combined scale = viewport zoom × layer transform scale (same factors CoordMath
     * uses when mapping points). Classic screen-space brush: zoom out → larger tip
     * in layer space; zoom in → finer control.
     */
    fun screenBrushSizeToLayer(
        screenSize: Float,
        viewport: CanvasViewport,
        layer: EditorLayer
    ): Float {
        val combined = max(viewport.scale * layer.transform.scale, 1e-3f)
        return screenSize / combined
    }

    fun layerDrawMatrix(layer: EditorLayer): Matrix {
        val t = layer.transform
        val cx = layer.bitmap.width / 2f
        val cy = layer.bitmap.height / 2f
        return Matrix().apply {
            postTranslate(-cx, -cy)
            postScale(t.scale, t.scale)
            postRotate(t.rotationDeg)
            postTranslate(cx + t.offsetX, cy + t.offsetY)
        }
    }
}
