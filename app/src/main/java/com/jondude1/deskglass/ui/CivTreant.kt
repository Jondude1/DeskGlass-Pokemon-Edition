package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Heartwood treant: leaves drift from its crown; when it marks a victim (aux > 0) a dark patch of soil swells at the marked spot
// with root spikes pushing up out of it; while it stands rooted after a burst (phase > 0) its eyes and bark glow amber.
fun DrawScope.treantFx(cx: Float, feet: Float, ox: Float, oy: Float, ts: Float, w: Float, h: Float, u: Float, t: Float, seed: Float, aux: Float, phase: Float, markX: Float, markY: Float) {
    for (k in 0 until 4) {
        val ph = (t * 0.45f + k * 0.25f + seed) % 1f
        val x = cx + kotlin.math.sin(ph * 6.28f + k) * w * 0.45f
        val y = feet - h * (1.0f - ph * 0.9f)
        drawRect(Color(0xFF6FA84A).copy(alpha = 0.8f * (1f - ph)), Offset(x, y), androidx.compose.ui.geometry.Size(u * 1.4f, u * 1.0f))
    }
    if (aux > 0f) {
        val prog = (1f - aux / 1.3f).coerceIn(0f, 1f)
        val mx = ox + markX * ts
        val my = oy + (markY + 0.45f) * ts
        drawCircle(Color(0xFF2A1808).copy(alpha = 0.35f + 0.3f * prog), radius = ts * (0.45f + 0.9f * prog), center = Offset(mx, my))
        drawCircle(Color(0xFFFF5A3A).copy(alpha = 0.10f + 0.15f * prog), radius = ts * 1.5f, center = Offset(mx, my))
        for (k in 0 until 7) {
            val ang = k * 0.9f + seed
            val len = ts * (0.25f + 0.7f * prog) * (0.7f + 0.3f * kotlin.math.sin(t * 20f + k))
            drawLine(Color(0xFF6B4423), Offset(mx, my), Offset(mx + kotlin.math.cos(ang) * len, my + kotlin.math.sin(ang) * len * 0.6f - len * 0.5f * prog), strokeWidth = u * 1.6f)
        }
    }
    if (phase > 0f) {
        val a = 0.30f + 0.2f * kotlin.math.sin(t * 9f)
        drawCircle(Color(0xFFFFC857).copy(alpha = a), radius = w * 0.55f, center = Offset(cx, feet - h * 0.5f))
        drawCircle(Color(0xFF5A3A1E).copy(alpha = 0.5f), radius = w * 0.4f, center = Offset(cx, feet - u * 0.5f))
    }
}
