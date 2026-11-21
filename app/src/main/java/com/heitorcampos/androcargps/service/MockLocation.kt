package com.heitorcampos.androcargps.service

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import com.heitorcampos.androcargps.Protocol

/**
 * TABLET: entrega a posição recebida ao Android como "local fictício".
 * Mesma técnica do Fake GPS: provedores de teste do GPS e da rede + modo fictício do Google Play
 * (é dele que o Google Maps lê). Só funciona com o app escolhido em
 * Opções do desenvolvedor → Selecionar app de local fictício.
 */
class MockLocation(ctx: Context) {
    private val lm = ctx.getSystemService(LocationManager::class.java)
    private val fused = LocationServices.getFusedLocationProviderClient(ctx)
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private var enabled = false
    private var closed = false

    @Suppress("DEPRECATION")
    private fun enable() {
        for (p in providers) {
            try {
                lm.removeTestProvider(p)
            } catch (_: Exception) {
            }
            lm.addTestProvider(p, false, false, false, false, true, true, true, 1, 1)
            lm.setTestProviderEnabled(p, true)
        }
        try {
            fused.setMockMode(true)
        } catch (_: Exception) {
        }
        enabled = true
    }

    /** Aplica a posição. Devolve uma mensagem de erro, ou null se deu certo. */
    @Synchronized
    fun push(f: Protocol.Fix): String? {
        if (closed) return null
        return try {
            if (!enabled) enable()
            for (p in providers) lm.setTestProviderLocation(p, build(f, p))
            try {
                fused.setMockLocation(build(f, "fused"))
            } catch (_: Exception) {
            }
            null
        } catch (_: SecurityException) {
            enabled = false
            "Escolha o Androcar GPS em Opções do desenvolvedor → Selecionar app de local fictício"
        } catch (e: IllegalArgumentException) {
            enabled = false
            "Erro ao aplicar o local fictício: ${e.message}"
        }
    }

    private fun build(f: Protocol.Fix, provider: String) = Location(provider).apply {
        latitude = f.lat
        longitude = f.lon
        f.alt?.let { altitude = it }
        f.speed?.let { speed = it }
        f.bearing?.let { bearing = it }
        accuracy = f.acc
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        verticalAccuracyMeters = f.acc * 1.5f
        if (f.speed != null) speedAccuracyMetersPerSecond = 0.5f
        if (f.bearing != null) bearingAccuracyDegrees = 5f
    }

    @Synchronized
    fun disable() {
        closed = true
        for (p in providers) {
            try {
                lm.setTestProviderEnabled(p, false)
                lm.removeTestProvider(p)
            } catch (_: Exception) {
            }
        }
        try {
            fused.setMockMode(false)
        } catch (_: Exception) {
        }
        enabled = false
    }
}
