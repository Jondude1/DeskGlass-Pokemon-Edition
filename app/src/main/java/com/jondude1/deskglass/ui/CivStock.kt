package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.Stock
import kotlin.math.abs
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// The village stores you can SEE: a log pile, a stone heap, grain sacks, a hide-drying rack and
// ingots near the camp, each growing with what is really in the stores, plus scarecrows on farms.
// ---------------------------------------------------------------------------------------------
private var slotKey = -1
private var slotList: List<IntArray> = emptyList()

private fun findSlots(sim: CivSim): List<IntArray> {
    val hub = sim.buildings.firstOrNull { it.type == "campfire" } ?: sim.buildings.firstOrNull { it.type == "hall" } ?: return emptyList()
    val key = sim.buildings.size * 31 + hub.x * 7 + hub.y
    if (key == slotKey) return slotList
    val cand = ArrayList<IntArray>()
    for (dx in -8..8) for (dy in -6..7) {
        val d = dx * dx + dy * dy * 2
        if (d < 12 || d > 90) continue
        cand.add(intArrayOf(dx, dy, d))
    }
    cand.sortBy { it[2] }
    val out = ArrayList<IntArray>()
    for (c in cand) {
        val x = hub.x + c[0]
        val y = hub.y + c[1]
        if (!sim.walkableTile(x, y)) continue
        if (sim.buildings.any { abs(it.x - x) <= 1 && abs(it.y - y) <= 1 }) continue
        if (out.any { abs(it[0] - x) < 3 && abs(it[1] - y) < 2 }) continue
        out.add(intArrayOf(x, y))
        if (out.size >= 5) break
    }
    slotKey = key
    slotList = out
    return out
}

private fun DrawScope.shadowAt(x: Float, y: Float, w: Float, u: Float) {
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(x - w / 2f, y - u * 0.9f), Size(w, u * 2.2f))
}

private fun DrawScope.woodPile(x: Float, y: Float, level: Int, u: Float) {
    shadowAt(x, y, u * 15f, u)
    val rows = intArrayOf(4, 3, 2, 1)
    var left = level.coerceIn(1, 10)
    for (r in 0 until 4) {
        val n = minOf(left, rows[r])
        if (n <= 0) break
        left -= n
        val full = rows[r]
        for (k in 0 until n) {
            val lx = x - full * u * 2f + k * u * 4f
            val ly = y - (r + 1) * u * 2.1f
            drawRect(Color(0xFF8A5A33), Offset(lx, ly), Size(u * 4f, u * 2.1f))
            drawRect(Color(0xFFA9763F), Offset(lx, ly), Size(u * 4f, u * 0.7f))
            drawRect(Color(0xFFD9B27A), Offset(lx, ly + u * 0.3f), Size(u * 0.9f, u * 1.5f))
        }
    }
}

private fun DrawScope.stoneHeap(x: Float, y: Float, level: Int, u: Float) {
    shadowAt(x, y, u * 13f, u)
    val rows = intArrayOf(3, 2, 2, 1)
    var left = level.coerceIn(1, 8)
    for (r in 0 until 4) {
        val n = minOf(left, rows[r])
        if (n <= 0) break
        left -= n
        val full = rows[r]
        for (k in 0 until n) {
            val lx = x - full * u * 2.1f + k * u * 4.2f
            val ly = y - (r + 1) * u * 2.8f
            drawRect(Color(0xFF6B7078), Offset(lx, ly), Size(u * 4f, u * 2.9f))
            drawRect(Color(0xFF8E939C), Offset(lx + u * 0.4f, ly + u * 0.4f), Size(u * 3.2f, u * 2.1f))
            drawRect(Color(0xFFB9BDC4), Offset(lx + u * 0.4f, ly + u * 0.4f), Size(u * 3.2f, u * 0.8f))
        }
    }
}

private fun DrawScope.grainSacks(x: Float, y: Float, level: Int, u: Float) {
    shadowAt(x, y, u * 12f, u)
    val rows = intArrayOf(3, 2, 1)
    var left = level.coerceIn(1, 6)
    for (r in 0 until 3) {
        val n = minOf(left, rows[r])
        if (n <= 0) break
        left -= n
        val full = rows[r]
        for (k in 0 until n) {
            val lx = x - full * u * 1.8f + k * u * 3.8f
            val ly = y - (r + 1) * u * 3.3f
            drawRect(Color(0xFFB59A62), Offset(lx, ly + u * 0.6f), Size(u * 3.6f, u * 3.1f))
            drawRect(Color(0xFFD9C38F), Offset(lx + u * 0.3f, ly + u * 0.9f), Size(u * 2.4f, u * 2.4f))
            drawRect(Color(0xFF8A6B3C), Offset(lx + u * 0.9f, ly), Size(u * 1.8f, u * 0.9f))
        }
    }
}

private fun DrawScope.hideRack(x: Float, y: Float, level: Int, u: Float, t: Float) {
    shadowAt(x, y, u * 12f, u)
    val post = Color(0xFF6B4A2B)
    drawRect(post, Offset(x - u * 5f, y - u * 8f), Size(u * 0.9f, u * 8f))
    drawRect(post, Offset(x + u * 4.1f, y - u * 8f), Size(u * 0.9f, u * 8f))
    drawRect(post, Offset(x - u * 5.4f, y - u * 8.2f), Size(u * 10.8f, u * 0.9f))
    for (k in 0 until level.coerceIn(1, 4)) {
        val hx = x - u * 4f + k * u * 2.4f
        val sw = sin(t * 1.6f + k * 1.7f) * u * 0.35f
        drawRect(if (k % 2 == 0) Color(0xFF9A7650) else Color(0xFFB08B5E), Offset(hx + sw, y - u * 7.3f), Size(u * 2.1f, u * 4.4f))
        drawRect(Color(0xFF6E5236), Offset(hx + sw, y - u * 3.2f), Size(u * 2.1f, u * 0.8f))
    }
}

private fun DrawScope.ingots(x: Float, y: Float, level: Int, u: Float) {
    shadowAt(x, y, u * 11f, u)
    val rows = intArrayOf(3, 2, 1)
    var left = level.coerceIn(1, 6)
    for (r in 0 until 3) {
        val n = minOf(left, rows[r])
        if (n <= 0) break
        left -= n
        val full = rows[r]
        for (k in 0 until n) {
            val lx = x - full * u * 2f + k * u * 4f
            val ly = y - (r + 1) * u * 1.9f
            drawRect(Color(0xFF8C93A0), Offset(lx, ly), Size(u * 3.8f, u * 1.9f))
            drawRect(Color(0xFFD7DCE6), Offset(lx + u * 0.3f, ly), Size(u * 3.2f, u * 0.8f))
        }
    }
}

private fun DrawScope.scarecrow(x: Float, y: Float, u: Float, t: Float, crow: Boolean) {
    val sway = sin(t * 1.3f + x * 0.01f) * u * 0.5f
    val wood = Color(0xFF6B4A2B)
    shadowAt(x, y, u * 6f, u)
    drawRect(wood, Offset(x - u * 0.45f, y - u * 9f), Size(u * 0.9f, u * 9f))
    drawRect(wood, Offset(x - u * 3.2f + sway, y - u * 6.6f), Size(u * 6.4f, u * 0.9f))
    drawRect(Color(0xFFB5543A), Offset(x - u * 1.5f + sway * 0.5f, y - u * 6.4f), Size(u * 3f, u * 3.2f))
    drawRect(Color(0xFFE8C547), Offset(x - u * 3.6f + sway, y - u * 6.2f), Size(u * 0.8f, u * 1.6f))
    drawRect(Color(0xFFE8C547), Offset(x + u * 2.8f + sway, y - u * 6.2f), Size(u * 0.8f, u * 1.6f))
    drawRect(Color(0xFFE8C547), Offset(x - u * 1.2f, y - u * 9.6f), Size(u * 2.4f, u * 2.4f))
    drawRect(Color(0xFF4A3A2A), Offset(x - u * 2.2f, y - u * 9.8f), Size(u * 4.4f, u * 0.8f))
    drawRect(Color(0xFF4A3A2A), Offset(x - u * 1.1f, y - u * 11.2f), Size(u * 2.2f, u * 1.5f))
    if (crow) {
        val hop = if (sin(t * 3f + x) > 0.92f) u * 0.8f else 0f
        drawRect(Color(0xFF22222C), Offset(x + u * 1.6f, y - u * 8.1f - hop), Size(u * 2.2f, u * 1.5f))
        drawRect(Color(0xFF22222C), Offset(x + u * 3.4f, y - u * 8.7f - hop), Size(u * 1.1f, u * 1.1f))
    }
}

fun DrawScope.villageStock(sim: CivSim, art: CivArt, g: Geo) {
    val u = g.ps
    val slots = findSlots(sim)
    var si = 0
    fun next(): Offset? {
        if (si >= slots.size) return null
        val s = slots[si++]
        return Offset(g.ox + (s[0] + 0.5f) * g.ts, g.oy + (s[1] + 0.9f) * g.ts)
    }
    val items = ArrayList<Pair<Int, Int>>()
    val wl = Stock.woodLevel(sim); if (wl > 0) items.add(0 to wl)
    val sl = Stock.stoneLevel(sim); if (sl > 0) items.add(1 to sl)
    val fl = Stock.foodLevel(sim); if (fl > 0) items.add(2 to fl)
    val hl = Stock.hideLevel(sim); if (hl > 0) items.add(3 to hl)
    val ml = Stock.metalLevel(sim); if (ml > 0) items.add(4 to ml)
    for (item in items) {
        val p = next() ?: break
        when (item.first) {
            0 -> woodPile(p.x, p.y, item.second, u)
            1 -> stoneHeap(p.x, p.y, item.second, u)
            2 -> grainSacks(p.x, p.y, item.second, u)
            3 -> hideRack(p.x, p.y, item.second, u, g.t)
            else -> ingots(p.x, p.y, item.second, u)
        }
    }
    for (b in sim.buildings) {
        if (b.type != "farm") continue
        val sp = art.farm
        val w = sp.lw * u
        val x = g.ox + (b.x + 0.5f) * g.ts + w / 2f - u * 2f
        val y = g.oy + (b.y + 0.97f) * g.ts - u * 1f
        scarecrow(x, y, u, g.t, b.ready >= 2 && g.dark < 0.4f)
    }
}
