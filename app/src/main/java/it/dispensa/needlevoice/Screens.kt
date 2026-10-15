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
fun VoiceScreen(a: Assistant, contactsOk: Boolean, onAsk: (String) -> Unit, onNeedContacts: () -> Unit, askContacts: () -> Unit) {
    val ctx = LocalContext.current
    val name = a.store.userName
    fun go() = a.listen(onAsk, onNeedContacts) { }
    fun run(t: String) = a.handle(listOf(t), onAsk, onNeedContacts) { }
    Page {
        Spacer(Modifier.height(26.dp))
        Text(if (name.isBlank()) "Ciao" else "Ciao $name", color = P.Dim, fontSize = 16.sp)
        GradText("Cosa facciamo\noggi?", 44)
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), Alignment.Center) { Orb(a.level, a.active, 280.dp) { go() } }
        Text(if (a.active && a.heard.isBlank()) "Ti ascolto…" else a.heard.ifBlank { "Tocca la sfera e parla" }, color = P.Txt, fontSize = 20.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (a.result.isNotBlank() && !a.active) Text(a.result, color = P.Dim, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        if (!contactsOk) { Spacer(Modifier.height(16.dp)); Glass(Modifier.clickable { askContacts() }) { Text("Consenti l'accesso ai Contatti per chiamare e scrivere per nome", color = P.Peach, fontSize = 14.sp) } }
        SectionLabel("Prova")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Timer di 10 minuti", "Accendi la luce della cucina", "Metti i Linkin Park su Spotify", "Cerca su Amazon batterie", "Che tempo fa a Roma", "Chiama Mario").forEach { Chip(it) { run(it) } }
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

// ---------------------------------------------------------------- AI
@Composable
fun AiScreen(a: Assistant, initial: String) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf(initial) }
    val uris = remember { mutableStateListOf<Uri>() }
    val answers = remember { mutableStateListOf<AiHub.Answer>() }
    var loading by remember { mutableStateOf(false) }
    val hub = remember { AiHub(a.store) }
    val apps = remember { AiApps.installed(ctx) }
    var info by remember { mutableStateOf("") }
    var camUri by remember { mutableStateOf<Uri?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { l -> l.forEach { u -> Media.copyToCache(ctx, u)?.let { uris.add(it) } } }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let { uris.add(it) } }

    fun compare() {
        if (hub.enabledAgents().isEmpty() || (q.isBlank() && uris.isEmpty())) return
        answers.clear(); loading = true
        val bmps = uris.mapNotNull { Media.decode(ctx, it, 1600) }
        hub.askAll(q.ifBlank { "Descrivi e confronta queste immagini." }, bmps) { r -> android.os.Handler(android.os.Looper.getMainLooper()).post { answers.add(r); if (answers.size >= hub.enabledAgents().size) loading = false } }
    }

    Page {
        Spacer(Modifier.height(26.dp)); GradText("Chiedi\nagli AI", 44)
        Spacer(Modifier.height(14.dp))
        GField(q, { q = it }, "Scrivi la domanda", onEnter = { compare() })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Scatta foto") { val f = Media.newFile(ctx); val u = Media.uriFor(ctx, f); camUri = u; cam.launch(u) }
            Chip("Galleria") { pick.launch("image/*") }
            Chip("Google Lens") { if (!AiApps.lens(ctx)) info = "Google Lens non disponibile" }
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
            Text("Tocca una foto per toglierla", color = P.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        SectionLabel("Invia alle tue app AI")
        if (apps.isEmpty()) Text("Nessuna app AI trovata sul telefono.", color = P.Dim, fontSize = 14.sp)
        else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.forEach { ai -> Chip(ai.name) { if (!AiApps.share(ctx, ai, q, uris.toList())) info = "${ai.name} non accetta questo contenuto" } }
        }
        Text("Si apre la tua app con domanda e foto già dentro: usa il tuo account, nessuna chiave.", color = P.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        if (info.isNotBlank()) Text(info, color = P.Peach, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        SectionLabel("Confronto automatico")
        if (hub.enabledAgents().isEmpty()) Text("Per vedere le risposte di più AI qui dentro serve una chiave API. Quella di Gemini è gratuita (aistudio.google.com): inseriscila in Opzioni.", color = P.Dim, fontSize = 14.sp, lineHeight = 20.sp)
        else {
            PrimaryButton(if (loading) "Sto chiedendo a ${hub.enabledAgents().joinToString(", ")}…" else "Chiedi a ${hub.enabledAgents().joinToString(", ")}") { compare() }
            Spacer(Modifier.height(12.dp))
            answers.forEach { r -> Glass(Modifier.padding(bottom = 10.dp)) { Text(r.agent, color = P.Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(r.text, color = if (r.ok) P.Txt else P.Red, fontSize = 15.sp, lineHeight = 22.sp) } }
        }
        Spacer(Modifier.height(120.dp))
    }
}

// ---------------------------------------------------------------- OPZIONI
@Composable
fun SettingsScreen(a: Assistant, contactsOk: Boolean, openAppSettings: () -> Unit) {
    val s = a.store; val ctx = LocalContext.current
    var name by remember { mutableStateOf(s.userName) }; var url by remember { mutableStateOf(s.haUrl) }; var tok by remember { mutableStateOf(s.haToken) }
    var city by remember { mutableStateOf(s.city) }; var gem by remember { mutableStateOf(s.geminiKey) }; var oai by remember { mutableStateOf(s.openaiKey) }; var ant by remember { mutableStateOf(s.anthropicKey) }
    var speak by remember { mutableStateOf(s.speakReplies) }; var msg by remember { mutableStateOf("") }
    fun save() { s.userName = name; s.haUrl = url; s.haToken = tok; s.city = city; s.geminiKey = gem; s.openaiKey = oai; s.anthropicKey = ant; s.speakReplies = speak; msg = "Salvato ✓" }
    Page {
        Spacer(Modifier.height(26.dp)); GradText("Opzioni", 44)
        SectionLabel("Tu"); GField(name, { name = it }, "Il tuo nome"); Spacer(Modifier.height(8.dp)); GField(city, { city = it }, "Città per il meteo", onEnter = { save() })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().glass(20.dp).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Risposta vocale", color = P.Txt, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(speak, { speak = it }, colors = SwitchDefaults.colors(checkedTrackColor = P.Violet, checkedThumbColor = Color.White, uncheckedTrackColor = Color.White.copy(alpha = 0.12f), uncheckedThumbColor = P.Dim, uncheckedBorderColor = Color.Transparent))
        }
        SectionLabel("Home Assistant")
        GField(url, { url = it }, "URL (es. http://192.168.1.20:8123)"); Spacer(Modifier.height(8.dp)); GField(tok, { tok = it }, "Token a lunga durata", secret = true, onEnter = { save() })
        Spacer(Modifier.height(10.dp))
        GhostButton("Verifica connessione") { save(); thread { val m = try { HomeAssistant(s).ping(); "Home Assistant connesso ✓" } catch (e: Exception) { e.message ?: "Errore" }; msg = m } }
        SectionLabel("Chiavi AI (facoltative)")
        GField(gem, { gem = it }, "Gemini (gratuita)", secret = true); Spacer(Modifier.height(8.dp)); GField(oai, { oai = it }, "OpenAI", secret = true); Spacer(Modifier.height(8.dp)); GField(ant, { ant = it }, "Anthropic", secret = true, onEnter = { save() })
        SectionLabel("Permessi")
        GhostButton(if (contactsOk) "Contatti: consentiti ✓" else "Consenti Contatti") { if (!contactsOk) openAppSettings() }
        Spacer(Modifier.height(8.dp)); GhostButton("Tutti i permessi dell'app") { openAppSettings() }
        Spacer(Modifier.height(18.dp)); PrimaryButton("Salva") { save() }
        if (msg.isNotBlank()) Text(msg, color = P.Mint, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        Text("Le chiavi restano solo su questo telefono.", color = P.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(120.dp))
    }
}
