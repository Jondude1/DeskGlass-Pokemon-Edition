package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.FloodState
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.T
import kotlin.math.sin
import kotlin.math.max

/** Floods: the lake spreads brown-blue water over the low banks with ripples, foam at the creeping edge and bobbing
 *  logs, sandbag rings protect some farms, and once it drains a skin of dark silt fades while stranded fish flop on the mud.
 *  Pure drawing; the water level and the sandbagged farms come from CivSim.flood. */
private fun fh(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

internal fun DrawScope.floodGround(sim: CivSim, g: Geo, vx0: Int, vx1: Int, vy0: Int, vy1: Int) {
    val fl = sim.flood
    val lvl = fl.level(sim.time)
    val sinceEnd = sim.time - fl.endAt
    val silt = if (lvl <= 0f && fl.endAt > 0.0 && sinceEnd in 0.0..70.0 && fl.soaked.isNotEmpty()) (1.0 - sinceEnd / 70.0).toFloat() else 0f
    if (lvl <= 0f && silt <= 0f) return
    val depth = fl.depth(sim.tiles)
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    val night = (g.dark / 0.55f).coerceIn(0f, 1f)
    val reach = lvl * FloodState.REACH
    for (y in vy0 until vy1) for (x in vx0 until vx1) {
        val i = y * GW + x
        val d = depth[i]
        if (d <= 0 || sim.tiles[i] == T.WATER) continue
        val px = g.ox + x * ts
        val py = g.oy + y * ts
        if (lvl > 0f) {
            val cover = (reach - d + 1f).coerceIn(0f, 1f)
            if (cover <= 0.02f) continue
            val h = fh(x, y)
            val a = 0.6f * cover * (1f - 0.35f * night)
            drawRect(Color(0xFF5A84A6).copy(alpha = a), Offset(px, py), Size(ts + 1f, ts + 1f))
            drawRect(Color(0xFF7A6446).copy(alpha = 0.2f * cover), Offset(px, py), Size(ts + 1f, ts + 1f))
            // little ripples sliding across
            val ph = (t * 0.9f + (h % 17) * 0.37f) % 1f
            val rx = px + ts * (0.1f + 0.5f * ph)
            val ry = py + ts * (0.2f + 0.6f * ((h shr 4) % 7) / 7f)
            drawRect(Color(0xFFD6ECFA).copy(alpha = 0.5f * cover * (1f - ph)), Offset(rx, ry), Size(ts * 0.35f, max(1f, ps * 0.7f)))
            // foam on the creeping edge
            if (cover < 0.9f) {
                drawRect(Color(0xFFEAF5FB).copy(alpha = 0.55f * (1f - cover)), Offset(px, py + ts * 0.35f), Size(ts + 1f, max(1f, ps)))
            }
            // a log or two riding the water
            if (h % 23 == 0 && cover > 0.8f) {
                val bob = sin(t * 1.7f + h % 9) * ps * 0.6f
                drawRect(Color(0xFF6B4A2B), Offset(px + ts * 0.15f + sin(t * 0.4f + h) * ps, py + ts * 0.45f + bob), Size(ts * 0.7f, ps * 1.3f))
                drawRect(Color(0xFF8C6740), Offset(px + ts * 0.15f, py + ts * 0.45f + bob), Size(ts * 0.18f, ps * 1.3f))
            }
        } else if (d <= 3) {
            // silt: dark rich mud that fades as the sun dries it
            drawRect(Color(0xFF4A3622).copy(alpha = 0.3f * silt), Offset(px, py), Size(ts + 1f, ts + 1f))
            val h = fh(x, y)
            if (h % 5 == 0) drawRect(Color(0xFF2F2216).copy(alpha = 0.4f * silt), Offset(px + ts * 0.3f, py + ts * 0.5f), Size(ts * 0.3f, ps))
            // stranded fish flopping in the mud
            if (fl.fishLeft > 0 && d <= 2 && h % 11 == 0 && sinceEnd < 25.0) {
                val hop = kotlin.math.abs(sin(t * 5f + h % 7)) * ps * 2f
                drawRect(Color(0xFFCFE3EE), Offset(px + ts * 0.3f, py + ts * 0.4f - hop), Size(ps * 2.2f, ps))
                drawRect(Color(0xFF7FA6BD), Offset(px + ts * 0.3f + ps * 1.6f, py + ts * 0.4f - hop - ps * 0.4f), Size(ps * 0.8f, ps * 1.8f))
            }
        }
    }
    // sandbag rings around the farms that were kept dry
    if (fl.bagged.isNotEmpty()) {
        for (b in sim.buildings) {
            if (b.type != "farm" || (b.y * GW + b.x) !in fl.bagged) continue
            if (b.x < vx0 - 3 || b.x > vx1 + 3 || b.y < vy0 - 3 || b.y > vy1 + 3) continue
            val bx = g.ox + b.x * ts - ts * 0.15f
            val by = g.oy + b.y * ts - ts * 0.15f
            val side = ts * 2.3f
            val n = 8
            for (k in 0 until n) {
                val u = k * side / n
                for (pos in 0 until 4) {
                    val (sx, sy) = when (pos) { 0 -> bx + u to by; 1 -> bx + u to by + side; 2 -> bx to by + u; else -> bx + side to by + u }
                    drawRect(Color(0xFF8A6A3C), Offset(sx, sy), Size(ts * 0.3f, ps * 1.8f))
                    drawRect(Color(0xFFC9A66B), Offset(sx, sy), Size(ts * 0.3f, ps * 0.7f))
                }
            }
        }
    }
}
