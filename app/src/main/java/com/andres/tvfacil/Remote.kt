package com.andres.tvfacil

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cosas que llegan desde GitHub, para que Andrés no tenga que entrar al Fire TV:
 *  1. config.json  -> los botones. Se aplica solo si su "version" es MAYOR que la última aplicada.
 *  2. Releases     -> si hay un APK más nuevo, se descarga y Android pide confirmar "Instalar".
 */
object Remote {
    private const val REPO = "aleogp/TVFacil"
    const val URL_CONFIG = "https://raw.githubusercontent.com/$REPO/main/config.json"
    private const val URL_RELEASE = "https://api.github.com/repos/$REPO/releases/latest"
    const val ACTION_INSTALL_STATUS = "com.andres.tvfacil.INSTALL_STATUS"

    private const val PREFS = "remote"
    private const val KEY_CFG_VERSION = "cfg_version"
    private const val KEY_CFG_CHECK = "cfg_check"
    private const val KEY_UPD_CHECK = "upd_check"
    private const val KEY_SKIP_TAG = "skip_tag"
    private const val KEY_SKIP_UNTIL = "skip_until"

    private const val CFG_EVERY = 10 * 60_000L        // config.json: cada 10 min como mucho
    private const val UPD_EVERY = 6 * 60 * 60_000L    // actualizaciones: cada 6 h como mucho
    private const val SKIP_FOR = 24 * 60 * 60_000L    // "ahora no": no volver a preguntar en 1 día

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "TVFacil")
        try {
            if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // ---------------- 1. Configuración remota ----------------

    fun appliedConfigVersion(ctx: Context) = prefs(ctx).getInt(KEY_CFG_VERSION, 0)

    /**
     * Descarga config.json y, si trae una versión nueva, reemplaza los botones.
     * Devuelve true si cambió algo. Llamar en un hilo aparte.
     */
    fun syncConfig(ctx: Context, force: Boolean = false): Boolean {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong(KEY_CFG_CHECK, 0) < CFG_EVERY) return false
        p.edit().putLong(KEY_CFG_CHECK, now).apply()
        return try {
            val o = JSONObject(get("$URL_CONFIG?t=${now / 60000}"))
            val version = o.optInt("version", 0)
            if (version <= appliedConfigVersion(ctx)) return false
            val arr = o.optJSONArray("botones") ?: return false
            val list = MutableList(arr.length()) { TvButton.fromJson(arr.getJSONObject(it)) }
            if (list.isEmpty() || list.any { it.label.isBlank() }) return false
            Config.save(ctx, list)
            Config.setLast(ctx, -1)
            p.edit().putInt(KEY_CFG_VERSION, version).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** La configuración actual en el mismo formato que config.json (para copiarla). */
    fun configJson(list: List<TvButton>, version: Int): String {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject()
                    .put("label", b.label).put("emoji", b.emoji)
                    .put("color", String.format("#%06X", b.color and 0xFFFFFFL))
                    .put("action", b.action).put("tunnel", b.tunnel).put("pkg", b.pkg)
            )
        }
        return JSONObject().put("version", version).put("botones", arr).toString(2)
    }

    // ---------------- 2. Actualizaciones ----------------

    data class Update(val tag: String, val apkUrl: String)

    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** "v7.15" > "7.9" -> compara número por número. */
    fun isNewer(tag: String, current: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(tag)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Busca en GitHub si hay una versión más nueva. Llamar en un hilo aparte.
     * Con force=false respeta las esperas (cada 6 h, y "ahora no" por un día).
     */
    fun checkUpdate(ctx: Context, force: Boolean = false): Update? {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong(KEY_UPD_CHECK, 0) < UPD_EVERY) return null
        p.edit().putLong(KEY_UPD_CHECK, now).apply()
        return try {
            val o = JSONObject(get(URL_RELEASE))
            val tag = o.optString("tag_name")
            if (tag.isBlank() || !isNewer(tag, currentVersion(ctx))) return null
            if (!force && tag == p.getString(KEY_SKIP_TAG, null) && now < p.getLong(KEY_SKIP_UNTIL, 0)) return null
            val assets = o.optJSONArray("assets") ?: return null
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break }
            }
            if (url.isNullOrBlank()) null else Update(tag, url)
        } catch (e: Exception) {
            null
        }
    }

    fun skip(ctx: Context, tag: String) {
        prefs(ctx).edit().putString(KEY_SKIP_TAG, tag)
            .putLong(KEY_SKIP_UNTIL, System.currentTimeMillis() + SKIP_FOR).apply()
    }

    /** ¿Android deja que TV Fácil instale APKs? (Fire OS 7+ pide un permiso por app). */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Abre el ajuste para permitir que TV Fácil instale apps. Devuelve false si no se pudo. */
    fun openInstallPermission(act: Activity): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        return try {
            act.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + act.packageName))
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Descarga el APK y lo entrega al instalador de Android. Llamar en un hilo aparte.
     * El resultado llega a MainActivity (onNewIntent) con la acción ACTION_INSTALL_STATUS;
     * ahí se abre la ventana de "Instalar" del sistema.
     */
    fun downloadAndInstall(ctx: Context, u: Update) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val c = URL(u.apkUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "TVFacil")
            try {
                if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}")
                val len = c.contentLength.toLong() // -1 si no se sabe (contentLengthLong es API 24+)
                c.inputStream.use { input ->
                    session.openWrite("TVFacil.apk", 0, len).use { out ->
                        input.copyTo(out, 64 * 1024)
                        session.fsync(out)
                    }
                }
            } finally {
                c.disconnect()
            }
            val intent = Intent(ctx, MainActivity::class.java).setAction(ACTION_INSTALL_STATUS)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getActivity(ctx, 7, intent, flags)
            session.commit(pi.intentSender)
            session.close()
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }
}
