package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.PI
import kotlin.math.sin

private val THEME_COLORS = longArrayOf(0xFFD9453A, 0xFFF2A7C8, 0xFF8E4FD9, 0xFF5FB8E8)

/**
 * Critter Pageant dressing at the campfire: a red parade rug, a swag of bunting between two posts, a small
 * pennant for every entrant, and (for about half a minute after the crowning) a big rosette with streamers
 * and confetti in the theme colour. Pure drawing from CivSim state.
 */
internal fun DrawScope.drawPageant(sim: CivSim, cx: Float, baseY: Float, ps: Float, ts: Float, t: Float) {
    val winning = sim.time - sim.pageantWinAt < 40.0
    val on = sim.pageantDay() || winning
    if (!on) return
    val col = Color(THEME_COLORS[(if (winning) sim.pageantWinTheme else sim.pageantTheme()).coerceIn(0, 3)].toInt())
    // rug in front of the fire
    val rw = ts * 2.6f
    drawRect(Color(0xFF9E2B25), Offset(cx - rw / 2f, baseY + ps * 1.2f), Size(rw, ps * 3.2f))
    drawRect(Color(0xFFE8C547), Offset(cx - rw / 2f, baseY + ps * 1.2f), Size(rw, ps * 0.5f))
    drawRect(Color(0xFFE8C547), Offset(cx - rw / 2f, baseY + ps * 3.9f), Size(rw, ps * 0.5f))
    // bunting between two posts
    val x0 = cx - rw * 0.6f
    val x1 = cx + rw * 0.6f
    val yTop = baseY - ts * 1.1f
    drawRect(Color(0xFF6B4A2B), Offset(x0 - ps * 0.4f, yTop), Size(ps * 0.8f, ts * 1.1f + ps * 2f))
    drawRect(Color(0xFF6B4A2B), Offset(x1 - ps * 0.4f, yTop), Size(ps * 0.8f, ts * 1.1f + ps * 2f))
    for (k in 0 until 9) {
        val f = k / 8f
        val fx = x0 + (x1 - x0) * f
        val fy = yTop + sin(f * PI.toFloat()) * ps * 2.4f + sin(t * 2.6f + k) * ps * 0.2f
        val c = if (k % 2 == 0) col else Color(0xFFF4EBD8)
        drawRect(c, Offset(fx - ps * 0.5f, fy), Size(ps * 1.1f, ps * 1.6f))
    }
    // a pennant on the rug for every entry
    val n = sim.pageantEntries.size
    for (k in 0 until n) {
        val px = cx + (k - (n - 1) / 2f) * ps * 3.2f
        val py = baseY + ps * 2.6f
        val bob = sin(t * 3f + k * 1.7f) * ps * 0.3f
        drawRect(Color(0xFFF4EBD8), Offset(px - ps * 0.2f, py - ps * 2.2f + bob), Size(ps * 0.4f, ps * 2.2f))
        drawRect(col, Offset(px + ps * 0.2f, py - ps * 2.2f + bob), Size(ps * 1.4f, ps * 0.9f))
    }
    if (winning) {
        val age = (sim.time - sim.pageantWinAt).toFloat()
        val fade = (1f - age / 40f).coerceIn(0f, 1f)
        val rx = cx
        val ry = baseY - ts * 1.6f + sin(t * 2f) * ps * 0.4f
        // streamers
        drawRect(col.copy(alpha = fade), Offset(rx - ps * 1.6f, ry), Size(ps * 1.1f, ps * 3.6f))
        drawRect(Color(0xFFE8C547).copy(alpha = fade), Offset(rx + ps * 0.5f, ry), Size(ps * 1.1f, ps * 3.6f))
        // rosette: ring of petals around a gold centre
        for (k in 0 until 10) {
            val a = k / 10f * 2f * PI.toFloat()
            drawCircle(col.copy(alpha = fade), radius = ps * 1.1f, center = Offset(rx + kotlin.math.cos(a) * ps * 2.1f, ry + sin(a) * ps * 2.1f))
        }
        drawCircle(Color(0xFFFFF1B0).copy(alpha = fade), radius = ps * 1.9f, center = Offset(rx, ry))
        drawCircle(Color(0xFFE8C547).copy(alpha = fade), radius = ps * 1.0f, center = Offset(rx, ry))
        // confetti
        for (k in 0 until 18) {
            val ph = (t * 0.6f + k * 0.173f) % 1f
            val ang = k * 2.4f
            val x = rx + sin(ang) * ps * 9f * ph
            val y = ry + ps * 4f - ph * ps * 11f + ph * ph * ps * 14f
            val c = Color(THEME_COLORS[k % 4].toInt()).copy(alpha = fade * (1f - ph * 0.6f))
            drawRect(c, Offset(x, y), Size(ps * 0.9f, ps * 0.6f))
        }
    }
}
