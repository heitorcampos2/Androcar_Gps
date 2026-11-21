package com.heitorcampos.androcargps.speedo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.heitorcampos.androcargps.Prefs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Velocímetro desenhado à mão (sem imagens), em seis modelos:
 *  - digital:    bolinha com o número grande (tipo Waze)
 *  - anel:       número grande + anel que enche conforme a velocidade
 *  - analógico:  mostrador com marcações, ponteiro e número embaixo
 *  - esportivo:  segmentos tipo conta-giros de corrida, acendendo com a velocidade
 *  - neon:       anel e número com brilho neon
 *  - cromado:    painel clássico de carro, aro cromado e visor digital
 * A cor de destaque ([accent]) vale para todos os modelos.
 */
class SpeedometerView(context: Context) : View(context) {

    var style: String = Prefs.STYLE_DIGITAL
        set(v) { field = v; invalidate() }
    var theme: String = Prefs.THEME_LIGHT
        set(v) { field = v; invalidate() }
    var maxKmh: Int = 160
        set(v) { field = v.coerceAtLeast(40); invalidate() }
    var accent: Int = Prefs.ACCENT_DEFAULT
        set(v) { field = v; invalidate() }

    /** null = sem sinal ("--"). */
    private var target: Float? = null
    private var shown = 0f
    private var animator: ValueAnimator? = null

    fun setSpeed(kmh: Float?) {
        val old = target
        target = kmh
        if (kmh == null) {
            animator?.cancel()
            invalidate()
            return
        }
        if (old == null) {
            shown = kmh
            invalidate()
            return
        }
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, kmh).apply {
            duration = 350
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    // ---------------------------------------------------------------- cores

    private data class Palette(
        val bgIn: Int, val bgOut: Int, val ring: Int, val track: Int,
        val text: Int, val sub: Int, val accent: Int,
    )

    private fun shade(c: Int, f: Float): Int {
        // f > 1 clareia, f < 1 escurece
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        fun ch(v: Int) = if (f >= 1) (v + (255 - v) * (f - 1)).roundToInt() else (v * f).roundToInt()
        return Color.rgb(ch(r).coerceIn(0, 255), ch(g).coerceIn(0, 255), ch(b).coerceIn(0, 255))
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    private fun palette() = when (theme) {
        Prefs.THEME_DARK -> Palette(0xFF1E2633.toInt(), 0xFF0B0F17.toInt(), accent, 0xFF2E3848.toInt(), Color.WHITE, 0xFF9AA5B8.toInt(), accent)
        // "Colorido": fundo na cor escolhida
        Prefs.THEME_BLUE -> Palette(shade(accent, 1.25f), shade(accent, 0.55f), Color.WHITE, 0x55FFFFFF, Color.WHITE, 0xCCFFFFFF.toInt(), Color.WHITE)
        else -> Palette(0xFFFFFFFF.toInt(), 0xFFC9CDD3.toInt(), 0xFF2B2B2B.toInt(), 0xFFD5D8DD.toInt(), 0xFF111111.toInt(), 0xFF555B66.toInt(), accent)
    }

    private fun speedColor(v: Float, p: Palette): Int = when {
        v >= maxKmh * 0.85f -> 0xFFFF5A5A.toInt()
        v >= maxKmh * 0.65f -> 0xFFF5B341.toInt()
        else -> p.accent
    }

    // ---------------------------------------------------------------- pincéis

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val arc = RectF()

    override fun onDraw(canvas: Canvas) {
        val p = palette()
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f
        val speed = target
        val value = if (speed == null) 0f else shown
        val number = if (speed == null) "--" else value.roundToInt().toString()

        when (style) {
            Prefs.STYLE_NEON -> { drawNeon(canvas, p, cx, cy, r, value, number); return }
            Prefs.STYLE_CHROME -> { drawChrome(canvas, p, cx, cy, r, value, number); return }
        }

        drawDisk(canvas, p, cx, cy, r)
        when (style) {
            Prefs.STYLE_ANALOG -> drawAnalog(canvas, p, cx, cy, r, value, number)
            Prefs.STYLE_SPORT -> drawSport(canvas, p, cx, cy, r, value, number)
            Prefs.STYLE_RING -> {
                val w = size * 0.075f
                arc.set(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f)
                stroke.strokeWidth = w
                stroke.color = p.track
                canvas.drawArc(arc, 135f, 270f, false, stroke)
                stroke.color = speedColor(value, p)
                canvas.drawArc(arc, 135f, 270f * (value / maxKmh).coerceIn(0f, 1f), false, stroke)
                drawNumber(canvas, p, cx, cy, size, number, 0.36f)
            }
            else -> drawNumber(canvas, p, cx, cy, size, number, 0.42f)
        }
    }

    /** Disco de fundo com leve relevo e borda na cor do tema. */
    private fun drawDisk(canvas: Canvas, p: Palette, cx: Float, cy: Float, r: Float) {
        val size = r * 2
        fill.shader = RadialGradient(cx, cy - r * 0.3f, r * 1.3f, p.bgIn, p.bgOut, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 0.98f, fill)
        fill.shader = null
        stroke.strokeWidth = size * 0.035f
        stroke.color = p.ring
        canvas.drawCircle(cx, cy, r * 0.98f - stroke.strokeWidth / 2, stroke)
    }

    private fun drawNumber(canvas: Canvas, p: Palette, cx: Float, cy: Float, size: Float, number: String, scale: Float) {
        text.shader = null
        text.color = p.text
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textSize = size * scale * (if (number.length >= 3) 0.82f else 1f)
        canvas.drawText(number, cx, cy + text.textSize * 0.30f, text)
        text.typeface = Typeface.DEFAULT
        text.color = p.sub
        text.textSize = size * 0.11f
        canvas.drawText("km/h", cx, cy + size * 0.30f, text)
    }

    // ---------------------------------------------------------------- analógico

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, r: Float, ro: Float, tickColor: Int, labelColor: Int, labels: Boolean) {
        val size = r * 2
        val majorStep = if (maxKmh <= 200) 20 else 40
        val minorStep = majorStep / 2
        text.shader = null
        text.typeface = Typeface.DEFAULT_BOLD
        text.color = labelColor
        text.textSize = size * 0.075f
        var k = 0
        while (k <= maxKmh) {
            val ang = Math.toRadians((135f + 270f * k / maxKmh).toDouble())
            val major = k % majorStep == 0
            val len = if (major) r * 0.13f else r * 0.07f
            stroke.strokeWidth = if (major) size * 0.018f else size * 0.009f
            stroke.color = if (k >= maxKmh * 0.85f) 0xFFFF5A5A.toInt() else tickColor
            val c = cos(ang).toFloat()
            val s = sin(ang).toFloat()
            canvas.drawLine(cx + c * ro, cy + s * ro, cx + c * (ro - len), cy + s * (ro - len), stroke)
            if (labels && major && size >= 110) {
                val rl = ro - len - text.textSize * 0.95f
                canvas.drawText(k.toString(), cx + c * rl, cy + s * rl + text.textSize * 0.35f, text)
            }
            k += minorStep
        }
    }

    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, r: Float, value: Float, color: Int, capInner: Int) {
        val size = r * 2
        val ang = Math.toRadians((135f + 270f * (value / maxKmh).coerceIn(0f, 1f)).toDouble())
        stroke.color = color
        stroke.strokeWidth = size * 0.028f
        canvas.drawLine(cx, cy, cx + cos(ang).toFloat() * r * 0.70f, cy + sin(ang).toFloat() * r * 0.70f, stroke)
        fill.shader = null
        fill.color = color
        canvas.drawCircle(cx, cy, size * 0.055f, fill)
        fill.color = capInner
        canvas.drawCircle(cx, cy, size * 0.025f, fill)
    }

    private fun drawAnalog(canvas: Canvas, p: Palette, cx: Float, cy: Float, r: Float, value: Float, number: String) {
        val size = r * 2
        drawTicks(canvas, cx, cy, r, r * 0.86f, p.sub, p.text, labels = true)
        drawNeedle(canvas, cx, cy, r, value, p.accent, p.bgIn)
        text.color = p.text
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = size * 0.15f
        canvas.drawText(number, cx, cy + r * 0.62f, text)
        text.typeface = Typeface.DEFAULT
        text.color = p.sub
        text.textSize = size * 0.07f
        canvas.drawText("km/h", cx, cy + r * 0.80f, text)
    }

    // ---------------------------------------------------------------- esportivo

    private fun drawSport(canvas: Canvas, p: Palette, cx: Float, cy: Float, r: Float, value: Float, number: String) {
        val size = r * 2
        val segments = 28
        val gap = 2.2f
        val each = 270f / segments
        val lit = ((value / maxKmh).coerceIn(0f, 1f) * segments).roundToInt()
        arc.set(cx - r * 0.76f, cy - r * 0.76f, cx + r * 0.76f, cy + r * 0.76f)
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeWidth = size * 0.11f
        for (i in 0 until segments) {
            val frac = (i + 1f) / segments
            stroke.color = when {
                i >= lit -> p.track
                frac > 0.85f -> 0xFFFF3B3B.toInt()
                frac > 0.65f -> 0xFFF5B341.toInt()
                else -> p.accent
            }
            canvas.drawArc(arc, 135f + i * each + gap / 2, each - gap, false, stroke)
        }
        stroke.strokeCap = Paint.Cap.ROUND
        // Número em fonte larga, itálico de corrida
        text.shader = null
        text.color = p.text
        text.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        text.textSize = size * 0.30f * (if (number.length >= 3) 0.85f else 1f)
        canvas.drawText(number, cx, cy + text.textSize * 0.32f, text)
        text.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        text.color = p.accent
        text.textSize = size * 0.09f
        canvas.drawText("KM/H", cx, cy + size * 0.30f, text)
    }

    // ---------------------------------------------------------------- neon

    private fun drawNeon(canvas: Canvas, p: Palette, cx: Float, cy: Float, r: Float, value: Float, number: String) {
        val size = r * 2
        val glow = if (theme == Prefs.THEME_BLUE) Color.WHITE else accent
        // Fundo sempre escuro (neon só brilha no escuro); no tema colorido, puxa para a cor
        val dark = if (theme == Prefs.THEME_BLUE) shade(accent, 0.25f) else 0xFF06080D.toInt()
        fill.shader = RadialGradient(cx, cy, r, shade(dark, 1.15f), dark, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 0.98f, fill)
        fill.shader = null

        // Anel com brilho: várias passadas largas e transparentes + traço fino forte
        arc.set(cx - r * 0.80f, cy - r * 0.80f, cx + r * 0.80f, cy + r * 0.80f)
        val sweep = 270f * (value / maxKmh).coerceIn(0f, 1f)
        val w = size * 0.035f
        stroke.color = withAlpha(glow, 40)
        stroke.strokeWidth = w
        canvas.drawArc(arc, 135f, 270f, false, stroke)
        for ((mult, a) in listOf(5f to 25, 3.2f to 45, 2f to 90, 1f to 255)) {
            stroke.strokeWidth = w * mult
            stroke.color = withAlpha(glow, a)
            if (sweep > 0.5f) canvas.drawArc(arc, 135f, sweep, false, stroke)
        }
        // Borda externa fina
        stroke.strokeWidth = size * 0.012f
        stroke.color = withAlpha(glow, 120)
        canvas.drawCircle(cx, cy, r * 0.96f, stroke)

        // Número com brilho
        text.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        text.textSize = size * 0.34f * (if (number.length >= 3) 0.82f else 1f)
        val y = cy + text.textSize * 0.32f
        text.style = Paint.Style.STROKE
        for ((sw, a) in listOf(size * 0.05f to 35, size * 0.03f to 60, size * 0.015f to 110)) {
            text.strokeWidth = sw
            text.color = withAlpha(glow, a)
            canvas.drawText(number, cx, y, text)
        }
        text.style = Paint.Style.FILL
        text.color = Color.WHITE
        canvas.drawText(number, cx, y, text)
        text.typeface = Typeface.DEFAULT
        text.color = withAlpha(glow, 220)
        text.textSize = size * 0.10f
        canvas.drawText("km/h", cx, cy + size * 0.30f, text)
    }

    // ---------------------------------------------------------------- cromado

    private fun drawChrome(canvas: Canvas, p: Palette, cx: Float, cy: Float, r: Float, value: Float, number: String) {
        val size = r * 2
        val darkFace = theme == Prefs.THEME_DARK
        // Aro cromado (brilho metálico girando em volta)
        fill.shader = SweepGradient(
            cx, cy,
            intArrayOf(0xFFF5F5F5.toInt(), 0xFF8A8F98.toInt(), 0xFFFFFFFF.toInt(), 0xFF6B7078.toInt(), 0xFFE8E8E8.toInt(), 0xFF7E838B.toInt(), 0xFFF5F5F5.toInt()),
            null,
        )
        canvas.drawCircle(cx, cy, r * 0.98f, fill)
        // Mostrador
        val faceIn: Int
        val faceOut: Int
        when (theme) {
            Prefs.THEME_DARK -> { faceIn = 0xFF2A2F38.toInt(); faceOut = 0xFF0E1116.toInt() }
            Prefs.THEME_BLUE -> { faceIn = shade(accent, 1.2f); faceOut = shade(accent, 0.45f) }
            else -> { faceIn = 0xFFFFFFFF.toInt(); faceOut = 0xFFCDD1D6.toInt() }
        }
        fill.shader = RadialGradient(cx, cy - r * 0.35f, r * 1.1f, faceIn, faceOut, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 0.86f, fill)
        fill.shader = null
        val ink = if (theme == Prefs.THEME_LIGHT) 0xFF1A1A1A.toInt() else Color.WHITE
        drawTicks(canvas, cx, cy, r, r * 0.80f, ink, ink, labels = true)

        // Visor digital (tipo LCD) embaixo
        val vw = r * 0.62f
        val vh = r * 0.26f
        val top = cy + r * 0.38f
        arc.set(cx - vw / 2, top, cx + vw / 2, top + vh)
        fill.shader = LinearGradient(0f, top, 0f, top + vh, 0xFF0B0D10.toInt(), 0xFF1C2026.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(arc, vh * 0.25f, vh * 0.25f, fill)
        fill.shader = null
        text.typeface = Typeface.MONOSPACE
        text.color = if (theme == Prefs.THEME_BLUE) Color.WHITE else accent
        text.textSize = vh * 0.78f
        canvas.drawText(number, cx, top + vh * 0.77f, text)

        // Ponteiro com tampa cromada
        drawNeedle(canvas, cx, cy, r, value, if (theme == Prefs.THEME_BLUE) Color.WHITE else accent, 0xFFDADDE2.toInt())
        text.typeface = Typeface.DEFAULT_BOLD
        text.color = if (darkFace || theme == Prefs.THEME_BLUE) 0xCCFFFFFF.toInt() else 0xFF555B66.toInt()
        text.textSize = size * 0.06f
        canvas.drawText("km/h", cx, cy - r * 0.30f, text)
    }
}
