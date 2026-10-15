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
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lanciata dall'icona: ascolta subito, esegue, si chiude. */
class ListenActivity : ComponentActivity() {
    private lateinit var asst: Assistant
    private val h = Handler(Looper.getMainLooper())

    private val permAll = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (hasMic()) { listen() } else { asst.result = "Serve il permesso microfono" } }
    private val permContacts = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { asst.resumePending(asst.actions.contacts.hasPermission()) { ok -> closeSoon(ok) } }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        asst = Assistant(this)
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)).clickable(remember { MutableInteractionSource() }, null) { finish() }, Alignment.BottomCenter) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).glass(34.dp, 0.10f).background(P.Ink.copy(alpha = 0.92f)).clickable(remember { MutableInteractionSource() }, null) { }.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Orb(asst.level, asst.active, 230.dp) { listen() }
                    Text(if (asst.active && asst.heard.isBlank()) "Ti ascolto…" else asst.heard, color = P.Txt, fontSize = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Text(asst.result, color = P.Dim, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("Apri app", Modifier.weight(1f)) { startActivity(Intent(this@ListenActivity, MainActivity::class.java)); finish() }
                        GhostButton("Chiudi", Modifier.weight(1f)) { finish() }
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); if (!asst.busy) start() }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); asst.busy = false; start() }

    private fun start() {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty() && !asst.store.askedPerms) { asst.store.askedPerms = true; permAll.launch(missing.toTypedArray()); return }
        if (hasMic()) { listen() } else { asst.result = "Serve il permesso microfono (Impostazioni, App, Needle)" }
    }

    private fun listen() = asst.listen(
        onAsk = { q -> startActivity(Intent(this, MainActivity::class.java).putExtra("ask", q)); finish() },
        onNeedContacts = { permContacts.launch(arrayOf(Manifest.permission.READ_CONTACTS)) },
        onFinished = { ok -> closeSoon(ok) })

    private fun closeSoon(ok: Boolean) { h.postDelayed({ finish() }, if (ok) 2200 else 2600) }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); asst.release(); super.onDestroy() }
}
