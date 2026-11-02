package com.jondude1.deskglass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jondude1.deskglass.sim.CivSim
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// The Great Cathedral: a City Age mega-project raised in five tiers (foundations, nave, windows
// and walls, twin towers, spire and bell). Scaffolding stands around it until the last tier;
// stained glass glows at night; a finished cathedral rings at dawn and a Jubilee fills the sky
// with fireworks and bunting.
// ---------------------------------------------------------------------------------------------
fun DrawScope.cathedral(sim: CivSim, g: Geo) {
    if (sim.cathX < 0) return
    val tier = sim.cathTier()
    val s = g.ts
    val u = g.ps
    val x0 = g.ox + sim.cathX * s
    val w = 5f * s
    val yb = g.oy + (sim.cathY + 3f) * s
    val workers = sim.villagers.count { it.action == "raise_cathedral" && it.phase == "working" }
    val jubilee = sim.time < sim.jubileeUntil

    // ground shadow
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(x0 - s * 0.1f, yb - s * 0.5f), Size(w + s * 0.5f, s * 0.9f))

    val stone = Color(0xFFBDB8AC)
    val stoneLit = Color(0xFFD9D4C6)
    val stoneShade = Color(0xFF8E897D)
    val outline = Color(0xFF4A463E)
    val slate = Color(0xFF55648C)
    val slateDark = Color(0xFF3B4668)
    val gold = Color(0xFFE8C14A)
    val platTop = yb - s * 0.6f

    // marked plot before any stone is laid
    if (tier == 0) {
        for (k in 0..4) drawRect(Color(0xFF8A6A3C), Offset(x0 + k * s - u * 0.5f, yb - s * 1.6f), Size(u * 1.0f, s * 1.1f))
        drawRect(Color(0xFFB89868), Offset(x0, yb - s * 1.6f), Size(w, u * 0.9f))
        drawRect(Color(0xFFB89868), Offset(x0, yb - s * 0.6f), Size(w, u * 0.9f))
        // a heap of cut blocks waiting
        drawRect(stoneShade, Offset(x0 + w - s * 1.1f, yb - s * 0.9f), Size(s * 0.7f, s * 0.4f))
        drawRect(stone, Offset(x0 + w - s * 1.05f, yb - s * 0.95f), Size(s * 0.55f, s * 0.35f))
    }

    // tier 1: foundation platform with steps
    if (tier >= 1) {
        drawRect(outline, Offset(x0 - u * 0.8f, platTop - u * 0.8f), Size(w + u * 1.6f, s * 0.6f + u * 1.6f))
        drawRect(stoneShade, Offset(x0, platTop), Size(w, s * 0.6f))
        drawRect(stone, Offset(x0, platTop), Size(w, s * 0.4f))
        drawRect(stoneLit, Offset(x0, platTop), Size(w, u * 1.0f))
        for (k in 0..3) drawRect(stoneShade, Offset(x0 + (k + 1) * s, platTop + u * 1.5f), Size(u * 0.8f, s * 0.35f))
    }

    // tier 2: the nave and its slate roof
    val bodyL = x0 + s * 0.55f
    val bodyW = w - s * 1.1f
    val bodyH = s * 1.7f
    val bodyTop = platTop - bodyH
    if (tier >= 2) {
        drawRect(outline, Offset(bodyL - u * 0.8f, bodyTop - u * 0.8f), Size(bodyW + u * 1.6f, bodyH + u * 0.8f))
        drawRect(stone, Offset(bodyL, bodyTop), Size(bodyW, bodyH))
        drawRect(stoneLit, Offset(bodyL, bodyTop), Size(bodyW * 0.45f, bodyH))
        drawRect(stoneShade, Offset(bodyL + bodyW - u * 2f, bodyTop), Size(u * 2f, bodyH))
        // stone courses
        var cy = bodyTop + s * 0.35f
        while (cy < platTop - 2f) { drawRect(stoneShade.copy(alpha = 0.45f), Offset(bodyL, cy), Size(bodyW, u * 0.5f)); cy += s * 0.35f }
        // slate roof, a wide gable
        val roof = Path().apply {
            moveTo(bodyL - s * 0.25f, bodyTop)
            lineTo(bodyL + bodyW / 2f, bodyTop - s * 0.95f)
            lineTo(bodyL + bodyW + s * 0.25f, bodyTop)
            close()
        }
        drawPath(roof, outline)
        val roofIn = Path().apply {
            moveTo(bodyL - s * 0.15f, bodyTop - u * 0.4f)
            lineTo(bodyL + bodyW / 2f, bodyTop - s * 0.88f)
            lineTo(bodyL + bodyW + s * 0.15f, bodyTop - u * 0.4f)
            close()
        }
        drawPath(roofIn, slate)
        drawRect(slateDark.copy(alpha = 0.6f), Offset(bodyL - s * 0.1f, bodyTop - s * 0.3f), Size(bodyW + s * 0.2f, u * 0.7f))
        // great door with a pointed arch
        val dx = bodyL + bodyW / 2f
        drawRect(outline, Offset(dx - s * 0.36f, platTop - s * 0.95f), Size(s * 0.72f, s * 0.95f))
        drawRect(Color(0xFF5A3B1E), Offset(dx - s * 0.3f, platTop - s * 0.88f), Size(s * 0.6f, s * 0.88f))
        drawRect(Color(0xFF7A5430), Offset(dx - s * 0.3f, platTop - s * 0.88f), Size(s * 0.28f, s * 0.88f))
        drawRect(gold, Offset(dx + s * 0.1f, platTop - s * 0.45f), Size(u * 0.9f, u * 0.9f))
    }

    // tier 3: stained-glass windows and buttresses
    if (tier >= 3) {
        val glass = listOf(Color(0xFFD9534F), Color(0xFF4A7FD9), Color(0xFF55B86A), Color(0xFFE8C14A))
        for (k in 0..3) {
            if (k == 1 || k == 2) continue
            val wx = bodyL + s * 0.28f + k * (bodyW - s * 0.9f) / 3f
            val wy = bodyTop + s * 0.35f
            drawRect(outline, Offset(wx - u * 0.7f, wy - u * 0.7f), Size(s * 0.4f + u * 1.4f, s * 0.85f + u * 1.4f))
            for (r in 0 until 3) drawRect(glass[(k + r) % 4], Offset(wx, wy + r * s * 0.285f), Size(s * 0.4f, s * 0.26f))
            if (g.dark > 0.2f) drawRect(Color(0xFFFFE9A0).copy(alpha = 0.45f * g.dark), Offset(wx, wy), Size(s * 0.4f, s * 0.85f))
        }
        // round rose window above the door
        val rx = bodyL + bodyW / 2f
        val ry = bodyTop + s * 0.55f
        drawCircle(outline, radius = s * 0.34f, center = Offset(rx, ry))
        for (r in 0 until 6) {
            val a = r * (PI.toFloat() / 3f)
            drawCircle(glass[r % 4], radius = s * 0.09f, center = Offset(rx + cos(a) * s * 0.2f, ry + sin(a) * s * 0.2f))
        }
        drawCircle(gold, radius = s * 0.09f, center = Offset(rx, ry))
        if (g.dark > 0.2f) drawCircle(Color(0xFFFFE9A0).copy(alpha = 0.35f * g.dark), radius = s * 0.3f, center = Offset(rx, ry))
        for (side in 0..1) {
            val bx = if (side == 0) bodyL - s * 0.2f else bodyL + bodyW - s * 0.05f
            drawRect(outline, Offset(bx - u * 0.6f, bodyTop + s * 0.5f), Size(s * 0.3f + u * 1.2f, bodyH - s * 0.5f))
            drawRect(stoneShade, Offset(bx, bodyTop + s * 0.55f), Size(s * 0.3f, bodyH - s * 0.55f))
        }
    }

    // tier 4: two towers with pointed caps
    if (tier >= 4) {
        val tw = s * 0.95f
        val th = s * 3.1f
        for (side in 0..1) {
            val tx = if (side == 0) x0 + s * 0.02f else x0 + w - tw - s * 0.02f
            val tTop = platTop - th
            drawRect(outline, Offset(tx - u * 0.8f, tTop - u * 0.8f), Size(tw + u * 1.6f, th + u * 0.8f))
            drawRect(stone, Offset(tx, tTop), Size(tw, th))
            drawRect(stoneLit, Offset(tx, tTop), Size(tw * 0.4f, th))
            drawRect(stoneShade, Offset(tx + tw - u * 1.6f, tTop), Size(u * 1.6f, th))
            var cy = tTop + s * 0.4f
            while (cy < platTop - 2f) { drawRect(stoneShade.copy(alpha = 0.4f), Offset(tx, cy), Size(tw, u * 0.5f)); cy += s * 0.4f }
            // lancet slit and belfry opening
            drawRect(Color(0xFF2B2A33), Offset(tx + tw / 2f - u * 1.0f, tTop + s * 0.9f), Size(u * 2.0f, s * 0.55f))
            drawRect(Color(0xFF2B2A33), Offset(tx + tw / 2f - s * 0.17f, tTop + s * 0.2f), Size(s * 0.34f, s * 0.4f))
            if (g.dark > 0.2f) drawRect(Color(0xFFFFE9A0).copy(alpha = 0.55f * g.dark), Offset(tx + tw / 2f - u * 1.0f, tTop + s * 0.9f), Size(u * 2.0f, s * 0.55f))
            val cap = Path().apply {
                moveTo(tx - s * 0.12f, tTop)
                lineTo(tx + tw / 2f, tTop - s * 1.15f)
                lineTo(tx + tw + s * 0.12f, tTop)
                close()
            }
            drawPath(cap, outline)
            val capIn = Path().apply {
                moveTo(tx - s * 0.05f, tTop - u * 0.5f)
                lineTo(tx + tw / 2f, tTop - s * 1.07f)
                lineTo(tx + tw + s * 0.05f, tTop - u * 0.5f)
                close()
            }
            drawPath(capIn, slate)
        }
    }

    // tier 5: central spire, golden weathervane and the bell
    if (tier >= 5) {
        val cx = x0 + w / 2f
        val sTop = bodyTop - s * 2.5f
        val spire = Path().apply {
            moveTo(cx - s * 0.5f, bodyTop - s * 0.6f)
            lineTo(cx, sTop)
            lineTo(cx + s * 0.5f, bodyTop - s * 0.6f)
            close()
        }
        drawPath(spire, outline)
        val spireIn = Path().apply {
            moveTo(cx - s * 0.42f, bodyTop - s * 0.62f)
            lineTo(cx, sTop + s * 0.1f)
            lineTo(cx + s * 0.42f, bodyTop - s * 0.62f)
            close()
        }
        drawPath(spireIn, slate)
        drawRect(slateDark.copy(alpha = 0.7f), Offset(cx, sTop + s * 0.3f), Size(s * 0.2f, bodyTop - sTop - s * 0.9f))
        // belfry with the bell
        drawRect(outline, Offset(cx - s * 0.5f, bodyTop - s * 1.2f), Size(s * 1.0f, s * 0.7f))
        drawRect(Color(0xFF2B2A33), Offset(cx - s * 0.4f, bodyTop - s * 1.12f), Size(s * 0.8f, s * 0.6f))
        val sw = sin(g.t * 3f) * (if (g.t - 0f >= 0f && sim.time - sim.cathFlash < 4.0) s * 0.14f else 0f)
        drawRect(gold, Offset(cx - s * 0.14f + sw, bodyTop - s * 0.95f), Size(s * 0.28f, s * 0.3f))
        drawRect(Color(0xFFFFF0A8), Offset(cx - s * 0.1f + sw, bodyTop - s * 0.95f), Size(s * 0.1f, s * 0.22f))
        // weathervane
        drawRect(gold, Offset(cx - u * 0.4f, sTop - s * 0.4f), Size(u * 0.8f, s * 0.45f))
        drawRect(gold, Offset(cx - s * 0.2f + sin(g.t * 1.2f) * u * 1.5f, sTop - s * 0.36f), Size(s * 0.4f, u * 1.2f))
        if (g.dark > 0.3f) drawCircle(Color(0xFFFFE9A0).copy(alpha = 0.22f * g.dark), radius = s * 0.7f, center = Offset(cx, sTop))
    }

    // scaffolding until the last tier is done
    if (tier < 5) {
        val sh = s * (1.2f + tier * 0.55f)
        val scaf = Color(0xFF8A6A3C)
        val scafDark = Color(0xFF5E4526)
        for (side in 0..1) {
            val px = if (side == 0) x0 - s * 0.35f else x0 + w + s * 0.05f
            drawRect(scafDark, Offset(px - u * 0.3f, yb - sh - s * 0.3f), Size(u * 1.5f, sh))
            drawRect(scaf, Offset(px, yb - sh - s * 0.3f), Size(u * 1.1f, sh))
            var py = yb - s * 0.9f
            while (py > yb - sh - s * 0.3f) {
                drawRect(scafDark, Offset(px - s * 0.2f, py), Size(s * 0.55f, u * 0.9f))
                drawRect(scaf, Offset(px - s * 0.2f, py - u * 0.3f), Size(s * 0.55f, u * 0.7f))
                py -= s * 0.6f
            }
        }
        if (workers > 0) {
            for (k in 0 until 4) {
                val p = (g.t * 0.9f + k * 0.25f) % 1f
                drawCircle(Color(0xFFD8D3C6).copy(alpha = 0.5f * (1f - p)), radius = u * (1.2f + p * 2.4f), center = Offset(x0 + w * (0.2f + 0.2f * k) + sin(g.t + k) * u, yb - s * 0.7f - p * s * 0.9f))
            }
            if ((g.t * 5f).toInt() % 2 == 0) drawRect(Color(0xFFFFF3C4), Offset(x0 + w * 0.5f, yb - s * 0.9f), Size(u * 1.0f, u * 1.0f))
        }
    }

    // glow of a tier just raised, or the dawn bell
    val since = (sim.time - sim.cathFlash).toFloat()
    if (since in 0f..3f) {
        val p = since / 3f
        drawCircle(Color(0xFFFFE9A0).copy(alpha = 0.55f * (1f - p)), radius = s * (1.5f + p * 4.5f), center = Offset(x0 + w / 2f, yb - s * 1.5f), style = Stroke(width = u * 1.6f))
    }
    if (tier >= 3 && g.dark > 0.45f) drawOval(Color(0xFFFFD27A).copy(alpha = 0.10f * g.dark), Offset(x0 - s * 0.6f, yb - s * 2.6f), Size(w + s * 1.2f, s * 3.2f))

    // Jubilee: bunting between the towers and fireworks over the roof
    if (jubilee && tier >= 5) {
        val flag = listOf(Color(0xFFE85D75), Color(0xFFFFD166), Color(0xFF4ECDC4), Color(0xFFB69CFF))
        for (k in 0 until 10) {
            val fx = x0 + s * 0.5f + k * (w - s) / 9f
            val fy = yb - s * 3.1f + sin(k / 9f * PI.toFloat()) * s * 0.55f
            drawRect(flag[k % 4], Offset(fx, fy), Size(s * 0.22f, s * 0.3f))
        }
        val spark = listOf(Color(0xFFFFE066), Color(0xFFFF6B8B), Color(0xFF7BE0FF), Color(0xFFC8A0FF), Color(0xFFFFFFFF))
        for (k in 0 until 3) {
            val ph = (g.t * 0.35f + k * 0.33f) % 1f
            val bx = x0 + w * (0.15f + 0.35f * k)
            val by = yb - s * (5.2f + (k % 2) * 1.2f)
            val a = (1f - ph).coerceIn(0f, 1f)
            val rad = s * (0.2f + ph * 1.6f)
            for (r in 0 until 12) {
                val ang = r * (PI.toFloat() / 6f) + k
                drawRect(spark[(r + k) % 5].copy(alpha = a), Offset(bx + cos(ang) * rad, by + sin(ang) * rad + ph * ph * s * 0.6f), Size(u * 1.1f, u * 1.1f))
            }
        }
    }
}
