package com.heitorcampos.androcargps.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/** Atalhos para telas do sistema. */
object Intents {
    private fun Context.go(i: Intent): Boolean = try {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    fun toast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_LONG).show()

    fun openDevOptions(c: Context) {
        if (!c.go(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
            toast(c, "Ative o modo desenvolvedor primeiro (Sobre o aparelho → toque 7x na versão do SO)")
            c.go(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
    }

    fun openAppDetails(c: Context) {
        c.go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null)))
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBattery(c: Context) {
        val pm = c.getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(c.packageName)) {
            toast(c, "A economia de bateria já está liberada para o app")
            return
        }
        if (!c.go(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}")))) {
            openAppDetails(c)
        }
    }

    fun openMaps(c: Context) {
        val i = c.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
        if (i == null || !c.go(i)) toast(c, "Google Maps não encontrado neste aparelho")
    }

    fun openUrl(c: Context, url: String) {
        c.go(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
