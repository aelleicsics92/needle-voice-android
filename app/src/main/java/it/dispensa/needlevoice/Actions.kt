package it.dispensa.needlevoice

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Esegue un comando. `done` viene sempre chiamato sul thread UI con il messaggio di esito. */
class Actions(private val ctx: Context, private val store: Store) {
    private val ui = Handler(Looper.getMainLooper())
    val apps by lazy { Apps(ctx) }
    val contacts by lazy { Contacts(ctx, store) }
    val phone by lazy { Phone(ctx) }

    /** Permesso che manca per eseguire il comando (null = nessuno). */
    fun permFor(c: Cmd): String? {
        val p = when {
            (c is Cmd.WhatsApp && c.to.isNotBlank()) || (c is Cmd.Sms && c.to.isNotBlank()) || (c is Cmd.Call && c.to.isNotBlank()) || (c is Cmd.Email && c.to.isNotBlank()) -> Manifest.permission.READ_CONTACTS
            c is Cmd.Agenda || c == Cmd.Briefing -> Manifest.permission.READ_CALENDAR
            c is Cmd.Parking && c.save -> Manifest.permission.ACCESS_FINE_LOCATION
            else -> return null
        }
        return if (ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED) null else p
    }

    fun run(cmd: Cmd, done: (String) -> Unit) {
        when (cmd) {
            is Cmd.Home -> bg(done) { homeControl(cmd) }
            is Cmd.SetTimer -> { try { Timers.set(ctx, store, cmd.ms); done("Timer di ${fmt(cmd.ms)} avviato") } catch (e: Exception) { done("Timer non riuscito: ${e.message}") } }
            Cmd.CancelTimers -> { val n = Timers.cancelAll(ctx, store); done(if (n > 0) "Timer annullato" else "Nessun timer attivo") }
            Cmd.ListTimers -> { val now = System.currentTimeMillis(); val l = store.timers().filter { it.second > now }
                done(if (l.isEmpty()) "Nessun timer attivo" else l.joinToString(", ") { "mancano ${fmt(it.second - now)}" }) }
            is Cmd.Alarm -> { val ok = start(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, cmd.hour).putExtra(AlarmClock.EXTRA_MINUTES, cmd.min).putExtra(AlarmClock.EXTRA_MESSAGE, cmd.label.ifBlank { "Needle" }).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                done(if (ok) "Sveglia impostata alle ${cmd.hour}:${"%02d".format(cmd.min)}" else "Non riesco a impostare la sveglia") }
            Cmd.CancelAlarms -> { val ok = start(Intent(AlarmClock.ACTION_DISMISS_ALARM).putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_NEXT)) || start(Intent(AlarmClock.ACTION_SHOW_ALARMS)); done(if (ok) "Sveglia annullata, controlla nell'orologio" else "Non riesco") }
            is Cmd.Remind -> { try { Timers.set(ctx, store, (cmd.atMs - System.currentTimeMillis()).coerceAtLeast(1000), cmd.text); done("Va bene, ti ricordo: ${cmd.text}") } catch (e: Exception) { done("Promemoria non riuscito") } }
            is Cmd.AddNote -> { store.addNote(cmd.text); done("Nota salvata") }
            is Cmd.DeleteNote -> { val n = if (cmd.query.isBlank()) (store.notes().lastOrNull()?.also { store.deleteNote(it.first) }?.let { 1 } ?: 0) else store.deleteNotesMatching(cmd.query)
                done(if (n == 0) "Nessuna nota trovata" else if (n == 1) "Nota eliminata" else "Eliminate $n note") }
            Cmd.DeleteAllNotes -> { store.clearNotes(); done("Tutte le note eliminate") }
            is Cmd.SearchNotes -> { val r = store.searchNotes(cmd.query); done(if (r.isEmpty()) "Nessuna nota trovata" else r.joinToString(". ") { it.second }) }
            Cmd.ReadNotes -> { val r = store.notes(); done(if (r.isEmpty()) "Non hai note" else r.joinToString(". ") { it.second }) }
            is Cmd.Remember -> { store.addFact(cmd.fact); done("Ok, me lo ricordo: ${cmd.fact}") }
            is Cmd.WhatsApp -> bg(done) { message(cmd.to, cmd.text, true) }
            is Cmd.Sms -> bg(done) { message(cmd.to, cmd.text, false) }
            is Cmd.Email -> bg(done) { email(cmd) }
            is Cmd.Call -> bg(done) {
                val r = contacts.resolve(cmd.to)
                when {
                    r.noPermission -> "Serve il permesso Contatti"
                    r.person == null -> if (cmd.to.isBlank()) { start(Intent(Intent.ACTION_DIAL)); "Apro il telefono" } else notFound(cmd.to, r.suggestions)
                    else -> { learn(cmd.to, r.person); start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(r.person.number)}"))); "Chiamo ${r.person.name.takeIf { it != r.person.number } ?: "il numero"}: premi il tasto verde" }
                }
            }
            is Cmd.Weather -> bg(done) { phone.weather(cmd.city.ifBlank { store.city }, cmd.day) }
            is Cmd.Agenda -> bg(done) { phone.agenda(cmd.day) }
            Cmd.Briefing -> bg(done) { briefing() }
            is Cmd.Parking -> if (cmd.save) bg(done) { phone.saveParking() } else {
                val p = store.parking; if (p.isBlank()) done("Non mi hai ancora detto dove hai parcheggiato") else { start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$p&mode=w"))); done("Ti porto alla macchina") } }
            is Cmd.Navigate -> { start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(cmd.dest)}"))); done("Navigo verso ${cmd.dest}") }
            is Cmd.Calendar -> { start(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, cmd.title.ifBlank { "Nuovo evento" })); done("Conferma l'evento nel calendario") }
            is Cmd.Camera -> { start(Intent(ctx, CaptureActivity::class.java).putExtra("front", cmd.front).putExtra("gallery", true)); done(if (cmd.front) "Selfie" else "Scatto") }
            is Cmd.Torch -> done(try { phone.torch(cmd.on) } catch (e: Exception) { "Torcia non disponibile" })
            is Cmd.Volume -> done(phone.volume(cmd.action, cmd.level))
            is Cmd.Panel -> { start(Intent(if (cmd.which == "bluetooth") Settings.ACTION_BLUETOOTH_SETTINGS else Settings.ACTION_WIFI_SETTINGS)); done("Ecco le impostazioni ${cmd.which}") }
            is Cmd.Tv -> bg(done) { Tv.launch(ctx, cmd.app) }
            is Cmd.OpenApp -> done(openApp(cmd.name))
            is Cmd.AppSearch -> done(appSearch(cmd.app, cmd.query))
            is Cmd.Play -> done(play(cmd.app, cmd.query))
            is Cmd.WebSearch -> { webSearch(cmd.query); done("Cerco ${cmd.query}") }
            is Cmd.AskAi, is Cmd.Agent -> done("")
            Cmd.Unknown -> done("Non ho capito")
        }
    }

    /** Versione bloccante (per l'agente, da thread di fondo). */
    fun runSync(cmd: Cmd): String {
        var out = ""; val l = CountDownLatch(1)
        ui.post { try { run(cmd) { out = it; l.countDown() } } catch (e: Exception) { out = e.message ?: "Errore"; l.countDown() } }
        l.await(40, TimeUnit.SECONDS); return out
    }

    private fun bg(done: (String) -> Unit, f: () -> String) = thread {
        val r = try { f() } catch (e: Exception) { e.message ?: "Errore" }
        ui.post { done(r) }
    }

    /** Dall'accessibilità se attiva: così si possono aprire app anche mentre siamo in secondo piano. */
    fun start(i: Intent): Boolean = try { (NeedleAccess.inst ?: ctx).startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    private fun startAny(l: List<Intent>) = l.any { start(it) }

    private fun fmt(ms: Long): String { val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val x = s % 60
        return listOfNotNull(h.takeIf { it > 0 }?.let { "$it ore" }, m.takeIf { it > 0 }?.let { "$it minuti" }, x.takeIf { it > 0 }?.let { "$it secondi" }).joinToString(" ") }

    private fun notFound(name: String, sug: List<String>) = "Non trovo \"$name\" in rubrica" + if (sug.isNotEmpty()) ". Forse: ${sug.joinToString(", ")}" else ""
    private fun learn(spoken: String, p: Contacts.Person) { if (p.name != p.number) Memory.learnAlias(store, spoken, p.name) }

    private fun homeControl(c: Cmd.Home): String {
        if (store.haUrl.startsWith("http") && store.haToken.isNotBlank()) return HomeAssistant(store).control(c.on, c.target, c.all)
        throw IllegalStateException("NOHA")
    }

    private fun briefing(): String {
        val n = store.userName.ifBlank { "" }; val sb = StringBuilder("Buongiorno $n. ")
        try { sb.append(phone.weather(store.city, 0)).append(' ') } catch (_: Exception) { }
        sb.append(phone.agenda(0)).append(". ")
        val notes = store.notes().size; if (notes > 0) sb.append("Hai $notes note salvate.")
        return sb.toString()
    }

    // ---- Messaggi: destinatario (anche storpiato) e testo separati
    private fun message(to: String, text: String, wa: Boolean): String {
        if (to.isBlank()) {
            if (wa) { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(text)}")).setPackage("com.whatsapp")); return "Scegli il destinatario" }
            start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", text)); return "Scegli il destinatario"
        }
        val r = contacts.resolve(to)
        if (r.noPermission) return "Serve il permesso Contatti"
        val p = r.person ?: return notFound(to, r.suggestions)
        learn(to, p)
        val who = p.name.takeIf { it != p.number } ?: p.number
        return if (wa) {
            val ok = start(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${contacts.waNumber(p.number)}?text=${Uri.encode(text)}")).setPackage("com.whatsapp"))
            if (ok) "WhatsApp a $who pronto: premi invia" else "WhatsApp non installato"
        } else { start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(p.number)}")).putExtra("sms_body", text)); "SMS a $who pronto: premi invia" }
    }

    private fun email(c: Cmd.Email): String {
        val addr = if (c.to.isBlank()) "" else (phone.emailOf(c.to) ?: return "Non trovo l'email di ${c.to}")
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(addr, "@")}")).putExtra(Intent.EXTRA_SUBJECT, c.subject).putExtra(Intent.EXTRA_TEXT, c.body)
        return if (start(i)) "Email ${if (addr.isNotBlank()) "a ${c.to} " else ""}pronta: premi invia" else "Non trovo un'app email"
    }

    // ---- App
    fun openApp(name: String): String {
        val a = apps.find(name) ?: return "Non trovo l'app \"$name\""
        val i = apps.launchIntent(a.pkg) ?: return "Non riesco ad aprire ${a.label}"
        store.bump("app:${a.label}"); start(i); return "Apro ${a.label}"
    }

    private fun appSearch(appTail: String, queryIn: String): String {
        var app: Apps.App?; var q = queryIn
        if (queryIn.isBlank()) { val (a, r) = apps.splitAppQuery(appTail); app = a; q = r } else app = apps.find(appTail)
        if (app == null) { webSearch(if (queryIn.isBlank()) appTail else "$appTail $queryIn"); return "App non trovata, cerco sul web" }
        if (q.isBlank()) return openApp(app.label)
        store.bump("app:${app.label}")
        if (startAny(apps.searchIntents(app, q))) return "Cerco $q su ${app.label}"
        apps.launchIntent(app.pkg)?.let { start(it) }
        return "Ho aperto ${app.label}, ma non supporta la ricerca a voce"
    }

    private fun play(appName: String, query: String): String {
        val app = if (appName.isBlank()) listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.google.android.youtube").firstOrNull { apps.installed(it) }?.let { p -> Apps.App(apps.all.firstOrNull { it.pkg == p }?.label ?: p, p) }
            else apps.find(appName)
        if (app == null) { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))); return "Cerco $query su YouTube" }
        store.bump("app:${app.label}")
        if (startAny(apps.playIntent(app, query))) return "Riproduco $query su ${app.label}"
        apps.launchIntent(app.pkg)?.let { start(it) }
        return "Ho aperto ${app.label}"
    }

    private fun webSearch(q: String) {
        if (!start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}")))) start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q))
    }
}
