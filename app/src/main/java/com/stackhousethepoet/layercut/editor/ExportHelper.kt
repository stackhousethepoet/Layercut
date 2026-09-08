package com.stackhousethepoet.layercut.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

object ExportHelper {

    /**
     * Flatten visible layers (bottom → top) into a single ARGB bitmap
     * sized to the base (bottom) layer.
     */
    fun flatten(layers: List<EditorLayer>): Bitmap? {
        val base = layers.firstOrNull { !it.bitmap.isRecycled } ?: return null
        val width = base.bitmap.width
        val height = base.bitmap.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (layer in layers) {
            if (!layer.visible || layer.bitmap.isRecycled) continue
            paint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
            val matrix = Matrix().apply {
                // Draw around layer center with transform, then place relative to canvas
                val cx = layer.bitmap.width / 2f
                val cy = layer.bitmap.height / 2f
                postTranslate(-cx, -cy)
                postScale(layer.transform.scale, layer.transform.scale)
                postRotate(layer.transform.rotationDeg)
                postTranslate(cx + layer.transform.offsetX, cy + layer.transform.offsetY)
            }
            canvas.drawBitmap(layer.bitmap, matrix, paint)
        }
        return out
    }

    fun savePngToGallery(context: Context, bitmap: Bitmap, displayName: String = "LayerCut_${System.currentTimeMillis()}.png"): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LayerCut")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { stream: OutputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IllegalStateException("compress failed")
                }
            } ?: throw IllegalStateException("no stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
