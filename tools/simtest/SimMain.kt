import com.jondude1.deskglass.sim.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun main(args: Array<String>) {
    val hours = (args.getOrNull(0) ?: "2").toDouble()
    val f = File.createTempFile("civ", ".json"); f.delete()
    val sim = CivSim(f, CoroutineScope(Dispatchers.Default)) { null }
    if (System.getenv("BOOST") != null) { sim.age = (System.getenv("BOOST")!!).toInt().coerceAtLeast(2); sim.metal = 30; sim.wood = 100; sim.stone = 100 }
    val legSeen = HashMap<String,Int>(); val legEnc = LinkedHashSet<String>()
    val errors = ArrayList<String>()
    sim.onError = { errors.add(it) }
    val cw = sim.critters
    val seenEnc = LinkedHashSet<Enc>()
    val logSeen = LinkedHashSet<String>()
    val actHist = HashMap<String, Int>()
    var farSamples = 0; var samples = 0; var stuckIdle = 0; val happySum = DoubleArray(1)
    val dt = 0.05f
    var maxOut = 0
    val spellsSeen = HashMap<String, Int>()
    var maxTeam = 0
    var noneOut = 0
    var swaps = 0
    val steps = (hours * 3600 / dt).toInt()
    var nextReport = 0
    val t0 = System.currentTimeMillis()
    for (i in 0 until steps) {
        sim.tick(dt)
        if (i % 12000 == 0) { val cmds = listOf("gather wood","attack the enemy","defend the village","hold a feast","build more homes","go hunting","everyone hide","study hard","trade at the market","rest now"); sim.whisper(cmds[(i/12000) % cmds.size]) }
        if (i % 40 == 0) { for (w in cw.wilds) if (w.c.species.rarity >= 5) legSeen.merge(w.c.species.name, 1, Int::plus); for (e in cw.encounters) if (e.foe?.species?.rarity ?: 0 >= 5) legEnc.add(e.state + ":" + e.result) }
        if (i % 20 == 0) { for (v in sim.villagers) { actHist.merge(if (v.phase == "idle") "IDLE" else v.action, 1, Int::plus); samples++; if (v.phase == "idle" && v.idleFor > 2f) stuckIdle++; happySum[0] += v.happiness; val cd = Math.hypot((v.x - 48.0), (v.y - 27.0)); if (cd > 26) farSamples++ } }
        if (i % 40 == 0) for (l in sim.log.toList()) if (l.contains("circle") || l.contains("teaches") || l.contains("scroll") || l.contains("channels")) logSeen.add(l)
        for (e in cw.encounters) { seenEnc.add(e); if (e.state == "cast" && e.spellT < 0.06f) spellsSeen.merge(e.spell, 1, Int::plus) }
        if (i % 20 == 0) {
            for (v in sim.villagers) {
                val out = cw.critters.count { it.owner == v.id && it.companion }
                if (out > maxOut) maxOut = out
                val tm = cw.critters.count { it.owner == v.id }
                if (tm > maxTeam) maxTeam = tm
                if (out > 1) { println("VIOLATION: ${v.name} has $out critters out at step $i"); return }
                if (cw.critters.any { it.owner == v.id } && out == 0) noneOut++
            }
            swaps += 0
        }
        cw.lastError?.let { println("CRITTER ERROR at step $i"); it.printStackTrace(System.out); return }
        if (i / (steps / 6) >= nextReport && i > 0) {
            nextReport++
            println("t=%.0f min  age=${sim.age} villagers=${sim.villagers.size} wild=${cw.wilds.size} kept=${cw.critters.size} enc=${cw.encounters.size} preds=${sim.predators.size} dex seen=${cw.seen.size} caught=${cw.caught.size}".format(sim.time / 60.0))
        }
    }
    println("max team size = $maxTeam; max critters out per villager = $maxOut, samples with owner-but-none-out = $noneOut")
    println("--- done in ${(System.currentTimeMillis() - t0) / 1000}s wall ---")
    println("sim errors: $errors")
    val byKind = seenEnc.groupBy { it.kind + ":" + it.result.ifEmpty { "(none)" } }.mapValues { it.value.size }
    println("encounters: $byKind")
    println("spells cast: $spellsSeen")
    println("summoners: " + sim.villagers.filter { cw.mageOf(it).talent }.joinToString { "${it.name} Lv${cw.mageOf(it).lv} (cap ${cw.mageOf(it).capacity}, used ${cw.budgetUsed(it)})" })
    println("magic log: " + logSeen.groupBy { it.replace(Regex("[A-Z][a-z]+"), "N").take(30) }.mapValues { it.value.size })
    logSeen.take(8).forEach { println("   $it") }
    println("actions: " + actHist.entries.sortedByDescending { it.value }.take(14).joinToString { "${it.key}=${it.value}" })
    println("far from village samples: $farSamples/$samples")
    println("MEASURE idle>2s=${"%.2f".format(100.0*stuckIdle/samples)}% IDLEtotal=${"%.2f".format(100.0*(actHist["IDLE"]?:0)/samples)}% distinctActions=${actHist.keys.size} avgHappiness=${"%.1f".format(happySum[0]/samples)} actions=${actHist.keys.sorted()}")
    println("scrolls common/fine/rare: ${cw.scrolls.toList()}")
    println("mentors: " + sim.villagers.filter { cw.mageOf(it).mentor >= 0 }.joinToString { "${it.name}<-${sim.villagers.firstOrNull { x -> x.id == cw.mageOf(it).mentor }?.name}" })
    println("unrevealed (children): " + sim.villagers.count { !cw.mageOf(it).revealed })
    println("final (rarity,stage) per line: " + Dex.lines().groupBy { it.last().rarity to it.last().stage }.mapValues { e -> e.value.map { it.first().name } })
    println("tier pool sizes: " + (0..2).map { t -> Dex.lines().count { l -> val top = l.last(); when (t) { 0 -> top.rarity <= 2; 1 -> top.rarity == 3; else -> top.rarity >= 4 || (top.rarity >= 3 && top.stage >= 3) } } })
    println("non-gifted villagers: " + sim.villagers.count { !cw.mageOf(it).talent })
    println("circle at ${cw.circleX},${cw.circleY}  swaps now=${cw.swaps.size}")
    println("kept critters:")
    for (c in cw.critters.sortedByDescending { it.level }) {
        println("  ${c.species.name.padEnd(12)} Lv${c.level.toString().padEnd(3)} ${c.species.typeText.padEnd(12)} owner=${cw.ownerName(c).ifEmpty { "village" }.padEnd(8)} companion=${c.companion} wins=${c.wins} hp=${c.hp}/${c.maxHp}")
    }
    println("evolution scenes active: ${cw.evolutions.size}")
    println("stores: food=${sim.food} wood=${sim.wood} stone=${sim.stone} ore=${sim.ore} metal=${sim.metal} hides=${sim.hides}")
    println("chronicle (last 12):"); sim.chronicle.takeLast(12).forEach { println("  $it") }
    println("news (last 14):"); sim.log.takeLast(14).forEach { println("  $it") }
    // save round trip of the critter layer
    val j = cw.toJson()
    val before = cw.critters.map { Triple(it.uid, it.sp, it.level) }
    cw.fromJson(j)
    val after = cw.critters.map { Triple(it.uid, it.sp, it.level) }
    println("save round trip ok=${before == after} (${after.size} critters, dex ${cw.caught.size}/${Dex.COUNT})")
    println("BUILD pop=${sim.villagers.size} age=${sim.age} ${sim.buildSummary()} total=${sim.buildings.size} goods=${sim.goods}")
    var viol = 0
    for (t in listOf("hut","farm","campfire","smelter","well","workshop","market","lamp")) if (sim.buildings.count{it.type==t} > sim.capFor(t) + 1) viol++
    println("FAUNA " + sim.fauna.animals.groupingBy { it.kind }.eachCount() + " capViolations=$viol")
    println("THREATS raids=${sim.raidsRepelled} ogres=${sim.ogresSlain} bosses=${sim.bossesSlain} golden=${sim.goldenAt} pop=${sim.villagers.size} legacy=${sim.legacy().count{it.second}}/${sim.legacy().size} legends=${sim.critters.legendsOwned()}")
    println("WEATHER day=${sim.day()} season=${sim.seasonName()} events=${sim.wxStats.toSortedMap()} distinct=${sim.wxStats.size}")
    sim.log.filter { it.contains("aider") || it.contains("ogre") || it.contains("wraith") || it.contains("drake") || it.contains("Golden") }.takeLast(8).forEach { println("  LOG $it") }
    println("LEGENDS wild=$legSeen enc=$legEnc maxCompLv=${sim.critters.critters.maxOfOrNull{it.level}}")
    println("LEGLOG " + sim.log.filter{it.contains("LEGENDDBG")||it.contains("pact")||it.contains("legend")}.takeLast(6))
    println("RESULTS " + sim.log.filter{it.contains("lost to")||it.contains("defeat")}.size)
}
