package it.dispensa.needlevoice

import java.text.SimpleDateFormat
import java.util.*

/** Quello che Needle impara di te: fatti, alias dei nomi, cosa usi di più. */
object Memory {
    fun profile(s: Store): String {
        val u = s.usage().entries.sortedByDescending { it.value }.take(8).joinToString(", ") { "${it.key} (${it.value})" }
        val now = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN).format(Date())
        return buildString {
            append("Adesso: $now. Città: ${s.city}.")
            if (s.userName.isNotBlank()) append(" L'utente si chiama ${s.userName}.")
            val f = s.facts(); if (f.isNotEmpty()) append("\nCose che so dell'utente: ").append(f.joinToString("; "))
            val a = s.aliases(); if (a.isNotEmpty()) append("\nNomi imparati: ").append(a.entries.joinToString("; ") { "\"${it.key}\" = ${it.value}" })
            if (u.isNotEmpty()) append("\nUso più frequente: $u")
        }
    }
    fun learnAlias(s: Store, spoken: String, real: String) { if (Fuzzy.clean(spoken) != Fuzzy.clean(real) && spoken.isNotBlank()) s.putAlias(spoken, real) }
    fun usageKey(c: Cmd): String = c::class.simpleName.orEmpty()
}
