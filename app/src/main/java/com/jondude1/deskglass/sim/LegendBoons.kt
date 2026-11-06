package com.jondude1.deskglass.sim

/**
 * Legend boons: a bound legendary critter (species 57..62) no longer just sits in the roster. Each one answers
 * the village now and then with its own gift: Aurelion mends the wounded, Tidewyrm blesses the farms, Elderstag
 * brings a grove's bounty, Stormroc hurls lightning at foes, Ironwarden strikes the nearest foe and heartens the
 * fighters, Voidwyrm tears at the fiercest foe. The effects live in CivSim.legendBoonTick.
 */
class LegendBoons {
    companion object {
        val NAMES = listOf("Aurelion", "Tidewyrm", "Elderstag", "Stormroc", "Ironwarden", "Voidwyrm")
        /** Spell colour (ARGB) for the aura of each legend. */
        val COLORS = longArrayOf(0xFFFFD36A, 0xFF6FD6E8, 0xFF8FDB6A, 0xFFFFF27A, 0xFFB8C4D8, 0xFFB57CFF)
    }

    val fired = IntArray(6)
    val next = DoubleArray(6)
    var foesStruck = 0
    var woundsMended = 0

    fun total(): Int = fired.sum()
    fun kinds(): Int = fired.count { it > 0 }

    fun reset() {
        fired.fill(0); next.fill(0.0); foesStruck = 0; woundsMended = 0
    }

    fun encode(): String = "${fired.joinToString(",")}|$foesStruck|$woundsMended"

    fun decode(str: String) {
        if (str.isEmpty()) return
        try {
            val p = str.split("|")
            val u = p[0].split(",")
            for (i in fired.indices) fired[i] = u.getOrNull(i)?.toIntOrNull() ?: 0
            foesStruck = p.getOrElse(1) { "0" }.toInt()
            woundsMended = p.getOrElse(2) { "0" }.toInt()
        } catch (e: Exception) {
            // an unreadable record just starts fresh
        }
    }
}
