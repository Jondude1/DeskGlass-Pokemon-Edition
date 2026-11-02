package com.jondude1.deskglass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.GH
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.T

private fun mapColor(t: Int, snow: Float): Int {
    val base = when (t) {
        T.GRASS -> 0xFF4E8F45.toInt()
        T.FOREST -> 0xFF2B5E2F.toInt()
        T.ROCK -> 0xFF7D7F86.toInt()
        T.WATER -> 0xFF2F6FB5.toInt()
        T.BUSH -> 0xFF5FA24A.toInt()
        T.ORE -> 0xFFB5873F.toInt()
        else -> 0xFF4E8F45.toInt()
    }
    if (snow <= 0.05f || t == T.ROCK || t == T.ORE) return base
    val k = (snow * 0.7f).coerceIn(0f, 0.7f)
    val r = (((base shr 16) and 255) * (1f - k) + 238 * k).toInt()
    val g = (((base shr 8) and 255) * (1f - k) + 244 * k).toInt()
    val b = ((base and 255) * (1f - k) + 250 * k).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * A small overview map of the whole land: terrain, buildings, villagers, foes and fires, with a frame showing what the
 * main view sees. Tap or drag to move the view there; tap a villager to follow them. [tick] forces a refresh.
 */
@Composable
fun VillageMinimap(sim: CivSim, cam: CivCamera, tick: Long, modifier: Modifier = Modifier) {
    // the terrain bitmap is rebuilt every few seconds (snow and fires change it a little)
    val terrain = remember(tick / 6L) {
        val bmp = android.graphics.Bitmap.createBitmap(GW, GH, android.graphics.Bitmap.Config.ARGB_8888)
        val snow = sim.snowCover()
        val px = IntArray(GW * GH)
        for (i in px.indices) px[i] = mapColor(sim.tiles[i], snow)
        for (b in sim.blazes) if (b.i in px.indices) px[b.i] = 0xFFFF7A1A.toInt()
        bmp.setPixels(px, 0, GW, 0, 0, GW, GH)
        bmp.asImageBitmap()
    }
    fun jump(p: Offset, w: Float, h: Float, pickVillager: Boolean) {
        if (w <= 0f || h <= 0f) return
        val wx = (p.x / w * GW).coerceIn(0f, GW.toFloat())
        val wy = (p.y / h * GH).coerceIn(0f, GH.toFloat())
        if (pickVillager) {
            val near = sim.villagers.minByOrNull { (it.x - wx) * (it.x - wx) + (it.y - wy) * (it.y - wy) }
            if (near != null && (near.x - wx) * (near.x - wx) + (near.y - wy) * (near.y - wy) < 6f) {
                cam.followId = near.id
                return
            }
        }
        cam.followId = -1
        cam.cx = wx
        cam.cy = wy
    }
    Box(
        modifier
            .size(width = 132.dp, height = 74.dp)
            .background(Color(0xCC101828), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x66F4F6FF), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { p -> jump(p, size.width.toFloat(), size.height.toFloat(), true) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> jump(change.position, size.width.toFloat(), size.height.toFloat(), false) }
            },
    ) {
        Canvas(Modifier.size(width = 132.dp, height = 74.dp)) {
            val sx = size.width / GW
            val sy = size.height / GH
            drawImage(terrain, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.None)
            for (b in sim.buildings) {
                drawRect(Color(0xFFE8C98A), Offset(b.x * sx, b.y * sy), Size(sx * 1.6f, sy * 1.6f))
            }
            for (p in sim.predators) {
                drawCircle(Color(0xFFFF4040), radius = 2.6f, center = Offset(p.x * sx, p.y * sy))
            }
            for (v in sim.villagers) {
                val me = v.id == cam.followId
                drawCircle(if (me) Color(0xFFFFD84A) else Color(0xFFFFFFFF), radius = if (me) 3.4f else 1.7f, center = Offset(v.x * sx, v.y * sy))
            }
            if (cam.ts > 0f && cam.w > 1f) {
                val vw = cam.w / cam.ts
                val vh = cam.h / cam.ts
                drawRect(
                    Color(0xFFF4F6FF),
                    Offset((cam.cx - vw / 2f) * sx, (cam.cy - vh / 2f) * sy),
                    Size(vw * sx, vh * sy),
                    style = Stroke(width = 1.5f),
                )
            }
        }
    }
}
