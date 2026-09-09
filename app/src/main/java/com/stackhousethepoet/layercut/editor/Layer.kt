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
    /** Sample visible composite color into brushSettings.color (auto-returns to PAINT). */
    EYEDROPPER,
    ERASER,
    RESTORE,
    MAGIC,
    TRANSFORM
}

data class BrushSettings(
    val size: Float = 24f,
    /** 0..1 — at ≥0.98 the engine forces full alpha + hard edge. */
    val opacity: Float = 1f,
    val color: Int = 0xFFFF0000.toInt(),
    /** Soft = BlurMaskFilter when opacity is below max; Hard never blurs. Default Hard for punch-through. */
    val soft: Boolean = false,
    /** RGB distance tolerance for Magic contiguous fill (8–80 typical). */
    val magicTolerance: Int = 32
)

data class CanvasViewport(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)
