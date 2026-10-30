package com.jondude1.deskglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.sim.CivSim

/** A small window on the right: the villager being followed, their summoning gift and the critters they have bonded. */
@Composable
fun VillagerPanel(sim: CivSim, id: Int, tick: Long, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = tick
    val v = sim.villagers.firstOrNull { it.id == id } ?: return
    val cw = sim.critters
    val cart = remember { CritterArt() }
    val mage = cw.mageOf(v)
    val mine = cw.ownedBy(v).sortedWith(compareByDescending<com.jondude1.deskglass.sim.Critter> { it.companion }.thenByDescending { it.level })
    Column(
        modifier
            .width(250.dp)
            .heightIn(max = 340.dp)
            .background(Color(0xDD0B1220), RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(v.name, color = Glass.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            (if (sim.isChild(v)) "Child" else v.job) + " · age ${sim.ageYears(v)} · feeling ${sim.mood(v)}",
            color = Glass.InkDim, fontSize = 12.sp,
        )
        if (v.parents.isNotBlank()) Text("child of ${v.parents}", color = Glass.InkFaint, fontSize = 11.sp)
        Text(
            "Doing: " + (if (v.action.isEmpty()) "thinking" else v.action.replace('_', ' ')),
            color = Glass.InkDim, fontSize = 12.sp,
        )
        Text(
            "HP ${v.hp.toInt()} · hunger ${v.hunger.toInt()} · energy ${v.energy.toInt()} · happy ${v.happiness.toInt()}",
            color = Glass.InkFaint, fontSize = 11.sp,
        )
        if (v.thought.isNotBlank()) Text("“${v.thought}”", color = Glass.InkDim, fontSize = 12.sp)

        Text("SUMMONING", color = Glass.InkFaint, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp))
        when {
            !mage.revealed -> Text("Too young for the gift test.", color = Glass.InkDim, fontSize = 12.sp)
            !mage.talent -> Text("Not gifted. Helps the village in other ways.", color = Glass.InkDim, fontSize = 12.sp)
            else -> {
                val teacher = sim.villagers.firstOrNull { it.id == mage.mentor }
                Text(
                    "Summoner Lv ${mage.lv}" + (if (teacher != null) " · taught by ${teacher.name}" else ""),
                    color = Glass.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
                Text(
                    "Mana ${mage.mana.toInt()}/${mage.maxMana.toInt()} · bonded levels ${cw.budgetUsed(v)}/${mage.capacity} · XP ${mage.xp}/${mage.xpNeed()}",
                    color = Glass.InkDim, fontSize = 11.sp,
                )
            }
        }

        if (mine.isNotEmpty()) {
            Text("SUMMONS ${mine.size}/6", color = Glass.InkFaint, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp))
        }
        for (c in mine) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Sprite(cart, c.sp, 40)
                Column {
                    Text(
                        (if (c.companion) "★ " else "") + c.label + "  Lv ${c.level}",
                        color = Glass.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    )
                    Text(
                        c.species.typeText + (if (c.companion) " · out" else " · infused") + (if (cw.isTired(c)) " · resting" else ""),
                        color = Glass.InkDim, fontSize = 11.sp,
                    )
                    Text(
                        "HP ${c.hp}/${c.maxHp} · trust ${c.bond}" + (if (c.species.evolveTo > 0) "/${c.trustNeeded()}" else ""),
                        color = Glass.InkFaint, fontSize = 11.sp,
                    )
                }
            }
        }
        Text("Tap them again to stop following.", color = Glass.InkFaint, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
