package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.GH
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.T

/** Tracks in the land: walkers leave dark footprints in fresh snow, muddy boot marks in the rain and kicked-up dust
 *  puffs in a drought. Beasts leave paw prints. Prints fade away. Pure drawing, sampled from live positions. */

private class Print(val x: Float, val y: Float, val born: Double, val kind: Int, val flip: Int, val paw: Boolean)

private object TrackBook {
    val prints = ArrayList<Print>()
    val last = HashMap<Int, FloatArray>()
    var lastTime = 0.0
}

private fun life(kind: Int): Double = when (kind) { 0 -> 45.0; 1 -> 28.0; else -> 7.0 }

private fun DrawScope.sampleTracks(sim: CivSim) {
    val book = TrackBook
    if (sim.time < book.lastTime - 1.0) { book.prints.clear(); book.last.clear() }
    book.lastTime = sim.time
    val now = sim.time
    while (book.prints.isNotEmpty() && now - book.prints[0].born > 60.0) book.prints.removeAt(0)
    var kind = -1
    if (sim.snowCover() > 0.35f) kind = 0
    else if (sim.raining()) kind = 1
    else if (sim.droughtLevel() > 0.15f) kind = 2
    if (kind < 0) { book.last.clear(); return }
    fun visit(key: Int, x: Float, y: Float, paw: Boolean) {
        if (x < 0f || y < 0f || x >= GW || y >= GH) return
        val ix = x.toInt()
        val iy = y.toInt()
        val tile = sim.tiles[iy * GW + ix]
        if (tile == T.WATER || tile == T.ROCK) { book.last.remove(key); return }
        val lp = book.last[key]
        if (lp == null) { book.last[key] = floatArrayOf(x, y, 0f); return }
        val dx = x - lp[0]
        val dy = y - lp[1]
        if (dx * dx + dy * dy < 0.36f) return
        if (kind == 0 && sim.owner[iy * GW + ix] >= 0 && ix % 3 == 0) { lp[0] = x; lp[1] = y; return }
        lp[0] = x; lp[1] = y; lp[2] = lp[2] + 1f
        if (book.prints.size < 320) book.prints.add(Print(x, y + 0.35f, now, kind, lp[2].toInt() and 1, paw))
    }
    for (v in sim.villagers) if (v.phase == "walking") visit(v.id, v.x, v.y, false)
    for (p in sim.predators) visit(1000 + System.identityHashCode(p) % 100000, p.x, p.y, true)
}

internal fun DrawScope.trackMarks(sim: CivSim, g: Geo, vx0: Int, vx1: Int, vy0: Int, vy1: Int) {
    sampleTracks(sim)
    val ts = g.ts
    val ps = g.ps
    for (p in TrackBook.prints) {
        val ix = p.x.toInt()
        val iy = p.y.toInt()
        if (ix < vx0 || ix >= vx1 || iy < vy0 || iy >= vy1) continue
        val age = ((sim.time - p.born) / life(p.kind)).toFloat()
        if (age >= 1f) continue
        val fade = 1f - age
        val px = g.ox + p.x * ts
        val py = g.oy + p.y * ts
        val side = if (p.flip == 0) -ps * 1.2f else ps * 1.2f
        when (p.kind) {
            0 -> { // dents in the snow with a pale rim
                val c = Color(0xFF8FA6C0).copy(alpha = 0.75f * fade)
                if (p.paw) {
                    drawRect(c, Offset(px - ps * 1.5f, py), Size(ps * 3f, ps * 1.6f))
                    drawRect(c, Offset(px - ps * 2.2f, py - ps * 1.4f), Size(ps * 1.1f, ps * 1.1f))
                    drawRect(c, Offset(px + ps * 1.1f, py - ps * 1.4f), Size(ps * 1.1f, ps * 1.1f))
                } else {
                    drawRect(c, Offset(px + side, py), Size(ps * 1.8f, ps * 2.6f))
                    drawRect(Color.White.copy(alpha = 0.5f * fade), Offset(px + side, py - ps * 0.6f), Size(ps * 1.8f, ps * 0.6f))
                }
            }
            1 -> { // wet mud marks
                val c = Color(0xFF4A331D).copy(alpha = 0.6f * fade)
                if (p.paw) {
                    drawRect(c, Offset(px - ps * 1.6f, py), Size(ps * 3.2f, ps * 1.8f))
                } else {
                    drawRect(c, Offset(px + side, py), Size(ps * 1.8f, ps * 2.4f))
                    drawRect(Color(0xFF7A9AB0).copy(alpha = 0.35f * fade), Offset(px + side, py + ps * 0.4f), Size(ps * 1.8f, ps * 0.7f))
                }
            }
            else -> { // dust kicked up, spreading and thinning
                val r = ps * (1.5f + 3.5f * age)
                val c = Color(0xFFE0CC9A).copy(alpha = 0.5f * fade)
                drawRect(c, Offset(px - r, py - r * 0.9f - ps * 3f * age), Size(r * 2f, r * 1.2f))
                drawRect(c.copy(alpha = 0.3f * fade), Offset(px - r * 0.6f, py - r * 1.5f - ps * 4f * age), Size(r * 1.2f, r * 0.8f))
            }
        }
    }
}
