package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.hypot
import kotlin.math.sin

/** Lessons: while an elder teaches a child, a small slate stands between them and little letters and sparks float up. */

private val SLATE = Color(0xFF3A3F47)
private val SLATE_EDGE = Color(0xFF8A6A3E)
private val CHALK = Color(0xFFF4F1E4)
private val SPARK_GOLD = Color(0xFFFFE08A)

fun DrawScope.lessonScenes(sim: CivSim, g: Geo) {
    val u = g.ps
    for (v in sim.villagers) {
        if (v.action != "teach" || v.phase != "working") continue
        var cx = v.x
        var cy = v.y
        var best = 7f
        for (c in sim.villagers) {
            if (!sim.isChild(c)) continue
            val d = hypot(c.x - v.x, c.y - v.y)
            if (d < best) { best = d; cx = c.x; cy = c.y }
        }
        val mx = g.ox + (v.x + cx) / 2f * g.ts
        val my = g.oy + (v.y + cy) / 2f * g.ts + g.ts * 0.5f
        // the slate on two little legs
        drawRect(SLATE_EDGE, Offset(mx - u * 3.4f, my - u * 7.2f), Size(u * 6.8f, u * 5.4f))
        drawRect(SLATE, Offset(mx - u * 2.8f, my - u * 6.6f), Size(u * 5.6f, u * 4.2f))
        drawRect(SLATE_EDGE, Offset(mx - u * 2.6f, my - u * 1.8f), Size(u * 0.8f, u * 1.8f))
        drawRect(SLATE_EDGE, Offset(mx + u * 1.8f, my - u * 1.8f), Size(u * 0.8f, u * 1.8f))
        // a few chalk scribbles that fill in over the lesson
        val k = ((g.t * 1.3f) % 3f).toInt() + 1
        drawRect(CHALK, Offset(mx - u * 2.2f, my - u * 5.8f), Size(u * 3.2f, u * 0.6f))
        if (k >= 2) drawRect(CHALK, Offset(mx - u * 2.2f, my - u * 4.7f), Size(u * 4.2f, u * 0.6f))
        if (k >= 3) drawRect(CHALK, Offset(mx - u * 2.2f, my - u * 3.6f), Size(u * 2.4f, u * 0.6f))
        // letters and sparks drifting up from the child
        for (i in 0 until 3) {
            val ph = (g.t * 0.7f + i * 0.33f) % 1f
            val px = g.ox + cx * g.ts + sin(g.t * 2f + i * 2.1f) * u * 2.2f + (i - 1) * u * 2f
            val py = g.oy + cy * g.ts - u * (10f + ph * 9f)
            val a = (1f - ph).coerceIn(0f, 1f)
            drawRect(SPARK_GOLD.copy(alpha = a), Offset(px, py), Size(u * 1.1f, u * 1.1f))
            drawRect(CHALK.copy(alpha = a * 0.8f), Offset(px - u * 0.5f, py + u * 0.4f), Size(u * 2.1f, u * 0.4f))
        }
    }
}
