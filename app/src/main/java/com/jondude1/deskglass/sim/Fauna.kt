package com.jondude1.deskglass.sim

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** An ordinary animal of the wide world (not a summonable critter): it only makes the land feel lived in. */
class Animal(val kind: String, var x: Float, var y: Float, val id: Int) {
    var tx = x
    var ty = y
    var pause = 0f
    var flee = 0f
    var facing = 1
    var moving = false
    var phase = 0f
    var alt = 0f          // height above the ground for flyers
    var speed = 1f
    var dir = 0f          // heading for flyers and flitting things
    var homeX = x
    var homeY = y
    var grazing = false
}

/**
 * Rabbits in the meadows, deer at the forest edges, ducks and frogs by the water, birds crossing the sky and
 * butterflies in the flowers. Nothing here is saved: the land fills itself again after a restart.
 */
class Fauna(private val sim: CivSim) {
    val animals = ArrayList<Animal>()
    var ready = false
        private set
    private val rnd = Random(97)
    private var nextId = 1
    private var respawnClock = 30f

    fun reset() {
        animals.clear()
        ready = false
    }

    private fun land(x: Int, y: Int): Boolean = sim.walkableTile(x, y)
    private fun water(x: Int, y: Int): Boolean = x in 0 until GW && y in 0 until GH && sim.tiles[y * GW + x] == T.WATER

    private fun nearForest(x: Int, y: Int, r: Int = 2): Boolean {
        for (dy in -r..r) for (dx in -r..r) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until GW && ny in 0 until GH && sim.tiles[ny * GW + nx] == T.FOREST) return true
        }
        return false
    }

    private fun nearWater(x: Int, y: Int): Boolean {
        for (dy in -1..1) for (dx in -1..1) if (water(x + dx, y + dy)) return true
        return false
    }

    private fun far(x: Float, y: Float, d: Float): Boolean = sim.villagers.none { hypot(it.x - x, it.y - y) < d }

    private fun spawn(kind: String, ok: (Int, Int) -> Boolean, tries: Int = 400, avoid: Float = 6f): Animal? {
        repeat(tries) {
            val x = 1 + rnd.nextInt(GW - 2)
            val y = 1 + rnd.nextInt(GH - 2)
            if (!ok(x, y)) return@repeat
            val fx = x + 0.2f + rnd.nextFloat() * 0.6f
            val fy = y + 0.2f + rnd.nextFloat() * 0.6f
            if (!far(fx, fy, avoid)) return@repeat
            val a = Animal(kind, fx, fy, nextId++)
            a.homeX = fx
            a.homeY = fy
            a.pause = rnd.nextFloat() * 4f
            a.phase = rnd.nextFloat() * 6.28f
            a.facing = if (rnd.nextBoolean()) 1 else -1
            a.speed = when (kind) {
                "rabbit" -> 1.5f
                "deer" -> 1.3f
                "duck" -> 0.7f
                "frog" -> 1.0f
                else -> 1f
            }
            animals.add(a)
            return a
        }
        return null
    }

    fun populate() {
        animals.clear()
        val grass = { x: Int, y: Int -> land(x, y) && sim.tiles[y * GW + x] == T.GRASS }
        repeat(16) { spawn("rabbit", grass) }
        // deer move in small herds near the trees
        repeat(5) {
            val lead = spawn("deer", { x, y -> grass(x, y) && nearForest(x, y) }) ?: return@repeat
            repeat(1 + rnd.nextInt(2)) {
                val d = Animal("deer", lead.x + (rnd.nextFloat() - 0.5f) * 2f, lead.y + (rnd.nextFloat() - 0.5f) * 2f, nextId++)
                if (land(d.x.toInt(), d.y.toInt())) {
                    d.homeX = lead.homeX
                    d.homeY = lead.homeY
                    d.speed = 1.3f
                    d.phase = rnd.nextFloat() * 6.28f
                    animals.add(d)
                }
            }
        }
        repeat(10) { spawn("duck", { x, y -> water(x, y) && nearWater(x, y) && !nearWaterOnlyEdge(x, y) }) }
        repeat(6) { spawn("frog", { x, y -> grass(x, y) && nearWater(x, y) }, avoid = 3f) }
        repeat(14) {
            val a = spawn("butterfly", grass, avoid = 2f) ?: return@repeat
            a.alt = 0.5f + rnd.nextFloat() * 0.5f
            a.dir = rnd.nextFloat() * 6.28f
        }
        repeat(9) { spawnBird() }
        ready = true
    }

    /** A little way in from the shore, so ducks float on the water and not on the sand. */
    private fun nearWaterOnlyEdge(x: Int, y: Int): Boolean = !(water(x - 1, y) && water(x + 1, y) && water(x, y - 1) && water(x, y + 1))

    private fun spawnBird() {
        val fromLeft = rnd.nextBoolean()
        val a = Animal("bird", if (fromLeft) -2f else GW + 2f, 3f + rnd.nextFloat() * (GH - 6f), nextId++)
        a.dir = if (fromLeft) 0f else PI.toFloat()
        a.dir += (rnd.nextFloat() - 0.5f) * 0.5f
        a.speed = 2.2f + rnd.nextFloat() * 1.4f
        a.alt = 1.8f + rnd.nextFloat() * 1.6f
        a.phase = rnd.nextFloat() * 6.28f
        a.facing = if (cos(a.dir) >= 0f) 1 else -1
        animals.add(a)
    }

    /** A hunter takes the nearest deer or rabbit. Returns what was taken, or null. */
    fun onHunt(v: Villager): String? {
        var best: Animal? = null
        var bd = 16f
        for (a in animals) {
            if (a.kind != "deer" && a.kind != "rabbit") continue
            val d = hypot(a.x - v.x, a.y - v.y)
            if (d < bd) {
                bd = d
                best = a
            }
        }
        val got = best ?: return null
        animals.remove(got)
        return got.kind
    }

    fun count(kind: String): Int = animals.count { it.kind == kind }

    fun tick(dt: Float) {
        if (!ready) {
            populate()
            return
        }
        respawnClock -= dt
        if (respawnClock <= 0f) {
            respawnClock = 25f + rnd.nextFloat() * 20f
            val grass = { x: Int, y: Int -> land(x, y) && sim.tiles[y * GW + x] == T.GRASS }
            if (count("rabbit") < 16) spawn("rabbit", grass, avoid = 10f)
            if (count("deer") < 8) spawn("deer", { x, y -> grass(x, y) && nearForest(x, y) }, avoid = 12f)
            if (count("bird") < 9) spawnBird()
        }
        val it = animals.iterator()
        while (it.hasNext()) {
            val a = it.next()
            if (!step(a, dt)) it.remove()
        }
    }

    private fun threat(a: Animal, r: Float): Boolean {
        for (v in sim.villagers) if (hypot(v.x - a.x, v.y - a.y) < r) return true
        for (p in sim.predators) if (hypot(p.x - a.x, p.y - a.y) < r * 1.4f) return true
        return false
    }

    private fun step(a: Animal, dt: Float): Boolean {
        a.phase += dt
        when (a.kind) {
            "bird" -> {
                a.x += cos(a.dir) * a.speed * dt
                a.y += sin(a.dir) * a.speed * dt * 0.4f
                a.facing = if (cos(a.dir) >= 0f) 1 else -1
                return a.x > -4f && a.x < GW + 4f && a.y > -3f && a.y < GH + 3f
            }
            "butterfly" -> {
                a.dir += (sin(a.phase * 1.3f + a.id) * 2.4f) * dt
                a.x += cos(a.dir) * 0.9f * dt
                a.y += sin(a.dir) * 0.6f * dt
                // drift back toward the flower patch it lives in
                a.x += (a.homeX - a.x) * 0.15f * dt
                a.y += (a.homeY - a.y) * 0.15f * dt
                a.facing = if (cos(a.dir) >= 0f) 1 else -1
                return true
            }
        }
        val scare = when (a.kind) { "rabbit" -> 3.4f; "deer" -> 4.6f; "duck" -> 2.8f; else -> 1.6f }
        if (a.flee <= 0f && threat(a, scare)) {
            a.flee = 1.6f + rnd.nextFloat()
            // run directly away from whoever is closest
            var cx = 0f
            var cy = 0f
            var bd = 99f
            for (v in sim.villagers) {
                val d = hypot(v.x - a.x, v.y - a.y)
                if (d < bd) { bd = d; cx = v.x; cy = v.y }
            }
            for (p in sim.predators) {
                val d = hypot(p.x - a.x, p.y - a.y)
                if (d < bd) { bd = d; cx = p.x; cy = p.y }
            }
            val ang = Math.atan2((a.y - cy).toDouble(), (a.x - cx).toDouble()).toFloat() + (rnd.nextFloat() - 0.5f) * 0.8f
            val len = 4f + rnd.nextFloat() * 3f
            a.tx = a.x + cos(ang) * len
            a.ty = a.y + sin(ang) * len
            a.pause = 0f
        }
        if (a.flee > 0f) a.flee -= dt
        if (a.pause > 0f) {
            a.pause -= dt
            a.moving = false
            return true
        }
        val dx = a.tx - a.x
        val dy = a.ty - a.y
        val d = hypot(dx, dy)
        if (d < 0.15f) {
            a.moving = false
            a.pause = when (a.kind) {
                "frog" -> 1.5f + rnd.nextFloat() * 4f
                "duck" -> 2f + rnd.nextFloat() * 5f
                else -> 1.5f + rnd.nextFloat() * 5f
            }
            a.grazing = a.kind == "deer" || a.kind == "rabbit"
            // pick the next spot near home
            val r = when (a.kind) { "frog" -> 1.6f; "duck" -> 5f; "deer" -> 6f; else -> 4.5f }
            for (n in 0 until 8) {
                val nx = a.homeX + (rnd.nextFloat() - 0.5f) * 2f * r
                val ny = a.homeY + (rnd.nextFloat() - 0.5f) * 2f * r * 0.7f
                val ok = if (a.kind == "duck") water(nx.toInt(), ny.toInt()) else land(nx.toInt(), ny.toInt())
                if (ok && nx > 0.5f && ny > 0.5f && nx < GW - 0.5f && ny < GH - 0.5f) {
                    a.tx = nx
                    a.ty = ny
                    break
                }
            }
            return true
        }
        val sp = a.speed * (if (a.flee > 0f) 2.6f else 1f) * dt
        val nx = a.x + dx / d * min(sp, d)
        val ny = a.y + dy / d * min(sp, d)
        val ok = if (a.kind == "duck") water(nx.toInt(), ny.toInt()) else land(nx.toInt(), ny.toInt())
        if (!ok) {
            a.tx = a.x
            a.ty = a.y
            a.moving = false
            a.flee = 0f
            return true
        }
        a.x = nx
        a.y = ny
        a.moving = true
        if (abs(dx) > 0.02f) a.facing = if (dx > 0) 1 else -1
        return true
    }
}
