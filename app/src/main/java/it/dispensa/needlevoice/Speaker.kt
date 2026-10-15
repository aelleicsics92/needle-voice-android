package it.dispensa.needlevoice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/** Voce: neurale femminile (Gemini / OpenAI / Google) con ripiego sulla voce di sistema. Vive oltre le activity. */
object Speaker {
    private var tts: TextToSpeech? = null
    @Volatile private var gen = 0
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile var lastEngine = ""

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { st ->
            val t = tts ?: return@TextToSpeech
            if (st == TextToSpeech.SUCCESS) {
                t.language = Locale.ITALIAN
                val best = t.voices?.filter { it.locale.language == "it" && !it.features.contains("notInstalled") }
                    ?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }.thenByDescending { it.name.contains("-x-iti", true) || it.name.contains("-x-itd", true) })?.firstOrNull()
                if (best != null) t.voice = best
            }
        }
    }

    fun stop() {
        gen++
        try { player?.stop(); player?.release() } catch (_: Exception) {}; player = null
        try { track?.stop(); track?.release() } catch (_: Exception) {}; track = null
        tts?.stop()
    }

    fun say(ctx: Context, text: String) {
        val t = text.replace(Regex("[*_#`]+"), "").trim(); if (t.isBlank()) return
        init(ctx); stop(); val my = gen
        val app = ctx.applicationContext
        thread {
            val st = Store(app)
            val engines = buildList<Pair<String, () -> Unit>> {
                if (st.geminiKey.isNotBlank()) add("Gemini" to { geminiTts(app, st, t, my) })
                if (st.openaiKey.isNotBlank()) add("OpenAI" to { openaiTts(app, st, t, my) })
                add("Google" to { googleTts(app, t, my) })
            }
            for ((n, f) in engines) { if (my != gen) return@thread; try { f(); lastEngine = n; return@thread } catch (_: Exception) { } }
            if (my == gen) { lastEngine = "Sistema"; tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "nv") }
        }
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 12000; c.readTimeout = 40000
        c.setRequestProperty("Content-Type", "application/json"); headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        return c.inputStream.use { it.readBytes() }
    }

    private fun geminiTts(app: Context, st: Store, text: String, my: Int) {
        val llm = Llm(st)
        val model = llm.ttsModel()
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Say naturally, warmly and clearly, in Italian: $text")))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Kore")))))
        val r = JSONObject(String(post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${st.geminiKey}", emptyMap(), body)))
        val b64 = r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData").getString("data")
        val pcm = Base64.decode(b64, Base64.DEFAULT)
        if (my != gen) return
        val at = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size).setTransferMode(AudioTrack.MODE_STATIC).build()
        track = at; at.write(pcm, 0, pcm.size); at.play()
        val ms = pcm.size / 2 * 1000L / 24000L
        var w = 0L; while (w < ms + 150 && my == gen) { Thread.sleep(100); w += 100 }
        try { at.stop(); at.release() } catch (_: Exception) {}; track = null
    }

    private fun playFile(app: Context, f: File, my: Int) {
        if (my != gen) return
        val latch = CountDownLatch(1)
        val mp = MediaPlayer().apply { setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setDataSource(f.absolutePath); setOnCompletionListener { latch.countDown() }; setOnErrorListener { _, _, _ -> latch.countDown(); true }; prepare() }
        player = mp; mp.start()
        while (!latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)) { if (my != gen) break }
        try { mp.release() } catch (_: Exception) {}; player = null; f.delete()
    }

    private fun openaiTts(app: Context, st: Store, text: String, my: Int) {
        val mp3 = post("https://api.openai.com/v1/audio/speech", mapOf("Authorization" to "Bearer ${st.openaiKey}"),
            JSONObject().put("model", "tts-1").put("voice", "nova").put("input", text).put("response_format", "mp3"))
        val f = File(app.cacheDir, "tts${System.nanoTime()}.mp3"); f.writeBytes(mp3); playFile(app, f, my)
    }

    /** Voce di Google Traduttore (senza chiave). Frasi spezzate in blocchi. */
    private fun googleTts(app: Context, text: String, my: Int) {
        val parts = Regex("[^.!?;:\\n]+[.!?;:]?").findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }.flatMap { s -> s.chunked(170).asSequence() }.toList()
        for (p in parts) {
            if (my != gen) return
            val c = URL("https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=it&q=${URLEncoder.encode(p, "UTF-8")}").openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"); c.connectTimeout = 10000; c.readTimeout = 20000
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            val f = File(app.cacheDir, "tts${System.nanoTime()}.mp3"); f.writeBytes(c.inputStream.use { it.readBytes() }); playFile(app, f, my)
        }
    }
}
