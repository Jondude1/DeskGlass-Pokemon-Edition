package com.jondude1.deskglass.sim

/**
 * The Village Almanac: at every dawn the village writes up the day that just ended from real counters
 * (people, births, deaths, stores, buildings, foes beaten, deeds, discoveries, critters) plus the best
 * chronicle line of that day. Nothing is invented; a quiet day says so. Pages are saved optionally.
 */
class Almanac {
    /** Finished pages, oldest first: "day|season|headline|detail lines joined by ;". */
    val pages = ArrayList<String>()
    private var lastDay = -1
    private var snap = IntArray(0)
    var lastPageAt = -999.0

    private fun slain(s: CivSim): Int = s.ogresSlain + s.bossesSlain + s.warlordsSlain + s.golemsSlain + s.lichesSlain + s.reaversSlain +
        s.trollsSlain + s.harpiesSlain + s.spidersSlain + s.wormsSlain + s.scorpionsSlain + s.toadsSlain + s.stagsSlain + s.ramsSlain + s.shamansSlain

    private fun take(s: CivSim): IntArray = intArrayOf(
        s.villagers.size, s.babiesBorn, s.buried, s.raidsRepelled, slain(s), s.expWins, s.fetesHeld, s.weds,
        s.deeds.size, s.buildings.size, s.food, s.wood, s.stone, s.techs.size, s.critters.critters.size, s.birthdays, s.starsFallen,
    )

    private fun delta(a: Int, b: Int): String = if (b > a) "+${b - a}" else if (b < a) "${b - a}" else "same"

    /** Called every frame; cheap unless a new day has begun. */
    fun check(s: CivSim) {
        val d = s.day()
        if (lastDay < 0 || snap.isEmpty()) {
            lastDay = d; snap = take(s); return
        }
        if (d <= lastDay) return
        val ended = lastDay
        val now = take(s)
        val bits = ArrayList<String>()
        val pop = now[0]
        bits.add("People $pop (${delta(snap[0], pop)})")
        if (now[1] > snap[1]) bits.add("${now[1] - snap[1]} born")
        if (now[2] > snap[2]) bits.add("${now[2] - snap[2]} laid to rest")
        if (now[7] > snap[7]) bits.add("${now[7] - snap[7]} wed")
        if (now[15] > snap[15]) bits.add("${now[15] - snap[15]} birthday(s)")
        if (now[3] > snap[3]) bits.add("${now[3] - snap[3]} raid(s) beaten")
        if (now[4] > snap[4]) bits.add("${now[4] - snap[4]} beast(s) slain")
        if (now[5] > snap[5]) bits.add("${now[5] - snap[5]} rift expedition(s) cleared")
        if (now[6] > snap[6]) bits.add("a fete was held")
        if (now[16] > snap[16]) bits.add("a star fell")
        if (now[9] > snap[9]) bits.add("${now[9] - snap[9]} building(s) raised")
        if (now[13] > snap[13]) bits.add("a new discovery")
        if (now[14] > snap[14]) bits.add("${now[14] - snap[14]} critter(s) joined")
        if (now[8] > snap[8]) bits.add("${now[8] - snap[8]} deed(s) done")
        val stores = "Food ${now[10]} (${delta(snap[10], now[10])}), wood ${now[11]} (${delta(snap[11], now[11])}), stone ${now[12]} (${delta(snap[12], now[12])})"
        val prefix = "Day $ended: "
        val best = s.chronicle.lastOrNull { it.startsWith(prefix) }?.substringAfter(": ")
        val quiet = bits.size <= 1 && best == null
        val head = (if (quiet) "A quiet day." else (best ?: "A busy day.")).replace("|", "/").replace(";", ",").replace("~", "-")
        val detail = ArrayList<String>()
        detail.addAll(bits)
        detail.add(stores)
        pages.add("$ended|${s.seasonName()}|$head|${detail.joinToString(";")}")
        while (pages.size > 40) pages.removeAt(0)
        lastPageAt = s.time
        snap = now; lastDay = d
        s.deedBannerAt = s.time
        s.deedBannerTag = "DAY $ended IN REVIEW"
        s.deedBannerName = head.take(60)
        s.deedBannerSub = bits.take(3).joinToString(" · ") + (if (bits.isNotEmpty()) " · " else "") + "People $pop"
    }

    fun encode(): String = pages.joinToString("~")

    fun decode(text: String) {
        pages.clear()
        for (p in text.split("~")) if (p.count { it == '|' } >= 3) pages.add(p)
        lastDay = -1; snap = IntArray(0)
    }

    fun reset() { pages.clear(); lastDay = -1; snap = IntArray(0); lastPageAt = -999.0 }
}
