package com.heitorcampos.androcargps

import android.graphics.Bitmap
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

/**
 * Pareamento por QR code.
 * O celular mostra:  androcargps://parear?c=2607&ip=10.84.146.129,192.168.1.65
 * O tablet lê com a câmera e guarda o código e os IPs.
 */
object Pairing {
    fun uri(code: String, ips: List<String>): String =
        "androcargps://parear?c=$code&ip=${ips.joinToString(",")}"

    data class Result(val code: String, val hosts: List<String>)

    fun parse(text: String?): Result? {
        if (text.isNullOrBlank()) return null
        return try {
            val u = Uri.parse(text.trim())
            if (u.scheme != "androcargps") return null
            val code = u.getQueryParameter("c")?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: return null
            val hosts = u.getQueryParameter("ip")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            Result(code, hosts)
        } catch (_: Exception) {
            null
        }
    }

    fun qr(text: String, size: Int = 640): Bitmap? = try {
        BarcodeEncoder().encodeBitmap(text, BarcodeFormat.QR_CODE, size, size)
    } catch (_: Exception) {
        null
    }
}
