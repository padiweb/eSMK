package com.smkaltan.ujian

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * OverlayDetectorService v2
 *
 * Deteksi foreground app menggunakan UsageStatsManager
 * (bekerja di Android 13+ menggantikan getRunningTasks yang diblokir)
 * + fallback ActivityManager untuk Android lama
 */
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
            stopSelf(); return START_NOT_STICKY
        }
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        startOverlayCheck()
        return START_STICKY
    }

    private fun startOverlayCheck() {
        checkRunnable = object : Runnable {
            override fun run() {
                if (!isRunning || !ExamAccessibilityService.isExamActive) {
                    handler.postDelayed(this, 2000)
                    return
                }
                val topPackage = getForegroundApp()
                if (topPackage != null && topPackage != packageName && !isSystemPackage(topPackage)) {
                    val appName = getAppName(topPackage)
                    // Laporkan ke MainActivity
                    MainActivity.instance?.runOnUiThread {
                        MainActivity.instance?.reportViolationToWeb(
                            "Aplikasi lain dibuka: $appName [Mobile App]"
                        )
                    }
                    // Kembalikan app ujian ke depan
                    bringExamToFront()
                }
                handler.postDelayed(this, 1500)
            }
        }
        handler.post(checkRunnable!!)
    }

    private fun getForegroundApp(): String? {
        // Android 13+ (API 33): pakai UsageStatsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getForegroundAppViaUsageStats()
        } else {
            getForegroundAppLegacy()
        }
    }

    private fun getForegroundAppViaUsageStats(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 3000, now)
            var lastPkg: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = event.packageName
                }
            }
            lastPkg
        } catch (e: Exception) { null }
    }

    @Suppress("DEPRECATION")
    private fun getForegroundAppLegacy(): String? {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getRunningTasks(1)?.firstOrNull()?.topActivity?.packageName
        } catch (e: Exception) { null }
    }

    private fun bringExamToFront() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) { }
    }

    private fun getAppName(pkg: String): String {
        return try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
    }

    private fun isSystemPackage(pkg: String): Boolean {
        val systemPkgs = listOf(
            "com.smkaltan.ujian", "android", "com.android.systemui",
            "com.android.launcher", "com.android.inputmethod",
            "com.google.android.inputmethod", "com.samsung.android.honeyboard",
            "com.swiftkey", "com.touchtype.swiftkey", "com.google.android.gms"
        )
        return systemPkgs.any { pkg.startsWith(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Monitor Keamanan", NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 Ujian Berlangsung")
            .setContentText("Monitor keamanan aktif")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        checkRunnable?.let { handler.removeCallbacks(it) }
    }
}
