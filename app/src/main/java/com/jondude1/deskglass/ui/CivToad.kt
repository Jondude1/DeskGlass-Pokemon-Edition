package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Bog toads: a pulsing throat sac, wet ripples where they sit, and the long sticky tongue that lashes out at a victim
// (it flashes for a third of a second from the toad's mouth to where the villager stood).
fun DrawScope.toadFx(
    cx: Float, feet: Float, ox: Float, oy: Float, ts: Float, w: Float, h: Float, u: Float, t: Float, seed: Float,
    hopping: Boolean, sinceLash: Float, lashX: Float, lashY: Float, px: Float, py: Float,
) {
    if (!hopping) {
        // damp ring under a squatting toad
        val ph = (t * 0.8f + seed) % 1f
        drawCircle(Color(0xFFBFE4F5).copy(alpha = 0.35f * (1f - ph)), radius = w * (0.3f + 0.4f * ph), center = Offset(cx, feet), style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 0.5f))
    }
    // croaking throat sac
    val pulse = 0.5f + 0.5f * kotlin.math.sin(t * 7f + seed * 3f)
    drawCircle(Color(0xFFE8D890).copy(alpha = 0.55f), radius = u * (1.2f + 1.4f * pulse), center = Offset(cx, feet - h * 0.32f))
    if (sinceLash in 0f..0.35f) {
        val k = sinceLash / 0.35f
        val mx = cx
        val my = feet - h * 0.55f
        val tx = ox + lashX * ts
        val ty = oy + (lashY + 0.45f) * ts - ts * 0.4f
        val ex = mx + (tx - mx) * (1f - k * 0.5f)
        val ey = my + (ty - my) * (1f - k * 0.5f)
        drawLine(Color(0xFFE0607A).copy(alpha = 1f - k * 0.6f), Offset(mx, my), Offset(ex, ey), strokeWidth = u * 1.1f)
        drawCircle(Color(0xFFFF8FA8).copy(alpha = 1f - k * 0.6f), radius = u * 1.4f, center = Offset(ex, ey))
    }
}
