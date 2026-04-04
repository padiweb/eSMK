package com.smkaltan.ujian

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var statusText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var isExamFinished = false
    private var securityRunnable: Runnable? = null

    companion object {
        const val EXAM_URL = "https://www.ujian.smkaltan.sch.id/login.php"
        const val TAG = "UjianSMKAltan"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Blokir screenshot & screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Layar tetap menyala
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        buildUI()
        setupFullScreen()
        setupWebView()
        startKioskMode()
        startSecurityMonitor()
    }

    private fun buildUI() {
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(0xFF1a237e.toInt())
        }

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

        val progress = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                bottomMargin = 32
            }
        }

        val appName = TextView(this).apply {
            text = "SMK Altan"
            setTextColor(0xFFFFD600.toInt())
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }

        statusText = TextView(this).apply {
            text = "Memuat sistem ujian..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            gravity = android.view.Gravity.CENTER
        }

        loadingLayout.addView(appName)
        loadingLayout.addView(progress)
        loadingLayout.addView(statusText)
        rootLayout.addView(loadingLayout)

        setContentView(rootLayout)
    }

    private fun setupFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(
                    WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars()
                )
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun startKioskMode() {
        try {
            startLockTask()
            Log.d(TAG, "Kiosk mode aktif")
        } catch (e: Exception) {
            Log.e(TAG, "Kiosk mode gagal: ${e.message}")
        }
    }

    private fun startSecurityMonitor() {
        securityRunnable = object : Runnable {
            override fun run() {
                if (!isExamFinished) {
                    setupFullScreen()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(securityRunnable!!)
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
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "UjianSMKAltan-AndroidApp/1.0 (Android ${Build.VERSION.RELEASE})"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loadingLayout.visibility = View.VISIBLE
                updateStatus("Memuat ujian...")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingLayout.visibility = View.GONE
                injectSecurityScript()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loadingLayout.visibility = View.VISIBLE
                    updateStatus("Koneksi bermasalah. Mencoba ulang dalam 3 detik...")
                    handler.postDelayed({ webView.reload() }, 3000)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                return !(url.startsWith("https://ujian.smkaltan.sch.id") ||
                         url.startsWith("http://ujian.smkaltan.sch.id"))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    updateStatus("Memuat... $newProgress%")
                } else {
                    loadingLayout.visibility = View.GONE
                }
            }
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
            }
        }

        // JavaScript bridge untuk website memanggil fungsi app
        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun examFinished() {
                runOnUiThread { showFinishDialog() }
            }

            @JavascriptInterface
            fun getDeviceInfo(): String {
                return """{"platform":"android","version":"${Build.VERSION.RELEASE}","isApp":true}"""
            }
        }, "AndroidBridge")

        checkNetworkAndLoad()
    }

    private fun injectSecurityScript() {
        val script = """
            (function() {
                window.isNativeApp = true;
                document.addEventListener('contextmenu', function(e){ e.preventDefault(); });
                document.addEventListener('selectstart', function(e){ e.preventDefault(); });
                document.addEventListener('copy', function(e){ e.preventDefault(); });
                document.addEventListener('cut', function(e){ e.preventDefault(); });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun checkNetworkAndLoad() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val connected = cm.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (connected) {
            webView.loadUrl(EXAM_URL)
        } else {
            updateStatus("Tidak ada koneksi internet!")
            AlertDialog.Builder(this)
                .setTitle("Koneksi Diperlukan")
                .setMessage("Pastikan WiFi atau data seluler aktif, lalu coba lagi.")
                .setCancelable(false)
                .setPositiveButton("Coba Lagi") { _, _ -> checkNetworkAndLoad() }
                .show()
        }
    }

    private fun showFinishDialog() {
        isExamFinished = true
        AlertDialog.Builder(this)
            .setTitle("Ujian Selesai")
            .setMessage("Ujian telah selesai. Terima kasih!")
            .setCancelable(false)
            .setPositiveButton("Keluar") { _, _ ->
                try { stopLockTask() } catch (e: Exception) { }
                finish()
            }
            .show()
    }

    private fun updateStatus(message: String) {
        runOnUiThread { statusText.text = message }
    }

    // Blokir semua tombol hardware
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isExamFinished) return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) webView.goBack()
                true
            }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SEARCH -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    @Deprecated("Deprecated")
    override fun onBackPressed() {
        if (!isExamFinished && webView.canGoBack()) {
            webView.goBack()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullScreen()
    }

    override fun onResume() {
        super.onResume()
        setupFullScreen()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        securityRunnable?.let { handler.removeCallbacks(it) }
        webView.destroy()
    }
}
