package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.DAY_LEN
import com.jondude1.deskglass.sim.NightSky
import kotlin.math.abs
import kotlin.math.sin

/** The three night sky wonders, drawn only while the sim says the sky is putting on a show (sim.skyShow()):
 *  a meteor shower, a tall rayed aurora, and a blood-moon eclipse. */

private fun hsh(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

fun DrawScope.skyShowFx(sim: CivSim, g: Geo) {
    val kind = sim.skyShow()
    if (kind == NightSky.NONE) return
    val frac = (sim.time % DAY_LEN) / DAY_LEN
    val env = NightSky.envelope(frac)
    val nightK = (g.dark / 0.55f).coerceIn(0f, 1f)
    val k = env * nightK
    if (k <= 0.01f) return
    when (kind) {
        NightSky.METEOR -> meteorShower(g, k)
        NightSky.AURORA -> auroraRays(g, k)
        else -> bloodMoon(g, k, frac)
    }
}

private fun DrawScope.meteorShower(g: Geo, k: Float) {
    val w = size.width
    val h = size.height
    val ps = g.ps
    val lanes = 9
    for (lane in 0 until lanes) {
        val period = 1.7f + (lane % 4) * 0.45f
        val tt = g.t + lane * 0.83f
        val n = (tt / period).toInt()
        val ph = (tt % period) / period
        if (ph > 0.34f) continue
        val p = ph / 0.34f
        val h1 = (hsh(n, lane + 3) % 1000) / 1000f
        val h2 = (hsh(n, lane + 41) % 1000) / 1000f
        val sx = w * (0.05f + 0.85f * h1)
        val sy = h * (0.02f + 0.38f * h2)
        val dx = -w * (0.16f + 0.10f * h2)
        val dy = h * (0.10f + 0.07f * h1)
        val hx = sx + dx * p
        val hy = sy + dy * p
        val fade = (1f - p * p) * k
        val tail = 0.30f + 0.2f * h1
        val tint = if (lane % 3 == 0) Color(0xFFFFE9A0) else Color(0xFFBFD8FF)
        drawLine(tint.copy(alpha = 0.22f * fade), Offset(hx - dx * tail, hy - dy * tail), Offset(hx, hy), strokeWidth = ps * 3.2f)
        drawLine(tint.copy(alpha = 0.6f * fade), Offset(hx - dx * tail * 0.7f, hy - dy * tail * 0.7f), Offset(hx, hy), strokeWidth = ps * 1.4f)
        drawCircle(Color.White.copy(alpha = 0.95f * fade), radius = ps * 1.6f, center = Offset(hx, hy))
        drawCircle(tint.copy(alpha = 0.25f * fade), radius = ps * 4.5f, center = Offset(hx, hy))
    }
    // a faint sparkle haze while the shower peaks
    for (i in 0 until 26) {
        val x = w * ((hsh(i, 5) % 1000) / 1000f)
        val y = h * 0.42f * ((hsh(i, 9) % 1000) / 1000f)
        val tw = (sin(g.t * 2.2f + i * 1.7f) * 0.5f + 0.5f)
        drawRect(Color.White.copy(alpha = 0.5f * tw * k), Offset(x, y), Size(ps * 1.1f, ps * 1.1f))
    }
}

private fun DrawScope.auroraRays(g: Geo, k: Float) {
    val w = size.width
    val h = size.height
    val cols = 64
    val cw = w / cols + 1f
    val tints = listOf(Color(0xFF6BFFB0), Color(0xFF58E0D0), Color(0xFF7FA8FF), Color(0xFFC08BFF))
    for (band in 0 until 4) {
        val base = h * (0.10f + 0.10f * band)
        for (i in 0 until cols) {
            val x = i * (w / cols)
            val wave = sin(x * 0.010f + g.t * 0.30f + band * 1.7f) * h * 0.06f + sin(x * 0.027f - g.t * 0.45f + band) * h * 0.025f
            val top = base + wave
            // rays flare up and down independently, like curtains rippling
            val flare = 0.55f + 0.45f * sin(g.t * 0.8f + i * 0.33f + band * 2.3f)
            val ht = h * (0.20f + 0.14f * flare)
            val a = (0.17f + 0.15f * flare) * k
            val c = tints[band]
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = a), c.copy(alpha = a * 0.5f), Color.Transparent), startY = top, endY = top + ht),
                Offset(x, top), Size(cw, ht), blendMode = BlendMode.Plus,
            )
            // a brighter ray core on every third column
            if (i % 3 == 0) drawRect(c.copy(alpha = a * 0.9f), Offset(x + cw * 0.35f, top + ht * 0.25f), Size(cw * 0.3f, ht * 0.5f), blendMode = BlendMode.Plus)
        }
    }
    // a green glow washes the horizon
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF6BFFB0).copy(alpha = 0.07f * k)), startY = h * 0.55f, endY = h),
        Offset(0f, h * 0.55f), Size(w, h * 0.45f), blendMode = BlendMode.Plus,
    )
}

private fun DrawScope.bloodMoon(g: Geo, k: Float, frac: Double) {
    val w = size.width
    val h = size.height
    val ps = g.ps
    val r = minOf(w, h) * 0.075f
    val cx = w * 0.80f
    val cy = h * 0.15f
    // totality in the middle of the window: 0 = bright moon, 1 = deep red
    val u = ((frac - NightSky.START) / (NightSky.END - NightSky.START)).toFloat()
    val tot = (1f - abs(u - 0.5f) / 0.32f).coerceIn(0f, 1f)
    val red = Color(0.70f, 0.20f, 0.12f)
    val pale = Color(0.92f, 0.94f, 1.0f)
    val mix = Color(pale.red + (red.red - pale.red) * tot, pale.green + (red.green - pale.green) * tot, pale.blue + (red.blue - pale.blue) * tot)
    // halo
    for (i in 0 until 4) drawCircle(mix.copy(alpha = (0.05f + 0.02f * i) * k), r * (3.2f - i * 0.6f), Offset(cx, cy))
    // the disc and a few craters
    drawCircle(mix.copy(alpha = 0.95f * k), r, Offset(cx, cy))
    val crater = Color(red.red * 0.6f, red.green * 0.6f, red.blue * 0.6f)
    drawCircle(crater.copy(alpha = 0.25f * k), r * 0.22f, Offset(cx - r * 0.35f, cy - r * 0.25f))
    drawCircle(crater.copy(alpha = 0.22f * k), r * 0.15f, Offset(cx + r * 0.30f, cy + r * 0.20f))
    drawCircle(crater.copy(alpha = 0.18f * k), r * 0.10f, Offset(cx + r * 0.05f, cy - r * 0.45f))
    // Earth's shadow slides across, then away again
    val sh = (1f - tot) * (if (u < 0.5f) -1f else 1f) * r * 1.9f
    drawCircle(Color(0xFF0B0714).copy(alpha = 0.78f * k * (1f - tot * 0.7f)), r * 1.02f, Offset(cx + sh, cy))
    // a red tint over the whole night while it is dark
    drawRect(Color(0xFFB02A1A).copy(alpha = 0.07f * tot * k), Offset(0f, 0f), size)
    // stars come out around the dimmed moon
    for (i in 0 until 18) {
        val x = w * ((hsh(i, 17) % 1000) / 1000f)
        val y = h * 0.4f * ((hsh(i, 23) % 1000) / 1000f)
        val tw = (sin(g.t * 1.6f + i * 2.1f) * 0.5f + 0.5f)
        drawRect(Color.White.copy(alpha = 0.55f * tw * tot * k), Offset(x, y), Size(ps * 1.1f, ps * 1.1f))
    }
}
