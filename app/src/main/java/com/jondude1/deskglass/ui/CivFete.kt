package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.cos
import kotlin.math.sin

/**
 * Seasonal fetes: while a fete is on, the green carries a maypole (spring), a great bonfire (summer),
 * a laden harvest table (autumn) or a tree of lanterns (winter), strung with bunting and drifting confetti.
 */
fun DrawScope.feteSite(sim: CivSim, g: Geo) {
    if (!sim.feteOn) return
    val u = g.ps
    val x = g.ox + sim.feteX * g.ts
    val y = g.oy + (sim.feteY + 0.45f) * g.ts
    val t = g.t
    val rise = ((sim.time - sim.feteStart) / 6.0).toFloat().coerceIn(0f, 1f)
    val fade = ((sim.feteUntil - sim.time) / 6.0).toFloat().coerceIn(0f, 1f)
    val k = rise * fade
    if (k <= 0f) return
    // trodden green under the dancers
    drawOval(Color(0xFFA4C46A).copy(alpha = 0.35f * k), Offset(x - u * 19f, y - u * 5.5f), Size(u * 38f, u * 11f))
    drawOval(Color(0xFFC9B36B).copy(alpha = 0.25f * k), Offset(x - u * 11f, y - u * 3.5f), Size(u * 22f, u * 7f))
    when (sim.feteKind) {
        "maypole" -> maypole(x, y, u, t, k)
        "bonfire" -> midsummerFire(x, y, u, t, k, g.dark)
        "harvest" -> harvestTable(x, y, u, t, k, g.dark)
        else -> lanternTree(x, y, u, t, k)
    }
    bunting(x, y, u, t, k, sim.feteKind)
    val cols = when (sim.feteKind) {
        "maypole" -> listOf(Color(0xFFE85D75), Color(0xFFFFD166), Color(0xFF9BD36B), Color(0xFFB69CFF))
        "bonfire" -> listOf(Color(0xFFFFB347), Color(0xFFFF7A3C), Color(0xFFFFE08A), Color(0xFFFFF3C4))
        "harvest" -> listOf(Color(0xFFD98A2B), Color(0xFFB5502A), Color(0xFFE8B63A), Color(0xFF8FB24A))
        else -> listOf(Color(0xFFBFE3FF), Color(0xFFFFFFFF), Color(0xFFFFD9A0), Color(0xFFA7C4FF))
    }
    for (i in 0 until 12) {
        val p = (t * 0.35f + i * 0.083f) % 1f
        val px = x + sin(i * 2.3f + t * 0.6f) * u * 15f
        val py = y - u * (3f + p * 26f)
        drawRect(cols[i % 4].copy(alpha = 0.85f * (1f - p) * k), Offset(px, py), Size(u * 1.1f, u * 0.9f))
    }
}

private fun DrawScope.maypole(x: Float, y: Float, u: Float, t: Float, k: Float) {
    val h = u * 24f * k
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(x - u * 4f, y - u * 0.9f), Size(u * 8f, u * 2.2f))
    drawRect(Color(0xFF6B4A2B), Offset(x - u * 0.7f, y - h), Size(u * 1.4f, h))
    drawRect(Color(0xFF8A6238), Offset(x - u * 0.7f, y - h), Size(u * 0.5f, h))
    val cols = listOf(Color(0xFFE85D75), Color(0xFFFFD166), Color(0xFF4ECDC4), Color(0xFFB69CFF), Color(0xFF9BD36B), Color(0xFFF08A4B))
    for (i in 0 until 6) {
        val a = i * 1.0472f + t * 0.9f
        val ex = x + cos(a) * u * 10f
        val ey = y - u * (0.8f + 0.6f * sin(a * 2f))
        val mx = (x + ex) / 2f + cos(a + 1.2f) * u * 1.5f
        val my = (y - h + u * 1.5f + ey) / 2f
        drawLine(cols[i], Offset(x, y - h + u * 1.5f), Offset(mx, my), strokeWidth = u * 0.7f)
        drawLine(cols[i], Offset(mx, my), Offset(ex, ey), strokeWidth = u * 0.7f)
        drawRect(cols[i], Offset(ex - u * 0.7f, ey - u * 0.7f), Size(u * 1.4f, u * 1.4f))
    }
    // flower wreath and a golden star on top
    for (i in 0 until 8) {
        val a = i * 0.785f
        drawCircle(cols[i % 6], radius = u * 0.9f, center = Offset(x + cos(a) * u * 2.4f, y - h + u * 0.6f + sin(a) * u * 0.8f))
    }
    drawRect(Color(0xFFFFD166), Offset(x - u * 1.0f, y - h - u * 2.4f), Size(u * 2f, u * 2f))
    drawRect(Color(0xFFFFF3C4), Offset(x - u * 0.4f, y - h - u * 1.8f), Size(u * 0.8f, u * 0.8f))
}

private fun DrawScope.midsummerFire(x: Float, y: Float, u: Float, t: Float, k: Float, dark: Float) {
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(x - u * 6f, y - u * 0.9f), Size(u * 12f, u * 2.6f))
    // glow, stronger at dusk
    val glow = (0.16f + 0.3f * dark) * k
    drawCircle(Color(0xFFFF9A3C).copy(alpha = glow * 0.5f), radius = u * 26f, center = Offset(x, y - u * 6f))
    drawCircle(Color(0xFFFFC46B).copy(alpha = glow), radius = u * 14f, center = Offset(x, y - u * 6f))
    // stone ring and crossed logs
    for (i in 0 until 9) {
        val a = i * 0.698f
        drawOval(Color(0xFF8A8F98), Offset(x + cos(a) * u * 5.4f - u * 1.1f, y - u * 1f + sin(a) * u * 1.4f), Size(u * 2.2f, u * 1.4f))
    }
    for (i in 0 until 5) {
        val lx = x + (i - 2) * u * 1.6f
        drawRect(Color(0xFF5A3C22), Offset(lx - u * 0.8f, y - u * 4.6f + (i % 2) * u * 0.8f), Size(u * 1.6f, u * 4.2f))
    }
    val fl = 1f + 0.18f * sin(t * 9f)
    val tall = u * 17f * k * fl
    drawOval(Color(0xFFE8531F).copy(alpha = 0.95f), Offset(x - u * 4.6f, y - tall * 0.78f), Size(u * 9.2f, tall * 0.8f))
    drawOval(Color(0xFFFF8A2B), Offset(x - u * 3.4f, y - tall * 0.9f), Size(u * 6.8f, tall * 0.8f))
    drawOval(Color(0xFFFFC94D), Offset(x - u * 2.2f, y - tall * 0.7f), Size(u * 4.4f, tall * 0.6f))
    drawOval(Color(0xFFFFF3C4), Offset(x - u * 1.1f, y - tall * 0.42f), Size(u * 2.2f, tall * 0.34f))
    for (i in 0 until 7) {
        val p = (t * 0.7f + i * 0.143f) % 1f
        drawRect(Color(0xFFFFD27A).copy(alpha = (1f - p) * k), Offset(x + sin(i * 3.1f + t * 1.4f) * u * 5f, y - tall * 0.5f - p * u * 18f), Size(u * 0.8f, u * 0.8f))
    }
}

private fun DrawScope.harvestTable(x: Float, y: Float, u: Float, t: Float, k: Float, dark: Float) {
    val w = u * 24f
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(x - w / 2f - u, y - u * 0.8f), Size(w + u * 2f, u * 2.6f))
    // trestle legs, top, linen cloth
    drawRect(Color(0xFF5A3C22), Offset(x - w / 2f + u * 1.2f, y - u * 4.2f), Size(u * 1.3f, u * 4.2f))
    drawRect(Color(0xFF5A3C22), Offset(x + w / 2f - u * 2.5f, y - u * 4.2f), Size(u * 1.3f, u * 4.2f))
    drawRect(Color(0xFF8A6238), Offset(x - w / 2f, y - u * 5.6f), Size(w, u * 1.8f))
    drawRect(Color(0xFFF4EBD6), Offset(x - w / 2f + u * 0.6f, y - u * 5.9f), Size(w - u * 1.2f, u * 1.2f))
    for (i in 0 until 6) drawRect(Color(0xFFD9CDB0), Offset(x - w / 2f + u * (1.6f + i * 3.8f), y - u * 4.7f), Size(u * 1.4f, u * 1.8f))
    // benches
    drawRect(Color(0xFF6B4A2B), Offset(x - w / 2f + u, y - u * 2.2f), Size(w - u * 2f, u * 0.9f))
    // pumpkins, loaves, grapes, sheaf, pie
    val top = y - u * 5.9f
    drawOval(Color(0xFFE0782B), Offset(x - w / 2f + u * 2f, top - u * 3.2f), Size(u * 4.2f, u * 3.4f))
    drawRect(Color(0xFF5E7B2F), Offset(x - w / 2f + u * 3.7f, top - u * 3.9f), Size(u * 0.9f, u * 1f))
    drawOval(Color(0xFFD98A2B), Offset(x - w / 2f + u * 6.2f, top - u * 2.4f), Size(u * 3.2f, u * 2.6f))
    drawOval(Color(0xFFC98A45), Offset(x - u * 2.6f, top - u * 2.1f), Size(u * 3.6f, u * 2f))
    drawOval(Color(0xFFC98A45), Offset(x + u * 0.8f, top - u * 2.1f), Size(u * 3.6f, u * 2f))
    for (i in 0 until 5) drawCircle(Color(0xFF7A3FA0), radius = u * 0.9f, center = Offset(x + u * (5f + (i % 3) * 1.4f), top - u * (0.8f + (i / 3) * 1.4f)))
    drawRect(Color(0xFFE8B63A), Offset(x + w / 2f - u * 5f, top - u * 4.4f), Size(u * 0.8f, u * 4.4f))
    drawRect(Color(0xFFE8B63A), Offset(x + w / 2f - u * 3.6f, top - u * 4.9f), Size(u * 0.8f, u * 4.9f))
    drawRect(Color(0xFFC98A2B), Offset(x + w / 2f - u * 5.4f, top - u * 1.4f), Size(u * 3.6f, u * 0.8f))
    drawOval(Color(0xFFE8C98A), Offset(x - u * 6.6f, top - u * 1.8f), Size(u * 3.4f, u * 1.8f))
    // candles flicker as the day dims
    val fl = 0.6f + 0.4f * sin(t * 8f)
    for (cx in listOf(x - u * 9f, x + u * 8f)) {
        drawRect(Color(0xFFF4EBD6), Offset(cx, top - u * 2.4f), Size(u * 0.9f, u * 2.4f))
        drawCircle(Color(0xFFFFD166).copy(alpha = (0.5f + 0.5f * dark) * k), radius = u * 0.8f * fl, center = Offset(cx + u * 0.45f, top - u * 3f))
        if (dark > 0.1f) drawCircle(Color(0xFFFFB347).copy(alpha = 0.18f * dark * k), radius = u * 7f, center = Offset(cx + u * 0.45f, top - u * 3f))
    }
}

private fun DrawScope.lanternTree(x: Float, y: Float, u: Float, t: Float, k: Float) {
    val h = u * 20f * k
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(x - u * 6f, y - u * 0.9f), Size(u * 12f, u * 2.4f))
    drawOval(Color(0xFFF4F8FF), Offset(x - u * 6.5f, y - u * 2.2f), Size(u * 13f, u * 3.2f))
    drawRect(Color(0xFF4A3524), Offset(x - u * 1f, y - h), Size(u * 2f, h))
    val brs = listOf(-8f to 0.55f, 7f to 0.62f, -5f to 0.78f, 9f to 0.86f, -3f to 0.96f, 4f to 1.0f)
    var n = 0
    for ((dx, f) in brs) {
        val by = y - h * f
        val bx = x + dx * u
        drawLine(Color(0xFF4A3524), Offset(x, by + u * 1.4f), Offset(bx, by - u * 1.4f), strokeWidth = u * 0.9f)
        val lc = listOf(Color(0xFFFFB347), Color(0xFFFF7A8A), Color(0xFF7FD6FF), Color(0xFFFFE08A), Color(0xFFB69CFF), Color(0xFF9BE8A6))[n % 6]
        val sway = sin(t * 1.6f + n) * u * 0.6f
        val ly = by + u * 1.2f
        drawLine(Color(0xFF8A8F98), Offset(bx, by - u * 1.4f), Offset(bx + sway, ly), strokeWidth = u * 0.3f)
        val pulse = 0.75f + 0.25f * sin(t * 2.5f + n * 1.7f)
        drawCircle(lc.copy(alpha = 0.22f * pulse * k), radius = u * 5.2f, center = Offset(bx + sway, ly + u * 1.6f))
        drawRect(lc, Offset(bx + sway - u * 1.0f, ly), Size(u * 2f, u * 3.2f))
        drawRect(Color.White.copy(alpha = 0.6f * pulse), Offset(bx + sway - u * 0.4f, ly + u * 0.7f), Size(u * 0.8f, u * 1.6f))
        drawRect(Color(0xFF3A3A44), Offset(bx + sway - u * 1.2f, ly - u * 0.4f), Size(u * 2.4f, u * 0.5f))
        n++
    }
}

private fun DrawScope.bunting(x: Float, y: Float, u: Float, t: Float, k: Float, kind: String) {
    val cols = when (kind) {
        "maypole" -> listOf(Color(0xFFE85D75), Color(0xFFFFD166), Color(0xFF4ECDC4))
        "bonfire" -> listOf(Color(0xFFFF7A3C), Color(0xFFFFD166), Color(0xFFE85D75))
        "harvest" -> listOf(Color(0xFFD98A2B), Color(0xFFB5502A), Color(0xFF8FB24A))
        else -> listOf(Color(0xFF7FD6FF), Color(0xFFFFFFFF), Color(0xFFB69CFF))
    }
    val lx = x - u * 17f
    val rx = x + u * 17f
    val top = y - u * 10f
    drawRect(Color(0xFF6B4A2B), Offset(lx - u * 0.5f, top), Size(u * 1f, u * 10f))
    drawRect(Color(0xFF6B4A2B), Offset(rx - u * 0.5f, top), Size(u * 1f, u * 10f))
    val n = 9
    for (i in 0 until n) {
        val f = i / (n - 1f)
        val px = lx + (rx - lx) * f
        val py = top + u * 1.2f + sin(f * 3.1416f) * u * 3.2f
        val wave = sin(t * 3f + i) * u * 0.4f
        val flag = Path().apply {
            moveTo(px - u * 1.1f, py)
            lineTo(px + u * 1.1f, py)
            lineTo(px + wave, py + u * 2.6f)
            close()
        }
        drawPath(flag, cols[i % 3].copy(alpha = k))
    }
}
