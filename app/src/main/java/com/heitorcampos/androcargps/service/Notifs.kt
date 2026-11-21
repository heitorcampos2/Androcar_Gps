package com.heitorcampos.androcargps.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.heitorcampos.androcargps.R
import com.heitorcampos.androcargps.ui.MainActivity

object Notifs {
    private const val CHANNEL = "gps"
    const val ID_SENDER = 1
    const val ID_RECEIVER = 2

    fun ensureChannel(c: Context) {
        val ch = NotificationChannel(CHANNEL, "GPS em andamento", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun sender(c: Context, text: String) =
        build(c, "Enviando GPS", text, SenderService::class.java, SenderService.ACTION_STOP)

    fun receiver(c: Context, text: String) =
        build(c, "Recebendo GPS do celular", text, ReceiverService::class.java, ReceiverService.ACTION_STOP)

    private fun build(c: Context, title: String, text: String, svc: Class<*>, stopAction: String): Notification {
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            c, 1, Intent(c, svc).setAction(stopAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_gps)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(0xFF2F7CF6.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Parar", stop)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun notify(c: Context, id: Int, n: Notification) {
        try {
            NotificationManagerCompat.from(c).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

/** startForeground com o tipo "localização" (obrigatório no Android 14). */
fun Service.startInForeground(id: Int, n: Notification) {
    if (Build.VERSION.SDK_INT >= 29) {
        startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    } else {
        startForeground(id, n)
    }
}
