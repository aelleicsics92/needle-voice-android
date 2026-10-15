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
    data class Music(val query: String) : Cmd()
    data class AskAi(val question: String) : Cmd()
    object Unknown : Cmd()
}

object Parser {
    private val NUM = mapOf("un" to 1, "uno" to 1, "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5, "sei" to 6, "sette" to 7,
        "otto" to 8, "nove" to 9, "dieci" to 10, "quindici" to 15, "venti" to 20, "trenta" to 30, "quaranta" to 40, "cinquanta" to 50, "sessanta" to 60)

    private fun num(s: String): Long? = s.toLongOrNull() ?: NUM[s]?.toLong()

    fun parse(raw: String): Cmd {
        val c = raw.lowercase(Locale.ITALIAN).trim().trimEnd('.', '!', '?')
        fun has(vararg k: String) = k.any { it in c }

        // TIMER
        if (has("timer", "conto alla rovescia", "cronometro")) {
            if (has("annulla", "ferma", "stop", "cancella", "elimina", "spegni", "interrompi", "basta")) return Cmd.CancelTimers
            if (has("quanto manca", "quali timer", "timer attiv", "elenco")) return Cmd.ListTimers
            return parseTimer(c)
        }
        if (Regex("^(ferma|annulla|stop|interrompi)( il| i| tutti i)? (timer|conto)").containsMatchIn(c)) return Cmd.CancelTimers

        // NOTE
        if (has("nota", "note", "appunt")) {
            if (has("elimina tutte", "cancella tutte", "svuota")) return Cmd.DeleteAllNotes
            if (has("elimina", "cancella", "rimuovi", "togli")) {
                val q = c.replace(Regex("\\b(elimina|cancella|rimuovi|togli|la|le|lo|nota|note|appunto|appunti|che contiene|con|di|del|dei|su|riguardo|sulla|l'ultima)\\b"), " ").replace(Regex("\\s+"), " ").trim()
                return Cmd.DeleteNote(q)
            }
            if (has("cerca", "trova", "quali note", "c'è una nota")) {
                return Cmd.SearchNotes(c.replace(Regex("\\b(cerca|trova|nelle|nella|le|la|nota|note|su|di|che|parlano|parla)\\b"), " ").replace(Regex("\\s+"), " ").trim())
            }
            if (has("leggi", "mostra", "elenca", "dimmi le", "quali sono")) return Cmd.ReadNotes
            if (has("aggiungi", "scrivi", "salva", "crea", "nuova", "prendi")) {
                val t = raw.replace(Regex("(?i)^.*?\\b(nota|appunto)\\b\\s*(che dice|:|di)?\\s*"), "").trim()
                return Cmd.AddNote(t.ifBlank { raw })
            }
        }
        if (c.startsWith("ricordami") || c.startsWith("ricorda ")) return Cmd.AddNote(raw.replace(Regex("(?i)^ricorda(mi)?\\s*(di)?\\s*"), "").trim())

        // WHATSAPP: "manda un messaggio whatsapp a Ale dicendo ciao"
        if (has("whatsapp", "wapp", "messaggio a", "scrivi a", "manda a", "invia a")) {
            val m = Regex("(?i)\\b(?:a|ad)\\s+(.+?)\\s+(?:dicendo(?:gli|le)?(?: che)?|scrivendo(?:gli|le)?|con scritto|testo|e (?:scrivi|dì|dille|digli)(?: che)?|:)\\s+(.+)$").find(raw)
            if (m != null) {
                val to = m.groupValues[1].replace(Regex("(?i)\\b(su )?whatsapp\\b"), "").trim()
                val text = m.groupValues[2].trim()
                return if (has("sms")) Cmd.Sms(to, text) else Cmd.WhatsApp(to, text)
            }
            val only = Regex("(?i)\\b(?:a|ad)\\s+([A-Za-zÀ-ÿ' ]+?)\\s*$").find(raw)
            if (has("whatsapp", "messaggio", "scrivi", "manda", "invia")) return Cmd.WhatsApp(only?.groupValues?.get(1)?.replace(Regex("(?i)\\bwhatsapp\\b"), "")?.trim().orEmpty(), "")
        }
        if (has("sms")) {
            val m = Regex("(?i)\\b(?:a|ad)\\s+(.+?)\\s+(?:dicendo|scrivendo|con scritto|:)\\s+(.+)$").find(raw)
            return Cmd.Sms(m?.groupValues?.get(1).orEmpty(), m?.groupValues?.get(2).orEmpty())
        }
        if (has("chiama", "telefona")) return Cmd.Call(raw.replace(Regex("(?i)^.*?\\b(chiama|telefona a?)\\b\\s*(a)?\\s*"), "").trim())

        // DOMOTICA
        if (has("accendi", "spegni", "attiva", "disattiva")) {
            val on = has("accendi", "attiva") && !has("spegni", "disattiva")
            val all = Regex("\\b(tutt[eio])\\b").containsMatchIn(c)
            val t = c.replace(Regex("\\b(accendi|spegni|attiva|disattiva|tutte|tutti|tutto|il|la|lo|le|gli|i|un|una|luce|luci|lampada|lampade|presa|prese|interruttore|di|del|della|dello|dei|delle|in|nel|nella|sul|sulla|per favore)\\b"), " ").replace(Regex("\\s+"), " ").trim()
            return Cmd.Home(on, t, all)
        }

        if (has("meteo", "che tempo", "piove", "previsioni")) {
            val city = Regex("(?i)\\b(?:a|di|su|per)\\s+([A-Za-zÀ-ÿ' ]+?)\\s*(?:oggi|domani|adesso)?\\s*$").find(raw)?.groupValues?.get(1).orEmpty()
            return Cmd.Weather(city)
        }
        if (has("naviga", "portami", "indicazioni", "strada per")) return Cmd.Navigate(raw.replace(Regex("(?i)\\b(naviga|portami|indicazioni|strada|verso|a|al|alla|in|fino a|per|da)\\b"), " ").replace(Regex("\\s+"), " ").trim())
        if (has("calendario", "evento", "appuntamento", "promemoria")) return Cmd.Calendar(raw.replace(Regex("(?i)\\b(aggiungi|crea|metti|inserisci|evento|appuntamento|calendario|al|nel|un)\\b"), " ").replace(Regex("\\s+"), " ").trim())
        if (has("musica", "riproduci", "metti la canzone", "suona")) return Cmd.Music(raw.replace(Regex("(?i)\\b(riproduci|metti|suona|musica|la canzone|di|su youtube)\\b"), " ").replace(Regex("\\s+"), " ").trim())
        if (c.startsWith("apri ") || c.startsWith("avvia ") || c.startsWith("lancia ")) return Cmd.OpenApp(c.substringAfter(' ').trim())

        // Domanda libera: chiedi agli AI
        if (has("chiedi", "domanda", "cosa ne pensi", "spiegami", "dimmi", "cos'è", "chi è", "come si", "perché", "quanto") ) return Cmd.AskAi(raw.replace(Regex("(?i)^(chiedi (a|agli) (ai|tutti|gemini|chatgpt|claude))\\s*"), "").trim())
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
