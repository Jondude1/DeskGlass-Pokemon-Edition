package com.jondude1.deskglass.sim

import kotlin.math.sin

/** The sky above the village: how much of it is cloud and when the sun breaks through in shafts of light. Everything is a
 *  pure function of time and the real weather, so nothing is saved. Drawing lives in ui/CivSky.kt. */
object Sky {
    private val seasonBase = floatArrayOf(0.42f, 0.28f, 0.52f, 0.58f)

    /** Cloud cover 0..1 on a fair day: slow weather fronts drift over, heavier in autumn and winter. */
    fun baseCover(t: Double, season: Int): Float {
        val b = seasonBase[season.coerceIn(0, 3)]
        return (b + 0.28 * sin(t * 0.0021 + 1.1) + 0.12 * sin(t * 0.0067)).toFloat().coerceIn(0.05f, 0.85f)
    }

    /** Cloud cover now: rain, storms, snow and fog close the sky, a drought leaves it bare. */
    fun cover(sim: CivSim): Float = when {
        sim.storm() -> 1f
        sim.raining() || sim.snowing() -> 0.92f
        sim.foggy() -> 0.8f
        sim.droughtLevel() > 0.15f -> 0.04f
        else -> baseCover(sim.time, sim.seasonIdx())
    }

    /** How many clouds are drawn for a cover value. */
    fun cloudCount(cover: Float): Int = (2 + cover * 9f).toInt()

    /** Strength 0..1 of sunbeams breaking through broken cloud: partial cover, sun up, strongest in the low morning and evening light. */
    fun beams(cover: Float, dayFrac: Double): Float {
        if (cover < 0.22f || cover > 0.78f) return 0f
        val u = ((dayFrac - 0.05) / 0.62).coerceIn(0.0, 1.0)
        if (dayFrac < 0.05 || dayFrac > 0.67) return 0f
        val low = Math.abs(2.0 * u - 1.0).toFloat()
        val mid = 1f - Math.abs(cover - 0.5f) / 0.28f
        return ((0.35f + 0.65f * low) * mid).coerceIn(0f, 1f)
    }

    /** Harness census of a fair-weather year (sampled every 20s): [mean cover, share of day with beams, mean clouds, overcast share]. */
    fun census(): DoubleArray {
        var sc = 0.0; var sb = 0; var sn = 0.0; var so = 0; var n = 0; var daySamples = 0
        var t = 0.0
        while (t < DAY_LEN * SEASON_DAYS * 4) {
            val c = baseCover(t, ((t / DAY_LEN).toInt() / SEASON_DAYS) % 4)
            val f = (t % DAY_LEN) / DAY_LEN
            sc += c; sn += cloudCount(c); n++
            if (c > 0.7f) so++
            if (f in 0.05..0.67) { daySamples++; if (beams(c, f) > 0.35f) sb++ }
            t += 20.0
        }
        return doubleArrayOf(sc / n, sb.toDouble() / daySamples, sn / n, so.toDouble() / n)
    }
}
