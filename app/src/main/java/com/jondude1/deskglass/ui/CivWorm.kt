package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jondude1.deskglass.sim.CivSim

// Giant burrowing worms: a moving ridge of loose earth while it tunnels, a flurry of dirt as it rears up, and craters where it erupted.
fun DrawScope.wormMound(cx: Float, cy: Float, u: Float, t: Float, seed: Float, about: Boolean) {
    val shake = if (about) kotlin.math.sin(t * 40f + seed) * u * 0.8f else 0f
    val x = cx + shake
    drawOval(Color(0xFF3A2A18).copy(alpha = 0.35f), Offset(x - u * 7f, cy - u * 2f), Size(u * 14f, u * 4.5f))
    drawOval(Color(0xFF7A5A36), Offset(x - u * 5.5f, cy - u * 3.6f), Size(u * 11f, u * 5f))
    drawOval(Color(0xFF9A7448), Offset(x - u * 3.6f, cy - u * 5f), Size(u * 7.2f, u * 3.6f))
    drawOval(Color(0xFFB88C58), Offset(x - u * 1.6f, cy - u * 5.4f), Size(u * 3.2f, u * 1.6f))
    // clods tossed up as it ploughs along
    for (k in 0 until 4) {
        val ph = (t * 1.6f + k * 0.25f + seed) % 1f
        val dx = (k - 1.5f) * u * 2.6f
        drawRect(Color(0xFF6B4A2B).copy(alpha = 1f - ph), Offset(x + dx, cy - u * 4f - ph * u * (if (about) 12f else 7f)), Size(u * 1.4f, u * 1.4f))
    }
    if (about) drawOval(Color(0xFFFFE9A8).copy(alpha = 0.5f), Offset(x - u * 8f, cy - u * 3.4f), Size(u * 16f, u * 5.5f), style = Stroke(width = u * 0.6f))
}

fun DrawScope.wormDust(cx: Float, feet: Float, u: Float, t: Float, seed: Float) {
    for (k in 0 until 5) {
        val ph = (t * 1.2f + k * 0.2f + seed) % 1f
        val dx = (k - 2f) * u * 3f
        drawRect(Color(0xFF8A6A44).copy(alpha = 0.8f * (1f - ph)), Offset(cx + dx, feet - ph * u * 6f), Size(u * 1.3f, u * 1.3f))
    }
}

fun DrawScope.wormCraters(sim: CivSim, g: Geo) {
    val cs = try { sim.craters.toList() } catch (e: Exception) { return }
    val u = g.ps
    for (c in cs) {
        val age = (sim.time - c[2]).toFloat()
        if (age > 150f) continue
        val fade = if (age > 110f) ((150f - age) / 40f).coerceIn(0f, 1f) else 1f
        val x = g.ox + c[0] * g.ts
        val y = g.oy + (c[1] + 0.3f) * g.ts
        drawOval(Color(0xFF2A1E12).copy(alpha = 0.55f * fade), Offset(x - u * 6f, y - u * 2.4f), Size(u * 12f, u * 4.8f))
        drawOval(Color(0xFF5A4028).copy(alpha = 0.9f * fade), Offset(x - u * 4.6f, y - u * 1.6f), Size(u * 9.2f, u * 3.4f))
        drawOval(Color(0xFF8A6A44).copy(alpha = 0.9f * fade), Offset(x - u * 7f, y - u * 2.8f), Size(u * 14f, u * 5.6f), style = Stroke(width = u * 1.2f))
        drawRect(Color(0xFFA09A8C).copy(alpha = fade), Offset(x + u * 5.6f, y - u * 2.6f), Size(u * 1.6f, u * 1.4f))
        drawRect(Color(0xFFA09A8C).copy(alpha = fade), Offset(x - u * 6.8f, y + u * 0.4f), Size(u * 1.4f, u * 1.2f))
    }
}
