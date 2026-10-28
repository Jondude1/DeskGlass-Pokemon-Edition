package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jondude1.deskglass.sim.CType
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.Critter
import com.jondude1.deskglass.sim.Swap
import com.jondude1.deskglass.sim.Villager
import com.jondude1.deskglass.sim.CritterWorld
import com.jondude1.deskglass.sim.Dex
import com.jondude1.deskglass.sim.Enc
import com.jondude1.deskglass.sim.EvolveScene
import com.jondude1.deskglass.sim.Wild
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Drawing for the critters: wild ones, helpers and companions, battles with their move effects, summoning rituals, bindings,
// glyph spells, infusion and evolution. Everything is drawn in the same pixel units (ps) as the rest of the village.

private const val TURN_IMPACT = 1.0f

private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun mixC(a: Color, b: Color, t: Float) = Color(
    lerpF(a.red, b.red, t), lerpF(a.green, b.green, t), lerpF(a.blue, b.blue, t), lerpF(a.alpha, b.alpha, t),
)
private fun typeColor(t: CType) = Color(t.color)
private fun hashI(a: Int, b: Int): Int {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return (h xor (h ushr 16)) and 0x7fffffff
}

/** One thing standing on the map this frame, drawn in order of how far down the screen it is. */
private class Actor(val y: Float, val draw: () -> Unit)

/** Where one fighter is, as a tile position plus a few animation knobs. */
private class Pose(var x: Float, var y: Float) {
    var sx = 1f
    var sy = 1f
    var alpha = 1f
    var flash = 0f
    var rot = 0f
    var shrinkIn = 0f   // 0..1 how far it has been pulled into a binding
    var whiteMix = 0f   // 0..1 how much of the white summoning light is still on it
    var show = true
}

private fun DrawScope.spr(
    img: ImageBitmap, cx: Float, base: Float, w: Float, h: Float, dir: Int,
    alpha: Float = 1f, sx: Float = 1f, sy: Float = 1f, rot: Float = 0f,
) {
    if (alpha <= 0.01f) return
    withTransform({
        scale(dir * sx, sy, Offset(cx, base))
        if (rot != 0f) rotate(rot, Offset(cx, base))
    }) {
        drawImage(
            img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset((cx - w / 2f).toInt(), (base - h).toInt()),
            dstSize = IntSize(max(1, w.toInt()), max(1, h.toInt())),
            alpha = alpha.coerceIn(0f, 1f),
            filterQuality = FilterQuality.None,
        )
    }
}

private fun DrawScope.critShadow(art: CivArt, img: ImageBitmap, cx: Float, base: Float, w: Float, h: Float, sun: Sun, ps: Float, alpha: Float = 1f) {
    drawOval(Color.Black.copy(alpha = 0.2f * alpha), Offset(cx - w * 0.42f, base - ps * 1.4f), Size(w * 0.84f, ps * 2.8f))
    castShadow(art, img, cx, base, w, h, sun)
}

private fun idleFrame(t: Float, seed: Int, moving: Boolean): Int =
    ((t * (if (moving) 6f else 2.1f)) + seed * 0.37f).toInt() and 1

private val ICON_HEART = listOf(".h.h.", "hhhhh", "hhhhh", ".hhh.", "..h..")
private val ICON_Q = listOf(".qqq.", "q...q", "...q.", ".....", "..q..")
private val ICON_Z = listOf("zzz", "..z", ".z.", "zzz")
private val ICON_STAR = listOf("..s..", "..s..", "sssss", ".sss.", ".s.s.")

private fun DrawScope.icon(rows: List<String>, c: Color, x: Float, y: Float, u: Float) {
    for (ry in rows.indices) for (cx in rows[ry].indices) {
        if (rows[ry][cx] != '.') drawRect(c, Offset(x + cx * u, y + ry * u), Size(u + 0.5f, u + 0.5f))
    }
}

// ---------------------------------------------------------------------------------------------
// Ordinary life: wild critters, helpers and companions, the training yard
// ---------------------------------------------------------------------------------------------
fun DrawScope.drawCritters(sim: CivSim, art: CivArt, cart: CritterArt, g: Geo, sun: Sun) {
    val cw = sim.critters
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    val actors = ArrayList<Actor>()
    val baseOf = { y: Float -> g.oy + (y + 0.45f) * ts }

    if (cw.circleX >= 0f) actors.add(Actor(-5f) { circleSite(sim, g) })

    // the practice dummy stands at the yard unless a lesson is using it
    if (cw.yardX >= 0f && cw.encounters.none { it.kind == "train" && it.state != "walk" }) {
        actors.add(Actor(cw.yardY) {
            val img = cart.frame(0, 0)
            val w = img.lw * ps
            val h = img.lh * ps
            val cx = g.ox + cw.yardX * ts
            val base = baseOf(cw.yardY)
            critShadow(art, img, cx, base, w, h, sun, ps)
            spr(img, cx, base, w, h, 1)
        })
    }

    for (wd in cw.wilds.toList()) {
        if (cw.encounters.any { it.wild === wd && it.state != "walk" }) continue
        actors.add(Actor(wd.y) { drawWildCritter(wd, cart, art, g, sun) })
    }

    for (c in cw.critters.toList()) {
        if (cw.evolving(c) != null || cw.packed(c)) continue
        if (cw.encounters.any { (it.mine === c || it.mine2 === c) && it.state != "walk" }) continue
        val b = cw.bodies[c.uid] ?: continue
        actors.add(Actor(b.y) { drawKeptCritter(cw, c, b.x, b.y, b.facing, b.moving, cart, art, g, sun) })
    }

    for (e in cw.encounters.toList()) {
        if (e.state == "walk") continue
        for (side in 0..1) {
            val sp = fighterSprite(e, side) ?: continue
            val p = pose(e, side, sim)
            if (!p.show) continue
            actors.add(Actor(p.y) { drawFighter(e, side, sp, p, cart, art, g, sun) })
        }
    }

    for (s in cw.evolutions.toList()) {
        actors.add(Actor(s.y) { drawEvolution(sim, s, cart, art, g, sun) })
    }

    actors.sortBy { it.y }
    for (a in actors) a.draw()
}

private fun DrawScope.drawWildCritter(w: Wild, cart: CritterArt, art: CivArt, g: Geo, sun: Sun) {
    val ps = g.ps
    val ts = g.ts
    val t = g.t
    val sp = w.c.sp
    val img = cart.frame(sp, idleFrame(t, sp + w.c.uid, w.moving))
    val wd = img.lw * ps
    val ht = img.lh * ps
    val cx = g.ox + w.x * ts
    val hop = if (w.moving) abs(sin(t * 9f + sp)) * ps * 1.2f else sin(t * 2.2f + sp) * ps * 0.25f
    val base = g.oy + (w.y + 0.45f) * ts
    val alpha = w.fade.coerceIn(0f, 1f)
    val species = w.c.species
    if (g.dark > 0.3f && (species.t1 == CType.GLOW || species.t1 == CType.SPIRIT || species.t2 == CType.GLOW || species.t2 == CType.SPIRIT)) {
        val glow = typeColor(if (species.t1 == CType.GLOW || species.t1 == CType.SPIRIT) species.t1 else species.t2!!)
        drawCircle(glow.copy(alpha = 0.16f * alpha), ts * 0.9f, Offset(cx, base - ht * 0.5f))
        drawCircle(glow.copy(alpha = 0.18f * alpha), ts * 0.5f, Offset(cx, base - ht * 0.5f))
    }
    if (alpha < 0.98f) riftFx(w.x, w.y, sin(alpha.coerceIn(0f, 1f) * PI.toFloat()), g)
    critShadow(art, img, cx, base, wd, ht, sun, ps, alpha)
    spr(img, cx, base - hop, wd, ht, w.facing, alpha)
    if (species.rarity >= 3 && alpha > 0.5f) {
        for (k in 0 until 2) {
            val a = t * 1.4f + k * 3.1f + sp
            val px = cx + cos(a) * wd * 0.5f
            val py = base - ht * 0.5f + sin(a * 1.3f) * ht * 0.45f
            val tw = (sin(t * 5f + k * 2f) * 0.5f + 0.5f)
            drawRect(Color(0xFFFFF4B0).copy(alpha = 0.3f + 0.6f * tw), Offset(px - ps * 0.7f, py - ps * 0.7f), Size(ps * 1.4f, ps * 1.4f))
        }
    }
}

private fun DrawScope.drawKeptCritter(
    cw: CritterWorld, c: Critter, x: Float, y: Float, facing: Int, moving: Boolean,
    cart: CritterArt, art: CivArt, g: Geo, sun: Sun,
) {
    val ps = g.ps
    val ts = g.ts
    val t = g.t
    val tired = cw.isTired(c)
    val img = cart.frame(c.sp, idleFrame(t, c.uid, moving && !tired))
    val wd = img.lw * ps
    val ht = img.lh * ps
    val cx = g.ox + x * ts
    val hop = if (tired) 0f else if (moving) abs(sin(t * 10f + c.uid)) * ps * 1.3f else sin(t * 2.3f + c.uid) * ps * 0.25f
    val base = g.oy + (y + 0.45f) * ts
    critShadow(art, img, cx, base, wd, ht, sun, ps)
    if (tired) {
        spr(img, cx, base, wd, ht, facing, 0.7f, 1f, 0.82f)
        val z = (t * 0.8f + c.uid * 0.3f) % 1f
        icon(ICON_Z, Color.White.copy(alpha = 1f - z), cx + wd * 0.25f + z * ps * 2f, base - ht - z * ps * 6f, ps * 0.9f)
    } else {
        spr(img, cx, base - hop, wd, ht, facing)
    }
    if (c.companion && !tired) {
        // a little bright mark above a companion, so you can tell who walks with whom
        val bx = cx
        val by = base - ht - ps * 4f - sin(t * 3f + c.uid) * ps * 0.5f
        icon(ICON_STAR, Color(0xFFFFE066), bx - ps * 2.5f, by, ps * 1f)
    }
    val job = c.work
    if (job > 0.01f && !tired && !moving) {
        // a helper at its job gives off a few bits of the type colour
        val k = typeColor(c.species.t1)
        for (i in 0 until 3) {
            val u = (t * 1.3f + i * 0.33f + c.uid * 0.1f) % 1f
            drawRect(k.copy(alpha = 0.8f * (1f - u)), Offset(cx + (i - 1) * ps * 3f, base - ht - u * ps * 7f), Size(ps * 1.3f, ps * 1.3f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Encounters
// ---------------------------------------------------------------------------------------------
private fun fighterSprite(e: Enc, side: Int): Int? =
    if (side == 0) e.a?.sp else (e.b?.sp ?: e.foe?.sp)

private fun fighterCritter(e: Enc, side: Int): Critter? =
    if (side == 0) e.a else (e.b ?: e.foe)

private fun pose(e: Enc, side: Int, sim: CivSim): Pose {
    val bx = if (side == 0) e.ax else e.bx
    val by = if (side == 0) e.ay else e.by
    val ox = if (side == 0) e.bx else e.ax
    val dir = if (ox >= bx) 1f else -1f
    val p = Pose(bx, by)
    val other = 1 - side
    if (e.kind == "summon" && side == 1 && e.state == "intro") p.show = false
    when (e.state) {
        "intro" -> {
            p.alpha = smooth(e.clock / 0.6f)
            val d = max(0f, 1f - e.clock / 0.9f)
            p.y -= abs(sin(e.clock * 9f)) * 0.12f * d
        }
        "turn" -> {
            val tn = e.turn
            val k = e.turnT
            if (tn != null) {
                if (tn.side == side) {
                    if (tn.move.fx == "lunge") {
                        val reach = max(0.2f, abs(ox - bx) - 0.7f)
                        val off = when {
                            k < 0.35f -> -0.14f * smooth(k / 0.35f)
                            k < 0.8f -> lerpF(-0.14f, reach, smooth((k - 0.35f) / 0.45f))
                            k < 1.0f -> reach
                            k < 1.6f -> lerpF(reach, 0f, smooth((k - 1.0f) / 0.6f))
                            else -> 0f
                        }
                        p.x += dir * off
                        p.y -= sin(smooth((k - 0.35f) / 0.65f).coerceIn(0f, 1f) * PI.toFloat()) * 0.1f
                    } else {
                        if (k < 0.7f) p.sy = 1f - 0.09f * sin(k / 0.7f * PI.toFloat())
                        val hk = ((k - 0.7f) / 0.5f).coerceIn(0f, 1f)
                        p.y -= sin(hk * PI.toFloat()) * 0.3f
                    }
                } else if (tn.hit) {
                    if (e.hurtSide == side && e.hurtT > 0f) {
                        p.x += sin(e.hurtT * 70f) * 0.06f - dir * 0.12f * (e.hurtT / 0.4f)
                        p.flash = if (((e.hurtT * 24f).toInt() and 1) == 0) 1f else 0f
                    }
                } else if (k in 1.0f..1.55f) {
                    p.x += -dir * 0.3f * sin((k - 1.0f) / 0.55f * PI.toFloat())
                }
            }
        }
        "snare" -> if (side == 1) {
            val t = e.snareT
            when {
                t < 0.7f -> {}
                t < 1.0f -> p.shrinkIn = (t - 0.7f) / 0.3f
                e.snareWon -> p.show = false
                t < 3.4f -> p.show = false
                else -> p.alpha = smooth((t - 3.4f) / 0.35f)
            }
        }
        "ritual" -> if (side == 1) {
            val c = e.clock
            if (c < 3.2f) p.show = false
            else {
                p.alpha = smooth((c - 3.2f) / 0.5f)
                p.whiteMix = 1f - smooth((c - 3.4f) / 0.7f)
                if (c < 4.2f) p.y -= sin(smooth((c - 3.2f) / 1.0f) * PI.toFloat()) * 0.08f
            }
        }
        "result" -> {
            val c = e.clock
            when (e.result) {
                "won" -> if (side == 1) {
                    p.alpha = (1f - c / 0.9f).coerceIn(0f, 1f)
                    p.sy = 1f - 0.4f * smooth(c / 0.9f)
                } else if (c < 2.4f) p.y -= abs(sin(c * 6f)) * 0.22f
                "lost" -> if (side == 0) {
                    p.alpha = lerpF(1f, 0.6f, smooth(c / 0.5f))
                    p.rot = 82f * smooth(c / 0.5f)
                }
                "caught" -> if (side == 1) p.show = false
                "failed" -> if (side == 1) {
                    p.alpha = (1f - c / 1.0f).coerceIn(0f, 1f)
                    p.whiteMix = smooth(c / 0.7f)
                    p.sy = 1f - 0.35f * smooth(c / 1.0f)
                }
                "spar" -> {
                    val win = e.battle?.winner ?: 0
                    if (win == side && c < 2.4f) p.y -= abs(sin(c * 6f)) * 0.22f
                }
                "trained" -> if (side == 0 && c < 2.4f) p.y -= abs(sin(c * 6f)) * 0.22f
            }
        }
    }
    // fighters face each other
    @Suppress("UNUSED_VARIABLE") val unused = other + sim.time.toInt()
    return p
}

private fun DrawScope.drawFighter(e: Enc, side: Int, sp: Int, p: Pose, cart: CritterArt, art: CivArt, g: Geo, sun: Sun) {
    val ps = g.ps
    val ts = g.ts
    val t = g.t
    val bx = if (side == 0) e.ax else e.bx
    val ox = if (side == 0) e.bx else e.ax
    val dir = if (e.kind == "summon") (if (e.vx >= e.bx) 1 else -1) else if (ox >= bx) 1 else -1
    val img = cart.frame(sp, if (sp == 0) 0 else idleFrame(t, sp + side, false))
    val wd = img.lw * ps
    val ht = img.lh * ps
    val cx = g.ox + p.x * ts
    val base = g.oy + (p.y + 0.45f) * ts
    if (p.shrinkIn > 0f) {
        // pulled into the binding as a white shape
        val k = p.shrinkIn
        val s = 1f - 0.85f * smooth(k)
        spr(cart.white(sp), cx, base - ht * 0.2f * smooth(k), wd, ht, dir, 1f - 0.2f * k, s, s)
        return
    }
    critShadow(art, img, cx, base, wd, ht, sun, ps, p.alpha)
    spr(img, cx, base, wd, ht, dir, p.alpha, p.sx, p.sy, p.rot * (if (dir == 1) 1f else 1f))
    if (p.flash > 0f) spr(cart.white(sp), cx, base, wd, ht, dir, 0.7f, p.sx, p.sy, p.rot)
    if (p.whiteMix > 0.01f) spr(cart.white(sp), cx, base, wd, ht, dir, p.whiteMix * p.alpha, p.sx, p.sy, p.rot)
}

/** Everything drawn over the villagers: move effects, rituals, bindings, glyph spells, ground rings and HP bars. */
fun DrawScope.drawCritterEffects(sim: CivSim, cart: CritterArt, g: Geo) {
    val cw = sim.critters
    for (e in cw.encounters.toList()) {
        if (e.state == "walk") continue
        if (e.kind != "summon") ring(e, g)
        when (e.state) {
            "turn" -> moveFx(e, g)
            "snare" -> bindFx(e, g)
            "ritual" -> ritualFx(e, g)
            "cast" -> castFx(e, g)
            "result" -> {
                if (e.result == "caught") caughtFx(e, g)
                if (e.result == "failed") ritualFx(e, g, fading = true)
            }
        }
        shieldFx(e, g)
        if (e.kind != "summon") hpBars(e, cart, g, sim)
    }
    for (s in cw.swaps.toList()) swapFx(sim, s, cart, g)
    for (s in cw.evolutions.toList()) evolutionOverlay(sim, s, g)
}

private fun DrawScope.ring(e: Enc, g: Geo) {
    val mx = g.ox + (e.ax + e.bx) / 2f * g.ts
    val my = g.oy + ((e.ay + e.by) / 2f + 0.45f) * g.ts
    val rx = (abs(e.bx - e.ax) / 2f + 0.9f) * g.ts
    val grow = smooth(e.clock / 0.7f)
    val pulse = 0.5f + 0.5f * sin(g.t * 4f)
    drawOval(
        Color.White.copy(alpha = (0.14f + 0.08f * pulse) * grow),
        Offset(mx - rx * grow, my - g.ts * 0.32f * grow), Size(rx * 2f * grow, g.ts * 0.64f * grow),
        style = Stroke(width = g.ps * 1.2f),
    )
}

private fun DrawScope.hpBars(e: Enc, cart: CritterArt, g: Geo, sim: CivSim) {
    @Suppress("UNUSED_VARIABLE") val unusedCart = cart
    @Suppress("UNUSED_VARIABLE") val unusedSim = sim
    for (side in 0..1) {
        val c = fighterCritter(e, side) ?: continue
        if (e.state == "result" && (e.result == "caught" || (e.result == "won" && side == 1 && e.clock > 1f))) continue
        if (e.state == "snare" && side == 1 && e.snareT > 0.7f) continue
        val sp = fighterSprite(e, side) ?: continue
        val p = pose(e, side, sim)
        val img = cart.frame(sp, 0)
        val ht = img.lh * g.ps
        val cx = g.ox + p.x * g.ts
        val top = g.oy + (p.y + 0.45f) * g.ts - ht - g.ps * 7f
        val bw = max(g.ts * 1.3f, g.ps * 22f)
        val disp = if (side == 0) e.dispA else e.dispB
        val frac = (disp / max(1, c.maxHp)).coerceIn(0f, 1f)
        val col = when {
            frac > 0.5f -> Color(0xFF4CCB5A)
            frac > 0.25f -> Color(0xFFF2C233)
            else -> Color(0xFFE5483C)
        }
        drawRect(Color(0xCC10141C), Offset(cx - bw / 2f - g.ps, top - g.ps), Size(bw + g.ps * 2f, g.ps * 4f))
        drawRect(Color(0xFF2B3340), Offset(cx - bw / 2f, top), Size(bw, g.ps * 2f))
        drawRect(col, Offset(cx - bw / 2f, top), Size(bw * frac, g.ps * 2f))
        drawRect(Color.White.copy(alpha = 0.35f), Offset(cx - bw / 2f, top), Size(bw * frac, g.ps * 0.7f))
    }
}

// ---- move effects ----

private fun DrawScope.bit(type: CType, x: Float, y: Float, s: Float, rot: Float, t: Float) {
    val c = typeColor(type)
    val lite = mixC(c, Color.White, 0.55f)
    val dark = mixC(c, Color.Black, 0.35f)
    withTransform({ rotate(rot, Offset(x, y)) }) {
        when (type) {
            CType.EMBER -> {
                drawCircle(c, s * 1.15f, Offset(x, y))
                drawCircle(Color(0xFFFFD94A), s * 0.7f, Offset(x, y))
                drawCircle(Color.White, s * 0.3f, Offset(x, y))
            }
            CType.TIDE -> {
                drawCircle(c, s, Offset(x, y))
                drawRect(c, Offset(x - s * 0.5f, y - s * 1.6f), Size(s, s * 0.9f))
                drawCircle(lite, s * 0.35f, Offset(x - s * 0.3f, y - s * 0.2f))
            }
            CType.LEAF -> {
                drawOval(c, Offset(x - s * 1.3f, y - s * 0.7f), Size(s * 2.6f, s * 1.4f))
                drawRect(dark, Offset(x - s * 1.2f, y - s * 0.1f), Size(s * 2.4f, s * 0.25f))
            }
            CType.SPARK -> {
                drawRect(c, Offset(x - s * 0.5f, y - s * 1.4f), Size(s, s * 2.8f))
                drawRect(c, Offset(x - s * 1.4f, y - s * 0.5f), Size(s * 2.8f, s))
                drawRect(Color.White, Offset(x - s * 0.3f, y - s * 0.3f), Size(s * 0.6f, s * 0.6f))
            }
            CType.STONE -> {
                drawRect(dark, Offset(x - s, y - s), Size(s * 2f, s * 2f))
                drawRect(c, Offset(x - s * 0.8f, y - s * 0.8f), Size(s * 1.6f, s * 1.6f))
                drawRect(lite, Offset(x - s * 0.6f, y - s * 0.6f), Size(s * 0.7f, s * 0.7f))
            }
            CType.GALE -> {
                drawArc(lite, 200f, 140f, false, Offset(x - s * 1.4f, y - s * 1.4f), Size(s * 2.8f, s * 2.8f), style = Stroke(width = s * 0.7f))
                drawArc(c, 210f, 110f, false, Offset(x - s * 0.9f, y - s * 0.9f), Size(s * 1.8f, s * 1.8f), style = Stroke(width = s * 0.5f))
            }
            CType.FROST -> {
                drawRect(lite, Offset(x - s * 0.5f, y - s * 1.5f), Size(s, s * 3f))
                drawRect(c, Offset(x - s * 1.5f, y - s * 0.5f), Size(s * 3f, s))
                drawRect(Color.White, Offset(x - s * 0.25f, y - s * 0.25f), Size(s * 0.5f, s * 0.5f))
            }
            CType.SHADE -> {
                drawCircle(dark, s * 1.2f, Offset(x, y))
                drawCircle(c, s * 0.8f, Offset(x, y))
                drawCircle(Color(0xFF1A1428), s * 0.45f, Offset(x + s * 0.2f, y))
            }
            CType.GLOW -> {
                drawCircle(c.copy(alpha = 0.35f), s * 1.8f, Offset(x, y))
                drawRect(c, Offset(x - s * 0.4f, y - s * 1.5f), Size(s * 0.8f, s * 3f))
                drawRect(c, Offset(x - s * 1.5f, y - s * 0.4f), Size(s * 3f, s * 0.8f))
                drawRect(Color.White, Offset(x - s * 0.4f, y - s * 0.4f), Size(s * 0.8f, s * 0.8f))
            }
            CType.VENOM -> {
                drawCircle(c, s * 1.05f, Offset(x, y))
                drawCircle(Color(0xFF9AE04A), s * 0.5f, Offset(x + s * 0.2f, y - s * 0.2f))
            }
            CType.IRON -> {
                drawRect(dark, Offset(x - s * 1.4f, y - s * 0.7f), Size(s * 2.8f, s * 1.4f))
                drawRect(c, Offset(x - s * 1.3f, y - s * 0.6f), Size(s * 2.6f, s * 0.7f))
                drawRect(Color(0xFFE0782A), Offset(x + s * 0.8f, y - s * 0.4f), Size(s * 0.6f, s * 0.8f))
            }
            CType.SPIRIT -> {
                drawCircle(c.copy(alpha = 0.4f), s * 1.6f, Offset(x, y))
                drawCircle(lite, s * 0.9f, Offset(x, y))
                drawRect(lite, Offset(x - s * 0.3f, y + s * 0.6f), Size(s * 0.6f, s * (1.2f + 0.4f * sin(t * 14f))))
            }
            CType.PLAIN -> {
                drawCircle(Color.White, s, Offset(x, y))
                drawCircle(Color(0xFFD8D0C0), s * 0.6f, Offset(x, y))
            }
        }
    }
}

private fun DrawScope.sparks(cx: Float, cy: Float, q: Float, col: Color, count: Int, radius: Float, ps: Float, seed: Int = 0) {
    if (q <= 0f || q >= 1f) return
    val r = radius * (1f - (1f - q) * (1f - q))
    for (i in 0 until count) {
        val ang = i * 2f * PI.toFloat() / count + (hashI(i, seed) % 100) / 100f
        val rr = r * (0.7f + (hashI(i, seed + 5) % 30) / 100f)
        val s = ps * (2.6f * (1f - q) + 0.6f)
        drawRect(col.copy(alpha = (1f - q).coerceIn(0f, 1f)), Offset(cx + cos(ang) * rr - s / 2f, cy + sin(ang) * rr - s / 2f), Size(s, s))
    }
}

private fun DrawScope.moveFx(e: Enc, g: Geo) {
    val tn = e.turn ?: return
    if (!tn.hit && e.turnT > 1.05f) return
    val ts = g.ts
    val ps = g.ps
    val k = e.turnT
    val ax = if (tn.side == 0) e.ax else e.bx
    val ay = if (tn.side == 0) e.ay else e.by
    val tx = if (tn.side == 0) e.bx else e.ax
    val ty = if (tn.side == 0) e.by else e.ay
    val a = Offset(g.ox + ax * ts, g.oy + (ay + 0.45f) * ts - ts * 0.4f)
    val d = Offset(g.ox + tx * ts, g.oy + (ty + 0.45f) * ts - ts * 0.4f)
    val type = tn.move.type
    val col = typeColor(type)
    val lite = mixC(col, Color.White, 0.55f)
    val after = (k - TURN_IMPACT) / 0.55f
    val big = tn.move.tier >= 3
    val size = ps * (if (big) 3.2f else if (tn.move.tier == 2) 2.6f else 2.0f)
    when (tn.move.fx) {
        "lunge" -> {
            if (k in 0.35f..1.0f) {
                // a smear of the type colour behind the charging critter
                val u = (k - 0.35f) / 0.65f
                for (i in 1..4) {
                    val bx = lerpF(a.x, d.x, smooth(u) * 0.8f) - (d.x - a.x).let { if (it >= 0f) 1f else -1f } * i * ps * 2.2f
                    drawRect(col.copy(alpha = 0.28f * (1f - i / 5f)), Offset(bx - ps * 2f, a.y - ps * 3f), Size(ps * 4f, ps * 6f))
                }
            }
            if (after in 0f..1f) {
                for (i in 0 until 3) {
                    val off = (i - 1) * ps * 4f
                    val len = ts * 0.6f * (1f - after * 0.4f)
                    drawLine(Color.White.copy(alpha = 1f - after), Offset(d.x - len / 2f + off, d.y - len / 2f), Offset(d.x + len / 2f + off, d.y + len / 2f), strokeWidth = ps * 1.8f)
                    drawLine(col.copy(alpha = 1f - after), Offset(d.x - len / 2f + off + ps, d.y - len / 2f), Offset(d.x + len / 2f + off + ps, d.y + len / 2f), strokeWidth = ps * 1.1f)
                }
                sparks(d.x, d.y, after, lite, 8, ts * 0.7f, ps, 3)
            }
        }
        "shot" -> {
            if (k in 0.35f..1.0f) {
                val u = (k - 0.35f) / 0.65f
                for (i in 4 downTo 0) {
                    val uu = (u - i * 0.045f).coerceIn(0f, 1f)
                    val px = lerpF(a.x, d.x, smooth(uu))
                    val py = lerpF(a.y, d.y, smooth(uu)) - sin(uu * PI.toFloat()) * ts * 0.5f
                    if (i == 0) bit(type, px, py, size, k * 360f * (if (type == CType.LEAF || type == CType.STONE) 1.4f else 0f), g.t)
                    else drawRect(col.copy(alpha = 0.4f * (1f - i / 5f)), Offset(px - size * 0.4f, py - size * 0.4f), Size(size * 0.8f, size * 0.8f))
                }
            } else if (k < 0.35f) {
                drawCircle(col.copy(alpha = 0.5f * (k / 0.35f)), size * (0.5f + k * 2f), a)
            }
            if (after in 0f..1f) {
                drawCircle(lite.copy(alpha = 0.7f * (1f - after)), ts * 0.55f * after + ps, d)
                sparks(d.x, d.y, after, col, 10, ts * 0.8f, ps, 7)
            }
        }
        "beam" -> {
            if (k in 0.4f..1.2f) {
                val u = ((k - 0.4f) / 0.8f)
                val fade = if (u > 0.75f) (1f - u) / 0.25f else 1f
                val segs = 9
                var prev = a
                val frame = (k * 26f).toInt()
                for (i in 1..segs) {
                    val f = i / segs.toFloat()
                    val jx = if (i == segs) 0f else ((hashI(i, frame) % 100) / 100f - 0.5f) * ts * 0.5f
                    val jy = if (i == segs) 0f else ((hashI(i + 9, frame) % 100) / 100f - 0.5f) * ts * 0.5f
                    val nx = lerpF(a.x, d.x, f) + jx
                    val ny = lerpF(a.y, d.y, f) + jy
                    drawLine(col.copy(alpha = 0.35f * fade), prev, Offset(nx, ny), strokeWidth = ps * 5f)
                    drawLine(lite.copy(alpha = fade), prev, Offset(nx, ny), strokeWidth = ps * 2.2f)
                    drawLine(Color.White.copy(alpha = fade), prev, Offset(nx, ny), strokeWidth = ps * 0.9f)
                    prev = Offset(nx, ny)
                }
                drawCircle(col.copy(alpha = 0.5f * fade), ps * 5f, a)
            }
            if (after in 0f..1f) {
                drawCircle(Color.White.copy(alpha = 0.8f * (1f - after)), ts * 0.4f * (1f - after) + ps, d)
                sparks(d.x, d.y, after, lite, 10, ts * 0.8f, ps, 11)
            }
        }
        "burst" -> {
            if (k in 0.15f..0.9f) {
                val u = (k - 0.15f) / 0.75f
                drawCircle(col.copy(alpha = 0.7f * u), ts * 0.55f * (1f - u) + ps, a, style = Stroke(width = ps * 1.4f))
                drawCircle(lite.copy(alpha = 0.5f * u), ps * 2.5f * u, a)
            }
            if (after in 0f..1f) {
                for (i in 0 until 3) {
                    val r = (after - i * 0.12f).coerceIn(0f, 1f)
                    if (r > 0f) drawCircle(col.copy(alpha = 0.8f * (1f - r)), ts * (0.25f + 0.95f * r), d, style = Stroke(width = ps * (2.2f - r)))
                }
                drawCircle(lite.copy(alpha = 0.5f * (1f - after)), ts * 0.5f * (1f - after), d)
                sparks(d.x, d.y, after, col, 14, ts * 1.0f, ps, 21)
            }
        }
        "rain" -> {
            val n = if (big) 9 else 6
            if (k in 0.15f..1.0f) {
                drawOval(col.copy(alpha = 0.18f + 0.1f * sin(k * 20f)), Offset(d.x - ts * 0.7f, d.y + ts * 0.15f), Size(ts * 1.4f, ts * 0.4f))
            }
            for (i in 0 until n) {
                val start = 0.15f + i * (0.62f / n)
                val u = (k - start) / 0.28f
                val ox = ((hashI(i, 3) % 100) / 100f - 0.5f) * ts * 1.2f
                val land = Offset(d.x + ox, d.y + ((hashI(i, 4) % 100) / 100f - 0.3f) * ts * 0.4f)
                if (u in 0f..1f) {
                    val y = lerpF(land.y - ts * 3.2f, land.y, u * u)
                    bit(type, land.x, y, size, if (type == CType.STONE || type == CType.IRON || type == CType.FROST) u * 200f else 0f, g.t)
                } else if (u in 1f..1.6f) {
                    val q = (u - 1f) / 0.6f
                    drawCircle(col.copy(alpha = 0.7f * (1f - q)), ps * (2f + 5f * q), land, style = Stroke(width = ps * 1.2f))
                    sparks(land.x, land.y, q, lite, 5, ts * 0.35f, ps, i)
                }
            }
        }
        "swirl" -> {
            if (k in 0.25f..1.45f) {
                val u = (k - 0.25f) / 1.2f
                val fade = if (u > 0.8f) (1f - u) / 0.2f else 1f
                for (i in 0 until 14) {
                    val ang = k * 9f + i * 0.45f
                    val r = ts * (1.05f - 0.75f * u) * (0.55f + 0.45f * (i % 3) / 2f)
                    val px = d.x + cos(ang) * r
                    val py = d.y + sin(ang) * r * 0.55f - (i % 5) * ps * 0.6f
                    val s = ps * (1.2f + (i % 3) * 0.6f)
                    drawRect((if (i % 2 == 0) col else lite).copy(alpha = fade), Offset(px - s / 2f, py - s / 2f), Size(s, s))
                }
                drawCircle(col.copy(alpha = 0.18f * fade), ts * 0.7f, d)
            }
            if (after in 0f..1f) sparks(d.x, d.y, after, lite, 10, ts * 0.9f, ps, 31)
        }
    }
}

// ---- summoning magic ----

private val VIOLET = Color(0xFFB48CFF)

private fun DrawScope.handOf(v: Villager, g: Geo): Offset =
    Offset(g.ox + (v.x + v.facing * 0.38f) * g.ts, g.oy + (v.y + 0.45f) * g.ts - g.ts * 0.62f)

/** A ring of floating runes around a point on the ground. [prog] 0..1 lights the runes one after another. */
private fun DrawScope.runeRing(cx: Float, cy: Float, r: Float, prog: Float, col: Color, alpha: Float, spin: Float, ps: Float) {
    if (alpha <= 0.01f || prog <= 0f) return
    val n = 10
    val twoPi = 2f * PI.toFloat()
    drawOval(col.copy(alpha = 0.3f * alpha), Offset(cx - r, cy - r * 0.5f), Size(r * 2f, r), style = Stroke(width = ps * 0.8f))
    for (i in 0 until n) {
        if (i.toFloat() / n > prog) break
        val a = spin + i * twoPi / n
        val x = cx + cos(a) * r
        val y = cy + sin(a) * r * 0.5f - ps * 1.2f * (0.5f + 0.5f * sin(spin * 2f + i))
        val sz = ps * 1.5f
        // a tiny rune: a bar and a stroke crossing it, different for odd and even
        if (i % 2 == 0) {
            drawRect(col.copy(alpha = alpha), Offset(x - sz / 2f, y - sz * 1.4f), Size(sz, sz * 2.8f))
            drawRect(Color.White.copy(alpha = alpha * 0.8f), Offset(x - sz * 1.2f, y - sz * 0.4f), Size(sz * 2.4f, sz * 0.8f))
        } else {
            drawRect(col.copy(alpha = alpha), Offset(x - sz * 1.2f, y - sz * 0.5f), Size(sz * 2.4f, sz))
            drawRect(Color.White.copy(alpha = alpha * 0.8f), Offset(x - sz * 0.4f, y - sz * 1.4f), Size(sz * 0.8f, sz * 2.8f))
        }
    }
}

/**
 * The rift gate: two rune-carved standing stones with a veil of violet light between them. [open] 0..1 is how far the
 * veil has been drawn open, [lit] 0..1 how many of the runes on the stones have woken.
 */
private fun DrawScope.riftGate(cx: Float, baseY: Float, ts: Float, ps: Float, open: Float, lit: Float, col: Color, alpha: Float, t: Float) {
    if (alpha <= 0.01f) return
    val half = ts * 0.95f
    val ph = ts * 1.75f
    val pw = ts * 0.3f
    val stone = Color(0xFF6C7080)
    val stoneLite = Color(0xFF9096A6)
    val stoneDark = Color(0xFF454957)
    // the veil first, so the stones stand in front of it
    if (open > 0.02f) {
        val vh = (ph - ts * 0.1f) * (0.25f + 0.75f * open)
        val vw = (half * 2f - pw) * (0.45f + 0.55f * open)
        val top = baseY - vh
        drawOval(col.copy(alpha = 0.18f * alpha * open), Offset(cx - vw / 2f - ps * 2f, top - ps * 2f), Size(vw + ps * 4f, vh + ps * 4f))
        drawOval(col.copy(alpha = 0.32f * alpha * open), Offset(cx - vw / 2f, top), Size(vw, vh))
        drawOval(Color.White.copy(alpha = 0.22f * alpha * open), Offset(cx - vw * 0.32f, top + vh * 0.12f), Size(vw * 0.64f, vh * 0.76f))
        for (i in 0 until 9) {
            val a = t * 2.6f + i * 0.7f
            val k = (i / 9f)
            val rr = (0.15f + 0.85f * k)
            drawRect(
                Color.White.copy(alpha = (0.35f + 0.5f * sin(a * 1.7f + i).let { it * it }) * alpha * open),
                Offset(cx + cos(a) * vw * 0.42f * rr - ps * 0.6f, top + vh * 0.5f + sin(a) * vh * 0.42f * rr - ps * 0.6f),
                Size(ps * 1.2f, ps * 1.2f),
            )
        }
    }
    for (side in intArrayOf(-1, 1)) {
        val x0 = cx + side * half - pw / 2f
        val top = baseY - ph
        drawRect(Color.Black.copy(alpha = 0.22f * alpha), Offset(x0 - ps, baseY - ps * 1.5f), Size(pw + ps * 3f, ps * 3f))
        drawRect(stone.copy(alpha = alpha), Offset(x0, top), Size(pw, ph))
        drawRect(stoneLite.copy(alpha = alpha), Offset(x0, top), Size(pw * 0.3f, ph))
        drawRect(stoneDark.copy(alpha = alpha), Offset(x0 + pw * 0.75f, top), Size(pw * 0.25f, ph))
        drawRect(stoneLite.copy(alpha = alpha), Offset(x0 - ps, top - ps * 1.5f), Size(pw + ps * 2f, ps * 2f))
        for (r in 0 until 4) {
            val on = lit * 4f - r
            if (on <= 0f) continue
            val ry = top + ph * (0.2f + 0.19f * r)
            val a = min(1f, on) * (0.75f + 0.25f * sin(t * 4f + r + side))
            drawRect(col.copy(alpha = 0.35f * a * alpha), Offset(x0 + pw * 0.2f - ps, ry - ps), Size(pw * 0.6f + ps * 2f, ps * 3.6f))
            drawRect(Color.White.copy(alpha = a * alpha), Offset(x0 + pw * 0.3f, ry), Size(pw * 0.4f, ps * 1.6f))
        }
    }
    // a worn lintel stone between the tops, lit by the veil
    drawRect(stone.copy(alpha = alpha), Offset(cx - half - pw / 2f, baseY - ph - ps * 3.5f), Size(half * 2f + pw, ps * 3.5f))
    drawRect(stoneLite.copy(alpha = alpha), Offset(cx - half - pw / 2f, baseY - ph - ps * 3.5f), Size(half * 2f + pw, ps * 1.2f))
}

private fun glyphStrokes(kind: String): List<List<Pair<Float, Float>>> = when (kind) {
    "heal" -> listOf(
        listOf(0f to -1f, 0f to 1f), listOf(-1f to 0f, 1f to 0f),
        listOf(0f to -0.6f, 0.6f to 0f, 0f to 0.6f, -0.6f to 0f, 0f to -0.6f),
    )
    "shield" -> listOf(listOf(-0.8f to -0.8f, 0.8f to -0.8f, 0.8f to 0.1f, 0f to 1f, -0.8f to 0.1f, -0.8f to -0.8f), listOf(0f to -0.6f, 0f to 0.5f))
    "fire" -> listOf(listOf(0f to -1f, 0.5f to -0.2f, 0.3f to 0.2f, 0.6f to 0.7f, 0f to 1f, -0.6f to 0.7f, -0.4f to 0.1f, -0.5f to -0.3f, 0f to -1f))
    "lightning" -> listOf(listOf(0.3f to -1f, -0.4f to -0.1f, 0.1f to -0.1f, -0.3f to 1f, 0.5f to 0.1f, 0f to 0.1f, 0.3f to -1f))
    else -> listOf(
        (0..14).map { i ->
            val a = i * 0.55f
            val r = 0.1f + i * 0.06f
            (cos(a) * r) to (sin(a) * r)
        },
        listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f, -1f to -1f),
    )
}

/** A glyph traced in the air, stroke by stroke. */
private fun DrawScope.glyph(kind: String, cx: Float, cy: Float, size: Float, prog: Float, col: Color, alpha: Float, w: Float) {
    val strokes = glyphStrokes(kind)
    var total = 0
    for (st in strokes) total += st.size - 1
    var budget = prog.coerceIn(0f, 1f) * total
    for (st in strokes) {
        for (i in 0 until st.size - 1) {
            val f = budget.coerceIn(0f, 1f)
            budget -= 1f
            if (f <= 0f) return
            val a = st[i]
            val b = st[i + 1]
            val p0 = Offset(cx + a.first * size, cy + a.second * size)
            val p1 = Offset(cx + lerpF(a.first, b.first, f) * size, cy + lerpF(a.second, b.second, f) * size)
            drawLine(col.copy(alpha = 0.35f * alpha), p0, p1, strokeWidth = w * 3f)
            drawLine(col.copy(alpha = alpha), p0, p1, strokeWidth = w)
            drawLine(Color.White.copy(alpha = alpha * 0.8f), p0, p1, strokeWidth = w * 0.4f)
        }
    }
}

private fun glyphColor(kind: String): Color = when (kind) {
    "heal" -> Color(0xFF6BE08A)
    "shield" -> Color(0xFF9FD8FF)
    "fire" -> Color(0xFFFF9A4A)
    "lightning" -> Color(0xFFFFE066)
    else -> Color(0xFFC9B8FF)
}

private fun DrawScope.scrollIcon(at: Offset, ps: Float, glow: Float) {
    if (glow > 0.01f) drawCircle(VIOLET.copy(alpha = 0.3f * glow), ps * 5f, at)
    drawRect(Color(0xFF6B4A2B), Offset(at.x - ps * 3.4f, at.y - ps * 2.2f), Size(ps * 6.8f, ps * 4.4f))
    drawRect(Color(0xFFE9D9A8), Offset(at.x - ps * 2.8f, at.y - ps * 1.8f), Size(ps * 5.6f, ps * 3.6f))
    for (i in 0 until 3) {
        drawRect(Color(0xFF7A4B24).copy(alpha = 0.8f), Offset(at.x - ps * 2.2f, at.y - ps * 1.1f + i * ps * 1.1f), Size(ps * (3.6f - i * 0.5f), ps * 0.45f))
    }
    if (glow > 0.01f) drawRect(VIOLET.copy(alpha = glow), Offset(at.x - ps * 0.6f, at.y - ps * 0.6f), Size(ps * 1.2f, ps * 1.2f))
}

/** The village's rift gate, always standing where summoners work; its veil glows brighter at night. */
internal fun DrawScope.circleSite(sim: CivSim, g: Geo) {
    val cw = sim.critters
    if (cw.circleX < 0f) return
    if (cw.encounters.any { it.kind == "summon" && it.state != "walk" }) return
    val ground = Offset(g.ox + cw.circleX * g.ts, g.oy + (cw.circleY + 0.45f) * g.ts)
    val open = 0.14f + 0.18f * g.dark + 0.04f * sin(g.t * 1.6f)
    riftGate(ground.x, ground.y, g.ts, g.ps, open, 0.55f + 0.45f * g.dark, VIOLET, 1f, g.t)
    if (g.dark > 0.3f) {
        for (i in 0 until 4) {
            val u = (g.t * 0.25f + i * 0.25f) % 1f
            drawRect(Color.White.copy(alpha = 0.5f * (1f - u) * g.dark), Offset(ground.x + (i - 1.5f) * g.ps * 5f, ground.y - u * g.ts * 1.2f), Size(g.ps * 1.1f, g.ps * 1.1f))
        }
    }
}

/** A wild critter slipping through (or back into) a rift: a violet whirl on the ground and a thin column of light. */
private fun DrawScope.riftFx(x: Float, y: Float, open: Float, g: Geo) {
    if (open <= 0.02f) return
    val c = Offset(g.ox + x * g.ts, g.oy + (y + 0.45f) * g.ts)
    val r = g.ts * (0.3f + 0.45f * open)
    drawOval(VIOLET.copy(alpha = 0.25f * open), Offset(c.x - r, c.y - r * 0.5f), Size(r * 2f, r), style = Stroke(width = g.ps * 1.6f))
    drawOval(Color.White.copy(alpha = 0.3f * open), Offset(c.x - r * 0.6f, c.y - r * 0.3f), Size(r * 1.2f, r * 0.6f), style = Stroke(width = g.ps))
    for (i in 0 until 5) {
        val a = g.t * 4f + i * 1.26f
        drawRect(VIOLET.copy(alpha = 0.8f * open), Offset(c.x + cos(a) * r * 0.9f - g.ps * 0.6f, c.y + sin(a) * r * 0.45f - g.ps * 0.6f), Size(g.ps * 1.2f, g.ps * 1.2f))
    }
    drawRect(Color.White.copy(alpha = 0.16f * open), Offset(c.x - g.ps * 1.5f, c.y - g.ts * 1.4f * open), Size(g.ps * 3f, g.ts * 1.4f * open))
}

private fun DrawScope.ritualFx(e: Enc, g: Geo, fading: Boolean = false) {
    val ts = g.ts
    val ps = g.ps
    val c = if (fading) 5.4f + e.clock else e.clock
    val ground = Offset(g.ox + e.bx * ts, g.oy + (e.by + 0.45f) * ts)
    val hand = handOf(e.v, g)
    val prog = (c / 1.5f).coerceIn(0f, 1f)
    val fail = !e.ritualWon
    val flick = if (fail && c > 3.2f && ((c * 16f).toInt() and 1) == 0) 0.35f else 1f
    val out = if (c > 4.8f) (1f - (c - 4.8f) / 0.6f).coerceIn(0f, 1f) else 1f
    val pulse = 0.9f + 0.1f * sin(c * 7f)
    val open = when {
        fail && c > 3.4f -> 1f - smooth((c - 3.4f) / 1.2f)
        c > 3.6f -> 1f - smooth((c - 3.6f) / 1.0f)
        else -> smooth((c - 0.8f) / 2.4f)
    }
    val gateCol = if (fail && c > 3.2f) Color(0xFFFF6A7A) else VIOLET
    riftGate(ground.x, ground.y, ts, ps, open, prog, gateCol, flick * pulse, c)
    if (c < 3.4f) {
        val glow = if (c in 1.2f..3.2f) 0.6f + 0.4f * sin(c * 9f) else 0f
        scrollIcon(Offset(hand.x, hand.y - ps * 2f), ps, glow)
        if (c in 1.4f..3.3f) {
            // runes lift off the scroll and drift into the gate
            for (i in 0 until 7) {
                val u = ((c - 1.4f) * 0.7f + i / 7f) % 1f
                val px = lerpF(hand.x, ground.x, smooth(u))
                val py = lerpF(hand.y, ground.y - ts * 0.8f, smooth(u)) - sin(u * PI.toFloat()) * ts * 0.7f
                drawRect(VIOLET.copy(alpha = 1f - u * 0.5f), Offset(px - ps * 0.7f, py - ps * 0.7f), Size(ps * 1.4f, ps * 1.4f))
            }
        }
    }
    if (c in 3.2f..3.8f) {
        val u = (c - 3.2f) / 0.6f
        drawCircle(Color.White.copy(alpha = 0.85f * (1f - u)), ts * (0.3f + 1.1f * u), Offset(ground.x, ground.y - ts * 0.8f))
    }
    if (fail && c > 3.3f) {
        val u = ((c - 3.3f) / 1.2f).coerceIn(0f, 1f)
        for (i in 0 until 5) {
            val a = i * 1.26f + 0.4f
            val len = ts * (0.4f + 0.7f * u)
            drawLine(Color(0xFFFF6A7A).copy(alpha = 0.8f * (1f - u * 0.4f) * out), Offset(ground.x, ground.y - ts * 0.8f),
                Offset(ground.x + cos(a) * len, ground.y - ts * 0.8f + sin(a) * len), strokeWidth = ps)
        }
    }
    if (!fail && c in 3.9f..5.4f) {
        // the bond: a thread of light between summoner and critter, with pulses running along it
        val to = Offset(ground.x, ground.y - ts * 0.45f)
        val chest = Offset(hand.x, hand.y)
        val a = (0.35f + 0.25f * sin(c * 8f)) * out
        drawLine(VIOLET.copy(alpha = a), chest, to, strokeWidth = ps * 1.6f)
        for (i in 0 until 3) {
            val u = (c * 1.3f + i / 3f) % 1f
            drawCircle(Color.White.copy(alpha = out), ps * 1.1f, Offset(lerpF(chest.x, to.x, u), lerpF(chest.y, to.y, u)))
        }
    }
}

private fun DrawScope.bindFx(e: Enc, g: Geo) {
    val ts = g.ts
    val ps = g.ps
    val t = e.snareT
    val c = Offset(g.ox + e.bx * ts, g.oy + (e.by + 0.45f) * ts)
    val orb = Offset(c.x, c.y - ts * 0.4f)
    val hand = handOf(e.v, g)
    val prog = (t / 0.7f).coerceIn(0f, 1f)
    val out = if (t > 3.7f) (1f - (t - 3.7f) / 0.5f).coerceIn(0f, 1f) else 1f
    runeRing(c.x, c.y - ps * 1.5f, ts * (1.25f - 0.35f * prog), prog, VIOLET, 0.95f * out, t * 1.4f, ps)
    if (t < 1.2f) {
        val u = (t / 0.5f).coerceIn(0f, 1f)
        drawLine(VIOLET.copy(alpha = 0.55f * (1f - t / 1.2f)), hand, Offset(lerpF(hand.x, c.x, u), lerpF(hand.y, c.y - ts * 0.3f, u)), strokeWidth = ps * 1.5f)
    }
    when {
        t < 0.7f -> {}
        t < 1.0f -> {
            val u = (t - 0.7f) / 0.3f
            drawCircle(VIOLET.copy(alpha = 0.35f), ps * (2f + 5f * u), orb)
            drawCircle(Color.White, ps * (0.8f + 2.4f * u), orb)
        }
        t < 3.4f -> {
            val hum = 0.85f + 0.15f * sin(t * 12f)
            drawCircle(VIOLET.copy(alpha = 0.35f * out), ps * 6f * hum, orb)
            drawCircle(Color.White.copy(alpha = out), ps * 3.2f, orb)
            for (b in 0 until 3) {
                val s = 1.1f + b * 0.8f
                if (t in s..(s + 0.5f)) {
                    val u = (t - s) / 0.5f
                    drawCircle(Color.White.copy(alpha = 0.7f * (1f - u)), ps * (3f + 9f * u), orb, style = Stroke(width = ps * 1.2f))
                }
            }
        }
        else -> {
            val u = ((t - 3.4f) / 0.8f).coerceIn(0f, 1f)
            if (e.snareWon) {
                val pos = Offset(lerpF(orb.x, hand.x, smooth(u)), lerpF(orb.y, hand.y, smooth(u)) - sin(u * PI.toFloat()) * ts * 0.4f)
                drawCircle(VIOLET.copy(alpha = 0.4f * (1f - u * 0.4f)), ps * 6f * (1f - u * 0.5f), pos)
                drawCircle(Color.White, ps * 3f * (1f - u * 0.6f), pos)
                sparks(hand.x, hand.y, ((u - 0.6f) / 0.4f).coerceIn(0f, 1f), Color(0xFFE8DAFF), 8, ts * 0.5f, ps, 71)
            } else {
                drawCircle(Color.White.copy(alpha = 0.7f * (1f - u)), ts * (0.2f + 0.7f * u), orb, style = Stroke(width = ps * 1.4f))
                sparks(orb.x, orb.y, u, Color(0xFFFF8A9A), 12, ts * 0.9f, ps, 73)
            }
        }
    }
}

private fun DrawScope.castFx(e: Enc, g: Geo) {
    val ts = g.ts
    val ps = g.ps
    val k = e.spellT
    val col = glyphColor(e.spell)
    val hand = handOf(e.v, g)
    val gc = Offset(hand.x, hand.y - ts * 0.55f)
    val fade = if (k > 1.5f) ((1.7f - k) / 0.2f).coerceIn(0f, 1f) else 1f
    val a = Offset(g.ox + e.ax * ts, g.oy + (e.ay + 0.45f) * ts - ts * 0.4f)
    val b = Offset(g.ox + e.bx * ts, g.oy + (e.by + 0.45f) * ts - ts * 0.4f)
    drawCircle(col.copy(alpha = 0.16f * fade), ts * 0.55f, gc)
    glyph(e.spell, gc.x, gc.y, ts * 0.34f, k / 0.7f, col, fade, ps * 1.1f)
    if (k < 0.7f) {
        // the fingertip leaves a trail of sparks while the glyph is drawn
        for (i in 0 until 3) {
            val u = (k * 3f + i / 3f) % 1f
            drawRect(Color.White.copy(alpha = 1f - u), Offset(gc.x + (i - 1) * ps * 2.5f, gc.y - ts * 0.34f + u * ps * 5f), Size(ps, ps))
        }
    }
    val u = ((k - 0.8f) / 0.9f).coerceIn(0f, 1f)
    if (k < 0.8f) return
    when (e.spell) {
        "heal" -> {
            drawCircle(col.copy(alpha = 0.5f * (1f - u)), ts * (0.2f + 0.6f * u), a, style = Stroke(width = ps * 1.6f))
            for (i in 0 until 9) {
                val q = (u + i * 0.11f) % 1f
                drawRect(col.copy(alpha = 1f - q), Offset(a.x + (i - 4) * ps * 2.2f, a.y + ts * 0.2f - q * ts * 0.9f), Size(ps * 1.3f, ps * 1.3f))
            }
        }
        "shield" -> {
            drawOval(col.copy(alpha = 0.18f * (1f - u * 0.4f)), Offset(a.x - ts * 0.8f, a.y - ts * 0.7f * (0.3f + 0.7f * u)), Size(ts * 1.6f, ts * 1.4f * (0.3f + 0.7f * u)))
            sparks(a.x, a.y, u, col, 10, ts * 0.8f, ps, 81)
        }
        "fire" -> {
            if (u < 0.6f) {
                val q = u / 0.6f
                val px = lerpF(gc.x, b.x, smooth(q))
                val py = lerpF(gc.y, b.y, smooth(q)) - sin(q * PI.toFloat()) * ts * 0.3f
                bit(CType.EMBER, px, py, ps * 3.2f, 0f, g.t)
            } else {
                val q = (u - 0.6f) / 0.4f
                drawCircle(Color(0xFFFFD94A).copy(alpha = 0.7f * (1f - q)), ts * (0.2f + 0.6f * q), b)
                sparks(b.x, b.y, q, Color(0xFFFF9A4A), 12, ts * 0.9f, ps, 83)
            }
        }
        "lightning" -> {
            if (u < 0.7f) {
                val frame = (k * 24f).toInt()
                var prev = Offset(gc.x, gc.y)
                for (i in 1..8) {
                    val f = i / 8f
                    val jx = if (i == 8) 0f else ((hashI(i, frame) % 100) / 100f - 0.5f) * ts * 0.55f
                    val jy = if (i == 8) 0f else ((hashI(i + 9, frame) % 100) / 100f - 0.5f) * ts * 0.45f
                    val n = Offset(lerpF(gc.x, b.x, f) + jx, lerpF(gc.y, b.y, f) + jy)
                    drawLine(col.copy(alpha = 0.4f), prev, n, strokeWidth = ps * 4.5f)
                    drawLine(Color.White, prev, n, strokeWidth = ps * 1.2f)
                    prev = n
                }
            } else sparks(b.x, b.y, (u - 0.7f) / 0.3f, col, 10, ts * 0.8f, ps, 85)
        }
        else -> {
            for (i in 0 until 3) {
                val q = (u - i * 0.15f).coerceIn(0f, 1f)
                if (q > 0f) drawCircle(col.copy(alpha = 0.8f * (1f - q)), ts * (0.15f + 0.9f * q), b, style = Stroke(width = ps * (2.4f - q)))
            }
            sparks(b.x, b.y, u, Color.White, 8, ts * 0.8f, ps, 87)
        }
    }
}

/** While a shield glyph holds, a faint dome stays around the summoner's critter. */
private fun DrawScope.shieldFx(e: Enc, g: Geo) {
    val left = e.battle?.shield ?: 0
    if (left <= 0 || e.state == "result") return
    val a = Offset(g.ox + e.ax * g.ts, g.oy + (e.ay + 0.45f) * g.ts - g.ts * 0.4f)
    val flick = 0.7f + 0.3f * sin(g.t * 9f)
    drawOval(Color(0xFF9FD8FF).copy(alpha = 0.12f * flick), Offset(a.x - g.ts * 0.8f, a.y - g.ts * 0.75f), Size(g.ts * 1.6f, g.ts * 1.5f))
    drawOval(Color(0xFFCDEBFF).copy(alpha = 0.5f * flick), Offset(a.x - g.ts * 0.8f, a.y - g.ts * 0.75f), Size(g.ts * 1.6f, g.ts * 1.5f), style = Stroke(width = g.ps))
}

/** Infusion: a critter dissolves into its summoner's palm, or is called back out of it. */
private fun DrawScope.swapFx(sim: CivSim, s: Swap, cart: CritterArt, g: Geo) {
    val owner = sim.villagers.firstOrNull { it.id == s.owner } ?: return
    val ts = g.ts
    val ps = g.ps
    val age = (sim.time - s.born).toFloat()
    val img = cart.frame(s.sp, 0)
    val wd = img.lw * ps
    val ht = img.lh * ps
    val hand = handOf(owner, g)
    val base = Offset(g.ox + s.x * ts, g.oy + (s.y + 0.45f) * ts)
    if (s.kind == "in") {
        if (age < 0f) {
            // still standing there, just called into being
            spr(img, base.x, base.y, wd, ht, 1)
            return
        }
        val p = (age / 1.1f).coerceIn(0f, 1f)
        if (p >= 1f) return
        val ease = smooth(p)
        val cx = lerpF(base.x, hand.x, ease)
        val cy = lerpF(base.y - ht * 0.4f, hand.y, ease) - sin(p * PI.toFloat()) * ts * 0.5f
        val sc = 1f - 0.85f * ease
        spr(cart.white(s.sp), cx, cy + ht * 0.4f * sc, wd, ht, 1, 1f - p * 0.3f, sc, sc)
        val tc = typeColor(Dex.byId(s.sp).t1)
        for (i in 0 until 6) {
            val q = (p - i * 0.06f).coerceIn(0f, 1f)
            val px = lerpF(base.x, hand.x, smooth(q))
            val py = lerpF(base.y - ht * 0.4f, hand.y, smooth(q)) - sin(q * PI.toFloat()) * ts * 0.5f
            drawRect(tc.copy(alpha = 0.7f * (1f - i / 6f)), Offset(px - ps * 0.8f, py - ps * 0.8f), Size(ps * 1.6f, ps * 1.6f))
        }
        runeRing(hand.x, hand.y + ps * 2.5f, ps * 5f, 1f, VIOLET, 0.8f * (if (p > 0.6f) (1f - p) / 0.4f else p / 0.6f), age * 3f, ps * 0.9f)
        if (p > 0.85f) sparks(hand.x, hand.y, (p - 0.85f) / 0.15f, Color.White, 6, ts * 0.35f, ps, 91)
    } else {
        if (age < 0f || age > 1.4f) return
        val p = age / 1.4f
        runeRing(base.x, base.y - ps * 1.5f, ts * 0.75f, (age / 0.5f).coerceIn(0f, 1f), VIOLET, 0.9f * (1f - p), age * 2.5f, ps)
        val h = ts * 2.2f * smooth((age / 0.5f).coerceIn(0f, 1f))
        val dn = (1f - p).coerceIn(0f, 1f)
        drawRect(VIOLET.copy(alpha = 0.2f * dn), Offset(base.x - ts * 0.4f, base.y - h), Size(ts * 0.8f, h))
        drawRect(Color.White.copy(alpha = 0.45f * dn), Offset(base.x - ts * 0.14f, base.y - h), Size(ts * 0.28f, h))
        spr(cart.white(s.sp), base.x, base.y, wd, ht, 1, (1f - age / 0.7f).coerceIn(0f, 1f))
        sparks(base.x, base.y - ht * 0.4f, p, Color.White, 10, ts * 0.8f, ps, 93)
    }
}

private fun DrawScope.caughtFx(e: Enc, g: Geo) {
    val q = (e.clock / 1.6f).coerceIn(0f, 1f)
    if (q >= 1f) return
    val c = Offset(g.ox + e.bx * g.ts, g.oy + (e.by + 0.45f) * g.ts - g.ts * 0.5f)
    drawCircle(Color(0xFFFFE066).copy(alpha = 0.6f * (1f - q)), g.ts * (0.2f + 0.8f * q), c, style = Stroke(width = g.ps * 1.4f))
    sparks(c.x, c.y, q, Color(0xFFFFF4B0), 12, g.ts * 0.9f, g.ps, 51)
    for (i in 0 until 2) {
        val u = (q + i * 0.3f) % 1f
        icon(ICON_HEART, Color(0xFFFF5A7A).copy(alpha = 1f - u), c.x + (i * 2 - 1) * g.ps * 4f, c.y - u * g.ts, g.ps)
    }
}

// ---- evolution ----

private fun DrawScope.drawEvolution(sim: CivSim, s: EvolveScene, cart: CritterArt, art: CivArt, g: Geo, sun: Sun) {
    val ps = g.ps
    val ts = g.ts
    val p = ((sim.time - s.born) / EvolveScene.DURATION).toFloat().coerceIn(0f, 1f)
    val id = if (s.flipped) s.to else s.from
    val img = cart.frame(id, if (p < 0.5f) idleFrame(g.t, id, false) else 0)
    val wd = img.lw * ps
    val ht = img.lh * ps
    val cx = g.ox + s.x * ts
    val base = g.oy + (s.y + 0.45f) * ts
    critShadow(art, img, cx, base, wd, ht, sun, ps)
    val white = cart.white(id)
    when {
        p < 0.58f -> {
            val shake = sin(g.t * 40f) * ps * 0.6f * p
            val flick = sin(p * p * 160f) > 0f
            spr(img, cx + shake, base, wd, ht, 1)
            if (flick) spr(white, cx + shake, base, wd, ht, 1, 0.35f + 0.6f * (p / 0.58f))
        }
        p < 0.70f -> {
            val u = (p - 0.58f) / 0.12f
            val grow = 1f + 0.25f * sin(u * PI.toFloat())
            spr(white, cx, base, wd, ht, 1, 1f, grow, grow)
        }
        else -> {
            val u = ((p - 0.70f) / 0.30f).coerceIn(0f, 1f)
            val pulse = 1f + 0.14f * (1f - u) * sin(u * 18f)
            spr(img, cx, base, wd, ht, 1, 1f, pulse, pulse)
            spr(white, cx, base, wd, ht, 1, (1f - u * 3f).coerceIn(0f, 1f), pulse, pulse)
        }
    }
}

private fun DrawScope.evolutionOverlay(sim: CivSim, s: EvolveScene, g: Geo) {
    val ps = g.ps
    val ts = g.ts
    val p = ((sim.time - s.born) / EvolveScene.DURATION).toFloat().coerceIn(0f, 1f)
    val id = if (s.flipped) s.to else s.from
    val sp = Dex.byId(id)
    val cx = g.ox + s.x * ts
    val base = g.oy + (s.y + 0.45f) * ts
    val mid = Offset(cx, base - ts * 0.55f)
    val col = typeColor(sp.t1)
    // soft light under everything
    val glow = (if (p < 0.62f) p / 0.62f else (1f - (p - 0.62f) / 0.38f)).coerceIn(0f, 1f)
    drawCircle(Color.White.copy(alpha = 0.14f * glow), ts * 1.6f, mid)
    drawCircle(col.copy(alpha = 0.16f * glow), ts * 1.0f, mid)
    if (p in 0.06f..0.7f) {
        for (i in 0 until 3) {
            val r = (((p - 0.06f) * 2.6f) + i / 3f) % 1f
            drawOval(Color.White.copy(alpha = 0.7f * (1f - r)), Offset(cx - ts * 1.3f * r, base - ts * 0.25f * r), Size(ts * 2.6f * r, ts * 0.5f * r), style = Stroke(width = ps * 1.3f))
        }
    }
    if (p in 0.2f..0.64f) {
        val k = ((p - 0.2f) / 0.44f).coerceIn(0f, 1f)
        for (i in 0 until 7) {
            val x = cx + (i - 3) * ps * 4.4f
            val h = ts * (1.2f + 0.8f * ((hashI(i, 5) % 100) / 100f)) * k
            drawRect(Color.White.copy(alpha = 0.2f * k), Offset(x, base - h), Size(ps * 1.6f, h))
        }
    }
    if (p in 0.54f..0.74f) {
        val u = (p - 0.54f) / 0.20f
        val flash = 1f - abs(u * 2f - 1f)
        drawCircle(Color.White.copy(alpha = 0.85f * flash), ts * (0.5f + 1.6f * u), mid)
    }
    if (p >= 0.62f) {
        val u = ((p - 0.62f) / 0.38f).coerceIn(0f, 1f)
        sparks(cx, base - ts * 0.5f, u, Color(0xFFFFF4B0), 16, ts * 1.3f, ps, 61)
        for (i in 0 until 4) {
            val a = u * 2f + i * 1.57f
            icon(ICON_STAR, Color(0xFFFFE066).copy(alpha = 1f - u), cx + cos(a) * ts * 0.9f - ps * 2f, base - ts * 0.6f + sin(a) * ts * 0.5f - ps * 2f, ps * 0.9f)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Text: names, battle banners, damage numbers, evolution lines
// ---------------------------------------------------------------------------------------------
fun DrawScope.critterTexts(
    sim: CivSim, g: Geo, labels: Boolean,
    textPaint: android.graphics.Paint, bubblePaint: android.graphics.Paint, namePaint: android.graphics.Paint,
) {
    val cw = sim.critters
    val ts = g.ts
    val ps = g.ps
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        fun banner(txt: String, cx: Float, y: Float, small: Boolean = false) {
            textPaint.textSize = ts * (if (small) 0.3f else 0.38f)
            textPaint.textAlign = android.graphics.Paint.Align.CENTER
            val tw = textPaint.measureText(txt)
            val r = android.graphics.RectF(cx - tw / 2f - ts * 0.25f, y - ts * 0.55f, cx + tw / 2f + ts * 0.25f, y + ts * 0.2f)
            nc.drawRoundRect(r, ts * 0.2f, ts * 0.2f, bubblePaint)
            nc.drawText(txt, cx, y, textPaint)
        }
        fun tag(txt: String, cx: Float, y: Float, color: Int) {
            namePaint.textSize = ts * 0.3f
            namePaint.textAlign = android.graphics.Paint.Align.CENTER
            val keep = namePaint.color
            namePaint.color = color
            nc.drawText(txt, cx, y, namePaint)
            namePaint.color = keep
        }

        if (labels) {
            for (cr in cw.critters.toList()) {
                if (cw.evolving(cr) != null || cw.packed(cr)) continue
                if (cw.encounters.any { (it.mine === cr || it.mine2 === cr) && it.state != "walk" }) continue
                val b = cw.bodies[cr.uid] ?: continue
                val txt = (if (cr.companion) "★ " else "") + cr.name + " Lv" + cr.level
                tag(txt, g.ox + b.x * ts, g.oy + (b.y + 0.45f) * ts + ts * 0.34f, argbOf(typeColor(cr.species.t1)))
            }
            for (w in cw.wilds.toList()) {
                if (cw.encounters.any { it.wild === w && it.state != "walk" }) continue
                if (w.fade < 0.5f) continue
                tag("wild " + w.c.name + " Lv" + w.c.level, g.ox + w.x * ts, g.oy + (w.y + 0.45f) * ts + ts * 0.34f, 0xCCFFFFFF.toInt())
            }
        }

        // little floating notes: what a helper brought home
        for (pp in cw.pops.toList()) {
            val age = ((sim.time - pp.born) / pp.life).toFloat().coerceIn(0f, 1f)
            namePaint.textSize = ts * 0.3f
            namePaint.textAlign = android.graphics.Paint.Align.CENTER
            val keep = namePaint.color
            namePaint.color = (pp.color.toInt() and 0x00FFFFFF) or (((1f - age) * 255).toInt() shl 24)
            nc.drawText(pp.text, g.ox + pp.x * ts, g.oy + (pp.y + 0.45f) * ts - ts * (0.9f + 0.8f * age), namePaint)
            namePaint.color = keep
        }

        for (e in cw.encounters.toList()) {
            if (e.state == "walk") continue
            val mx = g.ox + (e.ax + e.bx) / 2f * ts
            val topY = g.oy + (min(e.ay, e.by) + 0.45f) * ts
            for (side in 0..1) {
                val cr = fighterCritter(e, side) ?: continue
                if (e.state == "result" && (e.result == "caught" || (e.result == "won" && side == 1 && e.clock > 1f))) continue
                if (e.state == "snare" && side == 1 && e.snareT > 0.7f) continue
                val sp = fighterSprite(e, side) ?: continue
                val p = pose(e, side, sim)
                if (!p.show) continue
                val ht = (if (sp == 0) 16f else Dex.byId(sp).let { max(10f, 8f + it.stage * 5f) }) * ps
                val nm = if (sp == 0) "Training Dummy" else cr.name + " Lv" + cr.level
                tag(nm, g.ox + p.x * ts, g.oy + (p.y + 0.45f) * ts - ht - ps * 9f, 0xFFFFFFFF.toInt())
            }
            if (e.bannerT > 0f && e.banner.isNotEmpty()) banner(e.banner, mx, topY - ts * 2.6f)
            if (e.subT > 0f && e.sub.isNotEmpty()) banner(e.sub, mx, topY - ts * 2.0f, small = true)
            for (pp in e.pops.toList()) {
                val age = ((sim.time - pp.born) / pp.life).toFloat().coerceIn(0f, 1f)
                namePaint.textSize = ts * 0.46f
                namePaint.textAlign = android.graphics.Paint.Align.CENTER
                val keep = namePaint.color
                namePaint.color = (pp.color.toInt() and 0x00FFFFFF) or (((1f - age * age) * 255).toInt() shl 24)
                nc.drawText(pp.text, g.ox + pp.x * ts, g.oy + (pp.y + 0.45f) * ts - ts * 0.9f * age, namePaint)
                namePaint.color = keep
            }
        }

        for (s in cw.evolutions.toList()) {
            val p = ((sim.time - s.born) / EvolveScene.DURATION).toFloat()
            val a = Dex.byId(s.from).name
            val b = Dex.byId(s.to).name
            val txt = when {
                p < 0.62f -> "$a is evolving!"
                else -> "$a evolved into $b!"
            }
            if (p > 0.04f) banner(txt, g.ox + s.x * ts, g.oy + (s.y + 0.45f) * ts - ts * 2.2f)
        }
    }
}

private fun argbOf(c: Color): Int =
    android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

@Suppress("unused")
private fun unusedSqrt(x: Float) = sqrt(x)
