package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.Villager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Weather and season outfits: leaf umbrellas and splashes in the rain, scarves and breath puffs in winter,
 * snow caps in a snowfall, sweat in summer heat, a blossom in spring, an autumn shawl, plus footprints in
 * snow and mud that slowly fade. Pure drawing, no simulation state.
 */
internal object FootTrail {
    class Print(val x: Float, val y: Float, val born: Double, val snow: Boolean, val side: Float)

    val prints = ArrayList<Print>()
    private val last = HashMap<Int, FloatArray>()
    private var lastTime = 0.0

    fun reset() { prints.clear(); last.clear() }

    fun begin(sim: CivSim) {
        if (sim.time < lastTime - 1.0) reset() // a new or loaded world
        lastTime = sim.time
        val it = prints.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (sim.time - p.born > (if (p.snow) 50.0 else 28.0)) it.remove()
        }
    }

    fun step(sim: CivSim, v: Villager, snow: Boolean) {
        val l = last.getOrPut(v.id) { floatArrayOf(v.x, v.y, 1f) }
        val dx = v.x - l[0]
        val dy = v.y - l[1]
        if (dx * dx + dy * dy < 0.36f) return
        l[0] = v.x; l[1] = v.y; l[2] = -l[2]
        if (prints.size > 180) prints.removeAt(0)
        prints.add(Print(v.x, v.y, sim.time, snow, l[2]))
    }
}

private val UMBRELLAS = longArrayOf(0xFF4E9E4A, 0xFFC0392B, 0xFF3A7BD5, 0xFFE0A526, 0xFF8E4FD9)

/** Footprints on the ground; drawn before the villagers so feet stand on top. */
fun DrawScope.footprints(sim: CivSim, g: Geo) {
    FootTrail.begin(sim)
    val ps = g.ps
    for (p in FootTrail.prints) {
        val age = (sim.time - p.born).toFloat()
        val life = if (p.snow) 50f else 28f
        val a = (1f - age / life).coerceIn(0f, 1f)
        val x = g.ox + p.x * g.ts + p.side * ps * 1.3f
        val y = g.oy + (p.y + 0.5f) * g.ts
        val col = if (p.snow) Color(0xFF8FA6C4) else Color(0xFF4B3A28)
        drawRect(col.copy(alpha = 0.55f * a), Offset(x - ps * 0.6f, y - ps * 0.8f), Size(ps * 1.2f, ps * 1.6f))
        if (p.snow) drawRect(Color.White.copy(alpha = 0.35f * a), Offset(x - ps * 0.6f, y + ps * 0.8f), Size(ps * 1.2f, ps * 0.5f))
    }
}

/** Seasonal and weather extras on one villager. cx = horizontal centre, top = top of the sprite, w = sprite width. */
internal fun DrawScope.weatherGear(
    sim: CivSim, v: Villager, g: Geo, cx: Float, top: Float, feetY: Float, w: Float, fx: Float,
    child: Boolean, working: Boolean, walking: Boolean,
) {
    val t = g.t
    val ps = g.ps
    val u = w / 8f
    val foes = sim.predators.isNotEmpty()
    val rain = sim.raining()
    val storm = sim.storm()
    val snowy = sim.snowing()
    val season = sim.seasonName()
    val winter = season == "winter"
    val night = g.dark > 0.3f

    // x in sprite units from the left of an unflipped sprite (0..8); mirrored with the facing
    fun r(c: Long, x: Float, y: Float, rw: Float, rh: Float, a: Float = 1f) {
        val left = if (fx >= 0f) cx + (x - 4f) * u else cx - (x - 4f) * u - rw * u
        drawRect(Color(c).copy(alpha = a), Offset(left, top + y * u), Size(rw * u, rh * u))
    }

    if (walking) FootTrail.step(sim, v, snowy || winter && !rain)

    // ---- rain: leaf umbrella, drips, puddle rings ----
    if (rain && !foes && !(working && v.action.startsWith("build"))) {
        val col = UMBRELLAS[v.id % UMBRELLAS.size]
        val sway = if (storm) sin(t * 9f + v.id) * 0.5f else 0f
        r(0xFF6B4A2B, 7.6f, -3.4f, 0.6f, 8.6f) // handle
        r(col, 0.6f + sway, -6.6f, 6.8f, 1.2f)
        r(col, -0.6f + sway, -5.4f, 9.2f, 1.2f)
        r(0xFF1E2A36, -0.6f + sway, -4.2f, 9.2f, 0.5f, 0.6f) // rim shade
        r(0xFFFFFFFF, 1.6f + sway, -6.4f, 1.6f, 0.5f, 0.5f) // glint
        for (k in 0 until 2) {
            val p = (t * 1.8f + k * 0.5f + v.id * 0.23f) % 1f
            val ex = if (k == 0) -0.4f else 8.6f
            r(0xFFBFE6FF, ex, -4.0f + p * 9f, 0.6f, 0.9f, 0.8f * (1f - p))
        }
        if (walking) {
            val p = (t * 2.4f + v.id * 0.3f) % 1f
            drawCircle(Color(0xFFBFE6FF).copy(alpha = 0.5f * (1f - p)), radius = ps * (1f + 3f * p), center = Offset(cx, feetY), style = Stroke(width = ps * 0.4f))
        }
    } else if (rain && !child && !foes && night.not() && working && sin(t * 5f + v.id) > 0.9f) {
        // soaked builders shake the water off
        drawCircle(Color(0xFFBFE6FF).copy(alpha = 0.7f), radius = ps * 0.7f, center = Offset(cx + fx * ps * 4f, top + u * 2f))
    }

    if (child && !rain) return

    // ---- winter: scarf, breath, snow cap ----
    if (winter && !rain) {
        val scarf = longArrayOf(0xFFC0392B, 0xFF3A7BD5, 0xFFE0A526, 0xFF2F9E6B)[v.id % 4]
        r(scarf, 1.4f, 3.6f, 5.2f, 1.1f)
        val flap = sin(t * 3.4f + v.id) * (if (walking) 1.1f else 0.4f)
        r(scarf, -0.8f, 3.9f + flap * 0.4f, 1.6f, 0.9f)
        r(scarf, -1.9f, 4.3f + flap, 1.4f, 0.9f)
        val p = (t * 0.55f + v.id * 0.31f) % 1f
        if (p < 0.6f) {
            val bx = cx + fx * u * 3.6f + fx * p * u * 3f
            drawCircle(Color.White.copy(alpha = 0.55f * (1f - p / 0.6f)), radius = u * (0.7f + p * 1.8f), center = Offset(bx, top + u * 2.6f - p * u * 2f))
        }
    }
    if (snowy && !rain) {
        r(0xFFF4F8FF, 1.4f, -0.8f, 5.2f, 1.1f) // snow cap
        r(0xFFF4F8FF, 0.6f, 3.2f, 1.2f, 0.7f) // shoulders
        r(0xFFF4F8FF, 6.2f, 3.2f, 1.2f, 0.7f)
    }

    // ---- summer heat ----
    if (season == "summer" && !rain && g.dark < 0.15f && (working || walking)) {
        val p = (t * 0.7f + v.id * 0.37f) % 1f
        if (p < 0.5f) r(0xFF7FC4F0, 6.6f, 0.2f + p * 5f, 0.8f, 1.1f, 1f - p * 2f)
    }

    // ---- spring blossom behind the ear ----
    if (season == "spring" && !rain && v.id % 3 == 0) {
        r(0xFFFF8FB1, 5.6f, 0.1f, 1.1f, 1.1f)
        r(0xFFFFE066, 5.9f, 0.4f, 0.5f, 0.5f)
    }

    // ---- autumn shawl and the odd drifting leaf ----
    if (season == "autumn" && !rain) {
        r(0xFFB5502A, 1.2f, 3.5f, 5.6f, 1.0f)
        r(0xFFD98A2B, 1.6f, 4.4f, 4.8f, 0.6f)
        val p = (t * 0.35f + v.id * 0.19f) % 1f
        val lc = if (v.id % 2 == 0) Color(0xFFD98A2B) else Color(0xFFB5502A)
        drawRect(lc.copy(alpha = 0.9f * (1f - p)), Offset(cx + sin(p * 9f + v.id) * u * 3f, top - u * 3f + p * u * 14f), Size(u * 0.9f, u * 0.6f))
    }
}
