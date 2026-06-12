package com.example.cachefileloader

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.edit

@Suppress("DEPRECATION")
class PttForegroundService : Service() {

    companion object {
        const val ACTION_START_PTT = "com.example.cachefileloader.START_PTT"
        const val ACTION_STOP_PTT  = "com.example.cachefileloader.STOP_PTT"
        const val CHANNEL_ID       = "ptt_channel"
        const val NOTIF_ID         = 1001

        fun startPtt(context: Context) {
            val intent = Intent(context, PttForegroundService::class.java)
            intent.action = ACTION_START_PTT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopPtt(context: Context) {
            val intent = Intent(context, PttForegroundService::class.java)
            intent.action = ACTION_STOP_PTT
            context.startService(intent)
        }
    }

    private lateinit var audioRecorderManager: AudioRecorderManager

    // AudioRecorderManager fires every ~40 ms; only push to widget every 150 ms
    private var lastWidgetUpdateMs = 0L
    private val WIDGET_UPDATE_INTERVAL_MS = 150L

    override fun onCreate() {
        super.onCreate()
        audioRecorderManager = AudioRecorderManager()
        createNotificationChannel()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PTT -> {
                startForeground(NOTIF_ID, buildNotification("Transmitting..."))
                sendBroadcast(
                    Intent(PttWidget.ACTION_PTT_START).setPackage(packageName)
                )
                // Float [0,1] already normalised and boosted by AudioRecorderManager
                audioRecorderManager.startRecording { amplitude: Float ->
                    onAmplitude()
                }
            }
            ACTION_STOP_PTT -> {
                audioRecorderManager.stopRecording()
                resetVisualizerState()
                sendBroadcast(
                    Intent(PttWidget.ACTION_PTT_STOP).setPackage(packageName)
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorderManager.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------
    // Visualiser bridge
    // ------------------------------------------------------------------

    /**
     * Receives a pre-normalised Float [0,1] from AudioRecorderManager.
     * Throttled to WIDGET_UPDATE_INTERVAL_MS so we don't hammer
     * AppWidgetManager on every 40 ms audio buffer.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun onAmplitude() {
        val now = System.currentTimeMillis()
        if (now - lastWidgetUpdateMs < WIDGET_UPDATE_INTERVAL_MS) return
        lastWidgetUpdateMs = now

        val prefs = getSharedPreferences(PttWidget.PREFS_NAME, Context.MODE_PRIVATE)
        val nextFrame = (prefs.getInt(PttWidget.KEY_WAVE_FRAME, 0) + 1) % PttWidget.WAVE_FRAME_COUNT

        prefs.edit() {
            putInt(PttWidget.KEY_WAVE_FRAME, nextFrame)
        }

        PttWidget.refreshAllWidgets(this)
    }

    /** Snap bars and wave ring back to their resting state on PTT release. */
    private fun resetVisualizerState() {
        getSharedPreferences(PttWidget.PREFS_NAME, Context.MODE_PRIVATE)
            .edit() {
                putInt(PttWidget.KEY_WAVE_FRAME, 0)
            }
    }

    // ------------------------------------------------------------------
    // Notification helpers
    // ------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PTT Transmission",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shows when PTT is active"
            channel.setSound(null, null)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val stopIntent = Intent(this, PttForegroundService::class.java).apply {
            action = ACTION_STOP_PTT
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("PTT Field Comms")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_mic_active)
            .addAction(R.drawable.ic_mic_idle, "Release", stopPending)
            .setOngoing(true)
            .build()
    }
}