package com.jondude1.deskglass.sim

import kotlin.math.floor

/** Night lighting: which worn-path tiles hold a street lamp, which building types give light after dark and how far.
 *  A pure function of the sim state, so nothing is saved. Drawing lives in ui/CivLamps.kt. */
object Lamps {
    const val MAX = 60
    /** Path tiles must be walked this much before a lamp post is raised beside them. */
    const val MIN_TRAFFIC = 16f

    /** type -> [radius in tiles, strength 0..1]. Campfire, smelter, tower, hut and hall are lit by the older code in CivView. */
    private val glow: Map<String, FloatArray> = mapOf(
        "tavern" to floatArrayOf(3.4f, 0.8f), "market" to floatArrayOf(3.0f, 0.7f), "library" to floatArrayOf(2.4f, 0.65f),
        "museum" to floatArrayOf(2.8f, 0.7f), "guild" to floatArrayOf(2.6f, 0.7f), "infirmary" to floatArrayOf(2.2f, 0.65f),
        "kiln" to floatArrayOf(2.6f, 0.75f), "mill" to floatArrayOf(1.9f, 0.55f), "loom" to floatArrayOf(1.9f, 0.55f),
        "smokehouse" to floatArrayOf(1.9f, 0.55f), "dock" to floatArrayOf(2.2f, 0.6f), "bath" to floatArrayOf(2.4f, 0.6f),
        "fountain" to floatArrayOf(2.2f, 0.55f), "belfry" to floatArrayOf(2.2f, 0.6f), "stage" to floatArrayOf(3.0f, 0.75f),
        "shrine" to floatArrayOf(2.4f, 0.6f), "ranch" to floatArrayOf(1.9f, 0.5f), "pasture" to floatArrayOf(1.6f, 0.4f),
        "apiary" to floatArrayOf(1.4f, 0.4f), "quarry" to floatArrayOf(1.8f, 0.5f), "totem" to floatArrayOf(1.8f, 0.5f),
        "wonder" to floatArrayOf(4.0f, 0.85f), "ballista" to floatArrayOf(1.6f, 0.5f), "gate" to floatArrayOf(2.2f, 0.65f),
        "yard" to floatArrayOf(1.8f, 0.5f), "range" to floatArrayOf(1.8f, 0.5f), "observatory" to floatArrayOf(2.4f, 0.65f),
        "lighthouse" to floatArrayOf(3.0f, 0.8f), "armory" to floatArrayOf(2.2f, 0.7f),
    )

    fun lightOf(type: String): FloatArray? = glow[type]
    /** How many building kinds throw light after dark: the five older ones plus the table above. */
    fun lightKinds(): Int = 5 + glow.size

    private var cacheSim: CivSim? = null
    private var cacheKey = -1L
    private var cached = IntArray(0)

    /** Tile indices of the lamp posts, busiest paths first. Cached for a few seconds. */
    fun sites(sim: CivSim): IntArray {
        val key = floor(sim.time / 3.0).toLong()
        if (cacheSim === sim && key == cacheKey) return cached
        val tr = sim.traffic
        val found = ArrayList<Int>()
        for (i in tr.indices) {
            if (tr[i] < MIN_TRAFFIC || sim.tiles[i] != T.GRASS || sim.owner[i] >= 0) continue
            val x = i % GW
            val y = i / GW
            if ((x * 5 + y) % 6 != 0) continue
            found.add(i)
        }
        found.sortByDescending { tr[it] }
        cached = IntArray(minOf(MAX, found.size)) { found[it] }
        cacheSim = sim
        cacheKey = key
        return cached
    }

    /** 0..1: lamps come on one after another as dusk deepens, and go out one by one at dawn. */
    fun lit(dark: Float, tile: Int): Float {
        val stagger = ((tile * 37) % 100) / 100f * 0.14f
        return ((dark - 0.08f - stagger) / 0.05f).coerceIn(0f, 1f)
    }

    /** [lamp posts, buildings that glow at night, building kinds that glow]. */
    fun census(sim: CivSim): IntArray {
        val lit = sim.buildings.count { it.type in setOf("campfire", "smelter", "tower", "hall") || (it.type == "hut" && it.era >= 1) || glow.containsKey(it.type) }
        val kinds = sim.buildings.map { it.type }.filter { it in setOf("campfire", "smelter", "tower", "hall", "hut") || glow.containsKey(it) }.toSet().size
        val before = sim.buildings.count { it.type in setOf("campfire", "smelter", "tower", "hall") || (it.type == "hut" && it.era >= 1) }
        return intArrayOf(sites(sim).size, lit, kinds, before)
    }
}
