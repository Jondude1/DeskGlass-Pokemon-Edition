package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// The trade caravan: an ox pulls a canvas-covered wagon down the road off the village edge and home again.
// At the far end a road scene shows what the trader met (hill fair, spice traders, bandits, scholar, cairn).
// ---------------------------------------------------------------------------------------------
fun DrawScope.tradeCaravan(sim: CivSim, g: Geo) {
    if (!sim.carActive) return
    val u = g.ps
    val t = g.t
    val p = sim.carProgress()

    // the road scene at the far end, after the half-way event
    if (sim.carEventDone && sim.carEvent >= 0 && p in 0.5f..0.92f) {
        val ex = g.ox + (sim.carBx + sim.carDx * sim.carDist) * g.ts
        val ey = g.oy + (sim.carBy + sim.carDy * sim.carDist) * g.ts
        val a = (1f - (p - 0.5f) / 0.42f).coerceIn(0f, 1f)
        val sx = ex + u * 6f
        when (sim.carEvent) {
            0 -> { // hill fair: striped stall, bunting and coins
                for (k in 0 until 4) {
                    drawRect(if (k % 2 == 0) Color(0xFFD03A2A).copy(alpha = a) else Color(0xFFF1E9D2).copy(alpha = a), Offset(sx - u * 3.2f + k * u * 1.6f, ey - u * 5.2f), Size(u * 1.6f, u * 1.4f))
                }
                drawRect(Color(0xFF6B4A2B).copy(alpha = a), Offset(sx - u * 3.2f, ey - u * 3.8f), Size(u * 0.5f, u * 3.8f))
                drawRect(Color(0xFF6B4A2B).copy(alpha = a), Offset(sx + u * 2.7f, ey - u * 3.8f), Size(u * 0.5f, u * 3.8f))
                drawRect(Color(0xFF9A7448).copy(alpha = a), Offset(sx - u * 3.2f, ey - u * 1.8f), Size(u * 6.4f, u * 1.2f))
                for (k in 0 until 3) drawCircle(Color(0xFFE8C547).copy(alpha = a * (0.5f + 0.5f * sin(t * 5f + k * 2f))), u * 0.45f, Offset(sx - u * 1.8f + k * u * 1.8f, ey - u * 2.3f))
            }
            1 -> { // spice traders: open sacks of colour
                val cols = listOf(Color(0xFFC8501E), Color(0xFFE8B02A), Color(0xFF6A9A3A), Color(0xFF8A3A6A))
                for (k in 0 until 4) {
                    val bx = sx - u * 3.6f + k * u * 2.3f
                    drawRect(Color(0xFFB89A68).copy(alpha = a), Offset(bx, ey - u * 2.2f), Size(u * 1.9f, u * 2.2f))
                    drawOval(cols[k].copy(alpha = a), Offset(bx + u * 0.1f, ey - u * 3.0f), Size(u * 1.7f, u * 1.1f))
                }
                drawRect(Color(0xFF5A3A22).copy(alpha = a), Offset(sx + u * 3.4f, ey - u * 4.6f), Size(u * 0.4f, u * 4.6f))
                drawRect(Color(0xFFE8A82A).copy(alpha = a), Offset(sx + u * 3.8f, ey - u * 4.6f), Size(u * 2.2f, u * 1.4f))
            }
            2 -> { // road bandits: masked figures that scatter, or flee from the guards
                val flee = if (sim.carGuarded()) (p - 0.5f) * 4f else 0f
                for (k in 0 until 3) {
                    val bx = sx - u * 2f + k * u * 2.2f + flee * u * 5f * (k + 1)
                    val hop = abs(sin(t * 9f + k * 1.7f)) * u * 0.8f
                    drawRect(Color(0xFF3A2A30).copy(alpha = a), Offset(bx, ey - u * 3.4f - hop), Size(u * 1.2f, u * 2.4f))
                    drawRect(Color(0xFFD9B58A).copy(alpha = a), Offset(bx + u * 0.1f, ey - u * 4.4f - hop), Size(u * 1.0f, u * 1.0f))
                    drawRect(Color(0xFF8A1E1E).copy(alpha = a), Offset(bx + u * 0.05f, ey - u * 4.1f - hop), Size(u * 1.1f, u * 0.4f))
                    drawLine(Color(0xFFC9D2D8).copy(alpha = a), Offset(bx + u * 1.2f, ey - u * 3.2f - hop), Offset(bx + u * 2.4f, ey - u * 4.6f - hop), strokeWidth = u * 0.35f)
                }
                if (sim.carGuarded()) {
                    val sp = ((p - 0.5f) * 20f).toInt() % 3
                    drawCircle(Color(0xFFFFF0B0).copy(alpha = a * 0.7f), u * (1.0f + sp * 0.4f), Offset(sx - u * 4.5f, ey - u * 3.5f))
                }
            }
            3 -> { // scholar: little tent and an open book glowing
                drawRect(Color(0xFFE8DCC0).copy(alpha = a), Offset(sx - u * 2.6f, ey - u * 3.0f), Size(u * 5.2f, u * 3.0f))
                drawRect(Color(0xFF3F6FB5).copy(alpha = a), Offset(sx - u * 2.6f, ey - u * 3.6f), Size(u * 5.2f, u * 0.8f))
                drawRect(Color(0xFF4A3220).copy(alpha = a), Offset(sx - u * 0.6f, ey - u * 1.9f), Size(u * 1.2f, u * 1.9f))
                val gl = 0.5f + 0.5f * sin(t * 4f)
                drawCircle(Color(0xFFFFF0B0).copy(alpha = a * 0.3f * gl), u * 2.4f, Offset(sx + u * 4.2f, ey - u * 1.4f))
                drawRect(Color(0xFFF1E9D2).copy(alpha = a), Offset(sx + u * 3.4f, ey - u * 1.7f), Size(u * 1.6f, u * 1.0f))
                drawRect(Color(0xFF8A3A2A).copy(alpha = a), Offset(sx + u * 4.1f, ey - u * 1.7f), Size(u * 0.2f, u * 1.0f))
            }
            else -> { // old cairn: a heap of grey stones with a sparkle
                val cols = listOf(Color(0xFF8A8F94), Color(0xFF6E7378), Color(0xFFA0A5AA))
                for (k in 0 until 6) {
                    val w = u * (2.6f - k * 0.3f)
                    drawOval(cols[k % 3].copy(alpha = a), Offset(sx - w / 2f, ey - u * (0.8f + k * 0.9f)), Size(w, u * 1.1f))
                }
                val tw = 0.5f + 0.5f * sin(t * 6f)
                drawCircle(Color(0xFFFFE9A0).copy(alpha = a * tw), u * 0.8f, Offset(sx + u * 1.4f, ey - u * 4.6f))
            }
        }
    }

    // the wagon itself
    val x = g.ox + sim.carX * g.ts
    val y = g.oy + sim.carY * g.ts
    val dir = sim.carHeadX
    val roll = sim.carOffset() * 6.5f
    val bob = abs(sin(roll * 1.2f)) * u * 0.25f
    // dust trailing behind
    for (k in 0 until 5) {
        val ph = (t * 1.6f + k * 0.2f) % 1f
        drawCircle(Color(0xFFC9B58A).copy(alpha = 0.32f * (1f - ph)), u * (0.5f + ph * 1.5f), Offset(x - dir * u * (3f + ph * 6f), y - u * (0.6f + ph * 1.4f)))
    }
    drawOval(Color(0x44000000), Offset(x - u * 5.5f, y - u * 0.5f), Size(u * 11f, u * 1.4f))
    // wheels
    for (k in 0 until 2) {
        val wx = x - u * 2.6f + k * u * 4.4f
        val wy = y - u * 1.4f - bob
        drawCircle(Color(0xFF4A3220), u * 1.35f, Offset(wx, wy))
        drawCircle(Color(0xFFB88A55), u * 0.95f, Offset(wx, wy))
        for (s in 0 until 3) {
            val ang = roll + s * 1.0472f
            drawLine(Color(0xFF4A3220), Offset(wx - cos(ang) * u * 1.2f, wy - sin(ang) * u * 1.2f), Offset(wx + cos(ang) * u * 1.2f, wy + sin(ang) * u * 1.2f), strokeWidth = u * 0.3f)
        }
    }
    // bed, crates and canvas cover
    val by = y - u * 2.8f - bob
    drawRect(Color(0xFF7A5A34), Offset(x - u * 4.2f, by), Size(u * 8.4f, u * 1.5f))
    drawRect(Color(0xFF9A7448), Offset(x - u * 4.2f, by), Size(u * 8.4f, u * 0.5f))
    drawOval(Color(0xFFF1E9D2), Offset(x - u * 3.6f, by - u * 3.6f), Size(u * 7.2f, u * 4.4f))
    drawRect(Color(0xFFF1E9D2), Offset(x - u * 3.6f, by - u * 1.6f), Size(u * 7.2f, u * 1.8f))
    drawRect(Color(0xFFD03A2A), Offset(x - u * 3.6f, by - u * 0.9f), Size(u * 7.2f, u * 0.5f))
    drawOval(Color(0x1A000000), Offset(x - u * 3.6f, by - u * 3.6f), Size(u * 2.4f, u * 4.4f))
    drawRect(Color(0xFF8A6A3C), Offset(x - dir * u * 3.8f - u * 0.9f, by - u * 1.2f), Size(u * 1.8f, u * 1.4f))
    // pennant
    val flap = sin(t * 7f) * u * 0.5f
    drawRect(Color(0xFF6A9A3A), Offset(if (dir > 0f) x - u * 5.6f else x + u * 3.6f, by - u * 4.9f), Size(u * 2.0f, u * 0.9f + flap * 0.2f))
    drawRect(Color(0xFF4A3220), Offset(x - dir * u * 3.6f - u * 0.15f, by - u * 4.9f), Size(u * 0.3f, u * 1.8f))
    // the ox in front, legs trotting
    val ox = x + dir * u * 7.4f
    val oy = y - u * 2.6f - bob
    drawLine(Color(0xFF6B4A2B), Offset(x + dir * u * 4.1f, by + u * 0.5f), Offset(ox - dir * u * 1.6f, oy + u * 0.4f), strokeWidth = u * 0.35f)
    drawRect(Color(0xFF8A6A48), Offset(ox - u * 2.0f, oy - u * 1.6f), Size(u * 4.0f, u * 2.2f))
    drawRect(Color(0xFF6E5236), Offset(ox + dir * u * 1.3f - u * 0.9f, oy - u * 2.2f), Size(u * 1.8f, u * 1.7f))
    drawRect(Color(0xFFE8DCC0), Offset(ox + dir * u * 2.0f - u * 0.2f, oy - u * 2.8f), Size(u * 0.4f, u * 0.8f))
    for (k in 0 until 2) {
        val swing = sin(roll * 1.2f + k * 3.14f) * u * 0.5f
        drawRect(Color(0xFF4A3220), Offset(ox - u * 1.6f + k * u * 2.6f + swing, oy + u * 0.5f), Size(u * 0.6f, u * 1.6f))
    }
    // night lantern on the wagon
    if (g.dark > 0.35f) {
        drawCircle(Color(0xFFFFD27A).copy(alpha = 0.22f), u * 4.2f, Offset(x + dir * u * 3.6f, by - u * 0.4f))
        drawCircle(Color(0xFFFFE9A8), u * 0.6f, Offset(x + dir * u * 3.6f, by - u * 0.4f))
    }
}
