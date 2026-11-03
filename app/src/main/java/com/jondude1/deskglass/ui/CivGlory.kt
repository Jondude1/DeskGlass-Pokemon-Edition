package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.cos
import kotlin.math.sin

/**
 * Age of Gold: once the Golden Age has begun, golden pennants fly over the hall and gold motes drift up around it;
 * on a decree day a bright halo, rays and a coloured standard for the day's decree join them.
 */
fun decreeColor(kind: String): Color = when (kind) {
    "bounty" -> Color(0xFFE8B63A)
    "valor" -> Color(0xFFD8453A)
    "wisdom" -> Color(0xFF6A8CE8)
    "craft" -> Color(0xFFC9703A)
    else -> Color(0xFFE86AA8)
}

fun DrawScope.glorySite(sim: CivSim, g: Geo) {
    if (sim.goldenAt < 0.0) return
    val hall = sim.buildings.firstOrNull { it.type == "hall" } ?: return
    val u = g.ps
    val t = g.t
    val cx = g.ox + (hall.x + 0.5f) * g.ts
    val cy = g.oy + (hall.y + 0.5f) * g.ts
    val on = sim.decreeOn()
    val k = if (on) {
        val rise = ((sim.time - sim.decreeStart) / 3.0).toFloat().coerceIn(0f, 1f)
        val fade = ((sim.decreeUntil - sim.time) / 5.0).toFloat().coerceIn(0f, 1f)
        rise * fade
    } else 0f
    // a warm golden halo that swells on decree days
    drawCircle(Color(0xFFFFD84A).copy(alpha = 0.05f + 0.16f * k), radius = u * (22f + 10f * k), center = Offset(cx, cy - u * 4f))
    if (k > 0f) {
        for (i in 0 until 10) {
            val a = i * 0.628f + t * 0.15f
            val r0 = u * 9f
            val r1 = u * (20f + 6f * sin(t * 2f + i))
            drawLine(Color(0xFFFFE27A).copy(alpha = 0.22f * k), Offset(cx + cos(a) * r0, cy - u * 4f + sin(a) * r0 * 0.6f), Offset(cx + cos(a) * r1, cy - u * 4f + sin(a) * r1 * 0.6f), strokeWidth = u * 1.1f)
        }
    }
    // two golden pennants on poles beside the hall
    for (s in 0..1) {
        val px = cx + (if (s == 0) -u * 15f else u * 15f)
        val py = cy + u * 5f
        drawRect(Color(0xFF5A3C22), Offset(px - u * 0.45f, py - u * 15f), Size(u * 0.9f, u * 15f))
        val wave = sin(t * 3f + s * 1.7f) * u * 0.9f
        drawRect(Color(0xFFFFD84A), Offset(px + u * 0.45f, py - u * 15f), Size(u * 5.5f + wave, u * 3.2f))
        drawRect(Color(0xFFE8A91E), Offset(px + u * 0.45f, py - u * 12.8f), Size(u * 4.2f + wave, u * 0.8f))
        drawCircle(Color(0xFFFFF3B0), radius = u * 0.8f, center = Offset(px, py - u * 15.4f))
    }
    // the day's decree standard
    if (k > 0f) {
        val c = decreeColor(sim.decreeKind)
        val px = cx
        val py = cy - u * 14f
        drawRect(Color(0xFF5A3C22).copy(alpha = k), Offset(px - u * 0.4f, py - u * 10f), Size(u * 0.8f, u * 10f))
        val wave = sin(t * 4f) * u * 0.8f
        drawRect(c.copy(alpha = k), Offset(px + u * 0.4f, py - u * 10f), Size(u * 7f + wave, u * 4.6f))
        drawRect(Color(0xFFFFF3B0).copy(alpha = k), Offset(px + u * 2.4f, py - u * 8.6f), Size(u * 2.2f, u * 1.8f))
    }
    // rising gold motes
    val n = if (on) 16 else 8
    for (i in 0 until n) {
        val p = (t * 0.22f + i * (1f / n)) % 1f
        val mx = cx + sin(i * 2.3f + t * 0.7f) * u * (10f + (i % 4) * 4f)
        val my = cy - u * (2f + p * 26f)
        val tw = 0.5f + 0.5f * sin(t * 6f + i * 1.9f)
        val a = (1f - p) * (0.35f + 0.5f * tw) * (if (on) 1f else 0.7f)
        drawRect(Color(0xFFFFE27A).copy(alpha = a), Offset(mx, my), Size(u * 0.9f, u * 0.9f))
    }
}
