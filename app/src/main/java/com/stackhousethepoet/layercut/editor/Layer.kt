package com.stackhousethepoet.layercut.editor

import android.graphics.Bitmap
import java.util.UUID

data class LayerTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f
)

/**
 * @param bitmap Working (mutable) pixels the user paints/erases on.
 * @param originalBitmap Immutable copy taken at layer creation / load; used by Restore.
 */
data class EditorLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val bitmap: Bitmap,
    val originalBitmap: Bitmap,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val transform: LayerTransform = LayerTransform()
)

enum class ToolMode {
    PAN,
    PAINT,
    ERASER,
    RESTORE,
    TRANSFORM
}

data class BrushSettings(
    val size: Float = 24f,
    val opacity: Float = 1f,
    val color: Int = 0xFFFF0000.toInt(),
    val soft: Boolean = true
)

data class CanvasViewport(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)
