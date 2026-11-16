package com.andres.tvfacil

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Control de la app oficial WireGuard mediante sus "intents" de control remoto. */
object Wg {
    const val PKG = "com.wireguard.android"
    const val PERM = "com.wireguard.android.permission.CONTROL_TUNNELS"
    private const val UP = "com.wireguard.android.action.SET_TUNNEL_UP"
    private const val DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || ctx.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    fun setTunnel(ctx: Context, name: String, up: Boolean) {
        val i = Intent(if (up) UP else DOWN)
        i.setPackage(PKG)
        i.putExtra("tunnel", name)
        ctx.sendBroadcast(i)
    }

    fun allDown(ctx: Context, tunnels: List<String>) = tunnels.forEach { setTunnel(ctx, it, false) }
}
