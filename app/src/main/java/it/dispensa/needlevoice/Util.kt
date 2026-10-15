package it.dispensa.needlevoice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Media {
    fun newFile(ctx: Context): File = File(File(ctx.cacheDir, "photos").apply { mkdirs() }, "p${System.nanoTime()}.jpg")
    fun uriFor(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    /** Copia un'immagine scelta dalla galleria nella cache dell'app, così si può condividere con altre app. */
    fun copyToCache(ctx: Context, u: Uri): Uri? = try {
        val f = newFile(ctx)
        ctx.contentResolver.openInputStream(u)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        uriFor(ctx, f)
    } catch (_: Exception) { null }

    fun decode(ctx: Context, u: Uri, max: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (o.outWidth / s > max || o.outHeight / s > max) s *= 2
        ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    } catch (_: Exception) { null }
}
