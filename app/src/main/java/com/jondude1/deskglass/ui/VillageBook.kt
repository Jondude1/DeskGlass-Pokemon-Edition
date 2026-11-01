package com.jondude1.deskglass.ui

import androidx.compose.foundation.layout.Arrangement
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

private val BOOK_TABS = listOf("Deeds", "Lore", "Heroes", "Records", "Annals")

/** The Village Book: every deed (done and still to do), the heroes, lifetime records and the long chronicle. */
@Composable
fun VillageBookScreen(sim: CivSim, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
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
                2 -> heroesPage(sim)
                3 -> recordsPage(sim)
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
    if (libs == 0) Text("Build a library (Town Age) and villagers will study there to make discoveries.", color = Glass.InkFaint, fontSize = 13.sp)
    val next = sim.nextTech()
    if (next != null) {
        val pct = (sim.research * 100 / next.need).coerceIn(0, 100)
        SectionLabel("RESEARCHING")
        Text("${next.name} · $pct% · costs ${next.cost}", color = Glass.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("${sim.research}/${next.need} lore. ${next.text}", color = Glass.InkDim, fontSize = 12.sp)
    }
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
        "War" to "${sim.raidsRepelled} raids beaten · ${sim.ogresSlain} ogres · ${sim.bossesSlain} bosses · ${sim.warlordsSlain} warlords · ${sim.golemsSlain} golems · ${sim.lichesSlain} liches · ${sim.reaversSlain} sea reavers · ${sim.shipsSeized} longships burned",
        "Fetes" to "${sim.fetesHeld} held · ${sim.feteRevels} dances · ${sim.feteKinds.size} of 4 kinds seen" + (if (sim.feteOn) " · the ${sim.feteTitle()} is on now" else ""),
        "Fallen stars" to "${sim.starsFallen} fell · ${sim.starShardsGot} starmetal shards gathered" + (if (sim.starReady()) " · ${sim.starShards} shards still glitter in a crater" else ""),
        "Bandit camps" to "${sim.campsRazed} razed · ${sim.campHits} blows struck · ${sim.campSorties} raids sent" + (if (sim.campUp) " · a camp stands, ${(sim.campHp / sim.campMax * 100).toInt()}% strength" else ""),
        "Rifts" to "${sim.expWins} of ${sim.expeditions} expeditions cleared · ${sim.roomsCleared} rooms · ${sim.relics} relics",
        "Critters" to "${cw.critters.size} kept · ${cw.caught.size} species summoned · ${cw.seen.size} seen · ${cw.tourneys} tournaments",
        "Graveyard" to (if (sim.buried == 0) "no one has been laid to rest yet" else "${sim.buried} laid to rest · ${sim.mournVisits} visits with flowers" + (sim.graves.lastOrNull()?.let { " · latest: ${it.name} the ${it.job.lowercase()}, ${it.ageYears}, ${it.cause}" } ?: "")),
        "Builders" to "${sim.buildings.size} buildings · ${sim.titlesEarned} titles earned",
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
