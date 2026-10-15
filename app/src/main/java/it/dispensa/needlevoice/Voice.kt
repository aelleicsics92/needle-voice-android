package it.dispensa.needlevoice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class Voice(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val h = Handler(Looper.getMainLooper())
    private var timeout: Runnable? = null
    var listening = false; private set

    init {
        tts = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                t.language = Locale.ITALIAN
                // voce italiana migliore disponibile (qualità più alta, preferendo le "neural")
                val best = t.voices?.filter { it.locale.language == "it" && !it.features.contains("notInstalled") }
                    ?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }.thenByDescending { it.name.contains("neural", true) || it.name.contains("-x-iti", true) || it.name.contains("-x-itd", true) })?.firstOrNull()
                if (best != null) t.voice = best
                t.setSpeechRate(1.0f); t.setPitch(1.0f)
            }
        }
    }

    /** cbFinal riceve tutte le alternative del riconoscimento (la migliore per prima). */
    fun listen(cbPartial: (String) -> Unit, cbFinal: (List<String>) -> Unit, cbError: (String) -> Unit, cbLevel: (Float) -> Unit, maxMs: Long = 9000) {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { cbError("Riconoscimento vocale non disponibile"); return }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx); rec = r; listening = true
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() { timeout?.let { h.removeCallbacks(it) } }
            override fun onRmsChanged(v: Float) = cbLevel(v)
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) { val was = listening; stop(); if (was) cbError(if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "Non ho sentito nulla" else "Errore microfono ($e)") }
            override fun onResults(b: Bundle?) {
                val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                stop(); if (l.isEmpty()) cbError("Non ho sentito nulla") else cbFinal(l)
            }
            override fun onPartialResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(cbPartial) }
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 6)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        })
        timeout = Runnable { if (listening) { stop(); cbError("Nessun comando") } }.also { h.postDelayed(it, maxMs) }
    }

    fun stop() {
        listening = false; timeout?.let { h.removeCallbacks(it) }; timeout = null
        try { rec?.cancel(); rec?.destroy() } catch (_: Exception) {}
        rec = null
    }

    fun say(s: String) { if (s.isNotBlank()) tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "nv") }
    fun release() { stop(); tts?.shutdown(); tts = null }
}
