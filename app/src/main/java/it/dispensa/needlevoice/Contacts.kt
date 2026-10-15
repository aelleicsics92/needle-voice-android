package it.dispensa.needlevoice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

class Contacts(private val ctx: Context) {
    data class Person(val name: String, val number: String)
    data class Result(val person: Person?, val suggestions: List<String>, val noPermission: Boolean = false)

    fun hasPermission() = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun all(): List<Person> {
        val out = mutableListOf<Person>()
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
            while (c.moveToNext()) { val n = c.getString(0).orEmpty(); val p = c.getString(1).orEmpty(); if (n.isNotBlank() && p.isNotBlank()) out.add(Person(n, p)) }
        }
        return out
    }

    /** Nome parlato (anche storpiato) o numero detto a voce -> contatto. */
    fun resolve(spoken: String): Result {
        val t = spoken.trim()
        Fuzzy.spokenNumber(t)?.let { return Result(Person(it, it), emptyList()) }
        if (Regex("^\\+?[0-9 ()-]{6,}$").matches(t)) return Result(Person(t, t), emptyList())
        if (t.isBlank()) return Result(null, emptyList())
        if (!hasPermission()) return Result(null, emptyList(), true)
        val list = all()
        val scored = list.map { it to Fuzzy.sim(t, it.name) }.sortedWith(compareByDescending<Pair<Person, Double>> { it.second }.thenBy { it.first.name.length })
        val top = scored.firstOrNull()
        if (top != null && top.second >= 0.62) return Result(top.first, emptyList())
        return Result(null, scored.filter { it.second > 0.4 }.map { it.first.name }.distinct().take(3))
    }

    /** Numero in formato internazionale per wa.me (default Italia). */
    fun waNumber(n: String): String {
        val d = n.filter { it.isDigit() || it == '+' }
        return when { d.startsWith("+") -> d.drop(1); d.startsWith("00") -> d.drop(2); d.length <= 10 -> "39$d"; else -> d }
    }
}
