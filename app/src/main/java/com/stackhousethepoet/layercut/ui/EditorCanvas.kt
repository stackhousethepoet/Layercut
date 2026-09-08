package com.stackhousethepoet.layercut.ui

import android.graphics.Paint as AndroidPaint
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.stackhousethepoet.layercut.editor.CoordMath
import com.stackhousethepoet.layercut.editor.EditorViewModel
import com.stackhousethepoet.layercut.editor.ToolMode

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorCanvas(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val revision = viewModel.revision
    val layers = viewModel.layers
    val viewport = viewModel.viewport
    val contentW = viewModel.contentWidth
    val contentH = viewModel.contentHeight
    val tool = viewModel.toolMode
    val activeId = viewModel.activeLayerId

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val gestureModifier = when (tool) {
        ToolMode.PAINT, ToolMode.ERASER -> Modifier.pointerInteropFilter { event ->
            val pressure = event.pressure.takeIf { it > 0f } ?: 1f
            val w = canvasSize.width.toFloat().coerceAtLeast(1f)
            val h = canvasSize.height.toFloat().coerceAtLeast(1f)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.beginStroke(event.x, event.y, w, h, pressure)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    viewModel.continueStroke(event.x, event.y, w, h, pressure)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    viewModel.endStroke()
                    true
                }
                else -> false
            }
        }
        ToolMode.PAN -> Modifier.pointerInput(tool) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent()
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = true)
                    if (zoom != 1f || pan != Offset.Zero) {
                        viewModel.panZoomBy(zoom, pan.x, pan.y, centroid.x, centroid.y)
                    }
                    pressed = event.changes.any { it.pressed }
                }
            }
        }
        ToolMode.TRANSFORM -> Modifier.pointerInput(tool, activeId) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                viewModel.beginTransformGesture()
                try {
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val rotation = event.calculateRotation()
                        if (zoom != 1f || pan != Offset.Zero || rotation != 0f) {
                            viewModel.transformActiveLayer(pan.x, pan.y, zoom, rotation)
                        }
                        pressed = event.changes.any { it.pressed }
                    }
                } finally {
                    viewModel.endTransformGesture()
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .then(gestureModifier)
    ) {
        @Suppress("UNUSED_EXPRESSION")
        revision

        val cw = size.width
        val ch = size.height
        if (contentW <= 0f || contentH <= 0f || layers.isEmpty()) {
            drawRect(Color(0xFF2A2A2E))
            return@Canvas
        }

        drawIntoCanvas { composeCanvas ->
            val nc = composeCanvas.nativeCanvas
            nc.save()
            val originX = cw / 2f + viewport.offsetX - (contentW * viewport.scale) / 2f
            val originY = ch / 2f + viewport.offsetY - (contentH * viewport.scale) / 2f
            nc.translate(originX, originY)
            nc.scale(viewport.scale, viewport.scale)

            val check = 24f
            val light = AndroidPaint().apply { color = 0xFF3A3A3E.toInt() }
            val dark = AndroidPaint().apply { color = 0xFF2A2A2E.toInt() }
            var y = 0f
            var row = 0
            while (y < contentH) {
                var x = 0f
                var col = 0
                while (x < contentW) {
                    nc.drawRect(
                        x,
                        y,
                        (x + check).coerceAtMost(contentW),
                        (y + check).coerceAtMost(contentH),
                        if ((row + col) % 2 == 0) light else dark
                    )
                    x += check
                    col++
                }
                y += check
                row++
            }

            nc.clipRect(0f, 0f, contentW, contentH)

            val bitmapPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
            for (layer in layers) {
                if (!layer.visible || layer.bitmap.isRecycled) continue
                bitmapPaint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
                val matrix = CoordMath.layerDrawMatrix(layer)
                nc.drawBitmap(layer.bitmap, matrix, bitmapPaint)
            }

            val active = layers.find { it.id == activeId }
            if (active != null && !active.bitmap.isRecycled) {
                val outline = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    style = AndroidPaint.Style.STROKE
                    strokeWidth = 2f / viewport.scale
                    color = 0xFF7C4DFF.toInt()
                }
                nc.save()
                nc.concat(CoordMath.layerDrawMatrix(active))
                nc.drawRect(
                    0f,
                    0f,
                    active.bitmap.width.toFloat(),
                    active.bitmap.height.toFloat(),
                    outline
                )
                nc.restore()
            }

            nc.restore()
        }
    }
}
