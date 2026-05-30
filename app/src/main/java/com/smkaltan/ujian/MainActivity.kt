package com.smkaltan.ujian

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var statusText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var isExamFinished = false
    private var securityRunnable: Runnable? = null
    private var lastViolationTime = 0L
    private val VIOLATION_COOLDOWN = 5000L

    // Receiver untuk tangkap tombol Home
    private val homeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isExamFinished) {
                handler.postDelayed({ bringAppToFront() }, 200)
            }
        }
    }

    companion object {
        const val EXAM_URL = "https://ujian.smkaltan.sch.id/login.php"
        var instance: MainActivity? = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        // Cegah screenshot + jaga layar tetap nyala
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Tampilkan di atas lockscreen (untuk bring to front)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        buildUI()
        setupFullScreen()
        setupWebView()
        registerHomeReceiver()

        // Cek permission accessibility
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityPermissionDialog()
        } else {
            ExamAccessibilityService.isExamActive = true
        }

        // Cek usage stats — tunda 1 detik
        handler.postDelayed({
            if (!isFinishing && !isDestroyed && !isUsageStatsPermissionGranted()) {
                showUsageStatsPermissionDialog()
            }
        }, 1000)

        startSecurityMonitor()
        startOverlayDetectorService()
    }

    private fun registerHomeReceiver() {
        try {
            val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(homeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(homeReceiver, filter)
            }
        } catch (e: Exception) { }
    }

    private fun buildUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF1a237e.toInt())
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(webView)

        loadingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF1a237e.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        loadingLayout.addView(TextView(this).apply {
            text = "📖"; textSize = 56f; gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, 16)
        })
        loadingLayout.addView(TextView(this).apply {
            text = "Ujian SMK Altan"; setTextColor(0xFFFFFFFF.toInt()); textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, 8)
        })
        loadingLayout.addView(TextView(this).apply {
            text = "Sistem Ujian Online"; setTextColor(0xAAFFFFFF.toInt()); textSize = 13f
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, 32)
        })
        loadingLayout.addView(ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply { bottomMargin = 20 }
        })
        statusText = TextView(this).apply {
            text = "Memuat..."; setTextColor(0xAAFFFFFF.toInt()); textSize = 13f
            gravity = android.view.Gravity.CENTER
        }
        loadingLayout.addView(statusText)
        root.addView(loadingLayout)
        setContentView(root)
    }

    private fun setupFullScreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        } catch (e: Exception) { }
    }

    private fun startSecurityMonitor() {
        securityRunnable = object : Runnable {
            override fun run() {
                if (!isExamFinished) {
                    setupFullScreen()
                    // Kalau app tidak di foreground, paksa kembali
                    if (!hasWindowFocus()) {
                        bringAppToFront()
                    }
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(securityRunnable!!)
    }

    private fun bringAppToFront() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
        } catch (e: Exception) { }
    }

    fun reportViolationToWeb(reason: String) {
        if (isExamFinished) return
        val now = System.currentTimeMillis()
        if (now - lastViolationTime < VIOLATION_COOLDOWN) return
        lastViolationTime = now
        val escaped = reason.replace("'", "\\'").replace("\n", " ")
        runOnUiThread {
            webView.evaluateJavascript(
                "if(typeof window.reportMobileViolation==='function'){window.reportMobileViolation('$escaped');}",
                null
            )
        }
    }

    private fun startOverlayDetectorService() {
        try {
            val i = Intent(this, OverlayDetectorService::class.java).apply {
                action = OverlayDetectorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
            else startService(i)
        } catch (e: Exception) { }
    }

    private fun stopOverlayDetectorService() {
        try {
            startService(Intent(this, OverlayDetectorService::class.java).apply {
                action = OverlayDetectorService.ACTION_STOP
            })
        } catch (e: Exception) { }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60000L, now)
            !stats.isNullOrEmpty()
        } catch (e: Exception) { false }
    }

    private fun showUsageStatsPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Izin Monitoring Diperlukan")
            .setMessage("Untuk mendeteksi aplikasi lain saat ujian, aktifkan izin 'Penggunaan Aplikasi'.\n\nLangkah:\n1. Klik OK\n2. Cari 'eSMK Altan'\n3. Aktifkan\n4. Kembali ke aplikasi")
            .setCancelable(false)
            .setPositiveButton("OK, Aktifkan") { _, _ -> startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .setNegativeButton("Lewati") { _, _ -> }
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${ExamAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val sp = TextUtils.SimpleStringSplitter(':')
        sp.setString(enabled)
        while (sp.hasNext()) { if (sp.next().equals(service, ignoreCase = true)) return true }
        return false
    }

    private fun showAccessibilityPermissionDialog() {
        if (!isFinishing && !isDestroyed) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Izin Keamanan Diperlukan")
                .setMessage("Untuk memblokir floating window saat ujian, aktifkan izin Accessibility.\n\nLangkah:\n1. Klik OK\n2. Cari 'eSMK Altan'\n3. Aktifkan\n4. Kembali ke aplikasi")
                .setCancelable(false)
                .setPositiveButton("OK, Aktifkan") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .setNegativeButton("Lewati") { _, _ -> }
                .show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.clearCache(true)
        webView.clearHistory()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 UjianSMKAltan/1.0"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView?, u: String?, f: android.graphics.Bitmap?) {
                loadingLayout.visibility = View.VISIBLE
                updateStatus("Memuat...")
            }
            override fun onPageFinished(v: WebView?, u: String?) {
                loadingLayout.visibility = View.GONE
                webView.evaluateJavascript("""
                    (function(){
                        window.isNativeApp = true;
                        document.addEventListener('contextmenu', function(e){ e.preventDefault(); });
                        document.addEventListener('selectstart', function(e){ e.preventDefault(); });
                        document.addEventListener('copy', function(e){ e.preventDefault(); });
                        document.addEventListener('cut', function(e){ e.preventDefault(); });
                    })();
                """.trimIndent(), null)
                ExamAccessibilityService.isExamActive = true
            }
            override fun onReceivedError(v: WebView?, r: WebResourceRequest?, e: WebResourceError?) {
                if (r?.isForMainFrame == true) {
                    loadingLayout.visibility = View.VISIBLE
                    updateStatus("Gagal memuat. Mencoba ulang dalam 3 detik...")
                    handler.postDelayed({ webView.reload() }, 3000)
                }
            }
            override fun onReceivedSslError(v: WebView?, h: SslErrorHandler?, e: android.net.http.SslError?) {
                val url = v?.url ?: ""
                if (url.contains("smkaltan.sch.id")) h?.proceed() else h?.cancel()
            }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                val url = r?.url?.toString() ?: return false
                return !url.contains("smkaltan.sch.id")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView?, p: Int) {
                if (p < 100) updateStatus("Memuat... $p%") else loadingLayout.visibility = View.GONE
            }
            override fun onPermissionRequest(r: PermissionRequest?) { r?.deny() }
        }

        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface fun examFinished() { runOnUiThread { showFinishDialog() } }
            @JavascriptInterface fun getDeviceInfo(): String =
                """{"platform":"android","version":"${Build.VERSION.RELEASE}","isApp":true}"""
        }, "AndroidBridge")

        if (isNetworkAvailable()) webView.loadUrl(EXAM_URL) else showNoNetworkDialog()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun showNoNetworkDialog() {
        updateStatus("Tidak ada koneksi internet!")
        if (!isFinishing && !isDestroyed) {
            AlertDialog.Builder(this)
                .setTitle("Koneksi Diperlukan")
                .setMessage("Pastikan WiFi atau data seluler aktif, lalu coba lagi.")
                .setCancelable(false)
                .setPositiveButton("Coba Lagi") { _, _ ->
                    if (isNetworkAvailable()) webView.loadUrl(EXAM_URL) else showNoNetworkDialog()
                }.show()
        }
    }

    private fun showFinishDialog() {
        isExamFinished = true
        ExamAccessibilityService.isExamActive = false
        stopOverlayDetectorService()
        if (!isFinishing && !isDestroyed) {
            AlertDialog.Builder(this)
                .setTitle("Ujian Selesai")
                .setMessage("Ujian telah selesai. Terima kasih!")
                .setCancelable(false)
                .setPositiveButton("Keluar") { _, _ -> finish() }
                .show()
        }
    }

    private fun updateStatus(msg: String) { runOnUiThread { statusText.text = msg } }

    // Blokir tombol fisik saat ujian
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isExamFinished) return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { if (webView.canGoBack()) webView.goBack(); true }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_POWER -> true // blokir tombol power juga
            else -> super.onKeyDown(keyCode, event)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!isExamFinished && webView.canGoBack()) webView.goBack()
    }

    // Kalau app kehilangan fokus → paksa kembali ke depan
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullScreen()
        if (!hasFocus && !isExamFinished) {
            handler.postDelayed({
                if (!isExamFinished) bringAppToFront()
            }, 300)
        }
    }

    // Kalau app di-pause (misal Home ditekan) → paksa kembali
    override fun onPause() {
        super.onPause()
        webView.onPause()
        if (!isExamFinished) {
            handler.postDelayed({
                if (!isExamFinished) bringAppToFront()
            }, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        setupFullScreen()
        webView.onResume()
        if (isAccessibilityServiceEnabled()) ExamAccessibilityService.isExamActive = true
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        ExamAccessibilityService.isExamActive = false
        stopOverlayDetectorService()
        securityRunnable?.let { handler.removeCallbacks(it) }
        try { unregisterReceiver(homeReceiver) } catch (e: Exception) { }
        webView.stopLoading()
        webView.destroy()
    }
}
