package com.jondude1.deskglass.sim

import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

/** Anything that walks around the map on its own. Positions are in tiles. */
open class Mover(var x: Float, var y: Float) {
    var tx = x
    var ty = y
    var pause = 0f
    var facing = 1
    var moving = false
}

/** Where a kept critter stands. */
class Body(x: Float, y: Float) : Mover(x, y)

/** A wild critter wandering near the village. */
class Wild(val c: Critter, x: Float, y: Float) : Mover(x, y) {
    var claimed = -1
    var fade = 0f
    var life = 160f
    var leaving = false
}

/** A bit of floating text: damage, XP, what a helper brought home. */
class Pop(val x: Float, val y: Float, val text: String, val color: Long, val born: Double, val life: Float = 1.6f)

/** A critter changing shape. The species flips partway through, at the white flash. */
class EvolveScene(val uid: Int, val from: Int, val to: Int, var x: Float, var y: Float, val born: Double) {
    var flipped = false
    companion object {
        const val DURATION = 5.2
    }
}

/** One meeting between a villager and a critter: a catch, a berry offering, a friendly bout or training. */
class Enc(val kind: String, val v: Villager) {
    var v2: Villager? = null
    var mine: Critter? = null
    var mine2: Critter? = null
    var foe: Critter? = null
    var wild: Wild? = null

    /** walk | intro | turn | snare | offer | result */
    var state = "walk"
    var clock = 0f
    var battle: Battle? = null
    var turn: Turn? = null
    var turnT = 0f
    var impacted = false

    /** The two fighters on show. They are the real critters for wild fights and copies for friendly ones. */
    var a: Critter? = null
    var b: Critter? = null
    var dispA = 0f
    var dispB = 0f
    var ax = 0f
    var ay = 0f
    var bx = 0f
    var by = 0f
    var vx = -1f
    var vy = -1f
    var wx = -1f
    var wy = -1f

    var banner = ""
    var bannerT = 0f
    var sub = ""
    var subT = 0f
    val pops = ArrayList<Pop>()

    var snareTry = 0
    var snareT = 0f
    var snareWon = false
    var offerTry = 0
    var offerT = 0f
    var offerWon = false
    var spent = false

    var result = ""
    var hurtSide = -1
    var hurtT = 0f
    var resultLen = 3f
}

/**
 * The critters of the village: wild ones that wander in, villagers who befriend, catch, train and battle them,
 * helpers that work for the settlement, and the evolution of them all.
 */
class CritterWorld(private val sim: CivSim) {
    val critters = ArrayList<Critter>()
    val wilds = ArrayList<Wild>()
    val encounters = ArrayList<Enc>()
    val evolutions = ArrayList<EvolveScene>()
    val pops = ArrayList<Pop>()
    val seen = HashSet<Int>()
    val caught = HashSet<Int>()
    val bodies = HashMap<Int, Body>()
    var yardX = -1f
    var yardY = -1f

    private var nextUid = 1
    private val rnd = Random()
    private var spawnClock = 25f
    private var triggerClock = 4f
    private var anchorClock = 0f
    private val cooldown = HashMap<Int, Double>()
    private val lastSwap = HashMap<Int, Double>()
    private val anchors = HashMap<CType, FloatArray>()

    // ---- public lookups ----

    fun companionOf(v: Villager): Critter? = critters.firstOrNull { it.owner == v.id && it.companion }

    fun ownedBy(v: Villager): List<Critter> = critters.filter { it.owner == v.id }

    /** Each villager has one critter out at a time (the companion). The rest of theirs rest in the pack. */
    fun packed(c: Critter): Boolean = c.owner >= 0 && !c.companion

    fun ownerName(c: Critter): String = sim.villagers.firstOrNull { it.id == c.owner }?.name ?: ""

    fun inEncounter(c: Critter): Boolean = encounters.any { it.mine === c || it.mine2 === c }

    fun evolving(c: Critter): EvolveScene? = evolutions.firstOrNull { it.uid == c.uid }

    fun maxKept(): Int = min(24, sim.villagers.size * 2 + 2)

    fun isTired(c: Critter): Boolean = c.faintedUntil > sim.time

    /** A line a villager can say about their companion, built only from facts. */
    fun factFor(v: Villager): String? {
        val c = companionOf(v) ?: return null
        return "Your companion is ${c.name}, a level ${c.level} ${c.species.typeText} critter" +
            (if (c.species.evolveTo > 0) " that will evolve at level ${c.species.evolveLv}" else "") + "."
    }

    fun reset() {
        critters.clear()
        wilds.clear()
        encounters.clear()
        evolutions.clear()
        pops.clear()
        bodies.clear()
        seen.clear()
        caught.clear()
        cooldown.clear()
        lastSwap.clear()
        anchors.clear()
        yardX = -1f
        yardY = -1f
        nextUid = 1
    }

    // ---- map helpers ----

    private fun walkable(x: Int, y: Int): Boolean =
        x in 0 until GW && y in 0 until GH && sim.tiles[y * GW + x] != T.WATER && sim.buildings.none { it.x == x && it.y == y }

    private fun near(x: Int, y: Int, type: Int, r: Int = 1): Boolean {
        for (dy in -r..r) for (dx in -r..r) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until GW && ny in 0 until GH && sim.tiles[ny * GW + nx] == type) return true
        }
        return false
    }

    private fun centre(): FloatArray {
        val fire = sim.buildings.firstOrNull { it.type == "campfire" }
        return if (fire != null) floatArrayOf(fire.x + 0.5f, fire.y + 0.5f) else floatArrayOf(GW / 2f, GH / 2f)
    }

    private fun habitatOk(h: String, x: Int, y: Int, winter: Boolean, relaxed: Boolean): Boolean {
        val i = y * GW + x
        return when (h) {
            "forest" -> near(x, y, T.FOREST)
            "water" -> near(x, y, T.WATER)
            "rock" -> near(x, y, T.ROCK)
            "ore" -> near(x, y, T.ORE) || near(x, y, T.ROCK)
            "snow" -> if (relaxed) near(x, y, T.WATER) else sim.biome[i] == 2 || (winter && near(x, y, T.WATER))
            "meadow" -> sim.tiles[i] == T.GRASS
            else -> true
        }
    }

    private fun spawnCell(sp: Species, winter: Boolean): Int {
        val c = centre()
        for (pass in 0..2) {
            for (n in 0 until 70) {
                val x = 1 + rnd.nextInt(GW - 2)
                val y = 1 + rnd.nextInt(GH - 2)
                if (!walkable(x, y)) continue
                if (hypot(x + 0.5f - c[0], y + 0.5f - c[1]) < 7f) continue
                if (pass < 2 && !habitatOk(sp.habitat, x, y, winter, pass == 1)) continue
                if (wilds.any { hypot(it.x - x - 0.5f, it.y - y - 0.5f) < 3f }) continue
                return y * GW + x
            }
        }
        return -1
    }

    private fun cellNear(x: Float, y: Float, fx: Float, fy: Float): Int {
        val cx = x.toInt()
        val cy = y.toInt()
        var best = -1
        var bd = Float.MAX_VALUE
        for (dy in -3..3) for (dx in -3..3) {
            val nx = cx + dx
            val ny = cy + dy
            if (!walkable(nx, ny)) continue
            val dw = hypot(nx + 0.5f - x, ny + 0.5f - y)
            if (dw < 1.4f || dw > 2.8f) continue
            val dv = hypot(nx + 0.5f - fx, ny + 0.5f - fy)
            if (dv < bd) {
                bd = dv
                best = ny * GW + nx
            }
        }
        return best
    }

    private fun glide(m: Mover, speed: Float, dt: Float, check: Boolean = true): Boolean {
        val dx = m.tx - m.x
        val dy = m.ty - m.y
        val d = hypot(dx, dy)
        if (d < 0.05f) {
            m.moving = false
            return true
        }
        val s = min(d, speed * dt)
        val nx = m.x + dx / d * s
        val ny = m.y + dy / d * s
        if (check && !walkable(nx.toInt(), ny.toInt())) {
            m.moving = false
            m.tx = m.x
            m.ty = m.y
            return true
        }
        if (abs(dx) > 0.02f) m.facing = if (dx > 0) 1 else -1
        m.x = nx
        m.y = ny
        m.moving = true
        return false
    }

    private fun pickTarget(m: Mover, cx: Float, cy: Float, r: Float) {
        for (n in 0 until 8) {
            val x = cx + (rnd.nextFloat() * 2f - 1f) * r
            val y = cy + (rnd.nextFloat() * 2f - 1f) * r
            if (x < 1f || y < 1f || x > GW - 1f || y > GH - 1f) continue
            if (walkable(x.toInt(), y.toInt())) {
                m.tx = x
                m.ty = y
                return
            }
        }
        m.tx = m.x
        m.ty = m.y
    }

    private fun pop(x: Float, y: Float, text: String, color: Long) {
        pops.add(Pop(x, y, text, color, sim.time))
        while (pops.size > 24) pops.removeAt(0)
    }

    // ---- the tick ----

    fun tick(dt: Float) {
        try {
            spawnTick(dt)
            wildTick(dt)
            keptTick(dt)
            triggerTick(dt)
            for (e in encounters.toList()) encTick(e, dt)
            evoTick()
            pops.removeAll { sim.time - it.born > it.life }
        } catch (e: Exception) {
            lastError = e
            sim.guard(e)
        }
    }

    /** The most recent problem the critter layer hit, kept so a bug can be found. */
    var lastError: Exception? = null

    // ---- wild critters ----

    private fun spawnTick(dt: Float) {
        spawnClock -= dt
        if (spawnClock > 0f) return
        spawnClock = 22f + rnd.nextFloat() * 28f
        if (sim.villagers.size < 2 || sim.time < 45.0) return
        if (wilds.size >= min(5, 2 + sim.age)) return
        val night = sim.darkness() > 0.3f
        val month = java.time.LocalDate.now().monthValue
        val winter = month == 12 || month <= 2
        val sp = Dex.pickWild(rnd, sim.age, night, winter)
        val cell = spawnCell(sp, winter)
        if (cell < 0) return
        val c = Critter(nextUid++, sp.id, Dex.wildLevel(rnd, sp, sim.age))
        c.heal()
        val w = Wild(c, cell % GW + 0.5f, cell / GW + 0.5f)
        w.life = 140f + rnd.nextFloat() * 90f
        w.pause = 1f + rnd.nextFloat() * 2f
        wilds.add(w)
        if (seen.add(sp.id)) {
            sim.note("A wild ${sp.name} is spotted near the village.")
            if (seen.size == 1) sim.mark("The first wild critter was seen near the village.")
        }
    }

    private fun wildTick(dt: Float) {
        val iter = wilds.iterator()
        while (iter.hasNext()) {
            val w = iter.next()
            if (w.leaving) {
                w.fade -= dt * 1.2f
                if (w.fade <= 0f) iter.remove()
                continue
            }
            w.fade = min(1f, w.fade + dt * 1.5f)
            if (w.claimed >= 0) {
                if (encounters.none { it.wild === w }) w.claimed = -1
                w.moving = false
                continue
            }
            w.life -= dt
            if (w.life <= 0f) {
                w.leaving = true
                continue
            }
            if (w.pause > 0f) {
                w.pause -= dt
                w.moving = false
            } else if (glide(w, 1.1f, dt)) {
                w.pause = 1.5f + rnd.nextFloat() * 3.5f
                pickTarget(w, w.x, w.y, 3f)
            }
        }
    }

    // ---- kept critters: bodies, healing, helper work ----

    private fun anchorFor(t: CType): FloatArray? {
        val old = anchors[t]
        if (old != null && old[2] > sim.time) return old
        val c = centre()
        fun tileNear(type: Int): FloatArray? {
            var best = -1
            var bd = Float.MAX_VALUE
            for (i in sim.tiles.indices) {
                if (sim.tiles[i] != type || sim.amt[i] <= 0) continue
                val d = hypot(i % GW + 0.5f - c[0], i / GW + 0.5f - c[1])
                if (d < bd) {
                    bd = d
                    best = i
                }
            }
            return if (best < 0) null else floatArrayOf(best % GW + 0.5f, best / GW + 0.5f)
        }
        fun bldNear(vararg types: String): FloatArray? {
            val b = sim.buildings.filter { it.type in types }.minByOrNull { hypot(it.x + 0.5f - c[0], it.y + 0.5f - c[1]) }
            return if (b == null) null else floatArrayOf(b.x + 0.5f, b.y + 0.5f)
        }
        fun shore(): FloatArray? {
            var best = -1
            var bd = Float.MAX_VALUE
            for (y in 1 until GH - 1) for (x in 1 until GW - 1) {
                if (!walkable(x, y) || !near(x, y, T.WATER)) continue
                val d = hypot(x + 0.5f - c[0], y + 0.5f - c[1])
                if (d < bd) {
                    bd = d
                    best = y * GW + x
                }
            }
            return if (best < 0) null else floatArrayOf(best % GW + 0.5f, best / GW + 0.5f)
        }
        val found = when (t) {
            CType.EMBER -> bldNear("smelter", "campfire")
            CType.TIDE -> shore()
            CType.LEAF -> bldNear("farm", "hut")
            CType.SPARK, CType.IRON -> tileNear(T.ORE) ?: tileNear(T.ROCK)
            CType.STONE -> tileNear(T.ROCK)
            CType.GALE -> tileNear(T.FOREST)
            CType.FROST -> bldNear("hut", "hall", "campfire")
            CType.SHADE -> bldNear("tower", "palisade", "campfire")
            CType.GLOW -> bldNear("campfire")
            CType.VENOM -> bldNear("farm", "palisade", "campfire")
            CType.SPIRIT -> bldNear("hall", "campfire")
            CType.PLAIN -> bldNear("campfire")
        } ?: floatArrayOf(c[0], c[1])
        val out = floatArrayOf(found[0], found[1], (sim.time + 12.0).toFloat())
        anchors[t] = out
        return out
    }

    private fun bodyFor(c: Critter): Body {
        val b = bodies[c.uid]
        if (b != null) return b
        val owner = sim.villagers.firstOrNull { it.id == c.owner }
        val ctr = centre()
        val nb = Body(owner?.x ?: ctr[0], owner?.y ?: ctr[1])
        bodies[c.uid] = nb
        return nb
    }

    private fun keptTick(dt: Float) {
        val alive = sim.villagers.map { it.id }.toSet()
        for (c in critters) {
            if (c.owner >= 0 && c.owner !in alive) {
                val was = c.name
                c.owner = -1
                c.companion = false
                sim.note("$was stays on in the village.")
            }
        }
        teamTick()
        for (c in critters) {
            val body = bodyFor(c)
            // tiredness and healing
            if (c.faintedUntil > 0.0) {
                if (sim.time >= c.faintedUntil) {
                    c.faintedUntil = 0.0
                    c.hp = max(c.hp, (c.maxHp * 0.4f).toInt())
                }
            } else if (c.hp < c.maxHp && !inEncounter(c)) {
                c.regen += c.maxHp * (if (packed(c)) 0.03f else 0.012f) * dt
                if (c.regen >= 1f) {
                    val n = c.regen.toInt()
                    c.regen -= n
                    c.hp = min(c.maxHp, c.hp + n)
                }
            }
            if (inEncounter(c) || evolving(c) != null) continue
            if (packed(c)) {
                body.moving = false
                continue
            }
            val owner = if (c.companion) sim.villagers.firstOrNull { it.id == c.owner } else null
            if (c.faintedUntil > 0.0) {
                body.moving = false
                continue
            }
            if (owner != null) {
                val tx = owner.x - owner.facing * 0.9f
                val ty = owner.y + 0.25f
                if (hypot(tx - body.x, ty - body.y) > 6f) {
                    body.x = tx
                    body.y = ty
                }
                body.tx = tx
                body.ty = ty
                glide(body, if (owner.phase == "walking") 3.4f else 2.2f, dt, check = false)
                if (owner.phase != "walking" && hypot(tx - body.x, ty - body.y) < 0.25f) {
                    body.facing = owner.facing
                    body.moving = false
                }
            } else {
                val a = anchorFor(c.species.t1)
                if (body.pause > 0f) {
                    body.pause -= dt
                    body.moving = false
                } else if (glide(body, 1.2f, dt)) {
                    body.pause = 1f + rnd.nextFloat() * 3f
                    if (a != null) pickTarget(body, a[0], a[1], 1.8f)
                }
            }
            // work
            if (c.faintedUntil <= 0.0) {
                c.work += dt
                val interval = (if (c.companion) 70f else 30f) / (1f + c.level / 30f)
                if (c.work >= interval) {
                    c.work = 0f
                    val t = if (c.species.t2 != null && c.cycle % 2 == 1) c.species.t2!! else c.species.t1
                    c.cycle++
                    produce(c, t, body)
                }
            }
        }
    }

    /** Keeps exactly one critter out per villager, and trades them over now and then so the whole pack gets a turn. */
    private fun teamTick() {
        for (v in sim.villagers) {
            val mine = critters.filter { it.owner == v.id }
            if (mine.isEmpty()) continue
            val out = mine.firstOrNull { it.companion }
            val fit = mine.filter {
                !it.companion && it.faintedUntil <= 0.0 && it.hpFrac >= 0.5f && !inEncounter(it) && evolving(it) == null
            }
            if (out == null) {
                val pick = fit.maxByOrNull { it.level } ?: mine.maxByOrNull { it.hpFrac } ?: continue
                bringOut(v, pick, null)
                continue
            }
            if (busy(v) || inEncounter(out) || evolving(out) != null || fit.isEmpty()) continue
            val since = sim.time - (lastSwap[v.id] ?: sim.time.also { lastSwap[v.id] = it })
            val tired = out.faintedUntil > 0.0 || out.hpFrac < 0.3f
            if (!tired && since < 150.0) continue
            val order = mine.sortedBy { it.uid }
            val at = order.indexOf(out)
            val next = (1..order.size).map { order[(at + it) % order.size] }.firstOrNull { it in fit } ?: continue
            bringOut(v, next, out)
        }
    }

    private fun bringOut(v: Villager, c: Critter, old: Critter?) {
        lastSwap[v.id] = sim.time
        if (old != null) {
            old.companion = false
            val ob = bodyFor(old)
            pop(ob.x, ob.y - 0.9f, "rests", old.species.t1.color)
        }
        c.companion = true
        val b = bodyFor(c)
        b.x = v.x - v.facing * 0.9f
        b.y = v.y + 0.25f
        b.tx = b.x
        b.ty = b.y
        b.moving = false
        pop(b.x, b.y - 0.9f, "${c.name} out", c.species.t1.color)
    }

    private fun produce(c: Critter, type: CType, body: Body) {
        val lv = c.level
        var text = ""
        when (type) {
            CType.EMBER -> {
                if (sim.age >= 2 && sim.ore >= 2 && sim.wood >= 1 && sim.buildings.any { it.type == "smelter" }) {
                    val n = 1 + lv / 20
                    sim.ore -= 2
                    sim.wood -= 1
                    sim.metal += n
                    sim.totalSmelted += n
                    text = "+$n metal"
                } else {
                    sim.wood += 1
                    text = "+1 wood"
                }
            }
            CType.TIDE, CType.FROST -> {
                val n = 2 + lv / 12
                sim.food += n
                text = "+$n food"
            }
            CType.LEAF -> {
                val n = 2 + lv / 12
                sim.food += n
                val farms = sim.buildings.filter { it.type == "farm" }
                if (farms.isNotEmpty()) {
                    val f = farms[rnd.nextInt(farms.size)]
                    f.boostUntil = max(f.boostUntil, sim.time + 15.0)
                }
                text = "+$n food"
            }
            CType.SPARK, CType.IRON -> {
                val n = 1 + lv / 20
                if (sim.age >= 2) {
                    sim.ore += n
                    text = "+$n ore"
                } else {
                    sim.stone += n
                    text = "+$n stone"
                }
            }
            CType.STONE -> {
                val n = 1 + lv / 15
                sim.stone += n
                text = "+$n stone"
            }
            CType.GALE -> {
                val n = 1 + lv / 12
                sim.wood += n
                text = "+$n wood"
            }
            CType.SHADE -> {
                if (rnd.nextInt(3) == 0) {
                    sim.hides += 1
                    text = "+1 hide"
                }
            }
            CType.VENOM -> {
                sim.food += 1
                text = "+1 food"
            }
            CType.GLOW, CType.SPIRIT -> {
                for (v in sim.villagers) v.happiness = min(100f, v.happiness + 2f)
                text = "cheer"
                if (type == CType.SPIRIT && rnd.nextInt(12) == 0) {
                    sim.wood += 4
                    text = "found wood"
                }
            }
            CType.PLAIN -> {
                sim.food += 1
                text = "+1 food"
            }
        }
        if (text.isNotEmpty()) pop(body.x, body.y - 0.9f, text, type.color)
        c.gainXp(3)
        checkEvolve(c)
    }

    /** Damage per second this village's critters deal to a predator standing at a spot. */
    fun fightPower(px: Float, py: Float): Float {
        var sum = 0f
        val night = sim.isNight()
        for (c in critters) {
            if (c.faintedUntil > 0.0 || inEncounter(c) || packed(c)) continue
            val b = bodies[c.uid] ?: continue
            val d = hypot(b.x - px, b.y - py)
            val t = c.species.t1
            when {
                c.companion && d < 3.4f -> sum += 3f + c.level * 0.5f
                t == CType.SHADE && night && d < 6f -> sum += 2f + c.level * 0.35f
                t == CType.VENOM && d < 5f -> sum += 1.2f + c.level * 0.25f
                !c.companion && d < 2.6f -> sum += 1.5f + c.level * 0.2f
            }
        }
        return sum
    }

    // ---- who goes looking for trouble ----

    /** Villagers only snare a species when the village has room and fewer than two of it already. */
    private fun wantsMore(v: Villager, foe: Critter): Boolean {
        if (critters.size >= maxKept() || critters.count { it.sp == foe.sp } >= 2) return false
        val owned = critters.count { it.owner == v.id }
        if (owned < MAX_TEAM) return true
        // a full team only makes room for something clearly better than its weakest resting member
        val weak = weakestSwappable(v) ?: return false
        return worth(foe) >= worth(weak) + 3
    }

    /** How good a critter is to keep: level, how far it has evolved and how rare it is. */
    private fun worth(c: Critter): Int = c.level + c.species.stage * 4 + c.species.rarity * 3

    private fun weakestSwappable(v: Villager): Critter? =
        critters.filter { it.owner == v.id && !it.companion && !inEncounter(it) && evolving(it) == null }.minByOrNull { worth(it) }

    private fun busy(v: Villager): Boolean = encounters.any { it.v === v || it.v2 === v }

    private fun ready(c: Critter): Boolean = c.faintedUntil <= 0.0 && c.hpFrac >= 0.5f && !inEncounter(c) && evolving(c) == null

    private fun triggerTick(dt: Float) {
        triggerClock -= dt
        if (triggerClock > 0f) return
        triggerClock = 1.3f
        if (encounters.size >= 2 || sim.predators.isNotEmpty() || sim.villagers.size < 2) return
        val idle = sim.villagers.filter {
            !sim.isChild(it) && it.phase == "idle" && it.idleFor >= 0.5f && !busy(it) &&
                it.hunger < 75f && it.hp > 40f && it.energy > 20f
        }.shuffled(rnd)
        for (v in idle) {
            val comp = companionOf(v)
            val wild = wilds.filter { it.claimed < 0 && it.fade >= 1f && !it.leaving && hypot(it.x - v.x, it.y - v.y) < 16f }
                .minByOrNull { hypot(it.x - v.x, it.y - v.y) }
            if (wild != null) {
                val bold = v.job == "Hunter" || v.job == "Scout" || v.job == "Forager"
                val p = if (bold) 0.55f else 0.3f
                if (comp == null) {
                    if (sim.food >= 2 && critters.size < maxKept() && rnd.nextFloat() < p) {
                        startBefriend(v, wild)
                        return
                    }
                } else if (ready(comp) && rnd.nextFloat() < p) {
                    startWild(v, comp, wild)
                    return
                }
            }
            if (comp != null && ready(comp) && comp.level < Dex.MAX_LEVEL &&
                sim.time >= (cooldown[v.id] ?: 0.0) && rnd.nextFloat() < 0.12f
            ) {
                val partner = idle.firstOrNull {
                    it !== v && companionOf(it)?.let { pc -> ready(pc) } == true && hypot(it.x - v.x, it.y - v.y) < 12f
                }
                cooldown[v.id] = sim.time + 80.0
                if (partner != null) {
                    cooldown[partner.id] = sim.time + 80.0
                    if (startSpar(v, partner, comp, companionOf(partner)!!)) return
                } else if (startTrain(v, comp)) return
            }
        }
    }

    private fun startWild(v: Villager, comp: Critter, w: Wild) {
        val goal = cellNear(w.x, w.y, v.x, v.y)
        if (goal < 0) return
        val e = Enc("wild", v)
        e.mine = comp
        e.foe = w.c
        e.wild = w
        encounters.add(e)
        if (!sim.walkTo(v, "critter_catch", goal)) {
            encounters.remove(e)
            return
        }
        w.claimed = v.id
        v.say = "A wild ${w.c.species.name}!"
        v.sayLeft = 3f
    }

    private fun startBefriend(v: Villager, w: Wild) {
        val goal = cellNear(w.x, w.y, v.x, v.y)
        if (goal < 0) return
        val e = Enc("befriend", v)
        e.foe = w.c
        e.wild = w
        encounters.add(e)
        if (!sim.walkTo(v, "critter_befriend", goal)) {
            encounters.remove(e)
            return
        }
        w.claimed = v.id
        v.say = "Maybe it likes berries."
        v.sayLeft = 3f
    }

    private fun startSpar(v: Villager, v2: Villager, c1: Critter, c2: Critter): Boolean {
        val goal = cellNear(v2.x, v2.y, v.x, v.y)
        if (goal < 0) return false
        val e = Enc("spar", v)
        e.v2 = v2
        e.mine = c1
        e.mine2 = c2
        encounters.add(e)
        if (!sim.walkTo(v, "critter_spar", goal)) {
            encounters.remove(e)
            return false
        }
        v2.action = "critter_spar"
        v2.phase = "working"
        v2.workLeft = 999f
        v2.path = IntArray(0)
        v2.talkTo = null
        v.say = "Care for a friendly match, ${v2.name}?"
        v.sayLeft = 3f
        return true
    }

    private fun ensureYard() {
        if (yardX >= 0f) return
        val c = centre()
        for (n in 0 until 60) {
            val a = rnd.nextFloat() * 6.28f
            val r = 3.5f + rnd.nextFloat() * 2.5f
            val x = (c[0] + Math.cos(a.toDouble()).toFloat() * r).toInt()
            val y = (c[1] + Math.sin(a.toDouble()).toFloat() * r).toInt()
            if (x < 3 || y < 3 || x > GW - 4 || y > GH - 4) continue
            if (!walkable(x, y) || sim.tiles[y * GW + x] != T.GRASS) continue
            if (sim.buildings.any { abs(it.x - x) <= 2 && abs(it.y - y) <= 2 }) continue
            yardX = x + 0.5f
            yardY = y + 0.5f
            return
        }
        yardX = c[0] + 4f
        yardY = c[1] + 2f
    }

    private fun startTrain(v: Villager, comp: Critter): Boolean {
        ensureYard()
        val goal = cellNear(yardX, yardY, v.x, v.y)
        if (goal < 0) return false
        val e = Enc("train", v)
        e.mine = comp
        encounters.add(e)
        if (!sim.walkTo(v, "critter_train", goal)) {
            encounters.remove(e)
            return false
        }
        v.say = "Time to train, ${comp.name}."
        v.sayLeft = 3f
        return true
    }

    // ---- hooks from the village sim ----

    /** A villager on an encounter walk has arrived. */
    fun onArrive(v: Villager) {
        val e = encounters.firstOrNull { it.v === v && it.state == "walk" }
        if (e == null) {
            sim.releaseVillager(v)
            return
        }
        v.phase = "working"
        v.workLeft = 999f
        prepare(e)
    }

    /** Called while a villager is "working" on an encounter. Frees anyone left stranded. */
    fun onWork(v: Villager) {
        if (!busy(v)) sim.releaseVillager(v)
    }

    private fun clampX(x: Float) = x.coerceIn(1f, GW - 1f)

    private fun prepare(e: Enc) {
        val v = e.v
        e.clock = 0f
        e.state = "intro"
        when (e.kind) {
            "wild", "befriend" -> {
                val w = e.wild!!
                val dir = if (v.x >= w.x) 1 else -1
                e.bx = w.x
                e.by = w.y
                var ax = clampX(w.x + dir * 1.8f)
                if (!walkable(ax.toInt(), w.y.toInt())) ax = clampX(w.x - dir * 1.8f)
                e.ax = ax
                e.ay = w.y + 0.1f
                e.vx = clampX(if (ax > w.x) ax + 1.1f else ax - 1.1f)
                e.vy = w.y + 0.25f
                if (e.kind == "befriend") {
                    e.vx = clampX(w.x + dir * 1.5f)
                    e.vy = w.y + 0.1f
                    e.banner = "${v.name} holds out some berries"
                } else {
                    e.banner = "A wild ${w.c.name} (Lv ${w.c.level}) appears!"
                }
                e.bannerT = 1.5f
                seeFoe(e)
            }
            "spar" -> {
                val v2 = e.v2!!
                val mx = (v.x + v2.x) / 2f
                val my = (v.y + v2.y) / 2f
                val dir = if (v2.x >= v.x) 1 else -1
                e.ax = clampX(mx - dir * 0.95f)
                e.bx = clampX(mx + dir * 0.95f)
                e.ay = my + 0.1f
                e.by = my + 0.1f
                e.vx = clampX(mx - dir * 2.3f)
                e.vy = my + 0.15f
                e.wx = clampX(mx + dir * 2.3f)
                e.wy = my + 0.15f
                e.banner = "${v.name} and ${v2.name} start a friendly match"
                e.bannerT = 1.5f
            }
            "train" -> {
                val dir = if (v.x >= yardX) 1 else -1
                e.bx = yardX
                e.by = yardY
                e.ax = clampX(yardX + dir * 1.8f)
                e.ay = yardY + 0.1f
                e.vx = clampX(e.ax + dir * 1.1f)
                e.vy = yardY + 0.2f
                e.banner = "${e.mine!!.name} trains at the yard"
                e.bannerT = 1.5f
            }
        }
        // the fighters
        when (e.kind) {
            "wild" -> {
                e.a = e.mine
                e.b = e.foe
            }
            "spar" -> {
                e.a = dupe(e.mine!!)
                e.b = dupe(e.mine2!!)
            }
            "train" -> {
                e.a = dupe(e.mine!!)
                e.b = Critter(-1, Dex.DUMMY, e.mine!!.level).also { it.heal() }
            }
        }
        e.dispA = (e.a?.hp ?: 0).toFloat()
        e.dispB = (e.b?.hp ?: 0).toFloat()
        e.mine?.let { place(it, e.ax, e.ay) }
        e.mine2?.let { place(it, e.bx, e.by) }
    }

    private fun seeFoe(e: Enc) {
        e.foe?.let { seen.add(it.sp) }
    }

    private fun place(c: Critter, x: Float, y: Float) {
        val b = bodyFor(c)
        b.x = x
        b.y = y
        b.tx = x
        b.ty = y
        b.moving = false
    }

    private fun dupe(c: Critter): Critter {
        val d = Critter(-1, c.sp, c.level)
        d.nick = c.nick
        d.heal()
        return d
    }

    // ---- an encounter, moment by moment ----

    private fun abort(e: Enc, release: Boolean) {
        encounters.remove(e)
        e.wild?.let {
            it.claimed = -1
            it.c.heal()
        }
        if (release && sim.villagers.contains(e.v)) sim.releaseVillager(e.v)
        e.v2?.let { if (release && sim.villagers.contains(it)) sim.releaseVillager(it) }
    }

    private fun slide(v: Villager, tx: Float, ty: Float, dt: Float) {
        if (tx < 0f) return
        val dx = tx - v.x
        val dy = ty - v.y
        val d = hypot(dx, dy)
        if (d < 0.04f) return
        val s = min(d, 2.0f * dt)
        v.x += dx / d * s
        v.y += dy / d * s
    }

    private fun face(v: Villager, towardX: Float) {
        v.facing = if (towardX >= v.x) 1 else -1
    }

    private fun encTick(e: Enc, dt: Float) {
        val v = e.v
        if (!sim.villagers.contains(v) || !v.action.startsWith("critter_") ||
            (e.v2 != null && !sim.villagers.contains(e.v2!!))
        ) {
            abort(e, release = false)
            return
        }
        e.clock += dt
        if (e.bannerT > 0f) e.bannerT -= dt
        if (e.subT > 0f) e.subT -= dt
        if (e.hurtT > 0f) e.hurtT -= dt
        e.pops.removeAll { sim.time - it.born > it.life }
        if (e.state == "walk") {
            if (e.clock > 30f) abort(e, release = true)
            return
        }
        slide(v, e.vx, e.vy, dt)
        e.v2?.let { slide(it, e.wx, e.wy, dt) }
        face(v, e.bx)
        e.v2?.let { face(it, e.ax) }
        // displayed HP follows the real HP once a hit has landed
        val a = e.a
        val b = e.b
        if (a != null && b != null) {
            if (e.state != "turn" || e.impacted) {
                e.dispA += (a.hp - e.dispA) * min(1f, dt * 7f)
                e.dispB += (b.hp - e.dispB) * min(1f, dt * 7f)
            }
        }
        when (e.state) {
            "intro" -> if (e.clock >= 1.7f) {
                if (e.kind == "befriend") {
                    e.state = "offer"
                    e.clock = 0f
                    e.offerTry = 0
                    e.offerT = 0f
                    if (!e.spent) {
                        e.spent = true
                        sim.food -= 2
                    }
                    startOffer(e)
                } else {
                    e.battle = Battle(e.a!!, e.b!!, rnd)
                    e.state = "turn"
                    nextTurn(e)
                }
            }
            "turn" -> {
                e.turnT += dt
                if (!e.impacted && e.turnT >= IMPACT) applyImpact(e)
                if (e.turnT >= TURN) afterTurn(e)
            }
            "snare" -> snareTick(e, dt)
            "offer" -> offerTick(e, dt)
            "result" -> if (e.clock >= e.resultLen) finish(e)
        }
    }

    private fun nextTurn(e: Enc) {
        val bt = e.battle!!
        val t = bt.next()
        if (t == null) {
            toResult(e)
            return
        }
        e.turn = t
        e.turnT = 0f
        e.impacted = false
        val att = bt.side(t.side)
        e.banner = "${att.name} used ${t.move.name}!"
        e.bannerT = 1.5f
    }

    private fun applyImpact(e: Enc) {
        val t = e.turn ?: return
        e.impacted = true
        val tx = if (t.side == 0) e.bx else e.ax
        val ty = if (t.side == 0) e.by else e.ay
        if (!t.hit) {
            e.pops.add(Pop(tx, ty - 1.1f, "Missed!", 0xFFE8E8F0, sim.time))
            return
        }
        e.hurtSide = 1 - t.side
        e.hurtT = 0.4f
        val col = when {
            t.eff > 1f -> 0xFFFF6B5A
            t.eff < 1f -> 0xFFB0B8C8
            else -> 0xFFFFFFFF
        }
        e.pops.add(Pop(tx, ty - 1.1f, (if (t.crit) "${t.damage}!" else "${t.damage}"), col, sim.time))
        if (t.eff > 1f) {
            e.sub = "It's super effective!"
            e.subT = 1.3f
        } else if (t.eff < 1f) {
            e.sub = "Not very effective..."
            e.subT = 1.3f
        } else if (t.crit) {
            e.sub = "A sharp hit!"
            e.subT = 1.1f
        }
    }

    private fun afterTurn(e: Enc) {
        val bt = e.battle!!
        val t = e.turn
        if (bt.over) {
            toResult(e)
            return
        }
        val b = e.b!!
        if (e.kind == "wild" && t != null && t.side == 0 && b.hpFrac <= 0.45f && e.snareTry < 3 && wantsMore(e.v, e.foe!!)) {
            startSnare(e)
            return
        }
        nextTurn(e)
    }

    private fun startSnare(e: Enc) {
        e.state = "snare"
        e.snareTry++
        e.snareT = 0f
        val foe = e.foe!!
        val bonus = if (e.v.job == "Hunter" || e.v.job == "Scout") 0.08f else 0f
        e.snareWon = rnd.nextFloat() < Dex.catchChance(foe.species, foe.level, foe.hpFrac, bonus)
        e.banner = "${e.v.name} throws a snare!"
        e.bannerT = 1.6f
    }

    private fun snareTick(e: Enc, dt: Float) {
        e.snareT += dt
        if (e.snareT < SNARE_LEN) return
        if (e.snareWon) {
            keep(e, e.foe!!, "snare")
        } else {
            e.banner = "${e.foe!!.name} broke free!"
            e.bannerT = 1.5f
            e.state = "turn"
            nextTurn(e)
        }
    }

    private fun startOffer(e: Enc) {
        e.offerTry++
        e.offerT = 0f
        val foe = e.foe!!
        val bonus = if (e.v.job == "Forager" || e.v.job == "Hunter") 0.08f else 0f
        e.offerWon = rnd.nextFloat() < Dex.befriendChance(foe.species, foe.level) + bonus
        e.banner = if (e.offerTry == 1) "${foe.name} sniffs the berries..." else "${e.v.name} tries again..."
        e.bannerT = 1.6f
    }

    private fun offerTick(e: Enc, dt: Float) {
        e.offerT += dt
        if (e.offerT < OFFER_LEN) return
        if (e.offerWon) {
            keep(e, e.foe!!, "berries")
        } else if (e.offerTry < 3) {
            startOffer(e)
        } else {
            e.state = "result"
            e.clock = 0f
            e.result = "refused"
            e.resultLen = 2.4f
            e.banner = "${e.foe!!.name} eats the berries and wanders off."
            e.bannerT = 2.4f
            e.wild?.let {
                it.claimed = -1
                it.leaving = true
            }
            e.v.say = "Maybe next time."
            e.v.sayLeft = 3f
        }
    }

    /** A wild critter becomes one of the village's. */
    private fun keep(e: Enc, foe: Critter, how: String) {
        val v = e.v
        e.wild?.let {
            wilds.remove(it)
            it.claimed = -1
        }
        if (critters.count { it.owner == v.id } >= MAX_TEAM) {
            weakestSwappable(v)?.let { weak ->
                weak.owner = -1
                weak.companion = false
                sim.note("${v.name} swaps ${weak.name} for the new ${foe.species.name}; ${weak.name} stays on in the village.")
            }
        }
        foe.owner = v.id
        foe.hp = max(foe.hp, (foe.maxHp * 0.35f).toInt())
        val first = companionOf(v) == null
        val old = companionOf(v)
        if (first) {
            foe.companion = true
        } else if (old != null && foe.level >= old.level + 3) {
            old.companion = false
            foe.companion = true
        }
        critters.add(foe)
        val b = bodyFor(foe)
        b.x = e.bx
        b.y = e.by
        b.tx = b.x
        b.ty = b.y
        val newSpecies = caught.add(foe.sp)
        seen.add(foe.sp)
        val role = if (foe.companion) "as a companion" else "to its pack"
        sim.note(
            if (how == "berries") "${v.name} wins over a wild ${foe.species.name} with berries and takes it $role."
            else "${v.name} catches a wild ${foe.species.name} (Lv ${foe.level}) $role."
        )
        if (newSpecies) sim.mark("${v.name} added ${foe.species.name} to the village (${caught.size}/${Dex.COUNT} critters).")
        v.happiness = min(100f, v.happiness + 12f)
        v.remember("caught a ${foe.species.name}")
        v.say = if (first) "Welcome, ${foe.name}!" else "Got ${foe.name}!"
        v.sayLeft = 4f
        e.mine?.let {
            it.gainXp(8 + foe.level * 2)
            checkEvolve(it)
        }
        e.state = "result"
        e.clock = 0f
        e.result = "caught"
        e.resultLen = 3.4f
        e.banner = "${foe.species.name} joins the village!"
        e.bannerT = 3.2f
    }

    private fun toResult(e: Enc) {
        val bt = e.battle
        val winner = bt?.winner ?: 0
        e.state = "result"
        e.clock = 0f
        e.resultLen = 3f
        val v = e.v
        when (e.kind) {
            "wild" -> {
                val mine = e.mine!!
                val foe = e.foe!!
                if (winner == 0) {
                    val gain = 14 + foe.level * 5 + foe.species.stage * 6
                    val lv = mine.gainXp(gain)
                    mine.wins++
                    mine.bond++
                    e.result = "won"
                    e.banner = "${mine.name} wins! +$gain XP"
                    if (lv > 0) {
                        e.sub = "${mine.name} grew to level ${mine.level}!"
                        e.subT = 2.6f
                    }
                    v.happiness = min(100f, v.happiness + 8f)
                    v.remember("won a battle with ${mine.name}")
                    v.say = "Well fought, ${mine.name}!"
                    v.sayLeft = 4f
                    sim.note("${v.name}'s ${mine.name} beats a wild ${foe.species.name}.")
                    e.wild?.let {
                        it.claimed = -1
                        it.leaving = true
                    }
                    checkEvolve(mine)
                } else {
                    mine.hp = 0
                    mine.faintedUntil = sim.time + 50.0
                    e.result = "lost"
                    e.banner = "${mine.name} is too tired to go on."
                    v.say = "Rest up, ${mine.name}."
                    v.sayLeft = 4f
                    sim.note("A wild ${foe.species.name} drives off ${v.name}'s ${mine.name}.")
                    e.wild?.let {
                        it.claimed = -1
                        it.c.heal()
                    }
                }
            }
            "spar" -> {
                val c1 = e.mine!!
                val c2 = e.mine2!!
                val w1 = winner == 0
                val x1 = if (w1) 12 + c1.level * 3 else 8 + c1.level * 2
                val x2 = if (!w1) 12 + c2.level * 3 else 8 + c2.level * 2
                val l1 = c1.gainXp(x1)
                val l2 = c2.gainXp(x2)
                if (w1) c1.wins++ else c2.wins++
                val win = if (w1) c1 else c2
                e.result = "spar"
                e.banner = "${win.name} takes the match!"
                if (l1 + l2 > 0) {
                    e.sub = "${if (l1 > 0) c1.name else c2.name} grew stronger!"
                    e.subT = 2.4f
                }
                val v2 = e.v2!!
                v.happiness = min(100f, v.happiness + 6f)
                v2.happiness = min(100f, v2.happiness + 6f)
                v.affinity[v2.id] = (v.affinity[v2.id] ?: 0) + 1
                v2.affinity[v.id] = (v2.affinity[v.id] ?: 0) + 1
                v.remember("had a friendly match with ${v2.name}")
                v2.remember("had a friendly match with ${v.name}")
                v.say = "Good match, ${v2.name}!"
                v.sayLeft = 3f
                sim.note("${v.name} and ${v2.name} hold a friendly match: ${win.name} wins.")
                checkEvolve(c1)
                checkEvolve(c2)
            }
            "train" -> {
                val mine = e.mine!!
                val gain = 6 + mine.level * 3
                val lv = mine.gainXp(gain)
                e.result = "trained"
                e.banner = "${mine.name} finishes training. +$gain XP"
                if (lv > 0) {
                    e.sub = "${mine.name} grew to level ${mine.level}!"
                    e.subT = 2.6f
                }
                v.say = "Nice work, ${mine.name}."
                v.sayLeft = 3f
                mine.bond++
                checkEvolve(mine)
            }
        }
        e.bannerT = 2.8f
    }

    private fun finish(e: Enc) {
        encounters.remove(e)
        e.wild?.let { if (it.claimed == e.v.id) it.claimed = -1 }
        if (sim.villagers.contains(e.v)) sim.releaseVillager(e.v)
        e.v2?.let { if (sim.villagers.contains(it)) sim.releaseVillager(it) }
    }

    // ---- evolution ----

    private fun checkEvolve(c: Critter) {
        if (!c.readyToEvolve() || evolving(c) != null) return
        val b = bodyFor(c)
        evolutions.add(EvolveScene(c.uid, c.sp, c.species.evolveTo, b.x, b.y, sim.time))
    }

    private fun evoTick() {
        val iter = evolutions.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            val c = critters.firstOrNull { it.uid == s.uid }
            if (c == null) {
                iter.remove()
                continue
            }
            val b = bodyFor(c)
            s.x = b.x
            s.y = b.y
            val p = (sim.time - s.born) / EvolveScene.DURATION
            if (!s.flipped && p >= 0.62) {
                s.flipped = true
                val from = c.species
                c.evolve()
                val to = c.species
                caught.add(to.id)
                seen.add(to.id)
                val who = if (c.owner >= 0) "${ownerName(c)}'s ${from.name}" else from.name
                sim.note("$who evolved into ${to.name}!")
                sim.mark("$who evolved into ${to.name}.")
                sim.villagers.firstOrNull { it.id == c.owner }?.let {
                    it.happiness = min(100f, it.happiness + 15f)
                    it.say = "${to.name}! Look at you!"
                    it.sayLeft = 4f
                    it.remember("watched ${from.name} evolve into ${to.name}")
                }
            }
            if (p >= 1.0) {
                iter.remove()
                checkEvolve(c)
            }
        }
    }

    // ---- saving ----

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("next", nextUid)
        o.put("yx", yardX.toDouble()).put("yy", yardY.toDouble())
        val arr = JSONArray()
        for (c in critters) {
            arr.put(
                JSONObject().put("id", c.uid).put("sp", c.sp).put("lv", c.level).put("xp", c.xp).put("hp", c.hp)
                    .put("own", c.owner).put("comp", c.companion).put("nick", c.nick).put("wins", c.wins).put("bond", c.bond)
            )
        }
        o.put("critters", arr)
        o.put("seen", JSONArray(seen.toList()))
        o.put("caught", JSONArray(caught.toList()))
        return o
    }

    fun fromJson(o: JSONObject?) {
        reset()
        if (o == null) return
        try {
            nextUid = o.optInt("next", 1)
            yardX = o.optDouble("yx", -1.0).toFloat()
            yardY = o.optDouble("yy", -1.0).toFloat()
            val arr = o.optJSONArray("critters")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val j = arr.getJSONObject(i)
                    val sp = j.getInt("sp")
                    if (sp !in 1..Dex.COUNT) continue
                    val c = Critter(j.getInt("id"), sp, j.optInt("lv", 5).coerceIn(1, Dex.MAX_LEVEL))
                    c.xp = j.optInt("xp", 0)
                    c.hp = j.optInt("hp", c.maxHp).coerceIn(1, c.maxHp)
                    c.owner = j.optInt("own", -1)
                    c.companion = j.optBoolean("comp", false)
                    c.nick = j.optString("nick", "")
                    c.wins = j.optInt("wins", 0)
                    c.bond = j.optInt("bond", 0)
                    critters.add(c)
                    nextUid = max(nextUid, c.uid + 1)
                }
            }
            val s = o.optJSONArray("seen")
            if (s != null) for (i in 0 until s.length()) seen.add(s.getInt(i))
            val k = o.optJSONArray("caught")
            if (k != null) for (i in 0 until k.length()) caught.add(k.getInt(i))
            for (c in critters) {
                caught.add(c.sp)
                seen.add(c.sp)
            }
        } catch (e: Exception) {
            // A damaged critter save should never stop the village from loading.
        }
    }

    companion object {
        const val MAX_TEAM = 6
        const val TURN = 2.2f
        const val IMPACT = 1.0f
        const val SNARE_LEN = 4.2f
        const val OFFER_LEN = 3.4f
    }
}
