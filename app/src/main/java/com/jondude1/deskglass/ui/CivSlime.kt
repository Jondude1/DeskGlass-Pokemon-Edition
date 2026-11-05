package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Gloom slime: a faint green glow under it, bubbles rising through the jelly, drips falling to a trail of goo,
// and a sheen that flashes across the top. A big slime that is hurt also leaks glints (it is about to split).
fun DrawScope.slimeFx(cx: Float, feet: Float, w: Float, h: Float, u: Float, t: Float, seed: Float, big: Boolean, hpFrac: Float) {
    // glow on the ground
    drawCircle(Color(0xFF9CFF9C).copy(alpha = 0.10f + 0.04f * kotlin.math.sin(t * 3f + seed)), radius = w * 0.75f, center = Offset(cx, feet - u))
    // goo puddle trail
    drawOval(Color(0xFF3F8A52).copy(alpha = 0.35f), Offset(cx - w * 0.55f, feet - u * 1.2f), Size(w * 1.1f, u * 2.2f))
    // bubbles rising inside the body
    val n = if (big) 5 else 2
    for (k in 0 until n) {
        val ph = (t * 0.55f + k * 0.21f + seed * 0.37f) % 1f
        val bx = cx + kotlin.math.sin(k * 2.7f + seed) * w * 0.25f
        val by = feet - h * (0.12f + 0.7f * ph)
        drawCircle(Color(0xFFE4FFE0).copy(alpha = 0.7f * (1f - ph)), radius = u * (0.7f + 0.5f * (1f - ph)), center = Offset(bx, by))
    }
    // drips falling off the body
    for (k in 0 until 2) {
        val ph = (t * 0.9f + k * 0.5f + seed) % 1f
        val dx = cx + (k - 0.5f) * w * 0.5f
        drawRect(Color(0xFF7BC96A).copy(alpha = 0.8f * (1f - ph)), Offset(dx, feet - h * 0.3f + h * 0.3f * ph), Size(u * 0.9f, u * 1.6f))
    }
    // top sheen
    val sh = (kotlin.math.sin(t * 2f + seed) * 0.5f + 0.5f)
    drawRect(Color.White.copy(alpha = 0.25f * sh), Offset(cx - w * 0.25f, feet - h * 0.85f), Size(w * 0.18f, u * 1.2f))
    // a hurt big slime shivers and sparkles: it is about to split
    if (big && hpFrac < 0.7f) {
        for (k in 0 until 4) {
            val a = t * 9f + k * 1.6f + seed
            drawRect(Color(0xFFFFFFB0).copy(alpha = 0.8f), Offset(cx + kotlin.math.cos(a) * w * 0.5f, feet - h * 0.5f + kotlin.math.sin(a) * h * 0.4f), Size(u, u))
        }
    }
}
