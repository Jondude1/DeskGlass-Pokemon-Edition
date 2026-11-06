package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.Puddles
import kotlin.math.max

/** Wet ground: a damp tint after rain, muddy-rimmed puddles that mirror the sky (grey under cloud, gold at dusk, dark at night),
 *  rain rings in them while it falls, a sun glint once it clears, and a skin of cracked ice in winter. Pure drawing; the wetness
 *  and the puddle spots live in sim/Puddles.kt. */
fun DrawScope.wetGround(sim: CivSim, g: Geo) {
    Puddles.update(sim)
    val wet = Puddles.wet
    if (wet < 0.03f) return
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    // the whole field darkens a little while it is soaked
    drawRect(Color(0xFF20304A).copy(alpha = 0.07f * wet), Offset.Zero, size)
    val sites = Puddles.sites(sim)
    val ice = Puddles.winter(sim)
    val raining = sim.raining()
    val day = (1f - g.dark / 0.55f).coerceIn(0f, 1f)
    val sky = if (sim.storm()) Color(0xFF59647A) else if (raining || sim.foggy()) Color(0xFF7E8EA6) else Color(0xFF9CC4EA)
    for (k in sites.indices) {
        val s = Puddles.size(k, sites.size)
        if (s <= 0.05f) continue
        val i = sites[k]
        val h = Puddles.hash(i)
        val cx = g.ox + ((i % GW) + 0.5f + ((h shr 3) % 7 - 3) * 0.06f) * ts
        val cy = g.oy + ((i / GW) + 0.62f) * ts
        if (cx < -ts * 2f || cy < -ts * 2f || cx > size.width + ts * 2f || cy > size.height + ts * 2f) continue
        val w = ts * (0.9f + (h % 5) * 0.16f) * s
        val hh = w * 0.42f
        drawOval(Color(0xFF4A3B2C).copy(alpha = 0.30f * s), Offset(cx - w * 0.58f, cy - hh * 0.58f), Size(w * 1.16f, hh * 1.16f))
        val base = if (ice) Color(0xFFD7E7F2) else sky
        val lit = Color(
            base.red * (0.35f + 0.65f * day), base.green * (0.35f + 0.65f * day), base.blue * (0.45f + 0.55f * day), 0.82f * s,
        )
        drawOval(lit, Offset(cx - w * 0.5f, cy - hh * 0.5f), Size(w, hh))
        if (ice) {
            val c = Color(0xFFFFFFFF).copy(alpha = 0.55f * s)
            drawLine(c, Offset(cx - w * 0.3f, cy - hh * 0.1f), Offset(cx + w * 0.1f, cy + hh * 0.15f), strokeWidth = max(1f, ps * 0.5f))
            drawLine(c, Offset(cx - w * 0.05f, cy - hh * 0.3f), Offset(cx + w * 0.3f, cy - hh * 0.05f), strokeWidth = max(1f, ps * 0.5f))
        } else {
            drawOval(Color.White.copy(alpha = 0.35f * s * day), Offset(cx - w * 0.28f, cy - hh * 0.3f), Size(w * 0.3f, hh * 0.22f))
            if (raining) {
                for (r in 0 until 2) {
                    val ph = ((t * 1.3f + (h % 11) * 0.31f + r * 0.5f) % 1f)
                    val rw = w * 0.5f * ph
                    drawOval(
                        Color.White.copy(alpha = 0.55f * (1f - ph) * s),
                        Offset(cx - rw + ((h shr 5) % 5 - 2) * ts * 0.04f, cy - rw * 0.42f),
                        Size(rw * 2f, rw * 0.84f), style = Stroke(width = max(1f, ps * 0.4f)),
                    )
                }
            } else if (day > 0.5f && wet > 0.25f && (h + (t * 0.5f).toInt()) % 9 == 0) {
                // a sun glint winking on the water
                drawCircle(Color(0xFFFFFBE0).copy(alpha = 0.7f * s), radius = ps * 0.9f, center = Offset(cx + w * 0.12f, cy - hh * 0.1f))
            }
        }
    }
}
