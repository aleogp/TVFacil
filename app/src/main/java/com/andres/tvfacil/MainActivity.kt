package com.andres.tvfacil

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
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
    private lateinit var clockText: TextView
    private lateinit var infoText: TextView
    private lateinit var barra: TextView
    private lateinit var saver: FrameLayout
    private lateinit var saverPhoto: ImageView
    private lateinit var saverClock: TextView
    private var resumed = false
    private var saverIndex = 0
    private var saverStartedAt = 0L
    private var buttons: MutableList<TvButton> = mutableListOf()
    private val buttonViews = mutableListOf<View>()

    private enum class Mode { NORMAL, BUSY, MESSAGE, UPDATE, SAVER }
    private var mode = Mode.NORMAL
    private var messageShownAt = 0L
    private var pendingUpdate: Remote.Update? = null
    private var checking = false

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
        title.gravity = Gravity.CENTER_VERTICAL
        // En celular (sin tecla Menú): mantener presionado el título abre la configuración.
        title.setOnLongClickListener { openSetup(); true }

        // Encabezado: foto de la nieta (rota en cada visita) + título
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(32), dp(12), dp(32), dp(6))
        photo = ImageView(this)
        photo.visibility = View.GONE
        val plp = LinearLayout.LayoutParams(dp(88), dp(88))
        plp.rightMargin = dp(20)
        header.addView(photo, plp)
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // Reloj, fecha y clima a la derecha
        val clockBox = LinearLayout(this)
        clockBox.orientation = LinearLayout.VERTICAL
        clockBox.gravity = Gravity.END
        clockText = TextView(this)
        clockText.setTextColor(Color.WHITE)
        clockText.typeface = Typeface.DEFAULT_BOLD
        clockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
        clockText.gravity = Gravity.END
        infoText = TextView(this)
        infoText.setTextColor(0xFFCFD8DC.toInt())
        infoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        infoText.gravity = Gravity.END
        clockBox.addView(clockText)
        clockBox.addView(infoText)
        header.addView(clockBox)
        column.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        grid = GridLayout(this)
        column.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        barra = TextView(this)
        barra.setBackgroundColor(0xFFFFE082.toInt())
        barra.setTextColor(0xFF1B1B1B.toInt())
        barra.typeface = Typeface.DEFAULT_BOLD
        barra.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        barra.gravity = Gravity.CENTER
        barra.maxLines = 2
        barra.setPadding(dp(24), dp(10), dp(24), dp(10))
        barra.visibility = View.GONE
        root.addView(barra, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        // Salvapantallas: fotos grandes + reloj, cuando nadie toca el control
        saver = FrameLayout(this)
        saver.setBackgroundColor(Color.BLACK)
        saver.visibility = View.GONE
        saverPhoto = ImageView(this)
        saverPhoto.scaleType = ImageView.ScaleType.FIT_CENTER
        val sh = (resources.displayMetrics.heightPixels * 0.78).toInt()
        saver.addView(saverPhoto, FrameLayout.LayoutParams(sh, sh, Gravity.CENTER_VERTICAL or Gravity.START).apply {
            leftMargin = dp(60)
        })
        saverClock = TextView(this)
        saverClock.setTextColor(Color.WHITE)
        saverClock.typeface = Typeface.DEFAULT_BOLD
        saverClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
        saverClock.gravity = Gravity.CENTER
        saver.addView(saverClock, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL or Gravity.END).apply { rightMargin = dp(70) })
        saver.isClickable = true
        saver.setOnClickListener { stopSaver() }
        root.addView(saver, FrameLayout.LayoutParams(
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
            if (SystemClock.uptimeMillis() - messageShownAt > 1000) {
                if (mode == Mode.MESSAGE) hideOverlay()
                else if (mode == Mode.UPDATE) installUpdate()
            }
        }
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(root)
        handleInstallStatus(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInstallStatus(intent)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        buttons = Config.load(this)
        updateBarra()
        buildGrid()
        showNextPhoto()
        tick.run()
        resetIdle()
        checkRemote()
    }

    // ---------- Configuración y actualizaciones desde GitHub ----------

    /** En segundo plano: botones nuevos (config.json) y versión nueva del APK. */
    private fun checkRemote() {
        if (checking) return
        checking = true
        Thread {
            val changed = Remote.syncConfig(this)
            val upd = Remote.checkUpdate(this)
            Extras.senalDeVida(this)
            Extras.syncMensajes(this)
            Extras.syncClima(this)
            runOnUiThread {
                checking = false
                if (isFinishing) return@runOnUiThread
                val barraAntes = barra.visibility
                updateBarra()
                updateClock()
                if ((changed || barra.visibility != barraAntes) && mode == Mode.NORMAL) {
                    buttons = Config.load(this)
                    buildGrid()
                }
                if (upd != null && mode == Mode.NORMAL) offerUpdate(upd)
                else if (mode == Mode.NORMAL) showNewMessages()
            }
        }.start()
    }

    private fun offerUpdate(u: Remote.Update) {
        if (!Remote.canInstall(this)) return // falta el permiso: Andrés lo da desde la configuración
        pendingUpdate = u
        mode = Mode.UPDATE
        messageShownAt = SystemClock.uptimeMillis()
        showOverlay(
            "🆕  Hay una versión nueva de TV Fácil\n\n" +
                "Aprieten OK para instalarla.\n" +
                "Luego, en la ventana que sale, elijan \"Instalar\".\n\n" +
                "(Para dejarlo para mañana, aprieten la flecha ↩ Atrás)"
        )
    }

    private fun installUpdate() {
        val u = pendingUpdate ?: run { hideOverlay(); return }
        pendingUpdate = null
        mode = Mode.BUSY
        showOverlay("⏳\n\nDescargando la versión nueva…\n\nEsperen un momento, no aprieten nada.")
        Thread {
            val ok = try { Remote.downloadAndInstall(this, u); true } catch (e: Exception) { false }
            if (!ok) runOnUiThread {
                hideOverlay()
                showMessage("No se pudo descargar la versión nueva.\nNo pasa nada: TV Fácil sigue funcionando.\n\n(Aprieten OK para volver)")
            }
            // Si salió bien, Android responde en handleInstallStatus()
        }.start()
    }

    private fun skipUpdate() {
        pendingUpdate?.let { Remote.skip(this, it.tag) }
        pendingUpdate = null
        hideOverlay()
    }

    private fun handleInstallStatus(i: Intent?) {
        if (i == null || i.action != Remote.ACTION_INSTALL_STATUS) return
        setIntent(Intent(Intent.ACTION_MAIN))
        val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                hideOverlay()
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(confirm) } catch (_: Exception) { }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> hideOverlay()
            PackageInstaller.STATUS_FAILURE_ABORTED -> hideOverlay() // eligieron "Cancelar"
            else -> {
                hideOverlay()
                showMessage("No se pudo instalar la versión nueva.\nNo pasa nada: TV Fácil sigue funcionando.\n\n(Aprieten OK para volver)")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        handler.removeCallbacks(tick)
        handler.removeCallbacks(idleRunnable)
        handler.removeCallbacks(saverStep)
        if (mode == Mode.SAVER) stopSaver()
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
        val usableH = dm.heightPixels - dp(120) - (if (barra.visibility == View.VISIBLE) dp(80) else 0)
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
            "ayuda" -> pedirAyuda(b)
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
        resetIdle()
        if (mode == Mode.SAVER) {
            if (event.action == KeyEvent.ACTION_UP) stopSaver()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU) {
            val now = SystemClock.uptimeMillis()
            if (now - firstMenuAt > 4000) { firstMenuAt = now; menuPresses = 0 }
            menuPresses++
            if (menuPresses >= 5) { menuPresses = 0; openSetup() }
            return true
        }
        return when (mode) {
            Mode.BUSY -> true // mientras conecta, ignoramos todo
            Mode.UPDATE -> {
                if (event.action == KeyEvent.ACTION_UP &&
                    SystemClock.uptimeMillis() - messageShownAt > 1000) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> installUpdate()
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> skipUpdate()
                    }
                }
                true
            }
            Mode.MESSAGE -> {
                // Esperamos 1 s para que un toque doble no lo cierre sin leerlo
                if (event.action == KeyEvent.ACTION_UP &&
                    SystemClock.uptimeMillis() - messageShownAt > 1000) hideOverlay()
                true
            }
            Mode.NORMAL -> super.dispatchKeyEvent(event)
            Mode.SAVER -> true
        }
    }

    override fun onBackPressed() {
        if (mode != Mode.NORMAL) return
        super.onBackPressed()
    }

    // ---------- Reloj, mensajes y clima ----------

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 20_000)
        }
    }

    private fun updateClock() {
        clockText.text = Extras.hora()
        val clima = Extras.clima(this)
        infoText.text = Extras.fechaLarga() + if (clima.isNotEmpty()) "  ·  $clima ${Extras.CIUDAD}" else ""
        if (mode == Mode.SAVER) {
            saverClock.text = Extras.hora() + "\n" + Extras.fechaLarga().replace(" de ", "\nde ") +
                (if (clima.isNotEmpty()) "\n\n$clima" else "")
        }
    }

    private fun updateBarra() {
        val lineas = Extras.lineasBarra(this)
        if (lineas.isEmpty()) {
            barra.visibility = View.GONE
        } else {
            barra.text = lineas.take(3).joinToString("     ")
            barra.visibility = View.VISIBLE
        }
    }

    /** Mensajes nuevos de Andrés: se muestran una vez en grande. */
    private fun showNewMessages() {
        val nuevos = Extras.mensajesNuevos(this)
        if (nuevos.isEmpty()) return
        Extras.marcarVistos(this, nuevos.map { it.first })
        val texto = nuevos.joinToString("\n\n") { it.second }
        showMessage("📩  Mensaje de Andrés\n\n$texto\n\n(Aprieten OK para cerrar)")
    }

    // ---------- Botón de ayuda ----------

    private fun pedirAyuda(b: TvButton) {
        mode = Mode.BUSY
        showOverlay("📞\n\nAvisando a Andrés…")
        Thread {
            val ok = Extras.pedirAyuda()
            runOnUiThread {
                showOverlay(
                    if (ok) "✅  Ya le avisamos a Andrés.\n\nAhora se abre AnyDesk:\ndéjenlo abierto, él se va a conectar."
                    else "No pude avisarle por internet.\nLlámenlo por teléfono 📞\n\nAhora se abre AnyDesk."
                )
                handler.postDelayed({
                    hideOverlay()
                    if (b.pkg.isNotBlank() && !launch(b.pkg) && !openStore(b.pkg)) {
                        showMessage("No encuentro AnyDesk.\n\n(Aprieten OK para volver)")
                    }
                }, 4000)
            }
        }.start()
    }

    // ---------- Salvapantallas con fotos ----------

    private val idleRunnable = Runnable { startSaver() }

    private fun resetIdle() {
        handler.removeCallbacks(idleRunnable)
        if (resumed) handler.postDelayed(idleRunnable, SAVER_AFTER)
    }

    private fun photoFiles(): List<String> = try {
        (assets.list("fotos") ?: emptyArray()).filter { it.endsWith(".jpg") || it.endsWith(".png") }.sorted()
    } catch (_: Exception) {
        emptyList()
    }

    private fun startSaver() {
        if (!resumed || mode != Mode.NORMAL || photoFiles().isEmpty()) return
        mode = Mode.SAVER
        saverStartedAt = SystemClock.uptimeMillis()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        saverIndex = (Math.random() * photoFiles().size).toInt()
        saver.alpha = 0f
        saver.visibility = View.VISIBLE
        saver.animate().alpha(1f).setDuration(800).start()
        saverStep.run()
    }

    private val saverStep = object : Runnable {
        override fun run() {
            if (mode != Mode.SAVER) return
            // Después de 1 hora dejamos que el Fire TV se apague solo
            if (SystemClock.uptimeMillis() - saverStartedAt > SAVER_MAX) { stopSaver(); return }
            val files = photoFiles()
            if (files.isEmpty()) { stopSaver(); return }
            saverIndex = (saverIndex + 1) % files.size
            val bmp = try {
                assets.open("fotos/" + files[saverIndex]).use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
            if (bmp != null) {
                saverPhoto.animate().alpha(0f).setDuration(400).withEndAction {
                    saverPhoto.setImageBitmap(roundCorners(bmp))
                    saverPhoto.animate().alpha(1f).setDuration(600).start()
                }.start()
            }
            updateClock()
            handler.postDelayed(this, 9_000)
        }
    }

    private fun stopSaver() {
        handler.removeCallbacks(saverStep)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        saver.visibility = View.GONE
        if (mode == Mode.SAVER) mode = Mode.NORMAL
        resetIdle()
        val last = Config.getLast(this)
        buttonViews.getOrNull(if (last in buttonViews.indices) last else 0)?.requestFocus()
    }

    private fun roundCorners(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val r = src.width * 0.06f
        c.drawRoundRect(android.graphics.RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, paint)
        return out
    }

    // ---------- Varios ----------

    private fun openSetup() {
        if (mode == Mode.BUSY) return
        startActivity(Intent(this, SetupActivity::class.java))
    }

    companion object {
        private const val REQ_VPN = 42
        private const val REQ_PARTIDO = 43
        private const val SAVER_AFTER = 3 * 60_000L   // salvapantallas tras 3 min sin tocar
        private const val SAVER_MAX = 60 * 60_000L    // y como mucho 1 hora
    }

    private fun isLight(c: Int): Boolean {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return (0.299 * r + 0.587 * g + 0.114 * b) > 170
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
