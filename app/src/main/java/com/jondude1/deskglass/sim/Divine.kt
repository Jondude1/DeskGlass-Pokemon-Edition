package com.jondude1.deskglass.sim

import kotlin.math.min

/** One thing the player can do from the sky. Costs favour, which the village itself earns by being happy. */
class DivinePower(val id: String, val name: String, val cost: Int, val blurb: String)

/**
 * Divine Favour: the first thing besides a whispered order the player can DO. Happy villagers (and a blessed totem)
 * slowly fill a favour meter; spending it calls down one of five powers (sunburst, gentle rain, healing light,
 * bountiful harvest, smite). The effects live in CivSim.godPower because they touch weather, farms and foes.
 */
class Divine {
    companion object {
        val POWERS = listOf(
            DivinePower("sun", "Sunburst", 15, "Parts the clouds: ends rain, storm, fog and snow and lifts every heart."),
            DivinePower("rain", "Gentle Rain", 20, "Calls a soft rain that waters the farms and breaks a drought."),
            DivinePower("heal", "Healing Light", 25, "Mends every wounded villager and lifts the plague."),
            DivinePower("grow", "Bountiful Harvest", 25, "Speeds every farm for a while and brings in extra food."),
            DivinePower("smite", "Smite", 30, "Lightning strikes the fiercest foes near the village."),
        )
        fun find(id: String): DivinePower? = POWERS.firstOrNull { it.id == id }
    }

    var favour = 12f
    var earned = 0f
    val uses = IntArray(5)
    var healed = 0
    var smitten = 0
    var smiteKills = 0
    var refused = 0
    var lastUseAt = -999.0
    var lastId = ""
    var lastMsg = ""

    fun reset() {
        favour = 12f; earned = 0f; uses.fill(0); healed = 0; smitten = 0; smiteKills = 0; refused = 0
        lastUseAt = -999.0; lastId = ""; lastMsg = ""
    }

    fun totalUses(): Int = uses.sum()
    fun kinds(): Int = uses.count { it > 0 }
    fun canAfford(p: DivinePower): Boolean = favour >= p.cost

    /** Happy villagers pray more: favour per second grows with the crowd and its mood. */
    fun tick(s: CivSim, dt: Float) {
        val n = s.villagers.size
        if (n < 2) return
        var happy = 0f
        for (v in s.villagers) happy += v.happiness
        val avg = happy / n / 100f
        var rate = n * (0.3f + 0.7f * avg) * 0.0035f
        if (s.time < s.totemBlessUntil) rate *= 1.5f
        val gain = rate * dt
        val before = favour
        favour = min(100f, favour + gain)
        earned += favour - before
    }

    fun encode(): String =
        "${"%.2f".format(favour)}|${"%.1f".format(earned)}|${uses.joinToString(",")}|$healed|$smitten|$smiteKills|$refused"

    fun decode(str: String) {
        if (str.isEmpty()) return
        try {
            val p = str.split("|")
            favour = p[0].toFloat().coerceIn(0f, 100f)
            earned = p.getOrElse(1) { "0" }.toFloat()
            val u = p.getOrElse(2) { "" }.split(",")
            for (i in uses.indices) uses[i] = u.getOrNull(i)?.toIntOrNull() ?: 0
            healed = p.getOrElse(3) { "0" }.toInt()
            smitten = p.getOrElse(4) { "0" }.toInt()
            smiteKills = p.getOrElse(5) { "0" }.toInt()
            refused = p.getOrElse(6) { "0" }.toInt()
        } catch (e: Exception) {
            // an unreadable record just starts fresh
        }
    }
}
