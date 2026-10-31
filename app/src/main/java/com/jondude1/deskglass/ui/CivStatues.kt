package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Heroes' Row: a statue for every villager who earned an epithet. Wood in the early ages, stone
// from the Village Age, gilded in the City Age. The pose shows what the hero was famous for.
// ---------------------------------------------------------------------------------------------
fun DrawScope.heroStatues(sim: CivSim, g: Geo) {
    val u = g.ps
    for (s in sim.statues.toList()) {
        val x = g.ox + s.x * g.ts
        val y = g.oy + (s.y + 0.45f) * g.ts
        val age = (sim.time - s.born).toFloat()
        val rise = if (age in 0f..4f) (age / 4f) else 1f
        statue(x, y, u, g.t, s.tier, s.kind, rise, g.dark)
    }
}

private fun DrawScope.statue(x: Float, y: Float, u: Float, t: Float, tier: Int, kind: String, rise: Float, dark: Float) {
    val base = when (tier) { 0 -> Color(0xFF8A6238); 1 -> Color(0xFFA9AEBB); else -> Color(0xFFE8C547) }
    val shade = when (tier) { 0 -> Color(0xFF5E4024); 1 -> Color(0xFF787D8C); else -> Color(0xFFB8922A) }
    val light = when (tier) { 0 -> Color(0xFFB88A55); 1 -> Color(0xFFD3D7E0); else -> Color(0xFFFFF0A8) }
    val plinth = if (tier == 2) Color(0xFFD8DCE6) else Color(0xFF8E8A82)
    val plinthShade = if (tier == 2) Color(0xFF9A9FB0) else Color(0xFF66625C)
    // soft glow at night for gilded heroes
    if (tier == 2 && dark > 0.3f) {
        drawOval(Color(0xFFFFE08A).copy(alpha = 0.18f * dark), Offset(x - u * 8f, y - u * 10f), Size(u * 16f, u * 12f))
    }
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(x - u * 4.5f, y - u * 0.9f), Size(u * 9f, u * 2.2f))
    // plinth
    drawRect(plinthShade, Offset(x - u * 3.4f, y - u * 3.2f), Size(u * 6.8f, u * 3.2f))
    drawRect(plinth, Offset(x - u * 3.4f, y - u * 3.2f), Size(u * 5.8f, u * 2.6f))
    drawRect(Color(0xFF3B3A3F), Offset(x - u * 1.5f, y - u * 2.2f), Size(u * 3f, u * 0.9f))
    val h = rise.coerceIn(0.05f, 1f)
    val top = y - u * 3.2f
    // body
    drawRect(shade, Offset(x - u * 1.6f, top - u * 6.2f * h), Size(u * 3.2f, u * 6.2f * h))
    drawRect(base, Offset(x - u * 1.6f, top - u * 6.2f * h), Size(u * 2.6f, u * 6.2f * h))
    if (h < 0.9f) return
    // head
    drawRect(shade, Offset(x - u * 1.3f, top - u * 9.4f), Size(u * 2.6f, u * 3.0f))
    drawRect(base, Offset(x - u * 1.3f, top - u * 9.4f), Size(u * 2.1f, u * 3.0f))
    drawRect(light, Offset(x - u * 1.0f, top - u * 9.2f), Size(u * 0.8f, u * 0.8f))
    when (kind) {
        "slain" -> { // sword raised high
            drawRect(shade, Offset(x + u * 1.6f, top - u * 5.6f), Size(u * 1.2f, u * 1.0f))
            drawRect(light, Offset(x + u * 2.0f, top - u * 12.4f), Size(u * 0.8f, u * 6.8f))
            drawRect(shade, Offset(x + u * 1.3f, top - u * 6.0f), Size(u * 2.4f, u * 0.7f))
        }
        "rooms" -> { // staff with a glowing rift gem
            drawRect(shade, Offset(x + u * 2.0f, top - u * 12f), Size(u * 0.8f, u * 9f))
            val p = 0.6f + 0.4f * sin(t * 3f)
            drawRect(Color(0xFFB07CFF).copy(alpha = p), Offset(x + u * 1.5f, top - u * 13.6f), Size(u * 1.8f, u * 1.8f))
        }
        "fish" -> { // a big fish held up
            drawOval(light, Offset(x + u * 1.6f, top - u * 7.4f), Size(u * 4.2f, u * 1.8f))
            drawRect(shade, Offset(x + u * 5.6f, top - u * 7.7f), Size(u * 0.9f, u * 2.4f))
        }
        "stories" -> { // an open book
            drawRect(shade, Offset(x - u * 2.4f, top - u * 5.2f), Size(u * 4.8f, u * 2.0f))
            drawRect(light, Offset(x - u * 2.1f, top - u * 5.0f), Size(u * 2.0f, u * 1.6f))
            drawRect(light, Offset(x + u * 0.1f, top - u * 5.0f), Size(u * 2.0f, u * 1.6f))
        }
        "tourney" -> { // crown and cup
            drawRect(Color(0xFFE8C547), Offset(x - u * 1.5f, top - u * 11.0f), Size(u * 3.0f, u * 1.6f))
            for (k in 0 until 3) drawRect(Color(0xFFE8C547), Offset(x - u * 1.5f + k * u * 1.1f, top - u * 12.2f), Size(u * 0.8f, u * 1.3f))
            drawRect(Color(0xFFE8C547), Offset(x + u * 1.9f, top - u * 8.2f), Size(u * 2.2f, u * 2.0f))
            drawRect(Color(0xFFE8C547), Offset(x + u * 2.7f, top - u * 6.2f), Size(u * 0.7f, u * 1.4f))
        }
        "posies" -> { // a posy of flowers
            drawRect(Color(0xFF4F9A4A), Offset(x + u * 2.2f, top - u * 6.4f), Size(u * 0.6f, u * 2.6f))
            drawRect(Color(0xFFE86A8C), Offset(x + u * 1.6f, top - u * 7.6f), Size(u * 1.8f, u * 1.4f))
            drawRect(Color(0xFFFFE08A), Offset(x + u * 2.3f, top - u * 7.1f), Size(u * 0.6f, u * 0.6f))
        }
        "carved" -> { // a little carved toy
            drawRect(Color(0xFFB5543A), Offset(x + u * 1.8f, top - u * 6.6f), Size(u * 2.0f, u * 1.8f))
            drawRect(shade, Offset(x + u * 2.5f, top - u * 7.4f), Size(u * 0.7f, u * 0.9f))
        }
        else -> { // a tool across the shoulder (builders, gatherers)
            drawRect(Color(0xFF6B4A2B), Offset(x + u * 1.9f, top - u * 11.0f), Size(u * 0.7f, u * 7.0f))
            drawRect(shade, Offset(x + u * 1.2f, top - u * 11.4f), Size(u * 2.2f, u * 1.2f))
        }
    }
    if (tier == 2) {
        val p = (t * 0.5f + x * 0.004f) % 1f
        if (p < 0.35f) {
            val a = 1f - p / 0.35f
            drawRect(Color.White.copy(alpha = 0.9f * a), Offset(x - u * 0.6f, top - u * 8f), Size(u * 1.2f, u * 1.2f))
            drawRect(Color.White.copy(alpha = 0.6f * a), Offset(x - u * 1.8f, top - u * 7.4f), Size(u * 3.6f, u * 0.4f))
        }
    }
}
