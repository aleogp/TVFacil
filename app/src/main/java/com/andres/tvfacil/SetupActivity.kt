package com.andres.tvfacil

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Pantalla escondida para Andrés: se abre con la tecla Menú (≡) 5 veces. */
class SetupActivity : Activity() {

    private lateinit var list: ListView
    private var buttons: MutableList<TvButton> = mutableListOf()

    private val headerRows = listOf(
        "➕  Agregar botón",
        "🔐  Dar permiso para controlar WireGuard",
        "🛡  Abrir WireGuard",
        "↩  Restaurar botones originales",
        "✅  Listo (volver)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = ListView(this)
        val pad = (24 * resources.displayMetrics.density).toInt()
        list.setPadding(pad, pad, pad, pad)
        list.setOnItemClickListener { _, _, pos, _ -> onRow(pos) }
        setContentView(list)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        buttons = Config.load(this)
        val rows = headerRows.toMutableList()
        buttons.forEachIndexed { i, b ->
            val detail = when (b.action) {
                "retry" -> "botón de reintento"
                "reboot" -> "instrucciones para reiniciar"
                else -> "VPN: ${b.tunnel.ifBlank { "ninguna" }}  ·  App: ${appName(b.pkg)}"
            }
            rows.add("${i + 1}.  ${b.emoji}  ${b.label}      —  $detail")
        }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup) =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                }
        }
        list.adapter = adapter
    }

    private fun onRow(pos: Int) {
        when (pos) {
            0 -> {
                buttons.add(TvButton("Nuevo", "⭐", 0xFF00ACC1))
                save(); editButton(buttons.size - 1)
            }
            1 -> {
                if (!Wg.installed(this)) toast("Primero instala WireGuard")
                else if (Wg.hasPermission(this)) toast("El permiso ya está dado ✔")
                else if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(Wg.PERM), 7)
            }
            2 -> {
                val i = packageManager.getLeanbackLaunchIntentForPackage(Wg.PKG)
                    ?: packageManager.getLaunchIntentForPackage(Wg.PKG)
                if (i != null) startActivity(i) else toast("WireGuard no está instalado")
            }
            3 -> confirm("¿Volver a los botones originales?") { Config.reset(this); refresh() }
            4 -> finish()
            else -> editButton(pos - headerRows.size)
        }
    }

    private fun editButton(i: Int) {
        val b = buttons.getOrNull(i) ?: return
        val options = arrayOf(
            "Nombre: ${b.label}",
            "Emoji: ${b.emoji}",
            "Color",
            "App que abre: ${appName(b.pkg)}",
            "VPN: ${b.tunnel.ifBlank { "ninguna" }}",
            "Tipo: ${tipo(b.action)}",
            "⬆ Mover antes",
            "🗑 Borrar botón"
        )
        AlertDialog.Builder(this)
            .setTitle("${b.emoji}  ${b.label}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> askText("Nombre del botón", b.label) { b.label = it; saveAndReopen(i) }
                    1 -> askText("Emoji (o una letra)", b.emoji) { b.emoji = it; saveAndReopen(i) }
                    2 -> pickColor(b, i)
                    3 -> pickApp(b, i)
                    4 -> pickTunnel(b, i)
                    5 -> pickType(b, i)
                    6 -> if (i > 0) {
                        buttons.add(i - 1, buttons.removeAt(i)); save(); refresh(); editButton(i - 1)
                    }
                    7 -> confirm("¿Borrar \"${b.label}\"?") { buttons.removeAt(i); save(); refresh() }
                }
            }
            .setNegativeButton("Cerrar") { _, _ -> refresh() }
            .show()
    }

    private fun pickColor(b: TvButton, i: Int) {
        val names = Config.COLORS.keys.toTypedArray()
        AlertDialog.Builder(this).setTitle("Color")
            .setItems(names) { _, w -> b.color = Config.COLORS[names[w]]!!; saveAndReopen(i) }
            .show()
    }

    private fun pickType(b: TvButton, i: Int) {
        val keys = arrayOf("app", "retry", "reboot")
        AlertDialog.Builder(this).setTitle("Tipo de botón")
            .setItems(keys.map { tipo(it) }.toTypedArray()) { _, w -> b.action = keys[w]; saveAndReopen(i) }
            .show()
    }

    private fun pickTunnel(b: TvButton, i: Int) {
        val known = (Config.tunnels(buttons) + listOf("colombia", "mexico", "usa")).distinct()
        val items = listOf("Sin VPN") + known + listOf("Otro nombre…")
        AlertDialog.Builder(this)
            .setTitle("VPN (debe llamarse IGUAL que el túnel en WireGuard)")
            .setItems(items.toTypedArray()) { _, w ->
                when (w) {
                    0 -> { b.tunnel = ""; saveAndReopen(i) }
                    items.size - 1 -> askText("Nombre exacto del túnel", b.tunnel) { b.tunnel = it; saveAndReopen(i) }
                    else -> { b.tunnel = items[w]; saveAndReopen(i) }
                }
            }
            .show()
    }

    private fun pickApp(b: TvButton, i: Int) {
        val apps = launchableApps()
        val labels = apps.map { "${it.second}   (${it.first})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("¿Qué app abre este botón?")
            .setItems(labels) { _, w ->
                b.pkg = apps[w].first
                if (b.action != "app") b.action = "app"
                saveAndReopen(i)
            }
            .show()
    }

    /** Lista de (paquete, nombre) de todas las apps que se pueden abrir. */
    private fun launchableApps(): List<Pair<String, String>> {
        val pm = packageManager
        val result = linkedMapOf<String, String>()
        val cats = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (cat in cats) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName) continue
                result.getOrPut(pkg) { ri.loadLabel(pm).toString() }
            }
        }
        return result.entries.map { it.key to it.value }.sortedBy { it.second.lowercase() }
    }

    private fun appName(pkg: String): String {
        if (pkg.isBlank()) return "⚠ sin elegir"
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            "⚠ no instalada ($pkg)"
        }
    }

    private fun tipo(a: String) = when (a) {
        "retry" -> "Reintentar (\"No funciona\")"
        "reboot" -> "Instrucciones para reiniciar"
        else -> "Abrir una app"
    }

    // ---------- utilidades ----------

    private fun save() = Config.save(this, buttons)

    private fun saveAndReopen(i: Int) {
        save(); refresh(); editButton(i)
    }

    private fun askText(title: String, current: String, onOk: (String) -> Unit) {
        val input = EditText(this)
        input.setText(current)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setSelection(current.length)
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("OK") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty() || title.startsWith("Nombre exacto")) onOk(t)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirm(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton("Sí") { _, _ -> onYes() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
