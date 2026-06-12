package com.example.cachefileloader

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.graphics.toColorInt

class PttWidget : AppWidgetProvider() {

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_PTT_PRESS -> {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit { putBoolean(KEY_IS_TALKING, true) }
                refreshAllWidgets(context)
                PttForegroundService.startPtt(context)
            }
            ACTION_PTT_RELEASE -> {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit { putBoolean(KEY_IS_TALKING, false) }
                refreshAllWidgets(context)
                PttForegroundService.stopPtt(context)
            }
        }
    }

    companion object {
        const val PREFS_NAME       = "PttWidgetPrefs"
        const val KEY_IS_TALKING   = "isTalking"
        const val KEY_WAVE_FRAME   = "waveFrame"
        const val WAVE_FRAME_COUNT = 8

        const val ACTION_PTT_PRESS   = "com.example.cachefileloader.PTT_PRESS"
        const val ACTION_PTT_RELEASE = "com.example.cachefileloader.PTT_RELEASE"
        const val ACTION_PTT_START   = "com.example.cachefileloader.PTT_START"
        const val ACTION_PTT_STOP    = "com.example.cachefileloader.PTT_STOP"

        @RequiresApi(Build.VERSION_CODES.S)
        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, PttWidget::class.java)
            )
            ids.forEach { updateAppWidget(context, manager, it) }
        }

        @RequiresApi(Build.VERSION_CODES.S)
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs     = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isTalking = prefs.getBoolean(KEY_IS_TALKING, false)

            val views = RemoteViews(context.packageName, R.layout.widget_ptt)

            // ── Live dot ──────────────────────────────────────────────
            views.setViewVisibility(
                R.id.iv_live_dot,
                if (isTalking) View.VISIBLE else View.GONE
            )

            // ── PTT button state ──────────────────────────────────────
            if (isTalking) {
                views.setImageViewResource(R.id.btn_ptt_icon, R.drawable.ic_mic_active)
                views.setInt(R.id.btn_ptt, "setBackgroundResource", R.drawable.bg_ptt_active)
                views.setTextViewText(R.id.tv_status, "TRANSMITTING...")
                views.setInt(R.id.tv_status, "setTextColor", "#FF4444".toColorInt())
            } else {
                views.setImageViewResource(R.id.btn_ptt_icon, R.drawable.ic_mic_idle)
                views.setInt(R.id.btn_ptt, "setBackgroundResource", R.drawable.bg_ptt_idle)
                views.setTextViewText(R.id.tv_status, "HOLD TO TALK")
                views.setInt(R.id.tv_status, "setTextColor", "#AAAAAA".toColorInt())
            }

            // ── Wave ring ─────────────────────────────────────────────
            val waveFrame = prefs.getInt(KEY_WAVE_FRAME, 0)
            val waveDrawable = if (isTalking) {
                when (waveFrame % WAVE_FRAME_COUNT) {
                    0    -> R.drawable.ic_ptt_wave_0
                    1    -> R.drawable.ic_ptt_wave_1
                    2    -> R.drawable.ic_ptt_wave_2
                    3    -> R.drawable.ic_ptt_wave_3
                    4    -> R.drawable.ic_ptt_wave_4
                    5    -> R.drawable.ic_ptt_wave_5
                    6    -> R.drawable.ic_ptt_wave_6
                    7    -> R.drawable.ic_ptt_wave_7
                    else -> R.drawable.ic_ptt_wave_0
                }
            } else {
                R.drawable.ic_ptt_wave_0
            }
            views.setImageViewResource(R.id.iv_wave_ring, waveDrawable)

            // ── Click intent ──────────────────────────────────────────
            val action = if (isTalking) ACTION_PTT_RELEASE else ACTION_PTT_PRESS
            val broadcastIntent = Intent(context, PttWidget::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_ptt, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}