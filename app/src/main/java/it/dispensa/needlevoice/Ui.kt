package it.dispensa.needlevoice

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*

object C {
    const val BG = 0xFF0B0D0A.toInt(); const val CARD = 0xFF161914.toInt(); const val CARD2 = 0xFF1F231C.toInt()
    const val LIME = 0xFFC7FF4A.toInt(); const val TXT = 0xFFF3F4EF.toInt(); const val DIM = 0xFF9AA091.toInt(); const val RED = 0xFFFF6B5E.toInt()
}

fun View.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

fun rounded(color: Int, r: Float, ctx: Context, stroke: Int? = null) = GradientDrawable().apply {
    setColor(color); cornerRadius = r * ctx.resources.displayMetrics.density
    if (stroke != null) setStroke(ctx.dpi(1), stroke)
}

fun Context.text(s: String, size: Float = 15f, color: Int = C.TXT, bold: Boolean = false) = TextView(this).apply {
    text = s; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
}

fun Context.label(s: String) = text(s.uppercase(), 11f, C.LIME, true).apply { letterSpacing = .14f; setPadding(dpi(4), dpi(22), 0, dpi(8)) }

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; background = rounded(C.CARD, 20f, context); setPadding(dpi(16), dpi(14), dpi(16), dpi(14))
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(10) }
}

fun Context.button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
    text = label; isAllCaps = false; textSize = 15f; stateListAnimator = null
    setTextColor(if (primary) C.BG else C.TXT)
    background = rounded(if (primary) C.LIME else C.CARD2, 16f, context)
    setPadding(dpi(16), 0, dpi(16), 0); minHeight = dpi(48); minimumHeight = dpi(48)
    layoutParams = LinearLayout.LayoutParams(-1, dpi(50)).apply { bottomMargin = dpi(8) }
    setOnClickListener { onClick() }
}

/** Campo con select-all al focus, Invio per confermare. */
fun Context.field(hint: String, value: String = "", secret: Boolean = false, onEnter: (() -> Unit)? = null) = EditText(this).apply {
    this.hint = hint; setText(value); setTextColor(C.TXT); setHintTextColor(C.DIM); textSize = 15f
    background = rounded(C.CARD2, 14f, context); setPadding(dpi(14), dpi(12), dpi(14), dpi(12))
    setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_DONE
    inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
    setOnFocusChangeListener { _, f -> if (f) post { selectAll() } }
    setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_DONE) { onEnter?.invoke(); true } else false }
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(8) }
}

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun lp(w: Int = -1, h: Int = -2) = LinearLayout.LayoutParams(w, h)
