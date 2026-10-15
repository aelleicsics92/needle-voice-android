package it.dispensa.needlevoice

import java.text.Normalizer
import java.util.Locale

/** Confronto "a orecchio": tollera errori del riconoscimento vocale (morgy/morgi, linkin/linking...). */
object Fuzzy {
    fun clean(s: String): String = Normalizer.normalize(s.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Chiave fonetica italiana grezza. */
    fun phon(s: String): String {
        var x = clean(s).replace(" ", "")
        x = x.replace("ph", "f").replace("ck", "k").replace("ch", "k").replace("gh", "g").replace("sch", "sk")
            .replace("qu", "kw").replace("y", "i").replace("w", "v").replace("x", "ks").replace("j", "i").replace("h", "")
            .replace("c", "k").replace("q", "k")
        x = x.replace(Regex("(.)\\1+"), "$1")
        return x
    }

    fun lev(a: String, b: String): Int {
        if (a == b) return 0
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return d[b.length]
    }

    fun sim(a: String, b: String): Double {
        val x = clean(a); val y = clean(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val s1 = 1.0 - lev(x, y).toDouble() / maxOf(x.length, y.length)
        val px = phon(x); val py = phon(y)
        val s2 = if (px.isEmpty() || py.isEmpty()) 0.0 else 1.0 - lev(px, py).toDouble() / maxOf(px.length, py.length)
        val contain = if (x.length >= 3 && (y.contains(x) || x.contains(y))) 0.88 else 0.0
        // match per parola: "linkin park" vs "linking park"
        val wx = x.split(" "); val wy = y.split(" ")
        val s3 = wx.map { a1 -> wy.maxOf { b1 -> 1.0 - lev(a1, b1).toDouble() / maxOf(a1.length, b1.length) } }.average()
        return maxOf(s1, s2, contain, s3 * 0.95)
    }

    /** Migliore corrispondenza tra candidati; null se sotto soglia. */
    fun <T> best(q: String, items: List<T>, th: Double = 0.62, name: (T) -> String): T? =
        items.map { it to sim(q, name(it)) }.filter { it.second >= th }.maxByOrNull { it.second }?.first

    fun ranked(q: String, names: List<String>, n: Int = 3): List<String> = names.map { it to sim(q, it) }.sortedByDescending { it.second }.take(n).filter { it.second > 0.4 }.map { it.first }

    /** La parola chiave compare (anche storpiata di poco) tra le parole del testo? */
    fun hasWord(text: String, kw: String): Boolean {
        val c = clean(text); val k = clean(kw)
        if (k.contains(" ")) return c.contains(k)
        return c.split(" ").any { w -> w == k || (k.length >= 5 && w.length >= 4 && lev(w, k) <= (if (k.length >= 8) 2 else 1)) || (k.length >= 4 && w.startsWith(k.take(k.length - 1)) && w.length <= k.length + 3 && k.length >= 6) }
    }

    private val DIG = mapOf("zero" to "0", "uno" to "1", "due" to "2", "tre" to "3", "quattro" to "4", "cinque" to "5", "sei" to "6", "sette" to "7", "otto" to "8", "nove" to "9")
    /** "tre tre tre 1 2 3 45 67" -> "333123 4567" (solo cifre); null se meno di 6 cifre. */
    fun spokenNumber(s: String): String? {
        val d = StringBuilder()
        var prefixPlus = false
        for (w in clean(s).split(" ")) { if (w == "piu" && d.isEmpty()) { prefixPlus = true; continue }
            when { w.all { it.isDigit() } -> d.append(w); DIG.containsKey(w) -> d.append(DIG[w]); w == "doppio" -> {} } }
        return if (d.length >= 6) (if (prefixPlus) "+" else "") + d else null
    }
}
