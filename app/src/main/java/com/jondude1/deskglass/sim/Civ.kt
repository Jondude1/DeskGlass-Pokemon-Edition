package com.jondude1.deskglass.sim

import java.io.File
import java.util.Random
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The world: four times the area of the first versions. The village starts at the centre. */
const val GW = 96
const val GH = 54

/** The size of the original world, and of the stretch around the village where daily life still happens. */
const val OLD_GW = 48
const val OLD_GH = 27
const val DAY_LEN = 420.0
private const val SPEED = 2.3f
private const val YEAR = DAY_LEN * 2
/** Game days per season: spring, summer, autumn, winter, then round again. */
const val SEASON_DAYS = 3

object T {
    const val GRASS = 0
    const val FOREST = 1
    const val ROCK = 2
    const val WATER = 3
    const val BUSH = 4
    const val ORE = 5
}

class Building(val type: String, val x: Int, val y: Int, var era: Int = 0) {
    var boostUntil = 0.0
    var ready = 0
    var timer = 0f
    /** Sim time when villagers raised it; the view plays a construction animation for a few seconds. */
    var built = -999.0
}

class Persona(val trait: String, val voice: String, val dream: String, val lines: List<String>)

val JOBS = listOf(
    "Woodcutter", "Miner", "Forager", "Farmer", "Builder", "Scout", "Storyteller",
    "Hunter", "Bronzesmith", "Blacksmith",
)
private val JOB_MIN_AGE = mapOf("Farmer" to 1, "Bronzesmith" to 2, "Blacksmith" to 3)
private val JOB_MAX_AGE = mapOf("Bronzesmith" to 2)
val AGE_NAMES = listOf("Camp Age", "Hut Age", "Village Age", "Town Age", "City Age")

private val PERSONAS = listOf(
    Persona("a cautious planner who hates running out of food", "speaks carefully in short, anxious sentences and is always counting supplies", "a full storehouse that lasts the whole winter", listOf("Let's count the berries again.", "Better safe than hungry.")),
    Persona("a restless explorer who gets bored easily", "speaks in excited fragments and is always pointing somewhere", "to see what lies past the far mountains", listOf("I wonder what's over that ridge.", "I can't sit still today.")),
    Persona("a cheerful chatterbox who loves company", "is chatty, warm and full of exclamations", "a feast where the whole village sings", listOf("Isn't this the best village ever?", "Oh, I have to tell you something!")),
    Persona("a stubborn builder proud of every plank", "is gruff, proud and blunt", "a great hall with a proper roof", listOf("Built to last, that's how I do it.", "Nobody builds straighter than me.")),
    Persona("a dreamy poet who watches the sky", "is poetic and a little distant, always noticing the sky", "to write a song about the village", listOf("The clouds look like sleeping sheep.", "The light on the lake... lovely.")),
    Persona("a practical worker who says little", "is plain and brief and says only what matters", "a village that simply works", listOf("Work won't do itself.", "Fine.")),
    Persona("a nervous worrier who stockpiles things", "is jittery and always imagines what could go wrong", "to finally feel safe at night", listOf("Did you hear that?", "What if the berries run out?")),
    Persona("a bold leader who bosses others around", "is commanding and confident and gives orders", "to lead the village to greatness", listOf("Follow me, we have work to do.", "This village will be remembered.")),
    Persona("a kind caretaker who watches over the others", "is gentle and encouraging and asks how others feel", "that no one in the village goes hungry or lonely", listOf("Have you eaten today?", "You look tired, friend.")),
    Persona("a grumpy skeptic who doubts every plan", "is dry, sarcastic and complaining", "one day where absolutely nothing goes wrong", listOf("Hmph. We'll see about that.", "Of course it's raining.")),
)

private val JOB_BLURB = mapOf(
    "Woodcutter" to "You fell trees and haul timber. Wood is your pride, and you want the village to never run short of it.",
    "Miner" to "You dig stone from the rocks. Stone makes campfires and strengthens huts, and you take pride in it.",
    "Forager" to "You pick berries and fish the lake. You keep the village fed.",
    "Farmer" to "You tend the farms and the harvest. You know crops grow faster when someone looks after them.",
    "Builder" to "You raise huts, farms and campfires. The village grows only because you build.",
    "Scout" to "You roam far beyond the village, find caches and warn of wolves, and bring back news.",
    "Storyteller" to "You keep spirits up: you tell stories by the fire in the evening and chat with everyone.",
    "Hunter" to "You track and hunt game in the forest. Meat keeps the tribe fed before anyone knows farming.",
    "Bronzesmith" to "You mine ore and smelt bronze at the forge. Metal tools are the future of the village.",
    "Blacksmith" to "You mine ore and forge iron. Your tools are stronger than anything the village has had.",
    "Child" to "You are a small child. You play, ask questions and follow the grown-ups around.",
)

private val JOB_LINES = mapOf(
    "Woodcutter" to listOf("Another tree down.", "My axe needs a whetstone.", "Timber! ...sorry, habit.", "These oaks are stubborn."),
    "Miner" to listOf("Good solid rock here.", "My back aches, but the stone is fine.", "Stone never lies.", "Found a nice vein."),
    "Forager" to listOf("These berries are sweet today.", "The fish are biting.", "Never go hungry on my watch.", "More berries than yesterday."),
    "Farmer" to listOf("The crops look happy.", "A little water, a little patience.", "The soil here is rich.", "Harvest soon."),
    "Builder" to listOf("Another wall, another day.", "A hut needs a straight roof.", "We could fit one more house.", "Measure twice, hammer once."),
    "Scout" to listOf("I saw something beyond the hills.", "The wind is strange today.", "No wolves on the ridge. Yet.", "The world is big out there."),
    "Storyteller" to listOf("That reminds me of a tale...", "Gather round, friends.", "Every village needs its stories.", "Let me tell you a secret."),
    "Hunter" to listOf("I tracked a deer all morning.", "Quiet now, game ahead.", "Fresh tracks by the trees.", "Tonight we eat well."),
    "Bronzesmith" to listOf("The forge is hot today.", "Bronze rings when it's good.", "Copper and tin, that's the secret.", "Soon every tool will be metal."),
    "Blacksmith" to listOf("Iron beats bronze every time.", "Hammer, heat, hammer.", "This blade will outlast us all.", "The anvil sings."),
    "Child" to listOf("Play with me!", "Look, a beetle!", "I'm not tired!", "Are we there yet?", "Why is the sky blue?"),
)

private val STORIES = listOf(
    "Long ago a bear stole a whole winter of berries, and wept about it.",
    "There was once a tree so tall it scratched the moon.",
    "I once met a fish who swore it was a king.",
    "They say the old ruins sing when the wind is just right.",
    "A traveler once paid me in songs. I'm still rich.",
    "The first fire in this valley burned for a hundred days.",
)

class Villager(val id: Int, val name: String, var job: String, val personaIdx: Int) {
    val persona: Persona get() = PERSONAS[personaIdx % PERSONAS.size]
    val trait: String get() = persona.trait
    val colorIdx: Int get() = if (job == "Child") 6 else JOBS.indexOf(job).coerceAtLeast(0)
    var born = 0.0
    var lastTalk = -999.0
    var lifespan = 60
    var parents = ""
    var harmedBy = ""
    var harmedAt = -999.0
    var webAt = -999.0
    var x = 0f
    var y = 0f
    var hunger = 20f
    var energy = 90f
    var hp = 100f
    var happiness = 60f
    var action = ""
    var phase = "idle" // idle | walking | working | sleeping
    var path = IntArray(0)
    var pathIdx = 0
    var targetTile = -1
    var workLeft = 0f
    var gathered = 0
    var talkTo: Villager? = null
    var talkMsg = ""
    var thought = ""
    var say = ""
    var sayLeft = 0f
    var idleFor = 0f
    var waiting = false
    var facing = 1
    var replyIn = 0f
    var replyText = ""
    val heard = ArrayList<String>()
    val memories = ArrayList<String>()
    val recent = ArrayList<String>()
    val affinity = HashMap<Int, Int>()
    val said = ArrayList<String>()
    var replyTo: Villager? = null
    var partner = -1
    /** Lifetime deeds counted for the hero view: slain, rooms, gathered, built, fish, stories, posies, carved. */
    val st = HashMap<String, Int>()
    /** Earned epithets, oldest first ("the Wolfbane"). */
    val titles = ArrayList<String>()
    fun stat(k: String): Int = st[k] ?: 0
    fun bump(k: String, n: Int = 1) { st[k] = (st[k] ?: 0) + n }
    val epithet: String get() = titles.lastOrNull() ?: ""

    fun remember(text: String) {
        memories.add(text)
        while (memories.size > 6) memories.removeAt(0)
        addDiary("• $text")
    }

    /** The adventure log shown when the camera follows this villager. Newest last. */
    val diary = ArrayList<String>()

    fun addDiary(text: String) {
        if (diary.lastOrNull() == text) return
        diary.add(text)
        while (diary.size > 12) diary.removeAt(0)
    }
}

/** Whatever answers the villagers' questions: the local model, or Claude through the saved key. */
class Brain(val label: String, val gapSeconds: Double, val ask: (system: String, user: String) -> String)

private class Decision(val villagerId: Int, val text: String?, val error: String?, val reqId: Int = 0, val repaired: Boolean = false)

private val NAMES = listOf(
    "Mira", "Tobin", "Edda", "Rook", "Wren", "Bram", "Isla", "Finn", "Odette", "Cass", "Hugo", "Nell",
    "Pike", "Sorrel", "Dov", "Lark", "Ember", "Quill", "Tamsin", "Gideon", "Juno", "Alder", "Pip", "Vesna",
    "Maren", "Oswin", "Thea", "Corin", "Lio", "Yara", "Bastian", "Fenna", "Rhys", "Saga", "Orla", "Kit",
)
private val ACTIONS = setOf(
    "gather_wood", "gather_stone", "gather_food", "build_hut", "build_farm", "build_campfire",
    "eat", "sleep", "talk", "wander", "fish", "tend_farm", "explore", "tell_story", "huddle",
    "hunt", "mine_ore", "smelt", "build_smelter",
    "build_well", "build_workshop", "build_market", "build_lamp",
    "fetch_water", "craft", "trade", "dance", "patrol", "play",
    "build_granary", "build_barracks", "build_library", "build_wonder", "haul", "drill", "study", "attack",
    "forage_herbs", "swim", "stargaze", "carve", "pick_flowers", "delve", "build_tavern", "tavern", "build_infirmary", "recover", "build_totem", "honor", "mourn", "build_ranch", "tend_critters", "build_pasture", "tend_flock", "build_dock", "sail", "build_loom", "weave", "snowman", "rake_leaves", "fly_kite", "puddle_jump", "catch_fireflies", "hide_seek", "walk_dog", "assault_camp", "gather_star", "build_quarry", "quarry", "build_mill", "grind", "build_shrine", "vigil", "revel", "teach", "raise_cathedral", "build_apiary", "tend_bees", "build_orchard", "pick_fruit", "council", "douse", "build_range", "shoot", "build_bath", "build_belfry", "build_stage", "show", "soak", "build_kiln", "fire_pots", "build_yard", "train_critters",
)

/** Foes a few healthy fighters will chase down on their own. */
private val HUNTABLE_FOES = setOf("boar", "wolf", "bear", "pilferer", "frostwolf", "raider", "skeleton", "shaman", "reaver", "spider", "harpy", "burrower")

private val DX = intArrayOf(1, -1, 0, 0)
private val DY = intArrayOf(0, 0, 1, -1)

/** A tiny village that runs itself. A local language model picks what each villager does next. */
class CivSim(
    private val saveFile: File,
    private val scope: CoroutineScope,
    private val brainProvider: () -> Brain?,
) {
    val tiles = IntArray(GW * GH)
    val amt = IntArray(GW * GH)
    val buildings = ArrayList<Building>()
    val villagers = ArrayList<Villager>()
    val log = ArrayList<String>()
    var wood = 4
    var stone = 2
    var food = 6
    var ore = 0
    var metal = 0
    var totalSmelted = 0
    var explored = 0
    var age = 0
    var outsideRain = false
    var outsideSnow = false
    var stormUntil = 0.0; var fogUntil = 0.0; var snowUntil = 0.0; var rainbowUntil = 0.0
    /** The Long Dry: a summer drought that withers unwatered crops until the wells, buckets or rain save them. */
    var droughtUntil = 0.0; var droughtStart = 0.0; var droughtWaterUntil = 0.0; private var droughtOn = false
    var droughts = 0; var droughtsEnded = 0; var droughtWaterings = 0; var droughtSaved = 0; var droughtWithered = 0; var droughtRainEnds = 0
    fun drought() = time < droughtUntil
    /** 0..1: how parched the land looks right now (fades in and out at the edges of the dry spell). */
    fun droughtLevel(): Float = if (!drought()) 0f else minOf(1.0, (time - droughtStart) / 25.0, (droughtUntil - time) / 25.0).toFloat().coerceIn(0f, 1f)
    /** True while the farms have been freshly watered during a drought. */
    fun farmsWatered() = time < droughtWaterUntil
    var strikeAt = -99.0; var strikeX = 0f; var strikeY = 0f
    private var nextWeatherAt = DAY_LEN * 0.5; private var nextStrikeAt = 0.0; private var lastSeason = -1; private var wasStorm = false
    /** Counts of weather and season events seen this run (used by the headless harness). */
    val wxStats = HashMap<String, Int>()
    val foeStats = HashMap<String, Int>()
    var warlordsSlain = 0
    var golemsSlain = 0
    var shamansSlain = 0
    var frostPacksBroken = 0
    var pilfersFoiled = 0
    var pilfersLost = 0
    var swarmsBroken = 0
    var hornetsSwatted = 0
    var lichesSlain = 0
    var bonesRaised = 0
    var reaversSlain = 0
    var trollsSlain = 0
    var trollsStoned = 0
    var spidersSlain = 0
    var wormsSlain = 0
    var wormErupts = 0
    var wormHits = 0
    private var lastWormAt = -999.0
    /** Craters left where a burrower erupted: x, y, born. They fill in again after a while. */
    val craters = mutableListOf<FloatArray>()
    var harpiesSlain = 0
    var flocksScattered = 0
    var harpyDives = 0
    var harpyLoot = 0
    private var lastHarpyAt = -999.0
    var websSpun = 0
    var webCatches = 0
    var spiderBites = 0
    private var lastSpiderAt = -999.0
    /** Sticky webs on the ground: x, y, born. They slow whoever walks through and rot away. */
    val webs = mutableListOf<FloatArray>()
    /** Villager id -> time the spider venom wears off. */
    val poisoned = HashMap<Int, Double>()
    var trollBoulders = 0
    private var lastTrollAt = -999.0
    /** Petrified trolls left standing where the dawn caught them: x, y. */
    val trollRocks = mutableListOf<FloatArray>()
    var shipsSeized = 0
    var landings = 0
    private var lastLandingAt = -999.0
    /** A reaver longship moored off the shore while its crew is ashore. */
    var shipUp = false
    var shipX = 0f
    var shipY = 0f
    var shipDx = 1f
    var shipDy = 0f
    var shipT = 0f
    private var lastLichAt = -999.0
    private var lastWarlordAt = -999.0
    val chronicle = ArrayList<String>()
    var selfDefence = 0
    val deathCauses = mutableMapOf<String, Int>()
    val deathNotes = mutableListOf<String>()
    var time = DAY_LEN * 0.08
    /** Time multiplier for the watcher: 1, 2 or 5. Not saved. */
    @Volatile var speed = 1
    var brain = "starting…"
        private set

    var onNarrate: (String) -> Unit = {}
    var onError: (String) -> Unit = {}

    private val occupied = BooleanArray(GW * GH)
    /** Tiles holding a piece of the stone town wall or a gate post (slows foes that reach it). */
    val wallAt = BooleanArray(GW * GH)
    val wallPlan = ArrayList<Int>()
    var wallCx = -1
    var wallCy = -1
    var wallsRaised = 0
    var wallHoldSecs = 0f
    var wallDone = false
    private var nextStoneWallAt = 0.0
    private val reserved = HashSet<Int>()
    private var seed = System.currentTimeMillis()
    private var rnd = Random(seed)
    private val results = ConcurrentLinkedQueue<Decision>()
    private var inFlight = false
    private var retryAt = 0.0
    private var nextAskAt = 0.0
    private var lastBirth = 0.0
    private var farmClock = 0f
    private var bushClock = 0f
    private var nextId = 1
    private var godMsg = ""
    private var reqSeq = 0
    private var curReq = 0
    private var sentAt = 0.0
    private var okCount = 0
    private var fixCount = 0
    private var failCount = 0
    private var plagueUntil = 0.0
    var swims = 0
    /** Seasonal pastimes: snowmen in winter, leaf piles in autumn, kites in spring. */
    class SeasonProp(val kind: String, val x: Float, val y: Float, val born: Double)
    /** A village dog: it was a stray, a villager named it, and now it follows them everywhere. */
    val pets = ArrayList<Pet>()
    var dogsAdopted = 0
    var dogWalks = 0
    var dogBarks = 0
    private var nextPetAt = DAY_LEN * 1.2
    private val DOG_NAMES = listOf("Biscuit", "Bramble", "Patch", "Pepper", "Moss", "Ember", "Scout", "Clover", "Juniper", "Tuck", "Nibs", "Sable", "Dusty", "Maple", "Rusty", "Fidget")
    val seasonProps = ArrayList<SeasonProp>()
    /** Heroes' Row: a statue for each villager who earned an epithet. kind is the stat behind the title; tier 0 wood, 1 stone, 2 gilded. */
    class Statue(val name: String, val title: String, val kind: String, val x: Float, val y: Float, val born: Double, val tier: Int)
    val statues = ArrayList<Statue>()
    var statueCheers = 0
    private var nextStatueAt = 0.0
    /** The village graveyard: every villager who dies is laid to rest here and mourners bring flowers. */
    class Grave(val name: String, val job: String, val x: Float, val y: Float, val ageYears: Int, val cause: String, val day: Int, var tribute: Double = -9999.0)
    val graves = ArrayList<Grave>()
    var graveX = -1
    var graveY = -1
    var buried = 0
    var mournVisits = 0
    var lastBuryAt = -9999.0
    val graveSlots = 24
    var snowmenBuilt = 0
    /** Seasonal fetes: a maypole, midsummer bonfire, harvest table or winter lantern tree raised on the green on the last afternoon of each season. */
    /** Village Council: adults meet at the hall (or fire), vote a priority from real stock and threats, and it steers their choices for about a day. */
    var councilOn = false
    var councilX = 0f
    var councilY = 0f
    var councilStart = 0.0
    var councilUntil = 0.0
    var councilNextAt = 0.0
    /** Age of Gold: once the Golden Age begins the hall issues a golden decree every day or so. */
    var gloryNextAt = 0.0
    var decrees = 0
    var decreeKind = ""
    var decreeStart = 0.0
    var decreeUntil = 0.0
    var gildings = 0
    var decreeKinds = HashSet<String>()
    var lastDecree = ""
    var councilsHeld = 0
    var councilSeats = 0
    var policy = ""
    var policyUntil = 0.0
    var policyStart = 0.0
    var policyBase = 0f
    var policyVotes = 0
    var policyOf = 0
    var policyActs = 0
    var policiesWon = 0
    var policiesLost = 0
    val policyKinds = LinkedHashSet<String>()
    private var lastPolicy = ""
    private val councilGuestIds = HashSet<Int>()
    var feteOn = false
    var feteKind = ""
    var feteX = 0f
    var feteY = 0f
    var feteStart = 0.0
    var feteUntil = 0.0
    var lastFeteSeason = -1
    var fetesHeld = 0
    var feteRevels = 0
    val feteKinds = HashSet<String>()
    private val feteGuestIds = HashSet<Int>()
    var leafPilesRaked = 0
    var kitesFlown = 0
    // children's games (puddles, fireflies, hide and seek)
    var puddleJumps = 0
    var fireflyJars = 0
    var hideSeekGames = 0
    var hideFinds = 0
    val kidGameKinds = HashSet<String>()
    /** Mentoring: lessons given, apprentices who followed a mentor's trade, and who is learning what (child id to "trade|mentor"). */
    var lessonsGiven = 0
    var apprenticesMade = 0
    val apprentice = HashMap<Int, String>()
    var herbed = 0
    var stargazed = 0
    // ---- fallen stars: a streak crosses the night sky, lands near the village and leaves shards of starmetal to gather ----
    /** Time of the impact (the streak is drawn in the 3.5 s before it). */
    var starFallAt = -100.0
    var starX = 0f
    var starY = 0f
    var starShards = 0
    var starUntil = 0.0
        private set
    private var starAnnounced = true
    private var nextStarAt = DAY_LEN * 0.9
    var starsFallen = 0

    // ---- wildfires: lightning and dry summers light the forest; villagers douse the flames ----
    class Blaze(val i: Int, val born: Double, var hp: Float, var nextSpread: Double, var nextSpark: Double)
    val blazes = ArrayList<Blaze>()
    /** Scorched ground left behind: x, y, until. */
    val scorches = ArrayList<DoubleArray>()
    var firesStarted = 0
    var firesDoused = 0
    var fireDouses = 0
    var fireBurnedOut = 0
    var fireLoss = 0
    private var nextEmberAt = 0.0
    fun fireOn(): Boolean = blazes.isNotEmpty()
    var starShardsGot = 0
    fun starIncoming(): Boolean = starShards > 0 && time < starFallAt
    fun starReady(): Boolean = starShards > 0 && time >= starFallAt && time < starUntil
    var carved = 0
    // bandit camp: an outlaw hideout beyond the hills that sends raiders until the village storms it
    var campUp = false
    var campX = 0f
    var campY = 0f
    var campHp = 0f
    var campMax = 100f
    var campRazedAt = -999.0
    var campRazedX = 0f
    var campRazedY = 0f
    var campHitAt = -999.0
    var campsRazed = 0
    var campHits = 0
    var campSorties = 0
    private var campNextAt = DAY_LEN * 1.5
    private var campSortieAt = 0.0
    // rift expeditions: a party walks to a violet rift, clears four rooms and brings back loot and relics
    var riftState = 0 // 0 closed, 1 party gathering, 2 delving
    var riftX = 0f
    var riftY = 0f
    var riftRoom = 0
    var riftRoomT = 0f
    var riftOpenedAt = -999.0
    var riftResultAt = -999.0
    var riftWon = false
    // dungeon depth: side chambers between fights, one rally after a lost fight, a two-phase Warden
    var riftSide = false
    var riftSideKind = 0 // last side chamber: 1 trap, 2 vault, 3 spring, 4 shrine
    var riftSideAt = -999.0
    var riftRallied = false
    var riftBoost = 0f
    var riftWardenPhase = 0
    var sideRooms = 0
    var sideTraps = 0
    var sideVaults = 0
    var sideSprings = 0
    var sideShrines = 0
    var riftRallies = 0
    var riftRoomsDone = 0
    val party = ArrayList<Int>()
    private var nextRiftAt = DAY_LEN * 1.0
    var expeditions = 0
    var expWins = 0
    var expRetreats = 0
    var roomsCleared = 0
    var relics = 0
    var deepestRoom = 0
    var wardensSlain = 0
    var posies = 0
    var wastedGathers = 0
    var smartSurplus = true
    private val problemAt = HashMap<String, Long>()
    private val problemSkipped = HashMap<String, Int>()
    private var godUntil = 0.0
    private var lastNarrateAt = 0L
    private var lastErrorLog = 0L

    // kingdoms, world ages and god powers (declared before init so loading can use them)
    val kingdoms = ArrayList<Kingdom>()
    val rels = HashMap<String, Rel>()
    val biome = IntArray(GW * GH)
    val owner = IntArray(GW * GH) { -1 }
    val fx = ArrayList<Fx>()
    val predators = ArrayList<Predator>()
    private val fleeAt = HashMap<Int, Double>()
    var hides = 0
    val drops = ArrayList<Drop>()
    /** Wild critters, companions, helpers, training and evolution. */
    val critters = CritterWorld(this)
    /** Ordinary animals that make the land feel alive. Not saved. */
    val fauna = Fauna(this)
    /** A travelling guest (merchant, bard or pilgrim) who walks in, visits the fire and walks on. */
    class Visitor(val kind: String, var x: Float, var y: Float, val born: Double, val exitX: Float, val exitY: Float) {
        var arrivedAt = -1.0
        var leaving = false
        var stuck = 0f
    }
    var visitor: Visitor? = null
    private var nextVisitAt = 0.0
    val visitStats = HashMap<String, Int>()
    var knightDuels = 0
    var toysGiven = 0
    /** Villagers wearing a flower crown after their coming-of-age day (id -> crown ends). Not saved. */
    private val crowns = HashMap<Int, Double>()
    var comingAges = 0
    var comingGuests = 0
    var comingGifts = 0
    var comingParents = 0
    fun visitTotal(): Int = visitStats.values.sum()
    /** Worn footpaths: how often each tile has been walked. */
    val traffic = FloatArray(GW * GH)
    /** Things made at the workshop, sold at the market. */
    var goods = 0
    var raidsRepelled = 0
    var ogresSlain = 0
    var bossesSlain = 0
    var goldenAt = -1.0
    private var nextThreatAt = DAY_LEN * 1.4
    private var lastBossAt = -999.0
    private var lastOgreAt = -999.0
    private var studied = 0

    // ---- library research: scholars earn lore points, the village unlocks discoveries ----
    class Tech(val id: String, val name: String, val need: Int, val cost: String, val text: String, val minAge: Int = 0)
    val allTechs: List<Tech> = listOf(
        Tech("farm", "Crop Rotation", 150, "150 food", "Berries and farm harvests yield +1 food."),
        Tech("saws", "Two-Man Saws", 450, "120 wood", "Every load of wood is one log bigger."),
        Tech("quarry", "Deep Quarrying", 900, "120 stone", "Stone and ore loads are one bigger."),
        Tech("herbal", "Herbal Medicine", 1500, "200 food", "Infirmaries heal much faster."),
        Tech("vault", "Granary Vaults", 2300, "200 stone, 100 wood", "Every store holds a fifth more."),
        Tech("siege", "Siege Craft", 3300, "10 metal, 100 wood", "Ballistae shoot faster and hit harder."),
        Tech("stars", "Star Charts", 4600, "250 stone", "Wonders bless twice as strongly; scouts find richer caches."),
        // higher learning: opens in the Town Age once the first seven are known
        Tech("mill2", "Geared Millstones", 5600, "150 wood, 40 stone", "Every grinding at the windmill yields two more meals.", 3),
        Tech("hives", "Skep Husbandry", 6800, "120 wood, 30 food", "Honey harvests yield two more meals.", 3),
        Tech("roads", "Paved Roads", 8200, "150 stone", "Everyone walks a twelfth faster.", 3),
        Tech("blades", "Forged Blades", 9800, "12 metal, 100 wood", "Fighters hit harder; the bandit camp falls faster.", 3),
        Tech("feast", "Feast Craft", 11500, "200 food, 60 wood", "Loaves, mead and cider feasts come round more often.", 3),
        Tech("scribes", "Copyists' Hall", 13500, "200 wood, 100 stone", "Every reading adds an extra page of lore.", 3),
        Tech("marvels", "Age of Marvels", 15800, "250 stone, 250 wood", "Each load at the cathedral counts for more.", 3),
    )
    val techs = LinkedHashSet<String>()
    var research = 0
    var studyCount = 0
    var discoveryFx = -100.0
    private var nextResearchAt = 0.0
    fun hasTech(id: String) = id in techs
    fun nextTech(): Tech? = allTechs.firstOrNull { it.id !in techs }?.takeIf { age >= it.minAge }
    /** Discoveries of the higher learning that the scholars have made. */
    fun higherTechs(): Int = allTechs.count { it.minAge > 0 && it.id in techs }
    var tavernVisits = 0
    /** Tavern nights: dice, arm-wrestling, tall tales from the chronicle, sing-alongs and a lantern night every 7th visit. */
    var tavDice = 0
    var tavWrestles = 0
    var tavTales = 0
    var tavSongs = 0
    var tavNights = 0
    var tavernFx = -100.0
    var tavernFxKind = 0
    var hungerSnacks = 0
    var recoveries = 0
    var ballistaShots = 0
    var ballistaKills = 0
    var totemVisits = 0
    var ranchVisits = 0
    /** The village pasture: sheep and hens that villagers tend (eggs, wool). */
    var pastureVisits = 0
    /** The fishing dock: boats row out for bigger catches, and now and then bring back a sealed bottle. */
    var sailTrips = 0
    var dockCatch = 0
    var bottlesFound = 0
    /** The stone quarry: villagers cut blocks from the pit (stone, now and then ore or fine goods). */
    var quarryHauls = 0
    /** The windmill: grain ground to flour, and the loaves baked from it. */
    var millGrinds = 0
    /** The apiary: straw hives fill with honey over time; villagers harvest it (food, goods, mead feasts). */
    var honeyHarvests = 0
    var honeyJars = 0
    var beeStings = 0
    var meadFeasts = 0
    var honeyMark = 0.0
    /** The orchard: fruit trees blossom in spring, bear in summer and autumn; pickers fill baskets, cider feasts follow. */
    var fruitPicks = 0
    var fruitBaskets = 0
    var ciderFeasts = 0
    var fruitFalls = 0
    /** The archery range: villagers shoot at straw targets, sharpen their Fighting skill and hold friendly contests. */
    var shotsFired = 0
    /** The hot spring: soaks heal and cheer; every tenth is a steamy party. */
    var soaks = 0
    var soakHeals = 0
    var soakParties = 0
    var soakGossips = 0
    /** The bell tower: dawn/dusk peals cheer, alarm peals rally the fighters. */
    var bellRings = 0
    var bellAlarms = 0
    var bellReach = 0
    /** The puppet theatre: shows cheer the audience around the stage. */
    var shows = 0
    var grandShows = 0
    var showAudience = 0
    private var lastShowAt = -999.0
    /** The potter's kiln: wood and clay become pots and goods; every fifth firing is a glazed masterwork. */
    var kilnFires = 0
    var kilnGlazed = 0
    var kilnCheers = 0
    private var lastKilnAt = -999.0
    /** The critter training yard: drilled critters gain levels; sparring pairs bond. */
    var yardSessions = 0
    var yardSpars = 0
    private var lastYardAt = -999.0
    /** The weaver's loom: cloth from hides, and woollen cloaks that villagers visibly wear. */
    var loomWeaves = 0
    var loomCloaks = 0
    private var lastLoomAt = -999.0
    private var lastBellAt = -999.0
    var bullseyes = 0
    var archeryMatches = 0
    var archeryBest = ""
    private val rangeScore = HashMap<Int, Int>()
    var fruitMark = 0.0
    var flourMade = 0
    var loavesBaked = 0
    var quarryStone = 0
    /** The Great Cathedral: a five-tier City Age project raised from spare wood and stone; the last tier starts a Jubilee. */
    var cathX = -1
    var cathY = -1
    var cathWork = 0
    var cathTrips = 0
    var cathBells = 0
    var jubileeUntil = -1.0
    var cathFlash = -100.0
    private var cathTryAt = 0.0
    private var nextCathAt = 0.0
    private var lastBellDay = 0
    var quarryVeins = 0
    /** The Legend Shrine: vigils there fill a gauge; when it is full a legend of the chain answers and steps out. */
    var vigils = 0
    var vigilPoints = 0
    var legendCalls = 0
    var shrineGlowUntil = 0.0
    var shrineCallUntil = 0.0
    var shrineCallSp = 57
    fun vigilNeed(): Int = min(90, 24 + 12 * legendCalls)
    private var dockSiteAt = -999.0
    private var dockSiteVal = -1
    var flockSheep = 0
    var flockHens = 0
    var eggsGathered = 0
    var fleeces = 0
    /** Buildings raised by villagers (each plays a visible construction animation). */
    var raised = 0
    var totemBlessUntil = -999.0
    var omens = 0
    private var nextInfirmaryAt = 0.0
    var ageBannerAt = -999.0
    var ageBannerName = ""
    val deeds = LinkedHashSet<String>()
    var babiesBorn = 0
    // relationships: weddings, best friends and feuds
    var weds = 0
    var feudsStarted = 0
    var feudsEnded = 0
    val bffs = LinkedHashSet<String>()
    val feudEnds = HashMap<String, Double>()
    private val courting = HashMap<String, Double>()
    private var nextRelAt = 0.0
    var weddingAt = -999.0
    var weddingX = 0f
    var weddingY = 0f
    var weddingNames = ""
    var deedBannerAt = -999.0
    var deedBannerName = ""
    var deedBannerTag = ""
    var deedBannerSub = ""
    private var nextDeedAt = 0.0
    private var worldClock = 0f
    private var ownerClock = 0f
    private var nextWorldEventAt = 0.0
    private var shakeUntil = 0.0

    init {
        if (!load()) generate()
        setupWorld()
    }

    // ---- world ----

    private fun noise(r: Random, passes: Int): FloatArray {
        var a = FloatArray(GW * GH) { r.nextFloat() }
        repeat(passes) {
            val b = FloatArray(GW * GH)
            for (y in 0 until GH) for (x in 0 until GW) {
                var s = 0f
                var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx
                    val yy = y + dy
                    if (xx in 0 until GW && yy in 0 until GH) {
                        s += a[yy * GW + xx]
                        n++
                    }
                }
                b[y * GW + x] = s / n
            }
            a = b
        }
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (v in a) {
            mn = min(mn, v)
            mx = max(mx, v)
        }
        val span = max(0.0001f, mx - mn)
        for (i in a.indices) a[i] = (a[i] - mn) / span
        return a
    }

    private fun generate() {
        rnd = Random(seed)
        buildings.clear()
        villagers.clear()
        log.clear()
        occupied.fill(false)
        wallAt.fill(false)
        wallPlan.clear(); wallCx = -1; wallCy = -1; wallsRaised = 0; wallHoldSecs = 0f; wallDone = false; nextStoneWallAt = 0.0
        reserved.clear()
        wood = 4
        stone = 2
        food = 6
        ore = 0
        metal = 0
        totalSmelted = 0
        explored = 0
        age = 0
        chronicle.clear()
        predators.clear()
        time = DAY_LEN * 0.08
        lastBirth = time
        hides = 0
        puddleJumps = 0; fireflyJars = 0; hideSeekGames = 0; hideFinds = 0; kidGameKinds.clear()
        goods = 0
        visitor = null; nextVisitAt = time + DAY_LEN * 1.2; visitStats.clear(); knightDuels = 0; toysGiven = 0; raidsRepelled = 0; ogresSlain = 0; bossesSlain = 0; warlordsSlain = 0; golemsSlain = 0; shamansSlain = 0; frostPacksBroken = 0; pilfersFoiled = 0; pilfersLost = 0; swarmsBroken = 0; hornetsSwatted = 0; lichesSlain = 0; bonesRaised = 0; lastLichAt = -999.0; reaversSlain = 0; trollsSlain = 0; trollsStoned = 0; trollBoulders = 0; spidersSlain = 0; wormsSlain = 0; wormErupts = 0; wormHits = 0; lastWormAt = -999.0; craters.clear(); harpiesSlain = 0; flocksScattered = 0; harpyDives = 0; harpyLoot = 0; lastHarpyAt = -999.0; websSpun = 0; webCatches = 0; spiderBites = 0; lastSpiderAt = -999.0; webs.clear(); poisoned.clear(); skillUps = 0; skillBonusLoads = 0; skillMasters = 0; lastTrollAt = -999.0; trollRocks.clear(); shipsSeized = 0; landings = 0; lastLandingAt = -999.0; shipUp = false; shipT = 0f; foeStats.clear(); lastWarlordAt = -999.0; goldenAt = -1.0; deeds.clear(); babiesBorn = 0; weds = 0; feudsStarted = 0; feudsEnded = 0; bffs.clear(); feudEnds.clear(); courting.clear(); weddingAt = -999.0; nextRelAt = 0.0; deedBannerAt = -999.0; nextDeedAt = 0.0; titlesEarned = 0; dreamsDone = 0; lastThreatAt = 0.0; crowns.clear(); comingAges = 0; comingGuests = 0; comingGifts = 0; comingParents = 0; tavDice = 0; tavWrestles = 0; tavTales = 0; tavSongs = 0; tavNights = 0; tavernFx = -100.0
        riftState = 0; party.clear(); expeditions = 0; expWins = 0; expRetreats = 0; roomsCleared = 0; relics = 0; deepestRoom = 0; wardensSlain = 0; sideRooms = 0; sideTraps = 0; sideVaults = 0; sideSprings = 0; sideShrines = 0; riftRallies = 0; riftSide = false; nextRiftAt = time + DAY_LEN
        campUp = false; campsRazed = 0; campHits = 0; campSorties = 0; campRazedAt = -999.0; campHitAt = -999.0; campNextAt = time + DAY_LEN * 1.5
        nextThreatAt = DAY_LEN * 1.4; lastBossAt = -999.0
        traffic.fill(0f)
        fauna.reset()
        drops.clear()
        ageBannerAt = -999.0
        fx.clear()
        statues.clear(); statueCheers = 0; nextStatueAt = 0.0
        cathX = -1; cathY = -1; cathWork = 0; cathTrips = 0; cathBells = 0; jubileeUntil = -1.0; cathFlash = -100.0; cathTryAt = 0.0; nextCathAt = 0.0; lastBellDay = 0
        graves.clear(); graveX = -1; graveY = -1; buried = 0; mournVisits = 0; lastBuryAt = -9999.0
        pets.clear(); dogsAdopted = 0; dogWalks = 0; dogBarks = 0; nextPetAt = time + DAY_LEN * 1.2
        // a new world must not inherit the old world's clocks (alerts, rain, festivals...)
        blazes.clear(); scorches.clear(); firesStarted = 0; firesDoused = 0; fireDouses = 0; fireBurnedOut = 0; fireLoss = 0; nextEmberAt = 0.0; vigils = 0; vigilPoints = 0; legendCalls = 0; shrineGlowUntil = 0.0; shrineCallUntil = 0.0; alertUntil = 0.0; starShards = 0; starsFallen = 0; starShardsGot = 0; starUntil = 0.0; starAnnounced = true; rainUntil = 0.0; stormUntil = 0.0; fogUntil = 0.0; snowUntil = 0.0; rainbowUntil = 0.0; droughtUntil = 0.0; droughtStart = 0.0; droughtWaterUntil = 0.0; droughtOn = false; droughts = 0; droughtsEnded = 0; droughtWaterings = 0; droughtSaved = 0; droughtWithered = 0; droughtRainEnds = 0; strikeAt = -99.0; nextWeatherAt = time + DAY_LEN * 0.5; lastSeason = -1; wxStats.clear(); festivalUntil = 0.0; feteOn = false; feteUntil = 0.0; lastFeteSeason = -1; fetesHeld = 0; feteRevels = 0; lessonsGiven = 0; apprenticesMade = 0; apprentice.clear(); feteKinds.clear(); feteGuestIds.clear(); plagueUntil = 0.0; godUntil = 0.0; order = ""; orderUntil = 0.0; shakeUntil = 0.0
        councilOn = false; councilUntil = 0.0; councilNextAt = 0.0; gloryNextAt = 0.0; decrees = 0; decreeKind = ""; decreeUntil = 0.0; decreeStart = 0.0; gildings = 0; decreeKinds.clear(); lastDecree = ""; councilsHeld = 0; councilSeats = 0; policy = ""; policyUntil = 0.0; policyActs = 0; policiesWon = 0; policiesLost = 0; policyVotes = 0; policyOf = 0; policyKinds.clear(); councilGuestIds.clear()
        nextEventAt = time + DAY_LEN * 0.6
        nextTowerAt = 0.0; nextWallAt = 0.0; nextPlanAt = 0.0
        ageClock = 0f; worldClock = 0f; ownerClock = 0f; bushClock = 0f
        nextWorldEventAt = time + 60.0
        fleeAt.clear()
        val e = noise(rnd, 4)
        val f = noise(rnd, 3)
        val g = noise(rnd, 3)
        val cx = GW / 2
        val cy = GH / 2
        for (y in 0 until GH) for (x in 0 until GW) {
            val i = y * GW + x
            val d = hypot((x - cx).toFloat(), (y - cy).toFloat())
            tiles[i] = T.GRASS
            amt[i] = 0
            if (d < 6f) continue
            when {
                e[i] < 0.22f -> tiles[i] = T.WATER
                f[i] > 0.62f -> { tiles[i] = T.FOREST; amt[i] = 5 }
                g[i] > 0.80f -> { tiles[i] = T.ROCK; amt[i] = 6 }
                rnd.nextFloat() < 0.035f -> { tiles[i] = T.BUSH; amt[i] = 3 }
            }
        }
        for (k in 0 until 4) {
            val a = rnd.nextFloat() * 6.28f
            val i = (cy + (Math.sin(a.toDouble()) * 7).toInt()) * GW + cx + (Math.cos(a.toDouble()) * 7).toInt()
            tiles[i] = T.BUSH
            amt[i] = 3
        }
        ensure(T.FOREST, 5, 40)
        ensure(T.ROCK, 6, 14)
        ensure(T.BUSH, 3, 14)
        addBuilding("campfire", cx, cy)
        // two starter huts so the first nights are safe and warm
        addBuilding("hut", cx - 3, cy + 2)
        addBuilding("hut", cx + 3, cy + 2)
        for (k in 0 until 4) spawnVillager()
        addLog("A small band of villagers settles by a fire.")
        mark("A small band of villagers settled by a fire.")
    }

    private fun ensure(type: Int, amount: Int, wanted: Int) {
        var count = tiles.count { it == type }
        var guard = 0
        while (count < wanted && guard++ < 3000) {
            val x = rnd.nextInt(GW)
            val y = rnd.nextInt(GH)
            val i = y * GW + x
            if (tiles[i] == T.GRASS && hypot((x - GW / 2).toFloat(), (y - GH / 2).toFloat()) > 7f) {
                tiles[i] = type
                amt[i] = amount
                count++
            }
        }
    }

    private fun addBuilding(type: String, x: Int, y: Int, era: Int = age) {
        buildings.add(Building(type, x, y, era))
        occupied[y * GW + x] = true
        if (type == "wall" || type == "gate") wallAt[y * GW + x] = true
    }

    private fun spawnVillager(parent: Villager? = null, other: Villager? = null): Villager {
        val used = villagers.map { it.name }.toSet()
        val pool = NAMES.filter { it !in used }.ifEmpty { NAMES }
        val name = pool[rnd.nextInt(pool.size)]
        val baby = parent != null
        val first = listOf("Woodcutter", "Hunter", "Forager", "Builder")
        val job = when {
            baby -> "Child"
            else -> first.getOrNull(villagers.size)?.takeIf { jobOk(it) } ?: chooseJob()
        }
        val usedP = villagers.map { it.personaIdx }.toSet()
        val freeP = PERSONAS.indices.filter { it !in usedP }.ifEmpty { PERSONAS.indices.toList() }
        val persona = if (baby && rnd.nextBoolean()) (if (rnd.nextBoolean()) parent!!.personaIdx else (other ?: parent!!).personaIdx)
        else freeP[rnd.nextInt(freeP.size)]
        val v = Villager(nextId++, name, job, persona)
        v.lifespan = 55 + rnd.nextInt(15)
        v.born = if (baby) time else time - (16 + rnd.nextInt(15) + rnd.nextFloat()) * YEAR
        if (baby) v.parents = "${parent!!.name} & ${(other ?: parent).name}"
        val fire = buildings.firstOrNull { it.type == "campfire" }
        v.x = (parent?.x ?: ((fire?.x ?: GW / 2) + 0.5f)) + (rnd.nextFloat() - 0.5f) * 3f
        v.y = (parent?.y ?: ((fire?.y ?: GH / 2) + 0.5f)) + (rnd.nextFloat() - 0.5f) * 3f
        villagers.add(v)
        return v
    }

    fun restart() {
        seed = System.currentTimeMillis()
        nextId = 1
        critters.reset()
        generate()
        setupWorld()
        save()
    }

    // ---- time ----

    fun day(): Int = (time / DAY_LEN).toInt() + 1
    private fun frac(): Double = (time % DAY_LEN) / DAY_LEN
    fun isNight(): Boolean = frac().let { it >= 0.72 || it < 0.04 }

    fun partOfDay(): String {
        val f = frac()
        return when {
            f < 0.04 -> "night"
            f < 0.35 -> "morning"
            f < 0.60 -> "afternoon"
            f < 0.72 -> "evening"
            else -> "night"
        }
    }

    /** 0 = bright day, up to 0.55 = deep night. */
    fun darkness(): Float = max(baseDarkness(), ageInfo().darkFloor)

    private fun baseDarkness(): Float {
        val f = frac()
        return when {
            f < 0.04 -> 0.55f
            f < 0.10 -> (0.55 * (1 - (f - 0.04) / 0.06)).toFloat()
            f < 0.62 -> 0f
            f < 0.72 -> (0.55 * (f - 0.62) / 0.10).toFloat()
            else -> 0.55f
        }
    }

    // ---- log ----

    private fun addLog(text: String) {
        log.add(text)
        while (log.size > 40) log.removeAt(0)
    }

    private fun narrate(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastNarrateAt < 5_000L) return
        lastNarrateAt = now
        onNarrate(text)
    }

    private var order = ""
    private var orderUntil = 0.0

    private val ORDER_LABEL = mapOf(
        "attack" to "attack the enemy", "defend" to "stand guard", "hide" to "take cover", "rest" to "rest", "feast" to "hold a feast",
        "dance" to "dance", "story" to "tell stories", "study" to "study", "train" to "train", "explore" to "explore",
        "eat" to "eat", "fish" to "fish", "hunt" to "hunt", "water" to "fetch water", "trade" to "trade", "craft" to "craft",
        "build" to "build", "wood" to "gather wood", "stone" to "gather stone and ore", "food" to "gather food", "gather" to "gather supplies",
        "talk" to "talk together", "play" to "play",
    )

    /** Keywords for each order, checked in this order so that the specific ones win over the general ones. */
    private val ORDER_WORDS = listOf(
        "attack" to listOf("attack", "fight", "charge", "kill", "slay", "destroy", "battle", "crush", "strike", "assault", "smash", "defeat", "drive off", "drive them off", "hunt down", "go to war", "to arms", "engage", "raid them", "vanquish", "banish"),
        "defend" to listOf("defend", "defence", "defense", "guard", "protect", "patrol", "sentry", "watch the", "stay alert", "keep watch", "fortify", "hold the line", "man the", "stand ready", "secure"),
        "hide" to listOf("hide", "shelter", "flee", "run away", "run for", "take cover", "retreat", "huddle", "stay inside", "safe", "evacuate"),
        "rest" to listOf("rest", "sleep", "relax", "nap", "recover", "heal", "take a break", "bed", "tired"),
        "feast" to listOf("feast", "celebrate", "celebration", "party", "festival", "banquet", "revel", "toast", "cheer"),
        "dance" to listOf("dance", "dancing", "jig"),
        "story" to listOf("story", "stories", "sing", "song", "music", "tale", "legend", "poem", "entertain"),
        "study" to listOf("study", "read", "learn", "school", "teach", "library", "book", "research", "scholar"),
        "train" to listOf("train", "drill", "practice", "spar", "exercise", "get stronger", "level up", "barracks", "soldier"),
        "explore" to listOf("explore", "scout", "discover", "adventure", "map the", "far lands", "look beyond", "venture", "search the"),
        "eat" to listOf("eat", "dinner", "lunch", "breakfast", "meal", "hungry", "supper"),
        "fish" to listOf("fish", "lake", "angle", "net"),
        "hunt" to listOf("hunt", "game", "deer", "rabbit", "meat", "hides", "venison"),
        "water" to listOf("water", "well", "irrigate", "drink"),
        "trade" to listOf("trade", "sell", "market", "barter", "merchant", "shop", "coin", "buy"),
        "craft" to listOf("craft", "make things", "forge", "smith", "smelt", "workshop", "tools", "metal", "create"),
        "build" to listOf("build", "construct", "house", "home", "expand", "grow the village", "tower", "raise a", "lantern", "granary", "buildings"),
        "wood" to listOf("wood", "timber", "lumber", "chop", "logs", "trees", "axe"),
        "stone" to listOf("stone", "rock", "quarry", "mine", "mining", "ore", "dig", "boulder"),
        "food" to listOf("food", "berries", "berry", "forage", "harvest", "farm", "crops", "feed", "grain", "bread"),
        "gather" to listOf("gather", "collect", "work", "supplies", "resources", "stockpile", "stock up", "produce", "labour", "labor", "get to it", "busy"),
        "play" to listOf("play", "fun", "game time", "kids"),
        "talk" to listOf("talk", "chat", "speak", "meet", "council", "discuss", "together", "friends", "visit", "gossip"),
    )

    private fun parseOrder(text: String): String? {
        val t = " " + text.lowercase().replace(Regex("[^a-z ]"), " ") + " "
        for ((cmd, words) in ORDER_WORDS) {
            for (w in words) if (t.contains(" $w") ) return cmd
        }
        return null
    }

    /** What an obedient villager does for the current order, or null when it does not concern them. */
    private fun orderAction(v: Villager): Pair<String, String>? {
        if (order.isEmpty() || time >= orderUntil) return null
        val kid = isChild(v)
        val fighter = isFighter(v) || v.job == "Scout"
        val danger = predators.any { hypot(it.x - v.x, it.y - v.y) < 13f }
        val hasShore = nearestShore(v) >= 0
        if (kid) {
            return when (order) {
                "hide" -> "huddle" to ""
                "rest" -> "sleep" to ""
                "study" -> if (count("library") > 0) "study" to "" else "play" to ""
                "play", "dance", "feast" -> "play" to ""
                "talk" -> "talk" to (friendFor(v)?.name ?: "")
                else -> null
            }
        }
        if (danger && order !in setOf("attack", "defend", "hide")) return null
        fun need(): Pair<String, String> {
            val ad = adults()
            val wLack = (25 + ad * 4 - wood).toFloat() / (25 + ad * 4)
            val sLack = (10 + ad * 2 + (if (wonderSaving()) 45 else 0) - stone).toFloat() / (10 + ad * 2 + (if (wonderSaving()) 45 else 0))
            val fLack = (10 + ad * 3 - food).toFloat() / (10 + ad * 3)
            return when {
                fLack >= wLack && fLack >= sLack -> (if (age >= 1 && count("farm") > 0 && v.job == "Farmer") "tend_farm" else if (hasShore && rnd.nextBoolean()) "fish" else "gather_food") to ""
                wLack >= sLack -> "gather_wood" to ""
                else -> "gather_stone" to ""
            }
        }
        return when (order) {
            "attack" -> when {
                !fighter -> null
                predators.isNotEmpty() -> "attack" to ""
                else -> "patrol" to ""
            }
            "defend" -> when {
                fighter -> (if (!danger && count("barracks") > 0 && rnd.nextInt(3) == 0) "drill" else "patrol") to ""
                danger -> "huddle" to ""
                else -> null
            }
            "hide" -> "huddle" to ""
            "rest" -> if (v.energy < 95f) "sleep" to "" else "wander" to ""
            "feast", "dance" -> (if (count("campfire") > 0) "dance" else "talk") to (friendFor(v)?.name ?: "")
            "story" -> "tell_story" to ""
            "study" -> if (count("library") > 0) "study" to "" else "talk" to (friendFor(v)?.name ?: "")
            "train" -> (if (count("barracks") > 0) "drill" else if (fighter) "patrol" else "wander") to ""
            "explore" -> (if (fighter || v.job == "Scout" || rnd.nextInt(3) == 0) "explore" else "wander") to ""
            "eat" -> if (food > 0) "eat" to "" else need()
            "fish" -> (if (hasShore) "fish" else "gather_food") to ""
            "hunt" -> "hunt" to ""
            "water" -> (if (count("well") > 0) "fetch_water" else "gather_food") to ""
            "trade" -> if (count("market") > 0 && goods >= 2) "trade" to "" else if (count("workshop") > 0) "craft" to "" else need()
            "craft" -> when {
                count("workshop") > 0 && wood >= 6 && stone >= 2 -> "craft" to ""
                count("smelter") > 0 && ore >= 2 -> "smelt" to ""
                else -> need()
            }
            "build" -> {
                val kinds = listOf("hut", "farm", "campfire", "totem", "ranch", "pasture", "apiary", "orchard", "range", "bath", "belfry", "stage", "kiln", "yard", "loom", "dock", "quarry", "mill", "smelter", "well", "workshop", "granary", "market", "library", "tavern", "infirmary", "barracks", "shrine", "wonder", "lamp")
                val t = kinds.firstOrNull { canBuild(it) && affordable(it) }
                if (t != null && (v.job == "Builder" || rnd.nextInt(3) == 0)) "build_$t" to "" else if (v.job == "Builder" || rnd.nextBoolean()) (if (wood < stone * 2) "gather_wood" else "gather_stone") to "" else need()
            }
            "wood" -> "gather_wood" to ""
            "stone" -> (if (age >= 2 && ore < 40 && rnd.nextInt(3) == 0) "mine_ore" else "gather_stone") to ""
            "food" -> (if (v.job == "Farmer" && count("farm") > 0) "tend_farm" else if (hasShore && rnd.nextInt(3) == 0) "fish" else "gather_food") to ""
            "gather" -> need()
            "play" -> "wander" to ""
            "talk" -> "talk" to (friendFor(v)?.name ?: "")
            else -> null
        }
    }

    private fun affordable(type: String): Boolean = when (type) {
        "hut" -> wood >= 6 && (stone >= 2 || hides >= 1)
        "farm" -> wood >= 4
        "campfire" -> stone >= 3
        "totem" -> wood >= 12 && stone >= 4
        "ranch" -> wood >= 18 && stone >= 6
        "pasture" -> wood >= 10
        "apiary" -> wood >= 8
        "orchard" -> wood >= 10
        "range" -> wood >= 12 && stone >= 2
        "bath" -> wood >= 10 && stone >= 6
        "belfry" -> wood >= 14 && stone >= 10
        "stage" -> wood >= 16 && stone >= 4
        "kiln" -> wood >= 12 && stone >= 6
        "yard" -> wood >= 14 && stone >= 4
        "loom" -> wood >= 10
        "dock" -> wood >= 8
        "quarry" -> wood >= 16
        "mill" -> wood >= 14 && stone >= 4
        "shrine" -> wood >= 20 && stone >= 30
        "smelter", "well" -> wood >= 4 && stone >= 6
        "workshop" -> wood >= 10 && stone >= 4
        "granary" -> wood >= 14 && stone >= 6
        "market" -> wood >= 12 && stone >= 6
        "library" -> wood >= 18 && stone >= 12
        "tavern" -> wood >= 20 && stone >= 10
        "infirmary" -> wood >= 16 && stone >= 8
        "barracks" -> wood >= 16 && stone >= 14 && metal >= 2
        "lamp" -> wood >= 2 && stone >= 2
        "wonder" -> wood >= 50 && stone >= 40 && metal >= 5
        else -> false
    }

    fun whisper(text: String) {
        val t = text.trim().take(200)
        if (t.isEmpty()) return
        godMsg = t
        godUntil = time + 240.0
        addLog("A voice from the sky: \"$t\"")
        val cmd = parseOrder(t)
        if (cmd != null) {
            order = cmd
            orderUntil = time + 210.0
            if (cmd == "feast" && food >= 6) {
                food -= 4
                festivalUntil = time + 70.0
                for (v in villagers) v.happiness = min(100f, v.happiness + 10f)
            }
            addLog("The villagers heed the call: ${ORDER_LABEL[cmd] ?: cmd}.")
        }
        lastNarrateAt = 0L
        narrate("A voice from the sky: $t")
    }

    // ---- paths ----

    private fun blocked(i: Int): Boolean = tiles[i] == T.WATER || occupied[i]

    private fun cellOf(v: Villager): Int =
        v.y.toInt().coerceIn(0, GH - 1) * GW + v.x.toInt().coerceIn(0, GW - 1)

    private fun findPath(start: Int, goal: Int): IntArray? {
        if (start == goal) return IntArray(0)
        if (tiles[goal] == T.WATER) return null
        val prev = IntArray(GW * GH) { -1 }
        val queue = IntArray(GW * GH)
        var head = 0
        var tail = 0
        prev[start] = start
        queue[tail++] = start
        while (head < tail) {
            val c = queue[head++]
            val cx = c % GW
            val cy = c / GW
            for (d in 0..3) {
                val nx = cx + DX[d]
                val ny = cy + DY[d]
                if (nx < 0 || ny < 0 || nx >= GW || ny >= GH) continue
                val n = ny * GW + nx
                if (prev[n] != -1) continue
                if (n != goal && blocked(n)) continue
                prev[n] = c
                if (n == goal) {
                    val out = ArrayList<Int>()
                    var cur = goal
                    while (cur != start) {
                        out.add(cur)
                        cur = prev[cur]
                    }
                    out.reverse()
                    return out.toIntArray()
                }
                queue[tail++] = n
            }
        }
        return null
    }

    private fun nearestTile(v: Villager, type: Int): Int {
        var best = -1
        var bd = Float.MAX_VALUE
        var free = -1
        var fd = Float.MAX_VALUE
        val claimed = HashSet<Int>()
        for (o in villagers) if (o !== v && o.targetTile >= 0 && (o.action.startsWith("gather_") || o.action == "mine_ore")) claimed.add(o.targetTile)
        for (i in tiles.indices) {
            if (tiles[i] != type || amt[i] <= 0) continue
            val d = hypot(i % GW + 0.5f - v.x, i / GW + 0.5f - v.y)
            if (d < bd) {
                bd = d
                best = i
            }
            if (d < fd && i !in claimed) {
                fd = d
                free = i
            }
        }
        // spread people out over the trees and rocks unless the free ones are much further away
        return if (free >= 0 && fd <= bd + 8f) free else best
    }

    private fun nearestBuilding(v: Villager, type: String?): Building? =
        buildings.filter { type == null || it.type == type }
            .minByOrNull { hypot(it.x + 0.5f - v.x, it.y + 0.5f - v.y) }

    private fun count(type: String) = buildings.count { it.type == type }

    /** Sheep and hens shown in the pastures (the view splits them between pens). */
    fun sheepNow(): Int = if (count("pasture") == 0) 0 else max(2, flockSheep)
    fun hensNow(): Int = if (count("pasture") == 0) 0 else max(3, flockHens)

    /** Village-owned critters that can live at (and be tended in) the ranch. */
    fun ranchResidents(): Int = critters.critters.count { it.owner < 0 }

    internal fun walkableTile(x: Int, y: Int): Boolean =
        x in 0 until GW && y in 0 until GH && tiles[y * GW + x] != T.WATER && !occupied[y * GW + x]

    /** How many of each building the village can support, which grows with the number of people. */
    fun capFor(type: String): Int {
        val n = villagers.size
        val ad = adults()
        return when (type) {
            "hut" -> if (age < 2) (n + 1) / 2 + 2 else (n * 3) / 4 + 1
            "farm" -> if (age < 1) 0 else min(6, 1 + ad / 3)
            "campfire" -> min(4, 1 + n / 6)
            "totem" -> if (n < 5) 0 else min(2, n / 5)
            "pasture" -> if (n < 5) 0 else min(2, 1 + n / 12)
            "apiary" -> if (n < 6) 0 else min(2, 1 + n / 16)
            "orchard" -> if (n < 7) 0 else min(2, 1 + n / 18)
            "range" -> if (n < 8 || age < 1) 0 else min(2, 1 + n / 22)
            "bath" -> if (n < 6) 0 else min(2, 1 + n / 20)
            "belfry" -> if (n < 8 || age < 2) 0 else 1
            "stage" -> if (n < 8 || age < 1) 0 else 1
            "kiln" -> if (n < 7 || age < 1) 0 else 1
            "yard" -> if (n < 7 || age < 1) 0 else min(2, 1 + n / 22)
            "loom" -> if (n < 6 || age < 1 || count("pasture") == 0) 0 else 1
            "dock" -> if (n < 4 || dockSite() < 0) 0 else min(2, 1 + n / 14)
            "quarry" -> if (age < 2 || n < 8) 0 else min(2, 1 + n / 16)
            "shrine" -> if (age < 3 || n < 12) 0 else 1
            "mill" -> if (age < 1 || n < 6 || count("farm") < 2) 0 else min(2, 1 + n / 20)
            "ranch" -> if (n < 6 || ranchResidents() < 2) 0 else min(2, 1 + ranchResidents() / 9)
            "smelter" -> if (age < 2) 0 else min(3, 1 + ad / 5)
            "well" -> if (age < 1) 0 else min(3, n / 6)
            "workshop" -> if (age < 1) 0 else min(2, n / 8)
            "market" -> if (age < 2 || n < 8) 0 else min(if (age >= 4) 4 else 2, 1 + n / 16)
            "lamp" -> if (age < 1) 0 else min(10, n / 2)
            "granary" -> if (age < 2) 0 else min(3, n / 8)
            "barracks" -> if (age < 3) 0 else min(2, n / 10)
            "library" -> if (age < 3 || n < 10) 0 else min(2, n / 14)
            "tavern" -> if (age < 2 || n < 8) 0 else min(2, max(1, n / 12))
            "infirmary" -> if (age < 3 || n < 8) 0 else min(2, 1 + n / 20)
            "ballista" -> if (age < 3 || n < 8) 0 else min(if (age >= 4) 3 else 2, 1 + n / 10)
            "wonder" -> if (age < 4 || n < 14) 0 else min(3, 1 + (n - 14) / 6)
            else -> 99
        }
    }

    /** Homes, farms, workshops and the like all together: a small village cannot have a sprawl. */
    private fun totalCap() = 4 + n() * 2
    private fun n() = villagers.size
    private fun civicCount() = buildings.count { it.type != "hall" && it.type != "tower" && it.type != "palisade" && it.type != "wall" && it.type != "gate" && it.type != "totem" && it.type != "ranch" && it.type != "pasture" && it.type != "apiary" && it.type != "orchard" && it.type != "range" && it.type != "bath" && it.type != "belfry" && it.type != "stage" && it.type != "kiln" && it.type != "yard" && it.type != "loom" && it.type != "dock" && it.type != "quarry" && it.type != "mill" && it.type != "shrine" && it.type != "ballista" && it.type != "lamp" } +
        villagers.count { it.action.startsWith("build_") && it.action != "build_lamp" }

    private fun canBuild(type: String) = counting(type) < capFor(type) && (type == "ranch" || type == "pasture" || type == "apiary" || type == "orchard" || type == "range" || type == "bath" || type == "belfry" || type == "stage" || type == "kiln" || type == "yard" || type == "loom" || type == "dock" || type == "quarry" || type == "mill" || type == "shrine" || civicCount() < totalCap()) &&
        (type == "wonder" || type == "shrine" || type == "hut" || type == "farm" || !wonderSaving())

    /** City Age: the village puts spare stone aside for its next great wonder instead of spending it on small things. */
    private fun wonderSaving(): Boolean = age >= 4 && villagers.size >= 14 && counting("wonder") < capFor("wonder") &&
        stone < 50 && wood >= 40 && metal >= 5 && tiles.any { it == T.ROCK }

    /** A short "what is built and what could be" line for the panels. */
    fun buildSummary(): String {
        val parts = ArrayList<String>()
        for (t in listOf("hut", "farm", "campfire", "totem", "ranch", "pasture", "apiary", "orchard", "range", "bath", "belfry", "stage", "kiln", "yard", "loom", "smelter", "well", "workshop", "market", "granary", "barracks", "library", "tavern", "infirmary", "ballista", "shrine", "wonder", "lamp")) {
            val c = capFor(t)
            if (c > 0) parts.add("$t ${count(t)}/$c")
        }
        return parts.joinToString("  ")
    }

    /** Buildings standing plus the ones somebody is already walking out to raise. */
    private fun counting(type: String) = count(type) + villagers.count { it.action == "build_$type" }

    // ---- actions ----

    private fun go(v: Villager, action: String, goal: Int, target: Int = goal): Boolean {
        val p = findPath(cellOf(v), goal) ?: return false
        v.action = action
        v.phase = "walking"
        v.path = p
        v.pathIdx = 0
        v.targetTile = target
        v.gathered = 0
        if (p.isEmpty()) arrive(v)
        return true
    }

    /** Used by the critter world to send a villager somewhere on a critter errand. */
    internal fun walkTo(v: Villager, action: String, goal: Int): Boolean = go(v, action, goal)

    /** Frees a villager when a critter errand is over. */
    internal fun releaseVillager(v: Villager) = finish(v)

    /** A line for the news feed, narrated now and then. */
    internal fun note(text: String) {
        addLog(text)
        narrate(text)
    }

    /** A grass tile right on the water for a pier (cached for a few seconds), or -1 when the lake is too far or crowded. */
    private fun dockSite(): Int {
        if (time - dockSiteAt < 15.0 && dockSiteVal >= 0 && !occupied[dockSiteVal] && dockSiteVal !in reserved) return dockSiteVal
        if (time - dockSiteAt < 15.0 && dockSiteVal < 0) return -1
        dockSiteAt = time
        var cx = GW / 2f
        var cy = GH / 2f
        if (buildings.isNotEmpty()) {
            cx = buildings.map { it.x }.average().toFloat()
            cy = buildings.map { it.y }.average().toFloat()
        }
        var best = -1
        var bs = Float.MAX_VALUE
        for (y in 1 until GH - 1) for (x in 1 until GW - 1) {
            val i = y * GW + x
            if (tiles[i] != T.GRASS || occupied[i] || i in reserved) continue
            if (tiles[i - 1] != T.WATER && tiles[i + 1] != T.WATER && tiles[i - GW] != T.WATER && tiles[i + GW] != T.WATER) continue
            if (buildings.any { abs(it.x - x) <= 2 && abs(it.y - y) <= 2 }) continue
            val s = hypot(x - cx, y - cy)
            if (s < bs) { bs = s; best = i }
        }
        dockSiteVal = best
        return best
    }

    private fun findSite(): Int {
        var cx = GW / 2f
        var cy = GH / 2f
        if (buildings.isNotEmpty()) {
            cx = buildings.map { it.x }.average().toFloat()
            cy = buildings.map { it.y }.average().toFloat()
        }
        var best = -1
        var bs = Float.MAX_VALUE
        for (y in 1 until GH - 1) for (x in 1 until GW - 1) {
            val i = y * GW + x
            if (tiles[i] != T.GRASS || occupied[i] || i in reserved) continue
            if (buildings.any { abs(it.x - x) <= 1 && abs(it.y - y) <= 1 }) continue
            val s = hypot(x - cx, y - cy) + rnd.nextFloat() * 2.5f
            if (s < bs) {
                bs = s
                best = i
            }
        }
        return best
    }

    private fun nearestShoreAny(): Boolean = villagers.firstOrNull()?.let { nearestShore(it) >= 0 } ?: false

    private fun nearestShore(v: Villager): Int {
        var best = -1
        var bd = Float.MAX_VALUE
        for (y in 0 until GH) for (x in 0 until GW) {
            val i = y * GW + x
            if (tiles[i] == T.WATER || occupied[i]) continue
            val nearWater = (x > 0 && tiles[i - 1] == T.WATER) || (x < GW - 1 && tiles[i + 1] == T.WATER) ||
                (y > 0 && tiles[i - GW] == T.WATER) || (y < GH - 1 && tiles[i + GW] == T.WATER)
            if (!nearWater) continue
            val d = hypot(x + 0.5f - v.x, y + 0.5f - v.y)
            if (d < bd) {
                bd = d
                best = i
            }
        }
        return best
    }

    private fun startAction(v: Villager, action: String, target: String): Boolean {
        when (action) {
            "gather_wood", "gather_stone", "gather_food", "mine_ore" -> {
                if (action == "mine_ore" && (age < 2 || ore >= 60)) return false
                val type = when (action) {
                    "gather_wood" -> T.FOREST
                    "gather_stone" -> T.ROCK
                    "mine_ore" -> T.ORE
                    else -> T.BUSH
                }
                val i = nearestTile(v, type)
                if (i < 0) return false
                return go(v, action, i)
            }
            "build_hut", "build_farm", "build_campfire", "build_smelter", "build_well", "build_workshop", "build_market", "build_lamp", "build_granary", "build_barracks", "build_library", "build_wonder", "build_tavern", "build_infirmary", "build_totem", "build_ranch", "build_pasture", "build_apiary", "build_orchard", "build_range", "build_bath", "build_belfry", "build_stage", "build_kiln", "build_yard", "build_loom", "build_dock", "build_quarry", "build_mill", "build_shrine" -> {
                val type = action.removePrefix("build_")
                if (!canBuild(type)) return false
                val ok = when (action) {
                    "build_hut" -> wood >= 6 && (stone >= 2 || hides >= 1)
                    "build_farm" -> wood >= 4
                    "build_smelter" -> wood >= 4 && stone >= 6
                    "build_well" -> wood >= 4 && stone >= 6
                    "build_workshop" -> wood >= 10 && stone >= 4
                    "build_market" -> wood >= 12 && stone >= 6
                    "build_lamp" -> wood >= 2 && stone >= 2
                    "build_granary" -> wood >= 14 && stone >= 6
                    "build_barracks" -> wood >= 16 && stone >= 14 && metal >= 2
                    "build_library" -> wood >= 18 && stone >= 12
                    "build_tavern" -> wood >= 20 && stone >= 10
                    "build_infirmary" -> wood >= 16 && stone >= 8
                    "build_totem" -> wood >= 12 && stone >= 4
                    "build_ranch" -> wood >= 18 && stone >= 6
                    "build_pasture" -> wood >= 10
                    "build_apiary" -> wood >= 8
                    "build_orchard" -> wood >= 10
                    "build_range" -> wood >= 12 && stone >= 2
                    "build_bath" -> wood >= 10 && stone >= 6
                    "build_belfry" -> wood >= 14 && stone >= 10
                    "build_stage" -> wood >= 16 && stone >= 4
                    "build_kiln" -> wood >= 12 && stone >= 6
                    "build_yard" -> wood >= 14 && stone >= 4
                    "build_loom" -> wood >= 10
                    "build_dock" -> wood >= 8
                    "build_quarry" -> wood >= 16
                    "build_mill" -> wood >= 14 && stone >= 4
                    "build_shrine" -> wood >= 20 && stone >= 30
                    "build_wonder" -> wood >= 50 && stone >= 40 && metal >= 5
                    else -> stone >= 3
                }
                if (!ok) return false
                val site = if (action == "build_dock") dockSite() else findSite()
                if (site < 0) return false
                if (!go(v, action, site)) return false
                when (action) {
                    "build_hut" -> { wood -= 6; if (hides >= 1) hides-- else stone -= 2 }
                    "build_farm" -> wood -= 4
                    "build_smelter", "build_well" -> { wood -= 4; stone -= 6 }
                    "build_workshop" -> { wood -= 10; stone -= 4 }
                    "build_market" -> { wood -= 12; stone -= 6 }
                    "build_lamp" -> { wood -= 2; stone -= 2 }
                    "build_granary" -> { wood -= 14; stone -= 6 }
                    "build_barracks" -> { wood -= 16; stone -= 14; metal -= 2 }
                    "build_library" -> { wood -= 18; stone -= 12 }
                    "build_tavern" -> { wood -= 20; stone -= 10 }
                    "build_infirmary" -> { wood -= 16; stone -= 8 }
                    "build_totem" -> { wood -= 12; stone -= 4 }
                    "build_ranch" -> { wood -= 18; stone -= 6 }
                    "build_pasture" -> wood -= 10
                    "build_apiary" -> wood -= 8
                    "build_orchard" -> wood -= 10
                    "build_range" -> { wood -= 12; stone -= 2 }
                    "build_bath" -> { wood -= 10; stone -= 6 }
                    "build_belfry" -> { wood -= 14; stone -= 10 }
                    "build_stage" -> { wood -= 16; stone -= 4 }
                    "build_kiln" -> { wood -= 12; stone -= 6 }
                    "build_yard" -> { wood -= 14; stone -= 4 }
                    "build_loom" -> wood -= 10
                    "build_dock" -> wood -= 8
                    "build_quarry" -> wood -= 16
                    "build_mill" -> { wood -= 14; stone -= 4 }
                    "build_shrine" -> { wood -= 20; stone -= 30 }
                    "build_wonder" -> { wood -= 50; stone -= 40; metal -= 5 }
                    else -> stone -= 3
                }
                reserved.add(site)
                return true
            }
            "attack" -> {
                val p = predators.minByOrNull { hypot(it.x - v.x, it.y - v.y) } ?: return false
                val i = p.y.toInt().coerceIn(0, GH - 1) * GW + p.x.toInt().coerceIn(0, GW - 1)
                if (tiles[i] == T.WATER) return false
                return go(v, "attack", i)
            }
            "haul" -> {
                if (food < 4) return false
                val b = nearestBuilding(v, "granary") ?: return false
                return go(v, "haul", b.y * GW + b.x)
            }
            "drill" -> {
                val b = nearestBuilding(v, "barracks") ?: return false
                return go(v, "drill", b.y * GW + b.x)
            }
            "study" -> {
                val b = nearestBuilding(v, "library") ?: return false
                return go(v, "study", b.y * GW + b.x)
            }
            "tavern" -> {
                if (isChild(v) || food < 3) return false
                val b = nearestBuilding(v, "tavern") ?: return false
                return go(v, "tavern", b.y * GW + b.x)
            }
            "ring_bell" -> {
                if (!bellOk(v)) return false
                val b = nearestBuilding(v, "belfry") ?: return false
                return go(v, "ring_bell", b.y * GW + b.x)
            }
            "weave" -> {
                if (!loomOk(v)) return false
                val b = nearestBuilding(v, "loom") ?: return false
                return go(v, "weave", b.y * GW + b.x)
            }
            "train_critters" -> {
                if (!yardOk(v)) return false
                val b = nearestBuilding(v, "yard") ?: return false
                return go(v, "train_critters", b.y * GW + b.x)
            }
            "fire_pots" -> {
                if (!kilnOk(v)) return false
                val b = nearestBuilding(v, "kiln") ?: return false
                return go(v, "fire_pots", b.y * GW + b.x)
            }
            "show" -> {
                if (!showOk(v)) return false
                val b = nearestBuilding(v, "stage") ?: return false
                return go(v, "show", b.y * GW + b.x)
            }
            "soak" -> {
                if (!soakOk(v)) return false
                val b = nearestBuilding(v, "bath") ?: return false
                return go(v, "soak", b.y * GW + b.x)
            }
            "shoot" -> {
                if (!shootOk(v)) return false
                val b = nearestBuilding(v, "range") ?: return false
                return go(v, "shoot", b.y * GW + b.x)
            }
            "pick_fruit" -> {
                if (!fruitOk(v)) return false
                val b = nearestBuilding(v, "orchard") ?: return false
                return go(v, "pick_fruit", b.y * GW + b.x)
            }
            "tend_bees" -> {
                if (!beesOk(v)) return false
                val b = nearestBuilding(v, "apiary") ?: return false
                return go(v, "tend_bees", b.y * GW + b.x)
            }
            "tend_flock" -> {
                if (isChild(v)) return false
                val b = nearestBuilding(v, "pasture") ?: return false
                return go(v, "tend_flock", b.y * GW + b.x)
            }
            "tend_critters" -> {
                if (isChild(v) || ranchResidents() == 0) return false
                val b = nearestBuilding(v, "ranch") ?: return false
                return go(v, "tend_critters", b.y * GW + b.x)
            }
            "vigil" -> {
                if (!vigilOk(v)) return false
                val b = nearestBuilding(v, "shrine") ?: return false
                return go(v, "vigil", b.y * GW + b.x)
            }
            "council" -> {
                if (!councilOk() || isChild(v) || v.id in councilGuestIds) return false
                val t = councilSeatTile()
                if (t < 0) return false
                return go(v, "council", t)
            }
            "revel" -> {
                if (!feteOk()) return false
                val t = feteRevelTile()
                if (t < 0) return false
                return go(v, "revel", t)
            }
            "honor" -> {
                if (isChild(v)) return false
                val b = nearestBuilding(v, "totem") ?: return false
                return go(v, "honor", b.y * GW + b.x)
            }
            "mourn" -> {
                if (isChild(v) || graves.isEmpty()) return false
                val kin = graves.filter { v.parents.contains(it.name) }
                val g = (if (kin.isNotEmpty() && rnd.nextInt(3) > 0) kin else graves).let { it[rnd.nextInt(it.size)] }
                return go(v, "mourn", g.y.toInt() * GW + g.x.toInt())
            }
            "recover" -> {
                val b = nearestBuilding(v, "infirmary") ?: return false
                return go(v, "recover", b.y * GW + b.x)
            }
            "fetch_water" -> {
                val b = nearestBuilding(v, "well") ?: return false
                return go(v, "fetch_water", b.y * GW + b.x)
            }
            "craft" -> {
                if (wood < 6 || stone < 2 || goods >= 24) return false
                val b = nearestBuilding(v, "workshop") ?: return false
                return go(v, "craft", b.y * GW + b.x)
            }
            "trade" -> {
                if (goods < 2) return false
                val b = nearestBuilding(v, "market") ?: return false
                return go(v, "trade", b.y * GW + b.x)
            }
            "dance" -> {
                val b = nearestBuilding(v, "campfire") ?: return false
                return go(v, "dance", b.y * GW + b.x)
            }
            "play" -> {
                for (k in 0 until 10) {
                    val x = (v.x.toInt() + rnd.nextInt(11) - 5).coerceIn(1, GW - 2)
                    val y = (v.y.toInt() + rnd.nextInt(9) - 4).coerceIn(1, GH - 2)
                    val i = y * GW + x
                    if (!blocked(i) && go(v, "play", i)) return true
                }
                return false
            }
            "patrol" -> {
                // walk out to the edge of the village and look around
                val cx = if (buildings.isEmpty()) GW / 2f else buildings.map { it.x }.average().toFloat()
                val cy = if (buildings.isEmpty()) GH / 2f else buildings.map { it.y }.average().toFloat()
                for (k in 0 until 14) {
                    val ang = rnd.nextFloat() * 6.283f
                    val r = 7f + rnd.nextFloat() * 5f
                    val x = (cx + kotlin.math.cos(ang) * r).toInt().coerceIn(1, GW - 2)
                    val y = (cy + kotlin.math.sin(ang) * r * 0.7f).toInt().coerceIn(1, GH - 2)
                    val i = y * GW + x
                    if (!blocked(i) && go(v, "patrol", i)) return true
                }
                return false
            }
            "hunt" -> {
                // follow the game if there is some close by, otherwise go into the trees
                var prey = -1
                var pd = 22f
                for (a in fauna.animals) {
                    if (a.kind != "deer" && a.kind != "rabbit") continue
                    val d = hypot(a.x - v.x, a.y - v.y)
                    val ti = a.y.toInt() * GW + a.x.toInt()
                    if (d < pd && ti in tiles.indices && !blocked(ti)) { pd = d; prey = ti }
                }
                if (prey >= 0 && go(v, "hunt", prey)) return true
                val i = nearestTile(v, T.FOREST)
                if (i < 0) return false
                return go(v, "hunt", i)
            }
            "smelt" -> {
                if (age < 2 || ore < 2 || wood < 30 || metal >= 150) return false
                val b = nearestBuilding(v, "smelter") ?: return false
                return go(v, "smelt", b.y * GW + b.x)
            }
            "eat" -> {
                if (food <= 0) return false
                val b = nearestBuilding(v, "campfire") ?: nearestBuilding(v, null) ?: return false
                return go(v, "eat", b.y * GW + b.x)
            }
            "sleep" -> {
                val b = nearestBuilding(v, "hut") ?: nearestBuilding(v, "campfire")
                if (b == null) {
                    v.action = "sleep"
                    v.phase = "sleeping"
                    return true
                }
                return go(v, "sleep", b.y * GW + b.x)
            }
            "talk" -> {
                if (time - v.lastTalk < 70.0) return false
                val other = villagers.firstOrNull { it !== v && it.name.equals(target.trim(), ignoreCase = true) }
                    ?: friendFor(v) ?: return false
                v.talkTo = other
                if (v.talkMsg.isBlank()) v.talkMsg = lineFor(v)
                return go(v, "talk", cellOf(other))
            }
            "wander" -> {
                for (k in 0 until 12) {
                    val x = (v.x.toInt() + rnd.nextInt(13) - 6).coerceIn(1, GW - 2)
                    val y = (v.y.toInt() + rnd.nextInt(9) - 4).coerceIn(1, GH - 2)
                    val i = y * GW + x
                    if (!blocked(i) && go(v, "wander", i)) return true
                }
                return false
            }
            "fish" -> {
                val dk = nearestBuilding(v, "dock")
                if (dk != null && rnd.nextInt(3) != 0) return go(v, "fish", dk.y * GW + dk.x)
                val i = nearestShore(v)
                if (i < 0) return false
                return go(v, "fish", i)
            }
            "quarry" -> {
                if (isChild(v) || count("quarry") == 0 || alert() || stone >= capStone() * 0.9) return false
                val qb = nearestBuilding(v, "quarry") ?: return false
                return go(v, "quarry", qb.y * GW + qb.x)
            }
            "raise_cathedral" -> {
                if (!cathOk(v)) return false
                if (cathX < 0) pickCathedral()
                if (cathX < 0) return false
                return go(v, "raise_cathedral", (cathY + 3) * GW + cathX + 2)
            }
            "grind" -> {
                if (!millOk(v)) return false
                val mb = nearestBuilding(v, "mill") ?: return false
                return go(v, "grind", mb.y * GW + mb.x)
            }
            "sail" -> {
                if (isChild(v) || !sailOk()) return false
                val dk = nearestBuilding(v, "dock") ?: return false
                return go(v, "sail", dk.y * GW + dk.x)
            }
            "swim" -> {
                if (!canSwim()) return false
                val i = nearestShore(v)
                if (i < 0) return false
                return go(v, "swim", i)
            }
            "forage_herbs" -> {
                val i = nearestTile(v, T.BUSH)
                if (i < 0) return false
                return go(v, "forage_herbs", i)
            }
            "snowman", "rake_leaves", "fly_kite" -> {
                if (!seasonFunOk(action)) return false
                val i = seasonSpot(v, action)
                if (i < 0) return false
                return go(v, action, i)
            }
            "puddle_jump", "catch_fireflies" -> {
                if (!isChild(v) || !kidGameOk(action)) return false
                val i = seasonSpot(v, action)
                if (i < 0) return false
                return go(v, action, i)
            }
            "hide_seek" -> {
                if (!isChild(v) || !kidGameOk(action)) return false
                var i = nearestTile(v, T.BUSH)
                if (i < 0 || hypot(i % GW + 0.5f - v.x, i / GW + 0.5f - v.y) > 22f) i = nearestTile(v, T.FOREST)
                if (i < 0 || hypot(i % GW + 0.5f - v.x, i / GW + 0.5f - v.y) > 22f) return false
                return go(v, "hide_seek", i)
            }
            "teach" -> {
                val c = teachChild(v) ?: return false
                return go(v, "teach", c.y.toInt().coerceIn(0, GH - 1) * GW + c.x.toInt().coerceIn(0, GW - 1))
            }
            "walk_dog" -> {
                if (!hasDog(v) || !dogOk()) return false
                val i = seasonSpot(v, action)
                if (i < 0) return false
                return go(v, action, i)
            }
            "carve" -> {
                if (wood < 8 || goods >= 30) return false
                val b = nearestBuilding(v, "campfire") ?: return false
                return go(v, "carve", b.y * GW + b.x)
            }
            "assault_camp" -> {
                if (!campAssaultOk(v)) return false
                val ax = campX + (rnd.nextFloat() - 0.5f) * 1.6f
                val ay = campY + 1.2f
                val i = ay.toInt().coerceIn(0, GH - 1) * GW + ax.toInt().coerceIn(0, GW - 1)
                if (blocked(i)) return false
                return go(v, "assault_camp", i)
            }
            "delve" -> {
                if (riftState != 1 || party.size >= 4 || isChild(v) || v.hp < 60f || v.id in party) return false
                val i = riftY.toInt() * GW + riftX.toInt()
                if (!go(v, "delve", i)) return false
                party.add(v.id)
                return true
            }
            "pick_flowers" -> {
                if (friendFor(v) == null) return false
                val i = nearestTile(v, T.BUSH)
                if (i < 0) return false
                return go(v, "pick_flowers", i)
            }
            "douse" -> {
                if (isChild(v) || blazes.isEmpty()) return false
                val b = blazes.sortedBy { hypot((it.i % GW) + 0.5f - v.x, (it.i / GW) + 0.5f - v.y) }.let { it[if (it.size > 1 && rnd.nextInt(3) == 0) 1 else 0] }
                return go(v, "douse", b.i)
            }
            "gather_star" -> {
                if (!starReady() || alert() || isChild(v)) return false
                val i = starY.toInt().coerceIn(0, GH - 1) * GW + starX.toInt().coerceIn(0, GW - 1)
                return go(v, "gather_star", i)
            }
            "stargaze" -> {
                if (!canStargaze()) return false
                val b = nearestBuilding(v, "campfire") ?: return false
                return go(v, "stargaze", b.y * GW + b.x)
            }
            "tend_farm" -> {
                if (age < 1) return false
                val b = nearestBuilding(v, "farm") ?: return false
                return go(v, "tend_farm", b.y * GW + b.x)
            }
            "explore" -> {
                for (k in 0 until 14) {
                    val x = (GW / 2 + rnd.nextInt(OLD_GW + 20) - (OLD_GW + 20) / 2).coerceIn(1, GW - 2)
                    val y = (GH / 2 + rnd.nextInt(OLD_GH + 10) - (OLD_GH + 10) / 2).coerceIn(1, GH - 2)
                    val i = y * GW + x
                    if (blocked(i) || hypot(x + 0.5f - v.x, y + 0.5f - v.y) < 12f) continue
                    if (go(v, "explore", i)) return true
                }
                return false
            }
            "tell_story", "huddle" -> {
                // cover is the nearest campfire, hut or tower: running for the far hub gets people caught
                val b = (if (action == "huddle") buildings.filter { it.type == "campfire" || it.type == "hut" || it.type == "tower" }
                    .minByOrNull { hypot(it.x + 0.5f - v.x, it.y + 0.5f - v.y) } else null) ?: nearestBuilding(v, "campfire") ?: return false
                return go(v, action, b.y * GW + b.x)
            }
        }
        return false
    }

    // ---- Villager skills: practice at a trade raises its level, which speeds work and enlarges gathered loads.
    var skillUps = 0
    var skillBonusLoads = 0
    var skillMasters = 0
    private val SKILL_NAMES = mapOf("wood" to "Woodcutting", "stone" to "Mining", "farm" to "Farming", "fish" to "Fishing", "build" to "Building", "combat" to "Fighting", "lore" to "Scholarship", "craft" to "Smithing", "hunt" to "Hunting")
    private val MASTER_TITLES = mapOf("wood" to "the Master Woodcutter", "stone" to "the Master Miner", "farm" to "the Master Farmer", "fish" to "the Master Fisher", "build" to "the Master Mason", "combat" to "the Master-at-Arms", "lore" to "the Sage", "craft" to "the Master Smith", "hunt" to "the Master Hunter")

    fun skillOf(action: String): String? = when {
        action == "gather_wood" -> "wood"
        action == "gather_stone" || action == "mine_ore" || action == "quarry" -> "stone"
        action == "gather_food" || action == "tend_farm" || action == "grind" || action == "tend_bees" || action == "tend_flock" || action == "pick_fruit" -> "farm"
        action == "fish" || action == "sail" -> "fish"
        action.startsWith("build_") || action == "raise_cathedral" -> "build"
        action == "attack" || action == "drill" || action == "assault_camp" || action == "shoot" -> "combat"
        action == "study" -> "lore"
        action == "smelt" || action == "craft" -> "craft"
        action == "hunt" -> "hunt"
        else -> null
    }

    fun skillLevel(v: Villager, k: String): Int = kotlin.math.sqrt(v.stat("xp_$k") / 5.0).toInt().coerceAtMost(10)

    fun skillRank(lv: Int): String = when {
        lv <= 0 -> "Novice"
        lv <= 2 -> "Apprentice"
        lv <= 5 -> "Skilled"
        lv <= 8 -> "Expert"
        lv == 9 -> "Veteran"
        else -> "Master"
    }

    /** Progress to the next level, 0..1 (1 at the cap). */
    fun skillProgress(v: Villager, k: String): Float {
        val lv = skillLevel(v, k)
        if (lv >= 10) return 1f
        val lo = lv * lv * 5f
        val hi = (lv + 1) * (lv + 1) * 5f
        return ((v.stat("xp_$k") - lo) / (hi - lo)).coerceIn(0f, 1f)
    }

    /** The villager's best skills for the hero view: label, level, progress. Top three with any practice. */
    fun skillLines(v: Villager): List<Triple<String, Int, Float>> =
        SKILL_NAMES.keys.filter { v.stat("xp_$it") > 0 }.sortedByDescending { v.stat("xp_$it") }.take(3)
            .map { Triple(SKILL_NAMES[it] ?: it, skillLevel(v, it), skillProgress(v, it)) }

    private fun skillSpeed(v: Villager): Float {
        val k = skillOf(v.action) ?: return 1f
        return 1f + 0.03f * skillLevel(v, k)
    }

    private fun skillYield(v: Villager): Int {
        val k = skillOf(v.action) ?: return 0
        val lv = skillLevel(v, k)
        val b = if (lv >= 9) 2 else if (lv >= 5) 1 else 0
        if (b > 0 && (v.action.startsWith("gather_") || v.action == "mine_ore")) skillBonusLoads += b
        return b
    }

    private fun skillGain(v: Villager) {
        if (isChild(v)) return
        val k = skillOf(v.action) ?: return
        val before = skillLevel(v, k)
        v.bump("xp_$k")
        val after = skillLevel(v, k)
        if (after <= before) return
        skillUps++
        val nm = SKILL_NAMES[k] ?: k
        fx.add(Fx("levelup", v.x, v.y, time, 1.6f))
        v.happiness = min(100f, v.happiness + 3f)
        v.addDiary("$nm level $after: now ${skillRank(after).lowercase()}.")
        if (after == 5 || after == 9) {
            addLog("${v.name} grows ${skillRank(after).lowercase()} at $nm (level $after) and now brings back bigger loads.")
            v.remember("reached level $after in $nm")
        }
        if (after >= 10) {
            val t = MASTER_TITLES[k]
            skillMasters++
            if (t != null && t !in v.titles) {
                v.titles.add(t)
                titlesEarned++
                deedBannerAt = time; deedBannerTag = "MASTER"; deedBannerName = "${v.name} $t"; deedBannerSub = "Mastered $nm"
                addLog("${v.name} masters $nm and is named $t.")
                mark("${v.name} became $t.")
                v.remember("mastered $nm and was named $t")
                v.happiness = min(100f, v.happiness + 10f)
            }
        }
    }

    private class TitleDef(val stat: String, val need: Int, val title: String, val why: String)
    private val TITLE_DEFS = listOf(
        TitleDef("slain", 6, "the Wolfbane", "helped slay 6 foes"),
        TitleDef("slain", 25, "the Shieldbearer", "stood in 25 victories"),
        TitleDef("slain", 60, "the Warden", "fought off 60 foes"),
        TitleDef("rooms", 8, "the Delver", "cleared 8 rift rooms"),
        TitleDef("rooms", 30, "the Riftwalker", "cleared 30 rift rooms"),
        TitleDef("gathered", 250, "the Tireless", "gathered 250 loads"),
        TitleDef("built", 10, "the Builder", "raised 10 buildings"),
        TitleDef("fish", 60, "the Angler", "landed 60 catches"),
        TitleDef("stories", 40, "the Taleweaver", "told 40 tales"),
        TitleDef("posies", 25, "the Kindhearted", "gave 25 posies"),
        TitleDef("carved", 30, "the Toymaker", "carved 30 toys"),
        TitleDef("tourney", 1, "the Champion", "won a grand tournament"),
        TitleDef("tourney", 3, "the Grandmaster", "won three grand tournaments"),
    )

    /** What this villager has done so far, in short lines for the hero view. */
    fun careerLines(v: Villager): List<String> {
        val out = ArrayList<String>()
        fun add(k: String, label: String) { if (v.stat(k) > 0) out.add("$label ${v.stat(k)}") }
        add("slain", "Foes beaten"); add("rooms", "Rift rooms"); add("gathered", "Loads gathered"); add("built", "Built")
        add("tourney", "Tournaments won"); add("fish", "Fish"); add("stories", "Tales told"); add("posies", "Posies given"); add("carved", "Toys carved")
        if (!isChild(v)) out.add("Dream: " + v.persona.dream + (if (v.stat("dream") > 0) " (came true)" else " (not yet)"))
        return out
    }

    /** The next title this villager is working toward, in words, or null. */
    fun nextTitleText(v: Villager): String? {
        val d = TITLE_DEFS.filter { it.title !in v.titles }.minByOrNull { (it.need - v.stat(it.stat)).toFloat() / it.need } ?: return null
        return "Next: ${d.title.removePrefix("the ").replaceFirstChar { c -> c.uppercase() }} (${v.stat(d.stat)}/${d.need})"
    }

    /** Closest friends first, with how close they are. */
    fun bondsOf(v: Villager): List<Pair<Villager, Int>> =
        villagers.filter { it !== v && (v.affinity[it.id] ?: 0) >= 2 }.map { it to (v.affinity[it.id] ?: 0) }.sortedByDescending { it.second }.take(3)

    var titlesEarned = 0
    /** Personal dreams fulfilled (one per villager, tied to their personality). */
    var dreamsDone = 0
    private var dreamClock = 0f
    private var lastThreatAt = 0.0
    /** Pastimes picked because of personality, for the harness. */
    val pastimeCount = HashMap<String, Int>()

    fun bumpStat(v: Villager, k: String) { v.bump(k); checkTitles(v) }

    private fun checkTitles(v: Villager) {
        for (d in TITLE_DEFS) {
            if (d.title in v.titles || v.stat(d.stat) < d.need) continue
            v.titles.add(d.title)
            titlesEarned++
            deedBannerAt = time; deedBannerTag = "NEW TITLE"; deedBannerName = "${v.name} ${d.title}"; deedBannerSub = d.why.replaceFirstChar { c -> c.uppercase() }
            addLog("${v.name} earns the name ${d.title.removePrefix("the ").replaceFirstChar { c -> c.uppercase() }}: ${d.why}.")
            mark("${v.name} became ${d.title}.")
            v.remember("was named ${d.title} for having ${d.why}")
            v.happiness = min(100f, v.happiness + 12f)
            fx.add(Fx("spawn", v.x, v.y, time, 1.4f))
            raiseStatue(v, d)
            break
        }
    }

    /** The village carves a statue of a newly named hero near the fire (one per hero, up to ten). */
    private fun raiseStatue(v: Villager, d: TitleDef) {
        if (statues.size >= 10 || statues.any { it.name == v.name }) return
        val hub = buildings.firstOrNull { it.type == "hall" } ?: buildings.firstOrNull { it.type == "campfire" } ?: return
        for (k in 0 until 40) {
            val ang = rnd.nextFloat() * 6.2832f
            val r = 3.5f + rnd.nextFloat() * 4.5f
            val x = (hub.x + 0.5f + kotlin.math.cos(ang) * r).toInt()
            val y = (hub.y + 0.5f + kotlin.math.sin(ang) * r * 0.7f).toInt()
            if (x < 1 || y < 1 || x >= GW - 1 || y >= GH - 1) continue
            if (!walkableTile(x, y) || tiles[y * GW + x] == T.FOREST) continue
            if (buildings.any { abs(it.x - x) <= 1 && abs(it.y - y) <= 1 }) continue
            if (statues.any { abs(it.x - (x + 0.5f)) < 2.2f && abs(it.y - (y + 0.5f)) < 2f }) continue
            if (seasonProps.any { abs(it.x - x) < 2.5f && abs(it.y - y) < 2f }) continue
            val tier = if (age >= 4) 2 else if (age >= 2) 1 else 0
            statues.add(Statue(v.name, d.title, d.stat, x + 0.5f, y + 0.5f, time, tier))
            val what = when (tier) { 0 -> "carved of wood"; 1 -> "cut from stone"; else -> "gilded in gold" }
            addLog("The village raises a statue of ${v.name} ${d.title}, $what, on Heroes' Row.")
            mark("A statue of ${v.name} was raised.")
            narrate("A statue of ${v.name} is unveiled")
            fx.add(Fx("spawn", x + 0.5f, y + 0.5f, time, 1.6f))
            for (o in villagers) if (hypot(o.x - x, o.y - y) < 9f) o.happiness = min(100f, o.happiness + 5f)
            return
        }
    }

    /** Finds a quiet, flat patch away from the huts for the graveyard. */
    private fun pickGraveyard() {
        val hub = buildings.firstOrNull { it.type == "hall" } ?: buildings.firstOrNull { it.type == "campfire" } ?: return
        for (r in 10..22 step 2) for (k in 0 until 24) {
            val ang = k * 0.2618f + r * 0.37f
            val x0 = (hub.x + 0.5f + kotlin.math.cos(ang) * r).toInt()
            val y0 = (hub.y + 0.5f + kotlin.math.sin(ang) * r * 0.7f).toInt()
            if (x0 < 2 || y0 < 2 || x0 + 7 >= GW - 1 || y0 + 5 >= GH - 1) continue
            var ok = true
            loop@ for (yy in y0 - 1..y0 + 4) for (xx in x0 - 1..x0 + 6) {
                val i = yy * GW + xx
                if (tiles[i] == T.WATER || tiles[i] == T.FOREST || tiles[i] == T.ROCK || tiles[i] == T.ORE || occupied[i]) { ok = false; break@loop }
            }
            if (!ok) continue
            if (buildings.any { it.x >= x0 - 2 && it.x <= x0 + 7 && it.y >= y0 - 2 && it.y <= y0 + 5 }) continue
            if (statues.any { it.x >= x0 - 2 && it.x <= x0 + 7 && it.y >= y0 - 2 && it.y <= y0 + 5 }) continue
            graveX = x0; graveY = y0
            return
        }
    }

    /** A villager who dies is laid to rest in the graveyard with a headstone. */
    private fun buryVillager(v: Villager, cause: String) {
        try {
            if (graveX < 0) pickGraveyard()
            if (graveX < 0) return
            val slot = buried % graveSlots
            val g = Grave(v.name, v.job, graveX + (slot % 6) + 0.5f, graveY + (slot / 6) + 0.5f, ageYears(v), cause, day())
            if (slot < graves.size) graves[slot] = g else graves.add(g)
            buried++
            lastBuryAt = time
            fx.add(Fx("bless", g.x, g.y, time, 1.4f))
            addLog("${v.name} is laid to rest in the graveyard.")
        } catch (e: Exception) { guard(e) }
    }

    // ---- the Great Cathedral ----

    private val CATH_STEPS = intArrayOf(10, 28, 52, 84, 120)
    fun cathTier(): Int { var t = 0; for (k in CATH_STEPS.indices) if (cathWork >= CATH_STEPS[k]) t = k + 1; return t }
    fun cathTierName(t: Int): String = when (t) { 1 -> "Foundations"; 2 -> "Nave"; 3 -> "Windows and Walls"; 4 -> "Twin Towers"; else -> "Spire and Bell" }
    private fun cathReady() = age >= 4 && villagers.size >= 16 && cathWork < CATH_STEPS.last() && wood >= 150 && stone >= 200
    private fun cathOk(v: Villager) = cathReady() && !isChild(v) && !isNight() && !alert()

    private fun markCath() {
        if (cathX < 0) return
        for (yy in cathY..cathY + 2) for (xx in cathX..cathX + 4) if (xx in 0 until GW && yy in 0 until GH) occupied[yy * GW + xx] = true
    }

    /** Finds a flat 5x3 plot near the hall for the cathedral, clear of huts, statues and the graveyard. */
    private fun pickCathedral() {
        if (time < cathTryAt) return
        cathTryAt = time + 60.0
        val hub = buildings.firstOrNull { it.type == "hall" } ?: buildings.firstOrNull { it.type == "campfire" } ?: return
        for (r in 7..24 step 2) for (k in 0 until 28) {
            val ang = k * 0.2244f + r * 0.41f
            val x0 = (hub.x + 0.5f + kotlin.math.cos(ang) * r).toInt() - 2
            val y0 = (hub.y + 0.5f + kotlin.math.sin(ang) * r * 0.7f).toInt() - 1
            if (x0 < 3 || y0 < 3 || x0 + 7 >= GW - 2 || y0 + 6 >= GH - 2) continue
            var ok = true
            loop@ for (yy in y0 - 1..y0 + 4) for (xx in x0 - 1..x0 + 5) {
                val i = yy * GW + xx
                if (tiles[i] == T.WATER || tiles[i] == T.FOREST || tiles[i] == T.ROCK || tiles[i] == T.ORE || occupied[i] || i in reserved) { ok = false; break@loop }
            }
            if (!ok) continue
            if (buildings.any { it.x >= x0 - 2 && it.x <= x0 + 6 && it.y >= y0 - 2 && it.y <= y0 + 5 }) continue
            if (statues.any { it.x >= x0 - 2 && it.x <= x0 + 7 && it.y >= y0 - 2 && it.y <= y0 + 5 }) continue
            if (graveX >= 0 && graveX + 7 >= x0 - 1 && graveX <= x0 + 6 && graveY + 5 >= y0 - 1 && graveY <= y0 + 5) continue
            if (seasonProps.any { it.x >= x0 - 2 && it.x <= x0 + 7 && it.y >= y0 - 2 && it.y <= y0 + 5 }) continue
            cathX = x0; cathY = y0
            markCath()
            addLog("Masons mark out a plot for a great cathedral.")
            return
        }
    }

    private fun cathTierUp(t: Int) {
        val nm = cathTierName(t)
        val cx = cathX + 2.5f
        val cy = cathY + 1.5f
        cathFlash = time
        fx.add(Fx("bless", cx, cy, time, 1.8f))
        fx.add(Fx("spawn", cx, cy, time, 1.8f))
        goods += 3 * t
        for (o in villagers) if (hypot(o.x - cx, o.y - cy) < 12f) o.happiness = min(100f, o.happiness + 6f)
        if (t >= 5) {
            jubileeUntil = time + DAY_LEN
            festivalUntil = max(festivalUntil, time + 60.0)
            for (o in villagers) { o.happiness = min(100f, o.happiness + 25f); o.hp = min(100f, o.hp + 25f) }
            event("The Great Cathedral is finished! Its bell rings out and the village holds a Jubilee.")
            narrate("The great cathedral is finished and its bell rings out")
        } else if (t == 1) {
            event("The first stones of the Great Cathedral are laid: $nm done.")
        } else {
            addLog("The Great Cathedral grows: $nm done (tier $t of 5).")
            mark("Cathedral tier $t: $nm.")
        }
    }

    /** Once the cathedral stands its bell tolls every dawn, and a Jubilee keeps everyone cheerful. */
    private fun cathTick() {
        if (time < nextCathAt) return
        nextCathAt = time + 5.0
        if (cathX < 0 || villagers.isEmpty()) return
        if (cathTier() >= 5) {
            val d = day()
            if (d != lastBellDay && !isNight() && frac() < 0.2) {
                lastBellDay = d
                cathBells++
                cathFlash = time
                for (o in villagers) o.happiness = min(100f, o.happiness + 3f)
                fx.add(Fx("bless", cathX + 2.5f, cathY + 0.5f, time, 1.2f))
                if (cathBells % 4 == 1) addLog("The cathedral bell tolls at dawn.")
            }
        }
        if (time < jubileeUntil) for (o in villagers) if (o.phase != "sleeping") o.happiness = min(100f, o.happiness + 1f)
    }

    /** Villagers who linger near a hero's statue feel proud: a little morale now and then. */
    private fun statueTick() {
        if (time < nextStatueAt) return
        nextStatueAt = time + 12.0
        if (statues.isEmpty()) return
        for (v in villagers) {
            if (v.phase == "sleeping") continue
            val s = statues.firstOrNull { hypot(v.x - it.x, v.y - it.y) < 4f } ?: continue
            v.happiness = min(100f, v.happiness + 1.5f)
            statueCheers++
            if (statueCheers % 40 == 1) v.remember("looked up at the statue of ${s.name}")
        }
    }

    private fun creditWork(v: Villager) {
        if (v.phase != "working") return
        when {
            v.action.startsWith("gather_") || v.action == "mine_ore" || v.action == "tend_farm" -> v.bump("gathered", max(1, v.gathered))
            v.action == "fish" -> v.bump("fish", max(1, v.gathered))
            v.action.startsWith("build_") -> v.bump("built")
            v.action == "tell_story" -> v.bump("stories")
            v.action == "pick_flowers" -> v.bump("posies")
            v.action == "carve" -> v.bump("carved")
            else -> return
        }
        checkTitles(v)
    }

    private fun finish(v: Villager) {
        creditWork(v)
        v.action = ""
        v.phase = "idle"
        v.idleFor = 0f
        v.waiting = false
        v.gathered = 0
        v.path = IntArray(0)
        v.talkTo = null
        v.talkMsg = ""
    }

    private fun arrive(v: Villager) {
        when {
            v.action.startsWith("gather_") || v.action == "mine_ore" -> { v.phase = "working"; v.workLeft = 3f }
            v.action == "hunt" -> { v.phase = "working"; v.workLeft = 5f }
            v.action == "smelt" -> { v.phase = "working"; v.workLeft = 6f }
            v.action.startsWith("build_") -> { v.phase = "working"; v.workLeft = 6f }
            v.action == "eat" -> { v.phase = "working"; v.workLeft = 2f }
            v.action == "sleep" -> v.phase = "sleeping"
            v.action == "fish" -> { v.phase = "working"; v.workLeft = 4f }
            v.action == "tend_farm" -> { v.phase = "working"; v.workLeft = 5f }
            v.action == "explore" -> { v.phase = "working"; v.workLeft = 5f }
            v.action == "huddle" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "fetch_water" -> { v.phase = "working"; v.workLeft = 3f }
            v.action == "haul" -> { v.phase = "working"; v.workLeft = 4f }
            v.action == "attack" -> { v.phase = "working"; v.workLeft = 2.5f }
            v.action == "drill" -> { v.phase = "working"; v.workLeft = 6f }
            v.action == "study" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "tavern" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "recover" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "honor" -> { v.phase = "working"; v.workLeft = 6f }
            v.action == "mourn" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "revel" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "council" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "vigil" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "assault_camp" -> { v.phase = "working"; v.workLeft = 3f }
            v.action == "tend_flock" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "tend_bees" -> { v.phase = "working"; v.workLeft = 6f }
            v.action == "pick_fruit" -> { v.phase = "working"; v.workLeft = 6f }
            v.action == "shoot" -> { v.phase = "working"; v.workLeft = 5f }
            v.action == "soak" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "ring_bell" -> { v.phase = "working"; v.workLeft = 5f }
            v.action == "show" -> { v.phase = "working"; v.workLeft = 9f }
            v.action == "fire_pots" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "train_critters" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "weave" -> { v.phase = "working"; v.workLeft = 9f }
            v.action == "sail" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "quarry" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "grind" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "raise_cathedral" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "tend_critters" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "craft" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "trade" -> { v.phase = "working"; v.workLeft = 4f }
            v.action == "dance" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "play" -> { v.phase = "working"; v.workLeft = 5f }
            v.action == "swim" -> { v.phase = "working"; v.workLeft = 6f }
            v.action == "snowman" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "rake_leaves" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "fly_kite" -> { v.phase = "working"; v.workLeft = 9f }
            v.action == "puddle_jump" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "catch_fireflies" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "hide_seek" -> { v.phase = "working"; v.workLeft = 9f }
            v.action == "walk_dog" -> { v.phase = "working"; v.workLeft = 10f }
            v.action == "teach" -> { v.phase = "working"; v.workLeft = 9f }
            v.action == "forage_herbs" -> { v.phase = "working"; v.workLeft = 4f }
            v.action == "stargaze" -> { v.phase = "working"; v.workLeft = 8f }
            v.action == "gather_star" -> { v.phase = "working"; v.workLeft = 5f }
            v.action == "douse" -> { v.phase = "working"; v.workLeft = 3.2f }
            v.action == "carve" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "pick_flowers" -> { v.phase = "working"; v.workLeft = 4f }
            v.action == "delve" -> { v.phase = "working"; v.workLeft = 9999f }
            v.action == "patrol" -> { v.phase = "working"; v.workLeft = 3f }
            v.action.startsWith("critter_") -> critters.onArrive(v)
            v.action == "tell_story" -> {
                v.phase = "working"
                v.workLeft = 9f
                val story = Speech.clean(storyFor(), 90)
                v.say = if (isChild(v)) Speech.kidify(story) else story
                v.sayLeft = 8f
                noteSaid(v, story)
                addLog("${v.name} tells a story by the fire.")
                narrate("${v.name} tells a story by the fire")
                val listeners = villagers.filter {
                    it !== v && it.phase != "sleeping" && hypot(it.x - v.x, it.y - v.y) < 9f
                }
                for (l in listeners) {
                    l.happiness = min(100f, l.happiness + 8f)
                    l.affinity[v.id] = (l.affinity[v.id] ?: 0) + 1
                    v.affinity[l.id] = (v.affinity[l.id] ?: 0) + 1
                }
                if (listeners.isNotEmpty()) {
                    val l = listeners[rnd.nextInt(listeners.size)]
                    l.replyIn = 3f
                    l.replyTo = v
                    l.replyText = replyTo(l, v, story)
                    l.remember("${v.name} told a story: ${story.take(50)}")
                    v.remember("told ${l.name} a story")
                }
                v.happiness = min(100f, v.happiness + 5f)
            }
            v.action == "talk" -> {
                val o = v.talkTo
                v.bump("a_talk")
                v.lastTalk = time
                if (o != null) {
                    if (o.id == v.partner && rnd.nextInt(2) == 0) v.talkMsg = partnerLine(v, o)
                    v.say = v.talkMsg.take(90)
                    v.sayLeft = 7f
                    noteSaid(v, v.talkMsg)
                    o.replyTo = v
                    o.heard.add("${v.name}: ${v.talkMsg}")
                    while (o.heard.size > 3) o.heard.removeAt(0)
                    addLog("${v.name} to ${o.name}: ${v.talkMsg}")
                    narrate("${v.name} to ${o.name}: ${v.talkMsg}")
                    val argue = v.persona.trait.contains("grumpy") && rnd.nextInt(3) == 0
                    if (argue) {
                        v.affinity[o.id] = (v.affinity[o.id] ?: 0) - 2
                        o.affinity[v.id] = (o.affinity[v.id] ?: 0) - 2
                        o.happiness = max(0f, o.happiness - 6f)
                        o.replyText = "Oh, give it a rest, ${v.name}."
                        addLog("${v.name} and ${o.name} bicker.")
                        o.remember("${v.name} was rude to me")
                    } else {
                        v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1
                        o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1
                        v.happiness = min(100f, v.happiness + 4f)
                        o.happiness = min(100f, o.happiness + 4f)
                        o.replyText = replyTo(o, v, v.talkMsg)
                        o.remember("${v.name} said: ${v.talkMsg.take(50)}")
                    }
                    o.replyIn = 2.5f
                    v.remember("told ${o.name}: ${v.talkMsg.take(40)}")
                }
                finish(v)
            }
            else -> finish(v)
        }
    }

    private fun work(v: Villager, dt: Float) {
        if (v.action.startsWith("critter_")) {
            critters.onWork(v)
            return
        }
        v.workLeft -= dt * skillSpeed(v)
        if (v.workLeft > 0f) return
        v.bump("a_" + v.action)
        val sb = skillYield(v)
        skillGain(v)
        when (v.action) {
            "gather_wood", "gather_stone", "gather_food", "mine_ore" -> {
                val i = v.targetTile
                if (i >= 0 && amt[i] > 0) {
                    amt[i]--
                    v.gathered++
                    val y = 1 + sb + toolBonus() + (if ((v.action == "gather_wood" && hasTech("saws")) || ((v.action == "gather_stone" || v.action == "mine_ore") && hasTech("quarry"))) 1 else 0)
                    if ((v.action == "gather_wood" && wood >= capWood()) || (v.action == "gather_stone" && stone >= capStone()) ||
                        (v.action == "gather_food" && food >= capFood())
                    ) wastedGathers++
                    when (v.action) {
                        "gather_wood" -> wood += y
                        "gather_stone" -> stone += y
                        "mine_ore" -> ore += y
                        else -> food += 2 + sb + toolBonus() + (if (time < totemBlessUntil) 1 else 0) + (if (hasTech("farm")) 1 else 0)
                    }
                    if (amt[i] == 0 && tiles[i] != T.BUSH) tiles[i] = T.GRASS
                }
                if (v.gathered >= 4 || i < 0 || amt[i] <= 0) {
                    if (v.gathered >= 3) v.remember("brought back ${v.gathered} ${v.action.removePrefix("gather_")}")
                    finish(v)
                } else v.workLeft = 3f
            }
            "build_hut", "build_farm", "build_campfire", "build_smelter", "build_well", "build_workshop", "build_market", "build_lamp", "build_granary", "build_barracks", "build_library", "build_wonder", "build_tavern", "build_infirmary", "build_totem", "build_ranch", "build_pasture", "build_apiary", "build_orchard", "build_range", "build_bath", "build_belfry", "build_stage", "build_kiln", "build_yard", "build_loom", "build_dock", "build_quarry", "build_mill", "build_shrine" -> {
                val i = v.targetTile
                reserved.remove(i)
                val type = v.action.removePrefix("build_")
                val label = if (type == "hut" && age == 0) "tent" else if (type == "smelter") "forge" else if (type == "lamp") "lantern post" else if (type == "totem") "totem pole" else if (type == "ranch") "critter ranch" else if (type == "pasture") "pasture" else if (type == "apiary") "apiary" else if (type == "range") "archery range" else if (type == "bath") "hot spring" else if (type == "belfry") "bell tower" else if (type == "stage") "puppet theatre" else if (type == "kiln") "potter's kiln" else if (type == "yard") "critter training yard" else if (type == "loom") "weaver's loom" else if (type == "orchard") "orchard" else if (type == "dock") "fishing dock" else if (type == "quarry") "stone quarry" else if (type == "mill") "windmill" else if (type == "shrine") "legend shrine" else type
                if (type == "wonder" && i >= 0 && !occupied[i] && tiles[i] == T.GRASS) {
                    val kind = (0..2).firstOrNull { k -> buildings.none { it.type == "wonder" && it.era == k } } ?: 0
                    addBuilding("wonder", i % GW, i / GW, kind)
                    buildings.last().built = time; raised++
                    val nm = wonderName(kind)
                    addLog("${v.name} and the village raise the $nm!")
                    event("The $nm rises over the village: ${wonderBlurb(kind)}")
                    mark("Raised the $nm.")
                    narrate("${v.name} finishes the $nm")
                    v.remember("helped raise the $nm")
                    for (o in villagers) o.happiness = min(100f, o.happiness + 12f)
                    festivalUntil = max(festivalUntil, time + 40.0)
                    wonderFx = time; wonderFxKind = kind
                } else if (i >= 0 && !occupied[i] && tiles[i] == T.GRASS) {
                    addBuilding(type, i % GW, i / GW)
                    buildings.last().built = time; raised++
                    addLog("${v.name} built a $label.")
                    narrate("${v.name} finishes a $label")
                    v.remember("built a $label")
                    v.happiness = min(100f, v.happiness + 8f)
                }
                finish(v)
            }
            "eat" -> {
                if (food > 0) {
                    food--
                    if (v.hunger > 55f) v.happiness = min(100f, v.happiness + 4f)
                    v.hunger = max(0f, v.hunger - 45f)
                }
                finish(v)
            }
            "fish" -> {
                v.gathered++
                if (rnd.nextInt(4) != 0) food++
                if (buildings.any { it.type == "dock" && it.y * GW + it.x == v.targetTile }) { food = min(capFood(), food + 1); dockCatch++ }
                if (v.gathered >= 3) {
                    v.remember("fished at the lake")
                    finish(v)
                } else v.workLeft = 4f
            }
            "tend_farm" -> {
                val b = buildings.firstOrNull { it.type == "farm" && it.y * GW + it.x == v.targetTile }
                if (b != null) {
                    var got = b.ready * 2 + 1 + (if (hasTech("farm")) 1 else 0)
                    if (drought()) {
                        if (farmsWatered()) droughtSaved++
                        else { got = max(1, (got + 1) / 2); droughtWithered++ }
                    }
                    food += got
                    b.ready = 0
                    b.boostUntil = time + 120.0
                    addLog("${v.name} tended the farm (+$got food).")
                    v.remember("tended the farm")
                    v.happiness = min(100f, v.happiness + 3f)
                }
                finish(v)
            }
            "explore" -> {
                explored++
                val roll = rnd.nextInt(100)
                when {
                    roll < 45 -> {
                        val kind = rnd.nextInt(3)
                        val n = 3 + rnd.nextInt(2) + (if (hasTech("stars")) 2 else 0)
                        val what = when (kind) {
                            0 -> { wood += n; "wood" }
                            1 -> { stone += n; "stone" }
                            else -> { food += n; "food" }
                        }
                        addLog("${v.name} found an abandoned cache beyond the hills (+$n $what).")
                        narrate("${v.name} found a cache of $what")
                        v.remember("found a cache of $what out in the hills")
                        v.happiness = min(100f, v.happiness + 6f)
                    }
                    roll < 60 -> {
                        alertUntil = max(alertUntil, time + 70.0)
                        addLog("${v.name} spotted wolf tracks near the village!")
                        narrate("${v.name} spotted wolf tracks")
                        v.remember("saw wolf tracks and warned everyone")
                    }
                    roll < 75 -> {
                        food += 3
                        addLog("${v.name} found a hidden berry grove (+3 food).")
                        v.remember("found a hidden berry grove")
                    }
                    else -> {
                        addLog("${v.name} scouted the hills but found only wind.")
                        v.remember("scouted the hills, nothing to report")
                    }
                }
                finish(v)
            }
            "attack" -> {
                // a charge: strike whatever enemy is close
                val p = predators.filter { hypot(it.x - v.x, it.y - v.y) < 3.2f }.minByOrNull { it.hp }
                if (p != null) {
                    p.hp -= 12f + 4f * toolBonus()
                    fx.add(Fx("clash", p.x, p.y, time, 0.8f))
                    v.happiness = min(100f, v.happiness + 2f)
                }
                finish(v)
            }
            "haul" -> {
                // a granary keeps the harvest from spoiling
                food += 2
                v.remember("filled the granary")
                finish(v)
            }
            "drill" -> {
                v.hp = min(100f, v.hp + 12f)
                v.happiness = min(100f, v.happiness + 1f)
                if (rnd.nextInt(5) == 0) addLog("${v.name} drills with the guard.")
                finish(v)
            }
            "study" -> {
                v.happiness = min(100f, v.happiness + 3f)
                studied++
                studyCount++
                research += 1 + (if (v.job == "Storyteller" || v.job == "Scholar") 1 else 0) + (if (count("library") >= 2) 1 else 0) + (if (hasTech("scribes")) 1 else 0)
                if (studied % 6 == 1) addLog("${v.name} studies in the library.")
                v.remember("read in the library")
                finish(v)
            }
            "assault_camp" -> {
                if (campUp) {
                    val dmg = 8f + rnd.nextInt(7) + (if (isFighter(v)) 5f else 0f) + toolBonus() * 1.5f + (if (hasTech("blades")) 4f else 0f)
                    campHp -= dmg
                    campHits++
                    campHitAt = time
                    v.hp = max(30f, v.hp - (3f + rnd.nextInt(7)))
                    v.bump("camp", 1)
                    fx.add(Fx("clash", campX + (rnd.nextFloat() - 0.5f) * 1.8f, campY + 0.4f + (rnd.nextFloat() - 0.5f), time, 0.8f))
                    if (campHits % 7 == 1) addLog("${v.name} charges the bandit camp.")
                    v.remember("stormed the bandit camp")
                    if (campHp <= 0f) razeCamp(v)
                }
                finish(v)
            }
            "tend_bees" -> {
                // the apiary: smoke the hives and take the ripe honey (a sting now and then, a mead feast every fifth harvest)
                val jars = honeyReady()
                if (jars >= 1) {
                    honeyMark = max(honeyMark, time - HONEY_EVERY * 4 * max(1, count("apiary"))) + HONEY_EVERY
                    honeyHarvests++
                    val got = 3 + min(3, jars) + (if (v.job == "Farmer") 1 else 0) + (if (hasTech("hives")) 2 else 0)
                    var what = "smokes the hives and takes $got meals of honey"
                    val room = max(0, capFood() - food)
                    if (room < got) { goods += 1; what = "takes honey from the hives and sells the surplus (+1 goods)" }
                    food += min(room, got); goods += 1; honeyJars += 1
                    if (honeyHarvests % 4 == 0) { beeStings++; v.hp = max(20f, v.hp - 3f); what = "is stung while taking the honey, but fills the jars all the same" }
                    if (honeyHarvests % (if (hasTech("feast")) 4 else 5) == 0) {
                        meadFeasts++
                        for (o in villagers) if (hypot(o.x - v.x, o.y - v.y) < 7f) o.happiness = min(100f, o.happiness + 5f)
                        what = "opens a fresh pot of honey and shares honey cakes round"
                        addLog("${v.name} $what.")
                        narrate("${v.name} $what")
                    } else if (honeyHarvests % 7 == 1) addLog("${v.name} $what at the apiary.")
                    v.happiness = min(100f, v.happiness + 3f)
                    val ab = nearestBuilding(v, "apiary")
                    if (ab != null) fx.add(Fx("spawn", ab.x + 0.5f, ab.y + 0.5f, time, 1.0f))
                    val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "tend_bees" && hypot(it.x - v.x, it.y - v.y) < 4f }
                    for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                    v.remember("took honey from the apiary")
                }
                finish(v)
            }
            "soak" -> {
                // the hot spring: steam, warm water and gossip mend wounds, tiredness and moods (winter soaks are the sweetest)
                val before = v.hp
                val winter = seasonIdx() == 3
                v.hp = min(100f, v.hp + 14f)
                v.energy = min(100f, v.energy + 18f)
                v.happiness = min(100f, v.happiness + (if (winter) 9f else 6f))
                soaks++
                if (before < 70f) soakHeals++
                val bb = nearestBuilding(v, "bath")
                if (bb != null) fx.add(Fx("spawn", bb.x + 0.5f, bb.y + 0.4f, time, 0.8f))
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "soak" && hypot(it.x - v.x, it.y - v.y) < 4f }
                for (o in mates) {
                    v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 2; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 2
                    if (soaks % 4 == 0) soakGossips++
                }
                if (soaks % 10 == 0) {
                    soakParties++
                    for (o in villagers) if (hypot(o.x - v.x, o.y - v.y) < 8f) o.happiness = min(100f, o.happiness + 3f)
                    addLog("A steamy soak at the hot spring lifts everyone's spirits.")
                } else if (before < 60f && soakHeals % 3 == 1) addLog("${v.name} eases aching bones in the hot spring.")
                else if (winter && soaks % 7 == 0) addLog("${v.name} soaks in the hot spring while snow falls.")
                v.remember(if (before < 60f) "healed in the hot spring" else "soaked in the hot spring")
                finish(v)
            }
            "weave" -> {
                // the weaver's loom: hides become cloth and goods; every third bolt is sewn into a winter cloak for an adult without one
                val lb = nearestBuilding(v, "loom")
                lastLoomAt = time
                if (hides >= 2) hides -= 2
                loomWeaves++
                goods += 2
                v.gathered = 0
                if (lb != null) fx.add(Fx("spawn", lb.x + 0.5f, lb.y + 0.3f, time, 1.0f))
                var cloakMade = false
                if (loomWeaves % 3 == 0) {
                    val adults = villagers.filter { !isChild(it) }.sortedBy { it.id }
                    if (loomCloaks < adults.size) {
                        loomCloaks++
                        cloakMade = true
                        val who = adults.getOrNull(loomCloaks - 1)
                        if (who != null) {
                            who.happiness = min(100f, who.happiness + 8f)
                            who.remember("received a woollen cloak")
                            if (who === v) addLog("${v.name} sews a cloak for themself ($loomCloaks cloaks now).")
                            else addLog("${v.name} sews a woollen cloak for ${who.name} ($loomCloaks cloaks now).")
                        }
                    }
                }
                if (!cloakMade && loomWeaves % 9 == 1) addLog("${v.name} weaves a bolt of cloth at the loom.")
                v.happiness = min(100f, v.happiness + 2f)
                v.remember(if (cloakMade) "sewed a woollen cloak" else "wove cloth at the loom")
                finish(v)
            }
            "train_critters" -> {
                // the critter training yard: drill a critter against the dummy; a sparring partner doubles the lesson, bouts cheer the yard
                val yb = nearestBuilding(v, "yard")
                lastYardAt = time
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "train_critters" && hypot(it.x - v.x, it.y - v.y) < 4f }
                val c = critters.trainYard(v, mates.size)
                if (c != null) {
                    yardSessions++
                    if (yb != null) fx.add(Fx("spawn", yb.x + 0.5f, yb.y + 0.4f, time, 1.0f))
                    for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                    if (mates.isNotEmpty()) yardSpars++
                    v.happiness = min(100f, v.happiness + 3f)
                    if (critters.lastTrainLevels > 0) addLog("${c.name} reaches level ${c.level} in the training yard (${v.name} drilled it).")
                    else if (yardSessions % 25 == 1) addLog("${v.name} drills ${c.name} against the straw dummy.")
                    v.remember(if (mates.isNotEmpty()) "sparred critters with ${mates[0].name} in the yard" else "drilled ${c.name} in the yard")
                }
                finish(v)
            }
            "fire_pots" -> {
                // the potter's kiln: wood fires clay into pots; every fifth firing is a glazed masterwork that cheers the neighbours
                val kb = nearestBuilding(v, "kiln")
                lastKilnAt = time
                if (wood >= 2) wood -= 2
                kilnFires++
                val glazed = kilnFires % 5 == 0
                goods += if (glazed) 4 else 1
                v.gathered = 0
                if (kb != null) {
                    fx.add(Fx("spawn", kb.x + 0.5f, kb.y + 0.3f, time, 1.0f))
                    if (glazed) {
                        kilnGlazed++
                        var seen = 0
                        for (o in villagers) if (o !== v && hypot(o.x - kb.x, o.y - kb.y) < 9f) { seen++; o.happiness = min(100f, o.happiness + 4f) }
                        kilnCheers += seen
                        addLog("${v.name} draws a glazed pot from the kiln; the neighbours admire it ($seen came to look).")
                    } else if (kilnFires % 7 == 1) addLog("${v.name} fires a batch of pots in the kiln.")
                }
                v.happiness = min(100f, v.happiness + 2f)
                v.remember(if (glazed) "glazed a fine pot" else "fired pots in the kiln")
                finish(v)
            }
            "show" -> {
                // the puppet theatre: the performer works the puppets and everybody close by watches and laughs
                val sb = nearestBuilding(v, "stage")
                lastShowAt = time
                shows++
                val grand = shows % 6 == 0
                if (sb != null) {
                    fx.add(Fx("spawn", sb.x + 0.5f, sb.y + 0.3f, time, 1.0f))
                    var seen = 0
                    for (o in villagers) if (o !== v && hypot(o.x - sb.x, o.y - sb.y) < (if (grand) 22f else 10f)) {
                        seen++
                        o.happiness = min(100f, o.happiness + (if (grand) 8f else if (isChild(o)) 7f else 4f))
                        if (hypot(o.x - sb.x, o.y - sb.y) < 10f) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                    }
                    showAudience += seen
                    if (grand) {
                        grandShows++
                        goods += 2
                        addLog("${v.name}'s grand puppet show draws a crowd of $seen; the village roars with laughter.")
                    } else if (shows % 3 == 1) addLog("${v.name} puts on a puppet show${if (seen > 0) " for $seen onlookers" else ""}.")
                }
                v.happiness = min(100f, v.happiness + 4f)
                v.remember(if (grand) "gave a grand puppet show" else "put on a puppet show")
                finish(v)
            }
            "ring_bell" -> {
                // the bell tower: a dawn and dusk peal cheers the village, an alarm peal in danger steadies the fighters
                val bb = nearestBuilding(v, "belfry")
                val alarm = alert()
                lastBellAt = time
                bellRings++
                if (bb != null) {
                    fx.add(Fx("spawn", bb.x + 0.5f, bb.y + 0.3f, time, 1.0f))
                    var reached = 0
                    for (o in villagers) if (hypot(o.x - bb.x, o.y - bb.y) < 16f) {
                        reached++
                        if (alarm) { if (isFighter(o)) o.hp = min(100f, o.hp + 6f) } else o.happiness = min(100f, o.happiness + 3f)
                    }
                    bellReach += reached
                }
                if (alarm) {
                    bellAlarms++
                    if (bellAlarms % 3 == 1) addLog("${v.name} rings the alarm bell and the fighters rally.")
                } else if (bellRings % 6 == 1) addLog("${v.name} rings the ${if (partOfDay() == "morning") "morning" else "evening"} bell over the village.")
                v.remember(if (alarm) "rang the alarm bell" else "rang the bell tower")
                finish(v)
            }
            "shoot" -> {
                // the archery range: arrows at straw targets, a sharper eye with practice, a friendly contest every eighth bullseye
                val rb = nearestBuilding(v, "range")
                val lv = skillLevel(v, "combat")
                shotsFired++
                val chance = 0.30f + lv * 0.06f + (if (isFighter(v)) 0.10f else 0f) + (if (v.job == "Hunter") 0.10f else 0f)
                val bull = rnd.nextFloat() < chance
                if (rb != null) fx.add(Fx("arrow", rb.x + 0.2f + rnd.nextFloat() * 0.6f, rb.y + 0.15f, time, 0.6f))
                v.happiness = min(100f, v.happiness + 1.5f)
                if (bull) {
                    bullseyes++
                    v.happiness = min(100f, v.happiness + 3f)
                    rangeScore[v.id] = (rangeScore[v.id] ?: 0) + 1
                    if (rb != null) fx.add(Fx("spawn", rb.x + 0.5f, rb.y + 0.3f, time, 0.9f))
                    if (bullseyes % 8 == 0) {
                        val top = rangeScore.maxByOrNull { it.value }
                        val champ = villagers.firstOrNull { it.id == (top?.key ?: v.id) } ?: v
                        archeryMatches++
                        archeryBest = champ.name
                        goods += 2
                        for (o in villagers) if (hypot(o.x - v.x, o.y - v.y) < 7f) {
                            o.happiness = min(100f, o.happiness + 3f)
                            if (o !== champ) { champ.affinity[o.id] = (champ.affinity[o.id] ?: 0) + 1 }
                        }
                        champ.happiness = min(100f, champ.happiness + 8f)
                        rangeScore.clear()
                        addLog("${champ.name} wins the archery contest at the range.")
                        narrate("${champ.name} wins the archery contest")
                        champ.remember("won the archery contest")
                    } else if (bullseyes % 5 == 1) addLog("${v.name} hits the bullseye at the archery range.")
                } else if (shotsFired % 9 == 0) addLog("${v.name} practises archery at the range.")
                v.remember("practised archery")
                finish(v)
            }
            "pick_fruit" -> {
                // the orchard: climb, shake and fill baskets (windfalls now and then, a cider feast every fifth basket in autumn)
                val ripe = fruitReady()
                if (ripe >= 1) {
                    fruitMark = max(fruitMark, time - FRUIT_EVERY * 5 * max(1, count("orchard"))) + FRUIT_EVERY
                    fruitPicks++
                    val autumn = seasonIdx() == 2
                    val got = (if (autumn) 5 else 3) + min(2, ripe / 2) + (if (v.job == "Farmer" || v.job == "Forager") 1 else 0)
                    var what = "fills a basket with ${if (autumn) "ripe" else "early"} fruit"
                    val room = max(0, capFood() - food)
                    if (room < got) { goods += 1; what = "fills a basket and sells the surplus fruit (+1 goods)" }
                    food += min(room, got); fruitBaskets++
                    if (fruitPicks % 3 == 0) goods += 1
                    if (fruitPicks % 4 == 0) { fruitFalls++; v.hp = max(20f, v.hp - 2f); what = "slips off the ladder in a shower of fruit, but keeps the basket full" }
                    if (autumn && fruitPicks % 5 == 0) {
                        ciderFeasts++
                        for (o in villagers) if (hypot(o.x - v.x, o.y - v.y) < 7f) o.happiness = min(100f, o.happiness + 5f)
                        what = "presses fresh cider and passes the cups round"
                        addLog("${v.name} $what.")
                        narrate("${v.name} $what")
                    } else if (fruitPicks % 7 == 1) addLog("${v.name} $what at the orchard.")
                    v.happiness = min(100f, v.happiness + 3f)
                    val ob = nearestBuilding(v, "orchard")
                    if (ob != null) fx.add(Fx("spawn", ob.x + 0.5f, ob.y + 0.5f, time, 1.0f))
                    val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "pick_fruit" && hypot(it.x - v.x, it.y - v.y) < 4f }
                    for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                    v.remember("picked fruit in the orchard")
                }
                finish(v)
            }
            "tend_flock" -> {
                // the pasture: gather eggs, shear the sheep, and now and then the flock grows
                pastureVisits++
                val np = max(1, count("pasture"))
                if (flockSheep < 2) flockSheep = 2
                if (flockHens < 3) flockHens = 3
                val eggs = 1 + min(2, flockHens / 5)
                food = min(capFood(), food + eggs); eggsGathered += eggs
                var what = "gathers $eggs eggs"
                if (pastureVisits % 3 == 0) { hides += 1; fleeces++; what = "shears a sheep" }
                if (pastureVisits % 4 == 0) {
                    if (flockSheep < 3 * np && (pastureVisits / 4) % 2 == 0) { flockSheep++; addLog("A lamb is born at the pasture.") }
                    else if (flockHens < 5 * np) { flockHens++; addLog("A hen hatches a new chick at the pasture.") }
                }
                v.happiness = min(100f, v.happiness + 4f)
                val pb = nearestBuilding(v, "pasture")
                if (pb != null) fx.add(Fx("spawn", pb.x + 0.5f, pb.y + 0.5f, time, 1.0f))
                if (pastureVisits % 6 == 1) addLog("${v.name} $what at the pasture.")
                if (pastureVisits % 11 == 5) narrate("${v.name} $what at the pasture")
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "tend_flock" && hypot(it.x - v.x, it.y - v.y) < 4f }
                for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                v.remember("tended the flock at the pasture")
                finish(v)
            }
            "quarry" -> {
                // hammer and wedge: blocks of stone from the pit, and now and then a vein of ore or a fine block
                quarryHauls++
                var got = 4 + rnd.nextInt(3) + toolBonus() + (if (hasTech("quarry")) 2 else 0) + (if (v.job == "Miner") 2 else 0)
                got = min(got, max(0, capStone() - stone))
                stone += got; quarryStone += got
                var what = "cuts $got blocks of stone at the quarry"
                if (quarryHauls % 8 == 0) {
                    quarryVeins++
                    if (age >= 2) { ore += 3; what = "strikes a vein of ore in the quarry (+3 ore)" }
                    else { goods += 2; what = "cuts a fine block of marble at the quarry (+2 goods)" }
                    addLog("${v.name} $what.")
                    narrate("${v.name} $what")
                }
                v.happiness = min(100f, v.happiness + 2f)
                val qb = nearestBuilding(v, "quarry")
                if (qb != null) fx.add(Fx("spawn", qb.x + 0.5f, qb.y + 0.5f, time, 1.0f))
                if (quarryHauls % 9 == 1) addLog("${v.name} $what.")
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "quarry" && hypot(it.x - v.x, it.y - v.y) < 4f }
                for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                v.remember("cut stone at the quarry")
                finish(v)
            }
            "raise_cathedral" -> {
                if (cathX >= 0 && wood >= 10 && stone >= 10 && cathWork < CATH_STEPS.last()) {
                    wood -= 10; stone -= 10
                    val before = cathTier()
                    cathWork += (if (v.job == "Builder" || v.job == "Miner") 2 else 1) + (if (hasTech("marvels")) 1 else 0)
                    cathTrips++
                    fx.add(Fx("spawn", cathX + 2.5f, cathY + 1.5f, time, 1.0f))
                    if (cathTier() > before) cathTierUp(cathTier())
                    else if (cathTrips % 12 == 1) addLog("${v.name} lays stone for the great cathedral (${cathTierName(cathTier() + 1)}, $cathWork/${CATH_STEPS.last()}).")
                    v.happiness = min(100f, v.happiness + 2f)
                    val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "raise_cathedral" && hypot(it.x - v.x, it.y - v.y) < 5f }
                    for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                    v.remember("laid stone on the great cathedral")
                }
                finish(v)
            }
            "grind" -> {
                // the windmill: grain from the farms is ground to flour and baked, so a farm feeds more mouths
                millGrinds++
                val farms = count("farm")
                var got = 3 + rnd.nextInt(3) + min(3, farms / 2) + (if (v.job == "Farmer") 2 else 0) + (if (hasTech("farm")) 1 else 0) + (if (hasTech("mill2")) 2 else 0)
                val room = max(0, capFood() - food)
                var what = "grinds grain at the windmill into $got meals of flour"
                if (room < got) { goods += 1; what = "grinds the surplus grain at the windmill and sells the flour (+1 goods)"; got = room }
                food += got; flourMade += got
                if (millGrinds % (if (hasTech("feast")) 4 else 6) == 0) {
                    loavesBaked++
                    for (o in villagers) if (hypot(o.x - v.x, o.y - v.y) < 7f) o.happiness = min(100f, o.happiness + 4f)
                    goods += 1
                    what = "bakes a fresh loaf from the windmill flour and shares it round"
                    addLog("${v.name} $what.")
                    narrate("${v.name} $what")
                }
                v.happiness = min(100f, v.happiness + 3f)
                val mb = nearestBuilding(v, "mill")
                if (mb != null) fx.add(Fx("spawn", mb.x + 0.5f, mb.y + 0.5f, time, 1.0f))
                if (millGrinds % 9 == 1) addLog("${v.name} $what.")
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "grind" && hypot(it.x - v.x, it.y - v.y) < 4f }
                for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                v.remember("ground grain at the windmill")
                finish(v)
            }
            "sail" -> {
                // a boat from the dock: a bigger catch, and now and then a sealed bottle from the deep
                sailTrips++
                val got = 3 + rnd.nextInt(3) + (if (v.job == "Forager") 1 else 0)
                food = min(capFood(), food + got); dockCatch += got
                var what = "rows out and hauls in $got fish"
                if (sailTrips % 6 == 0) {
                    bottlesFound++
                    if (age >= 2) { metal += 1; what = "fishes a sealed bottle from the lake holding a trader's map (+1 metal)" }
                    else { goods += 2; hides += 1; what = "fishes a sealed bottle from the lake holding a few trinkets (+2 goods, +1 hide)" }
                    addLog("${v.name} $what.")
                    narrate("${v.name} $what")
                }
                v.happiness = min(100f, v.happiness + 5f)
                val db = nearestBuilding(v, "dock")
                if (db != null) fx.add(Fx("spawn", db.x + 0.5f, db.y + 0.5f, time, 1.0f))
                if (sailTrips % 7 == 1) addLog("${v.name} $what.")
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "sail" && hypot(it.x - v.x, it.y - v.y) < 4f }
                for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                v.remember("rowed out from the dock and fished the lake")
                finish(v)
            }
            "tend_critters" -> {
                // the critter ranch: feed and groom the village's own critters
                val c = critters.tendRanch(v)
                if (c != null) {
                    ranchVisits++
                    v.happiness = min(100f, v.happiness + 5f)
                    val rb = nearestBuilding(v, "ranch")
                    if (rb != null) fx.add(Fx("spawn", rb.x + 0.5f, rb.y + 0.5f, time, 1.0f))
                    if (ranchVisits % 5 == 1) addLog("${v.name} feeds and brushes ${c.name} at the ranch.")
                    if (ranchVisits % 9 == 4) narrate("${v.name} grooms ${c.name} at the ranch")
                    val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "tend_critters" && hypot(it.x - v.x, it.y - v.y) < 4f }
                    for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                    v.remember("fed ${c.name} at the ranch")
                }
                finish(v)
            }
            "vigil" -> {
                // a quiet vigil at the Legend Shrine: the gauge fills, and when it is full a legend of the chain answers
                vigils++; vigilPoints++
                v.happiness = min(100f, v.happiness + 5f)
                v.energy = min(100f, v.energy + 3f)
                val sh = nearestBuilding(v, "shrine")
                if (sh != null) fx.add(Fx("spawn", sh.x + 0.5f, sh.y + 0.5f, time, 1.0f))
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "vigil" && hypot(it.x - v.x, it.y - v.y) < 4f }
                for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                if (vigils % 10 == 1) addLog("${v.name} keeps a quiet vigil at the legend shrine (${min(vigilPoints, vigilNeed())}/${vigilNeed()}).")
                v.remember(if (mates.isNotEmpty()) "kept vigil at the shrine with ${mates[0].name}" else "kept vigil at the legend shrine")
                if (sh != null && vigilPoints >= vigilNeed() && time >= shrineCallUntil) callLegend(sh, v)
                finish(v)
            }
            "council" -> {
                // a seat at the village council: say what the village really needs, and bond with the neighbours
                if (councilOn && v.id !in councilGuestIds) {
                    councilGuestIds.add(v.id)
                    councilSeats++
                    v.happiness = min(100f, v.happiness + 2f)
                    val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "council" && hypot(it.x - v.x, it.y - v.y) < 5f }
                    for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                    if (councilGuestIds.size <= 3) {
                        val line = councilSpeech(v)
                        v.say = line; v.sayLeft = 7f; noteSaid(v, line)
                    }
                    fx.add(Fx("spawn", councilX, councilY, time, 1.0f))
                    v.remember("sat on the village council")
                }
                finish(v)
            }
            "revel" -> {
                // the seasonal fete: dance round the maypole, bonfire, harvest table or lantern tree
                feteRevels++
                feteGuestIds.add(v.id)
                v.happiness = min(100f, v.happiness + 9f)
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "revel" && hypot(it.x - v.x, it.y - v.y) < 5f }
                for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                for (o in villagers) if (o !== v && o.phase != "sleeping" && hypot(o.x - v.x, o.y - v.y) < 6f) o.happiness = min(100f, o.happiness + 2f)
                if (feteKind == "harvest" && food >= 1 && v.hunger >= 25f) { food -= 1; v.hunger = max(0f, v.hunger - 25f) }
                if (feteKind == "lantern") v.hp = min(100f, v.hp + 4f)
                if (feteOn) fx.add(Fx("spawn", feteX, feteY, time, 1.0f))
                if (feteRevels % 8 == 1) addLog("${v.name} joins the ${feteTitle()}.")
                v.remember(if (mates.isNotEmpty()) "joined the ${feteTitle()} with ${mates[0].name}" else "joined the ${feteTitle()}")
                finish(v)
            }
            "mourn" -> {
                // the graveyard: a quiet moment, flowers on a grave, and a little healing of the heart
                val g = graves.minByOrNull { hypot(it.x - v.x, it.y - v.y) }
                if (g != null && hypot(g.x - v.x, g.y - v.y) <= 3f) {
                mournVisits++
                g.tribute = time
                v.happiness = min(100f, v.happiness + 6f)
                v.energy = min(100f, v.energy + 2f)
                fx.add(Fx("bless", g.x, g.y, time, 1.0f))
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "mourn" && hypot(it.x - v.x, it.y - v.y) < 4f }
                for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                if (mournVisits % 3 == 1) addLog("${v.name} lays flowers on the grave of ${g.name} the ${g.job.lowercase()}.")
                v.remember(if (mates.isNotEmpty()) "mourned ${g.name} at the graveyard with ${mates[0].name}" else "laid flowers on the grave of ${g.name}")
                }
                finish(v)
            }
            "honor" -> {
                // the totem pole: a quiet bow, a dance, and now and then an omen that blesses the foraging
                totemVisits++
                v.happiness = min(100f, v.happiness + 6f)
                v.energy = min(100f, v.energy + 4f)
                val tot = nearestBuilding(v, "totem")
                if (tot != null) fx.add(Fx("spawn", tot.x + 0.5f, tot.y + 0.5f, time, 1.0f))
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "honor" && hypot(it.x - v.x, it.y - v.y) < 4f }
                for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                if (totemVisits % 4 == 0 || time > totemBlessUntil + 60.0 && rnd.nextInt(4) == 0) {
                    if (time >= totemBlessUntil) {
                        omens++
                        totemBlessUntil = time + 160.0
                        food += 3
                        addLog("${v.name} honors the totem pole. An omen of plenty: foraging is blessed for a while (+3 food).")
                        narrate("${v.name} reads a good omen at the totem")
                    }
                } else if (totemVisits % 6 == 1) addLog("${v.name} bows before the totem pole.")
                v.remember(if (mates.isNotEmpty()) "honored the totem with ${mates[0].name}" else "honored the totem pole")
                finish(v)
            }
            "recover" -> {
                // the healer's house: rest, bandages and broth mend wounds fast
                val before = v.hp
                v.hp = min(100f, v.hp + 35f)
                v.energy = min(100f, v.energy + 12f)
                v.happiness = min(100f, v.happiness + 4f)
                recoveries++
                if (before < 60f && recoveries % 3 == 1) addLog("${v.name} is bandaged and fed broth at the infirmary.")
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "recover" && hypot(it.x - v.x, it.y - v.y) < 4f }
                for (o in mates) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1 }
                v.remember(if (before < 60f) "was bandaged at the infirmary" else "rested at the infirmary")
                finish(v)
            }
            "tavern" -> {
                if (food >= 2) food--
                v.hunger = max(0f, v.hunger - 25f)
                v.happiness = min(100f, v.happiness + 5f)
                tavernVisits++
                val mates = villagers.filter { it !== v && it.phase == "working" && it.action == "tavern" && hypot(it.x - v.x, it.y - v.y) < 4f }
                for (o in mates) {
                    v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1
                    o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1
                    o.happiness = min(100f, o.happiness + 2f)
                }
                if (mates.isNotEmpty() && tavernVisits % 3 == 0) {
                    addLog("${v.name} and ${mates[0].name} share a drink at the tavern.")
                    narrate("${v.name} and ${mates[0].name} raise a cup")
                } else if (tavernVisits % 8 == 1) addLog("${v.name} drops by the tavern for a bite and a song.")
                v.remember(if (mates.isNotEmpty()) "shared a drink with ${mates[0].name} at the tavern" else "ate and rested at the tavern")
                val tb = nearestBuilding(v, "tavern")
                if (tb != null && mates.isNotEmpty()) {
                    val o = mates[0]
                    when (tavernVisits % 4) {
                        0 -> {
                            // a round of dice: one winner, a good laugh for the loser
                            val vWins = rnd.nextBoolean()
                            val w = if (vWins) v else o
                            val l = if (vWins) o else v
                            w.happiness = min(100f, w.happiness + 4f); l.happiness = min(100f, l.happiness + 1f)
                            tavDice++; tavernFx = time; tavernFxKind = 0
                            if (tavDice % 3 == 1) addLog("${w.name} wins a round of dice against ${l.name}, who laughs it off.")
                            w.remember("won at dice against ${l.name}")
                        }
                        1 -> {
                            // arm-wrestling: the better fighter usually wins, both grow closer
                            val sv = skillLevel(v, "combat") * 3 + (v.hp / 10f).toInt() + rnd.nextInt(8)
                            val so = skillLevel(o, "combat") * 3 + (o.hp / 10f).toInt() + rnd.nextInt(8)
                            val w = if (sv >= so) v else o
                            val l = if (sv >= so) o else v
                            w.happiness = min(100f, w.happiness + 5f); l.happiness = min(100f, l.happiness + 2f)
                            w.bump("xp_combat", 3); l.bump("xp_combat", 2)
                            v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 2; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 2
                            tavWrestles++; tavernFx = time; tavernFxKind = 1
                            if (tavWrestles % 3 == 1) addLog("${w.name} beats ${l.name} at arm-wrestling and buys the next round.")
                            w.remember("beat ${l.name} at arm-wrestling")
                        }
                        2 -> {
                            // a tall tale told from something that really happened
                            val tale = history(14).filter { it.length in 12..110 }.randomOrNull()
                            if (tale != null) {
                                var heard = 0
                                for (q in villagers) if (q !== v && hypot(q.x - tb.x, q.y - tb.y) < 5f) { q.happiness = min(100f, q.happiness + 3f); heard++ }
                                v.happiness = min(100f, v.happiness + 3f)
                                tavTales++; tavernFx = time; tavernFxKind = 2
                                if (tavTales % 2 == 1) addLog("${v.name} tells the tavern what happened: ${tale.trimEnd('.')}. $heard listen.")
                                v.remember("told the tavern a tale")
                            }
                        }
                        else -> {
                            // a sing-along that carries across the square
                            var sang = 0
                            for (q in villagers) if (hypot(q.x - tb.x, q.y - tb.y) < 7f) { q.happiness = min(100f, q.happiness + 2f); sang++ }
                            tavSongs++; tavernFx = time; tavernFxKind = 3
                            if (tavSongs % 3 == 1) addLog("A sing-along rises from the tavern; $sang villagers hum along.")
                        }
                    }
                    if (tavernVisits % 7 == 0) {
                        // every seventh visit the whole village drops in for a lantern night
                        var crowd = 0
                        for (q in villagers) if (hypot(q.x - tb.x, q.y - tb.y) < 12f) { q.happiness = min(100f, q.happiness + 6f); crowd++ }
                        goods += 1; tavNights++; tavernFx = time; tavernFxKind = 4
                        addLog("Lantern night at the tavern: $crowd villagers raise their cups.")
                        narrate("lanterns light up the tavern")
                    }
                }
                finish(v)
            }
            "fetch_water" -> {
                // watered crops grow faster
                for (b in buildings) if (b.type == "farm") b.boostUntil = max(b.boostUntil, time + 90.0)
                v.happiness = min(100f, v.happiness + 3f)
                if (drought()) {
                    droughtWaterUntil = time + 60.0; droughtWaterings++
                    food += 2
                    v.bump("gathered", 1)
                    if (droughtWaterings % 12 == 1) addLog("${v.name} carries buckets from the well to the dry fields (+2 food).")
                    v.remember("carried water to the parched fields")
                } else v.remember("drew water at the well")
                finish(v)
            }
            "craft" -> {
                if (wood >= 6 && stone >= 2) {
                    wood -= 6
                    stone -= 2
                    goods += 2 + toolBonus()
                    if (rnd.nextInt(4) == 0) addLog("${v.name} finishes some handiwork at the workshop.")
                    v.remember("made things at the workshop")
                    v.happiness = min(100f, v.happiness + 2f)
                }
                finish(v)
            }
            "trade" -> {
                if (goods >= 2) {
                    goods -= 2
                    val f = 4 + toolBonus()
                    food += f
                    if (age >= 3 && rnd.nextInt(3) == 0) metal++
                    addLog("${v.name} trades goods at the market (+$f food).")
                    v.remember("traded at the market")
                    v.happiness = min(100f, v.happiness + 3f)
                }
                finish(v)
            }
            "dance" -> {
                for (o in villagers) if (hypot(o.x - v.x, o.y - v.y) < 7f) o.happiness = min(100f, o.happiness + 3f)
                v.happiness = min(100f, v.happiness + 4f)
                if (rnd.nextInt(4) == 0) addLog("${v.name} dances by the fire.")
                finish(v)
            }
            "play" -> {
                v.happiness = min(100f, v.happiness + 3f)
                finish(v)
            }
            "swim" -> {
                v.happiness = min(100f, v.happiness + 9f)
                v.energy = max(0f, v.energy - 3f)
                v.hp = min(100f, v.hp + 4f)
                swims++
                if (swims % 5 == 1) addLog("${v.name} cools off with a swim in the lake.")
                v.remember("went for a swim in the lake")
                finish(v)
            }
            "snowman", "rake_leaves", "fly_kite" -> {
                val kidsNear = villagers.count { it !== v && isChild(it) && hypot(it.x - v.x, it.y - v.y) < 8f }
                for (o in villagers) if (o !== v && o.phase != "sleeping" && hypot(o.x - v.x, o.y - v.y) < 8f) o.happiness = min(100f, o.happiness + (if (isChild(o)) 6f else 2f))
                v.happiness = min(100f, v.happiness + 7f)
                when (v.action) {
                    "snowman" -> {
                        snowmenBuilt++
                        if (seasonProps.count { it.kind == "snowman" } < 5) seasonProps.add(SeasonProp("snowman", v.x + 0.6f, v.y, time))
                        if (snowmenBuilt % 2 == 1) { addLog("${v.name} builds a snowman by the fire."); narrate("${v.name} builds a snowman") }
                        v.remember(if (kidsNear > 0) "built a snowman with the children" else "built a snowman in the snow")
                    }
                    "rake_leaves" -> {
                        leafPilesRaked++
                        if (seasonProps.count { it.kind == "leafpile" } < 4) seasonProps.add(SeasonProp("leafpile", v.x + 0.6f, v.y, time))
                        if (leafPilesRaked % 2 == 1) { addLog("${v.name} rakes the autumn leaves into a pile."); narrate("${v.name} rakes leaves") }
                        v.remember(if (kidsNear > 0) "raked leaves and the children jumped in" else "raked the autumn leaves into a pile")
                    }
                    else -> {
                        kitesFlown++
                        if (kitesFlown % 3 == 1) { addLog("${v.name} flies a kite in the spring breeze."); narrate("${v.name} flies a kite") }
                        v.remember(if (kidsNear > 0) "flew a kite with the children" else "flew a kite on a breezy day")
                    }
                }
                finish(v)
            }
            "puddle_jump", "catch_fireflies", "hide_seek" -> {
                val pals = villagers.filter { it !== v && isChild(it) && it.phase != "sleeping" && hypot(it.x - v.x, it.y - v.y) < 9f }
                v.happiness = min(100f, v.happiness + 8f)
                v.energy = max(0f, v.energy - 2f)
                kidGameKinds.add(v.action)
                when (v.action) {
                    "puddle_jump" -> {
                        puddleJumps++
                        for (o in pals) o.happiness = min(100f, o.happiness + 3f)
                        if (puddleJumps % 4 == 1) { addLog("${v.name} splashes in the puddles."); narrate("${v.name} splashes in the puddles") }
                        v.remember("jumped in the puddles in the rain")
                    }
                    "catch_fireflies" -> {
                        fireflyJars++
                        for (o in pals) o.happiness = min(100f, o.happiness + 4f)
                        if (fireflyJars % 3 == 1) { addLog("${v.name} catches fireflies in a jar at dusk."); narrate("${v.name} catches fireflies") }
                        v.remember(if (pals.isNotEmpty()) "caught fireflies with the other children" else "caught fireflies at dusk")
                    }
                    else -> {
                        hideSeekGames++
                        val seeker = pals.minByOrNull { hypot(it.x - v.x, it.y - v.y) }
                        if (seeker != null) {
                            hideFinds++
                            seeker.say = "Found you, ${v.name}!"; seeker.sayLeft = 4f
                            for (o in pals) { v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1; o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1; o.happiness = min(100f, o.happiness + 3f) }
                            if (hideFinds % 3 == 1) addLog("${seeker.name} finds ${v.name} hiding in the bushes.")
                            v.remember("hid from ${seeker.name} and was found")
                        } else v.remember("hid in the bushes and waited")
                        if (hideSeekGames % 4 == 1) narrate("${v.name} plays hide and seek")
                    }
                }
                finish(v)
            }
            "walk_dog" -> {
                val pet = pets.firstOrNull { it.owner == v.id }
                dogWalks++
                if (pet != null) pet.bond++
                val kidsNear = villagers.count { it !== v && isChild(it) && hypot(it.x - v.x, it.y - v.y) < 8f }
                for (o in villagers) if (o !== v && o.phase != "sleeping" && hypot(o.x - v.x, o.y - v.y) < 7f) o.happiness = min(100f, o.happiness + (if (isChild(o)) 5f else 2f))
                v.happiness = min(100f, v.happiness + 8f)
                if (pet != null && dogWalks % 3 == 1) { addLog("${v.name} plays fetch with ${pet.name}."); narrate("${v.name} plays fetch with ${pet.name}") }
                if (pet != null) v.remember(if (kidsNear > 0) "played fetch with ${pet.name} while the children cheered" else "played fetch with my dog ${pet.name}")
                finish(v)
            }
            "forage_herbs" -> {
                // herbs from the bushes mend the hurt: the forager first, then anyone close by
                val wasHurt = v.hp < 75f
                v.hp = min(100f, v.hp + 25f)
                var helped = 0
                for (o in villagers) {
                    if (o === v || o.hp >= 80f || hypot(o.x - v.x, o.y - v.y) > 7f) continue
                    o.hp = min(100f, o.hp + 18f)
                    o.happiness = min(100f, o.happiness + 3f)
                    o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1
                    o.remember("${v.name} tended my wounds with herbs")
                    helped++
                }
                food += 1
                herbed++
                if (helped > 0) { addLog("${v.name} tends the wounded with herbs."); v.remember("healed $helped villagers with herbs") }
                else if (wasHurt) v.remember("rubbed herbs into my own wounds")
                else v.remember("gathered healing herbs")
                finish(v)
            }
            "carve" -> {
                if (wood >= 8 && goods < 30) {
                    wood -= 3
                    goods += 1
                    carved++
                    var kids = 0
                    for (o in villagers) if (o !== v && isChild(o) && hypot(o.x - v.x, o.y - v.y) < 7f) { o.happiness = min(100f, o.happiness + 6f); kids++ }
                    if (carved % 3 == 1) { addLog("${v.name} carves a wooden toy by the fire."); narrate("${v.name} carves a toy") }
                    v.remember(if (kids > 0) "carved toys for the children" else "carved wooden toys by the fire")
                    v.happiness = min(100f, v.happiness + 3f)
                }
                finish(v)
            }
            "delve" -> finish(v)
            "pick_flowers" -> {
                val f = friendFor(v)
                if (f != null) {
                    posies++
                    v.affinity[f.id] = (v.affinity[f.id] ?: 0) + 2
                    f.affinity[v.id] = (f.affinity[v.id] ?: 0) + 2
                    v.happiness = min(100f, v.happiness + 4f)
                    f.happiness = min(100f, f.happiness + 6f)
                    if (posies % 4 == 1) { addLog("${v.name} brings ${f.name} a posy of wildflowers."); narrate("${v.name} gives ${f.name} flowers") }
                    v.remember("picked wildflowers for ${f.name}")
                    f.remember("was given wildflowers by ${v.name}")
                }
                finish(v)
            }
            "douse" -> {
                val near = blazes.filter { hypot((it.i % GW) + 0.5f - v.x, (it.i / GW) + 0.5f - v.y) < 3.2f }.minByOrNull { it.hp }
                if (near != null) {
                    fireDouses++
                    val power = 12f + (if (count("well") > 0) 6f else 0f) + (skillLevel(v, "build") * 0.5f)
                    near.hp -= power
                    v.hp = max(5f, v.hp - 1.2f)
                    if (near.hp <= 0f) {
                        extinguish(near, v)
                    }
                }
                finish(v)
            }
            "gather_star" -> {
                if (starShards > 0) {
                    starShards--
                    starShardsGot++
                    metal += 1
                    if (starShardsGot % 4 == 0) stone += 2
                    var n = 0
                    for (o in villagers) if (o !== v && o.phase != "sleeping" && hypot(o.x - v.x, o.y - v.y) < 7f) { o.happiness = min(100f, o.happiness + 2f); n++ }
                    v.happiness = min(100f, v.happiness + 8f)
                    v.hp = min(100f, v.hp + 3f)
                    v.remember(if (n > 0) "gathered a shard of the fallen star while others watched" else "gathered a shard of the fallen star")
                    if (starShards == 0) { starUntil = 0.0; addLog("The last shard of the fallen star is gathered (${starShardsGot} shards in all)."); narrate("${v.name} gathers the last star shard") }
                    else if (starShardsGot % 3 == 1) { addLog("${v.name} gathers starmetal from the crater."); narrate("${v.name} gathers starmetal") }
                }
                finish(v)
            }
            "teach" -> {
                // a grown-up shows a child the trade; when the child grows up they may take it up
                val c = villagers.filter { isChild(it) && it.phase != "sleeping" && hypot(it.x - v.x, it.y - v.y) < 7f }.minByOrNull { hypot(it.x - v.x, it.y - v.y) }
                if (c != null && v.job in JOBS) {
                    lessonsGiven++
                    apprentice[c.id] = v.job + "|" + v.name
                    c.bump("lessons"); v.bump("taught")
                    c.happiness = min(100f, c.happiness + 8f); v.happiness = min(100f, v.happiness + 4f)
                    v.affinity[c.id] = (v.affinity[c.id] ?: 0) + 2; c.affinity[v.id] = (c.affinity[v.id] ?: 0) + 2
                    fx.add(Fx("spawn", c.x, c.y, time, 1.0f))
                    if (lessonsGiven % 4 == 1) { addLog("${v.name} teaches ${c.name} the ${v.job.lowercase()}'s craft."); narrate("${v.name} teaches ${c.name}") }
                    v.remember("taught ${c.name} the ${v.job.lowercase()}'s craft")
                    c.remember("learned the ${v.job.lowercase()}'s craft from ${v.name}")
                }
                finish(v)
            }
            "stargaze" -> {
                var n = 0
                for (o in villagers) if (o !== v && o.phase != "sleeping" && hypot(o.x - v.x, o.y - v.y) < 6f) { o.happiness = min(100f, o.happiness + 3f); n++ }
                v.happiness = min(100f, v.happiness + 6f)
                stargazed++
                if (stargazed % 4 == 1) { addLog("${v.name} watches the stars by the fire."); narrate("${v.name} watches the stars") }
                v.remember(if (n > 0) "watched the stars with friends" else "watched the stars alone by the fire")
                finish(v)
            }
            "patrol" -> {
                v.happiness = min(100f, v.happiness + 1f)
                // a patrol sometimes spots trouble early
                if (rnd.nextInt(14) == 0 && predators.isNotEmpty()) alertUntil = max(alertUntil, time + 40.0)
                finish(v)
            }
            "hunt" -> {
                val prey = fauna.onHunt(v)
                val got = when {
                    prey == "deer" -> { if (rnd.nextInt(3) == 0) hides++; 5 + toolBonus() }
                    prey == "rabbit" -> 3 + toolBonus()
                    rnd.nextInt(100) < 65 -> 3 + toolBonus()
                    else -> 1
                }
                food += got
                v.remember(if (got >= 3) "brought home game (+$got food)" else "had a poor hunt")
                v.happiness = min(100f, v.happiness + 1f)
                if (rnd.nextInt(5) == 0) addLog("${v.name} returns from the hunt (+$got food).")
                finish(v)
            }
            "smelt" -> {
                if (ore >= 2 && wood >= 1) {
                    ore -= 2
                    wood -= 1
                    metal++
                    totalSmelted++
                    v.remember("smelted metal at the forge")
                    if (totalSmelted % 5 == 1) addLog("${v.name} smelts fresh ${if (age >= 3) "iron" else "bronze"}.")
                }
                finish(v)
            }
            "huddle" -> {
                v.happiness = min(100f, v.happiness + 2f)
                finish(v)
            }
            "tell_story" -> finish(v)
            else -> finish(v)
        }
    }

    // ---- brains ----

    // ---- relationships: courting, weddings, best friends, feuds ----

    private fun pairKey(a: Villager, b: Villager): String = "${min(a.id, b.id)}-${max(a.id, b.id)}"
    private fun mutual(a: Villager, b: Villager): Int = min(a.affinity[b.id] ?: 0, b.affinity[a.id] ?: 0)
    private fun kin(a: Villager, b: Villager): Boolean {
        if (a.parents.contains(b.name) || b.parents.contains(a.name)) return true
        return a.parents.isNotBlank() && a.parents == b.parents
    }
    private fun grownUp(v: Villager): Boolean = !isChild(v) && ageYears(v) in 4..48

    fun partnerOf(v: Villager): Villager? = if (v.partner < 0) null else villagers.firstOrNull { it.id == v.partner }
    fun bestFriendOf(v: Villager): Villager? =
        villagers.firstOrNull { o -> o !== v && o.id != v.partner && bffs.contains(pairKey(v, o)) }
    fun feudWith(v: Villager): Villager? =
        villagers.firstOrNull { o -> o !== v && feudEnds.containsKey(pairKey(v, o)) }
    fun areFeuding(a: Villager, b: Villager): Boolean = feudEnds.containsKey(pairKey(a, b))

    private fun partnerLine(v: Villager, o: Villager): String {
        val l = ArrayList<String>()
        l.add("I'm glad I have you, ${o.name}.")
        l.add("Stay close tonight, ${o.name}.")
        if (o.hunger > 60f) l.add("You look hungry, ${o.name}. Eat something.")
        if (o.energy < 35f) l.add("You look worn out, ${o.name}. Rest soon.")
        if (v.hp < 60f) l.add("I'm hurt, ${o.name}, but I'm fine with you here.")
        if (babiesBorn > 0) l.add("The little ones keep us busy, ${o.name}.")
        if (isNight()) l.add("Look at the stars with me, ${o.name}.")
        return l[rnd.nextInt(l.size)]
    }

    /** When a villager dies, their partner mourns and old friendships and feuds are dropped. */
    private fun loseBonds(dead: Villager) {
        val w = villagers.firstOrNull { it.partner == dead.id }
        if (w != null) {
            w.partner = -1
            w.happiness = max(0f, w.happiness - 30f)
            w.remember("lost my beloved ${dead.name}")
            addLog("${w.name} mourns ${dead.name}, their beloved.")
            mark("${w.name} was widowed when ${dead.name} died.")
        }
        val dk = bffs.filter { it.split("-").contains(dead.id.toString()) }
        bffs.removeAll(dk.toSet())
        feudEnds.keys.filter { it.split("-").contains(dead.id.toString()) }.forEach { feudEnds.remove(it) }
        courting.keys.filter { it.split("-").contains(dead.id.toString()) }.forEach { courting.remove(it) }
    }

    private fun relationTick() {
        if (time < nextRelAt || villagers.size < 2) return
        nextRelAt = time + 6.0
        val adults = villagers.filter { grownUp(it) }
        // feuds end after a while, with a handshake
        for (k in feudEnds.entries.toList()) {
            if (time < k.value) continue
            feudEnds.remove(k.key)
            val ids = k.key.split("-").mapNotNull { it.toIntOrNull() }
            val a = villagers.firstOrNull { it.id == ids.getOrNull(0) }
            val b = villagers.firstOrNull { it.id == ids.getOrNull(1) }
            if (a != null && b != null) {
                a.affinity[b.id] = 0; b.affinity[a.id] = 0
                a.happiness = min(100f, a.happiness + 6f); b.happiness = min(100f, b.happiness + 6f)
                feudsEnded++
                addLog("${a.name} and ${b.name} made peace.")
                a.remember("made peace with ${b.name}"); b.remember("made peace with ${a.name}")
            }
        }
        // new feuds
        for (i in adults.indices) for (j in i + 1 until adults.size) {
            val a = adults[i]; val b = adults[j]
            if (mutual(a, b) <= -2 && !areFeuding(a, b) && feudEnds.size < 3) {
                feudEnds[pairKey(a, b)] = time + DAY_LEN * 1.5
                feudsStarted++
                a.happiness = max(0f, a.happiness - 8f); b.happiness = max(0f, b.happiness - 8f)
                a.say = "Leave me be, ${b.name}."; a.sayLeft = 6f
                addLog("${a.name} and ${b.name} have fallen out.")
                a.remember("fell out with ${b.name}"); b.remember("fell out with ${a.name}")
                mark("${a.name} and ${b.name} fell out.")
            }
        }
        // best friends
        for (i in adults.indices) for (j in i + 1 until adults.size) {
            val a = adults[i]; val b = adults[j]
            val k = pairKey(a, b)
            if (k in bffs || a.partner == b.id || mutual(a, b) < 9) continue
            if (bestFriendOf(a) != null || bestFriendOf(b) != null) continue
            bffs.add(k)
            a.happiness = min(100f, a.happiness + 10f); b.happiness = min(100f, b.happiness + 10f)
            a.say = "We're the best of friends, ${b.name}!"; a.sayLeft = 6f
            addLog("${a.name} and ${b.name} are now best friends.")
            a.remember("became best friends with ${b.name}"); b.remember("became best friends with ${a.name}")
            mark("${a.name} and ${b.name} became best friends.")
        }
        // courtship and weddings
        val single = adults.filter { it.partner < 0 && ageYears(it) >= 5 }
        for (i in single.indices) for (j in i + 1 until single.size) {
            val a = single[i]; val b = single[j]
            val k = pairKey(a, b)
            if (kin(a, b) || areFeuding(a, b) || mutual(a, b) < 5 || courting.containsKey(k)) continue
            if (courting.keys.any { c -> c.split("-").contains(a.id.toString()) || c.split("-").contains(b.id.toString()) }) continue
            courting[k] = time
            addLog("${a.name} and ${b.name} have grown very close.")
            a.remember("is falling for ${b.name}"); b.remember("is falling for ${a.name}")
        }
        for (e in courting.entries.toList()) {
            val ids = e.key.split("-").mapNotNull { it.toIntOrNull() }
            val a = villagers.firstOrNull { it.id == ids.getOrNull(0) }
            val b = villagers.firstOrNull { it.id == ids.getOrNull(1) }
            if (a == null || b == null || a.partner >= 0 || b.partner >= 0 || mutual(a, b) < 3) { courting.remove(e.key); continue }
            if (time - e.value < DAY_LEN * 0.3 || alert() || isNight() || raining() || food < 6) continue
            courting.remove(e.key)
            a.partner = b.id; b.partner = a.id
            weds++
            weddingAt = time
            val fire = buildings.firstOrNull { it.type == "hall" } ?: buildings.firstOrNull { it.type == "campfire" }
            weddingX = (fire?.x ?: a.x.toInt()) + 0.5f
            weddingY = (fire?.y ?: a.y.toInt()) + 1.6f
            weddingNames = "${a.name} & ${b.name}"
            for (o in villagers) o.happiness = min(100f, o.happiness + (if (o === a || o === b) 30f else 8f))
            festivalUntil = max(festivalUntil, time + 40.0)
            food = max(0, food - 3)
            a.say = "I do!"; a.sayLeft = 7f
            b.say = "And so do I!"; b.sayLeft = 7f
            event("${a.name} and ${b.name} are married at the ${if (fire?.type == "hall") "hall" else "fire"}! The village celebrates.")
            mark("${a.name} and ${b.name} were wed.")
            a.remember("married ${b.name}"); b.remember("married ${a.name}")
            break
        }
    }

    // ---- ages and generations ----

    /** A short history of the big moments, kept in the save file. */
    fun mark(text: String) {
        chronicle.add("Day ${day()}: $text")
        while (chronicle.size > 160) chronicle.removeAt(0)
    }

    /** Called by the screen when a frame of the sim throws, so one bug never takes the app down. */
    fun guard(e: Exception) {
        val now = System.currentTimeMillis()
        if (now - lastErrorLog > 60_000L) {
            lastErrorLog = now
            onError("Village hiccup: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun isChild(v: Villager) = v.job == "Child"
    fun ageYears(v: Villager): Int = max(0.0, (time - v.born) / YEAR).toInt()
    fun ageName(): String = AGE_NAMES[age.coerceIn(0, AGE_NAMES.size - 1)]
    fun nextAgeName(): String? = AGE_NAMES.getOrNull(age + 1)
    fun popCap(): Int = when (age) { 0 -> 10; 1 -> 14; 2 -> 20; 3 -> 28; else -> 38 }
    fun adults(): Int = villagers.count { !isChild(it) }
    fun toolBonus(): Int = when {
        age >= 3 && totalSmelted >= 30 -> 2
        age >= 2 && totalSmelted >= 8 -> 1
        else -> 0
    }

    private class Need(val label: String, val have: Float, val need: Float) {
        val done: Boolean get() = have >= need
    }

    /** Age of Empires style: people, buildings, time and a payment from the stores. Null in the last age. */
    private var momentumLogged = -1

    /** Thriving villages do not wait for the calendar: once every other requirement is met the days gate drops to 70%. */
    private fun withMomentum(parts: List<Need>?): List<Need>? {
        if (parts == null || age < 1) return parts
        val d = parts.firstOrNull { it.label == "days" } ?: return parts
        if (!parts.all { it.done || it.label == "days" }) return parts
        val eased = Need("days", d.have, kotlin.math.ceil(d.need * 0.7f))
        if (momentumLogged != age && eased.done != d.done) { momentumLogged = age; addLog("The village is thriving: stores are full and every hall is built, so it will not wait for the calendar.") }
        return parts.map { if (it.label == "days") eased else it }
    }

    private fun needs(): List<Need>? = withMomentum(needsBase())

    private fun needsBase(): List<Need>? {
        val ad = adults()
        fun n(l: String, h: Int, d: Int) = Need(l, h.toFloat(), d.toFloat())
        return when (age) {
            0 -> listOf(n("days", day(), 10), n("adults", ad, 6), n("homes", count("hut"), 3), n("food", food, 80), n("wood", wood, 60))
            1 -> listOf(
                n("days", day(), 30), n("adults", ad, 9), n("homes", count("hut"), 6), n("farms", count("farm"), 2),
                n("scouting trips", explored, 6), n("food", food, 200), n("wood", wood, 160), n("stone", stone, 80),
            )
            2 -> listOf(
                n("days", day(), 75), n("adults", ad, 12), n("homes", count("hut"), 8), n("forges", count("smelter"), 2),
                n("smelted metal", totalSmelted, 20), n("food", food, 400), n("wood", wood, 320), n("stone", stone, 200), n("metal", metal, 15),
            )
            3 -> listOf(
                n("days", day(), 150), n("adults", ad, 16), n("homes", count("hut"), 12), n("farms", count("farm"), 4),
                n("towers", count("tower"), 2), n("smelted metal", totalSmelted, 60), n("food", food, 800), n("wood", wood, 600),
                n("stone", stone, 400), n("metal", metal, 50), n("hides", hides, 20),
            )
            else -> null
        }
    }

    private fun payForAge() {
        when (age) {
            0 -> { food -= 80; wood -= 60 }
            1 -> { food -= 200; wood -= 160; stone -= 80 }
            2 -> { food -= 400; wood -= 320; stone -= 200; metal -= 15 }
            3 -> { food -= 800; wood -= 600; stone -= 400; metal -= 50; hides -= 20 }
        }
    }

    /** How close the village is to the next age, 0..100. */
    fun progress(): Int {
        val parts = needs() ?: return 100
        return (parts.map { min(1f, it.have / it.need) }.average() * 100).toInt().coerceIn(0, 99)
    }

    /** What is still missing for the next age, in plain words. */
    fun ageNeedText(): String {
        val parts = needs() ?: return "The city stands complete."
        val open = parts.filter { !it.done }
        if (open.isEmpty()) return "Ready to advance."
        return open.sortedBy { it.have / it.need }.take(3).joinToString(", ") { "${it.label} ${it.have.toInt()}/${it.need.toInt()}" }
    }

    private fun jobOk(j: String) = age >= (JOB_MIN_AGE[j] ?: 0) && age <= (JOB_MAX_AGE[j] ?: 99)

    private fun chooseJob(): String {
        val ok = JOBS.filter { jobOk(it) }
        val counts = ok.associateWith { j -> villagers.count { it.job == j } }
        val newest = ok.filter { age > 0 && (JOB_MIN_AGE[it] ?: 0) == age }
        if (newest.isNotEmpty() && rnd.nextInt(100) < 65) {
            val low = newest.minOf { counts[it] ?: 0 }
            if (low < 3) {
                val c = newest.filter { counts[it] == low }
                return c[rnd.nextInt(c.size)]
            }
        }
        val low = counts.values.minOrNull() ?: 0
        val c = ok.filter { counts[it] == low }
        return c[rnd.nextInt(c.size)]
    }

    private fun setJob(v: Villager, j: String) {
        addLog("${v.name} takes up a new trade: ${j.lowercase()}.")
        v.remember("became a ${j.lowercase()}")
        v.job = j
    }

    private fun retrain() {
        val grown = villagers.filter { !isChild(it) }
        fun n(j: String) = villagers.count { it.job == j }
        fun swap(from: List<String>, to: String, want: Int) {
            for (v in grown) {
                if (n(to) >= want) break
                if (v.job in from) setJob(v, to)
            }
        }
        when (age) {
            1 -> swap(listOf("Hunter", "Forager", "Woodcutter"), "Farmer", 2)
            2 -> swap(listOf("Miner", "Builder", "Woodcutter", "Forager"), "Bronzesmith", 2)
            3 -> {
                for (v in grown.filter { it.job == "Bronzesmith" }) setJob(v, "Blacksmith")
                swap(listOf("Miner", "Builder", "Woodcutter"), "Blacksmith", 2)
            }
        }
    }

    private fun advanceAge() {
        if (age >= AGE_NAMES.size - 1) return
        payForAge()
        age++
        if (age >= 2 && tiles.none { it == T.ORE }) ensure(T.ORE, 5, 8)
        event(
            when (age) {
                1 -> "Timber huts replace the tents and the first fields are sown. The Hut Age begins!"
                2 -> "Stone houses rise around a new village hall, and the forges are lit. The Village Age begins!"
                3 -> "Streets fill with townhouses, iron rings on the anvils. The Town Age begins!"
                else -> "Towers, walls and a grand keep stand over the settlement. The City Age begins!"
            }
        )
        mark(AGE_NAMES[age.coerceIn(0, AGE_NAMES.size - 1)] + " begins.")
        ageBannerName = AGE_NAMES[age.coerceIn(0, AGE_NAMES.size - 1)]
        ageBannerAt = time
        retrain()
        // every house is rebuilt in the style of the new age
        for (b in buildings) {
            if ((b.type == "hut" || b.type == "hall") && b.era < age) {
                b.era = age
                fx.add(Fx("spawn", b.x + 0.5f, b.y + 0.5f, time, 1.4f))
            }
        }
        if (age >= 2) placeHall()
        for (v in villagers) v.happiness = min(100f, v.happiness + 15f)
        festivalUntil = time + 40.0
    }

    /** The village hall: raised by everyone together when the Village Age begins, and grander in later ages. */
    private fun placeHall() {
        val h = buildings.firstOrNull { it.type == "hall" }
        if (h != null) {
            h.era = age
            return
        }
        val site = findSite()
        if (site < 0) return
        addBuilding("hall", site % GW, site / GW)
    }

    private fun capFood(): Int = intArrayOf(150, 400, 900, 1800, 3000)[age.coerceIn(0, 4)] + count("granary") * 300 + (if (hasTech("vault")) 400 else 0) + cathTier() * 80
    private fun capWood(): Int = intArrayOf(150, 400, 800, 1500, 2500)[age.coerceIn(0, 4)] + (if (hasTech("vault")) 300 else 0) + cathTier() * 80
    private fun capStone(): Int = intArrayOf(100, 250, 500, 1000, 2000)[age.coerceIn(0, 4)] + (if (hasTech("vault")) 250 else 0) + cathTier() * 80

    /** True when this gathering action would only pile goods on a full store. */
    private fun storeFull(action: String): Boolean = if (!smartSurplus) false else when (action) {
        "gather_wood" -> wood >= capWood() * 0.9
        "gather_stone" -> stone >= capStone() * 0.9
        "gather_food" -> food >= capFood() * 0.9
        else -> false
    }

    /** When the store is full, spend the effort on something useful or kind instead. */
    private fun surplusWork(v: Villager): String {
        val shop = count("workshop") > 0 && wood >= 6 && stone >= 2 && goods < 28
        val market = count("market") > 0 && goods >= 2
        return pick(
            "carve" to (if (wood >= 8 && goods < 30 && count("campfire") > 0) 4 else 0),
            "snowman" to (if (seasonFunOk("snowman")) 5 else 0), "rake_leaves" to (if (seasonFunOk("rake_leaves")) 5 else 0), "fly_kite" to (if (seasonFunOk("fly_kite")) 1 else 0),
            "pick_flowers" to (if (nearestTile(v, T.BUSH) >= 0 && v.recent.count { it == "pick_flowers" } == 0) 1 else 0),
            "walk_dog" to (if (hasDog(v) && dogOk()) 5 else 0),
            "craft" to (if (shop) 4 else 0), "trade" to (if (market) 3 else 0),
            "tavern" to (if (count("tavern") > 0 && food >= 3) 4 else 0),
            "recover" to (if (count("infirmary") > 0 && v.hp < 90f) 5 else 0),
            "honor" to (if (count("totem") > 0 && !isChild(v)) 2 else 0),
            "mourn" to (if (graves.isNotEmpty() && !isChild(v) && !raining()) 2 else 0),
            "vigil" to (if (vigilOk(v)) 3 else 0),
            "tend_flock" to (if (count("pasture") > 0 && !isChild(v)) 4 else 0),
            "tend_bees" to (if (beesOk(v)) 5 else 0),
            "pick_fruit" to (if (fruitOk(v)) 5 else 0),
            "soak" to (if (soakOk(v)) (if (v.hp < 75f) 9 else if (v.energy < 40f) 6 else if (seasonIdx() == 3) 5 else if (v.happiness >= 90f) 1 else 2) else 0),
            "show" to (if (showOk(v)) (if (v.happiness < 70f) 6 else 3) else 0),
            "train_critters" to (if (yardOk(v)) (if (critters.gifted(v)) 7 else 3) else 0),
            "fire_pots" to (if (kilnOk(v)) (if (goods < 6) 6 else 3) else 0),
            "weave" to (if (loomOk(v)) (if (seasonIdx() >= 2 && loomCloaks < villagers.count { !isChild(it) }) 7 else 3) else 0),
            "ring_bell" to (if (bellOk(v)) (if (alert()) 8 else 4) else 0),
            "shoot" to (if (shootOk(v)) (if (isFighter(v) || v.job == "Hunter") 6 else 3) else 0),
            "sail" to (if (count("dock") > 0 && !isChild(v) && sailOk()) 5 else 0),
            "raise_cathedral" to (if (cathOk(v)) 6 else 0),
            "grind" to (if (millOk(v)) (if (food < 40 + adults() * 5) 7 else 3) else 0),
            "quarry" to (if (quarryOk(v)) (if (stone < 60 + adults() * 8) 7 else 2) else 0),
            "assault_camp" to (if (campAssaultOk(v)) (if (isFighter(v)) 8 else 3) else 0),
            "tend_critters" to (if (count("ranch") > 0 && ranchResidents() > 0 && !isChild(v)) 4 else 0),
            "study" to studyW(v, 3), "drill" to (if (count("barracks") > 0 && isFighter(v)) 3 else 0),
            "dance" to (if (count("campfire") > 0) danceNeed(v, 2) else 0), "tell_story" to 2, "talk" to 2, "patrol" to (if (isFighter(v)) 2 else 0),
            "wander" to 1,
        )
    }

    private fun capStores() {
        val a = age.coerceIn(0, 4)
        food = min(food, capFood())
        wood = min(wood, capWood())
        stone = min(stone, capStone())
        ore = min(ore, 80)
        metal = min(metal, 200)
        hides = min(hides, 100)
        goods = min(goods, 30)
    }

    private var nextPlanAt = 0.0

    /** The settlement plans ahead: when the next age wants more homes, fields or forges, someone free gets on with it. */
    private fun planTick() {
        if (time < nextPlanAt || villagers.size < 3) return
        nextPlanAt = time + 40.0
        // old saves: make sure a hall stands from the Village Age on and every house matches the age
        if (age >= 2 && count("hall") == 0) placeHall()
        for (b in buildings) if ((b.type == "hut" || b.type == "hall") && b.era < age) b.era = age
        val np = needs() ?: return
        fun want(label: String) = np.any { it.label == label && !it.done }
        val free = villagers.filter { !isChild(it) && it.phase == "idle" && it.hunger < 60f }
        val worker = free.firstOrNull { it.job == "Builder" } ?: free.firstOrNull() ?: return
        when {
            want("homes") && wood >= 6 && (stone >= 2 || hides >= 1) -> startAction(worker, "build_hut", "")
            want("farms") && wood >= 4 -> startAction(worker, "build_farm", "")
            want("forges") && wood >= 4 && stone >= 6 -> startAction(worker, "build_smelter", "")
            else -> {
                val t = listOf("totem", "pasture", "apiary", "orchard", "ranch", "well", "granary", "workshop", "market", "library", "tavern", "infirmary", "barracks", "lamp").firstOrNull { canBuild(it) }
                if (t != null) startAction(worker, "build_$t", "")
            }
        }
    }

    private var ageClock = 0f

    private fun ageTick(dt: Float) {
        ageClock += dt
        if (ageClock < 2f) return
        ageClock = 0f
        val parts = needs() ?: return
        if (parts.all { it.done }) advanceAge()
    }

    private fun growUp(v: Villager) {
        val ap = apprentice.remove(v.id)?.split("|")
        val wanted = ap?.getOrNull(0) ?: ""
        val j = if (wanted in JOBS && jobOk(wanted) && rnd.nextInt(100) < 80) wanted else chooseJob()
        if (j == wanted && ap != null) {
            apprenticesMade++
            v.bump("trained")
            val m = villagers.firstOrNull { it.name == ap.getOrNull(1) }
            if (m != null) { m.happiness = min(100f, m.happiness + 12f); m.bump("apprentices"); m.remember("saw ${v.name} grow up to follow the ${j.lowercase()}'s craft") }
            addLog("${v.name} follows ${ap.getOrNull(1) ?: "an elder"} into the ${j.lowercase()}'s craft.")
        }
        v.job = j
        v.happiness = min(100f, v.happiness + 15f)
        v.remember("grew up and became a ${j.lowercase()}")
        event("${v.name} has grown up and becomes a ${j.lowercase()}.")
        mark("${v.name} grew up and became a ${j.lowercase()}.")
        comingOfAge(v, j, ap != null && j == wanted)
    }

    fun crowned(v: Villager): Boolean = (crowns[v.id] ?: -1.0) > time

    private fun giftSkill(job: String): String? = when (job) {
        "Woodcutter" -> "wood"
        "Miner" -> "stone"
        "Forager", "Farmer" -> "farm"
        "Builder" -> "build"
        "Scout", "Hunter" -> "combat"
        "Storyteller" -> "lore"
        "Bronzesmith", "Blacksmith" -> "craft"
        else -> null
    }

    /** A child becomes an adult: neighbours gather, parents beam, a flower crown for a day and a first lesson in the trade. */
    private fun comingOfAge(v: Villager, job: String, taught: Boolean) {
        comingAges++
        crowns[v.id] = time + DAY_LEN
        fx.add(Fx("coming", v.x, v.y, time, 7f))
        val guests = villagers.filter { it !== v && !isChild(it) && it.phase != "sleeping" && Math.hypot((it.x - v.x).toDouble(), (it.y - v.y).toDouble()) < 12.0 }
        for (g in guests) {
            g.happiness = min(100f, g.happiness + 6f)
            g.affinity[v.id] = (g.affinity[v.id] ?: 0) + 1
            v.affinity[g.id] = (v.affinity[g.id] ?: 0) + 1
        }
        comingGuests += guests.size
        if (guests.isNotEmpty()) guests[rnd.nextInt(guests.size)].remember("cheered as ${v.name} came of age")
        for (pr in villagers) {
            if (pr !== v && v.parents.contains(pr.name)) {
                pr.happiness = min(100f, pr.happiness + 10f)
                pr.remember("watched ${v.name} come of age")
                comingParents++
            }
        }
        val sk = giftSkill(job)
        if (sk != null) {
            val xp = if (taught) 45 else 20
            v.bump("xp_$sk", xp)
            comingGifts++
            v.addDiary("Came of age: first lessons in ${SKILL_NAMES[sk] ?: sk} (level ${skillLevel(v, sk)}).")
        }
        addLog("The village cheers as ${v.name} comes of age" + (if (guests.isNotEmpty()) " (${guests.size} neighbours gathered)." else "."))
    }

    private fun techNote(): String = when (age) {
        0 -> "Age: Camp. You live in tents and survive by hunting, fishing and foraging. Nobody knows farming yet."
        1 -> "Age: Huts. You build timber huts and farms. The settlement grows crops and stores food."
        2 -> "Age: Village. Stone houses and a village hall stand. Ore has been found; forges smelt bronze and metal tools make work faster."
        3 -> "Age: Town. Streets of townhouses, watchtowers and iron tools. The settlement is a real town now."
        else -> "Age: City. A walled city with a grand keep, towers and many trades."
    }

    // ---- brains ----

    private fun vigilOk(v: Villager) = count("shrine") > 0 && !isChild(v) && !isNight() && !alert()

    /** The shrine answers: the next legend of the chain that is not yet bound steps out beside it. */
    private fun callLegend(sh: Building, v: Villager) {
        val next = (57..62).firstOrNull { id -> critters.critters.none { it.sp == id } } ?: return
        vigilPoints = 0
        legendCalls++
        shrineCallSp = next
        shrineCallUntil = time + 330.0
        shrineGlowUntil = time + 40.0
        critters.spawnLegend(next, sh.x + 0.5f, sh.y + 2.2f)
        val nm = Dex.byId(next).name
        event("The legend shrine blazes with light! After the long vigils, $nm answers the village's call.")
        mark("The shrine answered: $nm stepped out (call ${legendCalls}).")
        for (o in villagers) o.happiness = min(100f, o.happiness + 8f)
        festivalUntil = max(festivalUntil, time + 20.0)
        fx.add(Fx("spawn", sh.x + 0.5f, sh.y + 0.5f, time, 2.0f))
        v.remember("saw $nm answer the shrine")
    }

    private val FRUIT_EVERY = 40.0
    /** Baskets of fruit ripe on the trees right now: none in winter or spring blossom, a few in summer, plenty in autumn. */
    fun fruitReady(): Int {
        val o = count("orchard")
        if (o == 0) return 0
        val si = seasonIdx()
        if (si == 0 || si == 3) return 0
        val ripe = ((time - fruitMark) / FRUIT_EVERY).toInt()
        return ripe.coerceIn(0, (if (si == 2) 5 else 2) * o)
    }
    /** A bell may ring at dawn, dusk or when danger is about, never twice within a short while. */
    private fun bellOk(v: Villager) = count("belfry") > 0 && !isChild(v) && v.hp > 30f && time - lastBellAt > 70.0 &&
        (alert() || (!isNight() && !raining() && (partOfDay() == "morning" || partOfDay() == "evening")))
    /** Shows are given in daylight or at dusk in fair weather, one at a time. */
    private fun showOk(v: Villager) = count("stage") > 0 && !isChild(v) && !alert() && !raining() && v.hp > 30f && time - lastShowAt > 45.0 &&
        (!isNight() || partOfDay() == "evening")
    /** Pots are fired by day while there is wood to spare, one batch at a time. */
    /** Cloth is woven by day when hides are in store and the loom has stood idle a while. */
    private fun loomOk(v: Villager) = count("loom") > 0 && !isChild(v) && !isNight() && !alert() && hides >= 2 && v.hp > 30f && time - lastLoomAt > 25.0
    /** Whether this villager wears one of the woven cloaks (cloaks go to adults in order of arrival). */
    fun cloaked(v: Villager): Boolean = loomCloaks > 0 && !isChild(v) && villagers.count { !isChild(it) && it.id < v.id } < loomCloaks
    /** The yard is for grown villagers with a critter to drill (their own, or one from the ranch), by day in fair weather. */
    private fun yardOk(v: Villager) = count("yard") > 0 && !isChild(v) && !isNight() && !alert() && !raining() && v.hp > 30f && v.hunger < 85f &&
        (critters.critters.any { it.owner == v.id } || (critters.critters.any { it.owner < 0 } && !critters.gifted(v))) && time - lastYardAt > 6.0
    private fun kilnOk(v: Villager) = count("kiln") > 0 && !isChild(v) && !isNight() && !alert() && wood >= 6 && v.hp > 30f && time - lastKilnAt > 30.0
    private fun soakOk(v: Villager) = count("bath") > 0 && !isChild(v) && !alert() && v.hunger < 85f
    private fun shootOk(v: Villager) = count("range") > 0 && !isChild(v) && !isNight() && !alert() && !raining() && v.hp > 30f
    private fun fruitOk(v: Villager) = count("orchard") > 0 && !isChild(v) && !isNight() && !alert() && !raining() && fruitReady() >= 1
    private val HONEY_EVERY = 55.0
    /** Jars of honey ripe in the hives right now (0 to 4 per apiary; the hives refill while nobody takes it). */
    fun honeyReady(): Int {
        val a = count("apiary")
        if (a == 0) return 0
        val ripe = ((time - honeyMark) / HONEY_EVERY).toInt()
        return ripe.coerceIn(0, 4 * a)
    }
    private fun beesOk(v: Villager) = count("apiary") > 0 && !isChild(v) && !isNight() && !alert() && !raining() && seasonIdx() != 3 && honeyReady() >= 1
    private fun millOk(v: Villager) = count("mill") > 0 && count("farm") > 0 && !isChild(v) && !isNight() && !alert()
    private fun quarryOk(v: Villager) = count("quarry") > 0 && !isChild(v) && !isNight() && !alert() && stone < capStone() * 0.85
    private fun sailOk() = !isNight() && !raining() && !alert() && !foggy() && count("dock") > 0
    private fun alert() = time < alertUntil || predators.isNotEmpty()
    fun raining() = time < rainUntil || time < stormUntil || outsideRain
    fun storm() = time < stormUntil
    private fun seasonFunOk(a: String): Boolean {
        if (raining() || foggy() || alert() || isNight() || storm()) return false
        return when (a) {
            "snowman" -> seasonName() == "winter" && seasonProps.count { it.kind == "snowman" } < 5
            "rake_leaves" -> seasonName() == "autumn" && seasonProps.count { it.kind == "leafpile" } < 4
            "fly_kite" -> seasonName() == "spring" && !snowing()
            else -> false
        }
    }

    /** Games only children play: splashing in rain puddles, catching fireflies at dusk, hide and seek in the bushes. */
    private fun kidGameOk(a: String): Boolean {
        if (alert() || storm() || foggy()) return false
        return when (a) {
            "puddle_jump" -> raining() && !snowing() && !isNight()
            "catch_fireflies" -> partOfDay() == "evening" && seasonIdx() != 3 && !raining() && !snowing()
            "hide_seek" -> !raining() && !snowing() && !isNight() && villagers.count { isChild(it) } >= 2
            else -> false
        }
    }

    private fun hasDog(v: Villager) = pets.any { it.owner == v.id }
    private fun dogOk() = !raining() && !storm() && !foggy() && !alert() && !isNight()

    private fun dreamMet(v: Villager): Boolean {
        fun a(k: String) = v.stat("a_$k")
        val builds = v.st.entries.filter { it.key.startsWith("a_build_") }.sumOf { it.value }
        val defended = count("tower") + count("ballista") + count("barracks") > 0
        return when (v.personaIdx % PERSONAS.size) {
            0 -> food >= 100 && a("gather_food") + a("haul") + a("fish") >= 15
            1 -> a("explore") >= 4
            2 -> a("talk") >= 20
            3 -> builds >= 4
            4 -> a("stargaze") + a("pick_flowers") + a("tell_story") >= 10
            5 -> a("gather_wood") + a("gather_stone") + a("gather_food") + a("mine_ore") >= 50
            6 -> defended && time - lastThreatAt > DAY_LEN * 0.5
            7 -> age >= 2 && adults() >= 12
            8 -> a("forage_herbs") + a("tend_critters") + a("pick_flowers") + a("recover") >= 8
            else -> time > DAY_LEN * 2 && time - lastThreatAt >= DAY_LEN
        }
    }

    /** A villager whose personal dream (from their personality) comes true is celebrated. */
    private fun dreamTick(dt: Float) {
        if (alert()) lastThreatAt = time
        dreamClock += dt
        if (dreamClock < 4f) return
        dreamClock = 0f
        for (v in villagers) {
            if (isChild(v) || v.stat("dream") > 0 || !dreamMet(v)) continue
            v.st["dream"] = 1
            dreamsDone++
            v.happiness = min(100f, v.happiness + 30f)
            for (o in villagers) if (o !== v && hypot(o.x - v.x, o.y - v.y) < 9f) o.happiness = min(100f, o.happiness + 6f)
            deedBannerAt = time; deedBannerTag = "DREAM COME TRUE"; deedBannerName = v.name
            deedBannerSub = "Dreamed of " + v.persona.dream
            addLog("${v.name}'s dream comes true: ${v.persona.dream}.")
            mark("${v.name}'s dream came true.")
            v.remember("my dream came true: ${v.persona.dream}")
            fx.add(Fx("spawn", v.x, v.y, time, 1.4f))
            break
        }
    }

    /** What a villager likes to do in spare moments, by personality. Null means no preference right now. */
    private fun personaPastime(v: Villager, part: String, chat: Int, hasShore: Boolean): Pair<String, String>? {
        val ad = adults()
        val a: String = when (v.personaIdx % PERSONAS.size) {
            0 -> if (food < 12 + ad * 4) "gather_food" else if (count("granary") > 0 && food >= 4) "haul" else if (hasShore) "fish" else return null
            1 -> "explore"
            2 -> if (chat == 1) "talk" else if (count("tavern") > 0 && part == "evening" && food >= 3) "tavern" else if (count("campfire") > 0) "dance" else return null
            3 -> if (wood < 30 + ad * 4) "gather_wood" else if (wood >= 10) "carve" else return null
            4 -> if (canStargaze() && count("campfire") > 0) "stargaze" else if (rnd.nextBoolean()) "pick_flowers" else "tell_story"
            5 -> return null
            6 -> if (count("tower") + count("ballista") > 0 && rnd.nextBoolean()) "patrol" else if (wood < 40 + ad * 4) "gather_wood" else "gather_stone"
            7 -> if (count("barracks") > 0) "drill" else "patrol"
            8 -> if (count("ranch") > 0 && ranchResidents() > 0) "tend_critters" else if (rnd.nextBoolean()) "pick_flowers" else "forage_herbs"
            else -> if (hasShore) "fish" else "wander"
        }
        pastimeCount.merge(a, 1, Int::plus)
        return a to (if (a == "talk") (friendFor(v)?.name ?: "") else "")
    }

    /** A stray wanders in now and then and a villager without a dog takes it in. */
    private fun petTick() {
        if (time < nextPetAt || villagers.size < 5) return
        val want = min(4, villagers.size / 3)
        nextPetAt = time + DAY_LEN * 1.5
        if (pets.size >= want) return
        val free = villagers.filter { v -> pets.none { it.owner == v.id } }
        if (free.isEmpty()) return
        val kids = free.filter { isChild(it) }
        val o = if (kids.isNotEmpty() && rnd.nextInt(2) == 0) kids[rnd.nextInt(kids.size)] else free[rnd.nextInt(free.size)]
        val used = pets.map { it.name }.toSet()
        val name = DOG_NAMES.filter { it !in used }.let { if (it.isEmpty()) DOG_NAMES else it }.let { it[rnd.nextInt(it.size)] }
        pets.add(Pet(name, o.id, 0, true))
        dogsAdopted++
        o.happiness = min(100f, o.happiness + 12f)
        o.remember("took in a stray dog and named it $name")
        addLog("A stray dog trots into camp. ${o.name} names it $name.")
        mark("${o.name} adopted a stray dog, $name.")
        narrate("A stray dog joins ${o.name}")
    }

    /** Called by the animals when a dog's owner is gone: it follows the nearest villager instead. */
    internal fun dogRehomed(p: Pet, n: Villager) {
        p.owner = n.id
        addLog("${p.name} now follows ${n.name}.")
        n.remember("${p.name} the dog chose to follow me")
    }

    /** A free open tile near the fire for a snowman, leaf pile or kite. */
    private fun seasonSpot(v: Villager, a: String): Int {
        val hub = buildings.firstOrNull { it.type == "campfire" } ?: buildings.firstOrNull { it.type == "hall" }
        val hx = hub?.x ?: v.x.toInt()
        val hy = hub?.y ?: v.y.toInt()
        val far = if (a == "fly_kite") 9 else 6
        for (k in 0 until 14) {
            val x = hx + rnd.nextInt(far * 2 + 1) - far
            val y = hy + rnd.nextInt(far + 4) - far / 2 - 1
            if (!walkableTile(x, y) || tiles[y * GW + x] == T.FOREST) continue
            if (buildings.any { abs(it.x - x) <= 1 && abs(it.y - y) <= 1 }) continue
            if (seasonProps.any { abs(it.x - x) < 2.5f && abs(it.y - y) < 2f }) continue
            return y * GW + x
        }
        return -1
    }

    private fun canSwim() = seasonName() == "summer" && !raining() && !snowing() && !foggy() && !isNight() && !alert()
    private fun canStargaze() = !raining() && !foggy() && !snowing() && !alert() && (partOfDay() == "evening" || (isNight() && seasonName() != "winter"))
    fun foggy() = time < fogUntil
    fun snowing() = time < snowUntil || outsideSnow
    fun seasonIdx(): Int = ((time / DAY_LEN).toInt() / SEASON_DAYS) % 4
    /** How far through the current season we are, 0..1. */
    fun seasonFrac(): Float = (((time / DAY_LEN) % SEASON_DAYS) / SEASON_DAYS).toFloat().coerceIn(0f, 1f)
    /** Snow and ice on the land, 0..1: builds up in winter, melts through the first half of spring. */
    fun snowCover(): Float {
        if (time < DAY_LEN * SEASON_DAYS) return 0f
        val f = seasonFrac()
        val base = when (seasonIdx()) {
            3 -> (0.2f + f * 2.6f).coerceAtMost(1f)
            0 -> (1f - f * 2.2f).coerceAtLeast(0f)
            else -> 0f
        }
        return if (snowing() && seasonIdx() != 1) (base + 0.25f).coerceAtMost(1f) else base
    }
    private fun festival() = time < festivalUntil

    /** Reading only pays while there are discoveries left and the reading room has seats (4 adults at once). */
    private fun studyW(v: Villager, base: Int): Int {
        if (count("library") == 0) return 0
        if (isChild(v)) return base
        if (nextTech() == null) return 0
        if (v.action != "study" && villagers.count { it.action == "study" && !isChild(it) } >= 4) return 0
        return base
    }

    /** Dancing is a mood tonic: the happier a villager already is, the less they want it (so work and hobbies fill the day). */
    private fun danceNeed(v: Villager, base: Int): Int = when {
        v.happiness >= 90f -> 0
        v.happiness >= 75f -> if (rnd.nextInt(3) == 0) base else 0
        else -> base * 2
    }

    /** The villager's current "quest" line for the follow view. */
    fun questLine(v: Villager): String {
        if (v.phase == "sleeping") return "Sleeping"
        if (v.action.isEmpty()) return "Deciding what to do next"
        val base = doing(v).replaceFirstChar { it.uppercase() }
        return when {
            v.action.startsWith("gather_") && v.phase == "working" -> "$base (${v.gathered}/4)"
            v.phase == "walking" -> "$base — on the way"
            else -> base
        }
    }

    fun mood(v: Villager): String = when {
        v.hunger > 85f -> "starving"
        v.energy < 20f -> "exhausted"
        alert() && v.happiness < 60f -> "scared"
        v.happiness > 75f -> "joyful"
        v.happiness > 55f -> "content"
        v.happiness > 35f -> "restless"
        else -> "gloomy"
    }

    private fun pick(vararg w: Pair<String, Int>): String {
        var total = 0
        for (p in w) total += p.second
        var roll = rnd.nextInt(max(1, total))
        for (p in w) {
            if (roll < p.second) return p.first
            roll -= p.second
        }
        return w[0].first
    }

    private fun friendFor(v: Villager): Villager? {
        val others = villagers.filter { it !== v }
        if (others.isEmpty()) return null
        if (v.partner >= 0 && rnd.nextInt(3) == 0) others.firstOrNull { it.id == v.partner }?.let { return it }
        if (rnd.nextInt(3) == 0) return others[rnd.nextInt(others.size)]
        return others.maxByOrNull { (v.affinity[it.id] ?: 0) * 2 + rnd.nextInt(5) }
    }

    private fun factLines(v: Villager): List<String> {
        val out = ArrayList<String>()
        val n = villagers.size
        if (food <= n / 2) out.add("Only $food food left for $n of us.")
        else if (food >= n * 2) out.add("The stores hold $food food. We'll manage.")
        if (wood < 4) out.add("We're down to $wood wood.")
        if (stone < 2) out.add("Barely any stone, just $stone.")
        if (v.hunger > 70f) out.add("My stomach is growling. I need to eat.")
        if (v.energy < 30f) out.add("I could sleep on my feet.")
        if (alert()) out.add(if (predators.any { isThreat(it.kind) }) "The fighting has everyone on edge." else "Those wolves have everyone on edge.")
        if (storm()) out.add("That thunder rattles my teeth.")
        else if (raining()) out.add("This rain slows the ${v.job.lowercase()} work.")
        if (foggy()) out.add("I can hardly see past the fire in this fog.")
        when (seasonIdx()) {
            0 -> out.add("Spring at last. Good growing weather ahead.")
            1 -> out.add("The summer days are long. Plenty of daylight to work.")
            2 -> out.add("Autumn. Best bring in the harvest, we have $food food.")
            else -> out.add("Winter. The stores must last until spring, and we have $food food.")
        }
        if (weatherNow().startsWith("cold")) out.add("The cold gets into my bones.")
        if (partOfDay() == "evening") out.add("The light is fading. Time to wind down.")
        if (isNight()) out.add("Hard to see anything at night.")
        wars().firstOrNull()?.let { out.add("Word is ${it.first.name} is at war with ${it.second.name}.") }
        if (time < plagueUntil) out.add("People are falling sick. Keep your distance.")
        history(5).lastOrNull()?.let { out.add("I can't stop thinking about this: ${it.take(60)}") }
        childrenOf(v).firstOrNull { ageYears(it) >= 1 }?.let { out.add("${it.name} is ${ageYears(it)} already. They grow fast.") }
        critters.companionOf(v)?.let { c ->
            out.add("${c.name} is level ${c.level} now.")
            if (c.species.evolveTo > 0) out.add("${c.name} will change shape once the bond with them reaches ${c.trustNeeded()} (now ${c.bond}).")
            com.jondude1.deskglass.sim.Dex.branchOf(c.sp)?.let { out.add("${c.name} might take a rarer shape if it changes ${it.hint}.") }
        }
        v.memories.lastOrNull { !it.contains(" said") && !it.contains("told ") && !it.contains("rude") }?.let { out.add("I keep remembering when I ${it.take(50).removePrefix("I ")}.") }
        nearby(v).firstOrNull()?.let { out.add("${it.name} is ${doing(it)} over there.") }
        out.add("The ${ageInfo().name.lowercase()} suits us.")
        if (age > 0) out.add("${count("hut")} huts and ${count("farm")} farms. We've come far.")
        if (rnd.nextInt(4) == 0) out.addAll(JOB_LINES[v.job] ?: v.persona.lines)
        if (out.isEmpty()) out.addAll(v.persona.lines)
        return out
    }

    private fun kidLines(v: Villager): List<String> {
        val out = ArrayList<String>()
        if (v.hunger > 60f) out.add("I'm hungry!")
        if (v.energy < 35f || isNight()) out.add("I'm sleepy.")
        if (storm()) out.add("That thunder is so loud!")
        else if (raining()) out.add("Rain! Splash!")
        if (snowing()) out.add("Snow! Let's catch some!")
        if (alert()) out.add("I don't like the scary ones.")
        if (weatherNow().startsWith("cold")) out.add("Brr, it's cold!")
        v.parents.split(" & ").firstOrNull()?.takeIf { it.isNotBlank() }?.let { out.add("Where is $it?") }
        nearby(v).firstOrNull()?.let { out.add("Look, ${it.name} is ${doing(it)}!") }
        if (food > 0) out.add("We have $food food!")
        out.add("Play with me!")
        return out
    }

    private fun lineFor(v: Villager): String {
        val pool = if (isChild(v)) kidLines(v) else factLines(v)
        val fresh = pool.filter { l -> v.said.none { Speech.similar(it, l) } }
        val use = fresh.ifEmpty { pool }
        return use[rnd.nextInt(use.size)]
    }

    /** A reply that answers what was actually said, using real state. */
    private fun replyTo(o: Villager, from: Villager, msg: String): String {
        val m = msg.lowercase()
        val w = wars().firstOrNull()
        val r = when {
            m.contains("food") || m.contains("hungry") || m.contains("berr") || m.contains("stomach") || m.contains("eat") ->
                if (food > villagers.size) "Aye, ${from.name}. $food food in the stores, we're fine."
                else "Aye, ${from.name}. Only $food left. We should gather more."
            m.contains("wolf") -> if (alert()) "I heard them too, ${from.name}. Stay near the fire." else "The wolves have gone quiet, ${from.name}. For now."
            m.contains("rain") || m.contains("wet") ->
                if (raining()) "It won't stop, ${from.name}." else "At least the rain has stopped."
            m.contains("wood") || m.contains("timber") -> "We have $wood wood right now, ${from.name}."
            m.contains("stone") || m.contains("rock") -> "Stone stands at $stone."
            m.contains("tired") || m.contains("sleep") ->
                if (o.energy < 40f) "Me too. My energy is low." else "Rest well, ${from.name}."
            m.contains("baby") || m.contains("born") || m.contains("child") -> "Good news for the village, ${from.name}."
            (m.contains("war") || m.contains("raid") || m.contains("kingdom")) && w != null ->
                "Keep the village safe, ${from.name}. ${w.first.name} is fighting ${w.second.name}."
            m.contains("?") -> "I'm not sure, ${from.name}. I'm ${doing(o)} just now."
            m.contains("level") || m.contains("bond") || m.contains("shape") ->
                "They're coming along well, ${from.name}. Keep training them."
            m.contains("fading") || m.contains("wind down") || m.contains("night") -> "Aye, it is time to rest soon, ${from.name}."
            m.contains("grow fast") || m.contains("already") -> "They do grow fast, ${from.name}."
            m.contains("come far") || m.contains("suits us") -> "We have, ${from.name}. Together."
            m.contains("sick") || m.contains("keep your distance") -> "Stay well, ${from.name}."
            m.contains("growling") || m.contains("sleep on my feet") -> "Take care of yourself, ${from.name}."
            m.contains("cold") -> "Stay close to the fire, ${from.name}."
            else -> when (rnd.nextInt(4)) {
                0 -> "Aye, ${from.name}."
                1 -> "That's fair, ${from.name}."
                2 -> "I was thinking the same, ${from.name}."
                else -> "Good to hear it, ${from.name}."
            }
        }
        val out = if (Speech.similar(r, msg)) lineFor(o) else r
        return if (isChild(o)) Speech.kidify(out) else out
    }

    /** Stories and gossip come from things that really happened. */
    private fun storyFor(): String {
        val h = history(8)
        if (h.isNotEmpty()) {
            val t = h[rnd.nextInt(h.size)].take(70)
            return listOf("Gather round. $t", "Remember when: $t", "They'll tell this one for years: $t")[rnd.nextInt(3)]
        }
        val l = log.takeLast(6)
        if (l.isNotEmpty()) return "Did you hear? " + l[rnd.nextInt(l.size)].take(70)
        return STORIES[rnd.nextInt(STORIES.size)]
    }

    private fun ruleDecision(v: Villager): Pair<String, String> {
        val others = villagers.filter { it !== v }
        val hasShore = nearestShore(v) >= 0
        val part = partOfDay()
        val chat = if (time - v.lastTalk > 90.0) 1 else 0
        val starving = v.hunger >= 70f && food < 3
        if (v.hunger >= 60f && food > 0) return "eat" to ""
        if (v.energy < 25f || (isNight() && v.energy < 92f)) return "sleep" to ""
        if (isChild(v)) {
            orderAction(v)?.let { return it }
            if (storm() || predators.any { hypot(it.x - v.x, it.y - v.y) < 18f }) return "huddle" to ""
            if (feteOk() && rnd.nextInt(2) == 0) return "revel" to ""
            val sfun = listOf("snowman", "rake_leaves", "fly_kite").firstOrNull { seasonFunOk(it) }
            if (sfun != null && (sfun != "fly_kite" || rnd.nextInt(3) == 0) && rnd.nextInt(3) == 0) return sfun to ""
            val games = listOf("puddle_jump", "catch_fireflies", "hide_seek").filter { kidGameOk(it) }
            if (games.isNotEmpty() && rnd.nextInt(if (games.size > 1 || games[0] != "hide_seek") 2 else 3) == 0) return games[rnd.nextInt(games.size)] to ""
            if (hasDog(v) && dogOk() && rnd.nextInt(3) == 0) return "walk_dog" to ""
            return when (pick("swim" to (if (canSwim() && hasShore) 2 else 0), "play" to 4, "study" to (if (count("library") > 0) 4 else 0), "talk" to chat, "wander" to 2, "fetch_water" to (if (count("well") > 0) 1 else 0))) {
                "talk" -> "talk" to (friendFor(v)?.name ?: "")
                "play" -> "play" to ""
                "swim" -> "swim" to ""
                "study" -> "study" to ""
                "fetch_water" -> "fetch_water" to ""
                else -> "wander" to ""
            }
        }
        orderAction(v)?.let { if (!starving) return it }
        // smart choices: tend wounds, shelter from rain
        if (!starving && !alert() && v.hp < 70f && count("infirmary") > 0) return "recover" to ""
        val hurtNear = villagers.count { it !== v && it.hp < 60f && hypot(it.x - v.x, it.y - v.y) < 14f }
        if (!starving && !alert() && (v.hp < 65f || (hurtNear > 0 && rnd.nextInt(3) == 0) || (time < plagueUntil && rnd.nextInt(6) == 0)) && nearestTile(v, T.BUSH) >= 0) return "forage_herbs" to ""
        if (blazes.isNotEmpty() && !starving && v.hp > 40f && villagers.count { it !== v && it.action == "douse" } < 6 && rnd.nextInt(if (blazes.any { fireThreatensBuildings(it) }) 2 else 3) != 0) return "douse" to ""
        if (raining() && !storm() && !starving && !isFighter(v) && !alert() && rnd.nextInt(20) == 0) return "huddle" to ""
        if (drought() && !starving && !alert() && !isNight() && !isFighter(v) && count("well") > 0 && count("farm") > 0 && v.hp > 35f &&
            !farmsWatered() && villagers.count { it.action == "fetch_water" } < 3 && rnd.nextInt(3) == 0) return "fetch_water" to ""
        if (alert() && !starving) {
            // only trouble that is actually close stops the work: fighters stand guard, the rest take cover
            val danger = predators.any { hypot(it.x - v.x, it.y - v.y) < 13f }
            val fighter = isFighter(v) || v.job == "Scout"
            // healthy fighters hunt the lone beasts and thieves down instead of waiting; heavy foes are met at the fire
            val huntable = predators.filter { it.kind in HUNTABLE_FOES }
            if (fighter && v.hp > 70f && huntable.isNotEmpty() && villagers.count { it !== v && it.action == "attack" } < 3 && !isNight()) return "attack" to ""
            if (danger) return (if (fighter) "patrol" else "huddle") to ""
            if (!fighter && time < alertUntil && predators.any { hypot(it.x - v.x, it.y - v.y) < 22f } && rnd.nextInt(4) == 0) return "huddle" to ""
        }
        if (storm() && !starving && !isFighter(v) && rnd.nextInt(10) == 0) return "huddle" to ""
        if (feteOk() && !starving && rnd.nextInt(2) == 0) return "revel" to ""
        if (councilOk() && !starving && v.id !in councilGuestIds && v.hp > 35f && rnd.nextInt(3) != 0) return "council" to ""
        // a festival draws people to the fire, but only part of the village at a time, and the cheerful keep to their work
        if (festival() && (v.happiness < 92f || rnd.nextInt(4) == 0)) {
            return when (pick("dance" to (if (v.happiness < 85f) 4 else 2), "talk" to chat, "tell_story" to (if (v.job == "Storyteller") 4 else 0))) {
                "talk" -> "talk" to (friendFor(v)?.name ?: "")
                "tell_story" -> "tell_story" to ""
                else -> "dance" to ""
            }
        }
        if (food < 3) {
            val a = when {
                v.job == "Farmer" && age >= 1 && count("farm") > 0 -> "tend_farm"
                v.job == "Hunter" -> "hunt"
                hasShore && rnd.nextBoolean() -> "fish"
                else -> "gather_food"
            }
            return a to ""
        }
        if (policy.isNotEmpty() && time < policyUntil && !starving && !alert() && rnd.nextInt(10) < 4) {
            policyAction(v)?.let { policyActs++; return it }
        }
        val builder = v.job == "Builder"
        // the village grows as far as its population allows
        if (wood >= 6 && stone >= 2 && canBuild("hut") && (builder || rnd.nextInt(5) == 0)) return "build_hut" to ""
        if (age >= 1 && wood >= 4 && canBuild("farm") && (builder || v.job == "Farmer" || rnd.nextInt(6) == 0)) return "build_farm" to ""
        if (stone >= 3 && canBuild("campfire") && v.job != "Scout") return "build_campfire" to ""
        if (wood >= 12 && stone >= 4 && canBuild("totem") && (builder || rnd.nextInt(6) == 0)) return "build_totem" to ""
        if (wood >= 18 && stone >= 6 && canBuild("ranch") && (builder || critters.gifted(v) || rnd.nextInt(6) == 0)) return "build_ranch" to ""
        if (count("ranch") > 0 && ranchResidents() > 0 && !isChild(v) && !starving && rnd.nextInt(if (critters.gifted(v)) 6 else 12) == 0) return "tend_critters" to ""
        if (wood >= 10 && canBuild("pasture") && (builder || v.job == "Farmer" || rnd.nextInt(6) == 0)) return "build_pasture" to ""
        if (count("pasture") > 0 && !isChild(v) && !starving && rnd.nextInt(if (v.job == "Farmer") 5 else 10) == 0) return "tend_flock" to ""
        if (wood >= 8 && canBuild("apiary") && (builder || v.job == "Farmer" || rnd.nextInt(6) == 0)) return "build_apiary" to ""
        if (beesOk(v) && !starving && rnd.nextInt(if (v.job == "Farmer") 3 else 6) == 0) return "tend_bees" to ""
        if (wood >= 10 && canBuild("orchard") && (builder || v.job == "Farmer" || rnd.nextInt(6) == 0)) return "build_orchard" to ""
        if (fruitOk(v) && !starving && rnd.nextInt(if (v.job == "Farmer" || v.job == "Forager") 3 else 6) == 0) return "pick_fruit" to ""
        if (wood >= 10 && stone >= 6 && canBuild("bath") && (builder || rnd.nextInt(6) == 0)) return "build_bath" to ""
        if (soakOk(v) && !starving && rnd.nextInt(if (v.hp < 75f) 2 else if (seasonIdx() == 3) 4 else if (v.happiness >= 90f && v.energy > 60f) 24 else 9) == 0) return "soak" to ""
        if (wood >= 14 && stone >= 10 && canBuild("belfry") && (builder || rnd.nextInt(6) == 0)) return "build_belfry" to ""
        if (bellOk(v) && v.hunger < 70f && rnd.nextInt(if (alert()) 3 else 4) == 0) return "ring_bell" to ""
        if (wood >= 16 && stone >= 4 && canBuild("stage") && (builder || rnd.nextInt(6) == 0)) return "build_stage" to ""
        if (wood >= 12 && stone >= 6 && canBuild("kiln") && (builder || rnd.nextInt(6) == 0)) return "build_kiln" to ""
        if (loomOk(v) && !starving && v.hunger < 70f && rnd.nextInt(if (seasonIdx() >= 2) 3 else 7) == 0) return "weave" to ""
        if (wood >= 10 && canBuild("loom") && (builder || rnd.nextInt(6) == 0)) return "build_loom" to ""
        if (wood >= 14 && stone >= 4 && canBuild("yard") && (builder || critters.gifted(v) || rnd.nextInt(6) == 0)) return "build_yard" to ""
        if (yardOk(v) && !starving && v.hunger < 70f && rnd.nextInt(if (critters.gifted(v)) 3 else 9) == 0) return "train_critters" to ""
        if (kilnOk(v) && !starving && v.hunger < 70f && rnd.nextInt(if (goods < 6) 3 else 7) == 0) return "fire_pots" to ""
        if (showOk(v) && !starving && v.hunger < 70f && rnd.nextInt(if (v.happiness < 70f) 3 else 8) == 0) return "show" to ""
        if (wood >= 12 && stone >= 2 && canBuild("range") && (builder || isFighter(v) || v.job == "Hunter" || rnd.nextInt(6) == 0)) return "build_range" to ""
        if (shootOk(v) && !starving && rnd.nextInt(if (isFighter(v) || v.job == "Hunter") 3 else 8) == 0) return "shoot" to ""
        if (wood >= 20 && stone >= 30 && canBuild("shrine") && (builder || critters.gifted(v) || rnd.nextInt(5) == 0)) return "build_shrine" to ""
        if (vigilOk(v) && !starving && rnd.nextInt(if (critters.gifted(v)) 4 else 9) == 0) return "vigil" to ""
        if (wood >= 14 && stone >= 4 && canBuild("mill") && (builder || v.job == "Farmer" || rnd.nextInt(5) == 0)) return "build_mill" to ""
        if (cathOk(v) && !starving && rnd.nextInt(if (v.job == "Builder" || v.job == "Miner") 3 else 7) == 0) return "raise_cathedral" to ""
        if (millOk(v) && !starving && rnd.nextInt(if (v.job == "Farmer") 3 else if (food < 40 + adults() * 5) 4 else 8) == 0) return "grind" to ""
        if (wood >= 16 && canBuild("quarry") && (builder || v.job == "Miner" || rnd.nextInt(5) == 0)) return "build_quarry" to ""
        if (quarryOk(v) && !starving && rnd.nextInt(if (v.job == "Miner") 2 else if (stone < 60 + adults() * 8) 4 else 12) == 0) return "quarry" to ""
        if (wood >= 8 && canBuild("dock") && (builder || v.job == "Forager" || rnd.nextInt(5) == 0)) return "build_dock" to ""
        if (count("dock") > 0 && sailOk() && !isChild(v) && !starving && rnd.nextInt(if (v.job == "Forager") 4 else 8) == 0) return "sail" to ""
        if (campUp && !starving && campAssaultOk(v) && rnd.nextInt(if (isFighter(v)) 3 else 9) == 0) return "assault_camp" to ""
        if (stone >= 6 && wood >= 4 && canBuild("smelter") &&
            (builder || v.job == "Bronzesmith" || v.job == "Blacksmith" || rnd.nextInt(6) == 0)
        ) return "build_smelter" to ""
        if (wood >= 4 && stone >= 6 && canBuild("well") && (builder || drought() || rnd.nextInt(6) == 0)) return "build_well" to ""
        if (wood >= 10 && stone >= 4 && canBuild("workshop") && (builder || rnd.nextInt(6) == 0)) return "build_workshop" to ""
        if (wood >= 12 && stone >= 6 && canBuild("market") && (builder || rnd.nextInt(6) == 0)) return "build_market" to ""
        if (wood >= 14 && stone >= 6 && canBuild("granary") && (builder || v.job == "Farmer" || rnd.nextInt(6) == 0)) return "build_granary" to ""
        if (wood >= 18 && stone >= 12 && canBuild("library") && (builder || v.job == "Storyteller" || rnd.nextInt(8) == 0)) return "build_library" to ""
        if (wood >= 20 && stone >= 10 && canBuild("tavern") && (builder || rnd.nextInt(8) == 0)) return "build_tavern" to ""
        if (wood >= 16 && stone >= 8 && canBuild("infirmary") && (builder || rnd.nextInt(8) == 0)) return "build_infirmary" to ""
        if (wood >= 16 && stone >= 14 && metal >= 2 && canBuild("barracks") && (builder || isFighter(v) && rnd.nextInt(4) == 0)) return "build_barracks" to ""
        if (wood >= 50 && stone >= 40 && metal >= 5 && canBuild("wonder") && (builder || rnd.nextInt(5) == 0)) return "build_wonder" to ""
        if (wood >= 2 && stone >= 2 && canBuild("lamp") && (builder || rnd.nextInt(8) == 0)) return "build_lamp" to ""

        if (graves.isNotEmpty() && !isChild(v) && !starving && !alert() && !isNight() && !raining() &&
            rnd.nextInt(if (time - lastBuryAt < DAY_LEN) 3 else if (part == "evening" || part == "morning") 9 else 22) == 0) return "mourn" to ""
        if (part == "evening" || part == "morning") {
            if (count("totem") > 0 && !isChild(v) && rnd.nextInt(if (part == "morning") 10 else 6) == 0) return "honor" to ""
            if (vigilOk(v) && rnd.nextInt(if (part == "evening") 4 else 8) == 0) return "vigil" to ""
        }
        if (part == "evening") {
            if (count("tavern") > 0 && food >= 3 && !isChild(v) && rnd.nextInt(if (villagers.any { it.action == "tavern" }) 2 else 3) == 0) return "tavern" to ""
            if (v.job == "Storyteller") return "tell_story" to ""
            if (count("campfire") > 0 && v.happiness < 88f && rnd.nextInt(4) == 0) return "dance" to ""
            if (chat == 1 && others.isNotEmpty() && rnd.nextInt(6) == 0) return "talk" to (friendFor(v)?.name ?: "")
        }
        if (starReady() && !alert() && !starving && v.hunger < 55f && (rnd.nextInt(3) == 0 || villagers.count { it.action == "gather_star" } == 0)) return "gather_star" to ""
        // leisure: a swim on summer days, stars on clear evenings, herbs now and then
        if (v.happiness < 85f || rnd.nextInt(3) == 0) {
            if (canSwim() && hasShore && v.hunger < 55f && rnd.nextInt(7) == 0) return "swim" to ""
            if (canStargaze() && count("campfire") > 0 && part == "evening" && rnd.nextInt(5) == 0) return "stargaze" to ""
            if (!starving && v.hunger < 55f && rnd.nextInt(5) == 0 && teachChild(v) != null) return "teach" to ""
            if (rnd.nextInt(3) == 0) for (sa in listOf("snowman", "rake_leaves", "fly_kite")) if (seasonFunOk(sa) && (sa != "fly_kite" || rnd.nextInt(4) == 0)) return sa to ""
        }
        if (rnd.nextInt(14) == 0) for (sa in listOf("snowman", "rake_leaves")) if (seasonFunOk(sa)) return sa to ""
        if (hasDog(v) && dogOk() && rnd.nextInt(6) == 0) return "walk_dog" to ""
        if (rnd.nextInt(40) == 0 && nearestTile(v, T.BUSH) >= 0) return "forage_herbs" to ""
        if (!alert() && !starving && v.hunger < 55f && rnd.nextInt(4) == 0) personaPastime(v, part, chat, hasShore)?.let { return it }
        val ad = adults()
        val farms = age >= 1 && count("farm") > 0
        val well = count("well") > 0
        val shop = count("workshop") > 0 && wood >= 12 && stone >= 4 && goods < 20
        val market = count("market") > 0 && goods >= 2
        val lowWood = wood < 25 + ad * 4
        val lowStone = stone < 10 + ad * 2 || wonderSaving()
        val lowFood = food < 10 + ad * 3
        val granary = count("granary") > 0 && food >= 4
        val barracks = count("barracks") > 0
        val library = count("library") > 0
        val fighterOnWatch = ad >= 6 && count("tower") == 0 && rnd.nextInt(6) == 0
        val act = when (v.job) {
            "Woodcutter" -> pick(
                "gather_wood" to (if (lowWood) 8 else 3), "gather_stone" to (if (lowStone) 3 else 0),
                "talk" to chat, "wander" to 1, "gather_food" to (if (lowFood) 3 else 0),
                "craft" to (if (shop) 2 else 0), "trade" to (if (market) 2 else 0),
            )
            "Miner" -> pick(
                "gather_stone" to (if (lowStone) 7 else 2),
                "mine_ore" to (if (age >= 2 && ore < 30 && ore < 60) 4 else 0),
                "gather_wood" to (if (lowWood) 3 else 1), "talk" to chat, "wander" to 1,
                "craft" to (if (shop) 3 else 0),
            )
            "Forager" -> pick(
                "gather_food" to (if (lowFood) 5 else 2), "fish" to (if (hasShore) 3 else 0), "talk" to chat, "wander" to 1,
                "haul" to (if (granary) 2 else 0),
                "fetch_water" to (if (well) 2 + (if (drought()) 6 else 0) else 0), "trade" to (if (market) 2 else 0),
            )
            "Hunter" -> pick(
                "hunt" to (if (lowFood) 8 else 4), "drill" to (if (barracks) 3 else 0), "gather_food" to 1, "fish" to (if (hasShore) 1 else 0), "talk" to chat,
                "patrol" to (if (fighterOnWatch) 3 else 1), "wander" to 1,
            )
            "Farmer" -> pick(
                "tend_farm" to (if (farms) 6 else 0), "haul" to (if (granary) 2 else 0), "fetch_water" to (if (well && farms) 3 + (if (drought()) 9 else 0) else 0),
                "gather_food" to 2, "gather_wood" to (if (farms) 0 else 4), "talk" to chat, "trade" to (if (market) 2 else 0),
            )
            "Builder" -> pick(
                "gather_wood" to (if (lowWood) 6 else 3), "gather_stone" to (if (lowStone) 4 else 1),
                "craft" to (if (shop) 3 else 0), "study" to studyW(v, 1), "talk" to chat, "wander" to 1,
            )
            "Bronzesmith", "Blacksmith" ->
                if (count("smelter") > 0) pick(
                    "smelt" to (if (ore >= 2 && wood >= 30 && metal < 150) 7 else 0),
                    "mine_ore" to (if (ore < 6) 6 else if (ore < 40) 1 else 0),
                    "gather_stone" to (if (stone < 20) 2 else 0),
                    "gather_wood" to (if (wood < 60) 5 else 1), "talk" to chat,
                    "craft" to (if (shop) 3 else 0),
                ) else pick("mine_ore" to 4, "gather_stone" to 3, "gather_wood" to 2, "talk" to chat)
            "Scout" -> pick("explore" to 3, "patrol" to 3, "drill" to (if (barracks) 2 else 0), "gather_food" to 2, "talk" to chat, "wander" to 1)
            else -> pick(
                "talk" to chat, "gather_food" to 2, "tell_story" to (if (v.energy > 40f) 2 else 0), "wander" to 1,
                "study" to studyW(v, 4), "tavern" to (if (count("tavern") > 0 && food >= 3) 2 else 0),
                "dance" to (if (count("campfire") > 0) danceNeed(v, 1) else 0), "fetch_water" to (if (well) 2 else 0),
                "trade" to (if (market) 2 else 0), "craft" to (if (shop) 2 else 0),
            )
        }
        val act2 = if (storeFull(act) && v.hunger < 60f) surplusWork(v) else act
        return act2 to (if (act2 == "talk") (friendFor(v)?.name ?: "") else "")
    }

    private fun applyDecision(v: Villager, actionIn: String, targetIn: String, say: String, thought: String) {
        v.waiting = false
        var action = actionIn
        var target = targetIn
        // don't do the exact same thing four times in a row
        val survival = action == "eat" || action == "sleep"
        if (!survival && v.recent.size >= 3 && v.recent.takeLast(3).all { it == action }) {
            action = "wander"
        }
        if (!survival && rnd.nextInt(10) < 7) orderAction(v)?.let { action = it.first; target = it.second }
        val thoughtClean = Speech.clean(thought, 100)
        if (thoughtClean.isNotBlank()) v.thought = thoughtClean
        var sayClean = Speech.clean(say, 90)
        if (isChild(v)) sayClean = Speech.kidify(sayClean)
        if (sayClean.isNotBlank() && v.said.any { Speech.similar(it, sayClean) }) sayClean = ""
        if (action == "talk") v.talkMsg = sayClean.ifBlank { lineFor(v) }
        else if (sayClean.isNotBlank()) {
            v.say = sayClean
            v.sayLeft = 7f
            noteSaid(v, sayClean)
            addLog("${v.name}: $sayClean")
        }
        if (!survival && storeFull(action)) {
            action = surplusWork(v)
            target = if (action == "talk") (friendFor(v)?.name ?: "") else ""
        }
        var ok = action in ACTIONS && startAction(v, action, target)
        if (!ok) {
            val (a, t) = ruleDecision(v)
            action = a
            ok = startAction(v, a, t)
            if (!ok) {
                action = "wander"
                ok = startAction(v, "wander", "")
            }
        }
        if (!ok) {
            v.idleFor = -3f
        } else {
            v.recent.add(action)
            while (v.recent.size > 5) v.recent.removeAt(0)
            v.addDiary("${partOfDay().replaceFirstChar { it.uppercase() }}: ${doing(v)}")
            if (v.thought.isNotBlank()) narrate("${v.name}: ${v.thought}")
        }
    }

    private fun others(): List<Villager> = villagers

    fun seasonName(): String = when (seasonIdx()) {
        0 -> "spring"
        1 -> "summer"
        2 -> "autumn"
        else -> "winter"
    }

    private fun weatherNow(): String = when {
        storm() -> "a thunderstorm"
        raining() -> "raining"
        drought() -> "hot and dry, in a drought that parches the fields"
        snowing() -> "cold, with snow falling"
        foggy() -> "foggy"
        seasonName() == "winter" -> "cold, with snow about"
        else -> "dry"
    }

    private fun foeText(): String {
        val k = predators.firstOrNull { isThreat(it.kind) }?.kind
        return when (k) {
            "raider" -> "raiders are attacking"
            "ogre" -> "an ogre is rampaging"
            "wraith" -> "rift wraiths are loose"
            "drake" -> "a rift drake is attacking"
            "warlord" -> "a raider warlord and his warband are attacking"
            "golem" -> "a stone golem is smashing through"
            "shaman" -> "a raider shaman is cursing us"
            "frostwolf" -> "a pack of frost wolves is on the prowl"
            "lich" -> "a rift lich and its skeletons are attacking"
            "skeleton" -> "skeletons are rattling out of the ground"
            "pilferer" -> "a pilferer is sneaking after the stores"
            "hornet" -> "an angry hornet swarm is buzzing about"
            "reaver" -> "sea reavers have landed from a longship"
            "troll" -> "a night troll is hurling boulders"
            "spider" -> "a giant forest spider is spinning webs"
            "harpy" -> "a flock of rift harpies is swooping on the stores"
            "burrower" -> "a giant burrowing worm is bursting out of the ground"
            else -> "wolves are howling"
        }
    }

    fun doing(o: Villager) = when (o.action) {
        "" -> if (o.phase == "sleeping") "sleeping" else "resting"
        "gather_wood" -> "cutting wood"
        "gather_stone" -> "gathering stone"
        "gather_food" -> "picking berries"
        "mine_ore" -> "mining ore"
        "smelt" -> "working the forge"
        "fish" -> "fishing"
        "hunt" -> "hunting"
        "tend_farm" -> "tending the farm"
        "explore" -> "scouting"
        "tell_story" -> "telling a story"
        "huddle" -> "keeping close to the fire"
        "eat" -> "eating"
        "sleep" -> "sleeping"
        "talk" -> "chatting"
        "wander" -> "strolling about"
        "fetch_water" -> "fetching water"
        "craft" -> "making things at the workshop"
        "trade" -> "trading at the market"
        "dance" -> "dancing"
        "patrol" -> "keeping watch"
        "play" -> "playing"
        "swim" -> "swimming in the lake"
        "snowman" -> "building a snowman"
        "rake_leaves" -> "raking autumn leaves into a pile"
        "fly_kite" -> "flying a kite"
        "puddle_jump" -> "splashing in the rain puddles"
        "catch_fireflies" -> "catching fireflies in a jar"
        "hide_seek" -> "playing hide and seek in the bushes"
        "walk_dog" -> "playing fetch with the dog"
        "forage_herbs" -> "gathering healing herbs"
        "stargaze" -> "watching the stars"
        "gather_star" -> "gathering shards of a fallen star"
        "douse" -> "fighting the forest fire with water"
        "carve" -> "carving wooden toys"
        "pick_flowers" -> "picking wildflowers for a friend"
        "assault_camp" -> "storming the bandit camp"
        "delve" -> if (riftState == 2) "fighting deep inside the rift" else "marching to the rift"
        "haul" -> "filling the granary"
        "drill" -> "drilling with the guard"
        "study" -> "reading in the library"
        "tavern" -> "eating and chatting at the tavern"
        "recover" -> "resting at the infirmary"
        "honor" -> "honoring the totem pole"
        "mourn" -> "laying flowers at the graveyard"
        "teach" -> "teaching a child a trade"
        "council" -> "sitting on the village council"
        "revel" -> if (feteOn) "joining the ${feteTitle()}" else "celebrating"
        "build_shrine" -> "building a legend shrine"
        "vigil" -> "keeping a vigil at the legend shrine"
        "tend_critters" -> "feeding and grooming critters at the ranch"
        "build_ranch" -> "building a critter ranch"
        "tend_flock" -> "tending the sheep and hens at the pasture"
        "tend_bees" -> "taking honey from the beehives"
        "pick_fruit" -> "picking fruit in the orchard"
        "shoot" -> "practising archery at the range"
        "soak" -> "soaking in the hot spring"
        "ring_bell" -> "ringing the bell tower"
        "show" -> "putting on a puppet show"
        "fire_pots" -> "firing pots in the potter's kiln"
        "train_critters" -> "drilling critters in the training yard"
        "build_yard" -> "building a critter training yard"
        "weave" -> "weaving cloth and cloaks at the loom"
        "build_loom" -> "building a weaver's loom"
        "build_kiln" -> "building a potter's kiln"
        "build_stage" -> "building a puppet theatre"
        "build_belfry" -> "building a bell tower"
        "build_bath" -> "building a hot spring"
        "build_range" -> "building an archery range"
        "build_orchard" -> "planting an orchard of fruit trees"
        "build_apiary" -> "building an apiary of straw beehives"
        "sail" -> "rowing out from the fishing dock"
        "build_dock" -> "building a fishing dock"
        "build_quarry" -> "building a stone quarry"
        "build_mill" -> "building a windmill"
        "grind" -> "grinding grain at the windmill"
        "raise_cathedral" -> "raising the great cathedral"
        "quarry" -> "cutting stone at the quarry"
        "build_pasture" -> "building a fenced pasture"
        "build_totem" -> "carving a totem pole"
        "build_infirmary" -> "building an infirmary"
        "build_hut" -> "building a home"
        "build_farm" -> "building a farm"
        "build_campfire" -> "building a campfire"
        "build_smelter" -> "building a forge"
        "build_well" -> "digging a well"
        "build_workshop" -> "building a workshop"
        "build_market" -> "building a market"
        "build_lamp" -> "setting up a lantern"
        "build_granary" -> "building a granary"
        "build_barracks" -> "building barracks"
        "build_library" -> "building a library"
        "build_tavern" -> "building a tavern"
        "build_wonder" -> "raising a great wonder"
        else -> if (o.action.startsWith("critter_")) "tending to their companion" else o.action.replace('_', ' ')
    }
    private fun childrenOf(v: Villager): List<Villager> = villagers.filter { it !== v && it.parents.contains(v.name) }
    private fun nearby(v: Villager, r: Float = 6f): List<Villager> =
        villagers.filter { it !== v && hypot(it.x - v.x, it.y - v.y) <= r }

    private fun noteSaid(v: Villager, text: String) {
        v.said.add(text)
        while (v.said.size > 8) v.said.removeAt(0)
    }

    private fun history(n: Int): List<String> = chronicle.takeLast(n).map { it.substringAfter(": ") }

    private fun allowedActions(): List<String> {
        val a = arrayListOf("gather_wood", "gather_stone", "gather_food", "fish", "hunt", "explore", "tell_story", "huddle", "sleep", "talk", "wander")
        if (food > 0) a.add("eat")
        if (wood >= 6 && stone >= 2 && canBuild("hut")) a.add("build_hut")
        if (stone >= 3 && canBuild("campfire")) a.add("build_campfire")
        if (age >= 1 && wood >= 4 && canBuild("farm")) a.add("build_farm")
        if (canBuild("well") && wood >= 4 && stone >= 6) a.add("build_well")
        if (canBuild("workshop") && wood >= 10 && stone >= 4) a.add("build_workshop")
        if (canBuild("market") && wood >= 12 && stone >= 6) a.add("build_market")
        if (canBuild("lamp") && wood >= 2 && stone >= 2) a.add("build_lamp")
        if (canBuild("wonder") && wood >= 50 && stone >= 40 && metal >= 5) a.add("build_wonder")
        if (count("well") > 0) a.add("fetch_water")
        if (count("workshop") > 0 && wood >= 6 && stone >= 2) a.add("craft")
        if (count("market") > 0 && goods >= 2) a.add("trade")
        if (count("campfire") > 0) a.add("dance")
        a.add("forage_herbs")
        if (canSwim() && nearestShoreAny()) a.add("swim")
        for (sa in listOf("snowman", "rake_leaves", "fly_kite")) if (seasonFunOk(sa)) a.add(sa)
        for (ga in listOf("puddle_jump", "catch_fireflies", "hide_seek")) if (kidGameOk(ga)) a.add(ga)
        if (pets.isNotEmpty() && dogOk()) a.add("walk_dog")
        if (canStargaze() && count("campfire") > 0) a.add("stargaze")
        if (starReady() && !alert()) a.add("gather_star")
        if (blazes.isNotEmpty()) a.add("douse")
        if (wood >= 8 && goods < 30 && count("campfire") > 0) a.add("carve")
        a.add("pick_flowers")
        if (riftState == 1 && party.size < 4) a.add("delve")
        if (campAssaultOk(null)) a.add("assault_camp")
        if (canBuild("granary") && wood >= 14 && stone >= 6) a.add("build_granary")
        if (canBuild("library") && wood >= 18 && stone >= 12) a.add("build_library")
        if (canBuild("tavern") && wood >= 20 && stone >= 10) a.add("build_tavern")
        if (count("tavern") > 0 && food >= 3) a.add("tavern")
        if (canBuild("infirmary") && wood >= 16 && stone >= 8) a.add("build_infirmary")
        if (canBuild("totem") && wood >= 12 && stone >= 4) a.add("build_totem")
        if (count("totem") > 0) a.add("honor")
        if (graves.isNotEmpty() && !isNight() && !alert()) a.add("mourn")
        if (feteOk()) a.add("revel")
        if (councilOk()) a.add("council")
        if (!alert() && !storm() && !isNight() && villagers.any { isChild(it) }) a.add("teach")
        if (canBuild("shrine") && wood >= 20 && stone >= 30) a.add("build_shrine")
        if (count("shrine") > 0 && !isNight() && !alert()) a.add("vigil")
        if (canBuild("ranch") && wood >= 18 && stone >= 6) a.add("build_ranch")
        if (count("ranch") > 0 && ranchResidents() > 0) a.add("tend_critters")
        if (canBuild("pasture") && wood >= 10) a.add("build_pasture")
        if (count("pasture") > 0) a.add("tend_flock")
        if (canBuild("apiary") && wood >= 8) a.add("build_apiary")
        if (count("apiary") > 0 && !isNight() && !raining() && seasonIdx() != 3 && honeyReady() >= 1) a.add("tend_bees")
        if (canBuild("orchard") && wood >= 10) a.add("build_orchard")
        if (count("orchard") > 0 && !isNight() && !raining() && fruitReady() >= 1) a.add("pick_fruit")
        if (canBuild("range") && wood >= 12 && stone >= 2) a.add("build_range")
        if (count("range") > 0 && !isNight() && !alert() && !raining()) a.add("shoot")
        if (canBuild("bath") && wood >= 10 && stone >= 6) a.add("build_bath")
        if (count("bath") > 0 && !alert()) a.add("soak")
        if (canBuild("belfry") && wood >= 14 && stone >= 10) a.add("build_belfry")
        if (count("belfry") > 0 && (alert() || (!isNight() && !raining()))) a.add("ring_bell")
        if (canBuild("stage") && wood >= 16 && stone >= 4) a.add("build_stage")
        if (canBuild("kiln") && wood >= 12 && stone >= 6) a.add("build_kiln")
        if (count("kiln") > 0 && !isNight() && !alert() && wood >= 6) a.add("fire_pots")
        if (canBuild("yard") && wood >= 14 && stone >= 4) a.add("build_yard")
        if (count("yard") > 0 && !isNight() && !alert() && !raining() && critters.critters.isNotEmpty()) a.add("train_critters")
        if (canBuild("loom") && wood >= 10) a.add("build_loom")
        if (count("loom") > 0 && !isNight() && !alert() && hides >= 2) a.add("weave")
        if (count("stage") > 0 && !alert() && !raining() && (!isNight() || partOfDay() == "evening")) a.add("show")
        if (canBuild("dock") && wood >= 8) a.add("build_dock")
        if (canBuild("quarry") && wood >= 16) a.add("build_quarry")
        if (count("quarry") > 0 && !isNight() && stone < capStone() * 0.85) a.add("quarry")
        if (canBuild("mill") && wood >= 14 && stone >= 4) a.add("build_mill")
        if (count("mill") > 0 && count("farm") > 0 && !isNight()) a.add("grind")
        if (cathReady() && !isNight()) a.add("raise_cathedral")
        if (count("dock") > 0 && sailOk()) a.add("sail")
        if (count("infirmary") > 0) a.add("recover")
        if (canBuild("barracks") && wood >= 16 && stone >= 14 && metal >= 2) a.add("build_barracks")
        if (count("granary") > 0 && food >= 4) a.add("haul")
        if (count("barracks") > 0) a.add("drill")
        if (predators.isNotEmpty()) { a.add("attack"); a.add("patrol") }
        if (count("library") > 0) a.add("study")
        if (age >= 1 && count("farm") > 0) a.add("tend_farm")
        if (age >= 2) {
            a.add("mine_ore")
            if (ore >= 2 && wood >= 1 && count("smelter") > 0) a.add("smelt")
            if (wood >= 4 && stone >= 6 && canBuild("smelter")) a.add("build_smelter")
        }
        return a
    }

    private fun buildPrompt(v: Villager): String {
        val kid = isChild(v)
        val sb = StringBuilder()
        sb.append("FACTS. Mention only things listed here. Never invent buildings, people, resources or events.\n")
        sb.append("Time: day ${day()}, ${partOfDay()}, ${seasonName()}. Weather: ${weatherNow()}.\n")
        visitor?.let { if (it.arrivedAt >= 0.0 && !it.leaving) sb.append("A ${visitorLabel(it.kind)} is visiting the fire right now.\n") }
        val ev = ArrayList<String>()
        if (alert()) ev.add(foeText() + " near the village")
        if (festival()) ev.add("a feast is being held by the fire")
        if (feteOn) ev.add("the ${feteTitle()} is being held on the green")
        if (councilOn) ev.add("the village council is meeting at the ${councilHubName()}")
        if (policy.isNotEmpty() && time < policyUntil) ev.add("the council has set the village priority: ${policyTitle()} (${policyBlurb()})")
        if (time < plagueUntil) ev.add("a sickness is spreading")
        if (blazes.isNotEmpty()) ev.add("a wildfire is burning in the forest (${blazes.size} patches) and needs dousing")
        if (starReady()) ev.add("a star fell from the sky near the village and ${starShards} shards of starmetal lie in the crater")
        wars().firstOrNull()?.let { ev.add("${it.first.name} is at war with ${it.second.name}") }
        sb.append("Happening now: " + (if (ev.isEmpty()) "nothing unusual" else ev.joinToString("; ")) + ". ${ageInfo().name}: ${ageInfo().blurb}\n")
        sb.append("You are ${v.name}, ${if (kid) "a small child" else "the ${v.job.lowercase()}"} of Hearthvale, ${ageYears(v)} years old. ${JOB_BLURB[v.job] ?: ""}\n")
        sb.append(techNote() + "\n")
        sb.append("Personality: ${v.trait}. You ${v.persona.voice}. Your dream: ${v.persona.dream}${if (v.stat("dream") > 0) " (it has come true!)" else ""}.\n")
        sb.append("Body and mood: hunger ${v.hunger.toInt()}/100 (eat above 60), energy ${v.energy.toInt()}/100, health ${v.hp.toInt()}/100, feeling ${mood(v)}.\n")
        v.recent.lastOrNull()?.let { sb.append("You just did: ${it.replace('_', ' ')}.\n") }
        val kids = childrenOf(v)
        val fam = ArrayList<String>()
        if (v.parents.isNotBlank()) fam.add("your parents are ${v.parents}")
        if (kids.isNotEmpty()) fam.add("your children: " + kids.joinToString(", ") { "${it.name} (${ageYears(it)})" })
        if (fam.isNotEmpty()) sb.append("Family: " + fam.joinToString("; ") + ".\n")
        critters.factFor(v)?.let { sb.append(it + "\n") }
        if (critters.critters.isNotEmpty()) sb.append("The village keeps ${critters.critters.size} friendly critters that help with the work and train.\n")
        sb.append("Village stock: wood $wood, stone $stone, food $food")
        if (age >= 2) sb.append(", ore $ore, metal $metal")
        sb.append(". Buildings: ${count("hut")} ${if (age == 0) "tents" else "huts"}, ")
        if (age >= 1) sb.append("${count("farm")} farms, ")
        if (age >= 2) sb.append("${count("smelter")} forges, ")
        sb.append("${count("campfire")} campfires")
        for ((t, nm) in listOf("well" to "wells", "workshop" to "workshops", "market" to "markets", "granary" to "granaries", "barracks" to "barracks", "library" to "libraries", "tavern" to "taverns", "ranch" to "critter ranches", "pasture" to "pastures", "apiary" to "apiaries", "orchard" to "orchards", "range" to "archery ranges", "bath" to "hot springs", "belfry" to "bell towers", "stage" to "puppet theatres", "kiln" to "potter's kilns", "yard" to "training yards", "loom" to "weaver's looms", "dock" to "fishing docks", "quarry" to "stone quarries", "mill" to "windmills", "shrine" to "legend shrines", "totem" to "totem poles", "infirmary" to "infirmaries", "wonder" to "great wonders")) if (count(t) > 0) sb.append(", ${count(t)} $nm")
        if (cathX >= 0 && cathTier() > 0) sb.append(", a great cathedral at tier ${cathTier()} of 5")
        if (goods > 0) sb.append(", $goods goods to sell")
        sb.append(". ${villagers.size} villagers (${villagers.count { isChild(it) }} children).\n")
        val near = nearby(v)
        if (near.isNotEmpty()) {
            sb.append("Nearby: " + near.joinToString("; ") {
                "${it.name} the ${it.job.lowercase()} (${doing(it)}${if (it.say.isNotBlank()) ", just said \"${it.say.take(50)}\"" else ""})"
            } + "\n")
        } else {
            sb.append("Nobody is close to you right now.\n")
        }
        val far = villagers.filter { it !== v && it !in near }
        if (far.isNotEmpty()) sb.append("Elsewhere: " + far.joinToString(", ") { "${it.name} (${doing(it)})" } + "\n")
        val close = villagers.filter { it !== v && (v.affinity[it.id] ?: 0) >= 2 }.joinToString(", ") { it.name }
        val rivals = villagers.filter { it !== v && (v.affinity[it.id] ?: 0) <= -2 }.joinToString(", ") { it.name }
        if (v.titles.isNotEmpty()) sb.append("Your earned epithets: " + v.titles.joinToString(", ") + ".\n")
        partnerOf(v)?.let { sb.append("Your partner (spouse) is ${it.name}.\n") }
        bestFriendOf(v)?.let { sb.append("Your best friend is ${it.name}.\n") }
        feudWith(v)?.let { sb.append("You are feuding with ${it.name} right now.\n") }
        if (close.isNotEmpty()) sb.append("Your friends: $close.\n")
        if (rivals.isNotEmpty()) sb.append("You don't get along with: $rivals.\n")
        if (v.memories.isNotEmpty()) sb.append("Your recent memories: " + v.memories.joinToString("; ") + "\n")
        if (v.heard.isNotEmpty()) sb.append("You recently heard: " + v.heard.joinToString(" | ") + "\n")
        if (v.recent.isNotEmpty()) sb.append("Your last actions: " + v.recent.joinToString(", ") + " (vary what you do).\n")
        if (v.said.isNotEmpty()) sb.append("Things you already said (do not repeat): " + v.said.takeLast(3).joinToString(" | ") + "\n")
        val h = history(3)
        if (h.isNotEmpty()) sb.append("Village history: " + h.joinToString(" | ") + "\n")
        val recent = log.takeLast(4)
        if (recent.isNotEmpty()) sb.append("Recent news: " + recent.joinToString(" | ") + "\n")
        sb.append(worldNote())
        if (godMsg.isNotBlank() && time < godUntil) {
            sb.append("A booming voice from the sky said to everyone: \"$godMsg\" (consider it seriously).\n")
        }
        sb.append("Actions you can use now: ${allowedActions().joinToString(", ")}.\n")
        if (kid) sb.append("You are a small child: use very short, simple words, at most 8 words, about play, food, family or what you see.\n")
        sb.append("What do you do next? Reply with one JSON object only. Your 'say' must rely only on the facts above.")
        return sb.toString()
    }

    private fun shortPrompt(v: Villager): String =
        "You are ${v.name}, ${if (isChild(v)) "a child" else "a ${v.job.lowercase()}"}. ${partOfDay()}. Hunger ${v.hunger.toInt()}, energy ${v.energy.toInt()}. " +
            "Stock: wood $wood, stone $stone, food $food. Pick one action from: ${allowedActions().joinToString(", ")}. JSON only."

    private fun logProblem(key: String, msg: String) {
        val now = System.currentTimeMillis()
        if (now - (problemAt[key] ?: 0L) < 120_000L) {
            problemSkipped[key] = (problemSkipped[key] ?: 0) + 1
            return
        }
        problemAt[key] = now
        val skipped = problemSkipped.remove(key) ?: 0
        onError(if (skipped > 0) "$msg (+$skipped similar)" else msg)
    }

    private fun brainStatus(base: String) {
        brain = base + " · $okCount ok" + (if (fixCount > 0) ", $fixCount repaired" else "") + (if (failCount > 0) ", $failCount failed" else "")
    }

    private fun send(v: Villager) {
        val b = brainProvider() ?: return
        val prompt = buildPrompt(v)
        val short = shortPrompt(v)
        v.waiting = true
        inFlight = true
        sentAt = time
        val req = ++reqSeq
        curReq = req
        brain = "thinking…"
        val id = v.id
        scope.launch(Dispatchers.IO) {
            var text: String? = null
            var err: String? = null
            var repaired = false
            for (attempt in 0..1) {
                try {
                    val t = b.ask(if (attempt == 0) SYSTEM else SYSTEM_SHORT, if (attempt == 0) prompt else short)
                    val p = Speech.parse(t)
                    if (p != null) {
                        text = t
                        repaired = p.repaired || attempt > 0
                        break
                    }
                    err = "unparsable reply: ${t.take(100)}"
                } catch (e: Exception) {
                    err = "${e.javaClass.simpleName}: ${e.message}"
                }
            }
            results.add(Decision(id, text, err, req, repaired))
        }
    }

    private fun drain() {
        if (inFlight && time - sentAt > 100.0) {
            inFlight = false
            curReq = ++reqSeq
            for (x in villagers) x.waiting = false
            failCount++
            retryAt = time + 30.0
            brainStatus("model too slow — autopilot")
            logProblem("slow", "Village model took too long; villagers use autopilot for a moment.")
        }
        while (true) {
            val r = results.poll() ?: break
            if (r.reqId != curReq) continue
            inFlight = false
            nextAskAt = time + (brainProvider()?.gapSeconds ?: 0.0)
            val v = villagers.firstOrNull { it.id == r.villagerId }
            if (r.text == null) {
                failCount++
                retryAt = time + 45.0
                brainStatus("offline — using autopilot")
                logProblem("call", "Village model call failed: ${r.error}")
                v?.waiting = false
                continue
            }
            try {
                val p = Speech.parse(r.text) ?: throw IllegalStateException("no usable JSON")
                okCount++
                if (r.repaired) fixCount++
                brainStatus((brainProvider()?.label ?: "model") + " online")
                if (v != null && v.phase == "idle") {
                    var act = p.action.trim().lowercase().replace(' ', '_').replace('-', '_')
                    if (act !in ACTIONS) act = ACTIONS.firstOrNull { act.length > 3 && (act.contains(it) || it.contains(act)) } ?: act
                    applyDecision(v, act, p.target, p.say, p.thought)
                } else if (v != null) {
                    v.waiting = false
                }
            } catch (e: Exception) {
                failCount++
                brainStatus("model answered oddly — autopilot")
                v?.waiting = false
                logProblem("parse", "Village model reply not understood: ${e.message}")
            }
        }
    }

    private fun schedule() {
        val b = brainProvider()
        val configured = b != null
        if (b == null) brain = "autopilot — set your local model in Settings"
        val idle = villagers.filter { it.phase == "idle" && it.idleFor >= 0.25f }.sortedByDescending { it.idleFor }
        for (v in idle) {
            if (isChild(v)) {
                val (a, t) = ruleDecision(v)
                applyDecision(v, a, t, "", "")
                continue
            }
            if (v.waiting) {
                if (v.idleFor > 25f) {
                    val (a, t) = ruleDecision(v)
                    applyDecision(v, a, t, "", "")
                }
                continue
            }
            if (configured && time >= retryAt) {
                if (!inFlight && time >= nextAskAt) send(v)
                else if (v.idleFor > 8f) {
                    val (a, t) = ruleDecision(v)
                    applyDecision(v, a, t, "", "")
                }
            } else {
                val (a, t) = ruleDecision(v)
                applyDecision(v, a, t, "", "")
            }
        }
    }

    // ---- kingdoms, world ages and god powers ----

    fun ageInfo(): WorldAgeInfo = WORLD_AGES[age.coerceIn(0, WORLD_AGES.size - 1)]

    fun shake(): Float = if (time < shakeUntil) ((shakeUntil - time) / 1.5).toFloat().coerceIn(0f, 1f) else 0f

    private fun relKey(a: Int, b: Int) = "${min(a, b)}-${max(a, b)}"

    fun relOf(a: Int, b: Int): String = rels[relKey(a, b)]?.kind ?: "peace"

    fun wars(): List<Pair<Kingdom, Kingdom>> {
        val out = ArrayList<Pair<Kingdom, Kingdom>>()
        for (r in rels.values) {
            if (r.kind != "war") continue
            val a = kingdoms.firstOrNull { it.id == r.a } ?: continue
            val b = kingdoms.firstOrNull { it.id == r.b } ?: continue
            out.add(Pair(a, b))
        }
        return out
    }

    fun kx(k: Kingdom): Float =
        (if (k.id == 0) (buildings.firstOrNull { it.type == "campfire" }?.x ?: k.cx) else k.cx) + 0.5f

    fun ky(k: Kingdom): Float =
        (if (k.id == 0) (buildings.firstOrNull { it.type == "campfire" }?.y ?: k.cy) else k.cy) + 0.5f

    fun homeCount(k: Kingdom): Int = (2 + k.pop / 4).coerceIn(2, max(2, k.homes.size))

    private fun strength(k: Kingdom): Float =
        if (k.id == 0) adults() * 1.5f + count("hut") + age * 3f + toolBonus() * 2f + 4f
        else k.pop * 1.5f + k.might

    private fun setupWorld() {
        val nb = noise(Random(seed * 17 + 3), 4)
        for (i in 0 until GW * GH) {
            val x = i % GW
            val y = i / GW
            val d = hypot((x - GW / 2).toFloat(), (y - GH / 2).toFloat())
            biome[i] = when {
                d < 9f -> 0
                nb[i] > 0.80f -> 1
                nb[i] < 0.13f -> 2
                else -> 0
            }
        }
        val r = Random(seed * 31 + 17)
        kingdoms.clear()
        rels.clear()
        kingdoms.add(Kingdom(0, "Hearthvale", "Human", 0xFF4F9BFF, GW / 2, GH / 2))
        nextWorldEventAt = time + 60.0
        refreshBorders()
    }

    private fun refreshBorders() {
        val me = kingdoms.firstOrNull() ?: return
        me.pop = villagers.size
        me.radius = min(10f, 3.5f + kotlin.math.sqrt(villagers.size.toFloat()) * 1.2f + buildings.size * 0.12f)
        for (k in kingdoms) {
            if (k.id != 0) k.radius = if (k.fallen) 0f else min(8.5f, 3.2f + kotlin.math.sqrt(k.pop.toFloat()) * 0.9f)
        }
        for (i in 0 until GW * GH) {
            if (tiles[i] == T.WATER) {
                owner[i] = -1
                continue
            }
            val x = i % GW + 0.5f
            val y = i / GW + 0.5f
            var best = -1
            var bestV = 1f
            for (k in kingdoms) {
                if (k.fallen || k.radius <= 0f) continue
                val v = hypot(x - kx(k), y - ky(k)) / k.radius
                if (v < bestV) {
                    bestV = v
                    best = k.id
                }
            }
            owner[i] = best
        }
    }

    private fun worldNote(): String {
        val inf = ageInfo()
        val sb = StringBuilder("World age: ${inf.name} (${inf.blurb}) ")
        if (predators.isNotEmpty()) {
            val kinds = predators.groupBy { it.kind }.entries.joinToString(", ") { "${it.value.size} ${it.key}${if (it.value.size == 1) "" else "s"}" }
            sb.append("DANGER: $kinds hunting near the village. Fighters should defend; others should stay by the fire. ")
        }
        val others = kingdoms.filter { it.id != 0 && !it.fallen }
        if (others.isNotEmpty()) {
            sb.append("Neighbouring kingdoms: " + others.joinToString(", ") { "${it.name} (${it.race}s, ${relOf(0, it.id)} with you)" } + ". ")
        }
        val w = wars().firstOrNull()
        if (w != null) sb.append("${w.first.name} is at war with ${w.second.name}. ")
        return sb.toString() + "\n"
    }

    private fun worldTick(dt: Float) {
        ownerClock += dt
        if (ownerClock > 1.5f) {
            ownerClock = 0f
            refreshBorders()
        }
        fx.removeAll { time - it.born > it.life }
        dropsTick()
    }

    /** What the beasts leave behind lies on the ground until somebody hauls it home. */
    private fun dropsTick() {
        val iter = drops.iterator()
        while (iter.hasNext()) {
            val d = iter.next()
            val workers = villagers.filter { !isChild(it) && it.phase != "sleeping" }
            val taker = workers.minByOrNull { hypot(it.x - d.x, it.y - d.y) }
            val near = taker != null && hypot(taker.x - d.x, taker.y - d.y) < 1.3f
            if (!near && d.by < 0 && time - d.born > 1.5 && taker != null) {
                val idle = workers.filter { (it.phase == "idle") && hypot(it.x - d.x, it.y - d.y) < 14f }
                    .minByOrNull { hypot(it.x - d.x, it.y - d.y) }
                if (idle != null) {
                    val cell = d.y.toInt().coerceIn(0, GH - 1) * GW + d.x.toInt().coerceIn(0, GW - 1)
                    if (go(idle, "wander", cell)) {
                        d.by = idle.id
                        idle.say = "I'll fetch it."
                        idle.sayLeft = 2f
                    }
                }
            }
            if (near || time - d.born > 30.0) {
                food += d.food
                hides += d.hides
                taker?.let {
                    it.gathered++
                    it.happiness = min(100f, it.happiness + 5f)
                    it.remember("hauled home a ${d.kind}'s meat and hides")
                }
                addLog("The ${d.kind} leaves behind meat and hides (+${d.food} food, +${d.hides} hides).")
                iter.remove()
            }
        }
    }

    // ---- predators: wolves and bears hunt anyone caught away from the fire ----

    /** A grassy shore tile with water beside it, far enough from the fire; returns x, y, and the direction to the water. */
    private fun shoreSpot(): FloatArray? {
        val fire = buildings.firstOrNull { it.type == "campfire" } ?: buildings.firstOrNull { it.type == "hall" }
        val cx = fire?.x?.toFloat() ?: (GW / 2f)
        val cy = fire?.y?.toFloat() ?: (GH / 2f)
        val opts = ArrayList<FloatArray>()
        for (y in 2 until GH - 2) for (x in 2 until GW - 2) {
            if (tiles[y * GW + x] != T.GRASS) continue
            var dx = 0
            var dy = 0
            if (tiles[y * GW + x + 1] == T.WATER) dx = 1
            else if (tiles[y * GW + x - 1] == T.WATER) dx = -1
            else if (tiles[(y + 1) * GW + x] == T.WATER) dy = 1
            else if (tiles[(y - 1) * GW + x] == T.WATER) dy = -1
            else continue
            val d = hypot(x - cx, y - cy)
            if (d < 7f || d > 40f) continue
            opts.add(floatArrayOf(x + 0.5f, y + 0.5f, dx.toFloat(), dy.toFloat(), d))
        }
        if (opts.isEmpty()) return null
        opts.sortBy { it[4] }
        return opts[rnd.nextInt(max(1, min(opts.size, 12)))]
    }

    /** A longship noses up to the shore and its crew of reavers storms out. */
    private fun landReavers(k: Int) {
        val s = shoreSpot() ?: run { spawnPredators("raider", k); return }
        shipUp = true
        shipX = s[0] + s[2] * 1.7f
        shipY = s[1] + s[3] * 1.7f
        shipDx = if (s[2] != 0f) s[2] else 1f
        shipDy = s[3]
        shipT = 0f
        foeStats["reaver"] = (foeStats["reaver"] ?: 0) + k
        repeat(k) {
            predators.add(Predator("reaver", s[0] + (rnd.nextFloat() - 0.5f) * 0.6f, s[1] + (rnd.nextFloat() - 0.5f) * 0.6f).also { it.angle = rnd.nextFloat() * 6.28f })
        }
        fx.add(Fx("spawn", s[0], s[1], time, 1.4f))
        event("A longship with a dragon prow rows out of the mist! $k sea reavers leap ashore, hungry for fish and plunder. Burn the ship to be rid of them!")
    }

    /** The longship stays moored while the crew is ashore, then burns or sails away. */
    private fun shipTick(dt: Float) {
        if (!shipUp) return
        if (predators.any { it.kind == "reaver" }) return
        if (shipT > 0f) {
            shipT -= dt
            if (shipT <= 0f) shipUp = false
        } else {
            shipUp = false
            event("The reavers' longship pushes off and vanishes into the mist.")
        }
    }

    private fun spawnPredators(kind: String, n: Int) {
        val fire = buildings.firstOrNull { it.type == "campfire" }
        if (isThreat(kind)) foeStats[kind] = (foeStats[kind] ?: 0) + n
        repeat(n) {
            for (attempt in 0 until 12) {
                val edgeX = if (rnd.nextBoolean()) GW / 2f - OLD_GW / 2f + 1.5f else GW / 2f + OLD_GW / 2f - 2.5f
                val y = GH / 2f - OLD_GH / 2f + 2f + rnd.nextFloat() * (OLD_GH - 4f)
                if (tiles[y.toInt() * GW + edgeX.toInt()] == T.WATER) continue
                if (fire != null && hypot(edgeX - fire.x, y - fire.y) < 9f) continue
                predators.add(Predator(kind, edgeX, y).also { it.angle = rnd.nextFloat() * 6.28f })
                if (kind == "wraith" || kind == "drake" || kind == "golem" || kind == "warlord" || kind == "lich" || kind == "troll") fx.add(Fx("spawn", edgeX, y, time, 1.4f))
                break
            }
        }
    }

    private fun stepPredator(p: Predator, dx: Float, dy: Float): Boolean {
        val nx = (p.x + dx).coerceIn(0.5f, GW - 0.6f)
        val ny = (p.y + dy).coerceIn(0.5f, GH - 0.6f)
        if (p.kind != "harpy" && tiles[ny.toInt() * GW + nx.toInt()] == T.WATER) {
            p.angle += 1.7f
            return false
        }
        p.x = nx
        p.y = ny
        return true
    }

    private fun isFighter(v: Villager) = !isChild(v) && v.hp > 35f &&
        (age >= 2 || v.job in setOf("Hunter", "Scout", "Builder", "Woodcutter", "Miner", "Bronzesmith", "Blacksmith"))

    private var nextTowerAt = 0.0

    /** When danger looms, the villagers raise a watchtower on their own. Towers shoot at nearby predators. */
    private fun defenceTick() {
        if (age < 1 || time < nextTowerAt || villagers.size < 3) return
        val danger = predators.isNotEmpty() || age >= 3
        if (!danger) return
        nextTowerAt = time + 90.0
        val have = count("tower")
        if (have >= 1 && count("ballista") < capFor("ballista") && wood >= 14 && stone >= 6 && metal >= 3) {
            val bs = findSite()
            if (bs >= 0) {
                wood -= 14
                stone -= 6
                metal -= 3
                addBuilding("ballista", bs % GW, bs / GW)
                event("A great ballista is wound up at the edge of the village. Its bolts reach far into the woods.")
                mark("A ballista was built.")
                return
            }
        }
        if (have >= 3 || wood < 8 || stone < 2) return
        val site = findSite()
        if (site < 0) return
        wood -= 8
        stone -= 2
        addBuilding("tower", site % GW, site / GW)
        val builder = villagers.firstOrNull { it.job == "Builder" } ?: villagers[rnd.nextInt(villagers.size)]
        builder.remember("helped raise a watchtower")
        event("The villagers raise a watchtower at the edge of the village. Archers will watch the woods.")
        mark("A watchtower was built.")
    }

    private var nextWallAt = 0.0

    /** Once a watchtower stands and danger lingers, villagers put up palisade walls around the village. */
    private fun wallTick() {
        if (time < nextWallAt || count("tower") < 1 || count("palisade") >= 8) return
        if (predators.isEmpty() && age < 3) return
        nextWallAt = time + 45.0
        if (wood < 6) return
        val site = findSite()
        if (site < 0) return
        wood -= 4
        addBuilding("palisade", site % GW, site / GW)
        if (count("palisade") == 1) event("The villagers begin a wooden palisade to keep the beasts out.")
        mark("A palisade wall was built.")
    }


    // ---- the stone town wall ----


    /** Lists the ring of cells around the hall; gate posts are stored as cell + GW*GH, the gaps between them stay open. */
    private fun planWall() {
        if (wallCx < 0) {
            val hall = buildings.firstOrNull { it.type == "hall" }
            if (hall != null) { wallCx = hall.x; wallCy = hall.y }
            else if (buildings.isNotEmpty()) { wallCx = buildings.map { it.x }.average().toInt(); wallCy = buildings.map { it.y }.average().toInt() }
            else return
        }
        val cx = wallCx
        val cy = wallCy
        val rx = 14
        val ry = 9
        val cells = ArrayList<Pair<Double, Int>>()
        for (x in cx - rx..cx + rx) for (y in cy - ry..cy + ry) {
            if (x < 1 || y < 1 || x >= GW - 1 || y >= GH - 1) continue
            val horiz = y == cy - ry || y == cy + ry
            val vert = x == cx - rx || x == cx + rx
            if (!horiz && !vert) continue
            val dx = x - cx
            val dy = y - cy
            if ((horiz && abs(dx) <= 1) || (vert && abs(dy) <= 1)) continue
            val post = (horiz && abs(dx) == 2) || (vert && abs(dy) == 2)
            val i = y * GW + x
            if (occupied[i] || i in reserved) continue
            if (tiles[i] != T.GRASS && tiles[i] != T.BUSH) continue
            cells.add(Math.atan2(dy.toDouble(), dx.toDouble()) to (if (post) i + GW * GH else i))
        }
        cells.sortBy { it.first }
        for (c in cells) wallPlan.add(c.second)
    }

    /** Masons raise the stone wall one piece at a time from surplus stone, ringed round the village with gates on every side. */
    private fun stoneWallTick() {
        if (time < nextStoneWallAt || wallDone || age < 2 || villagers.size < 8) return
        nextStoneWallAt = time + 6.0
        if (stone < 40 || wood < 6) return
        if (wallPlan.isEmpty()) {
            planWall()
            if (wallPlan.isEmpty()) { wallDone = true; return }
        }
        while (wallPlan.isNotEmpty()) {
            val c = wallPlan[0] % (GW * GH)
            if (occupied[c] || (tiles[c] != T.GRASS && tiles[c] != T.BUSH)) wallPlan.removeAt(0) else break
        }
        if (wallPlan.isEmpty()) {
            wallDone = true
            if (wallsRaised >= 40) event("The stone wall is complete: gates stand open on every side.")
            return
        }
        val raw = wallPlan.removeAt(0)
        val post = raw >= GW * GH
        val c = raw % (GW * GH)
        stone -= 3
        wood -= 1
        if (tiles[c] == T.BUSH) { tiles[c] = T.GRASS; amt[c] = 0 }
        addBuilding(if (post) "gate" else "wall", c % GW, c / GW)
        wallsRaised++
        if (wallsRaised == 1) event("Masons begin a stone wall around the village, with gates on every side.")
        if (wallsRaised % 25 == 0) mark("The stone wall grows: $wallsRaised pieces.")
    }

    /** Foes that reach the wall are pelted from the ramparts and slow a little as they climb (flyers and wraiths ignore it). */
    private fun wallSlow(p: Predator, dt: Float): Float {
        if (wallsRaised == 0 || p.kind == "hornet" || p.kind == "wraith" || p.kind == "harpy" || (p.kind == "burrower" && p.aux > 0f)) return 1f
        val fx = p.x.toInt()
        val fy = p.y.toInt()
        for (yy in fy - 1..fy + 1) for (xx in fx - 1..fx + 1) {
            if (xx in 0 until GW && yy in 0 until GH && wallAt[yy * GW + xx]) {
                wallHoldSecs += dt
                p.hp -= 5f * dt
                return 0.75f
            }
        }
        return 1f
    }

    private fun predatorTick(dt: Float) {
        defenceTick()
        wallTick()
        stoneWallTick()
        shipTick(dt)
        if (predators.isEmpty()) return
        val fires = buildings.filter { it.type == "campfire" || it.type == "tower" || it.type == "hut" }
        val towers = buildings.filter { it.type == "tower" }
        // ballistae: slow, heavy bolts at the nearest foe within range
        for (bl in buildings) {
            if (bl.type != "ballista") continue
            if (bl.timer > 0f) { bl.timer -= dt; continue }
            val tgt = predators.filter { !(it.kind == "burrower" && it.aux > 0f) && hypot(bl.x + 0.5f - it.x, bl.y + 0.5f - it.y) < 11f }.minByOrNull { hypot(bl.x + 0.5f - it.x, bl.y + 0.5f - it.y) } ?: continue
            tgt.hp -= (if (hasTech("siege")) 28f else 22f) * (if (tgt.kind == "golem") 0.5f else 1f)
            tgt.byBallista = true
            bl.timer = (2.8f + rnd.nextFloat()) * (if (hasTech("siege")) 0.7f else 1f)
            ballistaShots++
            fx.add(Fx("ballista", tgt.x, tgt.y, time, 0.6f))
        }
        for (p in predators.toList()) {
            p.life += dt
            if (p.cool > 0f) p.cool -= dt
            var prey: Villager? = null
            var best = 15f
            for (v in villagers) {
                val safe = fires.any { hypot(it.x + 0.5f - v.x, it.y + 0.5f - v.y) < (if (p.kind == "ogre" || p.kind == "drake" || p.kind == "golem" || p.kind == "warlord" || p.kind == "lich" || p.kind == "troll") 1.6f else 3.2f) }
                if (safe) continue
                val d = hypot(v.x - p.x, v.y - p.y)
                if (d < best) {
                    best = d
                    prey = v
                }
            }
            val speed = (when (p.kind) { "bear" -> 1.5f; "boar" -> 2.4f; "raider" -> 2.0f; "ogre" -> 1.3f; "wraith" -> 2.7f; "drake" -> 1.8f; "shaman" -> 1.7f; "warlord" -> 1.7f; "golem" -> 0.9f; "frostwolf" -> 2.9f; "lich" -> 1.3f; "skeleton" -> 1.7f; "pilferer" -> 3.1f; "hornet" -> 3.0f; "reaver" -> 2.2f; "troll" -> 1.25f; "spider" -> 2.3f; "burrower" -> (if (p.aux > 0f) 3.6f else 1.4f); "harpy" -> (if (p.aux > 0f) 2.6f else 4.6f); else -> 2.1f }) * dt * (if (p.buffUntil > p.life) 1.5f else 1f) * wallSlow(p, dt)
            var gone = false
            when (p.kind) {
                "pilferer" -> {
                    // a thief: dash to the campfire, grab food and wood, then bolt for the map edge
                    val hub = buildings.firstOrNull { it.type == "campfire" } ?: buildings.firstOrNull { it.type == "hall" } ?: buildings.firstOrNull { it.type == "hut" }
                    if (!p.looted && hub != null) {
                        val hx = hub.x + 0.5f
                        val hy = hub.y + 0.5f
                        val hd = max(0.05f, hypot(hx - p.x, hy - p.y))
                        if (hd > 1.2f) stepPredator(p, (hx - p.x) / hd * speed, (hy - p.y) / hd * speed)
                        else {
                            p.looted = true
                            p.lootF = min(food, 14)
                            p.lootW = min(wood, 8)
                            food -= p.lootF
                            wood -= p.lootW
                            fx.add(Fx("arrow", p.x, p.y, time, 0.5f))
                            addLog("The pilferer snatches ${p.lootF} food and ${p.lootW} wood and bolts for the hills!")
                            val victim = villagers.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
                            if (victim != null && hypot(victim.x - p.x, victim.y - p.y) < 6f) { victim.say = "Thief!"; victim.sayLeft = 2.5f }
                        }
                    } else {
                        val tx = if (p.x < GW / 2f) GW / 2f - OLD_GW / 2f - 1f else GW / 2f + OLD_GW / 2f + 1f
                        val td = max(0.1f, abs(tx - p.x))
                        val fleeStep = (tx - p.x) / td * speed * (if (p.looted) 1.2f else 1f)
                        val moved = stepPredator(p, fleeStep, (GH / 2f - p.y) * 0.02f * dt)
                        if (!moved) {
                            // a lake or river in the way: slide along the shore until the way is clear (thieves used to stall here forever)
                            val side = if (p.y < GH / 2f) 1f else -1f
                            if (!stepPredator(p, 0f, side * abs(fleeStep))) stepPredator(p, 0f, -side * abs(fleeStep))
                        }
                        // a thief that dawdles too long melts into the woods rather than haunting the village
                        val gaveUp = p.life > (if (p.looted) 150f else 260f) && fires.none { hypot(it.x + 0.5f - p.x, it.y + 0.5f - p.y) < 6f }
                        if (abs(p.x - tx) < 1.2f || gaveUp) {
                            predators.remove(p)
                            gone = true
                            if (p.looted && p.lootF + p.lootW > 0) {
                                pilfersLost++
                                event("The pilferer escapes into the woods with ${p.lootF} food and ${p.lootW} wood.")
                            }
                        }
                    }
                }
                "hornet" -> {
                    // erratic buzzing; the swarm stings whoever it reaches
                    stepPredator(p, kotlin.math.sin(p.life * 9f + p.angle * 3f) * speed * 0.7f, kotlin.math.cos(p.life * 11f + p.angle) * speed * 0.7f)
                }
                "lich" -> {
                    // the lich raises skeletons from the ground and curses from afar
                    p.aux -= dt
                    if (p.aux <= 0f) {
                        p.aux = 9f
                        if (predators.count { it.kind == "skeleton" } < 4) {
                            val sx = (p.x + (rnd.nextFloat() - 0.5f) * 2.4f).coerceIn(1f, GW - 1.5f)
                            val sy = (p.y + (rnd.nextFloat() - 0.5f) * 2.4f).coerceIn(1f, GH - 1.5f)
                            if (tiles[sy.toInt() * GW + sx.toInt()] != T.WATER) {
                                predators.add(Predator("skeleton", sx, sy))
                                fx.add(Fx("spawn", sx, sy, time, 1.0f))
                                bonesRaised++
                                foeStats["skeleton"] = (foeStats["skeleton"] ?: 0) + 1
                                if (bonesRaised <= 2 || rnd.nextInt(4) == 0) addLog("The lich raises a skeleton from the earth!")
                            }
                        }
                    }
                    if (prey != null && p.cool <= 0f && best < 5.5f && best > 1.5f) {
                        prey.hp -= 9f
                        prey.harmedBy = "lich"; prey.harmedAt = time
                        prey.happiness = max(0f, prey.happiness - 7f)
                        prey.say = "Cold bones!"
                        prey.sayLeft = 2.2f
                        p.cool = 2.8f
                        fx.add(Fx("arrow", prey.x, prey.y, time, 0.7f))
                    }
                }
                "shaman" -> {
                    // the shaman keeps its distance, curses villagers at range and mends the raiders around it
                    p.aux -= dt
                    if (p.aux <= 0f) {
                        p.aux = 3.2f
                        var healed = 0
                        for (o in predators) {
                            if (o !== p && (o.kind == "raider" || o.kind == "warlord") && hypot(o.x - p.x, o.y - p.y) < 5f && o.hp < predatorMaxHp(o.kind)) {
                                o.hp = min(predatorMaxHp(o.kind), o.hp + 14f)
                                healed++
                            }
                        }
                        if (healed > 0) fx.add(Fx("bless", p.x, p.y, time, 1.0f))
                    }
                    if (prey != null && p.cool <= 0f && best < 5.5f && best > 1.5f) {
                        prey.hp -= 7f
                        prey.harmedBy = "shaman"; prey.harmedAt = time
                        prey.happiness = max(0f, prey.happiness - 6f)
                        prey.say = "A curse!"
                        prey.sayLeft = 2.2f
                        p.cool = 2.4f
                        fx.add(Fx("arrow", prey.x, prey.y, time, 0.7f))
                    }
                }
                "spider" -> {
                    // the spider trails sticky webs behind it as it prowls
                    p.aux -= dt
                    if (p.aux <= 0f) {
                        p.aux = 2.0f + rnd.nextFloat()
                        if (webs.size >= 16) webs.removeAt(0)
                        val wx = if (prey != null && best < 5f) (p.x + prey.x) / 2f else p.x
                        val wy = if (prey != null && best < 5f) (p.y + prey.y) / 2f else p.y
                        webs.add(floatArrayOf(wx, wy, time.toFloat()))
                        websSpun++
                    }
                }
                "harpy" -> {
                    // harpies dive on the nearest villager, then wheel overhead for a few seconds before swooping again
                    if (p.aux > 0f) p.aux -= dt
                }
                "troll" -> {
                    // a night troll mends itself unless it was struck in the last few seconds, hurls boulders, and turns to stone at dawn
                    if (p.hp < p.aux - 0.01f) p.hurtAt = p.life
                    else if (p.life - p.hurtAt > 4f && p.hp < predatorMaxHp("troll")) p.hp = min(predatorMaxHp("troll"), p.hp + 3.5f * dt)
                    p.aux = p.hp
                    if (prey != null && p.cool <= 0f && best < 6.5f && best > 2.2f) {
                        prey.hp -= 12f
                        prey.harmedBy = "troll"; prey.harmedAt = time
                        prey.happiness = max(0f, prey.happiness - 6f)
                        prey.say = "A boulder!"
                        prey.sayLeft = 2.2f
                        p.cool = 3.2f
                        trollBoulders++
                        fx.add(Fx("boulder", prey.x, prey.y, time, 0.8f))
                        for (o in villagers) if (o !== prey && hypot(o.x - prey.x, o.y - prey.y) < 1.5f) { o.hp -= 5f; o.harmedBy = "troll"; o.harmedAt = time }
                    }
                    val fr = frac()
                    if (fr > 0.05 && fr < 0.45) {
                        predators.remove(p)
                        gone = true
                        trollsStoned++
                        if (trollRocks.size >= 6) trollRocks.removeAt(0)
                        trollRocks.add(floatArrayOf(p.x, p.y))
                        stone += 8
                        fx.add(Fx("quake", p.x, p.y, time, 1.4f))
                        event("The first light of dawn catches the night troll. It freezes and turns to stone! (+8 stone from the rubble)")
                        mark("A night troll was turned to stone by the dawn.")
                    }
                }
                "burrower" -> {
                    // a giant worm tunnels under the ground (unhurtable, mending, fast), erupts beneath its prey, then rears up and fights
                    if (p.aux > 0f) {
                        p.aux -= dt
                        p.hp = min(predatorMaxHp("burrower"), p.hp + 3f * dt)
                        p.cool = max(p.cool, 0.6f)
                        if (prey != null && best < 1.3f) p.aux = 0f
                        if (p.aux <= 0f) {
                            p.aux = 0f
                            p.phase = p.life + 6.5f
                            p.cool = 0.8f
                            wormErupts++
                            var hit = 0
                            for (o in villagers) if (hypot(o.x - p.x, o.y - p.y) < 2.3f) {
                                o.hp -= 14f * (if (isChild(o)) 1.5f else 1f)
                                o.harmedBy = "burrower"; o.harmedAt = time
                                o.happiness = max(0f, o.happiness - 10f)
                                o.say = "The ground!"; o.sayLeft = 2.2f
                                hit++
                            }
                            wormHits += hit
                            if (craters.size >= 8) craters.removeAt(0)
                            craters.add(floatArrayOf(p.x, p.y, time.toFloat()))
                            fx.add(Fx("quake", p.x, p.y, time, 1.3f))
                            if (wormErupts <= 2 || hit > 0 || rnd.nextInt(5) == 0) addLog("The burrowing worm bursts out of the ground" + (if (hit > 0) ", flinging $hit villager" + (if (hit > 1) "s" else "") + " aside!" else "!"))
                        }
                    } else if (p.life > p.phase || (p.hp < predatorMaxHp("burrower") * 0.4f && p.phase - p.life < 5f)) {
                        p.aux = 5f + rnd.nextFloat() * 3f
                        p.phase = 0f
                        fx.add(Fx("quake", p.x, p.y, time, 0.8f))
                        if (p.hp < predatorMaxHp("burrower") * 0.4f && rnd.nextInt(2) == 0) addLog("Wounded, the worm dives underground to mend!")
                    }
                }
                "warlord" -> {
                    // a war cry sends every raider into a faster charge
                    p.aux -= dt
                    if (p.aux <= 0f) {
                        p.aux = 14f
                        for (o in predators) if (o.kind == "raider" || o.kind == "shaman") o.buffUntil = o.life + 6f
                        fx.add(Fx("quake", p.x, p.y, time, 1.2f))
                        if (rnd.nextInt(2) == 0) addLog("The warlord bellows a war cry and the raiders surge forward!")
                    }
                }
            }
            if (gone) continue
            if (p.kind == "pilferer") prey = null
            if (p.life > (if (p.kind == "ogre" || p.kind == "drake" || p.kind == "golem" || p.kind == "warlord" || p.kind == "lich" || p.kind == "troll" || p.kind == "burrower") 320f else 150f) && prey == null && p.hp > 0f) {
                // the hunt is over: slip away toward the nearest map edge
                val tx = if (p.x < GW / 2f) GW / 2f - OLD_GW / 2f - 1f else GW / 2f + OLD_GW / 2f + 1f
                val d = max(0.1f, abs(tx - p.x))
                val outStep = (tx - p.x) / d * speed
                if (!stepPredator(p, outStep, 0f)) {
                    // water in the way: slide along the shore (foes used to freeze on a lake edge and haunt the village for hours)
                    val side = if (p.y < GH / 2f) 1f else -1f
                    if (!stepPredator(p, 0f, side * abs(outStep))) stepPredator(p, 0f, -side * abs(outStep))
                }
                if (abs(p.x - tx) < 1.2f || p.life > 480f) {
                    predators.remove(p)
                    if (predators.isEmpty()) event("The ${p.kind}s slink back into the woods. The village breathes again.")
                }
                continue
            }
            if (prey != null) {
                val dx = prey.x - p.x
                val dy = prey.y - p.y
                val d = max(0.05f, hypot(dx, dy))
                if (p.kind == "harpy" && p.aux > 0f) {
                    // wheel round the target on a ring about 3 tiles out
                    val ring = (d - 3.2f).coerceIn(-1f, 1f) * 0.6f
                    stepPredator(p, (-dy / d * 0.8f + dx / d * ring) * speed, (dx / d * 0.8f + dy / d * ring) * speed)
                } else if ((p.kind == "shaman" || p.kind == "lich") && d < 4.2f) stepPredator(p, -dx / d * speed * 0.6f, -dy / d * speed * 0.6f)
                else if (d > 0.8f && !((p.kind == "shaman" || p.kind == "lich") && d < 5f)) stepPredator(p, dx / d * speed, dy / d * speed)
                else if (p.kind != "shaman" && p.kind != "lich" && p.cool <= 0f) {
                    val dmg = (when (p.kind) { "bear" -> 22f; "boar" -> 16f; "raider" -> 9f; "ogre" -> 24f; "wraith" -> 11f; "drake" -> 28f; "warlord" -> 18f; "golem" -> 30f; "frostwolf" -> 8f; "skeleton" -> 10f; "hornet" -> 4f; "reaver" -> 14f; "troll" -> 20f; "spider" -> 7f; "harpy" -> 5f; "burrower" -> 15f; else -> 12f }) * (if (isChild(prey)) 1.5f else 1f)
                    if (p.kind == "harpy") {
                        // a dive: talons rake the target and snatch a little food, then the harpy climbs away to circle
                        val f = min(food, 3)
                        food -= f
                        p.lootF += f
                        harpyLoot += f
                        harpyDives++
                        p.aux = 3.2f + rnd.nextFloat() * 2f
                        prey.say = "Get off, you bird!"; prey.sayLeft = 2.2f
                        fx.add(Fx("arrow", p.x, p.y, time, 0.5f))
                        if (harpyDives <= 2 || rnd.nextInt(8) == 0) addLog("A harpy dives at ${prey.name}" + (if (f > 0) " and snatches $f food!" else "!"))
                    }
                    if (p.kind == "raider" || p.kind == "reaver") {
                        // raiders grab whatever they can carry as they strike; reavers carry off the fish
                        val f = min(food, if (p.kind == "reaver") 5 else 3)
                        val w = min(wood, 2)
                        food -= f
                        wood -= w
                        if (f + w > 0 && rnd.nextInt(3) == 0) addLog("A ${p.kind} snatches supplies from ${prey.name}!")
                    }
                    // a cornered adult fights back (kids and the weak cannot)
                    if (!isChild(prey) && prey.hp > 25f && p.kind in HUNTABLE_FOES && p.kind != "shaman" && p.kind != "reaver") {
                        p.hp -= 9f + 3f * toolBonus()
                        selfDefence++
                        fx.add(Fx("clash", p.x, p.y, time, 0.8f))
                    }
                    prey.hp -= dmg
                    prey.harmedBy = p.kind; prey.harmedAt = time
                    prey.happiness = max(0f, prey.happiness - 12f)
                    prey.say = "Aaah!"
                    prey.sayLeft = 2.5f
                    p.cool = if (p.kind == "frostwolf") 0.9f else if (p.kind == "hornet") 0.8f else 1.4f
                    if (p.kind == "hornet") { prey.say = "Ow, stings!"; prey.sayLeft = 2f }
                    if (p.kind == "spider") {
                        spiderBites++
                        poisoned[prey.id] = time + 9.0
                        prey.say = "It bit me! My leg burns!"; prey.sayLeft = 2.5f
                        fx.add(Fx("venom", prey.x, prey.y, time, 1.0f))
                    }
                    if (p.kind == "frostwolf") prey.happiness = max(0f, prey.happiness - 3f)
                    fx.add(Fx("clash", prey.x, prey.y, time, 1.2f))
                    if (p.kind == "golem") {
                        // the stone slam shakes everyone close by
                        p.cool = 2.2f
                        fx.add(Fx("quake", p.x, p.y, time, 1.0f))
                        for (o in villagers) if (o !== prey && hypot(o.x - p.x, o.y - p.y) < 2.2f) {
                            o.hp -= dmg * 0.4f
                            o.harmedBy = "golem"; o.harmedAt = time
                        }
                    }
                }
            } else if (p.kind != "pilferer") {
                if (rnd.nextInt(60) == 0) p.angle += (rnd.nextFloat() - 0.5f) * 2.4f
                stepPredator(p, kotlin.math.cos(p.angle) * speed * 0.5f, kotlin.math.sin(p.angle) * speed * 0.5f)
            }
            val hp0 = p.hp
            for (tw in towers) {
                if (hypot(tw.x + 0.5f - p.x, tw.y + 0.5f - p.y) < 6.5f) {
                    p.hp -= 6f * dt * (if (p.kind == "golem") 0.3f else 1f)
                    if (rnd.nextInt(45) == 0) fx.add(Fx("shot", p.x, p.y, time, 0.5f))
                }
            }
            val critterPower = critters.fightPower(p.x, p.y)
            if (critterPower > 0f) {
                p.hp -= critterPower * dt
                if (rnd.nextInt(40) == 0) fx.add(Fx("clash", p.x, p.y, time, 0.8f))
            }
            val fighters = villagers.filter { isFighter(it) && hypot(it.x - p.x, it.y - p.y) < 2.6f }
            if (fighters.isNotEmpty()) {
                p.hp -= fighters.size * (6f + 3f * toolBonus() + (if (hasTech("blades")) 2.5f else 0f)) * (if (p.kind == "wraith") 0.5f else if (p.kind == "golem") 0.6f else 1f) * dt
                if (rnd.nextInt(40) == 0) fx.add(Fx("clash", p.x, p.y, time, 0.8f))
            }
            if (p.kind == "burrower" && p.aux > 0f && p.hp < hp0) p.hp = hp0
            if (p.hp <= 0f) {
                predators.remove(p)
                if (p.byBallista) ballistaKills++
                val meat = when (p.kind) { "bear" -> 8; "boar" -> 6; "ogre" -> 12; "drake" -> 14; "frostwolf" -> 3; "lich", "skeleton", "raider", "wraith", "shaman", "warlord", "golem", "pilferer", "hornet", "troll", "spider", "harpy", "burrower" -> 0; else -> 4 }
                val hide = when (p.kind) { "bear" -> 3; "boar" -> 2; "ogre" -> 5; "drake" -> 8; "frostwolf" -> 3; "lich", "skeleton", "raider", "wraith", "shaman", "warlord", "golem", "pilferer", "hornet", "troll", "harpy", "burrower" -> 0; "spider" -> 2; else -> 1 }
                if (meat + hide > 0) drops.add(Drop(p.kind, p.x, p.y, time, meat, hide))
                onThreatDown(p)
                val hero = fighters.maxByOrNull { it.hp }
                for (f in fighters) { f.bump("slain"); checkTitles(f) }
                if (hero != null) {
                    hero.happiness = min(100f, hero.happiness + 15f)
                    hero.remember("helped kill a ${p.kind}")
                    event(if (isThreat(p.kind)) "${hero.name} and the others bring down the ${p.kind}!" else "${hero.name} and the others bring down the ${p.kind}! It leaves meat and hides.")
                    mark("${hero.name} helped slay a ${p.kind}.")
                } else {
                    event(if (isThreat(p.kind)) "The ${p.kind} falls." else "The ${p.kind} falls and leaves meat and hides.")
                }
            }
        }
        // everyone who is not a fighter runs for the fire when a predator gets close
        for (v in villagers) {
            if (v.phase == "sleeping") continue
            if (isFighter(v) && v.hp > 50f) continue
            val near = predators.any { hypot(it.x - v.x, it.y - v.y) < 4.5f }
            if (!near || time < (fleeAt[v.id] ?: 0.0)) continue
            fleeAt[v.id] = time + 4.0
            if (startAction(v, "huddle", "")) {
                v.say = "Run!"
                v.sayLeft = 2f
            }
        }
    }

    private fun onThreatDown(p: Predator) {
        when (p.kind) {
            "raider" -> {
                goods += 2
                if (age >= 2) metal += 1
                if (predators.none { it.kind == "raider" }) {
                    raidsRepelled++
                    event("The raiders are beaten back! Their stolen loot is recovered. (bands defeated: $raidsRepelled)")
                    mark("The village drove off a raider band.")
                    for (v in villagers) v.happiness = min(100f, v.happiness + 8f)
                }
            }
            "reaver" -> {
                reaversSlain++
                goods += 1
                metal += 1
                food += 2
                if (predators.none { it.kind == "reaver" } && shipUp) {
                    shipsSeized++
                    shipT = 7f
                    wood += 16
                    metal += 6
                    goods += 3
                    hides += 4
                    event("The last reaver falls! The villagers seize the longship and set it ablaze. (+16 wood, +6 metal, +3 goods, +4 hides)")
                    mark("A reaver longship was burned.")
                    for (v in villagers) v.happiness = min(100f, v.happiness + 12f)
                    fx.add(Fx("quake", shipX, shipY, time, 1.4f))
                }
            }
            "harpy" -> {
                harpiesSlain++
                goods += 1
                if (p.lootF > 0) { food += p.lootF; harpyLoot -= p.lootF }
                fx.add(Fx("bless", p.x, p.y, time, 0.8f))
                if (predators.none { it.kind == "harpy" }) {
                    flocksScattered++
                    goods += 3
                    event("The last harpy tumbles from the sky and the flock scatters! Feathers and stolen food are gathered up. (+4 goods)")
                    mark("A flock of rift harpies was driven off.")
                    for (v in villagers) v.happiness = min(100f, v.happiness + 6f)
                }
            }
            "burrower" -> {
                wormsSlain++
                goods += 2
                metal += 4
                stone += 6
                craters.clear()
                event("The burrowing worm thrashes and goes still. Its armour plates and the ore it dug up are carried home. (+2 goods, +4 metal, +6 stone)")
                mark("A giant burrowing worm was slain.")
                fx.add(Fx("quake", p.x, p.y, time, 1.0f))
                for (v in villagers) v.happiness = min(100f, v.happiness + 8f)
            }
            "spider" -> {
                spidersSlain++
                goods += 3
                hides += 2
                val gone = webs.size
                webs.clear()
                event("The giant spider curls up and falls still. The villagers cut the silk free and burn the webs. (+3 goods, +2 hides)")
                mark("A giant forest spider was slain.")
                if (gone > 0) fx.add(Fx("quake", p.x, p.y, time, 0.8f))
                for (v in villagers) v.happiness = min(100f, v.happiness + 6f)
            }
            "troll" -> {
                trollsSlain++
                goods += 3
                metal += 3
                stone += 6
                event("The night troll topples with a crash! Its boulders are carried home. (+3 goods, +3 metal, +6 stone)")
                mark("A night troll was slain.")
                for (v in villagers) v.happiness = min(100f, v.happiness + 12f)
            }
            "ogre" -> {
                ogresSlain++
                event("The ogre falls with a crash that shakes the ground!")
                mark("An ogre was slain.")
                for (v in villagers) v.happiness = min(100f, v.happiness + 12f)
            }
            "wraith" -> {
                goods += 1
                if (predators.none { it.kind == "wraith" }) {
                    event("The last wraith unravels into smoke. The breach in the sky closes.")
                    mark("A rift breach was sealed.")
                }
            }
            "shaman" -> {
                shamansSlain++
                goods += 1
                event("The raider shaman collapses and its charms scatter.")
            }
            "warlord" -> {
                warlordsSlain++
                goods += 4
                metal += 4
                event("The warlord is cut down! The raiders lose heart and the stolen banners fall.")
                mark("A raider warlord was slain.")
                for (v in villagers) v.happiness = min(100f, v.happiness + 14f)
                for (o in predators) if (o.kind == "raider" || o.kind == "shaman") o.hp -= 25f
            }
            "frostwolf" -> {
                if (predators.none { it.kind == "frostwolf" }) {
                    frostPacksBroken++
                    hides += 3
                    event("The last frost wolf flees yelping into the snow. The pack is broken! (+3 hides)")
                    mark("A frost wolf pack was broken.")
                    for (v in villagers) v.happiness = min(100f, v.happiness + 8f)
                }
            }
            "skeleton" -> {
                if (rnd.nextInt(3) == 0) stone += 1
            }
            "pilferer" -> {
                if (p.looted && p.lootF + p.lootW > 0) {
                    food += p.lootF
                    wood += p.lootW
                    event("The pilferer is caught! The stolen ${p.lootF} food and ${p.lootW} wood are carried home. (+1 goods)")
                } else event("The pilferer is caught before it could take a thing!")
                goods += 1
                pilfersFoiled++
                mark("A pilferer was caught.")
                for (v in villagers) v.happiness = min(100f, v.happiness + 5f)
            }
            "hornet" -> {
                hornetsSwatted++
                if (predators.none { it.kind == "hornet" }) {
                    swarmsBroken++
                    food += 8
                    event("The last hornet drops. The swarm is broken, and the villagers cut out the honeycomb! (+8 food)")
                    mark("A hornet swarm was broken.")
                    for (v in villagers) v.happiness = min(100f, v.happiness + 6f)
                }
            }
            "lich" -> {
                lichesSlain++
                goods += 5
                metal += 5
                stone += 10
                event("The rift lich shrieks and collapses! Its skeletons fall to dust.")
                mark("A rift lich was destroyed.")
                predators.removeAll { it.kind == "skeleton" }
                for (v in villagers) v.happiness = min(100f, v.happiness + 18f)
            }
            "golem" -> {
                golemsSlain++
                stone += 14
                metal += 3
                event("The rift golem crumbles into a heap of humming stone.")
                mark("A rift golem was shattered.")
                for (v in villagers) v.happiness = min(100f, v.happiness + 10f)
            }
            "drake" -> {
                bossesSlain++
                event("The rift drake is slain! The breach it guarded shatters.")
                mark("A rift drake was slain.")
                for (v in villagers) v.happiness = min(100f, v.happiness + 20f)
                val owned = critters.critters.map { it.sp }.toSet()
                val pool = (57..62).filter { it !in owned }
                if (pool.isNotEmpty()) critters.spawnLegend(pool[rnd.nextInt(pool.size)], p.x, p.y)
            }
        }
    }

    /** Real enemies arrive on a schedule that quickens with the age of the village. */
    /** At nightfall a mossy troll sometimes stomps out of the hills; it turns to stone if the dawn finds it. */
    /** Webs rot away, venom burns for a few seconds, and the spider is worth a hunt. */
    private fun spiderTick(dt: Float) {
        if (webs.isNotEmpty()) webs.removeAll { time - it[2] > 80.0 }
        if (poisoned.isNotEmpty()) {
            val it = poisoned.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                val v = villagers.firstOrNull { x -> x.id == e.key }
                if (v == null || time > e.value) { it.remove(); continue }
                v.hp = max(6f, v.hp - 1.2f * dt)
                v.harmedBy = "spider"; v.harmedAt = time
            }
        }
    }

    /** How much a villager is slowed by sticky webs underfoot. */
    private fun webSlow(v: Villager): Float {
        if (webs.isEmpty()) return 1f
        for (w in webs) if (abs(w[0] - v.x) < 0.9f && abs(w[1] - v.y) < 0.9f) {
            if (time - v.webAt > 6.0) { webCatches++; v.webAt = time }
            return 0.45f
        }
        return 1f
    }

    private fun trollTick() {
        if (age < 2 || villagers.size < 7 || time - lastTrollAt < DAY_LEN * 3.5) return
        val f = frac()
        if (f < 0.73 || f > 0.8) return
        if (predators.any { isThreat(it.kind) && it.kind != "pilferer" && it.kind != "hornet" }) return
        lastTrollAt = time
        spawnPredators("troll", 1)
        alertUntil = time + 100.0
        for (v in villagers) v.happiness = max(0f, v.happiness - 4f)
        event("Heavy footsteps in the dark. A night troll stomps out of the hills, hurling boulders. Strike it down before dawn, or the sunrise will turn it to stone.")
    }

    private fun threatTick() {
        if (villagers.size < 5 || time < nextThreatAt) return
        nextThreatAt = time + DAY_LEN * (if (age == 0) 1.7 else 2.5 - 0.25 * age) * (0.8 + rnd.nextFloat() * 0.5)
        if (predators.count { isThreat(it.kind) } > 0) return
        val n = villagers.size
        val boss = age >= 4 && time - lastBossAt > DAY_LEN * 6
        val what = pick(
            "raiders" to (if (age >= 1 && villagers.size >= 6 && !campUp) 4 else 0),
            "pilferer" to (if (age == 0) 8 else 3),
            "hornets" to (if (age >= 1 && (seasonName() == "summer" || seasonName() == "autumn")) 3 else 0),
            "harpy" to (if (age >= 1 && villagers.size >= 7 && time - lastHarpyAt > DAY_LEN * 2.5) 4 else 0),
            "burrower" to (if (age >= 1 && villagers.size >= 8 && time - lastWormAt > DAY_LEN * 3) 4 else 0),
            "spider" to (if (villagers.size >= 6 && time - lastSpiderAt > DAY_LEN * 1.5) 6 else 0),
            "ogre" to (if (age >= 2 && time - lastOgreAt > DAY_LEN * 4) 2 else 0),
            "breach" to (if (age >= 3) 3 else 0),
            "drake" to (if (boss) 3 else 0),
            "warband" to (if (age >= 3 && time - lastWarlordAt > DAY_LEN * 5) 3 else 0),
            "golem" to (if (age >= 3 && n >= 12) 2 else 0),
            "frostpack" to (if (age >= 2 && seasonName() == "winter") 6 else 0),
            "lich" to (if (age >= 4 && n >= 14 && time - lastLichAt > DAY_LEN * 5) 3 else 0),
            "landing" to (if (age >= 2 && n >= 8 && count("dock") > 0 && time - lastLandingAt > DAY_LEN * 3 && shoreSpot() != null) 4 else 0),
        )
        val minor = what == "pilferer" || what == "hornets"
        alertUntil = time + (if (minor) 45.0 else 100.0)
        if (!minor) for (v in villagers) v.happiness = max(0f, v.happiness - 4f)
        when (what) {
            "pilferer" -> {
                val k = if (n >= 12 && rnd.nextInt(3) == 0) 2 else 1
                spawnPredators("pilferer", k)
                event(if (k == 1) "A masked pilferer slinks out of the bushes, eyeing the stores. Catch it before it runs off with the loot!" else "Two masked pilferers slink toward the stores. Catch them before they run off with the loot!")
            }
            "harpy" -> {
                lastHarpyAt = time
                val k = min(6, 3 + n / 10)
                spawnPredators("harpy", k)
                event("Shrieks overhead! A flock of $k rift harpies wheels over the land, eyeing the food stores. Fighters and towers can drive them off!")
            }
            "burrower" -> {
                lastWormAt = time
                spawnPredators("burrower", 1)
                event("The ground rumbles and ripples. A giant burrowing worm is tunnelling toward the village and will burst out beneath your feet! Strike it while it is up.")
            }
            "spider" -> {
                lastSpiderAt = time
                val k = if (n >= 16 && rnd.nextInt(3) == 0) 2 else 1
                spawnPredators("spider", k)
                event(if (k == 1) "A giant forest spider has crept out of the trees, spinning sticky webs across the paths. Hunt it down before it poisons someone!" else "Two giant forest spiders have crept out of the trees, spinning webs across the paths!")
            }
            "hornets" -> {
                val k = min(7, 4 + n / 10)
                spawnPredators("hornet", k)
                event("A hornet nest was kicked over somewhere! A swarm of $k angry hornets is buzzing toward the village.")
            }
            "ogre" -> {
                lastOgreAt = time
                spawnPredators("ogre", 1 + (if (age >= 4 && n >= 18) 1 else 0))
                event("The ground trembles. A hulking ogre lumbers out of the hills toward the village!")
            }
            "breach" -> {
                val k = min(6, 2 + age / 2 + n / 12)
                spawnPredators("wraith", k)
                event("The sky tears open at the edge of the land and $k rift wraiths pour through!")
            }
            "warband" -> {
                lastWarlordAt = time
                spawnPredators("warlord", 1)
                val k = min(5, 2 + n / 12)
                spawnPredators("raider", k)
                spawnPredators("shaman", if (age >= 4) 2 else 1)
                event("War drums! A raider warlord leads a warband of $k bandits and a shaman toward the village. Break the warlord and the band will falter!")
            }
            "frostpack" -> {
                val k = min(7, 3 + n / 8 + age / 2)
                spawnPredators("frostwolf", k)
                event("Howls in the snow! A pack of $k frost wolves is stalking the village. Stay near the fire!")
            }
            "lich" -> {
                lastLichAt = time
                spawnPredators("lich", 1)
                event("A chill creeps over the land. A rift lich drifts in, and the dead stir around it. Strike it down and the skeletons crumble!")
            }
            "landing" -> {
                lastLandingAt = time
                landings++
                val k = min(6, 3 + n / 10 + (if (age >= 4) 1 else 0))
                landReavers(k)
            }
            "golem" -> {
                spawnPredators("golem", 1 + (if (age >= 5 && n >= 24) 1 else 0))
                event("The ground cracks open and a rift golem of living stone drags itself out. Arrows barely scratch it. Bring your fighters and critters!")
            }
            "drake" -> {
                lastBossAt = time
                spawnPredators("drake", 1)
                event("A great rift opens and a rift drake descends, wreathed in shadow! Gather every fighter and summoner!")
            }
            else -> {
                val k = min(6, 2 + n / 10 + age / 2)
                spawnPredators("raider", k)
                val sh = age >= 2 && rnd.nextInt(3) == 0
                if (sh) spawnPredators("shaman", 1)
                event("Raiders! A band of $k bandits${if (sh) " and a shaman" else ""} is marching on the village. Guard the stores!")
            }
        }
        mark("Enemies threaten the village.")
    }

    /** Long-term goals once the village is grown. Doing them all begins a Golden Age. */
    fun legacy(): List<Pair<String, Boolean>> = listOf(
        "Drive off 3 raider bands" to (raidsRepelled >= 3),
        "Slay a mighty beast (ogre, troll, warlord or worm)" to (ogresSlain + trollsSlain + warlordsSlain + wormsSlain >= 1),
        "Reach the City Age" to (age >= 4),
        "Grow to 20 villagers" to (villagers.size >= 20),
        "Raise a barracks and a library" to (count("barracks") > 0 && count("library") > 0),
        "Slay a rift drake" to (bossesSlain >= 1),
        "Raise a great wonder" to (count("wonder") > 0),
        "Summon a legend" to (critters.legendsOwned() > 0 || critters.caught.any { it in 57..62 }),
        "Train a critter to level 30" to critters.critters.any { it.level >= 30 },
    )

    /** Village deeds: milestones that pay out a reward, flash a banner and raise the village's renown. */
    class Deed(val id: String, val title: String, val text: String, val reward: String, val check: () -> Boolean)

    val allDeeds: List<Deed> by lazy {
        listOf(
            Deed("first_hut", "Roof Over Our Heads", "Three huts stand.", "+20 wood") { count("hut") >= 3 },
            Deed("big_folk", "A Real Village", "10 villagers live here.", "+30 food") { villagers.size >= 10 },
            Deed("town", "Bustling Town", "20 villagers live here.", "+40 food, +20 stone") { villagers.size >= 20 },
            Deed("bronze", "Fire and Ore", "Smelted 10 metal.", "+30 stone") { totalSmelted >= 10 },
            Deed("builder", "Master Builders", "Raised 25 buildings.", "+40 wood, +20 stone") { buildings.count { it.type != "wall" && it.type != "gate" } >= 25 },
            Deed("city", "City Age", "Reached the City Age.", "joy for all") { age >= 4 },
            Deed("first_raid", "Hold the Line", "Beat back a raider band.", "+20 food") { raidsRepelled >= 1 },
            Deed("raid5", "Wall of Spears", "Beat back 5 raider bands.", "+40 wood, +40 stone") { raidsRepelled >= 5 },
            Deed("ogre", "Ogre Slayer", "Slew an ogre.", "joy for all") { ogresSlain >= 1 },
            Deed("warlord", "Warlord Felled", "Slew a raider warlord.", "+40 food, +40 wood") { warlordsSlain >= 1 },
            Deed("golem", "Stone Breaker", "Shattered a rift golem.", "+40 stone") { golemsSlain >= 1 },
            Deed("pilfer1", "Thief-Taker", "Caught a pilferer.", "+20 food, +10 wood") { pilfersFoiled >= 1 },
            Deed("pilfer5", "Hard to Rob", "Caught 5 pilferers.", "+30 wood, +30 food") { pilfersFoiled >= 5 },
            Deed("hornets", "Honey Hunters", "Broke a hornet swarm.", "+25 food") { swarmsBroken >= 1 },
            Deed("frost", "Winter Wardens", "Broke a frost wolf pack.", "+30 wood, +10 food") { frostPacksBroken >= 1 },
            Deed("reaver1", "Shield Wall", "Beat back a sea reaver landing.", "+25 wood, +10 metal") { reaversSlain >= 3 },
            Deed("worm1", "Wormbane", "Slew a giant burrowing worm.", "+20 stone, +10 metal") { wormsSlain >= 1 },
            Deed("worm2", "Steady Ground", "Slew three burrowing worms.", "+30 wood") { wormsSlain >= 3 },
            Deed("spider1", "Webbreaker", "Slew a giant forest spider.", "+20 wood, +10 food") { spidersSlain >= 1 },
            Deed("harpy1", "Feather Bounty", "Brought down a rift harpy.", "+15 food") { harpiesSlain >= 1 },
            Deed("harpy2", "Clear Skies", "Scattered three harpy flocks.", "+25 wood") { flocksScattered >= 3 },
            Deed("spider2", "Silk Merchants", "Slew five giant forest spiders.", "+30 food") { spidersSlain >= 5 },
            Deed("troll1", "Trollbane", "Brought down a night troll before dawn.", "+20 stone, +10 metal") { trollsSlain >= 1 },
            Deed("troll2", "Sunrise Surprise", "Let the dawn turn a night troll to stone.", "+25 stone") { trollsStoned >= 1 },
            Deed("ship1", "Burn the Longship", "Seized and burned a reaver longship.", "+30 food, +10 stone") { shipsSeized >= 1 },
            Deed("lich", "Bone Breaker", "Destroyed a rift lich.", "+40 stone, +20 wood") { lichesSlain >= 1 },
            Deed("drake", "Drake Bane", "Slew a rift drake.", "joy for all") { bossesSlain >= 1 },
            Deed("cradle", "Cradle Songs", "Five babies born in the village.", "+30 food") { babiesBorn >= 5 },
            Deed("storm", "Weathered the Storm", "Rode out a thunderstorm.", "+10 food") { (wxStats["storm"] ?: 0) >= 1 },
            Deed("winter", "Through the Snow", "Survived a winter.", "+30 wood") { (wxStats["season:winter"] ?: 0) >= 1 && day() > 12 },
            Deed("year", "A Full Year", "Four seasons have come and gone.", "joy for all") { day() >= 13 },
            Deed("feast", "Harvest Home", "Held an autumn harvest festival.", "+40 food") { (wxStats["harvest festival"] ?: 0) >= 1 },
            Deed("wed", "Wedding Bells", "Two villagers were married.", "+20 food") { weds >= 1 },
            Deed("weds3", "Hearth and Home", "Three weddings held in the village.", "joy for all") { weds >= 3 },
            Deed("bff", "Fast Friends", "Two villagers became best friends.", "+10 food, +10 wood") { bffs.isNotEmpty() },
            Deed("bow", "First Bond", "Befriended a critter.", "+10 food") { critters.critters.isNotEmpty() },
            Deed("naturalist", "Friend of the Wild", "Seen twelve kinds of wild creature.", "+20 food") { fauna.kindsSeen.size >= 12 },
            Deed("foxhunt", "Red Hunter", "Watched a fox catch its supper.", "+10 food") { (fauna.seen["fox catch"] ?: 0) >= 1 },
            Deed("road", "Open Road", "Welcomed a travelling visitor to the fire.", "+15 food") { visitTotal() >= 1 },
            Deed("flock1", "Shepherd's Hands", "Tended the pasture flock twelve times.", "+12 food, +6 hides") { pastureVisits >= 12 },
            Deed("dock1", "Gone Fishing", "Rowed out from the fishing dock twelve times.", "+15 food, +6 wood") { sailTrips >= 12 },
            Deed("quarry1", "Stone by the Cartload", "Cut stone at the quarry twenty times.", "+20 wood, +10 food") { quarryHauls >= 20 },
            Deed("vigil1", "Keepers of the Flame", "Kept vigil at the legend shrine twenty times.", "+15 stone, +10 food") { vigils >= 20 },
            Deed("vigil2", "The Shrine Answers", "A legend answered the call of the shrine.", "joy for all") { legendCalls >= 1 },
            Deed("cath1", "Foundation Stones", "Laid the foundations of the Great Cathedral.", "+30 wood, +30 stone") { cathTier() >= 1 },
            Deed("cath5", "Hallowed Halls", "Finished the Great Cathedral, spire and bell.", "joy for all") { cathTier() >= 5 },
            Deed("bee1", "Honey Hands", "Took honey from the apiary twelve times.", "+12 food, +6 goods") { honeyHarvests >= 12 },
            Deed("show1", "Curtain Up", "Put on a puppet show twenty times.", "+8 wood, +4 goods") { shows >= 20 },
            Deed("kiln1", "Fired Clay", "Fired pots in the kiln twenty times.", "+6 wood, +4 stone") { kilnFires >= 20 },
            Deed("loom1", "Warp and Weft", "Wove cloth at the loom twenty times.", "+8 wood") { loomWeaves >= 20 },
            Deed("loom2", "Wrapped Against the Cold", "Five villagers wear woollen cloaks.", "joy for all") { loomCloaks >= 5 },
            Deed("yard1", "Drill Sergeants", "Drilled critters in the training yard thirty times.", "+8 wood, +4 stone") { yardSessions >= 30 },
            Deed("yard2", "Sparring Partners", "Held ten two-person critter sparring bouts in the yard.", "joy for all") { yardSpars >= 10 },
            Deed("kiln2", "Glaze Master", "Drew five glazed masterworks from the kiln.", "joy for all") { kilnGlazed >= 5 },
            Deed("show2", "Standing Ovation", "Gave three grand puppet shows.", "joy for all") { grandShows >= 3 },
            Deed("bell1", "Ring Out", "Rang the bell tower twenty times.", "+8 wood, +4 stone") { bellRings >= 20 },
            Deed("bell2", "Sound the Alarm", "Rang the alarm bell five times while foes were near.", "joy for all") { bellAlarms >= 5 },
            Deed("soak1", "Aching Bones", "Soaked in the hot spring twenty times.", "+8 food, +4 goods") { soaks >= 20 },
            Deed("rift_side", "Dungeon Delvers", "Explored five side chambers deep in the rifts.", "+20 wood") { sideRooms >= 5 },
            Deed("rift_rally", "Never Say Die", "A rift party rallied after being thrown back.", "joy for all") { riftRallies >= 1 },
            Deed("soak2", "Steam Gossip", "Shared three steamy hot-spring parties.", "joy for all") { soakParties >= 3 },
            Deed("arch1", "Straight Shooter", "Hit twenty bullseyes at the archery range.", "+10 wood, +4 stone") { bullseyes >= 20 },
            Deed("arch2", "Champion of the Butts", "Held three archery contests.", "joy for all") { archeryMatches >= 3 },
            Deed("fruit1", "Fruit of the Vine", "Filled twelve baskets in the orchard.", "+12 food, +6 goods") { fruitBaskets >= 12 },
            Deed("fruit2", "Cider Season", "Shared cider at three autumn feasts.", "joy for all") { ciderFeasts >= 3 },
            Deed("bee2", "Sweet Tooth", "Shared honey cakes at three mead feasts.", "joy for all") { meadFeasts >= 3 },
            Deed("mill1", "Daily Bread", "Ground grain at the windmill twenty times.", "+15 food, +8 wood") { millGrinds >= 20 },
            Deed("mill2", "The Baker's Round", "Baked and shared three loaves of fresh bread.", "joy for all") { loavesBaked >= 3 },
            Deed("quarry2", "Deep Vein", "Struck three veins in the quarry wall.", "+6 metal") { quarryVeins >= 3 },
            Deed("dock2", "Message in a Bottle", "Fished three sealed bottles from the lake.", "+20 food, +10 stone") { bottlesFound >= 3 },
            Deed("flock2", "Wool and Eggs", "Gathered fifty eggs from the pasture hens.", "+20 food, +10 wood") { eggsGathered >= 50 },
            Deed("ranch1", "Gentle Hands", "Fed and groomed the ranch critters fifteen times.", "+15 food, +8 wood") { ranchVisits >= 15 },
            Deed("ranch2", "Master of the Herd", "Tended the ranch critters sixty times.", "+25 food, +15 stone") { ranchVisits >= 60 },
            Deed("totem1", "Voice of the Ancestors", "Honored the totem pole twelve times.", "+15 food, +8 wood") { totemVisits >= 12 },
            Deed("dog1", "A Dog's Welcome", "A stray dog was taken in.", "+10 food") { dogsAdopted >= 1 },
            Deed("fetch", "Good Dog", "Played fetch with the dogs ten times.", "joy for all") { dogWalks >= 10 },
            Deed("snowman", "Frosty Friends", "Built three snowmen.", "joy for all") { snowmenBuilt >= 3 },
            Deed("puddle1", "Puddle Pirates", "Splashed in the rain puddles ten times.", "joy for all") { puddleJumps >= 10 },
            Deed("jar1", "Jar of Stars", "Caught fireflies on eight evenings.", "+10 food") { fireflyJars >= 8 },
            Deed("hide1", "Found You!", "Played hide and seek fifteen times.", "joy for all") { hideSeekGames >= 15 },
            Deed("kite", "Kite Day", "Flew kites on five spring days.", "+15 food") { kitesFlown >= 5 },
            Deed("dry1", "The Long Dry", "Lived through a summer drought.", "+25 food, +10 wood") { droughtsEnded >= 1 },
            Deed("dry2", "Bucket Chain", "Carried water to the parched fields thirty times.", "+20 food, +10 stone") { droughtWaterings >= 30 },
            Deed("fire1", "Bucket Brigade", "Put out a forest fire.", "+15 wood, +5 food") { firesDoused >= 1 },
            Deed("fire2", "Fire Wardens", "Threw water on the flames forty times.", "+20 wood, +10 stone") { fireDouses >= 40 },
            Deed("fallstar1", "Wish Upon a Star", "A shooting star fell near the village.", "joy for all") { starsFallen >= 1 },
            Deed("fallstar2", "Starmetal Smiths", "Gathered ten shards of fallen stars.", "+3 metal, +10 stone") { starShardsGot >= 10 },
            Deed("fete1", "Day of Dancing", "Held a seasonal fete on the green.", "+20 food") { fetesHeld >= 1 },
            Deed("fete4", "Wheel of the Year", "Held the maypole, bonfire, harvest and lantern fetes.", "+15 wood, +10 stone") { feteKinds.size >= 4 },
            Deed("teach1", "Passing It On", "Elders taught the children their trades ten times.", "joy for all") { lessonsGiven >= 10 },
            Deed("coming1", "Come of Age", "A child of the village grew up amid cheers and a flower crown.", "+10 food") { comingAges >= 1 },
            Deed("coming6", "A Generation Grown", "Six children came of age in the village.", "+10 wood, +5 stone") { comingAges >= 6 },
            Deed("teach2", "A Craft Handed Down", "A child grew up to follow a mentor's trade.", "+15 food") { apprenticesMade >= 1 },
            Deed("teach3", "Guild of Elders", "Five children grew into the trades they were taught.", "+15 wood, +10 stone") { apprenticesMade >= 5 },
            Deed("glory1", "By Royal Decree", "The Golden Age hall issued its first decree.", "+20 goods") { decrees >= 1 },
            Deed("glory2", "Age of Gold", "Ten golden decrees and two grand gildings.", "joy for all") { decrees >= 10 && gildings >= 2 },
            Deed("council1", "Heard by All", "Held the first village council and voted a priority.", "+15 food") { councilsHeld >= 1 },
            Deed("council2", "A Plan That Worked", "Three council priorities were met.", "+15 wood, +10 stone") { policiesWon >= 3 },
            Deed("revel", "Light Feet", "Danced at the village fetes forty times.", "+25 food") { feteRevels >= 40 },
            Deed("leaves", "Crunchy Leaves", "Raked four piles of autumn leaves.", "+15 wood") { leafPilesRaked >= 4 },
            Deed("omen", "Good Omens", "Read three omens of plenty at the totem.", "+20 food") { omens >= 3 },
            Deed("lore1", "First Discovery", "The scholars made their first discovery.", "+20 food, +10 stone") { techs.size >= 1 },
            Deed("lore4", "Learned Folk", "Four discoveries made in the library.", "+30 wood, +20 stone") { techs.size >= 4 },
            Deed("lore7", "Age of Wisdom", "The first seven discoveries made.", "+10 metal, joy for all") { techs.size >= 7 },
            Deed("lore10", "Higher Learning", "Three discoveries of the Town Age scholars.", "+40 wood, +40 stone") { higherTechs() >= 3 },
            Deed("lore14", "Age of Marvels", "All fourteen discoveries made.", "+15 metal, joy for all") { techs.size >= 14 },
            Deed("siege1", "Siege Engines", "Raised a great ballista.", "+15 wood, +5 stone") { count("ballista") >= 1 },
            Deed("siege10", "Bolt from Afar", "Felled ten foes with ballista bolts.", "+10 metal, +10 stone") { ballistaKills >= 10 },
            Deed("healer", "Mended and Whole", "Tended the hurt at the infirmary twenty times.", "+20 food, +10 stone") { recoveries >= 20 },
            Deed("pub2", "Lantern Nights", "Held three lantern nights at the tavern.", "+20 food, +10 stone") { tavNights >= 3 },
            Deed("pub3", "Tall Tales", "Told ten tall tales at the tavern.", "joy for all") { tavTales >= 10 },
            Deed("pub", "Merry Company", "Shared twenty-five meals at the tavern.", "+20 food, +10 wood") { tavernVisits >= 25 },
            Deed("inn", "Hospitable Hearth", "Welcomed five visitors.", "+20 wood, +10 stone") { visitTotal() >= 5 },
            Deed("tourney1", "Into the Ring", "A grand tournament was fought.", "+20 food") { critters.tourneys >= 1 },
            Deed("tourney4", "Arena Fame", "Four grand tournaments crowned a champion.", "+20 stone, +10 food") { critters.tourneys >= 4 },
            Deed("raise10", "Master Builders", "Raised ten new buildings.", "+20 wood, +10 stone") { raised >= 10 },
            Deed("raise30", "A Town Takes Shape", "Raised thirty new buildings.", "joy for all") { raised >= 30 },
            Deed("guests6", "Every Road Leads Here", "Met a knight, a scholar and a toymaker as well.", "+15 food, +10 stone") { listOf("knight", "scholar", "toymaker").all { (visitStats[it] ?: 0) >= 1 } },
            Deed("guests", "Every Kind of Guest", "Met a merchant, a bard and a pilgrim.", "joy for all") { listOf("merchant", "bard", "pilgrim").all { (visitStats[it] ?: 0) >= 1 } },
            Deed("egg1", "Warm Nest", "A critter egg hatched in the village.", "+20 food") { critters.eggsHatched >= 1 },
            Deed("shiny1", "A Glimmer in the Ether", "Befriended a shiny critter.", "+15 food, +8 stone") { critters.shinyKept() >= 1 },
            Deed("shiny3", "Rainbow Menagerie", "Kept three shiny critters at once.", "+25 food, +12 wood") { critters.shinyKept() >= 3 },
            Deed("egg6", "Cradle of Critters", "Six critter eggs hatched.", "+20 food, +10 wood") { critters.eggsHatched >= 6 },
            Deed("zoo", "Critter Keepers", "Own 10 critters.", "+20 food") { critters.critters.size >= 10 },
            Deed("fork1", "A Fork in the Road", "A critter took its rare second form.", "+20 food, +10 stone") { critters.caught.any { it in 90..95 } },
            Deed("fork3", "Both Roads Walked", "Three rare second forms were seen.", "joy for all") { critters.caught.count { it in 90..95 } >= 3 },
            Deed("dex", "Field Naturalists", "Caught 15 species.", "+30 food") { critters.caught.size >= 15 },
            Deed("level", "Trainer's Pride", "A critter reached level 25.", "joy for all") { critters.critters.any { it.level >= 25 } },
            Deed("legend", "Legend Bound", "Bonded with a legendary critter.", "joy for all") { critters.legendsOwned() > 0 },
            Deed("camp1", "Camp Breaker", "Stormed and razed a bandit camp.", "+25 wood, +15 stone") { campsRazed >= 1 },
            Deed("camp3", "Outlaw Bane", "Razed three bandit camps.", "joy for all") { campsRazed >= 3 },
            Deed("delver", "Into the Rift", "A party cleared a rift.", "+30 stone, +20 wood") { expWins >= 1 },
            Deed("delver3", "Rift Walkers", "Cleared three rift expeditions.", "joy for all") { expWins >= 3 },
            Deed("relic", "Relic Keepers", "Brought home 3 rift relics.", "+40 food, +20 stone") { relics >= 3 },
            Deed("dream1", "A Dream Come True", "A villager lived out their personal dream.", "+15 food") { dreamsDone >= 1 },
            Deed("dream5", "Village of Dreamers", "Five villagers saw their dreams come true.", "joy for all") { dreamsDone >= 5 },
            Deed("titled", "A Name Earned", "A villager earned an epithet.", "+20 food") { titlesEarned >= 1 },
            Deed("titled6", "Village of Legends", "Fifteen epithets earned in the village.", "joy for all") { titlesEarned >= 15 },
            Deed("grave1", "Where They Rest", "A graveyard was laid out for the fallen and the old.", "+10 stone, +10 wood") { buried >= 1 },
            Deed("mourn12", "Flowers for the Gone", "Laid flowers at the graves twelve times.", "joy for all") { mournVisits >= 12 },
            Deed("statue1", "Carved in Memory", "The first hero's statue was raised.", "+10 stone, +10 wood") { statues.isNotEmpty() },
            Deed("statue6", "Heroes' Row", "Six heroes stand in statue.", "joy for all") { statues.size >= 6 },
            Deed("wall1", "Mason's Round", "Raised twenty pieces of the stone town wall.", "+15 stone, +10 wood") { wallsRaised >= 20 },
            Deed("wall2", "Walled Town", "Closed the stone wall around the village.", "joy for all") { wallDone && wallsRaised >= 40 },
            Deed("wall3", "Hold the Line", "Foes lost a full minute climbing the wall.", "+6 metal") { wallHoldSecs >= 60f },
            Deed("wonder1", "Wonder of the Land", "Raised a great wonder.", "joy for all") { count("wonder") >= 1 },
            Deed("wonder3", "Three Wonders", "All three great wonders stand together.", "+60 wood, +60 stone") { count("wonder") >= 3 },
            Deed("golden", "Golden Age", "Completed every legacy goal.", "joy for all") { goldenAt >= 0.0 },
        )
    }

    fun renownTitle(): String {
        val n = deeds.size
        return when {
            n >= 20 -> "Immortal Hearth"
            n >= 15 -> "Famed Realm"
            n >= 10 -> "Renowned Village"
            n >= 5 -> "Known Settlement"
            n >= 1 -> "Rising Hamlet"
            else -> "Unknown Camp"
        }
    }

    private fun deedTick() {
        if (time < nextDeedAt || villagers.size < 2) return
        nextDeedAt = time + 3.0
        for (d in allDeeds) {
            if (d.id in deeds) continue
            val ok = try { d.check() } catch (e: Exception) { false }
            if (!ok) continue
            deeds.add(d.id)
            val r = d.reward
            fun grab(word: String): Int = Regex("\\+(\\d+) $word").find(r)?.groupValues?.get(1)?.toInt() ?: 0
            wood += grab("wood"); stone += grab("stone"); food += grab("food"); metal += grab("metal")
            if (r.contains("joy") || deeds.size % 5 == 0) {
                for (v in villagers) v.happiness = min(100f, v.happiness + 15f)
                festivalUntil = max(festivalUntil, time + 25.0)
            }
            deedBannerAt = time; deedBannerTag = ""; deedBannerName = d.title; deedBannerSub = d.text + "  " + r
            addLog("Deed done: ${d.title} - ${d.text} (${deeds.size}/${allDeeds.size})")
            mark("Deed: ${d.title}.")
            for (v in villagers) v.remember("the village earned: ${d.title}")
            break
        }
    }

    // ---- great wonders ----

    var wonderFx = -100.0
    var wonderFxKind = 0
    private var nextWonderAt = 0.0

    fun wonderName(k: Int): String = when (k) { 0 -> "Star Spire"; 1 -> "Grand Fountain"; else -> "Beacon Tower" }
    private fun wonderBlurb(k: Int): String = when (k) {
        0 -> "its crystal drinks starlight and lifts every heart."
        1 -> "its waters mend the hurt and the weary."
        else -> "its fire guides travellers, who leave tribute of wood and stone."
    }

    private fun techPaid(id: String): Boolean = when (id) {
        "farm" -> food >= 150
        "saws" -> wood >= 120
        "quarry" -> stone >= 120
        "herbal" -> food >= 200
        "vault" -> stone >= 200 && wood >= 100
        "siege" -> metal >= 10 && wood >= 100
        "mill2" -> wood >= 150 && stone >= 40
        "hives" -> wood >= 120 && food >= 30
        "roads" -> stone >= 150
        "blades" -> metal >= 12 && wood >= 100
        "feast" -> food >= 200 && wood >= 60
        "scribes" -> wood >= 200 && stone >= 100
        "marvels" -> stone >= 250 && wood >= 250
        else -> stone >= 250
    }

    private fun techPay(id: String) {
        when (id) {
            "farm" -> food -= 150
            "saws" -> wood -= 120
            "quarry" -> stone -= 120
            "herbal" -> food -= 200
            "vault" -> { stone -= 200; wood -= 100 }
            "siege" -> { metal -= 10; wood -= 100 }
            "mill2" -> { wood -= 150; stone -= 40 }
            "hives" -> { wood -= 120; food -= 30 }
            "roads" -> stone -= 150
            "blades" -> { metal -= 12; wood -= 100 }
            "feast" -> { food -= 200; wood -= 60 }
            "scribes" -> { wood -= 200; stone -= 100 }
            "marvels" -> { stone -= 250; wood -= 250 }
            else -> stone -= 250
        }
    }

    /** When the scholars have gathered enough lore (and the stores can fund it) a new discovery is made. */
    private fun researchTick() {
        if (time < nextResearchAt || villagers.isEmpty()) return
        nextResearchAt = time + 5.0
        val t = nextTech() ?: return
        if (research < t.need || !techPaid(t.id)) return
        techPay(t.id)
        techs.add(t.id)
        discoveryFx = time
        val lib = buildings.firstOrNull { it.type == "library" }
        if (lib != null) fx.add(Fx("spawn", lib.x + 0.5f, lib.y + 0.5f, time, 1.4f))
        deedBannerAt = time; deedBannerTag = "NEW DISCOVERY ${techs.size}/${allTechs.size}"; deedBannerName = t.name; deedBannerSub = t.text
        event("The scholars make a discovery: ${t.name}. ${t.text}")
        addLog("Discovery: ${t.name} - ${t.text}")
        mark("Discovery: ${t.name}.")
        for (v in villagers) { v.happiness = min(100f, v.happiness + 6f); v.remember("the scholars discovered ${t.name.lowercase()}") }
    }

    /** The infirmary's healer tends anyone hurt who lives close by, even if they never walk in. */
    private fun infirmaryTick() {
        if (time < nextInfirmaryAt) return
        nextInfirmaryAt = time + 15.0
        for (b in buildings) {
            if (b.type != "infirmary") continue
            for (v in villagers) if (v.hp < 90f && v.phase != "walking" && hypot(v.x - (b.x + 0.5f), v.y - (b.y + 0.5f)) < 10f) v.hp = min(100f, v.hp + (if (hasTech("herbal")) 10f else 5f))
        }
    }

    /** Each wonder blesses the village every so often, in its own way. */
    private fun wonderTick(dt: Float) {
        if (time < nextWonderAt || villagers.isEmpty()) return
        nextWonderAt = time + 20.0
        val sc = if (hasTech("stars")) 2f else 1f
        for (b in buildings) {
            if (b.type != "wonder") continue
            when (b.era) {
                0 -> for (v in villagers) v.happiness = min(100f, v.happiness + (if (isNight()) 2.5f else 1f) * sc)
                1 -> for (v in villagers) { if (v.hp < 100f) v.hp = min(100f, v.hp + 6f * sc); v.happiness = min(100f, v.happiness + 0.5f * sc) }
                else -> { wood += 2 * sc.toInt(); stone += 2 * sc.toInt(); if (rnd.nextInt(4) == 0) metal = min(200, metal + 1) }
            }
        }
    }

    private fun goldenTick() {
        if (goldenAt >= 0.0 || villagers.size < 4) return
        if (legacy().all { it.second }) {
            goldenAt = time
            festivalUntil = time + 150.0
            for (v in villagers) v.happiness = 100f
            event("A Golden Age begins! The village has beaten every threat and grown into a legend of the land.")
            mark("The Golden Age began.")
        }
    }

    // ---- rift expeditions ----


    // ---- travelling visitors ----

    fun visitorLabel(k: String): String = when (k) { "merchant" -> "travelling merchant"; "bard" -> "wandering bard"; "knight" -> "wandering knight"; "scholar" -> "travelling scholar"; "toymaker" -> "travelling toymaker"; else -> "pilgrim healer" }

    private fun visitorTick(dt: Float) {
        val vis = visitor
        if (vis == null) {
            if (time < nextVisitAt || villagers.size < 4) return
            if (predators.isNotEmpty() || isNight() || stormUntil > time) {
                nextVisitAt = time + 12.0
                return
            }
            val hurt = villagers.count { it.hp < 85f } + (if (time < plagueUntil) 3 else 0)
            val fighters = villagers.count { isFighter(it) }
            val kids = villagers.count { isChild(it) }
            val kind = pick(
                "merchant" to 3, "bard" to 3, "pilgrim" to (if (hurt > 0) 4 else 1),
                "knight" to (if (fighters >= 2) 3 else 0), "scholar" to 3, "toymaker" to (if (kids >= 1) 3 else 0),
            )
            val west = rnd.nextBoolean()
            val sx = if (west) 1.5f else GW - 2.5f
            var sy = GH / 2f
            for (k in 0 until 30) {
                val cy = 4 + rnd.nextInt(GH - 8)
                if (walkableTile(sx.toInt(), cy)) { sy = cy + 0.5f; break }
            }
            val ex = if (west) GW - 2.5f else 1.5f
            visitor = Visitor(kind, sx, sy, time, ex, GH / 2f + (rnd.nextFloat() - 0.5f) * 10f)
            return
        }
        val fire = buildings.firstOrNull { it.type == "hall" } ?: buildings.firstOrNull { it.type == "campfire" }
        val tx = if (vis.leaving) vis.exitX else (fire?.x ?: GW / 2) + 1.6f
        val ty = if (vis.leaving) vis.exitY else (fire?.y ?: GH / 2) + 1.2f
        val dx = tx - vis.x
        val dy = ty - vis.y
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        if (d > 0.9f) {
            val step = 1.7f * dt
            val nx = vis.x + dx / d * step
            val ny = vis.y + dy / d * step
            if (vis.stuck > 3f || walkableTile(nx.toInt(), ny.toInt())) { vis.x = nx; vis.y = ny }
            else if (walkableTile(nx.toInt(), vis.y.toInt())) vis.x = nx
            else if (walkableTile(vis.x.toInt(), ny.toInt())) vis.y = ny
            else vis.stuck += dt
        } else if (vis.leaving) {
            visitor = null
            nextVisitAt = time + DAY_LEN * (1.5 + rnd.nextDouble() * 1.5)
            return
        } else if (vis.arrivedAt < 0.0) {
            vis.arrivedAt = time
            visitorArrive(vis)
        } else if (time - vis.arrivedAt > 50.0) {
            vis.leaving = true
        }
        if (!vis.leaving && vis.arrivedAt < 0.0 && time - vis.born > 150.0) vis.leaving = true
        if (time - vis.born > 400.0) { visitor = null; nextVisitAt = time + DAY_LEN }
    }

    private fun gatherAround(vis: Visitor, action: String, n: Int) {
        var k = 0
        for (v in villagers.sortedBy { (it.x - vis.x) * (it.x - vis.x) + (it.y - vis.y) * (it.y - vis.y) }) {
            if (k >= n) break
            if (v.phase != "idle" || v.hunger > 80f) continue
            if (startAction(v, action, "")) k++
        }
    }

    private fun visitorArrive(vis: Visitor) {
        visitStats[vis.kind] = (visitStats[vis.kind] ?: 0) + 1
        fx.add(Fx("spawn", vis.x, vis.y, time, 1.4f))
        val name = visitorLabel(vis.kind)
        when (vis.kind) {
            "merchant" -> {
                val ratios = listOf(
                    Triple("wood", wood, wood.toFloat() / capWood()), Triple("stone", stone, stone.toFloat() / capStone()),
                    Triple("food", food, food.toFloat() / capFood()),
                )
                val sell = ratios.maxByOrNull { it.third }!!
                val amount = if (sell.third >= 0.4f) (sell.second * 0.25f).toInt().coerceIn(10, 60) else 0
                val gotGoods = min(6, 30 - goods).coerceAtLeast(0)
                goods += gotGoods
                var extra: String
                if (age >= 2) { metal += 4; extra = "4 metal" } else { hides += 6; extra = "6 hides" }
                if (amount > 0) {
                    when (sell.first) { "wood" -> wood -= amount; "stone" -> stone -= amount; else -> food -= amount }
                    event("A $name reaches the fire and swaps $amount ${sell.first} for $gotGoods goods and $extra.")
                } else {
                    event("A $name reaches the fire with curios: $gotGoods goods and $extra in trade for a few stories.")
                }
                for (v in villagers) if (!isChild(v)) v.happiness = min(100f, v.happiness + 4f)
                gatherAround(vis, if (count("market") > 0 && goods >= 2) "trade" else "huddle", 5)
            }
            "bard" -> {
                for (v in villagers) v.happiness = min(100f, v.happiness + 10f)
                festivalUntil = max(festivalUntil, time + 50.0)
                event("A $name strikes up a tune at the fire and the whole village gathers to listen.")
                gatherAround(vis, "dance", 6)
            }
            "knight" -> {
                val fighters = villagers.filter { isFighter(it) }.sortedByDescending { skillLevel(it, "combat") * 100 + it.hp.toInt() }
                val champ = fighters.firstOrNull()
                var trained = 0
                for (f in fighters) { f.bump("xp_combat", 6); trained++ }
                for (v in villagers) if (!isChild(v)) v.happiness = min(100f, v.happiness + 5f)
                if (champ != null) {
                    val lv = skillLevel(champ, "combat")
                    champ.happiness = min(100f, champ.happiness + 8f)
                    champ.remember("fought a friendly duel with a wandering knight")
                    champ.addDiary("Crossed wooden swords with a wandering knight.")
                    event("A $name rides in and challenges ${champ.name} (Fighting level $lv) to a friendly duel; $trained fighters pick up new tricks.")
                } else event("A $name rides in, finds no one to duel, and shares old war stories.")
                knightDuels++
                gatherAround(vis, "drill", 4)
            }
            "scholar" -> {
                val libs = count("library")
                if (libs > 0) {
                    val gain = 24 + 8 * libs
                    research += gain
                    event("A $name unpacks a satchel of scrolls at the fire and leaves $gain lore for the ${if (libs > 1) "libraries" else "library"}.")
                    gatherAround(vis, "study", 4)
                } else {
                    goods += min(3, (30 - goods).coerceAtLeast(0))
                    event("A $name has no library to share scrolls with, so reads aloud at the fire and trades a few maps for 3 goods.")
                    gatherAround(vis, "tell_story", 3)
                }
                for (v in villagers) if (!isChild(v) && (v.job == "Scholar" || v.job == "Storyteller")) v.happiness = min(100f, v.happiness + 8f)
            }
            "toymaker" -> {
                val kids = villagers.filter { isChild(it) }
                for (k in kids) k.happiness = min(100f, k.happiness + 15f)
                goods += min(3, (30 - goods).coerceAtLeast(0))
                toysGiven += kids.size
                event("A $name sets down a pack of wooden toys: ${kids.size} ${if (kids.size == 1) "child gets" else "children get"} one each, and 3 goods change hands.")
                var k = 0
                for (c in kids.sortedBy { (it.x - vis.x) * (it.x - vis.x) + (it.y - vis.y) * (it.y - vis.y) }) {
                    if (k >= 5) break
                    if (c.phase != "idle" || c.hunger > 80f) continue
                    if (startAction(c, "play", "")) k++
                }
            }
            else -> {
                var healed = 0
                for (v in villagers) if (v.hp < 95f) { v.hp = min(100f, v.hp + 45f); healed++ }
                val cured = time < plagueUntil
                if (cured) plagueUntil = time
                for (v in villagers) v.happiness = min(100f, v.happiness + 5f)
                event(
                    if (healed > 0 || cured) "A $name arrives, mends $healed villagers${if (cured) " and drives out the plague" else ""}, and blesses the camp."
                    else "A $name arrives, finds everyone well, and blesses the camp."
                )
                gatherAround(vis, "huddle", 4)
            }
        }
        mark("A $name visited.")
    }

    private fun partyMembers(): List<Villager> = party.mapNotNull { id -> villagers.firstOrNull { it.id == id } }

    private fun delvePower(v: Villager): Float {
        var p = (if (isFighter(v)) 1.0f else 0.6f) * (v.hp / 100f).coerceIn(0.3f, 1f) + 0.1f * toolBonus()
        var lv = 0
        for (c in critters.critters) if (c.owner == v.id && c.companion) lv += c.level
        p += min(1.5f, lv / 20f)
        return p
    }

    private fun cancelRift(text: String) {
        for (m in partyMembers()) if (m.action == "delve") finish(m)
        party.clear()
        riftState = 0
        nextRiftAt = time + 60.0
        addLog(text)
    }

    private fun openRift() {
        val cands = villagers.filter {
            !isChild(it) && it.hp > 60f && it.hunger < 65f && it.energy > 35f && it.phase != "sleeping" &&
                !it.action.startsWith("critter_") && it.action != "attack"
        }.sortedByDescending { delvePower(it) }
        if (cands.size < 3) { nextRiftAt = time + 40.0; return }
        var site = -1
        for (k in 0 until 24) {
            val ang = rnd.nextFloat() * 6.28f
            val r = 14f + rnd.nextFloat() * 8f
            val x = (GW / 2f + Math.cos(ang.toDouble()).toFloat() * r).toInt().coerceIn(2, GW - 3)
            val y = (GH / 2f + Math.sin(ang.toDouble()).toFloat() * r * 0.7f).toInt().coerceIn(2, GH - 3)
            val i = y * GW + x
            if (blocked(i) || tiles[i] == T.WATER) continue
            if (findPath(cellOf(cands[0]), i) == null) continue
            site = i
            break
        }
        if (site < 0) { nextRiftAt = time + 40.0; return }
        riftX = site % GW + 0.5f
        riftY = site / GW + 0.5f
        party.clear()
        for (c in cands) {
            if (party.size >= 4) break
            if (go(c, "delve", site)) { party.add(c.id); c.thought = "the rift calls" }
        }
        if (party.size < 3) { cancelRift("A rift flickered shut before a party could gather."); return }
        riftState = 1
        riftOpenedAt = time
        riftRoom = 0
        riftRoomsDone = 0
        riftSide = false; riftSideKind = 0; riftRallied = false; riftBoost = 0f; riftWardenPhase = 0
        fx.add(Fx("spawn", riftX, riftY, time, 1.6f))
        val names = partyMembers().joinToString(", ") { it.name }
        event("A violet rift flickers open beyond the village. $names grab their gear and march in to see what lies beyond.")
    }

    private fun endRift(ok: Boolean, text: String) {
        for (m in partyMembers()) {
            m.bump("rooms", riftRoomsDone)
            if (m.action == "delve") finish(m)
            checkTitles(m)
            m.happiness = min(100f, m.happiness + (if (ok) 10f else 2f))
            m.remember(if (ok) "came back alive from the rift with loot" else "was driven out of the rift")
        }
        expeditions++
        if (ok) expWins++ else expRetreats++
        deepestRoom = max(deepestRoom, riftRoomsDone)
        riftWon = ok
        riftResultAt = time
        fx.add(Fx("spawn", riftX, riftY, time, 1.4f))
        deedBannerAt = time
        deedBannerTag = "RIFT EXPEDITION"
        deedBannerName = if (ok) "Rift Cleared" else "Party Driven Back"
        deedBannerSub = text
        event(text)
        mark(if (ok) "A party cleared a rift." else "A rift party was driven back.")
        party.clear()
        riftState = 0
        nextRiftAt = time + DAY_LEN * (1.6 + rnd.nextFloat() * 0.8)
    }

    private fun resolveRoom(ms: List<Villager>) {
        val power = ms.sumOf { delvePower(it).toDouble() }.toFloat() + 0.12f * relics
        val diff = 1.3f + 0.75f * riftRoom + 0.25f * age + (if (riftRoom == 4) 0.8f else 0f)
        val chance = (0.68f + (power - diff) * 0.22f + riftBoost).coerceIn(0.15f, 0.95f)
        val foe = when (riftRoom) { 1 -> "gloomwebs in Gloomweb Hollow"; 2 -> "shard crawlers in their nest"; 3 -> "echo wraiths in the Echo Hall"; else -> "the Rift Warden" }
        val win = rnd.nextFloat() < chance
        val hit = ms[rnd.nextInt(ms.size)]
        hit.hp = max(15f, hit.hp - (if (win) 6f + rnd.nextInt(10) else 18f + rnd.nextInt(14)))
        fx.add(Fx("clash", riftX + (rnd.nextFloat() - 0.5f) * 1.6f, riftY + (rnd.nextFloat() - 0.5f), time, 1.0f))
        if (!win) {
            val avg = ms.sumOf { it.hp.toDouble() }.toFloat() / ms.size
            if (!riftRallied && avg >= 40f) {
                riftRallied = true
                riftRallies++
                riftBoost += 0.1f
                riftRoomT = 3f
                addLog("${hit.name} is thrown back by $foe, but the party rallies and charges again.")
                fx.add(Fx("spawn", riftX, riftY, time, 0.8f))
                return
            }
            val spoils = if (riftRoomsDone >= 2) "They retreat with the spoils of $riftRoomsDone rooms." else "They limp home wounded."
            endRift(false, "${hit.name} and the party were driven out by $foe. $spoils")
            return
        }
        if (riftRoom == 4 && riftWardenPhase == 0) {
            riftWardenPhase = 1
            riftRoomT = 4f
            addLog("The Rift Warden's crystal shell cracks! It rises, burning violet, for a second round.")
            fx.add(Fx("clash", riftX, riftY, time, 1.4f))
            return
        }
        roomsCleared++
        riftRoomsDone = riftRoom
        for (m in ms) m.happiness = min(100f, m.happiness + 3f)
        val loot = when (riftRoom) {
            1 -> { stone += 14; wood += 10; "14 stone, 10 wood" }
            2 -> { hides += 4; if (age >= 2) { ore += 5; "4 hides, 5 ore" } else { stone += 10; "4 hides, 10 stone" } }
            3 -> { food += 20; critters.scrolls[1] = critters.scrolls[1] + 1; if (age >= 2) { metal += 3; "20 food, 3 metal, a fine scroll" } else "20 food, a fine scroll" }
            else -> { relics++; wardensSlain++; critters.scrolls[2] = critters.scrolls[2] + 1; metal += 4; wood += 20; stone += 20; "a rift relic, a rare scroll, 4 metal" }
        }
        addLog("The party defeats $foe (room $riftRoom/4): $loot.")
        if (riftRoom >= 4) {
            for (v in villagers) v.happiness = min(100f, v.happiness + 4f)
            festivalUntil = max(festivalUntil, time + 20.0)
            endRift(true, "The party slays the Rift Warden and carries home a glowing relic: $loot.")
            return
        }
        riftRoom++
        fx.add(Fx("spawn", riftX, riftY, time, 1.0f))
        if (rnd.nextInt(100) < 60) { riftSide = true; riftRoomT = 0f }
    }

    /** A quiet chamber between two fights: a trap, a vault, a healing spring or a shrine. */
    private fun resolveSide(ms: List<Villager>) {
        val power = ms.sumOf { delvePower(it).toDouble() }.toFloat()
        sideRooms++
        riftSideAt = time
        when (rnd.nextInt(4)) {
            0 -> {
                riftSideKind = 1
                val v = ms[rnd.nextInt(ms.size)]
                if (rnd.nextFloat() < (0.45f + power * 0.08f).coerceIn(0.4f, 0.9f)) {
                    addLog("${v.name} spots a tripwire in the dark and the party steps over it.")
                } else {
                    sideTraps++
                    v.hp = max(15f, v.hp - 14f)
                    addLog("${v.name} trips a rune trap and is scorched, but fights on.")
                }
            }
            1 -> {
                riftSideKind = 2
                sideVaults++
                val g = 4 + age * 2
                goods += g
                if (age >= 2) metal += 2 else stone += 6
                addLog("The party pries open a dusty vault: $g goods and ${if (age >= 2) "2 metal" else "6 stone"}.")
            }
            2 -> {
                riftSideKind = 3
                sideSprings++
                for (m in ms) m.hp = min(100f, m.hp + 22f)
                addLog("A glowing spring bubbles in the rock; the party drinks and binds its wounds.")
            }
            else -> {
                riftSideKind = 4
                sideShrines++
                riftBoost += 0.08f
                for (m in ms) m.happiness = min(100f, m.happiness + 6f)
                addLog("A small shrine hums as the party passes; their spirits lift.")
            }
        }
        fx.add(Fx("spawn", riftX, riftY, time, 0.7f))
    }

    // ---- bandit camp ----

    fun campAssaultOk(v: Villager?): Boolean {
        if (!campUp || age < 1 || isNight() || plagueUntil > time) return false
        if (predators.any { isThreat(it.kind) }) return false
        if (v == null) return true
        if (isChild(v) || v.hp < 60f || v.hunger > 75f || v.energy < 25f) return false
        return villagers.count { it !== v && it.action == "assault_camp" } < 5
    }

    private fun spawnAt(kind: String, n: Int, x: Float, y: Float) {
        if (isThreat(kind)) foeStats[kind] = (foeStats[kind] ?: 0) + n
        repeat(n) {
            predators.add(Predator(kind, (x + (rnd.nextFloat() - 0.5f) * 2f).coerceIn(0.6f, GW - 0.7f), (y + 1f + rnd.nextFloat()).coerceIn(0.6f, GH - 0.7f)).also { it.angle = rnd.nextFloat() * 6.28f })
        }
    }

    private fun pitchCamp() {
        val anchor = villagers.firstOrNull { !isChild(it) } ?: return
        var site = -1
        for (k in 0 until 24) {
            val ang = rnd.nextFloat() * 6.28f
            val r = 17f + rnd.nextFloat() * 8f
            val x = (GW / 2f + Math.cos(ang.toDouble()).toFloat() * r).toInt().coerceIn(3, GW - 4)
            val y = (GH / 2f + Math.sin(ang.toDouble()).toFloat() * r * 0.7f).toInt().coerceIn(3, GH - 4)
            val i = y * GW + x
            if (blocked(i) || tiles[i] == T.WATER) continue
            if (hypot(x + 0.5f - riftX, y + 0.5f - riftY) < 5f && riftState != 0) continue
            if (findPath(cellOf(anchor), i) == null) continue
            site = i
            break
        }
        if (site < 0) { campNextAt = time + 40.0; return }
        campX = site % GW + 0.5f
        campY = site / GW + 0.5f
        campMax = 90f + 30f * age + 3f * villagers.size
        campHp = campMax
        campUp = true
        campHitAt = -999.0
        campSortieAt = time + DAY_LEN * 0.8
        fx.add(Fx("spawn", campX, campY, time, 1.4f))
        event("Smoke rises beyond the hills: outlaws have pitched a bandit camp. It will keep sending raiders until the village storms it!")
        mark("A bandit camp appeared.")
    }

    private fun razeCamp(last: Villager) {
        campUp = false
        campsRazed++
        campRazedAt = time
        campRazedX = campX
        campRazedY = campY
        val band = villagers.filter { it.action == "assault_camp" || it === last }
        wood += 25 + 5 * age; stone += 15 + 5 * age; food += 30; hides += 4; goods += 3
        var loot = "${25 + 5 * age} wood, ${15 + 5 * age} stone, 30 food, 4 hides"
        if (age >= 2) { metal += 3; loot += ", 3 metal" }
        if (campsRazed % 2 == 1) { critters.scrolls[1] = critters.scrolls[1] + 1; loot += ", a fine scroll" }
        for (m in band) {
            m.bump("camps", 1)
            m.happiness = min(100f, m.happiness + 10f)
            m.remember("helped burn down the bandit camp")
            if (m.action == "assault_camp") finish(m)
        }
        for (v in villagers) v.happiness = min(100f, v.happiness + 4f)
        festivalUntil = max(festivalUntil, time + 18.0)
        fx.add(Fx("spawn", campX, campY, time, 1.6f))
        deedBannerAt = time
        deedBannerTag = "BANDIT CAMP"
        deedBannerName = "Camp Razed"
        deedBannerSub = "${last.name} and ${band.size - 1} others burn the outlaw hideout: $loot"
        event("The bandit camp burns! ${last.name} and the band carry home $loot.")
        mark("The bandit camp was razed.")
        campNextAt = time + DAY_LEN * (3.0 + rnd.nextFloat() * 1.5)
    }

    private fun campTick(dt: Float) {
        if (!campUp) {
            if (age < 1 || villagers.size < 7 || time < campNextAt) return
            if (predators.any { isThreat(it.kind) } || isNight()) return
            pitchCamp()
            return
        }
        if (time - campHitAt > 20.0) campHp = min(campMax, campHp + 0.6f * dt)
        if (time < campSortieAt) return
        campSortieAt = time + DAY_LEN * (1.3 + rnd.nextFloat() * 0.6)
        if (villagers.size < 5 || predators.any { isThreat(it.kind) }) return
        val k = min(5, 2 + villagers.size / 8 + age / 3)
        spawnAt("raider", k, campX, campY)
        val sh = age >= 2 && rnd.nextInt(3) == 0
        if (sh) spawnAt("shaman", 1, campX, campY)
        campSorties++
        alertUntil = time + 100.0
        event("Outlaws spill out of their camp! $k bandits${if (sh) " and a shaman" else ""} are marching on the village.")
        mark("The bandit camp sent raiders.")
    }

    private fun expeditionTick(dt: Float) {
        if (riftState == 0) {
            for (v in villagers) if (v.action == "delve") finish(v)
            if (time < nextRiftAt || villagers.size < 7) return
            if (predators.any { isThreat(it.kind) } || isNight() || plagueUntil > time) { nextRiftAt = time + 30.0; return }
            openRift()
            return
        }
        val ms = partyMembers().filter { it.action == "delve" }
        party.retainAll(ms.map { it.id }.toSet())
        if (riftState == 1) {
            val here = ms.count { it.phase == "working" }
            if (ms.size < 2) { cancelRift("The rift party fell apart before it could go in."); return }
            if (here == ms.size || time - riftOpenedAt > 100.0) {
                if (here < 2) { cancelRift("The rift party never reached the rift."); return }
                for (m in ms) if (m.phase != "working") { finish(m); party.removeAll { it == m.id } }
                riftState = 2
                riftRoom = 1
                riftRoomT = 0f
                addLog("The party steps into the rift.")
                fx.add(Fx("spawn", riftX, riftY, time, 1.2f))
            }
            return
        }
        val inside = ms.filter { it.phase == "working" }
        if (inside.size < 2) { endRift(false, "The rift party was scattered and fled home."); return }
        riftRoomT += dt
        if (riftSide) {
            if (riftRoomT < 7f) return
            riftRoomT = 0f
            riftSide = false
            resolveSide(inside)
            return
        }
        if (riftRoomT < 11f) return
        riftRoomT = 0f
        resolveRoom(inside)
    }

    // ---- tick ----

    private var nextEventAt = DAY_LEN * 0.3
    private var alertUntil = 0.0
    private var rainUntil = 0.0
    var festivalUntil = 0.0

    private fun event(text: String) {
        addLog(text)
        lastNarrateAt = 0L
        narrate(text)
        for (v in villagers) v.remember(text.take(70))
    }

    private fun eventTick() {
        if (time < nextEventAt || villagers.size < 2) return
        nextEventAt = time + 110.0 + rnd.nextInt(120)
        when (rnd.nextInt(7)) {
            0 -> {
                for (i in tiles.indices) if (tiles[i] == T.BUSH) amt[i] = 3
                event("The bushes burst with ripe berries.")
            }
            1 -> {
                if (villagers.size < popCap() && rnd.nextInt(3) == 0) {
                    val n = spawnVillager()
                    event("A wandering ${n.job.lowercase()} named ${n.name} asks to stay.")
                } else {
                    food += 3
                    wood += 3
                    event("A friendly traveler shares supplies and stories.")
                }
            }
            2, 6 -> {
                alertUntil = time + 80.0
                for (v in villagers) v.happiness = max(0f, v.happiness - 6f)
                val bearLoose = age >= 2 && rnd.nextInt(4) == 0
                if (!bearLoose && rnd.nextInt(3) == 0) {
                    val n = 1 + rnd.nextInt(2)
                    spawnPredators("boar", n)
                    event("Wild boars crash out of the undergrowth and charge toward the village!")
                } else if (bearLoose) {
                    spawnPredators("bear", 1)
                    event("A huge bear crashes out of the forest and heads for the village!")
                } else {
                    val n = 2 + rnd.nextInt(2) + age / 2
                    spawnPredators("wolf", n)
                    event("A pack of $n wolves prowls out of the trees! Stay near the fire, fighters to the edge.")
                }
                mark("Predators attacked the village.")
            }
            3 -> {
                rainUntil = time + 90.0
                event("Rain begins to fall.")
            }
            4 -> {
                if (food >= 8) {
                    food -= 4
                    festivalUntil = time + 45.0
                    for (v in villagers) v.happiness = min(100f, v.happiness + 20f)
                    event("The villagers hold a small harvest feast around the fire.")
                } else {
                    stone += 3
                    event("Someone finds a pile of good stone by the stream.")
                }
            }
            else -> {
                val v = villagers[rnd.nextInt(villagers.size)]
                wood += 4
                event("${v.name} stumbles on a stash of seasoned timber (+4 wood).")
            }
        }
    }

    private fun fireThreatensBuildings(b: Blaze): Boolean {
        val bx = (b.i % GW) + 0.5f; val by = (b.i / GW) + 0.5f
        return buildings.any { hypot(it.x + 0.5f - bx, it.y + 0.5f - by) < 4.5f }
    }

    private fun igniteAt(i: Int, text: String) {
        if (blazes.size >= 14 || tiles[i] != T.FOREST || blazes.any { it.i == i }) return
        val sx = (i % GW) + 0.5; val sy = (i / GW) + 0.5
        if (scorches.any { abs(it[0] - sx) < 0.6 && abs(it[1] - sy) < 0.6 && it[2] > time }) return
        val first = blazes.isEmpty()
        blazes.add(Blaze(i, time, 26f, time + 10.0 + rnd.nextInt(8), time + 6.0))
        if (first) {
            firesStarted++
            wx("wildfire")
            event(text)
            for (v in villagers) if (!isChild(v) && v.phase != "sleeping") v.happiness = max(0f, v.happiness - 3f)
        }
    }

    private fun extinguish(b: Blaze, by: Villager?) {
        if (!blazes.remove(b)) return
        scorches.add(doubleArrayOf((b.i % GW) + 0.5, (b.i / GW) + 0.5, time + DAY_LEN * 0.8))
        firesDoused++
        if (by != null) {
            by.happiness = min(100f, by.happiness + 6f)
            by.remember("helped put out the forest fire")
        }
        if (blazes.isEmpty()) {
            val n = villagers.count { it.action == "douse" }
            event(if (by != null) "${by.name} throws the last bucket and the forest fire is out${if (n > 1) "; the brigade cheers" else ""}!" else "The forest fire is out.")
            for (v in villagers) if (!isChild(v) && v.phase != "sleeping") v.happiness = min(100f, v.happiness + 4f)
        }
    }

    private fun fireTick(dt: Float) {
        scorches.removeAll { it[2] < time }
        // dry spells light the odd ember
        if (time >= nextEmberAt) {
            nextEmberAt = time + DAY_LEN * (1.2 + rnd.nextFloat() * 1.6)
            if ((seasonIdx() == 1 || seasonIdx() == 2) && !raining() && !foggy() && villagers.size >= 3 && blazes.isEmpty()) {
                for (n in 0 until 40) {
                    val tx = 30 + rnd.nextInt(36); val ty = 10 + rnd.nextInt(34)
                    if (tiles[ty * GW + tx] == T.FOREST) { igniteAt(ty * GW + tx, "A dry ember catches in the forest and a wildfire starts! Villagers, fetch water!"); break }
                }
            }
        }
        if (blazes.isEmpty()) return
        val wet = raining()
        for (b in blazes.toList()) {
            if (wet) b.hp -= (if (storm()) 0.7f else 0.45f) * dt
            val age = time - b.born
            if (b.hp <= 0f) { extinguish(b, null); continue }
            if (age > 75.0) {
                blazes.remove(b); fireBurnedOut++
                scorches.add(doubleArrayOf((b.i % GW) + 0.5, (b.i / GW) + 0.5, time + DAY_LEN * 0.8))
                if (blazes.isEmpty()) event("The fire burns itself out and leaves charred ground.")
                continue
            }
            if (time >= b.nextSpread) {
                b.nextSpread = time + 11.0 + rnd.nextInt(9)
                if (!wet && blazes.size < 14 && rnd.nextInt(10) < 4) {
                    val bx = b.i % GW; val by = b.i / GW
                    val tx = (bx + rnd.nextInt(5) - 2).coerceIn(1, GW - 2); val ty = (by + rnd.nextInt(5) - 2).coerceIn(1, GH - 2)
                    if (tiles[ty * GW + tx] == T.FOREST) igniteAt(ty * GW + tx, "")
                }
            }
            if (time >= b.nextSpark) {
                b.nextSpark = time + 7.0 + rnd.nextInt(5)
                if (fireThreatensBuildings(b) && wood > 0) {
                    val lost = min(wood, 1 + rnd.nextInt(2)); wood -= lost; fireLoss += lost
                    if (fireLoss <= 2 || fireLoss % 8 == 0) addLog("Sparks drift over the huts and burn $lost wood.")
                }
            }
        }
    }

    private fun starTick() {
        if (starShards > 0 && !starAnnounced && time >= starFallAt) {
            starAnnounced = true
            starsFallen++
            event("A shooting star streaks down and crashes beyond the fields! Shards of starmetal glitter in the crater.")
            for (v in villagers) if (v.phase != "sleeping") v.happiness = min(100f, v.happiness + 4f)
        }
        if (starShards > 0 && starAnnounced && time >= starUntil) { starShards = 0; addLog("The fallen star's crater has gone dark; the shards are lost.") }
        if (time < nextStarAt || villagers.size < 3 || starShards > 0) return
        if (!isNight() || raining() || foggy() || snowing() || alert()) return
        // pick a bare grass tile some way from the fire, in the open
        val cx = 48f; val cy = 27f
        for (n in 0 until 60) {
            val a = rnd.nextFloat() * 6.2832f
            val d = 9f + rnd.nextFloat() * 8f
            val tx = (cx + cos(a) * d).toInt().coerceIn(3, GW - 4)
            val ty = (cy + sin(a) * d * 0.75f).toInt().coerceIn(3, GH - 4)
            val i = ty * GW + tx
            if (tiles[i] != T.GRASS || occupied[i]) continue
            starX = tx + 0.5f; starY = ty + 0.5f
            starFallAt = time + 3.5
            starShards = (3 + age / 2).coerceAtMost(6)
            starUntil = time + DAY_LEN * 0.95
            starAnnounced = false
            nextStarAt = time + DAY_LEN * (2.0 + rnd.nextFloat() * 1.5)
            wx("star")
            return
        }
        nextStarAt = time + 60.0
    }

    private fun wx(k: String) { wxStats[k] = (wxStats[k] ?: 0) + 1 }

    private fun seasonChange(s: Int) {
        wx("season:" + seasonName())
        if (s != 3 && seasonProps.any { it.kind == "snowman" }) { seasonProps.removeAll { it.kind == "snowman" }; addLog("The snowmen melt away.") }
        if (s != 2) seasonProps.removeAll { it.kind == "leafpile" }
        when (s) {
            0 -> {
                for (i in tiles.indices) if (tiles[i] == T.BUSH) amt[i] = 3
                event("Spring arrives. Blossoms open and the bushes fill with buds.")
            }
            1 -> {
                for (v in villagers) v.happiness = min(100f, v.happiness + 5f)
                event("Summer comes. The days are long and warm.")
            }
            2 -> {
                if (food >= 10 && villagers.size >= 3) {
                    food -= 4
                    festivalUntil = time + 70.0
                    for (v in villagers) v.happiness = min(100f, v.happiness + 15f)
                    wx("harvest festival")
                    event("Autumn arrives, and the villagers hold a harvest festival.")
                } else event("Autumn arrives. The leaves turn gold; time to fill the stores.")
            }
            else -> event("Winter arrives. Snow lies on the roofs, and the stores must last.")
        }
        mark("${seasonName().replaceFirstChar { it.uppercase() }} began.")
    }

    private fun rollWeather(s: Int) {
        val r = rnd.nextInt(100)
        val kind = when (s) {
            0 -> if (r < 28) "rain" else if (r < 40) "fog" else if (r < 48) "storm" else ""
            1 -> if (r < 20) "storm" else if (r < 30) "rain" else if (r < 35) "fog" else if (r < 52 && age >= 1 && count("farm") > 0 && !drought() && time > DAY_LEN * 4) "drought" else ""
            2 -> if (r < 25) "fog" else if (r < 50) "rain" else if (r < 56) "storm" else ""
            else -> if (r < 50) "snow" else if (r < 60) "fog" else ""
        }
        when (kind) {
            "rain" -> { rainUntil = time + 90.0 + rnd.nextInt(60); wx("rain"); event("Rain begins to fall.") }
            "storm" -> {
                stormUntil = time + 80.0 + rnd.nextInt(50); wasStorm = true; nextStrikeAt = time + 4.0
                wx("storm"); event("Dark clouds roll in and a thunderstorm breaks over the village!")
            }
            "fog" -> { fogUntil = time + 100.0 + rnd.nextInt(60); wx("fog"); event("A thick fog creeps in from the lake.") }
            "snow" -> { snowUntil = time + 110.0 + rnd.nextInt(60); wx("snow"); event("Snow begins to fall.") }
            "drought" -> {
                droughtStart = time; droughtUntil = time + 210.0 + rnd.nextInt(110); droughtWaterUntil = 0.0; droughtOn = true; droughts++
                wx("drought")
                nextEmberAt = min(nextEmberAt, time + 40.0 + rnd.nextInt(70))
                event(if (count("well") > 0) "A long dry spell begins: the fields are parched. Draw water from the well to save the crops!"
                      else "A long dry spell begins and the fields are parched. Without a well the crops will wither.")
            }
        }
    }

    private fun lightningStrike() {
        var x = 20f + rnd.nextInt(GW - 40); var y = 8f + rnd.nextInt(GH - 16)
        var hit = -1
        for (n in 0 until 40) {
            val tx = 30 + rnd.nextInt(36); val ty = 10 + rnd.nextInt(34)
            if (tiles[ty * GW + tx] == T.FOREST) { hit = ty * GW + tx; x = tx.toFloat(); y = ty.toFloat(); break }
        }
        strikeAt = time; strikeX = x + 0.5f; strikeY = y + 0.5f
        wx("lightning")
        for (v in villagers) if (hypot(v.x - x, v.y - y) < 7f) v.happiness = max(0f, v.happiness - 4f)
        if (hit >= 0 && rnd.nextInt(3) == 0) igniteAt(hit, "Lightning strikes the forest and the trees go up in flames! Villagers, fetch water!")
        else if (hit >= 0 && rnd.nextInt(3) == 0) {
            wood += 2
            addLog("Lightning splits an old tree; the timber is hauled in (+2 wood).")
        }
    }

    // ---- village council ----

    fun policyTitle(): String = when (policy) {
        "harvest" -> "Harvest First"
        "works" -> "Works and Walls"
        "watch" -> "Keep Watch"
        "lore" -> "Seek Knowledge"
        "cheer" -> "Good Cheer"
        else -> "none"
    }

    fun policyBlurb(): String = when (policy) {
        "harvest" -> "fill the food stores"
        "works" -> "gather wood and stone"
        "watch" -> "drill and patrol against beasts and raiders"
        "lore" -> "study in the library"
        "cheer" -> "lift the village's spirits"
        else -> ""
    }

    fun councilHubName(): String = if (buildings.any { it.type == "hall" }) "hall" else "fire"

    fun councilOk(): Boolean = councilOn && !storm() && !isNight() && time < councilUntil &&
        !predators.any { hypot(it.x - councilX, it.y - councilY) < 11f }

    private fun avgHappy(): Float = if (villagers.isEmpty()) 0f else villagers.map { it.happiness }.average().toFloat()

    private fun policyScores(): LinkedHashMap<String, Float> {
        val ad = max(1, adults())
        val fLack = ((10 + ad * 3 - food).toFloat() / (10 + ad * 3)).coerceIn(0f, 1f)
        val wLack = ((25 + ad * 4 - wood).toFloat() / (25 + ad * 4)).coerceIn(0f, 1f)
        val sLack = ((10 + ad * 2 - stone).toFloat() / (10 + ad * 2)).coerceIn(0f, 1f)
        val recent = time - lastThreatAt < DAY_LEN * 1.5
        val s = LinkedHashMap<String, Float>()
        s["harvest"] = 0.15f + fLack * 1.6f + (if (seasonIdx() == 2) 0.25f else 0f) - (if (lastPolicy == "harvest") 0.5f else 0f)
        s["works"] = 0.2f + max(wLack, sLack) * 1.2f + (if (villagers.size >= popCap() - 1) 0.3f else 0f) - (if (lastPolicy == "works") 0.5f else 0f)
        s["watch"] = (if (recent) 0.55f else 0.05f) + (if (campUp) 0.3f else 0f) + (if (ad >= 6 && count("tower") == 0) 0.25f else 0f) - (if (lastPolicy == "watch") 0.5f else 0f)
        s["lore"] = if (count("library") > 0 && nextTech() != null) 0.5f - (if (lastPolicy == "lore") 0.5f else 0f) else 0f
        s["cheer"] = (if (avgHappy() < 70f) (70f - avgHappy()) / 40f + 0.1f else 0.12f) - (if (lastPolicy == "cheer") 0.5f else 0f)
        return s
    }

    private fun policyOfJob(job: String): String = when (job) {
        "Farmer", "Forager" -> "harvest"
        "Woodcutter", "Miner", "Builder", "Bronzesmith", "Blacksmith" -> "works"
        "Hunter", "Scout" -> "watch"
        else -> "lore"
    }

    private fun councilSeatTile(): Int {
        val cx = councilX.toInt(); val cy = councilY.toInt()
        for (k in 0 until 14) {
            val x = cx + rnd.nextInt(7) - 3; val y = cy + rnd.nextInt(5) - 2
            if (abs(x - cx) <= 1 && abs(y - cy) <= 1) continue
            if (walkableTile(x, y)) return y * GW + x
        }
        return if (walkableTile(cx, cy)) cy * GW + cx else -1
    }

    /** A line grounded in the real state of the store, the stocks or the watch. */
    private fun councilSpeech(v: Villager): String = when (policyOfJob(v.job)) {
        "harvest" -> "We hold $food food for ${villagers.size} mouths."
        "works" -> "The store holds $wood wood and $stone stone."
        "watch" -> if (time - lastThreatAt < DAY_LEN * 1.5) "Beasts were at our gate not long ago." else "${count("tower")} towers keep the watch."
        else -> "Spirits in the village are at ${avgHappy().toInt()} out of 100."
    }

    private fun policyMetric(): Float = when (policy) {
        "harvest" -> food.toFloat()
        "works" -> (wood + stone).toFloat()
        "watch" -> buried.toFloat()
        "lore" -> research.toFloat()
        "cheer" -> avgHappy()
        else -> 0f
    }

    private fun policyAction(v: Villager): Pair<String, String>? {
        if (isChild(v)) return null
        val fighter = isFighter(v) || v.job == "Scout"
        val hasShore = nearestShore(v) >= 0
        return when (policy) {
            "harvest" -> when {
                v.job == "Farmer" && age >= 1 && count("farm") > 0 -> "tend_farm" to ""
                v.job == "Hunter" -> "hunt" to ""
                millOk(v) && rnd.nextInt(3) == 0 -> "grind" to ""
                hasShore && rnd.nextBoolean() -> "fish" to ""
                else -> "gather_food" to ""
            }
            "works" -> when {
                v.job == "Miner" && quarryOk(v) -> "quarry" to ""
                wood <= stone * 2 && v.job != "Miner" -> "gather_wood" to ""
                else -> "gather_stone" to ""
            }
            "watch" -> when {
                fighter && count("barracks") > 0 -> "drill" to ""
                fighter -> "patrol" to ""
                else -> null
            }
            "lore" -> if (count("library") > 0 && v.hunger < 60f) "study" to "" else null
            "cheer" -> when {
                count("tavern") > 0 && food >= 3 -> "tavern" to ""
                v.job == "Storyteller" -> "tell_story" to ""
                count("campfire") > 0 -> "dance" to ""
                else -> null
            }
            else -> null
        }
    }

    private fun councilTick() {
        if (policy.isNotEmpty() && time >= policyUntil) endPolicy()
        if (councilOn) {
            val want = min(8, max(3, adults() * 7 / 10))
            if (time >= councilUntil || storm() || isNight() || (councilGuestIds.size >= want && time - councilStart > 24.0)) concludeCouncil()
            return
        }
        if (age < 1 || time < councilNextAt) return
        if (councilNextAt <= 0.0) { councilNextAt = time + DAY_LEN * 0.8; return }
        val f = frac()
        if (f < 0.12 || f > 0.42) return
        if (adults() < 5 || storm() || policy.isNotEmpty() && time < policyUntil) return
        val hub = buildings.firstOrNull { it.type == "hall" } ?: buildings.firstOrNull { it.type == "campfire" } ?: return
        if (predators.any { hypot(it.x - hub.x, it.y - hub.y) < 13f }) return
        var spot = -1
        var bestD = 999f
        for (dy in -7..7) for (dx in -9..9) {
            val x = hub.x + dx; val y = hub.y + dy
            if (x < 1 || y < 1 || x >= GW - 1 || y >= GH - 1) continue
            val d = hypot(dx.toFloat(), dy.toFloat())
            if (d < 2.5f || d >= bestD) continue
            if (!walkableTile(x, y) || tiles[y * GW + x] == T.FOREST) continue
            if (buildings.any { abs(it.x - x) <= 1 && abs(it.y - y) <= 1 }) continue
            bestD = d; spot = y * GW + x
        }
        if (spot < 0) { councilNextAt = time + 30.0; return }
        councilOn = true
        councilX = (spot % GW) + 0.5f
        councilY = (spot / GW) + 0.5f
        councilStart = time
        councilUntil = time + 55.0
        councilGuestIds.clear()
        wx("council")
        event("A bell rings: the village council meets at the ${councilHubName()} to choose what the village does next.")
    }

    // ---- Age of Gold: golden decrees after the Golden Age begins ----

    fun decreeTitle(k: String = decreeKind): String = when (k) { "bounty" -> "Day of Bounty"; "valor" -> "Day of Valor"; "wisdom" -> "Day of Wisdom"; "craft" -> "Day of Craft"; else -> "Day of Revels" }

    fun decreeOn(): Boolean = decreeKind.isNotEmpty() && time < decreeUntil

    private fun gloryTick() {
        if (goldenAt < 0.0 || villagers.size < 4) return
        if (decreeKind.isNotEmpty() && time >= decreeUntil) decreeKind = ""
        if (gloryNextAt <= 0.0) gloryNextAt = max(time, goldenAt) + DAY_LEN * 0.3
        if (time < gloryNextAt || isNight() || storm()) return
        gloryNextAt = time + DAY_LEN * (0.9 + rnd.nextFloat() * 0.5)
        val fs = capFood().toFloat(); val ws = capWood().toFloat(); val ss = capStone().toFloat()
        val low = min(food / fs, min(wood / ws, stone / ss)).coerceIn(0f, 1f)
        val sc = LinkedHashMap<String, Float>()
        sc["bounty"] = 0.25f + (1f - low) * 1.1f
        sc["valor"] = 0.3f + (if (time - lastThreatAt < DAY_LEN * 1.5) 0.5f else 0f)
        sc["wisdom"] = if (techs.size < allTechs.size) 0.3f + (1f - techs.size.toFloat() / allTechs.size) * 0.6f else 0.05f
        sc["craft"] = 0.25f + (if (goods < 60) 0.35f else 0f)
        sc["revels"] = 0.2f + (100f - avgHappy()).coerceAtLeast(0f) / 30f
        var best = "revels"; var bv = -9f
        for ((k, b) in sc) { val x = b + rnd.nextFloat() * 0.3f - (if (k == lastDecree) 0.7f else 0f); if (x > bv) { bv = x; best = k } }
        decrees++
        decreeKind = best; lastDecree = best; decreeKinds.add(best)
        decreeStart = time; decreeUntil = time + DAY_LEN * 0.4
        val grand = decrees % 5 == 0
        val ad = villagers.filter { !isChild(it) }
        val line = when (best) {
            "bounty" -> {
                val kind = if (food / fs <= wood / ws && food / fs <= stone / ss) "food" else if (wood / ws <= stone / ss) "wood" else "stone"
                val amt = max(40, ((if (kind == "food") fs else if (kind == "wood") ws else ss) * 0.08f).toInt())
                val before = when (kind) { "food" -> food; "wood" -> wood; else -> stone }
                when (kind) { "food" -> food = min(capFood(), food + amt); "wood" -> wood = min(capWood(), wood + amt); else -> stone = min(capStone(), stone + amt) }
                "the gilded granaries open: $kind stood at $before, +$amt arrives from the tribute carts"
            }
            "valor" -> {
                for (v in ad) v.bump("xp_combat", 14)
                "${ad.size} grown villagers drill under the golden banner and sharpen their Fighting"
            }
            "wisdom" -> {
                val gain = 60 + 15 * count("library")
                research += gain
                "scholars are honoured and the library gains $gain lore (${techs.size} of ${allTechs.size} discoveries made)"
            }
            "craft" -> {
                goods += 25; metal += 2
                "workshops shine: +25 goods and 2 metal for the gilded crafts"
            }
            else -> {
                festivalUntil = max(festivalUntil, time + 70.0)
                for (v in villagers) v.happiness = min(100f, v.happiness + 12f)
                "spirits stand at ${avgHappy().toInt()} out of 100 and the village dances in the gold light"
            }
        }
        wx("glory")
        event("Decree of the Golden Age, ${decreeTitle()}: $line.")
        if (grand) {
            gildings++
            goods += 20; metal += 3
            festivalUntil = max(festivalUntil, time + 60.0)
            for (v in villagers) v.happiness = min(100f, v.happiness + 8f)
            event("Grand Gilding! Every roof of the hall quarter is dipped in gold: +20 goods, +3 metal and cheers for all (gilding ${gildings}).")
        }
        mark("Golden decree: ${decreeTitle()}.")
    }

    private fun concludeCouncil() {
        councilOn = false
        councilNextAt = time + DAY_LEN * 2.0
        val seated = villagers.filter { it.id in councilGuestIds }
        if (seated.size < 2) { addLog("The council bell rings but hardly anyone comes; no priority is chosen."); return }
        councilsHeld++
        val sc = policyScores()
        val votes = HashMap<String, Int>()
        for (v in seated) {
            var best = "harvest"; var bv = -1f
            for ((k, base) in sc) {
                val jobBias = if (policyOfJob(v.job) == k) 0.35f else 0f
                val sc2 = base + jobBias + rnd.nextFloat() * 0.25f
                if (sc2 > bv) { bv = sc2; best = k }
            }
            votes[best] = (votes[best] ?: 0) + 1
        }
        val win = votes.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { sc[it.key] ?: 0f }).first()
        policy = win.key
        lastPolicy = policy
        policyVotes = win.value
        policyOf = seated.size
        policyStart = time
        policyUntil = time + DAY_LEN * 1.2
        policyBase = policyMetric()
        policyKinds.add(policy)
        val why = when (policy) {
            "harvest" -> "food stands at $food"
            "works" -> "the stores hold $wood wood and $stone stone"
            "watch" -> if (time - lastThreatAt < DAY_LEN * 1.5) "beasts came to the village lately" else "the watch must stay sharp"
            "lore" -> "${techs.size} of ${allTechs.size} discoveries are made"
            else -> "spirits stand at ${avgHappy().toInt()} out of 100"
        }
        event("The council votes ${policyTitle()} ($why): ${policyVotes} of ${policyOf} agree to ${policyBlurb()}.")
        mark("The council chose ${policyTitle()}.")
    }

    private fun endPolicy() {
        val kind = policy
        val title = policyTitle()
        val now = policyMetric()
        val ok = when (kind) {
            "harvest" -> now >= policyBase + 8f
            "works" -> now >= policyBase + 14f
            "watch" -> now <= policyBase
            "lore" -> now >= policyBase + 4f
            "cheer" -> now >= policyBase + 2f
            else -> false
        }
        policy = ""
        if (ok) {
            policiesWon++
            for (v in villagers) v.happiness = min(100f, v.happiness + 4f)
            when (kind) {
                "harvest" -> addLog("The council's $title plan worked: food rose from ${policyBase.toInt()} to ${now.toInt()}.")
                "works" -> addLog("The council's $title plan worked: wood and stone rose from ${policyBase.toInt()} to ${now.toInt()}.")
                "watch" -> addLog("The council's $title plan worked: nobody was laid in the graveyard.")
                "lore" -> addLog("The council's $title plan worked: the scholars gained ${(now - policyBase).toInt()} lore.")
                else -> addLog("The council's $title plan worked: spirits rose to ${now.toInt()} out of 100.")
            }
        } else {
            policiesLost++
            addLog("The council's $title plan fell short; the village goes back to its old rhythm.")
        }
    }

    fun feteTitle(): String = when (feteKind) {
        "maypole" -> "spring maypole dance"
        "bonfire" -> "midsummer bonfire"
        "harvest" -> "harvest table feast"
        else -> "midwinter lantern night"
    }

    /** The nearest child a grown-up could teach right now, or null. */
    private fun teachChild(v: Villager): Villager? {
        if (isChild(v) || v.job !in JOBS || v.hp < 50f || alert() || storm() || isNight()) return null
        return villagers.filter { isChild(it) && it.phase != "sleeping" && hypot(it.x - v.x, it.y - v.y) < 28f }.minByOrNull { hypot(it.x - v.x, it.y - v.y) }
    }

    private fun feteOk(): Boolean = feteOn && !alert() && !storm() && !isNight() && time < feteUntil

    /** A walkable tile close to the fete site, so the dancers ring the prop instead of stacking on it. */
    private fun feteRevelTile(): Int {
        val cx = feteX.toInt(); val cy = feteY.toInt()
        for (k in 0 until 12) {
            val x = cx + rnd.nextInt(5) - 2; val y = cy + rnd.nextInt(5) - 2
            if (x == cx && y == cy) continue
            if (walkableTile(x, y)) return y * GW + x
        }
        return if (walkableTile(cx, cy)) cy * GW + cx else -1
    }

    private fun feteTick() {
        if (feteOn) {
            if (time >= feteUntil || storm()) endFete()
            return
        }
        val d = (time / DAY_LEN).toInt()
        val sn = d / SEASON_DAYS
        if (sn == lastFeteSeason || d % SEASON_DAYS != SEASON_DAYS - 1) return
        val f = frac()
        if (f < 0.28 || f > 0.52) return
        if (villagers.size < 4 || storm() || (alert() && predators.any { isThreat(it.kind) })) return
        val hub = buildings.firstOrNull { it.type == "campfire" } ?: buildings.firstOrNull { it.type == "hall" } ?: return
        var spot = -1
        for (k in 0 until 40) {
            val x = hub.x + rnd.nextInt(15) - 7
            val y = hub.y + rnd.nextInt(11) - 5
            if (!walkableTile(x, y) || tiles[y * GW + x] == T.FOREST) continue
            if (buildings.any { abs(it.x - x) <= 2 && abs(it.y - y) <= 2 }) continue
            if (seasonProps.any { abs(it.x - x) < 2.5f && abs(it.y - y) < 2f }) continue
            spot = y * GW + x; break
        }
        if (spot < 0) return
        lastFeteSeason = sn
        feteOn = true
        feteKind = when (seasonIdx()) { 0 -> "maypole"; 1 -> "bonfire"; 2 -> "harvest"; else -> "lantern" }
        feteX = (spot % GW) + 0.5f
        feteY = (spot / GW) + 0.5f
        feteStart = time
        feteUntil = time + 125.0
        feteGuestIds.clear()
        fetesHeld++
        feteKinds.add(feteKind)
        wx("fete")
        event("The villagers raise a ${when (feteKind) { "maypole" -> "ribboned maypole"; "bonfire" -> "great midsummer bonfire"; "harvest" -> "long harvest table"; else -> "tree of glowing lanterns" }} on the green. Come and celebrate the ${feteTitle()}!")
        mark("The ${feteTitle()} was held.")
    }

    private fun endFete() {
        feteOn = false
        val n = feteGuestIds.size
        if (n == 0) { addLog("The ${feteTitle()} passes quietly; nobody came."); return }
        val gift = min(30, 4 + n * 2)
        val reward = when (feteKind) {
            "maypole" -> { food += gift; "+$gift food in shared gifts" }
            "bonfire" -> { for (v in villagers) v.happiness = min(100f, v.happiness + 6f); "spirits soar" }
            "harvest" -> { food += gift / 2; goods = min(30, goods + 2); "+${gift / 2} food, +2 goods" }
            else -> { for (v in villagers) v.hp = min(100f, v.hp + 10f); "everyone feels mended" }
        }
        event("The ${feteTitle()} ends. $n villagers took part ($reward).")
    }

    private fun weatherTick() {
        val s = seasonIdx()
        feteTick()
        if (lastSeason < 0) lastSeason = s
        else if (s != lastSeason) { lastSeason = s; seasonChange(s) }
        if (time >= nextWeatherAt) {
            nextWeatherAt = time + 160.0 + rnd.nextInt(160)
            if (villagers.size >= 2 && !raining() && !foggy() && !snowing()) rollWeather(s)
        }
        if (droughtOn) {
            if (raining() && time < droughtUntil) { droughtUntil = time; droughtRainEnds++ }
            if (!drought()) {
                droughtOn = false; droughtsEnded++
                val byRain = raining()
                for (v in villagers) v.happiness = min(100f, v.happiness + (if (byRain) 8f else 4f))
                event(if (byRain) "Rain at last! The dry spell breaks and the fields drink deep." else "The dry spell ends and green returns to the fields.")
            }
        }
        if (time < stormUntil && time >= nextStrikeAt) { nextStrikeAt = time + 7.0 + rnd.nextInt(14); lightningStrike() }
        if (wasStorm && time >= stormUntil) {
            wasStorm = false
            if (!isNight() && villagers.size >= 2) {
                rainbowUntil = time + 70.0
                for (v in villagers) v.happiness = min(100f, v.happiness + 4f)
                wx("rainbow")
                event("A rainbow arcs over the village after the storm.")
            }
        }
    }

    fun tick(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.1f)
        time += dt
        weatherTick()
        councilTick()
        gloryTick()
        starTick()
        fireTick(dt)
        drain()

        for (v in villagers.toList()) update(v, dt)
        predatorTick(dt)
        fauna.tick(dt)

        bushClock += dt
        if (bushClock > 30f) {
            bushClock = 0f
            for (i in traffic.indices) if (traffic[i] > 0f) traffic[i] = max(0f, traffic[i] - 0.4f)
            if (age >= 2 && rnd.nextBoolean()) ensure(T.ORE, 5, 6)
            for (i in tiles.indices) {
                if (tiles[i] == T.BUSH && amt[i] < 3 && (seasonIdx() != 3 || i % 3 == 0)) amt[i]++
            }
            if (rnd.nextInt(3) == 0) {
                val i = rnd.nextInt(GW * GH)
                if (tiles[i] == T.GRASS && !occupied[i] && i % GW > 2 && i / GW > 2) {
                    val x = i % GW
                    val y = i / GW
                    val hasTree = (-2..2).any { dy -> (-2..2).any { dx ->
                        val xx = x + dx
                        val yy = y + dy
                        xx in 0 until GW && yy in 0 until GH && tiles[yy * GW + xx] == T.FOREST
                    } }
                    if (hasTree) {
                        tiles[i] = T.FOREST
                        amt[i] = 3
                    }
                }
            }
        }

        for (b in buildings) {
            if (b.type != "farm") continue
            var interval = (if (time < b.boostUntil) 12f else 40f) / ageInfo().farm / (when (seasonIdx()) { 0 -> 1.1f; 2 -> 1.4f; 3 -> 0.65f; else -> 1f })
            if (toolBonus() > 0) interval *= 0.8f
            if (raining()) interval *= 0.7f
            b.timer += dt
            if (b.timer >= interval) {
                b.timer = 0f
                b.ready++
                if (b.ready >= 4) {
                    food += b.ready
                    b.ready = 0
                }
            }
        }

        if (time - lastBirth > 200.0 && villagers.size < min(popCap(), count("hut") * 2 + 4) && food >= 6) {
            val ps = villagers.filter { !isChild(it) && ageYears(it) in 6..45 && it.hunger < 70f }
            if (ps.size >= 2) {
                lastBirth = time
                val couple = ps.filter { p -> p.partner >= 0 && ps.any { it.id == p.partner } }
                val a = if (couple.isNotEmpty() && rnd.nextInt(5) != 0) couple[rnd.nextInt(couple.size)] else ps[rnd.nextInt(ps.size)]
                val b = ps.firstOrNull { it.id == a.partner }
                    ?: ps.filter { it !== a && it.partner < 0 }.ifEmpty { ps.filter { it !== a } }.maxByOrNull { (a.affinity[it.id] ?: 0) * 2 + rnd.nextInt(4) }!!
                val n = spawnVillager(a, b)
                a.happiness = min(100f, a.happiness + 12f)
                b.happiness = min(100f, b.happiness + 12f)
                a.remember("welcomed baby ${n.name}")
                b.remember("welcomed baby ${n.name}")
                addLog("${a.name} and ${b.name} welcome a baby, ${n.name}!")
                babiesBorn++
                mark("${n.name} was born to ${a.name} and ${b.name}.")
                narrate("A baby is born: ${n.name}")
            }
        }

        ageTick(dt)
        planTick()
        threatTick()
        trollTick()
        spiderTick(dt)
        expeditionTick(dt)
        campTick(dt)
        visitorTick(dt)
        wonderTick(dt)
        cathTick()
        infirmaryTick()
        researchTick()
        statueTick()
        goldenTick()
        deedTick()
        relationTick()
        petTick()
        dreamTick(dt)
        critters.tick(dt)
        capStores()
        eventTick()
        worldTick(dt)
        schedule()
    }

    private fun update(v: Villager, dt: Float) {
        val asleep = v.phase == "sleeping"
        v.hunger = min(100f, v.hunger + (if (asleep) 0.12f else 0.33f) * dt)
        if (!asleep) v.energy = max(0f, v.energy - 0.2f * dt)
        if (v.hunger >= 90f && food >= 1) {
            // a famished villager grabs a bite from the store even when asleep or deep in work, so nobody starves beside a full larder
            food -= 1
            v.hunger = max(0f, v.hunger - 45f)
            hungerSnacks++
            v.thought = "that was a close one, I was famished"
            if (hungerSnacks <= 2 || hungerSnacks % 15 == 0) addLog("${v.name} was famished and wolfs down a bite from the stores.")
        }
        if (v.hunger >= 100f) v.hp -= 1.2f * dt else if (v.hunger < 70f && v.hp < 100f) v.hp = min(100f, v.hp + 0.4f * dt)
        v.happiness += (55f - v.happiness) * 0.01f * dt
        if (v.hunger > 85f) v.happiness -= 0.5f * dt
        v.happiness = v.happiness.coerceIn(0f, 100f)
        if (v.hp <= 0f) {
            villagers.remove(v)
            loseBonds(v)
            if (deathNotes.size < 30) deathNotes.add("${v.name}:${v.job}:${v.action}/${v.phase}:kid=${isChild(v)}:fireD=${buildings.filter { it.type == "campfire" || it.type == "tower" || it.type == "hut" }.minOfOrNull { hypot(it.x - v.x, it.y - v.y) }?.toInt()}:foes=${predators.map { it.kind }}")
            if (v.harmedBy.isNotEmpty() && time - v.harmedAt > 45.0 && v.hunger >= 99f) v.harmedBy = ""
            deathCauses[if (v.harmedBy.isNotEmpty()) v.harmedBy else "starved"] = (deathCauses[if (v.harmedBy.isNotEmpty()) v.harmedBy else "starved"] ?: 0) + 1
            buryVillager(v, if (v.harmedBy.isNotEmpty()) "killed by a ${v.harmedBy}" else "starved")
            if (v.harmedBy.isNotEmpty()) {
                event("${v.name} the ${v.job.lowercase()} was killed by a ${v.harmedBy}. The village mourns.")
                mark("${v.name} the ${v.job.lowercase()} was killed by a ${v.harmedBy}.")
            } else {
                event("${v.name} the ${v.job.lowercase()} starved. The village mourns.")
            }
            return
        }
        if (isChild(v) && ageYears(v) >= 3) growUp(v)
        if (ageYears(v) >= v.lifespan) {
            villagers.remove(v)
            loseBonds(v)
            buryVillager(v, "old age")
            for (o in villagers) o.happiness = max(0f, o.happiness - 8f)
            event("${v.name} the ${v.job.lowercase()} passed away peacefully at ${ageYears(v)}.")
            mark("${v.name} the ${v.job.lowercase()} passed away at ${ageYears(v)}.")
            return
        }
        if (v.energy <= 0f && !asleep) {
            v.action = "sleep"
            v.phase = "sleeping"
            v.thought = "so tired…"
        }
        if (v.sayLeft > 0f) {
            v.sayLeft -= dt
            if (v.sayLeft <= 0f) v.say = ""
        }
        if (v.replyIn > 0f) {
            v.replyIn -= dt
            if (v.replyIn <= 0f && v.replyText.isNotBlank()) {
                val txt = Speech.clean(v.replyText, 90)
                v.say = txt
                v.sayLeft = 6f
                noteSaid(v, txt)
                val to = v.replyTo
                if (to != null) {
                    to.heard.add("${v.name}: $txt")
                    while (to.heard.size > 3) to.heard.removeAt(0)
                    to.remember("${v.name} answered: ${txt.take(50)}")
                    v.remember("answered ${to.name}: ${txt.take(40)}")
                    addLog("${v.name} to ${to.name}: $txt")
                }
                v.replyText = ""
                v.replyTo = null
            }
        }
        when (v.phase) {
            "idle" -> v.idleFor += dt
            "walking" -> move(v, dt)
            "working" -> work(v, dt)
            "sleeping" -> {
                val c = cellOf(v)
                val inHut = buildings.any { it.type == "hut" && it.y * GW + it.x == c }
                v.energy += (if (inHut) 3.2f else 1.1f) * dt
                if (v.energy >= 100f || (v.energy >= 85f && !isNight())) {
                    v.energy = min(100f, v.energy)
                    v.thought = "feeling rested"
                    if (inHut) v.happiness = min(100f, v.happiness + 5f)
                    finish(v)
                }
            }
        }
    }

    private fun move(v: Villager, dt: Float) {
        if (v.pathIdx >= v.path.size) {
            arrive(v)
            return
        }
        val cell = v.path[v.pathIdx]
        val tx = cell % GW + 0.5f
        val ty = cell / GW + 0.5f
        val dx = tx - v.x
        val dy = ty - v.y
        val dist = hypot(dx, dy)
        val step = SPEED * dt * webSlow(v) * (if (hasTech("roads")) 1.08f else 1f)
        if (abs(dx) > 0.01f) v.facing = if (dx > 0) 1 else -1
        if (dist <= step) {
            v.x = tx
            v.y = ty
            v.pathIdx++
            if (cell in traffic.indices && !isChild(v)) traffic[cell] = min(60f, traffic[cell] + 1f)
            if (v.pathIdx >= v.path.size) arrive(v)
        } else {
            v.x += dx / dist * step
            v.y += dy / dist * step
        }
    }

    // ---- saving ----

    fun save() {
        try {
            val o = JSONObject()
            o.put("seed", seed)
            o.put("time", time)
            o.put("wood", wood).put("stone", stone).put("food", food)
            o.put("chron", JSONArray(chronicle.takeLast(160)))
            o.put("ore", ore).put("metal", metal).put("smelted", totalSmelted).put("explored", explored).put("age", age)
            o.put("nextId", nextId)
            o.put("hides", hides)
            o.put("goods", goods)
            o.put("raids", raidsRepelled).put("ogres", ogresSlain).put("bosses", bossesSlain).put("warlords", warlordsSlain).put("campUp", if (campUp) 1 else 0).put("campX", campX.toDouble()).put("campY", campY.toDouble()).put("campHp", campHp.toDouble()).put("campMax", campMax.toDouble()).put("campR", campsRazed).put("campH", campHits).put("campS", campSorties).put("expN", expeditions).put("expW", expWins).put("expR", expRetreats).put("expRooms", roomsCleared).put("relics", relics).put("expDeep", deepestRoom).put("wardens", wardensSlain).put("golems", golemsSlain).put("frostPacks", frostPacksBroken).put("pilfF", pilfersFoiled).put("pilfL", pilfersLost).put("swarms", swarmsBroken).put("hornets", hornetsSwatted).put("liches", lichesSlain).put("bones", bonesRaised).put("reavers", reaversSlain).put("spiderS", spidersSlain).put("wormS", wormsSlain).put("wormE", wormErupts).put("wormH", wormHits).put("harpyS", harpiesSlain).put("harpyF", flocksScattered).put("harpyD", harpyDives).put("harpyL", harpyLoot).put("spiderW", websSpun).put("spiderC", webCatches).put("spiderB", spiderBites).put("trollS", trollsSlain).put("trollR", trollsStoned).put("trollB", trollBoulders).put("trollRocks", trollRocks.joinToString(",") { "${it[0].toInt()}:${it[1].toInt()}" }).put("ships", shipsSeized).put("landings", landings).put("shamans", shamansSlain).put("golden", goldenAt).put("visits", visitStats.entries.joinToString(",") { "${it.key}:${it.value}" }).put("deeds", deeds.joinToString(",")).put("tavern", tavernVisits).put("snacks", hungerSnacks).put("pets", pets.joinToString(",") { "${it.name}:${it.owner}:${it.bond}" }).put("dogA", dogsAdopted).put("dogW", dogWalks).put("dogB", dogBarks).put("fetes", fetesHeld).put("feteR", feteRevels).put("feteL", lastFeteSeason).put("feteK", feteKinds.joinToString(",")).put("snowmen", snowmenBuilt).put("leafR", leafPilesRaked).put("starsF", starsFallen).put("starG", starShardsGot).put("kites", kitesFlown).put("sprops", seasonProps.joinToString(",") { "${it.kind}:${it.x.toInt()}:${it.y.toInt()}" }).put("statues", statues.joinToString(";") { "${it.name}|${it.title}|${it.kind}|${it.x.toInt()}|${it.y.toInt()}|${it.tier}" }).put("statueC", statueCheers).put("graves", graves.joinToString(";") { "${it.name}|${it.job}|${it.x.toInt()}|${it.y.toInt()}|${it.ageYears}|${it.cause}|${it.day}" }).put("graveX", graveX).put("graveY", graveY).put("buried", buried).put("mournN", mournVisits).put("totemV", totemVisits).put("raised", raised).put("omens", omens).put("recov", recoveries).put("balS", ballistaShots).put("balK", ballistaKills).put("ranchV", ranchVisits).put("pastV", pastureVisits).put("sailN", sailTrips).put("millN", millGrinds).put("millF", flourMade).put("millL", loavesBaked).put("quarryN", quarryHauls).put("quarryS", quarryStone).put("quarryV", quarryVeins).put("vigN", vigils).put("vigP", vigilPoints).put("legCalls", legendCalls).put("dockC", dockCatch).put("bottles", bottlesFound).put("flockS", flockSheep).put("flockH", flockHens).put("eggsG", eggsGathered).put("fleece", fleeces).put("techs", techs.joinToString(",")).put("research", research).put("studyN", studyCount).put("babies", babiesBorn).put("dreams", dreamsDone).put("weds", weds).put("feudsS", feudsStarted).put("feudsE", feudsEnded).put("bffs", bffs.joinToString(",")).put("courting", courting.entries.joinToString(",") { "${it.key}@${it.value.toInt()}" }).put("wallN", wallsRaised).put("wallX", wallCx).put("wallY", wallCy).put("wallD", if (wallDone) 1 else 0).put("wallH", wallHoldSecs.toDouble()).put("lessons", lessonsGiven).put("appMade", apprenticesMade).put("apprentice", apprentice.entries.joinToString(";") { "${it.key}:${it.value}" }).put("cathX", cathX).put("cathY", cathY).put("cathW", cathWork).put("cathT", cathTrips).put("cathB", cathBells).put("jubU", jubileeUntil).put("honeyN", honeyHarvests).put("honeyJ", honeyJars).put("honeyS", beeStings).put("honeyM", meadFeasts).put("honeyT", honeyMark).put("soakN", soaks).put("soakH", soakHeals).put("soakP", soakParties).put("soakG", soakGossips).put("shotN", shotsFired).put("shotB", bullseyes).put("shotM", archeryMatches).put("shotBest", archeryBest).put("fruitN", fruitPicks).put("fruitB", fruitBaskets).put("fruitC", ciderFeasts).put("fruitF", fruitFalls).put("fruitT", fruitMark).put("councN", councilsHeld).put("councS", councilSeats).put("councNext", councilNextAt).put("policy", policy).put("policyU", policyUntil).put("policyS", policyStart).put("policyB", policyBase.toDouble()).put("policyV", policyVotes).put("policyO", policyOf).put("policyA", policyActs).put("policyW", policiesWon).put("policyL", policiesLost).put("policyK", policyKinds.joinToString(",")).put("skUp", skillUps).put("skB", skillBonusLoads).put("skM", skillMasters).put("fireS", firesStarted).put("fireD", firesDoused).put("fireW", fireDouses).put("fireB", fireBurnedOut).put("fireL", fireLoss).put("sideN", sideRooms).put("sideT", sideTraps).put("sideV", sideVaults).put("sideS", sideSprings).put("sideH", sideShrines).put("riftRal", riftRallies).put("kgPud", puddleJumps).put("kgFly", fireflyJars).put("kgHide", hideSeekGames).put("kgFound", hideFinds).put("knightD", knightDuels).put("toysG", toysGiven).put("gDec", decrees).put("gNext", gloryNextAt).put("gKind", decreeKind).put("gUntil", decreeUntil).put("gStart", decreeStart).put("gGild", gildings).put("gKinds", decreeKinds.joinToString(",")).put("gLast", lastDecree).put("dryN", droughts).put("dryE", droughtsEnded).put("dryW", droughtWaterings).put("dryS", droughtSaved).put("dryX", droughtWithered).put("dryR", droughtRainEnds)
            o.put("coaN", comingAges).put("coaG", comingGuests).put("coaF", comingGifts).put("coaP", comingParents)
            o.put("bellR", bellRings).put("bellA", bellAlarms).put("tvD", tavDice).put("tvW", tavWrestles).put("tvT", tavTales).put("tvS", tavSongs).put("tvN", tavNights).put("showN", shows).put("showG", grandShows).put("showA", showAudience).put("kilnN", kilnFires).put("kilnG", kilnGlazed).put("kilnC", kilnCheers).put("yardN", yardSessions).put("yardS", yardSpars).put("yardT", critters.yardTrained).put("yardL", critters.yardLevelUps).put("yardX", critters.yardXp).put("loomN", loomWeaves).put("loomK", loomCloaks)
            o.put("traffic", String(CharArray(traffic.size) { i -> '0' + min(9, (traffic[i] / 4f).toInt()) }))
            val ks = JSONArray()
            kingdoms.forEach {
                ks.put(
                    JSONObject().put("id", it.id).put("pop", it.pop).put("might", it.might.toDouble())
                        .put("fallen", it.fallen).put("fallenAt", it.fallenAt).put("name", it.name)
                )
            }
            o.put("kings", ks)
            val rs = JSONArray()
            rels.values.forEach { rs.put(JSONObject().put("a", it.a).put("b", it.b).put("kind", it.kind).put("until", it.until)) }
            o.put("rels", rs)
            o.put("tiles", String(CharArray(tiles.size) { i -> '0' + tiles[i] }))
            o.put("amt", String(CharArray(amt.size) { i -> '0' + min(9, amt[i]) }))
            val bs = JSONArray()
            buildings.forEach { bs.put(JSONObject().put("t", it.type).put("x", it.x).put("y", it.y).put("e", it.era)) }
            o.put("buildings", bs)
            val vs = JSONArray()
            villagers.forEach {
                vs.put(
                    JSONObject().put("id", it.id).put("name", it.name).put("job", it.job).put("p", it.personaIdx)
                        .put("happy", it.happiness.toDouble()).put("x", it.x.toDouble()).put("y", it.y.toDouble())
                        .put("hunger", it.hunger.toDouble()).put("energy", it.energy.toDouble())
                        .put("hp", it.hp.toDouble())
                        .put("born", it.born).put("life", it.lifespan).put("par", it.parents).put("partner", it.partner)
                        .put("st", it.st.entries.filter { e -> e.value != 0 }.joinToString(",") { e -> "${e.key}:${e.value}" })
                        .put("titles", it.titles.joinToString("|"))
                        .put("aff", it.affinity.entries.filter { e -> e.value != 0 }.joinToString(",") { e -> "${e.key}:${e.value.coerceIn(-20, 40)}" })
                )
            }
            o.put("villagers", vs)
            o.put("log", JSONArray(log.takeLast(20)))
            o.put("critters", critters.toJson())
            val tmp = File(saveFile.parentFile, saveFile.name + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(saveFile)
        } catch (e: Exception) {
            // Saving is best effort.
        }
    }

    private fun load(): Boolean {
        return try {
            if (!saveFile.exists()) return false
            val o = JSONObject(saveFile.readText())
            seed = o.getLong("seed")
            // a village from the smaller world is carried into the middle of the big one
            val oldTiles = o.getString("tiles")
            val migrate = oldTiles.length == OLD_GW * OLD_GH
            val mx = (GW - OLD_GW) / 2
            val my = GH / 2 - OLD_GH / 2
            if (migrate) generate()
            time = o.getDouble("time")
            wood = o.getInt("wood")
            stone = o.getInt("stone")
            food = o.getInt("food")
            ore = o.optInt("ore", 0)
            metal = o.optInt("metal", 0)
            totalSmelted = o.optInt("smelted", 0)
            explored = o.optInt("explored", 0)
            nextId = o.optInt("nextId", 1)
            val t = o.getString("tiles")
            val a = o.getString("amt")
            if (migrate) {
                if (a.length != OLD_GW * OLD_GH) return false
                for (y in 0 until OLD_GH) for (x in 0 until OLD_GW) {
                    val ni = (y + my) * GW + x + mx
                    tiles[ni] = t[y * OLD_GW + x] - '0'
                    amt[ni] = a[y * OLD_GW + x] - '0'
                }
            } else {
                if (t.length != tiles.size || a.length != amt.size) return false
                for (i in tiles.indices) {
                    tiles[i] = t[i] - '0'
                    amt[i] = a[i] - '0'
                }
            }
            val shx = if (migrate) mx else 0
            val shy = if (migrate) my else 0
            occupied.fill(false)
            wallAt.fill(false)
            buildings.clear()
            val bs = o.getJSONArray("buildings")
            for (i in 0 until bs.length()) {
                val b = bs.getJSONObject(i)
                addBuilding(b.getString("t"), b.getInt("x") + shx, b.getInt("y") + shy, b.optInt("e", 1))
            }
            age = o.optInt("age", if (buildings.any { it.type == "farm" }) 1 else 0)
            if (age >= 2 && tiles.none { it == T.ORE }) ensure(T.ORE, 5, 8)
            lastBirth = time
            chronicle.clear()
            val ch = o.optJSONArray("chron")
            if (ch != null) for (i in 0 until ch.length()) chronicle.add(ch.getString(i))
            villagers.clear()
            val vs = o.getJSONArray("villagers")
            for (i in 0 until vs.length()) {
                val j = vs.getJSONObject(i)
                val v = Villager(j.getInt("id"), j.getString("name"), j.optString("job", JOBS[i % JOBS.size]), j.optInt("p", i))
                v.happiness = j.optDouble("happy", 60.0).toFloat()
                v.x = j.getDouble("x").toFloat() + shx
                v.y = j.getDouble("y").toFloat() + shy
                v.hunger = j.getDouble("hunger").toFloat()
                v.energy = j.getDouble("energy").toFloat()
                v.hp = j.getDouble("hp").toFloat()
                v.lifespan = j.optInt("life", 55 + rnd.nextInt(15))
                v.born = j.optDouble("born", time - (16 + rnd.nextInt(15) + rnd.nextFloat()) * YEAR)
                v.parents = j.optString("par", "")
                v.partner = j.optInt("partner", -1)
                j.optString("st", "").split(",").forEach { pr ->
                    val kv = pr.split(":")
                    val n = if (kv.size == 2) kv[1].toIntOrNull() else null
                    if (n != null) v.st[kv[0]] = n
                }
                j.optString("titles", "").split("|").filter { it.isNotBlank() }.forEach { v.titles.add(it) }
                j.optString("aff", "").split(",").forEach { pr ->
                    val kv = pr.split(":")
                    if (kv.size == 2) { val k = kv[0].toIntOrNull(); val a = kv[1].toIntOrNull(); if (k != null && a != null) v.affinity[k] = a }
                }
                villagers.add(v)
            }
            val lg = o.optJSONArray("log")
            if (lg != null) for (i in 0 until lg.length()) log.add(lg.getString(i))
            hides = o.optInt("hides", 0)
            goods = o.optInt("goods", 0)
            harpiesSlain = o.optInt("harpyS", 0); flocksScattered = o.optInt("harpyF", 0); harpyDives = o.optInt("harpyD", 0); harpyLoot = o.optInt("harpyL", 0); skillUps = o.optInt("skUp", 0); skillBonusLoads = o.optInt("skB", 0); skillMasters = o.optInt("skM", 0); firesStarted = o.optInt("fireS", 0); firesDoused = o.optInt("fireD", 0); fireDouses = o.optInt("fireW", 0); fireBurnedOut = o.optInt("fireB", 0); fireLoss = o.optInt("fireL", 0); raidsRepelled = o.optInt("raids", 0); ogresSlain = o.optInt("ogres", 0); bossesSlain = o.optInt("bosses", 0); warlordsSlain = o.optInt("warlords", 0); golemsSlain = o.optInt("golems", 0); shamansSlain = o.optInt("shamans", 0); frostPacksBroken = o.optInt("frostPacks", 0); pilfersFoiled = o.optInt("pilfF", 0); pilfersLost = o.optInt("pilfL", 0); swarmsBroken = o.optInt("swarms", 0); hornetsSwatted = o.optInt("hornets", 0); lichesSlain = o.optInt("liches", 0); bonesRaised = o.optInt("bones", 0); reaversSlain = o.optInt("reavers", 0); spidersSlain = o.optInt("spiderS", 0); wormsSlain = o.optInt("wormS", 0); wormErupts = o.optInt("wormE", 0); wormHits = o.optInt("wormH", 0); websSpun = o.optInt("spiderW", 0); webCatches = o.optInt("spiderC", 0); spiderBites = o.optInt("spiderB", 0); trollsSlain = o.optInt("trollS", 0); trollsStoned = o.optInt("trollR", 0); trollBoulders = o.optInt("trollB", 0); trollRocks.clear(); o.optString("trollRocks", "").split(",").filter { it.contains(":") }.forEach { val q = it.split(":"); trollRocks.add(floatArrayOf(q[0].toFloat() + 0.5f, q[1].toFloat() + 0.5f)) }; shipsSeized = o.optInt("ships", 0); landings = o.optInt("landings", 0)
            visitStats.clear()
            for (e in o.optString("visits", "").split(",")) { val kv = e.split(":"); if (kv.size == 2) visitStats[kv[0]] = kv[1].toIntOrNull() ?: 0 }
            goldenAt = o.optDouble("golden", -1.0)
            droughts = o.optInt("dryN", 0); droughtsEnded = o.optInt("dryE", 0); droughtWaterings = o.optInt("dryW", 0); droughtSaved = o.optInt("dryS", 0); droughtWithered = o.optInt("dryX", 0); droughtRainEnds = o.optInt("dryR", 0)
            comingAges = o.optInt("coaN", 0); comingGuests = o.optInt("coaG", 0); comingGifts = o.optInt("coaF", 0); comingParents = o.optInt("coaP", 0)
            decrees = o.optInt("gDec", 0); gloryNextAt = o.optDouble("gNext", 0.0); decreeKind = o.optString("gKind", ""); decreeUntil = o.optDouble("gUntil", 0.0); decreeStart = o.optDouble("gStart", 0.0); gildings = o.optInt("gGild", 0); lastDecree = o.optString("gLast", ""); decreeKinds.clear(); o.optString("gKinds", "").split(",").filter { it.isNotBlank() }.forEach { decreeKinds.add(it) }
            campUp = o.optInt("campUp", 0) == 1; campX = o.optDouble("campX", 0.0).toFloat(); campY = o.optDouble("campY", 0.0).toFloat(); campMax = o.optDouble("campMax", 100.0).toFloat(); campHp = o.optDouble("campHp", campMax.toDouble()).toFloat(); campsRazed = o.optInt("campR", 0); campHits = o.optInt("campH", 0); campSorties = o.optInt("campS", 0)
            campNextAt = time + DAY_LEN; campSortieAt = time + DAY_LEN * 0.8; campHitAt = -999.0; campRazedAt = -999.0
            expeditions = o.optInt("expN", 0); expWins = o.optInt("expW", 0); expRetreats = o.optInt("expR", 0); roomsCleared = o.optInt("expRooms", 0); relics = o.optInt("relics", 0); deepestRoom = o.optInt("expDeep", 0); wardensSlain = o.optInt("wardens", 0); sideRooms = o.optInt("sideN", 0); sideTraps = o.optInt("sideT", 0); sideVaults = o.optInt("sideV", 0); sideSprings = o.optInt("sideS", 0); sideShrines = o.optInt("sideH", 0); riftRallies = o.optInt("riftRal", 0); puddleJumps = o.optInt("kgPud", 0); fireflyJars = o.optInt("kgFly", 0); hideSeekGames = o.optInt("kgHide", 0); hideFinds = o.optInt("kgFound", 0); knightDuels = o.optInt("knightD", 0); toysGiven = o.optInt("toysG", 0)
            deeds.clear(); o.optString("deeds", "").split(",").filter { it.isNotBlank() }.forEach { deeds.add(it) }
            babiesBorn = o.optInt("babies", 0); dreamsDone = o.optInt("dreams", 0)
            fetesHeld = o.optInt("fetes", 0); feteRevels = o.optInt("feteR", 0); lastFeteSeason = o.optInt("feteL", -1); feteKinds.clear(); o.optString("feteK", "").split(",").filter { it.isNotBlank() }.forEach { feteKinds.add(it) }; snowmenBuilt = o.optInt("snowmen", 0); leafPilesRaked = o.optInt("leafR", 0); starsFallen = o.optInt("starsF", 0); starShardsGot = o.optInt("starG", 0); kitesFlown = o.optInt("kites", 0); wallsRaised = o.optInt("wallN", 0); wallCx = o.optInt("wallX", -1); wallCy = o.optInt("wallY", -1); wallDone = o.optInt("wallD", 0) == 1; wallHoldSecs = o.optDouble("wallH", 0.0).toFloat(); wallPlan.clear()
            cathX = o.optInt("cathX", -1); cathY = o.optInt("cathY", -1); cathWork = o.optInt("cathW", 0); cathTrips = o.optInt("cathT", 0); cathBells = o.optInt("cathB", 0); jubileeUntil = o.optDouble("jubU", -1.0); markCath(); lessonsGiven = o.optInt("lessons", 0); apprenticesMade = o.optInt("appMade", 0); apprentice.clear(); o.optString("apprentice", "").split(";").filter { it.contains(":") }.forEach { e -> val k = e.substringBefore(":").toIntOrNull(); if (k != null) apprentice[k] = e.substringAfter(":") }
            seasonProps.clear()
            for (e in o.optString("sprops", "").split(",")) { val kv = e.split(":"); if (kv.size == 3) seasonProps.add(SeasonProp(kv[0], (kv[1].toIntOrNull() ?: 0) + 0.5f, (kv[2].toIntOrNull() ?: 0) + 0.5f, time)) }
            statues.clear(); statueCheers = o.optInt("statueC", 0)
            graves.clear(); graveX = o.optInt("graveX", -1); graveY = o.optInt("graveY", -1); buried = o.optInt("buried", 0); mournVisits = o.optInt("mournN", 0); lastBuryAt = -9999.0
            for (e in o.optString("graves", "").split(";")) { val kv = e.split("|"); if (kv.size == 7 && kv[0].isNotBlank()) graves.add(Grave(kv[0], kv[1], (kv[2].toIntOrNull() ?: 0) + 0.5f, (kv[3].toIntOrNull() ?: 0) + 0.5f, kv[4].toIntOrNull() ?: 0, kv[5], kv[6].toIntOrNull() ?: 0)) }
            for (e in o.optString("statues", "").split(";")) { val kv = e.split("|"); if (kv.size == 6 && kv[0].isNotBlank()) statues.add(Statue(kv[0], kv[1], kv[2], (kv[3].toIntOrNull() ?: 0) + 0.5f, (kv[4].toIntOrNull() ?: 0) + 0.5f, time, kv[5].toIntOrNull() ?: 0)) }
            tavernVisits = o.optInt("tavern", 0); hungerSnacks = o.optInt("snacks", 0); dogsAdopted = o.optInt("dogA", 0); dogWalks = o.optInt("dogW", 0); dogBarks = o.optInt("dogB", 0)
            pets.clear()
            for (e in o.optString("pets", "").split(",")) { val kv = e.split(":"); if (kv.size == 3 && kv[0].isNotBlank()) pets.add(Pet(kv[0], kv[1].toIntOrNull() ?: -1, kv[2].toIntOrNull() ?: 0, false)) }
             totemVisits = o.optInt("totemV", 0); raised = o.optInt("raised", 0); omens = o.optInt("omens", 0); recoveries = o.optInt("recov", 0); ballistaShots = o.optInt("balS", 0); ballistaKills = o.optInt("balK", 0); ranchVisits = o.optInt("ranchV", 0); pastureVisits = o.optInt("pastV", 0); sailTrips = o.optInt("sailN", 0); millGrinds = o.optInt("millN", 0); flourMade = o.optInt("millF", 0); loavesBaked = o.optInt("millL", 0); quarryHauls = o.optInt("quarryN", 0); quarryStone = o.optInt("quarryS", 0); quarryVeins = o.optInt("quarryV", 0); vigils = o.optInt("vigN", 0); vigilPoints = o.optInt("vigP", 0); legendCalls = o.optInt("legCalls", 0); dockCatch = o.optInt("dockC", 0); bottlesFound = o.optInt("bottles", 0); flockSheep = o.optInt("flockS", 0); flockHens = o.optInt("flockH", 0); eggsGathered = o.optInt("eggsG", 0); fleeces = o.optInt("fleece", 0); honeyHarvests = o.optInt("honeyN", 0); honeyJars = o.optInt("honeyJ", 0); beeStings = o.optInt("honeyS", 0); meadFeasts = o.optInt("honeyM", 0); honeyMark = o.optDouble("honeyT", 0.0); shotsFired = o.optInt("shotN", 0); soaks = o.optInt("soakN", 0); soakHeals = o.optInt("soakH", 0); soakParties = o.optInt("soakP", 0); soakGossips = o.optInt("soakG", 0); bullseyes = o.optInt("shotB", 0); archeryMatches = o.optInt("shotM", 0); archeryBest = o.optString("shotBest", ""); fruitPicks = o.optInt("fruitN", 0); fruitBaskets = o.optInt("fruitB", 0); ciderFeasts = o.optInt("fruitC", 0); fruitFalls = o.optInt("fruitF", 0); fruitMark = o.optDouble("fruitT", 0.0); councilsHeld = o.optInt("councN", 0); councilSeats = o.optInt("councS", 0); councilNextAt = o.optDouble("councNext", 0.0); policy = o.optString("policy", ""); policyUntil = o.optDouble("policyU", 0.0); policyStart = o.optDouble("policyS", 0.0); policyBase = o.optDouble("policyB", 0.0).toFloat(); policyVotes = o.optInt("policyV", 0); policyOf = o.optInt("policyO", 0); policyActs = o.optInt("policyA", 0); policiesWon = o.optInt("policyW", 0); policiesLost = o.optInt("policyL", 0); policyKinds.clear(); o.optString("policyK", "").split(",").filter { it.isNotBlank() }.forEach { policyKinds.add(it) }
            bellRings = o.optInt("bellR", 0); bellAlarms = o.optInt("bellA", 0); tavDice = o.optInt("tvD", 0); tavWrestles = o.optInt("tvW", 0); tavTales = o.optInt("tvT", 0); tavSongs = o.optInt("tvS", 0); tavNights = o.optInt("tvN", 0); shows = o.optInt("showN", 0); grandShows = o.optInt("showG", 0); showAudience = o.optInt("showA", 0); kilnFires = o.optInt("kilnN", 0); kilnGlazed = o.optInt("kilnG", 0); kilnCheers = o.optInt("kilnC", 0); yardSessions = o.optInt("yardN", 0); yardSpars = o.optInt("yardS", 0); critters.yardTrained = o.optInt("yardT", 0); critters.yardLevelUps = o.optInt("yardL", 0); critters.yardXp = o.optInt("yardX", 0); loomWeaves = o.optInt("loomN", 0); loomCloaks = o.optInt("loomK", 0)
            techs.clear(); o.optString("techs", "").split(",").filter { it.isNotBlank() }.forEach { techs.add(it) }
            research = o.optInt("research", 0); studyCount = o.optInt("studyN", 0)
            weds = o.optInt("weds", 0); feudsStarted = o.optInt("feudsS", 0); feudsEnded = o.optInt("feudsE", 0)
            bffs.clear(); o.optString("bffs", "").split(",").filter { it.contains("-") }.forEach { bffs.add(it) }
            courting.clear(); o.optString("courting", "").split(",").forEach { c -> val kv = c.split("@"); if (kv.size == 2) kv[1].toDoubleOrNull()?.let { courting[kv[0]] = it } }
            feudEnds.clear()
            for (a in villagers) for (b in villagers) if (a.id < b.id && (a.affinity[b.id] ?: 0) <= -3 && (b.affinity[a.id] ?: 0) <= -3) feudEnds[pairKey(a, b)] = time + DAY_LEN * 1.5
            traffic.fill(0f)
            val tr = o.optString("traffic", "")
            if (tr.length == traffic.size) for (i in traffic.indices) traffic[i] = (tr[i] - '0') * 4f
            critters.fromJson(o.optJSONObject("critters"), shx, shy)
            drops.clear()
            titlesEarned = villagers.sumOf { it.titles.size }
            rels.clear()
            rnd = Random(seed xor time.toLong())
            villagers.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val SYSTEM = """You play one villager in a small pixel-art village, as a distinct character with a job, a personality, a way of speaking and a dream. Reply with ONLY a JSON object, no other text:
{"thought": "<your private thought, first person, under 14 words, in your voice>", "action": "<one action>", "target": "<villager name, only for talk>", "say": "<one short sentence you say aloud in your own voice, or empty>"}
Actions: gather_wood, gather_stone, gather_food (berries), fish (at the lake), hunt (game in the forest, hunter-gatherers' best food), tend_farm (needs a farm, Farming Age on), explore (scouting far away), delve (join the rift expedition, only while a rift is open), tell_story (at the fire, lifts everyone's spirits), huddle (stay by the fire), build_hut (6 wood + 2 stone), build_farm (4 wood, Farming Age on), build_campfire (3 stone), mine_ore (Bronze Age on), smelt (2 ore + 1 wood at a forge, Bronze Age on), build_smelter (4 wood + 6 stone, Bronze Age on), build_well (4 wood + 6 stone), build_workshop (10 wood + 4 stone), build_market (12 wood + 6 stone), build_lamp (2 wood + 2 stone), fetch_water (at a well, waters the farms; during a drought it saves the crops, which wither without it), craft (at a workshop), trade (goods for food at a market), dance (at the fire), build_granary (14 wood + 6 stone), build_barracks (16 wood, 14 stone, 2 metal), build_library (18 wood + 12 stone), build_totem (12 wood + 4 stone, carve a totem pole), honor (bow and dance at the totem pole, lifts mood, may bring an omen of plenty), build_ranch (18 wood + 6 stone, a fenced critter ranch once the village keeps critters), tend_critters (feed and groom the village critters at the ranch: heals them, makes them stronger and fonder of us, needs a ranch), build_pasture (10 wood, a fenced pasture for sheep and hens, needs 5 villagers), tend_flock (gather eggs and shear sheep at the pasture: brings food and hides, needs a pasture), build_apiary (8 wood, straw beehives, needs 6 villagers), build_orchard (10 wood, fruit trees, needs 7 villagers), soak (rest in the hot spring if one stands: heals wounds and tiredness, cheers you), build_belfry (raise a bell tower), build_stage (16 wood + 4 stone, a puppet theatre, needs 8 villagers and Hut Age), build_kiln (12 wood + 6 stone, a potter's kiln, needs 7 villagers and Hut Age), train_critters (drill a critter you own, or a ranch critter, at the training yard by day: it gains levels; needs a yard and a critter), build_yard (14 wood + 4 stone, a critter training yard, needs 7 villagers and Hut Age), weave (weave hides into cloth at the loom by day, makes goods, and every third bolt is sewn into a woollen cloak for a villager; needs a loom and hides), build_loom (10 wood, Hut Age on, a weaver's loom beside the pasture; needs a pasture), fire_pots (fire clay pots in the kiln by day, uses 2 wood; makes goods, every fifth pot is a glazed masterwork that neighbours admire; needs a kiln), show (put on a puppet show at the theatre in fair daytime or at dusk: the onlookers laugh and cheer, every sixth show is a grand one, needs a theatre), ring_bell (ring the bell tower at dawn, dusk or when foes come: cheers the village or rallies the fighters), shoot (practise archery at the archery range in dry daytime; sharpens Fighting, bullseyes cheer you, contests every few bullseyes; needs a range), pick_fruit (pick ripe fruit at the orchard in daytime dry weather, summer and especially autumn, brings food and goods, needs an orchard with fruit ready), tend_bees (take ripe honey from the apiary in fair daytime weather outside winter: brings food and goods, needs an apiary with honey ready), council (take a seat at the village council while it meets at the hall or fire; adults only, the village votes its next priority), build_shrine (20 wood + 30 stone, City Age on, a Legend Shrine; once enough vigils are kept a legendary critter answers), vigil (kneel and keep a quiet vigil at the shrine in daytime, needs a shrine), build_mill (14 wood + 4 stone, a windmill beside the farms), grind (grind grain at the windmill into food and now and then bake bread, needs a windmill and a farm), build_quarry (16 wood, Village Age on, a stone quarry pit that supplies stone), quarry (cut blocks of stone at the quarry: brings plenty of stone, needs a quarry), build_dock (8 wood, a fishing pier at the lake, needs 4 villagers and a lake shore), sail (row a boat out from the dock for a big catch, daytime and fair weather only, needs a dock), teach (a grown-up shows a nearby child their trade in daytime: the child may follow it on growing up, needs a child in the village), raise_cathedral (City Age, 16+ villagers: haul spare wood and stone to raise the great cathedral in five tiers; the last tier starts a Jubilee), revel (join the seasonal fete on the green while one is on: dance round the maypole, midsummer bonfire, harvest table or midwinter lantern tree, lifts everyone's mood), build_tavern (20 wood + 10 stone, Town Age on), tavern (eat and chat with friends at the tavern, needs food, lifts mood), build_infirmary (16 wood + 8 stone, Town Age on), recover (rest and be bandaged at the infirmary: use when hurt, heals a lot), build_wonder (City Age, 50 wood + 40 stone + 5 metal: a great wonder that blesses the whole village), haul (fill the granary), drill (train at the barracks), study (read at the library; scholars earn lore that unlocks discoveries), attack (charge the enemy), patrol (walk the edge of the village and watch), play (children), carve (whittle wooden toys at the fire from spare wood; the children cheer up), pick_flowers (gather wildflowers and give them to a friend), forage_herbs (pick healing herbs: mends you and hurt people nearby, use when hurt or when others are hurt), swim (summer days at the lake, lifts mood), snowman (winter days, build a snowman near the fire, cheers the children), rake_leaves (autumn days, rake a pile of leaves the children jump in), fly_kite (spring days, fly a kite in the breeze), walk_dog (play fetch with your dog, only if you have one), assault_camp (storm the bandit camp beyond the hills, only while it stands; for the healthy and brave, it stops its raids when razed), stargaze (clear evenings at the fire, lifts mood), gather_star (gather shining starmetal from a fallen star's crater, only while the facts say a star fell; gives metal), douse (put out the forest fire with water, only while the facts say a wildfire burns; a bit dangerous, protects the woods and huts), puddle_jump (children only: splash in the puddles while it rains), catch_fireflies (children only: catch fireflies in a jar on a spring, summer or autumn evening), hide_seek (children only: hide in the bushes while friends look for you, they grow closer), eat (needs food in stock), sleep, talk (set target and say), wander. Use only the actions listed as available to you in the message.
Mostly do the work of your job, but react to your needs, the weather, wolves, news and what others say. Talk about what you have seen, feel or remember, with opinions and feelings. Never repeat a line or an action you just did, and vary your sentences. Stay alive: eat when hungry, sleep when tired or at night.
Speak only about facts given in the message: your needs, the stock, the weather, people nearby and what they said, family, village history and news. Never invent buildings, people, resources or events. Answer what others actually said to you. If you are a child, use very short, simple words."""
        private const val SYSTEM_SHORT = """Reply with ONLY a JSON object: {"thought": "", "action": "<one allowed action>", "target": "", "say": "<one short sentence using only the facts given, or empty>"}"""
    }
}
