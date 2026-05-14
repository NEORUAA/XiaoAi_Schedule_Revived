package com.neoruaa.xiaoaischedule.importer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.neoruaa.xiaoaischedule.ui.MiuixPageScaffold
import com.neoruaa.xiaoaischedule.ui.theme.XiaoaischeduleTheme
import com.neoruaa.xiaoaischedule.web.WebFileChooserDelegate
import com.neoruaa.xiaoaischedule.web.WebViewLifecycleObserver
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class ImportWebViewActivity : ComponentActivity() {
    private lateinit var repository: ScheduleImportRepository
    private lateinit var fileChooserDelegate: WebFileChooserDelegate
    private var webView: WebView? = null
    private var status by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ScheduleImportRepository(this)
        fileChooserDelegate = WebFileChooserDelegate(this)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val current = webView
                    if (current?.canGoBack() == true) current.goBack() else finish()
                }
            },
        )
        setContent {
            XiaoaischeduleTheme {
                ImportWebContent(
                    url = intent.getStringExtra(ExtraUrl).orEmpty(),
                    title = intent.getStringExtra(ExtraTitle).orEmpty().ifBlank { "导入课程表" },
                    script = intent.getStringExtra(ExtraScript).orEmpty(),
                    schoolName = intent.getStringExtra(ExtraSchoolName).orEmpty().ifBlank { "小爱课程表" },
                    source = intent.getStringExtra(ExtraSource).orEmpty().ifBlank { "xiaoaischedule" },
                    buttonText = intent.getStringExtra(ExtraButtonText).orEmpty().ifBlank { "一键导入" },
                    aiMode = intent.getBooleanExtra(ExtraAiMode, false),
                    desktopMode = intent.getBooleanExtra(ExtraDesktopMode, false),
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun ImportWebContent(
        url: String,
        title: String,
        script: String,
        schoolName: String,
        source: String,
        buttonText: String,
        aiMode: Boolean,
        desktopMode: Boolean,
    ) {
        val toolsJs = remember {
            runCatching { assets.open("aischedule_tools.js").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
        val currentWebView = remember(url, script, aiMode, desktopMode) {
            WebView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.textZoom = 100
                if (desktopMode) {
                    settings.userAgentString = DesktopUserAgent
                }
                val bridge = ScheduleImportBridge(
                    context = this@ImportWebViewActivity,
                    webView = this,
                    repository = repository,
                    scope = lifecycleScope,
                    defaultSchoolName = schoolName,
                    defaultSource = source,
                    onStatus = { status = it },
                )
                addJavascriptInterface(bridge, "AndroidBridge")
                addJavascriptInterface(bridge, "app")
            }
        }
        DisposableEffect(currentWebView) {
            val observer = WebViewLifecycleObserver(currentWebView)
            lifecycle.addObserver(observer)
            currentWebView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return handleUrl(request.url)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return handleUrl(Uri.parse(url))
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    status = "正在加载..."
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    status = "页面已加载"
                    injectImportTools(view, toolsJs)
                    view.evaluateJavascript(ImportJs.BridgeGlue, null)
                    if (desktopMode) {
                        view.evaluateJavascript(DesktopPatch, null)
                    }
                }
            }
            currentWebView.webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams,
                ): Boolean {
                    return fileChooserDelegate.showFileChooser(filePathCallback, fileChooserParams)
                }

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    android.widget.Toast.makeText(this@ImportWebViewActivity, message.orEmpty(), android.widget.Toast.LENGTH_SHORT).show()
                    result?.confirm()
                    return true
                }
            }
            webView = currentWebView
            currentWebView.loadUrl(url)
            onDispose {
                lifecycle.removeObserver(observer)
            }
        }

        MiuixPageScaffold(
            title = title,
            onBack = { finish() },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.surface)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    if (status.isNotBlank()) {
                        Text(text = status, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Button(
                        onClick = {
                            status = if (aiMode) "正在提取页面源码..." else "正在执行脚本..."
                            val js = if (aiMode || script.isBlank()) ImportJs.ExtractHtml else script
                            currentWebView.evaluateJavascript(js, null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = buttonText, color = MiuixTheme.colorScheme.onPrimary)
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AndroidView(
                    factory = { currentWebView },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun injectImportTools(view: WebView, toolsJs: String) {
        view.evaluateJavascript(
            """
                (function(){
                  if (window.__xiaoAiImportToolsInjected) return;
                  window.__xiaoAiImportToolsInjected = true;
                  try {
                    ${toolsJs}
                    if (typeof AIScheduleTools === 'function') AIScheduleTools();
                  } catch(e) {
                    console.warn('tools inject failed', e);
                  }
                })();
            """.trimIndent(),
            null,
        )
    }

    private fun handleUrl(uri: Uri): Boolean {
        val scheme = uri.scheme.orEmpty()
        if (scheme == "http" || scheme == "https" || scheme.isBlank()) return false
        return runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        }.getOrDefault(true)
    }

    companion object {
        private const val ExtraUrl = "extra_url"
        private const val ExtraTitle = "extra_title"
        private const val ExtraScript = "extra_script"
        private const val ExtraSchoolName = "extra_school_name"
        private const val ExtraSource = "extra_source"
        private const val ExtraButtonText = "extra_button_text"
        private const val ExtraAiMode = "extra_ai_mode"
        private const val ExtraDesktopMode = "extra_desktop_mode"
        private const val DesktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private val DesktopPatch = """
            (function(){
              try {
                Object.defineProperty(navigator, 'platform', {get:function(){return 'Win32';}});
                Object.defineProperty(navigator, 'maxTouchPoints', {get:function(){return 0;}});
                var meta = document.querySelector('meta[name=viewport]') || document.createElement('meta');
                meta.name = 'viewport';
                meta.content = 'width=1280, initial-scale=1.0';
                if (!meta.parentNode) document.head.appendChild(meta);
              } catch(e) {}
            })();
        """.trimIndent()

        fun start(
            context: Context,
            url: String,
            script: String = "",
            title: String = "导入课程表",
            schoolName: String = "小爱课程表",
            source: String = "xiaoaischedule",
            buttonText: String = "一键导入",
            aiMode: Boolean = false,
            desktopMode: Boolean = false,
        ) {
            val intent = Intent(context, ImportWebViewActivity::class.java)
                .putExtra(ExtraUrl, url)
                .putExtra(ExtraScript, script)
                .putExtra(ExtraTitle, title)
                .putExtra(ExtraSchoolName, schoolName)
                .putExtra(ExtraSource, source)
                .putExtra(ExtraButtonText, buttonText)
                .putExtra(ExtraAiMode, aiMode)
                .putExtra(ExtraDesktopMode, desktopMode)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
