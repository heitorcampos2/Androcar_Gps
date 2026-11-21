package com.heitorcampos.androcargps.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.heitorcampos.androcargps.AppState
import com.heitorcampos.androcargps.Perms
import com.heitorcampos.androcargps.Prefs
import com.heitorcampos.androcargps.Protocol
import com.heitorcampos.androcargps.SenderStatus
import com.heitorcampos.androcargps.speedo.SpeedOverlay
import kotlinx.coroutines.flow.update
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * CELULAR: lê o GPS e envia para os tablets conectados (servidor TCP na porta [Protocol.PORT]).
 *
 * Economia de bateria: o GPS e o "manter acordado" só ficam ligados enquanto houver tablet
 * conectado. Sem tablet, o celular só espera a conexão (quase não gasta nada); quando o tablet
 * sai, espera 20 s antes de desligar o GPS, para uma reconexão rápida não perder o sinal.
 */
class SenderService : Service() {

    companion object {
        const val ACTION_STOP = "com.heitorcampos.androcargps.action.STOP_SENDER"
        const val ACTION_APPLY_INTERVAL = "com.heitorcampos.androcargps.action.APPLY_INTERVAL"
        private const val IDLE_GPS_OFF_MS = 20_000L

        @Volatile
        var isRunning = false
            private set

        fun start(c: Context) =
            ContextCompat.startForegroundService(c, Intent(c, SenderService::class.java))

        fun stop(c: Context) {
            c.stopService(Intent(c, SenderService::class.java))
        }

        /** Aplica o novo intervalo de envio sem derrubar a conexão com o tablet. */
        fun applyInterval(c: Context) {
            if (isRunning) {
                runCatching {
                    c.startService(Intent(c, SenderService::class.java).setAction(ACTION_APPLY_INTERVAL))
                }
            }
        }
    }

    private class Client(val socket: Socket, val out: BufferedWriter)

    private val clients = CopyOnWriteArrayList<Client>()
    private val io = Executors.newSingleThreadExecutor()
    private val ticker = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var server: ServerSocket? = null
    @Volatile private var active = false
    @Volatile private var lastSentAt = 0L
    private var fused: FusedLocationProviderClient? = null
    private var gpsOn = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastFixTime = 0L
    private var avgMs = 0.0
    private var lastNotifText: String? = null
    private var overlay: SpeedOverlay? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onFix(it) }
        }
    }

    private val gpsOffIfIdle = Runnable { if (clients.isEmpty()) setGps(false) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == SpeedOverlay.ACTION_REFRESH) {
            if (active) {
                overlay?.refresh(fromSettings = true)
                return START_STICKY
            }
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_APPLY_INTERVAL) {
            if (active) {
                if (gpsOn) {
                    setGps(false)
                    setGps(true)
                }
                return START_STICKY
            }
            stopSelf()
            return START_NOT_STICKY
        }
        if (active) return START_STICKY

        Notifs.ensureChannel(this)
        try {
            startInForeground(Notifs.ID_SENDER, Notifs.sender(this, "Aguardando o tablet…"))
        } catch (e: Exception) {
            AppState.sender.value = SenderStatus(error = "O Android não deixou iniciar: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Perms.hasLocation(this)) {
            AppState.sender.value = SenderStatus(error = "Permissão de localização negada")
            stopSelf()
            return START_NOT_STICKY
        }

        active = true
        isRunning = true
        fused = LocationServices.getFusedLocationProviderClient(this)
        AppState.sender.value = SenderStatus(running = true)
        overlay = SpeedOverlay(this) {
            val s = AppState.sender.value
            if (s.lastFixAt > 0 && System.currentTimeMillis() - s.lastFixAt < 4000) s.speedKmh ?: 0f else null
        }.also { it.refresh() }
        startServer(Prefs.senderCode(this))
        // Sinal de vida só quando não houve posição recente (menos tráfego no Wi-Fi)
        ticker.scheduleWithFixedDelay({
            if (System.currentTimeMillis() - lastSentAt >= 2000) broadcast(Protocol.PING)
        }, 2, 2, TimeUnit.SECONDS)
        return START_STICKY
    }

    private fun startServer(code: String) {
        thread(name = "androcar-servidor", isDaemon = true) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(Protocol.PORT))
                server = ss
                while (active) {
                    val s = ss.accept()
                    thread(name = "androcar-tablet", isDaemon = true) { handleClient(s, code) }
                }
            } catch (e: IOException) {
                if (active) {
                    AppState.sender.update {
                        it.copy(error = "Não consegui abrir a porta ${Protocol.PORT}: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleClient(s: Socket, code: String) {
        var client: Client? = null
        try {
            s.tcpNoDelay = true
            s.soTimeout = 5000
            val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val out = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            val hello = input.readLine()?.trim()?.split(" ") ?: return
            if (hello.size < 2 || hello[0] != Protocol.HELLO || hello[1] != code) {
                out.write("${Protocol.ERR} codigo\n")
                out.flush()
                return
            }
            out.write("${Protocol.OK}\n")
            out.flush()
            s.soTimeout = 0
            client = Client(s, out).also { clients.add(it) }
            onClientsChanged()
            // O tablet não manda mais nada; a leitura só serve para perceber quando ele sai.
            while (active && input.readLine() != null) Unit
        } catch (_: IOException) {
        } finally {
            client?.let { clients.remove(it) }
            runCatching { s.close() }
            onClientsChanged()
        }
    }

    private fun broadcast(line: String) {
        if (!active || clients.isEmpty()) return
        try {
            io.execute {
                var removed = false
                for (c in clients) {
                    try {
                        c.out.write(line)
                        c.out.write("\n")
                        c.out.flush()
                    } catch (_: IOException) {
                        clients.remove(c)
                        runCatching { c.socket.close() }
                        removed = true
                    }
                }
                lastSentAt = System.currentTimeMillis()
                if (removed) onClientsChanged()
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    /** Liga o GPS quando chega um tablet; desliga 20 s depois que o último sai. */
    private fun onClientsChanged() {
        main.post {
            if (!active) return@post
            val n = clients.size
            main.removeCallbacks(gpsOffIfIdle)
            if (n > 0) setGps(true) else if (gpsOn) main.postDelayed(gpsOffIfIdle, IDLE_GPS_OFF_MS)
            AppState.sender.update { it.copy(clients = n) }
            val text = if (n > 0) "Tablet conectado" else "Aguardando o tablet (GPS em espera)"
            if (text != lastNotifText) {
                lastNotifText = text
                Notifs.notify(this, Notifs.ID_SENDER, Notifs.sender(this, text))
            }
        }
    }

    /** Sempre na thread principal. */
    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun setGps(on: Boolean) {
        if (on == gpsOn) return
        val client = fused ?: return
        if (on) {
            val ms = Prefs.intervalMs(this)
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, ms)
                .setMinUpdateIntervalMillis(ms)
                .setMaxUpdateDelayMillis(0L)
                .build()
            try {
                client.requestLocationUpdates(req, callback, Looper.getMainLooper())
                gpsOn = true
                lastFixTime = 0L
                avgMs = 0.0
                // Mantém o celular acordado só enquanto está enviando
                wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndrocarGps:envio")
                    .apply { setReferenceCounted(false); acquire() }
            } catch (_: SecurityException) {
                AppState.sender.update { it.copy(error = "Permissão de localização negada") }
            }
        } else {
            client.removeLocationUpdates(callback)
            gpsOn = false
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
        AppState.sender.update { it.copy(gpsOn = gpsOn) }
    }

    private fun onFix(l: Location) {
        broadcast(Protocol.encode(l))
        val now = System.currentTimeMillis()
        if (lastFixTime > 0) {
            val d = (now - lastFixTime).toDouble()
            avgMs = if (avgMs == 0.0) d else avgMs * 0.7 + d * 0.3 // média suave
        }
        lastFixTime = now
        AppState.sender.update {
            it.copy(
                lastFixAt = now,
                accuracy = if (l.hasAccuracy()) l.accuracy else null,
                measuredMs = if (avgMs > 0) avgMs.toLong() else null,
                speedKmh = if (l.hasSpeed()) l.speed * 3.6f else null,
            )
        }
    }

    override fun onDestroy() {
        active = false
        isRunning = false
        main.removeCallbacksAndMessages(null)
        overlay?.hide()
        setGps(false)
        runCatching { server?.close() }
        clients.forEach { runCatching { it.socket.close() } }
        clients.clear()
        ticker.shutdownNow()
        io.shutdownNow()
        AppState.sender.value = SenderStatus()
        super.onDestroy()
    }
}
