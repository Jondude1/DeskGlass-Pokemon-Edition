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
    if (System.getenv("SURPLUS") == "0") sim.smartSurplus = false
    val cw = sim.critters
    val seenEnc = LinkedHashSet<Enc>()
    val logSeen = LinkedHashSet<String>()
    val actHist = HashMap<String, Int>()
    val perP = HashMap<Int, HashMap<String, Int>>()
    var hurtSamples = 0; var farSamples = 0; var samples = 0; var stuckIdle = 0; val happySum = DoubleArray(1)
    val dt = 0.05f
    var maxOut = 0
    val spellsSeen = HashMap<String, Int>()
    var maxTeam = 0
    var noneOut = 0
    var swaps = 0
    val steps = (hours * 3600 / dt).toInt()
    var lastAge = sim.age; val ageAt = ArrayList<String>()
    var nextReport = 0
    val huddleWhy = HashMap<String, Int>(); var foeSamples = 0; var foeTotal = 0
    val t0 = System.currentTimeMillis()
    for (i in 0 until steps) {
        sim.tick(dt)
        if (sim.age != lastAge) { lastAge = sim.age; ageAt.add("age${sim.age}@%.2fh".format(sim.time / 3600.0)) }
        if (i % 12000 == 0) { val cmds = listOf("gather wood","attack the enemy","defend the village","hold a feast","build more homes","go hunting","everyone hide","study hard","trade at the market","rest now"); sim.whisper(cmds[(i/12000) % cmds.size]) }
        if (i % 40 == 0) { for (w in cw.wilds) if (w.c.species.rarity >= 5) legSeen.merge(w.c.species.name, 1, Int::plus); for (e in cw.encounters) if (e.foe?.species?.rarity ?: 0 >= 5) legEnc.add(e.state + ":" + e.result) }
        if (i % 20 == 0) { foeTotal++; if (sim.predators.isNotEmpty()) foeSamples++ }
        if (i % 20 == 0) { for (v in sim.villagers) { if (v.action == "huddle" && v.phase != "idle") { val kid = v.job == "Child"; val near = sim.predators.any { Math.hypot((it.x - v.x).toDouble(), (it.y - v.y).toDouble()) < 13.0 }; val k = (if (kid) "kid" else "adult") + (if (near) "-near" else if (sim.predators.isNotEmpty()) "-farfoe:" + sim.predators.map { it.kind }.distinct().sorted().joinToString("+") + "@" + sim.predators.minOf { Math.hypot((it.x - v.x).toDouble(), (it.y - v.y).toDouble()) }.toInt() / 10 * 10 else "-nofoe"); huddleWhy.merge(k, 1, Int::plus) } }; for (v in sim.villagers) { actHist.merge(if (v.phase == "idle") "IDLE" else v.action, 1, Int::plus); if (v.job != "Child") perP.getOrPut(v.personaIdx) { HashMap() }.merge(if (v.phase == "idle") "IDLE" else v.action, 1, Int::plus); samples++; if (v.hp < 60f) hurtSamples++; if (v.phase == "idle" && v.idleFor > 2f) stuckIdle++; happySum[0] += v.happiness; val cd = Math.hypot((v.x - 48.0), (v.y - 27.0)); if (cd > 26) farSamples++ } }
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
    println("STARS fallen=${sim.starsFallen} shardsGathered=${sim.starShardsGot} metal=${sim.metal}")
    println("SURPLUS wastedGathers=${sim.wastedGathers} carved=${sim.carved} posies=${sim.posies}")
    println("FOEUPTIME ${"%.1f".format(100.0 * foeSamples / foeTotal)}% huddleShare=${"%.1f".format(100.0 * (actHist["huddle"] ?: 0) / samples)}% attackShare=${"%.2f".format(100.0 * (actHist["attack"] ?: 0) / samples)}%")
    println("HUDDLE " + huddleWhy.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" })
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
    println("STOCK levels wood=${Stock.woodLevel(sim)}/10 stone=${Stock.stoneLevel(sim)}/8 food=${Stock.foodLevel(sim)}/6 hides=${Stock.hideLevel(sim)}/4 metal=${Stock.metalLevel(sim)}/6 propKinds=${Stock.propKinds(sim).size} ${Stock.propKinds(sim)}")
    println("stores: food=${sim.food} wood=${sim.wood} stone=${sim.stone} ore=${sim.ore} metal=${sim.metal} hides=${sim.hides}")
    println("chronicle (last 12):"); sim.chronicle.takeLast(12).forEach { println("  $it") }
    println("news (last 14):"); sim.log.takeLast(14).forEach { println("  $it") }
    val atRanch = sim.critters.critters.count { c -> c.owner < 0 && sim.buildings.any { b -> b.type == "ranch" && Math.hypot((b.x + 0.5 - (sim.critters.bodies[c.uid]?.x ?: 0f)), (b.y + 0.5 - (sim.critters.bodies[c.uid]?.y ?: 0f))) < 4.0 } }
// save round trip of the critter layer
    val j = cw.toJson()
    val before = cw.critters.map { Triple(it.uid, it.sp, it.level) }
    cw.fromJson(j)
    val after = cw.critters.map { Triple(it.uid, it.sp, it.level) }
    println("save round trip ok=${before == after} (${after.size} critters, dex ${cw.caught.size}/${Dex.COUNT})")
    println("AGES reached: $ageAt final=${sim.age} pop=${sim.villagers.size}")
    println("AGENEED age=${sim.age} day=${sim.day()} ${sim.ageNeedText()} progress=${sim.progress()}")
    println("BUILD pop=${sim.villagers.size} age=${sim.age} ${sim.buildSummary()} total=${sim.buildings.size} goods=${sim.goods}")
    var viol = 0
    for (t in listOf("hut","farm","campfire","smelter","well","workshop","market","lamp")) if (sim.buildings.count{it.type==t} > sim.capFor(t) + 1) viol++
    println("FAUNA " + sim.fauna.animals.groupingBy { it.kind }.eachCount() + " capViolations=$viol")
    println("WILD kindsSeen=${sim.fauna.kindsSeen.size} ${sim.fauna.kindsSeen.sorted()} events=${sim.fauna.seen.toSortedMap()}")
    println("RIFTS expeditions=${sim.expeditions} wins=${sim.expWins} retreats=${sim.expRetreats} rooms=${sim.roomsCleared} deepest=${sim.deepestRoom} relics=${sim.relics} wardens=${sim.wardensSlain} state=${sim.riftState}")
    println("THREATS raids=${sim.raidsRepelled} ogres=${sim.ogresSlain} bosses=${sim.bossesSlain} golden=${sim.goldenAt} pop=${sim.villagers.size} legacy=${sim.legacy().count{it.second}}/${sim.legacy().size} legends=${sim.critters.legendsOwned()}")
    println("FOES kinds=${sim.foeStats.size} ${sim.foeStats.toSortedMap()} warlords=${sim.warlordsSlain} golems=${sim.golemsSlain} shamans=${sim.shamansSlain} frostPacks=${sim.frostPacksBroken} pilfersFoiled=${sim.pilfersFoiled} pilfersLost=${sim.pilfersLost} swarms=${sim.swarmsBroken} hornets=${sim.hornetsSwatted} liches=${sim.lichesSlain} bones=${sim.bonesRaised} landings=${sim.landings} reavers=${sim.reaversSlain} ships=${sim.shipsSeized}")
    println("WEATHER day=${sim.day()} season=${sim.seasonName()} events=${sim.wxStats.toSortedMap()} distinct=${sim.wxStats.size}")
    println("RELATIONS weddings=${sim.weds} couples=${sim.villagers.count { it.partner >= 0 } / 2} bffs=${sim.bffs.size} feudsStarted=${sim.feudsStarted} feudsEnded=${sim.feudsEnded} activeFeuds=${sim.villagers.count { sim.feudWith(it) != null } / 2} babiesBorn=${sim.babiesBorn}")
    println("VISITORS total=${sim.visitTotal()} ${sim.visitStats.toSortedMap()}")
    println("WONDERS count=${sim.buildings.count { it.type == "wonder" }} kinds=${sim.buildings.filter { it.type == "wonder" }.map { sim.wonderName(it.era) }} wood=${sim.wood} stone=${sim.stone} metal=${sim.metal}")
    println("EGGS laid=${sim.critters.eggsLaid} hatched=${sim.critters.eggsHatched} golden=${sim.critters.goldenHatched} pending=${sim.critters.eggs.count { it.hatchedAt < 0.0 }} critters=${sim.critters.critters.size}")
    println("TOURNEY held=${sim.critters.tourneys} matches=${sim.critters.tourMatches} forfeits=${sim.critters.tourForfeits} champ=${sim.critters.champName} on=${sim.critters.tourOn}")
    println("SHINY seen=${sim.critters.shinySeen} hatched=${sim.critters.shinyHatched} kept=${sim.critters.shinyKept()} lvs=${sim.critters.critters.filter { it.shiny }.map { it.species.name + it.level }}")
    println("PASTURE built=${sim.buildings.count { it.type == "pasture" }} visits=${sim.pastureVisits} eggs=${sim.eggsGathered} fleeces=${sim.fleeces} sheep=${sim.sheepNow()} hens=${sim.hensNow()}")
    println("DOCK built=${sim.buildings.count { it.type == "dock" }} sails=${sim.sailTrips} dockCatch=${sim.dockCatch} bottles=${sim.bottlesFound}")
    println("QUARRYAT ${sim.buildings.filter { it.type == "quarry" }.map { (it.built / 3600.0) }} now=${sim.time / 3600.0}")
    println("MILL built=${sim.buildings.count { it.type == "mill" }} grinds=${sim.millGrinds} flour=${sim.flourMade} loaves=${sim.loavesBaked} food=${sim.food}")
    println("QUARRY built=${sim.buildings.count { it.type == "quarry" }} hauls=${sim.quarryHauls} stone=${sim.quarryStone} veins=${sim.quarryVeins} storeStone=${sim.stone}")
    println("SHRINE built=${sim.buildings.count { it.type == "shrine" }} vigils=${sim.vigils} calls=${sim.legendCalls} points=${sim.vigilPoints}/${sim.vigilNeed()} legendsOwned=${sim.critters.legendsOwned()}")
    println("CAMP up=${sim.campUp} razed=${sim.campsRazed} hits=${sim.campHits} sorties=${sim.campSorties} hp=${sim.campHp.toInt()}/${sim.campMax.toInt()}")
    println("RANCH built=${sim.buildings.count { it.type == "ranch" }} visits=${sim.ranchVisits} tended=${sim.critters.ranchTended} residents=${sim.ranchResidents()} atRanch=$atRanch")
    println("BALLISTA built=${sim.buildings.count { it.type == "ballista" }} shots=${sim.ballistaShots} kills=${sim.ballistaKills} towers=${sim.buildings.count { it.type == "tower" }}")
    println("HEALTH hurtShare=%.2f%% infirmaries=${sim.buildings.count { it.type == "infirmary" }} recoveries=${sim.recoveries}".format(100.0 * hurtSamples / samples.coerceAtLeast(1)))
    println("STATUES count=${sim.statues.size} cheers=${sim.statueCheers} tiers=${sim.statues.map { it.tier }} heroes=${sim.statues.map { it.name + ":" + it.kind }}")
    println("RAISED buildings=${sim.raised} sitesNow=${sim.buildings.count { sim.time - it.built < 9.0 }}")
    println("TOTEM built=${sim.buildings.count { it.type == "totem" }} visits=${sim.totemVisits} omens=${sim.omens} kinds=${sim.buildings.map { it.type }.toSet().size}")
    println("MENTOR lessons=${sim.lessonsGiven} apprenticesMade=${sim.apprenticesMade} pending=${sim.apprentice.size} kids=${sim.villagers.count { sim.isChild(it) }}")
    println("SEASONFUN snowmen=${sim.snowmenBuilt} leafPiles=${sim.leafPilesRaked} kites=${sim.kitesFlown} propsNow=${sim.seasonProps.map { it.kind }} total=${sim.snowmenBuilt + sim.leafPilesRaked + sim.kitesFlown}")
    println("PETS dogs=${sim.pets.size} adopted=${sim.dogsAdopted} walks=${sim.dogWalks} barks=${sim.dogBarks} onMap=${sim.fauna.animals.count { it.kind == "dog" }} names=${sim.pets.map { it.name + ":" + it.bond }}")
    println("FETE held=${sim.fetesHeld} revels=${sim.feteRevels} kinds=${sim.feteKinds} on=${sim.feteOn}")
    run { val tv = sim.buildings.count { it.type == "tavern" }; println("TAVERN built=$tv visits=${sim.tavernVisits}") }
    run { println("RESEARCH studies=${sim.studyCount} points=${sim.research} libs=${sim.buildings.count { it.type == "library" }} techs=${sim.techs.size}/${sim.allTechs.size} list=${sim.techs.joinToString(",")}") }
    println("DEEDS ${sim.deeds.size}/${sim.allDeeds.size} renown=${sim.renownTitle()} ${sim.deeds}")
    run {
        val keys = perP.keys.sorted()
        fun cos(a: Map<String, Int>, b: Map<String, Int>): Double { val d = a.entries.sumOf { (it.value * (b[it.key] ?: 0)).toDouble() }; val na = Math.sqrt(a.values.sumOf { it.toDouble() * it }); val nb = Math.sqrt(b.values.sumOf { it.toDouble() * it }); return if (na == 0.0 || nb == 0.0) 1.0 else d / (na * nb) }
        var tot = 0.0; var n = 0
        for (i in keys.indices) for (j in i + 1 until keys.size) { tot += 1 - cos(perP[keys[i]]!!, perP[keys[j]]!!); n++ }
        println("PERSONA divergence=%.4f personas=${keys.size} dreams=${sim.dreamsDone} dreamers=${sim.villagers.filter { it.stat("dream") > 0 }.joinToString { it.name + ":" + it.persona.dream.take(20) }} pastimes=${sim.pastimeCount}".format(if (n == 0) 0.0 else tot / n))
    }
    println("TITLES earned=${sim.titlesEarned} villagersTitled=${sim.villagers.count { it.titles.isNotEmpty() }} list=${sim.villagers.filter { it.titles.isNotEmpty() }.joinToString { it.name + ":" + it.titles.joinToString("/") }} bonds=${sim.villagers.count { sim.bondsOf(it).isNotEmpty() }} career=${sim.villagers.count { sim.careerLines(it).isNotEmpty() }}")
    println("STATUS inflicted(burn,poison,stun,chill,fright)=${Battle.inflictedCount.drop(1)} ticks=${Battle.count.drop(1)} tickDamage=${Battle.tickTotal}")
    println("ABIL talents fired=${Battle.abilCount.values.sum()} kinds=${Battle.abilCount.size} ${Battle.abilCount.toSortedMap()}")
    sim.log.filter { it.contains("aider") || it.contains("ogre") || it.contains("wraith") || it.contains("drake") || it.contains("warlord") || it.contains("golem") || it.contains("shaman") || it.contains("Golden") }.takeLast(8).forEach { println("  LOG $it") }
    println("LEGENDS wild=$legSeen enc=$legEnc maxCompLv=${sim.critters.critters.maxOfOrNull{it.level}}")
    println("LEGLOG " + sim.log.filter{it.contains("LEGENDDBG")||it.contains("pact")||it.contains("legend")}.takeLast(6))
    println("RESULTS " + sim.log.filter{it.contains("lost to")||it.contains("defeat")}.size)
}
