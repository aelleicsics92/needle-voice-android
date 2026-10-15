package it.dispensa.needlevoice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var voice: Voice
    private lateinit var actions: Actions
    private lateinit var content: FrameLayout
    private lateinit var tabs: LinearLayout
    private val ui = Handler(Looper.getMainLooper())
    private val images = mutableListOf<Bitmap>()
    private var aiQuestion = ""
    private var aiOut: LinearLayout? = null
    private var imgInfo: TextView? = null
    private var camFile: File? = null
    private val names = listOf("Voce", "Note", "Casa", "AI", "Opzioni")
    private var cur = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this); voice = Voice(this); actions = Actions(this, store)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(C.BG); fitsSystemWindows = true }
        content = FrameLayout(this)
        tabs = LinearLayout(this).apply { background = rounded(C.CARD, 0f, context); setPadding(0, dpi(6), 0, dpi(6)) }
        names.forEachIndexed { i, n -> tabs.addView(text(n, 13f, C.DIM, true).apply { gravity = Gravity.CENTER; setPadding(0, dpi(12), 0, dpi(12)); setOnClickListener { show(i) } }, LinearLayout.LayoutParams(0, -2, 1f)) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(tabs, lp())
        setContentView(root)
        val ask = intent.getStringExtra("ask")
        if (ask != null) { aiQuestion = ask; show(3) } else show(0)
    }

    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); i?.getStringExtra("ask")?.let { aiQuestion = it; show(3) } }

    private fun show(i: Int) {
        cur = i
        for (k in 0 until tabs.childCount) (tabs.getChildAt(k) as TextView).apply { setTextColor(if (k == i) C.LIME else C.DIM) }
        content.removeAllViews()
        val v = when (i) { 0 -> tabVoice(); 1 -> tabNotes(); 2 -> tabHome(); 3 -> tabAi(); else -> tabSettings() }
        content.addView(ScrollView(this).apply { addView(v); isFillViewport = true; clipToPadding = false })
    }

    private fun page(title: String, sub: String = ""): LinearLayout = vbox().apply {
        setPadding(dpi(18), dpi(20), dpi(18), dpi(24))
        addView(text(title, 30f, C.TXT, true)); if (sub.isNotEmpty()) addView(text(sub, 14f, C.DIM).apply { setPadding(0, dpi(4), 0, dpi(8)) })
    }

    // ---------------- VOCE
    private fun tabVoice(): View {
        val p = page("Needle", "Tocca la sfera e parla")
        val orb = OrbView(this); val heard = text("", 18f, C.TXT, true).apply { gravity = Gravity.CENTER }
        val res = text("", 15f, C.DIM).apply { gravity = Gravity.CENTER; setPadding(0, dpi(4), 0, dpi(12)) }
        fun go() {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS), 7); return }
            orb.active = true; heard.text = ""; res.text = "In ascolto…"
            voice.listen({ heard.text = "«$it»" }, { t ->
                orb.active = false; heard.text = "«$t»"; val c = Parser.parse(t)
                if (c is Cmd.AskAi) { aiQuestion = c.question; show(3) } else actions.run(c) { m -> res.text = m; if (store.speakReplies) voice.say(m) }
            }, { orb.active = false; res.text = it }, { orb.pushLevel(it) })
        }
        orb.setOnClickListener { go() }
        p.addView(orb, lp(-1, dpi(260))); p.addView(heard, lp()); p.addView(res, lp())
        p.addView(label("Prova a dire"))
        val c = card()
        listOf("Accendi la luce della cucina", "Spegni tutte le luci", "Timer di 10 minuti · Annulla il timer", "Nota: comprare il pane · Elimina la nota pane",
            "Manda un messaggio WhatsApp a Ale dicendo arrivo tra 10 minuti", "Chiama Mario · Naviga verso Bracciano", "Che tempo fa a Roma?", "Chiedi a tutti: com'è meglio fare…").forEach { c.addView(text("•  $it", 14f, C.TXT).apply { setPadding(0, dpi(5), 0, dpi(5)) }) }
        p.addView(c)
        return p
    }

    // ---------------- NOTE
    private fun tabNotes(): View {
        val p = page("Note")
        val list = vbox()
        fun refresh() {
            list.removeAllViews()
            val n = store.notes()
            if (n.isEmpty()) list.addView(text("Nessuna nota. Dì «nota comprare il pane».", 14f, C.DIM))
            n.reversed().forEach { (id, t, _) ->
                val c = card().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                c.addView(text(t, 15f), LinearLayout.LayoutParams(0, -2, 1f))
                c.addView(text("✕", 18f, C.RED, true).apply { setPadding(dpi(14), dpi(4), dpi(4), dpi(4)); setOnClickListener { store.deleteNote(id); refresh() } })
                list.addView(c)
            }
        }
        lateinit var f: EditText
        fun add() { if (f.text.isNotBlank()) { store.addNote(f.text.toString()); f.setText(""); refresh() } }
        f = field("Scrivi una nota e premi Invio") { add() }
        p.addView(f); p.addView(button("Aggiungi", true) { add() }); p.addView(list)
        p.addView(button("Elimina tutte") { store.clearNotes(); refresh() })
        refresh(); return p
    }

    // ---------------- CASA
    private fun tabHome(): View {
        val p = page("Casa", "Luci e prese da Home Assistant")
        val list = vbox(); val st = text("", 14f, C.DIM)
        fun load() {
            st.text = "Carico dispositivi…"; list.removeAllViews()
            thread {
                try {
                    val e = HomeAssistant(store).entities()
                    ui.post {
                        st.text = "${e.size} dispositivi"
                        e.sortedBy { it.name }.forEach { d ->
                            val c = card().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                            c.addView(text(d.name, 15f), LinearLayout.LayoutParams(0, -2, 1f))
                            val sw = Switch(this).apply { isChecked = d.state == "on" }
                            sw.setOnClickListener { thread { try { HomeAssistant(store).toggle(d.copy(state = if (sw.isChecked) "off" else "on")) } catch (_: Exception) { ui.post { sw.isChecked = !sw.isChecked } } } }
                            c.addView(sw); list.addView(c)
                        }
                    }
                } catch (e: Exception) { ui.post { st.text = (e.message ?: "Errore") + " · vai in Opzioni" } }
            }
        }
        p.addView(st); p.addView(list)
        p.addView(button("Aggiorna", true) { load() })
        p.addView(button("Apri Tapo") { actions.run(Cmd.OpenApp("tapo")) { toast(it) } })
        p.addView(button("Apri Smart Life") { actions.run(Cmd.OpenApp("smart life")) { toast(it) } })
        load(); return p
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------------- AI (tutti gli agenti in parallelo)
    private fun tabAi(): View {
        val p = page("Chiedi agli AI", "ChatGPT, Claude e Gemini rispondono insieme")
        val hub = AiHub(store)
        if (hub.enabledAgents().isEmpty()) p.addView(text("Nessun agente attivo: inserisci almeno una chiave API in Opzioni.", 14f, C.RED))
        else p.addView(text("Attivi: " + hub.enabledAgents().joinToString(", "), 13f, C.LIME))
        lateinit var q: EditText
        val out = vbox().also { aiOut = it }
        fun ask() {
            val question = q.text.toString().trim()
            if (question.isEmpty() && images.isEmpty()) return
            out.removeAllViews()
            val boxes = hub.enabledAgents().associateWith { n -> card().also { c -> c.addView(text(n, 12f, C.LIME, true)); c.addView(text("Sto pensando…", 14f, C.DIM)); out.addView(c) } }
            val full = question.ifBlank { "Descrivi e confronta queste immagini." }
            hub.askAll(full, images.toList()) { a -> ui.post { boxes[a.agent]?.let { c -> c.removeViewAt(1); c.addView(text(a.text, 14f, if (a.ok) C.TXT else C.RED).apply { setTextIsSelectable(true) }) } } }
        }
        q = field("La tua domanda (Invio per inviare)", aiQuestion) { ask() }
        val info = text(imgText(), 13f, C.DIM).also { imgInfo = it }
        p.addView(q); p.addView(info, lp())
        p.addView(button("Chiedi a tutti", true) { ask() })
        p.addView(button("📷  Scatta foto") { takePhoto() })
        p.addView(button("🖼  Aggiungi immagini (anche più di una)") { pickImages() })
        p.addView(button("Rimuovi immagini") { images.clear(); info.text = imgText() })
        p.addView(label("Risposte")); p.addView(out)
        if (aiQuestion.isNotBlank() && hub.enabledAgents().isNotEmpty()) { ui.post { ask() }; aiQuestion = "" }
        return p
    }
    private fun imgText() = if (images.isEmpty()) "Nessuna immagine allegata" else "${images.size} immagini allegate"

    private fun pickImages() = startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 11)
    private fun takePhoto() {
        val f = File(File(cacheDir, "photos").apply { mkdirs() }, "p${System.currentTimeMillis()}.jpg"); camFile = f
        val u = FileProvider.getUriForFile(this, "$packageName.files", f)
        try { startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, u).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION), 12) } catch (_: Exception) { toast("Fotocamera non disponibile") }
    }
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        if (rs != RESULT_OK) return
        if (rq == 11 && d != null) {
            val uris = mutableListOf<Uri>(); d.data?.let { uris.add(it) }
            d.clipData?.let { cd -> for (i in 0 until cd.itemCount) uris.add(cd.getItemAt(i).uri) }
            uris.forEach { u -> decode { contentResolver.openInputStream(u) }?.let { images.add(it) } }
        }
        if (rq == 12) camFile?.let { f -> decode { f.inputStream() }?.let { images.add(it) } }
        imgInfo?.text = imgText()
    }
    private fun decode(open: () -> java.io.InputStream?): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; open()?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (o.outWidth / s > 2000 || o.outHeight / s > 2000) s *= 2
        open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    } catch (_: Exception) { null }

    // ---------------- OPZIONI
    private fun tabSettings(): View {
        val p = page("Opzioni")
        val url = field("URL Home Assistant (es. http://192.168.1.20:8123)", store.haUrl)
        val tok = field("Token Home Assistant (lunga durata)", store.haToken, true)
        val city = field("Città per il meteo", store.city)
        val ok = field("Chiave OpenAI (ChatGPT)", store.openaiKey, true)
        val an = field("Chiave Anthropic (Claude)", store.anthropicKey, true)
        val ge = field("Chiave Google (Gemini)", store.geminiKey, true)
        val speak = CheckBox(this).apply { text = "Risposta vocale"; setTextColor(C.TXT); isChecked = store.speakReplies }
        fun save() { store.haUrl = url.text.toString(); store.haToken = tok.text.toString(); store.city = city.text.toString()
            store.openaiKey = ok.text.toString(); store.anthropicKey = an.text.toString(); store.geminiKey = ge.text.toString(); store.speakReplies = speak.isChecked; toast("Salvato") }
        ge.setOnEditorActionListener { _, _, _ -> save(); true }
        p.addView(label("Home Assistant")); p.addView(url); p.addView(tok)
        p.addView(button("Verifica connessione") { save(); thread { val m = try { HomeAssistant(store).ping(); "Home Assistant connesso ✓" } catch (e: Exception) { e.message ?: "Errore" }; ui.post { toast(m) } } })
        p.addView(label("Generale")); p.addView(city); p.addView(speak)
        p.addView(label("Agenti AI (chiavi API)")); p.addView(ok); p.addView(an); p.addView(ge)
        p.addView(button("Salva", true) { save() })
        p.addView(text("Le chiavi restano solo su questo telefono.", 12f, C.DIM))
        return p
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(c, p, g); if (cur == 0) show(0) }
    override fun onDestroy() { voice.release(); super.onDestroy() }
}
