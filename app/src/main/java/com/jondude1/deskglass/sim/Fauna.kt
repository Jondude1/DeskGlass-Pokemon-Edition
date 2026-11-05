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
    var pet: Pet? = null  // dog: the village pet this animal is
    var bark = 0f         // dog: time left of the current bark
    var fetch = 0         // dog: 0 waiting by the owner, 1 running to the stick, 2 bringing it back
    var curled = false    // dog: asleep beside its owner
    var guard = false     // dog: standing between its owner and a foe
    var otterMode = 0     // otter: 0 swimming, 1 diving, 2 floating on its back with a shell, 3 offering a fish
    var otterT = 0f       // otter: time left in the current mode
    var diveIn = 6f       // otter: time until the next dive
    var catMode = 0       // cat: 0 prowling, 1 stalking a mouse, 2 pouncing, 3 napping, 4 purring at a villager
    var catT = 0f         // cat: time left in the current mode
    var swMode = 0        // swallow: 0 swooping, 1 perched on a roof, 2 flying to a roof
    var swT = 0f          // swallow: time left on the perch
    var swCatch = 6f      // swallow: time until the next insect catch
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
    private var petClock = 1f
    private var giftClock = 20f
    private var catClock = 6f
    private var swClock = 5f
    private var mouseClock = 10f
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
                "otter" -> 1.1f
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
        if (giftClock > 0f) giftClock -= dt
        petClock -= dt
        if (petClock <= 0f) { petClock = 2f; ensurePets() }
        val it = animals.iterator()
        while (it.hasNext()) {
            val a = it.next()
            if (!step(a, dt)) it.remove()
        }
        catClock -= dt
        if (catClock <= 0f) { catClock = 4f; ensureCats() }
        swClock -= dt
        if (swClock <= 0f) { swClock = 3f; ensureSwallows() }
        for (a in animals) if (a.kind != "dog" && a.kind != "cat" && a.kind != "mouse") kindsSeen.add(a.kind)
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
                animals.removeAll { it.kind == "squirrel" || it.kind == "dragonfly" || it.kind == "hedgehog" || it.kind == "otter" }
            } else {
                if (count("squirrel") < 6) spawn("squirrel", { x, y -> land(x, y) && nearForest(x, y, 1) }, avoid = 6f)
                if (sim.seasonIdx() == 1 && count("dragonfly") < 4) {
                    val a = spawn("dragonfly", { x, y -> land(x, y) && nearWater(x, y) }, avoid = 2f)
                    if (a != null) { a.alt = 0.7f; a.dir = rnd.nextFloat() * 6.28f }
                }
                if (count("otter") < 3) spawn("otter", { x, y -> water(x, y) && nearWater(x, y) && !nearWaterOnlyEdge(x, y) }, avoid = 4f)
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

    /** Every village pet gets a dog on the map: new strays trot in from afar, loaded ones start by their owner. */
    private fun ensurePets() {
        for (p in sim.pets.toList()) {
            if (animals.any { it.pet === p }) continue
            val o = sim.villagers.firstOrNull { it.id == p.owner } ?: sim.villagers.firstOrNull() ?: continue
            var a: Animal? = null
            if (p.fresh) a = spawn("dog", { x, y -> land(x, y) }, tries = 200, avoid = 9f)
            if (a == null) {
                a = Animal("dog", o.x + 0.6f, o.y + 0.3f, nextId++)
                a.phase = rnd.nextFloat() * 6.28f
                animals.add(a)
            }
            a.pet = p
            a.speed = 2.2f
            p.fresh = false
        }
    }

    private fun dogWalk(a: Animal, gx: Float, gy: Float, sp: Float, dt: Float): Boolean {
        val dx = gx - a.x
        val dy = gy - a.y
        val d = hypot(dx, dy)
        if (d < 0.05f) { a.moving = false; return true }
        val st = min(sp * dt, d)
        var nx = a.x + dx / d * st
        var ny = a.y + dy / d * st
        if (!land(nx.toInt(), ny.toInt())) {
            // slide along the obstacle if one axis is open
            if (land(nx.toInt(), a.y.toInt())) ny = a.y
            else if (land(a.x.toInt(), ny.toInt())) nx = a.x
            else { a.moving = false; return false }
        }
        a.x = nx
        a.y = ny
        a.moving = true
        if (abs(dx) > 0.03f) a.facing = if (dx > 0f) 1 else -1
        return true
    }

    /** A dog stays close to its owner, sleeps beside them, plays fetch and guards against foes. */
    private fun dogStep(a: Animal, dt: Float): Boolean {
        val pet = a.pet ?: return false
        if (!sim.pets.contains(pet)) return false
        if (a.bark > 0f) a.bark -= dt
        if (a.cool > 0f) a.cool -= dt
        var o = sim.villagers.firstOrNull { it.id == pet.owner }
        if (o == null) {
            val n = sim.villagers.minByOrNull { hypot(it.x - a.x, it.y - a.y) }
            if (n == null) { a.moving = false; return true }
            sim.dogRehomed(pet, n)
            o = n
        }
        val dOwner = hypot(o.x - a.x, o.y - a.y)
        if (dOwner > 30f) { a.x = o.x + 0.6f; a.y = o.y + 0.3f; a.moving = false; return true }
        val wasGuard = a.guard
        a.guard = false
        a.curled = false
        // a foe near the owner: stand between them and bark
        var foe: Predator? = null
        var fd = 10f
        for (p in sim.predators) {
            val d = hypot(p.x - o.x, p.y - o.y)
            if (d < fd) { fd = d; foe = p }
        }
        if (foe != null) {
            a.guard = true
            val ux = (foe.x - o.x) / max(0.1f, fd)
            val uy = (foe.y - o.y) / max(0.1f, fd)
            val gx = o.x + ux * min(2.4f, fd * 0.5f)
            val gy = o.y + uy * min(2.4f, fd * 0.5f)
            dogWalk(a, gx, gy, 3.6f, dt)
            a.facing = if (foe.x >= a.x) 1 else -1
            if (a.cool <= 0f) { a.bark = 1.3f; a.cool = 5f; if (!wasGuard) sim.dogBarks++ }
            return true
        }
        val fetching = o.action == "walk_dog" && o.phase == "working"
        if (!fetching) a.fetch = 0
        if (o.phase == "sleeping") {
            if (dOwner > 1.4f) dogWalk(a, o.x + 0.7f, o.y + 0.3f, 2.4f, dt) else { a.moving = false; a.curled = true }
            return true
        }
        if (fetching) {
            when (a.fetch) {
                0 -> {
                    if (dOwner > 1.3f) dogWalk(a, o.x + 0.8f, o.y + 0.2f, 3.2f, dt)
                    else {
                        a.moving = false
                        a.pause -= dt
                        if (a.pause <= 0f) {
                            // the owner throws a stick somewhere open nearby
                            for (n in 0 until 8) {
                                val ang = rnd.nextFloat() * 6.28f
                                val len = 3f + rnd.nextFloat() * 3f
                                val sx = o.x + cos(ang) * len
                                val sy = o.y + sin(ang) * len * 0.7f
                                if (land(sx.toInt(), sy.toInt())) { a.tx = sx; a.ty = sy; a.fetch = 1; break }
                            }
                            a.pause = 0.6f
                        }
                    }
                }
                1 -> {
                    dogWalk(a, a.tx, a.ty, 4.2f, dt)
                    if (hypot(a.tx - a.x, a.ty - a.y) < 0.5f || !a.moving) a.fetch = 2
                }
                else -> {
                    dogWalk(a, o.x + 0.8f, o.y + 0.2f, 4.0f, dt)
                    if (dOwner < 1.4f) { a.fetch = 0; a.pause = 0.5f }
                }
            }
            return true
        }
        // follow: trot when far, run when very far, otherwise amble about near the owner
        if (dOwner > 3.2f) {
            dogWalk(a, o.x + (if (a.id % 2 == 0) 0.9f else -0.9f), o.y + 0.4f, if (dOwner > 7f) 3.8f else 2.2f, dt)
            a.pause = 0f
        } else if (a.pause > 0f) {
            a.pause -= dt
            a.moving = false
        } else if (a.tx != a.x || a.ty != a.y) {
            if (hypot(a.tx - a.x, a.ty - a.y) < 0.2f || !dogWalk(a, a.tx, a.ty, 1.2f, dt)) { a.tx = a.x; a.ty = a.y; a.pause = 2f + rnd.nextFloat() * 4f; a.moving = false }
        } else {
            a.tx = o.x + (rnd.nextFloat() - 0.5f) * 4.5f
            a.ty = o.y + (rnd.nextFloat() - 0.5f) * 3f
            if (!land(a.tx.toInt(), a.ty.toInt())) { a.tx = a.x; a.ty = a.y; a.pause = 1.5f }
        }
        return true
    }


    /**
     * Otters in the lake: they dive for fish, float on their backs cracking shells, and when a villager is
     * watching from the shore a diver now and then surfaces with a silver fish and leaves it as a gift.
     */
    private fun otterThink(a: Animal, dt: Float): Boolean {
        if (a.otterMode != 0) {
            a.otterT -= dt
            a.moving = false
            if (a.otterT > 0f) return true
            if (a.otterMode == 1) {
                val near = sim.villagers.any { hypot(it.x - a.x, it.y - a.y) < 7f }
                if (near && giftClock <= 0f) {
                    a.otterMode = 3
                    a.otterT = 3.2f
                    giftClock = 35f + rnd.nextFloat() * 25f
                    sim.otterGift(a.x, a.y)
                    note("otter gift")
                } else {
                    a.otterMode = 2
                    a.otterT = 4f + rnd.nextFloat() * 3f
                    note("otter float")
                }
            } else {
                a.otterMode = 0
                a.pause = 0.4f
            }
            return true
        }
        a.diveIn -= dt
        if (a.diveIn <= 0f && a.flee <= 0f) {
            a.otterMode = 1
            a.otterT = 2.4f + rnd.nextFloat() * 2f
            a.diveIn = 12f + rnd.nextFloat() * 14f
            a.moving = false
            note("otter dive")
            return true
        }
        return false
    }

    // ---- village cats: they slip in, nap by the hearth, stalk the mice in the grain and purr for passers-by ----
    private fun catWanted(): Int {
        val n = sim.villagers.size
        if (n < 6 || sim.buildings.count { it.type == "hut" || it.type == "house" } < 3) return 0
        return min(4, 1 + n / 8)
    }

    private fun catHomeSpot(): Pair<Float, Float>? {
        val cold = sim.seasonIdx() == 3 || sim.isNight() || sim.raining()
        val fire = sim.buildings.filter { it.type == "campfire" }
        if (cold && fire.isNotEmpty()) { val b = fire[rnd.nextInt(fire.size)]; return Pair(b.x + 0.5f + (rnd.nextFloat() - 0.5f) * 2.4f, b.y + 1.1f) }
        val homes = sim.buildings.filter { it.type == "hut" || it.type == "house" || it.type == "granary" || it.type == "farm" || it.type == "market" || it.type == "workshop" }
        if (homes.isEmpty()) return null
        val b = homes[rnd.nextInt(homes.size)]
        return Pair(b.x + 0.5f + (rnd.nextFloat() - 0.5f) * 3f, b.y + 1.4f)
    }

    private fun ensureCats() {
        val want = catWanted()
        val have = count("cat")
        if (have > want) { animals.firstOrNull { it.kind == "cat" && far(it.x, it.y, 3f) }?.let { animals.remove(it) }; return }
        if (have < want && (have == 0 || rnd.nextInt(3) == 0)) {
            val spot = catHomeSpot() ?: return
            val x = spot.first.coerceIn(1f, GW - 2f)
            val y = spot.second.coerceIn(1f, GH - 2f)
            if (!land(x.toInt(), y.toInt())) return
            val a = Animal("cat", x, y, nextId++)
            a.homeX = x; a.homeY = y; a.speed = 1.5f; a.pause = 1f
            a.facing = if (rnd.nextBoolean()) 1 else -1
            a.cool = 8f
            animals.add(a)
            note("cat arrives")
            sim.catArrives()
        }
        // mice stir in the grain wherever there is a granary, farm or mill, but only while a cat is about to hunt them
        mouseClock -= 4f
        if (mouseClock <= 0f && have > 0) {
            mouseClock = 10f + rnd.nextFloat() * 12f
            val barns = sim.buildings.filter { it.type == "granary" || it.type == "farm" || it.type == "mill" }
            if (barns.isNotEmpty() && count("mouse") < 3) {
                val b = barns[rnd.nextInt(barns.size)]
                val mx = b.x + 0.5f + (rnd.nextFloat() - 0.5f) * 3f
                val my = b.y + 1.3f + rnd.nextFloat() * 0.8f
                if (land(mx.toInt(), my.toInt()) && mx > 1f && my > 1f && mx < GW - 1f && my < GH - 1f) {
                    val m = Animal("mouse", mx, my, nextId++)
                    m.homeX = mx; m.homeY = my; m.speed = 1.3f; m.life = 40f + rnd.nextFloat() * 30f
                    m.pause = rnd.nextFloat() * 2f
                    animals.add(m)
                    note("mouse")
                }
            }
        }
    }

    private fun catStep(a: Animal, dt: Float): Boolean {
        if (a.cool > 0f) a.cool -= dt
        // a prowling predator sends the cat bolting; it never fights
        val danger = sim.predators.any { hypot(it.x - a.x, it.y - a.y) < 3.5f }
        if (danger && a.catMode != 0) { a.catMode = 0; a.curled = false; a.target = 0 }
        when (a.catMode) {
            3 -> {
                a.catT -= dt; a.moving = false; a.curled = true
                if (a.catT <= 0f || danger) { a.catMode = 0; a.curled = false; a.pause = 0.8f }
                return true
            }
            4 -> {
                a.catT -= dt; a.moving = false
                if (a.catT <= 0f) { a.catMode = 0; a.pause = 0.5f }
                return true
            }
            2 -> {
                a.catT -= dt; a.moving = false
                if (a.catT <= 0f) {
                    val m = animals.firstOrNull { it.id == a.target && it.kind == "mouse" }
                    if (m != null && m.life > 0f && hypot(m.x - a.x, m.y - a.y) < 1.4f) {
                        m.life = -1f
                        sim.catMouse(a.x, a.y)
                        note("cat catch")
                        a.cool = 14f
                    } else {
                        note("cat miss")
                        a.cool = 6f
                    }
                    a.catMode = 0; a.target = 0; a.pause = 1.5f
                }
                return true
            }
            1 -> {
                val m = animals.firstOrNull { it.id == a.target && it.kind == "mouse" && it.life > 0f }
                a.chase += dt
                if (m == null || a.chase > 12f) { a.catMode = 0; a.target = 0; a.cool = 5f; return true }
                val dx = m.x - a.x
                val dy = m.y - a.y
                val d = hypot(dx, dy)
                if (d < 1.0f) { a.catMode = 2; a.catT = 0.55f; note("cat pounce"); return true }
                val sp = (if (d < 2.5f) 1.0f else 2.0f) * dt
                val nx = a.x + dx / d * min(sp, d)
                val ny = a.y + dy / d * min(sp, d)
                if (!land(nx.toInt(), ny.toInt())) { a.catMode = 0; a.target = 0; a.cool = 5f; return true }
                a.x = nx; a.y = ny; a.moving = true
                if (abs(dx) > 0.02f) a.facing = if (dx > 0) 1 else -1
                return true
            }
        }
        // prowling
        if (danger) { a.flee = 1.5f }
        if (a.flee > 0f) a.flee -= dt
        if (a.cool <= 0f) {
            // a passing villager stops to stroke the cat
            val v = sim.villagers.firstOrNull { hypot(it.x - a.x, it.y - a.y) < 1.3f && it.action != "attack" && it.hp > 20f }
            if (v != null && a.flee <= 0f) {
                a.catMode = 4; a.catT = 3.2f + rnd.nextFloat() * 2f; a.cool = 30f + rnd.nextFloat() * 25f
                a.facing = if (v.x >= a.x) 1 else -1
                sim.catPetted(v, a.x, a.y)
                note("cat purr")
                return true
            }
            var best: Animal? = null
            var bd = 7f
            for (o in animals) {
                if (o.kind != "mouse" || o.life <= 0f) continue
                val d = hypot(o.x - a.x, o.y - a.y)
                if (d < bd) { bd = d; best = o }
            }
            if (best != null && a.flee <= 0f) {
                a.catMode = 1; a.target = best.id; a.chase = 0f; a.pause = 0f
                note("cat stalk")
                return true
            }
        }
        if (a.pause > 0f) { a.pause -= dt; a.moving = false; return true }
        val dx = a.tx - a.x
        val dy = a.ty - a.y
        val d = hypot(dx, dy)
        if (d < 0.15f) {
            a.moving = false
            // doze off a while, in the sun by day and by the fire when it is cold or dark
            if (rnd.nextInt(100) < 38) {
                a.catMode = 3; a.catT = 14f + rnd.nextFloat() * 26f; a.curled = true
                note("cat nap")
                return true
            }
            a.pause = 1.5f + rnd.nextFloat() * 4f
            val spot = catHomeSpot()
            if (spot != null && rnd.nextInt(3) == 0) { a.homeX = spot.first; a.homeY = spot.second }
            for (n in 0 until 8) {
                val nx = a.homeX + (rnd.nextFloat() - 0.5f) * 3.2f
                val ny = a.homeY + (rnd.nextFloat() - 0.5f) * 2.2f
                if (land(nx.toInt(), ny.toInt()) && nx > 0.5f && ny > 0.5f && nx < GW - 0.5f && ny < GH - 0.5f) { a.tx = nx; a.ty = ny; break }
            }
            return true
        }
        val sp = a.speed * (if (a.flee > 0f) 2.8f else 1f) * dt
        val nx = a.x + dx / d * min(sp, d)
        val ny = a.y + dy / d * min(sp, d)
        if (!land(nx.toInt(), ny.toInt())) { a.tx = a.x; a.ty = a.y; a.moving = false; return true }
        a.x = nx; a.y = ny; a.moving = true
        if (abs(dx) > 0.02f) a.facing = if (dx > 0) 1 else -1
        return true
    }

    // ---- swallows: they return each spring, swoop over lake and roofs, perch under the eaves and leave in autumn ----
    private fun swallowSeason(): Boolean = sim.seasonIdx() <= 1 && !sim.isNight() && !sim.storm() && !sim.snowing()

    private fun ensureSwallows() {
        val n = sim.villagers.size
        if (!swallowSeason() || n < 6 || sim.buildings.count { it.type == "hut" || it.type == "house" } < 3) return
        val want = min(6, 2 + n / 5)
        if (count("swallow") >= want || rnd.nextInt(2) != 0) return
        val fromLeft = rnd.nextBoolean()
        val x = if (fromLeft) -1.5f else GW + 1.5f
        val y = (3f + rnd.nextFloat() * (GH - 6f)).coerceIn(1f, GH - 2f)
        val a = Animal("swallow", x, y, nextId++)
        a.alt = 1.4f; a.dir = if (fromLeft) 0f else PI.toFloat(); a.speed = 3.4f
        a.facing = if (fromLeft) 1 else -1
        a.homeX = GW * 0.5f; a.homeY = GH * 0.5f
        a.swCatch = 4f + rnd.nextFloat() * 8f
        animals.add(a)
        note("swallow")
        sim.swallowArrive(sim.villagers.firstOrNull()?.x ?: GW * 0.5f, sim.villagers.firstOrNull()?.y ?: GH * 0.5f)
    }

    private fun swallowStep(a: Animal, dt: Float): Boolean {
        // autumn, night and storms send them away over the edge of the map
        if (!swallowSeason()) {
            if (a.swMode == 1) { a.swMode = 0; a.dir = if (a.x < GW * 0.5f) PI.toFloat() else 0f }
            if (a.swMode == 2) a.swMode = 0
            a.dir = if (a.x < GW * 0.5f) PI.toFloat() else 0f
            a.x += cos(a.dir) * 4.2f * dt
            a.alt = min(2.2f, a.alt + dt * 0.5f)
            a.facing = if (cos(a.dir) >= 0f) 1 else -1
            if (a.x > -3f && a.x < GW + 3f) return true
            note("swallow leaves")
            return false
        }
        when (a.swMode) {
            1 -> {
                a.swT -= dt; a.moving = false
                a.alt = 1.05f
                if (a.swT <= 0f || sim.predators.any { hypot(it.x - a.x, it.y - a.y) < 3f }) { a.swMode = 0; a.dir = rnd.nextFloat() * 6.28f; a.swCatch = 5f + rnd.nextFloat() * 8f }
                return true
            }
            2 -> {
                val dx = a.tx - a.x
                val dy = a.ty - a.y
                val d = hypot(dx, dy)
                a.alt = max(1.05f, a.alt - dt * 0.8f)
                if (d < 0.25f) {
                    a.x = a.tx; a.y = a.ty; a.swMode = 1; a.swT = 6f + rnd.nextFloat() * 10f
                    sim.swallowPerch(a.x, a.y); note("swallow perch")
                    return true
                }
                val sp = 3.0f * dt
                a.x += dx / d * min(sp, d); a.y += dy / d * min(sp, d)
                if (abs(dx) > 0.02f) a.facing = if (dx > 0) 1 else -1
                return true
            }
        }
        // swooping: wide curving flight, low over the water and the fields, pulled gently toward the village centre
        a.dir += sin(a.phase * 1.7f + a.id) * 2.2f * dt
        a.x += cos(a.dir) * a.speed * dt
        a.y += sin(a.dir) * a.speed * 0.55f * dt
        a.x += (a.homeX - a.x) * 0.08f * dt
        a.y += (a.homeY - a.y) * 0.08f * dt
        a.alt = 0.55f + 0.55f * (0.5f + 0.5f * sin(a.phase * 0.9f + a.id * 1.3f))
        a.x = a.x.coerceIn(-1f, GW + 1f)
        a.y = a.y.coerceIn(1f, GH - 1f)
        a.facing = if (cos(a.dir) >= 0f) 1 else -1
        a.swCatch -= dt
        if (a.swCatch <= 0f) {
            a.swCatch = 6f + rnd.nextFloat() * 10f
            val roofs = sim.buildings.filter { it.type == "hut" || it.type == "house" }
            if (roofs.isNotEmpty() && rnd.nextInt(3) == 0) {
                val b = roofs[rnd.nextInt(roofs.size)]
                a.tx = b.x + 0.5f + (rnd.nextFloat() - 0.5f) * 0.8f; a.ty = b.y + 0.7f
                a.swMode = 2
            } else {
                sim.swallowCatch(); note("swallow catch")
            }
        }
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
            "otter" -> if (otterThink(a, dt)) return true
            "dog" -> return dogStep(a, dt)
            "cat" -> return catStep(a, dt)
            "swallow" -> return swallowStep(a, dt)
            "mouse" -> { a.life -= dt; if (a.life <= 0f) return false }
        }
        val scare = when (a.kind) { "rabbit" -> 3.4f; "deer" -> 4.6f; "duck" -> 2.8f; "fox" -> 4.2f; "squirrel" -> 2.6f; "hedgehog" -> 2.2f; "otter" -> 2.2f; "mouse" -> 2.4f; else -> 1.6f }
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
                "otter" -> 0.6f + rnd.nextFloat() * 2.4f
                "fox" -> 3f + rnd.nextFloat() * 6f
                else -> 1.5f + rnd.nextFloat() * 5f
            }
            a.grazing = a.kind == "deer" || a.kind == "rabbit"
            // pick the next spot near home
            val r = when (a.kind) { "frog" -> 1.6f; "duck" -> 5f; "deer" -> 6f; "fox" -> 7f; "squirrel" -> 3f; "hedgehog" -> 3.5f; "otter" -> 6f; else -> 4.5f }
            for (n in 0 until 8) {
                val nx = a.homeX + (rnd.nextFloat() - 0.5f) * 2f * r
                val ny = a.homeY + (rnd.nextFloat() - 0.5f) * 2f * r * 0.7f
                val ok = if (a.kind == "duck" || a.kind == "otter") water(nx.toInt(), ny.toInt()) else land(nx.toInt(), ny.toInt())
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
        val ok = if (a.kind == "duck" || a.kind == "otter") water(nx.toInt(), ny.toInt()) else land(nx.toInt(), ny.toInt())
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
