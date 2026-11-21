package com.heitorcampos.androcargps.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.heitorcampos.androcargps.AppState
import com.heitorcampos.androcargps.BuildConfig
import com.heitorcampos.androcargps.Net
import com.heitorcampos.androcargps.Prefs
import com.heitorcampos.androcargps.Protocol
import com.heitorcampos.androcargps.ReceiverStatus
import com.heitorcampos.androcargps.speedo.SpeedOverlay
import kotlinx.coroutines.flow.update
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * TABLET: conecta no celular, recebe as posições e aplica como local fictício.
 * Reconecta sozinho se o Wi-Fi cair; se ficar 6 s sem nada do celular, considera o sinal perdido.
 */
class ReceiverService : Service() {

    companion object {
        const val ACTION_STOP = "com.heitorcampos.androcargps.action.STOP_RECEIVER"

        @Volatile
        var isRunning = false
            private set

        fun start(c: Context) =
            ContextCompat.startForegroundService(c, Intent(c, ReceiverService::class.java))

        fun stop(c: Context) {
            c.stopService(Intent(c, ReceiverService::class.java))
        }
    }

    @Volatile private var active = false
    @Volatile private var socket: Socket? = null
    private var worker: Thread? = null
    private var mock: MockLocation? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastMessage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
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
        if (active) return START_STICKY

        Notifs.ensureChannel(this)
        try {
            startInForeground(Notifs.ID_RECEIVER, Notifs.receiver(this, "Procurando o celular…"))
        } catch (e: Exception) {
            AppState.receiver.value = ReceiverStatus(
                message = "O Android não deixou iniciar: ${e.message}", isError = true,
            )
            stopSelf()
            return START_NOT_STICKY
        }

        active = true
        isRunning = true
        mock = MockLocation(this)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndrocarGps:recepcao")
            .apply { setReferenceCounted(false) }
        wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AndrocarGps:wifi")
            .apply { setReferenceCounted(false) }

        AppState.receiver.value = ReceiverStatus(running = true, message = "Procurando o celular…")
        overlay = SpeedOverlay(this) {
            val s = AppState.receiver.value
            if (s.lastFixAt > 0 && System.currentTimeMillis() - s.lastFixAt < 4000) s.speedKmh ?: 0f else null
        }.also { it.refresh() }
        worker = thread(name = "androcar-receptor", isDaemon = true) { loop() }
        return START_STICKY
    }

    private fun loop() {
        while (active) {
            val code = Prefs.receiverCode(this)
            if (code.isNullOrBlank() || code.length != 4) {
                status(false, "Digite o código de pareamento que aparece no celular", isError = true)
                pause(2000)
                continue
            }
            val hosts = Net.candidates(this)
            if (hosts.isEmpty()) {
                status(false, "Procurando o celular… ligue o tablet no roteador (hotspot) do celular", true)
                pause(2000)
                continue
            }

            // Tenta cada endereço possível do celular até um responder
            var found: Socket? = null
            var host = hosts.first()
            for (h in hosts) {
                if (!active) break
                val t = Socket()
                try {
                    t.connect(InetSocketAddress(h, Protocol.PORT), 2000)
                    found = t
                    host = h
                    break
                } catch (_: IOException) {
                    runCatching { t.close() }
                }
            }
            if (found == null) {
                if (active) {
                    status(
                        false,
                        "Celular não encontrado. O envio está ligado no celular e o tablet no hotspot dele?",
                        true,
                    )
                    // Procura com calma quando o celular não aparece: 2 s, 3 s, 4 s… até 8 s (poupa bateria)
                    pause((2000L + misses * 1000L).coerceAtMost(8000L))
                    misses++
                }
                continue
            }
            misses = 0

            var linked = false
            try {
                found.use { s ->
                    socket = s
                    s.tcpNoDelay = true
                    s.soTimeout = 6000
                    val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    val out = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                    out.write("${Protocol.HELLO} $code ${BuildConfig.VERSION_CODE}\n")
                    out.flush()
                    val reply = input.readLine() ?: throw IOException("sem resposta")
                    if (!reply.startsWith(Protocol.OK)) {
                        status(false, "Código de pareamento incorreto — confira o código no celular", true, host)
                        pause(4000)
                        return@use
                    }
                    linked = true
                    status(true, "Conectado ao celular", host = host)
                    holdLocks(true) // acordado e Wi-Fi em alto desempenho só enquanto recebe
                    try {
                        while (active) {
                            val line = input.readLine() ?: break
                            if (line == Protocol.PING) continue
                            val fix = Protocol.decode(line) ?: continue
                            onFix(fix)
                        }
                    } finally {
                        holdLocks(false)
                    }
                }
            } catch (_: SocketTimeoutException) {
                if (active) {
                    status(
                        false,
                        if (linked) "Sinal do celular perdido — reconectando…"
                        else "O celular não respondeu — o envio está ligado lá?",
                        true, host,
                    )
                }
            } catch (_: IOException) {
                if (active) {
                    status(
                        false,
                        if (linked) "Conexão caiu — reconectando…"
                        else "Celular não encontrado — o envio está ligado lá?",
                        true, host,
                    )
                }
            }
            socket = null
            if (active) pause(1500)
        }
    }

    private var lastFixTime = 0L
    private var avgMs = 0.0
    private var misses = 0
    private var overlay: SpeedOverlay? = null

    @Synchronized
    @android.annotation.SuppressLint("WakelockTimeout")
    private fun holdLocks(on: Boolean) {
        try {
            if (on && active) {
                wakeLock?.let { if (!it.isHeld) it.acquire() }
                wifiLock?.let { if (!it.isHeld) it.acquire() }
            } else {
                wakeLock?.let { if (it.isHeld) it.release() }
                wifiLock?.let { if (it.isHeld) it.release() }
            }
        } catch (_: Exception) {
        }
    }

    private fun onFix(f: Protocol.Fix) {
        val err = mock?.push(f)
        val now = System.currentTimeMillis()
        if (lastFixTime > 0 && now - lastFixTime < 15_000) {
            val d = (now - lastFixTime).toDouble()
            avgMs = if (avgMs == 0.0) d else avgMs * 0.7 + d * 0.3
        }
        lastFixTime = now
        AppState.receiver.update {
            it.copy(
                measuredMs = if (avgMs > 0) avgMs.toLong() else null,
                lastFixAt = now,
                lat = f.lat,
                lon = f.lon,
                speedKmh = f.speed?.let { s -> s * 3.6f },
                accuracy = f.acc,
                mockError = err,
            )
        }
    }

    private fun status(connected: Boolean, message: String, isError: Boolean = false, host: String? = null) {
        AppState.receiver.update {
            it.copy(
                running = true,
                connected = connected,
                message = message,
                isError = isError,
                host = host ?: it.host,
            )
        }
        if (message != lastMessage && active) {
            lastMessage = message
            Notifs.notify(this, Notifs.ID_RECEIVER, Notifs.receiver(this, message))
        }
    }

    private fun pause(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    override fun onDestroy() {
        active = false
        isRunning = false
        runCatching { socket?.close() }
        worker?.interrupt()
        overlay?.hide()
        mock?.disable()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        AppState.receiver.value = ReceiverStatus()
        super.onDestroy()
    }
}
