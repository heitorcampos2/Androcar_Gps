package com.heitorcampos.androcargps

import android.location.Location
import org.json.JSONObject

/**
 * Protocolo entre celular e tablet (TCP, uma mensagem por linha).
 *
 *  tablet  -> celular : "HELLO <codigo> <versao>"
 *  celular -> tablet  : "OK" ou "ERR <motivo>"
 *  celular -> tablet  : posições em JSON, uma por linha, e "P" (sinal de vida) a cada 2 s
 */
object Protocol {
    const val PORT = 47800
    const val HELLO = "HELLO"
    const val OK = "OK"
    const val ERR = "ERR"
    const val PING = "P"

    data class Fix(
        val lat: Double,
        val lon: Double,
        val alt: Double?,
        val speed: Float?,
        val bearing: Float?,
        val acc: Float,
        val time: Long,
    )

    fun encode(l: Location): String = JSONObject().apply {
        put("lat", l.latitude)
        put("lon", l.longitude)
        if (l.hasAltitude()) put("alt", l.altitude)
        if (l.hasSpeed()) put("spd", l.speed.toDouble())
        if (l.hasBearing()) put("brg", l.bearing.toDouble())
        put("acc", (if (l.hasAccuracy()) l.accuracy else 10f).toDouble())
        put("ts", l.time)
    }.toString()

    fun decode(line: String): Fix? = try {
        val j = JSONObject(line)
        Fix(
            lat = j.getDouble("lat"),
            lon = j.getDouble("lon"),
            alt = if (j.has("alt")) j.getDouble("alt") else null,
            speed = if (j.has("spd")) j.getDouble("spd").toFloat() else null,
            bearing = if (j.has("brg")) j.getDouble("brg").toFloat() else null,
            acc = j.optDouble("acc", 10.0).toFloat(),
            time = j.optLong("ts", System.currentTimeMillis()),
        )
    } catch (_: Exception) {
        null
    }
}
