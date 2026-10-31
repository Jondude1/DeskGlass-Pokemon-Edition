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
    var target = 0        // fox: id of the rabbit it is stalking
    var chase = 0f        // fox: how long the current chase has lasted
    var cool = 0f         // fox: rest after a hunt
    var life = 0f         // fish and other short-lived things
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
    private var nightClock = 5f
    private var skyClock = 20f
    private var fishClock = 6f
    /** Wild-life happenings seen this run (used by the headless harness and the naturalist deed). Not saved. */
    val seen = HashMap<String, Int>()
    val kindsSeen = HashSet<String>()
    private fun note(k: String) { seen[k] = (seen[k] ?: 0) + 1 }

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
                "squirrel" -> 2.0f
                "hedgehog" -> 0.5f
                "fox" -> 1.5f
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
        repeat(3) { spawn("fox", { x, y -> grass(x, y) && nearForest(x, y, 3) }, avoid = 10f) }
        if (sim.seasonIdx() != 3) {
            repeat(6) { spawn("squirrel", { x, y -> land(x, y) && nearForest(x, y, 1) }, avoid = 4f) }
            repeat(4) { spawn("dragonfly", { x, y -> land(x, y) && nearWater(x, y) }, avoid = 2f)?.also { a -> a.alt = 0.7f; a.dir = rnd.nextFloat() * 6.28f } }
        }
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
        spawnWild(dt)
        val it = animals.iterator()
        while (it.hasNext()) {
            val a = it.next()
            if (!step(a, dt)) it.remove()
        }
        for (a in animals) kindsSeen.add(a.kind)
    }

    /** Night creatures, migrating geese, leaping fish and the animals that sleep through winter. */
    private fun spawnWild(dt: Float) {
        val winter = sim.seasonIdx() == 3
        val dusk = sim.isNight() || sim.partOfDay() == "evening"
        val grass = { x: Int, y: Int -> land(x, y) && sim.tiles[y * GW + x] == T.GRASS }
        nightClock -= dt
        if (nightClock <= 0f) {
            nightClock = 3f + rnd.nextFloat() * 3f
            if (dusk && !sim.raining() && !sim.snowing()) {
                if (!winter && count("firefly") < 18) {
                    val a = spawn("firefly", { x, y -> land(x, y) && (nearWater(x, y) || nearForest(x, y, 2)) }, avoid = 1f)
                    if (a != null) { a.alt = 0.3f + rnd.nextFloat() * 0.6f; a.dir = rnd.nextFloat() * 6.28f; note("firefly") }
                }
                if (sim.isNight() && count("owl") < 2) {
                    val a = spawn("owl", { x, y -> land(x, y) && nearForest(x, y, 1) }, avoid = 8f)
                    if (a != null) { a.alt = 1.1f; a.dir = rnd.nextFloat() * 6.28f; note("owl") }
                }
                if (!winter && count("bat") < 7) {
                    val a = spawn("bat", { x, y -> land(x, y) }, avoid = 3f)
                    if (a != null) { a.alt = 1.2f; a.dir = rnd.nextFloat() * 6.28f; note("bat") }
                }
            }
            if (winter) {
                // squirrels, dragonflies and hedgehogs sleep until spring
                animals.removeAll { it.kind == "squirrel" || it.kind == "dragonfly" || it.kind == "hedgehog" }
            } else {
                if (count("squirrel") < 6) spawn("squirrel", { x, y -> land(x, y) && nearForest(x, y, 1) }, avoid = 6f)
                if (sim.seasonIdx() == 1 && count("dragonfly") < 4) {
                    val a = spawn("dragonfly", { x, y -> land(x, y) && nearWater(x, y) }, avoid = 2f)
                    if (a != null) { a.alt = 0.7f; a.dir = rnd.nextFloat() * 6.28f }
                }
                if ((sim.seasonIdx() == 2 || sim.seasonIdx() == 0) && count("hedgehog") < 3) spawn("hedgehog", { x, y -> grass(x, y) && nearForest(x, y, 2) }, avoid = 6f)
            }
            if (count("fox") < 3) spawn("fox", { x, y -> grass(x, y) && nearForest(x, y, 3) }, avoid = 12f)
        }
        skyClock -= dt
        if (skyClock <= 0f) {
            skyClock = 45f + rnd.nextFloat() * 50f
            val migrating = sim.seasonIdx() == 0 || sim.seasonIdx() == 2
            if (migrating && !sim.isNight() && !sim.storm() && count("goose") == 0) spawnFlock()
        }
        fishClock -= dt
        if (fishClock <= 0f) {
            fishClock = 6f + rnd.nextFloat() * 10f
            if (!winter && !sim.isNight() && count("fish") < 2) {
                val a = spawn("fish", { x, y -> water(x, y) && !nearWaterOnlyEdge(x, y) }, avoid = 0f)
                if (a != null) { a.life = 1.1f; a.alt = 0f; note("fish") }
            }
        }
    }

    /** A V of geese crossing the sky, honking. */
    private fun spawnFlock() {
        val fromLeft = rnd.nextBoolean()
        val baseY = 4f + rnd.nextFloat() * (GH - 8f)
        val ang = if (fromLeft) 0f else PI.toFloat()
        val n = 5 + rnd.nextInt(3)
        for (i in 0 until n) {
            val row = (i + 1) / 2
            val side = if (i % 2 == 0) 1 else -1
            val back = row * 1.6f
            val a = Animal("goose", if (fromLeft) -2f - back else GW + 2f + back, baseY + side * row * 0.9f, nextId++)
            a.dir = ang
            a.speed = 2.0f
            a.alt = 2.6f + row * 0.1f
            a.phase = rnd.nextFloat() * 6.28f
            a.facing = if (fromLeft) 1 else -1
            animals.add(a)
        }
        note("goose flock")
    }

    private fun threat(a: Animal, r: Float): Boolean {
        for (v in sim.villagers) if (hypot(v.x - a.x, v.y - a.y) < r) return true
        for (p in sim.predators) if (hypot(p.x - a.x, p.y - a.y) < r * 1.4f) return true
        if (a.kind == "rabbit" || a.kind == "squirrel") for (f in animals) if (f.kind == "fox" && f.target != 0 && hypot(f.x - a.x, f.y - a.y) < r * 1.3f) return true
        return false
    }

    /** A fox stalks a rabbit, sprints at it, and either eats or loses it. Returns true when it handled this step. */
    private fun foxThink(a: Animal, dt: Float): Boolean {
        if (a.cool > 0f) a.cool -= dt
        if (a.flee > 0f) return false
        if (a.target == 0) {
            if (a.cool > 0f || sim.isNight() && rnd.nextInt(3) == 0) return false
            var best: Animal? = null
            var bd = 8f
            for (o in animals) {
                if (o.kind != "rabbit") continue
                val d = hypot(o.x - a.x, o.y - a.y)
                if (d < bd) { bd = d; best = o }
            }
            val b = best ?: return false
            if (far(a.x, a.y, 4f)) {
                a.target = b.id
                a.chase = 0f
                a.pause = 0f
                note("fox hunt")
            }
            return false
        }
        val prey = animals.firstOrNull { it.id == a.target }
        a.chase += dt
        if (prey == null || a.chase > 7f || !far(a.x, a.y, 2.5f)) {
            a.target = 0
            a.cool = 20f
            a.pause = 1.5f
            return false
        }
        val dx = prey.x - a.x
        val dy = prey.y - a.y
        val d = hypot(dx, dy)
        if (d < 0.55f) {
            prey.life = -1f
            a.target = 0
            a.cool = 90f + rnd.nextFloat() * 60f
            a.pause = 5f
            a.moving = false
            note("fox catch")
            return true
        }
        val sp = (if (d < 3f) 4.6f else 3.1f) * dt
        val nx = a.x + dx / d * min(sp, d)
        val ny = a.y + dy / d * min(sp, d)
        if (!land(nx.toInt(), ny.toInt())) { a.target = 0; a.cool = 10f; return true }
        a.x = nx
        a.y = ny
        a.moving = true
        a.grazing = false
        a.flee = 0f
        a.facing = if (dx > 0f) 1 else -1
        return true
    }

    private fun step(a: Animal, dt: Float): Boolean {
        a.phase += dt
        if (a.life < 0f) return false
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
        val dusk = sim.isNight() || sim.partOfDay() == "evening"
        when (a.kind) {
            "goose" -> {
                a.x += cos(a.dir) * a.speed * dt
                a.facing = if (cos(a.dir) >= 0f) 1 else -1
                return a.x > -8f && a.x < GW + 8f
            }
            "fish" -> {
                a.life -= dt
                return a.life > 0f
            }
            "firefly" -> {
                if (!dusk || sim.raining()) return false
                a.dir += (sin(a.phase * 0.9f + a.id) * 3f) * dt
                a.x += cos(a.dir) * 0.35f * dt
                a.y += sin(a.dir) * 0.25f * dt
                a.x += (a.homeX - a.x) * 0.2f * dt
                a.y += (a.homeY - a.y) * 0.2f * dt
                return true
            }
            "owl" -> {
                if (!sim.isNight()) return false
                a.dir += (sin(a.phase * 0.4f + a.id) * 0.9f) * dt
                a.x += cos(a.dir) * 1.4f * dt
                a.y += sin(a.dir) * 0.9f * dt
                a.x += (a.homeX - a.x) * 0.25f * dt
                a.y += (a.homeY - a.y) * 0.25f * dt
                a.facing = if (cos(a.dir) >= 0f) 1 else -1
                return true
            }
            "bat" -> {
                if (!dusk || sim.seasonIdx() == 3) return false
                a.dir += (sin(a.phase * 3.1f + a.id * 2f) * 6f) * dt
                a.x += cos(a.dir) * 2.4f * dt
                a.y += sin(a.dir) * 1.8f * dt
                a.x += (a.homeX - a.x) * 0.3f * dt
                a.y += (a.homeY - a.y) * 0.3f * dt
                a.facing = if (cos(a.dir) >= 0f) 1 else -1
                return true
            }
            "dragonfly" -> {
                if (sim.seasonIdx() == 3) return false
                // hovers, then darts a few steps, always near the water it hatched in
                if (a.pause > 0f) { a.pause -= dt; return true }
                a.dir += (rnd.nextFloat() - 0.5f) * 2f
                a.x += cos(a.dir) * 4.5f * dt
                a.y += sin(a.dir) * 3f * dt
                a.x += (a.homeX - a.x) * 0.6f * dt
                a.y += (a.homeY - a.y) * 0.6f * dt
                if (rnd.nextInt(40) == 0) a.pause = 0.4f + rnd.nextFloat() * 1.2f
                a.facing = if (cos(a.dir) >= 0f) 1 else -1
                return true
            }
            "fox" -> if (foxThink(a, dt)) return true
        }
        val scare = when (a.kind) { "rabbit" -> 3.4f; "deer" -> 4.6f; "duck" -> 2.8f; "fox" -> 4.2f; "squirrel" -> 2.6f; "hedgehog" -> 2.2f; else -> 1.6f }
        if (a.flee <= 0f && threat(a, scare)) {
            a.flee = 1.6f + rnd.nextFloat()
            // run directly away from whoever is closest
            var cx = 0f
            var cy = 0f
            var bd = 99f
            if (a.kind == "rabbit" || a.kind == "squirrel") for (f in animals) {
                if (f.kind != "fox") continue
                val d = hypot(f.x - a.x, f.y - a.y)
                if (d < bd) { bd = d; cx = f.x; cy = f.y }
            }
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
                "squirrel" -> 0.4f + rnd.nextFloat() * 2.2f
                "hedgehog" -> 2f + rnd.nextFloat() * 4f
                "fox" -> 3f + rnd.nextFloat() * 6f
                else -> 1.5f + rnd.nextFloat() * 5f
            }
            a.grazing = a.kind == "deer" || a.kind == "rabbit"
            // pick the next spot near home
            val r = when (a.kind) { "frog" -> 1.6f; "duck" -> 5f; "deer" -> 6f; "fox" -> 7f; "squirrel" -> 3f; "hedgehog" -> 3.5f; else -> 4.5f }
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
