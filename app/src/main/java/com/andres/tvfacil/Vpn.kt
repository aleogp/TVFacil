package com.andres.tvfacil

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader

/**
 * VPN propia de la app (WireGuard integrado). Las configuraciones van dentro del APK,
 * en assets/vpn/<nombre>.conf, y GitHub las pone ahí desde los "secrets" al compilar.
 */
object Vpn {
    private var backend: GoBackend? = null
    private val tunnels = mutableMapOf<String, SimpleTunnel>()

    private class SimpleTunnel(private val n: String) : Tunnel {
        override fun getName() = n
        override fun onStateChange(newState: Tunnel.State) {}
    }

    private fun backend(ctx: Context): GoBackend =
        backend ?: GoBackend(ctx.applicationContext).also { backend = it }

    /** Nombres de las VPN que trae el APK (p. ej. "colombia", "mexico"). */
    fun available(ctx: Context): List<String> =
        (ctx.assets.list("vpn") ?: emptyArray())
            .filter { it.endsWith(".conf") }
            .map { it.removeSuffix(".conf") }
            .sorted()

    /** Si no es null, hay que mostrar este aviso de Android ("solicitud de conexión") una vez. */
    fun permissionIntent(ctx: Context): Intent? = VpnService.prepare(ctx)

    /**
     * Apaga la VPN que esté puesta y, si [name] no es null, prende esa.
     * Bloquea unos segundos: llamar SIEMPRE fuera del hilo principal.
     */
    @Synchronized
    fun switchTo(ctx: Context, name: String?) {
        val b = backend(ctx)
        for (running in b.runningTunnelNames) {
            val t = tunnels.getOrPut(running) { SimpleTunnel(running) }
            try { b.setState(t, Tunnel.State.DOWN, null) } catch (_: Exception) {}
        }
        if (name.isNullOrBlank()) return

        val text = ctx.assets.open("vpn/$name.conf").bufferedReader().use { it.readText() }
            .replace("\r", "")
        val cfg = Config.parse(BufferedReader(StringReader(text)))
        val t = tunnels.getOrPut(name) { SimpleTunnel(name) }
        b.setState(t, Tunnel.State.UP, cfg)
    }

    fun current(ctx: Context): String? = backend(ctx).runningTunnelNames.firstOrNull()
}
