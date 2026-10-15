package it.dispensa.needlevoice

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class HomeAssistant(private val s: Store) {
    data class Entity(val id: String, val name: String, val state: String)

    private fun req(path: String, method: String = "GET", body: JSONObject? = null): String {
        require(s.haUrl.startsWith("http")) { "Home Assistant non configurato" }
        require(s.haToken.isNotBlank()) { "Token Home Assistant mancante" }
        val c = URL(s.haUrl + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 6000; c.readTimeout = 8000
        c.setRequestProperty("Authorization", "Bearer ${s.haToken}")
        c.setRequestProperty("Content-Type", "application/json")
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("HA errore $code")
        return txt
    }

    fun ping(): Boolean { req("/api/"); return true }

    fun entities(): List<Entity> {
        val a = JSONArray(req("/api/states"))
        return (0 until a.length()).map { a.getJSONObject(it) }
            .filter { val id = it.optString("entity_id"); id.startsWith("light.") || id.startsWith("switch.") }
            .map { Entity(it.getString("entity_id"), it.optJSONObject("attributes")?.optString("friendly_name", it.getString("entity_id")) ?: it.getString("entity_id"), it.optString("state")) }
    }

    private fun norm(x: String) = x.lowercase(Locale.ITALIAN).replace(Regex("[^a-zà-ÿ0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun control(on: Boolean, target: String, all: Boolean): String {
        val list = entities()
        val sel = if (all && target.isBlank()) list else {
            val scored = list.map { e -> e to maxOf(Fuzzy.sim(target, e.name), Fuzzy.sim(target, e.id.substringAfter('.').replace('_', ' '))) }
                .filter { it.second >= 0.6 }.sortedByDescending { it.second }
            if (all) scored.filter { it.second >= 0.6 }.map { it.first } else scored.take(1).map { it.first }
        }
        if (sel.isEmpty()) throw IllegalStateException("Non trovo \"$target\" in Home Assistant")
        sel.forEach { req("/api/services/${it.id.substringBefore('.')}/${if (on) "turn_on" else "turn_off"}", "POST", JSONObject().put("entity_id", it.id)) }
        return (if (on) "Acceso" else "Spento") + if (sel.size > 1) " (${sel.size} dispositivi)" else ": ${sel[0].name}"
    }

    fun toggle(e: Entity) { req("/api/services/${e.id.substringBefore('.')}/${if (e.state == "on") "turn_off" else "turn_on"}", "POST", JSONObject().put("entity_id", e.id)) }
}
