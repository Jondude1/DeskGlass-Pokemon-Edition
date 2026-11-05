package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Dust devils: grit and straw orbit the funnel in widening rings, a swirl of dust on the ground at its foot,
// and when the whirl is tired (thinning out, open to attack) the orbit slows and fades.
fun DrawScope.dustDevilFx(cx: Float, feet: Float, w: Float, h: Float, u: Float, t: Float, seed: Float, tired: Boolean) {
    val spin = if (tired) 2.2f else 7f
    val count = if (tired) 7 else 14
    for (k in 0 until count) {
        val f = k.toFloat() / count
        val ang = t * spin + k * 2.4f + seed
        val rad = w * (0.18f + 0.42f * f)
        val x = cx + kotlin.math.cos(ang) * rad
        val y = feet - h * (0.12f + 0.85f * f) + kotlin.math.sin(ang) * rad * 0.18f
        val c = if (k % 3 == 0) Color(0xFF8A6A30) else Color(0xFFE2C887)
        drawRect(c.copy(alpha = if (tired) 0.45f else 0.85f), Offset(x, y), Size(u * 1.3f, u * 1.3f))
    }
    // a few bright straws and leaves tossed higher up
    if (!tired) for (k in 0 until 3) {
        val ang = t * 5f + k * 2.1f + seed * 3f
        val x = cx + kotlin.math.cos(ang) * w * 0.7f
        val y = feet - h * 1.05f - kotlin.math.abs(kotlin.math.sin(ang * 0.7f)) * u * 5f
        drawRect(Color(0xFFB8A040), Offset(x, y), Size(u * 2.2f, u * 0.8f))
    }
    // dust cloud at its foot
    for (k in 0 until 4) {
        val ph = (t * 0.8f + k * 0.25f + seed) % 1f
        drawCircle(Color(0xFFD9BE86).copy(alpha = 0.4f * (1f - ph)), radius = u * (1.6f + 2.4f * ph), center = Offset(cx + (k - 1.5f) * u * 2.2f, feet - u * ph * 2f))
    }
}
