package com.neoruaa.xiaoaischedule.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.neoruaa.xiaoaischedule.R
import com.neoruaa.xiaoaischedule.account.AccountRepository
import com.neoruaa.xiaoaischedule.data.PrivacyStore
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun XiaoAiWebView(
    url: String,
    visible: Boolean,
    accountRepository: AccountRepository,
    privacyStore: PrivacyStore,
    routeHandler: NativeRouteHandler,
    fileChooserDelegate: WebFileChooserDelegate,
    host: BridgeHost,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
    openHttpExternally: Boolean = false,
    onWebViewReady: (WebView) -> Unit = {},
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    var loaded by rememberSaveable(url) { mutableStateOf(false) }
    val xiaoAiCss = remember(context) {
        runCatching {
            context.assets.open(XiaoAiCssAsset).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    val webView = remember(url) {
        WebView(context).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.configure()
        }
    }

    DisposableEffect(webView) {
        val activity = context as? ComponentActivity
        webView.addJavascriptInterface(
            XiaoAiBridge(
                context = context,
                webView = webView,
                accountRepository = accountRepository,
                privacyStore = privacyStore,
                routeHandler = routeHandler,
                host = host,
                scope = scope,
            ),
            "app",
        )
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleUrl(request.url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleUrl(Uri.parse(url))
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                hasError = false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.injectXiaoAiCss(xiaoAiCss)
                view.injectXiaoAiViewportPatch()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) hasError = true
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) hasError = true
            }

            private fun handleUrl(uri: Uri): Boolean {
                val scheme = uri.scheme.orEmpty()
                if (scheme == "tbopen") return true
                if (scheme == "aischedule" || scheme == "xiaoailite") {
                    return routeHandler.handleLocalScheme(uri, context)
                }
                if (openHttpExternally && (scheme == "http" || scheme == "https")) {
                    return runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    }.getOrDefault(false)
                }
                if (scheme != "http" && scheme != "https" && scheme.isNotBlank()) {
                    return runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    }.getOrDefault(true)
                }
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                return fileChooserDelegate.showFileChooser(filePathCallback, fileChooserParams)
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }
        }
        val observer = WebViewLifecycleObserver(webView)
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(visible, url) {
        if (visible && !loaded) {
            loaded = true
            webView.loadUrl(url)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { webView },
            update = {
                it.visibility = if (visible) View.VISIBLE else View.GONE
                onWebViewReady(it)
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (visible && hasError) {
            WebErrorView(
                onRetry = {
                    hasError = false
                    webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webView.reload()
                },
            )
        }
    }
}

private fun WebView.injectXiaoAiCss(css: String) {
    if (css.isBlank()) return
    val quotedCss = JSONObject.quote(css)
    evaluateJavascript(
        """
            (function() {
              var id = 'xiaoai-assets-style';
              var style = document.getElementById(id);
              if (!style) {
                style = document.createElement('style');
                style.id = id;
                (document.head || document.documentElement).appendChild(style);
              }
              style.textContent = $quotedCss;
            })();
        """.trimIndent(),
        null,
    )
}

private fun WebView.injectXiaoAiViewportPatch() {
    evaluateJavascript(XiaoAiViewportPatch, null)
}

private const val XiaoAiCssAsset = "xiaoai.css"

private val XiaoAiViewportPatch = """
    (function() {
      if (window.__xiaoAiViewportPatchInstalled) {
        if (window.__xiaoAiViewportPatchRun) {
          window.__xiaoAiViewportPatchRun();
        }
        return;
      }
      window.__xiaoAiViewportPatchInstalled = true;

      function ensureStyle() {
        var id = 'xiaoai-viewport-patch-style';
        if (document.getElementById(id)) {
          return;
        }
        var style = document.createElement('style');
        style.id = id;
        style.textContent = '#root>[class^="page___"],#root>[class*=" page___"]{height:100%!important;}';
        (document.head || document.documentElement).appendChild(style);
      }

      function pageNodes(root) {
        try {
          return root.querySelectorAll(':scope > [class^="page___"], :scope > [class*=" page___"]');
        } catch (error) {
          return document.querySelectorAll('#root > [class^="page___"], #root > [class*=" page___"]');
        }
      }

      function setImportantStyle(node, name, value) {
        if (
          node.style.getPropertyValue(name) !== value ||
          node.style.getPropertyPriority(name) !== 'important'
        ) {
          node.style.setProperty(name, value, 'important');
        }
      }

      function patch() {
        ensureStyle();
        var root = document.getElementById('root');
        if (!root) {
          return;
        }

        var pages = pageNodes(root);
        for (var i = 0; i < pages.length; i += 1) {
          setImportantStyle(pages[i], 'height', '100%');
        }
      }

      var pending = false;
      function schedulePatch() {
        if (pending) {
          return;
        }
        pending = true;
        setTimeout(function() {
          pending = false;
          patch();
          setTimeout(patch, 50);
          setTimeout(patch, 250);
        }, 0);
      }

      window.__xiaoAiViewportPatchRun = schedulePatch;

      var observer = new MutationObserver(schedulePatch);
      observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['class', 'style']
      });

      ['pushState', 'replaceState'].forEach(function(name) {
        var original = history[name];
        if (typeof original !== 'function') {
          return;
        }
        history[name] = function() {
          var result = original.apply(this, arguments);
          schedulePatch();
          return result;
        };
      });

      window.addEventListener('hashchange', schedulePatch);
      window.addEventListener('popstate', schedulePatch);
      schedulePatch();
    })();
""".trimIndent()

@Suppress("DEPRECATION")
private fun WebSettings.configure() {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
    loadsImagesAutomatically = true
    allowFileAccess = true
    allowContentAccess = true
    useWideViewPort = true
    loadWithOverviewMode = true
    setSupportZoom(false)
    builtInZoomControls = false
    displayZoomControls = false
    mediaPlaybackRequiresUserGesture = false
    cacheMode = WebSettings.LOAD_DEFAULT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }
}

@Composable
private fun WebErrorView(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FBFD))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.main_tab_no_network_retry), color = Color(0x99000000))
        Button(modifier = Modifier.padding(top = 14.dp), onClick = onRetry) {
            Text(
                text = stringResource(R.string.main_tab_click_to_retry),
                color = MiuixTheme.colorScheme.onPrimary,
            )
        }
    }
}
