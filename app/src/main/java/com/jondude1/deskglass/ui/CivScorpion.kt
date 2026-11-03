package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Sand scorpions: a dust trail kicked up behind each scuttling beast, a glowing venom bead that drips from the raised stinger,
// and a hot shimmer on the sand underneath.
fun DrawScope.scorpionFx(cx: Float, feet: Float, w: Float, h: Float, u: Float, t: Float, seed: Float) {
    // dusty trail
    for (k in 0 until 4) {
        val ph = (t * 1.5f + k * 0.25f + seed) % 1f
        val dx = (k - 1.5f) * u * 2.4f
        drawRect(Color(0xFFD8BC84).copy(alpha = 0.7f * (1f - ph)), Offset(cx + dx, feet - ph * u * 4f), Size(u * 1.3f, u * 1.3f))
    }
    // venom bead on the stinger (top right of the sprite), falling in a drip
    val tipX = cx + w * 0.30f
    val tipY = feet - h * 0.95f
    val pulse = 0.6f + 0.4f * kotlin.math.sin(t * 5f + seed)
    drawCircle(Color(0xFF8CD84A).copy(alpha = 0.25f * pulse), radius = u * 3.2f, center = Offset(tipX, tipY))
    val ph = (t * 0.9f + seed) % 1f
    drawRect(Color(0xFFB4F060).copy(alpha = 1f - ph), Offset(tipX - u * 0.5f, tipY + ph * u * 6f), Size(u * 1.2f, u * 1.6f))
}
