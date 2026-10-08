package com.quan.thuchi

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

class QuickEntryActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var saveButton: Button
    private lateinit var amountInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var errorText: TextView
    private lateinit var previewText: TextView
    private lateinit var category: CategoryItem
    private lateinit var kind: String
    private lateinit var factorButtons: List<Pair<Button, Long>>

    private var ready = false
    private var readinessAttempts = 0
    private var saving = false
    private var destroyed = false
    private var factor = 1000L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_quick_entry)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

        kind = intent.getStringExtra(QuickEntryWidgetProvider.EXTRA_KIND)
            ?.takeIf { it == "income" || it == "expense" }
            ?: "expense"

        category = WidgetCategoryCatalog
            .find(intent.getStringExtra(QuickEntryWidgetProvider.EXTRA_CATEGORY_ID).orEmpty())
            ?.takeIf { it.kind == kind }
            ?: WidgetCategoryCatalog.byKind(kind).first()

        findViewById<TextView>(R.id.quick_icon).text = category.icon
        findViewById<TextView>(R.id.quick_category).text = category.name
        findViewById<TextView>(R.id.quick_kind).apply {
            text = if (kind == "income") "TIỀN VÀO" else "TIỀN RA"
            setTextColor(Color.parseColor(if (kind == "income") "#168746" else "#D53B4C"))
        }

        amountInput = findViewById(R.id.quick_amount)
        noteInput = findViewById(R.id.quick_note)
        errorText = findViewById(R.id.quick_error)
        previewText = findViewById(R.id.quick_preview)
        saveButton = findViewById(R.id.quick_save)

        factorButtons = listOf(
            findViewById<Button>(R.id.factor_one) to 1L,
            findViewById<Button>(R.id.factor_thousand) to 1_000L,
            findViewById<Button>(R.id.factor_million) to 1_000_000L,
            findViewById<Button>(R.id.factor_billion) to 1_000_000_000L
        )
        factorButtons.forEach { (button, value) ->
            button.setOnClickListener { selectFactor(value) }
        }
        selectFactor(1_000L)

        amountInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updatePreview()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        saveButton.isEnabled = false
        findViewById<Button>(R.id.quick_cancel).setOnClickListener { finish() }
        saveButton.setOnClickListener { commit() }

        setupWebView()
        amountInput.requestFocus()
        amountInput.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(amountInput, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }

    private fun selectFactor(value: Long) {
        factor = value
        factorButtons.forEach { (button, buttonValue) ->
            button.isSelected = buttonValue == value
            button.setTextColor(Color.parseColor(if (buttonValue == value) "#FFFFFF" else "#17171C"))
        }
        updatePreview()
    }

    private fun updatePreview() {
        val raw = amountInput.text.toString().replace(",", "").trim().toDoubleOrNull() ?: 0.0
        previewText.text = "= " + NumberFormat.getNumberInstance(Locale.US).format(raw * factor)
    }

    @Suppress("DEPRECATION")
    private fun setupWebView() {
        web = findViewById(R.id.quick_webview)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
                loader.shouldInterceptRequest(Uri.parse(url))

            override fun onPageFinished(view: WebView, url: String) {
                checkReady()
            }
        }
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private fun checkReady() {
        if (destroyed || ready || !::web.isInitialized) return
        web.evaluateJavascript("window.nativeQuickReady ? window.nativeQuickReady() : false") { result ->
            if (result == "true") {
                web.evaluateJavascript("window.nativeQuickDefaultScale ? window.nativeQuickDefaultScale() : 1000") { scale ->
                    val defaultFactor = scale.trim('"').toLongOrNull()
                        ?.takeIf { it in listOf(1L, 1_000L, 1_000_000L, 1_000_000_000L) }
                        ?: 1_000L
                    selectFactor(defaultFactor)
                    ready = true
                    saveButton.isEnabled = true
                    errorText.text = ""
                }
            } else if (readinessAttempts++ < 40 && !destroyed) {
                web.postDelayed({ checkReady() }, 150)
            } else {
                ready = false
                saveButton.isEnabled = false
                errorText.text = "Không thể khởi tạo dữ liệu. Hãy mở ứng dụng chính một lần rồi thử lại."
            }
        }
    }

    private fun commit() {
        if (saving) return
        val raw = amountInput.text.toString().replace(",", "").trim().toDoubleOrNull()
        if (raw == null || raw <= 0) {
            errorText.text = "Vui lòng nhập số tiền hợp lệ"
            amountInput.requestFocus()
            return
        }
        if (!ready) {
            errorText.text = "Đang chuẩn bị dữ liệu, vui lòng chờ"
            return
        }

        saving = true
        saveButton.isEnabled = false
        val payload = JSONObject().apply {
            put("kind", kind)
            put("categoryId", category.id)
            put("raw", raw)
            put("factor", factor)
            put("note", noteInput.text?.toString()?.trim().orEmpty())
        }.toString()
        val script = "window.nativeQuickSave(" + JSONObject.quote(payload) + ")"

        web.evaluateJavascript(script) { result ->
            when (result.trim('"')) {
                "OK" -> {
                    Toast.makeText(this, "Đã ghi ${category.name}", Toast.LENGTH_SHORT).show()
                    finish()
                }
                "NOT_READY" -> showSaveError("Dữ liệu chưa sẵn sàng, vui lòng thử lại")
                "INVALID_CATEGORY" -> showSaveError("Danh mục không hợp lệ")
                "INVALID_AMOUNT" -> showSaveError("Số tiền hoặc hệ số không hợp lệ")
                "PERSIST_FAILED" -> showSaveError("Không thể ghi dữ liệu vào bộ nhớ")
                else -> showSaveError("Không thể lưu giao dịch")
            }
        }
    }

    private fun showSaveError(message: String) {
        saving = false
        saveButton.isEnabled = ready
        errorText.text = message
    }

    override fun onDestroy() {
        destroyed = true
        if (::web.isInitialized) {
            web.stopLoading()
            web.destroy()
        }
        super.onDestroy()
    }
}
