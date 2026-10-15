package it.dispensa.needlevoice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Lanciata dall'icona: ascolta subito, esegue, si chiude. */
class ListenActivity : Activity() {
    private lateinit var store: Store
    private lateinit var voice: Voice
    private lateinit var actions: Actions
    private lateinit var orb: OrbView
    private lateinit var heard: TextView
    private lateinit var result: TextView
    private val h = Handler(Looper.getMainLooper())
    private var busy = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this); voice = Voice(this); actions = Actions(this, store)
        val root = FrameLayout(this).apply { setBackgroundColor(0x99000000.toInt()); setOnClickListener { finish() } }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(C.BG, 28f, context); setPadding(dpi(20), dpi(12), dpi(20), dpi(20)); isClickable = true
        }
        orb = OrbView(this).also { it.setOnClickListener { startListen() } }
        heard = text("", 20f, C.TXT, true).apply { gravity = Gravity.CENTER; minLines = 2 }
        result = text("In ascolto…", 15f, C.DIM).apply { gravity = Gravity.CENTER; setPadding(0, dpi(6), 0, dpi(10)) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        row.addView(chip("⚙  Apri app") { startActivity(Intent(this@ListenActivity, MainActivity::class.java)); finish() })
        row.addView(chip("✕  Chiudi") { finish() })
        sheet.addView(orb, LinearLayout.LayoutParams(-1, dpi(210)))
        sheet.addView(heard, lp()); sheet.addView(result, lp()); sheet.addView(row, lp())
        root.addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dpi(10), 0, dpi(10), dpi(10)) })
        setContentView(root)
    }

    private fun chip(l: String, f: () -> Unit) = text(l, 14f, C.TXT).apply {
        background = rounded(C.CARD2, 20f, context); setPadding(dpi(18), dpi(10), dpi(18), dpi(10)); setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(6); marginEnd = dpi(6) }
    }

    override fun onResume() { super.onResume(); if (!busy) ensurePermsThenListen() }
    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); busy = false; ensurePermsThenListen() }

    private fun ensurePermsThenListen() {
        val need = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty() && !store.askedPerms) { store.askedPerms = true; requestPermissions(need.toTypedArray(), 7); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListen()
        else result.text = "Serve il permesso microfono (Impostazioni > App > Needle)"
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(c, p, g); ensurePermsThenListen() }

    private fun startListen() {
        busy = false; heard.text = ""; result.text = "In ascolto…"; orb.active = true
        voice.listen(
            cbPartial = { heard.text = "«$it»" },
            cbFinal = { handle(it) },
            cbError = { orb.active = false; result.text = it; h.postDelayed({ if (!busy) finish() }, 1800) },
            cbLevel = { orb.pushLevel(it) })
    }

    private fun handle(text: String) {
        busy = true; orb.active = false; heard.text = "«$text»"
        val cmd = Parser.parse(text)
        if (cmd is Cmd.AskAi || cmd == Cmd.Unknown && store.hasAnyAiKey()) {
            startActivity(Intent(this, MainActivity::class.java).putExtra("ask", (cmd as? Cmd.AskAi)?.question ?: text)); finish(); return
        }
        result.text = "…"
        actions.run(cmd) { msg ->
            result.text = msg
            if (store.speakReplies) voice.say(msg)
            h.postDelayed({ finish() }, if (cmd is Cmd.Unknown) 2500 else 1800)
        }
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); voice.release(); super.onDestroy() }
}
