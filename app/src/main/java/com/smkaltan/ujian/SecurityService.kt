package com.smkaltan.ujian

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground Service yang berjalan selama ujian.
 * Ini memastikan:
 * - App tidak di-kill oleh sistem Android
 * - App selalu muncul di notifikasi (tidak bisa di-swipe)
 * - Monitoring keamanan berjalan terus
 */
class SecurityService : Service() {

    companion object {
        const val CHANNEL_ID = "exam_security_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.smkaltan.ujian.STOP_EXAM"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Restart otomatis jika di-kill sistem
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitor Ujian",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring keamanan ujian aktif"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 Ujian Sedang Berlangsung")
            .setContentText("Jangan menutup aplikasi selama ujian")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Tidak bisa di-dismiss
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Restart service jika di-kill
        val broadcastIntent = Intent("com.smkaltan.ujian.RESTART_SERVICE")
        sendBroadcast(broadcastIntent)
    }
}
