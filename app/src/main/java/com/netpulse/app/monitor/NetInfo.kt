package com.netpulse.app.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.netpulse.app.data.Net

object NetInfo {
    /** Transport of the active network, or [Net.NONE] when the phone has no network at all. */
    fun transport(ctx: Context): Int = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val n = cm.activeNetwork
        val caps = n?.let { cm.getNetworkCapabilities(it) }
        when {
            n == null || caps == null -> Net.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Net.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Net.CELL
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Net.ETH
            else -> Net.OTHER
        }
    } catch (e: Exception) {
        Net.UNKNOWN
    }
}
