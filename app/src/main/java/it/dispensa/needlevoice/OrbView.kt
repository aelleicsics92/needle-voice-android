package it.dispensa.needlevoice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator

/** Sfera animata: pulsa con il volume della voce. */
class OrbView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var level = 0f; private var phase = 0f
    var active = false; set(v) { field = v; invalidate() }
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply { duration = 2400; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; level *= 0.94f; invalidate() } }
    fun pushLevel(rms: Float) { level = maxOf(level, ((rms + 2f) / 12f).coerceIn(0f, 1f)) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val base = minOf(width, height) * 0.26f
        val pulse = if (active) 1f + level * 0.5f + 0.06f * Math.sin(phase * 6.283).toFloat() else 1f + 0.03f * Math.sin(phase * 6.283).toFloat()
        for (i in 3 downTo 1) {
            p.shader = null; p.color = C.LIME; p.alpha = (if (active) 26 else 12) / i
            cv.drawCircle(cx, cy, base * pulse * (1f + i * 0.35f), p)
        }
        p.shader = RadialGradient(cx - base * .3f, cy - base * .3f, base * 1.4f, intArrayOf(0xFFEFFF9E.toInt(), C.LIME, 0xFF7FB800.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        p.alpha = 255; cv.drawCircle(cx, cy, base * pulse, p)
    }
}
