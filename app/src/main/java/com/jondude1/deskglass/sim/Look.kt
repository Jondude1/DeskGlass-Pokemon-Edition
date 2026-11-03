package com.jondude1.deskglass.sim

/** Individual looks: every villager gets a skin tone and hair colour from their id, and the old turn grey. Pure, no state. */
object Look {
    val SKIN = intArrayOf(0xFFF6D6B4.toInt(), 0xFFF2CBA3.toInt(), 0xFFDDAA7A.toInt(), 0xFFB87C50.toInt(), 0xFF8A5A3A.toInt())
    val HAIR = intArrayOf(
        0xFF3B2A1A.toInt(), 0xFF8A5A2B.toInt(), 0xFF222222.toInt(), 0xFFD9B04A.toInt(),
        0xFF7A2E1E.toInt(), 0xFF5A3A6A.toInt(), 0xFF2E4A3A.toInt(), 0xFFE6D2A0.toInt(),
    )
    const val GREY = 0xFFCDD0D8.toInt()

    private fun mix(id: Int): Int {
        var h = id * -1640531535 + 0x1234567
        h = h xor (h ushr 15); h *= 0x2c1b3c6d
        h = h xor (h ushr 12); h *= 0x297a2d39
        h = h xor (h ushr 15)
        return h and 0x7fffffff
    }

    fun skinOf(id: Int): Int = mix(id) % SKIN.size
    fun hairOf(id: Int): Int = (mix(id) / 7) % HAIR.size

    /** Shade colour for the shadowed half of a skin tone. */
    fun shade(c: Int): Int {
        val r = ((c shr 16) and 255) * 82 / 100
        val g = ((c shr 8) and 255) * 82 / 100
        val b = (c and 255) * 82 / 100
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun elder(sim: CivSim, v: Villager): Boolean = v.job != "Child" && sim.ageYears(v) * 100 >= v.lifespan * 55

    /** A number that is the same for two villagers only if they look the same. */
    fun key(sim: CivSim, v: Villager): Int {
        val e = elder(sim, v)
        return ((v.colorIdx * 5 + skinOf(v.id)) * 8 + (if (e) 0 else hairOf(v.id))) * 2 + (if (e) 1 else 0)
    }
}
