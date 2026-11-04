package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Siege rams: while the raider escort stands, a pale shield-wall arc shimmers in front of the ram; wheels kick up dust as it rolls,
// and right after a slam splinters and sparks fly from the iron head.
fun DrawScope.ramFx(cx: Float, feet: Float, w: Float, h: Float, u: Float, t: Float, seed: Float, phase: Float, cool: Float, shielded: Boolean) {
    if (shielded && phase == 0f) {
        val pulse = 0.16f + 0.08f * kotlin.math.sin(t * 5f + seed)
        drawCircle(Color(0xFFBFE6FF).copy(alpha = pulse), radius = w * 0.62f, center = Offset(cx - w * 0.15f, feet - h * 0.5f))
        for (k in 0 until 5) {
            val y = feet - h * (0.15f + 0.17f * k)
            drawRect(Color(0xFFE8F6FF).copy(alpha = pulse + 0.2f), Offset(cx - w * 0.62f, y), Size(u * 1.2f, u * 2.2f))
        }
    }
    for (k in 0 until 3) {
        val ph = (t * 2.4f + k * 0.33f + seed) % 1f
        drawRect(Color(0xFFC8B08A).copy(alpha = 0.7f * (1f - ph)), Offset(cx + w * 0.30f + ph * u * 4f, feet - u * 1.5f - ph * u * 3f), Size(u * 1.6f, u * 1.6f))
    }
    if (cool > 1.9f) {
        for (k in 0 until 6) {
            val ph = ((2.6f - cool) / 0.7f).coerceIn(0f, 1f)
            val a = k * 1.05f + seed
            drawRect(Color(if (k % 2 == 0) 0xFFFFD36A else 0xFFB87C48).copy(alpha = 1f - ph), Offset(cx - w * 0.45f + kotlin.math.cos(a) * u * 6f * ph, feet - h * 0.45f + kotlin.math.sin(a) * u * 6f * ph), Size(u * 1.4f, u * 1.4f))
        }
    }
}
