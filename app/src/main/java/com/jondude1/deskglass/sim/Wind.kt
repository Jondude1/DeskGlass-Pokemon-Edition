package com.jondude1.deskglass.sim

import kotlin.math.sin

/** The wind: a pure function of time, so nothing is saved. Gusts come and go between calm spells; what the wind carries
 *  (leaves, petals, seeds, snow, dust) depends on the real season and weather. Drawing lives in ui/CivWind.kt. */
object Wind {
    const val NONE = 0
    const val LEAVES = 1
    const val PETALS = 2
    const val SEEDS = 3
    const val SNOW = 4
    const val DUST = 5

    /** Gust strength 0..1 at time t (seconds). Calm spells of 20-40s alternate with gusts of 10-25s. */
    fun gust(t: Double): Float {
        val a = sin(t * 0.11) * 0.6 + sin(t * 0.047 + 1.3) * 0.4 + sin(t * 0.31 + 0.7) * 0.12
        return ((a - 0.1) * 1.7).coerceIn(0.0, 1.0).toFloat()
    }

    /** What floats on the wind right now. */
    fun cargo(sim: CivSim): Int {
        if (sim.raining() || sim.foggy()) return NONE
        if (sim.snowing() || sim.snowCover() > 0.3f) return SNOW
        if (sim.droughtLevel() > 0.15f) return DUST
        return when (sim.seasonIdx()) {
            0 -> PETALS
            1 -> SEEDS
            2 -> LEAVES
            else -> LEAVES
        }
    }

    fun cargoName(c: Int): String = when (c) { LEAVES -> "leaves"; PETALS -> "petals"; SEEDS -> "seeds"; SNOW -> "snow"; DUST -> "dust"; else -> "none" }

    /** Harness census over [0, until] seconds: [gust share of time, number of gusts, longest gust s, strongest gust]. */
    fun census(until: Double): DoubleArray {
        var on = 0; var n = 0; var gusts = 0; var run = 0.0; var longest = 0.0; var peak = 0f; var was = false
        var t = 0.0
        while (t < until) {
            val g = gust(t)
            val up = g > 0.3f
            if (up) { on++; run += 1.0; if (!was) gusts++ } else { if (run > longest) longest = run; run = 0.0 }
            if (g > peak) peak = g
            was = up
            n++
            t += 1.0
        }
        if (run > longest) longest = run
        return doubleArrayOf(if (n == 0) 0.0 else on.toDouble() / n, gusts.toDouble(), longest, peak.toDouble())
    }
}
