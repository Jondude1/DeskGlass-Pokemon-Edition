package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.GW
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Wildfires: licking flames over burning trees, rising smoke, night glow, and charred ground afterwards. */

private val FIRE_RED = Color(0xFFD7391A)
private val FIRE_ORANGE = Color(0xFFFF8A1F)
private val FIRE_YELLOW = Color(0xFFFFD84A)
private val FIRE_CORE = Color(0xFFFFF3B0)
private val CHAR_DARK = Color(0xFF2A211B)
private val CHAR_MID = Color(0xFF45372C)

fun DrawScope.wildfires(sim: CivSim, g: Geo) {
    if (sim.blazes.isEmpty() && sim.scorches.isEmpty()) return
    val u = g.ps
    // charred ground first
    for (sc in sim.scorches.toList()) {
        val left = ((sc[2] - sim.time) / 20.0).toFloat().coerceIn(0f, 1f)
        val cx = g.ox + sc[0].toFloat() * g.ts
        val cy = g.oy + sc[1].toFloat() * g.ts
        drawOval(CHAR_DARK.copy(alpha = 0.55f * left), Offset(cx - u * 4.2f, cy - u * 1.6f), Size(u * 8.4f, u * 3.6f))
        drawOval(CHAR_MID.copy(alpha = 0.6f * left), Offset(cx - u * 2.6f, cy - u * 1.1f), Size(u * 5.2f, u * 2.4f))
        drawRect(CHAR_DARK.copy(alpha = 0.8f * left), Offset(cx - u * 0.4f, cy - u * 3.4f), Size(u * 0.8f, u * 3.2f))
    }
    val dark = (g.dark / 0.55f).coerceIn(0f, 1f)
    for (b in sim.blazes.toList()) {
        val cx = g.ox + ((b.i % GW) + 0.5f) * g.ts
        val cy = g.oy + ((b.i / GW) + 0.5f) * g.ts
        val age = (sim.time - b.born).toFloat()
        val grow = min(1f, age / 3f)
        val fade = min(1f, max(0f, (75f - age) / 8f))
        val k = grow * fade
        // glow on the ground and trees
        for (r in 0 until 3) {
            drawCircle(FIRE_ORANGE.copy(alpha = (0.05f + 0.05f * dark) * (3 - r) * k), u * (14f - r * 3.5f), Offset(cx, cy - u * 3f))
        }
        // tongues of flame
        for (j in 0 until 5) {
            val ph = g.t * (5.5f + j * 0.7f) + j * 1.9f + b.i * 0.37f
            val flick = 0.65f + 0.35f * sin(ph)
            val fx = cx + (j - 2) * u * 1.7f + sin(ph * 0.5f) * u * 0.5f
            val h = u * (5.5f + (j % 3) * 2.2f) * flick * k
            val w = u * (2.6f - (if (j == 0 || j == 4) 0.8f else 0f))
            val base = cy + u * 0.6f
            drawRect(FIRE_RED.copy(alpha = 0.9f), Offset(fx - w / 2f, base - h), Size(w, h))
            drawRect(FIRE_ORANGE.copy(alpha = 0.95f), Offset(fx - w * 0.36f, base - h * 0.82f), Size(w * 0.72f, h * 0.82f))
            drawRect(FIRE_YELLOW.copy(alpha = 0.95f), Offset(fx - w * 0.2f, base - h * 0.55f), Size(w * 0.4f, h * 0.55f))
            drawRect(FIRE_CORE.copy(alpha = 0.9f), Offset(fx - w * 0.1f, base - h * 0.28f), Size(w * 0.2f, h * 0.28f))
        }
        // embers and smoke
        for (m in 0 until 6) {
            val p = ((g.t * 0.5f + m * 0.17f + b.i * 0.013f) % 1f)
            val sx = cx + sin(p * 7f + m * 2.1f) * u * 2.4f
            val sy = cy - u * 6f - p * u * 18f
            drawCircle(Color(0xFF55504B).copy(alpha = 0.34f * (1f - p) * k), u * (1.4f + 2.2f * p), Offset(sx, sy))
            if (m % 2 == 0) {
                val ey = cy - u * 4f - p * u * 11f
                drawRect(FIRE_YELLOW.copy(alpha = 0.9f * (1f - p) * k), Offset(sx + u * 1.2f, ey), Size(u * 0.6f, u * 0.6f))
            }
        }
        // bucket-brigade water arcs from villagers dousing nearby
        for (v in sim.villagers) {
            if (v.action != "douse" || v.phase != "working") continue
            val d = kotlin.math.hypot(v.x - (b.i % GW + 0.5f), v.y - (b.i / GW + 0.5f))
            if (d > 3.2f) continue
            val vx = g.ox + v.x * g.ts
            val vy = g.oy + (v.y - 0.5f) * g.ts
            for (m in 0 until 5) {
                val p = ((g.t * 1.6f + m * 0.2f) % 1f)
                val wx = vx + (cx - vx) * p
                val wy = vy + (cy - u * 2f - vy) * p - sin(p * 3.1416f) * u * 4f
                drawRect(Color(0xFF7FC4FF).copy(alpha = 0.85f), Offset(wx - u * 0.4f, wy - u * 0.4f), Size(u * 0.8f, u * 0.8f))
            }
            drawCircle(Color(0xFFDDEFFF).copy(alpha = 0.45f), u * 3f, Offset(cx, cy - u * 1.2f))
        }
    }
}
