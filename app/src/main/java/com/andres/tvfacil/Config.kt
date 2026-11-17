package com.andres.tvfacil

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un botón de la pantalla principal.
 *  action = "app"     -> pone la VPN indicada (o ninguna) y abre la app [pkg]
 *  action = "retry"   -> repite el último botón con más tiempo de espera
 *  action = "reboot"  -> muestra cómo reiniciar el Fire Stick con el control
 *  action = "barca"   -> pantalla del próximo partido del Barça (lee partido.json)
 *  tunnel = nombre de la VPN = archivo assets/vpn/<nombre>.conf ("" = sin VPN)
 */
data class TvButton(
    var label: String,
    var emoji: String,
    var color: Long,
    var action: String = "app",
    var tunnel: String = "",
    var pkg: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("label", label).put("emoji", emoji).put("color", color)
        .put("action", action).put("tunnel", tunnel).put("pkg", pkg)

    companion object {
        fun fromJson(o: JSONObject) = TvButton(
            o.optString("label"), o.optString("emoji"), o.optLong("color", 0xFF1565C0),
            o.optString("action", "app"), o.optString("tunnel"), o.optString("pkg")
        )
    }
}

object Config {
    private const val PREFS = "tvfacil"
    private const val KEY_BUTTONS = "buttons"
    const val KEY_LAST = "last"

    val COLORS = linkedMapOf(
        "Azul" to 0xFF1E88E5, "Rojo" to 0xFFC62828, "Amarillo" to 0xFFF9A825,
        "Verde" to 0xFF2E7D32, "Naranja" to 0xFFEF6C00, "Morado" to 0xFF6A1B9A,
        "Gris" to 0xFF546E7A, "Rosado" to 0xFFD81B60, "Celeste" to 0xFF00ACC1, "Granate" to 0xFFA50044
    )

    fun defaults(): MutableList<TvButton> = mutableListOf(
        TvButton("Partido del Barça", "🔵🔴", 0xFFA50044, "barca"),
        TvButton("Disney y Fútbol", "⚽", 0xFF1E88E5, "app", "colombia", "com.disney.disneyplus"),
        TvButton("Netflix", "🎬", 0xFFC62828, "app", "", "com.netflix.ninja"),
        TvButton("TV Colombia", "📺", 0xFFF9A825, "app", "colombia", "com.directv.dtvlatam"),
        TvButton("Canales", "📡", 0xFF2E7D32, "app", "usa", "com.kai.tv"),
        TvButton("No funciona", "🔧", 0xFFEF6C00, "retry"),
        TvButton("Llamar a Andrés", "📞", 0xFF6A1B9A, "app", "", "com.anydesk.anydeskandroid"),
        TvButton("Reiniciar", "🔄", 0xFF546E7A, "reboot")
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): MutableList<TvButton> {
        val raw = prefs(ctx).getString(KEY_BUTTONS, null) ?: return defaults()
        val list = try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { TvButton.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            return defaults()
        }
        // v3: KAI pasa de México a EE. UU. (una sola vez, en aparatos ya configurados)
        val p = prefs(ctx)
        if (!p.getBoolean("migr_kai_usa", false)) {
            list.filter { it.pkg == "com.kai.tv" && it.tunnel == "mexico" }.forEach { it.tunnel = "usa" }
            save(ctx, list)
            p.edit().putBoolean("migr_kai_usa", true).apply()
        }
        // v4: botón "Partido del Barça" al principio (una sola vez)
        if (!p.getBoolean("migr_barca", false)) {
            if (list.none { it.action == "barca" }) {
                list.add(0, TvButton("Partido del Barça", "🔵🔴", 0xFFA50044, "barca"))
                save(ctx, list)
            }
            p.edit().putBoolean("migr_barca", true).apply()
        }
        return list
    }

    fun save(ctx: Context, list: List<TvButton>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_BUTTONS, arr.toString()).apply()
    }

    fun reset(ctx: Context) = prefs(ctx).edit().remove(KEY_BUTTONS).remove(KEY_LAST).apply()

    /** Todos los túneles que usa algún botón (para apagarlos antes de cambiar). */
    fun tunnels(list: List<TvButton>): List<String> =
        list.map { it.tunnel.trim() }.filter { it.isNotEmpty() }.distinct()

    fun getLast(ctx: Context) = prefs(ctx).getInt(KEY_LAST, -1)
    fun setLast(ctx: Context, i: Int) = prefs(ctx).edit().putInt(KEY_LAST, i).apply()
}
