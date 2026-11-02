package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.DAY_LEN
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.SEASON_DAYS
import com.jondude1.deskglass.sim.T
import kotlin.math.sin

/** Seasons that change the land itself: trees, bushes and crops recolour tree by tree, snow settles on the grass,
 *  lakes freeze over, autumn leaves and spring petals litter the ground. Pure drawing, driven by the sim's clock. */

private fun sh(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

/** The season index a given plant shows right now: it turns over a stretch of the new season, each plant at its own moment. */
internal fun treeSeason(sim: CivSim, h: Int): Int {
    val s = sim.seasonIdx()
    if (sim.time < DAY_LEN * SEASON_DAYS) return s
    val turn = ((h shr 5) % 100) / 100f * 0.55f
    return if (sim.seasonFrac() >= turn) s else (s + 3) % 4
}

internal fun DrawScope.seasonGround(sim: CivSim, g: Geo, vx0: Int, vx1: Int, vy0: Int, vy1: Int) {
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    val season = sim.seasonIdx()
    val frac = sim.seasonFrac()
    val snow = sim.snowCover()
    val early = sim.time < DAY_LEN * SEASON_DAYS
    val leafK = if (early) 0f else when (season) {
        2 -> (frac * 2.2f).coerceAtMost(1f)
        3 -> (1f - frac * 3f).coerceAtLeast(0f)
        else -> 0f
    }
    val petalK = when (season) {
        0 -> if (early) 0.7f else (frac * 2.5f).coerceAtMost(1f)
        1 -> (1f - frac * 2.5f).coerceAtLeast(0f)
        else -> 0f
    }
    if (snow <= 0.01f && leafK <= 0.01f && petalK <= 0.01f) return
    for (y in vy0 until vy1) for (x in vx0 until vx1) {
        val i = y * GW + x
        val tile = sim.tiles[i]
        val h = sh(x, y)
        val rank = (h % 100) / 100f
        val px = g.ox + x * ts
        val py = g.oy + y * ts
        if (tile == T.WATER) {
            // lakes freeze over: a pale sheet with a bright streak and the odd crack
            if (snow > 0.05f && rank < snow) {
                val a = (0.34f + 0.2f * snow).coerceAtMost(0.55f)
                drawRect(Color(0xFFD7EEF8).copy(alpha = a), Offset(px, py), Size(ts + 1f, ts + 1f))
                if (h % 4 == 0) drawRect(Color.White.copy(alpha = 0.5f), Offset(px + ps * 2f, py + ps * 4f), Size(ps * 7f, ps * 0.8f))
                if (h % 9 == 0) {
                    drawRect(Color(0xFF8FB8D0).copy(alpha = 0.6f), Offset(px + ps * 5f, py + ps * 6f), Size(ps * 0.8f, ps * 4f))
                    drawRect(Color(0xFF8FB8D0).copy(alpha = 0.6f), Offset(px + ps * 5f, py + ps * 9f), Size(ps * 4f, ps * 0.8f))
                }
            }
            continue
        }
        val ground = tile == T.GRASS || tile == T.FOREST || tile == T.BUSH
        if (!ground || sim.owner[i] >= 0) continue
        if (snow > 0.05f && rank < snow) {
            // drifts: two soft lumps per tile with a blue shadow edge
            val c1 = Color(0xFFF4F8FC).copy(alpha = 0.9f)
            val c2 = Color(0xFFB7C8DC).copy(alpha = 0.8f)
            val ax = px + ((h shr 4) % 5 + 1) * ps
            val ay = py + ((h shr 8) % 5 + 6) * ps
            drawRect(c2, Offset(ax, ay + ps * 2f), Size(ps * 8f, ps * 1.2f))
            drawRect(c1, Offset(ax + ps, ay), Size(ps * 6f, ps * 2.2f))
            drawRect(c1, Offset(ax, ay + ps), Size(ps * 8f, ps * 1.4f))
            if (snow > 0.6f && h % 2 == 0) {
                val bx = px + ((h shr 10) % 6 + 1) * ps
                val by = py + ((h shr 12) % 4 + 1) * ps
                drawRect(c2, Offset(bx, by + ps * 1.5f), Size(ps * 5f, ps))
                drawRect(c1, Offset(bx + ps * 0.5f, by), Size(ps * 4f, ps * 1.8f))
            }
        } else if (leafK > 0.05f && rank < leafK && (tile == T.FOREST || h % 3 == 0)) {
            // fallen leaves
            val n = if (tile == T.FOREST) 4 else 2
            for (k in 0 until n) {
                val hk = sh(h, k + 3)
                val c = when (hk % 3) { 0 -> Color(0xFFE0752B); 1 -> Color(0xFFC9A227); else -> Color(0xFF9A4A22) }
                drawRect(c.copy(alpha = 0.9f), Offset(px + (hk % 13 + 1) * ps, py + ((hk shr 4) % 13 + 2) * ps), Size(ps * 1.5f, ps))
            }
        } else if (petalK > 0.05f && rank < petalK && (tile == T.FOREST || h % 4 == 0)) {
            // blossom petals and a few extra spring flowers
            val n = if (tile == T.FOREST) 3 else 1
            for (k in 0 until n) {
                val hk = sh(h, k + 9)
                val c = when (hk % 3) { 0 -> Color(0xFFFFC2DC); 1 -> Color(0xFFFFFFFF); else -> Color(0xFFFFE680) }
                val fx = px + (hk % 12 + 2) * ps
                val fy = py + ((hk shr 4) % 12 + 2) * ps
                if (tile == T.GRASS) {
                    val nod = sin(t * 1.5f + (hk % 7)) * ps * 0.3f
                    drawRect(Color(0xFF3F8744), Offset(fx + ps * 0.4f, fy + ps), Size(ps * 0.7f, ps * 1.4f))
                    drawRect(c, Offset(fx + nod, fy), Size(ps * 1.6f, ps * 1.6f))
                } else {
                    drawRect(c.copy(alpha = 0.9f), Offset(fx, fy), Size(ps * 1.3f, ps * 0.9f))
                }
            }
        }
    }
}
