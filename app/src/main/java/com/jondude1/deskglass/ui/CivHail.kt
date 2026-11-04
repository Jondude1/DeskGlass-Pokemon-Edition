package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.T
import kotlin.math.max
import kotlin.math.min

/** Hailstorms: white pellets streak down and bounce, the ground whitens with stones that melt away afterwards,
 *  struck villagers get a little "ow" star burst, and a ticking rattle of ice dots the sky. Pure drawing; the state
 *  comes from CivSim (hail(), hailWhite(), hailPops). */

private fun hh(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

/** Hailstones lying on open ground (drawn under buildings and villagers). */
internal fun DrawScope.hailGround(sim: CivSim, g: Geo, vx0: Int, vx1: Int, vy0: Int, vy1: Int) {
    val white = sim.hailWhite()
    if (white <= 0.02f) return
    val ts = g.ts
    val ps = g.ps
    for (y in vy0 until vy1) for (x in vx0 until vx1) {
        val tile = sim.tiles[y * GW + x]
        if (tile == T.WATER) continue
        val h = hh(x, y)
        if ((h % 100) / 100f > white) continue
        val px = g.ox + x * ts
        val py = g.oy + y * ts
        drawRect(Color(0xFFF2F8FC).copy(alpha = 0.22f * white), Offset(px, py), Size(ts + 1f, ts + 1f))
        val n = 2 + (h / 7) % 3
        for (k in 0 until n) {
            val sx = px + (((h shr (k * 3)) % 13) / 13f) * ts * 0.85f
            val sy = py + (((h shr (k * 3 + 1)) % 11) / 11f) * ts * 0.85f
            drawCircle(Color.White.copy(alpha = 0.85f * white), radius = ps * 0.9f, center = Offset(sx, sy))
            drawCircle(Color(0xFFB8D4EC).copy(alpha = 0.5f * white), radius = ps * 0.45f, center = Offset(sx + ps * 0.3f, sy + ps * 0.3f))
        }
    }
}

/** Falling hail over the whole view plus the "ow" bursts over struck villagers. */
internal fun DrawScope.hailFall(sim: CivSim, g: Geo) {
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    val w = size.width
    val hgt = size.height
    if (sim.hail() && sim.time >= sim.hailStartAt) {
        val ramp = ((sim.time - sim.hailStartAt) / 4.0).coerceIn(0.0, 1.0).toFloat()
        drawRect(Color(0xFF1C2A44).copy(alpha = 0.12f * ramp))
        val n = (70 * ramp).toInt()
        for (i in 0 until n) {
            val rx = (((hh(i, 31) % 1000) / 1000f) * 1.25f - 0.1f) * w
            val speed = 26f + (hh(i, 33) % 10)
            val ry = ((((hh(i, 35) % 1000) / 1000f) * hgt + t * ts * speed) % hgt)
            val big = hh(i, 37) % 5 == 0
            val r = ps * (if (big) 1.7f else 1.1f)
            // the pellet and a short pale streak behind it
            drawLine(Color(0xFFE8F2FA).copy(alpha = 0.45f), Offset(rx + ts * 0.25f, ry - ts * 0.5f), Offset(rx, ry), strokeWidth = max(1f, ps * 0.8f))
            drawCircle(Color.White.copy(alpha = 0.95f), radius = r, center = Offset(rx, ry))
            drawCircle(Color(0xFF9CC0E0).copy(alpha = 0.6f), radius = r * 0.5f, center = Offset(rx + r * 0.3f, ry + r * 0.3f))
            // some bounce back up where they hit
            if (hh(i, 39) % 3 == 0) {
                val up = ((t * 6f + i * 0.37f) % 1f)
                val bx = rx + (up - 0.5f) * ts * 0.9f
                val by = ry - sinHop(up) * ts * 0.7f
                drawCircle(Color.White.copy(alpha = 0.75f * (1f - up)), radius = ps * 0.8f, center = Offset(bx, by))
            }
        }
    }
    val now = sim.time.toFloat()
    for (p in sim.hailPops.toList()) {
        val age = now - p[2]
        if (age < 0f || age > 1.2f) continue
        val k = age / 1.2f
        val cx = g.ox + p[0] * ts
        val cy = g.oy + p[1] * ts - ts * 0.9f - k * ts * 0.5f
        val a = 1f - k
        for (j in 0 until 5) {
            val ang = j * 1.2566f + 0.4f
            val d = ps * (2f + k * 5f)
            drawCircle(Color(0xFFFFF2A0).copy(alpha = a), radius = ps * 0.9f, center = Offset(cx + kotlin.math.cos(ang) * d, cy + kotlin.math.sin(ang) * d))
        }
        drawCircle(Color.White.copy(alpha = a), radius = ps * 1.1f, center = Offset(cx, cy))
    }
}

private fun sinHop(u: Float): Float = 4f * u * (1f - u)
