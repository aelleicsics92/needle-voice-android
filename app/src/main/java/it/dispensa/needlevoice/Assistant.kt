package it.dispensa.needlevoice

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread

/** Logica condivisa tra overlay (icona/widget) e app completa. */
class Assistant(val ctx: Context) {
    val store = Store(ctx)
    val voice = Voice(ctx)
    val actions = Actions(ctx, store)
    val brain by lazy { Brain(ctx, store, actions) }
    private val ui = Handler(Looper.getMainLooper())
    var heard by mutableStateOf("")
    var result by mutableStateOf("")
    var active by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var thinking by mutableStateOf(false)
    var level by mutableFloatStateOf(0f)
    private var pendingAlts: List<String>? = null
    private var pendingNeed: ((String) -> Unit)? = null
    private var pendingDone: ((Boolean) -> Unit)? = null

    init { Speaker.init(ctx) }

    fun haOn() = store.haUrl.startsWith("http") && store.haToken.isNotBlank()

    fun listen(onNeedPerm: (String) -> Unit, onFinished: (Boolean) -> Unit) {
        Speaker.stop(); busy = false; heard = ""; result = "In ascolto…"; active = true; level = 0f
        voice.listen(
            cbPartial = { heard = it },
            cbFinal = { alts -> active = false; handle(alts, onNeedPerm, onFinished) },
            cbError = { active = false; result = it; onFinished(false) },
            cbLevel = { level = maxOf(level * 0.85f, ((it + 2f) / 12f).coerceIn(0f, 1f)) })
    }

    fun typed(text: String, onNeedPerm: (String) -> Unit, onFinished: (Boolean) -> Unit) { if (text.isNotBlank()) handle(listOf(text.trim()), onNeedPerm, onFinished) }

    private fun say(msg: String) { if (store.speakReplies) Speaker.say(ctx, msg) }

    fun handle(alts: List<String>, onNeedPerm: (String) -> Unit, onFinished: (Boolean) -> Unit) {
        busy = true
        val (text, c0) = Parser.parseBest(alts)
        heard = text
        var cmd = c0
        if (cmd is Cmd.Home && !haOn()) cmd = Cmd.Agent("$text (usa l'app Tapo o Smart Life: aprila e tocca il dispositivo giusto)")
        if (cmd == Cmd.Unknown) cmd = Cmd.Agent(text)
        val perm = actions.permFor(cmd)
        if (perm != null) { pendingAlts = alts; pendingNeed = onNeedPerm; pendingDone = onFinished; result = "Mi serve un permesso"; onNeedPerm(perm); return }
        store.bump(Memory.usageKey(cmd))
        when (val c = cmd) {
            is Cmd.AskAi -> { thinking = true; result = "Chiedo a più AI…"
                thread { val r = try { brain.council(c.question, emptyList()) { p -> ui.post { result = p } }.synthesis } catch (e: Exception) { "Errore: ${e.message}" }
                    ui.post { thinking = false; result = r; say(r); onFinished(true) } } }
            is Cmd.Agent -> { thinking = true; result = "Ci penso…"
                thread { val r = try { brain.agent(c.goal) { s -> ui.post { result = s } } } catch (e: Exception) { "Errore: ${e.message}" }
                    ui.post { thinking = false; result = r; say(r); onFinished(true) } } }
            else -> actions.run(c) { msg -> result = if (msg == "NOHA") "Home Assistant non configurato" else msg; say(result); onFinished(c != Cmd.Unknown) }
        }
    }

    fun resumePending(granted: Boolean) {
        val a = pendingAlts ?: return; val n = pendingNeed ?: return; val d = pendingDone ?: return
        pendingAlts = null
        if (!granted) { result = "Senza quel permesso non posso farlo"; d(false); return }
        handle(a, n, d)
    }

    /** Domanda con foto dall'app: confronto + sintesi parlata. */
    fun askCouncil(q: String, images: List<android.graphics.Bitmap>, progress: (String) -> Unit, done: (Brain.Council) -> Unit) {
        thread { val c = try { brain.council(q, images) { p -> ui.post { progress(p) } } } catch (e: Exception) { Brain.Council(q, emptyList(), "Errore: ${e.message}") }
            ui.post { say(c.synthesis); done(c) } }
    }

    fun release() = voice.release()
}
