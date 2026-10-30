package org.json
// In-memory stand-ins for Android's org.json, enough to type-check and round-trip on a plain JVM.
class JSONException(m: String = "") : Exception(m)
class JSONArray() {
    val items = ArrayList<Any?>()
    constructor(src: String) : this() { items.addAll((JP(src).value() as JSONArray).items) }
    constructor(c: Collection<*>) : this() { items.addAll(c) }
    fun length(): Int = items.size
    fun put(v: Any?): JSONArray { items.add(v); return this }
    fun getString(i: Int): String = items[i] as String
    fun getInt(i: Int): Int = (items[i] as Number).toInt()
    fun getJSONObject(i: Int): JSONObject = items[i] as JSONObject
    fun optJSONObject(i: Int): JSONObject? = items.getOrNull(i) as? JSONObject
    fun optString(i: Int): String = items.getOrNull(i) as? String ?: ""
    fun optInt(i: Int, d: Int): Int = (items.getOrNull(i) as? Number)?.toInt() ?: d
    fun optInt(i: Int): Int = optInt(i, 0)
    override fun toString(): String = Jw.w(this)
}
class JSONObject() {
    val m = LinkedHashMap<String, Any?>()
    constructor(src: String) : this() { m.putAll((JP(src).value() as JSONObject).m) }
    fun put(k: String, v: Any?): JSONObject { m[k] = v; return this }
    fun has(k: String) = m.containsKey(k)
    private fun num(k: String): Number? = m[k] as? Number
    fun getString(k: String): String = m[k] as? String ?: throw JSONException(k)
    fun optString(k: String): String = m[k] as? String ?: ""
    fun optString(k: String, d: String): String = m[k] as? String ?: d
    fun getInt(k: String): Int = num(k)?.toInt() ?: throw JSONException(k)
    fun optInt(k: String): Int = num(k)?.toInt() ?: 0
    fun optInt(k: String, d: Int): Int = num(k)?.toInt() ?: d
    fun getLong(k: String): Long = num(k)?.toLong() ?: throw JSONException(k)
    fun optLong(k: String, d: Long): Long = num(k)?.toLong() ?: d
    fun getDouble(k: String): Double = num(k)?.toDouble() ?: throw JSONException(k)
    fun optDouble(k: String, d: Double): Double = num(k)?.toDouble() ?: d
    fun getBoolean(k: String): Boolean = m[k] as? Boolean ?: throw JSONException(k)
    fun optBoolean(k: String, d: Boolean): Boolean = m[k] as? Boolean ?: d
    fun getJSONArray(k: String): JSONArray = m[k] as? JSONArray ?: throw JSONException(k)
    fun optJSONArray(k: String): JSONArray? = m[k] as? JSONArray
    fun getJSONObject(k: String): JSONObject = m[k] as? JSONObject ?: throw JSONException(k)
    fun optJSONObject(k: String): JSONObject? = m[k] as? JSONObject
    override fun toString(): String = Jw.w(this)
}

object Jw {
    fun w(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        is JSONObject -> v.m.entries.joinToString(",", "{", "}") { w(it.key) + ":" + w(it.value) }
        is JSONArray -> v.items.joinToString(",", "[", "]") { w(it) }
        else -> v.toString()
    }
}
class JP(val s: String) {
    var i = 0
    fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    fun value(): Any? {
        ws()
        val c = s[i]
        return when {
            c == '{' -> { i++; val o = JSONObject(); ws(); if (s[i] == '}') { i++; return o }
                while (true) { ws(); val k = str(); ws(); i++; o.put(k, value()); ws(); if (s[i] == ',') i++ else { i++; break } }; o }
            c == '[' -> { i++; val a = JSONArray(); ws(); if (s[i] == ']') { i++; return a }
                while (true) { a.put(value()); ws(); if (s[i] == ',') i++ else { i++; break } }; a }
            c == '"' -> str()
            s.startsWith("true", i) -> { i += 4; true }
            s.startsWith("false", i) -> { i += 5; false }
            s.startsWith("null", i) -> { i += 4; null }
            else -> { val st = i; while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++; val t = s.substring(st, i); if (t.any { it in ".eE" }) t.toDouble() else t.toLong().let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it } }
        }
    }
    fun str(): String { i++; val sb = StringBuilder(); while (s[i] != '"') { if (s[i] == '\\') { i++; when (s[i]) { 'n' -> sb.append('\n'); 'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }; else -> sb.append(s[i]) } } else sb.append(s[i]); i++ }; i++; return sb.toString() }
}
