package com.andres.tvfacil

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla "Partido del Barça": lee partido.json de GitHub (lo actualiza una tarea
 * programada) y ofrece abrir Disney+ o DGO con la VPN correcta.
 * Devuelve a MainActivity el paquete de la app elegida en EXTRA_PKG.
 */
class PartidoActivity : Activity() {

    companion object {
        const val URL_JSON = "https://raw.githubusercontent.com/aleogp/TVFacil/main/partido.json"
        const val EXTRA_PKG = "pkg"
        const val PKG_DISNEY = "com.disney.disneyplus"
        const val PKG_DGO = "com.directv.dtvlatam"
        private const val PREFS = "partido"
        private const val KEY_CACHE = "cache"
    }

    private lateinit var info: TextView
    private lateinit var botones: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(0xFF101820.toInt())
        root.setPadding(dp(40), dp(30), dp(40), dp(30))

        val titulo = TextView(this)
        titulo.text = "🔵🔴  Partido del Barça"
        titulo.setTextColor(Color.WHITE)
        titulo.typeface = Typeface.DEFAULT_BOLD
        titulo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
        titulo.gravity = Gravity.CENTER
        root.addView(titulo)

        info = TextView(this)
        info.setTextColor(Color.WHITE)
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        info.gravity = Gravity.CENTER
        info.setPadding(0, dp(24), 0, dp(24))
        info.text = "Buscando el próximo partido…"
        root.addView(info)

        botones = LinearLayout(this)
        botones.orientation = LinearLayout.HORIZONTAL
        botones.gravity = Gravity.CENTER
        root.addView(botones)

        setContentView(root)

        Thread {
            val json = descargar() ?: leerCache()
            runOnUiThread { mostrar(json) }
        }.start()
    }

    private fun descargar(): JSONObject? = try {
        val c = URL("$URL_JSON?t=${System.currentTimeMillis() / 60000}").openConnection() as HttpURLConnection
        c.connectTimeout = 7000
        c.readTimeout = 7000
        val text = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val o = JSONObject(text)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CACHE, text).apply()
        o
    } catch (e: Exception) {
        null
    }

    private fun leerCache(): JSONObject? = try {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CACHE, null)?.let { JSONObject(it) }
    } catch (e: Exception) {
        null
    }

    /** Primer partido que no haya terminado (inicio + 2,5 h). */
    private fun proximo(json: JSONObject?): JSONObject? {
        val arr = json?.optJSONArray("partidos") ?: return null
        val ahora = System.currentTimeMillis() / 1000
        var mejor: JSONObject? = null
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val ep = p.optLong("epoch", 0)
            if (ep == 0L || ep + 9000 < ahora) continue
            if (mejor == null || ep < mejor.optLong("epoch")) mejor = p
        }
        return mejor
    }

    private fun mostrar(json: JSONObject?) {
        botones.removeAllViews()
        val p = proximo(json)
        if (p == null) {
            info.text = "Todavía no tengo el próximo partido.\n\nPrueben primero en Disney;\nsi no está, en DGO."
            agregarBoton("▶  Probar en Disney", 0xFF1E88E5, PKG_DISNEY, true)
            agregarBoton("▶  Probar en DGO", 0xFFF9A825, PKG_DGO, false)
            agregarBoton("Volver", 0xFF546E7A, null, false)
            return
        }

        val ep = p.optLong("epoch") * 1000
        val fecha = SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale("es", "ES")).format(Date(ep))
        val ahora = System.currentTimeMillis()
        val cuando = when {
            ahora >= ep - 15 * 60_000 -> "⚡ ¡Está jugando ahora!"
            else -> fecha.replaceFirstChar { it.uppercase() } + " (hora de aquí)"
        }
        val rival = p.optString("rival", "?")
        val partido = if (p.optBoolean("local", true)) "Barça vs. $rival" else "$rival vs. Barça"
        val comp = p.optString("competicion", "")
        val app = p.optString("app", "").lowercase()
        val canal = p.optString("canal", "")

        val donde = when (app) {
            "disney" -> "Se ve en: DISNEY" + (if (canal.isNotBlank()) " ($canal)" else "")
            "dgo" -> "Se ve en: DGO" + (if (canal.isNotBlank()) "\nDentro de DGO busquen: $canal" else "")
            else -> "Todavía no sé el canal.\nPrueben primero en Disney; si no está, en DGO."
        }
        info.text = "$partido\n$comp\n$cuando\n\n$donde"

        when (app) {
            "disney" -> {
                agregarBoton("▶  Ver ahora en Disney", 0xFF1E88E5, PKG_DISNEY, true)
                agregarBoton("Probar en DGO", 0xFF546E7A, PKG_DGO, false)
            }
            "dgo" -> {
                agregarBoton("▶  Ver ahora en DGO", 0xFFF9A825, PKG_DGO, true)
                agregarBoton("Probar en Disney", 0xFF546E7A, PKG_DISNEY, false)
            }
            else -> {
                agregarBoton("▶  Probar en Disney", 0xFF1E88E5, PKG_DISNEY, true)
                agregarBoton("▶  Probar en DGO", 0xFFF9A825, PKG_DGO, false)
            }
        }
        agregarBoton("Volver", 0xFF546E7A, null, false)
    }

    private fun agregarBoton(texto: String, color: Long, pkg: String?, foco: Boolean) {
        val b = TextView(this)
        b.text = texto
        b.gravity = Gravity.CENTER
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        b.typeface = Typeface.DEFAULT_BOLD
        val c = color.toInt()
        val light = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) > 170
        b.setTextColor(if (light) Color.BLACK else Color.WHITE)
        b.setPadding(dp(32), dp(20), dp(32), dp(20))
        b.isFocusable = true
        b.isClickable = true
        val normal = GradientDrawable().apply { setColor(c); cornerRadius = dp(24).toFloat() }
        val focused = GradientDrawable().apply {
            setColor(c); cornerRadius = dp(24).toFloat(); setStroke(dp(7), Color.WHITE)
        }
        b.background = normal
        b.setOnFocusChangeListener { v, has ->
            v.background = if (has) focused else normal
            v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(120).start()
        }
        b.setOnClickListener {
            if (pkg == null) {
                setResult(RESULT_CANCELED)
            } else {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_PKG, pkg))
            }
            finish()
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(16), 0, dp(16), 0)
        botones.addView(b, lp)
        if (foco) b.post { b.requestFocus() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Suppress("unused")
    private fun ocultar(v: View) { v.visibility = View.GONE }
}
