package com.jondude1.deskglass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.sim.CType
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.Dex

/** The Critterdex: every critter the village keeps, and all fifty species by evolution line. */
@Composable
fun CritterDexScreen(sim: CivSim, onBack: () -> Unit) {
    val cart = remember { CritterArt() }
    val cw = sim.critters
    Column(Modifier.fillMaxSize()) {
        BackBar("Critterdex ${cw.caught.size}/${Dex.COUNT}", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Seen ${cw.seen.size} · summoned ${cw.caught.size} of ${Dex.COUNT}", color = Glass.InkDim, fontSize = 14.sp)

            Text("SUMMONERS", color = Glass.InkFaint, fontSize = 13.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 8.dp))
            val gifted = sim.villagers.filter { cw.gifted(it) }
            val waiting = sim.villagers.count { !cw.mageOf(it).revealed }
            if (gifted.isEmpty()) {
                Text("No one in the village has the gift yet.", color = Glass.InkFaint, fontSize = 13.sp)
            }
            Text(
                "Scrolls: ${cw.scrolls[0]} common · ${cw.scrolls[1]} fine · ${cw.scrolls[2]} rare" +
                    (if (waiting > 0) " · $waiting child(ren) await the gift test" else ""),
                color = Glass.InkDim, fontSize = 12.sp,
            )
            for (v in gifted.sortedByDescending { cw.mageOf(it).lv }) {
                val m = cw.mageOf(v)
                Column {
                    val teacher = sim.villagers.firstOrNull { it.id == m.mentor }
                    Text("${v.name}  ·  Summoner Lv ${m.lv}" + (if (teacher != null) "  ·  taught by ${teacher.name}" else ""), color = Glass.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Mana ${m.mana.toInt()}/${m.maxMana.toInt()} · bonded levels ${cw.budgetUsed(v)}/${m.capacity} · XP ${m.xp}/${m.xpNeed()}",
                        color = Glass.InkDim, fontSize = 12.sp,
                    )
                }
            }
            Text("IN THE VILLAGE", color = Glass.InkFaint, fontSize = 13.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 8.dp))
            if (cw.critters.isEmpty()) {
                Text("No critters yet. Wild ones slip through rifts; a gifted villager with a scroll can answer.", color = Glass.InkFaint, fontSize = 13.sp)
            }
            for (c in cw.critters.toList().sortedByDescending { it.level }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sprite(cart, c.sp, 56)
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (c.companion) "★ " else "") + c.name + "  Lv ${c.level}",
                            color = Glass.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        )
                        val owner = cw.ownerName(c)
                        Text(
                            c.species.typeText + (if (owner.isNotEmpty()) (if (c.companion) " · bonded, out with $owner" else " · infused in ${owner}'s palm") else " · helps around the village") +
                                (if (cw.isTired(c)) " · resting" else ""),
                            color = Glass.InkDim, fontSize = 13.sp,
                        )
                        Text(
                            "HP ${c.hp}/${c.maxHp} · XP ${c.xp}/${Dex.xpNeed(c.level)} · ${c.wins} wins · " + c.species.t1.job,
                            color = Glass.InkFaint, fontSize = 12.sp,
                        )
                    }
                }
            }

            Text("ALL SPECIES", color = Glass.InkFaint, fontSize = 13.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 14.dp))
            for (line in Dex.lines()) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    for (s in line) {
                        val known = s.id in cw.caught
                        val seen = s.id in cw.seen
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Sprite(cart, s.id, 64, if (known) null else if (seen) Color(0xFF6C7488) else Color(0xFF1B2030))
                            Text(
                                if (known || seen) s.name else "???",
                                color = if (known) Glass.Ink else Glass.InkDim, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (known || seen) s.typeText else "",
                                color = if (known) Color(s.t1.color) else Glass.InkFaint, fontSize = 11.sp,
                            )
                            if (known && s.evolveTo > 0) {
                                Text("evolves at Lv ${s.evolveLv}", color = Glass.InkFaint, fontSize = 10.sp)
                            }
                        }
                    }
                    // keep lines of two lined up with lines of three
                    for (i in line.size until 3) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Sprite(cart: CritterArt, id: Int, dp: Int, tint: Color? = null) {
    // bigger species fill more of the box, small ones stay small, as they are in the village
    val img = if (tint == null) cart.frame(id, 0) else cart.white(id)
    val scale = (img.width / 2f) / 30f
    val side = (dp * (0.45f + 0.55f * scale.coerceAtMost(1f))).toInt().coerceAtLeast(24)
    Image(
        bitmap = img,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
        colorFilter = if (tint != null) ColorFilter.tint(tint) else null,
        modifier = Modifier.size(dp.dp).padding((dp - side).coerceAtLeast(0).dp / 2),
    )
}

@Suppress("unused")
private val typesForRef: List<CType> = CType.values().toList()
