package it.dispensa.needlevoice

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.widget.RemoteViews

class BarWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_bar)
            fun pi(code: Int, mode: String?): PendingIntent {
                val i = Intent(ctx, ListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (mode != null) i.putExtra("mode", mode)
                return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            v.setOnClickPendingIntent(R.id.w_bar, pi(1, "type"))
            v.setOnClickPendingIntent(R.id.w_mic, pi(2, null))
            m.updateAppWidget(id, v)
        }
    }
}

class ListenTile : TileService() {
    override fun onStartListening() { qsTile?.let { it.label = "Needle"; it.updateTile() } }
    override fun onClick() {
        val i = Intent(this, ListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (android.os.Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 3, i, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}
