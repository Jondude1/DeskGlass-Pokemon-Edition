package com.jondude1.deskglass.sim

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pure placement rules for the life on the lakes (ducks, lilies, reeds, foam, jumping fish). Drawing lives in ui/CivWater.kt.
 *  Everything is derived from the tile grid and a hash, so nothing is saved. */
object WaterLife {
    fun h(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

    fun isWater(sim: CivSim, x: Int, y: Int): Boolean =
        x in 0 until GW && y in 0 until GH && sim.tiles[y * GW + x] == T.WATER

    /** True when the tile and all eight neighbours are water, so a duck can paddle round it without leaving the lake. */
    fun deep(sim: CivSim, x: Int, y: Int): Boolean {
        for (dy in -1..1) for (dx in -1..1) if (!isWater(sim, x + dx, y + dy)) return false
        return true
    }

    private fun isLand(sim: CivSim, x: Int, y: Int): Boolean =
        x in 0 until GW && y in 0 until GH && sim.tiles[y * GW + x] != T.WATER

    /** Bits of the sides that touch land: 1 north, 2 east, 4 south, 8 west. */
    fun shoreMask(sim: CivSim, x: Int, y: Int): Int {
        var m = 0
        if (isLand(sim, x, y - 1)) m = m or 1
        if (isLand(sim, x + 1, y)) m = m or 2
        if (isLand(sim, x, y + 1)) m = m or 4
        if (isLand(sim, x - 1, y)) m = m or 8
        return m
    }

    /** 0 nothing, 1 a single duck, 2 a mother with two ducklings. */
    fun duckKind(sim: CivSim, x: Int, y: Int): Int {
        val k = h(x, y)
        if (k % duckRate(sim) != 0 || !roomy(sim, x, y)) return 0
        return if ((k / 17) % 2 == 0) 2 else 1
    }

    /** Water on all four sides: enough room for a duck to paddle a little. */
    fun roomy(sim: CivSim, x: Int, y: Int): Boolean =
        isWater(sim, x, y) && isWater(sim, x - 1, y) && isWater(sim, x + 1, y) && isWater(sim, x, y - 1) && isWater(sim, x, y + 1)

    private var rateFor: CivSim? = null
    private var rate = 11

    /** Small ponds get more ducks per tile than big lakes so every pond has a few. */
    private fun duckRate(sim: CivSim): Int {
        if (rateFor !== sim) {
            var n = 0
            for (i in 0 until GW * GH) if (sim.tiles[i] == T.WATER) n++
            rate = if (n < 400) 4 else 11
            rateFor = sim
        }
        return rate
    }

    /** Where the duck of this tile paddles at time t, in tiles from the tile corner (stays inside the 3x3 water block). */
    fun duckX(x: Int, y: Int, t: Float, lag: Float = 0f): Float {
        val s = (h(x, y) shr 6) % 100 / 100f * 2f * PI.toFloat()
        return 0.5f + 0.6f * sin((t - lag) * 0.21f + s)
    }

    fun duckY(x: Int, y: Int, t: Float, lag: Float = 0f): Float {
        val s = (h(x, y) shr 12) % 100 / 100f * 2f * PI.toFloat()
        return 0.5f + 0.5f * sin((t - lag) * 0.16f + s * 1.3f)
    }

    /** 1 when the duck swims right, -1 when it swims left. */
    fun duckFacing(x: Int, y: Int, t: Float): Int {
        val s = (h(x, y) shr 6) % 100 / 100f * 2f * PI.toFloat()
        return if (cos(t * 0.21f + s) >= 0f) 1 else -1
    }

    /** True while the duck up-ends to feed. */
    fun duckDabbing(x: Int, y: Int, t: Float): Boolean {
        val s = (h(x, y) shr 3) % 100 / 10f
        return sin(t * 0.45f + s) > 0.93f
    }

    fun lily(sim: CivSim, x: Int, y: Int): Boolean = h(x, y) % 13 == 3 && shoreMask(sim, x, y) != 0 || (h(x, y) % 29 == 4 && deep(sim, x, y))

    /** Lily pads show from late spring until early autumn; the flower only in summer. */
    fun lilyPads(sim: CivSim): Boolean {
        val s = sim.seasonIdx(); val f = sim.seasonFrac()
        return when (s) { 0 -> f > 0.45f; 1 -> true; 2 -> f < 0.35f; else -> false }
    }

    fun lilyFlower(sim: CivSim): Boolean = sim.seasonIdx() == 1 || (sim.seasonIdx() == 0 && sim.seasonFrac() > 0.8f)

    /** A fish leaps from this tile on a long cycle: returns progress 0..1 of the leap, or -1 when it is not leaping. */
    fun fishLeap(sim: CivSim, x: Int, y: Int, t: Float): Float {
        val k = h(x, y)
        if (k % 31 != 5 || !roomy(sim, x, y)) return -1f
        val period = 13f + (k shr 5) % 9
        val ph = (t % period) / period
        return if (ph < 0.06f) ph / 0.06f else -1f
    }

    /** Counts for the headless harness: what the lakes of this village hold. */
    fun census(sim: CivSim): IntArray {
        var water = 0; var ducks = 0; var ducklings = 0; var lilies = 0; var reedTiles = 0; var foamEdges = 0; var leapers = 0
        for (y in 0 until GH) for (x in 0 until GW) {
            if (!isWater(sim, x, y)) continue
            water++
            when (duckKind(sim, x, y)) { 1 -> ducks++; 2 -> { ducks++; ducklings += 2 } }
            if (lily(sim, x, y)) lilies++
            val m = shoreMask(sim, x, y)
            if (m != 0) {
                foamEdges += Integer.bitCount(m)
                if (h(x, y) % 2 == 0) reedTiles++
            }
            if (h(x, y) % 31 == 5 && roomy(sim, x, y)) leapers++
        }
        return intArrayOf(water, ducks, ducklings, lilies, reedTiles, foamEdges, leapers)
    }
}
