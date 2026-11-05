package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Landmarks the scouts found beyond the hills: standing stones, a moonpool, a sunken ruin, an
// elder grove and a crystal cave. They stay on the map, and glow at night.
// ---------------------------------------------------------------------------------------------
fun DrawScope.landmarkSites(sim: CivSim, g: Geo) {
    val u = g.ps
    for (l in sim.landmarks.toList()) {
        val x = g.ox + l.x * g.ts
        val y = g.oy + (l.y + 0.9f) * g.ts
        val fresh = (sim.time - l.found) < 20.0
        when (l.kind) {
            "stones" -> standingStones(x, y, u, g.t, g.dark)
            "moonpool" -> moonpool(x, y, u, g.t, g.dark)
            "ruin" -> sunkenRuin(x, y, u, g.t)
            "grove" -> elderGrove(x, y, u, g.t)
            else -> crystalCave(x, y, u, g.t, g.dark)
        }
        if (fresh) {
            val a = (0.5f + 0.5f * sin(g.t * 5f)) * 0.5f
            drawCircle(Color(0xFFFFF2A8).copy(alpha = a), u * 11f, Offset(x, y - u * 4f))
        }
    }
}

private fun DrawScope.shade(x: Float, y: Float, w: Float, u: Float) {
    drawOval(Color(0x44000000), Offset(x - w / 2f, y - u * 1.2f), Size(w, u * 2.4f))
}

private fun DrawScope.standingStones(x: Float, y: Float, u: Float, t: Float, dark: Float) {
    shade(x, y, u * 18f, u)
    val rock = Color(0xFF8A8F96)
    val hi = Color(0xFFB4B9C0)
    val lo = Color(0xFF5C6168)
    val xs = floatArrayOf(-6f, -2f, 2.5f, 6.5f)
    val hs = floatArrayOf(6f, 9f, 8f, 5f)
    for (k in 0 until 4) {
        val cx = x + xs[k] * u
        val h = hs[k] * u
        drawRect(rock, Offset(cx - u * 1.4f, y - h), Size(u * 2.8f, h))
        drawRect(hi, Offset(cx - u * 1.4f, y - h), Size(u * 0.9f, h))
        drawRect(lo, Offset(cx + u * 0.6f, y - h + u), Size(u * 0.8f, h - u))
        drawRect(Color(0xFF6E9A5A), Offset(cx - u * 1.4f, y - u * 1.2f), Size(u * 2.8f, u * 1.2f))
    }
    // faint runes that pulse, brighter after dark
    val a = (0.25f + 0.55f * dark.coerceIn(0f, 1f)) * (0.6f + 0.4f * sin(t * 2f))
    for (k in 1..2) drawRect(Color(0xFF9FE8FF).copy(alpha = a), Offset(x + xs[k] * u - u * 0.5f, y - hs[k] * u * 0.6f), Size(u * 1.0f, u * 1.6f))
    if (dark > 0.3f) drawCircle(Color(0xFF9FE8FF).copy(alpha = 0.10f * dark), u * 10f, Offset(x, y - u * 4f))
}

private fun DrawScope.moonpool(x: Float, y: Float, u: Float, t: Float, dark: Float) {
    val glow = 0.15f + 0.35f * dark.coerceIn(0f, 1f)
    drawOval(Color(0xFF6D7480), Offset(x - u * 8f, y - u * 3.6f), Size(u * 16f, u * 6.4f))
    drawOval(Color(0xFF2C5C86), Offset(x - u * 7f, y - u * 3f), Size(u * 14f, u * 5f))
    drawOval(Color(0xFFBFE4FF).copy(alpha = 0.55f), Offset(x - u * 5f, y - u * 2.4f), Size(u * 10f, u * 3.2f))
    for (k in 0 until 3) {
        val ph = (t * 0.5f + k * 0.33f) % 1f
        drawOval(Color.White.copy(alpha = (1f - ph) * 0.5f), Offset(x - u * (1f + ph * 5f), y - u * (1.6f + ph * 0.8f)), Size(u * (2f + ph * 10f), u * (1f + ph * 2f)))
    }
    drawCircle(Color(0xFFCFEFFF).copy(alpha = glow), u * 11f, Offset(x, y - u * 1.5f))
    val tw = 0.5f + 0.5f * sin(t * 3f)
    drawRect(Color.White.copy(alpha = tw), Offset(x + u * 2f, y - u * 6f - tw * u), Size(u * 0.8f, u * 0.8f))
}

private fun DrawScope.sunkenRuin(x: Float, y: Float, u: Float, t: Float) {
    shade(x, y, u * 18f, u)
    val stone = Color(0xFF9A8F7C)
    val dk = Color(0xFF6B6354)
    val moss = Color(0xFF5E8A4A)
    // broken arch
    drawRect(stone, Offset(x - u * 7f, y - u * 8f), Size(u * 3f, u * 8f))
    drawRect(dk, Offset(x - u * 4.6f, y - u * 8f), Size(u * 0.6f, u * 8f))
    drawRect(stone, Offset(x + u * 3.5f, y - u * 5f), Size(u * 3f, u * 5f))
    drawRect(stone, Offset(x - u * 7f, y - u * 10f), Size(u * 8f, u * 2.4f))
    drawRect(dk, Offset(x - u * 7f, y - u * 8.2f), Size(u * 8f, u * 0.6f))
    // fallen blocks
    drawRect(stone, Offset(x - u * 1f, y - u * 2.4f), Size(u * 4f, u * 2.4f))
    drawRect(dk, Offset(x - u * 1f, y - u * 0.8f), Size(u * 4f, u * 0.8f))
    drawRect(stone, Offset(x + u * 1.6f, y - u * 4.2f), Size(u * 2.4f, u * 1.8f))
    // moss and a glint where something is buried
    drawRect(moss, Offset(x - u * 7f, y - u * 3f), Size(u * 3f, u * 1.2f))
    drawRect(moss, Offset(x + u * 3.5f, y - u * 1.6f), Size(u * 3f, u * 1.2f))
    val tw = 0.5f + 0.5f * sin(t * 3.4f)
    drawRect(Color(0xFFFFE27A).copy(alpha = tw), Offset(x + u * 0.5f, y - u * 0.6f), Size(u * 1.0f, u * 1.0f))
}

private fun DrawScope.elderGrove(x: Float, y: Float, u: Float, t: Float) {
    shade(x, y, u * 20f, u)
    val sway = sin(t * 1.3f) * u * 0.5f
    val trunk = Color(0xFF6B4A2B)
    val leafA = Color(0xFF3E7A3A)
    val leafB = Color(0xFF5FA050)
    val xs = floatArrayOf(-6f, 0f, 6f)
    val hs = floatArrayOf(8f, 12f, 9f)
    for (k in 0 until 3) {
        val cx = x + xs[k] * u
        val h = hs[k] * u
        drawRect(trunk, Offset(cx - u * 1.2f, y - h * 0.6f), Size(u * 2.4f, h * 0.6f))
        drawOval(leafA, Offset(cx - u * 5f + sway, y - h - u * 3f), Size(u * 10f, u * 7.5f))
        drawOval(leafB, Offset(cx - u * 3.6f + sway, y - h - u * 2.4f), Size(u * 6f, u * 4f))
        // golden fruit
        drawRect(Color(0xFFFFC94A), Offset(cx - u * 2f + sway, y - h + u * 0.6f), Size(u * 1.0f, u * 1.0f))
        drawRect(Color(0xFFFFC94A), Offset(cx + u * 1.6f + sway, y - h - u * 0.6f), Size(u * 1.0f, u * 1.0f))
    }
}

private fun DrawScope.crystalCave(x: Float, y: Float, u: Float, t: Float, dark: Float) {
    shade(x, y, u * 18f, u)
    val rock = Color(0xFF5E5B66)
    val rockHi = Color(0xFF7C7886)
    drawOval(rock, Offset(x - u * 8f, y - u * 9f), Size(u * 16f, u * 12f))
    drawOval(rockHi, Offset(x - u * 6.5f, y - u * 8.4f), Size(u * 7f, u * 5f))
    drawRect(Color.Black.copy(alpha = 0.85f), Offset(x - u * 2.6f, y - u * 5.2f), Size(u * 5.2f, u * 5.2f))
    val pulse = 0.55f + 0.45f * sin(t * 2.4f)
    drawCircle(Color(0xFFB07CFF).copy(alpha = (0.10f + 0.30f * dark.coerceIn(0f, 1f)) * pulse), u * 12f, Offset(x, y - u * 4f))
    val cols = intArrayOf(0xFFC9A3FF.toInt(), 0xFF8EE0FF.toInt(), 0xFFE7C7FF.toInt())
    val xs = floatArrayOf(-3.5f, 0f, 3.5f)
    val hs = floatArrayOf(3.6f, 5.2f, 3f)
    for (k in 0 until 3) {
        val c = Color(cols[k])
        val cx = x + xs[k] * u
        drawRect(c, Offset(cx - u * 0.8f, y - hs[k] * u), Size(u * 1.6f, hs[k] * u))
        drawRect(Color.White.copy(alpha = 0.6f * pulse), Offset(cx - u * 0.8f, y - hs[k] * u), Size(u * 0.6f, u * 1.0f))
    }
}
