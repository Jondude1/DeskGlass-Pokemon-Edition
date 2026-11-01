package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Reaver longship: a dragon-prowed ship moored off the shore while its crew raids the village.
// When the last reaver falls the ship burns, sinks and is gone.
// ---------------------------------------------------------------------------------------------
fun DrawScope.reaverShip(sim: CivSim, g: Geo) {
    if (!sim.shipUp) return
    val u = g.ps
    val t = g.t
    val burn = sim.shipT
    val sink = if (burn > 0f) (1f - burn / 7f).coerceIn(0f, 1f) else 0f
    val x = g.ox + sim.shipX * g.ts
    val y = g.oy + sim.shipY * g.ts + sink * u * 4f + sin(t * 1.8f) * u * 0.5f
    val dir = if (sim.shipDx < 0f) -1f else 1f
    // wake and ripples
    for (k in 0 until 3) {
        val ph = (t * 0.6f + k * 0.33f) % 1f
        drawOval(Color.White.copy(alpha = 0.28f * (1f - ph)), Offset(x - u * (10f + 3f * ph), y - u * 0.8f + k * u * 0.4f), Size(u * (20f + 6f * ph), u * 2.4f))
    }
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(x - u * 9f, y - u * 0.6f), Size(u * 18f, u * 2.4f))
    // hull
    val hull = Color(0xFF5A3A22)
    val hullLight = Color(0xFF8A5E36)
    drawRect(hull, Offset(x - u * 9f, y - u * 3.2f), Size(u * 18f, u * 2.2f))
    drawRect(hull, Offset(x - u * 7f, y - u * 1.1f), Size(u * 14f, u * 1.4f))
    drawRect(hullLight, Offset(x - u * 9f, y - u * 3.2f), Size(u * 18f, u * 0.7f))
    // raised stern and dragon prow
    drawRect(hull, Offset(x - dir * u * 9.5f - u * 0.7f, y - u * 4.6f), Size(u * 1.4f, u * 2f))
    val px = x + dir * u * 9.6f
    drawRect(hullLight, Offset(px - u * 0.7f, y - u * 6.4f), Size(u * 1.4f, u * 3.4f))
    drawRect(Color(0xFFE8C547), Offset(px + dir * u * 0.3f - u * 1f, y - u * 7.2f), Size(u * 2f, u * 1.4f))
    drawRect(Color(0xFFD03A2A), Offset(px + dir * u * 1.2f - u * 0.3f, y - u * 6.8f), Size(u * 0.6f, u * 0.6f))
    // shields along the gunwale
    for (k in 0 until 6) {
        val sx = x - u * 7.5f + k * u * 3f
        drawCircle(if (k % 2 == 0) Color(0xFFD03A2A) else Color(0xFFE8D8A8), u * 1.1f, Offset(sx, y - u * 2.3f))
        drawCircle(Color(0xFFE8C547), u * 0.35f, Offset(sx, y - u * 2.3f))
    }
    // mast and striped sail
    drawRect(Color(0xFF6B4A2B), Offset(x - u * 0.4f, y - u * 12f), Size(u * 0.8f, u * 9f))
    val sailW = u * 8f
    val sway = sin(t * 2.2f) * u * 0.4f
    if (burn <= 0f) {
        for (k in 0 until 4) {
            drawRect(if (k % 2 == 0) Color(0xFFD03A2A) else Color(0xFFF1E9D2), Offset(x - sailW / 2f + sway, y - u * 11.5f + k * u * 1.5f), Size(sailW, u * 1.5f))
        }
    } else {
        drawRect(Color(0xFF2A2420), Offset(x - sailW / 2f, y - u * 11.5f), Size(sailW, u * 3f))
    }
    // burning: flames and smoke
    if (burn > 0f) {
        for (k in 0 until 7) {
            val fx = x - u * 8f + k * u * 2.7f
            val fl = u * (2.6f + 1.8f * sin(t * 11f + k * 1.7f).let { if (it < 0f) -it else it })
            drawOval(Color(0xFFFF8A2B).copy(alpha = 0.85f), Offset(fx - u * 1.1f, y - u * 3f - fl), Size(u * 2.2f, fl))
            drawOval(Color(0xFFFFE070).copy(alpha = 0.9f), Offset(fx - u * 0.6f, y - u * 3f - fl * 0.6f), Size(u * 1.2f, fl * 0.6f))
        }
        for (k in 0 until 4) {
            val ph = (t * 0.5f + k * 0.25f) % 1f
            drawCircle(Color(0xFF55555B).copy(alpha = 0.4f * (1f - ph)), u * (1.8f + 2.5f * ph), Offset(x + sin(t + k) * u * 2f, y - u * 6f - ph * u * 12f))
        }
    }
}
