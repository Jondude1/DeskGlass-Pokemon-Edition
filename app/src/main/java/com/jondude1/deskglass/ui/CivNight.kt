package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.DAY_LEN
import com.jondude1.deskglass.sim.GH
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.T
import com.jondude1.deskglass.sim.Villager
import kotlin.math.abs
import kotlin.math.sin

/** Night and dawn visuals: carried lanterns, moon glitter on water, winter aurora, shooting stars and morning mist. */

/** True when a villager is out and about in the dark and carries a lantern. */
internal fun carriesLantern(v: Villager, dark: Float): Boolean =
    dark > 0.12f && v.action != "sleep" && v.action != "hide" && v.action != "huddle"

private fun nh(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

fun DrawScope.nightMagic(sim: CivSim, g: Geo) {
    val dark = g.dark
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    val ox = g.ox
    val oy = g.oy
    val fr = ((sim.time % DAY_LEN) / DAY_LEN).toFloat()
    val clear = !sim.raining() && !sim.foggy()
    val nightK = (dark / 0.55f).coerceIn(0f, 1f)

    // moonlight glitter on the water
    if (dark > 0.25f && clear) {
        val vx0 = kotlin.math.floor(-ox / ts).toInt().coerceIn(0, GW)
        val vx1 = (kotlin.math.ceil((size.width - ox) / ts).toInt() + 1).coerceIn(0, GW)
        val vy0 = kotlin.math.floor(-oy / ts).toInt().coerceIn(0, GH)
        val vy1 = (kotlin.math.ceil((size.height - oy) / ts).toInt() + 1).coerceIn(0, GH)
        for (y in vy0 until vy1) for (x in vx0 until vx1) {
            if (sim.tiles[y * GW + x] != T.WATER) continue
            val h = nh(x, y)
            if (h % 3 != 0) continue
            val tw = sin(t * 1.9f + (h % 17)).coerceIn(0f, 1f)
            if (tw < 0.08f) continue
            val sx = ox + x * ts + ((h shr 3) % 12 + 2) * ps
            val sy = oy + y * ts + ((h shr 7) % 12 + 2) * ps
            val c = Color(0xFFD6E6FF)
            drawRect(c.copy(alpha = 0.8f * tw * nightK), Offset(sx, sy), Size(ps * 1.5f, ps * 1.5f))
            drawRect(c.copy(alpha = 0.35f * tw * nightK), Offset(sx - ps * 1.2f, sy + ps * 0.4f), Size(ps * 4f, ps * 0.7f))
        }
    }

    // villagers carry little lanterns after dark
    if (dark > 0.12f) {
        var n = 0
        for (v in sim.villagers) {
            if (n >= 60) break
            if (!carriesLantern(v, dark)) continue
            n++
            val sway = sin(t * 5f + v.id) * ps * 0.4f
            val lx = ox + v.x * ts + ts * 0.32f + sway
            val ly = oy + (v.y - 0.35f) * ts
            val a = (dark * 1.4f).coerceIn(0f, 0.8f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFC46B).copy(alpha = a * 0.55f), Color.Transparent), center = Offset(lx, ly), radius = ts * 1.1f),
                radius = ts * 1.1f, center = Offset(lx, ly), blendMode = BlendMode.Plus,
            )
            drawRect(Color(0xFF4A3A2A), Offset(lx - ps * 0.5f, ly - ps * 1.6f), Size(ps * 1f, ps * 1.2f))
            drawRect(Color(0xFFFFE9A0).copy(alpha = 0.95f), Offset(lx - ps * 0.9f, ly - ps * 0.5f), Size(ps * 1.8f, ps * 2.1f))
        }
    }

    // aurora ribbons on clear winter nights
    if (sim.seasonIdx() == 3 && dark > 0.3f && clear) {
        val w = size.width
        val h = size.height
        val cols = 36
        val cw = w / cols + 1f
        val tints = listOf(Color(0xFF6BFFB0), Color(0xFF7FA8FF), Color(0xFFC08BFF))
        for (band in 0 until 3) {
            val base = h * (0.16f + 0.13f * band)
            for (i in 0 until cols) {
                val x = i * (w / cols)
                val wave = sin(x * 0.012f + t * 0.35f + band * 1.9f) * h * 0.05f + sin(x * 0.03f - t * 0.5f + band) * h * 0.02f
                val top = base + wave
                val ht = h * (0.16f + 0.05f * sin(t * 0.6f + i * 0.4f + band))
                val a = 0.20f * nightK * (0.6f + 0.4f * sin(t * 0.9f + i * 0.25f + band * 2f))
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, tints[band].copy(alpha = a), Color.Transparent), startY = top, endY = top + ht),
                    Offset(x, top), Size(cw, ht), blendMode = BlendMode.Plus,
                )
            }
        }
    }

    // a shooting star every little while
    if (dark > 0.3f && clear) {
        val period = 9f
        val k = (t / period).toInt()
        val ph = (t % period) / period
        if (ph < 0.07f) {
            val p = ph / 0.07f
            val h1 = (nh(k, 5) % 1000) / 1000f
            val h2 = (nh(k, 9) % 1000) / 1000f
            val sx = size.width * (0.1f + 0.6f * h1)
            val sy = size.height * (0.05f + 0.35f * h2)
            val dx = size.width * 0.28f
            val dy = size.height * 0.16f
            val hx = sx + dx * p
            val hy = sy + dy * p
            val tx = hx - dx * 0.22f
            val ty = hy - dy * 0.22f
            val fade = (1f - p) * nightK
            drawLine(Color(0xFFBFD8FF).copy(alpha = 0.55f * fade), Offset(tx, ty), Offset(hx, hy), strokeWidth = ps * 1.2f)
            drawCircle(Color.White.copy(alpha = 0.95f * fade), radius = ps * 1.5f, center = Offset(hx, hy))
        }
    }

    // morning mist hugs the ground at dawn
    val mist = (1f - abs(fr - 0.11f) / 0.1f).coerceIn(0f, 1f)
    if (mist > 0f && !sim.raining() && !sim.snowing()) {
        for (k in 0 until 7) {
            val span = size.width + ts * 12f
            val cx = -ts * 6f + ((t * ts * 0.18f + k * span / 7f) % span)
            val cy = oy + GH * ts * (0.18f + 0.11f * k)
            drawOval(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f * mist), Color.Transparent), center = Offset(cx, cy), radius = ts * 5f),
                topLeft = Offset(cx - ts * 6f, cy - ts * 1.6f), size = Size(ts * 12f, ts * 3.2f),
            )
        }
    }
}
