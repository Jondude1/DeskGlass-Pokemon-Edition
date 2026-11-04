package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Stormlings: rain streaks and crackling sparks around the thundercloud, and while it charges a flickering yellow
// target ring on the ground (with a jagged line down to it) that tells villagers where the bolt will land.
// A spent stormling (after every third bolt) sags, loses its sparks and drips grey.
fun DrawScope.stormlingFx(cx: Float, feet: Float, w: Float, h: Float, u: Float, t: Float, seed: Float, charge: Float, tx: Float, ty: Float, ts: Float, spent: Boolean) {
    val top = feet - h
    // rain streaks under the cloud
    for (k in 0 until 5) {
        val ph = (t * 2.2f + k * 0.2f + seed) % 1f
        val x0 = cx - w * 0.36f + k * w * 0.18f
        drawLine(Color(0xFFB8D4F0).copy(alpha = 0.7f * (1f - ph)), Offset(x0, feet + ph * u * 7f), Offset(x0 - u * 0.6f, feet + ph * u * 7f + u * 2.2f), strokeWidth = u * 0.7f)
    }
    if (!spent) {
        // sparks hopping along the cloud's edge
        for (k in 0 until 3) {
            val a = t * 7f + k * 2.1f + seed
            val sx = cx + kotlin.math.cos(a) * w * 0.52f
            val sy = top + h * 0.45f + kotlin.math.sin(a * 1.3f) * h * 0.3f
            if (kotlin.math.sin(t * 19f + k * 3f) > 0f) drawRect(Color(0xFFFFE878), Offset(sx - u * 0.6f, sy - u * 0.6f), Size(u * 1.4f, u * 1.4f))
        }
    }
    if (charge > 0f) {
        val p = (1f - charge / 1.3f).coerceIn(0f, 1f)
        val flick = 0.45f + 0.35f * kotlin.math.sin(t * 28f)
        val r = ts * 1.7f
        drawCircle(Color(0xFFFFE070).copy(alpha = 0.10f + 0.16f * p), radius = r, center = Offset(tx, ty))
        drawCircle(Color(0xFFFFE070).copy(alpha = flick), radius = r * (1.0f - 0.35f * p), center = Offset(tx, ty), style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 1.3f))
        drawCircle(Color(0xFFFFFFFF).copy(alpha = flick), radius = r * 0.25f * (0.4f + p), center = Offset(tx, ty))
        // a faint jagged line from the cloud to the mark
        var px = cx
        var py = feet
        val steps = 5
        for (i in 1..steps) {
            val f = i / steps.toFloat()
            val nx = cx + (tx - cx) * f + (if (i < steps) kotlin.math.sin(t * 40f + i * 2f) * u * 2f else 0f)
            val ny = feet + (ty - feet) * f
            drawLine(Color(0xFFFFF4B0).copy(alpha = 0.18f + 0.3f * p * flick), Offset(px, py), Offset(nx, ny), strokeWidth = u * 0.8f)
            px = nx; py = ny
        }
    }
}
