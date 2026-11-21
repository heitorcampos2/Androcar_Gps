package com.heitorcampos.androcargps.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
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
 * CELULAR: lê o GPS (1x por segundo, alta precisão) e envia para os tablets conectados.
 * Abre um servidor TCP na porta [Protocol.PORT]; o tablet se conecta e se identifica com o código.
 */
class SenderService : Service() {

    companion object {
        const val ACTION_STOP = "com.heitorcampos.androcargps.action.STOP_SENDER"
        const val ACTION_APPLY_INTERVAL = "com.heitorcampos.androcargps.action.APPLY_INTERVAL"

        /** Aplica o novo intervalo de envio sem derrubar a conexão com o tablet. */
        fun applyInterval(c: Context) {
            if (isRunning) {
                runCatching {
                    c.startService(Intent(c, SenderService::class.java).setAction(ACTION_APPLY_INTERVAL))
                }
            }
        }

        @Volatile
        var isRunning = false
            private set

        fun start(c: Context) =
            ContextCompat.startForegroundService(c, Intent(c, SenderService::class.java))

        fun stop(c: Context) {
            c.stopService(Intent(c, SenderService::class.java))
        }
    }

    private class Client(val socket: Socket, val out: BufferedWriter)

    private val clients = CopyOnWriteArrayList<Client>()
    private val io = Executors.newSingleThreadExecutor()
    private val ticker = Executors.newSingleThreadScheduledExecutor()

    @Volatile private var server: ServerSocket? = null
    @Volatile private var active = false
    private var fused: FusedLocationProviderClient? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onFix(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_APPLY_INTERVAL) {
            if (active) {
                fused?.removeLocationUpdates(callback)
                lastFixTime = 0L
                startLocation()
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
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndrocarGps:envio")
            .apply { setReferenceCounted(false); acquire() }

        AppState.sender.value = SenderStatus(running = true)
        startServer(Prefs.senderCode(this))
        startLocation()
        ticker.scheduleAtFixedRate({ broadcast(Protocol.PING) }, 2, 2, TimeUnit.SECONDS)
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
            refreshClients()
            // O tablet não manda mais nada; a leitura só serve para perceber quando ele sai.
            while (active && input.readLine() != null) Unit
        } catch (_: IOException) {
        } finally {
            client?.let { clients.remove(it) }
            runCatching { s.close() }
            refreshClients()
        }
    }

    private fun broadcast(line: String) {
        if (!active) return
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
                if (removed) refreshClients()
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val ms = Prefs.intervalMs(this)
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, ms)
            .setMinUpdateIntervalMillis(ms)
            .setMaxUpdateDelayMillis(0L)
            .build()
        val client = LocationServices.getFusedLocationProviderClient(this)
        fused = client
        try {
            client.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            AppState.sender.update { it.copy(error = "Permissão de localização negada") }
        }
    }

    private var lastFixTime = 0L
    private var avgMs = 0.0

    private fun onFix(l: Location) {
        broadcast(Protocol.encode(l))
        val now = System.currentTimeMillis()
        if (lastFixTime > 0) {
            val d = (now - lastFixTime).toDouble()
            avgMs = if (avgMs == 0.0) d else avgMs * 0.7 + d * 0.3   // média suave
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

    private fun refreshClients() {
        val n = clients.size
        AppState.sender.update { it.copy(clients = n) }
        if (active) {
            Notifs.notify(
                this, Notifs.ID_SENDER,
                Notifs.sender(this, if (n > 0) "Tablet conectado" else "Aguardando o tablet…"),
            )
        }
    }

    override fun onDestroy() {
        active = false
        isRunning = false
        fused?.removeLocationUpdates(callback)
        runCatching { server?.close() }
        clients.forEach { runCatching { it.socket.close() } }
        clients.clear()
        ticker.shutdownNow()
        io.shutdownNow()
        wakeLock?.let { if (it.isHeld) it.release() }
        AppState.sender.value = SenderStatus()
        super.onDestroy()
    }
}
