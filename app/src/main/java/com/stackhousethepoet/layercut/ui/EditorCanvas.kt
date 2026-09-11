package com.stackhousethepoet.layercut.ui

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.stackhousethepoet.layercut.editor.CoordMath
import com.stackhousethepoet.layercut.editor.EditorViewModel
import com.stackhousethepoet.layercut.editor.ToolMode
import kotlin.math.hypot

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

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(tool, activeId) {
                val canvasW = size.width.toFloat().coerceAtLeast(1f)
                val canvasH = size.height.toFloat().coerceAtLeast(1f)

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    when (tool) {
                        ToolMode.TRANSFORM -> handleTransform(viewModel)
                        ToolMode.PAN -> handleViewportPanZoom(viewModel, canvasW, canvasH)
                        ToolMode.PAINT, ToolMode.ERASER, ToolMode.RESTORE ->
                            handleBrushWithPinch(
                                viewModel = viewModel,
                                down = down,
                                canvasW = canvasW,
                                canvasH = canvasH
                            )
                        ToolMode.MAGIC ->
                            handleMagicWithPinch(
                                viewModel = viewModel,
                                down = down,
                                canvasW = canvasW,
                                canvasH = canvasH
                            )
                        ToolMode.EYEDROPPER ->
                            handleEyedropperWithPinch(
                                viewModel = viewModel,
                                down = down,
                                canvasW = canvasW,
                                canvasH = canvasH
                            )
                        ToolMode.DISTORT ->
                            handleDistortWithPinch(
                                viewModel = viewModel,
                                down = down,
                                canvasW = canvasW,
                                canvasH = canvasH
                            )
                    }
                }
            }
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
                val showLayerOutline =
                    tool == ToolMode.TRANSFORM || tool == ToolMode.DISTORT
                nc.save()
                nc.concat(CoordMath.layerDrawMatrix(active))
                // Active-layer selection box only in Move/Distort (not Paint/Erase/etc.)
                if (showLayerOutline) {
                    val outline = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = 2f / viewport.scale
                        color = 0xFF00E8C8.toInt()
                    }
                    nc.drawRect(
                        0f,
                        0f,
                        active.bitmap.width.toFloat(),
                        active.bitmap.height.toFloat(),
                        outline
                    )
                }
                // Distort anchor + radius guide in layer space
                if (tool == ToolMode.DISTORT && viewModel.hasDistortAnchor) {
                    val ax = viewModel.distortAnchorX
                    val ay = viewModel.distortAnchorY
                    val rad = viewModel.distortSettings.radius
                    val guide = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = 1.5f / (viewport.scale * active.transform.scale.coerceAtLeast(0.05f))
                        color = 0xAAFFAB40.toInt()
                    }
                    val cross = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = 2f / (viewport.scale * active.transform.scale.coerceAtLeast(0.05f))
                        color = 0xFFFFAB40.toInt()
                    }
                    nc.drawCircle(ax, ay, rad, guide)
                    val arm = 10f / active.transform.scale.coerceAtLeast(0.05f)
                    nc.drawLine(ax - arm, ay, ax + arm, ay, cross)
                    nc.drawLine(ax, ay - arm, ax, ay + arm, cross)
                }
                nc.restore()
            }

            nc.restore()
        }
    }
}

private fun pressureOf(change: PointerInputChange): Float {
    val p = change.pressure
    return if (p in 0.01f..1f) p else 1f
}

private suspend fun AwaitPointerEventScope.handleTransform(viewModel: EditorViewModel) {
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
            event.changes.forEach { if (it.positionChanged()) it.consume() }
            pressed = event.changes.any { it.pressed }
        }
    } finally {
        viewModel.endTransformGesture()
    }
}

/** Pan tool: one- or two-finger pan/zoom (gallery-style). */
private suspend fun AwaitPointerEventScope.handleViewportPanZoom(
    viewModel: EditorViewModel,
    canvasW: Float,
    canvasH: Float
) {
    var pressed = true
    while (pressed) {
        val event = awaitPointerEvent()
        applyPanZoom(viewModel, event, canvasW, canvasH)
        event.changes.forEach { if (it.positionChanged()) it.consume() }
        pressed = event.changes.any { it.pressed }
    }
}

/**
 * Brush tools: one finger paints; a second finger cancels the stroke and switches
 * to gallery-style pinch zoom / two-finger pan for the rest of the gesture.
 */
private suspend fun AwaitPointerEventScope.handleBrushWithPinch(
    viewModel: EditorViewModel,
    down: PointerInputChange,
    canvasW: Float,
    canvasH: Float
) {
    viewModel.beginStroke(
        down.position.x,
        down.position.y,
        canvasW,
        canvasH,
        pressureOf(down)
    )
    down.consume()

    var panZoomMode = false
    var active = true
    while (active) {
        val event = awaitPointerEvent()
        val pressedPointers = event.changes.filter { it.pressed }

        if (pressedPointers.isEmpty()) {
            if (!panZoomMode) viewModel.endStroke()
            active = false
            continue
        }

        if (pressedPointers.size >= 2) {
            if (!panZoomMode) {
                viewModel.endStroke()
                panZoomMode = true
            }
            applyPanZoom(viewModel, event, canvasW, canvasH)
            event.changes.forEach { it.consume() }
        } else if (!panZoomMode) {
            val change = pressedPointers.first()
            viewModel.continueStroke(
                change.position.x,
                change.position.y,
                canvasW,
                canvasH,
                pressureOf(change)
            )
            change.consume()
        } else {
            // After pinch, remaining single finger continues panning.
            applyPanZoom(viewModel, event, canvasW, canvasH)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
}

/**
 * Magic: single-finger tap applies fill; two fingers switch to pan/zoom without
 * requiring the Pan tool.
 */
private suspend fun AwaitPointerEventScope.handleMagicWithPinch(
    viewModel: EditorViewModel,
    down: PointerInputChange,
    canvasW: Float,
    canvasH: Float
) {
    val tapX = down.position.x
    val tapY = down.position.y
    down.consume()

    var becameMulti = false
    var active = true
    while (active) {
        val event = awaitPointerEvent()
        val pressedPointers = event.changes.filter { it.pressed }

        if (pressedPointers.isEmpty()) {
            if (!becameMulti) {
                viewModel.magicEraseAt(tapX, tapY, canvasW, canvasH)
            }
            active = false
            continue
        }

        if (pressedPointers.size >= 2) {
            becameMulti = true
            applyPanZoom(viewModel, event, canvasW, canvasH)
            event.changes.forEach { it.consume() }
        } else if (becameMulti) {
            applyPanZoom(viewModel, event, canvasW, canvasH)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } else {
            pressedPointers.forEach { it.consume() }
        }
    }
}

/**
 * Eyedropper: single-finger tap samples composite color; two fingers pan/zoom.
 */
private suspend fun AwaitPointerEventScope.handleEyedropperWithPinch(
    viewModel: EditorViewModel,
    down: PointerInputChange,
    canvasW: Float,
    canvasH: Float
) {
    val tapX = down.position.x
    val tapY = down.position.y
    down.consume()

    var becameMulti = false
    var active = true
    while (active) {
        val event = awaitPointerEvent()
        val pressedPointers = event.changes.filter { it.pressed }

        if (pressedPointers.isEmpty()) {
            if (!becameMulti) {
                viewModel.sampleColorAt(tapX, tapY, canvasW, canvasH)
            }
            active = false
            continue
        }

        if (pressedPointers.size >= 2) {
            becameMulti = true
            applyPanZoom(viewModel, event, canvasW, canvasH)
            event.changes.forEach { it.consume() }
        } else if (becameMulti) {
            applyPanZoom(viewModel, event, canvasW, canvasH)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } else {
            pressedPointers.forEach { it.consume() }
        }
    }
}


/**
 * Distort (Option B): one finger — tap sets warp center; drag away = bulge, toward = pinch.
 * Two fingers cancel into gallery pan/zoom (same as brush tools). Soft radius/strength from chrome.
 */
private suspend fun AwaitPointerEventScope.handleDistortWithPinch(
    viewModel: EditorViewModel,
    down: PointerInputChange,
    canvasW: Float,
    canvasH: Float
) {
    val downX = down.position.x
    val downY = down.position.y
    down.consume()

    val dragThresholdPx = 12f
    var dragged = false
    var distortStarted = false
    var panZoomMode = false
    var active = true

    while (active) {
        val event = awaitPointerEvent()
        val pressedPointers = event.changes.filter { it.pressed }

        if (pressedPointers.isEmpty()) {
            if (panZoomMode) {
                // viewport only
            } else if (distortStarted) {
                viewModel.endDistortGesture()
            } else if (!dragged) {
                viewModel.setDistortAnchorAt(downX, downY, canvasW, canvasH)
            }
            active = false
            continue
        }

        if (pressedPointers.size >= 2) {
            if (distortStarted && !panZoomMode) {
                viewModel.endDistortGesture()
                distortStarted = false
            }
            panZoomMode = true
            applyPanZoom(viewModel, event, canvasW, canvasH)
            event.changes.forEach { it.consume() }
            continue
        }

        if (panZoomMode) {
            applyPanZoom(viewModel, event, canvasW, canvasH)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
            continue
        }

        val change = pressedPointers.first()
        val distFromDown = hypot(
            (change.position.x - downX).toDouble(),
            (change.position.y - downY).toDouble()
        ).toFloat()
        if (!dragged && distFromDown >= dragThresholdPx) {
            dragged = true
        }
        if (dragged) {
            if (!distortStarted) {
                viewModel.beginDistortDrag(downX, downY, canvasW, canvasH)
                distortStarted = true
            }
            viewModel.continueDistortDrag(
                change.position.x,
                change.position.y,
                canvasW,
                canvasH
            )
        }
        change.consume()
    }
}

private fun applyPanZoom(
    viewModel: EditorViewModel,
    event: PointerEvent,
    canvasW: Float,
    canvasH: Float
) {
    val zoom = event.calculateZoom()
    val pan = event.calculatePan()
    val centroid = event.calculateCentroid(useCurrent = true)
    if (zoom != 1f || pan != Offset.Zero) {
        viewModel.panZoomBy(zoom, pan.x, pan.y, centroid.x, centroid.y, canvasW, canvasH)
    }
}
