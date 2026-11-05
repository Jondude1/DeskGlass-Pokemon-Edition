import com.jondude1.deskglass.sim.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun main(args: Array<String>) {
    val hours = (args.getOrNull(0) ?: "2").toDouble()
    val f = File.createTempFile("civ", ".json"); f.delete()
    val sim = CivSim(f, CoroutineScope(Dispatchers.Default)) { null }
    if (System.getenv("GOLDEN") != null) sim.goldenAt = 0.5
    if (System.getenv("BOOST") != null) { sim.age = (System.getenv("BOOST")!!).toInt().coerceAtLeast(2); sim.metal = 30; sim.wood = 100; sim.stone = 100 }
    val legSeen = HashMap<String,Int>(); val legEnc = LinkedHashSet<String>()
    val errors = ArrayList<String>()
    sim.onError = { errors.add(it) }
    if (System.getenv("SURPLUS") == "0") sim.smartSurplus = false
    val cw = sim.critters
    val seenEnc = LinkedHashSet<Enc>()
    val logSeen = LinkedHashSet<String>()
    val actHist = HashMap<String, Int>()
    val kidHist = HashMap<String, Int>()
    val perP = HashMap<Int, HashMap<String, Int>>()
    var hurtSamples = 0; var farSamples = 0; var samples = 0; var stuckIdle = 0; val happySum = DoubleArray(1)
    val dt = 0.05f
    val looksSeen = HashSet<Int>(); val jobsSeen = HashSet<String>(); var elderSamples = 0; var adultSamples = 0
    var maxOut = 0
    val spellsSeen = HashMap<String, Int>()
    var maxTeam = 0
    val forkTest = System.getenv("FORKTEST") != null
    var noneOut = 0
    var swaps = 0
    val steps = (hours * 3600 / dt).toInt()
    var lastAge = sim.age; val ageAt = ArrayList<String>()
    var nextReport = 0
    val foeKinds = HashMap<String, Int>(); val huddleWhy = HashMap<String, Int>(); var foeSamples = 0; var foeTotal = 0
    val t0 = System.currentTimeMillis()
    val godBot = System.getenv("NOGOD") == null; val godLog = ArrayList<String>()
    val sightKinds = HashMap<String, Int>(); var sightSamples = 0; var sightAny = 0; var sightMax = 0
    for (i in 0 until steps) {
        sim.tick(dt)
        if (godBot && i % 200 == 0) {
            val hurtN = sim.villagers.count { it.hp < 70f }
            val order = if (sim.predators.size >= 2 || (sim.predators.isNotEmpty() && sim.predators.any { isThreat(it.kind) })) listOf("smite", "heal", "rain", "grow", "sun") else if (hurtN >= 2) listOf("heal", "grow", "sun", "rain") else listOf("grow", "sun", "rain", "heal")
            for (id in order) { val pw = Divine.find(id)!!; if (sim.divine.canAfford(pw)) { val m = sim.godPower(id); if (godLog.size < 12 && !m.startsWith("Not") && !m.startsWith("The village sky") && !m.startsWith("It is") && !m.startsWith("Nobody") && !m.startsWith("There") && !m.startsWith("No foe")) godLog.add(m); break } }
        }
        if (i % 40 == 0) { val sl = Sights.find(sim); sightSamples++; if (sl.isNotEmpty()) sightAny++; if (sl.size > sightMax) sightMax = sl.size; for (s in sl) sightKinds.merge(s.kind, 1, Int::plus) }
        if (forkTest && i % 600 == 0) for (c in cw.critters) if (c.owner >= 0 && com.jondude1.deskglass.sim.Dex.branchOf(c.sp) != null && c.level < 28) { c.level = 28; c.bond = 40 }
        if (sim.age != lastAge) { lastAge = sim.age; ageAt.add("age${sim.age}@%.2fh".format(sim.time / 3600.0)) }
        if (i % 12000 == 0) { val cmds = listOf("gather wood","attack the enemy","defend the village","hold a feast","build more homes","go hunting","everyone hide","study hard","trade at the market","rest now"); sim.whisper(cmds[(i/12000) % cmds.size]) }
        if (i % 40 == 0) { for (w in cw.wilds) if (w.c.species.rarity >= 5) legSeen.merge(w.c.species.name, 1, Int::plus); for (e in cw.encounters) if (e.foe?.species?.rarity ?: 0 >= 5) legEnc.add(e.state + ":" + e.result) }
        if (i % 200 == 0) { for (v in sim.villagers) { looksSeen.add(Look.key(sim, v)); jobsSeen.add(v.job); if (v.job != "Child") { adultSamples++; if (Look.elder(sim, v)) elderSamples++ } } }
        if (i % 20 == 0) { foeTotal++; if (sim.predators.isNotEmpty()) foeSamples++; for (k in sim.predators.map { it.kind }.distinct()) foeKinds.merge(k, 1, Int::plus) }
        if (i % 20 == 0) { for (v in sim.villagers) if (v.job == "Child") kidHist.merge(if (v.action.isEmpty()) "IDLE" else v.action, 1, Int::plus) }
        if (i % 20 == 0) { for (v in sim.villagers) { if (v.action == "huddle" && v.phase != "idle") { val kid = v.job == "Child"; val near = sim.predators.any { Math.hypot((it.x - v.x).toDouble(), (it.y - v.y).toDouble()) < 13.0 }; val k = (if (kid) "kid" else "adult") + (if (near) "-near" else if (sim.predators.isNotEmpty()) "-farfoe:" + sim.predators.map { it.kind }.distinct().sorted().joinToString("+") + "@" + sim.predators.minOf { Math.hypot((it.x - v.x).toDouble(), (it.y - v.y).toDouble()) }.toInt() / 10 * 10 else "-nofoe") + (if (sim.raining()) "+rain" else ""); huddleWhy.merge(k, 1, Int::plus) } }; for (v in sim.villagers) { actHist.merge(if (v.phase == "idle") "IDLE" else v.action, 1, Int::plus); if (v.job != "Child") perP.getOrPut(v.personaIdx) { HashMap() }.merge(if (v.phase == "idle") "IDLE" else v.action, 1, Int::plus); samples++; if (v.hp < 60f) hurtSamples++; if (v.phase == "idle" && v.idleFor > 2f) stuckIdle++; happySum[0] += v.happiness; val cd = Math.hypot((v.x - 48.0), (v.y - 27.0)); if (cd > 26) farSamples++ } }
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
            println("   NEED day=${sim.day()} progress=${sim.progress()}% missing: ${sim.ageNeedText()} | food=${sim.food} wood=${sim.wood} stone=${sim.stone}")
            println("t=%.0f min  age=${sim.age} villagers=${sim.villagers.size} wild=${cw.wilds.size} kept=${cw.critters.size} enc=${cw.encounters.size} preds=${sim.predators.size} dex seen=${cw.seen.size} caught=${cw.caught.size}".format(sim.time / 60.0))
        }
    }
    println("max team size = $maxTeam; max critters out per villager = $maxOut, samples with owner-but-none-out = $noneOut")
    println("LOOKS distinctAppearances=${looksSeen.size} (was ${jobsSeen.size}: one per job) elderShare=${"%.1f".format(100.0 * elderSamples / adultSamples.coerceAtLeast(1))}% now: " + sim.villagers.map { Look.key(sim, it) }.distinct().size + " looks among " + sim.villagers.size + " villagers")
    println("--- done in ${(System.currentTimeMillis() - t0) / 1000}s wall ---")
    println("sim errors: $errors")
    val byKind = seenEnc.groupBy { it.kind + ":" + it.result.ifEmpty { "(none)" } }.mapValues { it.value.size }
    println("encounters: $byKind")
    println("spells cast: $spellsSeen")
    println("summoners: " + sim.villagers.filter { cw.mageOf(it).talent }.joinToString { "${it.name} Lv${cw.mageOf(it).lv} (cap ${cw.mageOf(it).capacity}, used ${cw.budgetUsed(it)})" })
    println("magic log: " + logSeen.groupBy { it.replace(Regex("[A-Z][a-z]+"), "N").take(30) }.mapValues { it.value.size })
    logSeen.take(8).forEach { println("   $it") }
    run { val kt = kidHist.values.sum().coerceAtLeast(1); println("KIDS samples=$kt play=${"%.1f".format(100.0 * (kidHist["play"] ?: 0) / kt)}% distinct=${kidHist.keys.size} mix=" + kidHist.entries.sortedByDescending { it.value }.take(14).joinToString { "${it.key}=${"%.0f".format(100.0 * it.value / kt)}%" }) }
    println("actions: " + actHist.entries.sortedByDescending { it.value }.take(14).joinToString { "${it.key}=${it.value}" })
    println("ACTALL total=$samples wander=${"%.2f".format(100.0*(actHist["wander"]?:0)/samples)}% upkeep=${"%.2f".format(100.0*(actHist["upkeep"]?:0)/samples)}% entropyBits=${"%.2f".format(actHist.values.sumOf { c -> val p = c.toDouble()/samples; -p*Math.log(p)/Math.log(2.0) })} uprows=${sim.upkeepDone}")
    println("far from village samples: $farSamples/$samples")
    println("STARS fallen=${sim.starsFallen} shardsGathered=${sim.starShardsGot} metal=${sim.metal}")
    run {
        val pg = sim.almanac.pages
        val quiet = pg.count { it.split("|")[2] == "A quiet day." }
        println("ALMANAC pages=${pg.size} quiet=$quiet distinctHeadlines=${pg.map { it.split("|")[2] }.toSet().size} lines/page=${if (pg.isEmpty()) 0 else pg.sumOf { it.split("|")[3].split(";").size } / pg.size}")
        for (x in pg.takeLast(3)) println("   ALM " + x)
        val enc = sim.almanac.encode(); val copy = com.jondude1.deskglass.sim.Almanac(); copy.decode(enc)
        println("ALMANAC save round trip ok=${copy.pages == pg}")
    }
    run {
        val d = sim.divine
        println("DIVINE powersUsed=${d.totalUses()} byKind=${Divine.POWERS.mapIndexed { k, p -> p.id + "=" + d.uses[k] }} distinctPowers=${d.kinds()} favourEarned=${"%.0f".format(d.earned)} favourNow=${"%.0f".format(d.favour)} refused=${d.refused} healed=${d.healed} foesStruck=${d.smitten} smiteKills=${d.smiteKills}")
        for (m in godLog.take(6)) println("   GOD $m")
        val enc = d.encode(); val c2 = Divine(); c2.decode(enc)
        println("DIVINE codec round trip ok=${c2.encode() == enc} enc=$enc")
        sim.divine.favour = 100f; sim.villagers[0].hp = 30f
        val hm = sim.godPower("heal"); println("DIVINE forced heal -> hp=${sim.villagers[0].hp.toInt()} msg=$hm")
        sim.divine.favour = 5f; println("DIVINE broke -> ${sim.godPower("smite")}")
        sim.save()
        val sim2 = CivSim(f, CoroutineScope(Dispatchers.Default)) { null }
        println("NIGHTSKY save round trip ok=${sim2.skyShows.toList() == sim.skyShows.toList() && sim2.skyWatches == sim.skyWatches && sim2.skyWishes == sim.skyWishes && sim2.skyLastNight == sim.skyLastNight}")
        println("REALMS save round trip ok=${sim2.realmRuns.toList() == sim.realmRuns.toList() && sim2.realmWins.toList() == sim.realmWins.toList() && sim2.lastTheme == sim.lastTheme && sim2.realmHazards == sim.realmHazards}")
        println("CARAVAN save round trip ok=${sim2.caravans == sim.caravans && sim2.carFinds == sim.carFinds && sim2.carAmbush == sim.carAmbush && sim2.carBeaten == sim.carBeaten && sim2.carSpent == sim.carSpent}")
        println("DIVINE full save round trip ok=${sim2.divine.encode() == sim.divine.encode()} loadedUses=${sim2.divine.totalUses()}")
    }
    println("SURPLUS wastedGathers=${sim.wastedGathers} carved=${sim.carved} posies=${sim.posies}")
    println("FOEUPTIME ${"%.1f".format(100.0 * foeSamples / foeTotal)}% huddleShare=${"%.1f".format(100.0 * (actHist["huddle"] ?: 0) / samples)}% attackShare=${"%.2f".format(100.0 * (actHist["attack"] ?: 0) / samples)}%")
    for (pp in sim.predators) println("FOE-NOW ${pp.kind} at ${"%.1f".format(pp.x)},${"%.1f".format(pp.y)} life=${pp.life.toInt()} looted=${pp.looted} hp=${pp.hp.toInt()}")
    println("FOEKINDS (share of samples) " + foeKinds.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${"%.1f".format(100.0 * it.value / foeTotal)}%" })
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
    println("RIFTS expeditions=${sim.expeditions} wins=${sim.expWins} retreats=${sim.expRetreats} rooms=${sim.roomsCleared} deepest=${sim.deepestRoom} relics=${sim.relics} wardens=${sim.wardensSlain} state=${sim.riftState} side=${sim.sideRooms} traps=${sim.sideTraps} vaults=${sim.sideVaults} springs=${sim.sideSprings} shrines=${sim.sideShrines} rallies=${sim.riftRallies}")
    println("THREATS raids=${sim.raidsRepelled} ogres=${sim.ogresSlain} bosses=${sim.bossesSlain} golden=${sim.goldenAt} pop=${sim.villagers.size} legacy=${sim.legacy().count{it.second}}/${sim.legacy().size} legends=${sim.critters.legendsOwned()}")
    println("FOES kinds=${sim.foeStats.size} ${sim.foeStats.toSortedMap()} warlords=${sim.warlordsSlain} golems=${sim.golemsSlain} shamans=${sim.shamansSlain} frostPacks=${sim.frostPacksBroken} pilfersFoiled=${sim.pilfersFoiled} pilfersLost=${sim.pilfersLost} swarms=${sim.swarmsBroken} hornets=${sim.hornetsSwatted} liches=${sim.lichesSlain} bones=${sim.bonesRaised} landings=${sim.landings} reavers=${sim.reaversSlain} ships=${sim.shipsSeized}")
    println("OTTERS now=${sim.fauna.count("otter")} dives=${sim.fauna.seen["otter dive"] ?: 0} floats=${sim.fauna.seen["otter float"] ?: 0} gifts=${sim.otterGifts} cheered=${sim.otterCheers} deeds=${sim.deeds.filter { it.startsWith("otter") }}")
    println("TROLLS slain=${sim.trollsSlain} stoned=${sim.trollsStoned} boulders=${sim.trollBoulders} rocks=${sim.trollRocks.size}")
    println("HARPIES slain=${sim.harpiesSlain} flocks=${sim.flocksScattered} dives=${sim.harpyDives} loot=${sim.harpyLoot}")
    println("SPIDERS slain=${sim.spidersSlain} webs=${sim.websSpun} caught=${sim.webCatches} bites=${sim.spiderBites} websNow=${sim.webs.size} poisonedNow=${sim.poisoned.size}")
    println("SCORPIONS slain=${sim.scorpionsSlain} stings=${sim.scorpionStings} pincers=${sim.scorpionPincers} nests=${sim.scorpionSwarms} poisonedNow=${sim.poisoned.size}")
    println("RAMS sieges=${sim.ramSieges} slain=${sim.ramsSlain} slams=${sim.ramSlams} wrecked=${sim.ramWrecked} shieldBreaks=${sim.ramBreaks} foeStats=${sim.foeStats["ram"]}")
    println("STORMLINGS slain=${sim.stormlingsSlain} bolts=${sim.stormlingBolts} hits=${sim.stormlingHits} dodged=${sim.stormlingDodges} spent=${sim.stormlingSpent} faded=${sim.stormlingFades} foeStats=${sim.foeStats["stormling"]}")
    println("REALMS runs=${sim.realmRuns.toList()} wins=${sim.realmWins.toList()} kinds=${sim.realmRuns.count { it > 0 }} hazards=${sim.realmHazards} spoils=${sim.realmBonus}")
    println("YETIS slain=${sim.yetisSlain} throws=${sim.yetiThrows} smashes=${sim.yetiSmashes} herds=${sim.yetiHerds} foeStats=${sim.foeStats["yeti"]}")
    println("DUSTDEVILS slain=${sim.dustSlain} flings=${sim.dustFlings} grainLost=${sim.dustGrain} tired=${sim.dustTires} storms=${sim.dustStorms} foeStats=${sim.foeStats["dustdevil"]}")
    println("STAGS slain=${sim.stagsSlain} charges=${sim.stagCharges} gores=${sim.stagGores} missed=${sim.stagMisses} ruts=${sim.stagRuts} foeStats=${sim.foeStats["stag"]}")
    println("TOADS slain=${sim.toadsSlain} lashes=${sim.toadLashes} leaps=${sim.toadLeaps} choruses=${sim.toadChoruses} foeStats=${sim.foeStats["toad"]}")
    println("WORMS slain=${sim.wormsSlain} erupts=${sim.wormErupts} flung=${sim.wormHits} cratersNow=${sim.craters.size}")
    println("WEATHER day=${sim.day()} season=${sim.seasonName()} events=${sim.wxStats.toSortedMap()} distinct=${sim.wxStats.size}")
    println("RELATIONS weddings=${sim.weds} couples=${sim.villagers.count { it.partner >= 0 } / 2} bffs=${sim.bffs.size} feudsStarted=${sim.feudsStarted} feudsEnded=${sim.feudsEnded} activeFeuds=${sim.villagers.count { sim.feudWith(it) != null } / 2} babiesBorn=${sim.babiesBorn}")
    println("VISITORS total=${sim.visitTotal()} ${sim.visitStats.toSortedMap()} duels=${sim.knightDuels} toys=${sim.toysGiven} kinds=${sim.visitStats.size} mends=${sim.tinkerMends} sowFarms=${sim.sowerFarms} seerReads=${sim.seerReadings}")
    println("WONDERS count=${sim.buildings.count { it.type == "wonder" }} kinds=${sim.buildings.filter { it.type == "wonder" }.map { sim.wonderName(it.era) }} pulses=${sim.wonderPulses} wood=${sim.wood} stone=${sim.stone} metal=${sim.metal}")
    println("EGGS laid=${sim.critters.eggsLaid} hatched=${sim.critters.eggsHatched} golden=${sim.critters.goldenHatched} pending=${sim.critters.eggs.count { it.hatchedAt < 0.0 }} critters=${sim.critters.critters.size}")
    println("FORK branchEvos=${cw.branchEvos} held=${cw.holdHeld} formsSeen=${cw.caught.filter { it in 90..95 }.sorted()} stage3Caught=${cw.caught.count { com.jondude1.deskglass.sim.Dex.byId(it).stage == 3 }}")
    println("NEWLINES caught=${cw.caught.filter { it >= 96 }.sorted()} seen=${cw.seen.filter { it >= 96 }.sorted()} kept=${cw.critters.count { it.sp >= 96 }} bound=${cw.caught.size}/${Dex.COUNT} dexMiles=${cw.dexMiles}")
    println("TOURNEY held=${sim.critters.tourneys} matches=${sim.critters.tourMatches} forfeits=${sim.critters.tourForfeits} champ=${sim.critters.champName} on=${sim.critters.tourOn}")
    println("DEX species=${com.jondude1.deskglass.sim.Dex.COUNT} caught=${sim.critters.caught.size} seen=${sim.critters.seen.size} newLines(72-80) caught=${sim.critters.caught.filter { it >= 72 }.sorted()} seen=${sim.critters.seen.filter { it >= 72 }.sorted()} kept=${sim.critters.critters.filter { it.species.id >= 72 }.map { it.species.name + it.level }}")
    run {
        val kc = sim.critters.critters.groupingBy { it.sp }.eachCount()
        println("DEXLURE caught=${sim.critters.caught.size} milestones=${sim.critters.dexMiles} lureSpawns=${sim.critters.lureSpawns} keptKinds=${kc.size} keptTotal=${sim.critters.critters.size} maxDup=${kc.values.maxOrNull() ?: 0} top=${kc.entries.sortedByDescending { it.value }.take(3).map { com.jondude1.deskglass.sim.Dex.byId(it.key).name + "x" + it.value }}")
    }
    println("ALPHA seen=${sim.critters.alphaSeen} tamed=${sim.critters.alphaTamed} beaten=${sim.critters.alphaBeaten} lost=${sim.critters.alphaLosses} kept=${sim.critters.alphaKept()} lvs=${sim.critters.critters.filter { it.alpha }.map { it.species.name + it.level }}")
    println("SHINY seen=${sim.critters.shinySeen} hatched=${sim.critters.shinyHatched} kept=${sim.critters.shinyKept()} lvs=${sim.critters.critters.filter { it.shiny }.map { it.species.name + it.level }}")
    println("APIARY built=${sim.buildings.count { it.type == "apiary" }} harvests=${sim.honeyHarvests} jars=${sim.honeyJars} stings=${sim.beeStings} mead=${sim.meadFeasts} ripeNow=${sim.honeyReady()}")
    println("BATH built=${sim.buildings.count { it.type == "bath" }} soaks=${sim.soaks} heals=${sim.soakHeals} gossips=${sim.soakGossips} parties=${sim.soakParties}")
    println("STAGE built=${sim.buildings.count { it.type == "stage" }} shows=${sim.shows} grand=${sim.grandShows} audience=${sim.showAudience}")
    println("FOUNTAIN built=${sim.buildings.count { it.type == "fountain" }} wishes=${sim.fountainWishes} granted=${sim.fountainGranted} tier=${sim.fountainTier} wood=${sim.wood} stone=${sim.stone}")
    println("LOOM built=${sim.buildings.count { it.type == "loom" }} weaves=${sim.loomWeaves} cloaks=${sim.loomCloaks} hides=${sim.hides} adults=${sim.villagers.count { !sim.isChild(it) }}")
    println("YARD built=${sim.buildings.count { it.type == "yard" }} sessions=${sim.yardSessions} trained=${sim.critters.yardTrained} spars=${sim.yardSpars} levelUps=${sim.critters.yardLevelUps} xp=${sim.critters.yardXp}")
    println("CARAVAN markets=${sim.buildings.count { it.type == "market" }} caravans=${sim.caravans} finds=${sim.carFinds} ambushes=${sim.carAmbush} beaten=${sim.carBeaten} spent=${sim.carSpent} active=${sim.carActive}")
    println("VOYAGE docks=${sim.buildings.count { it.type == "dock" }} voyages=${sim.voyages} finds=${sim.voyFinds} serpents=${sim.voyScares} crowd=${sim.voyCrowd} active=${sim.voyActive} routes=${sim.voyRouteLens()}")
    println("LIGHTHOUSE built=${sim.buildings.count { it.type == "lighthouse" }} tends=${sim.beaconTends} nightWatch=${sim.beaconNightWatch} fogSails=${sim.beaconFogSails} nightSails=${sim.beaconNightSails} scares=${sim.beaconScares} guided=${sim.beaconGuided} voyages=${sim.voyages} lit=${sim.beaconLit()}")
    println("GUILD built=${sim.buildings.count { it.type == "guild" }} works=${sim.guildWorks} rank=${sim.guildRank} spent=${sim.guildSpent} wood=${sim.wood} stone=${sim.stone} age=${sim.age}")
    println("ARMORY built=${sim.buildings.count { it.type == "armory" }} forged=${sim.gearForged} sets L/B/S=${sim.gearLeather}/${sim.gearBronze}/${sim.gearSteel} blocks=${sim.gearBlocks} saved=${sim.gearSaved.toInt()} metal=${sim.metal} wood=${sim.wood} stone=${sim.stone} adults=${sim.villagers.count { it.job != "Child" }} age=${sim.age}")
    println("MUSEUM built=${sim.buildings.count { it.type == "museum" }} visits=${sim.museumVisits} talks=${sim.museumTalks} shown=${Integer.bitCount(sim.museumShown)}/${Integer.bitCount(sim.exhibitMask())} age=${sim.age}")
    println("MARKETDAY market=${sim.buildings.count { it.type == "market" }} visits=${sim.stallVisits} days=${sim.stallDays} deals=${sim.stallDeals} bargains=${sim.stallBargains}")
    println("OBSERVATORY built=${sim.buildings.count { it.type == "observatory" }} watches=${sim.obsWatches} forecasts=${sim.obsForecasts} hits=${sim.obsHits} prepared=${sim.obsPrepared} now=${sim.forecastKind}")
    println("HAIL storms=${sim.hailStorms} knocks=${sim.hailHits} shelterTicks=${sim.hailSaved} grainLost=${sim.hailCrop} childrenIce=${sim.hailIce} stormsAll=${sim.wxStats["storm"] ?: 0} minHp=${sim.villagers.minOfOrNull { it.hp }?.toInt()}")
    println("CROWS flocks=${sim.crowFlocks} shooed=${sim.crowsShooed} scared=${sim.crowsScared} lost=${sim.crowLoss} saved=${sim.crowSaved} scarecrows=${sim.buildings.count { it.type == "scarecrow" }}")
    println("SMOKE built=${sim.buildings.count { it.type == "smokehouse" }} batches=${sim.smokeBatches} big=${sim.smokeRations} cheers=${sim.smokeCheers} food=${sim.food}")
    println("KILN built=${sim.buildings.count { it.type == "kiln" }} fires=${sim.kilnFires} glazed=${sim.kilnGlazed} cheers=${sim.kilnCheers}")
    println("BELL built=${sim.buildings.count { it.type == "belfry" }} rings=${sim.bellRings} alarms=${sim.bellAlarms} reach=${sim.bellReach}")
    println("ARCHERY built=${sim.buildings.count { it.type == "range" }} shots=${sim.shotsFired} bullseyes=${sim.bullseyes} contests=${sim.archeryMatches} best=${sim.archeryBest}")
    println("ORCHARD built=${sim.buildings.count { it.type == "orchard" }} picks=${sim.fruitPicks} baskets=${sim.fruitBaskets} cider=${sim.ciderFeasts} falls=${sim.fruitFalls} ripeNow=${sim.fruitReady()}")
    println("PASTURE built=${sim.buildings.count { it.type == "pasture" }} visits=${sim.pastureVisits} eggs=${sim.eggsGathered} fleeces=${sim.fleeces} sheep=${sim.sheepNow()} hens=${sim.hensNow()}")
    println("LANDMARKS found=${sim.landmarksFound} kinds=${sim.landmarks.map { it.kind }} visits=${sim.landmarkVisits} heals=${sim.landmarkHeals} relics=${sim.landmarkRelics}")
    println("DOCK built=${sim.buildings.count { it.type == "dock" }} sails=${sim.sailTrips} dockCatch=${sim.dockCatch} bottles=${sim.bottlesFound}")
    println("NIGHTSKY stargazes=${sim.stargazed} shows(meteor,aurora,eclipse)=${sim.skyShows[1]},${sim.skyShows[2]},${sim.skyShows[3]} watches=${sim.skyWatches} wishes=${sim.skyWishes}")
    println("ICEFISH trips=${sim.iceTrips} fish=${sim.iceCatch} pike=${sim.icePike} together=${sim.iceTogether} swims=${sim.swims}")
    println("QUARRYAT ${sim.buildings.filter { it.type == "quarry" }.map { (it.built / 3600.0) }} now=${sim.time / 3600.0}")
    println("CATH tier=${sim.cathTier()}/5 work=${sim.cathWork} trips=${sim.cathTrips} bells=${sim.cathBells} site=${sim.cathX},${sim.cathY} jubilee=${sim.jubileeUntil > 0} wood=${sim.wood} stone=${sim.stone}")
    println("MILL built=${sim.buildings.count { it.type == "mill" }} grinds=${sim.millGrinds} flour=${sim.flourMade} loaves=${sim.loavesBaked} food=${sim.food}")
    println("WALL raised=${sim.wallsRaised} gates=${sim.buildings.count { it.type == "gate" }} done=${sim.wallDone} planLeft=${sim.wallPlan.size} holdSecs=${"%.0f".format(sim.wallHoldSecs)} stone=${sim.stone}")
    println("QUARRY built=${sim.buildings.count { it.type == "quarry" }} hauls=${sim.quarryHauls} stone=${sim.quarryStone} veins=${sim.quarryVeins} storeStone=${sim.stone}")
    println("TRIALS met=${sim.trialsMetCount()}/6 announced=${Integer.bitCount(sim.trialMask)} hints=${sim.trialsHinted} calls=${sim.legendCalls} list=${(57..62).map { sim.legendTrial(it).first }}")
    println("SHRINE built=${sim.buildings.count { it.type == "shrine" }} vigils=${sim.vigils} calls=${sim.legendCalls} points=${sim.vigilPoints}/${sim.vigilNeed()} legendsOwned=${sim.critters.legendsOwned()}")
    println("CAMP up=${sim.campUp} razed=${sim.campsRazed} hits=${sim.campHits} sorties=${sim.campSorties} hp=${sim.campHp.toInt()}/${sim.campMax.toInt()}")
    println("RANCH built=${sim.buildings.count { it.type == "ranch" }} visits=${sim.ranchVisits} tended=${sim.critters.ranchTended} residents=${sim.ranchResidents()} atRanch=$atRanch")
    println("BALLISTA built=${sim.buildings.count { it.type == "ballista" }} shots=${sim.ballistaShots} kills=${sim.ballistaKills} towers=${sim.buildings.count { it.type == "tower" }}")
    println("HEALTH hurtShare=%.2f%% infirmaries=${sim.buildings.count { it.type == "infirmary" }} recoveries=${sim.recoveries}".format(100.0 * hurtSamples / samples.coerceAtLeast(1)))
    println("STATUES count=${sim.statues.size} cheers=${sim.statueCheers} tiers=${sim.statues.map { it.tier }} heroes=${sim.statues.map { it.name + ":" + it.kind }}")
    println("GRAVES buried=${sim.buried} onMap=${sim.graves.size} mourn=${sim.mournVisits} site=${sim.graveX},${sim.graveY} names=${sim.graves.map { it.name + ":" + it.cause }}")
    println("DEATHS causes=${sim.deathCauses}")
    println("SNACKS emergencyBites=${sim.hungerSnacks}")
    println("SELFDEF ${sim.selfDefence}")
    println("DEATHNOTES ${sim.deathNotes}")
    println("RAISED buildings=${sim.raised} sitesNow=${sim.buildings.count { sim.time - it.built < 9.0 }}")
    println("TOTEM built=${sim.buildings.count { it.type == "totem" }} visits=${sim.totemVisits} omens=${sim.omens} kinds=${sim.buildings.map { it.type }.toSet().size}")
    println("MENTOR lessons=${sim.lessonsGiven} apprenticesMade=${sim.apprenticesMade} pending=${sim.apprentice.size} kids=${sim.villagers.count { sim.isChild(it) }}")

    println("KIDGAMES puddles=${sim.puddleJumps} jars=${sim.fireflyJars} hide=${sim.hideSeekGames} found=${sim.hideFinds}")
    println("BIRTHDAY parties=${sim.birthdays} cakes=${sim.birthdayCakes} gifts=${sim.birthdayGifts}")
    println("COMING ceremonies=${sim.comingAges} guests=${sim.comingGuests} gifts=${sim.comingGifts} parentsCheered=${sim.comingParents} crownedNow=${sim.villagers.count { sim.crowned(it) }} gift-skilled=${sim.villagers.count { !sim.isChild(it) && sim.skillLines(it).isNotEmpty() }}")
    println("HEARTH games=${sim.hearthGames} matches=${sim.hearthMatches} mends=${sim.hearthMends} kidPlays=${sim.hearthPlays} share=${"%.2f".format(100.0*(actHist["hearth_game"]?:0)/samples)}% huddleShare=${"%.2f".format(100.0*(actHist["huddle"]?:0)/samples)}%")
    println("VISITS trips=${sim.friendVisits} met=${sim.friendVisitsMet} wanderShare=${"%.2f".format(100.0*(actHist["wander"]?:0)/samples)}% visitShare=${"%.2f".format(100.0*(actHist["visit_friend"]?:0)/samples)}%")
    println("SHROOMS picked=${sim.mushPicked} glowcaps=${sim.glowcapsFound} stews=${sim.mushStews} nowOut=${sim.seasonProps.count { it.kind == "shroom" || it.kind == "glowcap" }}")
    println("SEASONFUN snowmen=${sim.snowmenBuilt} leafPiles=${sim.leafPilesRaked} kites=${sim.kitesFlown} propsNow=${sim.seasonProps.map { it.kind }} total=${sim.snowmenBuilt + sim.leafPilesRaked + sim.kitesFlown}")
    println("PETS dogs=${sim.pets.size} adopted=${sim.dogsAdopted} walks=${sim.dogWalks} barks=${sim.dogBarks} onMap=${sim.fauna.animals.count { it.kind == "dog" }} names=${sim.pets.map { it.name + ":" + it.bond }}")
    println("FETE held=${sim.fetesHeld} revels=${sim.feteRevels} kinds=${sim.feteKinds} on=${sim.feteOn}")
    run {
        val adults = sim.villagers.filter { !sim.isChild(it) }
        val lvls = adults.flatMap { v -> listOf("wood","stone","farm","fish","build","combat","lore","craft","hunt").map { sim.skillLevel(v, it) } }
        val best = adults.maxOfOrNull { v -> listOf("wood","stone","farm","fish","build","combat","lore","craft","hunt").maxOf { sim.skillLevel(v, it) } } ?: 0
        println("FIRE started=${sim.firesStarted} doused=${sim.firesDoused} burnedOut=${sim.fireBurnedOut} douseTrips=${sim.fireDouses} woodLost=${sim.fireLoss} blazesNow=${sim.blazes.size} scorch=${sim.scorches.size}")
        println("DROUGHT started=${sim.droughts} ended=${sim.droughtsEnded} byRain=${sim.droughtRainEnds} waterings=${sim.droughtWaterings} savedHarvests=${sim.droughtSaved} witheredHarvests=${sim.droughtWithered} wells=${sim.buildings.count { it.type == "well" }} now=${sim.drought()}")
        run { val c = Sky.census(); println("SKY meanCover=${"%.2f".format(c[0])} beamShareOfDay=${"%.1f".format(c[1] * 100)}% meanClouds=${"%.1f".format(c[2])} overcastShare=${"%.1f".format(c[3] * 100)}% nowCover=${"%.2f".format(Sky.cover(sim))} nowClouds=${Sky.cloudCount(Sky.cover(sim))} layers(before->after): weather-linked cover 0->1, lumpy shadows 4 fixed ovals->2-11 by cover, sunlit patches 0->3, overcast wash 0->1, sunbeams 0->5 with motes") }
        run { val c = Wind.census(sim.time); println("WIND gustShare=${"%.1f".format(c[0] * 100)}% gusts=${c[1].toInt()} longest=${c[2].toInt()}s peak=${"%.2f".format(c[3])} cargoNow=${Wind.cargoName(Wind.cargo(sim))} layers(before->after): wind streaks 0->1, airborne kinds 0->5 (leaves,petals,seeds,snow,dust), smoke/banner/tree gust response 0->3") }
        run { val c = Lamps.census(sim); println("LAMPS posts=${c[0]} glowingBuildings=${c[1]} (was ${c[3]}) glowKinds=${c[2]} of ${Lamps.lightKinds()} lit kinds at night (was 5)") }
        run { val c = WaterLife.census(sim); println("WATERLIFE waterTiles=${c[0]} ducks=${c[1]} ducklings=${c[2]} lilies=${c[3]} reedTiles=${c[4]} foamEdges=${c[5]} leapingSpots=${c[6]} layers=twinkle,moonglitter,ice,ships(4) -> +ducks,ducklings,lilies,reeds,foam,leaps,rainrings(11)") }
        println("SKILLS ups=${sim.skillUps} masters=${sim.skillMasters} bonusLoads=${sim.skillBonusLoads} skilledPairs(lv>=3)=${lvls.count { it >= 3 }} lv5+=${lvls.count { it >= 5 }} best=$best titles=${sim.villagers.sumOf { it.titles.size }}")
    }
    println("GLORY golden=${sim.goldenAt} legacy=${sim.legacy().count { it.second }}/${sim.legacy().size} decrees=${sim.decrees} kinds=${sim.decreeKinds} gildings=${sim.gildings} now=${sim.decreeKind}")
    println("COUNCIL held=${sim.councilsHeld} seats=${sim.councilSeats} policies=${sim.policyKinds} steered=${sim.policyActs} won=${sim.policiesWon} lost=${sim.policiesLost} now=${sim.policy} on=${sim.councilOn}")
    run { val tv = sim.buildings.count { it.type == "tavern" }; println("TAVERN built=$tv visits=${sim.tavernVisits} dice=${sim.tavDice} wrestles=${sim.tavWrestles} tales=${sim.tavTales} songs=${sim.tavSongs} nights=${sim.tavNights}") }
    run { println("RESEARCH studies=${sim.studyCount} points=${sim.research} libs=${sim.buildings.count { it.type == "library" }} techs=${sim.techs.size}/${sim.allTechs.size} higher=${sim.higherTechs()} age=${sim.age} list=${sim.techs.joinToString(",")}") }
    println("SIGHTS samples=$sightSamples withSight=$sightAny max=$sightMax kinds=${sightKinds.size} $sightKinds")
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
    println("FIELD fights none/rain/sun/snow/night/storm=${Battle.fieldFights.toList()} boosted hits=${Battle.fieldBoosts} dulled hits=${Battle.fieldWeakens}")
    run {
        val byN = cw.critters.groupingBy { it.nature.name }.eachCount().toSortedMap()
        val sp = cw.critters.groupBy { it.sp to it.level }.filter { it.value.size > 1 }.values
        val spread = if (sp.isEmpty()) 0 else sp.maxOf { g -> g.maxOf { it.atk + it.def + it.spd } - g.minOf { it.atk + it.def + it.spd } }
        val distinct = cw.critters.map { Triple(it.atk, it.def, it.spd) }.distinct().size
        println("NATURE kept=${cw.critters.size} kinds=${byN.size} $byN distinctStatLines=$distinct sameSpeciesSpread=$spread")
    }
    run {
        val cw = sim.critters
        val rk = (0..4).associate { Rank.name(it).ifEmpty { "none" } to cw.critters.count { c -> c.rank == it } }
        println("RANKS kept=${cw.critters.size} byRank=$rk promotions=${cw.rankUps.toList()} topWins=${cw.critters.map { it.wins }.sortedDescending().take(5)} veterans=${cw.critters.count { it.rank > 0 }}")
    }
    run {
        val cw = sim.critters
        val st = cw.charmStats
        val kinds = (1..Charm.COUNT).associate { Charm.name(it) to cw.critters.count { c -> c.charm == it } }
        fun pct(w: Int, f: Int) = if (f == 0) "n/a" else "${w * 100 / f}%"
        println("CHARMS found=${cw.charmsFound} given=${cw.charmsGiven} spare=${cw.charmsSpare} held=${cw.critters.count { it.charm != 0 }}/${cw.critters.size} $kinds fired=${Battle.abilCount.filterKeys { it in listOf("Sunberry", "Guard Pebble", "Fang Charm", "Lucky Clover") }} winrate with=${pct(st[1], st[0])}(${st[0]}) without=${pct(st[3], st[2])}(${st[2]})")
    }
    run {
        val cw = sim.critters
        val w = cw.senseWon; val st = cw.senseStarted
        fun r(i: Int) = if (st[i] == 0) "n/a" else "${w[i] * 100 / st[i]}%(${st[i]})"
        println("SENSE scouts=${cw.senseScouts} declined=${cw.senseDeclined} swaps=${cw.senseSwaps} lessons=${cw.senseLessons} actualWin easy=${r(0)} even=${r(1)} risky=${r(2)} longshot=${r(3)}")
    }
    println("ABIL talents fired=${Battle.abilCount.values.sum()} kinds=${Battle.abilCount.size} ${Battle.abilCount.toSortedMap()}")
    sim.log.filter { it.contains("aider") || it.contains("ogre") || it.contains("wraith") || it.contains("drake") || it.contains("warlord") || it.contains("golem") || it.contains("shaman") || it.contains("Golden") }.takeLast(8).forEach { println("  LOG $it") }
    println("ROSTER kept=${sim.critters.critters.size}/${sim.critters.maxKept()} caught=${sim.critters.caught.size} seen=${sim.critters.seen.size} released=${sim.critters.rotated} releasedForNew=${sim.critters.rotatedForNew} dexMiles=${sim.critters.dexMiles}")
    run { val cs = sim.critters.critters; val lv = cs.map { it.level }.sortedDescending(); println("CRITLEVELS n=${cs.size} sum=${lv.sum()} max=${lv.firstOrNull() ?: 0} top5=${lv.take(5)} lv10+=${lv.count { it >= 10 }} lv20+=${lv.count { it >= 20 }} lv30+=${lv.count { it >= 30 }} stage3=${cs.count { it.species.stage >= 2 }}") }
    println("LEGENDS wild=$legSeen enc=$legEnc maxCompLv=${sim.critters.critters.maxOfOrNull{it.level}}")
    println("LEGLOG " + sim.log.filter{it.contains("LEGENDDBG")||it.contains("pact")||it.contains("legend")}.takeLast(6))
    println("RESULTS " + sim.log.filter{it.contains("lost to")||it.contains("defeat")}.size)
}
