package it.dispensa.needlevoice

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** Apre app sulla TV/Fire Stick via DIAL (Netflix, YouTube) sulla stessa rete Wi-Fi, senza accoppiamento. */
object Tv {
    private fun discover(ctx: Context): List<String> {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wm.createMulticastLock("needle-dial").apply { setReferenceCounted(false); acquire() }
        val locs = LinkedHashSet<String>()
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 1200
                val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n").toByteArray()
                s.send(DatagramPacket(msg, msg.size, InetAddress.getByName("239.255.255.250"), 1900))
                val end = System.currentTimeMillis() + 2600
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    try {
                        val p = DatagramPacket(buf, buf.size); s.receive(p)
                        String(p.data, 0, p.length).lines().firstOrNull { it.startsWith("LOCATION:", true) }?.substringAfter(':')?.trim()?.let { locs.add(it) }
                    } catch (_: java.net.SocketTimeoutException) { }
                }
            }
        } finally { lock.release() }
        return locs.mapNotNull { loc ->
            try { val c = URL(loc).openConnection() as HttpURLConnection; c.connectTimeout = 3000; c.readTimeout = 3000; c.responseCode
                c.getHeaderField("Application-URL")?.trimEnd('/').also { c.disconnect() } } catch (_: Exception) { null } }
    }

    fun launch(ctx: Context, app: String): String {
        val name = when { Fuzzy.sim(app, "netflix") > 0.7 -> "Netflix"; Fuzzy.sim(app, "youtube") > 0.7 -> "YouTube"; else -> return "Dalla TV posso aprire solo Netflix e YouTube" }
        val urls = discover(ctx)
        if (urls.isEmpty()) return "Non trovo la TV in rete: accendila e collegala allo stesso Wi-Fi"
        for (u in urls) {
            try { val c = URL("$u/$name").openConnection() as HttpURLConnection; c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(0); c.connectTimeout = 4000; c.readTimeout = 6000
                c.setRequestProperty("Content-Type", "text/plain; charset=utf-8"); c.outputStream.close()
                if (c.responseCode in 200..299) return "Apro $name sulla TV" } catch (_: Exception) { }
        }
        return "La TV non ha risposto"
    }
}
