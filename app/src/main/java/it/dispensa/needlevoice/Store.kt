package it.dispensa.needlevoice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("needle_voice", Context.MODE_PRIVATE)

    var haUrl: String
        get() = p.getString("ha_url", "").orEmpty()
        set(v) = p.edit().putString("ha_url", v.trim().trimEnd('/')).apply()
    var haToken: String
        get() = p.getString("ha_token", "").orEmpty()
        set(v) = p.edit().putString("ha_token", v.trim()).apply()
    var city: String
        get() = p.getString("city", "Bracciano").orEmpty()
        set(v) = p.edit().putString("city", v.trim()).apply()
    var openaiKey: String
        get() = p.getString("k_openai", "").orEmpty()
        set(v) = p.edit().putString("k_openai", v.trim()).apply()
    var anthropicKey: String
        get() = p.getString("k_anthropic", "").orEmpty()
        set(v) = p.edit().putString("k_anthropic", v.trim()).apply()
    var geminiKey: String
        get() = p.getString("k_gemini", "").orEmpty()
        set(v) = p.edit().putString("k_gemini", v.trim()).apply()
    var askedPerms: Boolean
        get() = p.getBoolean("asked", false)
        set(v) = p.edit().putBoolean("asked", v).apply()
    fun hasAnyAiKey() = openaiKey.isNotBlank() || anthropicKey.isNotBlank() || geminiKey.isNotBlank()
    var speakReplies: Boolean
        get() = p.getBoolean("speak", true)
        set(v) = p.edit().putBoolean("speak", v).apply()

    // Note: lista JSON di {id, text, ts}
    fun notes(): List<Triple<Long, String, Long>> {
        val a = try { JSONArray(p.getString("notes_json", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).map { val o = a.getJSONObject(it); Triple(o.getLong("id"), o.getString("text"), o.optLong("ts")) }
    }
    private fun saveNotes(l: List<Triple<Long, String, Long>>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.first).put("text", it.second).put("ts", it.third)) }
        p.edit().putString("notes_json", a.toString()).apply()
    }
    fun addNote(t: String) { val n = System.currentTimeMillis(); saveNotes(notes() + Triple(n, t.trim(), n)) }
    fun deleteNote(id: Long) = saveNotes(notes().filter { it.first != id })
    fun clearNotes() = saveNotes(emptyList())
    /** Elimina le note che contengono il testo; ritorna quante. */
    fun deleteNotesMatching(q: String): Int {
        val k = q.lowercase().trim(); val all = notes()
        val keep = all.filter { !it.second.lowercase().contains(k) }
        saveNotes(keep); return all.size - keep.size
    }
    fun searchNotes(q: String) = notes().filter { it.second.lowercase().contains(q.lowercase().trim()) }

    // Timer: lista di (id, fine)
    fun timers(): List<Pair<Long, Long>> { val a = try { JSONArray(p.getString("timers_json", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).map { val o = a.getJSONObject(it); o.getLong("id") to o.getLong("at") }.filter { it.second > System.currentTimeMillis() - 60_000 } }
    private fun saveTimers(l: List<Pair<Long, Long>>) { val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.first).put("at", it.second)) }; p.edit().putString("timers_json", a.toString()).apply() }
    fun addTimer(id: Long, at: Long) = saveTimers(timers() + (id to at))
    fun removeTimer(id: Long) = saveTimers(timers().filter { it.first != id })
    fun clearTimers() = saveTimers(emptyList())
    var userName: String
        get() = p.getString("user_name", "").orEmpty()
        set(v) = p.edit().putString("user_name", v.trim()).apply()
}
