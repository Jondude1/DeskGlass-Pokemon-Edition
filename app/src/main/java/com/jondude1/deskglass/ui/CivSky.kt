package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.DAY_LEN
import com.jondude1.deskglass.sim.GH
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.Sky
import kotlin.math.sin

/** The sky seen from the ground: real cloud cover (fronts drift over, rain and storms close the sky, a drought leaves it bare),
 *  lumpy cloud shadows sliding over the land, warm sunlit patches in the gaps, an overcast wash under heavy cloud and
 *  slanted sunbeams with floating motes when the sun breaks through broken cloud. Pure drawing; the cover is a function of
 *  time and weather (sim/Sky.kt). */

private fun sh(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

private object SkyBook {
    var cover = -1f
    var lastT = -1f
}

internal fun DrawScope.skyLight(sim: CivSim, g: Geo) {
    val t = g.t
    val ts = g.ts
    val book = SkyBook
    val target = Sky.cover(sim)
    val dt = if (book.lastT < 0f || t < book.lastT) 1f else (t - book.lastT).coerceIn(0f, 0.2f)
    book.lastT = t
    book.cover = if (book.cover < 0f) target else book.cover + (target - book.cover) * (dt * 0.5f).coerceAtMost(1f)
    val c = book.cover
    val day = (1f - g.dark / 0.55f).coerceIn(0f, 1f)
    if (day < 0.02f) return

    // lumpy cloud shadows drifting over the land
    val n = Sky.cloudCount(c)
    val span = GW * ts + 16f * ts
    val a = (0.045f + 0.075f * c) * day
    for (k in 0 until n) {
        val seed = sh(k, 17)
        val speed = ts * (0.09f + (seed % 7) * 0.012f)
        val cx = g.ox - 8f * ts + ((t * speed + k * span / n) % span)
        val cy = g.oy + ((seed shr 4) % 100) / 100f * GH * ts
        val w = ts * (5f + (seed % 6))
        val h = w * 0.34f
        val col = Color(0xFF10141C).copy(alpha = a)
        drawOval(col, Offset(cx - w * 0.5f, cy - h * 0.15f), Size(w * 0.55f, h * 0.8f))
        drawOval(col, Offset(cx - w * 0.25f, cy - h * 0.45f), Size(w * 0.6f, h * 1.0f))
        drawOval(col, Offset(cx + w * 0.05f, cy - h * 0.25f), Size(w * 0.5f, h * 0.9f))
        drawOval(col, Offset(cx - w * 0.35f, cy + h * 0.05f), Size(w * 0.85f, h * 0.55f))
    }

    // sunlit patches in the gaps between clouds
    if (c < 0.55f && day > 0.4f) {
        val warm = Color(0xFFFFF0B0).copy(alpha = 0.05f * day * (1f - c / 0.55f))
        for (k in 0 until 3) {
            val seed = sh(k, 91)
            val px = g.ox + ((t * ts * 0.07f + k * span / 3f) % span) - 6f * ts
            val py = g.oy + ((seed shr 3) % 100) / 100f * GH * ts
            drawOval(warm, Offset(px - ts * 4f, py - ts * 1.4f), Size(ts * 8f, ts * 2.8f))
        }
    }

    // heavy cloud dims the whole scene
    if (c > 0.7f) drawRect(Color(0xFF2A3042).copy(alpha = 0.10f * ((c - 0.7f) / 0.3f) * day), Offset.Zero, size)

    // slanted sunbeams with drifting motes
    val frac = (sim.time % DAY_LEN) / DAY_LEN
    val b = Sky.beams(c, frac) * day
    if (b > 0.03f) {
        val sun = sunFor(sim, g.dark)
        val slant = sun.shear * size.height * 0.22f
        val w = size.width
        val hgt = size.height
        for (j in 0 until 5) {
            val pulse = 0.6f + 0.4f * sin(t * 0.55f + j * 1.7f)
            val bx = w * (0.08f + 0.2f * j) + sin(t * 0.17f + j * 2.3f) * ts * 0.8f
            val bw = ts * (1.1f + (j % 3) * 0.55f)
            val p = Path()
            p.moveTo(bx, 0f)
            p.lineTo(bx + bw, 0f)
            p.lineTo(bx + bw * 2.4f + slant, hgt * 0.85f)
            p.lineTo(bx + bw * 0.6f + slant, hgt * 0.85f)
            p.close()
            val col = Color(0xFFFFE9A8).copy(alpha = 0.09f * b * pulse)
            drawPath(p, Brush.verticalGradient(listOf(col, Color.Transparent), 0f, hgt * 0.85f))
            for (m in 0 until 3) {
                val ph = (t * 0.06f + m * 0.33f + j * 0.17f) % 1f
                val mx = bx + bw * 0.5f + (bw * 1.5f + slant) * ph * 0.9f * 0.85f + sin(t * 0.9f + m + j) * ts * 0.15f
                drawCircle(Color(0xFFFFF4C8).copy(alpha = 0.35f * b * pulse * (1f - ph)), radius = ts * 0.06f, center = Offset(mx, hgt * 0.85f * ph))
            }
        }
    }
}
