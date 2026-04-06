package com.smkaltan.ujian

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * ExamAccessibilityService
 * Memantau semua perubahan window. Ketika floating window / overlay
 * dari app lain muncul saat ujian:
 * 1. Kirim notifikasi pelanggaran ke website via JavaScript
 * 2. Tutup overlay dengan simulasi tombol Back
 * 3. Kembalikan fokus ke app ujian
 */
class ExamAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "ExamAccessibility"
        var isExamActive = false
        var instance: ExamAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastPackage = ""
    private var lastViolationTime = 0L
    private val VIOLATION_COOLDOWN = 5000L // 5 detik jeda antar pelanggaran

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        Log.d(TAG, "Accessibility Service terhubung")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isExamActive) return
        val eventPackage = event?.packageName?.toString() ?: return
        if (eventPackage == "com.smkaltan.ujian") return
        if (isSystemPackage(eventPackage)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                if (eventPackage != lastPackage) {
                    lastPackage = eventPackage
                    handleOverlayDetected(eventPackage)
                }
            }
        }
    }

    private fun handleOverlayDetected(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastViolationTime < VIOLATION_COOLDOWN) return
        lastViolationTime = now

        val appLabel = getAppLabel(packageName)
        Log.d(TAG, "Overlay terdeteksi dari: $packageName ($appLabel)")

        handler.postDelayed({
            if (!isExamActive) return@postDelayed

            // 1. Laporkan ke website via MainActivity
            MainActivity.instance?.runOnUiThread {
                MainActivity.instance?.reportViolationToWeb(
                    "Floating window: $appLabel terdeteksi saat ujian [Mobile App]"
                )
            }

            // 2. Tutup overlay via tombol Back
            performGlobalAction(GLOBAL_ACTION_BACK)

            handler.postDelayed({
                // 3. Kembalikan fokus ke app ujian
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                startActivity(intent)
            }, 300)
        }, 200)
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    private fun isSystemPackage(pkg: String): Boolean {
        val systemPackages = listOf(
            "com.smkaltan.ujian",
            "android",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.android.inputmethod",
            "com.google.android.inputmethod",
            "com.samsung.android.honeyboard",
            "com.swiftkey",
            "com.touchtype.swiftkey",
            "com.google.android.gms",
            "com.android.settings"
        )
        return systemPackages.any { pkg.startsWith(it) }
    }

    override fun onInterrupt() { Log.d(TAG, "Service interrupted") }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isExamActive = false
    }
}
