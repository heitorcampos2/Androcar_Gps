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

    /** IP do celular digitado à mão (vazio = automático). */
    fun manualHost(c: Context): String? =
        sp(c).getString("ip_manual", null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setManualHost(c: Context, host: String) {
        sp(c).edit().putString("ip_manual", host.trim()).apply()
    }
}
