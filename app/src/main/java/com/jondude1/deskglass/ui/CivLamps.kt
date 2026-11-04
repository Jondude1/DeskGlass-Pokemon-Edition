package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.Lamps
import kotlin.math.cos
import kotlin.math.sin

/** Street lamps beside the worn paths (posts by day, lit one by one at dusk), warm pools round glowing buildings and
 *  moths round the flames. Pure drawing; the choice of tiles lives in sim/Lamps.kt. */

/** Lamp posts, drawn on the ground layer so walkers pass in front of them. */
fun DrawScope.lampPosts(sim: CivSim, g: Geo) {
    val ts = g.ts
    val ps = g.ps
    for (i in Lamps.sites(sim)) {
        val px = g.ox + ((i % GW) + 0.5f) * ts
        val py = g.oy + ((i / GW) + 0.9f) * ts
        if (px < -ts || py < -ts || px > size.width + ts || py > size.height + ts * 2f) continue
        val f = Lamps.lit(g.dark, i)
        drawRect(Color(0xFF3A3028).copy(alpha = 0.25f), Offset(px - ps * 2f, py - ps * 0.6f), Size(ps * 4f, ps * 1.2f))
        drawRect(Color(0xFF4B4038), Offset(px - ps * 0.5f, py - ps * 6f), Size(ps * 1f, ps * 6f))
        drawRect(Color(0xFF2E2620), Offset(px - ps * 1.4f, py - ps * 7.6f), Size(ps * 2.8f, ps * 0.8f))
        val head = if (f > 0f) Color(0xFFFFE08A) else Color(0xFFC9C3B0)
        drawRect(head, Offset(px - ps * 1f, py - ps * 6.8f), Size(ps * 2f, ps * 2.2f))
    }
}

/** Warm light on top of the dark: pools round every glowing building and lamp, plus moths. */
fun DrawScope.lampGlow(sim: CivSim, g: Geo) {
    val dark = g.dark
    if (dark <= 0.08f) return
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    val murk = if (sim.raining() || sim.foggy()) 1.3f else 1f
    for (b in sim.buildings) {
        val lg = Lamps.lightOf(b.type) ?: continue
        val cx = g.ox + (b.x + 0.5f) * ts
        val cy = g.oy + (b.y + 0.5f) * ts
        if (cx < -ts * 5f || cy < -ts * 5f || cx > size.width + ts * 5f || cy > size.height + ts * 5f) continue
        val flick = 1f + 0.05f * sin(t * 9f + b.x * 1.7f + b.y)
        val r = ts * lg[0] * 0.8f * flick
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFB763).copy(alpha = (dark * 0.5f * lg[1]).coerceIn(0f, 0.45f)), Color.Transparent), center = Offset(cx, cy), radius = r),
            radius = r, center = Offset(cx, cy), blendMode = BlendMode.Plus,
        )
    }
    var moths = 0
    for (i in Lamps.sites(sim)) {
        val f = Lamps.lit(dark, i)
        if (f <= 0f) continue
        val px = g.ox + ((i % GW) + 0.5f) * ts
        val py = g.oy + ((i / GW) + 0.9f) * ts - ps * 5.8f
        if (px < -ts * 3f || py < -ts * 3f || px > size.width + ts * 3f || py > size.height + ts * 3f) continue
        val r = ts * 1.5f * murk
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFC46B).copy(alpha = 0.5f * f * (dark * 1.4f).coerceIn(0f, 1f)), Color.Transparent), center = Offset(px, py), radius = r),
            radius = r, center = Offset(px, py), blendMode = BlendMode.Plus,
        )
        drawCircle(Color(0xFFFFF4C2).copy(alpha = 0.9f * f), radius = ps * 0.9f, center = Offset(px, py))
        if (moths < 24 && !sim.raining()) {
            moths++
            for (k in 0 until 2) {
                val a = t * (1.6f + 0.5f * k) + i * 1.3f + k * 3.1f
                val mx = px + cos(a) * ts * 0.34f
                val my = py + sin(a * 1.7f) * ts * 0.22f
                drawRect(Color(0xFFEFE6C8).copy(alpha = 0.85f * f), Offset(mx, my), Size(ps * 1.1f, ps * 0.8f))
            }
        }
    }
}
