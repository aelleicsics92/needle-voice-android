package it.dispensa.needlevoice

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { Store(this).lastCrash = (e.toString() + "\n" + e.stackTrace.take(8).joinToString("\n")) } catch (_: Throwable) { }
            old?.uncaughtException(t, e)
        }
    }
}
