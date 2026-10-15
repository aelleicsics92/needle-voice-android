package it.dispensa.needlevoice

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
    data class Weather(val city: String) : Cmd()
    data class Navigate(val dest: String) : Cmd()
    data class Calendar(val title: String) : Cmd()
    data class OpenApp(val name: String) : Cmd()
    data class AppSearch(val app: String, val query: String) : Cmd()
    data class Play(val app: String, val query: String) : Cmd()
    data class WebSearch(val query: String) : Cmd()
    data class AskAi(val question: String) : Cmd()
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
            return Cmd.Weather(city)
        }
        if (has("naviga", "portami", "indicazioni", "navigatore") || exact("strada per", "come arrivo")) return Cmd.Navigate(clip(raw, "naviga", "portami", "indicazioni", "strada", "verso", "a", "al", "alla", "in", "fino a", "per", "da", "navigatore", "come", "arrivo"))
        if (has("calendario", "evento", "appuntamento", "promemoria")) return Cmd.Calendar(clip(raw, "aggiungi", "crea", "metti", "inserisci", "evento", "appuntamento", "calendario", "al", "nel", "un", "promemoria"))
        if (Regex("^(apri|avvia|lancia|vai su|vai in|apri l'app|aprimi)\\b").containsMatchIn(c)) return Cmd.OpenApp(clip(raw, "apri", "avvia", "lancia", "vai", "su", "in", "l'app", "app", "aprimi", "l'applicazione", "applicazione"))

        if (has("chiedi", "domanda", "spiegami", "dimmi") || exact("cosa ne pensi", "cos'è", "chi è", "come si", "perché", "quanto")) return Cmd.AskAi(raw.replace(Regex("(?i)^(chiedi (a|agli) (ai|tutti|gemini|chatgpt|claude))\\s*"), "").trim())
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
