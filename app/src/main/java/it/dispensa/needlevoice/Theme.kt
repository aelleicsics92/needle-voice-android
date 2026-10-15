package it.dispensa.needlevoice

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object P {
    val Ink = Color(0xFF07070C); val Violet = Color(0xFF8B5CFF); val Pink = Color(0xFFFF5CAA); val Peach = Color(0xFFFFB36B)
    val Mint = Color(0xFF5CF2C8); val Dim = Color(0xFF9A9AB5); val Txt = Color(0xFFF4F4FA); val Red = Color(0xFFFF6B7A)
    val grad = Brush.linearGradient(listOf(Violet, Pink, Peach))
}

fun Modifier.glass(r: Dp = 24.dp, a: Float = 0.06f): Modifier {
    val s = RoundedCornerShape(r)
    return this.clip(s).background(Color.White.copy(alpha = a)).border(1.dp, Color.White.copy(alpha = 0.10f), s)
}

/** Sfondo "aurora" con aloni che si muovono lentamente. */
@Composable
fun Aurora(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxSize().background(P.Ink).drawBehind {
        val w = size.width; val h = size.height
        val c1 = Offset(w * (0.1f + 0.35f * a), h * 0.10f); val c2 = Offset(w * (0.95f - 0.3f * a), h * 0.85f)
        drawCircle(Brush.radialGradient(listOf(P.Violet.copy(alpha = 0.40f), Color.Transparent), c1, w * 0.95f), w * 0.95f, c1)
        drawCircle(Brush.radialGradient(listOf(P.Pink.copy(alpha = 0.26f), Color.Transparent), c2, w * 0.9f), w * 0.9f, c2)
    }, content = content)
}

/** Visualizzatore vocale: anello di barre che pulsano con la voce, sfumato viola-rosa-pesca. */
@Composable
fun Orb(level: Float, active: Boolean, diameter: Dp, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 9000 else 26000, easing = LinearEasing)), label = "rot")
    val phase by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(if (active) 1500 else 5200, easing = LinearEasing)), label = "ph")
    val breathe by t.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    val lv by animateFloatAsState(if (active) level else 0f, tween(90), label = "lv")
    Canvas(Modifier.size(diameter).clickable(remember { MutableInteractionSource() }, null) { onClick() }) {
        val c = center; val base = size.minDimension * 0.19f * breathe
        drawCircle(Brush.radialGradient(listOf(P.Violet.copy(alpha = 0.30f + lv * 0.25f), P.Pink.copy(alpha = 0.10f), Color.Transparent), c, base * 3.6f), base * 3.6f, c)
        val n = 60
        for (i in 0 until n) {
            val f = i.toFloat() / n; val a = f * 6.2832f + rot * 0.01745f
            val w = (Math.sin((a * 3f + phase).toDouble()) * 0.5 + 0.5).toFloat() * 0.6f + (Math.sin((a * 7f - phase * 1.3f).toDouble()) * 0.5 + 0.5).toFloat() * 0.4f
            val amp = if (active) 0.22f + lv * 1.25f else 0.10f
            val len = base * (0.10f + 0.9f * w * amp)
            val r0 = base * 1.08f; val r1 = r0 + len
            val col = when { f < 0.33f -> lerp(P.Violet, P.Pink, f / 0.33f); f < 0.66f -> lerp(P.Pink, P.Peach, (f - 0.33f) / 0.33f); else -> lerp(P.Peach, P.Violet, (f - 0.66f) / 0.34f) }
            drawLine(col, Offset(c.x + Math.cos(a.toDouble()).toFloat() * r0, c.y + Math.sin(a.toDouble()).toFloat() * r0), Offset(c.x + Math.cos(a.toDouble()).toFloat() * r1, c.y + Math.sin(a.toDouble()).toFloat() * r1), base * 0.085f, StrokeCap.Round)
        }
        drawCircle(Color.White.copy(alpha = 0.16f), base * 0.98f, c, style = Stroke(width = 2f))
        drawCircle(Brush.radialGradient(listOf(P.Pink.copy(alpha = 0.22f + lv * 0.2f), Color.Transparent), c, base), base * 0.96f, c)
    }
}

@Composable
fun GradText(s: String, size: Int, weight: FontWeight = FontWeight.Light, modifier: Modifier = Modifier) =
    Text(s, modifier, style = TextStyle(brush = P.grad, fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.08f).sp))

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Box(modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(P.grad).clickable { onClick() }, Alignment.Center) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }

@Composable
fun GhostButton(label: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) =
    Box(modifier.fillMaxWidth().height(50.dp).glass(25.dp, 0.07f).clickable { onClick() }, Alignment.Center) {
        Text(label, color = if (danger) P.Red else P.Txt, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }

@Composable
fun Chip(label: String, selected: Boolean = false, onClick: () -> Unit) =
    Box(Modifier.clip(RoundedCornerShape(20.dp)).then(if (selected) Modifier.background(P.grad) else Modifier.glass(20.dp, 0.07f)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, color = P.Txt, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }

@Composable
fun SectionLabel(s: String) = Text(s.uppercase(), color = P.Dim, fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))

/** Campo di testo: seleziona tutto al focus, Invio conferma. */
@Composable
fun GField(value: String, onChange: (String) -> Unit, hint: String, secret: Boolean = false, onEnter: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    var tf by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) { if (value != tf.text) tf = TextFieldValue(value) }
    BasicTextField(
        value = tf, onValueChange = { tf = it; onChange(it.text) }, singleLine = true,
        textStyle = TextStyle(color = P.Txt, fontSize = 16.sp), cursorBrush = SolidColor(P.Pink),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onEnter?.invoke() }),
        modifier = modifier.fillMaxWidth().glass(18.dp, 0.07f).onFocusChanged { if (it.isFocused) tf = tf.copy(selection = TextRange(0, tf.text.length)) }.padding(horizontal = 18.dp, vertical = 15.dp),
        decorationBox = { inner -> Box { if (tf.text.isEmpty()) Text(hint, color = P.Dim, fontSize = 16.sp); inner() } })
}

enum class Ic { Mic, Note, Home, Spark, Gear }

@Composable
fun NIcon(k: Ic, color: Color, size: Dp = 24.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val st = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (k) {
        Ic.Mic -> {
            drawRoundRect(color, Offset(w * .36f, w * .12f), Size(w * .28f, w * .46f), androidx.compose.ui.geometry.CornerRadius(w * .14f), style = st)
            drawArc(color, 0f, 180f, false, Offset(w * .24f, w * .30f), Size(w * .52f, w * .46f), style = st)
            drawLine(color, Offset(w * .5f, w * .76f), Offset(w * .5f, w * .9f), st.width, StrokeCap.Round)
        }
        Ic.Note -> {
            drawRoundRect(color, Offset(w * .2f, w * .14f), Size(w * .6f, w * .72f), androidx.compose.ui.geometry.CornerRadius(w * .1f), style = st)
            drawLine(color, Offset(w * .33f, w * .38f), Offset(w * .67f, w * .38f), st.width, StrokeCap.Round)
            drawLine(color, Offset(w * .33f, w * .56f), Offset(w * .58f, w * .56f), st.width, StrokeCap.Round)
        }
        Ic.Home -> {
            val p = Path().apply { moveTo(w * .16f, w * .48f); lineTo(w * .5f, w * .16f); lineTo(w * .84f, w * .48f); moveTo(w * .26f, w * .42f); lineTo(w * .26f, w * .84f); lineTo(w * .74f, w * .84f); lineTo(w * .74f, w * .42f) }
            drawPath(p, color, style = st)
        }
        Ic.Spark -> {
            val p = Path().apply { moveTo(w * .5f, w * .1f); quadraticBezierTo(w * .54f, w * .46f, w * .9f, w * .5f); quadraticBezierTo(w * .54f, w * .54f, w * .5f, w * .9f); quadraticBezierTo(w * .46f, w * .54f, w * .1f, w * .5f); quadraticBezierTo(w * .46f, w * .46f, w * .5f, w * .1f) }
            drawPath(p, color, style = st)
        }
        Ic.Gear -> {
            drawCircle(color, w * .13f, center, style = st)
            for (i in 0 until 8) { val a = Math.toRadians(i * 45.0); val c = center
                drawLine(color, Offset(c.x + (w * .27f * Math.cos(a)).toFloat(), c.y + (w * .27f * Math.sin(a)).toFloat()), Offset(c.x + (w * .39f * Math.cos(a)).toFloat(), c.y + (w * .39f * Math.sin(a)).toFloat()), st.width, StrokeCap.Round) }
            drawCircle(color, w * .27f, center, style = st)
        }
    }
}
