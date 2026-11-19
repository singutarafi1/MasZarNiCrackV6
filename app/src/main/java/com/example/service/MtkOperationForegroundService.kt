package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Foreground Service with WakeLock to protect long-running USB OTG firmware
 * read/write operations (Super.img dumping, flashing, backup, formatting)
 * from being frozen, killed by Doze mode, or suspended when the device sleeps.
 */
class MtkOperationForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "MediaTek Operation"
                val percentage = intent?.getIntExtra(EXTRA_PERCENTAGE, 0) ?: 0
                val speed = intent?.getStringExtra(EXTRA_SPEED) ?: ""
                val eta = intent?.getStringExtra(EXTRA_ETA) ?: ""
                val notification = buildNotification(title, percentage, speed, eta)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_START -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Firmware Flashing / Backup Active"
                val detail = intent?.getStringExtra(EXTRA_DETAIL) ?: "Transferring partition data over USB OTG..."
                val notification = buildNotification(title, 0, "", "")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    }
                    startForeground(NOTIFICATION_ID, notification, serviceType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MtkServiceTool:OperationWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    // Safety max timeout of 4 hours for massive eMMC/UFS dumps
                    acquire(4 * 60 * 60 * 1000L)
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MediaTek Operation Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for USB OTG firmware flashing and partition backup"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        percentage: Int,
        speed: String,
        eta: String
    ): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            if (percentage > 0) append("$percentage% ")
            if (speed.isNotBlank()) append("• $speed ")
            if (eta.isNotBlank() && eta != "--:--") append("• ETA $eta")
            if (isEmpty()) append("Active OTG USB data transfer in progress...")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percentage.coerceIn(0, 100), percentage <= 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "mtk_operation_channel"
        const val NOTIFICATION_ID = 2002

        private const val ACTION_START = "com.example.service.ACTION_START"
        private const val ACTION_UPDATE = "com.example.service.ACTION_UPDATE"
        private const val ACTION_STOP = "com.example.service.ACTION_STOP"

        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_DETAIL = "extra_detail"
        private const val EXTRA_PERCENTAGE = "extra_percentage"
        private const val EXTRA_SPEED = "extra_speed"
        private const val EXTRA_ETA = "extra_eta"

        fun startOperation(context: Context, title: String, detail: String) {
            try {
                val intent = Intent(context, MtkOperationForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_DETAIL, detail)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun updateProgress(
            context: Context,
            title: String,
            percentage: Int,
            speed: String = "",
            eta: String = ""
        ) {
            try {
                val intent = Intent(context, MtkOperationForegroundService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_PERCENTAGE, percentage)
                    putExtra(EXTRA_SPEED, speed)
                    putExtra(EXTRA_ETA, eta)
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun stopOperation(context: Context) {
            try {
                val intent = Intent(context, MtkOperationForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
