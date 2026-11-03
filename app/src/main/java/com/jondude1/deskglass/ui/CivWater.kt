package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.T
import com.jondude1.deskglass.sim.WaterLife
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Living lakes: foam along the shore, swaying reeds with cattails, lily pads and flowers, paddling ducks with ducklings,
 *  leaping fish and rain rings. Pure drawing; placement rules are in sim/WaterLife.kt. */

private fun DrawScope.duck(cx: Float, cy: Float, f: Int, u: Float, body: Color, head: Color, dab: Boolean) {
    val fl = f.toFloat()
    // soft shadow on the water
    drawOval(Color(0x33102030), Offset(cx - u * 3f, cy - u * 0.4f), Size(u * 6f, u * 2f))
    // tail and body
    drawRect(body, Offset(cx - fl * u * 3.2f - u * 0.7f, cy - u * (if (dab) 3.6f else 2.2f)), Size(u * 1.4f, u * 1.6f))
    drawOval(body, Offset(cx - u * 2.8f, cy - u * 2.2f), Size(u * 5.6f, u * 2.9f))
    drawRect(Color(0x33000000), Offset(cx - u * 1.6f, cy - u * 1.2f), Size(u * 2.8f, u * 0.6f))
    if (!dab) {
        // neck, head, beak and eye
        drawRect(head, Offset(cx + fl * u * 2.0f - u * 0.7f, cy - u * 3.6f), Size(u * 1.4f, u * 1.8f))
        drawRect(head, Offset(cx + fl * u * 2.4f - u * 0.9f, cy - u * 4.2f), Size(u * 1.8f, u * 1.6f))
        drawRect(Color(0xFFF2A81D), Offset(cx + fl * u * 3.6f - u * 0.7f, cy - u * 3.5f), Size(u * 1.4f, u * 0.8f))
        drawRect(Color(0xFF101010), Offset(cx + fl * u * 2.7f - u * 0.3f, cy - u * 3.9f), Size(u * 0.6f, u * 0.6f))
    }
}

internal fun DrawScope.waterLife(sim: CivSim, g: Geo, vx0: Int, vx1: Int, vy0: Int, vy1: Int) {
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    val dark = g.dark
    val snow = sim.snowCover()
    val raining = sim.raining()
    val pads = WaterLife.lilyPads(sim)
    val flowers = WaterLife.lilyFlower(sim)
    val day = dark < 0.45f
    for (y in vy0 until vy1) for (x in vx0 until vx1) {
        if (sim.tiles[y * GW + x] != T.WATER) continue
        val h = WaterLife.h(x, y)
        val px = g.ox + x * ts
        val py = g.oy + y * ts
        val frozen = snow > 0.2f && ((h % 100) / 100f) < snow
        val shore = WaterLife.shoreMask(sim, x, y)

        // lapping foam along the shore
        if (shore != 0 && !frozen) {
            val lap = 0.5f + 0.5f * sin(t * 1.5f + (h % 13))
            val fa = (0.18f + 0.30f * lap) * (1f - dark * 0.8f)
            val off = ps * (0.6f + 1.4f * lap)
            val fc = Color.White.copy(alpha = fa)
            if (shore and 1 != 0) { drawRect(fc, Offset(px + ps * 1f, py + off), Size(ps * 5f, ps * 0.8f)); drawRect(fc, Offset(px + ps * 9f, py + off), Size(ps * 5f, ps * 0.8f)) }
            if (shore and 4 != 0) { drawRect(fc, Offset(px + ps * 2f, py + ts - off - ps), Size(ps * 5f, ps * 0.8f)); drawRect(fc, Offset(px + ps * 10f, py + ts - off - ps), Size(ps * 4f, ps * 0.8f)) }
            if (shore and 2 != 0) { drawRect(fc, Offset(px + ts - off - ps, py + ps * 2f), Size(ps * 0.8f, ps * 5f)); drawRect(fc, Offset(px + ts - off - ps, py + ps * 10f), Size(ps * 0.8f, ps * 4f)) }
            if (shore and 8 != 0) { drawRect(fc, Offset(px + off, py + ps * 1f), Size(ps * 0.8f, ps * 5f)); drawRect(fc, Offset(px + off, py + ps * 9f), Size(ps * 0.8f, ps * 5f)) }
        }

        // reeds and cattails standing in the shallows
        if (shore != 0 && h % 2 == 0 && !(snow > 0.5f)) {
            for (side in 0 until 4) {
                if (shore and (1 shl side) == 0) continue
                if ((h shr (side + 3)) % 3 == 0) continue
                for (k in 0 until 3) {
                    val hk = WaterLife.h(h, side * 5 + k)
                    val along = ps * (2f + k * 4.2f + (hk % 3))
                    val tall = ps * (5f + (hk shr 3) % 4)
                    val bx: Float
                    val by: Float
                    when (side) {
                        0 -> { bx = px + along; by = py + ps * 4f }
                        2 -> { bx = px + along; by = py + ts - ps * 0.5f }
                        1 -> { bx = px + ts - ps * 2f; by = py + along + ps * 3f }
                        else -> { bx = px + ps * 2f; by = py + along + ps * 3f }
                    }
                    val sway = sin(t * 1.4f + hk % 7) * ps * 0.7f
                    drawLine(Color(0xFF4F8A3A), Offset(bx, by), Offset(bx + sway, by - tall), strokeWidth = ps * 0.8f)
                    drawLine(Color(0xFF6FA84A), Offset(bx + ps, by), Offset(bx + ps + sway * 0.6f, by - tall * 0.7f), strokeWidth = ps * 0.6f)
                    if (hk % 2 == 0) drawRect(Color(0xFF6B4423), Offset(bx + sway - ps * 0.6f, by - tall - ps * 1.6f), Size(ps * 1.4f, ps * 2.6f))
                }
            }
        }

        if (frozen) continue

        // lily pads, with pink and white flowers in summer
        if (pads && WaterLife.lily(sim, x, y)) {
            val drift = sin(t * 0.4f + h % 9) * ps * 0.8f
            val lx = px + ps * (5f + (h shr 4) % 5) + drift
            val ly = py + ps * (6f + (h shr 8) % 4)
            val pad = Color(0xFF3E8E4A)
            drawOval(pad, Offset(lx, ly), Size(ps * 4.4f, ps * 2.6f))
            drawRect(Color(0xFF2C6B38), Offset(lx + ps * 2.2f, ly + ps * 1.0f), Size(ps * 2.2f, ps * 0.5f))
            drawOval(pad, Offset(lx + ps * 4.2f, ly + ps * 1.2f), Size(ps * 3.2f, ps * 2f))
            if (flowers && h % 2 == 0) {
                val fc = if ((h shr 2) % 2 == 0) Color(0xFFFF9EC4) else Color(0xFFFFFFFF)
                drawRect(fc, Offset(lx + ps * 1.2f, ly - ps * 0.9f), Size(ps * 1.8f, ps * 1.4f))
                drawRect(Color(0xFFFFE066), Offset(lx + ps * 1.8f, ly - ps * 0.5f), Size(ps * 0.7f, ps * 0.7f))
            }
        }

        if (day) {
            // ducks paddle in the deep water; the mother leads two ducklings in her wake
            val dk = WaterLife.duckKind(sim, x, y)
            if (dk > 0 && snow < 0.2f) {
                val f = WaterLife.duckFacing(x, y, t)
                val dab = WaterLife.duckDabbing(x, y, t)
                val mallard = (h shr 9) % 2 == 0
                val body = if (mallard) Color(0xFF9A9288) else Color(0xFFF1EEE6)
                val head = if (mallard) Color(0xFF1F7A4C) else Color(0xFFF1EEE6)
                val bob = sin(t * 2.2f + h % 11) * ps * 0.3f
                val dx = px + WaterLife.duckX(x, y, t) * ts
                val dy = py + WaterLife.duckY(x, y, t) * ts + bob
                // V-shaped wake behind
                val wa = 0.32f * (1f - dark)
                drawLine(Color.White.copy(alpha = wa), Offset(dx - f * ps * 3f, dy - ps * 0.3f), Offset(dx - f * ps * 9f, dy - ps * 2.4f), strokeWidth = ps * 0.7f)
                drawLine(Color.White.copy(alpha = wa), Offset(dx - f * ps * 3f, dy - ps * 0.3f), Offset(dx - f * ps * 9f, dy + ps * 1.8f), strokeWidth = ps * 0.7f)
                if (dab) {
                    val r = ((t * 1.2f) % 1f)
                    drawCircle(Color.White.copy(alpha = 0.4f * (1f - r)), radius = ps * (2f + 5f * r), center = Offset(dx + f * ps * 3f, dy), style = Stroke(ps * 0.7f))
                }
                if (dk == 2) {
                    for (c in 0 until 2) {
                        val lag = 1.6f + c * 1.5f
                        val cx = px + WaterLife.duckX(x, y, t, lag) * ts
                        val cy = py + WaterLife.duckY(x, y, t, lag) * ts + bob * 0.6f
                        duck(cx, cy, f, ps * 0.55f, Color(0xFFE8C95A), Color(0xFFE8C95A), false)
                    }
                }
                duck(dx, dy, f, ps, body, head, dab)
            }

            // a fish leaps now and then
            val lp = WaterLife.fishLeap(sim, x, y, t)
            if (lp >= 0f && snow < 0.2f) {
                val fx = px + ts * 0.5f + (lp - 0.5f) * ps * 8f
                val arc = sin(lp * PI.toFloat())
                val fy = py + ts * 0.55f - arc * ps * 7f
                drawRect(Color(0xFF8FB9D6), Offset(fx - ps * 1.8f, fy - ps * 0.8f), Size(ps * 3.6f, ps * 1.6f))
                drawRect(Color(0xFFE6F2FA), Offset(fx - ps * 1.2f, fy), Size(ps * 2.4f, ps * 0.6f))
                drawRect(Color(0xFF6C9AB8), Offset(fx - ps * 2.8f, fy - ps * 1.2f), Size(ps * 1.2f, ps * 2.2f))
                drawRect(Color(0xFF101010), Offset(fx + ps * 1.0f, fy - ps * 0.5f), Size(ps * 0.5f, ps * 0.5f))
                // splash rings where it rises and where it lands
                val ring = Color.White.copy(alpha = 0.55f * (1f - abs(lp - 0.5f) * 1.2f))
                drawCircle(ring, radius = ps * (1.5f + 4f * lp), center = Offset(px + ts * 0.5f - ps * 4f, py + ts * 0.62f), style = Stroke(ps * 0.7f))
                drawCircle(ring, radius = ps * (1.5f + 4f * lp), center = Offset(px + ts * 0.5f + ps * 4f, py + ts * 0.62f), style = Stroke(ps * 0.7f))
            }
        }

        // rain rings spreading on the surface
        if (raining && h % 3 == 0) {
            val period = 1.5f + (h % 7) / 8f
            val r = ((t + (h % 17)) % period) / period
            val cx = px + ps * (3f + (h shr 5) % 10)
            val cy = py + ps * (4f + (h shr 9) % 8)
            drawCircle(Color.White.copy(alpha = 0.5f * (1f - r)), radius = ps * (0.8f + 3.8f * r), center = Offset(cx, cy), style = Stroke(ps * 0.6f))
        }
    }
}
