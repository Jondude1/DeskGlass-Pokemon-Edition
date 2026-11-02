package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.cos
import kotlin.math.sin

/**
 * Village Council: while the council meets, a round table with a scroll, stools and a hanging bell stands by the hall;
 * once a priority is voted, a coloured pennant with its emblem flies over the hall until the plan runs out.
 */
fun policyColor(kind: String): Color = when (kind) {
    "harvest" -> Color(0xFFE8B63A)
    "works" -> Color(0xFFC9703A)
    "watch" -> Color(0xFFD8453A)
    "lore" -> Color(0xFF6A8CE8)
    else -> Color(0xFFE86AA8)
}

fun DrawScope.councilSite(sim: CivSim, g: Geo) {
    if (sim.councilOn) councilTable(sim, g)
    if (sim.policy.isNotEmpty() && sim.time < sim.policyUntil) policyPennant(sim, g)
}

private fun DrawScope.councilTable(sim: CivSim, g: Geo) {
    val u = g.ps
    val x = g.ox + sim.councilX * g.ts
    val y = g.oy + (sim.councilY + 0.45f) * g.ts
    val t = g.t
    val rise = ((sim.time - sim.councilStart) / 4.0).toFloat().coerceIn(0f, 1f)
    val fade = ((sim.councilUntil - sim.time) / 4.0).toFloat().coerceIn(0f, 1f)
    val k = rise * fade
    if (k <= 0f) return
    // swept ground and a soft pool of light under the table
    drawOval(Color(0xFFC9B36B).copy(alpha = 0.30f * k), Offset(x - u * 12f, y - u * 4f), Size(u * 24f, u * 8f))
    drawOval(Color.Black.copy(alpha = 0.22f * k), Offset(x - u * 6.5f, y - u * 1.2f), Size(u * 13f, u * 3.2f))
    // stools in a ring
    for (i in 0 until 8) {
        val a = i * 0.785f + 0.4f
        val sx = x + cos(a) * u * 8.4f
        val sy = y - u * 1.2f + sin(a) * u * 2.4f
        drawRect(Color(0xFF5A3C22).copy(alpha = k), Offset(sx - u * 1.1f, sy - u * 1.6f), Size(u * 2.2f, u * 1.8f))
        drawRect(Color(0xFF8A6238).copy(alpha = k), Offset(sx - u * 1.3f, sy - u * 2.0f), Size(u * 2.6f, u * 0.9f))
    }
    // the round table
    drawRect(Color(0xFF5A3C22).copy(alpha = k), Offset(x - u * 0.9f, y - u * 4.2f), Size(u * 1.8f, u * 3.4f))
    drawOval(Color(0xFF6B4A2B).copy(alpha = k), Offset(x - u * 5.2f, y - u * 6.3f), Size(u * 10.4f, u * 3.6f))
    drawOval(Color(0xFF8A6238).copy(alpha = k), Offset(x - u * 4.8f, y - u * 6.6f), Size(u * 9.6f, u * 3.2f))
    // unrolled scroll, candle and a quill
    drawRect(Color(0xFFF4EBD6).copy(alpha = k), Offset(x - u * 2.6f, y - u * 6.0f), Size(u * 4.2f, u * 1.4f))
    drawRect(Color(0xFFD9CDB0).copy(alpha = k), Offset(x - u * 2.9f, y - u * 6.1f), Size(u * 0.7f, u * 1.6f))
    drawRect(Color(0xFFD9CDB0).copy(alpha = k), Offset(x + u * 1.3f, y - u * 6.1f), Size(u * 0.7f, u * 1.6f))
    drawRect(Color(0xFFF2E8C8).copy(alpha = k), Offset(x + u * 2.9f, y - u * 7.3f), Size(u * 0.9f, u * 1.8f))
    val fl = 1f + 0.25f * sin(t * 11f)
    drawCircle(Color(0xFFFFC94D).copy(alpha = 0.9f * k), radius = u * 0.7f * fl, center = Offset(x + u * 3.35f, y - u * 8.0f))
    drawCircle(Color(0xFFFFB347).copy(alpha = 0.18f * k), radius = u * 5f, center = Offset(x + u * 3.35f, y - u * 8f))
    // bell post
    val bx = x - u * 9.6f
    drawRect(Color(0xFF5A3C22).copy(alpha = k), Offset(bx - u * 0.5f, y - u * 12f), Size(u * 1f, u * 12f))
    drawRect(Color(0xFF5A3C22).copy(alpha = k), Offset(bx - u * 0.5f, y - u * 12f), Size(u * 4f, u * 0.9f))
    val sw = sin(t * 3f) * u * 0.6f * (1f - rise * 0.7f)
    drawRect(Color(0xFFE8C34A).copy(alpha = k), Offset(bx + u * 2.2f + sw, y - u * 11f), Size(u * 1.8f, u * 2.2f))
    // seated voices: little drifting dots
    for (i in 0 until 5) {
        val p = (t * 0.3f + i * 0.2f) % 1f
        drawRect(Color(0xFFFFF3C4).copy(alpha = (1f - p) * 0.8f * k), Offset(x + sin(i * 2.7f + t * 0.9f) * u * 8f, y - u * (8f + p * 14f)), Size(u * 0.9f, u * 0.9f))
    }
}

private fun DrawScope.policyPennant(sim: CivSim, g: Geo) {
    val hub = sim.buildings.firstOrNull { it.type == "hall" } ?: sim.buildings.firstOrNull { it.type == "campfire" } ?: return
    val u = g.ps
    val t = g.t
    val x = g.ox + (hub.x + (if (hub.type == "hall") -0.2f else 0.9f)) * g.ts
    val y = g.oy + (hub.y + 0.9f) * g.ts
    val left = ((sim.policyUntil - sim.time) / 12.0).toFloat().coerceIn(0f, 1f)
    val c = policyColor(sim.policy)
    val h = u * 17f
    // pole
    drawRect(Color(0xFF5A3C22), Offset(x - u * 0.5f, y - h), Size(u * 1f, h))
    drawCircle(Color(0xFFE8C34A), radius = u * 1.0f, center = Offset(x, y - h - u * 0.6f))
    // rippling pennant
    val pw = u * 9f
    val ph = u * 5.5f
    val top = y - h + u * 0.6f
    val path = Path()
    path.moveTo(x + u * 0.5f, top)
    val steps = 6
    for (i in 1..steps) {
        val f = i / steps.toFloat()
        path.lineTo(x + u * 0.5f + pw * f, top + sin(t * 4f + f * 4f) * u * 0.7f * f + ph * 0.5f * f)
    }
    for (i in steps downTo 0) {
        val f = i / steps.toFloat()
        path.lineTo(x + u * 0.5f + pw * f * 0.9f, top + ph * (1f - 0.45f * f) + sin(t * 4f + f * 4f) * u * 0.7f * f)
    }
    path.close()
    drawPath(path, c.copy(alpha = 0.95f * (0.5f + 0.5f * left)))
    // emblem on the cloth
    val ex = x + u * 3.2f
    val ey = top + u * 2.6f
    val ink = Color(0xFFFFF8E6)
    when (sim.policy) {
        "harvest" -> { drawRect(ink, Offset(ex - u * 0.2f, ey - u * 1.4f), Size(u * 0.5f, u * 2.8f)); drawRect(ink, Offset(ex - u * 1.1f, ey - u * 1.0f), Size(u * 0.6f, u * 1.0f)); drawRect(ink, Offset(ex + u * 0.6f, ey - u * 1.0f), Size(u * 0.6f, u * 1.0f)) }
        "works" -> { drawRect(ink, Offset(ex - u * 0.2f, ey - u * 1.0f), Size(u * 0.5f, u * 2.4f)); drawRect(ink, Offset(ex - u * 1.2f, ey - u * 1.4f), Size(u * 2.4f, u * 0.9f)) }
        "watch" -> { drawRect(ink, Offset(ex - u * 1.0f, ey - u * 1.3f), Size(u * 2.0f, u * 1.8f)); drawRect(ink, Offset(ex - u * 0.5f, ey + u * 0.5f), Size(u * 1.0f, u * 0.9f)) }
        "lore" -> { drawRect(ink, Offset(ex - u * 1.3f, ey - u * 1.1f), Size(u * 1.1f, u * 2.2f)); drawRect(ink, Offset(ex + u * 0.2f, ey - u * 1.1f), Size(u * 1.1f, u * 2.2f)) }
        else -> { drawCircle(ink, radius = u * 0.9f, center = Offset(ex - u * 0.6f, ey - u * 0.4f)); drawCircle(ink, radius = u * 0.9f, center = Offset(ex + u * 0.6f, ey - u * 0.4f)); drawRect(ink, Offset(ex - u * 0.9f, ey - u * 0.2f), Size(u * 1.8f, u * 1.2f)) }
    }
    // a few glints in the policy colour drifting up while it holds
    for (i in 0 until 4) {
        val p = (t * 0.35f + i * 0.25f) % 1f
        drawRect(c.copy(alpha = (1f - p) * 0.7f), Offset(x + u * (2f + i * 2f) + sin(t + i) * u, top - u * (1f + p * 7f)), Size(u * 0.9f, u * 0.9f))
    }
}
