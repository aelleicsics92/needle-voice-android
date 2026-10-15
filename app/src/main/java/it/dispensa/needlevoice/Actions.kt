package it.dispensa.needlevoice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

/** Esegue un comando. `done` viene sempre chiamato sul thread UI con il messaggio di esito. */
class Actions(private val ctx: Context, private val store: Store) {
    private val ui = Handler(Looper.getMainLooper())
    private val timers = mutableListOf<Pair<Long, String>>() // fine, etichetta

    fun run(cmd: Cmd, done: (String) -> Unit) {
        when (cmd) {
            is Cmd.Home -> bg(done) { HomeAssistant(store).control(cmd.on, cmd.target, cmd.all) }
            is Cmd.SetTimer -> done(setTimer(cmd.ms))
            Cmd.CancelTimers -> done(cancelTimers())
            Cmd.ListTimers -> done(listTimers())
            is Cmd.AddNote -> { store.addNote(cmd.text); done("Nota salvata: ${cmd.text}") }
            is Cmd.DeleteNote -> { val n = if (cmd.query.isBlank()) store.notes().lastOrNull()?.also { store.deleteNote(it.first) }?.let { 1 } ?: 0 else store.deleteNotesMatching(cmd.query)
                done(if (n == 0) "Nessuna nota trovata" else "Eliminate $n note") }
            Cmd.DeleteAllNotes -> { store.clearNotes(); done("Tutte le note eliminate") }
            is Cmd.SearchNotes -> { val r = store.searchNotes(cmd.query); done(if (r.isEmpty()) "Nessuna nota trovata" else r.joinToString(". ") { it.second }) }
            Cmd.ReadNotes -> { val r = store.notes(); done(if (r.isEmpty()) "Non hai note" else r.joinToString(". ") { it.second }) }
            is Cmd.WhatsApp -> whatsapp(cmd, done)
            is Cmd.Sms -> bg(done) { val n = resolve(cmd.to); start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${n ?: ""}")).putExtra("sms_body", cmd.text)); "SMS pronto${cmd.to.takeIf { it.isNotBlank() }?.let { " per $it" } ?: ""}: conferma l'invio" }
            is Cmd.Call -> bg(done) { val n = resolve(cmd.to); start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${n ?: ""}"))); if (n == null && cmd.to.isNotBlank()) "Contatto non trovato, apro il tastierino" else "Chiamata pronta: premi il tasto verde" }
            is Cmd.Weather -> { val c = cmd.city.ifBlank { store.city }; start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=meteo+${Uri.encode(c)}"))); done("Meteo $c") }
            is Cmd.Navigate -> { start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(cmd.dest)}"))); done("Navigazione verso ${cmd.dest}") }
            is Cmd.Calendar -> { start(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, cmd.title.ifBlank { "Nuovo evento" })); done("Evento: conferma nel calendario") }
            is Cmd.Music -> { val u = if (cmd.query.isBlank()) "https://music.youtube.com" else "https://music.youtube.com/search?q=${Uri.encode(cmd.query)}"; start(Intent(Intent.ACTION_VIEW, Uri.parse(u))); done("Musica") }
            is Cmd.OpenApp -> done(openApp(cmd.name))
            is Cmd.AskAi -> done("") // gestito dalla UI
            Cmd.Unknown -> done("Non ho capito")
        }
    }

    private fun bg(done: (String) -> Unit, f: () -> String) = thread {
        val r = try { f() } catch (e: Exception) { e.message ?: "Errore" }
        ui.post { done(r) }
    }

    fun start(i: Intent) { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { } }

    // ---- Timer: usa il Timer di sistema (suona anche ad app chiusa)
    private fun setTimer(ms: Long): String {
        val sec = (ms / 1000).toInt().coerceAtLeast(1)
        start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, sec).putExtra(AlarmClock.EXTRA_SKIP_UI, true).putExtra(AlarmClock.EXTRA_MESSAGE, "Needle"))
        timers.add(System.currentTimeMillis() + ms to "Needle")
        return "Timer di ${fmt(ms)} avviato"
    }
    private fun cancelTimers(): String {
        start(Intent(AlarmClock.ACTION_DISMISS_TIMER))
        timers.clear(); return "Timer annullati"
    }
    private fun listTimers(): String {
        val now = System.currentTimeMillis(); timers.removeAll { it.first < now }
        return if (timers.isEmpty()) "Nessun timer attivo" else timers.joinToString(", ") { "mancano ${fmt(it.first - now)}" }
    }
    private fun fmt(ms: Long): String { val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val x = s % 60
        return listOfNotNull(h.takeIf { it > 0 }?.let { "$it ore" }, m.takeIf { it > 0 }?.let { "$it min" }, x.takeIf { it > 0 }?.let { "$it sec" }).joinToString(" ") }

    // ---- WhatsApp: destinatario e testo separati, conferma di invio dentro WhatsApp
    private fun whatsapp(c: Cmd.WhatsApp, done: (String) -> Unit) = bg(done) {
        val n = resolve(c.to)?.filter { it.isDigit() || it == '+' }?.let { if (it.startsWith("+")) it.drop(1) else if (it.startsWith("00")) it.drop(2) else if (it.length <= 10) "39$it" else it }
        val uri = if (n != null) "https://wa.me/$n?text=${Uri.encode(c.text)}" else "https://wa.me/?text=${Uri.encode(c.text)}"
        start(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage("com.whatsapp"))
        if (n == null) "Contatto \"${c.to}\" non trovato: scegli tu il destinatario" else "WhatsApp a ${c.to} pronto: premi invia"
    }

    /** Nome contatto -> numero (serve permesso Contatti). Se è già un numero lo restituisce. */
    private fun resolve(name: String): String? {
        val t = name.trim(); if (t.isBlank()) return null
        if (Regex("^\\+?[0-9 ()-]{6,}$").matches(t)) return t
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
            var best: String? = null; var bestScore = 0
            while (c.moveToNext()) {
                val dn = c.getString(1).orEmpty().lowercase(); val q = t.lowercase()
                val sc = when { dn == q -> 3; dn.startsWith(q) -> 2; dn.contains(q) -> 1; else -> 0 }
                if (sc > bestScore) { bestScore = sc; best = c.getString(0) }
            }
            return best
        }
        return null
    }

    private val known = mapOf("whatsapp" to "com.whatsapp", "youtube" to "com.google.android.youtube", "maps" to "com.google.android.apps.maps", "mappe" to "com.google.android.apps.maps",
        "tapo" to "com.tplink.iot", "smart life" to "com.tuya.smartlife", "smartlife" to "com.tuya.smartlife", "chrome" to "com.android.chrome", "gmail" to "com.google.android.gm",
        "foto" to "com.google.android.apps.photos", "chatgpt" to "com.openai.chatgpt", "gemini" to "com.google.android.apps.bard", "claude" to "com.anthropic.claude",
        "spotify" to "com.spotify.music", "telegram" to "org.telegram.messenger", "instagram" to "com.instagram.android", "shopify" to "com.shopify.mobile", "ebay" to "com.ebay.mobile")

    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        if (name.contains("fotocamera") || name == "camera") { start(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)); return "Fotocamera" }
        if (name.contains("impostazioni")) { start(Intent(android.provider.Settings.ACTION_SETTINGS)); return "Impostazioni" }
        val pkg = known.entries.firstOrNull { name.contains(it.key) }?.value
            ?: pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }?.activityInfo?.packageName
        val i = pkg?.let { pm.getLaunchIntentForPackage(it) } ?: return "App \"$name\" non trovata"
        start(i); return "Apro $name"
    }
}
