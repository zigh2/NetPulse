package com.netpulse.app.monitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.netpulse.app.R
import com.netpulse.app.data.Fmt
import com.netpulse.app.data.LocaleHelper
import com.netpulse.app.ui.MainActivity

/** Home-screen widget: coloured status card with a live timer. Tap to open the app. */
class StatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    companion object {
        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, StatusWidget::class.java))
                if (ids.isEmpty()) return
                val views = build(ctx)
                ids.forEach { mgr.updateAppWidget(it, views) }
            } catch (_: Exception) {
            }
        }

        private fun build(base: Context): RemoteViews {
            val ctx = LocaleHelper.wrap(base)
            val l = MonitorState.flow.value
            val v = RemoteViews(ctx.packageName, R.layout.widget_status)

            val bg = when {
                !l.running || l.paused || l.online == null -> R.drawable.widget_bg_gray
                l.online == true -> R.drawable.widget_bg_green
                else -> R.drawable.widget_bg_red
            }
            v.setInt(R.id.widget_root, "setBackgroundResource", bg)

            val title = when {
                !l.running -> ctx.getString(R.string.status_idle)
                l.paused -> ctx.getString(R.string.paused_label)
                l.online == true -> ctx.getString(R.string.status_online)
                l.online == false -> ctx.getString(R.string.status_offline)
                else -> ctx.getString(R.string.status_waiting)
            }
            val sub = when {
                !l.running -> ctx.getString(R.string.widget_tap)
                l.paused -> ctx.getString(R.string.paused_sub, Fmt.hm(l.resumeAtMs))
                l.online == true && l.host != null -> ctx.getString(R.string.latency_via, l.latencyMs, l.host)
                l.online == false -> ctx.getString(R.string.no_reply)
                else -> ""
            }
            v.setTextViewText(R.id.w_title, title)
            v.setTextViewText(R.id.w_sub, sub)

            if (l.running && !l.paused && l.online != null && l.sinceMs > 0) {
                v.setViewVisibility(R.id.w_timer, View.VISIBLE)
                val base2 = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - l.sinceMs)
                v.setChronometer(R.id.w_timer, base2, null, true)
            } else {
                v.setViewVisibility(R.id.w_timer, View.GONE)
            }

            val open = PendingIntent.getActivity(
                ctx, 3, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
