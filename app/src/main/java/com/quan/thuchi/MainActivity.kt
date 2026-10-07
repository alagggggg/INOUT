package com.quan.thuchi

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingJson: String? = null
    private var pageLoaded = false
    private var restartAttempted = false

    private val importPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    private val exportPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            val json = pendingJson
            if (uri != null && json != null) {
                runCatching { contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) } }
                    .onFailure { showWebNotice("Không thể ghi tệp JSON: ${it.message ?: "Lỗi không xác định"}") }
            }
        }
        pendingJson = null
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setAllowFileAccessFromFileURLs(false)
            setAllowUniversalAccessFromFileURLs(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
        }

        WebView.setWebContentsDebuggingEnabled(false)
        webView.addJavascriptInterface(AndroidBridge(), "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            @Deprecated("For API below 21")
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? = assetLoader.shouldInterceptRequest(Uri.parse(url))
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                pageLoaded = true
                restartAttempted = false
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (!restartAttempted) {
                    restartAttempted = true
                    recreate()
                } else {
                    finish()
                }
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.host != "appassets.androidplatform.net"
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    val intent = params?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
                    }
                    intent.type = "application/json"
                    importPicker.launch(intent)
                    true
                } catch (_: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.handleAndroidBack ? window.handleAndroidBack() : 'exit'") { result ->
                    if (result == "\"exit\"") finish()
                }
            }
        })

        if (savedInstanceState == null) webView.loadUrl(APP_URL) else webView.restoreState(savedInstanceState)
        webView.postDelayed({
            if (!isFinishing && !pageLoaded) {
                webView.stopLoading()
                webView.loadUrl(APP_URL)
            }
        }, 5_000L)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.removeJavascriptInterface("Android")
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun showWebNotice(message: String) {
        val quoted = JSONObject.quote(message)
        webView.post { webView.evaluateJavascript("window.appNotice ? appNotice($quoted) : console.error($quoted)", null) }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun showKeyboard() {
            runOnUiThread {
                webView.requestFocus()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        @JavascriptInterface
        fun saveJson(fileName: String, json: String) {
            if (json.toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) {
                showWebNotice("Tệp sao lưu vượt quá giới hạn 20 MB.")
                return
            }
            pendingJson = json
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").let { if (it.endsWith(".json")) it else "$it.json" }
            runOnUiThread {
                exportPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, safeName)
                })
            }
        }
    }

    companion object {
        private const val APP_URL = "https://appassets.androidplatform.net/assets/index.html"
        private const val MAX_JSON_BYTES = 20 * 1024 * 1024
    }
}
