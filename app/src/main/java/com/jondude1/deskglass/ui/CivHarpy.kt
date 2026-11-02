package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Rift harpies: tawny feathers drift down behind a harpy, and a pale screech ring flashes when it stoops on a dive.
fun DrawScope.harpyFeathers(cx: Float, cy: Float, u: Float, t: Float, seed: Float, circling: Boolean) {
    for (k in 0 until 3) {
        val ph = (t * 0.7f + k * 0.34f + seed * 0.13f) % 1f
        val fx = cx + (k - 1) * u * 3f + kotlin.math.sin(ph * 6.28f + k) * u * 2f
        val fy = cy + u * 4f + ph * u * 14f
        val c = if (k == 1) Color(0xFFF4EEDC) else Color(0xFFB98A5A)
        drawRect(c.copy(alpha = 0.85f * (1f - ph)), Offset(fx, fy), Size(u * 1.6f, u * 0.7f))
    }
    if (!circling) {
        val ph = (t * 3f + seed) % 1f
        drawCircle(Color(0xFFFFF2C8).copy(alpha = 0.35f * (1f - ph)), radius = u * (3f + 7f * ph), center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 0.6f))
    }
}
