package com.jondude1.deskglass.sim

/** One thing worth looking at right now in the village. */
class Sight(val kind: String, val label: String, val x: Float, val y: Float, val weight: Int)

/** Finds what is happening in the village right now so the view can fly to it. Pure reading, nothing is saved. */
object Sights {
    fun find(sim: CivSim): List<Sight> {
        val out = ArrayList<Sight>()
        try {
            val seenFoe = HashSet<String>()
            for (p in sim.predators.toList()) {
                if (!seenFoe.add(p.kind)) continue
                val n = sim.predators.count { it.kind == p.kind }
                out.add(Sight("foe", if (n > 1) "$n ${p.kind}s attacking" else "A ${p.kind} attacking", p.x, p.y, if (predatorMaxHp(p.kind) >= 100f) 95 else 80))
            }
            if (sim.shipUp) out.add(Sight("ship", "Reaver longship landing", sim.shipX, sim.shipY, 90))
            val fb = sim.blazes.firstOrNull()
            if (fb != null) out.add(Sight("fire", "Fire!", (fb.i % GW).toFloat(), (fb.i / GW).toFloat(), 75))
            if (sim.riftState > 0) out.add(Sight("rift", if (sim.riftState == 2) "Rift expedition delving the ${sim.realmName(sim.riftTheme)}" else "Rift party gathering", sim.riftX, sim.riftY, 65))
            if (sim.time - sim.weddingAt < 90.0) out.add(Sight("wedding", "Wedding: " + sim.weddingNames, sim.weddingX, sim.weddingY, 60))
            if (sim.feteOn) out.add(Sight("fete", "Village fete (" + sim.feteKind + ")", sim.feteX, sim.feteY, 55))
            for (e in sim.critters.encounters.toList()) {
                if (e.state == "walk") continue
                val lab = when (e.kind) { "wild" -> "Critter battle"; "summon" -> "Summoning ritual"; "spar" -> "Critter sparring"; "train" -> "Critter training"; else -> "Critter " + e.kind }
                out.add(Sight("critter", lab + " · " + e.v.name, e.v.x, e.v.y, if (e.tour) 58 else 48))
                break
            }
            val vis = sim.visitor
            if (vis != null && vis.arrivedAt >= 0.0 && !vis.leaving) out.add(Sight("visitor", "A " + sim.visitorLabel(vis.kind) + " at the fire", vis.x, vis.y, 45))
            val hurt = sim.villagers.filter { it.hp < 35f }.minByOrNull { it.hp }
            if (hurt != null) out.add(Sight("hurt", hurt.name + " is badly hurt", hurt.x, hurt.y, 40))
        } catch (e: Exception) {
            // the sim keeps moving on its own thread; an odd frame just shows fewer sights
        }
        return out.sortedByDescending { it.weight }
    }

    /** The next sight after [afterKey] (kind+label) in importance order, wrapping round. */
    fun next(list: List<Sight>, afterKey: String): Sight? {
        if (list.isEmpty()) return null
        val i = list.indexOfFirst { it.kind + it.label == afterKey }
        return list[(i + 1) % list.size]
    }
}
