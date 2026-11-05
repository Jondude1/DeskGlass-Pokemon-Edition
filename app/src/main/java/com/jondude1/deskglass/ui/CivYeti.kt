package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Yetis: frosty breath puffs, drifting snow flakes shaken from the fur, and the snowball they hurl at a villager
// (it flies in a high arc for under half a second, then bursts into a spray of snow where the villager stood).
fun DrawScope.yetiFx(
    cx: Float, feet: Float, ox: Float, oy: Float, ts: Float, w: Float, h: Float, u: Float, t: Float, seed: Float,
    sinceThrow: Float, lashX: Float, lashY: Float,
) {
    // breath mist from the face
    for (k in 0 until 3) {
        val ph = (t * 0.9f + k * 0.33f + seed) % 1f
        drawCircle(Color(0xFFE8F4FF).copy(alpha = 0.5f * (1f - ph)), radius = u * (0.8f + 1.6f * ph), center = Offset(cx + w * 0.18f + ph * u * 3f, feet - h * 0.68f - ph * u * 4f))
    }
    // snow shaken off the shoulders
    for (k in 0 until 4) {
        val ph = (t * 0.7f + k * 0.25f + seed * 2f) % 1f
        drawRect(Color.White.copy(alpha = 0.8f * (1f - ph)), Offset(cx - w * 0.4f + k * w * 0.27f, feet - h * 0.6f + ph * h * 0.5f), Size(u * 1.1f, u * 1.1f))
    }
    if (sinceThrow in 0f..0.45f) {
        val k = sinceThrow / 0.45f
        val sx = cx
        val sy = feet - h * 0.75f
        val tx = ox + lashX * ts
        val ty = oy + (lashY + 0.45f) * ts - ts * 0.4f
        if (k < 0.9f) {
            val bx = sx + (tx - sx) * k
            val by = sy + (ty - sy) * k - kotlin.math.sin(3.14159f * k) * ts * 1.3f
            drawCircle(Color(0xFFA9C4E0), radius = u * 2.0f, center = Offset(bx, by))
            drawCircle(Color.White, radius = u * 1.5f, center = Offset(bx, by))
        } else {
            val b = (k - 0.9f) / 0.1f
            for (j in 0 until 7) {
                val ang = j * 0.9f
                val rr = ts * (0.15f + 0.35f * b)
                drawRect(Color.White.copy(alpha = 1f - b * 0.7f), Offset(tx + kotlin.math.cos(ang) * rr, ty + kotlin.math.sin(ang) * rr * 0.6f), Size(u * 1.3f, u * 1.3f))
            }
        }
    }
}
