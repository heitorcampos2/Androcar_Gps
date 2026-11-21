package com.heitorcampos.androcargps.speedo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.heitorcampos.androcargps.Prefs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Velocímetro desenhado à mão (sem imagens), em três estilos:
 *  - digital:   bolinha com o número grande (tipo Waze)
 *  - anel:      número grande + anel que enche conforme a velocidade
 *  - analógico: mostrador com marcações, ponteiro e número pequeno embaixo
 */
class SpeedometerView(context: Context) : View(context) {

    var style: String = Prefs.STYLE_DIGITAL
        set(v) { field = v; invalidate() }
    var theme: String = Prefs.THEME_LIGHT
        set(v) { field = v; invalidate() }
    var maxKmh: Int = 160
        set(v) { field = v.coerceAtLeast(40); invalidate() }

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

    private data class Palette(val bgIn: Int, val bgOut: Int, val ring: Int, val track: Int, val text: Int, val sub: Int, val accent: Int)

    private fun palette() = when (theme) {
        Prefs.THEME_DARK -> Palette(0xFF1E2633.toInt(), 0xFF0B0F17.toInt(), 0xFF2F7CF6.toInt(), 0xFF2E3848.toInt(), Color.WHITE, 0xFF9AA5B8.toInt(), 0xFF2F7CF6.toInt())
        Prefs.THEME_BLUE -> Palette(0xFF4A90FF.toInt(), 0xFF1F5FD1.toInt(), Color.WHITE, 0x55FFFFFF, Color.WHITE, 0xCCFFFFFF.toInt(), Color.WHITE)
        else -> Palette(0xFFFFFFFF.toInt(), 0xFFC9CDD3.toInt(), 0xFF2B2B2B.toInt(), 0xFFD5D8DD.toInt(), 0xFF111111.toInt(), 0xFF555B66.toInt(), 0xFF2F7CF6.toInt())
    }

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

        // Fundo: disco com leve relevo e borda
        fill.shader = RadialGradient(cx, cy - r * 0.3f, r * 1.3f, p.bgIn, p.bgOut, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 0.98f, fill)
        fill.shader = null
        stroke.strokeWidth = size * 0.035f
        stroke.color = p.ring
        canvas.drawCircle(cx, cy, r * 0.98f - stroke.strokeWidth / 2, stroke)

        val number = if (speed == null) "--" else value.roundToInt().toString()

        when (style) {
            Prefs.STYLE_ANALOG -> drawAnalog(canvas, p, cx, cy, r, value, number)
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

    private fun speedColor(v: Float, p: Palette): Int = when {
        v >= maxKmh * 0.85f -> 0xFFFF5A5A.toInt()
        v >= maxKmh * 0.65f -> 0xFFF5B341.toInt()
        else -> p.accent
    }

    private fun drawNumber(canvas: Canvas, p: Palette, cx: Float, cy: Float, size: Float, number: String, scale: Float) {
        text.color = p.text
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textSize = size * scale * (if (number.length >= 3) 0.82f else 1f)
        canvas.drawText(number, cx, cy + text.textSize * 0.30f, text)
        text.typeface = Typeface.DEFAULT
        text.color = p.sub
        text.textSize = size * 0.11f
        canvas.drawText("km/h", cx, cy + size * 0.30f, text)
    }

    private fun drawAnalog(canvas: Canvas, p: Palette, cx: Float, cy: Float, r: Float, value: Float, number: String) {
        val size = r * 2
        val start = 135f
        val sweep = 270f
        val ro = r * 0.86f
        // Marcações
        val majorStep = when {
            maxKmh <= 120 -> 20
            maxKmh <= 200 -> 20
            else -> 40
        }
        val minorStep = majorStep / 2
        text.typeface = Typeface.DEFAULT_BOLD
        text.color = p.text
        text.textSize = size * 0.075f
        var k = 0
        while (k <= maxKmh) {
            val ang = Math.toRadians((start + sweep * k / maxKmh).toDouble())
            val major = k % majorStep == 0
            val len = if (major) r * 0.13f else r * 0.07f
            stroke.strokeWidth = if (major) size * 0.018f else size * 0.009f
            stroke.color = if (k >= maxKmh * 0.85f) 0xFFFF5A5A.toInt() else p.sub
            val c = cos(ang).toFloat()
            val s = sin(ang).toFloat()
            canvas.drawLine(cx + c * ro, cy + s * ro, cx + c * (ro - len), cy + s * (ro - len), stroke)
            if (major && size >= 110) {
                val rl = ro - len - text.textSize * 0.95f
                canvas.drawText(k.toString(), cx + c * rl, cy + s * rl + text.textSize * 0.35f, text)
            }
            k += minorStep
        }
        // Ponteiro
        val ang = Math.toRadians((start + sweep * (value / maxKmh).coerceIn(0f, 1f)).toDouble())
        stroke.color = 0xFFE53935.toInt()
        stroke.strokeWidth = size * 0.028f
        canvas.drawLine(cx, cy, cx + cos(ang).toFloat() * r * 0.70f, cy + sin(ang).toFloat() * r * 0.70f, stroke)
        fill.color = 0xFFE53935.toInt()
        canvas.drawCircle(cx, cy, size * 0.055f, fill)
        fill.color = p.bgIn
        canvas.drawCircle(cx, cy, size * 0.025f, fill)
        // Número embaixo
        text.color = p.text
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = size * 0.15f
        canvas.drawText(number, cx, cy + r * 0.62f, text)
        text.typeface = Typeface.DEFAULT
        text.color = p.sub
        text.textSize = size * 0.07f
        canvas.drawText("km/h", cx, cy + r * 0.80f, text)
    }
}
