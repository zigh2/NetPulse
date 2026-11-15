package com.netpulse.app.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** In-app language: wraps a context with the chosen locale ("system" leaves it alone). */
object LocaleHelper {
    fun lang(ctx: Context): String =
        ctx.getSharedPreferences("netpulse", Context.MODE_PRIVATE).getString("lang", "system") ?: "system"

    fun wrap(ctx: Context): Context {
        val l = lang(ctx)
        if (l == "system") return ctx
        val locale = Locale(l)
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return ctx.createConfigurationContext(cfg)
    }
}
