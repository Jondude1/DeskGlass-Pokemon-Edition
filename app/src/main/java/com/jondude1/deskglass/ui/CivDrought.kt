package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.T
import kotlin.math.sin

/** The Long Dry: while a summer drought lasts the grass pales and cracks, the sky shimmers with heat and dust drifts
 *  over the land. Wet farm soil darkens with droplets right after the villagers carry water. Pure drawing. */

private fun dh(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

internal fun DrawScope.droughtGround(sim: CivSim, g: Geo, vx0: Int, vx1: Int, vy0: Int, vy1: Int) {
    val lv = sim.droughtLevel()
    if (lv <= 0.01f) return
    val ts = g.ts
    val ps = g.ps
    for (y in vy0 until vy1) for (x in vx0 until vx1) {
        if (x < 0 || y < 0 || x >= GW) continue
        val i = y * GW + x
        if (i >= sim.tiles.size || sim.tiles[i] != T.GRASS) continue
        val h = dh(x, y)
        val rank = (h % 100) / 100f
        if (rank > lv * 0.8f) continue
        val px = g.ox + x * ts
        val py = g.oy + y * ts
        // sun-bleached patch
        drawRect(Color(0xFFD8C27A).copy(alpha = 0.22f * lv), Offset(px, py), Size(ts + 1f, ts + 1f))
        // a crack in the baked earth
        if (h % 3 == 0) {
            val cx = px + ((h shr 3) % 9 + 3) * ps
            val cy = py + ((h shr 7) % 8 + 4) * ps
            val c = Color(0xFF6B5532).copy(alpha = 0.55f * lv)
            drawRect(c, Offset(cx, cy), Size(ps * 4.5f, ps * 0.7f))
            drawRect(c, Offset(cx + ps * 3.8f, cy), Size(ps * 0.7f, ps * 2.4f))
            if (h % 2 == 0) drawRect(c, Offset(cx + ps * 1.5f, cy - ps * 2.2f), Size(ps * 0.7f, ps * 2.2f))
        }
    }
    // wet soil and water droplets on the farms right after a bucket run
    if (sim.farmsWatered()) {
        val wet = ((sim.droughtWaterUntil - sim.time) / 30.0).toFloat().coerceIn(0f, 1f)
        for (b in sim.buildings) {
            if (b.type != "farm") continue
            val fx = g.ox + (b.x - 0.4f) * ts
            val fy = g.oy + (b.y - 0.4f) * ts
            drawRect(Color(0xFF3B2A18).copy(alpha = 0.22f * wet), Offset(fx, fy), Size(ts * 2.2f, ts * 1.9f))
            for (k in 0 until 6) {
                val p = (g.t * 0.9f + k * 0.17f) % 1f
                val dx = fx + ts * (0.2f + (k * 37 % 19) / 10f)
                drawRect(Color(0xFF9AD6FF).copy(alpha = (1f - p) * wet), Offset(dx, fy + ts * 0.2f + p * ts * 1.2f), Size(ps * 0.9f, ps * 1.6f))
            }
        }
    }
}

/** Heat haze, a hot amber wash and dust drifting across the whole view. */
internal fun DrawScope.droughtSky(sim: CivSim, ts: Float, ps: Float, t: Float) {
    val lv = sim.droughtLevel()
    if (lv <= 0.01f) return
    drawRect(Color(0xFFFFB347).copy(alpha = 0.10f * lv))
    // shimmering heat lines
    for (k in 0 until 9) {
        val yy = size.height * (0.08f + k * 0.105f) + sin(t * 1.3f + k * 1.7f) * ps * 2f
        val xx = ((t * 0.02f * (1 + k % 3) + k * 0.23f) % 1.2f - 0.1f) * size.width
        drawRect(Color.White.copy(alpha = 0.07f * lv), Offset(xx, yy), Size(ts * (6f + k % 4 * 2f), ps * 0.9f))
    }
    // dust motes blowing in the dry wind
    for (k in 0 until 22) {
        val p = (t * 0.035f * (1f + (k % 4) * 0.3f) + k * 0.047f) % 1f
        val dx = p * (size.width + ts * 4f) - ts * 2f
        val dy = size.height * (((k * 37) % 100) / 100f) + sin(t * 1.6f + k) * ps * 3f
        drawRect(Color(0xFFE6CC8A).copy(alpha = 0.34f * lv), Offset(dx, dy), Size(ps * 1.2f, ps * 1.2f))
    }
}
