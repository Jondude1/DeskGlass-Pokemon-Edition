package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Fallen stars: a bright streak crosses the night sky and crashes near the village, leaving a glowing crater of starmetal. */

private val STAR_WHITE = Color(0xFFFFFFFF)
private val STAR_GOLD = Color(0xFFFFE9A0)
private val STAR_BLUE = Color(0xFF9FD0FF)
private val STAR_VIOLET = Color(0xFFB79BFF)

/** The crater with its shards, drawn on the ground layer. */
fun DrawScope.starCrater(sim: CivSim, g: Geo) {
    if (sim.starShards <= 0 || sim.time < sim.starFallAt) return
    val u = g.ps
    val cx = g.ox + sim.starX * g.ts
    val cy = g.oy + sim.starY * g.ts
    val since = (sim.time - sim.starFallAt).toFloat()
    val life = (sim.starUntil - sim.time).toFloat()
    val fade = min(min(1f, since / 1.5f), min(1f, life / 20f)).coerceIn(0f, 1f)
    val pulse = 0.75f + 0.25f * sin(g.t * 2.4f)
    val glowK = 0.5f + (g.dark / 0.55f).coerceIn(0f, 1f) * 0.9f
    // scorched ring and bowl
    drawOval(Color(0xFF2B2018).copy(alpha = 0.55f * fade), Offset(cx - u * 11f, cy - u * 4.6f), Size(u * 22f, u * 9.6f))
    drawOval(Color(0xFF3F3026).copy(alpha = 0.8f * fade), Offset(cx - u * 9.2f, cy - u * 4.0f), Size(u * 18.4f, u * 7.8f))
    drawOval(Color(0xFF1A130E).copy(alpha = 0.9f * fade), Offset(cx - u * 7.4f, cy - u * 3.2f), Size(u * 14.8f, u * 6.2f))
    // soft glow rising from the bowl
    for (k in 0 until 4) {
        val r = u * (16f - k * 3.4f)
        drawCircle(STAR_GOLD.copy(alpha = (0.05f + 0.035f * k) * glowK * pulse * fade), r, Offset(cx, cy - u * 1.5f))
    }
    // shards: one crystal per remaining piece
    val n = sim.starShards.coerceAtMost(8)
    for (i in 0 until n) {
        val a = i * 2.399f + 0.7f
        val rr = 1.5f + (i % 3) * 1.5f
        val sx = cx + cos(a) * u * rr * 1.5f
        val sy = cy + sin(a) * u * rr * 0.55f - u * 1.2f
        val tw = 0.6f + 0.4f * sin(g.t * 3.1f + i * 1.7f)
        val h = u * (3.4f + (i % 2) * 1.2f)
        // crystal body
        drawRect(STAR_BLUE.copy(alpha = fade), Offset(sx - u * 0.9f, sy - h), Size(u * 1.8f, h))
        drawRect(STAR_WHITE.copy(alpha = fade), Offset(sx - u * 0.9f, sy - h), Size(u * 0.8f, h))
        drawRect(STAR_VIOLET.copy(alpha = fade), Offset(sx + u * 0.1f, sy - h * 0.7f), Size(u * 0.8f, h * 0.7f))
        drawRect(STAR_GOLD.copy(alpha = fade), Offset(sx - u * 0.9f, sy - h - u * 0.9f), Size(u * 1.8f, u * 0.9f))
        // glint
        drawRect(STAR_WHITE.copy(alpha = 0.9f * tw * fade), Offset(sx - u * 0.4f, sy - h - u * 2.4f), Size(u * 0.8f, u * 0.8f))
        drawRect(STAR_WHITE.copy(alpha = 0.5f * tw * fade), Offset(sx - u * 1.6f, sy - h - u * 1.6f + u * 0.1f), Size(u * 3.2f, u * 0.6f))
        drawRect(STAR_WHITE.copy(alpha = 0.5f * tw * fade), Offset(sx - u * 0.3f, sy - h - u * 3.2f), Size(u * 0.6f, u * 2.4f))
    }
    // motes drifting upward
    for (k in 0 until 7) {
        val p = (g.t * 0.28f + k * 0.143f) % 1f
        val mx = cx + sin(p * 6f + k * 2.1f) * u * (5f + k % 3)
        val my = cy - u * (2f + p * 17f)
        drawRect((if (k % 2 == 0) STAR_GOLD else STAR_WHITE).copy(alpha = 0.85f * (1f - p) * fade), Offset(mx, my), Size(u * 0.9f, u * 0.9f))
    }
}

/** The streak across the sky, the flash and the shock ring at impact. Drawn over the world. */
fun DrawScope.starStreak(sim: CivSim, g: Geo) {
    val ix = g.ox + sim.starX * g.ts
    val iy = g.oy + sim.starY * g.ts
    val u = g.ps
    if (sim.starIncoming()) {
        val left = (sim.starFallAt - sim.time).toFloat()
        val p = (1f - left / 3.5f).coerceIn(0f, 1f)
        val e = p * p
        val sx = ix - g.ts * 11f
        val sy = iy - g.ts * 17f
        val hx = sx + (ix - sx) * e
        val hy = sy + (iy - sy) * e
        // sky tint while it falls
        drawRect(STAR_BLUE.copy(alpha = 0.05f * p), Offset(0f, 0f), size)
        // trail: fading glowing segments behind the head
        val segs = 22
        for (k in 0 until segs) {
            val q = k / segs.toFloat()
            val tp = max(0f, e - q * 0.32f * (0.3f + p))
            val tx = sx + (ix - sx) * tp
            val ty = sy + (iy - sy) * tp
            val a = (1f - q) * 0.8f
            val w = u * (4.6f * (1f - q) + 0.8f)
            drawRect(STAR_GOLD.copy(alpha = a * 0.45f), Offset(tx - w, ty - w), Size(w * 2f, w * 2f))
            drawRect((if (k % 3 == 0) STAR_BLUE else STAR_WHITE).copy(alpha = a), Offset(tx - w * 0.5f, ty - w * 0.5f), Size(w, w))
        }
        // head
        drawCircle(STAR_GOLD.copy(alpha = 0.18f), u * 11f, Offset(hx, hy))
        drawCircle(STAR_WHITE.copy(alpha = 0.35f), u * 6f, Offset(hx, hy))
        drawCircle(STAR_WHITE, u * 2.8f, Offset(hx, hy))
        return
    }
    val since = (sim.time - sim.starFallAt).toFloat()
    if (since < 0f || since > 2.2f || sim.starsFallen <= 0) return
    // impact flash, ring and sparks
    val f = (1f - since / 0.7f).coerceIn(0f, 1f)
    if (f > 0f) drawRect(STAR_WHITE.copy(alpha = 0.32f * f), Offset(0f, 0f), size)
    val rp = (since / 2.2f).coerceIn(0f, 1f)
    drawOval(STAR_GOLD.copy(alpha = 0.7f * (1f - rp)), Offset(ix - g.ts * 7f * rp, iy - g.ts * 3f * rp), Size(g.ts * 14f * rp, g.ts * 6f * rp), style = Stroke(width = u * 1.6f))
    drawOval(STAR_WHITE.copy(alpha = 0.5f * (1f - rp)), Offset(ix - g.ts * 4.6f * rp, iy - g.ts * 2f * rp), Size(g.ts * 9.2f * rp, g.ts * 4f * rp), style = Stroke(width = u * 1.1f))
    for (k in 0 until 14) {
        val a = k * 0.449f
        val d = since * g.ts * (2.6f + (k % 4) * 0.6f)
        val sx = ix + cos(a) * d
        val sy = iy - since * g.ts * (3.4f - (k % 3)) + since * since * g.ts * 4.6f + sin(a) * d * 0.25f
        drawRect((if (k % 2 == 0) STAR_GOLD else STAR_WHITE).copy(alpha = (1f - rp).coerceIn(0f, 1f)), Offset(sx, sy), Size(u * 1.2f, u * 1.2f))
    }
}
