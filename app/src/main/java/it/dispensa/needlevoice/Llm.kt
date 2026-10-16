package it.dispensa.needlevoice

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Client unico per i modelli: Groq, Gemini, OpenRouter, Mistral (gratuiti) + OpenAI, Anthropic (facoltativi). I modelli vengono scelti dinamicamente dall'elenco del provider. */
class Llm(private val s: Store) {
    data class Model(val provider: String, val id: String, val label: String, val vision: Boolean)

    companion object {
        private val cache = HashMap<String, List<String>>()
        val SIGNUP = mapOf("Groq" to "https://console.groq.com/keys", "Gemini" to "https://aistudio.google.com/apikey",
            "OpenRouter" to "https://openrouter.ai/settings/keys", "Mistral" to "https://console.mistral.ai/api-keys")
        /** Riconosce il provider dalla chiave incollata. */
        fun detect(k: String): String? = when {
            k.startsWith("gsk_") -> "Groq"; k.startsWith("AIza") -> "Gemini"; k.startsWith("sk-or-") -> "OpenRouter"
            k.startsWith("sk-ant-") -> "Anthropic"; k.startsWith("sk-") -> "OpenAI"; k.length in 28..40 && k.all { it.isLetterOrDigit() } -> "Mistral"; else -> null }
    }

    fun key(p: String) = when (p) { "Groq" -> s.groqKey; "Gemini" -> s.geminiKey; "OpenRouter" -> s.orKey; "Mistral" -> s.mistralKey; "OpenAI" -> s.openaiKey; "Anthropic" -> s.anthropicKey; else -> "" }
    fun setKey(p: String, v: String) { when (p) { "Groq" -> s.groqKey = v; "Gemini" -> s.geminiKey = v; "OpenRouter" -> s.orKey = v; "Mistral" -> s.mistralKey = v; "OpenAI" -> s.openaiKey = v; "Anthropic" -> s.anthropicKey = v } }
    val providers = listOf("Groq", "Gemini", "OpenRouter", "Mistral", "OpenAI", "Anthropic")
    fun active() = providers.filter { key(it).isNotBlank() }
    fun hasBrain() = active().isNotEmpty()

    private fun base(p: String) = when (p) { "Groq" -> "https://api.groq.com/openai/v1"; "OpenRouter" -> "https://openrouter.ai/api/v1"; "Mistral" -> "https://api.mistral.ai/v1"; "OpenAI" -> "https://api.openai.com/v1"; else -> "" }

    private fun http(url: String, headers: Map<String, String>, body: JSONObject?, timeout: Int = 60000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = timeout
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) { c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = c.responseCode
        val t = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code ${t.take(140)}")
        return t
    }

    /** Prova la chiave sui provider (per formati non riconosciuti dal prefisso). */
    fun probe(k: String): String? {
        val order = listOf("Gemini", "Groq", "OpenRouter", "Mistral", "OpenAI")
        for (p in order) {
            try {
                if (p == "Gemini") http("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1&key=$k", emptyMap(), null, 15000)
                else http("${base(p)}/models", mapOf("Authorization" to "Bearer $k"), null, 15000)
                if (p == "OpenRouter") continue
                return p
            } catch (_: Exception) { }
        }
        return null
    }

    /** Elenco id modelli del provider (in cache). */
    private fun models(p: String): List<String> = cache.getOrPut(p) {
        try {
            when (p) {
                "Gemini" -> { val a = JSONObject(http("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=${key(p)}", emptyMap(), null)).getJSONArray("models")
                    (0 until a.length()).map { a.getJSONObject(it) }.filter { m -> (0 until (m.optJSONArray("supportedGenerationMethods")?.length() ?: 0)).any { m.getJSONArray("supportedGenerationMethods").getString(it) == "generateContent" } }
                        .map { it.getString("name").removePrefix("models/") } }
                "Anthropic" -> listOf("claude-sonnet-4-5")
                "OpenAI" -> listOf("gpt-4o-mini")
                else -> { val h = if (key(p).isNotBlank()) mapOf("Authorization" to "Bearer ${key(p)}") else emptyMap()
                    val a = JSONObject(http("${base(p)}/models", h, null)).getJSONArray("data"); (0 until a.length()).map { a.getJSONObject(it).getString("id") } }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun first(l: List<String>, prefs: List<String>, bad: List<String> = emptyList()): String? {
        for (pr in prefs) l.firstOrNull { it.contains(pr, true) && bad.none { b -> it.contains(b, true) } }?.let { return it }
        return null
    }

    /** Scelta del modello migliore per ruolo. */
    fun pick(p: String, vision: Boolean = false, alt: Int = 0): Model? {
        val m = models(p)
        val bad = listOf("tts", "image", "live", "embed", "audio", "whisper", "guard", "moderation", "transcribe", "-lite", "thinking-exp")
        val id: String? = when (p) {
            "Groq" -> if (vision) first(m, listOf("llama-4-scout", "llama-4-maverick", "vision"), bad) else if (alt == 0) first(m, listOf("llama-3.3-70b", "llama-3.1-70b", "llama-3.1-8b"), bad) else first(m, listOf("gpt-oss-120b", "qwen3-32b", "kimi", "gpt-oss-20b"), bad)
            "Gemini" -> first(m, listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-flash-latest", "gemini-1.5-flash"), bad + "preview") ?: first(m, listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini"), bad) ?: "gemini-2.0-flash"
            "OpenRouter" -> { val f = m.filter { it.endsWith(":free") }
                if (vision) first(f, listOf("qwen2.5-vl", "qwen-vl", "qwen3-vl", "gemma-3", "llama-4", "pixtral", "mistral-small-3"), bad)
                else if (alt == 0) first(f, listOf("deepseek-r1", "deepseek-chat", "deepseek-v3", "qwen3", "llama-3.3-70b", "gpt-oss"), bad) else first(f, listOf("gpt-oss", "qwen3", "gemma-3", "llama-3.3"), bad) }
            "Mistral" -> first(m, if (vision) listOf("pixtral-large", "mistral-small-latest", "pixtral") else listOf("mistral-large-latest", "mistral-small-latest"), bad) ?: "mistral-small-latest"
            "OpenAI" -> "gpt-4o-mini"; "Anthropic" -> "claude-sonnet-4-5"; else -> null }
        return id?.let { Model(p, it, "$p · ${it.substringAfterLast('/').removeSuffix(":free")}", vision) }
    }

    fun ttsModel(): String = first(models("Gemini"), listOf("flash-preview-tts", "flash-tts", "tts")) ?: "gemini-2.5-flash-preview-tts"

    /** Modello veloce per pianificare azioni. */
    fun planner(): Model? = listOf("Groq", "Gemini", "OpenRouter", "Mistral", "OpenAI", "Anthropic").firstOrNull { key(it).isNotBlank() }?.let { pick(it) }
    fun judge(): Model? = listOf("Gemini", "Groq", "Mistral", "OpenRouter", "OpenAI", "Anthropic").firstOrNull { key(it).isNotBlank() }?.let { pick(it) }

    /** I membri del consiglio: modelli diversi, più famiglie possibili. */
    fun council(withImages: Boolean): List<Model> = buildList {
        for (p in active()) {
            if (withImages) { pick(p, true)?.let { add(it) } }
            else { pick(p)?.let { add(it) }; if (p == "Groq" || p == "OpenRouter") pick(p, false, 1)?.takeIf { x -> none { it.id == x.id } }?.let { add(it) } }
        }
    }

    fun chat(m: Model, system: String, user: String, imagesB64: List<String> = emptyList(), json: Boolean = false, maxTokens: Int = 900, temp: Double = 0.3): String {
        val k = key(m.provider)
        return when (m.provider) {
            "Gemini" -> {
                val parts = JSONArray().put(JSONObject().put("text", user))
                imagesB64.forEach { parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", it))) }
                val gc = JSONObject().put("temperature", temp).put("maxOutputTokens", maxTokens); if (json) gc.put("responseMimeType", "application/json")
                val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts))).put("generationConfig", gc)
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                JSONObject(http("https://generativelanguage.googleapis.com/v1beta/models/${m.id}:generateContent?key=$k", emptyMap(), body))
                    .getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").let { a -> (0 until a.length()).joinToString("") { a.getJSONObject(it).optString("text") } }
            }
            "Anthropic" -> {
                val c = JSONArray(); imagesB64.forEach { c.put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", it))) }
                c.put(JSONObject().put("type", "text").put("text", user))
                JSONObject(http("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to k, "anthropic-version" to "2023-06-01"),
                    JSONObject().put("model", m.id).put("max_tokens", maxTokens).put("system", system).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", c)))))
                    .getJSONArray("content").getJSONObject(0).getString("text")
            }
            else -> {
                val content: Any = if (imagesB64.isEmpty()) user else JSONArray().also { a -> a.put(JSONObject().put("type", "text").put("text", user)); imagesB64.forEach { a.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$it"))) } }
                val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", content))
                val body = JSONObject().put("model", m.id).put("messages", msgs).put("temperature", temp).put("max_tokens", maxTokens)
                if (json && m.provider != "OpenRouter") body.put("response_format", JSONObject().put("type", "json_object"))
                val h = mutableMapOf("Authorization" to "Bearer $k"); if (m.provider == "OpenRouter") { h["HTTP-Referer"] = "https://github.com/aelleicsics92/needle-voice-android"; h["X-Title"] = "Needle" }
                JSONObject(http("${base(m.provider)}/chat/completions", h, body)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    .replace(Regex("(?s)<think>.*?</think>"), "").trim()
            }
        }
    }
}
