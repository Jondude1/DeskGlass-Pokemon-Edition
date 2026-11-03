package com.jondude1.deskglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.Sight
import com.jondude1.deskglass.sim.Sights
import kotlinx.coroutines.delay
import kotlin.math.max

private fun flyTo(cam: CivCamera, s: Sight) {
    cam.followId = -1
    cam.cx = s.x
    cam.cy = s.y
    cam.zoom = max(cam.zoom, 2.4f)
}

/**
 * "Sights": a button that flies the view to what is happening right now (foes, fires, weddings, fetes, rift parties,
 * critter bouts, visitors, the badly hurt), and a Tour switch that keeps the camera hopping between them on its own.
 */
@Composable
fun VillageSights(sim: CivSim, cam: CivCamera, tick: Long, modifier: Modifier = Modifier) {
    var tour by remember { mutableStateOf(false) }
    var lastKey by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var captionAt by remember { mutableStateOf(0L) }
    val list = Sights.find(sim)
    fun go() {
        val s = Sights.next(Sights.find(sim), lastKey)
        if (s != null) {
            lastKey = s.kind + s.label
            caption = s.label
            captionAt = tick
            flyTo(cam, s)
        }
    }
    LaunchedEffect(tour) {
        while (tour) {
            delay(7000L)
            go()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassButton("◎ Sights ${list.size}", { go() }, filled = list.isNotEmpty())
            GlassButton(if (tour) "Tour: on" else "Tour: off", { tour = !tour }, filled = tour)
        }
        if (caption.isNotEmpty() && (tour || tick - captionAt < 8L)) {
            Text(
                caption,
                color = Color(0xFFFFE9A8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.background(Color(0xAA101828), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
