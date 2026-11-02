package com.jondude1.deskglass.sim

import java.util.Random
import kotlin.math.max
import kotlin.math.min

// Pure game rules for the critters: types, moves, the fifty species, stats, battles and catching.
// Nothing in here touches Android, so it can be tested on a plain JVM.

enum class CType(val label: String, val color: Long, val job: String) {
    EMBER("Ember", 0xFFFF7A2B, "smelts ore at the forge"),
    TIDE("Tide", 0xFF3C9BE6, "fishes the shallows"),
    LEAF("Leaf", 0xFF58B84A, "tends the farms"),
    SPARK("Spark", 0xFFFFD23C, "digs ore with static"),
    STONE("Stone", 0xFFA39478, "hauls stone"),
    GALE("Gale", 0xFFA8D8F0, "carries timber on the wind"),
    FROST("Frost", 0xFF9FE3F5, "keeps the food stores cold"),
    SHADE("Shade", 0xFF8B6BC4, "guards the village at night"),
    GLOW("Glow", 0xFFFFF0A0, "lifts everyone's spirits"),
    VENOM("Venom", 0xFFB45FD9, "drives pests and beasts off"),
    IRON("Iron", 0xFFB4BFCC, "mines ore and scrap"),
    SPIRIT("Spirit", 0xFF8FA6FF, "soothes and finds lost things"),
    PLAIN("Plain", 0xFFD8D0C0, "helps where it can"),
}

/** How a move looks when it plays: lunge, shot, beam, burst, rain or swirl. */
class Move(val id: String, val name: String, val type: CType, val power: Int, val acc: Int, val fx: String, val tier: Int)

/** One of the fifty species. art id = id. */
class Species(
    val id: Int,
    val name: String,
    val t1: CType,
    val t2: CType?,
    val stage: Int,
    val hp: Int,
    val atk: Int,
    val def: Int,
    val spd: Int,
    val evolveTo: Int,
    val evolveLv: Int,
    val rarity: Int,
    val habitat: String,
    val activity: Int,
    val blurb: String,
) {
    val types: List<CType> get() = if (t2 != null) listOf(t1, t2) else listOf(t1)
    val typeText: String get() = types.joinToString("/") { it.label }
}

object Dex {
    const val DUMMY = 0
    const val COUNT = 80
    const val MAX_LEVEL = 50
    /** Level at which a critter learns the ultimate of its main type. */
    const val ULTIMATE_LV = 30

    // ---- moves ----
    private val moveList = listOf(
        Move("scuffle", "Scuffle", CType.PLAIN, 35, 100, "lunge", 1),
        Move("cinderlash", "Cinder Lash", CType.EMBER, 40, 100, "lunge", 1),
        Move("blazerush", "Blaze Rush", CType.EMBER, 62, 95, "shot", 2),
        Move("sunfall", "Sunfall", CType.EMBER, 90, 85, "rain", 3),
        Move("brinesplash", "Brine Splash", CType.TIDE, 40, 100, "shot", 1),
        Move("surgewave", "Surge Wave", CType.TIDE, 62, 95, "burst", 2),
        Move("deepcurrent", "Deepcurrent", CType.TIDE, 90, 85, "swirl", 3),
        Move("thornflick", "Thorn Flick", CType.LEAF, 40, 100, "shot", 1),
        Move("bramblewrap", "Bramble Wrap", CType.LEAF, 62, 95, "burst", 2),
        Move("verdantcrush", "Verdant Crush", CType.LEAF, 90, 85, "rain", 3),
        Move("staticnip", "Static Nip", CType.SPARK, 40, 100, "lunge", 1),
        Move("arcsnap", "Arc Snap", CType.SPARK, 62, 95, "beam", 2),
        Move("stormfall", "Stormfall", CType.SPARK, 90, 85, "rain", 3),
        Move("pebbletoss", "Pebble Toss", CType.STONE, 40, 100, "shot", 1),
        Move("boulderroll", "Boulder Roll", CType.STONE, 62, 95, "lunge", 2),
        Move("cragcrash", "Crag Crash", CType.STONE, 90, 85, "rain", 3),
        Move("gustpeck", "Gust Peck", CType.GALE, 40, 100, "lunge", 1),
        Move("windslice", "Wind Slice", CType.GALE, 62, 95, "shot", 2),
        Move("cyclonespin", "Cyclone Spin", CType.GALE, 90, 85, "swirl", 3),
        Move("chillnip", "Chill Nip", CType.FROST, 40, 100, "lunge", 1),
        Move("hailshards", "Hail Shards", CType.FROST, 62, 95, "shot", 2),
        Move("glacierdrop", "Glacier Drop", CType.FROST, 90, 85, "rain", 3),
        Move("murkbite", "Murk Bite", CType.SHADE, 40, 100, "lunge", 1),
        Move("duskclaw", "Dusk Claw", CType.SHADE, 62, 95, "burst", 2),
        Move("eclipseveil", "Eclipse Veil", CType.SHADE, 90, 85, "swirl", 3),
        Move("flickerbeam", "Flicker Beam", CType.GLOW, 40, 100, "beam", 1),
        Move("radiantpulse", "Radiant Pulse", CType.GLOW, 62, 95, "burst", 2),
        Move("daybreak", "Daybreak", CType.GLOW, 90, 85, "rain", 3),
        Move("spitsting", "Spit Sting", CType.VENOM, 40, 100, "shot", 1),
        Move("toxiclob", "Toxic Lob", CType.VENOM, 62, 95, "shot", 2),
        Move("blightbloom", "Blight Bloom", CType.VENOM, 90, 85, "burst", 3),
        Move("rivetjab", "Rivet Jab", CType.IRON, 40, 100, "lunge", 1),
        Move("platebash", "Plate Bash", CType.IRON, 62, 95, "lunge", 2),
        Move("anvilfall", "Anvil Fall", CType.IRON, 90, 85, "rain", 3),
        Move("wispnudge", "Wisp Nudge", CType.SPIRIT, 40, 100, "shot", 1),
        Move("hauntwave", "Haunt Wave", CType.SPIRIT, 62, 95, "burst", 2),
        Move("soulbloom", "Soul Bloom", CType.SPIRIT, 90, 85, "swirl", 3),
        // tier 4: one ultimate per type, learned very late
        Move("ashencataclysm", "Ashen Cataclysm", CType.EMBER, 118, 80, "rain", 4),
        Move("maelstromcrown", "Maelstrom Crown", CType.TIDE, 118, 80, "swirl", 4),
        Move("worldrootsurge", "Worldroot Surge", CType.LEAF, 118, 80, "burst", 4),
        Move("thundersovereign", "Thunder Sovereign", CType.SPARK, 118, 80, "beam", 4),
        Move("mountainfall", "Mountainfall", CType.STONE, 118, 80, "rain", 4),
        Move("skyrendgale", "Skyrend Gale", CType.GALE, 118, 80, "swirl", 4),
        Move("absolutewinter", "Absolute Winter", CType.FROST, 118, 80, "rain", 4),
        Move("midnightmaw", "Midnight Maw", CType.SHADE, 118, 80, "burst", 4),
        Move("aurorajudgement", "Aurora Judgement", CType.GLOW, 118, 80, "beam", 4),
        Move("plaguenova", "Plague Nova", CType.VENOM, 118, 80, "burst", 4),
        Move("titanhammer", "Titan Hammer", CType.IRON, 118, 80, "lunge", 4),
        Move("requiemtide", "Requiem Tide", CType.SPIRIT, 118, 80, "swirl", 4),
    )
    val moves: Map<String, Move> = moveList.associateBy { it.id }
    val scuffle: Move = moves.getValue("scuffle")

    fun moveOf(t: CType, tier: Int): Move = moveList.firstOrNull { it.type == t && it.tier == tier } ?: scuffle

    // ---- species ----
    private fun s(
        id: Int, name: String, t1: CType, t2: CType?, stage: Int,
        hp: Int, atk: Int, def: Int, spd: Int, evo: Int, evoLv: Int,
        rarity: Int, habitat: String, activity: Int, blurb: String,
    ) = Species(id, name, t1, t2, stage, hp, atk, def, spd, evo, evoLv, rarity, habitat, activity, blurb)

    // activity: 0 any time, 1 night only, 2 day only
    private val list: List<Species> = listOf(
        // Ember line: a fox kit that grows a flame mane
        s(1, "Cindrel", CType.EMBER, null, 1, 42, 52, 40, 56, 2, 14, 1, "meadow", 0, "A fox kit whose tail tip never stops smouldering."),
        s(2, "Flarecoat", CType.EMBER, null, 2, 62, 76, 58, 74, 3, 30, 2, "meadow", 0, "Its mane crackles when it runs."),
        s(3, "Pyremane", CType.EMBER, null, 3, 84, 104, 76, 92, -1, 0, 3, "meadow", 0, "A great fire-maned hunter that warms a whole camp."),
        // Tide line: tadpole to frog to a crested river lord
        s(4, "Dripple", CType.TIDE, null, 1, 48, 44, 46, 42, 5, 14, 1, "water", 0, "A round little tadpole that leaves wet footprints."),
        s(5, "Gillfin", CType.TIDE, null, 2, 70, 62, 68, 56, 6, 30, 2, "water", 0, "A lanky frog with a sail-like fin."),
        s(6, "Brinelord", CType.TIDE, null, 3, 98, 84, 92, 64, -1, 0, 3, "water", 0, "A broad river lord with a crest like a wave."),
        // Leaf line: bud, thorn-backed beast, walking tree
        s(7, "Sproutle", CType.LEAF, null, 1, 46, 46, 48, 38, 8, 14, 1, "forest", 0, "A bud of a creature with a single bright leaf."),
        s(8, "Thornback", CType.LEAF, null, 2, 68, 66, 74, 50, 9, 30, 2, "forest", 0, "Thorns line its back and it guards the berry bushes."),
        s(9, "Verdanox", CType.LEAF, null, 3, 96, 88, 102, 52, -1, 0, 3, "forest", 0, "A bull with a small tree growing from its back."),
        // Spark line: gecko, wyrm, storm drake
        s(10, "Zaplet", CType.SPARK, null, 1, 38, 50, 38, 66, 11, 14, 1, "meadow", 0, "A quick gecko that crackles when it is happy."),
        s(11, "Arcwyrm", CType.SPARK, null, 2, 56, 72, 54, 90, 12, 30, 2, "meadow", 0, "A sleek wyrm that arcs sparks between its horns."),
        s(12, "Stormdrake", CType.SPARK, null, 3, 76, 98, 70, 116, -1, 0, 3, "meadow", 0, "A small drake that carries its own thunderhead."),
        // Stone line: pebble, golem, crystal golem
        s(13, "Pebblet", CType.STONE, null, 1, 50, 50, 62, 26, 14, 14, 1, "rock", 0, "A pebble with eyes. It rarely moves, and never hurries."),
        s(14, "Boulderkin", CType.STONE, null, 2, 76, 76, 92, 34, 15, 30, 2, "rock", 0, "A stout rock-walker with mossy shoulders."),
        s(15, "Crestgolem", CType.STONE, null, 3, 104, 100, 126, 40, -1, 0, 3, "rock", 0, "Crystals crown its head and glow when it lifts something heavy."),
        // Gale line: round bird, wing, raptor
        s(16, "Breezlet", CType.GALE, null, 1, 40, 46, 40, 64, 17, 14, 1, "meadow", 0, "A round little bird that rides every draught."),
        s(17, "Gustwing", CType.GALE, null, 2, 58, 66, 56, 86, 18, 30, 2, "meadow", 0, "Long wings that whistle on the downstroke."),
        s(18, "Cyclonyx", CType.GALE, null, 3, 78, 92, 72, 112, -1, 0, 3, "meadow", 0, "A raptor that sets the clouds turning."),
        // Frost line: chick, penguin, glacier penguin
        s(19, "Chillet", CType.FROST, null, 1, 46, 44, 44, 44, 20, 14, 1, "snow", 0, "A fluffy penguin chick that sneezes snowflakes."),
        s(20, "Frostwaddle", CType.FROST, null, 2, 68, 64, 66, 56, 21, 30, 2, "snow", 0, "It toboggans on its belly faster than it walks."),
        s(21, "Glaciarch", CType.FROST, null, 3, 96, 88, 94, 60, -1, 0, 3, "snow", 0, "A tall penguin with a crown of blue ice."),
        // Shade line: kitten, cat, panther
        s(22, "Murkit", CType.SHADE, null, 1, 40, 54, 38, 60, 23, 14, 1, "forest", 1, "A sooty kitten that hides in any dark corner."),
        s(23, "Duskcat", CType.SHADE, null, 2, 58, 78, 54, 82, 24, 30, 2, "forest", 1, "Its purple eyes are the only thing you see at night."),
        s(24, "Nightveil", CType.SHADE, null, 3, 78, 106, 70, 104, -1, 0, 3, "forest", 1, "A panther that trails a cloak of living shadow."),
        // Glow line: firefly, lantern fly, great moth
        s(25, "Glimmerbug", CType.GLOW, null, 1, 44, 40, 44, 50, 26, 14, 1, "meadow", 1, "A firefly that blinks in time with the crickets."),
        s(26, "Lumenfly", CType.GLOW, null, 2, 64, 58, 66, 72, 27, 30, 2, "meadow", 1, "Lantern wings light a path for lost travelers."),
        s(27, "Radiantis", CType.GLOW, null, 3, 90, 82, 88, 96, -1, 0, 3, "meadow", 1, "A great moth whose wings glow like stained glass."),
        // Venom line: snake, coil, cobra
        s(28, "Spitling", CType.VENOM, null, 1, 40, 50, 40, 54, 29, 14, 1, "forest", 0, "A young snake with more spit than sense."),
        s(29, "Toxicoil", CType.VENOM, null, 2, 62, 72, 58, 70, 30, 30, 2, "forest", 0, "It coils around a branch and waits for pests."),
        s(30, "Venomaw", CType.VENOM, null, 3, 84, 98, 78, 88, -1, 0, 3, "forest", 0, "A hooded cobra with a bloom of poison on its hood."),
        // Iron line: beetle, bolt beetle, armoured rhino beetle
        s(31, "Rivetbug", CType.IRON, null, 1, 44, 50, 58, 36, 32, 14, 1, "ore", 0, "A beetle that looks like it was riveted together."),
        s(32, "Boltbeetle", CType.IRON, null, 2, 66, 72, 86, 46, 33, 30, 2, "ore", 0, "Plated wings that ring like a bell when struck."),
        s(33, "Ironclad", CType.IRON, null, 3, 92, 98, 118, 52, -1, 0, 3, "ore", 0, "A rhino beetle in a full suit of plate."),
        // Spirit line: wisp, lantern ghost, robed spirit
        s(34, "Wispling", CType.SPIRIT, null, 1, 38, 42, 40, 58, 35, 14, 1, "any", 1, "A curl of pale light that follows the sound of laughter."),
        s(35, "Ghostlamp", CType.SPIRIT, null, 2, 60, 64, 60, 76, 36, 30, 2, "any", 1, "It carries a little lantern that never needs oil."),
        s(36, "Spectrelle", CType.SPIRIT, null, 3, 82, 92, 80, 98, -1, 0, 3, "any", 1, "A graceful robed spirit that hums when it floats."),
        // Dual-type pairs
        s(37, "Mossback", CType.LEAF, CType.STONE, 1, 52, 48, 64, 30, 38, 22, 2, "forest", 0, "A small turtle with a garden on its shell."),
        s(38, "Fernshell", CType.LEAF, CType.STONE, 2, 84, 74, 100, 42, -1, 0, 3, "forest", 0, "Ferns, flowers and a few mushrooms ride on its shell."),
        s(39, "Cinderpup", CType.EMBER, CType.STONE, 1, 46, 56, 48, 46, 40, 22, 2, "rock", 0, "A pup that sleeps on warm stones and snores smoke."),
        s(40, "Magmahound", CType.EMBER, CType.STONE, 2, 78, 92, 76, 64, -1, 0, 3, "rock", 0, "Molten cracks glow along its flanks."),
        s(41, "Brinecrab", CType.TIDE, CType.IRON, 1, 48, 52, 62, 34, 42, 22, 2, "water", 0, "A crab in a shell of rusty plate."),
        s(42, "Tidalclaw", CType.TIDE, CType.IRON, 2, 80, 84, 98, 44, -1, 0, 3, "water", 0, "One huge claw can crack a boulder."),
        s(43, "Snowowl", CType.FROST, CType.GALE, 1, 42, 46, 42, 60, 44, 22, 2, "snow", 0, "An owlet that glides without a sound."),
        s(44, "Blizzardowl", CType.FROST, CType.GALE, 2, 70, 76, 66, 92, -1, 0, 3, "snow", 0, "A snow owl that brings flurries in its wake."),
        s(45, "Fuzzvolt", CType.SPARK, CType.GALE, 1, 40, 48, 40, 68, 46, 22, 2, "meadow", 0, "A fluffy hare whose fur stands up before a storm."),
        s(46, "Thunderhare", CType.SPARK, CType.GALE, 2, 66, 78, 60, 100, -1, 0, 3, "meadow", 0, "Its jump is a clap of thunder."),
        s(47, "Gloomwing", CType.SHADE, CType.VENOM, 1, 42, 50, 40, 62, 48, 22, 2, "forest", 1, "A bat that hangs upside down and mutters."),
        s(48, "Eclipsebat", CType.SHADE, CType.VENOM, 2, 70, 82, 62, 96, -1, 0, 3, "forest", 1, "Its wings blot out the moon for a heartbeat."),
        // Rare singles
        s(49, "Sunpetal", CType.GLOW, CType.LEAF, 1, 70, 64, 70, 66, -1, 0, 4, "meadow", 2, "A flower sprite that only blooms in full sun."),
        s(50, "Auroryn", CType.GLOW, CType.FROST, 1, 80, 76, 74, 80, -1, 0, 4, "snow", 1, "A pale stag whose antlers shimmer like the northern lights."),
        // Clockwork line: a wind-up fox that grows into a forge-hearted hound
        s(51, "Tinkit", CType.IRON, null, 1, 42, 48, 54, 44, 52, 14, 1, "ore", 0, "A wind-up fox kit. A tiny key turns in its back."),
        s(52, "Cogfox", CType.IRON, CType.EMBER, 2, 64, 72, 78, 62, 53, 30, 2, "ore", 0, "Gears show through its flanks and glow orange when it runs."),
        s(53, "Forgehound", CType.IRON, CType.EMBER, 3, 90, 100, 106, 70, -1, 0, 3, "ore", 0, "A great hound with a furnace for a heart."),
        // Cliff line: a mountain goat of the high crags
        s(54, "Kidling", CType.GALE, null, 1, 44, 48, 46, 58, 55, 14, 1, "rock", 0, "A nimble kid that bounds up any cliff."),
        s(55, "Cragram", CType.GALE, CType.STONE, 2, 68, 72, 76, 72, 56, 30, 2, "rock", 0, "Its curled horns chip stone from the crag as it climbs."),
        s(56, "Summitaur", CType.GALE, CType.STONE, 3, 94, 98, 100, 88, -1, 0, 3, "rock", 0, "A mountain-lord who watches the whole valley from the peak."),
        // Legends: not found in the wild. They answer only to a summoner who has proved a village worth guarding.
        s(57, "Aurelion", CType.EMBER, CType.GLOW, 1, 110, 124, 100, 104, -1, 0, 5, "legend", 0, "A lion of living sunlight. Its mane is a dawn that never ends."),
        s(58, "Tidewyrm", CType.TIDE, CType.FROST, 1, 124, 106, 112, 90, -1, 0, 5, "legend", 0, "A sea serpent of green ice that sleeps beneath the deepest water."),
        s(59, "Elderstag", CType.LEAF, CType.SPIRIT, 1, 120, 100, 118, 82, -1, 0, 5, "legend", 0, "An ancient stag with a whole grove growing in its antlers."),
        s(60, "Stormroc", CType.SPARK, CType.GALE, 1, 100, 122, 92, 130, -1, 0, 5, "legend", 0, "A thunderbird whose wingbeats are the first storm of spring."),
        s(61, "Ironwarden", CType.IRON, CType.STONE, 1, 130, 116, 146, 56, -1, 0, 5, "legend", 0, "A colossus of riveted stone that has guarded the old gate for ages."),
        s(62, "Voidwyrm", CType.SHADE, CType.SPIRIT, 1, 118, 130, 98, 112, -1, 0, 5, "legend", 1, "The dragon that sleeps behind every rift. It answers only to the bravest."),
        // Fungus line: a mushroom pup that grows a vast spotted cap
        s(63, "Puffcap", CType.LEAF, CType.VENOM, 1, 44, 46, 46, 36, 64, 18, 2, "forest", 0, "A tiny mushroom pup that puffs sleepy spores when startled."),
        s(64, "Sporecap", CType.LEAF, CType.VENOM, 2, 66, 66, 70, 44, 65, 34, 2, "forest", 0, "Its cap grows wider with every rain and drips glowing spores."),
        s(65, "Mycelord", CType.LEAF, CType.VENOM, 3, 94, 90, 98, 50, -1, 0, 3, "forest", 0, "A lord of the deep woods whose roots reach every tree in the valley."),
        // Jelly line: a night-lit drifter of the river
        s(66, "Jellipip", CType.TIDE, CType.GLOW, 1, 46, 40, 44, 48, 67, 18, 2, "water", 0, "A pale little jelly that blinks pink when it is happy."),
        s(67, "Lanternjelly", CType.TIDE, CType.GLOW, 2, 68, 58, 66, 62, 68, 34, 2, "water", 0, "A lantern glows in its bell and lures lost fishermen home."),
        s(68, "Aurorajelly", CType.TIDE, CType.GLOW, 3, 92, 84, 88, 78, -1, 0, 3, "water", 0, "Ribbons of green light trail behind it like the northern lights."),
        // Digger line: a mole that tunnels under the village and learns to drill
        s(69, "Molekin", CType.STONE, null, 1, 52, 50, 58, 30, 70, 18, 2, "ore", 0, "A shy mole with a pink nose that sniffs out buried ore."),
        s(70, "Tunnelmole", CType.STONE, CType.IRON, 2, 76, 74, 90, 38, 71, 34, 2, "ore", 0, "It wears a steel cap and digs tunnels faster than miners."),
        s(71, "Drillmaw", CType.STONE, CType.IRON, 3, 102, 104, 116, 44, -1, 0, 3, "ore", 0, "A bulky digger with a spinning drill snout that bores through any rock."),
        // Bee line: a striped grub-bee that grows into a hive queen
        s(72, "Buzzlet", CType.GALE, CType.LEAF, 1, 42, 48, 40, 62, 73, 18, 2, "meadow", 2, "A round striped bee that hums off-key and loves clover."),
        s(73, "Honeywing", CType.GALE, CType.LEAF, 2, 62, 70, 56, 84, 74, 34, 2, "meadow", 2, "Its wings drip golden nectar that the farmers collect."),
        s(74, "Queenbuzz", CType.GLOW, CType.LEAF, 3, 84, 92, 74, 108, -1, 0, 3, "meadow", 2, "A hive queen in a crown of wax. The whole meadow hums when she flies."),
        // Candle line: a wax stub with a living flame that grows into a beacon spirit
        s(75, "Wickling", CType.SPIRIT, CType.EMBER, 1, 44, 46, 42, 52, 76, 18, 2, "any", 1, "A candle stub with a shy flame. It only lights up after dark."),
        s(76, "Waxwraith", CType.SPIRIT, CType.EMBER, 2, 66, 66, 62, 68, 77, 34, 2, "any", 1, "A drooping wax ghost whose flame never gutters, even in a gale."),
        s(77, "Beaconel", CType.SPIRIT, CType.GLOW, 3, 90, 90, 86, 84, -1, 0, 3, "any", 1, "A tall candle spirit with a flame like a lighthouse. Lost travelers follow it home."),
        // Coral line: a reef-crabbed shell-walker of the lake
        s(78, "Coralit", CType.TIDE, CType.STONE, 1, 50, 46, 60, 32, 79, 18, 2, "water", 0, "A little shell-walker with a sprig of pink coral on its head."),
        s(79, "Reefback", CType.TIDE, CType.STONE, 2, 78, 70, 92, 40, 80, 34, 2, "water", 0, "A reef grows on its back, full of tiny glowing fish."),
        s(80, "Tidemonarch", CType.TIDE, CType.STONE, 3, 104, 92, 118, 46, -1, 0, 3, "water", 0, "A living reef-king who calls the shoals home with a conch horn."),
    )

    private val dummy = Species(
        DUMMY, "Straw Dummy", CType.PLAIN, null, 1, 70, 20, 40, 10, -1, 0, 1, "any", 0, "A training dummy stuffed with straw.",
    )

    val species: List<Species> = list

    fun byId(id: Int): Species = if (id in 1..COUNT) list[id - 1] else dummy

    /** The species that evolves into this one, or -1. */
    fun preOf(id: Int): Int = list.firstOrNull { it.evolveTo == id }?.id ?: -1

    /** The whole evolution line this species belongs to, first stage first. */
    fun lineOf(id: Int): List<Species> {
        var first = id
        while (preOf(first) > 0) first = preOf(first)
        val out = ArrayList<Species>()
        var cur = first
        while (cur > 0) {
            out.add(byId(cur))
            cur = byId(cur).evolveTo
        }
        return out
    }

    /** All lines, each as its list of stages, in the order they first appear. */
    fun lines(): List<List<Species>> = list.filter { preOf(it.id) < 0 }.map { lineOf(it.id) }

    // ---- stats ----
    fun maxHp(sp: Species, lv: Int): Int = sp.hp * 2 * lv / 100 + lv + 10
    private fun other(base: Int, lv: Int): Int = base * 2 * lv / 100 + 5
    fun atk(sp: Species, lv: Int) = other(sp.atk, lv)
    fun def(sp: Species, lv: Int) = other(sp.def, lv)
    fun spd(sp: Species, lv: Int) = other(sp.spd, lv)

    fun xpNeed(lv: Int): Int = 10 + lv * lv

    /** Moves a species learns, as (level, move), in the order learned. */
    fun learnset(sp: Species): List<Pair<Int, Move>> {
        val out = ArrayList<Pair<Int, Move>>()
        out.add(1 to scuffle)
        out.add(1 to moveOf(sp.t1, 1))
        out.add(9 to moveOf(sp.t1, 2))
        out.add(20 to moveOf(sp.t1, 3))
        if (sp.t1 != CType.PLAIN) out.add(ULTIMATE_LV to moveOf(sp.t1, 4))
        val t2 = sp.t2
        if (t2 != null) {
            out.add(12 to moveOf(t2, 1))
            out.add(26 to moveOf(t2, 2))
            out.add(38 to moveOf(t2, 3))
            if (t2 != CType.PLAIN) out.add(46 to moveOf(t2, 4))
        }
        return out.sortedBy { it.first }
    }

    /** The four most recently learned moves at a level. */
    fun movesAt(sp: Species, lv: Int): List<Move> {
        val known = learnset(sp).filter { it.first <= lv }.map { it.second }
        return known.takeLast(4)
    }

    // ---- types ----
    private val strong: Map<CType, Set<CType>> = mapOf(
        CType.EMBER to setOf(CType.LEAF, CType.FROST, CType.IRON),
        CType.TIDE to setOf(CType.EMBER, CType.STONE),
        CType.LEAF to setOf(CType.TIDE, CType.STONE),
        CType.SPARK to setOf(CType.TIDE, CType.GALE),
        CType.STONE to setOf(CType.EMBER, CType.SPARK, CType.GALE),
        CType.GALE to setOf(CType.LEAF, CType.VENOM),
        CType.FROST to setOf(CType.LEAF, CType.GALE, CType.STONE),
        CType.SHADE to setOf(CType.GLOW, CType.SPIRIT),
        CType.GLOW to setOf(CType.SHADE, CType.VENOM),
        CType.VENOM to setOf(CType.LEAF, CType.GLOW),
        CType.IRON to setOf(CType.FROST, CType.STONE, CType.SPIRIT),
        CType.SPIRIT to setOf(CType.SHADE, CType.VENOM),
    )
    private val weak: Map<CType, Set<CType>> = mapOf(
        CType.EMBER to setOf(CType.TIDE, CType.STONE, CType.EMBER),
        CType.TIDE to setOf(CType.LEAF, CType.TIDE),
        CType.LEAF to setOf(CType.EMBER, CType.LEAF, CType.GALE, CType.VENOM),
        CType.SPARK to setOf(CType.STONE, CType.SPARK, CType.LEAF),
        CType.STONE to setOf(CType.IRON, CType.LEAF),
        CType.GALE to setOf(CType.STONE, CType.IRON, CType.SPARK),
        CType.FROST to setOf(CType.EMBER, CType.IRON, CType.FROST),
        CType.SHADE to setOf(CType.GLOW, CType.SHADE),
        CType.GLOW to setOf(CType.IRON, CType.GLOW),
        CType.VENOM to setOf(CType.IRON, CType.STONE, CType.VENOM),
        CType.IRON to setOf(CType.EMBER, CType.TIDE, CType.IRON),
        CType.SPIRIT to setOf(CType.IRON, CType.SPIRIT),
    )

    fun effOne(att: CType, def: CType): Float = when {
        strong[att]?.contains(def) == true -> 2f
        weak[att]?.contains(def) == true -> 0.5f
        else -> 1f
    }

    fun effectiveness(att: CType, def: Species): Float {
        var e = effOne(att, def.t1)
        val t2 = def.t2
        if (t2 != null) e *= effOne(att, t2)
        return e
    }

    // ---- wild encounters ----
    /** Weighted pick of a wild species for the current moment. */
    fun pickWild(rnd: Random, age: Int, night: Boolean, winter: Boolean): Species {
        var total = 0f
        val w = FloatArray(list.size)
        for ((i, sp) in list.withIndex()) {
            var x = when (sp.rarity) { 1 -> 10f; 2 -> 3.5f; 3 -> 1.2f; else -> 0.6f }
            if (sp.stage >= 2 && sp.evolveTo >= 0 && age < 2) x = 0f
            if (sp.stage == 2 && sp.evolveTo < 0 && sp.rarity == 3 && age < 2) x = 0f
            if (sp.stage == 3 && age < 4) x = 0f
            if (sp.rarity == 4 && age < 2) x = 0f
            if (sp.rarity >= 5) x = 0f
            if (sp.activity == 1 && !night && !(sp.id == 50 && winter)) x = 0f
            if (sp.activity == 2 && night) x = 0f
            if (sp.habitat == "snow" && winter) x *= 2.5f
            w[i] = x
            total += x
        }
        var r = rnd.nextFloat() * total
        for (i in w.indices) {
            r -= w[i]
            if (r <= 0f && w[i] > 0f) return list[i]
        }
        return list[0]
    }

    /** A believable wild level for a species at a village age. */
    fun wildLevel(rnd: Random, sp: Species, age: Int): Int {
        val base = 3 + age * 5 + rnd.nextInt(5)
        val floor = if (preOf(sp.id) > 0) byId(preOf(sp.id)).evolveLv else 1
        val ceil = if (sp.evolveLv > 0) sp.evolveLv + 6 else MAX_LEVEL
        return base.coerceAtLeast(floor).coerceAtMost(ceil).coerceIn(2, MAX_LEVEL)
    }

    /** Chance that one snare holds. */
    fun catchChance(sp: Species, level: Int, hpFrac: Float, bonus: Float): Float {
        val base = when (sp.rarity) { 1 -> 0.50f; 2 -> 0.38f; 3 -> 0.27f; 5 -> 0.42f; else -> 0.16f }
        val hurt = (1f - hpFrac.coerceIn(0f, 1f)) * 0.5f
        val lvl = -0.006f * level
        return (base + hurt + lvl + bonus).coerceIn(0.08f, 0.92f)
    }

    /** Chance that a berry offering wins a wild critter over, for a villager with no companion. */
    fun befriendChance(sp: Species, level: Int): Float =
        (when (sp.rarity) { 1 -> 0.55f; 2 -> 0.4f; 3 -> 0.28f; else -> 0.18f } - 0.02f * level).coerceIn(0.1f, 0.8f)
}

/** A critter's temperament: nudges one or two battle stats. Derived from the critter's id, so nothing needs saving. */
class Nature(val name: String, val up: Int, val down: Int, val flavour: String) {
    /** up/down: 0 attack, 1 defence, 2 speed, -1 none. */
    fun mult(stat: Int): Float = if (stat == up && up >= 0) (if (down >= 0) 1.10f else 1.06f) else if (stat == down) 0.92f else 1f
    val text: String get() {
        val n = listOf("Atk", "Def", "Spd")
        return when {
            up < 0 -> "balanced"
            down < 0 -> "+${n[up]}"
            else -> "+${n[up]} −${n[down]}"
        }
    }
    companion object {
        val ALL = listOf(
            Nature("Bold", 1, 0, "stubborn and steady"),
            Nature("Brave", 0, 2, "charges in first"),
            Nature("Timid", 2, 0, "quick to dart away"),
            Nature("Hasty", 2, 1, "never sits still"),
            Nature("Fierce", 0, 1, "fights without a guard"),
            Nature("Calm", 1, 2, "slow and unshakeable"),
            Nature("Proud", 0, -1, "stands tall"),
            Nature("Sturdy", 1, -1, "hard to knock down"),
            Nature("Swift", 2, -1, "light on its feet"),
            Nature("Mellow", -1, -1, "easygoing"),
            Nature("Plain", -1, -1, "no airs"),
            Nature("Quiet", -1, -1, "keeps to itself"),
        )
        val NEUTRAL = Nature("Plain", -1, -1, "no airs")
        fun of(uid: Int): Nature = if (uid < 0) NEUTRAL else ALL[(uid * 7 + 3) % ALL.size]
    }
}

/** One creature, wild or kept. */
class Critter(val uid: Int, var sp: Int, var level: Int) {
    var nick = ""
    var xp = 0
    var hp = 1
    var owner = -1
    var companion = false
    var wins = 0
    var bond = 0
    var faintedUntil = 0.0
    var regen = 0f
    var work = 0f
    var cycle = 0
    /** A rare recoloured critter: a little tougher and always sparkling. */
    var shiny = false
    /** Copy of another critter's nature (battle doubles have no id of their own); -1 means derive it from the id. */
    var natureIdx = -1
    val nature: Nature get() = if (natureIdx in Nature.ALL.indices) Nature.ALL[natureIdx] else Nature.of(uid)

    val species: Species get() = Dex.byId(sp)
    val maxHp: Int get() = Dex.maxHp(species, level).let { if (shiny) it * 108 / 100 else it }
    val atk: Int get() = Dex.atk(species, level).let { (if (shiny) it * 108 / 100 else it) }.let { (it * nature.mult(0)).toInt() }
    val def: Int get() = Dex.def(species, level).let { (if (shiny) it * 108 / 100 else it) }.let { (it * nature.mult(1)).toInt() }
    val spd: Int get() = Dex.spd(species, level).let { (if (shiny) it * 108 / 100 else it) }.let { (it * nature.mult(2)).toInt() }
    val moves: List<Move> get() = Dex.movesAt(species, level)
    val name: String get() = nick.ifBlank { species.name }
    /** "Ember the Cindrel" once named, plain species name before. */
    val label: String get() = if (nick.isBlank()) species.name else "$nick the ${species.name}"
    val hpFrac: Float get() = (hp.toFloat() / maxHp).coerceIn(0f, 1f)

    fun heal() {
        hp = maxHp
    }

    /** Adds experience; returns the levels gained. Evolution is left to the caller. */
    fun gainXp(amount: Int): Int {
        if (level >= Dex.MAX_LEVEL) return 0
        xp += amount
        var gained = 0
        while (level < Dex.MAX_LEVEL && xp >= Dex.xpNeed(level)) {
            xp -= Dex.xpNeed(level)
            level++
            gained++
            hp = min(maxHp, hp + 4 + level / 4)
        }
        if (level >= Dex.MAX_LEVEL) xp = 0
        return gained
    }

    /** True when it is old enough to change shape. */
    /** The trust a bonded critter needs before it will change shape: the milestone for its next form. */
    fun trustNeeded(): Int = if (species.stage >= 2) 40 else 18

    fun readyToEvolve(): Boolean {
        val s = species
        if (s.evolveTo <= 0) return false
        // a bonded critter evolves when its trust is deep enough (and it has grown a little); strays still go by level
        return if (owner >= 0) bond >= trustNeeded() && level >= max(2, s.evolveLv - 4) else level >= s.evolveLv
    }

    /** Becomes its next form. HP keeps its share of the new maximum. */
    fun evolve() {
        val frac = hpFrac
        val s = species
        if (s.evolveTo <= 0) return
        if (nick == s.name) nick = ""
        sp = s.evolveTo
        hp = max(1, (maxHp * frac).toInt())
    }
}

/** Passive battle talents. A critter learns the signature talent of its main type at level 10. */
object Abil {
    const val LEVEL = 10
    fun name(t: CType): String = when (t) {
        CType.EMBER -> "Blaze"
        CType.TIDE -> "Soothing Tide"
        CType.LEAF -> "Regrowth"
        CType.SPARK -> "Static Skin"
        CType.STONE -> "Sturdy"
        CType.GALE -> "Tailwind"
        CType.FROST -> "Rime Coat"
        CType.SHADE -> "Evasive"
        CType.GLOW -> "Dazzle"
        CType.VENOM -> "Toxic Barbs"
        CType.IRON -> "Plating"
        CType.SPIRIT -> "Soul Siphon"
        CType.PLAIN -> "Adaptable"
    }

    fun text(t: CType): String = when (t) {
        CType.EMBER -> "hits 35% harder when below a third health"
        CType.TIDE -> "heals a little each turn"
        CType.LEAF -> "heals a fifth once when it drops below half health"
        CType.SPARK -> "may stun whoever strikes it"
        CType.STONE -> "survives one knockout blow at 1 health"
        CType.GALE -> "usually moves first"
        CType.FROST -> "may chill whoever strikes it"
        CType.SHADE -> "slips away from 15% of attacks"
        CType.GLOW -> "dazzles foes so they miss more"
        CType.VENOM -> "barbs hurt whoever strikes it"
        CType.IRON -> "takes 12% less damage"
        CType.SPIRIT -> "heals from a share of the damage it deals"
        CType.PLAIN -> "hits 8% harder"
    }

    /** The talent name, or null while the critter is too young. */
    fun of(c: Critter): String? = if (c.level >= LEVEL) name(c.species.t1) else null

    fun has(c: Critter, t: CType): Boolean = c.level >= LEVEL && c.species.t1 == t
}

/** Battlefield weather: the sky over the fight tilts the odds for some critter types. */
object Field {
    const val NONE = 0
    const val RAIN = 1
    const val SUN = 2
    const val SNOW = 3
    const val NIGHT = 4
    const val STORM = 5

    fun name(f: Int): String = when (f) {
        RAIN -> "Rain"; SUN -> "Blazing sun"; SNOW -> "Snowfall"; NIGHT -> "Moonlit night"; STORM -> "Thunderstorm"; else -> ""
    }

    /** Damage multiplier for a move of type [t] under field [f]. */
    fun mul(f: Int, t: CType): Float = when (f) {
        RAIN -> when (t) { CType.TIDE -> 1.25f; CType.EMBER -> 0.75f; CType.SPARK -> 1.1f; else -> 1f }
        SUN -> when (t) { CType.EMBER -> 1.25f; CType.LEAF -> 1.15f; CType.FROST -> 0.75f; CType.TIDE -> 0.9f; else -> 1f }
        SNOW -> when (t) { CType.FROST -> 1.25f; CType.EMBER -> 0.8f; CType.LEAF -> 0.85f; else -> 1f }
        NIGHT -> when (t) { CType.SHADE -> 1.25f; CType.SPIRIT -> 1.2f; CType.GLOW -> 0.85f; else -> 1f }
        STORM -> when (t) { CType.SPARK -> 1.3f; CType.GALE -> 1.2f; CType.TIDE -> 1.1f; CType.STONE -> 0.9f; else -> 1f }
        else -> 1f
    }

    /** Which types the sky favours, for the opening banner. */
    fun favours(f: Int): String = when (f) {
        RAIN -> "Tide moves surge, Ember fizzles"
        SUN -> "Ember and Leaf moves surge, Frost wilts"
        SNOW -> "Frost moves surge, Ember and Leaf falter"
        NIGHT -> "Shade and Spirit moves surge, Glow dims"
        STORM -> "Spark, Gale and Tide moves surge"
        else -> ""
    }
}

/** One attack inside a battle. */
class Turn(
    val side: Int,
    val move: Move,
    val hit: Boolean,
    val damage: Int,
    val eff: Float,
    val crit: Boolean,
    val foeFainted: Boolean,
    /** Status newly put on the target by this hit (Battle.BURN etc.), 0 for none. */
    val inflicted: Int = 0,
    /** Damage the attacker took from its own burn or poison before acting. */
    val tick: Int = 0,
    /** The status that caused [tick]. */
    val tickStatus: Int = 0,
    /** True when the attacker lost its action (stunned, or collapsed from a burn or poison). */
    val skip: Boolean = false,
    /** A talent that fired this turn, e.g. "Blaze!", and the side that owns it (-1 for none). */
    val abil: String = "",
    val abilSide: Int = -1,
    /** How much the battlefield weather changed this hit (1 = not at all). */
    val fieldMul: Float = 1f,
)

/** Turn-by-turn fight between two critters. The caller plays each turn out on screen. */
class Battle(val a: Critter, val b: Critter, private val rnd: Random) {
    private val queue = ArrayDeque<Int>()

    /** The sky over the fight (Field.RAIN etc.), 0 for none. */
    var field = 0

    /** Hits side 0 can still take at half strength from a shield glyph. */
    var shield = 0

    /** Status of each side: 0 none, else one of the constants below. */
    val status = IntArray(2)
    private val statusLeft = IntArray(2)
    private val used = BooleanArray(2)
    private val burstUsed = BooleanArray(2)
    private val burstOn = BooleanArray(2)

    var over = false
        private set
    var winner = -1
        private set
    var rounds = 0
        private set

    fun side(i: Int): Critter = if (i == 0) a else b

    private fun order(): List<Int> {
        val fa = (a.spd * (if (status[0] == CHILL) 0.7f else 1f)).toInt() + rnd.nextInt(6) + (if (Abil.has(a, CType.GALE)) 12 else 0)
        val fb = (b.spd * (if (status[1] == CHILL) 0.7f else 1f)).toInt() + rnd.nextInt(6) + (if (Abil.has(b, CType.GALE)) 12 else 0)
        return if (fa >= fb) listOf(0, 1) else listOf(1, 0)
    }

    private fun pickMove(att: Critter, def: Critter): Move {
        val ms = att.moves
        var best = ms.first()
        var bestScore = -1f
        for (m in ms) {
            var score = m.power * (m.acc / 100f) * Dex.effectiveness(m.type, def.species) * Field.mul(field, m.type)
            if (att.species.types.contains(m.type)) score *= 1.3f
            score *= 0.75f + rnd.nextFloat() * 0.5f
            if (score > bestScore) {
                bestScore = score
                best = m
            }
        }
        return best
    }

    /** Resolves the next attack, or null when the fight is over. */
    fun next(): Turn? {
        if (over) return null
        if (queue.isEmpty()) {
            rounds++
            if (rounds > 40) {
                over = true
                winner = if (a.hpFrac >= b.hpFrac) 0 else 1
                return null
            }
            order().forEach { queue.addLast(it) }
        }
        val s = queue.removeFirst()
        val att = side(s)
        val def = side(1 - s)
        if (att.hp <= 0 || def.hp <= 0) {
            over = true
            winner = if (a.hp > 0) 0 else 1
            return null
        }
        // statuses bite before the creature acts
        val st = status[s]
        var tick = 0
        if (st == BURN || st == POISON) {
            tick = max(1, att.maxHp / (if (st == BURN) 14 else 12))
            att.hp = max(0, att.hp - tick)
            count[st]++
            tickTotal += tick
        }
        if (att.hp <= 0) {
            over = true
            winner = 1 - s
            return Turn(s, Dex.scuffle, false, 0, 1f, false, false, 0, tick, st, true)
        }
        if (st == STUN) {
            status[s] = 0
            return Turn(s, Dex.scuffle, false, 0, 1f, false, false, 0, 0, st, true)
        }
        if (st != 0) {
            statusLeft[s]--
            if (statusLeft[s] <= 0) status[s] = 0
        }
        var abil = ""
        var abilSide = -1
        fun fire(text: String, side: Int, kind: String) {
            abil = text
            abilSide = side
            abilCount[kind] = (abilCount[kind] ?: 0) + 1
        }
        if (Abil.has(att, CType.TIDE) && att.hp < att.maxHp) {
            val h = max(1, att.maxHp / 16)
            att.hp = min(att.maxHp, att.hp + h)
            fire("+$h Soothing Tide", s, "Soothing Tide")
        }
        if (Abil.has(att, CType.LEAF) && !used[s] && att.hpFrac < 0.5f) {
            used[s] = true
            val h = max(2, att.maxHp / 5)
            att.hp = min(att.maxHp, att.hp + h)
            fire("+$h Regrowth", s, "Regrowth")
        }
        if (!burstUsed[s] && att.bond >= BURST_BOND && att.hpFrac < 0.3f) {
            burstUsed[s] = true
            burstOn[s] = true
            status[s] = 0
            val h = max(2, att.maxHp * 3 / 10)
            att.hp = min(att.maxHp, att.hp + h)
            fire("Bond Burst! +$h", s, "Bond Burst")
        }
        val move = pickMove(att, def)
        var hit = rnd.nextInt(100) < move.acc
        if (hit && Abil.has(def, CType.GLOW) && rnd.nextInt(100) < 10) {
            hit = false
            fire("Dazzled!", 1 - s, "Dazzle")
        }
        if (hit && Abil.has(def, CType.SHADE) && rnd.nextInt(100) < 15) {
            hit = false
            fire("Evasive!", 1 - s, "Evasive")
        }
        if (hit && st == FRIGHT && rnd.nextInt(100) < 35) hit = false
        if (burstOn[s]) hit = true
        var dmg = 0
        var eff = 1f
        var crit = false
        var inflicted = 0
        var fm = 1f
        if (hit) {
            eff = Dex.effectiveness(move.type, def.species)
            crit = rnd.nextInt(100) < 6
            val l = att.level
            var d = ((2f * l / 5f + 2f) * move.power * att.atk / max(1, def.def) / 50f + 2f)
            if (att.species.types.contains(move.type)) d *= 1.3f
            d *= eff
            fm = Field.mul(field, move.type)
            d *= fm
            if (fm > 1f) fieldBoosts++ else if (fm < 1f) fieldWeakens++
            if (crit) d *= 1.5f
            if (st == BURN) d *= 0.8f
            if (st == CHILL) d *= 0.85f
            if (Abil.has(att, CType.EMBER) && att.hpFrac < 0.34f) {
                d *= 1.35f
                fire("Blaze!", s, "Blaze")
            }
            if (Abil.has(att, CType.PLAIN)) d *= 1.08f
            if (burstOn[s]) {
                d *= 1.5f
                burstOn[s] = false
            }
            if (move.tier >= 4 && abil.isEmpty()) fire("ULTIMATE!", s, "Ultimate")
            if (Abil.has(def, CType.IRON)) d *= 0.88f
            d *= 0.88f + rnd.nextFloat() * 0.12f
            if (s == 1 && shield > 0) {
                d *= 0.5f
                shield--
            }
            dmg = max(1, d.toInt())
            if (def.hp - dmg <= 0 && Abil.has(def, CType.STONE) && !used[1 - s]) {
                used[1 - s] = true
                dmg = max(0, def.hp - 1)
                fire("Sturdy!", 1 - s, "Sturdy")
            }
            def.hp = max(0, def.hp - dmg)
            if (def.hp > 0 && att.hp > 0) {
                if (Abil.has(att, CType.SPIRIT) && dmg > 0) {
                    val h = max(1, dmg * 15 / 100)
                    att.hp = min(att.maxHp, att.hp + h)
                    fire("+$h Soul Siphon", s, "Soul Siphon")
                }
                if (Abil.has(def, CType.VENOM)) {
                    val r = max(1, att.maxHp / 16)
                    att.hp = max(1, att.hp - r)
                    fire("-$r Barbs", 1 - s, "Toxic Barbs")
                }
                if (status[s] == 0 && Abil.has(def, CType.SPARK) && rnd.nextInt(100) < 20) {
                    status[s] = STUN
                    statusLeft[s] = 1
                    fire("Static!", 1 - s, "Static Skin")
                } else if (status[s] == 0 && Abil.has(def, CType.FROST) && rnd.nextInt(100) < 20) {
                    status[s] = CHILL
                    statusLeft[s] = 3
                    fire("Rime!", 1 - s, "Rime Coat")
                }
            }
            val kind = statusOf(move.type)
            if (kind != 0 && def.hp > 0 && status[1 - s] == 0) {
                val chance = when (move.tier) { 1 -> 14; 2 -> 22; 3 -> 32; else -> 45 }
                if (rnd.nextInt(100) < chance) {
                    status[1 - s] = kind
                    statusLeft[1 - s] = when (kind) { STUN -> 1; POISON -> 4; else -> 3 }
                    inflicted = kind
                    inflictedCount[kind]++
                }
            }
        }
        val fainted = def.hp <= 0
        if (fainted) {
            over = true
            winner = s
        }
        return Turn(s, move, hit, dmg, eff, crit, fainted, inflicted, tick, st, false, abil, abilSide, fm)
    }

    companion object {
        /** Bond a critter needs before it can find a last burst of strength. */
        const val BURST_BOND = 25
        const val BURN = 1
        const val POISON = 2
        const val STUN = 3
        const val CHILL = 4
        const val FRIGHT = 5

        /** Counters for the headless test: ticks of each status and how often each was inflicted. */
        val count = IntArray(6)
        val inflictedCount = IntArray(6)
        var tickTotal = 0

        /** Headless-test counters: hits boosted or dulled by the battlefield weather, and fights per field. */
        var fieldBoosts = 0
        var fieldWeakens = 0
        val fieldFights = IntArray(6)

        /** Headless-test counter: how often each talent fired. */
        val abilCount = HashMap<String, Int>()

        fun statusOf(t: CType): Int = when (t) {
            CType.EMBER -> BURN
            CType.VENOM -> POISON
            CType.SPARK -> STUN
            CType.FROST -> CHILL
            CType.SHADE -> FRIGHT
            else -> 0
        }

        fun statusName(st: Int): String = when (st) {
            BURN -> "burn"; POISON -> "poison"; STUN -> "stun"; CHILL -> "chill"; FRIGHT -> "fright"; else -> ""
        }

        fun statusColor(st: Int): Long = when (st) {
            BURN -> 0xFFFF7A2B
            POISON -> 0xFFB45FD9
            STUN -> 0xFFFFD23C
            CHILL -> 0xFF9FE3F5
            FRIGHT -> 0xFF6A4FA8
            else -> 0xFFFFFFFF
        }
    }
}
