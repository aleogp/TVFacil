package com.andres.tvfacil

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var grid: GridLayout
    private lateinit var overlay: TextView
    private lateinit var photo: ImageView
    private var buttons: MutableList<TvButton> = mutableListOf()
    private val buttonViews = mutableListOf<View>()

    private enum class Mode { NORMAL, BUSY, MESSAGE }
    private var mode = Mode.NORMAL
    private var messageShownAt = 0L

    // Código secreto: tecla Menú (≡) 5 veces seguidas abre la configuración.
    private var menuPresses = 0
    private var firstMenuAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        root.setBackgroundColor(0xFF101820.toInt())

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL

        val title = TextView(this)
        title.text = "¿Qué quieren ver?"
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        // En celular (sin tecla Menú): mantener presionado el título abre la configuración.
        title.setOnLongClickListener { openSetup(); true }

        // Encabezado: foto de la nieta (rota en cada visita) + título
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER
        header.setPadding(0, dp(12), 0, dp(6))
        photo = ImageView(this)
        photo.visibility = View.GONE
        val plp = LinearLayout.LayoutParams(dp(88), dp(88))
        plp.rightMargin = dp(20)
        header.addView(photo, plp)
        header.addView(title)
        column.addView(header)

        grid = GridLayout(this)
        column.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        overlay = TextView(this)
        overlay.setBackgroundColor(0xF0000000.toInt())
        overlay.setTextColor(Color.WHITE)
        overlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
        overlay.gravity = Gravity.CENTER
        overlay.setPadding(dp(48), dp(48), dp(48), dp(48))
        overlay.visibility = View.GONE
        overlay.isClickable = true // en celular: tocar para cerrar mensajes
        overlay.setOnClickListener {
            if (mode == Mode.MESSAGE && SystemClock.uptimeMillis() - messageShownAt > 1000) hideOverlay()
        }
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        buttons = Config.load(this)
        buildGrid()
        showNextPhoto()
    }

    override fun onPause() {
        super.onPause()
        // Si salen de la app mientras conecta, no dejamos tareas pendientes a medias.
        if (mode == Mode.MESSAGE) hideOverlay()
    }

    // ---------- Pantalla de botones ----------

    private fun buildGrid() {
        grid.removeAllViews()
        buttonViews.clear()
        val n = buttons.size
        val cols = when {
            n <= 4 -> 2
            n <= 6 -> 3
            else -> 4
        }
        val rows = (n + cols - 1) / cols
        grid.columnCount = cols

        val dm = resources.displayMetrics
        val margin = dp(12)
        val usableW = dm.widthPixels - dp(40)
        val usableH = dm.heightPixels - dp(110)
        val w = usableW / cols - 2 * margin
        val h = minOf(usableH / rows - 2 * margin, (w * 0.8).toInt())

        buttons.forEachIndexed { index, b ->
            val v = makeButtonView(b, w, h)
            v.setOnClickListener { onButton(index) }
            val lp = GridLayout.LayoutParams()
            lp.width = w
            lp.height = h
            lp.setMargins(margin, margin, margin, margin)
            grid.addView(v, lp)
            buttonViews.add(v)
        }

        val last = Config.getLast(this)
        val target = if (last in buttonViews.indices) last else 0
        buttonViews.getOrNull(target)?.post { buttonViews.getOrNull(target)?.requestFocus() }
    }

    private fun makeButtonView(b: TvButton, w: Int, h: Int): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.isFocusable = true
        box.isClickable = true
        box.setPadding(dp(8), dp(8), dp(8), dp(8))

        val color = b.color.toInt()
        val normal = GradientDrawable().apply {
            setColor(color); cornerRadius = dp(28).toFloat()
        }
        val focused = GradientDrawable().apply {
            setColor(color); cornerRadius = dp(28).toFloat()
            setStroke(dp(8), Color.WHITE)
        }
        box.background = normal
        box.setOnFocusChangeListener { v, has ->
            v.background = if (has) focused else normal
            v.animate().scaleX(if (has) 1.07f else 1f).scaleY(if (has) 1.07f else 1f)
                .setDuration(120).start()
            if (Build.VERSION.SDK_INT >= 21) v.elevation = if (has) dp(10).toFloat() else 0f
        }

        val textColor = if (isLight(color)) Color.BLACK else Color.WHITE
        val emoji = TextView(this)
        emoji.text = b.emoji
        emoji.gravity = Gravity.CENTER
        emoji.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.32f)
        box.addView(emoji)

        val label = TextView(this)
        label.text = b.label
        label.gravity = Gravity.CENTER
        label.setTextColor(textColor)
        label.typeface = Typeface.DEFAULT_BOLD
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, minOf(h * 0.15f, w * 0.11f))
        box.addView(label)
        return box
    }

    // ---------- Acciones ----------

    private fun onButton(index: Int) {
        if (mode != Mode.NORMAL) return
        val b = buttons.getOrNull(index) ?: return
        when (b.action) {
            "retry" -> {
                val last = Config.getLast(this)
                val lb = buttons.getOrNull(last)
                if (lb == null || lb.action != "app") {
                    showMessage("Primero elijan lo que quieren ver.\n\n(Aprieten OK para volver)")
                } else {
                    runApp(lb, retry = true)
                }
            }
            "barca" -> startActivityForResult(Intent(this, PartidoActivity::class.java), REQ_PARTIDO)
            "reboot" -> showMessage(
                "🔄  Para reiniciar el Fire Stick:\n\n" +
                    "Mantengan apretados AL MISMO TIEMPO\n" +
                    "el botón del CENTRO ⭕  y  el botón ▶⏸\n" +
                    "durante 5 segundos, hasta que la pantalla se apague.\n\n" +
                    "(Aprieten OK para volver)"
            )
            else -> {
                Config.setLast(this, index)
                runApp(b, retry = false)
            }
        }
    }

    private fun runApp(b: TvButton, retry: Boolean) {
        if (b.pkg.isBlank()) {
            showMessage("Este botón todavía no está configurado.\n\nLlamen a Andrés 📞\n\n(Aprieten OK para volver)")
            return
        }
        val tunnel = b.tunnel.trim()

        if (tunnel.isNotEmpty() && tunnel !in Vpn.available(this)) {
            showMessage("Falta la VPN \"$tunnel\" en la app.\n\nLlamen a Andrés 📞\n\n(Aprieten OK para volver)")
            return
        }
        if (tunnel.isNotEmpty()) {
            val perm = Vpn.permissionIntent(this)
            if (perm != null) {
                // Aviso de Android que se acepta una sola vez
                pendingIndex = buttons.indexOf(b)
                pendingRetry = retry
                startActivityForResult(perm, REQ_VPN)
                return
            }
        }

        mode = Mode.BUSY
        val pais = tunnel.replaceFirstChar { it.uppercase() }
        showOverlay(
            if (tunnel.isNotEmpty()) "⏳\n\nConectando con $pais…\n\nEsperen un momento, no aprieten nada."
            else "⏳\n\nAbriendo ${b.label}…"
        )

        val started = SystemClock.uptimeMillis()
        Thread {
            var error: Exception? = null
            try {
                if (retry) { Vpn.switchTo(this, null); Thread.sleep(1500) }
                Vpn.switchTo(this, tunnel.ifEmpty { null })
            } catch (e: Exception) {
                error = e
            }
            // Dejamos que la conexión se asiente antes de abrir la app
            val minWait = when {
                tunnel.isEmpty() -> 800L
                retry -> 5000L
                else -> 3000L
            }
            val wait = (minWait - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0)
            handler.postDelayed({
                hideOverlay()
                if (error != null) {
                    showMessage("No se pudo conectar la VPN.\n\nPrueben el botón 🔧 No funciona,\no llamen a Andrés 📞\n\n(Aprieten OK para volver)")
                } else if (!launch(b.pkg)) {
                    // La app no está instalada: abrimos la tienda de Amazon en esa app
                    if (!openStore(b.pkg)) {
                        showMessage("No encuentro la app de \"${b.label}\".\n\nLlamen a Andrés 📞\n\n(Aprieten OK para volver)")
                    }
                }
            }, wait)
        }.start()
    }

    private var pendingIndex = -1
    private var pendingRetry = false

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PARTIDO) {
            val pkg = data?.getStringExtra(PartidoActivity.EXTRA_PKG)
            if (resultCode != RESULT_OK || pkg == null) return
            // Usamos el botón que ya abre esa app (con su VPN); si no existe, Colombia
            val idx = buttons.indexOfFirst { it.action == "app" && it.pkg == pkg }
            val b = buttons.getOrNull(idx) ?: TvButton("Partido", "⚽", 0xFFA50044, "app", "colombia", pkg)
            if (idx >= 0) Config.setLast(this, idx)
            handler.post { runApp(b, false) }
            return
        }
        if (requestCode != REQ_VPN) return
        val b = buttons.getOrNull(pendingIndex)
        pendingIndex = -1
        if (resultCode == RESULT_OK && b != null) {
            handler.post { runApp(b, pendingRetry) }
        } else {
            showMessage("Hay que aceptar el aviso de conexión\npara que funcione.\n\nVuelvan a intentarlo y elijan \"Aceptar\".\n\n(Aprieten OK para volver)")
        }
    }

    private fun launch(pkg: String): Boolean {
        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent); true
        } catch (e: Exception) {
            false
        }
    }

    /** Abre la ficha de la app en la tienda (Amazon primero, luego Google Play). */
    private fun openStore(pkg: String): Boolean {
        val uris = listOf("amzn://apps/android?p=$pkg", "market://details?id=$pkg")
        for (u in uris) {
            try {
                val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    // ---------- Foto de la nieta ----------

    private fun showNextPhoto() {
        val files = try {
            (assets.list("fotos") ?: emptyArray()).filter { it.endsWith(".jpg") || it.endsWith(".png") }.sorted()
        } catch (e: Exception) {
            emptyList()
        }
        if (files.isEmpty()) { photo.visibility = View.GONE; return }
        val prefs = getSharedPreferences("tvfacil", MODE_PRIVATE)
        val i = (prefs.getInt("foto", -1) + 1) % files.size
        prefs.edit().putInt("foto", i).apply()
        val bmp = try {
            assets.open("fotos/" + files[i]).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } ?: return
        photo.setImageBitmap(circle(bmp))
        photo.visibility = View.VISIBLE
    }

    private fun circle(src: Bitmap): Bitmap {
        val s = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        c.drawCircle(s / 2f, s / 2f, s / 2f, paint)
        val border = Paint(Paint.ANTI_ALIAS_FLAG)
        border.style = Paint.Style.STROKE
        border.color = Color.WHITE
        border.strokeWidth = s * 0.04f
        c.drawCircle(s / 2f, s / 2f, s / 2f - s * 0.02f, border)
        return out
    }

    // ---------- Mensajes en pantalla completa ----------

    private fun showOverlay(text: String) {
        overlay.text = text
        overlay.visibility = View.VISIBLE
    }

    private fun showMessage(text: String) {
        mode = Mode.MESSAGE
        messageShownAt = SystemClock.uptimeMillis()
        showOverlay(text)
    }

    private fun hideOverlay() {
        overlay.visibility = View.GONE
        mode = Mode.NORMAL
        val last = Config.getLast(this)
        buttonViews.getOrNull(if (last in buttonViews.indices) last else 0)?.requestFocus()
    }

    // ---------- Teclas del control ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU) {
            val now = SystemClock.uptimeMillis()
            if (now - firstMenuAt > 4000) { firstMenuAt = now; menuPresses = 0 }
            menuPresses++
            if (menuPresses >= 5) { menuPresses = 0; openSetup() }
            return true
        }
        return when (mode) {
            Mode.BUSY -> true // mientras conecta, ignoramos todo
            Mode.MESSAGE -> {
                // Esperamos 1 s para que un toque doble no lo cierre sin leerlo
                if (event.action == KeyEvent.ACTION_UP &&
                    SystemClock.uptimeMillis() - messageShownAt > 1000) hideOverlay()
                true
            }
            Mode.NORMAL -> super.dispatchKeyEvent(event)
        }
    }

    override fun onBackPressed() {
        if (mode != Mode.NORMAL) return
        super.onBackPressed()
    }

    // ---------- Varios ----------

    private fun openSetup() {
        if (mode == Mode.BUSY) return
        startActivity(Intent(this, SetupActivity::class.java))
    }

    companion object {
        private const val REQ_VPN = 42
        private const val REQ_PARTIDO = 43
    }

    private fun isLight(c: Int): Boolean {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return (0.299 * r + 0.587 * g + 0.114 * b) > 170
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
