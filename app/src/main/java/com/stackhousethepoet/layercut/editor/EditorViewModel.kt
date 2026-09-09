package com.stackhousethepoet.layercut.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlin.math.roundToInt

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

    var magicBusy by mutableStateOf(false)
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
        soft: Boolean? = null,
        magicTolerance: Int? = null
    ) {
        var newSoft = soft ?: brushSettings.soft
        // Sliding opacity to ~100% auto-selects Hard for dramatic punch-through.
        if (opacity != null && opacity >= BrushEngine.FULL_OPACITY_THRESHOLD && soft == null) {
            newSoft = false
        }
        brushSettings = brushSettings.copy(
            size = size ?: brushSettings.size,
            opacity = opacity ?: brushSettings.opacity,
            color = color ?: brushSettings.color,
            soft = newSoft,
            magicTolerance = magicTolerance ?: brushSettings.magicTolerance
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
        recycleLayerBitmaps(removed)
        if (activeLayerId == id) {
            activeLayerId = layers.getOrNull(min(idx, layers.lastIndex))?.id
        }
        bump()
    }

    fun updateViewport(scale: Float, offsetX: Float, offsetY: Float) {
        viewport = CanvasViewport(
            scale = scale.coerceIn(MIN_ZOOM, MAX_ZOOM),
            offsetX = offsetX,
            offsetY = offsetY
        )
    }

    fun panZoomBy(
        zoomChange: Float,
        panX: Float,
        panY: Float,
        focusX: Float,
        focusY: Float,
        canvasW: Float,
        canvasH: Float
    ) {
        val old = viewport
        val newScale = (old.scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        // Keep the content point under the focus stable, accounting for the
        // centered content origin used by EditorCanvas / CoordMath.
        val originX = canvasW / 2f + old.offsetX - (contentWidth * old.scale) / 2f
        val originY = canvasH / 2f + old.offsetY - (contentHeight * old.scale) / 2f
        val contentX = if (old.scale == 0f) 0f else (focusX - originX) / old.scale
        val contentY = if (old.scale == 0f) 0f else (focusY - originY) / old.scale
        val rawOx = focusX - canvasW / 2f + (contentWidth * newScale) / 2f - contentX * newScale + panX
        val rawOy = focusY - canvasH / 2f + (contentHeight * newScale) / 2f - contentY * newScale + panY
        viewport = clampViewport(newScale, rawOx, rawOy, canvasW, canvasH)
    }

    /**
     * Keep the content rectangle reasonably on-screen:
     * - when scaled content fits the canvas on an axis, force centered offset on that axis
     * - when zoomed in, clamp so at least ~20% of the content stays intersecting the viewport
     */
    private fun clampViewport(
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        canvasW: Float,
        canvasH: Float
    ): CanvasViewport {
        if (contentWidth <= 0f || contentHeight <= 0f || canvasW <= 0f || canvasH <= 0f) {
            return CanvasViewport(scale, offsetX, offsetY)
        }
        val minVisible = 0.2f
        val scaledW = contentWidth * scale
        val scaledH = contentHeight * scale
        val ox = clampAxisOffset(offsetX, scaledW, canvasW, minVisible)
        val oy = clampAxisOffset(offsetY, scaledH, canvasH, minVisible)
        return CanvasViewport(scale, ox, oy)
    }

    private fun clampAxisOffset(
        offset: Float,
        scaledContent: Float,
        canvasSize: Float,
        minVisible: Float
    ): Float {
        // Content edge in screen space: L = canvas/2 + offset - scaled/2
        if (scaledContent <= canvasSize) {
            // Content fits — keep centered (offset ~0) so zoom-out never drifts away.
            return 0f
        }
        val lMin = -(1f - minVisible) * scaledContent
        val lMax = canvasSize - minVisible * scaledContent
        val oxMin = lMin - canvasSize / 2f + scaledContent / 2f
        val oxMax = lMax - canvasSize / 2f + scaledContent / 2f
        val lo = min(oxMin, oxMax)
        val hi = max(oxMin, oxMax)
        return offset.coerceIn(lo, hi)
    }

    fun transformActiveLayer(panX: Float, panY: Float, zoom: Float, rotation: Float) {
        val layer = activeLayer ?: return
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
            val layer = createLayer(name = "Background", working = bmp)
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
            val fitted = fitIntoCanvas(src, contentWidth.toInt().coerceAtLeast(1), contentHeight.toInt().coerceAtLeast(1))
            if (fitted !== src && !src.isRecycled) src.recycle()
            val layer = createLayer(name = "Layer ${layers.size + 1}", working = fitted)
            layers.add(layer)
            activeLayerId = layer.id
            toolMode = ToolMode.TRANSFORM
            bump()
            statusMessage = "Layer added"
        }
    }

    fun beginStroke(canvasX: Float, canvasY: Float, canvasW: Float, canvasH: Float, pressure: Float) {
        val layer = activeLayer ?: return
        if (toolMode != ToolMode.PAINT && toolMode != ToolMode.ERASER && toolMode != ToolMode.RESTORE) return
        ensureMutable(layer)
        val working = activeLayer ?: return
        undoStack.pushBeforeChange(working)
        refreshUndoFlags()
        strokePressure = if (pressure in 0.01f..1f) pressure else 1f
        val (lx, ly) = CoordMath.screenToLayer(
            canvasX, canvasY, viewport, working, canvasW, canvasH, contentWidth, contentHeight
        )
        brushEngine.beginStroke(lx, ly)
        applyBrushStamp(working, lx, ly)
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
        applyBrushStamp(layer, lx, ly)
        bump()
    }

    fun endStroke() {
        if (!strokeActive) return
        strokeActive = false
        brushEngine.endStroke()
        refreshUndoFlags()
        bump()
    }

    /**
     * Magic erase: flood-fill contiguous similar pixels from the tap point to transparent.
     * Runs on a background thread; undo snapshot is taken before mutate.
     */
    fun magicEraseAt(canvasX: Float, canvasY: Float, canvasW: Float, canvasH: Float) {
        if (toolMode != ToolMode.MAGIC || magicBusy) return
        val layer = activeLayer ?: return
        ensureMutable(layer)
        val working = activeLayer ?: return
        val (lx, ly) = CoordMath.screenToLayer(
            canvasX, canvasY, viewport, working, canvasW, canvasH, contentWidth, contentHeight
        )
        val px = lx.roundToInt()
        val py = ly.roundToInt()
        if (px !in 0 until working.bitmap.width || py !in 0 until working.bitmap.height) {
            statusMessage = "Tap on the active layer"
            return
        }

        undoStack.pushBeforeChange(working)
        refreshUndoFlags()
        magicBusy = true
        val bitmap = working.bitmap
        val tolerance = brushSettings.magicTolerance
        viewModelScope.launch {
            val erased = withContext(Dispatchers.Default) {
                MagicFill.eraseContiguous(bitmap, px, py, tolerance)
            }
            magicBusy = false
            if (erased) {
                bump()
                statusMessage = "Magic erase applied — refine edges with Erase/Restore"
            } else {
                statusMessage = "Nothing to erase here"
            }
        }
    }

    /**
     * Eyedropper: sample the topmost visible opaque pixel under the finger
     * (layer stack top → bottom at that content point) and set brush color
     * to opaque RGB matching what the user sees. Auto-switches back to Paint.
     */
    fun sampleColorAt(canvasX: Float, canvasY: Float, canvasW: Float, canvasH: Float) {
        if (toolMode != ToolMode.EYEDROPPER) return
        if (layers.isEmpty() || contentWidth <= 0f || contentHeight <= 0f) return

        // Walk top → bottom so the pixel the user sees wins.
        for (i in layers.lastIndex downTo 0) {
            val layer = layers[i]
            if (!layer.visible || layer.opacity <= 0f || layer.bitmap.isRecycled) continue
            val (lx, ly) = CoordMath.screenToLayer(
                canvasX, canvasY, viewport, layer, canvasW, canvasH, contentWidth, contentHeight
            )
            val px = lx.roundToInt()
            val py = ly.roundToInt()
            if (px !in 0 until layer.bitmap.width || py !in 0 until layer.bitmap.height) continue
            val pixel = layer.bitmap.getPixel(px, py)
            val pixelAlpha = Color.alpha(pixel)
            // Effective visibility: pixel alpha × layer opacity.
            val effectiveAlpha = (pixelAlpha * layer.opacity.coerceIn(0f, 1f)).roundToInt()
            if (effectiveAlpha < 16) continue // treat near-transparent as see-through
            val opaque = Color.argb(255, Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            updateBrush(color = opaque)
            toolMode = ToolMode.PAINT
            statusMessage = "Color sampled"
            return
        }
        statusMessage = "No opaque color here"
        toolMode = ToolMode.PAINT
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
        layers.forEach { recycleLayerBitmaps(it) }
        layers.clear()
        undoStack.clear()
        refreshUndoFlags()
        activeLayerId = null
    }

    private fun createLayer(name: String, working: Bitmap): EditorLayer {
        val original = working.copy(Bitmap.Config.ARGB_8888, false)
            ?: working.copy(Bitmap.Config.ARGB_8888, true)
            ?: working
        return EditorLayer(name = name, bitmap = working, originalBitmap = original)
    }

    private fun recycleLayerBitmaps(layer: EditorLayer) {
        if (!layer.bitmap.isRecycled) layer.bitmap.recycle()
        if (layer.originalBitmap !== layer.bitmap && !layer.originalBitmap.isRecycled) {
            layer.originalBitmap.recycle()
        }
    }

    private fun applyBrushStamp(layer: EditorLayer, lx: Float, ly: Float) {
        when (toolMode) {
            ToolMode.RESTORE -> brushEngine.stampRestore(
                layer.bitmap,
                layer.originalBitmap,
                lx,
                ly,
                brushSettings,
                pressureScale = strokePressure
            )
            ToolMode.ERASER -> brushEngine.stamp(
                layer.bitmap,
                lx,
                ly,
                brushSettings,
                erase = true,
                pressureScale = strokePressure
            )
            ToolMode.PAINT -> brushEngine.stamp(
                layer.bitmap,
                lx,
                ly,
                brushSettings,
                erase = false,
                pressureScale = strokePressure
            )
            else -> Unit
        }
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

    companion object {
        const val MIN_ZOOM = 0.05f
        const val MAX_ZOOM = 100f
    }
}
