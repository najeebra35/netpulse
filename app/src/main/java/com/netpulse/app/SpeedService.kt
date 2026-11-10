package com.netpulse.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps a live ↓/↑ speed readout in the status bar
 * notification, updating once a second, even when the app is closed.
 */
class SpeedService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val monitor = SpeedMonitor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        startForeground(NOTIF_ID, build(0, 0, 0))
        if (job == null) {
            job = scope.launch {
                monitor.sample()
                while (isActive) {
                    delay(1000)
                    val s = monitor.sample()
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIF_ID, build(s.downBps, s.upBps, s.totalRxBytes + s.totalTxBytes))
                }
            }
        }
        return START_STICKY
    }

    private fun build(down: Long, up: Long, sessionTotal: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SpeedService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val title = "↓ ${Fmt.rate(down)}   ↑ ${Fmt.rate(up)}"
        val text = "Session: ${Fmt.bytes(sessionTotal)} · tap to open"

        val stopAction = Notification.Action.Builder(
            Icon.createWithBitmap(dotIcon()), "Stop", stop
        ).build()

        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(Icon.createWithBitmap(speedIcon(down)))
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(stopAction)
            .build()
    }

    private fun dotIcon(): Bitmap {
        val b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        Canvas(b).drawColor(Color.TRANSPARENT)
        return b
    }

    /** Draws the live download rate as the status-bar icon. */
    private fun speedIcon(down: Long): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mbps = down * 8.0 / 1e6
        val (big, unit) = when {
            mbps >= 1 -> "%.0f".format(mbps) to "Mb"
            else -> "%.0f".format(down / 1e3) to "Kb"
        }
        p.textSize = 52f
        c.drawText(big, size / 2f, 48f, p)
        p.textSize = 34f
        c.drawText(unit, size / 2f, 88f, p)
        return bmp
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Live speed", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "netpulse_speed"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.netpulse.app.STOP"

        fun start(ctx: Context) {
            val i = Intent(ctx, SpeedService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, SpeedService::class.java).setAction(ACTION_STOP))
        }
    }
}
