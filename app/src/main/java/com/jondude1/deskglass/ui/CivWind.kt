package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.GH
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.Wind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The wind you can see: gusts roll in and out, pale streaks sweep over the land, and the air carries things that fit
 *  the season (autumn leaves, spring petals, summer seeds, snow flurries, drought dust). The stronger the gust, the more
 *  is carried and the faster it flies. Pure drawing; the gust is a function of time (sim/Wind.kt). */

private fun wh(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

private object WindBook {
    var phase = 0f
    var lastT = -1f
}

private const val COUNT = 120

internal fun windPush(t: Float): Float = Wind.gust(t.toDouble())

internal fun DrawScope.windBorne(sim: CivSim, g: Geo, vx0: Int, vx1: Int, vy0: Int, vy1: Int) {
    val t = g.t
    val ts = g.ts
    val ps = g.ps
    val book = WindBook
    val dt = if (book.lastT < 0f || t < book.lastT) 0f else (t - book.lastT).coerceIn(0f, 0.1f)
    book.lastT = t
    val gust = windPush(t)
    book.phase += dt * (0.35f + 2.2f * gust)
    val ph = book.phase
    val cargo = Wind.cargo(sim)

    // gust streaks: long pale lines sweep across the screen
    if (g.dark < 0.5f && gust > 0.15f) {
        val w = size.width
        val h = size.height
        for (j in 0 until 9) {
            val seed = wh(j, 31)
            val len = ts * (2.5f + (seed % 5) * 0.7f)
            val x = ((ph * ts * 5f + j * w / 9f) % (w + len * 2f)) - len
            val y = ((seed shr 3) % 100) / 100f * h + sin(ph * 0.8f + j) * ts * 0.3f
            val a = 0.16f * gust * (1f - g.dark / 0.5f)
            drawLine(Color.White.copy(alpha = a), Offset(x, y), Offset(x + len, y + len * 0.06f), strokeWidth = ps * 0.7f)
            drawLine(Color.White.copy(alpha = a * 0.7f), Offset(x + len * 0.75f, y + len * 0.05f), Offset(x + len * 0.95f, y - ps * 1.5f), strokeWidth = ps * 0.6f)
        }
    }

    if (cargo == Wind.NONE) return
    val shown = (COUNT * (0.28f + 0.72f * gust)).toInt()
    val fall = when (cargo) { Wind.LEAVES -> 0.5f; Wind.PETALS -> 0.35f; Wind.SEEDS -> -0.12f; Wind.SNOW -> 1.1f; else -> 0.02f }
    val night = g.dark > 0.55f
    for (k in 0 until shown) {
        val s = wh(k, 977)
        val speed = 0.8f + (s % 7) * 0.22f
        var x = ((s % 1000) / 1000f * (GW + 12) + ph * speed * 2.6f) % (GW + 12f) - 6f
        var y = (((s shr 10) % 1000) / 1000f * GH + ph * fall * (0.6f + (s % 5) * 0.2f)) % GH.toFloat()
        if (y < 0f) y += GH.toFloat()
        val spin = ph * (2.5f + (s % 6) * 0.9f) + k
        y += sin(ph * 1.7f + k * 1.3f) * (0.25f + 0.5f * gust)
        if (x < vx0 - 1 || x > vx1 + 1 || y < vy0 - 1 || y > vy1 + 1) continue
        val px = g.ox + x * ts
        val py = g.oy + y * ts
        val lift = ps * (3f + (s % 4) * 1.5f)
        val flip = abs(sin(spin))
        when (cargo) {
            Wind.LEAVES -> {
                val c = when (s % 4) { 0 -> Color(0xFFE0752B); 1 -> Color(0xFFC9A227); 2 -> Color(0xFF9A4A22); else -> Color(0xFFB8321F) }
                drawOval(Color.Black.copy(alpha = 0.10f), Offset(px - ps, py + lift), Size(ps * 2.2f, ps * 0.8f))
                drawRect(c.copy(alpha = 0.95f), Offset(px - ps * 0.8f, py - ps * 0.6f * flip), Size(ps * (0.8f + 1.3f * flip), ps * 1.3f))
                if (gust > 0.35f) drawLine(c.copy(alpha = 0.3f), Offset(px - ps * 4f * gust, py), Offset(px, py), strokeWidth = ps * 0.6f)
            }
            Wind.PETALS -> {
                val c = when (s % 3) { 0 -> Color(0xFFFFC2DC); 1 -> Color(0xFFFFFFFF); else -> Color(0xFFFFE680) }
                drawRect(c.copy(alpha = 0.95f), Offset(px, py), Size(ps * (0.7f + 0.9f * flip), ps * 1.1f))
                if (gust > 0.4f) drawLine(c.copy(alpha = 0.3f), Offset(px - ps * 3.5f * gust, py), Offset(px, py), strokeWidth = ps * 0.5f)
            }
            Wind.SEEDS -> {
                val a = if (night) 0.35f else 0.85f
                val bob = sin(spin * 0.4f) * ps
                drawLine(Color.White.copy(alpha = a), Offset(px, py + bob), Offset(px - ps * 1.2f, py + bob - ps * 1.8f), strokeWidth = ps * 0.5f)
                drawLine(Color.White.copy(alpha = a), Offset(px, py + bob), Offset(px + ps * 1.2f, py + bob - ps * 1.8f), strokeWidth = ps * 0.5f)
                drawLine(Color.White.copy(alpha = a), Offset(px, py + bob), Offset(px, py + bob - ps * 2.2f), strokeWidth = ps * 0.5f)
                drawRect(Color(0xFF8A7A52).copy(alpha = a), Offset(px - ps * 0.3f, py + bob), Size(ps * 0.7f, ps * 0.9f))
            }
            Wind.SNOW -> {
                val a = 0.9f
                drawRect(Color.White.copy(alpha = a), Offset(px, py), Size(ps * 1.2f, ps * 1.2f))
                drawLine(Color.White.copy(alpha = 0.35f * (0.4f + gust)), Offset(px - ps * 5f * (0.4f + gust), py - ps * 1.2f * (0.4f + gust)), Offset(px, py), strokeWidth = ps * 0.6f)
            }
            else -> {
                drawCircle(Color(0xFFC9AC72).copy(alpha = 0.22f + 0.15f * gust), radius = ps * (1.2f + (s % 3) * 0.5f), center = Offset(px, py))
                drawLine(Color(0xFFC9AC72).copy(alpha = 0.18f), Offset(px - ps * 6f * (0.4f + gust), py), Offset(px, py), strokeWidth = ps * 1.2f)
            }
        }
    }
}
