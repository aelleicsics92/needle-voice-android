package it.dispensa.needlevoice

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Il cervello: confronta più IA e ne fa la sintesi; improvvisa azioni sul telefono con un ciclo osserva-agisci. */
class Brain(private val ctx: Context, private val store: Store, private val actions: Actions) {
    val llm = Llm(store)
    data class Answer(val label: String, val text: String, val ok: Boolean)
    data class Council(val question: String, val answers: List<Answer>, val synthesis: String)

    private fun b64(b: Bitmap): String {
        val m = 1280; val r = m.toFloat() / maxOf(b.width, b.height)
        val s = if (r >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * r).toInt(), (b.height * r).toInt(), true)
        val o = ByteArrayOutputStream(); s.compress(Bitmap.CompressFormat.JPEG, 80, o); return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }

    // ------------------------------------------------------------ CONSIGLIO + SINTESI
    fun council(question: String, images: List<Bitmap>, progress: (String) -> Unit = {}): Council {
        val models = llm.council(images.isNotEmpty())
        if (models.isEmpty()) return Council(question, emptyList(), if (images.isNotEmpty()) "Nessun modello con visione configurato. Aggiungi una chiave Gemini o Groq in Opzioni." else "Non ho nessun modello AI configurato. Aggiungi una chiave gratuita in Opzioni, sezione Cervello.")
        val imgs = images.map { b64(it) }
        val sys = "Sei un assistente esperto e onesto. Rispondi in italiano, in modo accurato e conciso (massimo 150 parole). Se c'è un'immagine, analizzala con attenzione. Se non sei sicuro, dillo.\n${Memory.profile(store)}"
        val res = java.util.Collections.synchronizedList(ArrayList<Answer>()); val latch = CountDownLatch(models.size)
        models.forEach { m -> thread {
            val t0 = System.currentTimeMillis()
            val a = try { Answer(m.label, llm.chat(m, sys, question, if (m.vision || imgs.isEmpty()) imgs else emptyList()).trim(), true) } catch (e: Exception) { Answer(m.label, e.message ?: "errore", false) }
            res.add(a); progress("${m.label} ✓ (${(System.currentTimeMillis() - t0) / 1000}s)"); latch.countDown() } }
        latch.await(75, TimeUnit.SECONDS)
        val good = res.filter { it.ok && it.text.isNotBlank() }
        val syn = when {
            good.isEmpty() -> "Nessun modello ha risposto: " + (res.firstOrNull()?.text ?: "timeout")
            good.size == 1 -> good[0].text
            else -> try { val j = llm.judge()!!
                llm.chat(j, "Hai le risposte di diversi assistenti AI alla stessa domanda. Scrivi la sintesi finale in italiano, massimo 70 parole, da leggere ad alta voce: dai la risposta più probabilmente corretta; se gli assistenti sono in disaccordo su un punto importante dillo in una frase. Niente elenchi, niente markdown, non citare i nomi dei modelli.",
                    "Domanda: $question\n\n" + good.joinToString("\n\n") { "[${it.label}]\n${it.text}" }, maxTokens = 400, temp = 0.2) } catch (e: Exception) { good.first().text }
        }
        val c = Council(question, res.toList(), syn.trim())
        store.lastCouncil = JSONObject().put("q", question).put("syn", c.synthesis).put("answers", JSONArray().also { a -> c.answers.forEach { a.put(JSONObject().put("label", it.label).put("text", it.text).put("ok", it.ok)) } }).toString()
        return c
    }

    // ------------------------------------------------------------ AGENTE CHE IMPROVVISA
    private val tools = """
STRUMENTI (args):
open_app{name} | search_in_app{app,query} | web_search{query} | play{app,query} (app vuoto = Spotify/YouTube Music)
call{to} | whatsapp{to,text} | sms{to,text} | email{to,subject,body}
alarm{hour,minute,label} | timer{seconds} | remind{minutes,text} | note{text} | remember{fact}
navigate{dest} | camera{front} | torch{on} | volume{action:up|down|mute|unmute|set,level} | weather{city,day} | agenda{day}
home_device{on,target,all} (luci/prese via Home Assistant) | tv{app} (Netflix/YouTube sulla TV)
click{id|text} | type{text,id} | press{key:back|home|recents|notifications|enter} | scroll{dir:up|down} | wait{ms}  (usano l'elenco numerato dello schermo)
ask_council{question} (domande difficili, ragionamento) | answer{text} (rispondi a voce, termina)"""

    private fun sysPrompt() = """Sei Needle, l'assistente vocale personale di ${store.userName.ifBlank { "questo utente" }} su Android. Esegui l'obiettivo IMPROVVISANDO: scegli UNA azione alla volta tra gli strumenti.
Rispondi SOLO con JSON: {"say":"frase breve da dire ad alta voce, in italiano","action":"nome","args":{...},"finished":true|false}. finished=true se dopo questa azione l'obiettivo è completo.
$tools
Regole: preferisci gli strumenti diretti a toccare lo schermo; usa click/type solo quando serve (es. per luci in Tapo o Smart Life: open_app, poi click sul nome del dispositivo o del pulsante). Usa gli id dell'elenco schermo attuale. Domande di cultura generale o semplici: answer. Se manca l'app, dillo con answer. Non inventare contatti o numeri. Mai testo fuori dal JSON.
${Memory.profile(store)}"""

    private fun parse(raw: String): JSONObject? = try { val t = raw.replace("```json", "").replace("```", ""); JSONObject(t.substring(t.indexOf('{'), t.lastIndexOf('}') + 1)) } catch (_: Exception) { null }

    fun agent(goal: String, onStep: (String) -> Unit): String {
        val m = llm.planner() ?: return "Non ho capito. Per farmi improvvisare aggiungi una chiave AI gratuita in Opzioni."
        val hist = StringBuilder(); val sys = sysPrompt()
        for (step in 1..12) {
            val screen = NeedleAccess.inst?.snapshot(60) ?: "(accessibilità spenta: non vedo lo schermo)"
            val user = "Obiettivo: $goal\n\nAzioni già fatte:\n${hist.ifEmpty { "(nessuna)" }}\nSchermo attuale:\n$screen"
            val raw = try { llm.chat(m, sys, user, json = true, maxTokens = 500, temp = 0.2) } catch (e: Exception) { return "Errore del cervello: ${e.message?.take(80)}" }
            val j = parse(raw) ?: return raw.take(300)
            val action = j.optString("action"); val args = j.optJSONObject("args") ?: JSONObject(); val say = j.optString("say")
            onStep(say.ifBlank { action })
            if (action == "answer" || action.isBlank()) return args.optString("text").ifBlank { say }.ifBlank { "Fatto" }
            val res = try { exec(action, args) } catch (e: Exception) { "errore: ${e.message}" }
            hist.append("$step. $action $args -> ${res.take(160)}\n")
            if (action == "ask_council") return res
            if (j.optBoolean("finished")) return say.ifBlank { res }.ifBlank { "Fatto" }
            Thread.sleep(when (action) { "open_app", "search_in_app", "play", "web_search" -> 2200L; "click" -> 1100L; else -> 600L })
        }
        return "Ho provato più passaggi ma non sono riuscita a finire"
    }

    private fun s(g: JSONObject, k: String) = g.optString(k, "")
    private fun exec(a: String, g: JSONObject): String {
        val acc = NeedleAccess.inst
        return when (a) {
            "open_app" -> actions.runSync(Cmd.OpenApp(s(g, "name")))
            "search_in_app" -> actions.runSync(Cmd.AppSearch(s(g, "app"), s(g, "query")))
            "web_search" -> actions.runSync(Cmd.WebSearch(s(g, "query")))
            "play" -> actions.runSync(Cmd.Play(s(g, "app"), s(g, "query")))
            "call" -> actions.runSync(Cmd.Call(s(g, "to")))
            "whatsapp" -> actions.runSync(Cmd.WhatsApp(s(g, "to"), s(g, "text")))
            "sms" -> actions.runSync(Cmd.Sms(s(g, "to"), s(g, "text")))
            "email" -> actions.runSync(Cmd.Email(s(g, "to"), s(g, "subject"), s(g, "body")))
            "alarm" -> actions.runSync(Cmd.Alarm(g.optInt("hour"), g.optInt("minute"), s(g, "label")))
            "timer" -> actions.runSync(Cmd.SetTimer(g.optLong("seconds", 60) * 1000, ""))
            "remind" -> actions.runSync(Cmd.Remind(System.currentTimeMillis() + g.optLong("minutes", 5) * 60_000, s(g, "text")))
            "note" -> actions.runSync(Cmd.AddNote(s(g, "text")))
            "remember" -> actions.runSync(Cmd.Remember(s(g, "fact")))
            "navigate" -> actions.runSync(Cmd.Navigate(s(g, "dest")))
            "camera" -> actions.runSync(Cmd.Camera(g.optBoolean("front")))
            "torch" -> actions.runSync(Cmd.Torch(g.optBoolean("on", true)))
            "volume" -> actions.runSync(Cmd.Volume(s(g, "action").ifBlank { "set" }, if (g.has("level")) g.optInt("level") else null))
            "weather" -> actions.runSync(Cmd.Weather(s(g, "city"), g.optInt("day")))
            "agenda" -> actions.runSync(Cmd.Agenda(g.optInt("day")))
            "home_device" -> actions.runSync(Cmd.Home(g.optBoolean("on", true), s(g, "target"), g.optBoolean("all")))
            "tv" -> actions.runSync(Cmd.Tv(s(g, "app")))
            "click" -> if (acc == null) "accessibilità spenta" else if (acc.click(if (g.has("id")) g.optInt("id") else null, s(g, "text").ifBlank { null })) "ok" else "elemento non trovato"
            "type" -> if (acc == null) "accessibilità spenta" else if (acc.type(s(g, "text"), if (g.has("id")) g.optInt("id") else null)) "ok" else "campo non trovato"
            "press" -> if (acc == null) "accessibilità spenta" else when (s(g, "key")) { "back" -> acc.back(); "home" -> acc.home(); "recents" -> acc.recents(); "notifications" -> acc.notifications(); else -> acc.enter() }.let { if (it) "ok" else "non riuscito" }
            "scroll" -> if (acc == null) "accessibilità spenta" else if (acc.scroll(s(g, "dir") != "up")) "ok" else "niente da scorrere"
            "wait" -> { Thread.sleep(g.optLong("ms", 1000).coerceIn(200, 5000)); "ok" }
            "ask_council" -> council(s(g, "question"), emptyList()).synthesis
            else -> "azione sconosciuta: $a"
        }
    }
}
