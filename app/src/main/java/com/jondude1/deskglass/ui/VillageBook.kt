package com.jondude1.deskglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.sim.CivSim
import kotlinx.coroutines.delay

private val BOOK_TABS = listOf("Deeds", "Lore", "Folk", "Heroes", "Records", "Days", "Annals")

/** The Village Book: every deed (done and still to do), the heroes, lifetime records and the long chronicle. */
@Composable
fun VillageBookScreen(sim: CivSim, onBack: () -> Unit, onFollow: (Int) -> Unit = {}) {
    var tab by remember { mutableIntStateOf(0) }
    var folkSort by remember { mutableIntStateOf(0) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            tick++
        }
    }
    @Suppress("UNUSED_VARIABLE") val t = tick
    Column(Modifier.fillMaxSize()) {
        BackBar("Village Book · ${sim.renownTitle()}", onBack)
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in BOOK_TABS.indices) {
                GlassButton(BOOK_TABS[i], { tab = i }, filled = tab == i)
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                0 -> deedsPage(sim)
                1 -> lorePage(sim)
                2 -> folkPage(sim, folkSort, { folkSort = it }, onFollow)
                3 -> heroesPage(sim)
                4 -> recordsPage(sim)
                5 -> almanacPage(sim)
                else -> annalsPage(sim)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Glass.InkFaint, fontSize = 13.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun deedsPage(sim: CivSim) {
    val all = sim.allDeeds
    val done = all.filter { it.id in sim.deeds }
    val open = all.filter { it.id !in sim.deeds }
    Text("${done.size} of ${all.size} deeds done · ${sim.renownTitle()}", color = Color(0xFF9BE3BC), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    SectionLabel("STILL TO DO")
    if (open.isEmpty()) Text("Every deed is done. The village is legend.", color = Glass.InkDim, fontSize = 13.sp)
    for (d in open) {
        Column {
            Text("○ ${d.title}", color = Glass.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("${d.text}  Reward: ${d.reward}", color = Glass.InkFaint, fontSize = 12.sp)
        }
    }
    SectionLabel("DONE")
    if (done.isEmpty()) Text("Nothing yet. Give the village time.", color = Glass.InkFaint, fontSize = 13.sp)
    for (d in done) {
        Column {
            Text("✔ ${d.title}", color = Color(0xFF9BE3BC), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(d.text, color = Glass.InkDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun lorePage(sim: CivSim) {
    val libs = sim.buildings.count { it.type == "library" }
    Text("${sim.techs.size} of ${sim.allTechs.size} discoveries · ${sim.research} lore gathered · ${sim.studyCount} study sessions", color = Color(0xFF9BE3BC), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    if (libs == 0) Text("Build a library (Hut Age) and villagers will study there to make discoveries.", color = Glass.InkFaint, fontSize = 13.sp)
    val next = sim.nextTech()
    if (next != null) {
        val pct = (sim.research * 100 / next.need).coerceIn(0, 100)
        SectionLabel("RESEARCHING")
        Text("${next.name} · $pct% · costs ${next.cost}", color = Glass.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("${sim.research}/${next.need} lore. ${next.text}", color = Glass.InkDim, fontSize = 12.sp)
    }
    if (next == null && libs > 0 && sim.techs.size < sim.allTechs.size) Text("The scholars have learned all they can for now. Higher learning opens in the Town Age.", color = Glass.InkFaint, fontSize = 13.sp)
    SectionLabel("DISCOVERIES")
    for (t in sim.allTechs) {
        val done = sim.hasTech(t.id)
        Column {
            Text((if (done) "✔ " else "○ ") + t.name, color = if (done) Color(0xFF9BE3BC) else Glass.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(t.text + (if (done) "" else "  (${t.need} lore, ${t.cost})"), color = if (done) Glass.InkDim else Glass.InkFaint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun heroesPage(sim: CivSim) {
    val titled = sim.villagers.filter { it.titles.isNotEmpty() }
    Text("${titled.size} villager(s) have earned titles · ${sim.statues.size}/10 statues", color = Glass.InkDim, fontSize = 14.sp)
    SectionLabel("TITLED VILLAGERS")
    if (titled.isEmpty()) Text("No titles yet. Deeds in battle, rifts, the river and the fireside earn them.", color = Glass.InkFaint, fontSize = 13.sp)
    for (v in titled) {
        Column {
            Text("★ ${v.name} ${v.titles.joinToString(", ")}", color = Glass.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("${v.job.lowercase()} · age ${sim.ageYears(v)} · feeling ${sim.mood(v)}", color = Glass.InkFaint, fontSize = 12.sp)
        }
    }
    SectionLabel("HEROES' ROW")
    if (sim.statues.isEmpty()) Text("No statue stands yet.", color = Glass.InkFaint, fontSize = 13.sp)
    for (s in sim.statues) {
        Text("${s.name} ${s.title}", color = Glass.InkDim, fontSize = 14.sp)
    }
}

@Composable
private fun recordsPage(sim: CivSim) {
    val cw = sim.critters
    val rows = listOf(
        "Village" to "${sim.ageName()} · day ${sim.day()} · ${sim.villagers.size}/${sim.popCap()} villagers · ${sim.babiesBorn} babies born · ${sim.weds} weddings",
        "War" to "${sim.raidsRepelled} raids beaten · ${sim.ogresSlain} ogres · ${sim.bossesSlain} bosses · ${sim.warlordsSlain} warlords · ${sim.golemsSlain} golems · ${sim.lichesSlain} liches · ${sim.reaversSlain} sea reavers · ${sim.trollsSlain} trolls slain · ${sim.ramsSlain} siege rams smashed · ${sim.trollsStoned} turned to stone · ${sim.harpiesSlain} harpies slain (${sim.flocksScattered} flocks scattered) · ${sim.spidersSlain} spiders slain (${sim.websSpun} webs spun) · ${sim.wormsSlain} burrowing worms slain (${sim.wormErupts} eruptions, ${sim.wormHits} flung) · ${sim.scorpionsSlain} sand scorpions slain (${sim.scorpionStings} stings, ${sim.scorpionSwarms} nests wiped out) · ${sim.toadsSlain} bog toads slain (${sim.toadLashes} tongue lashes, ${sim.toadChoruses} choruses silenced) · ${sim.stagsSlain} antler stags slain (${sim.stagCharges} charges, ${sim.stagGores} gorings, ${sim.stagMisses} missed) · ${sim.stormlingsSlain} stormlings slain (${sim.stormlingBolts} bolts, ${sim.stormlingDodges} dodged) · ${sim.shipsSeized} longships burned",
        "Birthdays" to (if (sim.birthdays == 0) "no birthday has been kept yet" else "${sim.birthdays} birthdays · ${sim.birthdayCakes} cakes · ${sim.birthdayGifts} keepsakes"),
        "Tavern" to (if (sim.tavernVisits == 0) "no one has raised a cup yet" else "${sim.tavernVisits} visits · ${sim.tavDice} dice games · ${sim.tavWrestles} arm-wrestles · ${sim.tavTales} tales · ${sim.tavSongs} songs · ${sim.tavNights} lantern nights"),
        "Hailstorms" to (if (sim.hailStorms == 0) "no hail has fallen yet" else "${sim.hailStorms} hailstorms · ${sim.hailHits} knocks taken · ${sim.hailSaved} sheltering ticks · ${sim.hailCrop} grain flattened · ${sim.hailIce} children scooped stones"),
        "Night sky shows" to (if (sim.skyShows.sum() == 0) "the sky has not put on a show yet" else "${sim.skyShows[1]} meteor showers · ${sim.skyShows[2]} auroras · ${sim.skyShows[3]} blood-moon eclipses · ${sim.skyWatches} stayed up to watch · ${sim.skyWishes} wishes made"),

        "Divine favour" to (if (sim.divine.totalUses() == 0) "${sim.divine.favour.toInt()} of 100 favour stored · no power called down yet" else "${sim.divine.totalUses()} powers called (${sim.divine.kinds()} of 5 kinds) · ${sim.divine.smitten} foes struck, ${sim.divine.smiteKills} slain by bolts · ${sim.divine.healed} wounded mended · ${sim.divine.favour.toInt()} of 100 favour stored"),
        "Fetes" to "${sim.fetesHeld} held · ${sim.feteRevels} dances · ${sim.feteKinds.size} of 4 kinds seen" + (if (sim.feteOn) " · the ${sim.feteTitle()} is on now" else ""),
        "Age of Gold" to (if (sim.goldenAt < 0.0) "the Golden Age has not begun yet" else "${sim.decrees} decrees (${sim.decreeKinds.size} of 5 kinds) · ${sim.gildings} grand gildings" + (if (sim.decreeOn()) " · ${sim.decreeTitle()} today" else "")),
        "Village council" to (if (sim.councilsHeld == 0) "no council has met yet" else "${sim.councilsHeld} held · ${sim.councilSeats} seats taken · ${sim.policiesWon} plans met, ${sim.policiesLost} fell short · ${sim.policyActs} choices steered" + (if (sim.policy.isNotEmpty()) " · priority now: ${sim.policyTitle()} (${sim.policyBlurb()})" else "")),
        "Fallen stars" to "${sim.starsFallen} fell · ${sim.starShardsGot} starmetal shards gathered" + (if (sim.starReady()) " · ${sim.starShards} shards still glitter in a crater" else ""),
        "Bandit camps" to "${sim.campsRazed} razed · ${sim.campHits} blows struck · ${sim.campSorties} raids sent" + (if (sim.campUp) " · a camp stands, ${(sim.campHp / sim.campMax * 100).toInt()}% strength" else ""),
        "Rifts" to "${sim.expWins} of ${sim.expeditions} expeditions cleared · ${sim.roomsCleared} rooms · ${sim.relics} relics · ${sim.sideRooms} side chambers (${sim.sideVaults} vaults, ${sim.sideTraps} traps) · ${sim.riftRallies} rallies",
        "Rift realms" to "${sim.realmWins.count { it > 0 }} of 5 realms cleared · " + (0 until 5).joinToString(" · ") { "${sim.realmName(it)} ${sim.realmWins[it]}/${sim.realmRuns[it]}" } + " · ${sim.realmHazards} hazards",
        "Critters" to "${cw.critters.size} kept · ${cw.caught.size} species summoned · ${cw.seen.size} seen · ${cw.tourneys} tournaments",
        "Graveyard" to (if (sim.buried == 0) "no one has been laid to rest yet" else "${sim.buried} laid to rest · ${sim.mournVisits} visits with flowers" + (sim.graves.lastOrNull()?.let { " · latest: ${it.name} the ${it.job.lowercase()}, ${it.ageYears}, ${it.cause}" } ?: "")),
        "Town wall" to (if (sim.wallsRaised == 0) "not begun (masons start in the Village Age)" else "${sim.wallsRaised} pieces raised" + (if (sim.wallDone) " · the ring is closed, gates open on every side" else " · still growing") + " · foes lost ${sim.wallHoldSecs.toInt()}s at the ramparts"),
        "Great Cathedral" to (if (sim.cathX < 0) "not begun (the village starts it in the City Age with 16+ people and spare wood and stone)" else "tier ${sim.cathTier()} of 5" + (if (sim.cathTier() in 1..4) " · next: ${sim.cathTierName(sim.cathTier() + 1)}" else "") + " · ${sim.cathTrips} loads laid" + (if (sim.cathTier() >= 5) " · bell has tolled ${sim.cathBells} dawns" else "")),
        "Builders" to "${sim.buildings.count { it.type != "wall" && it.type != "gate" }} buildings · ${sim.titlesEarned} titles earned",
        "Stores" to "food ${sim.food} · wood ${sim.wood} · stone ${sim.stone} · hides ${sim.hides} · metal ${sim.metal}",
    )
    for (r in rows) {
        Column {
            Text(r.first, color = Glass.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(r.second, color = Glass.InkDim, fontSize = 13.sp)
        }
    }
    SectionLabel("LEGACY GOALS")
    for (g in sim.legacy()) {
        Text((if (g.second) "✔ " else "○ ") + g.first, color = if (g.second) Color(0xFF9BE3BC) else Glass.InkFaint, fontSize = 13.sp)
    }
}

@Composable
private fun annalsPage(sim: CivSim) {
    Text("${sim.chronicle.size} entries, newest first", color = Glass.InkDim, fontSize = 14.sp)
    if (sim.chronicle.isEmpty()) Text("The pages are blank.", color = Glass.InkFaint, fontSize = 13.sp)
    for (line in sim.chronicle.toList().reversed()) {
        Text(line, color = Glass.InkDim, fontSize = 13.sp)
    }
}

@Composable
private fun almanacPage(sim: CivSim) {
    val pages = sim.almanac.pages.toList().reversed()
    Text("${pages.size} day(s) written up, newest first. A new page is written at every dawn.", color = Glass.InkDim, fontSize = 14.sp)
    if (pages.isEmpty()) Text("The first page is written when the next day dawns.", color = Glass.InkFaint, fontSize = 13.sp)
    for (p in pages) {
        val f = p.split("|")
        if (f.size < 4) continue
        Column {
            Text("Day ${f[0]} · ${f[1]} · ${f[2]}", color = Glass.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(f[3].split(";").joinToString("  ·  "), color = Glass.InkDim, fontSize = 12.sp)
        }
    }
}


/** A thin filled bar for one need (0..100); used by the Folk page and the follow panel. */
@Composable
fun NeedBar(label: String, value: Float, color: Color, barWidth: Dp = 70.dp) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Glass.InkFaint, fontSize = 10.sp, modifier = Modifier.width(34.dp))
        Box(Modifier.width(barWidth).height(6.dp).background(Color(0x33FFFFFF), RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth((value / 100f).coerceIn(0.02f, 1f)).height(6.dp).background(color, RoundedCornerShape(3.dp)))
        }
    }
}

/** How much a villager needs help right now, 0 (fine) .. 100 (urgent); used to sort the Folk page. */
private fun neediness(v: com.jondude1.deskglass.sim.Villager): Float =
    maxOf(100f - v.hp, v.hunger, 100f - v.energy, 100f - v.happiness)

@Composable
private fun folkPage(sim: CivSim, sort: Int, setSort: (Int) -> Unit, onFollow: (Int) -> Unit) {
    val folk = sim.villagers.toList()
    val kids = folk.count { sim.isChild(it) }
    val avgHappy = if (folk.isEmpty()) 0 else (folk.sumOf { it.happiness.toDouble() } / folk.size).toInt()
    val hurt = folk.count { it.hp < 60f }
    val hungry = folk.count { it.hunger > 70f }
    Text("${folk.size} villagers · $kids children · average happiness $avgHappy" + (if (hurt > 0) " · $hurt hurt" else "") + (if (hungry > 0) " · $hungry hungry" else ""), color = Color(0xFF9BE3BC), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val jobs = folk.groupingBy { it.job }.eachCount().entries.sortedByDescending { it.value }
    Text(jobs.joinToString(" · ") { "${it.value} ${it.key.lowercase()}" }, color = Glass.InkDim, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton("By job", { setSort(0) }, filled = sort == 0)
        GlassButton("Neediest", { setSort(1) }, filled = sort == 1)
        GlassButton("Eldest", { setSort(2) }, filled = sort == 2)
    }
    val rows = when (sort) {
        1 -> folk.sortedByDescending { neediness(it) }
        2 -> folk.sortedByDescending { it.born.let { b -> -b } }
        else -> folk.sortedWith(compareBy({ it.job }, { it.name }))
    }
    for (v in rows) {
        Column(Modifier.fillMaxWidth().background(Color(0x14FFFFFF), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.width(210.dp)) {
                    Text((if (v.titles.isNotEmpty()) "★ " else "") + v.name, color = Glass.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text((if (sim.isChild(v)) "child" else v.job.lowercase()) + " · age ${sim.ageYears(v)} · feeling ${sim.mood(v)}", color = Glass.InkFaint, fontSize = 12.sp)
                }
                GlassButton("Follow", { onFollow(v.id) })
            }
            Text("Doing: " + (if (v.action.isEmpty()) "thinking" else v.action.replace('_', ' ')) + (sim.partnerOf(v)?.let { " · married to ${it.name}" } ?: "") + (sim.feudWith(v)?.let { " · feuding with ${it.name}" } ?: ""), color = Glass.InkDim, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    NeedBar("health", v.hp, if (v.hp < 40f) Color(0xFFFF6B5E) else Color(0xFF7BD88F))
                    NeedBar("fed", 100f - v.hunger, if (v.hunger > 70f) Color(0xFFFF9E57) else Color(0xFFE6C16A))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    NeedBar("rest", v.energy, if (v.energy < 25f) Color(0xFFB07CFF) else Color(0xFF7CB8FF))
                    NeedBar("cheer", v.happiness, if (v.happiness < 30f) Color(0xFFFF8AA8) else Color(0xFFFFD84A))
                }
            }
            if (v.thought.isNotBlank()) Text("“${v.thought}”", color = Glass.InkFaint, fontSize = 12.sp)
        }
    }
}
