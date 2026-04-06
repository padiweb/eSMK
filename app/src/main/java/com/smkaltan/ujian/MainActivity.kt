package com.smkaltan.ujian

import android.annotation.SuppressLint
import android.content.Context
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

    companion object {
        const val EXAM_URL = "https://ujian.smkaltan.sch.id/login.php"
        var instance: MainActivity? = null  // untuk dipanggil dari AccessibilityService
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        buildUI()
        setupFullScreen()
        setupWebView()

        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityPermissionDialog()
        } else {
            ExamAccessibilityService.isExamActive = true
            startKioskMode()
            startSecurityMonitor()
        }
    }

    private fun buildUI() {
        val rootLayout = FrameLayout(this).apply { setBackgroundColor(0xFF1a237e.toInt()) }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        rootLayout.addView(webView)

        loadingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF1a237e.toInt())
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val bookIcon = TextView(this).apply { text = "📖"; textSize = 56f; gravity = android.view.Gravity.CENTER; setPadding(0,0,0,16) }
        val appName = TextView(this).apply {
            text = "Ujian SMK Altan"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER; setPadding(0,0,0,8)
        }
        val appSubtitle = TextView(this).apply {
            text = "Sistem Ujian Online"
            setTextColor(0xAAFFFFFF.toInt()); textSize = 13f
            gravity = android.view.Gravity.CENTER; setPadding(0,0,0,32)
        }
        val progress = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(80,80).apply { bottomMargin=20 }
        }
        statusText = TextView(this).apply {
            text = "Memuat sistem ujian..."; setTextColor(0xAAFFFFFF.toInt())
            textSize = 13f; gravity = android.view.Gravity.CENTER
        }

        loadingLayout.addView(bookIcon); loadingLayout.addView(appName)
        loadingLayout.addView(appSubtitle); loadingLayout.addView(progress)
        loadingLayout.addView(statusText); rootLayout.addView(loadingLayout)
        setContentView(rootLayout)
    }

    // ===== LAPORAN PELANGGARAN DARI ACCESSIBILITY SERVICE =====
    fun reportViolationToWeb(reason: String) {
        if (isExamFinished) return
        // Kirim via JavaScript ke halaman ujian
        val escaped = reason.replace("'", "\\'").replace("\n", " ")
        webView.evaluateJavascript(
            "if(typeof window.reportMobileViolation==='function'){window.reportMobileViolation('$escaped');}",
            null
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${ExamAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) { if (splitter.next().equals(service, ignoreCase = true)) return true }
        return false
    }

    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Izin Diperlukan")
            .setMessage(
                "Untuk memblokir floating window saat ujian, aplikasi memerlukan izin Accessibility.\n\n" +
                "Langkah:\n1. Klik OK\n2. Cari 'Ujian SMK Altan'\n3. Aktifkan\n4. Kembali ke aplikasi"
            )
            .setCancelable(false)
            .setPositiveButton("OK, Aktifkan") { _, _ ->
                startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Lewati") { _, _ ->
                startKioskMode(); startSecurityMonitor()
            }
            .show()
    }

    private fun setupFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun startKioskMode() {
        try { startLockTask() } catch (e: Exception) { }
    }

    private fun startSecurityMonitor() {
        securityRunnable = object : Runnable {
            override fun run() {
                if (!isExamFinished) {
                    setupFullScreen()
                    if (!hasWindowFocus()) bringAppToFront()
                    handler.postDelayed(this, 800)
                }
            }
        }
        handler.post(securityRunnable!!)
    }

    private fun bringAppToFront() {
        try {
            startActivity(android.content.Intent(this, MainActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        } catch (e: Exception) { }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.clearCache(true); webView.clearHistory()
        webView.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            allowFileAccess = false; allowContentAccess = false
            setSupportZoom(true); builtInZoomControls = false; displayZoomControls = false
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 " +
                "UjianSMKAltan/1.0"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loadingLayout.visibility = View.VISIBLE; updateStatus("Memuat halaman...")
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                loadingLayout.visibility = View.GONE; injectSecurityScript()
                ExamAccessibilityService.isExamActive = true
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error?.description?.toString() ?: "Error" else "Error"
                    loadingLayout.visibility = View.VISIBLE
                    updateStatus("Gagal: $msg\nMencoba ulang...")
                    handler.postDelayed({ webView.reload() }, 3000)
                }
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) { handler?.proceed() }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) updateStatus("Memuat... $newProgress%") else loadingLayout.visibility = View.GONE
            }
            override fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }
        }

        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface fun examFinished() { runOnUiThread { showFinishDialog() } }
            @JavascriptInterface fun getDeviceInfo() = """{"platform":"android","version":"${Build.VERSION.RELEASE}","isApp":true}"""
        }, "AndroidBridge")

        if (isNetworkAvailable()) webView.loadUrl(EXAM_URL) else showNoNetworkDialog()
    }

    private fun injectSecurityScript() {
        val script = """
            (function(){
                window.isNativeApp=true;
                document.addEventListener('contextmenu',function(e){e.preventDefault();});
                document.addEventListener('selectstart',function(e){e.preventDefault();});
                document.addEventListener('copy',function(e){e.preventDefault();});
                document.addEventListener('cut',function(e){e.preventDefault();});
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNoNetworkDialog() {
        updateStatus("Tidak ada koneksi internet!")
        AlertDialog.Builder(this).setTitle("Koneksi Diperlukan")
            .setMessage("Pastikan WiFi atau data seluler aktif, lalu coba lagi.").setCancelable(false)
            .setPositiveButton("Coba Lagi") { _, _ -> if (isNetworkAvailable()) webView.loadUrl(EXAM_URL) else showNoNetworkDialog() }
            .show()
    }

    private fun showFinishDialog() {
        isExamFinished = true; ExamAccessibilityService.isExamActive = false
        AlertDialog.Builder(this).setTitle("Ujian Selesai").setMessage("Ujian telah selesai. Terima kasih!").setCancelable(false)
            .setPositiveButton("Keluar") { _, _ -> try { stopLockTask() } catch (e: Exception) { }; finish() }.show()
    }

    private fun updateStatus(message: String) { runOnUiThread { statusText.text = message } }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isExamFinished) return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { if (webView.canGoBack()) webView.goBack(); true }
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SEARCH -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    @Deprecated("Deprecated")
    override fun onBackPressed() { if (!isExamFinished && webView.canGoBack()) webView.goBack() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullScreen()
        if (!hasFocus && !isExamFinished) handler.postDelayed({ bringAppToFront() }, 500)
    }

    override fun onResume() {
        super.onResume(); setupFullScreen(); webView.onResume()
        if (isAccessibilityServiceEnabled()) ExamAccessibilityService.isExamActive = true
    }
    override fun onPause() { super.onPause(); webView.onPause() }
    override fun onDestroy() {
        super.onDestroy(); instance = null
        ExamAccessibilityService.isExamActive = false
        securityRunnable?.let { handler.removeCallbacks(it) }
        webView.destroy()
    }
}
