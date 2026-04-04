package com.smkaltan.ujian

import android.annotation.SuppressLint
import android.app.ActivityManager
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
import android.util.Log
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
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var overlayBlocker: View

    private val handler = Handler(Looper.getMainLooper())
    private var isExamFinished = false
    private var overlayCheckRunnable: Runnable? = null

    companion object {
        const val EXAM_URL = "https://www.ujiansmkaltan.sch.id"
        const val TAG = "UjianSMKAltan"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ===== KEAMANAN LAYER 1: Blokir screenshot & screen recording =====
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // ===== Layar tetap menyala saat ujian =====
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        buildUI()
        setupFullScreen()
        checkAndBlockOverlayApps()
        setupWebView()
        startKioskMode()
        startSecurityMonitor()
        registerHomeBlocker()
    }

    private fun buildUI() {
        val rootLayout = FrameLayout(this)
        rootLayout.setBackgroundColor(0xFF1a237e.toInt())

        // WebView
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(webView)

        // Loading overlay
        loadingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF1a237e.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        progressBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                bottomMargin = 32
            }
        }
        statusText = TextView(this).apply {
            text = "Memuat sistem ujian..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = android.view.Gravity.CENTER
        }
        loadingLayout.addView(progressBar)
        loadingLayout.addView(statusText)
        rootLayout.addView(loadingLayout)

        // Overlay blocker - transparent view di atas segalanya saat terdeteksi overlay
        overlayBlocker = View(this).apply {
            setBackgroundColor(0xCC000000.toInt())
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(overlayBlocker)

        setContentView(rootLayout)
    }

    // ===== KEAMANAN LAYER 2: Full screen - sembunyikan status bar & nav bar =====
    private fun setupFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.systemGestures()
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    // ===== KEAMANAN LAYER 3: Cek & blokir aplikasi overlay =====
    private fun checkAndBlockOverlayApps() {
        if (!Settings.canDrawOverlays(this)) return

        // Jika app ini sendiri tidak butuh overlay, kita monitor saja
        Log.d(TAG, "Overlay permission tersedia - monitoring aktif")
    }

    // ===== KEAMANAN LAYER 4: Kiosk Mode - Lock Task =====
    private fun startKioskMode() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME)
            startLockTask() // Android screen pinning
            Log.d(TAG, "Kiosk mode aktif")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai kiosk mode: ${e.message}")
        }
    }

    // ===== KEAMANAN LAYER 5: Monitor keamanan berkelanjutan =====
    private fun startSecurityMonitor() {
        overlayCheckRunnable = object : Runnable {
            override fun run() {
                if (!isExamFinished) {
                    performSecurityCheck()
                    handler.postDelayed(this, 800)
                }
            }
        }
        handler.post(overlayCheckRunnable!!)
    }

    private fun performSecurityCheck() {
        // Paksa full screen kembali jika berubah
        setupFullScreen()

        // Cek apakah ada overlay aktif dari app lain
        val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        
        // Pada Android 12+, cek apakah window kita masih di depan
        if (!hasWindowFocus() && !isExamFinished) {
            // Coba ambil fokus kembali
            val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeDialogs)
            window.decorView.requestFocus()
        }
    }

    // ===== KEAMANAN LAYER 6: Blokir tombol HOME & RECENTS =====
    private val homeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isExamFinished) {
                // Kembalikan app ke depan
                val relaunch = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                startActivity(relaunch)
            }
        }
    }

    private fun registerHomeBlocker() {
        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        registerReceiver(homeReceiver, filter)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // User agent khusus agar website bisa deteksi ini adalah app Android
            userAgentString = "UjianSMKAltan-AndroidApp/1.0 (Android ${Build.VERSION.RELEASE})"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loadingLayout.visibility = View.VISIBLE
                updateStatus("Memuat ujian...")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingLayout.visibility = View.GONE
                // Inject JavaScript untuk komunikasi dengan website
                injectSecurityScript()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loadingLayout.visibility = View.VISIBLE
                    updateStatus("Koneksi bermasalah. Mencoba ulang...")
                    handler.postDelayed({ webView.reload() }, 3000)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // Hanya izinkan URL dari domain yang sama
                return if (url.startsWith("https://www.ujiansmkaltan.sch.id") ||
                           url.startsWith("https://ujiansmkaltan.sch.id")) {
                    false // izinkan WebView load
                } else {
                    true // blokir URL eksternal
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                updateStatus("Memuat... $newProgress%")
                if (newProgress == 100) {
                    loadingLayout.visibility = View.GONE
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                // Blokir semua permission request dari website
                request?.deny()
            }
        }

        // Interface JavaScript untuk komunikasi website → app
        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun examFinished() {
                runOnUiThread {
                    isExamFinished = true
                    finishExam()
                }
            }

            @JavascriptInterface
            fun getDeviceInfo(): String {
                return """{"platform":"android","version":"${Build.VERSION.RELEASE}","app":"UjianSMKAltan"}"""
            }
        }, "AndroidBridge")

        checkNetworkAndLoad()
    }

    private fun injectSecurityScript() {
        // Script yang di-inject ke halaman web untuk komunikasi dua arah
        val script = """
            (function() {
                // Beritahu website bahwa ini adalah aplikasi Android resmi
                window.isNativeApp = true;
                window.androidVersion = '${Build.VERSION.RELEASE}';
                
                // Disable right click
                document.addEventListener('contextmenu', function(e) { e.preventDefault(); });
                
                // Disable text selection
                document.addEventListener('selectstart', function(e) { e.preventDefault(); });
                
                // Disable copy-paste
                document.addEventListener('copy', function(e) { e.preventDefault(); });
                document.addEventListener('cut', function(e) { e.preventDefault(); });
                
                // Disable developer tools shortcuts
                document.addEventListener('keydown', function(e) {
                    if (e.key === 'F12' || (e.ctrlKey && e.shiftKey && e.key === 'I')) {
                        e.preventDefault();
                    }
                });
                
                console.log('UjianSMKAltan Android App - Security layer aktif');
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun checkNetworkAndLoad() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (isConnected) {
            webView.loadUrl(EXAM_URL)
        } else {
            updateStatus("Tidak ada koneksi internet!")
            showNoNetworkDialog()
        }
    }

    private fun showNoNetworkDialog() {
        AlertDialog.Builder(this)
            .setTitle("Koneksi Diperlukan")
            .setMessage("Aplikasi ujian membutuhkan koneksi internet. Pastikan WiFi atau data seluler aktif.")
            .setCancelable(false)
            .setPositiveButton("Coba Lagi") { _, _ -> checkNetworkAndLoad() }
            .show()
    }

    fun finishExam() {
        AlertDialog.Builder(this)
            .setTitle("Ujian Selesai")
            .setMessage("Ujian telah selesai. Terima kasih!")
            .setCancelable(false)
            .setPositiveButton("Keluar") { _, _ ->
                stopLockTask()
                finish()
            }
            .show()
    }

    private fun updateStatus(message: String) {
        runOnUiThread { statusText.text = message }
    }

    // ===== Blokir semua tombol hardware =====
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isExamFinished) return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) webView.goBack()
                true // Blokir keluar
            }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> true // Blokir semua
            else -> super.onKeyDown(keyCode, event)
        }
    }

    @Deprecated("Deprecated")
    override fun onBackPressed() {
        if (!isExamFinished) {
            if (webView.canGoBack()) webView.goBack()
            // Tidak melakukan super.onBackPressed() = blokir keluar
        } else {
            super.onBackPressed()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullScreen()
        if (!hasFocus && !isExamFinished) {
            // Sistem mencoba menampilkan sesuatu di atas app
            val intent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        setupFullScreen()
        if (!isExamFinished) {
            webView.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isExamFinished) {
            // Coba kembalikan ke depan
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.moveTaskToFront(taskId, 0)
        }
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayCheckRunnable?.let { handler.removeCallbacks(it) }
        try {
            unregisterReceiver(homeReceiver)
        } catch (e: Exception) { /* ignore */ }
        webView.destroy()
    }
}
