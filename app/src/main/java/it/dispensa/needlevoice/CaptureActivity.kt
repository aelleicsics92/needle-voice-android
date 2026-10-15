package it.dispensa.needlevoice

import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat

/** Scatto istantaneo: apre la camera, aspetta ~0,9s per l'esposizione, scatta, salva, chiude. */
class CaptureActivity : ComponentActivity() {
    private val h = Handler(Looper.getMainLooper())
    private var done = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> if (ok) go() else finishAndRemoveTask() }.launch(android.Manifest.permission.CAMERA)
        } else go()
    }

    private fun go() {
        val front = intent.getBooleanExtra("front", false)
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            try {
                val prov = fut.get()
                val cap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                prov.unbindAll()
                prov.bindToLifecycle(this, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, cap)
                h.postDelayed({ shoot(cap, prov) }, 900)
            } catch (e: Exception) { finishAndRemoveTask() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun shoot(cap: ImageCapture, prov: ProcessCameraProvider) {
        if (done) return; done = true
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Needle_" + System.currentTimeMillis())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Needle")
        }
        val opts = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv).build()
        try { MediaActionSound().play(MediaActionSound.SHUTTER_CLICK) } catch (_: Exception) { }
        cap.takePicture(opts, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                setResult(RESULT_OK, android.content.Intent().setData(r.savedUri))
                prov.unbindAll(); finishAndRemoveTask()
            }
            override fun onError(e: ImageCaptureException) { prov.unbindAll(); finishAndRemoveTask() }
        })
    }
}
