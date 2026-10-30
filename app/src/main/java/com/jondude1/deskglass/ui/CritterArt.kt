package com.jondude1.deskglass.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * All critter pixel art, built once from the text grids in CritterGrids with the same outline-and-shade polish the
 * rest of the village uses. Index 0 is the straw training dummy, 1..62 are the species.
 */
class CritterArt {
    private val common = mapOf(
        'e' to 0xFF1E1A26.toInt(), 'w' to 0xFFFFFFFF.toInt(), 'k' to 0xFF2B2430.toInt(),
        'p' to 0xFFFF9FB2.toInt(), 'y' to 0xFFFFE066.toInt(),
    )

    private fun build(rows: List<String>, pal: Map<Char, Int>): ImageBitmap {
        val h = rows.size
        var w = 1
        for (r in rows) if (r.length > w) w = r.length
        val src = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) src[y * w + x] = pal[rows[y].getOrElse(x) { '.' }] ?: 0
        val big = PixelFx.polish(src, w, h, true, true)
        val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
        bmp.setPixels(big, 0, w * 2, 0, 0, w * 2, h * 2)
        return bmp.asImageBitmap()
    }

    /** The second idle frame: the body sinks a row toward the legs and the accent colours shimmer. */
    private fun frameB(rows: List<String>, legs: Int): List<String> {
        val n = rows.size
        val body = n - legs
        val w = rows.maxOf { it.length }
        val blank = ".".repeat(w)
        val out = ArrayList<String>()
        out.add(blank)
        if (body <= 1) {
            out.addAll(rows.subList(0, n - 1))
        } else {
            out.addAll(rows.subList(0, body - 1))
            out.addAll(rows.subList(body, n))
        }
        return out.map { r -> r.map { if (it == 'a') 'A' else if (it == 'A') 'a' else it }.joinToString("") }
    }

    private fun whiteOf(sp: ImageBitmap): ImageBitmap {
        val w = sp.width
        val h = sp.height
        val px = IntArray(w * h)
        sp.asAndroidBitmap().getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) px[i] = if ((px[i] ushr 24) > 40) 0xFFFFFFFF.toInt() else 0
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp.asImageBitmap()
    }

    /** frames[id][0..1]; id 0 is the training dummy. */
    val frames: Array<Array<ImageBitmap>> = Array(Dex_COUNT + 1) { id ->
        if (id == 0) {
            val pal = common + mapOf(
                's' to 0xFFD8B455.toInt(), 't' to 0xFFE8D2A0.toInt(), 'w' to 0xFF8A5A2E.toInt(), 'x' to 0xFFC0392B.toInt(),
            )
            val a = build(CritterGrids.dummy, pal)
            arrayOf(a, a)
        } else {
            val d = CritterGrids.defs.getValue(id)
            val pal = common + mapOf('b' to d.pal[0], 'B' to d.pal[1], 'l' to d.pal[2], 'a' to d.pal[3], 'A' to d.pal[4])
            arrayOf(build(d.rows, pal), build(frameB(d.rows, d.legs), pal))
        }
    }

    val whites: Array<ImageBitmap> = Array(Dex_COUNT + 1) { whiteOf(frames[it][0]) }

    fun frame(id: Int, n: Int): ImageBitmap = frames[id.coerceIn(0, Dex_COUNT)][n and 1]
    fun white(id: Int): ImageBitmap = whites[id.coerceIn(0, Dex_COUNT)]

    companion object {
        const val Dex_COUNT = 62
    }
}
