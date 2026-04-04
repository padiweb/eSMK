package com.smkaltan.ujian

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        const val EXAM_URL = "https://ujian.smkaltan.sch.id/login.php"
        const val ALLOWED_DOMAIN = "ujian.smkaltan.sch.id"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
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

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(webView)

        loadingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF1a237e.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Icon buku sederhana via TextView (unicode)
        val bookIcon = TextView(this).apply {
            text = "📖"
            textSize = 56f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val appName = TextView(this).apply {
            text = "Ujian SMK Altan"
            // PUTIH (bukan kuning)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val appSubtitle = TextView(this).apply {
            text = "Sistem Ujian Online"
            // Putih transparan untuk subtitle
            setTextColor(0xAAFFFFFF.toInt())
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val progress = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                bottomMargin = 20
            }
        }

        statusText = TextView(this).apply {
            text = "Memuat sistem ujian..."
            // PUTIH (bukan kuning)
            setTextColor(0xAAFFFFFF.toInt())
            textSize = 13f
            gravity = android.view.Gravity.CENTER
        }

        loadingLayout.addView(bookIcon)
        loadingLayout.addView(appName)
        loadingLayout.addView(appSubtitle)
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
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, ExamDeviceAdminReceiver::class.java)

            if (dpm.isDeviceOwnerApp(packageName)) {
                // Device Owner: kiosk tanpa dialog sama sekali
                dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
                startLockTask()
            } else {
                // Tanpa Device Owner: screen pinning biasa (ada dialog sekali)
                startLockTask()
            }
        } catch (e: Exception) {
            // Lanjut meski gagal
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
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 " +
                "UjianSMKAltan/1.0"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loadingLayout.visibility = View.VISIBLE
                updateStatus("Memuat halaman...")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingLayout.visibility = View.GONE
                injectSecurityScript()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.description?.toString() ?: "Error"
                    } else "Error"
                    loadingLayout.visibility = View.VISIBLE
                    updateStatus("Gagal: $msg\nMencoba ulang...")
                    handler.postDelayed({ webView.reload() }, 3000)
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
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

        if (isNetworkAvailable()) {
            webView.loadUrl(EXAM_URL)
        } else {
            showNoNetworkDialog()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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

    private fun showNoNetworkDialog() {
        updateStatus("Tidak ada koneksi internet!")
        AlertDialog.Builder(this)
            .setTitle("Koneksi Diperlukan")
            .setMessage("Pastikan WiFi atau data seluler aktif, lalu coba lagi.")
            .setCancelable(false)
            .setPositiveButton("Coba Lagi") { _, _ ->
                if (isNetworkAvailable()) webView.loadUrl(EXAM_URL)
                else showNoNetworkDialog()
            }
            .show()
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
        if (!isExamFinished && webView.canGoBack()) webView.goBack()
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
