package com.jondude1.deskglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.sim.CivSim

@Composable
private fun StatBar(label: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Glass.InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
        Box(
            Modifier.width(110.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x66000000)),
        ) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(8.dp).background(color))
        }
    }
}

/**
 * The view while following one villager, like a role-playing game: health and needs in the corner, a quest line for
 * what they are doing right now, an adventure log, and a dialogue box for what they say and think. The villager is
 * still run by the village AI; this only lets you watch.
 */
@Composable
fun RpgHud(sim: CivSim, id: Int, tick: Long, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = tick
    val v = sim.villagers.firstOrNull { it.id == id } ?: return
    Box(modifier) {
        // a soft vignette frames the hero
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color.Transparent, Color(0x66000000)), radius = 1400f),
            ),
        )
        // vitals, top left (under the exit button)
        Column(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 64.dp)
                .background(Color(0xCC0B1220), RoundedCornerShape(14.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(v.name, color = Color(0xFFFFE9A0), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                (if (sim.isChild(v)) "Child" else v.job) + " · age ${sim.ageYears(v)} · ${sim.mood(v)}",
                color = Glass.InkDim, fontSize = 11.sp,
            )
            StatBar("HP", v.hp / 100f, Color(0xFFE0453A))
            StatBar("FOOD", 1f - v.hunger / 100f, Color(0xFFE89A2B))
            StatBar("NRG", v.energy / 100f, Color(0xFF4F9BFF))
            StatBar("JOY", v.happiness / 100f, Color(0xFFFFD84A))
        }
        // adventure log, left, above the dialogue box
        val log = v.diary.takeLast(6)
        if (log.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 128.dp).width(230.dp)
                    .background(Color(0x990B1220), RoundedCornerShape(12.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("ADVENTURE LOG", color = Glass.InkFaint, fontSize = 9.sp, letterSpacing = 2.sp)
                for ((i, line) in log.withIndex()) {
                    Text(line, color = if (i == log.lastIndex) Glass.Ink else Glass.InkFaint, fontSize = 11.sp, maxLines = 2)
                }
            }
        }
        // quest line and dialogue box, bottom
        val speech = when {
            v.say.isNotBlank() -> "“${v.say}”"
            v.thought.isNotBlank() -> "(${v.thought})"
            else -> ""
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 12.dp).fillMaxWidth()
                .background(Color(0xE60B1220), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("QUEST", color = Color(0xFFFFD84A), fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Text(sim.questLine(v), color = Glass.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (speech.isNotEmpty()) {
                Text("${v.name}: $speech", color = Glass.InkDim, fontSize = 13.sp, maxLines = 3)
            }
            val tt = v.talkTo
            if (tt != null) Text("Talking with ${tt.name}", color = Glass.InkFaint, fontSize = 11.sp)
        }
    }
}

private fun Modifier.fillMaxSize(): Modifier = this.then(Modifier.fillMaxWidth().height(2000.dp))
