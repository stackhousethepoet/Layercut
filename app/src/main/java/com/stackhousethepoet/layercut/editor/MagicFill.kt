package com.stackhousethepoet.layercut.editor

import android.graphics.Bitmap
import android.graphics.Color
import java.util.ArrayDeque

/**
 * Contiguous (4-connected) magic-wand erase: pixels similar in RGB to the seed
 * become transparent. Not ML subject cutout — finish edges with Erase/Restore.
 */
object MagicFill {

    /**
     * @param tolerance max Euclidean RGB distance (0–255 scale per channel combined).
     * @return true if any pixels were cleared.
     */
    fun eraseContiguous(bitmap: Bitmap, startX: Int, startY: Int, tolerance: Int): Boolean {
        if (bitmap.isRecycled || !bitmap.isMutable) return false
        val w = bitmap.width
        val h = bitmap.height
        if (startX !in 0 until w || startY !in 0 until h) return false

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val seedIdx = startY * w + startX
        val seed = pixels[seedIdx]
        if (Color.alpha(seed) == 0) return false

        val seedR = Color.red(seed)
        val seedG = Color.green(seed)
        val seedB = Color.blue(seed)
        val tol = tolerance.coerceIn(0, 255)
        val tolSq = tol * tol

        fun matches(c: Int): Boolean {
            if (Color.alpha(c) == 0) return false
            val dr = Color.red(c) - seedR
            val dg = Color.green(c) - seedG
            val db = Color.blue(c) - seedB
            return dr * dr + dg * dg + db * db <= tolSq
        }

        if (!matches(seed)) return false

        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()
        pixels[seedIdx] = Color.TRANSPARENT
        visited[seedIdx] = true
        queue.add(seedIdx)
        var count = 1

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val x = idx % w
            val y = idx / w

            fun tryVisit(nx: Int, ny: Int) {
                if (nx !in 0 until w || ny !in 0 until h) return
                val n = ny * w + nx
                if (visited[n]) return
                visited[n] = true
                if (!matches(pixels[n])) return
                pixels[n] = Color.TRANSPARENT
                count++
                queue.add(n)
            }

            tryVisit(x - 1, y)
            tryVisit(x + 1, y)
            tryVisit(x, y - 1)
            tryVisit(x, y + 1)
        }

        if (count == 0) return false
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return true
    }
}
