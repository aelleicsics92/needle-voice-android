package it.dispensa.needlevoice

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.concurrent.thread

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), content = content)

@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().glass(24.dp).padding(18.dp), content = content)

// ---------------------------------------------------------------- VOCE
@Composable
fun VoiceScreen(a: Assistant, onNeedPerm: (String) -> Unit, askPerm: (String) -> Unit) {
    val name = a.store.userName
    var typed by remember { mutableStateOf("") }
    val contactsOk = a.actions.contacts.hasPermission()
    fun go() = a.listen(onNeedPerm) { }
    fun send(t: String) { if (t.isNotBlank()) { a.typed(t, onNeedPerm) { }; typed = "" } }
    val top = remember { a.store.usage().entries.filter { it.key.startsWith("app:") }.sortedByDescending { it.value }.take(3).map { it.key.removePrefix("app:") } }
    Page {
        Spacer(Modifier.height(22.dp))
        Text(if (name.isBlank()) "Ciao" else "Ciao $name", color = P.Dim, fontSize = 16.sp)
        GradText("Cosa facciamo\noggi?", 42)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { GField(typed, { typed = it }, "Scrivi un comando…", onEnter = { send(typed) }) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(27.dp)).background(P.grad).clickable { if (typed.isBlank()) go() else send(typed) }, Alignment.Center) { NIcon(if (typed.isBlank()) Ic.Mic else Ic.Spark, Color.White, 24.dp) }
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp), Alignment.Center) { Orb(a.level, a.active, 270.dp) { go() } }
        Text(if (a.active && a.heard.isBlank()) "Ti ascolto…" else a.heard.ifBlank { "Tocca la sfera e parla" }, color = P.Txt, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (a.result.isNotBlank() && !a.active) Text(a.result, color = if (a.thinking) P.Pink else P.Dim, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        if (!contactsOk) { Spacer(Modifier.height(16.dp)); Glass(Modifier.clickable { askPerm(android.Manifest.permission.READ_CONTACTS) }) { Text("Consenti i Contatti per chiamare e scrivere per nome", color = P.Peach, fontSize = 14.sp) } }
        if (!NeedleAccess.on) { Spacer(Modifier.height(10.dp)); Glass(Modifier.clickable { askPerm("ACCESS") }) { Text("Attiva l'accessibilità per farmi usare tutte le app", color = P.Peach, fontSize = 14.sp) } }
        SectionLabel("Prova")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (top.map { "Apri $it" } + listOf("Scatta una foto", "Sveglia alle 7 e mezza", "Timer di 10 minuti", "Che tempo fa domani", "Buongiorno", "Metti i Linkin Park su Spotify", "Cerca su Amazon batterie", "Manda una mail a Mario")).distinct().forEach { Chip(it) { send(it) } }
        }
        Spacer(Modifier.height(120.dp))
    }
}

// ---------------------------------------------------------------- NOTE
@Composable
fun NotesScreen(a: Assistant) {
    var notes by remember { mutableStateOf(a.store.notes()) }
    var draft by remember { mutableStateOf("") }
    fun add() { if (draft.isNotBlank()) { a.store.addNote(draft); draft = ""; notes = a.store.notes() } }
    Page {
        Spacer(Modifier.height(26.dp)); GradText("Note", 44)
        Spacer(Modifier.height(14.dp))
        GField(draft, { draft = it }, "Scrivi e premi Invio", onEnter = { add() })
        Spacer(Modifier.height(10.dp)); PrimaryButton("Aggiungi") { add() }
        Spacer(Modifier.height(14.dp))
        if (notes.isEmpty()) Text("Nessuna nota. Prova: «nota comprare il pane».", color = P.Dim, fontSize = 15.sp, modifier = Modifier.padding(8.dp))
        notes.reversed().forEach { (id, text, _) ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).glass(20.dp).padding(start = 18.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = P.Txt, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable { a.store.deleteNote(id); notes = a.store.notes() }, Alignment.Center) { Text("✕", color = P.Red, fontSize = 16.sp) }
            }
        }
        if (notes.isNotEmpty()) { Spacer(Modifier.height(6.dp)); GhostButton("Elimina tutte", danger = true) { a.store.clearNotes(); notes = a.store.notes() } }
        Spacer(Modifier.height(120.dp))
    }
}

// ---------------------------------------------------------------- CASA
@Composable
fun HomeScreen(a: Assistant, goSettings: () -> Unit) {
    var items by remember { mutableStateOf<List<HomeAssistant.Entity>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    val configured = a.store.haUrl.startsWith("http") && a.store.haToken.isNotBlank()
    LaunchedEffect(tick, configured) {
        if (!configured) return@LaunchedEffect
        status = "Carico…"
        thread {
            try { val e = HomeAssistant(a.store).entities().sortedBy { it.name }; items = e; status = "${e.size} dispositivi" }
            catch (e: Exception) { status = (e.message ?: "Errore") + ". Controlla URL e rete." }
        }
    }
    Page {
        Spacer(Modifier.height(26.dp)); GradText("Casa", 44)
        if (!configured) {
            Spacer(Modifier.height(14.dp))
            Glass {
                Text("Collega Home Assistant", color = P.Txt, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("1. Installa Home Assistant (mini PC, Raspberry Pi o Home Assistant Green).\n2. Aggiungi le integrazioni Tapo e Tuya (Smart Life).\n3. Profilo, Sicurezza, crea un token a lunga durata.\n4. Inserisci qui URL e token.", color = P.Dim, fontSize = 14.sp, lineHeight = 21.sp)
            }
            Spacer(Modifier.height(12.dp)); PrimaryButton("Configura") { goSettings() }
        } else {
            Text(status, color = P.Dim, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            items.forEach { d ->
                var on by remember(d.id, d.state) { mutableStateOf(d.state == "on") }
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).glass(20.dp, if (on) 0.11f else 0.05f).padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(d.name, color = P.Txt, fontSize = 16.sp, fontWeight = FontWeight.Medium); Text(if (d.id.startsWith("light")) "Luce" else "Presa", color = P.Dim, fontSize = 12.sp) }
                    Switch(on, { v -> on = v; thread { try { HomeAssistant(a.store).toggle(d.copy(state = if (v) "off" else "on")) } catch (_: Exception) { on = !v } } },
                        colors = SwitchDefaults.colors(checkedTrackColor = P.Violet, checkedThumbColor = Color.White, uncheckedTrackColor = Color.White.copy(alpha = 0.12f), uncheckedThumbColor = P.Dim, uncheckedBorderColor = Color.Transparent))
                }
            }
            Spacer(Modifier.height(6.dp)); GhostButton("Aggiorna") { tick++ }
        }
        SectionLabel("App")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Apri Tapo") { a.actions.run(Cmd.OpenApp("tapo")) { } }
            Chip("Apri Smart Life") { a.actions.run(Cmd.OpenApp("smart life")) { } }
            Chip("Apri Alexa") { a.actions.run(Cmd.OpenApp("alexa")) { } }
        }
        Spacer(Modifier.height(120.dp))
    }
}

// ---------------------------------------------------------------- CERVELLO
@Composable
fun BrainScreen(a: Assistant, initial: String, goSettings: () -> Unit) {
    val ctx = LocalContext.current
    val llm = remember { Llm(a.store) }
    var q by remember { mutableStateOf(initial) }
    val uris = remember { mutableStateListOf<Uri>() }
    var progress by remember { mutableStateOf(listOf<String>()) }
    var council by remember { mutableStateOf<Brain.Council?>(null) }
    var loading by remember { mutableStateOf(false) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val active = llm.active()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { l -> l.forEach { u -> Media.copyToCache(ctx, u)?.let { uris.add(it) } } }
    val shot = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> r.data?.data?.let { uris.add(it) } }

    fun ask() {
        if (loading || active.isEmpty() || (q.isBlank() && uris.isEmpty())) return
        loading = true; progress = emptyList(); council = null
        val bmps = uris.mapNotNull { Media.decode(ctx, it, 1600) }
        a.askCouncil(q.ifBlank { "Descrivi cosa vedi in queste immagini e dimmi cosa è importante." }, bmps, { p -> progress = progress + p }) { c -> council = c; loading = false }
    }
    LaunchedEffect(initial) { if (initial.isNotBlank() && active.isNotEmpty()) ask() }

    Page {
        Spacer(Modifier.height(26.dp)); GradText("Cervello", 44)
        Text("Più IA ragionano insieme, io ti leggo il succo.", color = P.Dim, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        if (active.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Glass {
                Text("Collega i modelli (gratis)", color = P.Txt, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Serve una chiave per ciascuno: si crea in un minuto, senza carta. Ti consiglio Groq e Gemini.", color = P.Dim, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Llm.SIGNUP.forEach { (n, u) -> Chip("Ottieni $n") { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(u)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                }
            }
            Spacer(Modifier.height(10.dp)); PrimaryButton("Incolla la chiave in Opzioni") { goSettings() }
        } else {
            Text(active.joinToString(" · ") { "$it ✓" }, color = P.Mint, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(10.dp))
            GField(q, { q = it }, "Chiedi qualsiasi cosa", onEnter = { ask() })
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Inquadra") { shot.launch(android.content.Intent(ctx, CaptureActivity::class.java).putExtra("gallery", false)) }
                Chip("Galleria") { pick.launch("image/*") }
                Chip("Google Lens") { AiApps.lens(ctx) }
            }
            if (uris.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uris.toList().forEach { u ->
                        val bmp = remember(u) { Media.decode(ctx, u, 300)?.asImageBitmap() }
                        Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).clickable { uris.remove(u) }) {
                            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Text("✕", color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(if (loading) "Sto ragionando con ${active.size} provider…" else "Chiedi a tutti") { ask() }
            progress.forEach { Text(it, color = P.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 6.dp)) }
        }
        val saved = remember(council) { council ?: runCatching { org.json.JSONObject(a.store.lastCouncil) }.getOrNull()?.let { j -> Brain.Council(j.optString("q"), j.getJSONArray("answers").let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }.map { Brain.Answer(it.optString("label"), it.optString("text"), it.optBoolean("ok")) } }, j.optString("syn")) } }
        if (saved != null && saved.synthesis.isNotBlank()) {
            SectionLabel("Il succo")
            Glass { Text(saved.synthesis, color = P.Txt, fontSize = 17.sp, lineHeight = 25.sp); Spacer(Modifier.height(10.dp)); Chip("Rileggi ad alta voce") { Speaker.say(ctx, saved.synthesis) } }
            SectionLabel("Le risposte")
            saved.answers.forEach { r -> Glass(Modifier.padding(bottom = 10.dp)) { Text(r.label, color = P.Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(r.text, color = if (r.ok) P.Txt else P.Red, fontSize = 14.sp, lineHeight = 21.sp) } }
        }
        Spacer(Modifier.height(120.dp))
    }
}

// ---------------------------------------------------------------- OPZIONI
@Composable
fun SettingsScreen(a: Assistant, openAppSettings: () -> Unit, openAccess: () -> Unit) {
    val s = a.store; val ctx = LocalContext.current; val llm = remember { Llm(s) }
    var name by remember { mutableStateOf(s.userName) }; var url by remember { mutableStateOf(s.haUrl) }; var tok by remember { mutableStateOf(s.haToken) }
    var city by remember { mutableStateOf(s.city) }; var paste by remember { mutableStateOf("") }
    var speak by remember { mutableStateOf(s.speakReplies) }; var msg by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    fun save() { s.userName = name; s.haUrl = url; s.haToken = tok; s.city = city; s.speakReplies = speak; msg = "Salvato ✓" }
    fun addKey(k: String) { val key = k.trim(); val p = Llm.detect(key); if (p == null) msg = "Chiave non riconosciuta" else { llm.setKey(p, key); paste = ""; msg = "$p collegato ✓"; tick++ } }
    Page {
        Spacer(Modifier.height(26.dp)); GradText("Opzioni", 44)
        SectionLabel("Tu"); GField(name, { name = it }, "Il tuo nome"); Spacer(Modifier.height(8.dp)); GField(city, { city = it }, "Città per il meteo", onEnter = { save() })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().glass(20.dp).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Risposta vocale", color = P.Txt, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(speak, { speak = it }, colors = SwitchDefaults.colors(checkedTrackColor = P.Violet, checkedThumbColor = Color.White, uncheckedTrackColor = Color.White.copy(alpha = 0.12f), uncheckedThumbColor = P.Dim, uncheckedBorderColor = Color.Transparent))
        }
        SectionLabel("Cervello (modelli gratuiti)")
        key(tick) { Text(if (llm.active().isEmpty()) "Nessun modello collegato" else llm.active().joinToString(" · ") { "$it ✓" }, color = if (llm.active().isEmpty()) P.Peach else P.Mint, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp)) }
        GField(paste, { paste = it; if (Llm.detect(it.trim()) != null && it.trim().length > 20) addKey(it) }, "Incolla qui una chiave: riconosco il provider da sola", secret = true, onEnter = { addKey(paste) })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Llm.SIGNUP.forEach { (n, u) -> Chip("Ottieni $n") { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(u)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        Text("Con più chiavi attive confronto più modelli (Llama, GPT-OSS, DeepSeek, Gemini, Mistral…) e ti dico il succo. La voce naturale usa Gemini se c'è la chiave.", color = P.Dim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
        SectionLabel("Accessibilità (usare tutte le app)")
        GhostButton(if (NeedleAccess.on) "Attiva ✓ (gestisci)" else "Attiva l'accessibilità") { openAccess() }
        Text("Se Android blocca l'opzione: Impostazioni, App, Needle, menu ⋮, \"Consenti impostazioni con restrizioni\".", color = P.Dim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
        SectionLabel("Home Assistant (facoltativo)")
        GField(url, { url = it }, "URL (es. http://192.168.1.20:8123)"); Spacer(Modifier.height(8.dp)); GField(tok, { tok = it }, "Token a lunga durata", secret = true, onEnter = { save() })
        Spacer(Modifier.height(10.dp))
        GhostButton("Verifica connessione") { save(); thread { val m = try { HomeAssistant(s).ping(); "Home Assistant connesso ✓" } catch (e: Exception) { e.message ?: "Errore" }; msg = m } }
        Text("Senza Home Assistant, luci e prese Tapo/Smart Life le comando aprendo le loro app (serve l'accessibilità).", color = P.Dim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
        SectionLabel("Cosa ho imparato di te")
        key(tick) {
            val facts = s.facts(); val al = s.aliases()
            if (facts.isEmpty() && al.isEmpty()) Text("Ancora niente. Dimmi «ricorda che…» e imparo anche da come usi il telefono.", color = P.Dim, fontSize = 14.sp)
            facts.forEach { f -> Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).glass(16.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(f, color = P.Txt, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp)); Box(Modifier.size(40.dp).clickable { s.removeFact(f); tick++ }, Alignment.Center) { Text("✕", color = P.Red) } } }
            al.forEach { (k, v) -> Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).glass(16.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text("«$k» = $v", color = P.Txt, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp)); Box(Modifier.size(40.dp).clickable { s.removeAlias(k); tick++ }, Alignment.Center) { Text("✕", color = P.Red) } } }
        }
        SectionLabel("Permessi")
        GhostButton("Permessi dell'app") { openAppSettings() }
        Spacer(Modifier.height(18.dp)); PrimaryButton("Salva") { save() }
        if (msg.isNotBlank()) Text(msg, color = P.Mint, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        if (s.lastCrash.isNotBlank()) { SectionLabel("Ultimo errore"); Glass { Text(s.lastCrash.take(900), color = P.Dim, fontSize = 11.sp, lineHeight = 15.sp); Spacer(Modifier.height(8.dp)); Chip("Cancella") { s.lastCrash = ""; tick++ } } }
        Text("Le chiavi restano solo su questo telefono.", color = P.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(120.dp))
    }
}
