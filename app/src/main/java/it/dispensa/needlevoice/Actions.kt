package it.dispensa.needlevoice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.app.SearchManager
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

/** Esegue un comando. `done` viene sempre chiamato sul thread UI con il messaggio di esito. */
class Actions(private val ctx: Context, private val store: Store) {
    private val ui = Handler(Looper.getMainLooper())
    val apps by lazy { Apps(ctx) }
    val contacts by lazy { Contacts(ctx) }

    fun needsContacts(c: Cmd) = (c is Cmd.WhatsApp && c.to.isNotBlank()) || (c is Cmd.Sms && c.to.isNotBlank()) || (c is Cmd.Call && c.to.isNotBlank())

    fun run(cmd: Cmd, done: (String) -> Unit) {
        when (cmd) {
            is Cmd.Home -> bg(done) { HomeAssistant(store).control(cmd.on, cmd.target, cmd.all) }
            is Cmd.SetTimer -> { Timers.set(ctx, store, cmd.ms); done("Timer di ${fmt(cmd.ms)} avviato") }
            Cmd.CancelTimers -> { val n = Timers.cancelAll(ctx, store); done(if (n > 0) "Timer annullato" else "Nessun timer attivo") }
            Cmd.ListTimers -> { val now = System.currentTimeMillis(); val l = store.timers().filter { it.second > now }
                done(if (l.isEmpty()) "Nessun timer attivo" else l.joinToString(", ") { "mancano ${fmt(it.second - now)}" }) }
            is Cmd.AddNote -> { store.addNote(cmd.text); done("Nota salvata") }
            is Cmd.DeleteNote -> { val n = if (cmd.query.isBlank()) (store.notes().lastOrNull()?.also { store.deleteNote(it.first) }?.let { 1 } ?: 0) else store.deleteNotesMatching(cmd.query)
                done(if (n == 0) "Nessuna nota trovata" else if (n == 1) "Nota eliminata" else "Eliminate $n note") }
            Cmd.DeleteAllNotes -> { store.clearNotes(); done("Tutte le note eliminate") }
            is Cmd.SearchNotes -> { val r = store.searchNotes(cmd.query); done(if (r.isEmpty()) "Nessuna nota trovata" else r.joinToString(". ") { it.second }) }
            Cmd.ReadNotes -> { val r = store.notes(); done(if (r.isEmpty()) "Non hai note" else r.joinToString(". ") { it.second }) }
            is Cmd.WhatsApp -> bg(done) { message(cmd.to, cmd.text, true) }
            is Cmd.Sms -> bg(done) { message(cmd.to, cmd.text, false) }
            is Cmd.Call -> bg(done) {
                val r = contacts.resolve(cmd.to)
                when {
                    r.noPermission -> "Serve il permesso Contatti"
                    r.person == null -> if (cmd.to.isBlank()) { start(Intent(Intent.ACTION_DIAL)); "Apro il telefono" } else notFound(cmd.to, r.suggestions)
                    else -> { start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(r.person.number)}"))); "Chiamo ${r.person.name.takeIf { it != r.person.number } ?: "il numero"}: premi il tasto verde" }
                }
            }
            is Cmd.Weather -> { val c = cmd.city.ifBlank { store.city }; start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=meteo+${Uri.encode(c)}"))); done("Meteo a $c") }
            is Cmd.Navigate -> { start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(cmd.dest)}"))); done("Navigo verso ${cmd.dest}") }
            is Cmd.Calendar -> { start(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, cmd.title.ifBlank { "Nuovo evento" })); done("Conferma l'evento nel calendario") }
            is Cmd.OpenApp -> done(openApp(cmd.name))
            is Cmd.AppSearch -> done(appSearch(cmd.app, cmd.query))
            is Cmd.Play -> done(play(cmd.app, cmd.query))
            is Cmd.WebSearch -> { webSearch(cmd.query); done("Cerco ${cmd.query}") }
            is Cmd.AskAi -> done("")
            Cmd.Unknown -> done("Non ho capito")
        }
    }

    private fun bg(done: (String) -> Unit, f: () -> String) = thread {
        val r = try { f() } catch (e: Exception) { e.message ?: "Errore" }
        ui.post { done(r) }
    }

    fun start(i: Intent): Boolean = try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    private fun startAny(l: List<Intent>) = l.any { start(it) }

    private fun fmt(ms: Long): String { val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val x = s % 60
        return listOfNotNull(h.takeIf { it > 0 }?.let { "$it ore" }, m.takeIf { it > 0 }?.let { "$it minuti" }, x.takeIf { it > 0 }?.let { "$it secondi" }).joinToString(" ") }

    private fun notFound(name: String, sug: List<String>) = "Non trovo \"$name\" in rubrica" + if (sug.isNotEmpty()) ". Forse: ${sug.joinToString(", ")}" else ""

    // ---- Messaggi: destinatario (anche storpiato) e testo separati
    private fun message(to: String, text: String, wa: Boolean): String {
        if (to.isBlank()) {
            if (wa) { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(text)}")).setPackage("com.whatsapp")); return "Scegli il destinatario" }
            start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", text)); return "Scegli il destinatario"
        }
        val r = contacts.resolve(to)
        if (r.noPermission) return "Serve il permesso Contatti"
        val p = r.person ?: return notFound(to, r.suggestions)
        val who = p.name.takeIf { it != p.number } ?: p.number
        return if (wa) {
            val ok = start(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${contacts.waNumber(p.number)}?text=${Uri.encode(text)}")).setPackage("com.whatsapp"))
            if (ok) "WhatsApp a $who pronto: premi invia" else "WhatsApp non installato"
        } else { start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(p.number)}")).putExtra("sms_body", text)); "SMS a $who pronto: premi invia" }
    }

    // ---- App
    fun openApp(name: String): String {
        if (Fuzzy.hasWord(name, "fotocamera") && apps.find(name) == null) { start(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)); return "Fotocamera" }
        val a = apps.find(name) ?: return "Non trovo l'app \"$name\""
        val i = apps.launchIntent(a.pkg) ?: return "Non riesco ad aprire ${a.label}"
        start(i); return "Apro ${a.label}"
    }

    private fun appSearch(appTail: String, queryIn: String): String {
        var app: Apps.App? = null; var q = queryIn
        if (queryIn.isBlank()) { val (a, r) = apps.splitAppQuery(appTail); app = a; q = r } else app = apps.find(appTail)
        if (app == null) { webSearch(if (queryIn.isBlank()) appTail else "$appTail $queryIn"); return "App non trovata, cerco sul web" }
        if (q.isBlank()) return openApp(app.label)
        if (startAny(apps.searchIntents(app, q))) return "Cerco $q su ${app.label}"
        apps.launchIntent(app.pkg)?.let { start(it) }
        return "Ho aperto ${app.label}, ma non supporta la ricerca a voce"
    }

    private fun play(appName: String, query: String): String {
        val app = if (appName.isBlank()) listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.google.android.youtube").firstOrNull { apps.installed(it) }?.let { p -> Apps.App(apps.all.firstOrNull { it.pkg == p }?.label ?: p, p) }
            else apps.find(appName)
        if (app == null) { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))); return "Cerco $query su YouTube" }
        if (startAny(apps.playIntent(app, query))) return "Riproduco $query su ${app.label}"
        apps.launchIntent(app.pkg)?.let { start(it) }
        return "Ho aperto ${app.label}"
    }

    private fun webSearch(q: String) {
        if (!start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q))) start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}")))
    }
}
