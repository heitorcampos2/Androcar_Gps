package com.heitorcampos.androcargps.speedo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.heitorcampos.androcargps.Prefs
import com.heitorcampos.androcargps.R
import com.heitorcampos.androcargps.service.ReceiverService
import com.heitorcampos.androcargps.service.SenderService
import kotlin.math.roundToInt

/**
 * Velocímetro flutuante por cima dos outros apps (Google Maps etc.).
 * Toque na bolinha mostra os botões: ✥ mover (arrastar) e ✕ fechar.
 * Os botões somem sozinhos depois de alguns segundos.
 * Vive dentro do serviço de envio/recepção: só aparece enquanto o GPS está rodando.
 */
class SpeedOverlay(
    private val ctx: Context,
    /** Velocidade atual em km/h, ou null se não há sinal recente. */
    private val speed: () -> Float?,
) {
    companion object {
        const val ACTION_REFRESH = "com.heitorcampos.androcargps.action.SPEEDO_REFRESH"

        fun canDraw(c: Context) = Settings.canDrawOverlays(c)

        /** Avisa o serviço que estiver rodando para mostrar/esconder/redesenhar o velocímetro. */
        fun refresh(c: Context) {
            val svc = when {
                SenderService.isRunning -> SenderService::class.java
                ReceiverService.isRunning -> ReceiverService::class.java
                else -> return
            }
            runCatching { c.startService(Intent(c, svc).setAction(ACTION_REFRESH)) }
        }
    }

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var dial: SpeedometerView? = null
    private var controls: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var closedByUser = false

    private val tick = object : Runnable {
        override fun run() {
            dial?.setSpeed(speed())
            main.postDelayed(this, 500)
        }
    }
    private val hideControls = Runnable { controls?.visibility = View.INVISIBLE }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).roundToInt()

    /** Lê as configurações e mostra, esconde ou redesenha. [fromSettings] = pedido vindo da tela de configurações. */
    fun refresh(fromSettings: Boolean = false) {
        if (fromSettings) closedByUser = false
        val s = Prefs.speedo(ctx)
        if (!s.enabled || closedByUser || !canDraw(ctx)) {
            hide()
            return
        }
        if (root == null) show(s) else applySettings(s)
    }

    private fun applySettings(s: Prefs.Speedo) {
        val d = dial ?: return
        d.style = s.style
        d.theme = s.theme
        d.maxKmh = s.maxKmh
        val px = dp(s.sizeDp)
        d.layoutParams = LinearLayout.LayoutParams(px, px)
        root?.alpha = s.opacity / 100f
        params?.let { p ->
            p.x = s.x
            p.y = s.y
            root?.let { r -> runCatching { wm.updateViewLayout(r, p) } }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(s: Prefs.Speedo) {
        val px = dp(s.sizeDp)
        val d = SpeedometerView(ctx).apply {
            style = s.style
            theme = s.theme
            maxKmh = s.maxKmh
            layoutParams = LinearLayout.LayoutParams(px, px)
        }
        val move = roundButton(R.drawable.ic_move, 0xFFE53935.toInt())
        val close = roundButton(R.drawable.ic_close, 0xFFE53935.toInt())
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(move, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(18) })
            addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
            visibility = View.INVISIBLE
        }
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(d)
            addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        val frame = FrameLayout(ctx).apply {
            addView(column)
            alpha = s.opacity / 100f
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = s.x
            y = s.y
        }

        // Toque na bolinha: mostra/esconde os botões
        d.setOnClickListener {
            if (row.visibility == View.VISIBLE) {
                row.visibility = View.INVISIBLE
            } else {
                row.visibility = View.VISIBLE
                main.removeCallbacks(hideControls)
                main.postDelayed(hideControls, 5000)
            }
        }
        // ✕ fecha até a próxima vez que o envio/recepção for iniciado (ou até religar nas configurações)
        close.setOnClickListener {
            closedByUser = true
            hide()
        }
        // ✥ arrastar para mover
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        move.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; touchX = e.rawX; touchY = e.rawY
                    main.removeCallbacks(hideControls)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchX).roundToInt()
                    lp.y = startY + (e.rawY - touchY).roundToInt()
                    runCatching { wm.updateViewLayout(frame, lp) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    Prefs.setSpeedoPosition(ctx, lp.x, lp.y)
                    main.postDelayed(hideControls, 5000)
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(frame, lp)
        } catch (_: Exception) {
            return
        }
        root = frame
        dial = d
        controls = row
        params = lp
        main.post(tick)
    }

    private fun roundButton(icon: Int, tint: Int) = ImageView(ctx).apply {
        setImageResource(icon)
        setColorFilter(tint)
        val pad = dp(10)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFD9DCE1.toInt())
            setStroke(dp(1), 0x33000000)
        }
        elevation = dp(4).toFloat()
    }

    fun hide() {
        main.removeCallbacks(tick)
        main.removeCallbacks(hideControls)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        dial = null
        controls = null
        params = null
    }
}
