package it.dispensa.needlevoice

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Invia testo e foto alle app AI installate (usano il tuo account, nessuna chiave API). */
object AiApps {
    data class A(val name: String, val pkg: String)
    val list = listOf(A("ChatGPT", "com.openai.chatgpt"), A("Gemini", "com.google.android.apps.bard"), A("Claude", "com.anthropic.claude"),
        A("Copilot", "com.microsoft.copilot"), A("Perplexity", "ai.perplexity.app.android"), A("Grok", "ai.x.grok"))

    fun installed(ctx: Context) = list.filter { try { ctx.packageManager.getPackageInfo(it.pkg, 0); true } catch (_: Exception) { false } }

    fun share(ctx: Context, a: A, text: String, uris: List<Uri>): Boolean {
        val i = if (uris.size > 1) Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            else Intent(Intent.ACTION_SEND).also { if (uris.isNotEmpty()) it.putExtra(Intent.EXTRA_STREAM, uris[0]) }
        i.type = if (uris.isEmpty()) "text/plain" else "image/*"
        if (text.isNotBlank()) i.putExtra(Intent.EXTRA_TEXT, text)
        i.setPackage(a.pkg).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (uris.isNotEmpty()) { val cd = ClipData.newRawUri("img", uris[0]); uris.drop(1).forEach { cd.addItem(ClipData.Item(it)) }; i.clipData = cd }
        return try { ctx.startActivity(i); true } catch (_: Exception) { false }
    }

    fun lens(ctx: Context): Boolean {
        val g = "com.google.android.googlequicksearchbox"
        val l = listOf(Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage(g), ctx.packageManager.getLaunchIntentForPackage(g))
        return l.filterNotNull().any { try { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false } }
    }
}
