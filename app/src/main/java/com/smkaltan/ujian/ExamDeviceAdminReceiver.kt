package com.smkaltan.ujian

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device Admin Receiver
 * 
 * Ini memungkinkan aplikasi menjadi Device Owner, yang memberikan
 * kontrol penuh termasuk:
 * - Lock Task Mode tanpa batasan
 * - Blokir factory reset
 * - Kontrol network policy
 * 
 * Untuk aktifkan Device Owner, jalankan via ADB:
 * adb shell dpm set-device-owner com.smkaltan.ujian/.ExamDeviceAdminReceiver
 */
class ExamDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        const val TAG = "ExamDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin diaktifkan")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device Admin dinonaktifkan")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.d(TAG, "Kiosk mode aktif untuk: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.d(TAG, "Kiosk mode selesai")
    }
}
