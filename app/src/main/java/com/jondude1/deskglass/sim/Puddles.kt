package com.jondude1.deskglass.sim

/** Wet ground: rain fills puddles on open grass, they shrink and dry in sun and drought, freeze over in winter.
 *  Wetness is a smoothed value driven by the real weather; the puddle spots are a fixed hash of the map. Not saved:
 *  after a restart the ground simply starts dry. Drawing lives in ui/CivPuddles.kt. */
object Puddles {
    const val MAX = 36

    private var simRef: CivSim? = null
    private var lastT = -1.0
    var wet = 0f
        private set
    var peakCount = 0
        private set
    var wetSamples = 0
        private set
    var samples = 0
        private set

    /** Advance wetness to the sim clock. Safe to call every frame. */
    fun update(sim: CivSim) {
        if (simRef !== sim || sim.time < lastT) { simRef = sim; lastT = sim.time; wet = 0f; peakCount = 0; wetSamples = 0; samples = 0; cacheKey = -1L }
        val dt = (sim.time - lastT).coerceIn(0.0, 60.0).toFloat()
        lastT = sim.time
        if (dt <= 0f) return
        wet = when {
            sim.storm() -> wet + dt / 20f
            sim.raining() -> wet + dt / 35f
            sim.droughtLevel() > 0.15f -> wet - dt / 25f
            sim.snowing() -> wet - dt / 120f
            else -> wet - dt / 110f
        }.coerceIn(0f, 1f)
    }

    private var cacheKey = -1L
    private var cached = IntArray(0)

    /** Candidate puddle tiles: open grass away from buildings, spread by a hash of the position. */
    fun sites(sim: CivSim): IntArray {
        val key = (sim.buildings.size * 1000L + sim.tiles.size)
        if (key == cacheKey) return cached
        val found = ArrayList<Int>()
        for (i in sim.tiles.indices) {
            if (sim.tiles[i] != T.GRASS || sim.owner[i] >= 0) continue
            val x = i % GW
            val y = i / GW
            if (x < 2 || y < 2 || x >= GW - 2 || y >= GH - 2) continue
            if (sim.traffic[i] > 40f) continue
            if (hash(i) % 23 != 0) continue
            found.add(i)
        }
        found.sortBy { hash(it * 7 + 3) }
        cached = IntArray(minOf(MAX, found.size)) { found[it] }
        cacheKey = key
        return cached
    }

    fun hash(i: Int): Int = ((i * 73856093) xor (i * 19349663 + 12345)) and 0x7fffffff

    /** Fraction 0..1 of its full size that puddle number k (in site order) has now; 0 when it has not formed yet. */
    fun size(k: Int, count: Int): Float {
        val start = k.toFloat() / count.coerceAtLeast(1) * 0.7f
        return ((wet - start) / 0.3f).coerceIn(0f, 1f)
    }

    fun winter(sim: CivSim): Boolean = sim.seasonIdx() == 3

    /** Number of puddles standing now. */
    fun count(sim: CivSim): Int {
        val s = sites(sim)
        var n = 0
        for (k in s.indices) if (size(k, s.size) > 0.05f) n++
        return n
    }

    /** Harness sample (once per sampled tick). */
    fun sample(sim: CivSim) {
        update(sim)
        val n = count(sim)
        samples++
        if (wet > 0.15f) wetSamples++
        if (n > peakCount) peakCount = n
    }
}
