package com.jondude1.deskglass.sim

/** Spring floods: after heavy rain or a storm the lake swells over its banks, creeps across the low ground, then drains away.
 *  The water level is a pure function of the clock (rise, hold, recede), so the drawing and the sim always agree. Only the
 *  lifetime counters are saved; a flood in progress simply ends when the game is reloaded. Drawing: ui/CivFlood.kt. */
class FloodState {
    companion object {
        const val REACH = 5          // tiles inland from the lake that a full flood can cover
        const val RISE = 40.0
        const val HOLD = 35.0
        const val FALL = 50.0
    }

    var startAt = -999.0
    var lastAt = -999.0
    var on = false
    var warned = false
    val bagged = HashSet<Int>()      // farm tile keys kept dry with sandbags this flood
    val soaked = HashSet<Int>()      // farm tile keys the water reached this flood
    var lostThis = 0
    var fishLeft = 0                 // stranded fish still flopping on the mud
    var fishAt = -999.0
    var endAt = -999.0

    // lifetime counters (saved)
    var floods = 0
    var farmsHit = 0
    var baggedFarms = 0
    var foodLost = 0
    var silt = 0
    var fishScooped = 0

    private var depthCache: IntArray? = null
    private var depthKey = -1

    fun total() = RISE + HOLD + FALL
    fun active(t: Double) = t >= startAt && t < startAt + total()

    /** 0..1 water level now (0 before and after a flood). */
    fun level(t: Double): Float {
        val e = t - startAt
        if (e <= 0.0 || e >= total()) return 0f
        return when {
            e < RISE -> (e / RISE).toFloat()
            e < RISE + HOLD -> 1f
            else -> (1.0 - (e - RISE - HOLD) / FALL).toFloat()
        }
    }

    /** Tiles from the lake (1..REACH) for every land tile that a flood can reach; 0 for water, rock and far ground. */
    fun depth(tiles: IntArray): IntArray {
        val c = depthCache
        if (c != null && depthKey == tiles.size) return c
        val d = IntArray(tiles.size)
        val q = ArrayDeque<Int>()
        for (i in tiles.indices) if (tiles[i] == T.WATER) { d[i] = -1; q.addLast(i) }
        val dirs = intArrayOf(1, -1, GW, -GW)
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            val di = if (d[i] < 0) 0 else d[i]
            if (di >= REACH) continue
            for (k in 0 until 4) {
                val j = i + dirs[k]
                if (j < 0 || j >= tiles.size) continue
                if ((k == 0 && i % GW == GW - 1) || (k == 1 && i % GW == 0)) continue
                if (d[j] != 0 || tiles[j] == T.WATER || tiles[j] == T.ROCK) continue
                d[j] = di + 1
                q.addLast(j)
            }
        }
        for (i in d.indices) if (d[i] < 0) d[i] = 0
        depthCache = d; depthKey = tiles.size
        return d
    }

    /** True if water of the given level has reached a tile of this depth. */
    fun covers(depth: Int, level: Float): Boolean = depth in 1..REACH && level * REACH >= depth - 0.3f

    fun lakeTiles(tiles: IntArray): Int { var n = 0; for (t in tiles) if (t == T.WATER) n++; return n }

    fun resetAll() {
        startAt = -999.0; lastAt = -999.0; on = false; warned = false; bagged.clear(); soaked.clear(); lostThis = 0; fishLeft = 0
        fishAt = -999.0; endAt = -999.0; floods = 0; farmsHit = 0; baggedFarms = 0; foodLost = 0; silt = 0; fishScooped = 0
    }
}
