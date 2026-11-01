package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// The village graveyard: a small fenced plot where everyone who dies is laid to rest under a
// headstone (rounded stone, wooden cross or flat slab). Mourners leave flowers that stay fresh
// for a while, and on dark nights pale wisps drift above the old graves.
// ---------------------------------------------------------------------------------------------
fun DrawScope.graveyard(sim: CivSim, g: Geo) {
    val list = sim.graves.toList()
    if (list.isEmpty() || sim.graveX < 0) return
    val u = g.ps
    val ts = g.ts
    val rows = ((list.size + 5) / 6).coerceIn(1, 4)
    val left = g.ox + (sim.graveX - 0.25f) * ts
    val right = g.ox + (sim.graveX + 6.25f) * ts
    val top = g.oy + (sim.graveY - 0.25f) * ts
    val bottom = g.oy + (sim.graveY + rows + 0.1f) * ts
    // fence: two rails and a post every tile
    val rail = Color(0xFF7A5A3A)
    val railShade = Color(0xFF52391F)
    drawRect(railShade, Offset(left, top + u * 1.2f), Size(right - left, u * 0.9f))
    drawRect(railShade, Offset(left, bottom - u * 1.6f), Size(right - left, u * 0.9f))
    var px = left
    while (px <= right + 1f) {
        drawRect(railShade, Offset(px - u * 0.2f, top - u * 0.4f), Size(u * 1.4f, u * 3.4f))
        drawRect(rail, Offset(px, top - u * 0.4f), Size(u * 1.0f, u * 3.2f))
        drawRect(railShade, Offset(px - u * 0.2f, bottom - u * 3.0f), Size(u * 1.4f, u * 3.4f))
        drawRect(rail, Offset(px, bottom - u * 3.0f), Size(u * 1.0f, u * 3.2f))
        px += ts
    }
    drawRect(railShade, Offset(left, top), Size(u * 1.2f, bottom - top))
    drawRect(rail, Offset(left, top), Size(u * 0.9f, bottom - top))
    drawRect(railShade, Offset(right - u * 1.2f, top), Size(u * 1.2f, bottom - top))
    drawRect(rail, Offset(right - u * 1.2f, top), Size(u * 0.9f, bottom - top))
    for (gr in list) {
        val cx = g.ox + gr.x * ts
        val gy = g.oy + (gr.y + 0.4f) * ts
        val h = (gr.name.hashCode() and 0x7fffffff)
        val fresh = (sim.time - gr.tribute) in 0.0..500.0
        // mound of earth
        drawOval(Color.Black.copy(alpha = 0.2f), Offset(cx - u * 4.8f, gy - u * 0.6f), Size(u * 9.6f, u * 2.4f))
        drawOval(Color(0xFF6B4A2E), Offset(cx - u * 4f, gy - u * 1.8f), Size(u * 8f, u * 3.2f))
        drawOval(Color(0xFF8A6240), Offset(cx - u * 3.4f, gy - u * 2.0f), Size(u * 6.2f, u * 2.4f))
        when (h % 3) {
            0 -> { // rounded stone
                drawRect(Color(0xFF6E727C), Offset(cx - u * 2.6f, gy - u * 8.6f), Size(u * 5.2f, u * 7.4f))
                drawRect(Color(0xFF9EA3AF), Offset(cx - u * 2.6f, gy - u * 8.6f), Size(u * 4.2f, u * 7.4f))
                drawRect(Color(0xFF9EA3AF), Offset(cx - u * 1.6f, gy - u * 9.6f), Size(u * 3.2f, u * 1.2f))
                drawRect(Color(0xFF50535C), Offset(cx - u * 1.2f, gy - u * 6.6f), Size(u * 2.4f, u * 0.7f))
                drawRect(Color(0xFF50535C), Offset(cx - u * 1.2f, gy - u * 5.0f), Size(u * 1.8f, u * 0.7f))
            }
            1 -> { // wooden cross
                drawRect(Color(0xFF52391F), Offset(cx - u * 1.0f, gy - u * 9.0f), Size(u * 2.4f, u * 8.2f))
                drawRect(Color(0xFF9A7448), Offset(cx - u * 1.0f, gy - u * 9.0f), Size(u * 1.8f, u * 8.2f))
                drawRect(Color(0xFF52391F), Offset(cx - u * 3.2f, gy - u * 6.8f), Size(u * 6.8f, u * 2.0f))
                drawRect(Color(0xFF9A7448), Offset(cx - u * 3.2f, gy - u * 6.8f), Size(u * 6.2f, u * 1.5f))
            }
            else -> { // flat slab with a small rune
                drawRect(Color(0xFF5E6470), Offset(cx - u * 3.6f, gy - u * 6.0f), Size(u * 7.2f, u * 5.0f))
                drawRect(Color(0xFF8B92A0), Offset(cx - u * 3.6f, gy - u * 6.0f), Size(u * 6.4f, u * 4.4f))
                drawRect(Color(0xFF3E424C), Offset(cx - u * 0.5f, gy - u * 5.2f), Size(u * 1.0f, u * 2.8f))
                drawRect(Color(0xFF3E424C), Offset(cx - u * 1.5f, gy - u * 4.4f), Size(u * 3.0f, u * 0.8f))
            }
        }
        // moss on the old stones
        if (sim.day() - gr.day > 6) drawRect(Color(0xFF5E8F4A).copy(alpha = 0.8f), Offset(cx - u * 2.4f, gy - u * 2.2f), Size(u * 1.8f, u * 1.0f))
        // fresh flowers from mourners
        if (fresh) {
            val c1 = when (h % 4) { 0 -> Color(0xFFFF8FB8); 1 -> Color(0xFFFFE066); 2 -> Color(0xFFFFFFFF); else -> Color(0xFFB07CFF) }
            drawRect(Color(0xFF3F8744), Offset(cx - u * 3.4f, gy - u * 1.6f), Size(u * 1.0f, u * 1.4f))
            drawRect(c1, Offset(cx - u * 3.6f, gy - u * 2.6f), Size(u * 1.6f, u * 1.6f))
            drawRect(Color(0xFF3F8744), Offset(cx + u * 2.2f, gy - u * 1.6f), Size(u * 1.0f, u * 1.4f))
            drawRect(Color(0xFFFFFFFF), Offset(cx + u * 2.0f, gy - u * 2.6f), Size(u * 1.6f, u * 1.6f))
        }
        // wisps over the graves on dark nights
        if (g.dark > 0.45f && h % 3 != 1) {
            val ph = ((g.t * 0.25f + (h % 10) * 0.1f) % 1f)
            val a = (sin(ph * 3.1416f)).coerceIn(0f, 1f) * 0.55f * g.dark
            val wx = cx + sin(g.t * 1.3f + h) * u * 1.6f
            val wy = gy - u * 4f - ph * u * 9f
            drawOval(Color(0xFFBFE8FF).copy(alpha = a * 0.35f), Offset(wx - u * 2.2f, wy - u * 2.2f), Size(u * 4.4f, u * 4.4f))
            drawRect(Color(0xFFE8F8FF).copy(alpha = a), Offset(wx - u * 0.6f, wy - u * 0.6f), Size(u * 1.2f, u * 1.2f))
        }
    }
}
