package com.stackhousethepoet.layercut.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    val layers = mutableStateListOf<EditorLayer>()

    var activeLayerId by mutableStateOf<String?>(null)
        private set

    var toolMode by mutableStateOf(ToolMode.PAN)
        private set

    var brushSettings by mutableStateOf(BrushSettings())
        private set

    var viewport by mutableStateOf(CanvasViewport())
        private set

    var contentWidth by mutableFloatStateOf(0f)
        private set
    var contentHeight by mutableFloatStateOf(0f)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    var statusMessage by mutableStateOf<String?>(null)

    var revision by mutableIntStateOf(0)
        private set

    private val undoStack = UndoStack(30)
    private val brushEngine = BrushEngine()
    private var strokeActive = false
    private var strokePressure = 1f

    val activeLayer: EditorLayer?
        get() = layers.find { it.id == activeLayerId }

    fun setTool(mode: ToolMode) {
        toolMode = mode
    }

    fun updateBrush(
        size: Float? = null,
        opacity: Float? = null,
        color: Int? = null,
        soft: Boolean? = null
    ) {
        brushSettings = brushSettings.copy(
            size = size ?: brushSettings.size,
            opacity = opacity ?: brushSettings.opacity,
            color = color ?: brushSettings.color,
            soft = soft ?: brushSettings.soft
        )
    }

    fun selectLayer(id: String) {
        activeLayerId = id
    }

    fun setLayerVisibility(id: String, visible: Boolean) {
        updateLayer(id) { it.copy(visible = visible) }
        bump()
    }

    fun setLayerOpacity(id: String, opacity: Float) {
        updateLayer(id) { it.copy(opacity = opacity.coerceIn(0f, 1f)) }
        bump()
    }

    fun reorderLayers(from: Int, to: Int) {
        if (from !in layers.indices || to !in layers.indices) return
        val item = layers.removeAt(from)
        layers.add(to, item)
        bump()
    }

    fun moveLayerUp(id: String) {
        val i = layers.indexOfFirst { it.id == id }
        if (i < 0 || i >= layers.lastIndex) return
        reorderLayers(i, i + 1)
    }

    fun moveLayerDown(id: String) {
        val i = layers.indexOfFirst { it.id == id }
        if (i <= 0) return
        reorderLayers(i, i - 1)
    }

    fun deleteLayer(id: String) {
        if (layers.size <= 1) {
            statusMessage = "Keep at least one layer"
            return
        }
        val idx = layers.indexOfFirst { it.id == id }
        if (idx < 0) return
        val removed = layers.removeAt(idx)
        if (!removed.bitmap.isRecycled) removed.bitmap.recycle()
        if (activeLayerId == id) {
            activeLayerId = layers.getOrNull(min(idx, layers.lastIndex))?.id
        }
        bump()
    }

    fun updateViewport(scale: Float, offsetX: Float, offsetY: Float) {
        viewport = CanvasViewport(
            scale = scale.coerceIn(0.1f, 8f),
            offsetX = offsetX,
            offsetY = offsetY
        )
    }

    fun panZoomBy(zoomChange: Float, panX: Float, panY: Float, focusX: Float, focusY: Float) {
        val old = viewport
        val newScale = (old.scale * zoomChange).coerceIn(0.1f, 8f)
        // Keep focus point stable under zoom
        val worldX = (focusX - old.offsetX) / old.scale
        val worldY = (focusY - old.offsetY) / old.scale
        val newOx = focusX - worldX * newScale + panX
        val newOy = focusY - worldY * newScale + panY
        viewport = CanvasViewport(newScale, newOx, newOy)
    }

    fun transformActiveLayer(panX: Float, panY: Float, zoom: Float, rotation: Float) {
        val layer = activeLayer ?: return
        if (!strokeActive) {
            // Push undo once at start of transform gesture — handled by beginTransform/end
        }
        val t = layer.transform
        val newScale = (t.scale * zoom).coerceIn(0.05f, 10f)
        updateLayer(layer.id) {
            it.copy(
                transform = t.copy(
                    offsetX = t.offsetX + panX / viewport.scale,
                    offsetY = t.offsetY + panY / viewport.scale,
                    scale = newScale,
                    rotationDeg = t.rotationDeg + rotation
                )
            )
        }
        bump()
    }

    private var transformUndoPushed = false

    fun beginTransformGesture() {
        val layer = activeLayer ?: return
        if (!transformUndoPushed) {
            undoStack.pushBeforeChange(layer)
            refreshUndoFlags()
            transformUndoPushed = true
        }
    }

    fun endTransformGesture() {
        transformUndoPushed = false
    }

    fun loadBaseImage(uri: Uri) {
        viewModelScope.launch {
            val bmp = decodeMutable(uri) ?: run {
                statusMessage = "Could not load image"
                return@launch
            }
            clearProject()
            contentWidth = bmp.width.toFloat()
            contentHeight = bmp.height.toFloat()
            val layer = EditorLayer(name = "Background", bitmap = bmp)
            layers.add(layer)
            activeLayerId = layer.id
            viewport = CanvasViewport(1f, 0f, 0f)
            toolMode = ToolMode.PAN
            bump()
            statusMessage = "Photo loaded"
        }
    }

    fun addLayerFromUri(uri: Uri) {
        viewModelScope.launch {
            val src = decodeMutable(uri) ?: run {
                statusMessage = "Could not add layer"
                return@launch
            }
            // Fit into content bounds while preserving aspect
            val fitted = fitIntoCanvas(src, contentWidth.toInt().coerceAtLeast(1), contentHeight.toInt().coerceAtLeast(1))
            if (fitted !== src && !src.isRecycled) src.recycle()
            val layer = EditorLayer(
                name = "Layer ${layers.size + 1}",
                bitmap = fitted
            )
            layers.add(layer)
            activeLayerId = layer.id
            toolMode = ToolMode.TRANSFORM
            bump()
            statusMessage = "Layer added"
        }
    }

    fun beginStroke(canvasX: Float, canvasY: Float, canvasW: Float, canvasH: Float, pressure: Float) {
        val layer = activeLayer ?: return
        if (toolMode != ToolMode.PAINT && toolMode != ToolMode.ERASER) return
        ensureMutable(layer)
        undoStack.pushBeforeChange(layer)
        refreshUndoFlags()
        strokePressure = if (pressure in 0.01f..1f) pressure else 1f
        val (lx, ly) = CoordMath.screenToLayer(
            canvasX, canvasY, viewport, layer, canvasW, canvasH, contentWidth, contentHeight
        )
        brushEngine.beginStroke(lx, ly)
        brushEngine.stamp(
            layer.bitmap, lx, ly, brushSettings,
            erase = toolMode == ToolMode.ERASER,
            pressureScale = strokePressure
        )
        strokeActive = true
        bump()
    }

    fun continueStroke(canvasX: Float, canvasY: Float, canvasW: Float, canvasH: Float, pressure: Float) {
        if (!strokeActive) return
        val layer = activeLayer ?: return
        if (pressure in 0.01f..1f) strokePressure = pressure
        val (lx, ly) = CoordMath.screenToLayer(
            canvasX, canvasY, viewport, layer, canvasW, canvasH, contentWidth, contentHeight
        )
        brushEngine.appendStroke(lx, ly)
        // Incremental stamp for live preview feel
        brushEngine.stamp(
            layer.bitmap, lx, ly, brushSettings,
            erase = toolMode == ToolMode.ERASER,
            pressureScale = strokePressure
        )
        bump()
    }

    fun endStroke() {
        if (!strokeActive) return
        strokeActive = false
        brushEngine.endStroke()
        refreshUndoFlags()
        bump()
    }

    fun undo() {
        val snap = undoStack.popUndo() ?: return
        val targetIdx = layers.indexOfFirst { it.id == snap.layerId }
        if (targetIdx < 0) {
            snap.bitmapCopy.recycle()
            refreshUndoFlags()
            return
        }
        val target = layers[targetIdx]
        undoStack.capture(target)?.let { undoStack.pushRedoSnapshot(it) }
        if (!target.bitmap.isRecycled) target.bitmap.recycle()
        layers[targetIdx] = target.copy(
            bitmap = snap.bitmapCopy,
            transform = snap.transform,
            opacity = snap.opacity,
            visible = snap.visible,
            name = snap.name
        )
        activeLayerId = snap.layerId
        refreshUndoFlags()
        bump()
    }

    fun redo() {
        val snap = undoStack.popRedo() ?: return
        val targetIdx = layers.indexOfFirst { it.id == snap.layerId }
        if (targetIdx < 0) {
            snap.bitmapCopy.recycle()
            refreshUndoFlags()
            return
        }
        val target = layers[targetIdx]
        undoStack.capture(target)?.let { undoStack.pushUndoSnapshot(it) }
        if (!target.bitmap.isRecycled) target.bitmap.recycle()
        layers[targetIdx] = target.copy(
            bitmap = snap.bitmapCopy,
            transform = snap.transform,
            opacity = snap.opacity,
            visible = snap.visible,
            name = snap.name
        )
        activeLayerId = snap.layerId
        refreshUndoFlags()
        bump()
    }

    fun exportPng(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                val flat = ExportHelper.flatten(layers.toList()) ?: return@withContext false
                try {
                    val uri = ExportHelper.savePngToGallery(getApplication(), flat)
                    flat.recycle()
                    uri != null
                } catch (_: Exception) {
                    if (!flat.isRecycled) flat.recycle()
                    false
                }
            }
            statusMessage = if (ok) "Exported to Pictures/LayerCut" else "Export failed"
            onDone(ok)
        }
    }

    fun clearStatus() {
        statusMessage = null
    }

    private fun refreshUndoFlags() {
        canUndo = undoStack.canUndo
        canRedo = undoStack.canRedo
    }

    private fun updateLayer(id: String, block: (EditorLayer) -> EditorLayer) {
        val i = layers.indexOfFirst { it.id == id }
        if (i >= 0) layers[i] = block(layers[i])
    }

    private fun ensureMutable(layer: EditorLayer) {
        if (!layer.bitmap.isMutable) {
            val copy = layer.bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return
            val old = layer.bitmap
            updateLayer(layer.id) { it.copy(bitmap = copy) }
            if (old !== copy && !old.isRecycled) old.recycle()
        }
    }

    private fun bump() {
        revision++
    }

    private fun clearProject() {
        layers.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        layers.clear()
        undoStack.clear()
        refreshUndoFlags()
        activeLayerId = null
    }

    private suspend fun decodeMutable(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val cr = getApplication<Application>().contentResolver
        cr.openInputStream(uri)?.use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        val maxSide = 4096
        var sample = 1
        val w = boundsWidth(uri)
        val h = boundsHeight(uri)
        while (w / sample > maxSide || h / sample > maxSide) sample *= 2
        cr.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
                inSampleSize = sample
            }
            BitmapFactory.decodeStream(input, null, opts)?.let { bmp ->
                if (bmp.isMutable) bmp else bmp.copy(Bitmap.Config.ARGB_8888, true)?.also {
                    if (it !== bmp) bmp.recycle()
                }
            }
        }
    }

    private fun boundsWidth(uri: Uri): Int {
        val cr = getApplication<Application>().contentResolver
        return cr.openInputStream(uri)?.use {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(it, null, o)
            o.outWidth
        } ?: 1
    }

    private fun boundsHeight(uri: Uri): Int {
        val cr = getApplication<Application>().contentResolver
        return cr.openInputStream(uri)?.use {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(it, null, o)
            o.outHeight
        } ?: 1
    }

    private fun fitIntoCanvas(src: Bitmap, canvasW: Int, canvasH: Int): Bitmap {
        val scale = min(1f, min(canvasW.toFloat() / src.width, canvasH.toFloat() / src.height))
        if (scale >= 0.999f) {
            return if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true) ?: src
        }
        val nw = max(1, (src.width * scale).toInt())
        val nh = max(1, (src.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(src, nw, nh, true)
        return if (scaled.isMutable) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true) ?: scaled
    }

    override fun onCleared() {
        clearProject()
        super.onCleared()
    }
}
