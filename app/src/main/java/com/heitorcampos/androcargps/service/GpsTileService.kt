package com.heitorcampos.androcargps.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.heitorcampos.androcargps.Perms
import com.heitorcampos.androcargps.Prefs
import com.heitorcampos.androcargps.ui.MainActivity

/** Botão nas configurações rápidas (barra de notificações) para ligar/desligar sem abrir o app. */
class GpsTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val mode = Prefs.mode(this)
        if (mode == null || !Perms.hasLocation(this)) {
            openApp()
            return
        }
        val send = mode == Prefs.MODE_SEND
        val running = if (send) SenderService.isRunning else ReceiverService.isRunning
        try {
            when {
                running && send -> SenderService.stop(this)
                running -> ReceiverService.stop(this)
                send -> SenderService.start(this)
                else -> ReceiverService.start(this)
            }
        } catch (_: Exception) {
            openApp()
        }
        handler.postDelayed({ refresh() }, 500)
    }

    private fun refresh() {
        val t = qsTile ?: return
        val mode = Prefs.mode(this)
        val running = when (mode) {
            Prefs.MODE_SEND -> SenderService.isRunning
            Prefs.MODE_RECEIVE -> ReceiverService.isRunning
            else -> false
        }
        t.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Androcar GPS"
        if (Build.VERSION.SDK_INT >= 29) {
            t.subtitle = when (mode) {
                Prefs.MODE_SEND -> if (running) "Enviando" else "Enviar"
                Prefs.MODE_RECEIVE -> if (running) "Recebendo" else "Receber"
                else -> "Configurar"
            }
        }
        t.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}
