package it.dispensa.needlevoice

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

class Apps(private val ctx: Context) {
    data class App(val label: String, val pkg: String)

    private val pm = ctx.packageManager
    val all: List<App> by lazy {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.distinctBy { it.pkg }
    }

    private val alias: Map<String, List<String>> = mapOf(
        "play store" to listOf("com.android.vending"), "google play" to listOf("com.android.vending"), "store" to listOf("com.android.vending"),
        "amazon" to listOf("com.amazon.mShop.android.shopping"), "spotify" to listOf("com.spotify.music"),
        "youtube music" to listOf("com.google.android.apps.youtube.music"), "youtube" to listOf("com.google.android.youtube"),
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"), "maps" to listOf("com.google.android.apps.maps"), "mappe" to listOf("com.google.android.apps.maps"),
        "google maps" to listOf("com.google.android.apps.maps"), "chrome" to listOf("com.android.chrome"), "gmail" to listOf("com.google.android.gm"),
        "foto" to listOf("com.google.android.apps.photos"), "chatgpt" to listOf("com.openai.chatgpt"), "gemini" to listOf("com.google.android.apps.bard"),
        "claude" to listOf("com.anthropic.claude"), "tapo" to listOf("com.tplink.iot"), "smart life" to listOf("com.tuya.smartlife"), "smartlife" to listOf("com.tuya.smartlife"),
        "alexa" to listOf("com.amazon.dee.app"), "telegram" to listOf("org.telegram.messenger"), "instagram" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana"), "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "netflix" to listOf("com.netflix.mediaclient"),
        "prime video" to listOf("com.amazon.avod.thirdpartyclient"), "ebay" to listOf("com.ebay.mobile"), "subito" to listOf("it.subito"), "vinted" to listOf("fr.vinted"),
        "shopify" to listOf("com.shopify.mobile"), "paypal" to listOf("com.paypal.android.p2pmobile"), "google" to listOf("com.google.android.googlequicksearchbox"),
        "calcolatrice" to listOf("com.google.android.calculator", "com.miui.calculator"), "orologio" to listOf("com.google.android.deskclock", "com.android.deskclock"),
        "impostazioni" to listOf("com.android.settings"), "fotocamera" to listOf("com.android.camera", "com.google.android.GoogleCamera", "com.miui.camera"),
        "calendario" to listOf("com.google.android.calendar"), "contatti" to listOf("com.google.android.contacts", "com.android.contacts"),
        "drive" to listOf("com.google.android.apps.docs"), "traduttore" to listOf("com.google.android.apps.translate"), "lens" to listOf("com.google.android.googlequicksearchbox"))

    fun installed(pkg: String) = try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
    fun launchIntent(pkg: String): Intent? = pm.getLaunchIntentForPackage(pkg)

    /** Cerca l'app per nome (alias noti, poi corrispondenza fuzzy sulle app installate). */
    fun find(name: String): App? {
        val n = Fuzzy.clean(name); if (n.isBlank()) return null
        alias.entries.filter { Fuzzy.sim(n, it.key) >= 0.85 }.sortedByDescending { Fuzzy.sim(n, it.key) }.forEach { e ->
            e.value.firstOrNull { installed(it) }?.let { p -> return App(all.firstOrNull { it.pkg == p }?.label ?: e.key, p) } }
        return Fuzzy.best(n, all, 0.7) { it.label }
    }

    /** "play store gioco" -> (Play Store, "gioco"): prova le prime 3,2,1 parole come nome app. */
    fun splitAppQuery(tail: String): Pair<App?, String> {
        val w = tail.trim().split(Regex("\\s+"))
        for (k in minOf(3, w.size) downTo 1) {
            val a = find(w.take(k).joinToString(" "))
            if (a != null) return a to w.drop(k).joinToString(" ")
        }
        return null to tail
    }

    private fun q(s: String) = Uri.encode(s)

    private fun template(pkg: String, query: String): String? = when (pkg) {
        "com.amazon.mShop.android.shopping" -> "https://www.amazon.it/s?k=${q(query)}"
        "com.android.vending" -> "market://search?q=${q(query)}&c=apps"
        "com.google.android.youtube" -> "https://www.youtube.com/results?search_query=${q(query)}"
        "com.google.android.apps.youtube.music" -> "https://music.youtube.com/search?q=${q(query)}"
        "com.spotify.music" -> "spotify:search:${q(query)}"
        "com.google.android.apps.maps" -> "geo:0,0?q=${q(query)}"
        "com.ebay.mobile" -> "https://www.ebay.it/sch/i.html?_nkw=${q(query)}"
        "it.subito" -> "https://www.subito.it/annunci-italia/vendita/usato/?q=${q(query)}"
        "fr.vinted" -> "https://www.vinted.it/catalog?search_text=${q(query)}"
        "com.netflix.mediaclient" -> "https://www.netflix.com/search?q=${q(query)}"
        "com.instagram.android" -> "https://www.instagram.com/explore/search/keyword/?q=${q(query)}"
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "https://www.tiktok.com/search?q=${q(query)}"
        "com.facebook.katana" -> "https://www.facebook.com/search/top?q=${q(query)}"
        "com.android.chrome", "com.google.android.googlequicksearchbox" -> "https://www.google.com/search?q=${q(query)}"
        else -> null
    }

    /** Intent da provare in ordine per cercare `query` dentro l'app. */
    fun searchIntents(app: App, query: String): List<Intent> {
        val l = mutableListOf<Intent>()
        template(app.pkg, query)?.let { l.add(Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(app.pkg)) }
        l.add(Intent(Intent.ACTION_SEARCH).setPackage(app.pkg).putExtra(SearchManager.QUERY, query))
        l.add(Intent("com.google.android.gms.actions.SEARCH_ACTION").setPackage(app.pkg).putExtra(SearchManager.QUERY, query))
        l.add(Intent(Intent.ACTION_WEB_SEARCH).setPackage(app.pkg).putExtra(SearchManager.QUERY, query))
        return l
    }

    fun playIntent(app: App, query: String): List<Intent> {
        val l = mutableListOf<Intent>()
        l.add(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(app.pkg)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*").putExtra(SearchManager.QUERY, query))
        l.addAll(searchIntents(app, query))
        return l
    }
}
