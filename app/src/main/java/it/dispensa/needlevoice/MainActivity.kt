package it.dispensa.needlevoice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var notesView: TextView
    private var silenceTimer: CountDownTimer? = null
    private var timer: CountDownTimer? = null
    private var listening = false
    private val prefs by lazy { getSharedPreferences("needle_voice", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildUi()
        refreshNotes()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(0xFF10120F.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val brand = TextView(this).apply {
            text = "NEEDLE / VOICE"
            textSize = 13f; letterSpacing = .18f; setTextColor(0xFFC7FF4A.toInt())
        }
        val title = TextView(this).apply {
            text = "Comandi,\nsenza mani."
            textSize = 34f; setTextColor(0xFFF3F4EF.toInt()); setPadding(0, 18, 0, 8)
        }
        status = TextView(this).apply { text = "Pronto · premi e parla"; textSize = 15f; setTextColor(0xFFB5B9AE.toInt()); setPadding(0, 0, 0, 20) }
        val listen = Button(this).apply {
            text = "🎙  ASCOLTA"
            textSize = 18f; setTextColor(0xFF10120F.toInt()); setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFC7FF4A.toInt()))
            setOnClickListener { startListening() }
        }
        transcript = TextView(this).apply { text = "La tua richiesta apparirà qui."; textSize = 16f; setTextColor(0xFFF3F4EF.toInt()); setPadding(0, 18, 0, 18) }
        content.addView(brand); content.addView(title); content.addView(status); content.addView(listen, matchWidth())
        content.addView(transcript)
        content.addView(sectionTitle("PROVA A DIRE"))
        content.addView(body("• Accendi la luce della cucina\n• Spegni la presa del salotto\n• Spegni tutte le luci\n• Imposta un timer di 5 minuti\n• Ricordami di comprare il pane\n• Aggiungi un evento al calendario\n• Apri WhatsApp / manda un messaggio\n• Chiama un numero / invia un SMS\n• Naviga verso Bracciano\n• Che tempo fa a Bracciano?\n• Apri YouTube / musica"))
        content.addView(sectionTitle("NOTE RAPIDE"))
        notesView = body("")
        content.addView(notesView)
        val addNote = actionButton("＋  Scrivi una nota") { promptNote() }
        content.addView(addNote, matchWidth())
        content.addView(sectionTitle("CASA SMART · CONTROLLO DIRETTO"))
        content.addView(body("Collega Home Assistant per accendere e spegnere luci e prese con la voce, senza aprire Tapo o Smart Life. I dispositivi Tapo/Tuya devono essere già esposti a Home Assistant."))
        content.addView(actionButton("⚙  Configura Home Assistant") { configureHomeAssistant() }, matchWidth())
        content.addView(actionButton("Verifica connessione smart home") { testHomeAssistant() }, matchWidth())
        content.addView(actionButton("Apri Tapo") { launchPackage("com.tplink.iot", "Tapo") }, matchWidth())
        content.addView(actionButton("Apri Smart Life") { launchPackage("com.tuya.smartlife", "Smart Life") }, matchWidth())
        content.addView(sectionTitle("AZIONI RAPIDE"))
        content.addView(actionButton("Timer / sveglia Android") { openClock() }, matchWidth())
        content.addView(actionButton("Meteo") { openWeather("Bracciano") }, matchWidth())
        content.addView(actionButton("WhatsApp") { openWhatsApp() }, matchWidth())
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
    private fun sectionTitle(s: String) = TextView(this).apply { text = s; textSize = 12f; letterSpacing = .12f; setTextColor(0xFFC7FF4A.toInt()); setPadding(0, 18, 0, 8) }
    private fun body(s: String) = TextView(this).apply { text = s; textSize = 15f; setTextColor(0xFFD8DBD2.toInt()); setPadding(0, 4, 0, 12) }
    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(0xFFF3F4EF.toInt()); setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF282C25.toInt())); setOnClickListener { action() }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { say("Il riconoscimento vocale non è disponibile su questo telefono."); return }
        stopListening(false)
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true; status.text = "In ascolto… parla adesso"; startSilenceTimeout() }
                override fun onBeginningOfSpeech() { silenceTimer?.cancel(); status.text = "Ti ascolto…" }
                override fun onRmsChanged(rmsdB: Float) { }
                override fun onBufferReceived(buffer: ByteArray?) { }
                override fun onEndOfSpeech() { status.text = "Elaboro…"; silenceTimer?.cancel() }
                override fun onError(error: Int) { stopListening(false); status.text = "Pronto · premi e parla" }
                override fun onResults(results: Bundle?) {
                    stopListening(false)
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) { status.text = "Nessun comando rilevato"; return }
                    transcript.text = "«$text»"
                    handleCommand(text)
                }
                override fun onPartialResults(partialResults: Bundle?) { }
                override fun onEvent(eventType: Int, params: Bundle?) { }
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dimmi cosa devo fare")
            }
            recognizer?.startListening(i)
            startSilenceTimeout()
        } catch (_: Exception) { stopListening(false); say("Non riesco ad avviare il microfono.") }
    }

    private fun startSilenceTimeout() {
        silenceTimer?.cancel()
        silenceTimer = object : CountDownTimer(7000, 1000) {
            override fun onTick(ms: Long) { }
            override fun onFinish() { if (listening) { stopListening(true); status.text = "Disattivato · nessun comando" } }
        }.start()
    }

    private fun stopListening(cancel: Boolean) {
        listening = false; silenceTimer?.cancel(); silenceTimer = null
        try { if (cancel) recognizer?.cancel() else recognizer?.stopListening() } catch (_: Exception) { }
        recognizer?.destroy(); recognizer = null
    }

    private fun handleCommand(raw: String) {
        val c = raw.lowercase(Locale.ITALIAN).trim()
        status.text = "Comando ricevuto"
        when {
            "timer" in c || "conto alla rovescia" in c -> {
                val n = Regex("(\\d+)").find(c)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
                val millis = when { "ora" in c || "ore" in c -> n * 3_600_000L; "second" in c -> n * 1000L; else -> n * 60_000L }
                timer?.cancel(); timer = object : CountDownTimer(millis, 1000) {
                    override fun onTick(ms: Long) { status.text = "Timer: ${ms / 1000}s" }
                    override fun onFinish() { status.text = "⏰ Timer terminato"; say("Timer terminato") }
                }.start(); say("Timer avviato")
            }
            "nota" in c || "ricordami" in c || "ricorda" in c -> {
                val note = raw.replace(Regex("(?i)^.*?(nota|ricordami|ricorda)(\\s+di)?\\s*"), "").trim().ifBlank { raw }
                saveNote(note); say("Nota salvata")
            }
            "spegni" in c || "accendi" in c || "attiva" in c || "disattiva" in c || "luce" in c || "luci" in c || "presa" in c || "smart home" in c -> controlSmartHome(c)
            "tapo" in c -> launchPackage("com.tplink.iot", "Tapo")
            "smart life" in c || "smartlife" in c -> launchPackage("com.tuya.smartlife", "Smart Life")
            "whatsapp" in c && ("scrivi" in c || "messaggio" in c || "manda" in c || "invia" in c) -> composeWhatsApp(raw)
            "whatsapp" in c -> openWhatsApp()
            "chiama" in c || "telefon" in c -> { val number = Regex("\\+?[0-9][0-9 ()-]{5,}").find(raw)?.value; if (number != null) dial(number) else { say("Apro il telefono, scegli il contatto."); startActivity(Intent(Intent.ACTION_DIAL)) } }
            "sms" in c || "messaggio" in c -> { val number = Regex("\\+?[0-9][0-9 ()-]{5,}").find(raw)?.value; val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${number ?: ""}")); i.putExtra("sms_body", raw); safeStart(i) }
            "meteo" in c || "tempo fa" in c -> openWeather(Regex("(?i)(?:a|di)\\s+([A-Za-zÀ-ÿ'-]+)").find(raw)?.groupValues?.get(1) ?: "Bracciano")
            "sveglia" in c || "allarme" in c -> openClock()
            "calendario" in c || "evento" in c || "appuntamento" in c -> createCalendarEvent(raw)
            "naviga" in c || "portami" in c || "indicazioni" in c -> navigateTo(raw)
            "musica" in c || "youtube" in c || "riproduci" in c -> openMedia(c)
            "apri" in c -> {
                val pkg = when { "youtube" in c -> "com.google.android.youtube"; "mappe" in c || "maps" in c -> "com.google.android.apps.maps"; "fotocamera" in c || "camera" in c -> "com.android.camera"; "impostazioni" in c -> "com.android.settings"; else -> null }
                if (pkg != null) launchPackage(pkg, pkg) else say("Per questa app, usa il comando Apri Tapo, Smart Life o WhatsApp.")
            }
            else -> { status.text = "Comando non ancora supportato"; say("Non ho ancora un'azione configurata per questo comando.") }
        }
    }

    private fun configureHomeAssistant() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val url = EditText(this).apply { hint = "URL, es. http://192.168.1.20:8123"; setSingleLine(true); setText(prefs.getString("ha_url", "")) }
        val token = EditText(this).apply { hint = "Token di accesso a lunga durata"; setSingleLine(true); setText(prefs.getString("ha_token", "")) }
        box.addView(url); box.addView(token)
        AlertDialog.Builder(this).setTitle("Collega Home Assistant").setMessage("In Home Assistant crea un token di accesso a lunga durata dal tuo profilo. Usa l'URL raggiungibile dal telefono. Il token viene salvato localmente nelle preferenze dell'app.").setView(box)
            .setNegativeButton("Annulla", null).setPositiveButton("Salva") { _, _ ->
                prefs.edit().putString("ha_url", url.text.toString().trim().trimEnd('/')).putString("ha_token", token.text.toString().trim()).apply()
                say("Configurazione salvata"); status.text = "Home Assistant configurato · premi verifica"
            }.show()
    }

    private fun haRequest(path: String, method: String = "GET", payload: JSONObject? = null): String {
        val base = prefs.getString("ha_url", "").orEmpty().trimEnd('/')
        val token = prefs.getString("ha_token", "").orEmpty()
        require(base.startsWith("http://") || base.startsWith("https://")) { "Configura prima l'URL di Home Assistant" }
        require(token.isNotBlank()) { "Configura prima il token di Home Assistant" }
        val conn = URL("$base$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 7000; conn.readTimeout = 7000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        if (payload != null) { conn.doOutput = true; conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) } }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("Home Assistant HTTP $code: $response")
        return response
    }

    private fun testHomeAssistant() {
        status.text = "Verifico Home Assistant…"
        thread {
            try {
                val result = haRequest("/api/")
                runOnUiThread { status.text = "Home Assistant connesso"; say("Home Assistant connesso correttamente") }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Connessione non riuscita"; say(e.message ?: "Connessione non riuscita") }
            }
        }
    }

    private fun controlSmartHome(command: String) {
        val base = command.replace(Regex("(?i)\\b(per favore|per piacere|per cortesia|per la casa|smart home)\\b"), " ").trim()
        val turnOff = listOf("spegni", "disattiva", "spegni tutte").any { it in base }
        val turnOn = listOf("accendi", "attiva").any { it in base }
        if (!turnOff && !turnOn) { say("Dimmi accendi o spegni, poi il nome della luce o della presa."); return }
        val target = base.replace(Regex("(?i)\\b(accendi|spegni|attiva|disattiva|tutte|tutti|il|la|lo|le|gli|i|un|una|luce|luci|lampada|lampade|presa|prese|interruttore|interruttori|di|del|della|dello|dei|degli|delle|in|nel|nella|sul|sulla)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        status.text = "Controllo dispositivi smart…"
        thread {
            try {
                val states = JSONArray(haRequest("/api/states"))
                val candidates = mutableListOf<Pair<String, String>>()
                for (i in 0 until states.length()) {
                    val item = states.getJSONObject(i); val id = item.optString("entity_id")
                    if (!(id.startsWith("light.") || id.startsWith("switch."))) continue
                    val friendly = item.optJSONObject("attributes")?.optString("friendly_name", id) ?: id
                    candidates.add(id to friendly)
                }
                val all = Regex("(?i)\\b(tutte|tutti)\\b").containsMatchIn(base)
                val matches = if (all) candidates.filter { it.first.startsWith("light.") || it.first.startsWith("switch.") }
                    else candidates.filter { (id, name) ->
                        val q = target.lowercase(Locale.ITALIAN)
                        q.isNotBlank() && (name.lowercase(Locale.ITALIAN).contains(q) || q.split(" ").filter { it.length > 2 }.any { name.lowercase(Locale.ITALIAN).contains(it) || id.substringAfter('.').contains(it) })
                    }.sortedByDescending { (_, name) -> if (name.equals(target, true)) 100 else name.length }
                if (matches.isEmpty()) throw IllegalStateException("Non trovo un dispositivo corrispondente. Controlla i nomi in Home Assistant.")
                val selected = if (all) matches else listOf(matches.first())
                for ((entity, _) in selected) {
                    val domain = entity.substringBefore('.')
                    haRequest("/api/services/$domain/${if (turnOff) "turn_off" else "turn_on"}", "POST", JSONObject().put("entity_id", entity))
                }
                runOnUiThread {
                    status.text = "Smart home: ${selected.size} dispositivo/i aggiornato/i"
                    say(if (turnOff) "Disattivato" else "Attivato")
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Comando smart home non riuscito"; say(e.message ?: "Errore smart home") }
            }
        }
    }

    private fun createCalendarEvent(raw: String) {
        val title = raw.replace(Regex("(?i)\\b(aggiungi|crea|metti|inserisci|evento|appuntamento|calendario|per|domani|oggi)\\b"), " ").replace(Regex("\\s+"), " ").trim().ifBlank { "Nuovo appuntamento" }
        val i = Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
            .putExtra(android.provider.CalendarContract.Events.TITLE, title)
        safeStart(i)
    }

    private fun navigateTo(raw: String) {
        val destination = raw.replace(Regex("(?i)\\b(naviga|portami|indicazioni|verso|a|al|alla|in|fino a)\\b"), " ").trim().ifBlank { "Bracciano" }
        safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(destination)}")))
    }

    private fun openMedia(command: String) {
        val url = if ("musica" in command || "riproduci" in command) "https://music.youtube.com" else "https://www.youtube.com"
        safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun saveNote(note: String) {
        val old = prefs.getString("notes", "").orEmpty()
        prefs.edit().putString("notes", (if (old.isBlank()) "" else "$old\n") + "• $note").apply(); refreshNotes()
    }
    private fun refreshNotes() { if (::notesView.isInitialized) notesView.text = prefs.getString("notes", "Nessuna nota salvata.") }
    private fun promptNote() {
        val edit = EditText(this).apply { hint = "Scrivi la nota…" }
        AlertDialog.Builder(this).setTitle("Nuova nota").setView(edit).setNegativeButton("Annulla", null).setPositiveButton("Salva") { _, _ -> if (edit.text.isNotBlank()) saveNote(edit.text.toString()) }.show()
    }
    private fun launchPackage(pkg: String, label: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i) else { status.text = "$label non installata"; say("Non trovo $label installata.") }
    }
    private fun openClock() { safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
    private fun openWeather(city: String) { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=meteo+${Uri.encode(city)}"))) }
    private fun openWhatsApp() { launchPackage("com.whatsapp", "WhatsApp") }
    private fun composeWhatsApp(raw: String) {
        val msg = raw.substringAfter("whatsapp", "").replace(Regex("(?i)^(\\s*(scrivi|manda|invia|un messaggio)(\\s+a)?\\s*)"), "").trim()
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; setPackage("com.whatsapp"); putExtra(Intent.EXTRA_TEXT, msg.ifBlank { raw }) }
        safeStart(i)
    }
    private fun dial(number: String) { safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.replace(" ", "")}"))) }
    private fun safeStart(i: Intent) { try { startActivity(i) } catch (_: Exception) { Toast.makeText(this, "Azione non disponibile su questo telefono", Toast.LENGTH_LONG).show() } }
    private fun say(s: String) { if (tts?.isSpeaking == true) tts?.stop(); tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "needle-voice") }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale.ITALIAN }
    override fun onDestroy() { stopListening(true); timer?.cancel(); tts?.shutdown(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening() }
}
