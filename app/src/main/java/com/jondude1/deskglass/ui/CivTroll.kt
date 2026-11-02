package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim

// Petrified night trolls: grey crouching boulders with moss and a faint glint, left where the dawn caught them.
fun DrawScope.trollRocks(sim: CivSim, g: Geo) {
    val u = g.ps
    for (r in sim.trollRocks) {
        val x = g.ox + r[0] * g.ts
        val y = g.oy + (r[1] + 0.45f) * g.ts
        drawOval(Color.Black.copy(alpha = 0.22f), Offset(x - u * 6f, y - u * 1f), Size(u * 12f, u * 3f))
        val stone = Color(0xFF8C8A84)
        val dark = Color(0xFF5E5C58)
        val light = Color(0xFFB4B2AA)
        drawRect(dark, Offset(x - u * 5f, y - u * 6f), Size(u * 10f, u * 6f))
        drawRect(stone, Offset(x - u * 4f, y - u * 8f), Size(u * 8f, u * 4f))
        drawRect(stone, Offset(x - u * 2.5f, y - u * 11f), Size(u * 5f, u * 3f))
        drawRect(light, Offset(x - u * 3f, y - u * 7.5f), Size(u * 3f, u * 1.2f))
        drawRect(dark, Offset(x - u * 1.8f, y - u * 9.6f), Size(u * 1.2f, u * 0.9f))
        drawRect(dark, Offset(x + u * 0.8f, y - u * 9.6f), Size(u * 1.2f, u * 0.9f))
        drawRect(Color(0xFF5E8A48), Offset(x - u * 4f, y - u * 8.4f), Size(u * 2.4f, u * 0.9f))
        drawRect(Color(0xFF5E8A48), Offset(x + u * 1.5f, y - u * 6.3f), Size(u * 2.8f, u * 0.9f))
        val tw = ((g.t * 0.8f + r[0]) % 1f)
        if (tw < 0.2f) drawRect(Color.White.copy(alpha = 0.8f * (1f - tw * 5f)), Offset(x + u * 2f, y - u * 10f), Size(u * 1.4f, u * 1.4f))
    }
}
