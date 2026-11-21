package com.heitorcampos.androcargps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)

/**
 * Atualização pelo GitHub: cada push no main gera uma release "v1.0.<n>" (GitHub Actions)
 * com o Androcar_Gps.apk. O app compara o <n> com a versão instalada (versionCode = 100 + n).
 */
object Updater {
    const val REPO_URL = "https://github.com/heitorcampos2/Androcar_Gps"
    private const val LATEST_API = "https://api.github.com/repos/heitorcampos2/Androcar_Gps/releases/latest"
    private const val APK_URL = "$REPO_URL/releases/latest/download/Androcar_Gps.apk"

    /** Devolve a versão nova, ou null se já está na mais recente. Rodar fora da thread principal. */
    fun check(): UpdateInfo? {
        val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            useCaches = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Androcar-GPS")
        }
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = j.optString("tag_name").removePrefix("v")          // "1.0.7"
            val run = tag.substringAfterLast('.').toIntOrNull() ?: return null
            val code = 100 + run
            if (code <= BuildConfig.VERSION_CODE) return null
            return UpdateInfo(code, tag, APK_URL, j.optString("body"))
        } finally {
            conn.disconnect()
        }
    }

    fun download(c: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val dir = File(c.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "Androcar_Gps.apk")
        var url = URL(info.apkUrl)
        var conn: HttpURLConnection
        // O GitHub redireciona algumas vezes até o arquivo; segue manualmente para garantir.
        var hops = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 20000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Androcar-GPS")
            }
            val code = conn.responseCode
            if (code in 300..399 && hops < 6) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("redirecionamento sem destino")
                conn.disconnect()
                url = URL(url, loc)
                hops++
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("HTTP $code")
            }
            break
        }
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) {
                                last = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
            return file
        } finally {
            conn.disconnect()
        }
    }

    /** Abre o instalador. Devolve false se precisou pedir a permissão de instalar apps primeiro. */
    fun install(c: Context, apk: File): Boolean {
        if (!c.packageManager.canRequestPackageInstalls()) {
            c.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", apk)
        c.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }
}
