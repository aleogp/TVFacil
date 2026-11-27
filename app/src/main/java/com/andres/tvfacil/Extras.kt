package com.andres.tvfacil

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Funciones "de familia":
 *  - Mensajes y recordatorios (mensajes.json en GitHub)
 *  - Botón de ayuda: avisa al celular de Andrés por ntfy
 *  - Señal de vida: avisa a healthchecks.io que la tele se está usando
 *  - Clima de Calonge (open-meteo, sin clave)
 * Todo lo de red se llama en un hilo aparte.
 */
object Extras {
    private const val REPO = "aleogp/TVFacil"
    private const val URL_MENSAJES = "https://raw.githubusercontent.com/$REPO/main/mensajes.json"
    private const val URL_PARTIDO = "https://raw.githubusercontent.com/$REPO/main/partido.json"
    private const val URL_CLIMA = "https://api.open-meteo.com/v1/forecast?latitude=41.86&longitude=3.08" +
        "&current=temperature_2m,weather_code&timezone=Europe%2FMadrid"
    const val CIUDAD = "Calonge"

    private const val PREFS = "extras"
    private val ZONA: ZoneId = ZoneId.systemDefault()
    private val ES = Locale("es", "ES")

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

    private fun throttled(ctx: Context, key: String, everyMs: Long, force: Boolean): Boolean {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong(key, 0) < everyMs) return true
        p.edit().putLong(key, now).apply()
        return false
    }

    // ---------------- Mensajes, recordatorios y partido ----------------

    /** Descarga mensajes.json y partido.json (cada 10 min como mucho). */
    fun syncMensajes(ctx: Context, force: Boolean = false) {
        if (throttled(ctx, "msg_check", 10 * 60_000L, force)) return
        try {
            val t = System.currentTimeMillis() / 60000
            val txt = get("$URL_MENSAJES?t=$t")
            JSONObject(txt) // valida
            prefs(ctx).edit().putString("mensajes", txt).apply()
        } catch (_: Exception) {
        }
        try {
            val t = System.currentTimeMillis() / 60000
            val txt = get("$URL_PARTIDO?t=$t")
            JSONObject(txt)
            // Misma caché que usa la pantalla del partido
            ctx.getSharedPreferences("partido", Context.MODE_PRIVATE).edit().putString("cache", txt).apply()
        } catch (_: Exception) {
        }
    }

    private fun mensajesJson(ctx: Context): JSONObject? = try {
        prefs(ctx).getString("mensajes", null)?.let { JSONObject(it) }
    } catch (_: Exception) {
        null
    }

    private fun parseDate(s: String): LocalDate? = try {
        if (s.isBlank()) null else LocalDate.parse(s.trim())
    } catch (_: Exception) {
        null
    }

    /** Mensajes vigentes hoy: lista de (id, texto). */
    private fun mensajesVigentes(ctx: Context): List<Pair<String, String>> {
        val arr = mensajesJson(ctx)?.optJSONArray("mensajes") ?: return emptyList()
        val hoy = LocalDate.now(ZONA)
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val texto = m.optString("texto").trim()
            if (texto.isEmpty()) continue
            val desde = parseDate(m.optString("desde"))
            val hasta = parseDate(m.optString("hasta"))
            if (desde != null && hoy.isBefore(desde)) continue
            if (hasta != null && hoy.isAfter(hasta)) continue
            val id = m.optString("id").ifBlank { texto.hashCode().toString() }
            out.add(id to texto)
        }
        return out
    }

    /** Mensajes vigentes que todavía no se mostraron en grande. */
    fun mensajesNuevos(ctx: Context): List<Pair<String, String>> {
        val vistos = prefs(ctx).getStringSet("vistos", emptySet()) ?: emptySet()
        return mensajesVigentes(ctx).filter { it.first !in vistos }
    }

    fun marcarVistos(ctx: Context, ids: Collection<String>) {
        val p = prefs(ctx)
        val vistos = (p.getStringSet("vistos", emptySet()) ?: emptySet()).toMutableSet()
        vistos.addAll(ids)
        p.edit().putStringSet("vistos", vistos).apply()
    }

    /** Líneas para la barra de abajo: mensajes + recordatorios de hoy/mañana + partido. */
    fun lineasBarra(ctx: Context): List<String> {
        val out = mutableListOf<String>()
        val hoy = LocalDate.now(ZONA)
        val manana = hoy.plusDays(1)

        mensajesVigentes(ctx).forEach { out.add("📩 ${it.second}") }

        val recs = mensajesJson(ctx)?.optJSONArray("recordatorios")
        if (recs != null) {
            for (i in 0 until recs.length()) {
                val r = recs.optJSONObject(i) ?: continue
                val texto = r.optString("texto").trim()
                if (texto.isEmpty()) continue
                val hora = r.optString("hora").trim()
                // "fecha": "2026-12-03"  o  "cada_anio": "12-25"
                var fecha = parseDate(r.optString("fecha"))
                val anual = r.optString("cada_anio").trim()
                if (fecha == null && anual.matches(Regex("\\d{2}-\\d{2}"))) {
                    fecha = parseDate("${hoy.year}-$anual")
                }
                val cuando = when (fecha) {
                    hoy -> "Hoy"
                    manana -> "Mañana"
                    else -> null
                } ?: continue
                out.add("📅 $cuando" + (if (hora.isNotEmpty()) " $hora" else "") + ": $texto")
            }
        }

        partidoLinea(ctx, hoy, manana)?.let { out.add(it) }
        return out
    }

    private fun partidoLinea(ctx: Context, hoy: LocalDate, manana: LocalDate): String? {
        return try {
            val txt = ctx.getSharedPreferences("partido", Context.MODE_PRIVATE).getString("cache", null) ?: return null
            val arr = JSONObject(txt).optJSONArray("partidos") ?: return null
            val ahora = System.currentTimeMillis() / 1000
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val ep = p.optLong("epoch", 0)
                if (ep == 0L || ep + 9000 < ahora) continue
                val dt = java.time.Instant.ofEpochSecond(ep).atZone(ZONA).toLocalDateTime()
                val dia = when (dt.toLocalDate()) {
                    hoy -> "Hoy"
                    manana -> "Mañana"
                    else -> continue
                }
                val rival = p.optString("rival", "?")
                val app = when (p.optString("app").lowercase()) {
                    "disney" -> " · por Disney"
                    "dgo" -> " · por DGO"
                    else -> ""
                }
                val hora = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                return "⚽ $dia $hora juega el Barça contra $rival$app"
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // ---------------- Clima ----------------

    /** Actualiza el clima (cada 30 min como mucho). */
    fun syncClima(ctx: Context) {
        if (throttled(ctx, "clima_check", 30 * 60_000L, false)) return
        try {
            val cur = JSONObject(get(URL_CLIMA)).getJSONObject("current")
            val temp = Math.round(cur.getDouble("temperature_2m"))
            val emoji = emojiClima(cur.optInt("weather_code", -1))
            prefs(ctx).edit().putString("clima", "$emoji $temp°").apply()
        } catch (_: Exception) {
        }
    }

    fun clima(ctx: Context): String = prefs(ctx).getString("clima", "") ?: ""

    private fun emojiClima(code: Int) = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌦️"
        in 85..86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun fechaLarga(): String {
        val s = LocalDate.now(ZONA).format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES))
        return s.replaceFirstChar { it.uppercase() }
    }

    fun hora(): String = LocalDateTime.now(ZONA).format(DateTimeFormatter.ofPattern("HH:mm"))

    // ---------------- Botón de ayuda (ntfy) ----------------

    fun ayudaConfigurada() = BuildConfig.NTFY_TOPIC.isNotBlank()

    /** Manda la notificación de ayuda al celular de Andrés. Devuelve true si salió. */
    fun pedirAyuda(): Boolean {
        val topic = BuildConfig.NTFY_TOPIC
        if (topic.isBlank()) return false
        return try {
            val c = URL("https://ntfy.sh/$topic").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Title", "Tus papas piden ayuda con la tele")
            c.setRequestProperty("Priority", "high")
            c.setRequestProperty("Tags", "sos,tv")
            val body = "Tocaron 'Llamar a Andrés' en TV Fácil (${hora()} hora de España). Se abrió AnyDesk: entra cuando puedas."
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode in 200..299
            c.disconnect()
            ok
        } catch (_: Exception) {
            false
        }
    }

    // ---------------- Señal de vida (healthchecks.io) ----------------

    /** Avisa que la tele se está usando (cada hora como mucho). */
    fun senalDeVida(ctx: Context) {
        val url = BuildConfig.HC_PING_URL
        if (url.isBlank()) return
        if (throttled(ctx, "hc_ping", 60 * 60_000L, false)) return
        try {
            get(url)
        } catch (_: Exception) {
            prefs(ctx).edit().putLong("hc_ping", 0).apply() // reintentar la próxima vez
        }
    }
}
