package com.smkaltan.ujian

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class OverlayDetectorService : Service() {

    companion object {
        const val CHANNEL_ID = "exam_overlay_detector"
        const val NOTIF_ID = 1002
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        var isRunning = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        createNotificationChannel()

        // Android 14+ wajib pakai ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
                startForeground(
                    NOTIF_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            // Kalau tetap gagal, jalankan tanpa foreground
            isRunning = true
        }

        startCheck()
        return START_STICKY
    }

    private fun startCheck() {
        checkRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                try {
                    if (ExamAccessibilityService.isExamActive) {
                        val top = getForegroundApp()
                        if (top != null && top != packageName && !isSystemApp(top)) {
                            val name = getAppName(top)
                            MainActivity.instance?.runOnUiThread {
                                MainActivity.instance?.reportViolationToWeb(
                                    "Aplikasi lain dibuka: $name"
                                )
                            }
                            bringExamToFront()
                        }
                    }
                } catch (e: Exception) { /* abaikan error */ }
                handler.postDelayed(this, 2000)
            }
        }
        handler.post(checkRunnable!!)
    }

    private fun getForegroundApp(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 3000L, now)
            var last: String? = null
            val ev = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    last = ev.packageName
                }
            }
            last
        } catch (e: Exception) {
            null // Kalau tidak ada permission, kembalikan null saja
        }
    }

    private fun bringExamToFront() {
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            })
        } catch (e: Exception) { }
    }

    private fun getAppName(pkg: String): String {
        return try {
            applicationContext.packageManager.run {
                getApplicationLabel(getApplicationInfo(pkg, 0)).toString()
            }
        } catch (e: Exception) { pkg }
    }

    private fun isSystemApp(pkg: String): Boolean {
        val systemPrefixes = listOf(
            "com.smkaltan.ujian",
            "android",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.inputmethod",
            "com.google.android.inputmethod",
            "com.samsung.android.honeyboard",
            "com.swiftkey",
            "com.touchtype.swiftkey",
            "com.google.android.gms",
            "com.android.settings"
        )
        return systemPrefixes.any { pkg.startsWith(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Monitor Keamanan Ujian",
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(" Ujian Berlangsung")
            .setContentText("Monitor keamanan aktif")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        checkRunnable?.let { handler.removeCallbacks(it) }
    }
}
