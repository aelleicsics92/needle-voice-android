package it.dispensa.needlevoice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/** Funzioni del telefono: torcia, volume, meteo parlato, agenda, parcheggio, email. */
class Phone(private val ctx: Context) {
    private fun has(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun torch(on: Boolean): String {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "Questo telefono non ha il flash"
        cm.setTorchMode(id, on); return if (on) "Torcia accesa" else "Torcia spenta"
    }

    fun volume(action: String, level: Int?): String {
        val am = ctx.getSystemService(AudioManager::class.java); val st = AudioManager.STREAM_MUSIC; val max = am.getStreamMaxVolume(st)
        when {
            level != null -> am.setStreamVolume(st, (max * level.coerceIn(0, 100) / 100), AudioManager.FLAG_SHOW_UI)
            action == "up" -> am.adjustStreamVolume(st, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            action == "down" -> am.adjustStreamVolume(st, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            action == "mute" -> am.adjustStreamVolume(st, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            action == "unmute" -> am.adjustStreamVolume(st, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
        }
        return "Volume ${am.getStreamVolume(st) * 100 / max}%"
    }

    private fun get(url: String) = URL(url).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }.getInputStream().bufferedReader().use { it.readText() }

    private val wmo = mapOf(0 to "sereno", 1 to "prevalentemente sereno", 2 to "parzialmente nuvoloso", 3 to "coperto", 45 to "nebbia", 48 to "nebbia", 51 to "pioggerella", 53 to "pioggerella", 55 to "pioggerella",
        61 to "pioggia debole", 63 to "pioggia", 65 to "pioggia forte", 71 to "neve debole", 73 to "neve", 75 to "neve forte", 80 to "rovesci", 81 to "rovesci", 82 to "rovesci forti", 95 to "temporale", 96 to "temporale con grandine", 99 to "temporale con grandine")

    /** Meteo reale (Open-Meteo, senza chiave), pronto da leggere ad alta voce. */
    fun weather(cityIn: String, day: Int = 0): String {
        val city = cityIn.ifBlank { "Bracciano" }
        val g = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(city, "UTF-8")}&count=1&language=it")).optJSONArray("results") ?: return "Non trovo la città $city"
        val c = g.getJSONObject(0)
        val f = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=${c.getDouble("latitude")}&longitude=${c.getDouble("longitude")}&current=temperature_2m,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=3"))
        val cur = f.getJSONObject("current"); val d = f.getJSONObject("daily")
        val name = c.getString("name")
        val tmax = d.getJSONArray("temperature_2m_max").getDouble(day).toInt(); val tmin = d.getJSONArray("temperature_2m_min").getDouble(day).toInt()
        val rain = d.getJSONArray("precipitation_probability_max").optInt(day); val cond = wmo[d.getJSONArray("weather_code").getInt(day)] ?: ""
        return if (day == 0) "A $name ci sono ${cur.getDouble("temperature_2m").toInt()} gradi, ${wmo[cur.getInt("weather_code")] ?: ""}. Oggi massima $tmax, minima $tmin, probabilità di pioggia $rain per cento."
        else "${if (day == 1) "Domani" else "Dopodomani"} a $name: $cond, massima $tmax, minima $tmin, pioggia $rain per cento."
    }

    /** Eventi del calendario di oggi/domani. */
    fun agenda(day: Int): String {
        if (!has(Manifest.permission.READ_CALENDAR)) return "Serve il permesso Calendario"
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, day); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        val start = if (day == 0) System.currentTimeMillis() else cal.timeInMillis; cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); val end = cal.timeInMillis
        val u = CalendarContract.Instances.CONTENT_URI.buildUpon().also { android.content.ContentUris.appendId(it, start); android.content.ContentUris.appendId(it, end) }.build()
        val out = ArrayList<String>(); val fmt = SimpleDateFormat("H:mm", Locale.ITALIAN)
        ctx.contentResolver.query(u, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY), null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
            while (c.moveToNext() && out.size < 8) out.add(if (c.getInt(2) == 1) c.getString(0) else "alle ${fmt.format(Date(c.getLong(1)))} ${c.getString(0)}")
        }
        val w = if (day == 0) "oggi" else "domani"
        return if (out.isEmpty()) "Non hai impegni $w" else "Hai ${out.size} impegni $w: " + out.joinToString(", ")
    }

    fun saveParking(): String {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) return "NEEDLOC"
        val lm = ctx.getSystemService(LocationManager::class.java)
        val l = lm.getProviders(true).mapNotNull { try { lm.getLastKnownLocation(it) } catch (_: SecurityException) { null } }.maxByOrNull { it.time } ?: return "Non riesco a trovare la posizione, apri prima Maps"
        Store(ctx).parking = "${l.latitude},${l.longitude}"; return "Ricordo dove hai parcheggiato"
    }

    /** Email del contatto (nome anche storpiato). */
    fun emailOf(spoken: String): String? {
        val t = spoken.trim()
        if ("@" in t) return t
        val d = t.lowercase().replace(" chiocciola ", "@").replace(" at ", "@").replace(" punto ", ".").replace(" ", "")
        if (Regex("^[^@]+@[^@]+\\.[a-z]{2,}$").matches(d)) return d
        if (!has(Manifest.permission.READ_CONTACTS)) return null
        val l = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME, ContactsContract.CommonDataKinds.Email.ADDRESS), null, null, null)?.use { c ->
            while (c.moveToNext()) l.add(c.getString(0).orEmpty() to c.getString(1).orEmpty()) }
        return Fuzzy.best(t, l, 0.62) { it.first }?.second
    }
}
