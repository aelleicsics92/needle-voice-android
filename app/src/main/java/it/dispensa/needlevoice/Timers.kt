package it.dispensa.needlevoice

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object Timers {
    const val CH = "needle_timer_v2"
    const val ACT_FIRE = "it.dispensa.needlevoice.TIMER_FIRE"
    const val ACT_STOP = "it.dispensa.needlevoice.TIMER_STOP"

    private fun pi(ctx: Context, id: Long, act: String) = PendingIntent.getBroadcast(ctx, id.toInt(), Intent(ctx, TimerReceiver::class.java).setAction(act).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) != null) return
        val ch = NotificationChannel(CH, "Timer", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 500, 300, 500)
        }
        nm.createNotificationChannel(ch)
    }

    fun set(ctx: Context, store: Store, ms: Long): Long {
        channel(ctx)
        val id = System.currentTimeMillis() % 1_000_000_000L; val at = System.currentTimeMillis() + ms
        val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        ctx.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi(ctx, id, ACT_FIRE))
        store.addTimer(id, at); return id
    }

    fun cancelAll(ctx: Context, store: Store): Int {
        val am = ctx.getSystemService(AlarmManager::class.java); val nm = ctx.getSystemService(NotificationManager::class.java)
        val l = store.timers(); l.forEach { am.cancel(pi(ctx, it.first, ACT_FIRE)); nm.cancel(it.first.toInt()) }
        nm.cancelAll(); store.clearTimers(); return l.size
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val id = i.getLongExtra("id", 0)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (i.action == Timers.ACT_STOP) { nm.cancel(id.toInt()); return }
        Store(ctx).removeTimer(id); Timers.channel(ctx)
        val stop = PendingIntent.getBroadcast(ctx, id.toInt() + 1, Intent(ctx, TimerReceiver::class.java).setAction(Timers.ACT_STOP).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, Timers.CH).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Timer terminato")
            .setContentText("Tocca per fermare").setContentIntent(open).setCategory(Notification.CATEGORY_ALARM).setTimeoutAfter(60_000)
            .addAction(Notification.Action.Builder(null, "Ferma", stop).build()).setAutoCancel(true).build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(id.toInt(), n)
    }
}
