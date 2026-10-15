package it.dispensa.needlevoice

import java.util.Calendar
import java.util.Locale

sealed class Cmd {
    data class Home(val on: Boolean, val target: String, val all: Boolean) : Cmd()
    data class SetTimer(val ms: Long, val label: String) : Cmd()
    object CancelTimers : Cmd()
    object ListTimers : Cmd()
    data class AddNote(val text: String) : Cmd()
    data class DeleteNote(val query: String) : Cmd()
    object DeleteAllNotes : Cmd()
    data class SearchNotes(val query: String) : Cmd()
    object ReadNotes : Cmd()
    data class WhatsApp(val to: String, val text: String) : Cmd()
    data class Sms(val to: String, val text: String) : Cmd()
    data class Call(val to: String) : Cmd()
    data class Weather(val city: String, val day: Int = 0) : Cmd()
    data class Navigate(val dest: String) : Cmd()
    data class Calendar(val title: String) : Cmd()
    data class OpenApp(val name: String) : Cmd()
    data class AppSearch(val app: String, val query: String) : Cmd()
    data class Play(val app: String, val query: String) : Cmd()
    data class WebSearch(val query: String) : Cmd()
    data class AskAi(val question: String) : Cmd()
    data class Alarm(val hour: Int, val min: Int, val label: String) : Cmd()
    object CancelAlarms : Cmd()
    data class Remind(val atMs: Long, val text: String) : Cmd()
    data class Email(val to: String, val subject: String, val body: String) : Cmd()
    data class Camera(val front: Boolean) : Cmd()
    data class Torch(val on: Boolean) : Cmd()
    data class Volume(val action: String, val level: Int?) : Cmd()
    object Briefing : Cmd()
    data class Agenda(val day: Int) : Cmd()
    data class Parking(val save: Boolean) : Cmd()
    data class Tv(val app: String) : Cmd()
    data class Remember(val fact: String) : Cmd()
    data class Panel(val which: String) : Cmd()
    data class Agent(val goal: String) : Cmd()
    object Unknown : Cmd()
}

object Parser {
    private val NUM = mapOf("un" to 1, "uno" to 1, "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5, "sei" to 6, "sette" to 7,
        "otto" to 8, "nove" to 9, "dieci" to 10, "quindici" to 15, "venti" to 20, "trenta" to 30, "quaranta" to 40, "cinquanta" to 50, "sessanta" to 60)
    private fun num(s: String): Long? = s.toLongOrNull() ?: NUM[s]?.toLong()

    private val FILLER = Regex("^(ehi |ei |ciao |ok )?(needle |nidl )?(per favore |per piacere |puoi |potresti |vorrei |mi puoi |mi potresti |dai )*", RegexOption.IGNORE_CASE)

    /** Prova tutte le alternative del riconoscimento e prende la prima comprensibile. */
    fun parseBest(alts: List<String>): Pair<String, Cmd> {
        for (a in alts) { val c = parse(a); if (c != Cmd.Unknown) return a to c }
        return (alts.firstOrNull().orEmpty()) to Cmd.Unknown
    }


    private val UNITS = listOf("zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci", "undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto", "diciannove")
    private val WORDNUM: Map<String, Int> by lazy {
        val m = HashMap<String, Int>(); UNITS.forEachIndexed { i, w -> m[w] = i }; m["un"] = 1; m["mezzanotte"] = 0; m["mezzogiorno"] = 12
        listOf("venti" to 20, "trenta" to 30, "quaranta" to 40, "cinquanta" to 50).forEach { (t, v) -> m[t] = v; for (u in 1..9) m[(if (u == 1 || u == 8) t.dropLast(1) else t) + UNITS[u]] = v + u }
        m
    }
    private fun digits(c: String) = Fuzzy.clean(c).split(" ").joinToString(" ") { WORDNUM[it]?.toString() ?: it }

    /** "alle 7 e mezza", "alle sette e un quarto", "18:30" -> (ora, minuti) */
    fun parseClock(raw: String): Pair<Int, Int>? {
        val d = digits(raw); val pm = Regex("\\b(pomeriggio|sera|stasera|notte)\\b").containsMatchIn(d)
        fun fix(h: Int) = if (pm && h in 1..11) h + 12 else h
        Regex("(\\d{1,2})[:.](\\d{2})").find(raw)?.let { return fix(it.groupValues[1].toInt()) to it.groupValues[2].toInt() }
        Regex("\\b(?:alle|all|ore|per le)\\s*(\\d{1,2})\\s*(?:e\\s*(mezza|mezzo|1 quarto|3 quarti|\\d{1,2})|meno\\s*(1 quarto))?").find(d)?.let { m ->
            var h = fix(m.groupValues[1].toInt()); var mi = 0
            when (val x = m.groupValues[2]) { "mezza", "mezzo" -> mi = 30; "1 quarto" -> mi = 15; "3 quarti" -> mi = 45; "" -> {}; else -> mi = x.toInt() }
            if (m.groupValues[3].isNotEmpty()) { h -= 1; mi = 45 }
            if (h in 0..23 && mi in 0..59) return (if (h < 0) 23 else h) to mi
        }
        return null
    }

    /** Istante futuro descritto in testo: "tra 20 minuti", "alle 18", "domani alle 8". */
    fun parseWhen(raw: String): Long? {
        val d = digits(raw); val now = Calendar.getInstance()
        Regex("\\btra\\s+(\\d+)\\s*(ore|ora|minuti|minuto|min|secondi)").find(d)?.let { m ->
            val n = m.groupValues[1].toLong(); return now.timeInMillis + n * when { m.groupValues[2].startsWith("or") -> 3_600_000L; m.groupValues[2].startsWith("s") -> 1000L; else -> 60_000L } }
        if (Regex("\\btra\\s+mezz").containsMatchIn(d)) return now.timeInMillis + 1_800_000L
        val (h, mi) = parseClock(raw) ?: return null
        val t = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0) }
        if (Regex("\\bdomani\\b").containsMatchIn(d)) t.add(Calendar.DAY_OF_YEAR, 1)
        else if (t.timeInMillis <= now.timeInMillis) { if (h < 12 && t.timeInMillis + 43_200_000L > now.timeInMillis && !Regex("\\b(mattina|domattina)\\b").containsMatchIn(d)) t.add(Calendar.HOUR_OF_DAY, 12) else t.add(Calendar.DAY_OF_YEAR, 1) }
        return t.timeInMillis
    }

    private fun clip(s: String, vararg junk: String) = s.replace(Regex("(?i)\\b(" + junk.joinToString("|") + ")\\b"), " ").replace(Regex("\\s+"), " ").trim()

    fun parse(input: String): Cmd {
        val raw = input.trim().replace(FILLER, "").trim()
        val c = raw.lowercase(Locale.ITALIAN).trimEnd('.', '!', '?', ',')
        fun has(vararg k: String) = k.any { Fuzzy.hasWord(c, it) }
        fun exact(vararg k: String) = k.any { it in c }
        if (c.isBlank()) return Cmd.Unknown

        // STOP generico (suoneria timer)
        if (c in listOf("ferma", "stop", "basta", "zitto", "zitta", "smettila", "fermati", "silenzio")) return Cmd.CancelTimers

        // TIMER
        if (has("timer", "cronometro") || exact("conto alla rovescia")) {
            if (has("annulla", "ferma", "stop", "cancella", "elimina", "interrompi", "basta", "disattiva", "togli", "spegni", "rimuovi")) return Cmd.CancelTimers
            if (exact("quanto manca", "quali timer", "timer attiv", "elenco")) return Cmd.ListTimers
            return parseTimer(c)
        }
        if (has("sveglia") && has("annulla", "cancella", "elimina", "ferma")) return Cmd.CancelTimers


        // TORCIA / VOLUME / PANNELLI
        if (has("torcia", "flash") || exact("lampadina del telefono", "luce del telefono")) return Cmd.Torch(!has("spegni", "disattiva", "chiudi"))
        if (has("volume")) { val n = Regex("(\\d{1,3})").find(digits(c))?.groupValues?.get(1)?.toIntOrNull()
            return Cmd.Volume(when { has("alza", "aumenta", "più forte", "piu forte") -> "up"; has("abbassa", "diminuisci", "più basso", "piu basso") -> "down"; else -> "set" }, if (has("alza", "abbassa", "aumenta", "diminuisci")) null else n) }
        if (has("muto", "silenzioso", "silenzia")) return Cmd.Volume("mute", null)
        if (has("wifi") || has("bluetooth")) return Cmd.Panel(if (has("bluetooth")) "bluetooth" else "wifi")

        // SVEGLIA
        if (has("sveglia", "svegliami", "svegliarmi") || exact("metti la sveglia")) {
            if (has("annulla", "cancella", "elimina", "togli", "disattiva", "spegni")) return Cmd.CancelAlarms
            parseClock(raw)?.let { (h, m) -> return Cmd.Alarm(h, m, "") }
            parseWhen(raw)?.let { t -> val cal = Calendar.getInstance().apply { timeInMillis = t }; return Cmd.Alarm(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), "") }
        }

        // MEMORIA ("ricorda che mia moglie si chiama Anna")
        Regex("(?i)^(?:ricorda(?:ti)? che|tieni a mente che|impara che|ricorda che|segnati che)\\s+(.+)$").find(raw)?.let { return Cmd.Remember(it.groupValues[1].trim()) }

        // PROMEMORIA con orario ("ricordami alle 18 di chiamare Mario", "ricordami tra 10 minuti di ...")
        if (Regex("^(ricordami|ricorda|promemoria|avvisami)\\b").containsMatchIn(c)) {
            val whenMs = parseWhen(raw)
            if (whenMs != null) {
                val text = raw.replace(Regex("(?i)^(ricordami|ricorda|promemoria|avvisami)\\s*"), "").replace(Regex("(?i)\\b(tra|alle|all'|domani|stasera|stamattina|oggi)\\s*[0-9a-zàèéìòù:.]*\\s*(ore|ora|minuti|minuto|min|secondi|e mezza|e mezzo)?"), " ").replace(Regex("(?i)\\bdi\\b"), " ").replace(Regex("\\s+"), " ").trim()
                return Cmd.Remind(whenMs, text.ifBlank { "Promemoria" })
            }
        }

        // FOTOCAMERA istantanea
        if ((has("foto", "fotografia", "selfie", "scatta", "scattami", "fotografami", "autoscatto")) && !has("galleria", "album") && Regex("\\b(scatta|scattami|fai|fammi|fa|fotografami|prendi|selfie|autoscatto)").containsMatchIn(c)) return Cmd.Camera(has("selfie", "frontale", "me stess", "autoscatto"))

        // TV / FIRE STICK
        if (exact("sulla tv", "sulla televisione", "sulla fire", "firestick", "fire stick", "fire tv", "sul televisore", "in tv")) {
            return Cmd.Tv(clip(raw, "apri", "metti", "avvia", "lancia", "fai partire", "sulla", "sul", "tv", "televisione", "televisore", "fire", "stick", "firestick", "su", "in", "la", "il", "lo"))
        }

        // CERCA / METTI in un'app
        Regex("(?i)^(?:cerca|trova|ricerca|cercami|cerchi)\\s+(?:su|in|nel|nella|nello|sul|sullo|sulla|da|con|dentro)\\s+(?:l'|lo |la |il )?(.+)$").find(raw)?.let { m ->
            // "play store gioco": app e query vengono separate da Actions in base alle app installate
            return Cmd.AppSearch(m.groupValues[1].trim(), "") }
        Regex("(?i)^(?:cerca|trova|ricerca)\\s+(.+?)\\s+(?:su|in|nel|nella|nello|sul|sullo|sulla|con|dentro)\\s+(?:l'|lo |la |il )?(.+)$").find(raw)?.let { m ->
            val app = m.groupValues[2].trim()
            return if (Regex("(?i)^(google|internet|web|il web)$").matches(app)) Cmd.WebSearch(m.groupValues[1].trim()) else Cmd.AppSearch(app, m.groupValues[1].trim()) }
        Regex("(?i)^(?:apri|avvia|lancia)\\s+(.+?)\\s+e\\s+(?:cerca|trova)\\s+(.+)$").find(raw)?.let { return Cmd.AppSearch(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        Regex("(?i)^(?:metti|mettimi|riproduci|suona|suonami|ascolta|ascoltare|fai partire|fammi sentire|play|canta|avvia)\\s+(?:la |il |lo |i |le |gli |l'|un |una |un po' di )?(.+?)\\s+(?:su|con|in|da|tramite)\\s+(?:l'|lo |la |il )?(.+)$").find(raw)?.let { m ->
            val q = m.groupValues[1].trim(); val app = m.groupValues[2].trim()
            if (app.split(" ").size <= 3 && !has("luce", "luci", "timer")) return Cmd.Play(app, q) }
        Regex("(?i)^(?:metti|mettimi|riproduci|suona|suonami|ascolta|fai partire|fammi sentire|play)\\s+(?:la |il |lo |i |le |gli |l'|un |una |un po' di )?(.+)$").find(raw)?.let { m ->
            val q = m.groupValues[1].trim()
            if (!has("luce", "luci", "lampada", "presa", "sveglia", "timer", "tv", "televisione", "riscaldamento")) return Cmd.Play("", q) }
        Regex("(?i)^(?:cerca|trova|cercami|ricerca|googla)\\s+(.+)$").find(raw)?.let { return Cmd.WebSearch(it.groupValues[1].trim()) }

        // NOTE
        if (has("nota", "note", "appunto", "appunti")) {
            if (exact("elimina tutte", "cancella tutte", "svuota", "cancella tutto", "elimina tutto")) return Cmd.DeleteAllNotes
            if (has("elimina", "cancella", "rimuovi", "togli", "butta"))
                return Cmd.DeleteNote(clip(c, "elimina", "cancella", "rimuovi", "togli", "butta", "la", "le", "lo", "nota", "note", "appunto", "appunti", "che contiene", "con", "di", "del", "dei", "su", "riguardo", "sulla", "ultima", "l'ultima"))
            if (has("cerca", "trova", "cercami") || exact("quali note", "c'è una nota"))
                return Cmd.SearchNotes(clip(c, "cerca", "trova", "cercami", "nelle", "nella", "le", "la", "nota", "note", "su", "di", "che", "parlano", "parla"))
            if (has("leggi", "mostra", "elenca") || exact("dimmi le", "quali sono")) return Cmd.ReadNotes
            if (has("aggiungi", "scrivi", "salva", "crea", "nuova", "prendi", "segna", "annota")) {
                val t = raw.replace(Regex("(?i)^.*?\\b(nota|appunto)\\b\\s*(che dice|:|di)?\\s*"), "").trim()
                return Cmd.AddNote(t.ifBlank { raw })
            }
        }
        if (Regex("^(ricordami|ricorda|segna|annota)\\b").containsMatchIn(c)) return Cmd.AddNote(raw.replace(Regex("(?i)^(ricorda(mi)?|segna|annota)\\s*(di|che)?\\s*"), "").trim())


        // EMAIL
        if (has("mail", "email", "e-mail", "posta") && has("manda", "invia", "scrivi", "scrivere", "mandare", "spedisci")) {
            val m = Regex("(?i)\\b(?:a|ad)\\s+(.+?)\\s+(?:con )?(?:oggetto|titolo)\\s+(.+?)\\s+(?:e )?(?:dicendo|testo|scrivendo|con scritto|messaggio)\\s+(.+)$").find(raw)
            if (m != null) return Cmd.Email(m.groupValues[1].trim(), m.groupValues[2].trim(), m.groupValues[3].trim())
            val m2 = Regex("(?i)\\b(?:a|ad)\\s+(.+?)\\s*(?:,|:|\\bdicendo\\b|\\bscrivendo\\b|\\bcon scritto\\b|\\bil testo\\b|\\bche\\b)\\s*(.+)$").find(raw)
            if (m2 != null) return Cmd.Email(m2.groupValues[1].trim(), "", m2.groupValues[2].trim())
            return Cmd.Email(Regex("(?i)\\b(?:a|ad)\\s+([A-Za-zÀ-ÿ' ]+?)\\s*$").find(raw)?.groupValues?.get(1).orEmpty(), "", "")
        }

        // GIORNATA
        if (exact("buongiorno", "briefing", "riassunto della giornata", "cosa devo fare oggi", "com'è la giornata", "com'e la giornata")) return Cmd.Briefing
        if ((has("agenda", "impegni", "appuntamenti", "calendario") || exact("cosa ho domani", "cosa ho oggi", "che ho domani", "che ho oggi")) && !has("aggiungi", "crea", "metti", "inserisci", "nuovo", "nuova")) return Cmd.Agenda(if (has("domani")) 1 else 0)
        if (exact("ho parcheggiato", "parcheggiato qui", "ricorda dove parcheggio", "ricorda il parcheggio", "segna il parcheggio")) return Cmd.Parking(true)
        if (exact("dov'è la macchina", "dove ho parcheggiato", "dov'è la mia auto", "dov'e la macchina", "dov'e la mia auto", "portami alla macchina", "trova la macchina", "trova l'auto", "portami all'auto")) return Cmd.Parking(false)

        // MESSAGGI / CHIAMATE
        val msgRe = Regex("(?i)\\b(?:a|ad)\\s+(.+?)\\s*(?:,|:|\\bdicendo(?:gli|le)?(?: che)?\\b|\\bscrivendo(?:gli|le)?\\b|\\bcon scritto\\b|\\bil testo\\b|\\be (?:scrivi|dì|dille|digli)(?: che)?\\b|\\bche\\b)\\s*(.+)$")
        if (has("whatsapp", "wapp", "uozap", "messaggio", "sms") || Regex("(?i)^(scrivi|manda|invia|mandi|scrivigli|mandagli|dì|dillo)\\s+a\\b").containsMatchIn(raw)) {
            val sms = has("sms")
            val m = msgRe.find(raw)
            if (m != null) {
                val to = m.groupValues[1].replace(Regex("(?i)\\b(su |con |tramite )?(whatsapp|uozap|wapp|sms)\\b"), "").trim()
                val text = m.groupValues[2].trim()
                return if (sms) Cmd.Sms(to, text) else Cmd.WhatsApp(to, text)
            }
            val only = Regex("(?i)\\b(?:a|ad)\\s+([A-Za-zÀ-ÿ' ]+?)(?:\\s+(?:su|con|tramite)\\s+\\w+)?\\s*$").find(raw)?.groupValues?.get(1).orEmpty().replace(Regex("(?i)\\b(whatsapp|messaggio|sms)\\b"), "").trim()
            if (has("whatsapp", "wapp", "uozap", "messaggio", "sms", "scrivi", "manda", "invia")) return if (sms) Cmd.Sms(only, "") else Cmd.WhatsApp(only, "")
        }
        if (has("chiama", "telefona", "chiamami", "componi") ) {
            val n = Fuzzy.spokenNumber(c)
            if (n != null) return Cmd.Call(n)
            return Cmd.Call(clip(raw, "chiama", "telefona", "componi", "a", "il", "numero", "di", "al", "alla"))
        }

        // DOMOTICA
        if (has("accendi", "spegni", "attiva", "disattiva", "accendere", "spegnere")) {
            val off = has("spegni", "disattiva", "spegnere")
            val on = has("accendi", "attiva", "accendere") && !off
            val all = Regex("\\b(tutt[eio])\\b").containsMatchIn(c)
            return Cmd.Home(on, clip(c, "accendi", "spegni", "attiva", "disattiva", "accendere", "spegnere", "tutte", "tutti", "tutto", "il", "la", "lo", "le", "gli", "i", "un", "una", "luce", "luci", "lampada", "lampade", "presa", "prese", "interruttore", "di", "del", "della", "dello", "dei", "delle", "in", "nel", "nella", "sul", "sulla", "per favore"), all)
        }

        if (has("meteo", "piove", "previsioni", "pioggia", "temperatura") || exact("che tempo")) {
            val city = Regex("(?i)\\b(?:a|di|su|per|in)\\s+([A-Za-zÀ-ÿ' ]+?)\\s*(?:oggi|domani|adesso|stasera|stamattina)?\\s*$").find(raw)?.groupValues?.get(1).orEmpty()
            return Cmd.Weather(city.replace(Regex("(?i)\\b(domani|dopodomani|oggi)\\b"), "").trim(), if (has("dopodomani")) 2 else if (has("domani")) 1 else 0)
        }
        if (has("naviga", "portami", "indicazioni", "navigatore") || exact("strada per", "come arrivo")) return Cmd.Navigate(clip(raw, "naviga", "portami", "indicazioni", "strada", "verso", "a", "al", "alla", "in", "fino a", "per", "da", "navigatore", "come", "arrivo"))
        if (has("calendario", "evento", "appuntamento", "promemoria")) return Cmd.Calendar(clip(raw, "aggiungi", "crea", "metti", "inserisci", "evento", "appuntamento", "calendario", "al", "nel", "un", "promemoria"))
        if (Regex("^(apri|avvia|lancia|vai su|vai in|apri l'app|aprimi)\\b").containsMatchIn(c)) return Cmd.OpenApp(clip(raw, "apri", "avvia", "lancia", "vai", "su", "in", "l'app", "app", "aprimi", "l'applicazione", "applicazione"))

        if (Regex("^chiedi (a|agli|alle|a tutte|a tutti)").containsMatchIn(c) || exact("confronta le ai", "chiedi a tutte le ai")) return Cmd.AskAi(raw.replace(Regex("(?i)^(chiedi (a|agli) (ai|tutti|gemini|chatgpt|claude))\\s*"), "").trim())
        return Cmd.Unknown
    }

    private fun parseTimer(c: String): Cmd {
        val re = Regex("(\\d+|\\b(?:un|uno|una|due|tre|quattro|cinque|sei|sette|otto|nove|dieci|quindici|venti|trenta|quaranta|cinquanta|sessanta)\\b)\\s*(ore|ora|minut[oi]|min|second[oi]|sec)")
        var ms = 0L
        re.findAll(c).forEach {
            val n = num(it.groupValues[1]) ?: return@forEach
            ms += when { it.groupValues[2].startsWith("or") -> n * 3_600_000L; it.groupValues[2].startsWith("m") -> n * 60_000L; else -> n * 1000L }
        }
        if ("mezz'ora" in c || "mezz ora" in c || "mezza ora" in c) ms += 1_800_000L
        if (ms == 0L) ms = (Regex("(\\d+)").find(c)?.groupValues?.get(1)?.toLongOrNull() ?: 1L) * 60_000L
        return Cmd.SetTimer(ms, "")
    }
}
