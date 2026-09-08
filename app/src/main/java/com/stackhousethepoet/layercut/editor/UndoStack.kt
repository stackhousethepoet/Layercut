package com.stackhousethepoet.layercut.editor

import android.graphics.Bitmap

/** Snapshot-based undo/redo for a single layer change at a time. */
class UndoStack(private val maxDepth: Int = 30) {

    data class Snapshot(
        val layerId: String,
        val bitmapCopy: Bitmap,
        val transform: LayerTransform,
        val opacity: Float,
        val visible: Boolean,
        val name: String
    )

    private val undo = ArrayDeque<Snapshot>()
    private val redo = ArrayDeque<Snapshot>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun capture(layer: EditorLayer): Snapshot? {
        if (layer.bitmap.isRecycled) return null
        val copy = layer.bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        return Snapshot(
            layerId = layer.id,
            bitmapCopy = copy,
            transform = layer.transform,
            opacity = layer.opacity,
            visible = layer.visible,
            name = layer.name
        )
    }

    fun pushBeforeChange(layer: EditorLayer) {
        val snap = capture(layer) ?: return
        undo.addLast(snap)
        while (undo.size > maxDepth) {
            undo.removeFirst().bitmapCopy.recycle()
        }
        clearRedo()
    }

    fun popUndo(): Snapshot? = undo.removeLastOrNull()

    fun popRedo(): Snapshot? = redo.removeLastOrNull()

    fun pushRedoSnapshot(snap: Snapshot) {
        redo.addLast(snap)
    }

    fun pushUndoSnapshot(snap: Snapshot) {
        undo.addLast(snap)
        while (undo.size > maxDepth) {
            undo.removeFirst().bitmapCopy.recycle()
        }
    }

    fun clear() {
        undo.forEach { it.bitmapCopy.recycle() }
        redo.forEach { it.bitmapCopy.recycle() }
        undo.clear()
        redo.clear()
    }

    private fun clearRedo() {
        redo.forEach { it.bitmapCopy.recycle() }
        redo.clear()
    }
}
