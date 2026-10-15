package it.dispensa.needlevoice

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Interroga ChatGPT, Claude e Gemini in parallelo (via API key) con testo e immagini opzionali. */
class AiHub(private val s: Store) {
    data class Answer(val agent: String, val text: String, val ok: Boolean)

    fun enabledAgents(): List<String> = buildList {
        if (s.openaiKey.isNotBlank()) add("ChatGPT")
        if (s.anthropicKey.isNotBlank()) add("Claude")
        if (s.geminiKey.isNotBlank()) add("Gemini")
    }

    fun askAll(q: String, images: List<Bitmap>, onAnswer: (Answer) -> Unit) {
        val b64 = images.map { val o = ByteArrayOutputStream(); scaled(it).compress(Bitmap.CompressFormat.JPEG, 80, o); Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP) }
        val jobs = mapOf<String, () -> String>(
            "ChatGPT" to { openai(q, b64) }, "Claude" to { claude(q, b64) }, "Gemini" to { gemini(q, b64) })
        enabledAgents().forEach { name ->
            thread {
                onAnswer(try { Answer(name, jobs[name]!!.invoke().trim(), true) } catch (e: Exception) { Answer(name, e.message ?: "errore", false) })
            }
        }
    }

    private fun scaled(b: Bitmap): Bitmap { val m = 1280; val r = m.toFloat() / maxOf(b.width, b.height); return if (r >= 1) b else Bitmap.createScaledBitmap(b, (b.width * r).toInt(), (b.height * r).toInt(), true) }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 90000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json"); headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val t = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code ${t.take(160)}")
        return JSONObject(t)
    }

    private fun openai(q: String, imgs: List<String>): String {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", q))
        imgs.forEach { content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$it"))) }
        val r = post("https://api.openai.com/v1/chat/completions", mapOf("Authorization" to "Bearer ${s.openaiKey}"),
            JSONObject().put("model", "gpt-4o-mini").put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content))))
        return r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun claude(q: String, imgs: List<String>): String {
        val content = JSONArray()
        imgs.forEach { content.put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", it))) }
        content.put(JSONObject().put("type", "text").put("text", q))
        val r = post("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to s.anthropicKey, "anthropic-version" to "2023-06-01"),
            JSONObject().put("model", "claude-sonnet-4-5").put("max_tokens", 1024).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content))))
        return r.getJSONArray("content").getJSONObject(0).getString("text")
    }

    private fun gemini(q: String, imgs: List<String>): String {
        val parts = JSONArray().put(JSONObject().put("text", q))
        imgs.forEach { parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", it))) }
        val r = post("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${s.geminiKey}", emptyMap(),
            JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts))))
        return r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }
}
