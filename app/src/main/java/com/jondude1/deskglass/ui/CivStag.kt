package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Antler stags: a red warning lane that flashes along the line of the coming charge while the stag paws the ground,
// speed lines and kicked-up leaves while it charges, and a ring of dizzy stars plus snorting steam once its antlers are stuck.
fun DrawScope.stagFx(cx: Float, feet: Float, w: Float, h: Float, u: Float, t: Float, seed: Float, aux: Float, phase: Float, angle: Float, ts: Float) {
    val cy = feet - h * 0.45f
    val ca = kotlin.math.cos(angle)
    val sa = kotlin.math.sin(angle)
    if (aux > 0f) {
        // warning lane: pulsing red stripe along the charge, with arrow chevrons
        val pulse = 0.35f + 0.25f * kotlin.math.sin(t * 18f)
        drawLine(Color(0xFFFF4A3A).copy(alpha = pulse), Offset(cx, feet), Offset(cx + ca * ts * 6f, feet + sa * ts * 6f), strokeWidth = u * 2.2f)
        for (k in 1..4) {
            val d = ts * (1.2f * k)
            drawRect(Color(0xFFFFB0A0).copy(alpha = pulse + 0.15f), Offset(cx + ca * d - u, feet + sa * d - u), Size(u * 2f, u * 2f))
        }
        // pawing dust at the front hooves
        for (k in 0 until 3) {
            val ph = (t * 3f + k * 0.33f + seed) % 1f
            drawRect(Color(0xFFC8A872).copy(alpha = 0.8f * (1f - ph)), Offset(cx - w * 0.30f + k * u * 1.5f, feet - ph * u * 5f), Size(u * 1.3f, u * 1.3f))
        }
        drawRect(Color(0xFFFFE070), Offset(cx - u * 0.6f, cy - h * 0.75f), Size(u * 1.2f, u * 3f))
        drawRect(Color(0xFFFFE070), Offset(cx - u * 0.6f, cy - h * 0.75f + u * 3.8f), Size(u * 1.2f, u * 1.2f))
    } else if (aux < 0f) {
        // speed lines and tumbling leaves behind a charging stag
        for (k in 0 until 5) {
            val ph = (t * 6f + k * 0.2f + seed) % 1f
            val back = u * 4f + ph * ts * 1.6f
            val off = (k - 2) * u * 2.2f
            val x0 = cx - ca * back - sa * off
            val y0 = feet - h * 0.35f - sa * back + ca * off
            drawLine(Color(0xFFFFF0C8).copy(alpha = 0.6f * (1f - ph)), Offset(x0, y0), Offset(x0 - ca * u * 5f, y0 - sa * u * 5f), strokeWidth = u * 0.9f)
        }
        for (k in 0 until 4) {
            val ph = (t * 4f + k * 0.25f + seed) % 1f
            drawRect(Color(if (k % 2 == 0) 0xFFD0802C else 0xFFB8442A).copy(alpha = 1f - ph), Offset(cx - ca * ts * ph * 2f + k * u, feet - u * 2f - ph * u * 6f), Size(u * 1.4f, u * 1.4f))
        }
    } else if (phase > 0f) {
        // dizzy stars circling the stuck antlers, and steam from the nostrils
        for (k in 0 until 3) {
            val a = t * 5f + k * 2.094f
            val sx = cx + kotlin.math.cos(a) * w * 0.28f
            val sy = feet - h * 1.05f + kotlin.math.sin(a) * u * 2f
            drawRect(Color(0xFFFFE070), Offset(sx - u, sy - u), Size(u * 2f, u * 2f))
        }
        val ph = (t * 1.6f + seed) % 1f
        drawCircle(Color.White.copy(alpha = 0.5f * (1f - ph)), radius = u * (1.5f + 2f * ph), center = Offset(cx - w * 0.38f, feet - h * 0.5f - ph * u * 5f))
    }
}
