package it.dispensa.needlevoice

import android.Manifest
import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** App completa (pressione lunga sull'icona > "Apri Needle"). */
class MainActivity : ComponentActivity() {
    private lateinit var asst: Assistant
    private var tab by mutableIntStateOf(0)
    private var aiQuestion by mutableStateOf("")

    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        asst.resumePending(r.values.all { it })
        tab = tab  // ridisegna
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        asst = Assistant(this)
        intent.getStringExtra("ask")?.let { aiQuestion = it; tab = 3 }
        if (intent.getBooleanExtra("brain", false)) tab = 3
        setContent {
            Aurora {
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> VoiceScreen(asst, { p -> need(p) }, { p -> need(p) })
                            1 -> NotesScreen(asst)
                            2 -> HomeScreen(asst) { tab = 4 }
                            3 -> BrainScreen(asst, aiQuestion) { tab = 4 }
                            else -> SettingsScreen(asst, { appSettings() }, { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
                        }
                        NavBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); i.getStringExtra("ask")?.let { aiQuestion = it; tab = 3 }; if (i.getBooleanExtra("brain", false)) tab = 3 }

    private fun appSettings() = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    private fun need(p: String) {
        if (p == "ACCESS") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return }
        val l = mutableListOf(p)
        if (p == Manifest.permission.READ_CONTACTS) { l.add(Manifest.permission.RECORD_AUDIO); if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS) }
        askPerms.launch(l.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }.toTypedArray().ifEmpty { arrayOf(p) })
    }

    override fun onDestroy() { asst.release(); super.onDestroy() }
}

@Composable
fun NavBar(sel: Int, onSel: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf("Voce" to Ic.Mic, "Note" to Ic.Note, "Casa" to Ic.Home, "Cervello" to Ic.Spark, "Opzioni" to Ic.Gear)
    Row(modifier.padding(horizontal = 18.dp, vertical = 14.dp).fillMaxWidth().glass(32.dp, 0.10f).padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        items.forEachIndexed { i, (label, ic) ->
            val on = i == sel
            Column(Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).then(if (on) Modifier.glass(26.dp, 0.14f) else Modifier).clickable { onSel(i) }.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                NIcon(ic, if (on) Color.White else P.Dim, 24.dp)
                Text(label, color = if (on) Color.White else P.Dim, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
