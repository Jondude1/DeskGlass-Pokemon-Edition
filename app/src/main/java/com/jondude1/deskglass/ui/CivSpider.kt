package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim

// Giant forest spiders: sticky webs on the ground (spokes and rings with dew glints) and green venom motes over the bitten.
fun DrawScope.spiderWebs(sim: CivSim, g: Geo) {
    val ws = try { sim.webs.toList() } catch (e: Exception) { return }
    val u = g.ps
    for (w in ws) {
        val age = (sim.time - w[2]).toFloat()
        val fade = if (age > 65f) ((80f - age) / 15f).coerceIn(0f, 1f) else 1f
        val cx = g.ox + w[0] * g.ts
        val cy = g.oy + w[1] * g.ts
        val a = 0.75f * fade
        val line = Color(0xFFF2F2F8).copy(alpha = a)
        // spokes
        for (k in 0 until 4) {
            val ang = k * 0.785f
            val dx = kotlin.math.cos(ang) * u * 7f
            val dy = kotlin.math.sin(ang) * u * 3.4f
            drawLine(line, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), strokeWidth = u * 0.5f)
        }
        // rings
        for (r in 1..2) {
            val rx = u * 2.6f * r
            val ry = u * 1.3f * r
            drawOval(line.copy(alpha = a * 0.7f), Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 0.4f))
        }
        val tw = ((g.t * 0.6f + w[0] * 1.7f) % 1f)
        if (tw < 0.25f) drawRect(Color.White.copy(alpha = fade * (1f - tw * 4f)), Offset(cx + u * 2.2f, cy - u * 0.8f), Size(u * 1.2f, u * 1.2f))
    }
    // venom: rising green motes above the poisoned
    if (sim.poisoned.isEmpty()) return
    val ids = try { sim.poisoned.keys.toList() } catch (e: Exception) { return }
    for (v in sim.villagers) {
        if (v.id !in ids) continue
        val vx = g.ox + v.x * g.ts
        val vy = g.oy + v.y * g.ts
        for (k in 0 until 3) {
            val ph = ((g.t * 0.9f + k * 0.33f + v.x) % 1f)
            drawRect(Color(0xFF8CD84A).copy(alpha = 0.85f * (1f - ph)), Offset(vx + (k - 1) * u * 2.5f, vy - u * 8f - ph * u * 9f), Size(u * 1.4f, u * 1.4f))
        }
    }
}
