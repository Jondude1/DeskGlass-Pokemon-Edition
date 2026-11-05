package com.jondude1.deskglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.Divine
import com.jondude1.deskglass.sim.DivinePower

private fun shortName(id: String): String = when (id) {
    "sun" -> "Sun"
    "rain" -> "Rain"
    "heal" -> "Heal"
    "grow" -> "Grow"
    else -> "Smite"
}

@Composable
private fun PowerChip(p: DivinePower, ready: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (ready) Color(0xCC3A2E10) else Color(0x99101828))
            .border(1.dp, if (ready) Color(0xFFFFD84A) else Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(shortName(p.id), color = if (ready) Color(0xFFFFE9A8) else Glass.InkFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text("${p.cost}", color = if (ready) Color(0xFFFFD84A) else Glass.InkFaint, fontSize = 10.sp)
    }
}

/**
 * Divine Favour: a favour meter the village fills by being happy, and five powers to spend it on
 * (Sun, Rain, Heal, Grow, Smite). Horizontal in the side panel, a narrow column over the map in fullscreen.
 */
@Composable
fun DivinePanel(sim: CivSim, tick: Long, vertical: Boolean, modifier: Modifier = Modifier) {
    val d = sim.divine
    var note by remember { mutableStateOf("") }
    var noteAt by remember { mutableStateOf(-100L) }
    val fav = d.favour
    fun use(p: DivinePower) {
        note = sim.godPower(p.id)
        noteAt = tick
    }
    val bar: @Composable (Modifier) -> Unit = { m ->
        Box(m.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((fav / 100f).coerceIn(0.02f, 1f)).background(if (fav >= 30f) Color(0xFFFFD84A) else Color(0xFFC9A94A)))
        }
    }
    if (vertical) {
        Column(modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FAVOUR ${fav.toInt()}", color = Color(0xFFFFE9A8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
            bar(Modifier.fillMaxWidth())
            for (p in Divine.POWERS) PowerChip(p, d.canAfford(p), Modifier.fillMaxWidth()) { use(p) }
            if (note.isNotEmpty() && tick - noteAt < 10L) {
                Text(note, color = Color(0xFFFFE9A8), fontSize = 10.sp, modifier = Modifier.background(Color(0xCC101828), RoundedCornerShape(8.dp)).padding(5.dp))
            }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DIVINE FAVOUR ${fav.toInt()}/100", color = Color(0xFFFFE9A8), fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
                bar(Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (p in Divine.POWERS) PowerChip(p, d.canAfford(p), Modifier.weight(1f)) { use(p) }
            }
            val fresh = note.isNotEmpty() && tick - noteAt < 10L
            val shown = if (fresh) note else "Happy villagers fill the meter. Sun clears weather, Rain breaks droughts, Heal mends the hurt, Grow ripens farms, Smite strikes foes."
            Text(shown, color = if (fresh) Color(0xFFFFE9A8) else Glass.InkFaint, fontSize = 12.sp)
        }
    }
}
