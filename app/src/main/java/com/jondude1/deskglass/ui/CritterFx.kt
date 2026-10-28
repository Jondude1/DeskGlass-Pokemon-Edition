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

// Drawing for the critters: wild ones, helpers and companions, battles with their move effects, snares, berry offers
// and evolution. Everything is drawn in the same pixel units (ps) as the rest of the village.

private const val TURN_IMPACT = 1.0f
private const val SNARE_TIME = 4.2f
private const val OFFER_TIME = 3.4f

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
    var shrinkIn = 0f   // 0..1 how far it has been pulled into a snare
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
        if (cw.evolving(c) != null) continue
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
        "offer" -> if (side == 1) {
            val t = e.offerT
            if (t in 0.8f..2.6f) {
                p.sy = 1f + 0.05f * sin(t * 9f)
                p.x += -dir * 0.05f * sin(t * 4f)
            } else if (t >= 2.6f && !e.offerWon) {
                p.x += sin(t * 28f) * 0.04f
            } else if (t >= 2.6f) {
                p.y -= abs(sin((t - 2.6f) * 8f)) * 0.14f
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
                "refused" -> if (side == 1 && c > 0.8f) {
                    p.alpha = (1f - (c - 0.8f) / 1.2f).coerceIn(0f, 1f)
                    p.x += -dir * 0.6f * (c - 0.8f)
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
    val dir = if (ox >= bx) 1 else -1
    val img = cart.frame(sp, if (sp == 0) 0 else idleFrame(t, sp + side, false))
    val wd = img.lw * ps
    val ht = img.lh * ps
    val cx = g.ox + p.x * ts
    val base = g.oy + (p.y + 0.45f) * ts
    if (p.shrinkIn > 0f) {
        // pulled into the snare as a white shape that melts toward the ball
        val k = p.shrinkIn
        val s = 1f - 0.85f * smooth(k)
        spr(cart.white(sp), cx, base - ht * 0.2f * smooth(k), wd, ht, dir, 1f - 0.2f * k, s, s)
        return
    }
    critShadow(art, img, cx, base, wd, ht, sun, ps, p.alpha)
    spr(img, cx, base, wd, ht, dir, p.alpha, p.sx, p.sy, p.rot * (if (dir == 1) 1f else 1f))
    if (p.flash > 0f) spr(cart.white(sp), cx, base, wd, ht, dir, 0.7f, p.sx, p.sy, p.rot)
}

/** Everything drawn over the villagers: move effects, snares, berries, ground rings and HP bars. */
fun DrawScope.drawCritterEffects(sim: CivSim, cart: CritterArt, g: Geo) {
    val cw = sim.critters
    for (e in cw.encounters.toList()) {
        if (e.state == "walk") continue
        ring(e, g)
        if (e.state == "turn") moveFx(e, g)
        if (e.state == "snare") snareFx(e, g)
        if (e.state == "offer") offerFx(e, g)
        if (e.state == "result" && e.result == "caught") caughtFx(e, g)
        hpBars(e, cart, g, sim)
    }
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

// ---- snares and berries ----

private fun DrawScope.ball(x: Float, y: Float, r: Float, rot: Float, ps: Float) {
    withTransform({ rotate(rot, Offset(x, y)) }) {
        drawCircle(Color(0xFF5E4222), r + ps * 0.6f, Offset(x, y))
        drawCircle(Color(0xFFC49A56), r, Offset(x, y))
        drawCircle(Color(0xFFE2BE7C), r * 0.55f, Offset(x - r * 0.25f, y - r * 0.3f))
        drawLine(Color(0xFF5E4222), Offset(x - r, y), Offset(x + r, y), strokeWidth = ps * 0.8f)
        drawLine(Color(0xFF5E4222), Offset(x, y - r), Offset(x, y + r), strokeWidth = ps * 0.8f)
        drawRect(Color(0xFFE5483C), Offset(x - ps * 0.9f, y - ps * 0.9f), Size(ps * 1.8f, ps * 1.8f))
    }
}

private fun DrawScope.snareFx(e: Enc, g: Geo) {
    val ts = g.ts
    val ps = g.ps
    val t = e.snareT
    val from = Offset(g.ox + e.v.x * ts, g.oy + (e.v.y + 0.45f) * ts - ts * 0.8f)
    val target = Offset(g.ox + e.bx * ts, g.oy + (e.by + 0.45f) * ts - ts * 0.3f)
    val ground = Offset(g.ox + e.bx * ts, g.oy + (e.by + 0.45f) * ts - ps * 3.2f)
    val r = ps * 3.6f
    when {
        t < 0.7f -> {
            val u = t / 0.7f
            val px = lerpF(from.x, target.x, smooth(u))
            val py = lerpF(from.y, target.y, smooth(u)) - sin(u * PI.toFloat()) * ts * 1.1f
            ball(px, py, r, u * 540f, ps)
        }
        t < 1.0f -> {
            val u = (t - 0.7f) / 0.3f
            drawCircle(Color.White.copy(alpha = 0.6f * (1f - u)), ts * (0.25f + 0.4f * u), target)
            val bp = Offset(lerpF(target.x, ground.x, smooth(u)), lerpF(target.y, ground.y, smooth(u)))
            ball(bp.x, bp.y, r, 0f, ps)
        }
        t < 3.4f -> {
            var rot = 0f
            for (b in 0 until 3) {
                val s = 1.1f + b * 0.8f
                if (t in s..(s + 0.5f)) rot = sin((t - s) / 0.5f * 2f * PI.toFloat()) * 24f
            }
            ball(ground.x, ground.y, r, rot, ps)
            val blink = (sin(t * 6f) * 0.5f + 0.5f)
            drawRect(Color(0xFFFFE066).copy(alpha = 0.15f + 0.3f * blink), Offset(ground.x - ps * 0.6f, ground.y - r - ps * 3f), Size(ps * 1.2f, ps * 1.2f))
        }
        else -> {
            val u = ((t - 3.4f) / 0.8f).coerceIn(0f, 1f)
            if (e.snareWon) {
                ball(ground.x, ground.y, r, 0f, ps)
                drawCircle(Color(0xFFFFE066).copy(alpha = 0.8f * (1f - u)), ts * (0.2f + 0.8f * u), ground, style = Stroke(width = ps * 1.6f))
                sparks(ground.x, ground.y - r, u, Color(0xFFFFF4B0), 12, ts * 0.9f, ps, 41)
            } else {
                if (u < 0.45f) ball(ground.x, ground.y, r * (1f + u), (u * 60f), ps)
                drawCircle(Color.White.copy(alpha = 0.7f * (1f - u)), ts * (0.2f + 0.6f * u), ground)
                sparks(ground.x, ground.y, u, Color.White, 10, ts * 0.8f, ps, 43)
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = SNARE_TIME
}

private fun DrawScope.berry(x: Float, y: Float, ps: Float) {
    drawCircle(Color(0xFF7A1F2B), ps * 2.1f, Offset(x, y))
    drawCircle(Color(0xFFE0405A), ps * 1.7f, Offset(x, y))
    drawCircle(Color(0xFFFF9FB2), ps * 0.6f, Offset(x - ps * 0.6f, y - ps * 0.6f))
    drawRect(Color(0xFF3E8E41), Offset(x - ps * 0.4f, y - ps * 2.6f), Size(ps * 1.6f, ps * 1f))
}

private fun DrawScope.offerFx(e: Enc, g: Geo) {
    val ts = g.ts
    val ps = g.ps
    val t = e.offerT
    val dirToV = if (e.v.x >= e.bx) 1f else -1f
    val from = Offset(g.ox + e.v.x * ts, g.oy + (e.v.y + 0.45f) * ts - ts * 0.7f)
    val restBase = Offset(g.ox + (e.bx + dirToV * 0.75f) * ts, g.oy + (e.by + 0.45f) * ts - ps * 2f)
    val foe = Offset(g.ox + e.bx * ts, g.oy + (e.by + 0.45f) * ts - ts * 0.55f)
    for (i in 0 until 3) {
        val rest = Offset(restBase.x + (i - 1) * ps * 4.2f * dirToV, restBase.y + (i % 2) * ps * 1.4f)
        val u = ((t - i * 0.12f) / 0.65f)
        if (u < 0f) continue
        if (t < 2.6f || !e.offerWon) {
            if (u < 1f) {
                val px = lerpF(from.x, rest.x, smooth(u))
                val py = lerpF(from.y, rest.y, smooth(u)) - sin(u * PI.toFloat()) * ts * 0.6f
                berry(px, py, ps)
            } else berry(rest.x, rest.y, ps)
        } else {
            val q = ((t - 2.6f) / 0.8f).coerceIn(0f, 1f)
            sparks(rest.x, rest.y, q, Color(0xFFFF9FB2), 5, ts * 0.4f, ps, i)
        }
    }
    if (t >= 2.6f) {
        val q = ((t - 2.6f) / 0.8f).coerceIn(0f, 1f)
        if (e.offerWon) {
            for (i in 0 until 3) {
                val u = (q + i * 0.18f) % 1f
                icon(ICON_HEART, Color(0xFFFF5A7A).copy(alpha = 1f - u), foe.x + (i - 1) * ps * 5f, foe.y - ts * 0.3f - u * ts * 0.9f, ps * 1.1f)
            }
        } else {
            icon(ICON_Q, Color(0xFFE8E8F0).copy(alpha = 1f - q * 0.6f), foe.x - ps * 3f, foe.y - ts * 0.6f - q * ps * 3f, ps * 1.2f)
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = OFFER_TIME
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
                if (cw.evolving(cr) != null) continue
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
