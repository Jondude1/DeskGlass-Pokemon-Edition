package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.abs
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// The great voyage: a blue-sailed galleon carries its captain out across the lake and home again.
// Half way a landmark (wreck, islet, trader's skiff, silver shoal or lake serpent) rises from the water.
// ---------------------------------------------------------------------------------------------
fun DrawScope.voyageGalleon(sim: CivSim, g: Geo) {
    if (!sim.voyActive || sim.voyPx.size < 2) return
    val u = g.ps
    val t = g.t
    val p = sim.voyProgress()

    // the landmark at the far end of the route, after the half-way event
    if (sim.voyEventDone && sim.voyEvent >= 0 && p in 0.5f..0.9f) {
        val last = sim.voyPx.size - 1
        val lx = g.ox + sim.voyPx[last] * g.ts
        val ly = g.oy + sim.voyPy[last] * g.ts
        val a = (1f - (p - 0.5f) / 0.4f).coerceIn(0f, 1f)
        when (sim.voyEvent) {
            0 -> { // wreck: broken hull and a leaning mast
                drawRect(Color(0xFF4A3220).copy(alpha = a), Offset(lx - u * 5f, ly - u * 1.2f), Size(u * 10f, u * 2f))
                drawRect(Color(0xFF6B4A2B).copy(alpha = a), Offset(lx - u * 4f, ly - u * 2.2f), Size(u * 3f, u * 1.2f))
                drawLine(Color(0xFF6B4A2B).copy(alpha = a), Offset(lx + u * 1f, ly - u * 1f), Offset(lx + u * 4f, ly - u * 8f), strokeWidth = u * 0.9f)
                drawRect(Color(0xFFE8D8A8).copy(alpha = a * 0.8f), Offset(lx + u * 2.3f, ly - u * 7f), Size(u * 2.4f, u * 2.8f))
            }
            1 -> { // islet with fruit trees
                drawOval(Color(0xFFD9C48A).copy(alpha = a), Offset(lx - u * 6f, ly - u * 1.5f), Size(u * 12f, u * 3.2f))
                drawOval(Color(0xFF4F8A3A).copy(alpha = a), Offset(lx - u * 5f, ly - u * 2.4f), Size(u * 10f, u * 2.6f))
                for (k in 0 until 3) {
                    val tx = lx - u * 3.5f + k * u * 3.5f
                    drawRect(Color(0xFF6B4A2B).copy(alpha = a), Offset(tx - u * 0.35f, ly - u * 5f), Size(u * 0.7f, u * 3f))
                    drawCircle(Color(0xFF3E7A2E).copy(alpha = a), u * 2f, Offset(tx, ly - u * 5.6f))
                    drawCircle(Color(0xFFD03A2A).copy(alpha = a), u * 0.5f, Offset(tx + u * 0.8f, ly - u * 5f))
                }
            }
            2 -> { // trader's skiff with a lantern and a striped sail
                drawRect(Color(0xFF7A5A34).copy(alpha = a), Offset(lx - u * 4f, ly - u * 1.4f), Size(u * 8f, u * 1.6f))
                drawRect(Color(0xFF9A7448).copy(alpha = a), Offset(lx - u * 4f, ly - u * 1.4f), Size(u * 8f, u * 0.5f))
                drawRect(Color(0xFF6B4A2B).copy(alpha = a), Offset(lx - u * 0.3f, ly - u * 7f), Size(u * 0.6f, u * 5.6f))
                for (k in 0 until 3) drawRect(if (k % 2 == 0) Color(0xFFE8A82A).copy(alpha = a) else Color(0xFFF1E9D2).copy(alpha = a), Offset(lx + u * 0.4f, ly - u * 6.6f + k * u * 1.2f), Size(u * 3.2f, u * 1.2f))
                drawCircle(Color(0xFFFFE070).copy(alpha = a * (0.6f + 0.4f * sin(t * 6f))), u * 0.7f, Offset(lx - u * 3.2f, ly - u * 2.4f))
            }
            3 -> { // silver shoal: flickering fish backs
                for (k in 0 until 14) {
                    val ph = t * 2.2f + k * 1.7f
                    val fx = lx + sin(k * 2.1f) * u * 6f
                    val fy = ly + sin(k * 3.7f) * u * 2.2f + sin(ph) * u * 0.5f
                    val flash = (0.5f + 0.5f * sin(ph * 1.6f)).coerceIn(0f, 1f)
                    drawOval(Color(0xFFDCE9F2).copy(alpha = a * (0.35f + 0.55f * flash)), Offset(fx - u * 0.9f, fy - u * 0.35f), Size(u * 1.8f, u * 0.7f))
                }
            }
            else -> { // lake serpent: dark humps and a red eye
                for (k in 0 until 3) {
                    val hy = ly - u * (0.6f + 1.4f * abs(sin(t * 2.4f + k * 1.1f)))
                    drawOval(Color(0xFF2E5A4A).copy(alpha = a), Offset(lx - u * 5f + k * u * 3.4f, hy), Size(u * 3f, u * 2.4f))
                }
                drawOval(Color(0xFF2E5A4A).copy(alpha = a), Offset(lx + u * 5f, ly - u * 5f), Size(u * 2.6f, u * 4f))
                drawCircle(Color(0xFFFF3A2A).copy(alpha = a), u * 0.4f, Offset(lx + u * 6.8f, ly - u * 4.3f))
            }
        }
    }

    // the galleon itself; the captain stands on its deck (the villager is drawn at the same spot)
    val x = g.ox + sim.voyShipX * g.ts
    val y = g.oy + sim.voyShipY * g.ts + u * 2.2f + sin(t * 1.9f) * u * 0.5f
    val dir = if (sim.voyHeadX < 0f) -1f else 1f
    // wake
    for (k in 0 until 3) {
        val ph = (t * 0.7f + k * 0.33f) % 1f
        drawOval(Color.White.copy(alpha = 0.3f * (1f - ph)), Offset(x - dir * u * (9f + 6f * ph) - u * 3f, y - u * 0.8f + k * u * 0.5f), Size(u * (7f + 4f * ph), u * 1.8f))
    }
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(x - u * 8f, y - u * 0.3f), Size(u * 16f, u * 2.2f))
    val hull = Color(0xFF4F3320)
    val plank = Color(0xFF86592F)
    drawRect(hull, Offset(x - u * 8f, y - u * 2.4f), Size(u * 16f, u * 2.2f))
    drawRect(hull, Offset(x - u * 6f, y - u * 0.4f), Size(u * 12f, u * 1.2f))
    drawRect(plank, Offset(x - u * 8f, y - u * 2.4f), Size(u * 16f, u * 0.6f))
    drawRect(Color(0xFFE8C547), Offset(x - u * 8f, y - u * 1.5f), Size(u * 16f, u * 0.35f))
    // raised stern cabin and bowsprit
    drawRect(hull, Offset(x - dir * u * 8f - u * 2f, y - u * 4.4f), Size(u * 4f, u * 2.2f))
    drawRect(plank, Offset(x - dir * u * 8f - u * 2f, y - u * 4.4f), Size(u * 4f, u * 0.5f))
    drawLine(plank, Offset(x + dir * u * 8f, y - u * 2.2f), Offset(x + dir * u * 11.5f, y - u * 3.6f), strokeWidth = u * 0.8f)
    // two masts with blue and cream sails, each with a gold star
    val sway = sin(t * 2f) * u * 0.35f
    for (m in 0 until 2) {
        val mx = x + (if (m == 0) -3.5f else 3.2f) * u
        val h = if (m == 0) 12.5f else 10.5f
        drawRect(Color(0xFF6B4A2B), Offset(mx - u * 0.35f, y - u * h), Size(u * 0.7f, u * (h - 2.2f)))
        for (k in 0 until 3) {
            val sw = u * (if (m == 0) 6.6f else 5.4f)
            val sy = y - u * (h - 0.6f) + k * u * 2.6f
            drawRect(if ((k + m) % 2 == 0) Color(0xFF3F6FB5) else Color(0xFFF1E9D2), Offset(mx - sw / 2f + sway * (1 + k * 0.3f), sy), Size(sw, u * 2.5f))
        }
        drawCircle(Color(0xFFE8C547), u * 0.7f, Offset(mx + sway, y - u * (h - 3.4f)))
        // pennant
        val flap = sin(t * 7f + m) * u * 0.6f
        drawRect(Color(0xFFD03A2A), Offset(mx, y - u * h - u * 1.3f), Size(u * 2.4f * dir, u * 0.9f + flap * 0.2f))
    }
    // barrels of cargo once something was found
    if (sim.voyEventDone && sim.voyEvent in 0..3) {
        for (k in 0 until 2) {
            drawRect(Color(0xFF9A7448), Offset(x + u * (1f + k * 1.8f), y - u * 3.6f), Size(u * 1.4f, u * 1.3f))
            drawRect(Color(0xFF5A3A22), Offset(x + u * (1f + k * 1.8f), y - u * 3.1f), Size(u * 1.4f, u * 0.25f))
        }
    }
    // night lantern at the prow
    if (g.dark > 0.35f) {
        val px = x + dir * u * 9f
        drawCircle(Color(0xFFFFD27A).copy(alpha = 0.22f), u * 4.5f, Offset(px, y - u * 3.6f))
        drawCircle(Color(0xFFFFE9A8), u * 0.7f, Offset(px, y - u * 3.6f))
    }
}
