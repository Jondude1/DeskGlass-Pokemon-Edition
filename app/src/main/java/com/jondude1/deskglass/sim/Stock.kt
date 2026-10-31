package com.jondude1.deskglass.sim

/** How full a store looks on screen: 0 = empty, otherwise 1..max. Pure, so the headless harness can check it. */
object Stock {
    fun level(amount: Int, cap: Int, max: Int): Int {
        if (amount <= 0) return 0
        val c = if (cap < 1) 1 else cap
        return (1 + amount.toLong() * (max - 1) / c).toInt().coerceIn(1, max)
    }

    private val WOOD = intArrayOf(150, 400, 800, 1500, 2500)
    private val STONE = intArrayOf(100, 250, 500, 1000, 2000)
    private val FOOD = intArrayOf(150, 400, 900, 1800, 3000)

    fun woodLevel(sim: CivSim) = level(sim.wood, WOOD[sim.age.coerceIn(0, 4)], 10)
    fun stoneLevel(sim: CivSim) = level(sim.stone, STONE[sim.age.coerceIn(0, 4)], 8)
    fun foodLevel(sim: CivSim) = level(sim.food, FOOD[sim.age.coerceIn(0, 4)] + sim.buildings.count { it.type == "granary" } * 300, 6)
    fun hideLevel(sim: CivSim) = level(sim.hides, 100, 4)
    fun metalLevel(sim: CivSim) = level(sim.metal, 200, 6)

    /** Each kind of prop the village shows right now (stockpiles that exist, plus a scarecrow per farm). */
    fun propKinds(sim: CivSim): List<String> {
        val l = ArrayList<String>()
        if (woodLevel(sim) > 0) l.add("woodpile")
        if (stoneLevel(sim) > 0) l.add("stoneheap")
        if (foodLevel(sim) > 0) l.add("grainsacks")
        if (hideLevel(sim) > 0) l.add("hiderack")
        if (metalLevel(sim) > 0) l.add("ingots")
        if (sim.buildings.any { it.type == "farm" }) l.add("scarecrow")
        return l
    }
}
