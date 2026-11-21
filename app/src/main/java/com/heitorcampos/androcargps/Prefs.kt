package com.heitorcampos.androcargps

import android.content.Context

/** Preferências locais do app (nada é sincronizado). */
object Prefs {
    const val MODE_SEND = "enviar"
    const val MODE_RECEIVE = "receber"

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("androcar_gps", Context.MODE_PRIVATE)

    fun mode(c: Context): String? = sp(c).getString("modo", null)

    fun setMode(c: Context, mode: String?) {
        sp(c).edit().apply {
            if (mode == null) remove("modo") else putString("modo", mode)
        }.apply()
    }

    /** Código de 4 dígitos mostrado no celular. */
    fun senderCode(c: Context): String =
        sp(c).getString("codigo_envio", null) ?: newSenderCode(c)

    fun newSenderCode(c: Context): String {
        val code = (1000..9999).random().toString()
        sp(c).edit().putString("codigo_envio", code).apply()
        return code
    }

    /** Código digitado no tablet. */
    fun receiverCode(c: Context): String? = sp(c).getString("codigo_recepcao", null)

    fun setReceiverCode(c: Context, code: String) {
        sp(c).edit().putString("codigo_recepcao", code).apply()
    }

    const val INTERVAL_MIN = 100L
    const val INTERVAL_MAX = 10_000L
    const val INTERVAL_DEFAULT = 1000L

    /** Intervalo de envio do GPS, em milissegundos (celular). */
    fun intervalMs(c: Context): Long =
        sp(c).getLong("intervalo_ms", INTERVAL_DEFAULT).coerceIn(INTERVAL_MIN, INTERVAL_MAX)

    fun setIntervalMs(c: Context, ms: Long) {
        sp(c).edit().putLong("intervalo_ms", ms.coerceIn(INTERVAL_MIN, INTERVAL_MAX)).apply()
    }

    // ---------- Velocímetro flutuante
    const val STYLE_DIGITAL = "digital"
    const val STYLE_RING = "anel"
    const val STYLE_ANALOG = "analogico"
    const val THEME_DARK = "escuro"
    const val THEME_LIGHT = "claro"
    const val THEME_BLUE = "azul"

    data class Speedo(
        val enabled: Boolean,
        val style: String,
        val theme: String,
        val sizeDp: Int,
        val opacity: Int,
        val maxKmh: Int,
        val x: Int,
        val y: Int,
    )

    fun speedo(c: Context): Speedo = sp(c).run {
        Speedo(
            enabled = getBoolean("velo_ativo", false),
            style = getString("velo_estilo", STYLE_DIGITAL) ?: STYLE_DIGITAL,
            theme = getString("velo_tema", THEME_LIGHT) ?: THEME_LIGHT,
            sizeDp = getInt("velo_tamanho", 130).coerceIn(80, 260),
            opacity = getInt("velo_opacidade", 95).coerceIn(30, 100),
            maxKmh = getInt("velo_maximo", 160),
            x = getInt("velo_x", 40),
            y = getInt("velo_y", 200),
        )
    }

    fun setSpeedo(c: Context, s: Speedo) {
        sp(c).edit()
            .putBoolean("velo_ativo", s.enabled)
            .putString("velo_estilo", s.style)
            .putString("velo_tema", s.theme)
            .putInt("velo_tamanho", s.sizeDp)
            .putInt("velo_opacidade", s.opacity)
            .putInt("velo_maximo", s.maxKmh)
            .putInt("velo_x", s.x)
            .putInt("velo_y", s.y)
            .apply()
    }

    fun setSpeedoPosition(c: Context, x: Int, y: Int) {
        sp(c).edit().putInt("velo_x", x).putInt("velo_y", y).apply()
    }

    /** IPs do celular lidos do QR code. */
    fun qrHosts(c: Context): List<String> =
        sp(c).getString("ips_qr", null)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    fun setQrHosts(c: Context, hosts: List<String>) {
        sp(c).edit().putString("ips_qr", hosts.joinToString(",")).apply()
    }

    /** IP do celular digitado à mão (vazio = automático). */
    fun manualHost(c: Context): String? =
        sp(c).getString("ip_manual", null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setManualHost(c: Context, host: String) {
        sp(c).edit().putString("ip_manual", host.trim()).apply()
    }
}
