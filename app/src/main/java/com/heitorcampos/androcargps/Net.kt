package com.heitorcampos.androcargps

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object Net {
    /**
     * Onde está o celular?
     * 1) IP digitado nas configurações, se houver;
     * 2) senão, o "gateway" do Wi-Fi: quando o tablet está no roteador (hotspot) do celular,
     *    o gateway É o celular — por isso não precisa digitar nada.
     */
    fun findPhone(c: Context): String? = Prefs.manualHost(c) ?: wifiGateway(c)

    @Suppress("DEPRECATION")
    fun wifiGateway(c: Context): String? {
        try {
            val cm = c.getSystemService(ConnectivityManager::class.java)
            for (n in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                val lp = cm.getLinkProperties(n) ?: continue
                for (r in lp.routes) {
                    val gw = r.gateway
                    if (r.isDefaultRoute && gw is Inet4Address && !gw.isAnyLocalAddress) {
                        return gw.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return try {
            val wm = c.applicationContext.getSystemService(WifiManager::class.java)
            val g = wm.dhcpInfo?.gateway ?: 0
            if (g == 0) null else intToIp(g)
        } catch (_: Exception) {
            null
        }
    }

    private fun intToIp(i: Int) =
        "${i and 0xff}.${(i shr 8) and 0xff}.${(i shr 16) and 0xff}.${(i shr 24) and 0xff}"

    /** IPs deste aparelho (para mostrar no celular, caso precise digitar no tablet). */
    fun localIps(): List<String> = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
    } catch (_: Exception) {
        emptyList()
    }
}
