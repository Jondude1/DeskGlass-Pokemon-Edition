package com.jondude1.deskglass.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Random

/** A sprite is drawn on a small grid, then polished to twice that size, so logical size is half the bitmap. */
val ImageBitmap.lw: Float get() = width / 2f
val ImageBitmap.lh: Float get() = height / 2f

/** A little "+1" that floats up from a villager who just gathered something. */
class Floater(val x: Float, val y: Float, val born: Double, val color: Int)

/** All the pixel art for the village, drawn from small text grids when the app starts. */
class CivArt {
    private fun sprite(rows: List<String>, pal: Map<Char, Int>, outline: Boolean = true, shade: Boolean = true): ImageBitmap {
        val h = rows.size
        var w = 1
        for (r in rows) if (r.length > w) w = r.length
        val src = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            src[y * w + x] = pal[rows[y].getOrElse(x) { '.' }] ?: 0
        }
        val big = PixelFx.polish(src, w, h, outline, shade)
        val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
        bmp.setPixels(big, 0, w * 2, 0, 0, w * 2, h * 2)
        return bmp.asImageBitmap()
    }

    // ---- ground, built in the background from the shape of the land ----
    @Volatile var ground: List<ImageBitmap>? = null
    @Volatile var groundSig = 0
    @Volatile var groundBusy = false

    /** Cells the life layer uses: open water, shoreline water, and plain grass. */
    var innerWater = IntArray(0)
    var shoreWater = IntArray(0)
    var grassCells = IntArray(0)
    var meadowCells = IntArray(0)

    fun groundImages(frames: Array<IntArray>, w: Int, h: Int): List<ImageBitmap> = frames.map {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(it, 0, w, 0, 0, w, h)
        bmp.asImageBitmap()
    }

    fun rebuildDecor(cw: Int, ch: Int, water: BooleanArray, grass: BooleanArray) {
        fun w(x: Int, y: Int) = x in 0 until cw && y in 0 until ch && water[y * cw + x]
        val inner = ArrayList<Int>()
        val shore = ArrayList<Int>()
        val gr = ArrayList<Int>()
        val meadow = ArrayList<Int>()
        for (y in 0 until ch) for (x in 0 until cw) {
            val i = y * cw + x
            if (water[i]) {
                var n = 0
                if (w(x - 1, y)) n++
                if (w(x + 1, y)) n++
                if (w(x, y - 1)) n++
                if (w(x, y + 1)) n++
                if (n == 4 && w(x - 1, y - 1) && w(x + 1, y - 1) && w(x - 1, y + 1) && w(x + 1, y + 1)) inner.add(i)
                else if (n in 1..3) shore.add(i)
            } else if (grass[i]) {
                gr.add(i)
                var ok = true
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= cw || ny >= ch || water[ny * cw + nx] || !grass[ny * cw + nx]) ok = false
                }
                if (ok) meadow.add(i)
            }
        }
        innerWater = inner.toIntArray()
        shoreWater = shore.toIntArray()
        grassCells = gr.toIntArray()
        meadowCells = meadow.toIntArray()
    }

    // ---- drop shadows: a black copy of each sprite, flattened and sheared in the draw ----
    val shadowPaint = android.graphics.Paint()
    val nightPaint = android.graphics.Paint()
    val lightPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
    }
    private val shadowCache = java.util.IdentityHashMap<ImageBitmap, Bitmap>()

    fun shadowOf(sp: ImageBitmap): Bitmap = shadowCache.getOrPut(sp) {
        val w = sp.width
        val h = sp.height
        val px = IntArray(w * h)
        sp.asAndroidBitmap().getPixels(px, 0, w, 0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(PixelFx.silhouette(px), 0, w, 0, 0, w, h)
        bmp
    }

    // ---- bits of life that remember a little between frames ----
    val lastGather = HashMap<Int, Int>()
    val floaters = ArrayList<Floater>()

    val oak = sprite(OAK, mapOf(
        'g' to 0xFF3E8E41.toInt(), 'G' to 0xFF2C6B33.toInt(), 'h' to 0xFF6BB85A.toInt(),
        't' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(),
    ))
    val pine = sprite(PINE, mapOf(
        'p' to 0xFF2F7F52.toInt(), 'P' to 0xFF1F5A3B.toInt(), 'q' to 0xFF4E9E6A.toInt(),
        't' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(),
    ))
    val stump = sprite(STUMP, mapOf('t' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(), 'c' to 0xFFD9B77E.toInt()))
    val rock = sprite(ROCK, mapOf('r' to 0xFF9AA0AA.toInt(), 'R' to 0xFFC9CED6.toInt(), 'd' to 0xFF6B707A.toInt()))
    private val bushPal = mapOf('g' to 0xFF3E8E41.toInt(), 'G' to 0xFF2C6B33.toInt(), 'h' to 0xFF6BB85A.toInt(), 'r' to 0xFFE0405A.toInt())
    val bush = sprite(BUSH, bushPal)
    val bushEmpty = sprite(BUSH.map { it.replace('r', 'g') }, bushPal)
    val hut = sprite(HUT, mapOf(
        'r' to 0xFFB5533C.toInt(), 'R' to 0xFF8F3E2C.toInt(), 'S' to 0xFFD97A5C.toInt(),
        'w' to 0xFFDDBB82.toInt(), 'W' to 0xFFBF9C63.toInt(), 'd' to 0xFF4A2E1B.toInt(),
        'k' to 0xFF9FD3F0.toInt(), 'b' to 0xFF8A6A3E.toInt(), 'c' to 0xFF6E6E78.toInt(),
    ))
    val hutStone = sprite(HUT, mapOf(
        'r' to 0xFF6F7A8C.toInt(), 'R' to 0xFF4F5868.toInt(), 'S' to 0xFF8E99AB.toInt(),
        'w' to 0xFFB9B9BF.toInt(), 'W' to 0xFF95959D.toInt(), 'd' to 0xFF3E2A1B.toInt(),
        'k' to 0xFF9FD3F0.toInt(), 'b' to 0xFF6B6B74.toInt(), 'c' to 0xFF6E6E78.toInt(),
    ))
    val townHouse = sprite(TOWNHOUSE, mapOf(
        'r' to 0xFFA04A38.toInt(), 'R' to 0xFF7C3A2B.toInt(), 'c' to 0xFF6E6E78.toInt(),
        'w' to 0xFFE7DCC2.toInt(), 'W' to 0xFFC9B99A.toInt(), 'k' to 0xFF9FD3F0.toInt(),
        'd' to 0xFF4A2E1B.toInt(), 'b' to 0xFF8D909C.toInt(),
    ))
    val cityHouse = sprite(CITYHOUSE, mapOf(
        'r' to 0xFF5B6B8C.toInt(), 'R' to 0xFF3F4C6B.toInt(), 'p' to 0xFF5A3A22.toInt(), 'f' to 0xFFD94F4F.toInt(),
        'w' to 0xFFEDE3CC.toInt(), 'W' to 0xFFCFC2A6.toInt(), 'k' to 0xFF9FD3F0.toInt(),
        'd' to 0xFF4A2E1B.toInt(), 'b' to 0xFF8D909C.toInt(),
    ))
    val hall = sprite(HALL, mapOf(
        'r' to 0xFFC9A04A.toInt(), 'R' to 0xFF9C7A2E.toInt(), 'p' to 0xFF5A3A22.toInt(), 'f' to 0xFF4F9BFF.toInt(),
        'w' to 0xFFA8743A.toInt(), 'W' to 0xFF86582A.toInt(), 'k' to 0xFFF2D88A.toInt(),
        'd' to 0xFF3B2412.toInt(), 'b' to 0xFF7A6A55.toInt(),
    ))
    val tent = sprite(TENT, mapOf(
        'p' to 0xFF6B4A2B.toInt(), 't' to 0xFFC9A26B.toInt(), 'T' to 0xFFA67E4B.toInt(), 'd' to 0xFF3B2A1A.toInt(),
    ))
    val oreRock = sprite(ORE, mapOf(
        'r' to 0xFF9AA0AA.toInt(), 'R' to 0xFFC9CED6.toInt(), 'd' to 0xFF6B707A.toInt(),
        'o' to 0xFFD98A3D.toInt(), 'O' to 0xFFF2D06B.toInt(),
    ))
    val forge = sprite(FORGE, mapOf(
        'c' to 0xFF4A4A52.toInt(), 'C' to 0xFF2E2E34.toInt(), 's' to 0xFF8A8A94.toInt(), 'S' to 0xFF62626C.toInt(),
        'O' to 0xFFFF7A21.toInt(), 'y' to 0xFFFFD84A.toInt(),
    ))
    val wolf = sprite(WOLF, mapOf(
        'w' to 0xFF8E8E98.toInt(), 'W' to 0xFFB8B8C2.toInt(), 'd' to 0xFF50505A.toInt(),
        'e' to 0xFFFF4040.toInt(), 'n' to 0xFF222222.toInt(),
    ))
    val bear = sprite(BEAR, mapOf(
        'b' to 0xFF6B4A2B.toInt(), 'B' to 0xFF4A3220.toInt(), 'e' to 0xFFFF3030.toInt(), 'n' to 0xFFC9A26B.toInt(),
    ))
    val boar = sprite(BOAR, mapOf(
        'b' to 0xFF6B4A33.toInt(), 'B' to 0xFF4A3322.toInt(), 'k' to 0xFF2A1C12.toInt(),
        'w' to 0xFFF2E6C8.toInt(), 'e' to 0xFFFF4040.toInt(), 'p' to 0xFFD99A8A.toInt(),
    ))
    val tower = sprite(TOWER, mapOf(
        's' to 0xFFB9BCC6.toInt(), 'S' to 0xFF8D909C.toInt(), 'k' to 0xFF2A2A34.toInt(),
        'w' to 0xFF8A5A2B.toInt(), 'W' to 0xFF6B4220.toInt(), 'r' to 0xFFB8452F.toInt(), 'R' to 0xFF8E3322.toInt(),
        'f' to 0xFFFFC34A.toInt(), 'd' to 0xFF4A2E1B.toInt(),
    ))
    val wall = sprite(WALL, mapOf(
        's' to 0xFFA9ADB8.toInt(), 'S' to 0xFF8A8E9B.toInt(), 'b' to 0xFFC6CAD4.toInt(), 'k' to 0xFF4A4C58.toInt(),
    ))
    val gate = sprite(GATE, mapOf(
        's' to 0xFFA9ADB8.toInt(), 'S' to 0xFF8A8E9B.toInt(), 'b' to 0xFFC6CAD4.toInt(), 'k' to 0xFF4A4C58.toInt(),
        'r' to 0xFFB8452F.toInt(), 'R' to 0xFF8E3322.toInt(), 'f' to 0xFFFF9A2E.toInt(), 'F' to 0xFFFFE070.toInt(), 'd' to 0xFF6B4220.toInt(),
    ))
    val palisade = sprite(PALISADE, mapOf(
        'w' to 0xFF9A6B38.toInt(), 'W' to 0xFF7A5028.toInt(), 'k' to 0xFF3A2614.toInt(),
    ))
    private val deerPal = mapOf('d' to 0xFFB98A52.toInt(), 'D' to 0xFFF2E3C4.toInt(), 'a' to 0xFF6B4A2B.toInt(), 'n' to 0xFF2A1C12.toInt())
    val deer = listOf(sprite(DEER1, deerPal), sprite(DEER2, deerPal))
    val farm = sprite(FARM, mapOf(
        'd' to 0xFF6B4A2B.toInt(), 'D' to 0xFF573A20.toInt(), 'g' to 0xFF7BC043.toInt(),
        'G' to 0xFFE8C547.toInt(), 'f' to 0xFF8B6B45.toInt(),
    ))

    // ---- seasonal looks (0 spring, 1 summer, 2 autumn, 3 winter): same grids, new palettes ----
    private val oakBase = mapOf(
        'g' to 0xFF3E8E41.toInt(), 'G' to 0xFF2C6B33.toInt(), 'h' to 0xFF6BB85A.toInt(),
        't' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(),
    )
    val oakS: List<ImageBitmap> = listOf(
        sprite(OAK, oakBase + mapOf('g' to 0xFF5BB04E.toInt(), 'G' to 0xFF3C8A40.toInt(), 'h' to 0xFFFFB9D6.toInt())),
        sprite(OAK, oakBase),
        sprite(OAK, oakBase + mapOf('g' to 0xFFD9822B.toInt(), 'G' to 0xFFA24A1E.toInt(), 'h' to 0xFFF2C14E.toInt())),
        sprite(OAK, oakBase + mapOf('g' to 0xFFE3ECF4.toInt(), 'G' to 0xFF9DB0C6.toInt(), 'h' to 0xFFFFFFFF.toInt())),
    )
    private val pineBase = mapOf(
        'p' to 0xFF2F7F52.toInt(), 'P' to 0xFF1F5A3B.toInt(), 'q' to 0xFF4E9E6A.toInt(),
        't' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(),
    )
    val pineS: List<ImageBitmap> = listOf(
        sprite(PINE, pineBase + mapOf('p' to 0xFF3A9A5A.toInt(), 'P' to 0xFF246E44.toInt(), 'q' to 0xFF7CCB8A.toInt())),
        sprite(PINE, pineBase),
        sprite(PINE, pineBase + mapOf('p' to 0xFF4C7A3C.toInt(), 'P' to 0xFF35592B.toInt(), 'q' to 0xFF8FA84A.toInt())),
        sprite(PINE, pineBase + mapOf('p' to 0xFF3C6B58.toInt(), 'P' to 0xFF274E40.toInt(), 'q' to 0xFFF2F7FB.toInt())),
    )
    private val bushBase = mapOf('g' to 0xFF3E8E41.toInt(), 'G' to 0xFF2C6B33.toInt(), 'h' to 0xFF6BB85A.toInt(), 'r' to 0xFFE0405A.toInt())
    private val bushPals: List<Map<Char, Int>> = listOf(
        bushBase + mapOf('g' to 0xFF5BB04E.toInt(), 'h' to 0xFF8CD070.toInt(), 'r' to 0xFFFF9AC8.toInt()),
        bushBase,
        bushBase + mapOf('g' to 0xFFB8742A.toInt(), 'G' to 0xFF8A4A1E.toInt(), 'h' to 0xFFE0A83C.toInt()),
        bushBase + mapOf('g' to 0xFFDCE6EF.toInt(), 'G' to 0xFF93A6BC.toInt(), 'h' to 0xFFFFFFFF.toInt()),
    )
    val bushS: List<ImageBitmap> = bushPals.map { sprite(BUSH, it) }
    val bushEmptyS: List<ImageBitmap> = bushPals.map { sprite(BUSH.map { r -> r.replace('r', 'g') }, it) }
    private val farmBase = mapOf(
        'd' to 0xFF6B4A2B.toInt(), 'D' to 0xFF573A20.toInt(), 'g' to 0xFF7BC043.toInt(),
        'G' to 0xFFE8C547.toInt(), 'f' to 0xFF8B6B45.toInt(),
    )
    val farmS: List<ImageBitmap> = listOf(
        sprite(FARM, farmBase + mapOf('g' to 0xFF9AD55A.toInt(), 'G' to 0xFFFFF2A8.toInt())),
        sprite(FARM, farmBase),
        sprite(FARM, farmBase + mapOf('g' to 0xFFC9A23A.toInt(), 'G' to 0xFFE0822B.toInt())),
        sprite(FARM, farmBase + mapOf('d' to 0xFF8A7A6A.toInt(), 'D' to 0xFF6E6054.toInt(), 'g' to 0xFFDCE6EF.toInt(), 'G' to 0xFFFFFFFF.toInt())),
    )
    val well = sprite(WELL, mapOf(
        'R' to 0xFFB5533C.toInt(), 'b' to 0xFF7A4B24.toInt(), 's' to 0xFF9AA0AA.toInt(), 'S' to 0xFF2B68AE.toInt(), 'd' to 0xFF6B707A.toInt(),
    ))
    val workshop = sprite(WORKSHOP, mapOf(
        'r' to 0xFF7C5A38.toInt(), 'R' to 0xFF5E4326.toInt(), 'w' to 0xFFC9A26B.toInt(), 'W' to 0xFFA67E4B.toInt(),
        'd' to 0xFF3B2412.toInt(), 'a' to 0xFF6E6E78.toInt(), 'A' to 0xFF4A4A52.toInt(), 'k' to 0xFF9FD3F0.toInt(),
        'y' to 0xFFE8C547.toInt(),
    ))
    val market = sprite(MARKET, mapOf(
        'r' to 0xFFC8483A.toInt(), 'f' to 0xFFF4F0E6.toInt(), 'b' to 0xFF8A6A3E.toInt(), 'B' to 0xFF6B4A2B.toInt(),
        'a' to 0xFFD94F4F.toInt(), 'g' to 0xFF7BC043.toInt(), 'y' to 0xFFE8C547.toInt(), 'o' to 0xFFE89A2B.toInt(),
    ))
    val lamp = sprite(LAMP, mapOf(
        'p' to 0xFF4A3A2A.toInt(), 'P' to 0xFF2E241A.toInt(), 'y' to 0xFFFFE070.toInt(), 'o' to 0xFFFFB347.toInt(),
        'k' to 0xFF2A2A34.toInt(),
    ))
    val granary = sprite(GRANARY, mapOf(
        'r' to 0xFFB8863F.toInt(), 'R' to 0xFF8E6529.toInt(), 'w' to 0xFFC9A26B.toInt(), 'W' to 0xFFA67E4B.toInt(),
        'd' to 0xFF3B2412.toInt(), 'p' to 0xFF6B4A2B.toInt(), 'y' to 0xFFE8C547.toInt(),
    ))
    val barracks = sprite(BARRACKS, mapOf(
        'r' to 0xFF7A3A2E.toInt(), 'R' to 0xFF572820.toInt(), 's' to 0xFFB9BCC6.toInt(), 'S' to 0xFF8D909C.toInt(),
        'd' to 0xFF3A2614.toInt(), 'f' to 0xFFC0392B.toInt(), 'k' to 0xFF2A2A34.toInt(), 'm' to 0xFFC9CED6.toInt(),
    ))
    val library = sprite(LIBRARY, mapOf(
        'r' to 0xFF3F5E8C.toInt(), 'R' to 0xFF2B4468.toInt(), 'w' to 0xFFE7DCC2.toInt(), 'W' to 0xFFC9B99A.toInt(),
        'd' to 0xFF4A2E1B.toInt(), 'k' to 0xFF9FD3F0.toInt(), 'y' to 0xFFF2D88A.toInt(), 'b' to 0xFF8D909C.toInt(),
    ))
    val shrine = sprite(SHRINE, mapOf(
        's' to 0xFFC9CBD6.toInt(), 'S' to 0xFF8F93A6.toInt(), 'y' to 0xFFFFE9A0.toInt(), 'g' to 0xFF9FF0FF.toInt(),
        'p' to 0xFFB07CFF.toInt(), 'k' to 0xFF3A3A4A.toInt(), 'm' to 0xFF4C7A3E.toInt(),
    ))
    val totem = sprite(TOTEM, mapOf(
        'w' to 0xFFB98A57.toInt(), 'W' to 0xFF8A6238.toInt(), 'r' to 0xFFC0392B.toInt(), 'b' to 0xFF3F7FBF.toInt(),
        'y' to 0xFFF2D04A.toInt(), 'k' to 0xFF2A1A10.toInt(), 'f' to 0xFF3FA060.toInt(), 'F' to 0xFF2A7A48.toInt(),
        's' to 0xFF9A9CA6.toInt(), 'S' to 0xFF737580.toInt(),
    ))
    val tavern = sprite(TAVERN, mapOf(
        'c' to 0xFF6E6E78.toInt(), 'r' to 0xFF8A4A2E.toInt(), 'R' to 0xFF6B3720.toInt(), 'w' to 0xFFEBDDBB.toInt(),
        'W' to 0xFFCDBB92.toInt(), 't' to 0xFF5A3A20.toInt(), 'y' to 0xFFF2D88A.toInt(), 'd' to 0xFF3B2412.toInt(),
        'g' to 0xFFC8923F.toInt(), 'm' to 0xFFE8C547.toInt(), 'b' to 0xFF8D909C.toInt(),
    ))
    val ranch = sprite(RANCH, mapOf(
        'r' to 0xFFB5483A.toInt(), 'R' to 0xFF8C3328.toInt(), 'w' to 0xFFC9975A.toInt(), 'W' to 0xFF9A6C3A.toInt(),
        'd' to 0xFF3B2412.toInt(), 'y' to 0xFFE8C547.toInt(), 'f' to 0xFFA9784A.toInt(), 'b' to 0xFF8D909C.toInt(),
    ))
    val pasture = sprite(PASTURE, mapOf(
        'y' to 0xFFD9B35A.toInt(), 'Y' to 0xFFB58A3C.toInt(), 'w' to 0xFFC9975A.toInt(), 'W' to 0xFF9A6C3A.toInt(),
        'd' to 0xFF3B2412.toInt(), 'h' to 0xFFE8C547.toInt(), 'f' to 0xFFA9784A.toInt(), 'b' to 0xFF8D909C.toInt(),
    ))
    val apiary = sprite(APIARY, mapOf(
        's' to 0xFFE0B040.toInt(), 'S' to 0xFFB5822A.toInt(), 'd' to 0xFF3B2412.toInt(), 'w' to 0xFFC9975A.toInt(),
        'W' to 0xFF8E6A3A.toInt(), 'g' to 0xFF5E9B45.toInt(), 'p' to 0xFFE06A9A.toInt(), 'y' to 0xFFF5D76E.toInt(),
        'v' to 0xFF9A6AD6.toInt(),
    ))
    val bath = sprite(BATH, mapOf(
        'r' to 0xFF8A8A92.toInt(), 'R' to 0xFF5E5E68.toInt(), 'w' to 0xFF5AB8D8.toInt(), 'W' to 0xFF9AE0F0.toInt(),
        't' to 0xFF7A4E26.toInt(), 'g' to 0xFF4F9E3F.toInt(), 'G' to 0xFF3A7A30.toInt(),
    ))
    val range = sprite(RANGE, mapOf(
        'r' to 0xFFC9382B.toInt(), 'h' to 0xFFF4EEDC.toInt(), 'y' to 0xFFF2D14E.toInt(), 't' to 0xFF7A4E26.toInt(),
        'g' to 0xFF4F9E3F.toInt(), 'G' to 0xFF3A7A30.toInt(), 'c' to 0xFFC9975A.toInt(), 'W' to 0xFF8E6A3A.toInt(),
    ))
    val stage = sprite(STAGE, mapOf(
        'r' to 0xFFB8302B.toInt(), 'R' to 0xFF7E1F22.toInt(), 'y' to 0xFFF2D14E.toInt(), 'b' to 0xFF4A6FB5.toInt(),
        'B' to 0xFF2E4A82.toInt(), 't' to 0xFFB98A4E.toInt(), 'W' to 0xFF6E4A24.toInt(),
        'g' to 0xFF4F9E3F.toInt(), 'G' to 0xFF3A7A30.toInt(),
    ))
    val fountain = sprite(FOUNTAIN, mapOf(
        's' to 0xFFD2D2D8.toInt(), 'S' to 0xFF8F8F9A.toInt(), 'b' to 0xFF4FA3E0.toInt(), 'h' to 0xFFD6F1FF.toInt(),
        'g' to 0xFF4F9E3F.toInt(), 'G' to 0xFF3A7A30.toInt(),
    ))
    val loom = sprite(LOOM, mapOf(
        'r' to 0xFFC8473A.toInt(), 'w' to 0xFFF0EBDD.toInt(), 'W' to 0xFF6E4A24.toInt(), 'y' to 0xFFE8C547.toInt(),
        'b' to 0xFF3F6FB5.toInt(), 'B' to 0xFF2E4F86.toInt(), 'm' to 0xFF7A4B9C.toInt(),
        'g' to 0xFF4F9E3F.toInt(), 'G' to 0xFF3A7A30.toInt(),
    ))
    val kiln = sprite(KILN, mapOf(
        'c' to 0xFFC8714A.toInt(), 'C' to 0xFF8E4A2E.toInt(), 'd' to 0xFF2A1E1A.toInt(), 'o' to 0xFFF29A38.toInt(),
        's' to 0xFF9A9AA2.toInt(), 'S' to 0xFF6C6C76.toInt(), 't' to 0xFFB98A4E.toInt(), 'W' to 0xFF6E4A24.toInt(),
        'p' to 0xFFD9A066.toInt(), 'b' to 0xFF4A82B5.toInt(),
        'g' to 0xFF4F9E3F.toInt(), 'G' to 0xFF3A7A30.toInt(),
    ))
    val smokehouse = sprite(SMOKEHOUSE, mapOf(
        'r' to 0xFF7A4B2A.toInt(), 'R' to 0xFF55341C.toInt(), 'w' to 0xFFB98A4E.toInt(), 'W' to 0xFF6E4A24.toInt(),
        'd' to 0xFF2A1E1A.toInt(), 'f' to 0xFFA9BCCF.toInt(), 'F' to 0xFFD98A4E.toInt(),
        's' to 0xFF9A9AA2.toInt(), 'S' to 0xFF6C6C76.toInt(),
        'g' to 0xFF4F9E3F.toInt(), 'G' to 0xFF3A7A30.toInt(),
    ))
    val museum = sprite(MUSEUM, mapOf(
        'y' to 0xFFE8C547.toInt(), 'Y' to 0xFFB8932A.toInt(), 'p' to 0xFFE6E0D2.toInt(), 'P' to 0xFFB9B2A2.toInt(),
        'c' to 0xFFF4F0E6.toInt(), 'C' to 0xFFC9C2B2.toInt(), 'b' to 0xFF3F5E86.toInt(), 'd' to 0xFF3A2A1E.toInt(),
        'f' to 0xFF9A9AA2.toInt(), 'F' to 0xFF6C6C76.toInt(),
        'g' to 0xFF4F9E3F.toInt(), 'G' to 0xFF3A7A30.toInt(),
    ))
    val yard = sprite(YARD, mapOf(
        'h' to 0xFFE2C070.toInt(), 'H' to 0xFFB8903C.toInt(), 'r' to 0xFFC8402E.toInt(),
        'F' to 0xFF6E4A24.toInt(), 'f' to 0xFFA8793E.toInt(), 'a' to 0xFFD9C08A.toInt(), 'A' to 0xFFB8A070.toInt(),
        'g' to 0xFF4F9E3F.toInt(), 'G' to 0xFF3A7A30.toInt(),
    ))
    val belfry = sprite(BELFRY, mapOf(
        'r' to 0xFFB8452F.toInt(), 'R' to 0xFF7A2C1E.toInt(), 's' to 0xFF9A9AA2.toInt(), 'S' to 0xFF6C6C76.toInt(),
        'y' to 0xFFF2D14E.toInt(), 'Y' to 0xFFC9A030.toInt(), 'd' to 0xFF2A2A32.toInt(),
    ))
    val orchard = sprite(ORCHARD, mapOf(
        'g' to 0xFF4F9E3F.toInt(), 'G' to 0xFF3A7A30.toInt(), 't' to 0xFF7A4E26.toInt(), 'T' to 0xFF4A2D14.toInt(),
        'w' to 0xFFC9975A.toInt(), 'W' to 0xFF8E6A3A.toInt(), 'y' to 0xFFF2D14E.toInt(),
    ))
    val dock = sprite(DOCK, mapOf(
        'w' to 0xFFC49A5E.toInt(), 'W' to 0xFF8E6A3A.toInt(), 'p' to 0xFF5A3A20.toInt(), 'y' to 0xFFF5D76E.toInt(),
        'b' to 0xFFB8452F.toInt(), 'B' to 0xFF7A2C1E.toInt(), 's' to 0xFFF4F0E4.toInt(), 'r' to 0xFFD9CBA0.toInt(),
        'n' to 0xFF8D909C.toInt(),
    ))
    val quarry = sprite(QUARRY, mapOf(
        'w' to 0xFF9A6B38.toInt(), 'r' to 0xFFD9CBA0.toInt(), 'c' to 0xFFB8452F.toInt(), 's' to 0xFFB9BCC6.toInt(),
        'S' to 0xFF8A8D99.toInt(), 'k' to 0xFF3A3A46.toInt(), 'g' to 0xFF6E6A62.toInt(),
    ))
    val mill = sprite(MILL, mapOf(
        'r' to 0xFFB8452F.toInt(), 'R' to 0xFF8A2E20.toInt(), 'b' to 0xFFDCCFB4.toInt(), 'B' to 0xFFA8977A.toInt(),
        'h' to 0xFF6B4220.toInt(), 'k' to 0xFF9FD3F0.toInt(), 'd' to 0xFF4A2E1B.toInt(),
    ))
    val infirmary = sprite(INFIRMARY, mapOf(
        'r' to 0xFF5F8A5C.toInt(), 'R' to 0xFF45693F.toInt(), 'w' to 0xFFF1EBDD.toInt(), 'W' to 0xFFD2C8AE.toInt(),
        'x' to 0xFFD94A4A.toInt(), 'd' to 0xFF4A2E1B.toInt(), 'k' to 0xFF9FD3F0.toInt(), 'b' to 0xFF8D909C.toInt(),
        'h' to 0xFF7BB86B.toInt(),
    ))
    val ballista = sprite(BALLISTA, mapOf(
        'w' to 0xFF9A6B38.toInt(), 'W' to 0xFF6B4220.toInt(), 'k' to 0xFF3A2614.toInt(), 's' to 0xFFB9BCC6.toInt(),
        'S' to 0xFFEDEFF5.toInt(), 'o' to 0xFF2A2A34.toInt(), 'O' to 0xFF8A5A2B.toInt(), 'r' to 0xFFB8452F.toInt(),
    ))
    val wonderSpire = sprite(WONDER_SPIRE, mapOf(
        'c' to 0xFF9FE8F8.toInt(), 'C' to 0xFFD8F8FF.toInt(), 'p' to 0xFF7A5FC0.toInt(), 'w' to 0xFFE7DCC2.toInt(),
        'W' to 0xFFC9B99A.toInt(), 'k' to 0xFF2B3A67.toInt(), 'd' to 0xFF4A2E1B.toInt(), 'b' to 0xFF8D909C.toInt(),
    ))
    val wonderFountain = sprite(WONDER_FOUNTAIN, mapOf(
        'l' to 0xFFB8E8FF.toInt(), 'L' to 0xFFEAFBFF.toInt(), 's' to 0xFFC9CED6.toInt(), 'S' to 0xFF8D909C.toInt(),
        'b' to 0xFF4FA3D8.toInt(), 'B' to 0xFF8FD0F0.toInt(),
    ))
    val wonderBeacon = sprite(WONDER_BEACON, mapOf(
        'f' to 0xFFFF8A2A.toInt(), 'y' to 0xFFFFC94A.toInt(), 'Y' to 0xFFFFF0A0.toInt(), 'm' to 0xFF6B4A2B.toInt(),
        'k' to 0xFF2A2A34.toInt(), 's' to 0xFFB9BCC6.toInt(), 'S' to 0xFF8D909C.toInt(), 'd' to 0xFF3A2614.toInt(),
    ))
    val raider = sprite(RAIDER, mapOf(
        's' to 0xFFE0B48A.toInt(), 'h' to 0xFF3A2A1E.toInt(), 'r' to 0xFFB02A2A.toInt(), 'R' to 0xFF7A1C1C.toInt(),
        'l' to 0xFF5A4630.toInt(), 'c' to 0xFF6B4A2B.toInt(), 'm' to 0xFFC9CED6.toInt(), 'k' to 0xFF1E1A16.toInt(),
    ))
    val ogre = sprite(OGRE, mapOf(
        'g' to 0xFF7E9A52.toInt(), 'G' to 0xFF5A7438.toInt(), 'b' to 0xFF6B4A2B.toInt(), 'B' to 0xFF4A3220.toInt(),
        'w' to 0xFFF2E6C8.toInt(), 'e' to 0xFFFF4040.toInt(), 'k' to 0xFF2A1C12.toInt(),
    ))
    val wraith = sprite(WRAITH, mapOf(
        'v' to 0xFF6A4AA8.toInt(), 'V' to 0xFF3E2A70.toInt(), 'p' to 0xFFB48AFF.toInt(), 'e' to 0xFFFFF06A.toInt(),
        'k' to 0xFF1A1030.toInt(),
    ))
    val merchant = sprite(MERCHANT, mapOf(
        'h' to 0xFF8A4B2A.toInt(), 's' to 0xFFD9A878.toInt(), 'k' to 0xFF1E1A16.toInt(), 'c' to 0xFF2F7F86.toInt(),
        'C' to 0xFF1E5A60.toInt(), 'p' to 0xFFC8923F.toInt(), 'P' to 0xFF8E6420.toInt(), 'y' to 0xFFE8C547.toInt(), 'b' to 0xFF3A2A1E.toInt(),
    ))
    val bard = sprite(BARD, mapOf(
        'h' to 0xFF6A3A8C.toInt(), 'f' to 0xFF3AB86A.toInt(), 's' to 0xFFD9A878.toInt(), 'k' to 0xFF1E1A16.toInt(),
        'r' to 0xFFC8452F.toInt(), 'R' to 0xFF8A2A1A.toInt(), 'l' to 0xFFB5733A.toInt(), 'L' to 0xFF6B4020.toInt(),
        'w' to 0xFFE8D8A8.toInt(), 'b' to 0xFF3A2A1E.toInt(),
    ))
    val pilgrim = sprite(PILGRIM, mapOf(
        'w' to 0xFFEDEBE0.toInt(), 'W' to 0xFFB9B5A0.toInt(), 's' to 0xFFD9A878.toInt(), 'k' to 0xFF1E1A16.toInt(),
        't' to 0xFF6B4A2B.toInt(), 'g' to 0xFFFFF2A0.toInt(), 'G' to 0xFFFFD86A.toInt(), 'b' to 0xFF3A2A1E.toInt(),
    ))
    val knight = sprite(KNIGHT, mapOf(
        'i' to 0xFFB9BCC6.toInt(), 'I' to 0xFF8D909C.toInt(), 'r' to 0xFFC8452F.toInt(), 'k' to 0xFF22222A.toInt(),
        'b' to 0xFF2F5F9F.toInt(), 'B' to 0xFF1E3A66.toInt(), 'y' to 0xFFE8C547.toInt(), 's' to 0xFFE8ECF4.toInt(),
        'c' to 0xFF6B4A2B.toInt(),
    ))
    val scholar = sprite(SCHOLAR, mapOf(
        'h' to 0xFF3A5FA0.toInt(), 's' to 0xFFD9A878.toInt(), 'y' to 0xFFE8C547.toInt(), 'g' to 0xFF4A7A5A.toInt(),
        'G' to 0xFF2F5238.toInt(), 'B' to 0xFF8A2A1A.toInt(), 'w' to 0xFFF2E6C8.toInt(), 'k' to 0xFF1E1A16.toInt(),
    ))
    val toymaker = sprite(TOYMAKER, mapOf(
        'h' to 0xFFD9484A.toInt(), 's' to 0xFFD9A878.toInt(), 'k' to 0xFF1E1A16.toInt(), 't' to 0xFFE8B83A.toInt(),
        'T' to 0xFFB88A1E.toInt(), 'p' to 0xFF8A5A2A.toInt(), 'P' to 0xFF5E3A1A.toInt(), 'a' to 0xFF4AA8E8.toInt(),
        'e' to 0xFF6AD05A.toInt(), 'b' to 0xFF3A2A1E.toInt(),
    ))
    val shaman = sprite(SHAMAN, mapOf(
        's' to 0xFFD9A878.toInt(), 'm' to 0xFF6E3A8C.toInt(), 'M' to 0xFF4A2462.toInt(), 'b' to 0xFFEDE3C8.toInt(),
        'g' to 0xFF7FFFB0.toInt(), 'c' to 0xFF6B4A2B.toInt(), 'k' to 0xFF1E1A16.toInt(), 'r' to 0xFFB02A2A.toInt(),
    ))
    val warlord = sprite(WARLORD, mapOf(
        's' to 0xFFD9A878.toInt(), 'h' to 0xFF2E2420.toInt(), 'i' to 0xFF8D909C.toInt(), 'I' to 0xFF5E616C.toInt(),
        'r' to 0xFF8E1F1F.toInt(), 'R' to 0xFF5A1010.toInt(), 'm' to 0xFFD8DCE4.toInt(), 'c' to 0xFF6B4A2B.toInt(),
        'k' to 0xFF1E1A16.toInt(), 'y' to 0xFFE8C547.toInt(), 'e' to 0xFFFF5040.toInt(),
    ))
    val golem = sprite(GOLEM, mapOf(
        'a' to 0xFF8A8A92.toInt(), 'A' to 0xFF5E5E68.toInt(), 'l' to 0xFFB4B4BC.toInt(), 'c' to 0xFF7FE0FF.toInt(),
        'C' to 0xFF3AA0D0.toInt(), 'k' to 0xFF22222A.toInt(), 'm' to 0xFF5A7A4A.toInt(),
    ))
    val frostwolf = sprite(FROSTWOLF, mapOf(
        'w' to 0xFFC8E4F4.toInt(), 'W' to 0xFFF0F8FF.toInt(), 'i' to 0xFF7FA8C8.toInt(),
        'e' to 0xFF6AE0FF.toInt(), 'n' to 0xFF1E2A3A.toInt(),
    ))
    val pilferer = sprite(PILFERER, mapOf(
        'h' to 0xFF5A4630.toInt(), 'H' to 0xFF3A2C1E.toInt(), 's' to 0xFFE0B48A.toInt(), 'e' to 0xFFFFE04A.toInt(),
        'b' to 0xFFC8A060.toInt(), 'B' to 0xFF8C6C3A.toInt(), 'g' to 0xFF6A7A3A.toInt(), 'k' to 0xFF1E1A16.toInt(),
        'r' to 0xFFB02A2A.toInt(),
    ))
    val reaver = sprite(REAVER, mapOf(
        'w' to 0xFFE8E0C8.toInt(), 'i' to 0xFF8D909C.toInt(), 'I' to 0xFF5E616C.toInt(), 's' to 0xFFE0B48A.toInt(),
        'k' to 0xFF1E1A16.toInt(), 'o' to 0xFFD08A2A.toInt(), 'B' to 0xFF8C5A2B.toInt(), 'e' to 0xFFE8C547.toInt(),
        'r' to 0xFF2F5F9A.toInt(), 'c' to 0xFF6B4A2B.toInt(), 'a' to 0xFFD8DCE4.toInt(), 'A' to 0xFF9A9EA8.toInt(),
    ))
    val troll = sprite(TROLL, mapOf(
        'g' to 0xFF6E8F4E.toInt(), 'G' to 0xFF4F6B38.toInt(), 'e' to 0xFFFFD84A.toInt(), 'k' to 0xFF2A2A22.toInt(),
        'w' to 0xFFF2EAD0.toInt(), 'm' to 0xFF7A5A3A.toInt(),
    ))
    val spider = sprite(SPIDER, mapOf(
        'b' to 0xFF3A2A44.toInt(), 'B' to 0xFF5A4468.toInt(), 'r' to 0xFFFF4A3A.toInt(), 'y' to 0xFFD8C040.toInt(),
        'k' to 0xFF1A1220.toInt(), 'g' to 0xFF8CD84A.toInt(),
    ))
    val scorpion = sprite(SCORPION, mapOf(
        's' to 0xFFD2843A.toInt(), 'S' to 0xFF96521E.toInt(), 'k' to 0xFF2A1A10.toInt(), 'r' to 0xFFFF4A3A.toInt(),
        'g' to 0xFF8CD84A.toInt(), 'c' to 0xFFE8B066.toInt(),
    ))
    val stag = sprite(STAG, mapOf(
        'b' to 0xFF9A6234.toInt(), 'B' to 0xFF603A1E.toInt(), 'c' to 0xFFE8D2A0.toInt(), 'm' to 0xFFD0802C.toInt(),
        'a' to 0xFFEFE4BC.toInt(), 'A' to 0xFFB6A474.toInt(), 'k' to 0xFF1E140C.toInt(), 'r' to 0xFFFF5A3A.toInt(),
    ))
    val toad = sprite(TOAD, mapOf(
        'g' to 0xFF5E9A3A.toInt(), 'G' to 0xFF3C6B24.toInt(), 'y' to 0xFFD8D890.toInt(), 'k' to 0xFF1C2A14.toInt(),
        'e' to 0xFFE8C040.toInt(),
    ))
    val worm = sprite(WORM, mapOf(
        'p' to 0xFFC08A78.toInt(), 'P' to 0xFF8E5A52.toInt(), 'a' to 0xFF8A8478.toInt(), 'k' to 0xFF2A1418.toInt(),
        'w' to 0xFFF2EAD0.toInt(), 'd' to 0xFF6B4A2B.toInt(),
    ))
    val harpy = listOf(
        sprite(HARPY_UP, HARPY_PAL),
        sprite(HARPY_DOWN, HARPY_PAL),
    )
    val hornet = sprite(HORNET, mapOf(
        'y' to 0xFFF2C830.toInt(), 'k' to 0xFF1E1A16.toInt(), 'w' to 0xFFD8F0FF.toInt(), 'r' to 0xFFD04A2A.toInt(),
    ))
    val skeleton = sprite(SKELETON, mapOf(
        'b' to 0xFFE8E4D0.toInt(), 'B' to 0xFFB5AE92.toInt(), 'k' to 0xFF1E1A16.toInt(), 'g' to 0xFF6AFFC8.toInt(),
        's' to 0xFF8D909C.toInt(),
    ))
    val lich = sprite(LICH, mapOf(
        'm' to 0xFF3E3A5A.toInt(), 'M' to 0xFF24203A.toInt(), 'b' to 0xFFE8E4D0.toInt(), 'g' to 0xFF6AFFC8.toInt(),
        'c' to 0xFF7A5A3A.toInt(),
    ))
    val drake = sprite(DRAKE, mapOf(
        'd' to 0xFF3A2A5A.toInt(), 'D' to 0xFF241838.toInt(), 'p' to 0xFF8A5AD0.toInt(), 'e' to 0xFFFF5A5A.toInt(),
        'w' to 0xFFE8E0F0.toInt(), 'f' to 0xFFB45AFF.toInt(),
    ))
    private val chickPal = mapOf('w' to 0xFFF4F0E6.toInt(), 'o' to 0xFFE89A2B.toInt(), 'r' to 0xFFD9373A.toInt(), 'd' to 0xFF222222.toInt(), 'g' to 0xFFC9C3B4.toInt())
    val chicken = listOf(sprite(CHICK1, chickPal), sprite(CHICK2, chickPal))
    private val firePal = mapOf('y' to 0xFFFFE070.toInt(), 'o' to 0xFFFF8A2B.toInt(), 'L' to 0xFF6B4A2B.toInt(), 'l' to 0xFF4A3220.toInt(), 's' to 0xFF8E8E96.toInt())
    private val rabbitPal = mapOf('b' to 0xFFB59A7A.toInt(), 'e' to 0xFF9A8060.toInt(), 'w' to 0xFFF4F0E6.toInt(), 'n' to 0xFFD99A8A.toInt(), 'd' to 0xFF222222.toInt())
    val rabbit = listOf(sprite(RABBIT1, rabbitPal), sprite(RABBIT2, rabbitPal))
    private val duckPal = mapOf('g' to 0xFF2F8F58.toInt(), 'o' to 0xFFE89A2B.toInt(), 'w' to 0xFFF4F0E6.toInt(), 'b' to 0xFFB59A7A.toInt(), 'd' to 0xFF222222.toInt())
    val duck = listOf(sprite(DUCK1, duckPal), sprite(DUCK2, duckPal))
    val fire = listOf(sprite(FIRE1, firePal, outline = false, shade = false), sprite(FIRE2, firePal, outline = false, shade = false))

    val cactus = sprite(CACTUS, mapOf('g' to 0xFF4E9E4A.toInt(), 'G' to 0xFF3A7A38.toInt()))
    val elfHouse = sprite(ELF_HOUSE, mapOf(
        'g' to 0xFF4FAE5C.toInt(), 'G' to 0xFF2F7A3E.toInt(), 'h' to 0xFF8ADB7A.toInt(),
        'w' to 0xFFE9DDB8.toInt(), 'k' to 0xFFBFE9FF.toInt(), 'd' to 0xFF5A3A22.toInt(), 'b' to 0xFF8A6A3E.toInt(),
    ))
    val orcHut = sprite(ORC_HUT, mapOf(
        'k' to 0xFFE8E0C8.toInt(), 's' to 0xFF7A6A55.toInt(), 'h' to 0xFF7A5A3A.toInt(),
        'R' to 0xFFC0392B.toInt(), 'd' to 0xFF2A1A10.toInt(), 'b' to 0xFF4A3220.toInt(),
    ))
    val dwarfHall = sprite(DWARF_HALL, mapOf(
        'r' to 0xFF8A4B2B.toInt(), 'R' to 0xFF6B3A20.toInt(), 'c' to 0xFF55555E.toInt(),
        's' to 0xFFA6A6B0.toInt(), 'S' to 0xFF7E7E88.toInt(), 'd' to 0xFF2A1A10.toInt(),
        'O' to 0xFFFFA32B.toInt(), 'b' to 0xFF5E5E68.toInt(),
    ))
    val keep = sprite(KEEP, mapOf(
        's' to 0xFFB9BCC6.toInt(), 'S' to 0xFF8D909C.toInt(), 'k' to 0xFF2A2A34.toInt(),
        'd' to 0xFF4A2E1B.toInt(), 'm' to 0xFF9C9FAB.toInt(),
    ))
    val ship = sprite(SHIP, mapOf(
        'm' to 0xFF5A3A22.toInt(), 'w' to 0xFFF4F0E6.toInt(), 'b' to 0xFF8A5A2B.toInt(), 'B' to 0xFF6B4220.toInt(),
    ))

    fun homeFor(race: String): ImageBitmap = when (race) {
        "Elf" -> elfHouse
        "Orc" -> orcHut
        else -> dwarfHall
    }

    private fun folkSprites(skin: Int, skinShade: Int, hair: Int, shirt: Int, shade: Int, top: List<String>?, beard: Boolean, tusks: Boolean): List<ImageBitmap> {
        val pal = mapOf(
            'r' to hair, 'h' to skin, 'H' to skinShade, 'd' to 0xFF222222.toInt(),
            's' to shirt, 'S' to shade, 'p' to 0xFF3A3F5C.toInt(), 'b' to 0xFF3B2A1A.toInt(), 'w' to 0xFFF4F0E6.toInt(),
        )
        fun tweak(g: List<String>): List<String> {
            val rows = g.toMutableList()
            if (top != null) rows[0] = top[0]
            if (beard) {
                rows[4] = ".rrrrrr."
                rows[5] = ".srrrrs."
            }
            if (tusks) rows[4] = ".whHHhw."
            return rows
        }
        return listOf(sprite(tweak(PERSON1), pal), sprite(tweak(PERSON2), pal))
    }

    private val folkMap = mapOf(
        "Elf" to folkSprites(0xFFF0D8B0.toInt(), 0xFFD9BC90.toInt(), 0xFFE8E0A0.toInt(), 0xFFC77DFF.toInt(), 0xFF9A55D1.toInt(), listOf("h.rrrr.h"), false, false),
        "Orc" to folkSprites(0xFF7DB84A.toInt(), 0xFF5E9234.toInt(), 0xFF222222.toInt(), 0xFFE8503A.toInt(), 0xFFB03A28.toInt(), null, false, true),
        "Dwarf" to folkSprites(0xFFF2C7A0.toInt(), 0xFFD9A982.toInt(), 0xFFB5651D.toInt(), 0xFFF0B13C.toInt(), 0xFFB98A27.toInt(), null, true, false),
    )

    fun folk(race: String, frame: Int): ImageBitmap = (folkMap[race] ?: folkMap.getValue("Elf"))[frame and 1]

    private val shirts = intArrayOf(0xFFD94F4F.toInt(), 0xFF4F7FD9.toInt(), 0xFFE0B040.toInt(), 0xFF8E4FD9.toInt(), 0xFF4FB58E.toInt(), 0xFFE07A3A.toInt(), 0xFFD94F9B.toInt(), 0xFFEFEFEF.toInt(), 0xFF8B5A2B.toInt(), 0xFFCD7F32.toInt(), 0xFF4A4F5A.toInt())
    private val shirtShade = intArrayOf(0xFFA83A3A.toInt(), 0xFF3A62A8.toInt(), 0xFFB48C2E.toInt(), 0xFF6D3AA8.toInt(), 0xFF3A8D6E.toInt(), 0xFFB05E2A.toInt(), 0xFFA83A78.toInt(), 0xFFC0C0C0.toInt(), 0xFF6B4220.toInt(), 0xFF9A5F22.toInt(), 0xFF2F333B.toInt())
    private val hairs = intArrayOf(0xFF3B2A1A.toInt(), 0xFF8A5A2B.toInt(), 0xFF222222.toInt(), 0xFFD9B04A.toInt(), 0xFF7A2E1E.toInt(), 0xFFB0B0B0.toInt(), 0xFF5A3A6A.toInt(), 0xFF2E4A3A.toInt(), 0xFF3B2A1A.toInt(), 0xFF7A2E1E.toInt(), 0xFF222222.toInt())
    private val people = List(10) { i ->
        val pal = mapOf(
            'r' to hairs[i], 'h' to 0xFFF2CBA3.toInt(), 'H' to 0xFFD9A982.toInt(), 'd' to 0xFF222222.toInt(),
            's' to shirts[i], 'S' to shirtShade[i], 'p' to 0xFF3A3F5C.toInt(), 'b' to 0xFF3B2A1A.toInt(),
        )
        listOf(sprite(PERSON1, pal), sprite(PERSON2, pal))
    }

    fun person(colorIdx: Int, frame: Int): ImageBitmap = people[colorIdx.coerceIn(0, 9)][frame and 1]

    private val lookCache = HashMap<Int, List<ImageBitmap>>()

    /** A villager with their own skin tone and hair colour (grey when old), built once per combination. */
    fun personLook(colorIdx: Int, skin: Int, hair: Int, elder: Boolean, frame: Int): ImageBitmap {
        val ci = colorIdx.coerceIn(0, 9)
        val si = skin.coerceIn(0, com.jondude1.deskglass.sim.Look.SKIN.size - 1)
        val hi = hair.coerceIn(0, com.jondude1.deskglass.sim.Look.HAIR.size - 1)
        val key = ((ci * 5 + si) * 8 + (if (elder) 0 else hi)) * 2 + (if (elder) 1 else 0)
        val l = lookCache.getOrPut(key) {
            val sk = com.jondude1.deskglass.sim.Look.SKIN[si]
            val pal = mapOf(
                'r' to (if (elder) com.jondude1.deskglass.sim.Look.GREY else com.jondude1.deskglass.sim.Look.HAIR[hi]),
                'h' to sk, 'H' to com.jondude1.deskglass.sim.Look.shade(sk), 'd' to 0xFF222222.toInt(),
                's' to shirts[ci], 'S' to shirtShade[ci], 'p' to 0xFF3A3F5C.toInt(), 'b' to 0xFF3B2A1A.toInt(),
            )
            listOf(sprite(PERSON1, pal), sprite(PERSON2, pal))
        }
        return l[frame and 1]
    }

    companion object {
        val OAK = listOf(
            "....hhhhhh....",
            "..hhhhgggggg..",
            ".hhhggggggGgg.",
            ".hhgggggggggg.",
            "hhgggGgggggggG",
            "hgggggggGggggg",
            "gggGgggggggGgg",
            ".ggggggGggggg.",
            ".GgggggggggGg.",
            "..GGgggggGGG..",
            "....GGGGGG....",
            "......tt......",
            "......tT......",
            "......tT......",
            ".....tttT.....",
            "....ttTTTt....",
        )
        val PINE = listOf(
            ".....qq.....",
            "....qppp....",
            "....ppPp....",
            "...qpppPp...",
            "...pppPpp...",
            "..qppppPpp..",
            "..pppPpppp..",
            ".qpppppPppp.",
            ".ppPpppppPp.",
            "qppppPpppppp",
            "pppPpppppPpp",
            "....tttt....",
            "....tTtt....",
            "....tTtt....",
        )
        val STUMP = listOf(
            "..cccc..",
            ".cttttc.",
            ".tttTtt.",
            ".tTttTt.",
            "..tTTt..",
        )
        val ROCK = listOf(
            "....rrrr....",
            "..rrRRRrrr..",
            ".rrRRRrrrrrd",
            "rrrRRrrrrrrd",
            "rrrrrrrrdrrd",
            "rrrdrrrrrrdd",
            ".rrrrrrrrddd",
            "..dddddddd..",
        )
        val BUSH = listOf(
            "...hhgg...",
            "..hhgGgg..",
            ".hgggrggGg",
            "hgrgggggGg",
            "ggggGgrggg",
            ".gggggggGg",
            "..GGGGGG..",
        )
        val CACTUS = listOf(
            "...gg...",
            ".g.gg...",
            ".g.gg.g.",
            ".gggg.g.",
            "...ggggg",
            "...gG...",
            "...gG...",
            "...gG...",
        )
        val ELF_HOUSE = listOf(
            "....gggg....",
            "..ggghhggg..",
            ".gghgggggGg.",
            "gggggggggGGg",
            ".ggggggggGG.",
            "..wwwwwwww..",
            "..wkwddwkw..",
            "..wwwddwww..",
            "..wwwddwww..",
            "..bbbbbbbb..",
        )
        val ORC_HUT = listOf(
            "....k..k....",
            "....s..s....",
            ".....ss.....",
            "....hhhh....",
            "...hhhRhh...",
            "..hhhhRhhh..",
            ".hhhhhddhhh.",
            "hhhhhhddhhhh",
            "bbbbbbddbbbb",
        )
        val DWARF_HALL = listOf(
            "......cc....",
            "......cc....",
            "...rrrrrrr..",
            "..rrrrrrrrr.",
            ".rrrrrrrrrrR",
            ".ssssssssss.",
            ".sSssddddsS.",
            ".ssssdOOdss.",
            ".sSsssddssS.",
            "bbbbbbbbbbbb",
        )
        val KEEP = listOf(
            "..m.m.mm.m.m..",
            "..mssssssssm..",
            "..sSsssssssS..",
            "..ssskssksss..",
            "..sSsssssssS..",
            "..ssssssssss..",
            "..ssskssksss..",
            "..sSsssssssS..",
            ".ssssssssssss.",
            ".sSssdddssSss.",
            ".sssddddddsss.",
            ".sSsddddddSss.",
            "SSSSSSSSSSSSSS",
        )
        val SHIP = listOf(
            "......m.......",
            "......mww.....",
            "......mwwww...",
            "......mwwwww..",
            "......m.......",
            "bbbbbbbbbbbbb.",
            ".bBBBBBBBBBb..",
            "..bbbbbbbbb...",
        )
        val HUT = listOf(
            "......RR......",
            "....SSrrRR....",
            "..SSrrrrrrRR..",
            ".SrrrrrrrrrrR.",
            "rrrrrrrrrrrrrR",
            ".wwwwwwwwwwww.",
            ".wkkwwwwwkkw..".replace("..", "W."),
            ".wkkwddddwkkw.",
            ".wwwwddddwwww.",
            ".WwwwddddwwwW.",
            ".bbbbbbbbbbbb.",
        )
        val TOWNHOUSE = listOf(
            "....cc........",
            "....cc..rrrr..",
            "...rrrrrrRRRR.",
            "..rrrrrrrRRRRR",
            ".rrrrrrrrrRRRR",
            "rrrrrrrrrrRRRR",
            ".wwwwwwwwwwww.",
            ".wkkwWwwwWkkw.",
            ".wkkwwwwwwkkw.",
            ".wwwwwddwwwww.",
            ".WwwwwddwwwwW.",
            ".bbbbbddbbbbb.",
        )
        val CITYHOUSE = listOf(
            "......p.......",
            "......pff.....",
            "....rrrrrr....",
            "...rrrrrrRR...",
            "..rrrrrrrRRR..",
            ".rrrrrrrrrRRR.",
            ".wwwwwwwwwwww.",
            ".wkkwwwwwwkkw.",
            ".wkkwwwwwwkkw.",
            ".wwwwwwwwwwww.",
            ".wkkwwddwwkkw.",
            ".WwwwwddwwwwW.",
            ".bbbbbddbbbbb.",
        )
        val HALL = listOf(
            "......p.........",
            "......pff.......",
            "....rrrrrrrr....",
            "..rrrrrrrrrrRR..",
            ".rrrrrrrrrrrrRR.",
            "rrrrrrrrrrrrrrRR",
            ".wwwwwwwwwwwwww.",
            ".wwkkwwddwwkkww.",
            ".WwkkwwddwwkkwW.",
            ".bbbbbbddbbbbbb.",
        )
        val TENT = listOf(
            "......pp......",
            ".....tttT.....",
            "....ttttTT....",
            "...tttttTTT...",
            "..tttttddTTT..",
            ".tttttdddTTTT.",
            "tttttttdddTTTT",
            "ttttttddddTTTT",
        )
        val ORE = listOf(
            "....rrrr....",
            "..rrRRRrrr..",
            ".rrRoRrrorrd",
            "rrrRRrrrOrrd",
            "rorrrrrrdrrd",
            "rrrdrorrrrdd",
            ".rrrrrrOrddd",
            "..dddddddd..",
        )
        val FORGE = listOf(
            "....cccc....",
            "....cCCc....",
            "....cCCc....",
            "...cccccc...",
            "..ssssssss..",
            ".ssssssssss.",
            ".ssOOOOOOss.",
            ".ssOyyyyOss.",
            ".ssOyyyyOss.",
            ".ssssssssss.",
            "SSSSSSSSSSSS",
        )
        val WOLF = listOf(
            "......d.d.",
            ".....wwwww",
            "ww..wwwWen",
            "wwwwwwwww.",
            "wwwwwwwww.",
            ".d.d..d.d.",
            ".d.d..d.d.",
        )
        val BEAR = listOf(
            "........b.b.",
            "..bbbbbbbbbb",
            ".bbbbbbbbebn",
            "bbbbbbbbbbbb",
            "bbbBbbbbbBbb",
            "bbbbbbbbbbbb",
            ".bbbbbbbbbb.",
            ".BB.BB..BB..",
            ".BB.BB..BB..",
        )
        val BOAR = listOf(
            "..........",
            "...kk.....",
            "bbbbbbbb..",
            "bBbbbbbbbb",
            "bbbbbbbbew",
            "bbbbbbbbpw",
            ".bbbbbbbbp",
            ".B.B..B.B.",
            ".B.B..B.B.",
        )
        val WALL = listOf(
            "sssss...sssss...",
            "sbbbs...sbbbs...",
            "ssssssssssssssss",
            "sbsSsbsSsbsSsbsS",
            "SsSbsSsSSsSbsSsS",
            "sSsSsbsSsSsSsbsS",
            "SsbsSsSSsbsSsSsS",
            "SSSSSSSSSSSSSSSS",
            "kkkkkkkkkkkkkkkk",
        )
        val GATE = listOf(
            ".......F........",
            ".......f........",
            ".......d........",
            "sssss.sssss.ssss",
            "sbbbs.sbbbs.sbbs",
            "ssssssssssssssss",
            "sbsSsrrrrrSbsSsS",
            "SsSbsrRRRrsSsbsS",
            "sSsSsrRRRrSsSsSs",
            "SsbsSrRRRrsSbsSs",
            "sSsSsrR.RrSsSsbs",
            "SsSbsSr.rSsSsSsS",
            "sSsbSsSsSsbSsSsS",
            "SsSsSbsSsSsSsbsS",
            "SSSSSSSSSSSSSSSS",
            "kkkkkkkkkkkkkkkk",
        )
        val PALISADE = listOf(
            "w.w.w.w.w.w.",
            "wWwWwWwWwWwW",
            "wWwWwWwWwWwW",
            "wWwWwWwWwWwW",
            "kkkkkkkkkkkk",
        )
        val DEER1 = listOf(
            "......a.a...",
            ".......aa...",
            "......dddn..",
            ".dddddddd...",
            "dddddddddd..",
            "dddddddddd..",
            ".dDdDdDdDd..",
            ".d.d..d.d...",
            ".d.d..d.d...",
        )
        val DEER2 = listOf(
            "......a.a...",
            ".......aa...",
            "......dddn..",
            ".dddddddd...",
            "dddddddddd..",
            "dddddddddd..",
            ".dDdDdDdDd..",
            "..d.d.d.d...",
            ".d..d..d.d..",
        )
        val TOWER = listOf(
            "....rrrr....",
            "...rrRRrr...",
            "..rrRRRRrr..",
            "...wwwwww...",
            "..wWwwwwWw..",
            "..wwkkkkww..",
            "...ssssss...",
            "...sSssSs...",
            "...skkkks...",
            "...ssssss...",
            "...sSssSs...",
            "...ssssss...",
            "..ssSssSss..",
            "..sssddsss..",
            ".SSSSddSSSS.",
        )
        val FARM = listOf(
            "ffffffffffffff",
            "dDdDdDdDdDdDdD",
            "dddddddddddddd",
            "dDdDdDdDdDdDdD",
            "dddddddddddddd",
            "dDdDdDdDdDdDdD",
            "dddddddddddddd",
            "dDdDdDdDdDdDdD",
            "dddddddddddddd",
            "ffffffffffffff",
        )
        val WELL = listOf(
            "..RRRR..",
            ".RRRRRR.",
            "..b..b..",
            "..b..b..",
            ".ssssss.",
            "ssSSSSss",
            "sdssssds",
            ".dddddd.",
        )
        val WORKSHOP = listOf(
            "...rrrrrrrr...",
            "..rrRrrRrrRr..",
            ".rrrrrrrrrrrr.",
            "..wwwwwwwwww..",
            "..wWwwkkwwWw..",
            "..wwwwkkwwww..",
            "..wWwwwwwwWw..",
            "..wwddddwwaa..",
            "..wwddddwAaA..",
            "..wwddddwaaa..",
            ".WWWWWWWWWWWW.",
        )
        val MARKET = listOf(
            "..rfrfrfrfrfrf..",
            ".rfrfrfrfrfrfrf.",
            "rfrfrfrfrfrfrfrf",
            ".a.b........b.a.",
            "..b.gg.yy...b...",
            "..b.gg.yo.oo.b..",
            "..bBBBBBBBBBBb..",
            "..bBggBooBBBBb..",
            "..BBBBBBBBBBBB..",
        )
        val LAMP = listOf(
            ".kkk.",
            "kyyyk",
            "kyoyk",
            ".kkk.",
            "..p..",
            "..p..",
            "..p..",
            "..p..",
            ".PPP.",
        )
        val GRANARY = listOf(
            "....rrrrrr....",
            "..rrRrrrrRrr..",
            ".rrrrrrrrrrrr.",
            "..wwwwwwwwww..",
            "..wWwwwwwwWw..",
            "..wwwwyywwww..",
            "..wWwwyywwWw..",
            "..wwwddddwww..",
            "..wWwddddwWw..",
            ".ppppppppppp..",
        )
        val BARRACKS = listOf(
            "..f...........",
            "..k.rrrrrrrr..",
            ".rrrrRrrRrrrr.",
            "rrRrrrrrrrrRrr",
            ".ssssssssssss.",
            ".sSsssmmsssSs.",
            ".ssssmmmmssss.",
            ".sSssssssssSs.",
            ".sssssddsssss.",
            ".SSSSSddSSSSS.",
        )
        val SHRINE = listOf(
            "......yy......",
            ".....yggy.....",
            "..ss.yggy.ss..",
            "..sS..yy..Ss..",
            "..sS.SSSS.Ss..",
            "..sSSSppSSSs..",
            "..ssppyyppss..",
            "sSSSSSSSSSSSSs",
            "SSkSSSSSSSSkSS",
            "mmmmmmmmmmmmmm",
        )
        val TOTEM = listOf(
            "f..yyy..F",
            "fF.yyy.Ff",
            ".FfwwwfF.",
            "..wkwkw..",
            "..wwrww..",
            "..wWWWw..",
            ".bbbbbbb.",
            "wwwkwkwww",
            "wwwwywwww",
            "wwwrrrwww",
            ".WwwwwwW.",
            "..wkwkw..",
            "..wwwww..",
            "..wWbWw..",
            ".sssSsSss",
            "sSSsssSSs",
        )
        val TAVERN = listOf(
            "....cc........",
            "....cc..rrrr..",
            "..rrrrrRRRRrr.",
            ".rrrrrrrrrrrrr",
            "rrRRRRRRRRRRrr",
            ".wwwwwwwwwwww.",
            ".wtyywwwwyytw.",
            ".wtyywwwwyytw.",
            "gwwwwwddwwwww.",
            "gmwWwwddwwWww.",
            ".bbbbbddbbbbb.",
        )
        val RANCH = listOf(
            "...rrrr.........",
            "..rrRRrr........",
            ".rrRRRRrr.......",
            "rrrrrrrrrr......",
            ".wwwwwwwww..y...",
            ".wWwddddwWw.yy..",
            ".wWwddddwWw.....",
            ".wwwddddwwwffff.",
            ".wwwddddwwwf..f.",
            "bbbbbbbbbbbffffb",
        )
        val BALLISTA = listOf(
            "..........ww..",
            "..........wk..",
            ".........wk...",
            "..rssssssssSS.",
            ".........wk...",
            "..wwwwwwwwk...",
            "..wWWWWWWWw...",
            "...wW....wW...",
            "..oooo..oooo..",
            ".oOOOOooOOOOo.",
            "..oooo..oooo..",
        )
        val PASTURE = listOf(
            "..yyyy..............",
            ".yyYYyy.............",
            "yyyyyyyy............",
            ".wwwwww.f....f.....f",
            ".wddddw.ffffffffffff",
            ".wddddw.f....f.....f",
            ".wwwwww.f....f.....f",
            ".wWhhWw.ffffffffffff",
            ".wwwwww.f....f.....f",
            "bbbbbbb.f....f.....f",
        )
        val BATH = listOf(
            "....rr..rr....",
            "..rrRRrrRRrr..",
            ".rRwwWwwwWwwr.",
            "rRwWwwwwwwWwRr",
            "rRwwwWwwwwwwRr",
            ".rRwwwwWwwwRr.",
            "..rrRwwwwRrr..",
            ".gg.rrRRrr.gg.",
            "..tt........tt",
            "gGggGgggGggGgg",
        )
        val RANGE = listOf(
            ".rr...rr...rr.",
            "rhhr.rhhr.rhhr",
            "rhyr.rhyr.rhyr",
            ".rr...rr...rr.",
            ".tt...tt...tt.",
            ".tt...tt...tt.",
            "gGggGgggGggGgg",
            ".W..W..W..W..W",
            "cccccccccccccc",
            "W..W..W..W..W.",
        )
        val STAGE = listOf(
            "..rRrRrRrRrR..",
            ".yyyyyyyyyyyy.",
            ".rRbbbbbbbbRr.",
            ".rRbbbbyybbRr.",
            ".rRbbbbyybbRr.",
            ".rRbbbbbbbbRr.",
            ".tttttttttttt.",
            ".tWtWtWtWtWtW.",
            ".W..........W.",
            "gGggGgggGggGgg",
        )
        val FOUNTAIN = listOf(
            "......hh......",
            ".....hbbh.....",
            "......sS......",
            "......sS......",
            "...ssssssss...",
            "..sbhbbbbhbs..",
            ".sSbbbhbbbbSs.",
            ".ssssssssssss.",
            "..SSSSSSSSSS..",
            "gGggGgggGggGgg",
        )
        val LOOM = listOf(
            "..rwrwrwrwrw..",
            ".rwrwrwrwrwrw.",
            ".W..........W.",
            ".WyyyyyyyyyyW.",
            ".WbBbBbBbBbBW.",
            ".WbBbBbBbBbBW.",
            ".WmrmrmrmrmrW.",
            ".WrmrmrmrmrmW.",
            ".WW........WW.",
            "gGggGgggGggGgg",
        )
        val YARD = listOf(
            "......hh......",
            ".....hHHh.....",
            "......hh......",
            ".....rhhr.....",
            "......FF......",
            "..f...FF...f..",
            ".fFf..FF..fFf.",
            "fFfFfFFFFfFfFf",
            "aAaaAaaaAaaAaa",
            "gGggGgggGggGgg",
        )
        val KILN = listOf(
            "......sS......",
            "......sS......",
            ".....cCCc.....",
            "....cCccCc....",
            "...cCccccCc...",
            "..cCcddddcCc..",
            "..cCcdooDcCc..".replace("D","d"),
            "..cCcddddcCc..",
            ".ttpbtpWtpbtt.",
            "gGggGgggGggGgg",
        )
        val MUSEUM = listOf(
            "......yy......",
            ".....pppp.....",
            "....pPppPp....",
            "..pppppppppp..",
            "pppppppppppppp",
            "yYyYyYyYyYyYyY",
            ".cCbbcddcbbCc.",
            ".cCbbcddcbbCc.",
            "ffffffffffffff",
            "gGggGgggGggGgg",
        )
        val SMOKEHOUSE = listOf(
            "......sS......",
            "......sS......",
            "...rrrrrrrr...",
            "..rRrRrRrRrR..",
            ".rrrrrrrrrrrr.",
            ".WwFwFwFwwddW.",
            ".WwfwfwfwwddW.",
            ".WwwwwwwwwddW.",
            ".WWWWWWWWWWWW.",
            "gGggGgggGggGgg",
        )
        val BELFRY = listOf(
            "......rr......",
            ".....rrrr.....",
            "....rrrRRr....",
            "...rrrrrrrr...",
            "...sSddddSs...",
            "...sSdyydSs...",
            "...sSdYYdSs...",
            "...sSddddSs...",
            "...ssssssss...",
            "..SSSSSSSSSS..",
        )
        val ORCHARD = listOf(
            ".gg...gg...gg.",
            "gGgg.gGgg.gGgg",
            "gggG.gggG.gggG",
            "gGgg.gGgg.gGgg",
            ".gg...gg...gg.",
            ".tT...tT...tT.",
            ".tt...tt...tt.",
            "wwwwwwwwwwwwww",
            "W..W..W..W..W.",
            "GyGGGyGGGyGGGy",
        )
        val APIARY = listOf(
            "......ss......",
            ".ss..ssss..ss.",
            "ssss.ssss.ssss",
            "SSSS.SSSS.SSSS",
            "ssss.ssss.ssss",
            "sdds.sdds.sdds",
            "SSSS.SSSS.SSSS",
            "wwwwwwwwwwwwww",
            "W..W......W..W",
            "gpgyggvgpgygvg",
        )
        val DOCK = listOf(
            ".........y....",
            ".........p....",
            "....s....p....",
            "...ss....p....",
            "..sss.........",
            "...p.....wwwww",
            "wwwwwwwwwwWWWw",
            "wWwWwWwWwWwWwW",
            "p..bbbbbb.p..p",
            "...BBBBBB.....",
        )
        val QUARRY = listOf(
            "......ww......",
            ".....wrrw.....",
            "....w.r..w.ss.",
            "...w..r...wsSs",
            "..wwwwcwwwwSSs",
            "ssSssSSsssSSss",
            "SSsSSkkkSSsSSs",
            "sSSkkkkkkkSSsS",
            "kSSSkkkkkSSSSk",
            "gggggggggggggg",
        )
        val MILL = listOf(
            "......rr......",
            ".....rrrr.....",
            "....rrRRrr....",
            "...rrrrrrrr...",
            "....bbbbbb....",
            "....bbhhbb....",
            "....bbbbbb....",
            "...bbbbbbbb...",
            "...bbkkbbbb...",
            "...bbkkbbbb...",
            "..bbbbbbbbbb..",
            "..bbbbddbbbb..",
            "..bbbbddbbbb..",
            "..BBBBBBBBBB..",
        )
        val INFIRMARY = listOf(
            "..............",
            "...rrrrrrrr...",
            "..rrRRRRRRrr..",
            ".rrrrrrxxrrrr.",
            "..wwwwxxxxww..",
            "..wkkwwxxwkkw.",
            "..wkkwwwwwkkw.",
            "..wwwwwwwwwww.",
            "..wWwwddwwWwh.",
            ".bbbbbddbbbbbh",
        )
        val LIBRARY = listOf(
            ".....rrrr.....",
            "...rrRRRRrr...",
            "..rrRRRRRRrr..",
            ".rrrrrrrrrrrr.",
            "..wwwwwwwwww..",
            "..wkkwwwwkkw..",
            "..wkkwwwwkkw..",
            "..wwwwyywwww..",
            "..wWwwddwwWw..",
            ".bbbbbddbbbbb.",
        )
        val WONDER_SPIRE = listOf(
            "......cC........",
            ".....cCCc.......",
            "....cCCCCc......",
            ".....cCCc.......",
            "......cc........",
            "......pp........",
            ".....wwww.......",
            ".....wWWw.......",
            "....wwwwww......",
            "....wWwwWw......",
            "...wwwkkwww.....",
            "...wWwkkwWw.....",
            "..wwwwwwwwww....",
            "..wWwwddwwWw....",
            ".wwwwwddwwwww...",
            "bbbbbbddbbbbbb..",
        )
        val WONDER_FOUNTAIN = listOf(
            "......l.l.......",
            ".....l.L.l......",
            "......lLl.......",
            ".......s........",
            ".....sssss......",
            "....sbbbbbs.....",
            "......sSs.......",
            "......sSs.......",
            "..sssssssssss...",
            ".sbbbbbbbbbbbs..",
            "sSbbBbbbbBbbbSs.",
            "ssSSSSSSSSSSSss.",
            "..sSSSSSSSSSs...",
        )
        val WONDER_BEACON = listOf(
            ".....ff.........",
            "....fyyf........",
            "....fYYf........",
            ".....ff.........",
            "...mmmmmmm......",
            "...mkkkkkm......",
            "....sssss.......",
            "....sSssS.......",
            "....skkks.......",
            "....sssss.......",
            "....sSssS.......",
            "....sssss.......",
            "...ssSsSss......",
            "...sssddss......",
            "..sssssddsss....",
            ".SSSSSSSSSSSS...",
        )
        val RAIDER = listOf(
            "...hhhh...m.",
            "..hssssh..m.",
            "..hsksks..m.",
            "..hssssh.cm.",
            "...rrrr.cc..",
            "..rrRrrrc...",
            ".srrRRrrs...",
            ".s.rrrr.....",
            "...llll.....",
            "...l..l.....",
            "..kk..kk....",
        )
        val OGRE = listOf(
            "....gggggg......",
            "...gggggggg.....",
            "...gewggwegg....",
            "...gggkkggg.....",
            "...gwgggggwg....",
            "..GGGGGGGGGG....",
            ".GgGbbbbbbGgG...",
            ".gg.bbBBbb.gg.cc",
            ".gg.bbbbbb.ggcc.",
            ".Gg.bbBBbb.gc...",
            "....bbbbbb......",
            "....GG..GG......",
            "....GG..GG......",
            "...kkk..kkk.....",
        )
        val WRAITH = listOf(
            "....vvvv....",
            "...vvvvvv...",
            "..vveevvev..",
            "..vvvvvvvv..",
            "..vVvvvvVv..",
            ".vvVvppvVvv.",
            ".vvvvppvvvv.",
            "vvVvvvvvvVvv",
            "vv.vvvvvv.vv",
            "v...vvvv...v",
            "....v..v....",
            "...v....v...",
        )
        val MERCHANT = listOf(
            "...hhhhhh...",
            "..hhhhhhhh..",
            "...ssssss...",
            "...skssks...",
            "....ssss....",
            "..pcccccc...",
            ".ppcccyccc..",
            ".pPccccccs..",
            ".pPccCCcc.s.",
            ".ppcccccc...",
            "...cc..cc...",
            "...bb..bb...",
        )
        val BARD = listOf(
            "....hhh.f...",
            "...hhhhhf...",
            "...ssssss...",
            "...skssks...",
            "....ssss....",
            "..rrrrrrr..w",
            ".rrrRrrrrl.w",
            ".s.rrrrrlLw.",
            "..srrrrllL..",
            "...rrrrlL...",
            "...rr..rr...",
            "...bb..bb...",
        )
        val KNIGHT = listOf(
            "....rr......",
            "...iiii.....",
            "...ikki....s",
            "...iiii....s",
            "..biiiib...s",
            ".bBiIIiBb.ys",
            ".bByiIIiBbcs",
            ".bBbiIIi...c",
            "..bBiIIi....",
            "...iiii.....",
            "...ii.ii....",
            "...kk.kk....",
        )
        val SCHOLAR = listOf(
            "....hh......",
            "...hhhh.....",
            "..hhhhhh....",
            "...ssss.....",
            "...sysy.....",
            "....ss......",
            "..ggggggBBB.",
            ".gggGggBwwwB",
            ".gggGgggBwwB",
            ".gggGgggBBB.",
            "..gGggGg....",
            "...kk.kk....",
        )
        val TOYMAKER = listOf(
            "...hhhh..a.e",
            "..hhhhhh.pp.",
            "...ssss.pPpp",
            "...skks.pPpp",
            "....ss..pPpp",
            "..ttttttpPp.",
            ".sttTttttpp.",
            ".s.ttTttt...",
            "...ttTtt....",
            "...tt..tt...",
            "...bb..bb...",
            "...bb..bb...",
        )
        val PILGRIM = listOf(
            "....wwww..g.",
            "...wwwwww.gG",
            "...wsssw..t.",
            "...wskskw.t.",
            "...wsssw..t.",
            "..wwwwwww.t.",
            ".wwwWwwwwst.",
            ".wwwWwwww.t.",
            ".wwwWWwww.t.",
            "..wwwwwww.t.",
            "..wWwwwWw.t.",
            "...bb.bb..t.",
        )
        val SHAMAN = listOf(
            "...bb.bb....",
            "..bbbbbbb.g.",
            "..bssssb..g.",
            "..bskskb..c.",
            "..bsrrsb.cg.",
            "...mmmm..c..",
            "..mmMmmm.c..",
            ".smmMMmmsc..",
            ".s.mmmmm.c..",
            "...mmmmm....",
            "..mMmmmMm...",
            "..kk...kk...",
        )
        val WARLORD = listOf(
            "..i.hhhh.i....",
            "..iihhhhii....",
            "...hssssh.....",
            "...hseses.....",
            "...hsrrsh...m.",
            "..iiyyyyii..m.",
            ".iIrrRRrrIi.m.",
            "isIrrRRrrIsmm.",
            "s.IrrRRrrI.cm.",
            "..crrRRrrc.c..",
            "...llllll.....",
            "...ll..ll.....",
            "...ll..ll.....",
            "..kkk..kkk....",
        )
        val FROSTWOLF = listOf(
            "....W.W...W.",
            "...iwwwwwwww",
            "ww.iwwwWWwen",
            "wwwwwwwwwwww",
            "WWwwwwwwwww.",
            ".i.i...i.i..",
            ".i.i...i.i..",
        )
        val PILFERER = listOf(
            "..hhhh....",
            ".hhHHhh...",
            ".hHeHeHh..",
            ".hHssssH..",
            "..hrrrrh..",
            ".hhhhhhhbb",
            "shhgggghbB",
            "s.hhhhhhBB",
            "..hhhhhh..",
            "..hh..hh..",
            ".kkk..kkk.",
        )
        val REAVER = listOf(
            "w..iiii..w.",
            "ww.iIIi.ww.",
            ".w.ssss.w..",
            "...skks....",
            "..ooooo..aa",
            "BBBrrrrr.aA",
            "BeBrrrrrrcA",
            "BBBrrrrr.c.",
            "...rrrrr.c.",
            "...rr.rr...",
            "..kkk.kkk..",
        )
        val TROLL = listOf(
            "....gggg....",
            "...gGGGGg...",
            "..gGeGGeGg..",
            "..gGGGGGGg..",
            "..gwkkkkwg..",
            "gg.gggggg.gg",
            "GGggGggGggGG",
            "GGmmmmmmmmGG",
            "GG.mmmmmm.GG",
            "gg.gggggg.gg",
            "...gg..gg...",
            "..ggg..ggg..",
            ".kkkk..kkkk.",
        )
        val SPIDER = listOf(
            "k..........k",
            ".k...bb...k.",
            "..k.bBBb.k..",
            "k.kbrBBrbk.k",
            ".kkbBbbBbkk.",
            "k..bybbyb..k",
            "..kbbyybbk..",
            ".k.kbbbbk.k.",
            "k...kbbk...k",
            "....k..k....",
        )
        val TOAD = listOf(
            "..kkk....kkk..",
            ".kgeek..kgeek.",
            "kggggggggggggk",
            "kggGgggggggGgk",
            "kggkkkkkkkkggk",
            "kgyyyyyyyyyygk",
            ".kgyyyyyyyygk.",
            "gGk.kggggk.kGg",
            "ggg.k....k.ggg",
        )
        val STAG = listOf(
            "a..a.a.........",
            "aA.aAaA........",
            ".aAaAAa........",
            "..aAAa.........",
            "..kbbmm........",
            ".rkbbmmbbbbb...",
            "kkbbbmmmbbbBBb.",
            "..bbmmmbbbbBBBb",
            "...bmmbbbbbbBBB",
            "....bcccccbbBb.",
            "....bcccccbbbB.",
            "....B.B....B.B.",
            "....k.k....k.k.",
        )
        val SCORPION = listOf(
            "..........gr...",
            ".........sSSg..",
            ".........sS.sg.",
            "cc.....sSSSs...",
            "cSc.ssSSSSSSs..",
            ".cSsssSsSssSs..",
            "..sSSkSSSSkSs..",
            "k.sSSSSSSSSs.k.",
            ".k.k.k..k.k.k..",
            "k..k.k..k.k..k.",
        )
        val WORM = listOf(
            "...w.wkkw.w...",
            "..wkkkkkkkkw..",
            "..wkkwkkwkkw..",
            "...pkkkkkkp...",
            "...aPpppPpa...",
            "...ppppppPp...",
            "...aPpppppa...",
            "...ppPppppp...",
            "..aPpppppPpa..",
            "..pppPpppppp..",
            ".dddpppppppdd.",
            "dddddddddddddd",
        )
        val HARPY_PAL = mapOf(
            'f' to 0xFF8A5A3A.toInt(), 'F' to 0xFFB98A5A.toInt(), 'p' to 0xFFE8C9A0.toInt(), 'k' to 0xFF2A1E16.toInt(),
            'y' to 0xFFE8B830.toInt(), 'r' to 0xFFD04A3A.toInt(), 'w' to 0xFFF4EEDC.toInt(),
        )
        val HARPY_UP = listOf(
            "f..........f",
            "ff........ff",
            "fFf......fFf",
            "fFFf.kk.fFFf",
            ".fFFfpppfFf.",
            "..fFfpkpfF..",
            "...fFpppFf..",
            "....fyyf....",
            ".....yy.....",
            "....y..y....",
        )
        val HARPY_DOWN = listOf(
            "............",
            "............",
            "......kk....",
            "....ffpppf..",
            "..fFfFpkpFff",
            ".fFFFfpppfFFf",
            "fFFf.fFFf.fFf",
            "ff..fyyf...ff",
            "f....yy....f.",
            ".....y..y...",
        )
        val HORNET = listOf(
            "ww....ww",
            ".wwwwww.",
            "..kyyk..",
            ".kyykyyk",
            "ryykyyk.",
            ".rykkyk.",
            "..rk.kr.",
        )
        val SKELETON = listOf(
            "..bbbb..",
            ".bbbbbb.",
            ".bgbbgb.",
            ".bbbbbb.",
            "..bkkb..",
            "..sbbs..",
            ".bbBBbb.",
            "b.sBBs.b",
            "b..BB..b",
            "...bb...",
            "..b..b..",
            ".bb..bb.",
        )
        val LICH = listOf(
            "..mmmmmm..gg",
            ".mMMMMMMm.gg",
            ".mMbbbbMm.c.",
            ".mMbgbgMm.c.",
            "..mMbbbMm.c.",
            "..mmMMmmm.c.",
            ".bmmMMmmmbc.",
            ".b.mMMMm.b..",
            "...mMMMMm.c.",
            "..mmMMMMmmc.",
            "..mMMMMMMmc.",
            ".mmmmmmmmmm.",
        )
        val GOLEM = listOf(
            "....aaaaaa....",
            "...alaaaaAa...",
            "...acckkcca...",
            "...aaakkaaa...",
            "..AAaaaaaaAA..",
            ".aaAaaccaaAaa.",
            "aaaAAaCCaAAaaa",
            "aal.AaaaaaA.lA",
            "aa..AacmaaA.aA",
            "Aa..AaaaaaA.AA",
            "....aaAAaaa...",
            "....aa..aa....",
            "...aaa..aaa...",
            "..AAAA..AAAA..",
        )
        val DRAKE = listOf(
            "..............dd.dd...",
            ".....D.......dddddd...",
            "....DdD.....ddeddedd..",
            "...DdddD...ddddddddww.",
            "..DdpppdDdddddddddddw.",
            ".DdpppppdddddddddddD..",
            ".DdppppdddddddddddD...",
            "DDdddddddddddddddDD...",
            "Dd.dddddpppddddd......",
            "...dddddpppdddd.......",
            "...ddDDdddddDDdd......",
            "..ddD..dD..dD.dd......",
            "..dD...dD..dD.dd..ff..",
            ".DD....DD..DD.DD.fff..",
        )
        val CHICK1 = listOf(
            "...r...",
            "..wwd..",
            ".wwwwo.",
            "wwwwg..",
            ".wwwg..",
            "..o.o..",
        )
        val CHICK2 = listOf(
            ".......",
            ".......",
            "..wwwd.",
            ".wwwwwo",
            "wwwwgr.",
            "..o.o..",
        )
        val FIRE1 = listOf(
            "....y...",
            "...yoy..",
            "..yooyy.",
            "..yooy..",
            "..LlLlL.",
            ".LLLLLL.",
            "..ssss..",
        )
        val FIRE2 = listOf(
            "...y....",
            "..yoy...",
            "..yooy..",
            ".yyooyy.",
            "..LlLlL.",
            ".LLLLLL.",
            "..ssss..",
        )
        val RABBIT1 = listOf(
            "......e.",
            "......ee",
            ".....bbb",
            ".bbbbbbn",
            "wbbbbbb.",
            ".bb..bb.",
        )
        val RABBIT2 = listOf(
            "......e.",
            ".....ee.",
            ".....bbb",
            "bbbbbbbn",
            "wbbbbbb.",
            "bb....bb",
        )
        val DUCK1 = listOf(
            ".....gg.",
            "....gdgo",
            ".bbbwwg.",
            "bbbbwww.",
            ".wwwww..",
        )
        val DUCK2 = listOf(
            ".....gg.",
            "....gdgo",
            ".bbbwwg.",
            ".bbbwww.",
            "..wwww..",
        )
        val PERSON1 = listOf(
            "..rrrr..",
            ".rrrrrr.",
            ".rhhhhr.",
            ".hdhhdh.",
            "..hHHh..",
            ".ssssss.",
            "hsSssSsh",
            "hssssssh",
            ".sSssSs.",
            ".pppppp.",
            ".pp..pp.",
            ".bb..bb.",
        )
        val PERSON2 = listOf(
            "..rrrr..",
            ".rrrrrr.",
            ".rhhhhr.",
            ".hdhhdh.",
            "..hHHh..",
            ".ssssss.",
            "hsSssSsh",
            "hssssssh",
            ".sSssSs.",
            ".pppppp.",
            "..pppp..",
            "..bbbb..",
        )
    }
}
