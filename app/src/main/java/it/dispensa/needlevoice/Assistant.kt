package it.dispensa.needlevoice

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Logica condivisa tra overlay (icona) e app completa. */
class Assistant(val ctx: Context) {
    val store = Store(ctx)
    val voice = Voice(ctx)
    val actions = Actions(ctx, store)
    var heard by mutableStateOf("")
    var result by mutableStateOf("")
    var active by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var level by mutableFloatStateOf(0f)
    private var pending: Pair<List<String>, Cmd>? = null

    /** onAsk: domanda libera per gli AI. onNeedContacts: chiedere il permesso. onFinished(ok): comando eseguito o errore. */
    fun listen(onAsk: (String) -> Unit, onNeedContacts: () -> Unit, onFinished: (Boolean) -> Unit) {
        busy = false; heard = ""; result = "In ascolto…"; active = true; level = 0f
        voice.listen(
            cbPartial = { heard = it },
            cbFinal = { alts -> active = false; handle(alts, onAsk, onNeedContacts, onFinished) },
            cbError = { active = false; result = it; onFinished(false) },
            cbLevel = { level = maxOf(level * 0.85f, ((it + 2f) / 12f).coerceIn(0f, 1f)) })
    }

    fun handle(alts: List<String>, onAsk: (String) -> Unit, onNeedContacts: () -> Unit, onFinished: (Boolean) -> Unit) {
        busy = true
        val (text, cmd) = Parser.parseBest(alts)
        heard = text
        if (cmd is Cmd.AskAi || (cmd == Cmd.Unknown && store.hasAnyAiKey())) { onAsk((cmd as? Cmd.AskAi)?.question ?: text); return }
        if (actions.needsContacts(cmd) && !actions.contacts.hasPermission()) {
            pending = alts to cmd; result = "Mi serve il permesso Contatti"; onNeedContacts(); return
        }
        result = "…"
        actions.run(cmd) { msg -> result = msg; if (store.speakReplies) voice.say(msg); onFinished(cmd != Cmd.Unknown) }
    }

    fun resumePending(granted: Boolean, onFinished: (Boolean) -> Unit) {
        val p = pending ?: return; pending = null
        if (!granted) { result = "Senza permesso Contatti non posso trovare i nomi"; onFinished(false); return }
        actions.run(p.second) { msg -> result = msg; if (store.speakReplies) voice.say(msg); onFinished(true) }
    }

    fun release() = voice.release()
}
