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
    for (sp in sim.seasonProps.toList()) {
        val x = g.ox + sp.x * g.ts
        val y = g.oy + (sp.y + 0.9f) * g.ts
        when (sp.kind) {
            "snowman" -> snowman(x, y, u, g.t)
            "shroom" -> mushrooms(x, y, u, g.t, false, g.dark)
            "glowcap" -> mushrooms(x, y, u, g.t, true, g.dark)
            else -> leafPile(x, y, u, g.t)
        }
    }
}

private fun DrawScope.snowman(x: Float, y: Float, u: Float, t: Float) {
    shadowAt(x, y, u * 8f, u)
    val white = Color(0xFFF4F8FF)
    val shade = Color(0xFFC9D6EA)
    drawOval(shade, Offset(x - u * 3.2f, y - u * 4.4f), Size(u * 6.4f, u * 4.6f))
    drawOval(white, Offset(x - u * 3f, y - u * 4.7f), Size(u * 5.8f, u * 4.4f))
    drawOval(shade, Offset(x - u * 2.4f, y - u * 7.6f), Size(u * 4.8f, u * 3.8f))
    drawOval(white, Offset(x - u * 2.2f, y - u * 7.9f), Size(u * 4.4f, u * 3.6f))
    drawOval(white, Offset(x - u * 1.7f, y - u * 10.4f), Size(u * 3.4f, u * 3.2f))
    drawRect(Color(0xFF22222C), Offset(x - u * 1.0f, y - u * 9.4f), Size(u * 0.7f, u * 0.7f))
    drawRect(Color(0xFF22222C), Offset(x + u * 0.4f, y - u * 9.4f), Size(u * 0.7f, u * 0.7f))
    drawRect(Color(0xFFE8782B), Offset(x - u * 0.2f, y - u * 8.7f), Size(u * 1.8f, u * 0.6f))
    drawRect(Color(0xFF22222C), Offset(x - u * 1.5f, y - u * 11f), Size(u * 3f, u * 0.6f))
    drawRect(Color(0xFF22222C), Offset(x - u * 1f, y - u * 12.6f), Size(u * 2f, u * 1.7f))
    val wave = sin(t * 1.6f + x * 0.02f) * u * 0.5f
    drawRect(Color(0xFFC0392B), Offset(x - u * 2f + wave, y - u * 7.9f), Size(u * 4f, u * 0.9f))
    drawRect(Color(0xFFC0392B), Offset(x + u * 1.2f + wave, y - u * 7.2f), Size(u * 0.9f, u * 2f))
    drawRect(Color(0xFF6B4A2B), Offset(x - u * 5f, y - u * 7f + wave * 0.3f), Size(u * 2.6f, u * 0.5f))
    drawRect(Color(0xFF6B4A2B), Offset(x + u * 2.4f, y - u * 7f - wave * 0.3f), Size(u * 2.6f, u * 0.5f))
}

private fun DrawScope.leafPile(x: Float, y: Float, u: Float, t: Float) {
    shadowAt(x, y, u * 9f, u)
    val cols = listOf(Color(0xFFB5502A), Color(0xFFD98A2B), Color(0xFFE8B63A), Color(0xFF8E3B22))
    drawOval(cols[3], Offset(x - u * 4.6f, y - u * 3.2f), Size(u * 9.2f, u * 3.8f))
    drawOval(cols[0], Offset(x - u * 3.8f, y - u * 4.4f), Size(u * 7.6f, u * 3.8f))
    drawOval(cols[1], Offset(x - u * 2.6f, y - u * 5.4f), Size(u * 5.2f, u * 3.2f))
    drawOval(cols[2], Offset(x - u * 1.4f, y - u * 6.0f), Size(u * 2.8f, u * 2.2f))
    for (k in 0 until 3) {
        val p = (t * 0.35f + k * 0.34f + x * 0.003f) % 1f
        drawRect(cols[k % 3].copy(alpha = 0.9f * (1f - p)), Offset(x + sin(p * 7f + k) * u * 5f, y - u * (5f + p * 9f)), Size(u * 1.1f, u * 0.8f))
    }
}

/**
 * The rift of an expedition: a violet veil between two rune stones with four pips showing how many rooms the party has
 * cleared. Fades out after the party comes home.
 */
fun DrawScope.riftSite(sim: CivSim, g: Geo) {
    val since = (sim.time - sim.riftResultAt).toFloat()
    if (sim.riftState == 0 && (since < 0f || since > 5f)) return
    val open = if (sim.riftState == 0) (1f - since / 5f).coerceIn(0f, 1f) else 1f
    val u = g.ps
    val cx = g.ox + sim.riftX * g.ts
    val cy = g.oy + sim.riftY * g.ts
    val pulse = 0.8f + 0.2f * sin(g.t * 3.2f)
    val hh = g.ts * 1.7f * open
    val ww = g.ts * 0.85f * open
    val realmTint = when (sim.riftTheme) { 1 -> Color(0xFF6AD0FF); 2 -> Color(0xFF8FD65A); 3 -> Color(0xFFFF7A3A); 4 -> Color(0xFF2FC9B0); else -> Color(0xFF9A5CFF) }
    val tint = if (sim.riftState == 0 && !sim.riftWon) Color(0xFFB04A4A) else realmTint
    drawOval(tint.copy(alpha = 0.22f * open * pulse), Offset(cx - g.ts * 1.5f, cy - g.ts * 0.45f), Size(g.ts * 3f, g.ts * 0.95f))
    drawOval(Color(0xFF2A1450).copy(alpha = 0.92f * open), Offset(cx - ww, cy - hh * 1.8f), Size(ww * 2f, hh * 1.8f))
    drawOval(tint.copy(alpha = 0.55f * open * pulse), Offset(cx - ww * 0.65f, cy - hh * 1.6f), Size(ww * 1.3f, hh * 1.4f))
    drawOval(Color.White.copy(alpha = 0.35f * open * pulse), Offset(cx - ww * 0.25f, cy - hh * 1.3f), Size(ww * 0.5f, hh * 0.8f))
    val stone = Color(0xFF6C7080)
    val dark = Color(0xFF454957)
    drawRect(stone, Offset(cx - ww - u * 2.5f, cy - hh * 1.9f), Size(u * 2.5f, hh * 1.9f))
    drawRect(dark, Offset(cx - ww - u * 2.5f, cy - hh * 1.9f), Size(u * 0.8f, hh * 1.9f))
    drawRect(stone, Offset(cx + ww, cy - hh * 1.9f), Size(u * 2.5f, hh * 1.9f))
    drawRect(dark, Offset(cx + ww + u * 1.7f, cy - hh * 1.9f), Size(u * 0.8f, hh * 1.9f))
    val mote = when (sim.riftTheme) { 1 -> Color(0xFFF2FBFF); 2 -> Color(0xFFD8F5A0); 3 -> Color(0xFFFFD27A); 4 -> Color(0xFFBFFFF0); else -> Color(0xFFE6D0FF) }
    for (k in 0 until 7) {
        val ph = (g.t * 0.6f + k * 0.37f) % 1f
        val sx = cx + sin(k * 2.1f + g.t) * ww * 0.8f
        // frost flakes fall, spores drift sideways, embers and bubbles rise
        val sy = if (sim.riftTheme == 1) cy - hh * 2f + ph * hh * 2f else cy - ph * hh * 2f
        val dx = if (sim.riftTheme == 2) sin(g.t * 1.3f + k) * u * 3f else 0f
        val sz = if (sim.riftTheme == 4) u * (1.4f + (k % 3) * 0.5f) else u * 1.2f
        drawRect(mote.copy(alpha = (1f - ph) * 0.8f * open), Offset(sx + dx, sy), Size(sz, sz))
    }
    if (sim.riftState != 0 && sim.riftTheme != 0) {
        // a realm banner: a coloured pennant hung above the left rune stone
        drawRect(Color(0xFF3A3040), Offset(cx - ww - u * 1.8f, cy - hh * 1.9f - u * 9f), Size(u * 0.8f, u * 7f))
        drawRect(realmTint.copy(alpha = 0.9f), Offset(cx - ww - u * 1.0f, cy - hh * 1.9f - u * 9f), Size(u * 4.5f, u * 3.2f))
        drawRect(Color.White.copy(alpha = 0.5f * pulse), Offset(cx - ww - u * 0.4f, cy - hh * 1.9f - u * 8.4f), Size(u * 1.4f, u * 1.4f))
    }
    if (sim.riftState == 2) {
        for (k in 1..4) {
            val on = k <= sim.riftRoomsDone
            val cur = k == sim.riftRoom
            val c = if (on) Color(0xFF7FD6A8) else if (cur) realmTint.copy(alpha = pulse) else Color(0xFF5A5470)
            drawRect(c, Offset(cx + (k - 2.5f) * u * 4f, cy - hh * 1.9f - u * 5f), Size(u * 3f, u * 3f))
        }
        if (sim.riftSideKind != 0 && sim.time - sim.riftSideAt < 25.0) {
            val sc = when (sim.riftSideKind) { 1 -> Color(0xFFE06A4A); 2 -> Color(0xFFFFD35A); 3 -> Color(0xFF6AC8FF); else -> Color(0xFFFF9AD0) }
            val rise = ((sim.time - sim.riftSideAt).toFloat() / 25f).coerceIn(0f, 1f)
            drawCircle(sc.copy(alpha = 0.9f * (1f - rise)), radius = u * 2.2f, center = Offset(cx, cy - hh * 1.9f - u * 11f - rise * u * 8f))
            drawCircle(Color.White.copy(alpha = 0.7f * (1f - rise)), radius = u * 0.9f, center = Offset(cx, cy - hh * 1.9f - u * 11f - rise * u * 8f))
        }
    }
}

/**
 * The bandit camp: a ring of sharpened stakes round two ragged tents, a smoking fire, a black banner and watchful guards,
 * with a health bar showing how battered it is. After it falls it smoulders for a while as charred ruins.
 */
fun DrawScope.campSite(sim: CivSim, g: Geo) {
    val u = g.ps
    val t = g.t
    val sinceRaze = (sim.time - sim.campRazedAt).toFloat()
    if (!sim.campUp) {
        if (sinceRaze < 0f || sinceRaze > 60f) return
        val fade = 1f - sinceRaze / 60f
        val rx = g.ox + sim.campRazedX * g.ts
        val ry = g.oy + sim.campRazedY * g.ts
        drawOval(Color(0xFF1E1A18).copy(alpha = 0.55f * fade), Offset(rx - u * 14f, ry - u * 3f), Size(u * 28f, u * 8f))
        for (k in 0 until 5) {
            drawRect(Color(0xFF2B221C).copy(alpha = fade), Offset(rx + (k - 2.5f) * u * 4.5f, ry - u * (2f + (k % 3) * 1.5f)), Size(u * 1.6f, u * (3f + (k % 3) * 1.5f)))
        }
        for (k in 0 until 8) {
            val p = (t * 0.5f + k * 0.125f) % 1f
            drawCircle(Color(0xFF5A5450).copy(alpha = 0.45f * (1f - p) * fade), radius = u * (1.5f + 2.5f * p), center = Offset(rx + sin(k * 1.9f + t) * u * 8f, ry - u * (3f + p * 18f)))
            drawRect(Color(0xFFFF8A3C).copy(alpha = 0.8f * (1f - p) * fade), Offset(rx + sin(k * 2.7f) * u * 9f, ry - u * (1f + p * 7f)), Size(u * 0.9f, u * 0.9f))
        }
        return
    }
    val hit = (sim.time - sim.campHitAt).toFloat()
    val shake = if (hit in 0f..0.6f) sin(t * 60f) * u * 0.8f else 0f
    val cx = g.ox + sim.campX * g.ts + shake
    val cy = g.oy + sim.campY * g.ts
    // ground shadow and trampled earth
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(cx - u * 17f, cy - u * 4f), Size(u * 34f, u * 10f))
    drawOval(Color(0xFF6B5638).copy(alpha = 0.55f), Offset(cx - u * 15f, cy - u * 3f), Size(u * 30f, u * 8f))
    // stake palisade across the back
    for (k in -6..6) {
        val sx = cx + k * u * 2.4f
        val sy = cy - u * 3.5f - abs(k) * u * 0.25f
        drawRect(Color(0xFF5A3F25), Offset(sx, sy - u * 6f), Size(u * 1.6f, u * 6f))
        drawRect(Color(0xFF7B5A38), Offset(sx, sy - u * 6f), Size(u * 0.6f, u * 6f))
        drawRect(Color(0xFF2E2118), Offset(sx + u * 0.4f, sy - u * 7.2f), Size(u * 0.8f, u * 1.4f))
    }
    // two ragged tents
    fun tent(x: Float, w: Float, h: Float, body: Color, shade: Color) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(x - w / 2f, cy + u); lineTo(x, cy + u - h); lineTo(x + w / 2f, cy + u); close()
        }
        drawPath(p, body)
        val q = androidx.compose.ui.graphics.Path().apply {
            moveTo(x, cy + u - h); lineTo(x + w / 2f, cy + u); lineTo(x + w * 0.1f, cy + u); close()
        }
        drawPath(q, shade)
        drawRect(Color(0xFF1B1411), Offset(x - w * 0.09f, cy + u - h * 0.45f), Size(w * 0.18f, h * 0.45f))
        drawRect(Color(0xFF3A2A1E), Offset(x - u * 0.3f, cy + u - h - u * 2f), Size(u * 0.6f, u * 2f))
    }
    tent(cx - u * 9f, u * 13f, u * 10f, Color(0xFF8E3B34), Color(0xFF6B2A25))
    tent(cx + u * 9.5f, u * 11f, u * 8.5f, Color(0xFFA68246), Color(0xFF7D6232))
    // fire in the middle, with smoke
    val fl = 0.7f + 0.3f * sin(t * 11f)
    drawCircle(Color(0xFFFF9A3C).copy(alpha = 0.2f * fl), radius = u * 9f, center = Offset(cx, cy))
    drawRect(Color(0xFF3A2A1E), Offset(cx - u * 2.2f, cy - u * 0.6f), Size(u * 4.4f, u * 1.2f))
    drawRect(Color(0xFFFF7A1E), Offset(cx - u * 1.2f, cy - u * (2.6f * fl + 0.6f)), Size(u * 2.4f, u * 2.6f * fl))
    drawRect(Color(0xFFFFD66B), Offset(cx - u * 0.6f, cy - u * (1.6f * fl + 0.4f)), Size(u * 1.2f, u * 1.6f * fl))
    for (k in 0 until 4) {
        val p = (t * 0.35f + k * 0.25f) % 1f
        drawCircle(Color(0xFF4A4642).copy(alpha = 0.5f * (1f - p)), radius = u * (1.2f + 2.4f * p), center = Offset(cx + sin(t + k) * u * 1.5f + p * u * 3f, cy - u * (3f + p * 22f)))
    }
    // black banner on a tall pole, flapping
    val px = cx + u * 1.5f
    drawRect(Color(0xFF3A2A1E), Offset(px, cy - u * 22f), Size(u * 0.9f, u * 20f))
    for (k in 0 until 6) {
        val wv = sin(t * 5f + k * 0.8f) * u * 0.6f
        drawRect(Color(0xFF1C1A22), Offset(px + u * 0.9f + k * u * 1.2f, cy - u * 22f + wv), Size(u * 1.3f, u * 6f))
    }
    drawRect(Color(0xFFE8E2D0), Offset(px + u * 3.2f, cy - u * 20.2f + sin(t * 5f + 2.4f) * u * 0.6f), Size(u * 1.4f, u * 1.4f))
    drawRect(Color(0xFFE8E2D0), Offset(px + u * 4.6f, cy - u * 20.2f + sin(t * 5f + 3.6f) * u * 0.6f), Size(u * 1.4f, u * 1.4f))
    // guards pacing with spears
    for (k in 0 until 2) {
        val gx = cx + sin(t * 0.7f + k * 3.1f) * u * 6f + (if (k == 0) -u * 3f else u * 4f)
        val gy = cy + u * (2f + k * 1.2f) - abs(sin(t * 5f + k)) * u * 0.6f
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(gx - u * 2f, gy - u * 0.4f), Size(u * 4f, u * 1.2f))
        drawRect(Color(0xFF2B2B33), Offset(gx - u * 1.2f, gy - u * 5f), Size(u * 2.4f, u * 4.6f))
        drawRect(Color(0xFFD9B08A), Offset(gx - u * 1f, gy - u * 7f), Size(u * 2f, u * 2f))
        drawRect(Color(0xFF1C1A22), Offset(gx - u * 1.2f, gy - u * 7.4f), Size(u * 2.4f, u * 1f))
        drawRect(Color(0xFF8B8F99), Offset(gx + u * 1.6f, gy - u * 9f), Size(u * 0.6f, u * 8.5f))
        drawRect(Color(0xFFC9CEDA), Offset(gx + u * 1.4f, gy - u * 10.4f), Size(u * 1f, u * 1.6f))
    }
    // sparks and a red flash when a villager lands a blow
    if (hit in 0f..0.7f) {
        for (k in 0 until 6) {
            val p = hit / 0.7f
            drawRect(Color(0xFFFFE08A).copy(alpha = 1f - p), Offset(cx + sin(k * 2.3f) * u * 9f * p, cy - u * (5f + k * 1.2f) - p * u * 4f), Size(u * 1f, u * 1f))
        }
    }
    // health bar
    val frac = (sim.campHp / sim.campMax).coerceIn(0f, 1f)
    val bw = u * 22f
    val by = cy - u * 27f
    drawRect(Color.Black.copy(alpha = 0.6f), Offset(cx - bw / 2f - u * 0.5f, by - u * 0.5f), Size(bw + u, u * 3f))
    drawRect(Color(0xFF5A2020), Offset(cx - bw / 2f, by), Size(bw, u * 2f))
    drawRect(if (frac > 0.5f) Color(0xFFD64545) else Color(0xFFFF9A3C), Offset(cx - bw / 2f, by), Size(bw * frac, u * 2f))
}

/** A little cluster of toadstools; glowcaps are blue-green and shine more the darker it gets. */
private fun DrawScope.mushrooms(x: Float, y: Float, u: Float, t: Float, glow: Boolean, dark: Float) {
    shadowAt(x, y, u * 6f, u)
    val stem = Color(0xFFF0E6CF)
    val capA = if (glow) Color(0xFF3FD0C0) else Color(0xFFC0392B)
    val capB = if (glow) Color(0xFF8CF2E4) else Color(0xFFE05A3A)
    if (glow) {
        val pulse = 0.55f + 0.45f * sin(t * 2.2f + x * 0.05f)
        val a = (0.18f + 0.4f * dark.coerceIn(0f, 1f)) * pulse
        drawCircle(Color(0xFF7FFFE8).copy(alpha = a), u * 7.5f, Offset(x, y - u * 2.5f))
        drawCircle(Color(0xFFB9FFF4).copy(alpha = a * 0.8f), u * 3.5f, Offset(x, y - u * 2.5f))
    }
    // tall one, short one, tiny one
    val xs = floatArrayOf(-u * 1.8f, u * 1.6f, u * 0.1f)
    val hs = floatArrayOf(u * 3.4f, u * 2.4f, u * 1.6f)
    val ws = floatArrayOf(u * 4.2f, u * 3.4f, u * 2.4f)
    for (k in 0 until 3) {
        val cx = x + xs[k]
        drawRect(stem, Offset(cx - u * 0.5f, y - hs[k]), Size(u * 1.0f, hs[k]))
        drawOval(capA, Offset(cx - ws[k] / 2f, y - hs[k] - u * 1.2f), Size(ws[k], u * 2.2f))
        drawOval(capB, Offset(cx - ws[k] / 2f + u * 0.3f, y - hs[k] - u * 1.2f), Size(ws[k] * 0.8f, u * 1.4f))
        if (!glow) drawRect(Color.White, Offset(cx - u * 0.2f, y - hs[k] - u * 0.6f), Size(u * 0.6f, u * 0.6f))
    }
}
