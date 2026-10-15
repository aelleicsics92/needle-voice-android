package it.dispensa.needlevoice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lanciata dall'icona, dal widget o dal tasto rapido: ascolta subito (o apre la barra di testo), esegue, si chiude. */
class ListenActivity : ComponentActivity() {
    private lateinit var asst: Assistant
    private val h = Handler(Looper.getMainLooper())
    private var typeMode by mutableStateOf(false)
    private var closeTask: Runnable? = null

    private val permAll = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (hasMic()) { if (!typeMode) listen() } else { asst.result = "Serve il permesso microfono" } }
    private val permOne = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> asst.resumePending(r.values.all { it }) }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        asst = Assistant(this)
        typeMode = intent.getStringExtra("mode") == "type"
        setContent {
            var typed by remember { mutableStateOf("") }
            val fr = remember { FocusRequester() }
            LaunchedEffect(typeMode) { if (typeMode) try { fr.requestFocus() } catch (_: Exception) { } }
            fun send() { if (typed.isNotBlank()) { cancelClose(); asst.typed(typed, { p -> permOne.launch(arrayOf(p)) }) { ok -> closeSoon(ok) }; typed = "" } }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)).clickable(remember { MutableInteractionSource() }, null) { finishAndRemoveTask() }, Alignment.BottomCenter) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp).glass(34.dp, 0.10f).background(P.Ink.copy(alpha = 0.92f)).clickable(remember { MutableInteractionSource() }, null) { }.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!typeMode) Orb(asst.level, asst.active, 210.dp) { cancelClose(); listen() }
                    Text(if (asst.active && asst.heard.isBlank()) "Ti ascolto…" else asst.heard, color = P.Txt, fontSize = 21.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, minLines = if (typeMode) 1 else 2, modifier = Modifier.fillMaxWidth())
                    Text(asst.result, color = if (asst.thinking) P.Pink else P.Dim, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { GField(typed, { typed = it }, "Scrivi un comando…", onEnter = { send() }, modifier = Modifier.focusRequester(fr)) }
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(27.dp)).background(P.grad).clickable { if (typed.isBlank()) { typeMode = false; cancelClose(); listen() } else send() }, Alignment.Center) { NIcon(if (typed.isBlank()) Ic.Mic else Ic.Spark, Color.White, 24.dp) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("Apri app", Modifier.weight(1f)) { startActivity(Intent(this@ListenActivity, MainActivity::class.java)); finishAndRemoveTask() }
                        GhostButton("Chiudi", Modifier.weight(1f)) { finishAndRemoveTask() }
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); if (!asst.busy && !typeMode) start() }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); typeMode = i.getStringExtra("mode") == "type"; asst.busy = false; if (!typeMode) start() }

    private fun start() {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty() && !asst.store.askedPerms) { asst.store.askedPerms = true; permAll.launch(missing.toTypedArray()); return }
        if (hasMic()) { listen() } else { asst.result = "Serve il permesso microfono (Impostazioni, App, Needle)" }
    }

    private fun listen() = asst.listen({ p -> permOne.launch(arrayOf(p)) }) { ok -> closeSoon(ok) }

    private fun cancelClose() { closeTask?.let { h.removeCallbacks(it) }; closeTask = null }
    private fun closeSoon(ok: Boolean) {
        cancelClose()
        val ms = (if (ok) 2200L else 2800L).coerceAtLeast(asst.result.length * 45L)
        closeTask = Runnable { finishAndRemoveTask() }.also { h.postDelayed(it, ms) }
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); asst.release(); super.onDestroy() }
}
