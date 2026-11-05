package com.jondude1.deskglass.sim

/** Night sky wonders: on some clear nights the sky itself puts on a show (a meteor shower, an aurora, a blood-moon eclipse).
 *  Which nights is a pure function of the day number, so the calendar is stable and nothing but the counters is saved.
 *  Drawing lives in ui/CivSkyShow.kt. */
object NightSky {
    const val NONE = 0
    const val METEOR = 1
    const val AURORA = 2
    const val ECLIPSE = 3

    /** The show runs from early evening until late night, as a fraction of the day. */
    const val START = 0.60
    const val END = 0.92

    private fun h(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

    /** What the sky has planned for the night that starts on day index [d] (0 = first day). */
    fun kindOn(d: Int): Int {
        val season = (d / SEASON_DAYS) % 4
        val sd = d % SEASON_DAYS
        if (season == 2 && sd == 1) return ECLIPSE
        val sn = d / SEASON_DAYS
        if (sd == h(sn, 7) % SEASON_DAYS && h(sn, 11) % 100 < 70) return METEOR
        val chance = when (season) { 3 -> 55; 2 -> 20; 0 -> 8; else -> 0 }
        return if (h(d, 3) % 100 < chance) AURORA else NONE
    }

    /** 0..1 strength of the show at this moment of the day (rises, peaks around the middle of the window, fades). */
    fun envelope(frac: Double): Float {
        if (frac < START || frac > END) return 0f
        val u = ((frac - START) / (END - START)).toFloat()
        return kotlin.math.sin(u * Math.PI.toFloat()).coerceIn(0f, 1f)
    }

    fun title(kind: Int): String = when (kind) {
        METEOR -> "a meteor shower"
        AURORA -> "an aurora"
        ECLIPSE -> "a blood-moon eclipse"
        else -> ""
    }
}
